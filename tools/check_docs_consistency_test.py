#!/usr/bin/env python3
"""Unit tests for check_docs_consistency.py."""

from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("check_docs_consistency.py")
SPEC = importlib.util.spec_from_file_location("check_docs_consistency", MODULE_PATH)
docs = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = docs
SPEC.loader.exec_module(docs)


class DocsConsistencyTests(unittest.TestCase):
    def test_workflow_claims_fail_when_no_workflows_exist(self) -> None:
        with _temporary_docs_repo(
            {
                "README.md": "![Build](https://github.com/owner/repo/actions/workflows/build.yml/badge.svg)",
                "docs/RELEASE.md": "release.yml emits artifact attestations",
                "ROADMAP.md": "CI fails on new release regressions",
            }
        ):
            issues = docs.check_release_workflow_truth()

        self.assertGreaterEqual(len(issues), 3)
        self.assertTrue(all("no .github/workflows/*.yml files exist" in issue for issue in issues))

    def test_workflow_claims_are_allowed_when_workflows_exist(self) -> None:
        with _temporary_docs_repo(
            {
                "README.md": "![Build](https://github.com/owner/repo/actions/workflows/build.yml/badge.svg)",
                "docs/RELEASE.md": "release.yml emits artifact attestations",
                "ROADMAP.md": "CI fails on new release regressions",
                ".github/workflows/build.yml": "name: local-test-fixture\n",
            }
        ):
            self.assertEqual(docs.check_release_workflow_truth(), [])

    def test_workflow_truth_check_uses_word_boundaries_for_ci(self) -> None:
        with _temporary_docs_repo(
            {
                "README.md": "Precipitation forecast and F-Droid certification notes.",
                "docs/RELEASE.md": "Application signing is verified locally.",
                "ROADMAP.md": "Accessibility and specification work remains.",
            }
        ):
            self.assertEqual(docs.check_release_workflow_truth(), [])


class _temporary_docs_repo:
    def __init__(self, files: dict[str, str]) -> None:
        self.files = files
        self.temp_dir: tempfile.TemporaryDirectory[str] | None = None
        self.old_root = docs.REPO_ROOT
        self.old_files = docs.WORKFLOW_TRUTH_FILES

    def __enter__(self) -> Path:
        self.temp_dir = tempfile.TemporaryDirectory()
        root = Path(self.temp_dir.name)
        for relative, content in self.files.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        docs.REPO_ROOT = root
        docs.WORKFLOW_TRUTH_FILES = [
            root / "README.md",
            root / "docs" / "RELEASE.md",
            root / "ROADMAP.md",
        ]
        return root

    def __exit__(self, exc_type, exc, tb) -> None:  # noqa: ANN001
        docs.REPO_ROOT = self.old_root
        docs.WORKFLOW_TRUTH_FILES = self.old_files
        assert self.temp_dir is not None
        self.temp_dir.cleanup()


if __name__ == "__main__":
    unittest.main()
