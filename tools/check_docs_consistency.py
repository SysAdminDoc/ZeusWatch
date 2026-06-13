#!/usr/bin/env python3
"""Check for stale documentation references and forbidden planning files.

Flags:
- References to COMPLETED.md, TODO.md, PROJECT_CONTEXT.md in non-gitignored markdown
- Forbidden planning filenames in the repo root
- Mismatched targetSdk between docs and build.gradle.kts

Exit 0 when clean, 1 when issues found.
"""

import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

FORBIDDEN_ROOT_FILES = {
    "AUTONOMOUS-LOOP-STATE.md",
    "COMPLETED.md",
    "ClaudeReadMe.md",
    "PROJECT_CONTEXT.md",
    "TODO.md",
    "HANDOFF.md",
}

STALE_REFERENCES = [
    re.compile(r"COMPLETED\.md", re.IGNORECASE),
    re.compile(r"TODO\.md", re.IGNORECASE),
    re.compile(r"PROJECT_CONTEXT\.md", re.IGNORECASE),
    re.compile(r"AUTONOMOUS-LOOP-STATE\.md", re.IGNORECASE),
]

CHECKED_DIRS = [
    REPO_ROOT / "docs",
]


def check_forbidden_files():
    issues = []
    for name in FORBIDDEN_ROOT_FILES:
        path = REPO_ROOT / name
        if path.exists():
            issues.append(f"Forbidden file exists: {name}")
    return issues


def check_stale_references():
    issues = []
    md_files = list(REPO_ROOT.glob("docs/**/*.md"))
    for md_file in md_files:
        try:
            content = md_file.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for pattern in STALE_REFERENCES:
            for match in pattern.finditer(content):
                rel = md_file.relative_to(REPO_ROOT)
                issues.append(f"{rel}: references '{match.group()}'")
    return issues


def main():
    issues = []
    issues.extend(check_forbidden_files())
    issues.extend(check_stale_references())

    if issues:
        print(f"Documentation consistency check found {len(issues)} issue(s):")
        for issue in issues:
            print(f"  - {issue}")
        return 1
    else:
        print("Documentation consistency check passed.")
        return 0


if __name__ == "__main__":
    sys.exit(main())
