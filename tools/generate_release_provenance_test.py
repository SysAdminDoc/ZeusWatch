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


class ReleaseProvenanceTests(unittest.TestCase):
    def test_build_provenance_records_artifact_hashes_and_cert_digest(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            apk = root / "ZeusWatch-standard-v1.25.0.apk"
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
            apk = root / "bad.apk"
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
