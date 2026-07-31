#!/usr/bin/env python3
"""Evaluate Local ASR cue JSON against accurate lyric references.

The input is intentionally independent from the Android product flow:

{
  "models": [{
    "name": "ggml-base.bin",
    "fixtures": [{
      "name": "clear-vocal",
      "reference": "accurate lyric text",
      "cues": [{"start_ms": 0, "end_ms": 1000, "text": "recognized text"}],
      "elapsed_ms": 1234,
      "peak_rss_kb": 123456,
      "temperature_before_c": 35.0,
      "temperature_after_c": 37.0,
      "crashed": false,
      "demo_fallback": false
    }]
  }]
}

References must be supplied by the user or an existing project fixture. This
tool never invents reference lyrics, never uses product fallback data, and only
reports metrics for approved local Whisper model names.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
from pathlib import Path
from typing import Any


APPROVED_MODELS = {
    "ggml-base.bin",
    "ggml-base.en.bin",
    "ggml-small.en-q5_1.bin",
}


def normalize(text: str) -> str:
    folded = unicodedata.normalize("NFKC", text).casefold()
    folded = folded.replace("’", "'").replace("‘", "'").replace("`", "'")
    return " ".join(re.findall(r"[a-z0-9]+(?:'[a-z0-9]+)?", folded))


def edit_counts(reference: list[str], hypothesis: list[str]) -> dict[str, int]:
    rows = [[0] * (len(hypothesis) + 1) for _ in range(len(reference) + 1)]
    operations: list[list[str | None]] = [
        [None] * (len(hypothesis) + 1) for _ in range(len(reference) + 1)
    ]
    for i in range(1, len(reference) + 1):
        rows[i][0] = i
        operations[i][0] = "delete"
    for j in range(1, len(hypothesis) + 1):
        rows[0][j] = j
        operations[0][j] = "insert"

    for i, ref_word in enumerate(reference, 1):
        for j, hyp_word in enumerate(hypothesis, 1):
            if ref_word == hyp_word:
                rows[i][j] = rows[i - 1][j - 1]
                operations[i][j] = "equal"
                continue
            choices = [
                (rows[i - 1][j] + 1, "delete"),
                (rows[i][j - 1] + 1, "insert"),
                (rows[i - 1][j - 1] + 1, "substitute"),
            ]
            rows[i][j], operations[i][j] = min(choices, key=lambda item: (item[0], item[1]))

    counts = {"substitutions": 0, "deletions": 0, "insertions": 0}
    i, j = len(reference), len(hypothesis)
    while i or j:
        operation = operations[i][j]
        if operation == "equal":
            i -= 1
            j -= 1
        elif operation == "delete":
            counts["deletions"] += 1
            i -= 1
        elif operation == "insert":
            counts["insertions"] += 1
            j -= 1
        elif operation == "substitute":
            counts["substitutions"] += 1
            i -= 1
            j -= 1
        else:
            raise AssertionError(f"missing edit operation at {i},{j}")
    return counts


def repeated_tokens(words: list[str]) -> int:
    count = 0
    index = 1
    while index < len(words):
        if words[index] == words[index - 1]:
            count += 1
        index += 1
    return count


def timing_valid(cues: list[dict[str, Any]]) -> bool:
    previous_start = -1
    previous_end = -1
    for cue in cues:
        try:
            start = int(cue["start_ms"])
            end = int(cue["end_ms"])
        except (KeyError, TypeError, ValueError):
            return False
        if start < 0 or end <= start or start < previous_start or end < previous_end:
            return False
        if not str(cue.get("text", "")).strip():
            return False
        previous_start, previous_end = start, end
    return bool(cues)


def evaluate_fixture(fixture: dict[str, Any]) -> dict[str, Any]:
    reference = normalize(str(fixture.get("reference", "")))
    if not reference:
        raise ValueError(f"fixture has no accurate reference lyrics: {fixture.get('name', 'unnamed')}")
    cues = fixture.get("cues", [])
    if not isinstance(cues, list):
        cues = []
    hypothesis = normalize(" ".join(str(cue.get("text", "")) for cue in cues if isinstance(cue, dict)))
    reference_words = reference.split()
    hypothesis_words = hypothesis.split()
    counts = edit_counts(reference_words, hypothesis_words)
    word_denominator = max(1, len(reference_words))
    char_denominator = max(1, len(reference.replace(" ", "")))
    char_counts = edit_counts(list(reference.replace(" ", "")), list(hypothesis.replace(" ", "")))
    crashed = bool(fixture.get("crashed", False))
    empty_result = not hypothesis_words
    return {
        "name": fixture.get("name", "unnamed"),
        "wer": (counts["substitutions"] + counts["deletions"] + counts["insertions"]) / word_denominator,
        "cer": (char_counts["substitutions"] + char_counts["deletions"] + char_counts["insertions"]) / char_denominator,
        "reference_words": len(reference_words),
        "reference_chars": len(reference.replace(" ", "")),
        "hypothesis_words": len(hypothesis_words),
        "word_errors": counts["substitutions"] + counts["deletions"] + counts["insertions"],
        "char_errors": char_counts["substitutions"] + char_counts["deletions"] + char_counts["insertions"],
        "substitutions": counts["substitutions"],
        "leaked_words": counts["deletions"],
        "inserted_words": counts["insertions"],
        "repeated_tokens": repeated_tokens(hypothesis_words),
        "empty_result": empty_result,
        "cue_count": len(cues),
        "timestamps_valid": timing_valid(cues),
        "crashed": crashed,
        "demo_fallback": bool(fixture.get("demo_fallback", False)),
        "elapsed_ms": fixture.get("elapsed_ms"),
        "peak_rss_kb": fixture.get("peak_rss_kb"),
        "temperature_before_c": fixture.get("temperature_before_c"),
        "temperature_after_c": fixture.get("temperature_after_c"),
    }


def evaluate_model(model: dict[str, Any]) -> dict[str, Any]:
    model_name = str(model.get("name", "unnamed"))
    if model_name not in APPROVED_MODELS:
        raise ValueError(f"unapproved model: {model_name}")
    raw_fixtures = model.get("fixtures", [])
    if len(raw_fixtures) < 3:
        raise ValueError(f"model requires at least three 30-60 second fixtures: {model_name}")
    if any(
        fixture.get("duration_ms", 0) < 30_000 or fixture.get("duration_ms", 0) > 60_000
        for fixture in raw_fixtures
    ):
        raise ValueError(f"model has a fixture outside the required 30-60 second range: {model_name}")
    fixtures = [evaluate_fixture(fixture) for fixture in raw_fixtures]
    reference_words = sum(fixture["reference_words"] for fixture in fixtures)
    reference_chars = sum(fixture["reference_chars"] for fixture in fixtures)
    word_errors = sum(fixture["word_errors"] for fixture in fixtures)
    char_errors = sum(fixture["char_errors"] for fixture in fixtures)
    return {
        "name": model_name,
        "fixtures": fixtures,
        "aggregate": {
            "wer": word_errors / max(1, reference_words),
            "cer": char_errors / max(1, reference_chars),
            "leaked_words": sum(fixture["leaked_words"] for fixture in fixtures),
            "repeated_tokens": sum(fixture["repeated_tokens"] for fixture in fixtures),
            "empty_results": sum(1 for fixture in fixtures if fixture["empty_result"]),
            "invalid_timestamps": sum(1 for fixture in fixtures if not fixture["timestamps_valid"]),
            "crashes": sum(1 for fixture in fixtures if fixture["crashed"]),
            "demo_fallbacks": sum(1 for fixture in fixtures if fixture["demo_fallback"]),
            "elapsed_ms": [fixture["elapsed_ms"] for fixture in fixtures],
            "peak_rss_kb": [fixture["peak_rss_kb"] for fixture in fixtures],
            "temperature_before_c": [fixture["temperature_before_c"] for fixture in fixtures],
            "temperature_after_c": [fixture["temperature_after_c"] for fixture in fixtures],
        },
    }


def fixture_signature(fixture: dict[str, Any]) -> tuple[str, str, int]:
    return (
        str(fixture.get("name", "unnamed")),
        normalize(str(fixture.get("reference", ""))),
        int(fixture.get("duration_ms", 0)),
    )


def validate_comparable_models(models: list[dict[str, Any]]) -> None:
    expected: list[tuple[str, str, int]] | None = None
    expected_model: str | None = None
    seen_models: set[str] = set()
    for model in models:
        model_name = str(model.get("name", "unnamed"))
        if model_name in seen_models:
            raise ValueError(f"duplicate model: {model_name}")
        seen_models.add(model_name)
        signatures = [fixture_signature(fixture) for fixture in model.get("fixtures", [])]
        if expected is None:
            expected = signatures
            expected_model = model_name
            continue
        if signatures != expected:
            raise ValueError(
                f"model fixtures are not comparable: {model_name} differs from {expected_model}"
            )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="JSON manifest containing model results")
    parser.add_argument("--output", type=Path, help="write JSON results to this ignored output path")
    args = parser.parse_args()
    try:
        payload = json.loads(args.manifest.read_text(encoding="utf-8"))
        models = payload.get("models", [])
        validate_comparable_models(models)
        result = {"models": [evaluate_model(model) for model in models]}
        if not result["models"]:
            raise ValueError("manifest has no models")
        rendered = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
        if args.output:
            args.output.write_text(rendered, encoding="utf-8")
        else:
            sys.stdout.write(rendered)
        return 0
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"asr-evaluate: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
