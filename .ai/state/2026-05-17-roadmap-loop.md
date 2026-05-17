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

## Batch: N-8 Detekt Baseline Reduction - Radar Preview

Reduced the Detekt baseline by one ID:

- Extracted `RadarPreviewCard` into focused private composables for the map
  image stack, gradient, empty state, status pill, caption, and footer.
- Regenerated `config/detekt/baseline.xml`; the
  `LongMethod:RadarPreviewCard.kt$@Composable fun RadarPreviewCard(...)` entry
  is removed.
- Baseline count is now 21 IDs.

## Remaining N-8 Boundary

N-8 remains open. The next low-risk i18n-era targets are
`CurrentConditionsHeader` and `CustomAlertsScreen.RuleEditor`; broader entries
such as `MainScreen`, `SettingsScreen`, and `RadarScreen` need larger
feature-preserving extraction passes.

## Batch: N-8 Detekt Baseline Reduction - Current Conditions

Reduced the Detekt baseline by one more ID:

- Extracted `CurrentConditionsHeader` copy calculation, location/daylight row,
  main conditions row, weather-icon frame, and metric row into focused helpers.
- Regenerated `config/detekt/baseline.xml`; the
  `LongMethod:CurrentConditionsHeader.kt$@Composable fun CurrentConditionsHeader(...)`
  entry is removed.
- Baseline count is now 20 IDs.

## Remaining N-8 Boundary

N-8 remains open. The remaining low-risk i18n-era long-method target is
`CustomAlertsScreen.RuleEditor`; broader entries such as `MainScreen`,
`SettingsScreen`, `RadarScreen`, and `WidgetRefreshWorker` need larger
feature-preserving extraction passes.

## Batch: N-8 Detekt Baseline Reduction - Custom Alerts

Reduced the Detekt baseline by one more ID:

- Extracted `CustomAlertsScreen.RuleEditor` into private composables for the
  header, metric picker, operator picker, threshold input/feedback, enabled
  toggle, and action row.
- Regenerated `config/detekt/baseline.xml`; the
  `LongMethod:CustomAlertsScreen.kt$@Composable private fun RuleEditor(...)`
  entry is removed.
- Baseline count is now 19 IDs.

## Remaining N-8 Boundary

N-8 remains open. The i18n-era long-method cleanup is complete; remaining
entries are larger structural extractions around `MainScreen`, `SettingsScreen`,
`RadarScreen`, `CompareScreen`, `WidgetRefreshWorker`, `SunArc`,
`TemperatureGraph`, and long-parameter constructors/functions.

## Batch: N-8 Detekt Baseline Reduction - Sun Arc

Reduced the Detekt baseline by one more ID:

- Extracted `SunArc` time parsing, semantic summary, twilight/moon state, and
  drawing operations into focused helpers.
- Kept the public `SunArc` composable signature unchanged.
- Regenerated `config/detekt/baseline.xml`; the
  `LongMethod:SunMoonArc.kt$@Composable fun SunArc(...)` entry is removed.
- Baseline count is now 18 IDs.

## Remaining N-8 Boundary

N-8 remains open. Remaining entries are larger structural extractions around
`MainScreen`, `SettingsScreen`, `RadarScreen`, `CompareScreen`,
`WidgetRefreshWorker`, `TemperatureGraph`, and long-parameter
constructors/functions.

## Batch: N-8 Detekt Baseline Reduction - Temperature Graph

Reduced the Detekt baseline by one more ID:

- Extracted `TemperatureGraph` metrics, path building, precipitation bars,
  normal band drawing, feels-like overlay, high/low markers, time labels, and
  inspection tooltip into focused helpers.
- Kept the public `TemperatureGraph` composable signature type-compatible while
  replacing the fully qualified `LocalDateTime` reference with an import.
- Regenerated `config/detekt/baseline.xml`; the
  `LongMethod:TemperatureGraph.kt$@Composable fun TemperatureGraph(...)` entry
  is removed.
- Baseline count is now 17 IDs.

## Remaining N-8 Boundary

N-8 remains open. Remaining entries are larger structural extractions around
`MainScreen`, `SettingsScreen`, `RadarScreen`, `CompareScreen`,
`WidgetRefreshWorker`, and long-parameter constructors/functions.

## Batch: N-8 Detekt Baseline Reduction - Widget Refresh Worker

Reduced the Detekt baseline by one more ID:

- Extracted `WidgetRefreshWorker.doWork` battery gating, empty-state clearing,
  primary location refresh, mapped-widget refresh, orphan cleanup, widget
  updates, persistent notification updates, and saved-location cache warming
  into focused helpers.
- Preserved the existing success/retry behavior through a small
  `WidgetRefreshState` accumulator.
- Regenerated `config/detekt/baseline.xml`; the
  `CyclomaticComplexMethod:WidgetRefreshWorker.doWork` entry is removed.
- Baseline count is now 16 IDs.

