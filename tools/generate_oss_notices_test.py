#!/usr/bin/env python3
"""Tests for the open-source notices generator.

The notices screen is how the app satisfies the licence terms of everything it
ships, and F-Droid rejects a build whose notices claim proprietary components
it does not contain. Both failure directions are silent, so they are pinned
here against fixtures rather than only against the real classpath.
"""

from __future__ import annotations

import unittest

import generate_oss_notices as notices


def license_data() -> dict:
    return {
        "groups": {
            "androidx.core": {"license": "Apache-2.0", "url": "https://example.invalid/androidx"},
            "com.squareup.okhttp3": {"license": "Apache-2.0", "url": "https://example.invalid/okhttp"},
            "com.google.firebase": {"license": "Apache-2.0", "url": "https://example.invalid/firebase"},
            "com.google.guava": {"license": "Apache-2.0", "url": "https://example.invalid/guava"},
        }
    }


PROVIDER_SOURCE = """
enum class WeatherSourceProvider(
    val displayName: String,
) {
    OPEN_METEO(
        displayName = "Open-Meteo",
    ),
    NWS(
        displayName = "NWS",
    ),
    ;

    fun helper() = displayName
}
"""


class NoticesGeneratorTests(unittest.TestCase):

    def build(self, runtime: dict[str, set[str]]):
        return notices.build_notices(
            runtime=runtime,
            license_data=license_data(),
            provider_text=PROVIDER_SOURCE,
        )

    def by_name(self, document: dict) -> dict[str, dict]:
        return {entry["name"]: entry for entry in document["dependencies"]}

    def test_an_artifact_absent_from_freenet_is_marked_standard_only(self) -> None:
        document, problems = self.build(
            {
                "standard": {"androidx.core:core:1.0.0", "com.google.firebase:firebase-common:21.0.0"},
                "freenet": {"androidx.core:core:1.0.0"},
            }
        )

        self.assertEqual([], problems)
        entries = self.by_name(document)
        self.assertTrue(entries["com.google.firebase:firebase-common"]["standardOnly"])
        self.assertFalse(entries["androidx.core:core"]["standardOnly"])

    def test_standard_only_is_derived_from_the_classpaths_not_the_group(self) -> None:
        # The old generator carried a hand-kept list of standard-only groups,
        # which went stale the moment a dependency moved between flavors.
        document, _ = self.build(
            {
                "standard": {"com.google.firebase:firebase-common:21.0.0"},
                "freenet": {"com.google.firebase:firebase-common:21.0.0"},
            }
        )

        self.assertFalse(self.by_name(document)["com.google.firebase:firebase-common"]["standardOnly"])

    def test_an_artifact_resolved_at_two_versions_gets_one_row(self) -> None:
        # guava lands on -android for one flavor and -jre for the other, and a
        # duplicated row reads as two separate libraries in the notices list.
        document, _ = self.build(
            {
                "standard": {"com.google.guava:guava:33.0.0-android"},
                "freenet": {"com.google.guava:guava:33.0.0-jre"},
            }
        )

        rows = [entry for entry in document["dependencies"] if entry["name"] == "com.google.guava:guava"]
        self.assertEqual(1, len(rows))
        self.assertFalse(rows[0]["standardOnly"])

    def test_an_unmapped_group_is_a_problem_rather_than_a_missing_notice(self) -> None:
        # Dropping the entry would ship an unattributed dependency, which is
        # the licence breach the whole asset exists to prevent.
        document, problems = self.build(
            {
                "standard": {"io.example.unmapped:widget:1.0.0"},
                "freenet": set(),
            }
        )

        self.assertEqual(1, len(problems))
        self.assertIn("io.example.unmapped", problems[0])
        self.assertNotIn("io.example.unmapped:widget", self.by_name(document))

    def test_every_entry_carries_a_real_version(self) -> None:
        document, _ = self.build(
            {
                "standard": {"androidx.core:core:1.13.1"},
                "freenet": {"androidx.core:core:1.13.1"},
            }
        )

        self.assertEqual("1.13.1", self.by_name(document)["androidx.core:core"]["version"])

    def test_wear_only_artifacts_are_not_claimed_by_the_phone(self) -> None:
        # The phone ships the notices screen. Listing a module only the watch
        # resolves would claim the phone contains code it does not.
        document, _ = self.build(
            {
                "standard": {"androidx.core:core:1.0.0"},
                "freenet": {"androidx.core:core:1.0.0"},
                "wear": {"com.squareup.okhttp3:okhttp:5.0.0"},
            }
        )

        self.assertNotIn("com.squareup.okhttp3:okhttp", self.by_name(document))

    def test_providers_come_from_the_enum_in_declaration_order(self) -> None:
        names = notices.parse_providers(PROVIDER_SOURCE)

        self.assertEqual(["OPEN_METEO", "NWS"], names)

    def test_an_unattributed_weather_source_is_a_problem(self) -> None:
        _, problems = notices.build_notices(
            runtime={"standard": set(), "freenet": set()},
            license_data=license_data(),
            provider_text=PROVIDER_SOURCE.replace("NWS(", "MADE_UP_SOURCE("),
        )

        self.assertEqual(1, len(problems))
        self.assertIn("MADE_UP_SOURCE", problems[0])

    def test_rendering_is_stable_between_runs(self) -> None:
        # The asset is committed, so unstable ordering would churn the diff on
        # every regeneration and hide real changes.
        runtime = {
            "standard": {"androidx.core:core:1.0.0", "com.squareup.okhttp3:okhttp:5.0.0"},
            "freenet": {"androidx.core:core:1.0.0"},
        }

        first, _ = self.build(runtime)
        second, _ = self.build(runtime)

        self.assertEqual(notices.render(first), notices.render(second))
        self.assertTrue(notices.render(first).endswith("\n"))


if __name__ == "__main__":
    unittest.main()
