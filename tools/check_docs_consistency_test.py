#!/usr/bin/env python3
"""Unit tests for check_docs_consistency.py."""

from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("check_docs_consistency.py")
SPEC = importlib.util.spec_from_file_location("check_docs_consistency", MODULE_PATH)
docs = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = docs
SPEC.loader.exec_module(docs)


class DocsConsistencyTests(unittest.TestCase):
    def test_workflow_claims_fail_when_no_workflows_exist(self) -> None:
        with _temporary_docs_repo(
            {
                "README.md": "![Build](https://github.com/owner/repo/actions/workflows/build.yml/badge.svg)",
                "docs/RELEASE.md": "release.yml emits artifact attestations",
                "ROADMAP.md": "CI fails on new release regressions",
            }
        ):
            issues = docs.check_release_workflow_truth()

        self.assertGreaterEqual(len(issues), 3)
        self.assertTrue(all("no .github/workflows/*.yml files exist" in issue for issue in issues))

    def test_workflow_claims_are_allowed_when_workflows_exist(self) -> None:
        with _temporary_docs_repo(
            {
                "README.md": "![Build](https://github.com/owner/repo/actions/workflows/build.yml/badge.svg)",
                "docs/RELEASE.md": "release.yml emits artifact attestations",
                "ROADMAP.md": "CI fails on new release regressions",
                ".github/workflows/build.yml": "name: local-test-fixture\n",
            }
        ):
            self.assertEqual(docs.check_release_workflow_truth(), [])

    def test_workflow_truth_check_uses_word_boundaries_for_ci(self) -> None:
        with _temporary_docs_repo(
            {
                "README.md": "Precipitation forecast and F-Droid certification notes.",
                "docs/RELEASE.md": "Application signing is verified locally.",
                "ROADMAP.md": "Accessibility and specification work remains.",
            }
        ):
            self.assertEqual(docs.check_release_workflow_truth(), [])

    def test_onboarding_everything_count_matches_card_type_count(self) -> None:
        with _temporary_docs_repo(
            {
                "app/src/main/java/com/sysadmindoc/nimbus/data/repository/CardConfig.kt": """
enum class CardType(val label: String) {
    WEATHER_SUMMARY("Weather Summary"),
    RADAR_PREVIEW("Radar Preview"),
    NOWCAST("Rain Next Hour"),
}
""",
                "app/src/main/res/values/strings.xml": """
<resources>
    <string name="onboarding_cards_everything_count">2 cards</string>
</resources>
""",
                "app/src/main/res/values-es/strings.xml": """
<resources>
    <string name="onboarding_cards_everything_count">3 tarjetas</string>
</resources>
""",
            }
        ):
            issues = docs.check_onboarding_card_count()

        self.assertEqual(len(issues), 1)
        self.assertIn("onboarding everything count 2 != CardType count 3", issues[0])

    def test_radar_provider_inventory_requires_readme_labels(self) -> None:
        with _temporary_docs_repo(
            _radar_inventory_files(
                readme="| **Radar Providers** | Windy Radar |\n",
            )
        ):
            issues = docs.check_radar_provider_inventory()

        self.assertTrue(
            any("missing radar provider label 'RainViewer Native'" in issue for issue in issues),
            issues,
        )

    def test_dependency_stack_versions_follow_version_catalog(self) -> None:
        good_stack = _stack_line(datastore="1.2.1")
        bad_stack = _stack_line(datastore="1.0.0")
        with _temporary_docs_repo(
            _version_source_files(
                readme=bad_stack,
                claude="**Version**: v1.25.0 (phone versionCode 105, wear versionCode 77)\n"
                + good_stack,
            )
        ):
            issues = docs.check_dependency_version_claims()

        self.assertTrue(
            any("README.md: stack claim for DataStore does not match datastore 1.2.1" in issue for issue in issues),
            issues,
        )

    def test_dependency_stack_checks_okhttp_version(self) -> None:
        with _temporary_docs_repo(
            _version_source_files(
                readme=_stack_line(datastore="1.2.1").replace("OkHttp 5.4.0", "OkHttp 5.3.2"),
                claude=_stack_line(datastore="1.2.1"),
            )
        ):
            issues = docs.check_dependency_version_claims()

        self.assertTrue(
            any("README.md: stack claim for OkHttp does not match okhttp 5.4.0" in issue for issue in issues),
            issues,
        )

    def test_room_schema_claims_follow_database_annotation(self) -> None:
        with _temporary_docs_repo(
            {
                "README.md": "Room Database (v4)\n",
                "CLAUDE.md": "**Room DB**: v5 — current schema\n",
                "app/src/main/java/com/sysadmindoc/nimbus/data/api/NimbusDatabase.kt": """
@Database(entities = [], version = 5, exportSchema = true)
abstract class NimbusDatabase
""",
            }
        ):
            issues = docs.check_room_schema_claims()

        self.assertEqual(len(issues), 1)
        self.assertIn("README.md: Room Database architecture label v4 != NimbusDatabase v5", issues[0])

    def test_deep_link_claims_follow_manifest_scheme(self) -> None:
        with _temporary_docs_repo(
            _manifest_contract_files(
                readme="Deep Links: `nimbus://`\nForeground-only location.\n",
            )
        ):
            issues = docs.check_manifest_contract_claims()

        self.assertTrue(any("missing manifest deep-link scheme 'zeuswatch://'" in issue for issue in issues), issues)
        self.assertTrue(any("stale deep-link scheme 'nimbus://'" in issue for issue in issues), issues)

    def test_location_claim_fails_when_background_permission_is_declared(self) -> None:
        files = _manifest_contract_files(
            readme="Deep Links: `zeuswatch://`\nForeground-only location.\n",
        )
        files["app/src/main/AndroidManifest.xml"] = files["app/src/main/AndroidManifest.xml"].replace(
            "</manifest>",
            '<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" /></manifest>',
        )
        with _temporary_docs_repo(files):
            issues = docs.check_manifest_contract_claims()

        self.assertTrue(
            any("claims foreground-only location but manifest declares" in issue for issue in issues),
            issues,
        )

    def test_historical_scheme_and_workflow_claims_require_superseded_label(self) -> None:
        with _temporary_docs_repo(
            {
                "docs/phases-pre-v1.md": "# Old plan\nDeep links use nimbus://. GitHub Actions ships releases.\n",
            }
        ):
            issues = docs.check_historical_supersession_labels()

        self.assertEqual(
            issues,
            ["docs/phases-pre-v1.md: historical deep-link/workflow claims are not labeled superseded"],
        )

    def test_local_release_posture_is_required_without_workflows(self) -> None:
        with _temporary_docs_repo(
            {
                "docs/RELEASE.md": "Upload a release somehow.\n",
            }
        ):
            issues = docs.check_local_release_posture()

        self.assertEqual(len(issues), 3)

    def test_version_sync_checks_claude_header(self) -> None:
        with _temporary_docs_repo(
            _version_source_files(
                readme="![Version](https://img.shields.io/badge/version-1.25.0-blue)\n",
                claude="**Version**: v1.24.2 (phone versionCode 100, wear versionCode 70)\n",
                roadmap="**Current Version**: v1.25.0 (phone versionCode 105, wear versionCode 77)\n",
            )
        ):
            issues = docs.check_version_sync()

        self.assertTrue(
            any("CLAUDE.md: version header v1.24.2 (100/70)" in issue for issue in issues),
            issues,
        )


