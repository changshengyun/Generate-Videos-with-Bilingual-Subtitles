"""Minimal DeepSeek subtitle-enhancement probe.

The API key is read from DEEPSEEK_API_KEY or a local, ignored .env file. This script intentionally
uses synthetic subtitle text and does not send media, file paths, or user data.
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path


def load_local_dotenv(path: Path = Path(".env")) -> None:
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
MODEL = os.environ.get("DEEPSEEK_MODEL", "deepseek-v4-flash")


def build_payload() -> dict:
    return {
        "model": MODEL,
        "temperature": 0.2,
        "max_tokens": 800,
        "response_format": {"type": "json_object"},
        "messages": [
            {
                "role": "system",
                "content": (
                    "你是英文歌词字幕校正和中文翻译助手。"
                    "只处理用户提供的字幕片段，不猜测歌曲名称，"
                    "不补写未提供的歌词。输出合法 JSON，格式为："
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
                    "请处理下面两条测试字幕。它们是合成文本，不代表真实歌曲：\n"
                    '[{"cue_id":"cue-001","start":"00:00:01,000",'
                    '"end":"00:00:04,000",'
                    '"english":"I walk alone beneath the city lights"},'
                    '{"cue_id":"cue-002","start":"00:00:04,000",'
                    '"end":"00:00:07,000",'
                    '"english":"And carry all the dreams I left behind"}]'
                ),
            },
        ],
    }


def main() -> int:
    api_key = os.environ.get("DEEPSEEK_API_KEY")
    if not api_key:
        print("Missing DEEPSEEK_API_KEY; no request was sent.", file=sys.stderr)
        return 2

    request = urllib.request.Request(
        ENDPOINT,
        data=json.dumps(build_payload(), ensure_ascii=False).encode("utf-8"),
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
        return 1
    except urllib.error.URLError as error:
        print(f"Network error: {error.reason}")
        return 1

    try:
        result = json.loads(body)
        content = result["choices"][0]["message"]["content"]
        parsed_content = json.loads(content)
    except (KeyError, IndexError, TypeError, json.JSONDecodeError) as error:
        print(f"Response format error: {error}")
        return 1

    print(f"HTTP status: {status}")
    print(json.dumps(parsed_content, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
