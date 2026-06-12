#!/usr/bin/env python3
"""Low-rate live contract smoke checks for public weather providers."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CACHE_DIR = ROOT / "build" / "provider-contract-cache"
DEFAULT_REPORT = ROOT / "build" / "reports" / "provider-contracts.json"
DEFAULT_TIMEOUT_SECONDS = 12.0
DEFAULT_MAX_CACHE_AGE_SECONDS = 6 * 60 * 60
USER_AGENT = "ZeusWatch ProviderContractSmoke/1.0 (https://github.com/SysAdminDoc/ZeusWatch)"
ACCEPT_HEADER = "application/json, application/geo+json;q=0.9, */*;q=0.1"
NYC_LATITUDE = "40.7128"
NYC_LONGITUDE = "-74.0060"

JsonMap = dict[str, Any]
Transport = Callable[[Request, float], tuple[int, dict[str, str], bytes]]


@dataclass(frozen=True)
class ValidationResult:
    ok: bool
    message: str


@dataclass(frozen=True)
class ContractCheck:
    key: str
    name: str
    url: str
    docs_url: str
    validator: Callable[[Any], ValidationResult]


@dataclass(frozen=True)
class FetchResult:
    status: int
    body: bytes | None
    headers: dict[str, str]
    from_cache: bool
    cache_state: str
    error: str | None = None


@dataclass(frozen=True)
class CheckResult:
    key: str
    name: str
    url: str
    docs_url: str
    ok: bool
    status: int
    source: str
    message: str

    def to_report(self) -> JsonMap:
        return {
            "key": self.key,
            "name": self.name,
            "url": self.url,
            "docsUrl": self.docs_url,
            "ok": self.ok,
            "status": self.status,
            "source": self.source,
            "message": self.message,
        }


def provider_checks() -> list[ContractCheck]:
    open_meteo_query = urlencode(
        {
            "latitude": NYC_LATITUDE,
            "longitude": NYC_LONGITUDE,
            "current": "temperature_2m,weather_code",
            "hourly": "temperature_2m",
            "daily": "weather_code",
            "forecast_days": "1",
            "forecast_hours": "1",
            "timezone": "UTC",
        }
    )
    nws_query = urlencode(
        {
            "point": f"{NYC_LATITUDE},{NYC_LONGITUDE}",
            "status": "actual",
            "message_type": "alert,update",
        }
    )
    met_no_query = urlencode({"lat": NYC_LATITUDE, "lon": NYC_LONGITUDE})
    return [
        ContractCheck(
            key="wmo-members",
            name="WMO member metadata",
            url="https://severeweather.wmo.int/v2/json/wmo_member.json",
            docs_url="https://severeweather.wmo.int/about.html",
            validator=validate_wmo_members,
        ),
        ContractCheck(
            key="rainviewer-metadata",
            name="RainViewer weather-map metadata",
            url="https://api.rainviewer.com/public/weather-maps.json",
            docs_url="https://www.rainviewer.com/api/weather-maps-api.html",
            validator=validate_rainviewer_metadata,
        ),
        ContractCheck(
            key="open-meteo-forecast",
            name="Open-Meteo forecast",
            url=f"https://api.open-meteo.com/v1/forecast?{open_meteo_query}",
            docs_url="https://open-meteo.com/en/docs",
            validator=validate_open_meteo_forecast,
        ),
        ContractCheck(
            key="nws-active-alerts",
            name="NWS active alerts",
            url=f"https://api.weather.gov/alerts/active?{nws_query}",
            docs_url="https://www.weather.gov/documentation/services-web-api",
            validator=validate_nws_alerts,
        ),
        ContractCheck(
            key="met-no-compact",
            name="MET Norway compact forecast",
            url=f"https://api.met.no/weatherapi/locationforecast/2.0/compact?{met_no_query}",
            docs_url="https://api.met.no/weatherapi/locationforecast/2.0/documentation",
            validator=validate_met_no_compact,
        ),
    ]


def validate_wmo_members(data: Any) -> ValidationResult:
    if not isinstance(data, list):
        return ValidationResult(False, "expected a top-level list of WMO regions")
    regions = [region for region in data if isinstance(region, dict)]
    member_count = 0
    named_members = 0
    for region in regions:
        members = region.get("members")
        if isinstance(members, list):
            member_count += len(members)
            named_members += sum(
                1
                for member in members
                if isinstance(member, dict) and _non_empty_string(member.get("mid")) and _non_empty_string(member.get("name"))
            )
    if not regions:
        return ValidationResult(False, "no region objects found")
    if member_count == 0:
        return ValidationResult(False, "no WMO members found in region objects")
    if named_members == 0:
        return ValidationResult(False, "members are missing mid/name fields")
    return ValidationResult(True, f"{len(regions)} regions, {member_count} members")


def validate_rainviewer_metadata(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    errors: list[str] = []
    if not isinstance(data.get("generated"), int):
        errors.append("generated must be an integer Unix timestamp")
    if not _non_empty_string(data.get("host")):
        errors.append("host must be present")
    radar = data.get("radar")
    if not isinstance(radar, dict):
        errors.append("radar object must be present")
    else:
        past = radar.get("past")
        if not isinstance(past, list) or not past:
            errors.append("radar.past must contain at least one frame")
        elif not _valid_radar_frame(past[0]):
            errors.append("radar.past[0] must include numeric time and string path")
    if errors:
        return ValidationResult(False, "; ".join(errors))
    past_count = len(data["radar"].get("past", []))
    nowcast_count = len(data["radar"].get("nowcast", []))
    return ValidationResult(True, f"{past_count} past frames, {nowcast_count} nowcast frames")


def validate_open_meteo_forecast(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    errors: list[str] = []
    current = data.get("current")
    hourly = data.get("hourly")
    daily = data.get("daily")
    if not isinstance(current, dict):
        errors.append("current object missing")
    else:
        if "temperature_2m" not in current:
            errors.append("current.temperature_2m missing")
        if "weather_code" not in current:
            errors.append("current.weather_code missing")
    if not _has_non_empty_time_series(hourly):
        errors.append("hourly.time missing or empty")
    if not _has_non_empty_time_series(daily):
        errors.append("daily.time missing or empty")
    if errors:
        return ValidationResult(False, "; ".join(errors))
    return ValidationResult(True, "current, hourly, and daily forecast blocks present")


def validate_nws_alerts(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    if data.get("type") != "FeatureCollection":
        return ValidationResult(False, "type must be FeatureCollection")
    features = data.get("features")
    if not isinstance(features, list):
        return ValidationResult(False, "features must be a list")
    if features:
        first = features[0]
        if not isinstance(first, dict) or not isinstance(first.get("properties"), dict):
            return ValidationResult(False, "alert features must include a properties object")
    return ValidationResult(True, f"{len(features)} active alert features")


def validate_met_no_compact(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    if data.get("type") != "Feature":
        return ValidationResult(False, "type must be Feature")
    properties = data.get("properties")
    if not isinstance(properties, dict):
        return ValidationResult(False, "properties object missing")
    timeseries = properties.get("timeseries")
    if not isinstance(timeseries, list) or not timeseries:
        return ValidationResult(False, "properties.timeseries must contain forecast entries")
    first = timeseries[0]
    if not isinstance(first, dict):
        return ValidationResult(False, "timeseries entries must be objects")
    details = _nested_dict(first, "data", "instant", "details")
    if details is None or "air_temperature" not in details:
        return ValidationResult(False, "timeseries[0].data.instant.details.air_temperature missing")
    return ValidationResult(True, f"{len(timeseries)} forecast timeseries entries")


def run_check(
    check: ContractCheck,
    *,
    cache_dir: Path,
    timeout_seconds: float,
    max_cache_age_seconds: float,
    force_refresh: bool,
    transport: Transport = None,
) -> CheckResult:
    fetch = fetch_json(
        check.url,
        cache_dir=cache_dir,
        timeout_seconds=timeout_seconds,
        max_cache_age_seconds=max_cache_age_seconds,
        force_refresh=force_refresh,
        transport=transport or default_transport,
    )
    source = fetch.cache_state if fetch.from_cache else "live"
    if fetch.error is not None:
        return CheckResult(check.key, check.name, check.url, check.docs_url, False, fetch.status, source, fetch.error)
    if fetch.body is None:
        return CheckResult(check.key, check.name, check.url, check.docs_url, False, fetch.status, source, "empty response body")
    if fetch.status < 200 or fetch.status >= 300:
        excerpt = fetch.body.decode("utf-8", errors="replace").strip().replace("\n", " ")[:180]
        message = f"HTTP {fetch.status}"
        if excerpt:
            message = f"{message}: {excerpt}"
        return CheckResult(check.key, check.name, check.url, check.docs_url, False, fetch.status, source, message)
    try:
        data = json.loads(fetch.body.decode("utf-8"))
    except json.JSONDecodeError as exc:
        return CheckResult(check.key, check.name, check.url, check.docs_url, False, fetch.status, source, f"invalid JSON: {exc}")
    validation = check.validator(data)
    return CheckResult(check.key, check.name, check.url, check.docs_url, validation.ok, fetch.status, source, validation.message)


def fetch_json(
    url: str,
    *,
    cache_dir: Path,
    timeout_seconds: float,
    max_cache_age_seconds: float,
    force_refresh: bool = False,
    transport: Transport = None,
) -> FetchResult:
    transport = transport or default_transport
    cache = _read_cache(cache_dir, url)
    if cache and not force_refresh and _cache_is_fresh(cache, max_cache_age_seconds):
        return FetchResult(
            status=int(cache.get("status", 200)),
            body=str(cache.get("body", "")).encode("utf-8"),
            headers=dict(cache.get("headers", {})),
            from_cache=True,
            cache_state="fresh-cache",
        )

    request_headers = {"User-Agent": USER_AGENT, "Accept": ACCEPT_HEADER}
    if cache:
        request_headers.update(_conditional_headers(cache))
    request = Request(url, headers=request_headers)

    try:
        status, headers, body = transport(request, timeout_seconds)
    except HTTPError as exc:
        if exc.code == 304 and cache and cache.get("body"):
            return FetchResult(
                status=200,
                body=str(cache.get("body", "")).encode("utf-8"),
                headers=dict(cache.get("headers", {})),
                from_cache=True,
                cache_state="not-modified-cache",
            )
        return FetchResult(
            status=exc.code,
            body=_safe_http_error_body(exc),
            headers=_headers_to_dict(exc.headers.items() if exc.headers else []),
            from_cache=False,
            cache_state="none",
            error=f"HTTP {exc.code} {exc.reason}".strip(),
        )
    except (TimeoutError, URLError, OSError) as exc:
        return FetchResult(
            status=0,
            body=None,
            headers={},
            from_cache=False,
            cache_state="none",
            error=f"request failed: {exc}",
        )

    normalized_headers = _headers_to_dict(headers.items())
    if 200 <= status < 300:
        _write_cache(cache_dir, url, status, normalized_headers, body)
    return FetchResult(
        status=status,
        body=body,
        headers=normalized_headers,
        from_cache=False,
        cache_state="none",
    )


def default_transport(request: Request, timeout_seconds: float) -> tuple[int, dict[str, str], bytes]:
    with urlopen(request, timeout=timeout_seconds) as response:
        return response.status, _headers_to_dict(response.headers.items()), response.read()


def _cache_path(cache_dir: Path, url: str) -> Path:
    digest = hashlib.sha256(url.encode("utf-8")).hexdigest()[:24]
    return cache_dir / f"{digest}.json"


def _read_cache(cache_dir: Path, url: str) -> JsonMap | None:
    path = _cache_path(cache_dir, url)
    if not path.exists():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    if not isinstance(data, dict) or data.get("url") != url:
        return None
    return data


def _write_cache(cache_dir: Path, url: str, status: int, headers: dict[str, str], body: bytes) -> None:
    cache_dir.mkdir(parents=True, exist_ok=True)
    path = _cache_path(cache_dir, url)
    path.write_text(
        json.dumps(
            {
                "url": url,
                "fetched_at": time.time(),
                "status": status,
                "headers": {
                    key: value
                    for key, value in headers.items()
                    if key in {"etag", "last-modified", "cache-control", "content-type"}
                },
                "body": body.decode("utf-8", errors="replace"),
            },
            indent=2,
            sort_keys=True,
        ),
        encoding="utf-8",
    )


def _cache_is_fresh(cache: JsonMap, max_cache_age_seconds: float) -> bool:
    try:
        fetched_at = float(cache.get("fetched_at", 0))
    except (TypeError, ValueError):
        return False
    return time.time() - fetched_at <= max_cache_age_seconds and bool(cache.get("body"))


def _conditional_headers(cache: JsonMap) -> dict[str, str]:
    headers = cache.get("headers")
    if not isinstance(headers, dict):
        return {}
    result: dict[str, str] = {}
    etag = headers.get("etag")
    last_modified = headers.get("last-modified")
    if isinstance(etag, str) and etag:
        result["If-None-Match"] = etag
    if isinstance(last_modified, str) and last_modified:
        result["If-Modified-Since"] = last_modified
    return result


def _headers_to_dict(items: Any) -> dict[str, str]:
    return {str(key).lower(): str(value) for key, value in items}


def _safe_http_error_body(exc: HTTPError) -> bytes:
    try:
        return exc.read()
    except OSError:
        return b""


def _non_empty_string(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _valid_radar_frame(value: Any) -> bool:
    return isinstance(value, dict) and isinstance(value.get("time"), int) and _non_empty_string(value.get("path"))


def _has_non_empty_time_series(value: Any) -> bool:
    return isinstance(value, dict) and isinstance(value.get("time"), list) and bool(value.get("time"))


def _nested_dict(data: JsonMap, *keys: str) -> JsonMap | None:
    current: Any = data
    for key in keys:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current if isinstance(current, dict) else None


def _write_report(path: Path, results: list[CheckResult]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(
            {
                "generatedAt": datetime.now(timezone.utc).isoformat(),
                "results": [result.to_report() for result in results],
            },
            indent=2,
            sort_keys=True,
        ),
        encoding="utf-8",
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
    checks = provider_checks()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE_DIR)
    parser.add_argument("--json-report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument("--max-cache-age-hours", type=float, default=DEFAULT_MAX_CACHE_AGE_SECONDS / 3600)
    parser.add_argument("--force-refresh", action="store_true")
    parser.add_argument("--check", action="append", choices=[check.key for check in checks])
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    selected_keys = set(args.check or [])
    checks = [check for check in provider_checks() if not selected_keys or check.key in selected_keys]
    max_cache_age_seconds = max(args.max_cache_age_hours, 0) * 3600
    print(f"Provider contract smoke checks: {len(checks)} endpoint(s), cache <= {args.max_cache_age_hours:g}h")
    results = [
        run_check(
            check,
            cache_dir=args.cache_dir,
            timeout_seconds=args.timeout,
            max_cache_age_seconds=max_cache_age_seconds,
            force_refresh=args.force_refresh,
        )
        for check in checks
    ]
    for result in results:
        status = "PASS" if result.ok else "FAIL"
        print(f"{status} {result.key} [{result.status} {result.source}] {result.message}")
        if not result.ok:
            print(f"  URL: {result.url}")
            print(f"  Docs: {result.docs_url}")
    _write_report(args.json_report, results)
    failures = [result for result in results if not result.ok]
    if failures:
        print(f"{len(failures)} provider contract check(s) failed. See {args.json_report}.")
        return 1
    print(f"All provider contract checks passed. Report: {args.json_report}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