class _temporary_docs_repo:
    def __init__(self, files: dict[str, str]) -> None:
        self.files = files
        self.temp_dir: tempfile.TemporaryDirectory[str] | None = None
        self.old_root = docs.REPO_ROOT
        self.old_files = docs.WORKFLOW_TRUTH_FILES

    def __enter__(self) -> Path:
        self.temp_dir = tempfile.TemporaryDirectory()
        root = Path(self.temp_dir.name)
        for relative, content in self.files.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        docs.REPO_ROOT = root
        docs.WORKFLOW_TRUTH_FILES = [
            root / "README.md",
            root / "docs" / "RELEASE.md",
            root / "ROADMAP.md",
        ]
        return root

    def __exit__(self, exc_type, exc, tb) -> None:  # noqa: ANN001
        docs.REPO_ROOT = self.old_root
        docs.WORKFLOW_TRUTH_FILES = self.old_files
        assert self.temp_dir is not None
        self.temp_dir.cleanup()


def _radar_inventory_files(readme: str) -> dict[str, str]:
    strings = """
<resources>
    <string name="settings_radar_provider_windy">Windy Radar</string>
    <string name="settings_radar_provider_windy_summary">Global interactive radar from Windy.</string>
    <string name="settings_radar_provider_rainviewer">RainViewer Native</string>
    <string name="settings_radar_provider_rainviewer_summary">Past-only RainViewer playback.</string>
</resources>
"""
    return {
        "README.md": readme,
        "app/src/main/java/com/sysadmindoc/nimbus/data/repository/UserPreferences.kt": """
enum class RadarProvider(
    val label: String,
    val summary: String,
) {
    WINDY_WEBVIEW(label = "Windy Radar", summary = "Global interactive radar from Windy."),
    NATIVE_MAPLIBRE(label = "RainViewer Native", summary = "Past-only RainViewer playback."),
}
""",
        "app/src/main/java/com/sysadmindoc/nimbus/util/SettingsEnumText.kt": """
internal val RadarProvider.labelRes: Int
    get() = when (this) {
        RadarProvider.WINDY_WEBVIEW -> R.string.settings_radar_provider_windy
        RadarProvider.NATIVE_MAPLIBRE -> R.string.settings_radar_provider_rainviewer
    }

internal val RadarProvider.summaryRes: Int
    get() = when (this) {
        RadarProvider.WINDY_WEBVIEW -> R.string.settings_radar_provider_windy_summary
        RadarProvider.NATIVE_MAPLIBRE -> R.string.settings_radar_provider_rainviewer_summary
    }
""",
        "app/src/main/res/values/strings.xml": strings,
        "app/src/main/res/values-es/strings.xml": strings,
    }


