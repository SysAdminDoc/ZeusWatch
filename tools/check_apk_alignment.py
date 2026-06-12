#!/usr/bin/env python3
"""Verify APK 16 KB page-size alignment with Android SDK zipalign."""

from __future__ import annotations

import argparse
import glob
import os
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable, Sequence

ROOT = Path(__file__).resolve().parents[1]
ZIPALIGN_ARGS = ("-c", "-P", "16", "-v", "4")


@dataclass(frozen=True)
class AlignmentResult:
    apk: Path
    ok: bool
    command: list[str]
    stdout: str
    stderr: str
    return_code: int


Runner = Callable[..., subprocess.CompletedProcess[str]]


def discover_apks(patterns: Sequence[str], missing_patterns: list[str] | None = None) -> list[Path]:
    apks: dict[Path, None] = {}
    for pattern in patterns:
        matches = glob.glob(pattern, recursive=True)
        if not matches:
            if missing_patterns is not None:
                missing_patterns.append(pattern)
        for match in matches:
            path = Path(match)
            if path.is_file() and path.suffix.lower() == ".apk":
                apks[path.resolve()] = None
    return sorted(apks)


def resolve_zipalign(explicit: str | None = None, env: dict[str, str] | None = None) -> Path:
    provided_env = env is not None
    env = env or os.environ
    candidates: list[Path] = []
    if explicit:
        candidates.append(Path(explicit))
    zipalign_env = env.get("ZIPALIGN")
    if zipalign_env:
        candidates.append(Path(zipalign_env))
    for name in ("zipalign", "zipalign.exe"):
        found = shutil.which(name)
        if found:
            candidates.append(Path(found))
    for root_name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk_root = env.get(root_name)
        if sdk_root:
            candidates.extend(_build_tools_zipaligns(Path(sdk_root)))
    if not provided_env:
        for sdk_root in _common_sdk_roots():
            candidates.extend(_build_tools_zipaligns(sdk_root))

    existing = [candidate for candidate in candidates if candidate.is_file()]
    if existing:
        return _latest_zipalign(existing)
    searched = ", ".join(str(candidate) for candidate in candidates) or "PATH, ANDROID_HOME, ANDROID_SDK_ROOT"
    raise FileNotFoundError(f"zipalign not found; searched {searched}")


def check_alignment(apks: Sequence[Path], zipalign: Path, runner: Runner = subprocess.run) -> list[AlignmentResult]:
    results: list[AlignmentResult] = []
    for apk in apks:
        command = [str(zipalign), *ZIPALIGN_ARGS, str(apk)]
        completed = runner(command, text=True, capture_output=True)
        results.append(
            AlignmentResult(
                apk=apk,
                ok=completed.returncode == 0,
                command=command,
                stdout=completed.stdout or "",
                stderr=completed.stderr or "",
                return_code=completed.returncode,
            )
        )
    return results


def print_results(results: Sequence[AlignmentResult]) -> None:
    for result in results:
        label = "PASS" if result.ok else "FAIL"
        print(f"{label} {result.apk}")
        if not result.ok:
            print(f"  Command: {' '.join(result.command)}")
            if result.stdout.strip():
                print(_indent(result.stdout.rstrip()))
            if result.stderr.strip():
                print(_indent(result.stderr.rstrip()))


def _build_tools_zipaligns(sdk_root: Path) -> list[Path]:
    build_tools = sdk_root / "build-tools"
    if not build_tools.exists():
        return []
    return [
        path
        for path in build_tools.glob("*/zipalign*")
        if path.name in {"zipalign", "zipalign.exe"}
    ]


def _common_sdk_roots() -> list[Path]:
    home = Path.home()
    return [
        home / "AppData" / "Local" / "Android" / "Sdk",
        home / "Library" / "Android" / "sdk",
        home / "Android" / "Sdk",
    ]


def _latest_zipalign(paths: Iterable[Path]) -> Path:
    return sorted(paths, key=_version_key)[-1]


def _version_key(path: Path) -> tuple[int, ...]:
    version = path.parent.name
    parts: list[int] = []
    for chunk in version.replace("-", ".").split("."):
        if chunk.isdigit():
            parts.append(int(chunk))
        else:
            parts.append(0)
    return tuple(parts)


def _indent(text: str) -> str:
    return "\n".join(f"  {line}" for line in text.splitlines())


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run Android's recommended 16 KB page-size APK alignment check: "
            "zipalign -c -P 16 -v 4 APK_NAME.apk"
        )
    )
    parser.add_argument("patterns", nargs="+", help="APK path or glob pattern. Quote globs so Python expands them.")
    parser.add_argument("--zipalign", help="Explicit path to Android SDK zipalign.")
    parser.add_argument(
        "--allow-missing-patterns",
        action="store_true",
        help="Warn instead of failing when a supplied APK glob does not match any files.",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    missing_patterns: list[str] = []
    apks = discover_apks(args.patterns, missing_patterns=missing_patterns)
    if missing_patterns:
        for pattern in missing_patterns:
            print(f"WARN no APKs matched: {pattern}")
        if not args.allow_missing_patterns:
            print("ERROR one or more expected APK patterns did not match. Use --allow-missing-patterns for local ad hoc checks.")
            return 1
    if not apks:
        print("ERROR no APKs found for alignment verification.")
        return 1
    try:
        zipalign = resolve_zipalign(args.zipalign)
    except FileNotFoundError as exc:
        print(f"ERROR {exc}")
        return 1
    print(f"Using zipalign: {zipalign}")
    print(f"Checking {len(apks)} APK(s) with: zipalign {' '.join(ZIPALIGN_ARGS)}")
    results = check_alignment(apks, zipalign)
    print_results(results)
    failures = [result for result in results if not result.ok]
    if failures:
        print(f"{len(failures)} APK alignment check(s) failed.")
        return 1
    print("All APKs passed 16 KB zip alignment.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
