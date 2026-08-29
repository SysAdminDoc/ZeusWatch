#!/usr/bin/env python3
"""Tests for the release asset staging contract.

Obtainium and IzzyOnDroid users configure a filename pattern once and expect it
to hold for every release, so the names this produces are a public contract.
The failure this guards against is quiet: an unsigned or leftover APK sitting
beside the real one gets published under the contracted name and installs as
the release.
"""

from __future__ import annotations

import shutil
import tempfile
import unittest
from pathlib import Path

import stage_release_assets as stage


class ReleaseStagingTests(unittest.TestCase):

    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root, ignore_errors=True)
        self.standard = self.root / "standard"
        self.freenet = self.root / "freenet"
        self.wear = self.root / "wear"
        for directory in (self.standard, self.freenet, self.wear):
            directory.mkdir(parents=True)

    def build(self, *, skip: set[tuple[str, str]] = frozenset()) -> None:
        for flavor, abi in stage.EXPECTED_PHONE_BUILDS:
            if (flavor, abi) in skip:
                continue
            directory = self.standard if flavor == "standard" else self.freenet
            (directory / f"app-{flavor}-{abi}-release.apk").write_bytes(b"apk")
        (self.wear / "wear-release.apk").write_bytes(b"apk")

    def run_plan(self, version: str = "1.4.0"):
        return stage.plan(version, phone_dirs=(self.standard, self.freenet), wear_dir=self.wear)

    def test_a_complete_build_stages_every_asset_under_the_contracted_name(self) -> None:
        self.build()

        staged, problems = self.run_plan()

        self.assertEqual([], problems)
        self.assertEqual(
            {
                "ZeusWatch-v1.4.0-standard-arm64-v8a.apk",
                "ZeusWatch-v1.4.0-standard-armeabi-v7a.apk",
                "ZeusWatch-v1.4.0-standard-universal.apk",
                "ZeusWatch-v1.4.0-freenet-arm64-v8a.apk",
                "ZeusWatch-v1.4.0-freenet-armeabi-v7a.apk",
                "ZeusWatch-v1.4.0-freenet-universal.apk",
                "ZeusWatch-v1.4.0-wear.apk",
            },
            {name for _, name in staged},
        )

    def test_a_missing_abi_is_reported_rather_than_quietly_shipped(self) -> None:
        # Publishing five of the six phone APKs leaves those users on the
        # previous version with no signal that anything went wrong.
        self.build(skip={("freenet", "armeabi-v7a")})

        _, problems = self.run_plan()

        self.assertEqual(["missing phone build: freenet armeabi-v7a"], problems)

    def test_a_missing_wear_apk_is_reported(self) -> None:
        self.build()
        (self.wear / "wear-release.apk").unlink()

        _, problems = self.run_plan()

        self.assertIn(f"no wear release APK in {self.wear}", problems)

    def test_an_unsigned_apk_beside_the_real_one_fails_the_staging(self) -> None:
        # This is the actual publishing accident: an -unsigned or leftover APK
        # in the output directory is exactly the wrong file to hand users.
        self.build()
        (self.standard / "app-standard-universal-release-unsigned.apk").write_bytes(b"apk")

        _, problems = self.run_plan()

        self.assertEqual(
            ["unrecognised APK in standard: app-standard-universal-release-unsigned.apk"],
            problems,
        )

    def test_a_debug_apk_is_never_treated_as_a_release_asset(self) -> None:
        self.build()
        (self.freenet / "app-freenet-universal-debug.apk").write_bytes(b"apk")

        _, problems = self.run_plan()

        self.assertEqual(1, len(problems))
        self.assertIn("app-freenet-universal-debug.apk", problems[0])

    def test_a_missing_output_directory_is_reported_with_its_path(self) -> None:
        self.build()
        shutil.rmtree(self.freenet)

        _, problems = self.run_plan()

        self.assertIn(f"missing build output directory: {self.freenet}", problems)

    def test_names_match_what_the_provenance_document_promises(self) -> None:
        # The two tools describe the same contract from opposite ends, and a
        # rename in one that misses the other publishes unverifiable assets.
        import generate_release_provenance as provenance

        self.build()
        staged, _ = self.run_plan(version="2.0.1")

        published = [Path(name) for _, name in staged]
        self.assertEqual([], provenance.validate_asset_names(published, "2.0.1"))

    def test_the_version_comes_from_the_gradle_file(self) -> None:
        build_file = self.root / "build.gradle.kts"
        build_file.write_text('android {\n    versionName = "9.9.9"\n}\n', encoding="utf-8")

        self.assertEqual("9.9.9", stage.read_version(build_file))

    def test_a_gradle_file_without_a_version_is_an_error_not_a_blank_name(self) -> None:
        build_file = self.root / "empty.gradle.kts"
        build_file.write_text("android {\n}\n", encoding="utf-8")

        with self.assertRaises(ValueError):
            stage.read_version(build_file)


if __name__ == "__main__":
    unittest.main()
