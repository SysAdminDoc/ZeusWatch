#!/usr/bin/env python3
"""Unit tests for check_apk_alignment.py."""

from __future__ import annotations

import importlib.util
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("check_apk_alignment.py")
SPEC = importlib.util.spec_from_file_location("check_apk_alignment", MODULE_PATH)
alignment = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = alignment
SPEC.loader.exec_module(alignment)


class ApkAlignmentTests(unittest.TestCase):
    def test_discover_apks_sorts_and_deduplicates(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            first = root / "b.apk"
            second = root / "a.apk"
            ignored = root / "notes.txt"
            first.write_text("", encoding="utf-8")
            second.write_text("", encoding="utf-8")
            ignored.write_text("", encoding="utf-8")

            apks = alignment.discover_apks([str(root / "*.apk"), str(first)])

            self.assertEqual(apks, sorted({first.resolve(), second.resolve()}))

    def test_discover_apks_reports_missing_patterns(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            missing_patterns: list[str] = []

            apks = alignment.discover_apks([str(Path(tmp) / "*.apk")], missing_patterns=missing_patterns)

            self.assertEqual(apks, [])
            self.assertEqual(missing_patterns, [str(Path(tmp) / "*.apk")])

    def test_resolve_zipalign_prefers_latest_android_build_tools(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            sdk_root = Path(tmp)
            old_zipalign = sdk_root / "build-tools" / "35.0.0" / _zipalign_name()
            new_zipalign = sdk_root / "build-tools" / "36.0.0" / _zipalign_name()
            old_zipalign.parent.mkdir(parents=True)
            new_zipalign.parent.mkdir(parents=True)
            old_zipalign.write_text("", encoding="utf-8")
            new_zipalign.write_text("", encoding="utf-8")

            resolved = alignment.resolve_zipalign(env={"ANDROID_HOME": str(sdk_root), "PATH": os.environ.get("PATH", "")})

            self.assertEqual(resolved, new_zipalign)

    def test_check_alignment_invokes_recommended_zipalign_args(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / "app.apk"
            zipalign = Path(tmp) / _zipalign_name()
            apk.write_text("", encoding="utf-8")
            zipalign.write_text("", encoding="utf-8")
            calls: list[list[str]] = []

            def runner(command, text, capture_output):  # noqa: ANN001
                self.assertTrue(text)
                self.assertTrue(capture_output)
                calls.append(command)
                return subprocess.CompletedProcess(command, 0, stdout="Verification successful", stderr="")

            result = alignment.check_alignment([apk], zipalign, runner=runner)

            self.assertTrue(result[0].ok)
            self.assertEqual(calls[0], [str(zipalign), "-c", "-P", "16", "-v", "4", str(apk)])


def _zipalign_name() -> str:
    return "zipalign.exe" if os.name == "nt" else "zipalign"


if __name__ == "__main__":
    unittest.main()
