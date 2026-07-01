#!/usr/bin/env python3
"""Generate a machine-readable provenance manifest for local APK releases."""

from __future__ import annotations

import argparse
import glob
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Iterable, Sequence

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SHA256SUMS = ROOT / "SHA256SUMS.txt"
DEFAULT_APK_PATTERNS = (
    str(ROOT / "app" / "build" / "outputs" / "apk" / "standard" / "release" / "*.apk"),
    str(ROOT / "app" / "build" / "outputs" / "apk" / "freenet" / "release" / "*.apk"),
    str(ROOT / "wear" / "build" / "outputs" / "apk" / "release" / "*.apk"),
)
RELEASE_VERIFICATION_COMMANDS = (
    r".\gradlew.bat clean assembleStandardRelease assembleFreenetRelease :wear:assembleRelease",
    r"py -3 tools\check_provider_contracts.py",
    r"Get-FileHash .\app\build\outputs\apk\standard\release\*.apk, .\app\build\outputs\apk\freenet\release\*.apk, .\wear\build\outputs\apk\release\*.apk -Algorithm SHA256",
    r"apksigner verify --verbose --print-certs .\app\build\outputs\apk\standard\release\*.apk",
    r"apksigner verify --verbose --print-certs .\app\build\outputs\apk\freenet\release\*.apk",
    r"apksigner verify --verbose --print-certs .\wear\build\outputs\apk\release\*.apk",
    r"py -3 tools\check_apk_alignment.py .\app\build\outputs\apk\standard\release\*.apk .\app\build\outputs\apk\freenet\release\*.apk .\wear\build\outputs\apk\release\*.apk",
    r"sha256sum -c SHA256SUMS.txt",
    r"py -3 tools\generate_release_provenance.py",
)


Runner = Callable[..., subprocess.CompletedProcess[str]]


@dataclass(frozen=True)
class Artifact:
    path: Path
    file_name: str
    size_bytes: int
    sha256: str
    signing_certificate_sha256: str

    def to_report(self, root: Path) -> dict[str, object]:
        return {
            "fileName": self.file_name,
            "path": _relative_or_absolute(self.path, root),
            "sizeBytes": self.size_bytes,
            "sha256": self.sha256,
            "signingCertificateSha256": self.signing_certificate_sha256,
        }


def discover_apks(patterns: Sequence[str]) -> list[Path]:
    apks: dict[Path, None] = {}
    for pattern in patterns:
        for match in glob.glob(pattern, recursive=True):
            path = Path(match)
            if path.is_file() and path.suffix.lower() == ".apk":
                apks[path.resolve()] = None
    return sorted(apks)


def read_version(build_file: Path = ROOT / "app" / "build.gradle.kts") -> str:
    match = re.search(r'^\s*versionName\s*=\s*"([^"]+)"', build_file.read_text(encoding="utf-8"), re.MULTILINE)
    if not match:
        raise ValueError(f"versionName not found in {build_file}")
    return match.group(1)


def read_sha256sums(path: Path) -> dict[str, str]:
    if not path.is_file():
        raise FileNotFoundError(f"checksum file not found: {path}")
    checksums: dict[str, str] = {}
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) < 2:
            raise ValueError(f"invalid checksum line {line_number}: {raw_line}")
        digest, file_name = parts[0].lower(), parts[-1].lstrip("*")
        if not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise ValueError(f"invalid SHA-256 digest on line {line_number}: {digest}")
        checksums[file_name] = digest
    return checksums


def hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def resolve_apksigner(explicit: str | None = None, env: dict[str, str] | None = None) -> Path:
    provided_env = env is not None
    env = env or os.environ
    candidates: list[Path] = []
    if explicit:
        candidates.append(Path(explicit))
    apksigner_env = env.get("APKSIGNER")
    if apksigner_env:
        candidates.append(Path(apksigner_env))
    for name in ("apksigner", "apksigner.bat", "apksigner.exe"):
        found = shutil.which(name)
        if found:
            candidates.append(Path(found))
    for root_name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk_root = env.get(root_name)
        if sdk_root:
            candidates.extend(_build_tools_apksigners(Path(sdk_root)))
    if not provided_env:
        for sdk_root in _common_sdk_roots():
            candidates.extend(_build_tools_apksigners(sdk_root))

    existing = [candidate for candidate in candidates if candidate.is_file()]
    if existing:
        return _latest_build_tool(existing)
    searched = ", ".join(str(candidate) for candidate in candidates) or "PATH, ANDROID_HOME, ANDROID_SDK_ROOT"
    raise FileNotFoundError(f"apksigner not found; searched {searched}")