def _version_source_files(
    readme: str,
    claude: str = "",
    roadmap: str = "",
) -> dict[str, str]:
    return {
        "README.md": readme,
        "CLAUDE.md": claude,
        "ROADMAP.md": roadmap,
        "app/build.gradle.kts": """
android {
    defaultConfig {
        versionCode = 105
        versionName = "1.25.0"
    }
}
dependencies {
    "standardImplementation"(platform("com.google.firebase:firebase-bom:34.12.0"))
}
""",
        "wear/build.gradle.kts": """
android {
    defaultConfig {
        versionCode = 77
        versionName = "1.25.0"
    }
}
""",
        "gradle/libs.versions.toml": """
[versions]
kotlin = "2.1.0"
compose-bom = "2025.04.01"
hilt = "2.53.1"
retrofit = "3.0.0"
okhttp = "5.4.0"
room = "2.6.1"
datastore = "1.2.1"
maplibre = "13.3.1"
glance = "1.1.1"
work = "2.11.2"
lottie = "6.7.1"
coil = "3.1.0"
""",
    }


def _stack_line(datastore: str) -> str:
    return (
        "**Stack:** Kotlin 2.1.0, Jetpack Compose (BOM 2025.04.01), "
        "Hilt 2.53.1, Retrofit 3.0.0, OkHttp 5.4.0, Room 2.6.1, "
        f"DataStore {datastore}, MapLibre 13.3.1, Glance 1.1.1, "
        "WorkManager 2.11.2, Lottie 6.7.1, Coil 3.1.0, "
        "Firebase Firestore (BOM 34.12.0)\n"
    )


def _manifest_contract_files(readme: str) -> dict[str, str]:
    return {
        "README.md": readme,
        "CLAUDE.md": "Deep links use `zeuswatch://`.\n",
        "app/src/main/AndroidManifest.xml": """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <application>
        <activity>
            <intent-filter><data android:scheme="zeuswatch" /></intent-filter>
        </activity>
    </application>
</manifest>
""",
    }


if __name__ == "__main__":
    unittest.main()
