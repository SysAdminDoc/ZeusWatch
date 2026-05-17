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