def signing_cert_sha256(apk: Path, apksigner: Path, runner: Runner = subprocess.run) -> str:
    command = [str(apksigner), "verify", "--verbose", "--print-certs", str(apk)]
    completed = runner(command, text=True, capture_output=True)
    output = f"{completed.stdout or ''}\n{completed.stderr or ''}"
    if completed.returncode != 0:
        raise RuntimeError(f"apksigner verification failed for {apk}: {output.strip()}")
    match = re.search(r"certificate SHA-256 digest:\s*([0-9A-Fa-f:]+)", output)
    if not match:
        raise ValueError(f"apksigner output did not include a certificate SHA-256 digest for {apk}")
    return _normalize_cert_digest(match.group(1))


def collect_git_state(root: Path = ROOT, runner: Runner = subprocess.run) -> dict[str, object]:
    commit = _run_text(["git", "rev-parse", "HEAD"], root, runner)
    branch = _run_text(["git", "branch", "--show-current"], root, runner)
    status = _run_text(["git", "status", "--porcelain", "--untracked-files=no"], root, runner, check=False)
    dirty_entries = [line for line in status.splitlines() if line.strip()]
    return {
        "commit": commit.strip(),
        "branch": branch.strip(),
        "dirty": bool(dirty_entries),
        "dirtyEntries": dirty_entries,
    }


def collect_toolchain(root: Path = ROOT, runner: Runner = subprocess.run, env: dict[str, str] | None = None) -> dict[str, object]:
    env = env or os.environ
    gradlew = root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    gradle = _run_text([str(gradlew), "--version"], root, runner, check=False)
    java = _run_text(["java", "-version"], root, runner, check=False)
    sdk_root = env.get("ANDROID_HOME") or env.get("ANDROID_SDK_ROOT") or ""
    build_tools = _installed_build_tools(Path(sdk_root)) if sdk_root else []
    return {
        "gradle": _first_matching_line(gradle, "Gradle ") or gradle.splitlines()[:1],
        "java": java.splitlines()[:3],
        "androidSdk": sdk_root,
        "androidBuildTools": build_tools,
    }


def build_provenance(
    *,
    apks: Sequence[Path],
    sha256sums: Path,
    version: str,
    apksigner: Path,
    root: Path = ROOT,
    runner: Runner = subprocess.run,
    env: dict[str, str] | None = None,
) -> dict[str, object]:
    if not apks:
        raise ValueError("no APKs found for provenance")
    expected_hashes = read_sha256sums(sha256sums)
    artifacts: list[Artifact] = []
    missing_from_sums: list[str] = []
    mismatches: list[str] = []
    for apk in apks:
        digest = hash_file(apk)
        expected = expected_hashes.get(apk.name)
        if expected is None:
            missing_from_sums.append(apk.name)
        elif expected != digest:
            mismatches.append(f"{apk.name}: expected {expected}, actual {digest}")
        artifacts.append(
            Artifact(
                path=apk,
                file_name=apk.name,
                size_bytes=apk.stat().st_size,
                sha256=digest,
                signing_certificate_sha256=signing_cert_sha256(apk, apksigner, runner),
            )
        )
    if missing_from_sums:
        raise ValueError("APK missing from SHA256SUMS.txt: " + ", ".join(missing_from_sums))
    if mismatches:
        raise ValueError("SHA256SUMS.txt mismatch: " + "; ".join(mismatches))

    return {
        "schemaVersion": 1,
        "project": "ZeusWatch",
        "version": version,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "source": collect_git_state(root, runner),
        "toolchain": collect_toolchain(root, runner, env),
        "sha256sums": {
            "path": _relative_or_absolute(sha256sums, root),
            "verified": True,
        },
        "artifacts": [artifact.to_report(root) for artifact in artifacts],
        "commands": {
            "generator": " ".join(sys.argv),
            "releaseVerification": list(RELEASE_VERIFICATION_COMMANDS),
        },
    }


