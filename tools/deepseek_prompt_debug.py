"""Debug harness for the DeepSeek subtitle-enhancement prompt.

Usage:
    python tools/deepseek_prompt_debug.py
    python tools/deepseek_prompt_debug.py --input tools/deepseek_prompt_input.json
    python tools/deepseek_prompt_debug.py --save tools/deepseek_prompt_result.json

The API key is read from DEEPSEEK_API_KEY or a local, ignored .env file. It is
never printed or saved.
The default input is synthetic and contains no user media or private data.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


def load_local_dotenv(path: Path = Path(".env")) -> None:
    """Load simple KEY=VALUE entries without adding a dotenv dependency."""
    if not path.is_file():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


load_local_dotenv()


ENDPOINT = "https://api.deepseek.com/chat/completions"
DEFAULT_MODEL = "deepseek-v4-flash"
DEFAULT_CUES = [
    {
        "cue_id": "cue-001",
        "start": "00:00:01,000",
        "end": "00:00:04,000",
        "english": "I walk alone beneath the city lights",
    },
    {
        "cue_id": "cue-002",
        "start": "00:00:04,000",
        "end": "00:00:07,000",
        "english": "And carry all the dreams I left behind",
    },
]


def load_cues(path: str | None) -> list[dict[str, Any]]:
    if not path:
        return DEFAULT_CUES
    value = json.loads(Path(path).read_text(encoding="utf-8"))
    if isinstance(value, dict):
        value = value.get("cues")
    if not isinstance(value, list) or not value:
        raise ValueError("input must be a non-empty JSON array, or {\"cues\": [...]}")
    for cue in value:
        if not isinstance(cue, dict) or not {"cue_id", "start", "end", "english"} <= cue.keys():
            raise ValueError("each cue needs cue_id, start, end, and english")
    return value


def build_payload(cues: list[dict[str, Any]], model: str) -> dict[str, Any]:
    return {
        "model": model,
        "temperature": 0.2,
        "max_tokens": max(800, len(cues) * 220),
        "response_format": {"type": "json_object"},
        "messages": [
            {
                "role": "system",
                "content": (
                    "你是英文歌词字幕校正和中文翻译助手。"
                    "只处理用户提供的字幕片段，不猜测歌曲名称，不补写未提供的歌词。"
                    "必须输出合法 JSON，格式为："
                    '{"song_match":{"title":null,"artist":null,"confidence":0.0},'
                    '"cues":[{"cue_id":"string",'
                    '"english_corrected":"string",'
                    '"chinese_translation":"string",'
                    '"confidence":0.0}]}'
                    "中文要自然流畅，避免逐词直译；无法确认时保留原文并降低置信度。"
                ),
            },
            {
                "role": "user",
                "content": (
                    "请处理以下字幕。保持 cue_id 和顺序，不要修改时间轴。\n"
                    + json.dumps(cues, ensure_ascii=False, indent=2)
                ),
            },
        ],
    }


def parse_json_content(content: str) -> dict[str, Any]:
    cleaned = content.strip()
    if cleaned.startswith("```"):
        lines = cleaned.splitlines()
        cleaned = "\n".join(lines[1:-1]).strip()
    result = json.loads(cleaned)
    if not isinstance(result, dict):
        raise ValueError("model JSON root must be an object")
    return result


def validate_result(result: dict[str, Any], input_cues: list[dict[str, Any]]) -> list[str]:
    errors: list[str] = []
    match = result.get("song_match")
    if not isinstance(match, dict) or "confidence" not in match:
        errors.append("missing song_match.confidence")

    output_cues = result.get("cues")
    if not isinstance(output_cues, list):
        return errors + ["missing cues array"]
    if len(output_cues) != len(input_cues):
        errors.append(f"cue count {len(output_cues)} != input count {len(input_cues)}")

    expected_ids = [cue["cue_id"] for cue in input_cues]
    actual_ids = [cue.get("cue_id") for cue in output_cues if isinstance(cue, dict)]
    if actual_ids != expected_ids:
        errors.append(f"cue id/order mismatch: {actual_ids!r}")

    for index, cue in enumerate(output_cues):
        if not isinstance(cue, dict):
            errors.append(f"cue {index} is not an object")
            continue
        for field in ("english_corrected", "chinese_translation", "confidence"):
            if field not in cue:
                errors.append(f"cue {index} missing {field}")
        if not cue.get("chinese_translation"):
            errors.append(f"cue {index} has empty chinese_translation")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", help="JSON cue array or object with a cues array")
    parser.add_argument("--save", help="optional path for parsed model JSON")
    parser.add_argument("--model", default=os.environ.get("DEEPSEEK_MODEL", DEFAULT_MODEL))
    args = parser.parse_args()

    api_key = os.environ.get("DEEPSEEK_API_KEY")
    if not api_key:
        print("ERROR: DEEPSEEK_API_KEY is not set; no request was sent.", file=sys.stderr)
        return 2

    try:
        input_cues = load_cues(args.input)
        payload = build_payload(input_cues, args.model)
    except (OSError, json.JSONDecodeError, ValueError) as error:
        print(f"INPUT ERROR: {error}", file=sys.stderr)
        return 2

    print("=== Request summary (secret-safe) ===")
    print(f"endpoint: {ENDPOINT}")
    print(f"model: {args.model}")
    print(f"cue_count: {len(input_cues)}")
    print("authorization: <redacted>")
    print("request_body:")
    print(json.dumps(payload, ensure_ascii=False, indent=2))

    request = urllib.request.Request(
        ENDPOINT,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            status = response.status
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        print(f"HTTP status: {error.code}")
        print("The response body was not printed.")
        return 1
    except urllib.error.URLError as error:
        print(f"NETWORK ERROR: {error.reason}")
        return 1

    try:
        envelope = json.loads(body)
        content = envelope["choices"][0]["message"]["content"]
        result = parse_json_content(content)
    except (KeyError, IndexError, TypeError, json.JSONDecodeError, ValueError) as error:
        print(f"RESPONSE FORMAT ERROR: {error}")
        return 1

    print(f"HTTP status: {status}")
    print("=== Parsed model output ===")
    print(json.dumps(result, ensure_ascii=False, indent=2))

    errors = validate_result(result, input_cues)
    print("=== Validation ===")
    if errors:
        print("FAIL")
        for error in errors:
            print(f"- {error}")
        exit_code = 1
    else:
        print("PASS: cue count, cue order, cue ids, and required fields are valid.")
        exit_code = 0

    if args.save:
        Path(args.save).write_text(
            json.dumps(result, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"saved: {args.save}")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
