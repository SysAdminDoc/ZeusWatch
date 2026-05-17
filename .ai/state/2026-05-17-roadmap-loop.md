# Roadmap Continuation State - 2026-05-17

## Batch: N-1 Local Localization Gate

Implemented local N-1 work:

- Added `tools/check_localization.py`.
- Added CI step `Check hardcoded user-facing strings`.
- Moved radar, report FAB, current weather notification, crash dialog, share
  text/image, Wear tile, driving alert, and health alert copy onto string
  resources.
- Converted `DrivingAlert` and `HealthAlert` models from English message text
  to resource-backed message metadata so Today cards and health notifications
  localize at render/delivery time.
- Completed app Spanish string coverage: 925 default strings and 925 Spanish
  strings.
- Fixed the `forecast_precip_rain_next_hours` placeholder mismatch caught by
  Android lint.
- Added `docs/TRANSLATION.md` with local checks and Weblate component setup
  notes.

## Verification

- `python tools/check_localization.py`
- `git diff --check`
- `.\gradlew.bat :app:testStandardDebugUnitTest --tests com.sysadmindoc.nimbus.util.DrivingConditionEvaluatorTest --tests com.sysadmindoc.nimbus.util.HealthAlertEvaluatorTest --console=plain`
- `.\gradlew.bat :wear:testDebugUnitTest --console=plain`
- `.\gradlew.bat lintStandardDebug --console=plain`

## Remaining N-1 Boundary

External service/human work remains:

- Connect the repository to a Weblate project.
- Merge at least three community-maintained locales.

This requires Weblate service access and translators; it is the current blocker
before N-1 can be marked fully closed.

## Batch: N-2 Certificate Pins

Implemented the local N-2 work:

- Fixed `tools/capture_api_pins.sh` so it no longer trips `set -u` before
  processing captured certificates.
- Added `tools/capture_api_pins.ps1` for Windows release runs. It locates
  OpenSSL on PATH or in standard Git for Windows install locations.
- Captured live SPKI pins on 2026-05-17 for:
  - `api.openweathermap.org` (OpenWeatherMap forecast and OWM AQI).
  - `api.pirateweather.net` (Pirate Weather path-key API).
- Populated `ApiCertificatePins.hostPins` with the live leaf and
  intermediate/root backup pins.
- Updated `ApiCertificatePinsTest` to assert current keyed-host coverage,
  active pinner construction, `sha256/` formatting, non-placeholder values,
  and duplicate-free pin sets.
- Updated `docs/RELEASE.md` to document Bash and PowerShell pin capture and to
  clarify that SPKI pins are public hashes committed to source control.

## Remaining N-2 Boundary

No local N-2 blocker remains. Future release work still needs to re-run the
capture scripts and update `ApiCertificatePins.hostPins` if either keyed host
rotates its leaf or intermediate certificate chain.

## Batch: N-3 Australian BOM Forecast Path

Implemented the safe indirect N-3 path:

- Added `WeatherSourceProvider.OPEN_METEO_BOM` with the user-facing label
  `Open-Meteo + BOM ACCESS-G`.
- Added `OpenMeteoApi.getBomForecast()` for Open-Meteo's documented `/v1/bom`
  endpoint, using a BOM-specific variable set that avoids unsupported regular
  Forecast API fields.
- Added `WeatherRepository.getBomWeatherDirect()` and shared Open-Meteo fetch
  plumbing.
- Updated the Open-Meteo mapper so hourly-only responses can still populate
  current conditions from the nearest hourly bucket.
- Routed the new provider through `WeatherSourceManager`.
- Added tests for provider routing and hourly-only BOM mapping.

## Remaining N-3 Boundary

No direct BOM app endpoint was used. The direct `api.weather.bom.gov.au` path
remains intentionally out of scope until BOM publishes stable open-data terms
for that interface. Australian severe-weather warnings still rely on the
existing alert-source fallback surface rather than a new BOM warnings adapter.

## Batch: N-4 Wear Complication Suite

Implemented the local N-4 work:

- Added `SMALL_IMAGE` support to the Wear complication manifest.
- Added `ic_complication_weather` for icon-capable watch-face slots.
- Extracted `WeatherComplicationDataFactory` so complication data building is
  testable without invoking the Hilt service directly.
- Preserved and tested `SHORT_TEXT`, `LONG_TEXT`, and `RANGED_VALUE` output.
- Made `LONG_TEXT` include a high/low title and localized all preview/current
  complication copy through Wear string resources.
- Added Robolectric-backed factory tests for preview/current data for every
  declared type, UV clamping, high/low title text, and small-image propagation.
- Fixed the Wear tile humidity/wind string format mismatch caught by Wear lint.
- Added `docs/WEAR_OS.md` with complication install/use notes.

## Remaining N-4 Boundary

No local N-4 blocker remains. Follow-up Wear test work continues under N-6 for
`WeatherTileService` callback behavior and `WearWeatherRepository.getCurrentWeather`
with mocked OkHttp.

## Batch: N-5 WFF Weather Interoperability

Closed N-5 as a compatibility decision:

- Reviewed the current Android WFF weather guide, WFF SourceType reference, WFF
  overview/release notes, and AndroidX Wear Watchface release notes.
- Confirmed WFF weather data is consumed by system-rendered `[WEATHER.*]`
  expressions and guarded by `[WEATHER.IS_AVAILABLE]` / `[WEATHER.IS_ERROR]`.
- Confirmed AndroidX removed the briefly exposed default weather data-source
  path in `wear-watchface` 1.2.0 beta02.
- Checked local ZeusWatch DataLayer sync and local Gradle cache for a public
  weather publisher/provider API; none was present.
- Added `docs/WFF_WEATHER_INTEROP.md` with evidence, a compatibility matrix, and
  a future API watchlist.
- Updated `docs/WEAR_OS.md` to point users at complications for ZeusWatch data
  on user-selected watch faces.

## Remaining N-5 Boundary

No local implementation is appropriate until Android or AndroidX publishes a
normal-app weather publisher API that can write to the Wear OS system weather
store consumed by WFF.

## Batch: N-6 Wear OS Test Coverage

Closed N-6 locally:

- Added mocked-OkHttp `WearWeatherRepository.getCurrentWeather` coverage for a
  successful direct Open-Meteo response, non-2xx API failure, and fresh
  phone-sync short-circuiting.
- Added `WeatherTileRequestRunner` so the `CallbackToFutureAdapter` tile request
  path is testable without requiring Hilt service creation in Robolectric.
- Added `WeatherTileService.loadTileData()` coverage to prove fresh synced data
  avoids location/repository fallback work.
- Added tile resource future coverage.
- Verified full Wear unit tests, Detekt, and Wear lint.

## Remaining N-6 Boundary

No local N-6 blocker remains. The remaining Wear reliability work should move
under normal regression expansion, not this roadmap item.
