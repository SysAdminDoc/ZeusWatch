#!/usr/bin/env python3
"""Copy the release build outputs into one directory under contracted names.

Release assets used to be uploaded straight from the AGP output directories,
so their names were whatever the build produced and whatever the person
uploading renamed them to. They drifted between releases, which breaks the
filename patterns Obtainium and IzzyOnDroid users configure once and expect to
keep working.

The contract is:

    ZeusWatch-v{version}-{flavor}-{abi}.apk
    ZeusWatch-v{version}-wear.apk

Nothing else is uploadable. Exit 0 when every expected artifact was staged,
1 when one is missing or the source directory holds something unrecognised.
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# AGP output name -> the part of the contracted name that identifies the build.
PHONE_OUTPUT = re.compile(r"^app-(?P<flavor>standard|freenet)-(?P<abi>[a-z0-9-]+)-release\.apk$")
WEAR_OUTPUT = re.compile(r"^wear-release\.apk$")

PHONE_DIRS = (
    ROOT / "app" / "build" / "outputs" / "apk" / "standard" / "release",
    ROOT / "app" / "build" / "outputs" / "apk" / "freenet" / "release",
)
WEAR_DIR = ROOT / "wear" / "build" / "outputs" / "apk" / "release"
DEFAULT_STAGING = ROOT / "build" / "release-assets"

EXPECTED_PHONE_BUILDS = {
    ("standard", "arm64-v8a"),
    ("standard", "armeabi-v7a"),
    ("standard", "universal"),
    ("freenet", "arm64-v8a"),
    ("freenet", "armeabi-v7a"),
    ("freenet", "universal"),
}


def read_version(build_file: Path = ROOT / "app" / "build.gradle.kts") -> str:
    match = re.search(r'versionName\s*=\s*"([^"]+)"', build_file.read_text(encoding="utf-8"))
    if not match:
        raise ValueError(f"versionName not found in {build_file}")
    return match.group(1)


def asset_name(version: str, flavor: str, abi: str) -> str:
    return f"ZeusWatch-v{version}-{flavor}-{abi}.apk"


def wear_asset_name(version: str) -> str:
    return f"ZeusWatch-v{version}-wear.apk"


def plan(version: str) -> tuple[list[tuple[Path, str]], list[str]]:
    """Pairs of (source apk, contracted name), plus any problems found."""
    staged: list[tuple[Path, str]] = []
    problems: list[str] = []
    seen_builds: set[tuple[str, str]] = set()

    for directory in PHONE_DIRS:
        if not directory.is_dir():
            problems.append(f"missing build output directory: {directory}")
            continue
        for apk in sorted(directory.glob("*.apk")):
            match = PHONE_OUTPUT.match(apk.name)
            if not match:
                # An unsigned or leftover APK beside the real one is exactly
                # how the wrong file gets published.
                problems.append(f"unrecognised APK in {directory.name}: {apk.name}")
                continue
            flavor, abi = match.group("flavor"), match.group("abi")
            seen_builds.add((flavor, abi))
            staged.append((apk, asset_name(version, flavor, abi)))

    if WEAR_DIR.is_dir():
        wear_apks = [apk for apk in sorted(WEAR_DIR.glob("*.apk")) if WEAR_OUTPUT.match(apk.name)]
        if not wear_apks:
            problems.append(f"no wear release APK in {WEAR_DIR}")
        else:
            staged.append((wear_apks[0], wear_asset_name(version)))
    else:
        problems.append(f"missing build output directory: {WEAR_DIR}")

    for missing in sorted(EXPECTED_PHONE_BUILDS - seen_builds):
        problems.append(f"missing phone build: {missing[0]} {missing[1]}")

    return staged, problems


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", help="override the version read from app/build.gradle.kts")
    parser.add_argument("--staging-dir", type=Path, default=DEFAULT_STAGING)
    parser.add_argument("--dry-run", action="store_true", help="print the plan without copying")
    args = parser.parse_args(argv or sys.argv[1:])

    version = args.version or read_version()
    staged, problems = plan(version)
    if problems:
        print(f"Release asset staging found {len(problems)} problem(s):")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    if args.dry_run:
        for source, name in staged:
            print(f"{source.name} -> {name}")
        return 0

    # Cleared, not merged: a stale APK from the previous version sitting in the
    # staging directory would be uploaded alongside the current one.
    if args.staging_dir.exists():
        shutil.rmtree(args.staging_dir)
    args.staging_dir.mkdir(parents=True)
    for source, name in staged:
        shutil.copy2(source, args.staging_dir / name)

    print(f"Staged {len(staged)} release assets in {args.staging_dir}:")
    for _, name in staged:
        print(f"  {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