def write_manifest(path: Path, data: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", action="append", dest="apk_patterns", help="APK path or glob. May be repeated.")
    parser.add_argument("--sha256sums", type=Path, default=DEFAULT_SHA256SUMS)
    parser.add_argument("--output", type=Path, help="Output JSON path. Defaults beside SHA256SUMS.txt.")
    parser.add_argument("--version", help="Release version. Defaults to app/build.gradle.kts versionName.")
    parser.add_argument("--apksigner", help="Explicit path to Android SDK apksigner.")
    parser.add_argument("--allow-dirty", action="store_true", help="Emit provenance even when tracked files are dirty.")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    version = args.version or read_version()
    output = args.output or args.sha256sums.parent / f"ZeusWatch-v{version}-provenance.json"
    apks = discover_apks(args.apk_patterns or DEFAULT_APK_PATTERNS)
    try:
        apksigner = resolve_apksigner(args.apksigner)
        data = build_provenance(
            apks=apks,
            sha256sums=args.sha256sums,
            version=version,
            apksigner=apksigner,
        )
        if data["source"]["dirty"] and not args.allow_dirty:
            dirty = "\n".join(f"  {entry}" for entry in data["source"]["dirtyEntries"])
            print("ERROR tracked source tree is dirty; commit or revert before release provenance generation.")
            print(dirty)
            return 1
        write_manifest(output, data)
    except (FileNotFoundError, RuntimeError, ValueError) as exc:
        print(f"ERROR {exc}")
        return 1
    print(f"Wrote release provenance: {output}")
    return 0


def _run_text(
    command: Sequence[str],
    cwd: Path,
    runner: Runner,
    *,
    check: bool = True,
) -> str:
    try:
        completed = runner(list(command), cwd=str(cwd), text=True, capture_output=True)
    except OSError as exc:
        if check:
            raise
        return str(exc)
    output = "\n".join(part for part in (completed.stdout, completed.stderr) if part)
    if check and completed.returncode != 0:
        raise RuntimeError(f"{' '.join(command)} failed: {output.strip()}")
    return output


def _build_tools_apksigners(sdk_root: Path) -> list[Path]:
    build_tools = sdk_root / "build-tools"
    if not build_tools.exists():
        return []
    return [
        path
        for path in build_tools.glob("*/apksigner*")
        if path.name in {"apksigner", "apksigner.bat", "apksigner.exe"}
    ]


def _common_sdk_roots() -> list[Path]:
    home = Path.home()
    return [
        home / "AppData" / "Local" / "Android" / "Sdk",
        home / "Library" / "Android" / "sdk",
        home / "Android" / "Sdk",
    ]


def _installed_build_tools(sdk_root: Path) -> list[str]:
    build_tools = sdk_root / "build-tools"
    if not build_tools.exists():
        return []
    return sorted(path.name for path in build_tools.iterdir() if path.is_dir())


def _latest_build_tool(paths: Iterable[Path]) -> Path:
    return sorted(paths, key=_version_key)[-1]


def _version_key(path: Path) -> tuple[int, ...]:
    version = path.parent.name
    parts: list[int] = []
    for chunk in version.replace("-", ".").split("."):
        parts.append(int(chunk) if chunk.isdigit() else 0)
    return tuple(parts)


def _normalize_cert_digest(value: str) -> str:
    compact = re.sub(r"[^0-9A-Fa-f]", "", value).upper()
    if len(compact) != 64:
        raise ValueError(f"invalid certificate SHA-256 digest: {value}")
    return ":".join(compact[index : index + 2] for index in range(0, 64, 2))


def _relative_or_absolute(path: Path, root: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(root.resolve()).as_posix()
    except ValueError:
        return str(resolved)


def _first_matching_line(text: str, prefix: str) -> str | None:
    return next((line.strip() for line in text.splitlines() if line.strip().startswith(prefix)), None)


if __name__ == "__main__":
    sys.exit(main())
