#!/usr/bin/env python3
"""Check for stale documentation references, forbidden planning files,
and version/privacy metadata drift.

Flags:
- References to COMPLETED.md, TODO.md, PROJECT_CONTEXT.md in non-gitignored markdown
- Forbidden planning filenames in the repo root
- README privacy wording that references the pre-auth device ID model

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

STALE_PRIVACY_PATTERNS = [
    re.compile(r"hashed\s+anonymous\s+device\s+[Ii][Dd]", re.IGNORECASE),
    re.compile(r"ANDROID_ID", re.IGNORECASE),
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


def check_readme_privacy():
    issues = []
    readme = REPO_ROOT / "README.md"
    if not readme.exists():
        return issues
    try:
        content = readme.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return issues
    for pattern in STALE_PRIVACY_PATTERNS:
        for match in pattern.finditer(content):
            issues.append(
                f"README.md: stale privacy wording '{match.group()}' "
                "(community reports now use anonymous Firebase Auth UID, not device ID)"
            )
    return issues


def parse_gradle_version(gradle_path):
    try:
        content = gradle_path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return None
    match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    return match.group(1) if match else None


def check_version_sync():
    issues = []
    app_gradle = REPO_ROOT / "app" / "build.gradle.kts"
    wear_gradle = REPO_ROOT / "wear" / "build.gradle.kts"

    app_ver = parse_gradle_version(app_gradle)
    wear_ver = parse_gradle_version(wear_gradle)

    if app_ver and wear_ver and app_ver != wear_ver:
        issues.append(
            f"Version mismatch: app={app_ver}, wear={wear_ver}"
        )

    return issues


def main():
    issues = []
    issues.extend(check_forbidden_files())
    issues.extend(check_stale_references())
    issues.extend(check_readme_privacy())
    issues.extend(check_version_sync())

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
