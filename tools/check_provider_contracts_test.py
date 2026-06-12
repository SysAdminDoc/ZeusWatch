#!/usr/bin/env python3
"""Unit tests for check_provider_contracts.py."""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from urllib.error import HTTPError

MODULE_PATH = Path(__file__).with_name("check_provider_contracts.py")
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


if __name__ == "__main__":
    unittest.main()
