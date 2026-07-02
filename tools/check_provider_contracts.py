#!/usr/bin/env python3
"""Low-rate live contract smoke checks for public weather providers."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Callable
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
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
SYDNEY_LATITUDE = "-33.8688"
SYDNEY_LONGITUDE = "151.2093"
LONDON_LATITUDE = "51.5072"
LONDON_LONGITUDE = "-0.1276"
BERLIN_LATITUDE = "52.5200"
BERLIN_LONGITUDE = "13.4050"
COPENHAGEN_LATITUDE = "55.6761"
COPENHAGEN_LONGITUDE = "12.5683"
OTTAWA_LATITUDE = "45.4215"
OTTAWA_LONGITUDE = "-75.6972"
HONG_KONG_LATITUDE = "22.3027"
HONG_KONG_LONGITUDE = "114.1772"
PARIS_LATITUDE = "48.8566"
PARIS_LONGITUDE = "2.3522"
VIENNA_LATITUDE = "48.2082"
VIENNA_LONGITUDE = "16.3738"
HELSINKI_LATITUDE = "60.1699"
HELSINKI_LONGITUDE = "24.9384"

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
    url: str | None
    docs_url: str
    validator: Callable[[Any], ValidationResult]
    providers: tuple[str, ...] = ()
    data_types: tuple[str, ...] = ()
    coverage: str = ""
    schema_assertion: str = ""
    unavailable_policy: str = "Fail release verification on HTTP or schema drift; use a fresh cache only within the configured cache window."
    response_format: str = "json"
    required_env_vars: tuple[str, ...] = ()
    policy_only: bool = False


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
    url: str | None
    docs_url: str
    ok: bool
    status: int
    source: str
    message: str
    providers: tuple[str, ...]
    data_types: tuple[str, ...]
    coverage: str
    schema_assertion: str
    unavailable_policy: str

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
            "providers": list(self.providers),
            "dataTypes": list(self.data_types),
            "coverage": self.coverage,
            "schemaAssertion": self.schema_assertion,
            "unavailableProviderPolicy": self.unavailable_policy,
        }


def provider_checks() -> list[ContractCheck]:
    today = datetime.now(timezone.utc).date()
    bright_sky_start = (today - timedelta(days=1)).isoformat()
    bright_sky_end = (today + timedelta(days=1)).isoformat()
    eccc_today = today.strftime("%Y%m%d")
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
    open_meteo_air_quality_query = urlencode(
        {
            "latitude": NYC_LATITUDE,
            "longitude": NYC_LONGITUDE,
            "current": "us_aqi,european_aqi,pm10,pm2_5,ozone",
            "hourly": "us_aqi,pm2_5",
            "forecast_days": "1",
            "timezone": "UTC",
        }
    )
    open_meteo_minutely_query = urlencode(
        {
            "latitude": NYC_LATITUDE,
            "longitude": NYC_LONGITUDE,
            "minutely_15": "precipitation",
            "forecast_minutely_15": "24",
            "timezone": "UTC",
        }
    )
    open_meteo_bom_query = urlencode(
        {
            "latitude": SYDNEY_LATITUDE,
            "longitude": SYDNEY_LONGITUDE,
            "hourly": "temperature_2m,weather_code",
            "daily": "weather_code,temperature_2m_max,temperature_2m_min",
            "forecast_days": "1",
            "forecast_hours": "1",
            "timezone": "UTC",
        }
    )
    open_meteo_ukmo_query = urlencode(
        {
            "latitude": LONDON_LATITUDE,
            "longitude": LONDON_LONGITUDE,
            "models": "ukmo_seamless",
            "hourly": "temperature_2m,weather_code",
            "daily": "weather_code,temperature_2m_max,temperature_2m_min",
            "forecast_days": "1",
            "forecast_hours": "1",
            "timezone": "UTC",
        }
    )
    open_meteo_dmi_query = urlencode(
        {
            "latitude": COPENHAGEN_LATITUDE,
            "longitude": COPENHAGEN_LONGITUDE,
            "models": "dmi_seamless",
            "hourly": "temperature_2m,weather_code",
            "daily": "weather_code,temperature_2m_max,temperature_2m_min",
            "forecast_days": "1",
            "forecast_hours": "1",
            "timezone": "UTC",
        }
    )
    open_meteo_meteo_france_query = urlencode(
        {
            "latitude": PARIS_LATITUDE,
            "longitude": PARIS_LONGITUDE,
            "current": "temperature_2m,weather_code",
            "hourly": "temperature_2m,weather_code",
            "daily": "weather_code,temperature_2m_max,temperature_2m_min",
            "forecast_days": "1",
            "forecast_hours": "1",
            "timezone": "UTC",
        }
    )
    open_meteo_meteo_france_minutely_query = urlencode(
        {
            "latitude": PARIS_LATITUDE,
            "longitude": PARIS_LONGITUDE,
            "minutely_15": "precipitation",
            "forecast_minutely_15": "24",
            "timezone": "UTC",
        }
    )
    geosphere_nowcast_query = urlencode(
        {
            "lat_lon": f"{VIENNA_LATITUDE},{VIENNA_LONGITUDE}",
            "parameters": "rr",
            "forecast_offset": "0",
        }
    )
    geosphere_warnings_query = urlencode(
        {
            "lon": VIENNA_LONGITUDE,
            "lat": VIENNA_LATITUDE,
            "lang": "en",
        }
    )
    fmi_forecast_query = urlencode(
        {
            "service": "WFS",
            "version": "2.0.0",
            "request": "getFeature",
            "storedquery_id": "fmi::forecast::harmonie::surface::point::timevaluepair",
            "latlon": f"{HELSINKI_LATITUDE},{HELSINKI_LONGITUDE}",
            "parameters": (
                "Temperature,Humidity,Pressure,WindSpeedMS,WindDirection,WindGust,"
                "TotalCloudCover,Precipitation1h,Visibility,DewPoint,WeatherSymbol3"
            ),
            "timestep": "60",
        }
    )
    bright_sky_forecast_query = urlencode(
        {
            "lat": BERLIN_LATITUDE,
            "lon": BERLIN_LONGITUDE,
            "date": bright_sky_start,
            "last_date": bright_sky_end,
            "tz": "Etc/UTC",
        }
    )
    bright_sky_alert_query = urlencode({"lat": BERLIN_LATITUDE, "lon": BERLIN_LONGITUDE})
    eccc_forecast_query = urlencode(
        {
            "bbox": "-75.9,45.2,-75.4,45.7",
            "lang": "en",
            "f": "json",
            "limit": "10",
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
            coverage="global metadata",
            schema_assertion="top-level region list with member mid/name fields",
        ),
        ContractCheck(
            key="rainviewer-metadata",
            name="RainViewer weather-map metadata",
            url="https://api.rainviewer.com/public/weather-maps.json",
            docs_url="https://www.rainviewer.com/api/weather-maps-api.html",
            validator=validate_rainviewer_metadata,
            coverage="global radar metadata",
            schema_assertion="generated timestamp, tile host, and at least one radar.past frame",
        ),
        ContractCheck(
            key="open-meteo-forecast",
            name="Open-Meteo forecast",
            url=f"https://api.open-meteo.com/v1/forecast?{open_meteo_query}",
            docs_url="https://open-meteo.com/en/docs",
            validator=validate_open_meteo_forecast,
            providers=("OPEN_METEO",),
            data_types=("FORECAST",),
            coverage=f"New York, US ({NYC_LATITUDE},{NYC_LONGITUDE})",
            schema_assertion="current, hourly, and daily forecast blocks are present",
        ),
        ContractCheck(
            key="open-meteo-air-quality",
            name="Open-Meteo air quality",
            url=f"https://air-quality-api.open-meteo.com/v1/air-quality?{open_meteo_air_quality_query}",
            docs_url="https://open-meteo.com/en/docs/air-quality-api",
            validator=validate_open_meteo_air_quality,
            providers=("OPEN_METEO",),
            data_types=("AIR_QUALITY",),
            coverage=f"New York, US ({NYC_LATITUDE},{NYC_LONGITUDE})",
            schema_assertion="current AQI/pollutants and hourly AQI time series are present",
        ),
        ContractCheck(
            key="open-meteo-minutely",
            name="Open-Meteo 15-minute precipitation",
            url=f"https://api.open-meteo.com/v1/forecast?{open_meteo_minutely_query}",
            docs_url="https://open-meteo.com/en/docs",
            validator=validate_open_meteo_minutely,
            providers=("OPEN_METEO",),
            data_types=("MINUTELY",),
            coverage=f"New York, US ({NYC_LATITUDE},{NYC_LONGITUDE})",
            schema_assertion="minutely_15 precipitation time series is present",
        ),
        ContractCheck(
            key="open-meteo-bom",
            name="Open-Meteo BOM ACCESS-G forecast",
            url=f"https://api.open-meteo.com/v1/bom?{open_meteo_bom_query}",
            docs_url="https://open-meteo.com/en/docs/bom-api",
            validator=validate_open_meteo_model_forecast,
            providers=("OPEN_METEO_BOM",),
            data_types=("FORECAST",),
            coverage=f"Sydney, AU ({SYDNEY_LATITUDE},{SYDNEY_LONGITUDE})",
            schema_assertion="model endpoint returns hourly and daily forecast blocks",
        ),
        ContractCheck(
            key="open-meteo-ukmo",
            name="Open-Meteo UK Met Office forecast",
            url=f"https://api.open-meteo.com/v1/forecast?{open_meteo_ukmo_query}",
            docs_url="https://open-meteo.com/en/docs/ukmo-api",
            validator=validate_open_meteo_model_forecast,
            providers=("OPEN_METEO_UKMO",),
            data_types=("FORECAST",),
            coverage=f"London, UK ({LONDON_LATITUDE},{LONDON_LONGITUDE})",
            schema_assertion="Forecast API with models=ukmo_seamless returns hourly and daily blocks",
        ),
        ContractCheck(
            key="open-meteo-dmi",
            name="Open-Meteo DMI HARMONIE forecast",
            url=f"https://api.open-meteo.com/v1/forecast?{open_meteo_dmi_query}",
            docs_url="https://open-meteo.com/en/docs/dmi-api",
            validator=validate_open_meteo_model_forecast,
            providers=("OPEN_METEO_DMI",),
            data_types=("FORECAST",),
            coverage=f"Copenhagen, DK ({COPENHAGEN_LATITUDE},{COPENHAGEN_LONGITUDE})",
            schema_assertion="Forecast API with models=dmi_seamless returns hourly and daily blocks",
        ),
        ContractCheck(
            key="open-meteo-meteo-france",
            name="Open-Meteo Meteo-France forecast",
            url=f"https://api.open-meteo.com/v1/meteofrance?{open_meteo_meteo_france_query}",
            docs_url="https://open-meteo.com/en/docs/meteofrance-api",
            validator=validate_open_meteo_forecast,
            providers=("OPEN_METEO_METEO_FRANCE",),
            data_types=("FORECAST",),
            coverage=f"Paris, FR ({PARIS_LATITUDE},{PARIS_LONGITUDE})",
            schema_assertion="Meteo-France endpoint returns current, hourly, and daily forecast blocks",
        ),
        ContractCheck(
            key="open-meteo-meteo-france-minutely",
            name="Open-Meteo Meteo-France AROME nowcast",
            url=f"https://api.open-meteo.com/v1/meteofrance?{open_meteo_meteo_france_minutely_query}",
            docs_url="https://open-meteo.com/en/docs/meteofrance-api",
            validator=validate_open_meteo_minutely,
            providers=("OPEN_METEO_METEO_FRANCE",),
            data_types=("MINUTELY",),
            coverage=f"Paris, FR ({PARIS_LATITUDE},{PARIS_LONGITUDE})",
            schema_assertion="Meteo-France endpoint returns minutely_15 precipitation buckets",
        ),
        ContractCheck(
            key="open-meteo-kma-quarantine",
            name="Open-Meteo KMA quarantine policy",
            url=None,
            docs_url="https://open-meteo.com/en/docs/kma-api",
            validator=validate_policy_only,
            providers=("OPEN_METEO_KMA",),
            data_types=("FORECAST",),
            coverage="South Korea model wrapper",
            schema_assertion="provider remains non-selectable while upstream KMA/KIM updates are suspended",
            unavailable_policy="OPEN_METEO_KMA is intentionally absent from WeatherSourceProvider.forType() until a live KMA contract passes.",
            policy_only=True,
        ),
        ContractCheck(
            key="fmi-harmonie-forecast",
            name="FMI Harmonie WFS forecast",
            url=f"https://opendata.fmi.fi/wfs?{fmi_forecast_query}",
            docs_url="https://en.ilmatieteenlaitos.fi/open-data-manual-wfs-examples-and-guidelines",
            validator=validate_fmi_wfs_forecast,
            providers=("FMI",),
            data_types=("FORECAST",),
            coverage=f"Helsinki, FI ({HELSINKI_LATITUDE},{HELSINKI_LONGITUDE})",
            schema_assertion="WFS FeatureCollection includes point time-value series for Temperature, WindSpeedMS, and WeatherSymbol3",
            response_format="text",
        ),
        ContractCheck(
            key="nws-active-alerts",
            name="NWS active alerts",
            url=f"https://api.weather.gov/alerts/active?{nws_query}",
            docs_url="https://www.weather.gov/documentation/services-web-api",
            validator=validate_nws_alerts,
            providers=("NWS",),
            data_types=("ALERTS",),
            coverage=f"New York, US ({NYC_LATITUDE},{NYC_LONGITUDE})",
            schema_assertion="FeatureCollection with a features list",
        ),
        ContractCheck(
            key="met-no-complete",
            name="MET Norway complete forecast",
            url=f"https://api.met.no/weatherapi/locationforecast/2.0/complete?{met_no_query}",
            docs_url="https://api.met.no/weatherapi/locationforecast/2.0/documentation",
            validator=validate_met_no_compact,
            providers=("MET_NORWAY",),
            data_types=("FORECAST",),
            coverage=f"New York, US ({NYC_LATITUDE},{NYC_LONGITUDE})",
            schema_assertion="GeoJSON Feature with forecast timeseries and air temperature",
        ),
        ContractCheck(
            key="bright-sky-forecast",
            name="Bright Sky forecast",
            url=f"https://api.brightsky.dev/weather?{bright_sky_forecast_query}",
            docs_url="https://brightsky.dev/docs/",
            validator=validate_bright_sky_weather,
            providers=("BRIGHT_SKY",),
            data_types=("FORECAST",),
            coverage=f"Berlin, DE ({BERLIN_LATITUDE},{BERLIN_LONGITUDE})",
            schema_assertion="weather array with timestamped DWD entries",
        ),
        ContractCheck(
            key="bright-sky-alerts",
            name="Bright Sky alerts",
            url=f"https://api.brightsky.dev/alerts?{bright_sky_alert_query}",
            docs_url="https://brightsky.dev/docs/",
            validator=validate_bright_sky_alerts,
            providers=("BRIGHT_SKY",),
            data_types=("ALERTS",),
            coverage=f"Berlin, DE ({BERLIN_LATITUDE},{BERLIN_LONGITUDE})",
            schema_assertion="alerts array is present even when no active alerts exist",
        ),
        ContractCheck(
            key="environment-canada-forecast",
            name="Environment Canada forecast",
            url=f"https://api.weather.gc.ca/collections/citypageweather-realtime/items?{eccc_forecast_query}",
            docs_url="https://eccc-msc.github.io/open-data/msc-data/citypageweather/readme_citypageweather_en/",
            validator=validate_eccc_feature_collection,
            providers=("ENVIRONMENT_CANADA",),
            data_types=("FORECAST",),
            coverage=f"Ottawa, CA ({OTTAWA_LATITUDE},{OTTAWA_LONGITUDE})",
            schema_assertion="GeoJSON FeatureCollection with nearby city weather features",
        ),
        ContractCheck(
            key="environment-canada-alert-cap-index",
            name="Environment Canada CAP alert index",
            url=f"https://dd.weather.gc.ca/today/alerts/cap/{eccc_today}/",
            docs_url="https://eccc-msc.github.io/open-data/msc-data/alerts/readme_alerts-datamart_en/",
            validator=validate_eccc_cap_index,
            providers=("ENVIRONMENT_CANADA",),
            data_types=("ALERTS",),
            coverage="Canada CAP datamart current-day index",
            schema_assertion="current-day CAP index is reachable and exposes alert directories/files",
            response_format="text",
        ),
        ContractCheck(
            key="hko-forecast",
            name="Hong Kong Observatory 9-day forecast",
            url="https://data.weather.gov.hk/weatherAPI/opendata/weather.php?dataType=fnd&lang=en",
            docs_url="https://www.hko.gov.hk/en/weatherAPI/doc/files/HKO_Open_Data_API_Documentation.pdf",
            validator=validate_hko_forecast,
            providers=("HKO",),
            data_types=("FORECAST",),
            coverage=f"Hong Kong ({HONG_KONG_LATITUDE},{HONG_KONG_LONGITUDE})",
            schema_assertion="weatherForecast list with forecastDate, temperature, icon, and PSR fields",
        ),
        ContractCheck(
            key="hko-warning-info",
            name="Hong Kong Observatory warning information",
            url="https://data.weather.gov.hk/weatherAPI/opendata/weather.php?dataType=warningInfo&lang=en",
            docs_url="https://www.hko.gov.hk/en/weatherAPI/doc/files/HKO_Open_Data_API_Documentation.pdf",
            validator=validate_hko_warning_info,
            providers=("HKO",),
            data_types=("ALERTS",),
            coverage=f"Hong Kong ({HONG_KONG_LATITUDE},{HONG_KONG_LONGITUDE})",
            schema_assertion="empty no-alert object or details list with warningStatementCode/content entries",
        ),
        ContractCheck(
            key="geosphere-austria-nowcast",
            name="GeoSphere Austria INCA nowcast",
            url=f"https://dataset.api.hub.geosphere.at/v1/timeseries/forecast/nowcast-v1-15min-1km?{geosphere_nowcast_query}",
            docs_url="https://data.hub.geosphere.at/en/dataset/nowcast-v1-15min-1km",
            validator=validate_geosphere_nowcast,
            providers=("GEOSPHERE_AUSTRIA",),
            data_types=("MINUTELY",),
            coverage=f"Vienna, AT ({VIENNA_LATITUDE},{VIENNA_LONGITUDE})",
            schema_assertion="FeatureCollection has timestamps and rr precipitation buckets",
        ),
        ContractCheck(
            key="geosphere-austria-warnings",
            name="GeoSphere Austria point warnings",
            url=f"https://warnungen.zamg.at/wsapp/api/getWarningsForCoords?{geosphere_warnings_query}",
            docs_url="https://openapi.hub.geosphere.at/warnapi/v1/",
            validator=validate_geosphere_warnings,
            providers=("GEOSPHERE_AUSTRIA",),
            data_types=("ALERTS",),
            coverage=f"Vienna, AT ({VIENNA_LATITUDE},{VIENNA_LONGITUDE})",
            schema_assertion="GeoJSON Feature response has a properties.warnings list",
        ),
        ContractCheck(
            key="meteoalarm-germany",
            name="MeteoAlarm Germany warnings",
            url="https://feeds.meteoalarm.org/api/v1/warnings/feeds-germany",
            docs_url="https://feeds.meteoalarm.org/",
            validator=validate_meteoalarm_warnings,
            providers=("METEOALARM",),
            data_types=("ALERTS",),
            coverage=f"Germany feed slug ({BERLIN_LATITUDE},{BERLIN_LONGITUDE})",
            schema_assertion="warnings array with nested alert payload when active warnings exist",
        ),
        ContractCheck(
            key="jma-extra-xml",
            name="JMA extra warnings feed",
            url="https://www.data.jma.go.jp/developer/xml/feed/extra.xml",
            docs_url="https://www.data.jma.go.jp/developer/",
            validator=validate_jma_atom_feed,
            providers=("JMA",),
            data_types=("ALERTS",),
            coverage="Japan high-frequency warnings feed",
            schema_assertion="Atom XML feed with title and entry/link structure",
            response_format="text",
        ),
        ContractCheck(
            key="openweathermap-forecast-alerts",
            name="OpenWeatherMap forecast and alerts",
            url="https://api.openweathermap.org/data/3.0/onecall?lat=40.7128&lon=-74.0060&appid={api_key}&units=metric&exclude=minutely",
            docs_url="https://openweathermap.org/api/one-call-3",
            validator=validate_owm_onecall,
            providers=("OPEN_WEATHER_MAP",),
            data_types=("FORECAST", "ALERTS"),
            coverage=f"New York, US ({NYC_LATITUDE},{NYC_LONGITUDE})",
            schema_assertion="One Call response includes current, hourly, daily, and optional alerts",
            unavailable_policy="If OPENWEATHERMAP_API_KEY or OWM_API_KEY is unset, live validation is skipped but the key-required provider remains covered by this contract.",
            required_env_vars=("OPENWEATHERMAP_API_KEY", "OWM_API_KEY"),
        ),
        ContractCheck(
            key="openweathermap-air-quality",
            name="OpenWeatherMap air pollution",
            url="https://api.openweathermap.org/data/2.5/air_pollution?lat=40.7128&lon=-74.0060&appid={api_key}",
            docs_url="https://openweathermap.org/api/air-pollution",
            validator=validate_owm_air_pollution,
            providers=("OPEN_WEATHER_MAP",),
            data_types=("AIR_QUALITY",),
            coverage=f"New York, US ({NYC_LATITUDE},{NYC_LONGITUDE})",
            schema_assertion="air_pollution response includes list[0].main.aqi",
            unavailable_policy="If OPENWEATHERMAP_API_KEY or OWM_API_KEY is unset, live validation is skipped but the key-required provider remains covered by this contract.",
            required_env_vars=("OPENWEATHERMAP_API_KEY", "OWM_API_KEY"),
        ),
        ContractCheck(
            key="pirate-weather-forecast",
            name="Pirate Weather forecast",
            url="https://api.pirateweather.net/forecast/{api_key}/40.7128,-74.0060?units=si&exclude=minutely",
            docs_url="https://docs.pirateweather.net/en/latest/API/",
            validator=validate_pirate_weather,
            providers=("PIRATE_WEATHER",),
            data_types=("FORECAST",),
            coverage=f"New York, US ({NYC_LATITUDE},{NYC_LONGITUDE})",
            schema_assertion="forecast response includes currently, hourly, and daily blocks",
            unavailable_policy="If PIRATE_WEATHER_API_KEY is unset, live validation is skipped but the key-required provider remains covered by this contract.",
            required_env_vars=("PIRATE_WEATHER_API_KEY",),
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


def validate_open_meteo_model_forecast(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    errors: list[str] = []
    if not _has_non_empty_time_series(data.get("hourly")):
        errors.append("hourly.time missing or empty")
    if not _has_non_empty_time_series(data.get("daily")):
        errors.append("daily.time missing or empty")
    if errors:
        return ValidationResult(False, "; ".join(errors))
    return ValidationResult(True, "hourly and daily model forecast blocks present")


def validate_open_meteo_air_quality(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    current = data.get("current")
    hourly = data.get("hourly")
    errors: list[str] = []
    if not isinstance(current, dict):
        errors.append("current object missing")
    elif "us_aqi" not in current and "european_aqi" not in current:
        errors.append("current AQI fields missing")
    if not _has_non_empty_time_series(hourly):
        errors.append("hourly.time missing or empty")
    elif not isinstance(hourly.get("us_aqi"), list):
        errors.append("hourly.us_aqi missing")
    if errors:
        return ValidationResult(False, "; ".join(errors))
    return ValidationResult(True, "current AQI and hourly AQI series present")


def validate_open_meteo_minutely(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    minutely = data.get("minutely_15")
    if not _has_non_empty_time_series(minutely):
        return ValidationResult(False, "minutely_15.time missing or empty")
    if not isinstance(minutely.get("precipitation"), list):
        return ValidationResult(False, "minutely_15.precipitation missing")
    return ValidationResult(True, f"{len(minutely['time'])} minutely precipitation buckets")


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


def validate_bright_sky_weather(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    weather = data.get("weather")
    if not isinstance(weather, list) or not weather:
        return ValidationResult(False, "weather must contain at least one entry")
    first = weather[0]
    if not isinstance(first, dict) or not _non_empty_string(first.get("timestamp")):
        return ValidationResult(False, "weather[0].timestamp missing")
    return ValidationResult(True, f"{len(weather)} Bright Sky weather entries")


def validate_bright_sky_alerts(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    alerts = data.get("alerts")
    if not isinstance(alerts, list):
        return ValidationResult(False, "alerts must be a list")
    return ValidationResult(True, f"{len(alerts)} Bright Sky alerts")


def validate_eccc_feature_collection(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    if data.get("type") != "FeatureCollection":
        return ValidationResult(False, "type must be FeatureCollection")
    features = data.get("features")
    if not isinstance(features, list) or not features:
        return ValidationResult(False, "features must contain nearby city weather entries")
    first = features[0]
    if not isinstance(first, dict) or not isinstance(first.get("properties"), dict):
        return ValidationResult(False, "features[0].properties missing")
    return ValidationResult(True, f"{len(features)} ECCC city weather features")


def validate_eccc_cap_index(data: Any) -> ValidationResult:
    if not isinstance(data, str):
        return ValidationResult(False, "expected text/html index")
    if "Index of /today/alerts/cap/" not in data:
        return ValidationResult(False, "CAP index title missing")
    if ".cap" not in data and "LAND/" not in data and "WATR/" not in data:
        return ValidationResult(False, "CAP index has no alert files or LAND/WATR directories")
    return ValidationResult(True, "current-day ECCC CAP index reachable")


def validate_hko_forecast(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    forecasts = data.get("weatherForecast")
    if not isinstance(forecasts, list) or not forecasts:
        return ValidationResult(False, "weatherForecast must contain 9-day entries")
    first = forecasts[0]
    if not isinstance(first, dict):
        return ValidationResult(False, "weatherForecast entries must be objects")
    errors: list[str] = []
    if not _non_empty_string(first.get("forecastDate")):
        errors.append("forecastDate missing")
    if not isinstance(first.get("forecastMaxtemp"), dict):
        errors.append("forecastMaxtemp missing")
    if not isinstance(first.get("forecastMintemp"), dict):
        errors.append("forecastMintemp missing")
    if not isinstance(first.get("ForecastIcon"), int):
        errors.append("ForecastIcon missing")
    if not _non_empty_string(first.get("PSR")):
        errors.append("PSR missing")
    if errors:
        return ValidationResult(False, "; ".join(errors))
    return ValidationResult(True, f"{len(forecasts)} HKO forecast days")


def validate_hko_warning_info(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    details = data.get("details")
    if details is None:
        return ValidationResult(True, "no active HKO warnings")
    if not isinstance(details, list):
        return ValidationResult(False, "details must be a list when present")
    if details:
        first = details[0]
        if not isinstance(first, dict):
            return ValidationResult(False, "warning details must be objects")
        if not _non_empty_string(first.get("warningStatementCode")):
            return ValidationResult(False, "warningStatementCode missing")
        if not isinstance(first.get("contents"), list):
            return ValidationResult(False, "contents list missing")
    return ValidationResult(True, f"{len(details)} HKO warning detail entries")


def validate_geosphere_nowcast(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    timestamps = data.get("timestamps")
    features = data.get("features")
    errors: list[str] = []
    if not isinstance(timestamps, list) or not timestamps:
        errors.append("timestamps missing or empty")
    if not isinstance(features, list) or not features:
        errors.append("features missing or empty")
    else:
        first = features[0]
        properties = first.get("properties") if isinstance(first, dict) else None
        parameters = properties.get("parameters") if isinstance(properties, dict) else None
        rr = parameters.get("rr") if isinstance(parameters, dict) else None
        data_values = rr.get("data") if isinstance(rr, dict) else None
        if not isinstance(data_values, list) or not data_values:
            errors.append("features[0].properties.parameters.rr.data missing or empty")
    if errors:
        return ValidationResult(False, "; ".join(errors))
    return ValidationResult(True, f"{len(timestamps)} INCA nowcast timestamps")


def validate_geosphere_warnings(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    if data.get("type") != "Feature":
        return ValidationResult(False, "type must be Feature")
    properties = data.get("properties")
    if not isinstance(properties, dict):
        return ValidationResult(False, "properties object missing")
    warnings = properties.get("warnings")
    if not isinstance(warnings, list):
        return ValidationResult(False, "properties.warnings must be a list")
    if warnings:
        first = warnings[0]
        if not isinstance(first, dict):
            return ValidationResult(False, "warning entries must be objects")
        if "warnstufeid" not in first or "warntypid" not in first:
            return ValidationResult(False, "warning entries must include warnstufeid and warntypid")
    return ValidationResult(True, f"{len(warnings)} GeoSphere point warnings")


def validate_fmi_wfs_forecast(data: Any) -> ValidationResult:
    if not isinstance(data, str):
        return ValidationResult(False, "expected WFS XML text")
    errors: list[str] = []
    if "FeatureCollection" not in data:
        errors.append("FeatureCollection missing")
    if "PointTimeSeriesObservation" not in data:
        errors.append("PointTimeSeriesObservation missing")
    if "MeasurementTVP" not in data:
        errors.append("MeasurementTVP missing")
    for parameter in ("Temperature", "WindSpeedMS", "WeatherSymbol3"):
        if f"param={parameter}" not in data:
            errors.append(f"{parameter} observedProperty missing")
    if errors:
        return ValidationResult(False, "; ".join(errors))
    return ValidationResult(True, f"{data.count('MeasurementTVP')} FMI time-value pairs")


def validate_meteoalarm_warnings(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    warnings = data.get("warnings")
    if not isinstance(warnings, list):
        return ValidationResult(False, "warnings must be a list")
    if warnings:
        first = warnings[0]
        if not isinstance(first, dict):
            return ValidationResult(False, "warning entries must be objects")
        alert = first.get("alert")
        if alert is not None and not isinstance(alert, dict):
            return ValidationResult(False, "warning.alert must be an object when present")
    return ValidationResult(True, f"{len(warnings)} MeteoAlarm warnings")


def validate_jma_atom_feed(data: Any) -> ValidationResult:
    if not isinstance(data, str):
        return ValidationResult(False, "expected Atom XML text")
    if "<feed" not in data or "<title>" not in data:
        return ValidationResult(False, "Atom feed/title missing")
    if "<entry>" not in data and "<link" not in data:
        return ValidationResult(False, "Atom feed has no entries or links")
    return ValidationResult(True, "JMA Atom feed reachable")


def validate_owm_onecall(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    errors: list[str] = []
    if not isinstance(data.get("current"), dict):
        errors.append("current object missing")
    if not isinstance(data.get("hourly"), list):
        errors.append("hourly list missing")
    if not isinstance(data.get("daily"), list):
        errors.append("daily list missing")
    if errors:
        return ValidationResult(False, "; ".join(errors))
    return ValidationResult(True, "current, hourly, and daily One Call blocks present")


def validate_owm_air_pollution(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    items = data.get("list")
    if not isinstance(items, list) or not items:
        return ValidationResult(False, "list must contain pollution entries")
    main = items[0].get("main") if isinstance(items[0], dict) else None
    if not isinstance(main, dict) or "aqi" not in main:
        return ValidationResult(False, "list[0].main.aqi missing")
    return ValidationResult(True, f"{len(items)} air pollution entries")


def validate_pirate_weather(data: Any) -> ValidationResult:
    if not isinstance(data, dict):
        return ValidationResult(False, "expected a JSON object")
    errors: list[str] = []
    if not isinstance(data.get("currently"), dict):
        errors.append("currently object missing")
    if not isinstance(data.get("hourly"), dict):
        errors.append("hourly object missing")
    if not isinstance(data.get("daily"), dict):
        errors.append("daily object missing")
    if errors:
        return ValidationResult(False, "; ".join(errors))
    return ValidationResult(True, "currently, hourly, and daily blocks present")


def validate_policy_only(data: Any) -> ValidationResult:
    del data
    return ValidationResult(True, "policy-only contract")


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
    if check.policy_only:
        return _check_result(check, True, 0, "policy", check.unavailable_policy)
    url = _resolve_check_url(check)
    if url is None:
        env_names = " or ".join(check.required_env_vars)
        return _check_result(
            check,
            True,
            0,
            "credential-policy",
            f"live check skipped because {env_names} is not set; {check.unavailable_policy}",
        )
    fetch = fetch_json(
        url,
        cache_dir=cache_dir,
        timeout_seconds=timeout_seconds,
        max_cache_age_seconds=max_cache_age_seconds,
        force_refresh=force_refresh,
        transport=transport or default_transport,
    )
    source = fetch.cache_state if fetch.from_cache else "live"
    if fetch.error is not None:
        return _check_result(check, False, fetch.status, source, fetch.error, url=url)
    if fetch.body is None:
        return _check_result(check, False, fetch.status, source, "empty response body", url=url)
    if fetch.status < 200 or fetch.status >= 300:
        excerpt = fetch.body.decode("utf-8", errors="replace").strip().replace("\n", " ")[:180]
        message = f"HTTP {fetch.status}"
        if excerpt:
            message = f"{message}: {excerpt}"
        return _check_result(check, False, fetch.status, source, message, url=url)
    if check.response_format == "text":
        data = fetch.body.decode("utf-8", errors="replace")
    else:
        try:
            data = json.loads(fetch.body.decode("utf-8"))
        except json.JSONDecodeError as exc:
            return _check_result(check, False, fetch.status, source, f"invalid JSON: {exc}", url=url)
    validation = check.validator(data)
    return _check_result(check, validation.ok, fetch.status, source, validation.message, url=url)


def _check_result(
    check: ContractCheck,
    ok: bool,
    status: int,
    source: str,
    message: str,
    *,
    url: str | None = None,
) -> CheckResult:
    return CheckResult(
        key=check.key,
        name=check.name,
        url=check.url if url is None else url,
        docs_url=check.docs_url,
        ok=ok,
        status=status,
        source=source,
        message=message,
        providers=check.providers,
        data_types=check.data_types,
        coverage=check.coverage,
        schema_assertion=check.schema_assertion,
        unavailable_policy=check.unavailable_policy,
    )


def _resolve_check_url(check: ContractCheck) -> str | None:
    if check.url is None:
        return None
    if not check.required_env_vars:
        return check.url
    api_key = next((os.environ[name] for name in check.required_env_vars if os.environ.get(name)), None)
    if api_key is None:
        return None
    return check.url.replace("{api_key}", quote(api_key, safe=""))


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
