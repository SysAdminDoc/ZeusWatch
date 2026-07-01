#!/usr/bin/env python3
"""Unit tests for check_provider_contracts.py."""

from __future__ import annotations

import importlib.util
import json
import re
import sys
import tempfile
import unittest
from pathlib import Path
from urllib.error import HTTPError

MODULE_PATH = Path(__file__).with_name("check_provider_contracts.py")
WEATHER_SOURCE_PATH = MODULE_PATH.parents[1] / "app/src/main/java/com/sysadmindoc/nimbus/data/repository/WeatherSource.kt"
SPEC = importlib.util.spec_from_file_location("check_provider_contracts", MODULE_PATH)
contracts = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = contracts
SPEC.loader.exec_module(contracts)


class ProviderContractTests(unittest.TestCase):
    def test_validators_accept_minimal_provider_payloads(self) -> None:
        self.assertTrue(
            contracts.validate_wmo_members(
                [{"ra": 4, "members": [{"mid": "USA", "name": "United States"}]}]
            ).ok
        )
        self.assertTrue(
            contracts.validate_rainviewer_metadata(
                {
                    "generated": 1710000000,
                    "host": "https://tilecache.rainviewer.com",
                    "radar": {"past": [{"time": 1710000000, "path": "/v2/radar/1710000000"}], "nowcast": []},
                }
            ).ok
        )
        self.assertTrue(
            contracts.validate_open_meteo_forecast(
                {
                    "current": {"temperature_2m": 12.3, "weather_code": 3},
                    "hourly": {"time": ["2026-06-12T00:00"]},
                    "daily": {"time": ["2026-06-12"]},
                }
            ).ok
        )
        self.assertTrue(
            contracts.validate_open_meteo_model_forecast(
                {
                    "hourly": {"time": ["2026-06-12T00:00"]},
                    "daily": {"time": ["2026-06-12"]},
                }
            ).ok
        )
        self.assertTrue(
            contracts.validate_open_meteo_air_quality(
                {
                    "current": {"us_aqi": 12, "pm2_5": 4.2},
                    "hourly": {"time": ["2026-06-12T00:00"], "us_aqi": [12]},
                }
            ).ok
        )
        self.assertTrue(
            contracts.validate_open_meteo_minutely(
                {"minutely_15": {"time": ["2026-06-12T00:00"], "precipitation": [0.0]}}
            ).ok
        )
        self.assertTrue(contracts.validate_nws_alerts({"type": "FeatureCollection", "features": []}).ok)
        self.assertTrue(
            contracts.validate_met_no_compact(
                {
                    "type": "Feature",
                    "properties": {
                        "timeseries": [
                            {"data": {"instant": {"details": {"air_temperature": 20.1}}}},
                        ],
                    },
                }
            ).ok
        )
        self.assertTrue(
            contracts.validate_bright_sky_weather(
                {"weather": [{"timestamp": "2026-06-12T00:00:00+00:00"}]}
            ).ok
        )
        self.assertTrue(contracts.validate_bright_sky_alerts({"alerts": []}).ok)
        self.assertTrue(
            contracts.validate_eccc_feature_collection(
                {"type": "FeatureCollection", "features": [{"properties": {"name": {"en": "Ottawa"}}}]}
            ).ok
        )
        self.assertTrue(
            contracts.validate_eccc_cap_index(
                "<h1>Index of /today/alerts/cap/20260701</h1><a href=\"LAND/\">LAND/</a>"
            ).ok
        )
        self.assertTrue(contracts.validate_meteoalarm_warnings({"warnings": [{"alert": {"info": []}}]}).ok)
        self.assertTrue(contracts.validate_jma_atom_feed("<feed><title>JMA</title><link href=\"x\" /></feed>").ok)
        self.assertTrue(
            contracts.validate_owm_onecall({"current": {}, "hourly": [], "daily": []}).ok
        )
        self.assertTrue(contracts.validate_owm_air_pollution({"list": [{"main": {"aqi": 2}}]}).ok)
        self.assertTrue(
            contracts.validate_pirate_weather({"currently": {}, "hourly": {}, "daily": {}}).ok
        )

    def test_validator_reports_missing_rainviewer_frames(self) -> None:
        result = contracts.validate_rainviewer_metadata({"generated": 1710000000, "host": "https://example.test"})
        self.assertFalse(result.ok)
        self.assertIn("radar object", result.message)

    def test_fresh_cache_skips_transport(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            cache_dir = Path(tmp)
            url = "https://example.test/data.json"
            contracts._write_cache(cache_dir, url, 200, {"etag": '"abc"'}, b'{"ok": true}')

            def blocked_transport(request, timeout_seconds):  # noqa: ANN001
                raise AssertionError("fresh cache should avoid network")

            result = contracts.fetch_json(
                url,
                cache_dir=cache_dir,
                timeout_seconds=1,
                max_cache_age_seconds=3600,
                transport=blocked_transport,
            )
            self.assertTrue(result.from_cache)
            self.assertEqual(json.loads(result.body.decode("utf-8")), {"ok": True})

    def test_stale_cache_uses_conditional_headers_and_accepts_304(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            cache_dir = Path(tmp)
            url = "https://example.test/data.json"
            contracts._write_cache(
                cache_dir,
                url,
                200,
                {"etag": '"abc"', "last-modified": "Fri, 12 Jun 2026 00:00:00 GMT"},
                b'{"ok": true}',
            )
            path = contracts._cache_path(cache_dir, url)
            cache = json.loads(path.read_text(encoding="utf-8"))
            cache["fetched_at"] = 0
            path.write_text(json.dumps(cache), encoding="utf-8")
            seen_headers: dict[str, str] = {}

            def not_modified_transport(request, timeout_seconds):  # noqa: ANN001
                del timeout_seconds
                seen_headers.update({key.lower(): value for key, value in request.header_items()})
                raise HTTPError(request.full_url, 304, "Not Modified", hdrs=None, fp=None)

            result = contracts.fetch_json(
                url,
                cache_dir=cache_dir,
                timeout_seconds=1,
                max_cache_age_seconds=1,
                transport=not_modified_transport,
            )
            self.assertTrue(result.from_cache)
            self.assertEqual(seen_headers["if-none-match"], '"abc"')
            self.assertIn("if-modified-since", seen_headers)
            self.assertEqual(json.loads(result.body.decode("utf-8")), {"ok": True})

    def test_selectable_provider_matrix_has_contract_coverage(self) -> None:
        selectable_pairs = _selectable_provider_pairs()
        contract_pairs = {
            (provider, data_type)
            for check in contracts.provider_checks()
            for provider in check.providers
            for data_type in check.data_types
        }

        self.assertEqual(set(), selectable_pairs - contract_pairs)

    def test_contract_entries_include_release_metadata(self) -> None:
        for check in contracts.provider_checks():
            with self.subTest(check=check.key):
                self.assertTrue(check.docs_url)
                self.assertTrue(check.coverage)
                self.assertTrue(check.schema_assertion)
                self.assertTrue(check.unavailable_policy)
                if check.providers or check.data_types:
                    self.assertTrue(check.providers)
                    self.assertTrue(check.data_types)

    def test_policy_only_and_missing_credentials_are_non_network_contracts(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            cache_dir = Path(tmp)

            def blocked_transport(request, timeout_seconds):  # noqa: ANN001
                raise AssertionError("policy contracts should not call transport")

            kma = next(check for check in contracts.provider_checks() if check.key == "open-meteo-kma-quarantine")
            kma_result = contracts.run_check(
                kma,
                cache_dir=cache_dir,
                timeout_seconds=1,
                max_cache_age_seconds=0,
                force_refresh=True,
                transport=blocked_transport,
            )
            self.assertTrue(kma_result.ok)
            self.assertEqual(kma_result.source, "policy")

            owm = next(check for check in contracts.provider_checks() if check.key == "openweathermap-forecast-alerts")
            old_env = {key: contracts.os.environ.get(key) for key in owm.required_env_vars}
            try:
                for key in owm.required_env_vars:
                    contracts.os.environ.pop(key, None)
                owm_result = contracts.run_check(
                    owm,
                    cache_dir=cache_dir,
                    timeout_seconds=1,
                    max_cache_age_seconds=0,
                    force_refresh=True,
                    transport=blocked_transport,
                )
            finally:
                for key, value in old_env.items():
                    if value is None:
                        contracts.os.environ.pop(key, None)
                    else:
                        contracts.os.environ[key] = value
            self.assertTrue(owm_result.ok)
            self.assertEqual(owm_result.source, "credential-policy")


def _selectable_provider_pairs() -> set[tuple[str, str]]:
    text = WEATHER_SOURCE_PATH.read_text(encoding="utf-8")
    enum_start = text.index("enum class WeatherSourceProvider")
    body_start = text.index("{", enum_start) + 1
    body_end = text.index(";\n", body_start)
    body = text[body_start:body_end]
    pairs: set[tuple[str, str]] = set()
    offset = 0
    while True:
        match = re.search(r"\b([A-Z0-9_]+)\(", body[offset:])
        if match is None:
            break
        provider = match.group(1)
        call_start = offset + match.end() - 1
        call, call_end = _balanced_call(body, call_start)
        supported = _types_for_assignment(call, "supportedTypes") or set()
        implemented = _types_for_assignment(call, "implementedTypes")
        if "implementedTypes = emptySet()" in call:
            implemented = set()
        if implemented is None:
            implemented = supported
        pairs.update((provider, data_type) for data_type in supported & implemented)
        offset = call_end
    return pairs


def _types_for_assignment(call: str, assignment: str) -> set[str] | None:
    marker = f"{assignment} = "
    start = call.find(marker)
    if start == -1:
        return None
    value_start = start + len(marker)
    if call.startswith("emptySet()", value_start):
        return set()
    set_start = call.find("(", value_start)
    if set_start == -1:
        return set()
    set_call, _ = _balanced_call(call, set_start)
    return set(re.findall(r"WeatherDataType\.([A-Z_]+)", set_call))


def _balanced_call(text: str, open_paren: int) -> tuple[str, int]:
    depth = 0
    for index in range(open_paren, len(text)):
        char = text[index]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return text[open_paren : index + 1], index + 1
    raise AssertionError("unbalanced Kotlin enum call")


if __name__ == "__main__":
    unittest.main()
