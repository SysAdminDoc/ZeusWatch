#!/usr/bin/env python3
"""Check documentation against live project manifests and catalogs.

Flags:
- References to COMPLETED.md, TODO.md, PROJECT_CONTEXT.md in non-gitignored markdown
- Forbidden planning filenames in the repo root
- README privacy wording that references the pre-auth device ID model
- Workflow-only release claims when no checked-in workflow files exist
- Dependency, Room schema, deep-link, location, and release-posture drift

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


def parse_version_catalog():
    content = read_text(REPO_ROOT / "gradle" / "libs.versions.toml")
    if content is None:
        return {}

    versions = {}
    in_versions = False
    for line in content.splitlines():
        stripped = line.strip()
        if stripped == "[versions]":
            in_versions = True
            continue
        if in_versions and stripped.startswith("["):
            break
        if not in_versions:
            continue
        match = re.match(r'([A-Za-z0-9_.-]+)\s*=\s*"([^"]+)"', stripped)
        if match:
            versions[match.group(1)] = match.group(2)
    return versions


def parse_firebase_bom_version():
    content = read_text(REPO_ROOT / "app" / "build.gradle.kts")
    if content is None:
        return None
    match = re.search(r'platform\("com\.google\.firebase:firebase-bom:([^"]+)"\)', content)
    return match.group(1) if match else None


def parse_room_schema_version():
    content = read_text(
        REPO_ROOT / "app" / "src" / "main" / "java" / "com" /
        "sysadmindoc" / "nimbus" / "data" / "api" / "NimbusDatabase.kt"
    )
    if content is None:
        return None
    match = re.search(r"@Database\s*\(.*?\bversion\s*=\s*(\d+)", content, re.DOTALL)
    return int(match.group(1)) if match else None


def parse_manifest_contract():
    content = read_text(REPO_ROOT / "app" / "src" / "main" / "AndroidManifest.xml")
    if content is None:
        return None
    schemes = set(re.findall(r'<data[^>]+android:scheme="([^"]+)"', content))
    permissions = set(re.findall(r'<uses-permission[^>]+android:name="([^"]+)"', content))
    return schemes, permissions


def parse_radar_providers():
    content = read_text(
        REPO_ROOT / "app" / "src" / "main" / "java" / "com" /
        "sysadmindoc" / "nimbus" / "data" / "repository" / "UserPreferences.kt"
    )
    if content is None:
        return None
    match = re.search(
        r"enum\s+class\s+RadarProvider\([^)]*\)\s*\{(?P<body>.*?)\n\}",
        content,
        re.DOTALL,
    )
    if not match:
        return None
    return [
        (entry.group(1), entry.group(2))
        for entry in re.finditer(
            r"^\s*([A-Z][A-Z0-9_]*)\(\s*label\s*=\s*\"([^\"]+)\"",
            match.group("body"),
            re.MULTILINE,
        )
    ]


def parse_radar_provider_string_branches(property_name):
    content = read_text(
        REPO_ROOT / "app" / "src" / "main" / "java" / "com" /
        "sysadmindoc" / "nimbus" / "util" / "SettingsEnumText.kt"
    )
    if content is None:
        return None
    match = re.search(
        rf"internal\s+val\s+RadarProvider\.{re.escape(property_name)}:\s*Int\s*"
        r"get\(\)\s*=\s*when\s*\(this\)\s*\{(?P<body>.*?)\n\s*\}",
        content,
        re.DOTALL,
    )
    if not match:
        return None
    return {
        provider: res_name
        for provider, res_name in re.findall(
            r"RadarProvider\.([A-Z0-9_]+)\s*->\s*R\.string\.([A-Za-z0-9_]+)",
            match.group("body"),
        )
    }


def parse_string_names(path):
    content = read_text(path)
    if content is None:
        return None
    return set(re.findall(r'<string\s+name="([^"]+)"', content))


def parse_string_value(path, name):
    content = read_text(path)
    if content is None:
        return None
    match = re.search(
        rf'<string\s+name="{re.escape(name)}">([^<]*)</string>',
        content,
    )
    return match.group(1) if match else None


def current_readme_content(content):
    # Remove only the historical inventory block; current Privacy/License
    # sections follow it and still belong to the authoritative README.
    return re.sub(
        r"\*\*Implemented through.*?\n---",
        "\n---",
        content,
        count=1,
        flags=re.DOTALL,
    )


def current_claude_content(content):
    return content.split("## Release History", 1)[0]


def check_room_schema_claims():
    issues = []
    schema_version = parse_room_schema_version()
    if schema_version is None:
        return issues

    claims = [
        (
            REPO_ROOT / "README.md",
            current_readme_content,
            r"Room Database \(v(\d+)\)",
            "Room Database architecture label",
        ),
        (
            REPO_ROOT / "CLAUDE.md",
            current_claude_content,
            r"\*\*Room DB\*\*:\s*v(\d+)\b",
            "Room DB architecture label",
        ),
    ]
    for path, current_content, pattern, label in claims:
        content = read_text(path)
        if content is None:
            continue
        match = re.search(pattern, current_content(content), re.IGNORECASE)
        rel = path.relative_to(REPO_ROOT)
        if not match:
            issues.append(f"{rel}: missing {label} derived from NimbusDatabase")
        elif int(match.group(1)) != schema_version:
            issues.append(
                f"{rel}: {label} v{match.group(1)} != NimbusDatabase v{schema_version}"
            )
    return issues


def check_manifest_contract_claims():
    issues = []
    contract = parse_manifest_contract()
    if contract is None:
        return issues
    schemes, permissions = contract

    current_docs = [
        (REPO_ROOT / "README.md", current_readme_content),
        (REPO_ROOT / "CLAUDE.md", current_claude_content),
    ]
    for path, current_content in current_docs:
        content = read_text(path)
        if content is None:
            continue
        current = current_content(content)
        rel = path.relative_to(REPO_ROOT)
        for scheme in sorted(schemes):
            scheme_label = f"{scheme}://"
            if scheme_label not in current:
                issues.append(f"{rel}: missing manifest deep-link scheme '{scheme_label}'")
        if "nimbus://" in current and "nimbus" not in schemes:
            issues.append(f"{rel}: stale deep-link scheme 'nimbus://' is not in AndroidManifest.xml")

    background_permission = "android.permission.ACCESS_BACKGROUND_LOCATION"
    has_background_location = background_permission in permissions
    readme = read_text(REPO_ROOT / "README.md")
    if readme:
        foreground_claim = re.search(
            r"foreground[- ]only location|no background location",
            current_readme_content(readme),
            re.IGNORECASE,
        )
        if has_background_location and foreground_claim:
            issues.append(
                "README.md: claims foreground-only location but manifest declares "
                "ACCESS_BACKGROUND_LOCATION"
            )
        elif not has_background_location and not foreground_claim:
            issues.append(
                "README.md: missing foreground-only location claim derived from AndroidManifest.xml"
            )
    return issues


def check_historical_supersession_labels():
    issues = []
    content = read_text(REPO_ROOT / "docs" / "phases-pre-v1.md")
    if content is None:
        return issues
    contains_superseded_claim = "nimbus://" in content or re.search(
        r"GitHub Actions|release\.yml|build\.yml", content, re.IGNORECASE
    )
    header = content[:700]
    if contains_superseded_claim and not (
        re.search(r"historical", header, re.IGNORECASE)
        and re.search(r"superseded", header, re.IGNORECASE)
    ):
        issues.append(
            "docs/phases-pre-v1.md: historical deep-link/workflow claims are not labeled superseded"
        )
    return issues


def check_local_release_posture():
    if has_checked_in_workflows():
        return []
    release_doc = read_text(REPO_ROOT / "docs" / "RELEASE.md")
    if release_doc is None:
        return ["docs/RELEASE.md: missing local release procedure"]
    required_patterns = [
        r"build signed release APKs locally",
        r"Verify artifacts locally",
        r"gh release upload",
    ]
    return [
        f"docs/RELEASE.md: missing local release posture '{pattern}'"
        for pattern in required_patterns
        if not re.search(pattern, release_doc, re.IGNORECASE)
    ]


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


def check_onboarding_card_count():
    issues = []
    card_count = parse_card_type_count()
    if card_count is None:
        return issues

    string_files = [
        (REPO_ROOT / "app" / "src" / "main" / "res" / "values" / "strings.xml", "values"),
        (REPO_ROOT / "app" / "src" / "main" / "res" / "values-es" / "strings.xml", "values-es"),
    ]
    for path, label in string_files:
        value = parse_string_value(path, "onboarding_cards_everything_count")
        if value is None:
            issues.append(f"{label}/strings.xml: missing onboarding_cards_everything_count")
            continue
        match = re.search(r"\b(\d+)\b", value)
        if not match:
            issues.append(
                f"{label}/strings.xml: onboarding_cards_everything_count has no numeric count"
            )
            continue
        found = int(match.group(1))
        if found != card_count:
            issues.append(
                f"{label}/strings.xml: onboarding everything count {found} != "
                f"CardType count {card_count}"
            )
    return issues


def check_radar_provider_inventory():
    issues = []
    providers = parse_radar_providers()
    if not providers:
        return issues

    provider_names = {name for name, _ in providers}
    readme = read_text(REPO_ROOT / "README.md")
    if readme:
        current_content = current_readme_content(readme)
        if len(providers) != 2 and re.search(r"\bDual Radar Provider\b", current_content):
            issues.append(
                "README.md: radar section still says Dual Radar Provider "
                f"but RadarProvider has {len(providers)} entries"
            )
        for _, label in providers:
            if label not in current_content:
                issues.append(f"README.md: missing radar provider label '{label}'")

    for property_name in ["labelRes", "summaryRes"]:
        branches = parse_radar_provider_string_branches(property_name)
        if branches is None:
            issues.append(f"SettingsEnumText.kt: missing RadarProvider.{property_name} mapping")
            continue
        branch_names = set(branches)
        for missing in sorted(provider_names - branch_names):
            issues.append(
                f"SettingsEnumText.kt: RadarProvider.{property_name} missing {missing}"
            )
        for extra in sorted(branch_names - provider_names):
            issues.append(
                f"SettingsEnumText.kt: RadarProvider.{property_name} has stale {extra}"
            )

        for values_dir in ["values", "values-es"]:
            string_names = parse_string_names(
                REPO_ROOT / "app" / "src" / "main" / "res" / values_dir / "strings.xml"
            )
            if string_names is None:
                continue
            for provider, res_name in sorted(branches.items()):
                if res_name not in string_names:
                    issues.append(
                        f"{values_dir}/strings.xml: missing {res_name} for "
                        f"RadarProvider.{provider}.{property_name}"
                    )
    return issues


def check_dependency_version_claims():
    issues = []
    versions = parse_version_catalog()
    firebase_bom = parse_firebase_bom_version()
    if firebase_bom:
        versions["firebase-bom"] = firebase_bom

    dependency_claims = [
        ("Kotlin", "kotlin"),
        ("Jetpack Compose", "compose-bom"),
        ("Hilt", "hilt"),
        ("Retrofit", "retrofit"),
        ("OkHttp", "okhttp"),
        ("Room", "room"),
        ("DataStore", "datastore"),
        ("MapLibre", "maplibre"),
        ("Glance", "glance"),
        ("WorkManager", "work"),
        ("Lottie", "lottie"),
        ("Coil", "coil"),
        ("Firebase Firestore", "firebase-bom"),
    ]

    for path in [REPO_ROOT / "README.md", REPO_ROOT / "CLAUDE.md"]:
        content = read_text(path)
        if content is None:
            continue
        rel = path.relative_to(REPO_ROOT)
        stack_lines = [
            line for line in content.splitlines()
            if re.match(r"\s*\*\*Stack(?::|\*\*:)", line)
        ]
        if not stack_lines:
            issues.append(f"{rel}: missing **Stack:** dependency line")
            continue
        stack_line = " ".join(stack_lines)
        for label_pattern, version_key in dependency_claims:
            version = versions.get(version_key)
            if version is None:
                continue
            pattern = rf"{label_pattern}[^,\n|]*\b{re.escape(version)}\b"
            if not re.search(pattern, stack_line):
                display = re.sub(r"\\|\(|\)|\?:", "", label_pattern)
                issues.append(
                    f"{rel}: stack claim for {display} does not match "
                    f"{version_key} {version}"
                )
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
        if not match:
            issues.append("CLAUDE.md: missing version header")
        else:
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
    issues.extend(check_local_release_posture())
    issues.extend(check_version_sync())
    issues.extend(check_readme_inventory())
    issues.extend(check_onboarding_card_count())
    issues.extend(check_radar_provider_inventory())
    issues.extend(check_dependency_version_claims())
    issues.extend(check_room_schema_claims())
    issues.extend(check_manifest_contract_claims())
    issues.extend(check_historical_supersession_labels())

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
