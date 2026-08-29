#!/usr/bin/env python3
"""Unit tests for generate_release_provenance.py."""

from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("generate_release_provenance.py")
SPEC = importlib.util.spec_from_file_location("generate_release_provenance", MODULE_PATH)
provenance = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = provenance
SPEC.loader.exec_module(provenance)


CERT_DIGEST = "FB:03:10:AA:52:0F:6C:C6:EB:DA:04:61:71:9E:A9:22:40:EA:2B:4A:A1:D0:15:79:A9:D1:8A:F5:A9:5F:A7:CD"


class ReleaseAssetNamingTests(unittest.TestCase):
    """The naming contract exists because published asset names drifted across
    three different shapes between v1.24.1 and v1.29.0, which silently breaks
    the filename filters Obtainium and IzzyOnDroid users configure once."""

    def test_contracted_names_are_accepted(self) -> None:
        names = [
            "ZeusWatch-v1.29.1-standard-arm64-v8a.apk",
            "ZeusWatch-v1.29.1-standard-armeabi-v7a.apk",
            "ZeusWatch-v1.29.1-standard-universal.apk",
            "ZeusWatch-v1.29.1-freenet-arm64-v8a.apk",
            "ZeusWatch-v1.29.1-wear.apk",
        ]
        self.assertEqual(
            [],
            provenance.validate_asset_names([Path(name) for name in names], "1.29.1"),
        )

    def test_every_historical_naming_variant_is_rejected(self) -> None:
        for name in (
            "app-standard-arm64-v8a-release.apk",          # raw AGP output
            "ZeusWatch-freenet-arm64-v8a-v1.24.1.apk",     # v1.24.1 shape
            "ZeusWatch-Wear-v1.29.0.apk",                  # v1.29.0 wear shape
            "ZeusWatch-v1.29.1-standard-x86_64.apk",       # abi the app does not ship
        ):
            with self.subTest(name=name):
                self.assertTrue(provenance.validate_asset_names([Path(name)], "1.29.1"))

    def test_a_stale_version_in_the_name_is_rejected(self) -> None:
        # A leftover APK from the previous release is the easiest thing to
        # upload by accident, and its hash would not match SHA256SUMS either.
        problems = provenance.validate_asset_names(
            [Path("ZeusWatch-v1.29.0-standard-universal.apk")], "1.29.1"
        )

        self.assertEqual(1, len(problems))
        self.assertIn("carries version 1.29.0", problems[0])

    def test_build_provenance_refuses_a_misnamed_apk(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = root / "app-standard-arm64-v8a-release.apk"
            apk.write_bytes(b"fake-apk")
            sha256sums = root / "SHA256SUMS.txt"
            sha256sums.write_text(f"{provenance.hash_file(apk)}  {apk.name}\n", encoding="utf-8")

            with self.assertRaises(ValueError) as caught:
                provenance.build_provenance(
                    apks=[apk],
                    sha256sums=sha256sums,
                    version="1.29.1",
                    apksigner=root / "apksigner.bat",
                    root=root,
                    runner=fake_runner,
                    env={"ANDROID_HOME": str(root / "sdk")},
                )

            self.assertIn("naming contract", str(caught.exception))


class ReleaseProvenanceTests(unittest.TestCase):
    def test_build_provenance_records_artifact_hashes_and_cert_digest(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = root / "ZeusWatch-v1.25.0-standard-arm64-v8a.apk"
            apk.write_bytes(b"fake-apk")
            digest = provenance.hash_file(apk)
            sha256sums = root / "SHA256SUMS.txt"
            sha256sums.write_text(f"{digest}  {apk.name}\n", encoding="utf-8")

            data = provenance.build_provenance(
                apks=[apk],
                sha256sums=sha256sums,
                version="1.25.0",
                apksigner=root / "apksigner.bat",
                root=root,
                runner=fake_runner,
                env={"ANDROID_HOME": str(root / "sdk")},
            )

            self.assertEqual(data["project"], "ZeusWatch")
            self.assertEqual(data["version"], "1.25.0")
            self.assertEqual(data["source"]["commit"], "abc123")
            self.assertFalse(data["source"]["dirty"])
            self.assertTrue(data["sha256sums"]["verified"])
            artifact = data["artifacts"][0]
            self.assertEqual(artifact["fileName"], apk.name)
            self.assertEqual(artifact["sha256"], digest)
            self.assertEqual(artifact["signingCertificateSha256"], CERT_DIGEST)

    def test_build_provenance_rejects_checksum_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = root / "ZeusWatch-v1.25.0-freenet-universal.apk"
            apk.write_bytes(b"actual")
            sha256sums = root / "SHA256SUMS.txt"
            sha256sums.write_text(f"{'0' * 64}  {apk.name}\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "mismatch"):
                provenance.build_provenance(
                    apks=[apk],
                    sha256sums=sha256sums,
                    version="1.25.0",
                    apksigner=root / "apksigner.bat",
                    root=root,
                    runner=fake_runner,
                )

    def test_signing_cert_parser_normalizes_unseparated_digest(self) -> None:
        output = "Signer #1 certificate SHA-256 digest: fb0310aa520f6cc6ebda0461719ea92240ea2b4aa1d01579a9d18af5a95fa7cd"

        def runner(command, text, capture_output):  # noqa: ANN001
            self.assertIn("--print-certs", command)
            self.assertTrue(text)
            self.assertTrue(capture_output)
            return subprocess.CompletedProcess(command, 0, stdout=output, stderr="")

        digest = provenance.signing_cert_sha256(Path("app.apk"), Path("apksigner"), runner)

        self.assertEqual(digest, CERT_DIGEST)

    def test_collect_git_state_reports_dirty_tracked_files(self) -> None:
        def runner(command, cwd, text, capture_output):  # noqa: ANN001
            self.assertTrue(text)
            self.assertTrue(capture_output)
            if command[:2] == ["git", "rev-parse"]:
                return subprocess.CompletedProcess(command, 0, stdout="abc123\n", stderr="")
            if command[:2] == ["git", "branch"]:
                return subprocess.CompletedProcess(command, 0, stdout="main\n", stderr="")
            if command[:2] == ["git", "status"]:
                return subprocess.CompletedProcess(command, 0, stdout=" M README.md\n", stderr="")
            raise AssertionError(command)

        state = provenance.collect_git_state(Path("."), runner)

        self.assertTrue(state["dirty"])
        self.assertEqual(state["dirtyEntries"], [" M README.md"])

    def test_write_manifest_uses_stable_json(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "ZeusWatch-v1.25.0-provenance.json"

            provenance.write_manifest(output, {"schemaVersion": 1, "project": "ZeusWatch"})

            data = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(data["project"], "ZeusWatch")
            self.assertTrue(output.read_text(encoding="utf-8").endswith("\n"))


def fake_runner(command, cwd=None, text=True, capture_output=True):  # noqa: ANN001
    del cwd
    if command[:2] == ["git", "rev-parse"]:
        return subprocess.CompletedProcess(command, 0, stdout="abc123\n", stderr="")
    if command[:2] == ["git", "branch"]:
        return subprocess.CompletedProcess(command, 0, stdout="main\n", stderr="")
    if command[:2] == ["git", "status"]:
        return subprocess.CompletedProcess(command, 0, stdout="", stderr="")
    if command and "gradlew" in Path(command[0]).name:
        return subprocess.CompletedProcess(command, 0, stdout="Gradle 8.9\n", stderr="")
    if command[:2] == ["java", "-version"]:
        return subprocess.CompletedProcess(command, 0, stdout="", stderr='openjdk version "17.0.12"\n')
    if "verify" in command and "--print-certs" in command:
        return subprocess.CompletedProcess(
            command,
            0,
            stdout=f"Signer #1 certificate SHA-256 digest: {CERT_DIGEST}\n",
            stderr="",
        )
    raise AssertionError(command)


if __name__ == "__main__":
    unittest.main()
