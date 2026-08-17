#!/usr/bin/env python3
"""Regression tests for tools/asr-evaluate.py.

These tests use synthetic non-song text. They validate the evaluator contract
only; they are not ASR quality fixtures and cannot be used to select a model.
"""

from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("asr-evaluate.py")
SPEC = importlib.util.spec_from_file_location("asr_evaluate", SCRIPT)
assert SPEC and SPEC.loader
asr_evaluate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(asr_evaluate)


def fixture(name: str, text: str, cues: list[dict[str, object]] | None = None) -> dict[str, object]:
    return {
        "name": name,
        "duration_ms": 30_000,
        "reference": text,
        "cues": cues
        if cues is not None
        else [{"start_ms": 0, "end_ms": 1000, "text": text}],
        "elapsed_ms": 100,
        "peak_rss_kb": 1000,
        "temperature_before_c": 25.0,
        "temperature_after_c": 26.0,
        "crashed": False,
        "demo_fallback": False,
    }


def model(name: str, references: list[str]) -> dict[str, object]:
    return {
        "name": name,
        "fixtures": [
            fixture("sample-a", references[0]),
            fixture("sample-b", references[1]),
            fixture("sample-c", references[2]),
        ],
    }


class AsrEvaluateTest(unittest.TestCase):
    def test_normalizes_curly_apostrophes(self) -> None:
        self.assertEqual("don't stop", asr_evaluate.normalize("Don’t Stop"))
        self.assertEqual("we're ready", asr_evaluate.normalize("We`re ready"))

    def test_rejects_unapproved_model(self) -> None:
        with self.assertRaisesRegex(ValueError, "unapproved model"):
            asr_evaluate.evaluate_model(model("ggml-tiny.en.bin", ["alpha", "beta", "gamma"]))

    def test_rejects_missing_accurate_reference(self) -> None:
        broken = model("ggml-base.bin", ["alpha", "", "gamma"])
        with self.assertRaisesRegex(ValueError, "no accurate reference"):
            asr_evaluate.evaluate_model(broken)

    def test_rejects_non_comparable_fixture_sets(self) -> None:
        base = model("ggml-base.bin", ["alpha", "beta", "gamma"])
        candidate = model("ggml-small.en-q5_1.bin", ["alpha", "changed beta", "gamma"])
        with self.assertRaisesRegex(ValueError, "not comparable"):
            asr_evaluate.validate_comparable_models([base, candidate])

    def test_evaluates_wer_cer_repetition_and_timestamps(self) -> None:
        payload = {
            "name": "ggml-base.bin",
            "fixtures": [
                fixture("sample-a", "alpha beta gamma"),
                fixture(
                    "sample-b",
                    "delta echo",
                    [{"start_ms": 0, "end_ms": 1000, "text": "delta delta"}],
                ),
                fixture(
                    "sample-c",
                    "foxtrot golf",
                    [{"start_ms": 2000, "end_ms": 1000, "text": "foxtrot"}],
                ),
            ],
        }

        result = asr_evaluate.evaluate_model(payload)

        self.assertEqual("ggml-base.bin", result["name"])
        self.assertEqual(1, result["aggregate"]["repeated_tokens"])
        self.assertEqual(1, result["aggregate"]["invalid_timestamps"])
        self.assertGreater(result["aggregate"]["wer"], 0)
        self.assertGreater(result["aggregate"]["cer"], 0)

    def test_cli_writes_json_output(self) -> None:
        payload = {
            "models": [
                model("ggml-base.bin", ["alpha beta", "gamma delta", "epsilon zeta"]),
                model("ggml-small.en-q5_1.bin", ["alpha beta", "gamma delta", "epsilon zeta"]),
            ]
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            manifest = temp / "manifest.json"
            output = temp / "result.json"
            manifest.write_text(json.dumps(payload), encoding="utf-8")

            completed = subprocess.run(
                [sys.executable, str(SCRIPT), str(manifest), "--output", str(output)],
                cwd=SCRIPT.parent.parent,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual("", completed.stderr)
            self.assertEqual(0, completed.returncode)
            rendered = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(2, len(rendered["models"]))


if __name__ == "__main__":
    unittest.main()
