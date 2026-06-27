#!/usr/bin/env python3
"""Check for stale documentation references, forbidden planning files,
release-process truth, and version/privacy metadata drift.

Flags:
- References to COMPLETED.md, TODO.md, PROJECT_CONTEXT.md in non-gitignored markdown
- Forbidden planning filenames in the repo root
- README privacy wording that references the pre-auth device ID model
- Workflow-only release claims when no checked-in workflow files exist

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

WORKFLOW_TRUTH_FILES = [
    REPO_ROOT / "README.md",
    REPO_ROOT / "docs" / "RELEASE.md",
    REPO_ROOT / "ROADMAP.md",
]

WORKFLOW_CLAIM_PATTERNS = [
    re.compile(r"https://github\.com/[^)\s]+/actions/workflows/[^)\s]+", re.IGNORECASE),
    re.compile(r"\bGitHub Actions\b", re.IGNORECASE),
    re.compile(r"\bDependabot\b", re.IGNORECASE),
    re.compile(r"\brelease\.yml\b", re.IGNORECASE),
    re.compile(r"\bbuild\.yml\b", re.IGNORECASE),
    re.compile(r"\bgh\s+attestation\s+verify\b", re.IGNORECASE),
    re.compile(r"\bartifact attestations?\b", re.IGNORECASE),
    re.compile(r"\bdependency SBOM\b", re.IGNORECASE),
    re.compile(r"\bCI\b", re.IGNORECASE),
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


def has_checked_in_workflows():
    workflow_dir = REPO_ROOT / ".github" / "workflows"
    if not workflow_dir.exists():
        return False
    return any(path.is_file() for path in workflow_dir.glob("*.yml")) or any(
        path.is_file() for path in workflow_dir.glob("*.yaml")
    )


def check_release_workflow_truth():
    if has_checked_in_workflows():
        return []

    issues = []
    for path in WORKFLOW_TRUTH_FILES:
        content = read_text(path)
        if content is None:
            continue
        rel = path.relative_to(REPO_ROOT)
        for pattern in WORKFLOW_CLAIM_PATTERNS:
            for match in pattern.finditer(content):
                line = content.count("\n", 0, match.start()) + 1
                issues.append(
                    f"{rel}:{line}: workflow-only release claim '{match.group()}' "
                    "but no .github/workflows/*.yml files exist"
                )
    return issues


def read_text(path):
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return None


def parse_gradle_string(gradle_path, key):
    content = read_text(gradle_path)
    if content is None:
        return None
    match = re.search(rf'{re.escape(key)}\s*=\s*"([^"]+)"', content)
    return match.group(1) if match else None


def parse_gradle_int(gradle_path, key):
    content = read_text(gradle_path)
    if content is None:
        return None
    match = re.search(rf"{re.escape(key)}\s*=\s*(\d+)", content)
    return int(match.group(1)) if match else None


def parse_card_type_count():
    content = read_text(
        REPO_ROOT / "app" / "src" / "main" / "java" / "com" /
        "sysadmindoc" / "nimbus" / "data" / "repository" / "CardConfig.kt"
    )
    if content is None:
        return None
    match = re.search(
        r"enum\s+class\s+CardType\([^)]*\)\s*\{(?P<body>.*?)\n\}",
        content,
        re.DOTALL,
    )
    if not match:
        return None
    return len(re.findall(r"^\s*[A-Z][A-Z0-9_]*\(", match.group("body"), re.MULTILINE))


def parse_widget_receiver_count():
    content = read_text(REPO_ROOT / "app" / "src" / "main" / "AndroidManifest.xml")
    if content is None:
        return None
    receivers = re.findall(
        r'android:name="\.widget\.Nimbus[A-Za-z0-9]+WidgetReceiver"',
        content,
    )
    return len(set(receivers))


def current_readme_content(content):
    # The historical "Implemented through" section can mention old counts.
    return content.split("**Implemented through", 1)[0]


def check_readme_inventory():
    issues = []
    readme = read_text(REPO_ROOT / "README.md")
    if not readme:
        return issues

    current_content = current_readme_content(readme)
    card_count = parse_card_type_count()
    widget_count = parse_widget_receiver_count()

    if card_count is not None:
        card_patterns = [
            (r"premium dark UI,\s*(\d+)\s+customizable cards", "hero card count"),
            (r"Toggle each of the\s*(\d+)\s+card types", "settings card visibility count"),
            (r"(\d+)\s+Card Types via LazyColumn", "architecture card count"),
            (
                r"\*\*Cards\*\*\s*\|\s*Toggle \+ reorder each of\s*(\d+)\s+card types",
                "settings table card count",
            ),
            (r"### Card Types \((\d+)\)", "card types heading count"),
        ]
        for pattern, label in card_patterns:
            for match in re.finditer(pattern, current_content, re.IGNORECASE):
                found = int(match.group(1))
                if found != card_count:
                    issues.append(
                        f"README.md: {label} {found} != CardType count {card_count}"
                    )

    if widget_count is not None:
        for match in re.finditer(
            r"#\s*(\d+)\s+Glance home screen widgets",
            current_content,
            re.IGNORECASE,
        ):
            found = int(match.group(1))
            if found != widget_count:
                issues.append(
                    f"README.md: widget source-tree count {found} != "
                    f"manifest receiver count {widget_count}"
                )

        widget_section = re.search(
            r"### Widgets \(Jetpack Glance\)(?P<section>.*?)(?:\n### |\n---)",
            current_content,
            re.DOTALL,
        )
        if widget_section:
            rows = re.findall(
                r"^\|\s*\*\*[^|]+\*\*\s*\|",
                widget_section.group("section"),
                re.MULTILINE,
            )
            if len(rows) != widget_count:
                issues.append(
                    f"README.md: widget table has {len(rows)} rows != "
                    f"manifest receiver count {widget_count}"
                )
        else:
            issues.append("README.md: missing Widgets section")

    return issues


def check_version_sync():
    issues = []
    app_gradle = REPO_ROOT / "app" / "build.gradle.kts"
    wear_gradle = REPO_ROOT / "wear" / "build.gradle.kts"

    app_ver = parse_gradle_string(app_gradle, "versionName")
    wear_ver = parse_gradle_string(wear_gradle, "versionName")
    app_code = parse_gradle_int(app_gradle, "versionCode")
    wear_code = parse_gradle_int(wear_gradle, "versionCode")

    if app_ver and wear_ver and app_ver != wear_ver:
        issues.append(
            f"Version mismatch: app={app_ver}, wear={wear_ver}"
        )

    readme = read_text(REPO_ROOT / "README.md")
    if readme and app_ver:
        match = re.search(r"badge/version-([0-9A-Za-z.\-]+)-", readme)
        if not match:
            issues.append("README.md: missing version badge")
        elif match.group(1) != app_ver:
            issues.append(
                f"README.md: version badge {match.group(1)} != app version {app_ver}"
            )

    roadmap = read_text(REPO_ROOT / "ROADMAP.md")
    if roadmap and app_ver and app_code is not None and wear_code is not None:
        match = re.search(
            r"\*\*Current Version\*\*:\s*v?([0-9A-Za-z.\-]+)\s*"
            r"\(phone versionCode\s*(\d+),\s*wear versionCode\s*(\d+)\)",
            roadmap,
        )
        if not match:
            issues.append("ROADMAP.md: missing current version header")
        else:
            version, phone_code, watch_code = match.groups()
            if version != app_ver or int(phone_code) != app_code or int(watch_code) != wear_code:
                issues.append(
                    "ROADMAP.md: current version header "
                    f"v{version} ({phone_code}/{watch_code}) != "
                    f"v{app_ver} ({app_code}/{wear_code})"
                )

    claude = read_text(REPO_ROOT / "CLAUDE.md")
    if claude and app_ver and app_code is not None and wear_code is not None:
        match = re.search(
            r"\*\*Version\*\*:\s*v?([0-9A-Za-z.\-]+)\s*"
            r"\(phone versionCode\s*(\d+),\s*wear versionCode\s*(\d+)\)",
            claude,
        )
        if match:
            version, phone_code, watch_code = match.groups()
            if version != app_ver or int(phone_code) != app_code or int(watch_code) != wear_code:
                issues.append(
                    "CLAUDE.md: version header "
                    f"v{version} ({phone_code}/{watch_code}) != "
                    f"v{app_ver} ({app_code}/{wear_code})"
                )

    return issues


def main():
    issues = []
    issues.extend(check_forbidden_files())
    issues.extend(check_stale_references())
    issues.extend(check_readme_privacy())
    issues.extend(check_release_workflow_truth())
    issues.extend(check_version_sync())
    issues.extend(check_readme_inventory())

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