## Remaining N-8 Boundary

N-8 remains open. Remaining entries are larger structural extractions around
`MainScreen`, `SettingsScreen`, `RadarScreen`, `CompareScreen`, and
long-parameter constructors/functions.

## Batch: N-8 Detekt Baseline Reduction - Synced Weather Payload

Reduced the Detekt baseline by one more ID:

- Replaced the 17-parameter `SyncedWeatherStore.save(...)` API with a typed
  `SyncedWeatherPayload` data object.
- Updated `WeatherDataListenerService`, `SyncedWeatherStoreTest`, and
  `WearWeatherRepositoryTest` call sites to construct the payload explicitly.
- Regenerated `config/detekt/baseline.xml`; the
  `LongParameterList:SyncedWeatherStore.save` entry is removed.
- Baseline count is now 15 IDs.

## Remaining N-8 Boundary

N-8 remains open. Remaining entries are larger structural extractions around
`MainScreen`, `SettingsScreen`, `RadarScreen`, `CompareScreen`, and the
`MainViewModel` constructor seam.

## Batch: N-8 Detekt Baseline Reduction - Compare Screen

Reduced the Detekt baseline by one more ID:

- Split the route-level `CompareScreen` composable into route wiring,
  full-screen state handling, scrollable content, selector controls, loaded
  weather summary, condition card, and metric row helpers.
- Preserved the existing `CompareScreen` public signature and all current
  comparison states.
- Regenerated `config/detekt/baseline.xml`; the
  `LongMethod:CompareScreen.kt$@Composable fun CompareScreen(...)` entry is
  removed.
- Baseline count is now 14 IDs.

## Remaining N-8 Boundary

N-8 remains open. Remaining entries are larger structural extractions around
`MainScreen`, `SettingsScreen`, `RadarScreen`, and the `MainViewModel`
constructor seam.

## Batch: N-8 Detekt Baseline Reduction - Radar Screen

Reduced the Detekt baseline by four IDs:

- Split `RadarScreen` and `RadarTab` route composables into shared coordinate,
  render-state, action, effect, native/web/offline map, playback/status, FAB,
  and report-sheet helpers.
- Preserved full-screen and embedded-tab behavior while removing duplicate
  native radar and report-sheet orchestration.
- Regenerated `config/detekt/baseline.xml`; the `CyclomaticComplexMethod` and
  `LongMethod` entries for both `RadarScreen` and `RadarTab` are removed.
- Baseline count is now 10 IDs.

## Remaining N-8 Boundary

N-8 remains open. Remaining entries are larger structural extractions around
`MainScreen`, `SettingsScreen`, and the `MainViewModel` constructor seam.

## Batch: N-8 Detekt Baseline Reduction - Main ViewModel Dependencies

Reduced the Detekt baseline by one more ID:

- Introduced `MainViewModelDependencies`, a Hilt-injectable data object that
  carries the ViewModel's repository, settings, sync, location, summary, and
  dispatcher dependencies.
- Kept the existing private property names inside `MainViewModel` to avoid a
  broad behavioral rewrite.
- Updated `MainViewModelTest` to construct the dependency bundle explicitly.
- Regenerated `config/detekt/baseline.xml`; the
  `LongParameterList:MainViewModel` constructor entry is removed.
- Baseline count is now 9 IDs.

## Remaining N-8 Boundary

N-8 remains open. Remaining entries are structural Compose extractions around
`MainScreen` and `SettingsScreen`.

## Batch: N-8 Detekt Baseline Reduction - Settings Screen

Reduced the Detekt baseline by two IDs:

- Introduced `SettingsActions` to replace the long callback parameter list on
  `SettingsContent`.
- Split the monolithic settings content body into category-level helpers for
  appearance, forecast, alerts, and advanced settings plus focused section
  helpers for cards, units, notifications, data display, health, data sources,
  API keys, advanced cache settings, and about rows.
- Regenerated `config/detekt/baseline.xml`; the `CyclomaticComplexMethod` and
  `LongMethod` entries for `SettingsContent` are removed.
- Baseline count is now 7 IDs.

## Remaining N-8 Boundary

N-8 remains open. The remaining Detekt baseline entries are all in
`MainScreen`.

## Batch: N-8 Detekt Baseline Reduction - Main Screen Card Renderer

Reduced the Detekt baseline by three IDs:

- Replaced the long `RenderCard` parameter list with `CardRenderContext`.
- Split the monolithic card `when` into forecast, atmosphere, lifestyle, and
  detail card-family renderers.
- Regenerated `config/detekt/baseline.xml`; the `CyclomaticComplexMethod`,
  `LongMethod`, and `LongParameterList` entries for `RenderCard` are removed.
- Baseline count is now 4 IDs.

## Remaining N-8 Boundary

N-8 remains open. Remaining entries are `MainScreen` route orchestration and
`WeatherContent` list orchestration.
