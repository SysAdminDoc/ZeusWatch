# Changelog

All notable changes to Nimbus Weather are documented here.

## [Unreleased]

### Added
- Started the localization roadmap by moving core navigation, Locations, Compare, and Wear OS state copy into Android string resources, with an initial Spanish resource set for those high-traffic surfaces.
- Extended the localization foundation through Settings, including category cards, section headers, control labels, helper copy, source selectors, API-key guidance, and accessibility state descriptions.
- Localized home-screen widget chrome, including widget descriptions, Glance empty states, section labels, freshness announcements, setup flow copy, and compact Today/Tomorrow day labels.
- Localized Custom Alerts screen/editor copy, rule status semantics, notification channel labels, and custom-rule notification text.
- Localized Home Cards setting labels for every `CardType`, including move and show/hide accessibility descriptions.
- Localized Settings enum labels for units, radar providers, icon/theme/summary styles, alert filters, alert-source preferences, and weather-source dropdowns.
- Localized top-level Today screen loading, retry/location actions, share menu, offline banner, freshness badges, footer, location chips, and focused empty-card copy.
- Localized visible Today card header titles through the shared `WeatherCard` resource-title overload.
- Localized Today alert banner/details, the community report sheet, shared back action, alert severity/urgency labels, and report condition labels.
- Localized Today card micro-labels for current-condition hero metrics, rain nowcast legend, snowfall units, and radar preview/open states.
- Localized hourly/daily forecast card tabs, detail labels, UV/cloud/humidity descriptors, and short forecast summary sentences.
- Localized standalone Hourly and Daily forecast tab titles, Today/Tomorrow labels, high/low summaries, rain labels, wind labels, UV chips, and feels-like copy.
- Localized Today overview copy for current conditions, details grid labels, forecast brief chips/footer, and the skeleton loading label.

### Verified
- `./gradlew.bat :app:compileStandardDebugKotlin :wear:compileDebugKotlin --console=plain`
- `./gradlew.bat :app:testStandardDebugUnitTest --tests com.sysadmindoc.nimbus.ui.screen.locations.LocationsScreenLogicTest --console=plain`
- `./gradlew.bat :app:compileStandardDebugKotlin --console=plain`
- `./gradlew.bat :app:testStandardDebugUnitTest --tests com.sysadmindoc.nimbus.widget.WidgetRefreshWorkerLogicTest --tests com.sysadmindoc.nimbus.widget.WidgetConfigLogicTest --console=plain`
- `./gradlew.bat :app:testStandardDebugUnitTest --tests com.sysadmindoc.nimbus.util.AlertCheckWorkerTest --console=plain`
- `./gradlew.bat :app:testStandardDebugUnitTest --tests com.sysadmindoc.nimbus.data.repository.UserPreferencesTest --console=plain`
- `./gradlew.bat :app:compileStandardDebugKotlin --console=plain`
- `./gradlew.bat :app:testStandardDebugUnitTest --tests com.sysadmindoc.nimbus.ui.screen.main.MainScreenLogicTest --console=plain`

## [1.20.3] - 2026-05-13

Premium UX polish pass across phone, Wear OS, widgets, and project screenshots.

### Changed
- Normalized the visual system around crisper 8-12dp surfaces, tighter component rhythm, and non-rounded action controls across weather cards, radar controls, settings, locations, compare, custom alerts, widgets, and Wear OS surfaces.
- Removed negative letter spacing from the Compose type scale so headings and compact labels render more cleanly across densities.
- Reworked the pollen card into the shared weather-card surface and replaced the weak empty state with calmer, more useful copy.
- Improved accessibility semantics for icon-only toolbar, bottom navigation, and radar report controls so TalkBack focuses the actual tappable controls instead of unlabeled containers or duplicate child icons.
- Tightened the remaining secondary icon/status backdrops to square-rounded shapes so the interface no longer mixes circles, pills, and crisper cards.
- Improved settings, location, compare, and custom alert control semantics so toggles, radio rows, chips, dropdown rows, delete controls, and section expanders announce clearer labels and selected/on/off state.
- Fixed the weekly database WAL checkpoint worker to run its `PRAGMA wal_checkpoint(TRUNCATE)` through a query cursor, removing a launch-time SQLite warning from the background maintenance path.
- Stabilized the connected Today-screen UI test so duplicate visible temperature text no longer breaks the full device accessibility suite.
- Refined the community report sheet with clearer anonymous-report copy, a crisper drag handle, and selectable condition chips that expose their selected state to accessibility services.
- Added real-device screenshots for Today, Settings, and Radar to replace the README screenshot placeholder.

### Verified
- Compiled phone and Wear Kotlin targets.
- Installed and launched the standard debug build on a connected Android device for visual smoke testing.
- Built the signed standard release APK for distribution and device installation.

## [1.20.2] - 2026-05-13

Engineering audit pass round 2 — six more bugs found by a deep adversarial sweep on top of v1.20.1.

### Fixed
- **`MoonPhase.fromDayOfCycle` + `calculateLunarAge` negative-modulo trap** — Java's `%` operator preserves the sign of the dividend, so any caller passing a negative day-of-cycle value (or any future bug producing one) silently fell into the `normalized < 1.85` branch and reported the wrong phase. Both call sites now normalize with the `((x % p) + p) % p` idiom into `[0, period)`. Regression test exercises -3, -15, and -29.53 day inputs.
- **`NowcastAlertWorker.nowcastReferenceTime` anchored on the wrong bucket** — the previous logic used `series.minByOrNull { it.time }?.time` (the earliest bucket) as the anchor for "now", which meant `detectNowcastTransition` reported `minutesUntil = 30` when the truth, relative to the device wall clock, was `minutesUntil = 5`. The worker fires for `prefs.lastLocation` — the user's own current location — so the wall clock IS the right anchor when it falls within the series window. The new logic prefers the wall clock when the series straddles it (or is within 60 min) and falls back to the earliest bucket otherwise, so remote-timezone series still behave correctly.
- **`SyncedWeatherStore.save()` could lose data on Wear OS process kill** — the editor used `.apply()` (async write) inside a `WearableListenerService`, which Wear OS kills aggressively after `onDataChanged` returns; if the process exited before the disk write completed, the watch fell back to stale or empty data on next wakeup. Switched to `.commit()` (synchronous) and added per-index key cleanup so shrinking the hourly/daily/alert arrays doesn't leave stale entries in the prefs file forever.
- **`AlertRepository` silently dropped MeteoAlarm in `ALL_SOURCES` mode** — `MeteoAlarmAdapter.getAlerts(lat, lon)` is a no-op stub returning `emptyList()` because the EUMETNET feed requires an ISO country code. The dispatch code only routed to `getAlertsForCountry` when `countryCode != null`, so any `ALL_SOURCES` query without a detected country (no Geocoder + non-European timezone) dropped MeteoAlarm entirely. Worse, the country-aware path made `getWarnings("us")`-style doomed calls for non-European country codes. The fix gates the country-aware call on `countryCode in adapter.supportedRegions` and short-circuits otherwise. Three regression tests cover: no-country short-circuit, non-European country short-circuit, and supported-country pass-through.
- **`WidgetRefreshWorker` + `DatabaseMaintenanceWorker` + standard-flavor `WearSyncManager` swallowed `CancellationException`** — each had an outer `catch (_: Exception)` that masked WorkManager / structured-concurrency cancels as either `Result.retry()` or a silent "sync failed" log line, preventing cooperative cancellation and leaving cancelled coroutines technically still alive. All three now re-throw `CancellationException` before the generic catch.
- **Environment Canada province bbox put Calgary/Banff in British Columbia** — `bc`'s longitude range was `-139.1..-114.0`, which overlapped with `ab`'s `-120.0..-110.0`. The `linkedMapOf` was iterated in declaration order, so BC won the tie for everything west of -114°W in southern Alberta (Calgary at -114.07, Banff at -115.57, Canmore, Lake Louise). The real BC-AB border sits at the Continental Divide near -120°W, so the BC east edge was tightened to `-139.1..-120.0`. Tests added for both Calgary and Banff.

### Tests
- `AirQualityRepositoryTest.MoonPhase fromDayOfCycle handles negative cycle days` — covers the +period-then-mod normalization for -3, -15, and -29.53 day inputs.
- `EnvironmentCanadaAlertAdapterTest.Calgary resolves to Alberta` and `Banff resolves to Alberta` — pin the corrected BC east edge.
- `AlertRepositoryTest.MeteoAlarm short-circuits cleanly when no country code is detected`, `MeteoAlarm only fires for supported European countries`, `MeteoAlarm runs for supported European country` — three-way test of the new gating logic.
- `NowcastAlertLogicTest.nowcastReferenceTime uses wall clock when buckets are aligned with device time` — covers the in-window path; the old "anchor to earliest" test was rewritten to cover only the remote-timezone fallback path.
- Stabilized the v1.20.1 `met norway projects UTC timestamps into the device timezone` test against wall-clock minute drift — the previous version was flaky after the half-hour mark because `findCurrentEntry` would pick the next-hour entry instead of `baseUtc`.

### Version
- phone versionCode 84 → 85, versionName 1.20.1 → 1.20.2
- wear versionCode 60 → 61, versionName 1.20.1 → 1.20.2


## [1.20.1] - 2026-05-13

Engineering audit pass — five bugs across forecast adapters, wear sync, lightning service, and debug log redaction.

### Fixed
- **MET Norway forecast adapter** — UTC timestamps were rendered as if they were location-local. `OffsetDateTime.parse(timestamp).toLocalDateTime()` discards the `Z` offset, so every hourly time and the derived `today` daily key was shifted by the user's UTC offset (visible whenever it isn't zero, i.e. essentially every MET Norway user — the API is Nordic-focused, all of which sit at UTC+1/+2). Replaced with `atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()` and threaded the zone through `mapHourly` / `aggregateDaily` so the projection stays consistent with the rest of the rendering pipeline.
- **Environment Canada forecast adapter** — Same class of bug as MET Norway. `observationDateTimeUtc` and the compact-format fallback were both being stripped of their UTC offset and used as a local clock. Western Canadian timezones (PST/MST, -7/-8) saw the `today` date silently drift to the next UTC day late in the evening, mislabeling the "Today/Tonight" daily pair as belonging to tomorrow. Fix mirrors the MET Norway approach.
- **Bright Sky (DWD) forecast adapter** — `tz=Etc/UTC` makes every entry come back with a `+00:00` offset; the adapter stripped the offset, anchoring all forecast hours to UTC components. German users (DWD's primary audience) saw a 1–2h drift (CET/CEST). Projection through `atZoneSameInstant(ZoneId.systemDefault())` now keeps the wall clock aligned with the device.
- **Wear `WearWeatherRepository` User-Agent** — hardcoded literal `"ZeusWatch-Wear/1.14.0"` had drifted six versions behind the manifest. Enabled `buildConfig = true` on the wear module and switched the header to `"ZeusWatch-Wear/${BuildConfig.VERSION_NAME} (Android Wear; Open-Source)"` so future version bumps stay in sync.
- **`BlitzortungService.connect()` double-open race** — gating on `isConnected` (which is only flipped from the WebSocket listener's `onOpen` callback) left a window where a second `connect()` call from the UI could create a parallel WebSocket while the first was still mid-handshake, doubling subscription traffic and leaking the first socket. Gating on `webSocket != null` closes the window because the reference is set synchronously inside the `@Synchronized` block.
- **Pirate Weather path-embedded API key log leak** — `HttpLoggingInterceptor` at `BASIC` level logs the request URL; Pirate Weather embeds its API key as a path segment (`/forecast/{key}/{lat},{lon}`), which the query-param-only redactor in `NetworkModule` missed. Added a second pass that captures the `/forecast/` prefix and re-anchors on the coordinate pair, scrubbing the key in both the bare and `,exclude=…`-suffixed forms. Was previously documented as an accepted limitation; debug builds no longer carry the key into logcat.

### Tests
- New regression test `met norway projects UTC timestamps into the device timezone` in `ForecastAdapterTimezoneTest` — locks the JVM default zone to America/New_York and asserts that both `hourly.first().time` and `current.observationTime` come back projected into NY rather than as raw UTC components.
- `ApiKeyRedactionTest` rewritten to cover the new two-pass redaction: replaced the "documents-the-leak" test with `strips Pirate Weather path-embedded key` plus `redacts Pirate Weather path key alongside exclude suffix`.

### Version
- phone versionCode 83 → 84, versionName 1.20.0 → 1.20.1
- wear versionCode 59 → 60, versionName 1.20.0 → 1.20.1


## [1.20.0] - 2026-06-24

WAL checkpoint maintenance worker + WeatherSummaryEngine unit tests.

### Added
- **`DatabaseMaintenanceWorker`** — `@HiltWorker CoroutineWorker` that runs `PRAGMA wal_checkpoint(TRUNCATE)` weekly via `WorkManager` with `ExistingPeriodicWorkPolicy.KEEP`. Prevents unbounded WAL growth on long-running devices. No network required, no user-facing setting. Scheduled unconditionally at application startup.
- **`WeatherSummaryEngineTest`** (25 tests) — covers all four time-of-day slots (morning/afternoon/evening/tonight), all condition phrase variants for major `WeatherCode` values, daytime/nighttime temperature label selection (`Highs`/`Lows`) using the daily `temperatureHigh`/`temperatureLow`, wind note thresholds (30/40/60 km/h), UV index high/very-high warnings and night suppression, muggy humidity note with temperature gating, and yesterday-comparison logic (warmer/cooler/within-range/null cases).
- **`WeatherSummaryEnginePrecipTest`** (8 tests) — exercises all 7 branches of `precipitationOutlook()` (empty hourly, maxProb < 20, ≥ 8 rainy hours, 4+ early / 4+ late / 4+ mid, single-hour > 60% / single-hour ≤ 60%) plus the null-when-empty guard.
- **`WeatherSummaryEngineWithStyleTest`** (5 tests) — verifies that `generateWithStyle()` returns the template for `TEMPLATE` style, falls back to template when `aiEngine` is null, delegates to the engine and returns its text when it produces a result, and falls back to template when the engine returns null or throws.

### Version
- phone versionCode 82 → 83, versionName 1.19.0 → 1.20.0
- wear versionCode 58 → 59, versionName 1.19.0 → 1.20.0



PirateWeather adapter test coverage.

### Added
- **Adapter unit tests — PirateWeather** — `PirateWeatherForecastAdapterTest` covers the core data-mapping paths: happy-path success, blank API key → failure, null `currently` block → failure, `IOException` propagation, `precipType = "snow"` → `snowfall` populated, `precipType = "rain"` → `snowfall` null, hourly past-entry filter (entries >1 h before now are excluded, entries at the exact boundary are retained), hourly snow `precipType` → `snowfall`, daily `precipIntensity` mm/h × 24 → mm/day, daily snow `precipType` → `snowfallSum`, null `precipType` → `snowfallSum` null, first-daily `temperatureHigh`/`temperatureLow` → `current.dailyHigh`/`current.dailyLow`, daily wind m/s → km/h, and sunrise/sunset ISO 8601 format. `PwIconMapperTest` exhaustively covers all 15 icon-to-WMO-code mappings (clear, partly-cloudy, cloudy, wind, fog, rain, rain+sleet, sleet, snow, hail, thunderstorm, tornado, unknown → -1) and all 7 `isDayFromIcon` cases (day suffix, night suffix, bare rain/fog/snow, and `partly-cloudy-night`).

### Version
- phone versionCode 81 → 82, versionName 1.18.0 → 1.19.0
- wear versionCode 57 → 58, versionName 1.18.0 → 1.19.0



Test coverage expansion + notification deep linking + background refresh cadence.

### Added
- **Adapter unit tests — OWM** — `OwmForecastAdapterTest` covers wind m/s→km/h, visibility passthrough, WMO code mapping, day/night icon suffix, precipitation-probability fraction→percent, clamping, and blank-API-key failure. `OwmAlertAdapterTest` covers severity tag mapping, empty alert list, and missing key. `OwmAqiAdapterTest` covers PM2.5/PM10/O3 AQI breakpoints, empty-list failure, and missing key.
- **Adapter unit tests — BrightSky** — `BrightSkyForecastAdapterTest` covers station name, custom-name override, condition→WMO mapping, day/night icon suffix, visibility-in-meters passthrough, daily high/low aggregation, multi-day sorted output, and circular wind mean for north-spanning ranges (e.g. 350°+10° yields a north-ish bearing, not ~180°). `BrightSkyAlertAdapterTest` covers EN-over-DE preference, German fallback, blank-alertId→`dwd-{id}` synthetic key, EN description preference, severity mapping, and `senderName` always "DWD".
- **Adapter unit tests — Environment Canada** — `EnvironmentCanadaAlertAdapterTest` covers `resolveProvince()` for all 13 provinces/territories by representative city, 3 non-Canadian coordinates returning null, `getAlerts()` for non-Canadian coords → empty, "No watches or warnings" / "No warnings in effect" filter text, null-title filter, severity mapping, `senderName`, synthetic-ID generation, and `areaDesc` passthrough.
- **Adapter unit tests — MeteoAlarm** — `MeteoAlarmAdapterTest` covers `getAlerts()` always-empty contract (country detection is at AlertRepository level), `getAlertsForCountry()` full field mapping, lowercase country code, null identifier → synthetic ID, null event → skip, multiple info blocks, multi-area join, null areaDesc fallback, MINOR/EXTREME severity, empty warning list, and `senderName` fallback chain (info.senderName → warning.sender → displayName).
- **Adapter unit tests — JMA** — `JmaAlertAdapterTest` covers happy-path full field mapping, null title filter, author→senderName, null author→displayName, null area→"Japan", onset→effective, null onset fallback to updated, null id→synthetic, unknown/null severity→UNKNOWN, null instruction always null, multiple entries, empty list, and API exception → failure result.

### Changed
- **Background weather freshness is more aggressive** — `WidgetRefreshWorker` now schedules periodic weather, widget, persistent-notification, and Wear sync refreshes every 15 minutes instead of 30 minutes, using WorkManager's update policy so existing installs adopt the faster cadence on the next schedule sync.
- **Notification taps now land on the relevant weather surface** — severe-alert notifications open the active alert banner, rain-nowcast notifications open the Rain Next Hour card, and health notifications open the Health Alerts card. The Today screen temporarily exposes a targeted card when it was hidden in the user's card layout, then scrolls there instead of dropping users at the default forecast top.

### Version
- phone versionCode 80 → 81, versionName 1.17.0 → 1.18.0
- wear versionCode 56 → 57, versionName 1.17.0 → 1.18.0

## [1.17.0] - 2026-04-24

Provider expansion + security hardening + accessibility release. Closes the last HIGH-priority open roadmap item (Environment Canada forecast) and adds MET Norway as a new provider for uncontested Nordic coverage. All prior tests plus 20+ new test assertions pass.

### Added
- **MET Norway LocationForecast 2.0 as a forecast provider** — richest free weather API in the landscape, now selectable in Settings > Data Sources > Forecast. `MetNorwayForecastAdapter` consumes `/locationforecast/2.0/complete`, maps to `WeatherData` with m/s → km/h wind conversion, and extracts precipitation probability, thunder probability, UV index, gusts, dew point, and cloud-layer fractions (high/medium/low). `MetSymbolMapper` translates MET's `symbol_code` vocabulary (clearsky_day, rainandthunder, lightsleetshowers, etc.) into WMO codes, falling back to cloud-fraction classification when symbols are absent. Daily is aggregated from hourly because MET doesn't publish a daily block. Global User-Agent already satisfies MET's non-default-UA requirement. Data license: CC BY 4.0 (attribution surfaces in About + README).
- **Environment Canada forecast adapter (unstub)** — closes the last HIGH-priority open roadmap item. `EnvironmentCanadaForecastAdapter` queries the OGC API Features `citypageweather-realtime` collection on `api.weather.gc.ca`, picks the nearest feature within a 0.5° bbox (widening to 1.5° for sparse/remote regions), maps `currentConditions` → `CurrentConditions` (kPa → hPa pressure, km → m visibility, humidex → windChill → temperature feels-like fallback) and `forecastGroup.forecast[]` → `DailyConditions` by pairing day and night period entries. `iconCode` → WMO via a %30-folded lookup (handles day/night symmetry) with a condition-text fallback. Hourly is empty — ECCC's free OGC tier doesn't publish hourly forecast data.
- **Certificate pinning scaffolding for API-key endpoints** — `ApiCertificatePins.build()` wires a `CertificatePinner` into the OWM forecast, OWM AQI, and Pirate Weather OkHttp clients. Keyless public APIs (Open-Meteo, NWS, DWD Bright Sky, MeteoAlarm, JMA, ECCC, MET Norway) stay unpinned — pinning them would add fragility without security benefit. `hostPins` ships empty; pins are captured per-release via `tools/capture_api_pins.sh` rather than hardcoded so certificate rotation doesn't brick the app between releases. When the map is empty, `build()` returns `CertificatePinner.DEFAULT` (no-op) so runtime is unchanged. The two-pin invariant (leaf + intermediate per host) is enforced by unit test.
- **Widget freshness pill is now a tap-to-refresh control** — the "4m" / "Live" / "2h" pill on loaded home-screen widgets was decorative; body taps opened the app and there was no way to force refresh short of waiting for the 30-minute WorkManager schedule. The pill is now clickable and routes through `WidgetRefreshAction`. TalkBack announces "Data updated Xm ago. Tap to refresh now." The existing battery-saver short-circuit in `WidgetRefreshWorker` (skip at ≤15%) still guards the enqueued work.
- **Canvas-drawn cards expose a11y semantics** — five Canvas-heavy cards were previously invisible to TalkBack. `AqiGauge` (with `LiveRegionMode.Polite` so AQI updates auto-announce), `TemperatureGraph` (range + direction summary), `PressureTrendCard` (current + trend + 24h delta), `MoonPhaseCard` (sunrise/sunset/day-length + phase + illumination), and `CloudCoverCard` (current + 24h min/max) now carry `mergeDescendants = true` `contentDescription` blocks sourced from their live data.
- **New tests** — `MetNorwayAdapterTest` (happy path + sparse data + empty timeseries + symbol mapping + day/night suffix), `EnvironmentCanadaForecastAdapterTest` (nearest-feature selection + kPa/km unit conversion + day/night pairing + empty collection failure + iconCode mapping + condition-text fallback + malformed JSON + unknown-condition default), `ApiCertificatePinsTest` (default-pinner contract + two-pin invariant + sha256/ format + 44-char base64 length check). `WeatherSourceManagerTest` + `UserPreferencesTest` updated to reflect the new MET_NORWAY and ENVIRONMENT_CANADA forecast capabilities.

### Changed
- **ECCC alert Retrofit split from forecast Retrofit** — alerts live at `weather.gc.ca` (Atom feeds) while forecasts live at `api.weather.gc.ca` (OGC API). New `@Named("eccc_forecast")` Retrofit added alongside the existing `@Named("eccc")` one. Both keyless, both honour the global User-Agent + retry interceptors.
- **`WeatherSourceProvider.ENVIRONMENT_CANADA`** no longer has an `implementedTypes` override — forecast is now a first-class capability, so Settings > Data Sources shows ECCC as a selectable forecast primary and fallback.

### Version
- phone versionCode 79 → 80, versionName 1.16.0 → 1.17.0
- wear versionCode 55 → 56, versionName 1.16.0 → 1.17.0

## [1.16.0] - 2026-04-24

Resilience + roadmap-closure release. Closed 8 HIGH/MEDIUM ROADMAP items from the v1.14.0 architectural audit plus 2 audit-round follow-ups. 331 unit tests pass (+10 new: RateLimitInterceptor, ApiKeyRedaction). One HIGH item — Environment Canada forecast adapter — deferred to v1.17.0 because it needs full citypageweather-realtime GeoJSON mapping.

### Added
- **GMS-free crash reporting** — integrated ACRA (5.13.1) as the successor to the Crashlytics path removed in v1.5.0. Runs in both `standard` and `freenet` flavors: crashes do *not* auto-upload; ACRA opens a dialog offering to email a sanitized report (no PII, no API keys, no coordinates) so the freenet flavor remains F-Droid-compliant. Redaction patterns cover `.*apikey.*`, `.*api_key.*`, `.*owm_key.*`, `.*pirate_key.*`, `.*last_location.*`. Skipped in debug builds.
- **Per-host rate limiter** — new `RateLimitInterceptor` (GCRA token bucket + `Retry-After`-aware single retry + fail-fast 429 above cap). Wired onto OpenWeatherMap, OpenWeatherMap AQI, and Pirate Weather OkHttp clients with conservative rate/burst settings sized to free-tier quotas. `Retry-After` capped at 5s so a misconfigured server can't stall workers. Open-Meteo / Bright Sky / NWS / Environment Canada (free, high-quota) are unrate-limited.
- **OkHttp retry now recovers 5xx** — previously only retried IOException; now also retries HTTP 500-599 responses once with exponential backoff. 429 is delegated to `RateLimitInterceptor`.
- **API key redaction in debug logs** — custom `HttpLoggingInterceptor.Logger` scrubs `?appid=`, `?apikey=`, `?api_key=`, `?key=` values before they hit logcat so screen recordings of debug builds don't leak user-supplied OWM/Pirate Weather credentials.
- **Wear OS sync freshness indicator** — CurrentScreen now shows a footer pill ("Phone • 4m ago • tap to refresh" / "Watch • just now • tap to refresh") so users can see whether the data came from the paired phone via DataLayer sync or from a direct API call, how stale it is, and tap to force a re-sync.
- **Ambient notification group** — nowcast + health + custom-rule notifications now share `AMBIENT_GROUP_ID` with a group-summary row so they collapse in the shade instead of stacking ungrouped. Severe alerts keep their existing severity-based group unchanged.
- **Notification deep link routing** — custom-rule notification taps now route to `Routes.CUSTOM_ALERTS` via `zeuswatch://custom_alerts` instead of dropping onto the default forecast tab. Nowcast / health taps route via `zeuswatch://main` (for future per-card targeting). `MainActivity.resolveDeepLink` extended with `custom_alerts` + explicit `main` hosts.
- **Detekt 1.23.8 static analysis** — rules config at `config/detekt/detekt.yml`, baseline at `config/detekt/baseline.xml`, wired into `build.yml` before lint so CI fails on *new* code smells. Reports upload as a workflow artifact. Initial baseline has 22 findings (long/cyclomatic methods in compose screens) that can be chipped away incrementally.
- **Dependabot config** — weekly Gradle PR cap 10 + monthly GitHub Actions PR cap 5. AGP / Kotlin / Compose compiler / KSP / Hilt pinned manually because their version coupling breaks single-component bumps.

### Changed
- **ProGuard: Gemini Nano `-keep` rule** — added `-keep class com.google.ai.edge.** { *; }` and matching `-dontwarn` block. Without this, R8 was stripping AI Core reflection targets and standard-flavor release builds would crash on first `GeminiNanoSummaryEngine` call. ACRA classes also now have explicit keep rules.
- **Room schema export re-enabled** — `NimbusDatabase` now has `exportSchema = true` with output routed to `app/schemas/` via KSP. Migrations can now be diffed against committed baselines in code review and regression-tested.
- **`showYesterdayComparison` setting is now honored** — the toggle existed in `UserPreferences` and `SettingsScreen` but `MainScreen` was passing `state.yesterdayHigh` to `CurrentConditionsHeader` unconditionally. The header now receives `null` when the preference is off, hiding the comparison.
- **CI build expansion** — `build.yml` now compiles and uploads wear debug APKs, runs wear unit tests, builds R8 release variants on main (catching shrinker regressions before release), and runs Detekt. `release.yml` rebuilds with proper release variants (not debug), reconstructs the signing keystore from GitHub secrets when present, produces `ZeusWatch-standard-*.apk` / `ZeusWatch-freenet-*.apk` / `ZeusWatch-wear-*.apk`, and uploads them to the GitHub Release tied to the pushed tag (or `workflow_dispatch` input).

### Version
- phone versionCode 78 → 79, versionName 1.15.0 → 1.16.0
- wear versionCode 54 → 55, versionName 1.15.0 → 1.16.0

## [1.15.0] - 2026-04-22

### Changed
- **UI modernization pass** — rebuilt every screen on a shared chrome layer. New `NimbusChrome.kt` exposes `GlassActionButton`, `ScreenHeader`, `PremiumMessageCard`, and `InlineNoticeCard`; new `WearChrome.kt` exposes `WearPanel`, `WearHeader`, `WearStateCard`, `WearMiniPill`, and `WearLinkRow`. MainScreen, RadarScreen, SettingsScreen, LocationsScreen, CompareScreen, and CustomAlertsScreen were each cut down in size (~180 lines removed from MainScreen alone) by moving header/state/empty/loading patterns onto those shared components, eliminating duplicated gradient/border/padding blocks. Toolbar buttons now share a single `GlassActionButton` shape.
- **Typography refinements** — `Type.kt` rebalanced for better hierarchy at common densities (displayLarge 92→88sp, headlineLarge 30→32sp, body tracking/weights tuned). Matches Material 3 baseline deltas without changing semantic roles.
- **Accessibility** — location selector pills now use `selectable`/`selectableGroup` with `Role.Tab` so TalkBack announces active-tab state instead of generic "button, double tap to activate."
- **Component polish** — `AlertBanner`, `AlertDetailSheet`, `CurrentConditionsHeader`, `DailyForecastList`, `HourlyForecastStrip`, `RadarPreviewCard`, `ShimmerSkeleton`, `WeatherCard`, `WeatherDetailsGrid`, and `WeatherSummaryCard` rebuilt on the shared glass surfaces/palette, unifying padding, border thickness, and corner radii.
- **Status copy** — "X% rain today" → "Rain risk X%", "Cached • …" → "Offline-ready • …", "Updated …" → "Refreshed …", offline banner replaced with an `InlineNoticeCard` ("Offline mode").
- **Widget theme consolidation** — new `WidgetTheme.kt` centralizes Glance colors, text styles, `widgetUpdatedLabel()`, `weatherIconRes()`, plus shared `WidgetPill` and `WidgetEmptyState` composables. `NimbusSmallWidget`, `NimbusMediumWidget`, `NimbusLargeWidget`, `NimbusForecastStripWidget`, and `WidgetConfigActivity` now consume the shared theme.
- **Wear OS UI** — AlertsScreen, CurrentScreen, DailyScreen, and HourlyScreen rebuilt on the new WearChrome primitives; consistent panel styling, mini-pill metadata, and tap targets across the 4 screens.
- **Footer label** — "Data: Open-Meteo.com" → "Forecast data from Open-Meteo".

### Fixed
- Removed unused `NimbusToolbarSurface` import from `MainScreen.kt` after toolbar buttons consolidated onto `GlassActionButton`.

### Version
- phone versionCode 77 → 78, versionName 1.14.2 → 1.15.0
- wear versionCode 53 → 54, versionName 1.14.0 → 1.15.0

## [1.14.2] - 2026-04-17

### Fixed
- **Engineering audit round 2** — 4 bugs across 2 files.
- **WeatherSummaryEngine timezone-unaware greeting** — `LocalTime.now()` drove the "this morning / this afternoon" label using the device clock, producing wrong greetings when viewing weather for a remote timezone. Now pulls hour-of-day from `current.observationTime` (location-local) with a device-time fallback.
- **WeatherSummaryEngine wind band misclassification** — 30–40 km/h was labelled "light winds". Corrected the threshold table so that range renders as "moderate winds."
- **WeatherSummaryEngine precipitation time labels** — `precipitationOutlook()` used fixed "this morning" / "this evening" strings based on array index, not actual time of day. Replaced with relative labels ("soon" / "later today") that stay correct regardless of when the function runs.
- **OnThisDayRepository Feb 29 crash** — `LocalDate.of(year, 2, 29)` threw `DateTimeException` in non-leap years. New `safeDate()` helper clamps day-of-month to the target month's `lengthOfMonth()`.

## [1.14.1] - 2026-04-17

### Fixed
- **Engineering audit round 1** — 10 bugs across 9 files.
- **Visibility unit mismatch (3 adapters)** — `WeatherFormatter.formatVisibility()` expects meters; OWM/BrightSky were pre-dividing to km and PirateWeather was storing km as if it were meters. All three now return meters so unit conversion produces correct miles/km/ft.
- **`addSeenAlertIds` ordering bug** — called `Set.drop()` on an unordered set, dropping arbitrary IDs during cap-and-trim. Rewritten with `LinkedHashSet` that prioritizes newly-seen IDs.
- **CAS-retryable lambda side effects (2 ViewModels)** — `MutableStateFlow.update` lambdas can re-run under CAS contention; `MainViewModel.observeSavedLocations` and `CompareViewModel.syncSavedLocations` were triggering fetches and consuming request tokens inside the lambda. Extracted to post-update blocks.
- **BrightSky UTC date offset** — `LocalDate.now(UTC)` could miss today's data for UTC+ timezones; now subtracts 1 day so morning queries still return a full hourly series.
- **latLonToTile math error** — `ln(tan(lat))` blew up at extreme latitudes; clamp via `coerceIn(-85.0511, 85.0511)` (Web Mercator limits).
- **Notification ID collisions** — summary ID 0 collides with system defaults; health notification hash was 8-bit (256 unique IDs). Widened to 16-bit hash and base ID 0x1000.
- **HealthAlertEvaluator null humidity** — `minOf { it.humidity ?: 0 }` coerced nulls to 0%, producing phantom "extreme low humidity" alerts. Nulls are now filtered before min/max.
- **MainScreen excessive recomposition** — `WeatherThemeState` allocated fresh on every recomposition; wrapped in `remember { }` so theme consumers don't cascade-recompose.

## [1.14.0] - 2026-04-16

### Added
- **Wear OS — Alerts, Daily forecast, AQI**. Phone DataLayer sync now pushes alerts, 7-day daily forecast, and air quality alongside existing current + hourly data.
- **`AlertsScreen`** (wear) — severity-colored dots, event names, headlines, expiry times.
- **`DailyScreen`** (wear) — 7-day `ScalingLazyColumn` with weather emoji, high/low, precip chance.
- **AQI chip on watch CurrentScreen** — color-coded by EPA level, replaces precip chip when AQI available.
- **Alert banner on CurrentScreen** — orange strip with top alert name (or count), tappable to open `AlertsScreen`.
- **Watch navigation expanded** — CurrentScreen now links to Hourly and 7-Day side-by-side plus banner tap. `WearNavHost` routes: `CURRENT`, `HOURLY`, `DAILY`, `ALERTS`.

### Fixed
- **Background wear sync** — `WidgetRefreshWorker` now calls `wearSyncManager.syncWeather()` after its primary fetch, so the watch stays fresh during background-only refresh cycles.
- **Sync ordering** — phone syncs AFTER its parallel sub-fetches finish (alerts, AQI, etc.), so the watch gets the full payload in a single push instead of an incomplete first push.

### Changed
- New data models: `WearDailyEntry`, `WearAlertEntry`. `WearWeatherData` and `WearUiState` extended with `daily`, `alerts`, `aqi`, `aqiLabel`.
- `SyncedWeatherStore` and `WeatherDataListenerService` updated for the new fields.

### Version
- wear versionCode 52 → 53.

## [1.13.0] - 2026-04-15

### Added
- **Wear OS DataLayer sync** — phone pushes simplified weather (current + 12h hourly as a `DataMap`) to the paired watch after every successful fetch. Eliminates redundant watch-side API calls and keeps the two surfaces consistent.
- **`WearSyncManager`** with flavor-split implementation: real `DataClient.putDataItem()` in `standard`, no-op in `freenet`.
- **`WeatherDataListenerService`** (wear) — `WearableListenerService` that receives `/weather/current` data events and persists to `SyncedWeatherStore` (SharedPreferences).
- **`WearWeatherRepository`** prefers synced data when < 30 minutes fresh; falls back to direct Open-Meteo when disconnected or stale.
- **Tile + complication** now consult `SyncedWeatherStore` first.

### Changed
- ProGuard rules for `com.google.android.gms.wearable.**` added to both phone and wear modules.
- wear versionCode 51 → 52, versionName 1.10.0 → 1.13.0 (aligned with phone for release parity).

## [1.12.0] - 2026-04-15

### Added
- **Health alert system overhaul.**
- **Migraine pressure detection** now uses real barometric surface pressure from hourly forecasts with a 3-hour rolling window. Replaces the old temp/humidity proxy heuristic. Pressure-drop and pressure-rise events fire independently against a user-configurable threshold (default 5.0 hPa / 3h).
- **Temperature/humidity fallback** kicks in when pressure data is unavailable (e.g. Pirate Weather, some Bright Sky stations).
- **`HealthAlertWorker`** — hourly CoroutineWorker that proactively evaluates health triggers and fires notifications via new `CHANNEL_HEALTH`. Per-type per-day dedupe with 7-day auto-prune.
- **Three alert categories fully functional:** migraine (pressure), respiratory (humidity >85% or <20%), arthritis (temperature swings > 10–15°C / 12h). Each fires at WARNING or ADVISORY severity independently.

### Fixed
- **Critical toggle gating bug** — `healthAlertsEnabled` and `migraineAlerts` Settings toggles were not gating the evaluator, so alerts fired regardless of user preference. Now properly conditional, matching the `drivingAlerts` pattern.

## [1.11.0] - 2026-04-15

### Added
- **Weather source adapters — 3 alternative providers** replacing the old stubs.
- **OpenWeatherMap (One Call 3.0)** — full forecast (current + 48h hourly + 8-day daily), severe weather alerts, air quality with EPA AQI conversion from μg/m³. Condition codes 2xx–8xx mapped to WMO for consistent icon/theme rendering. Requires API key.
- **Pirate Weather** (Dark Sky-compatible) — full forecast with Dark Sky icon → WMO mapping, humidity / cloudCover 0–1 → percent conversion, SI wind m/s → km/h. Requires API key.
- **Bright Sky (DWD)** — free, no-key German Weather Service. 10-day MOSMIX forecasts, current observations, DWD severe weather alerts with English/German bilingual fields. Daily aggregation computed from hourly with circular-mean wind direction.
- **Settings integration** — all three sources appear in Settings > Data Sources dropdowns. API key fields shown conditionally when OWM or Pirate Weather is selected.
- **`WeatherSourceManager`** primary + fallback dispatching works across all new providers.
- **New files**: `OpenWeatherMapApi`, `PirateWeatherApi`, `BrightSkyApi` (Retrofit), `OwmResponse`, `PirateWeatherResponse`, `BrightSkyResponse` (serialization), `OwmAdapters`, `PirateWeatherAdapter`, `BrightSkyAdapters`. `NetworkModule` extended with 4 new named Retrofit instances.

## [1.10.0] - 2026-04-14

### Changed
- **Wear OS companion — complete rewrite.** Multi-screen Wear Compose UI with `SwipeDismissableNavHost` (current conditions + 12-hour hourly).
- **Watch-side GPS** via `FusedLocationProviderClient` with SharedPreferences cache and reverse geocoding — replaces the hard-coded US coordinates in the prior build.
- **Rich current screen** — weather emoji, temperature, condition, H/L, detail chips (humidity, wind, UV, precip), location name.
- **Hourly forecast** — `ScalingLazyColumn` with time / emoji / temp / precip rows.

### Fixed
- **Tile ANR** — `WeatherTileService` replaced `runBlocking` with `CoroutineScope` + `CallbackToFutureAdapter`. The old code was blocking the main thread.
- **Tile enrichment** — humidity, wind, and location info added; complication now uses the synced location instead of stale defaults.

### Added
- **`WearLocationProvider`** — permission-gated GPS, `Geocoder` reverse lookup, graceful fallback chain.
- **Wear signing config** (shared keystore), `isShrinkResources = true`.
- wear versionCode / versionName aligned to 1.10.0 / 51.

## [1.9.0] - 2026-04-14

### Added
- **Custom Alert Rules** — user-defined weather threshold notifications. Create rules like "notify me if today's high > 32°C" or "alert when wind gusts exceed 50 km/h in the next 12 hours." Five supported metrics: today's high, tonight's low, wind gust (12h), precipitation sum (24h), and UV peak. Each rule is independently toggleable and stores thresholds in canonical metric units; the UI converts to the user's preferred display units (°F, mph, inches, etc.) at the Compose layer.
- **`CustomAlertsScreen`** — dedicated screen accessible from Settings > Alerts > Custom Alert Rules. Full CRUD: add rules via FAB, edit via tap, toggle/delete per-row. Bottom-sheet editor with metric chip selector, operator picker (above/below), numeric threshold input, and enable toggle.
- **`CustomAlertWorker`** — hourly periodic CoroutineWorker that evaluates enabled rules against the latest forecast for the user's last-known location. Dedupe per (rule-id, calendar date) via SharedPreferences-backed seen-set, pruned to 7 days. Network-constrained with exponential backoff.
- **`CustomAlertEvaluator`** — pure-function evaluator: resolves each metric from `WeatherData` (daily high/low, hourly wind gusts, 24h precip sum, daily UV max), applies the operator/threshold, and returns triggered hits with observed values.
- **`CHANNEL_CUSTOM` notification channel** — separate "Custom Alerts" channel so users can silence custom rules without losing severe weather or nowcast notifications. Per-rule stable notification IDs prevent clobbering.
- **Startup sync** — `NimbusApplication.onCreate` now schedules/cancels `CustomAlertWorker` based on whether any enabled custom rules exist.
- **Adaptive icon monochrome layer** — launcher icons updated to include `ic_launcher_monochrome` for Material You themed icons on Android 13+.

## [1.8.0] - 2026-04-12

### Added
- **Proactive precipitation nowcasting notifications** — "Rain in about 15 min", "Rain stopping soon", etc. The existing (but previously unwired) `nowcastingAlerts` preference now schedules a `NowcastAlertWorker` that runs every 15 minutes, pulls the last-known location, fetches the minutely-15 precipitation series, and checks for the first dry→wet or wet→dry transition within a 60-minute window. Title and intensity (light/steady/heavy) are derived from the peak mm value in the incoming wet run.
- **`CHANNEL_NOWCAST` notification channel** — default importance (sound but no DND bypass), its own user-facing "Rain Nowcast" label under the existing Weather Alerts group.
- **Dedupe + cooldown** — transition signature (`start:<timestamp>` or `stop:<timestamp>`) is persisted in a new `nimbus_nowcast_alerts` SharedPreferences file. The same bucket never notifies twice, and a 45-minute minimum gap between any two back-to-back nowcast notifications guards against flip-flop storms spamming the shade.
- **Settings wiring** — toggling "Nowcasting alerts" in Settings now schedules/cancels the worker immediately (previously the toggle only wrote the pref). Flow is permission-gated by the same `POST_NOTIFICATIONS` prompt used for other alert toggles.
- **Startup sync** — `NimbusApplication.onCreate` now reschedules the nowcast worker based on current settings alongside the existing `AlertCheckWorker` sync.

### Tests
- `NowcastAlertLogicTest` — 8 pure-function tests covering empty input, uniformly dry / uniformly wet series (no transition), rain-starting with peak intensity detection, rain-stopping, look-ahead window exclusion (transitions beyond 60 min are ignored), first-transition-only semantics, and notification signature stability across peak-intensity revisions (so a sharpening peak doesn't re-notify).

## [1.7.0] - 2026-04-12

### Added
- **"On This Day" card** (28th dynamic card). Surfaces historical weather context for the current calendar date and location:
  - 10-year average high temperature for this date at this location, with a color-coded delta badge comparing today's forecast high (amber = warmer than normal, blue = cooler, neutral = near normal).
  - Record high and record low for this date over the sampled window.
  - Prior-year highs rendered as a 52dp sparkline with an average-line guideline and year tick labels at both ends.
  - Polite empty state when the archive has no usable observations (polar regions, brand-new settlements, first run without network and no cache).
  - TalkBack: sparkline carries a semantic `contentDescription` describing the temperature range.
- **Open-Meteo Archive integration**. New `OpenMeteoArchiveApi` Retrofit interface against `archive-api.open-meteo.com`, wired through a new `@Named("archive")` Retrofit in `NetworkModule`.
- **Immutable historical cache**. `OnThisDayRepository` persists responses in a dedicated `nimbus_on_this_day` SharedPreferences file, keyed by `lat,lon,MM-dd` with `Locale.US` formatting (following the v1.6.5 locale-safe cache-key pattern). Historical observations don't change once logged, so there is no TTL — first access per (location, date) costs one archive call; every subsequent access is instant and offline-safe.
- `CardType.ON_THIS_DAY` — disabled by default; users enable it via the existing Settings card-order screen.

## [1.6.5] - 2026-04-11

Third QA audit pass focused on card composables, charts, accessibility, theme, and data-parsing robustness. 27 findings raised, 25 rejected as false positives (already-guarded divide-by-zeros, already-clamped path math, semantic AQI colors that follow EPA standards and shouldn't be themed). 2 latent i18n bugs confirmed and fixed.

### Fixed
- **NWS alert requests malformed on comma-decimal locales** — `NwsAlertAdapter` built its request point via `"%.4f,%.4f".format(lat, lon)` using the default locale. On de_DE / fr_FR / es_ES and similar locales, this produces `"39,7392,-104,9847"` — comma-decimal output that the NWS API parses as four fields and rejects. The adapter now uses `String.format(Locale.US, ...)` explicitly. Regression test added (`getAlertsFormatsPointWithDotDecimalOnLocalesThatUseCommaDecimal`).
- **NWS HTTP error handling fragile to Retrofit message-format changes** — `NwsAlertAdapter` detected the "non-US coordinates → 404" case via `e.message?.contains("404")`, which breaks if Retrofit's exception message format ever changes. The adapter now catches `retrofit2.HttpException` and inspects `e.code()` directly. All other exceptions still propagate as `Result.failure`.
- **Weather cache key locale-dependent** — `WeatherCacheEntity.makeKey` formatted lat/lon with default locale, producing locale-dependent cache keys (`"39,74,-104,99"` on a German device vs `"39.74,-104.99"` elsewhere). Cache reads stayed consistent *within* a locale, but if the user ever changed device locale, every existing cache entry became unreachable. Now pinned to `Locale.US`.

### Audit findings rejected (verified against real code)
- `PrecipitationChartCard` divide-by-zero — `if (data.isEmpty()) return` on line 42 + `if (maxProb > 0)` on line 90 already guard the Canvas
- `MoonPhaseCard` illFraction clamping — already `.coerceIn(0.0, 1.0)` on line 136
- `VisibilityCard` thresholds bounds — list has 7 elements, loop `for (i in 0 until 6)` accesses indices 0–6 safely
- `UvIndexBar` divide-by-zero — gated by `if (uvIndex >= 1)` on line 88
- `AqiGauge` hardcoded colors break AMOLED — colors are semantic EPA AQI tiers and shouldn't be themed away
- 10+ other hardcoded-color findings — semantic data colors, not UI chrome
- `WeatherWallpaperService` paint.alpha clamping — particle spawn caps alpha at 0.5; multiplied by 255 = max 127
- `NwsAlertAdapter` `e.message.contains` issue — **confirmed and fixed above**
- Various `contentDescription = null` on decorative icons — intentional
- `WeatherWallpaperService` frame rate hardcoded 30 fps — acceptable tradeoff for battery

## [1.6.4] - 2026-04-11

Second QA audit pass. v1.6.3 covered hot files from the v1.6.2 stabilization diff; this round audited everything else (main view model, repositories, location services, utilities, theme, wallpaper, icon packs). 22 findings were raised, 20 were verified as false positives and rejected, and 2 latent bugs were confirmed and fixed.

### Fixed
- **AirQuality hourly list empty for distant locations** — `AirQualityRepository` was comparing Open-Meteo hourly timestamps (returned in location-local time when `timezone=auto` is set) against the device's `LocalDateTime.now()`. For users viewing a location in a different timezone (e.g. a phone in Denver looking at Tokyo weather), the filter `!t.isBefore(now.minusHours(1))` dropped every hour in the response, leaving the hourly AQI and 5-day daily AQI cards blank. The repository now anchors "now" off `response.current.time` (parsed as location-local) instead of the device clock. Pollen hourly fallback uses the same anchor.
- **Settings screen main-thread icon pack discovery** — `SettingsViewModel` computed `availableIconPacks` at construction by calling `IconPackManager.getAvailablePacks()`, which reaches into every installed third-party icon-pack APK via `PackageManager.getResourcesForApplication()` + `AssetManager.open()`. That's blocking disk I/O on the main thread — a StrictMode violation that could hitch the first frame of the Settings screen on slow storage or with many packs installed. Now exposed as a `StateFlow<List<IconPack>>` computed on `Dispatchers.IO`; `SettingsScreen` consumes it via `collectAsStateWithLifecycle`. Initial state is an empty list so the UI isn't blocked on discovery.

### Audit findings rejected (with verified reasons)
| Claim | Verdict |
|---|---|
| `MainViewModel.loadWeatherForCoords` default-arg race | Single-threaded on `Dispatchers.Main.immediate` |
| `fetchWeather` default-arg race | Same; only caller passes the arg explicitly |
| `fetchYesterdayComparison` missing `withContext(IO)` | `WeatherRepository.getYesterdayWeather` already wraps in IO |
| `AlertRepository` silently swallows adapter exceptions | Intentional partial-failure pattern |
| Freenet `LocationProvider` listener leak | `LocationManagerCompat.getCurrentLocation` + `CancellationSignal` is the documented pattern |
| `DrivingConditionEvaluator` black-ice at ≤2°C is wrong | Intentional NWS-recommended safety margin for bridge decks / shaded spots |
| `HealthAlertEvaluator` `>` vs `>=` off-by-one | Pedantic; thresholds are advisory, not medical |
| `PetSafetyEvaluator` pavement formula runs at night | Guarded by `if (current.isDay && current.cloudCover < 50)` — audit misread |
| `MeteoconMapper` non-exhaustive | Has fall-through default |
| `WeatherWallpaperService` Handler lifecycle unsafe | Correctly guards with `visible` flag + `removeCallbacks` |
| `MainActivity` sync deep-link | Simple URI parsing, acceptable |
| `WeatherFormatter.feelsLikeReason` hardcoded Celsius threshold | Computed on canonical metric; display unit doesn't enter |
| `WeatherSummaryEngine` not DI-injected | Stateless Kotlin `object` — DI adds nothing |
| `WeatherWallpaperService` raw Int weather code | `WeatherEffect.fromWmoCode` has `else -> CLEAR` default |
| `MainViewModel` mutable props should be state | Internal tracking, not UI state |
| `ClothingSuggestionEvaluator.weatherCode.isRainy` null safety | Enum property, not nullable |
| `AlertRepository` timezone-to-country fallback | Best-effort only; primary path is Geocoder |
| `SavedLocationMatchingTest` robustness | Tests verify the behavior contract |
| `WeatherSummaryEngine` `hourly.isEmpty()` unsafe | `hourly.take(12)` on empty list is safe |
| `GravitySensor` capture clarity | Working as designed |

## [1.6.3] - 2026-04-11

Post-v1.6.2 QA audit. Fixes four latent bugs uncovered by a full senior-dev / UX / QA review of the v1.6.2 stabilization pass.

### Fixed
- **Widget ANR risk** — `NimbusWidgetReceiverBase.onDisabled()` and `onDeleted()` previously ran DataStore suspend calls inside `runBlocking { }` on the main thread, which can trigger an ANR if DataStore or WorkManager is slow. Now wraps the cleanup with `goAsync()` and executes it on `Dispatchers.IO`, then calls `pending.finish()` — up to ~10 s of safe background work per broadcast.
- **Locale-dependent saved-location dedupe** — `SavedLocationMatching.normalizeLocationToken` used default-locale `lowercase()`, which is unstable across devices (Turkish dotless-i in particular: `"I".lowercase(tr) == "ı"`, not `"i"`). The new implementation decomposes the string to Unicode NFD, strips combining marks, then `lowercase(Locale.ROOT)`. `"Paris"` now dedupes against `"París"` (different diacritics from different geocoding sources), and `"Istanbul"` / `"istanbul"` match on Turkish-locale devices.
- **Radar frame refresh stuck on NTP rollback** — `shouldLoadRadarFrames()` used `nowMillis - lastLoadedAt >= interval`, which stays `false` when the wall clock rolls backward (e.g. NTP adjustment shortly after boot). The predicate now also treats a negative delta as "stale", so a clock correction can't leave the user staring at old radar frames for up to 5 minutes.
- **Settings permission banner not reactive** — the "Notification Permission Off" card in `SettingsScreen` was computed from a one-shot `hasNotificationPermission(context)` snapshot. If the user left the app, granted POST_NOTIFICATIONS via Android Settings, and came back, the card stayed visible until some other recomposition. The screen now subscribes to the lifecycle and re-reads the permission on `ON_RESUME`, and the in-app permission launcher updates the state directly from its `granted` callback.

### Tests
- `SavedLocationMatchingTest` — covers coordinate-epsilon matching, current-location exclusion, diacritic-insensitive label matching (`Paris` / `París`), and Turkish-locale stability (`Istanbul` / `istanbul` on `tr-TR`).
- `RadarViewModelTest.shouldLoadRadarFrames forces refresh when clock rolls backward` — NTP-rollback regression guard.

### Audit notes
Four additional issues flagged by the audit were verified against the code and rejected as false positives: MainScreen tab normalization, `CompareViewModel.activeLoads` "race" (all on `viewModelScope.launch` = `Dispatchers.Main.immediate`), BlitzortungService WebSocket threading (OkHttp handles it), and ConnectivityObserver "captive portal too strict" (requiring `NET_CAPABILITY_VALIDATED` is correct for a network-dependent weather app). See the commit message for full rationale.

## [1.6.2] - 2026-04-11

### Added
- **Notification permission UX** — Settings now gates alert and persistent weather notification toggles behind a runtime POST_NOTIFICATIONS prompt on Android 13+, with a PermissionNoticeCard explaining the requirement.
- **Permission-aware startup** — NimbusApplication reschedules alert/notification workers based on current toggle state instead of blindly launching them, with workers injected through a new `@DefaultDispatcher` Hilt qualifier.
- **Per-widget display labels** — WidgetDataProvider now persists a dedicated `displayLocationName` per appWidgetId so different widgets can share a coordinate and still show the label the user chose.
- **Coordinate-grouped widget refresh** — new `WidgetRefreshPlan` / `buildWidgetRefreshPlan()` pipeline deduplicates saved locations across widgets, fetches each unique coordinate once, and fans results back out to every assigned widget.
- **Orphaned widget cleanup** — stale widget mappings for appWidgetIds that no longer exist are now pruned automatically during refresh.
- **Alert dedupe by coordinate** — AlertCheckWorker collapses saved locations that share the same coordinate (4dp precision) so the same severe-weather alert no longer fires twice.
- **Saved-location duplicate prevention** — new `SavedLocationMatching` utility (epsilon 0.0001° + normalized label) blocks duplicate inserts in LocationRepository and hides already-saved matches from the search results list.
- **Current-location anchoring** — current location is now pinned at `sortOrder = -1` across `addLocation`, `reorderLocations`, and `ensureCurrentLocation` so drag-reorder can never demote it.
- **Widget-config current-location hiding** — the "saved locations" picker in the widget config screen now hides the current-location row so users don't accidentally re-pick it as a saved choice.
- **Radar frame throttle** — RadarViewModel caches the last successful frame load and skips redundant fetches within a 5-minute window via `shouldLoadRadarFrames()` / `canAnimateRadarPlayback()` guards.
- **Radar status overlay** — RadarScreen renders a `RadarStatusCard` for loading/error states, disables playback controls when no frames are available, and hides the community-report FAB when offline or without a valid location.
- **Compare slot state** — CompareViewModel now uses an explicit `Slot { PRIMARY, SECONDARY }` enum plus request-token + active-load counter so a fast location swap can't leave stale weather in the other slot.
- **Connectivity validation** — ConnectivityObserver now requires both `NET_CAPABILITY_INTERNET` and either `NET_CAPABILITY_VALIDATED` or a VPN transport before reporting online.
- **Configurable weather cache TTL** — WeatherRepository exposes a user-configurable cache TTL (default 30 min, via `DEFAULT_CACHE_MAX_AGE_MS`) instead of the fixed 6-hour cap, and uses the proper `reverseGeocode()` API for coordinate lookups.
- **Tablet tab normalization** — MainScreen hides the Radar tab on tablets (split pane already shows radar) and auto-corrects the selected tab via `visibleMainTabs()` / `normalizeSelectedMainTab()` so rotation can't leave an invalid selection.
- **Expanded test coverage** — new unit tests for `LocationRepository`, `AlertCheckWorker`, `WeatherRepository`, `CompareViewModel`, widget refresh/config logic, radar screen logic, and locations/main screen logic.

### Fixed
- Race in CompareScreen where swapping primary/secondary locations mid-fetch could overwrite the new slot with the old slot's data.
- Radar playback controls rendering before a frame set was available, producing a broken transport UI on first load.
- BlitzortungService no longer re-creates its CoroutineScope on disconnect; uses field-level scope + `tryEmit` to avoid dropped emissions and job leaks.
- Widget refresh no longer hammers the network when multiple widgets point at the same coordinate, and respects low-battery (<=15%) without returning `Result.retry()` unnecessarily.
- AlertNotificationHelper now only marks alerts as "seen" when they were actually notified, instead of every fetched alert.
- LocationsScreen drag threshold is computed in real pixels via `LocalDensity` instead of a hardcoded float, fixing drag calibration on high-density screens.
- MainActivity and WearMainActivity migrated to lifecycle-aware `collectAsStateWithLifecycle()` to stop collecting state in the background.
- ApiKeyField in Settings now commits on focus-loss with a "Saved when you leave the field" hint + proper IME action handling, ending the silent-save bug.

## [1.5.0] - 2026-03-27

### Added
- **Humidity & Comfort card** — humidity gauge with comfort level, dew point, and zone markers
- **Precipitation Forecast card** — 24h probability bars with peak callout and total accumulation
- **Pressure Trend card** — 24h barometric pressure line graph with trend direction and delta
- **Wind Forecast card** — 24h wind speed line graph with gust overlay bars and peak callout
- Feels-like temperature overlay (orange dashed line) on Temperature Graph
- Temperature range bars in Daily Forecast rows (color-coded cold-to-warm)
- Alert expiry countdown on alert banners ("3h 15m left")
- Pull-to-refresh on Hourly and Daily tabs
- HourlyTab shows feels-like temperature and wind speed per row
- Compare screen: weather condition icons, visibility/cloud cover rows, value highlighting
- Outdoor Activity Score: factor breakdown bars (temp/wind/rain/UV/humidity)
- Location screen: weather condition icons next to cached temperatures
- Collapsible settings sections with tap-to-toggle arrows
- Snowfall card: daily total accumulation
- Hourly strip respects 48/72h forecast hours setting

### Fixed
- Yesterday comparison now converts to user's temperature unit (was raw Celsius diff)
- `Icons.Filled.CompareArrows` migrated to `Icons.AutoMirrored.Filled.CompareArrows`
- `statusBarColor`/`navigationBarColor` replaced with `WindowCompat.setDecorFitsSystemWindows`
- DailyForecast snow detail used sun icon instead of AcUnit
- SunArc and NowcastCard respect user's 12h/24h time format setting
- HourlyTab time format respects 12h/24h preference
- RadarViewModel/RadarScreen coordinate check `||` -> `&&` (equator/meridian locations)
- WeatherWallpaperService catches all exceptions in unlockCanvasAndPost
- Frame counter overflow prevention in wallpaper particle system
- WidgetConfigActivity leaked MainScope replaced with lifecycleScope
- NimbusLargeWidget missing isDay param for daily weather descriptions
- NimbusForecastStripWidget guarded against < 6 hourly items
- Radar layer URLs: removed broken OWM tile endpoints, replaced with RainViewer satellite

### Changed
- Today tab converted from Column+verticalScroll to single LazyColumn (cards now truly lazy-loaded)
- WeatherSummaryCard, AqiCard, MoonPhaseCard use WeatherCard wrapper for consistent styling
- AqiCard uses gradient AqiGauge from separate file (deleted inline duplicate)
- Data Sources and Advanced settings sections start collapsed

### Removed
- Firebase Crashlytics (plugin, dependency, ProGuard rules)
- Dead `MainScreenContent` wrapper function
- Dead `ReorderableCardColumn.kt` (MainScreen inlines card rendering)
- Unused FlowRow/ExperimentalLayoutApi imports

## [1.4.0] - 2026-03-27

### Security
- Set `allowBackup="false"` and added data extraction rules to prevent sensitive data leakage
- WebView mixed content changed to `MIXED_CONTENT_NEVER_ALLOW` with domain whitelist (embed.windy.com, openstreetmap.org, cartocdn.com)
- HTTP logging interceptor now gated behind `BuildConfig.DEBUG` (no longer logs in release builds)
- Added `network_security_config.xml` blocking cleartext traffic
- Added Firestore Security Rules (`firestore.rules`) with validated writes, rate limiting, and device-scoped deletes
- Replaced all unsafe `enum.valueOf()` calls with `safeValueOf()` in UserPreferences settings mapping

### Added
- Firebase Crashlytics integration for production crash reporting
- Offline detection with `ConnectivityObserver` and persistent "You're offline" banner
- Radar screen offline guard (shows empty state instead of blank WebView)
- Reduced motion support — particles and shimmer animations respect system `ANIMATOR_DURATION_SCALE`
- Tab switch crossfade animation (300ms fade between Today/Hourly/Daily/Radar)
- Reactive "Updated Xm ago" timestamp that refreshes every 60 seconds
- Context-aware error icons (LocationOff, CloudOff, ErrorOutline) based on error type
- Staleness timestamp on small widget
- `contentDescription` on all widget weather images via shared `WidgetUtils.weatherDescription()`
- Semantics on WindCompass and AqiGauge Canvas elements for screen readers
- `kotlinx-collections-immutable` dependency for Compose stability
- ConnectivityObserver utility for reactive network state
- ReducedMotion utility composable
- WeatherRepository unit tests (11 tests)
- UserPreferences unit tests (26 tests)
- NwsAlertAdapter unit tests (23 tests)
- WidgetDataProvider unit tests (14 tests)

### Changed
- `ReorderableCardColumn` converted from `Column` to `LazyColumn` (only visible cards composed)
- All `List<>` fields in `MainUiState` changed to `ImmutableList<>` (prevents unnecessary recompositions)
- Extracted shared `RenderCard` composable — eliminated ~260 lines of duplicate card rendering code
- `WeatherRepository.sourceManager` changed from field injection to constructor injection with `dagger.Lazy`
- Weather cache auto-evicts entries older than 6 hours
- `BlitzortungService.isConnected` made `@Volatile` with `@Synchronized` connect/disconnect
- OkHttp retry interceptor added (2 retries with exponential backoff on IOException)
- Independent sub-fetches (alerts, AQI, astronomy, radar, nowcast) now run in parallel
- User-friendly error messages replace raw exception text
- `WidgetRefreshWorker` extracted `buildHourlyItems`/`buildDailyItems`/`buildWidgetData` helpers (eliminated 70-line copy-paste)
- Per-widget try/catch moved inside loop (one failure no longer skips all widgets)
- `AccessibilityHelper` methods now format temperatures using user-preferred units
- Touch targets on DailyForecast rows and location chips increased to 48dp minimum
- `Locale.US` replaced with `Locale.getDefault()` in user-facing date/time formatters
- User-Agent string now uses `BuildConfig.VERSION_NAME` instead of hardcoded version
- Room indices added to `SavedLocationEntity` on `isCurrentLocation` and `sortOrder`
- `reorderLocations` wrapped in `@Transaction` (eliminates N+1 query pattern)
- ProGuard rules added for `@HiltWorker`, widget serializables, Crashlytics, and log stripping
- WidgetRefreshWorker skips refresh when battery ≤ 15%
- Coil ImageLoader configured with 25% memory cache and 50MB disk cache

## [1.3.6] - 2026-03-26

### Added
- Coil ImageLoader configuration with 25% memory cache and 50MB disk cache
- WeatherSourceManager unit tests (12 tests covering fallback, alerts, AQI, minutely)

### Changed
- WidgetRefreshWorker skips refresh when battery ≤ 15% to preserve device life

## [1.3.5] - 2026-03-26

### Security
- Moved release signing credentials from build.gradle.kts to local.properties (no longer committed)

### Fixed
- WeatherParticles animations now stop when app is backgrounded (battery drain fix)
- Empty catch blocks in MainViewModel now log warnings for debuggability
- Added stable keys to HourlyTab, DailyTab, HourlyForecastStrip LazyLists (fixes animation glitches)
- Removed `fallbackToDestructiveMigration()` from Room database (prevents silent data loss)
- Yesterday comparison no longer blocks derived data computation (clothing, health, driving alerts load instantly)
- Unimplemented weather source adapters (OWM, Pirate Weather, Bright Sky) hidden from Settings UI

### Added
- ClothingSuggestionEvaluator unit tests (15 tests)
- PetSafetyEvaluator unit tests (17 tests)
- DrivingConditionEvaluator unit tests (17 tests)
- HealthAlertEvaluator unit tests (13 tests)

## [1.3.4] - 2026-03-26

### Fixed
- Removed unsafe `!!` assertion in AirQualityRepository pollen fallback path
- Added NaN guard for temperature normals band to prevent rendering artifacts
- Added safety check for uninitialized `sourceManager` in WeatherRepository
- Added contentDescriptions to alert, health, pet safety, driving, and severe weather icons
- Added missing ProGuard rules for Firebase, Coroutines, DataStore, WorkManager, and Hilt

## [1.3.3] - 2026-03-26

### Added
- "Warmer/cooler than yesterday" comparison label in current conditions header
- Temperature normals band on temperature graph (shaded region showing forecast average range)
- Drag-to-reorder saved locations with long-press drag handles
- Batch sort order persistence for reordered locations via Room DAO

### Changed
- SavedLocationDao now supports `updateSortOrder()` for efficient reordering
- TemperatureGraph accepts optional `normalHigh`/`normalLow` parameters for normals band

## [1.3.2] - 2026-03-26

### Added
- Dew point comfort descriptor ("Comfortable", "Muggy", "Oppressive") in Today's Details
- Feels-like explanation in header ("Wind chill", "Heat index") when differs from actual temp
- Pressure trend indicator (Rising/Falling/Steady) using hourly surface pressure data
- Saved location temperature preview shows cached temps in the location list
- 5-day daily AQI forecast bars in Air Quality card with color-coded severity
- Feels-like temperature shown in hourly strip when 3+ degrees different from actual

### Changed
- Open-Meteo hourly params now include `surface_pressure` for trend analysis
- WeatherDetailsGrid accepts hourly data for pressure trend computation

## [1.3.1] - 2026-03-26

### Added
- "What to Wear" clothing suggestions card based on temperature, wind, rain, and UV
- Pet Safety card with pavement temperature estimates, heat stress, cold exposure, and storm anxiety alerts
- 72-hour hourly forecast option (configurable in Settings > Data Display)
- Dominant pollutant highlighting in Air Quality card (worst contributor gets amber badge)

## [1.3.0] - 2026-03-26

### Added
- Visibility unit setting (miles/km) now configurable in Settings
- Alert source preference UI (Auto-detect, NWS, MeteoAlarm, JMA, Environment Canada, All)
- Configurable migraine pressure threshold (3.0/5.0/7.0/10.0 hPa/3h)
- Per-widget location configuration with config activity
- Radar map layer selector (Radar, Temperature, Wind, Clouds, Precipitation)
- Per-widget weather data fetching in WidgetRefreshWorker
- Radar layer overlay rendering in RadarMapView

### Fixed
- Removed stale TODO comment about freenet LocationManager (already implemented)
- Widget per-location data loading with fallback to global data

## [1.2.0] - 2025-12-15

### Added
- Real-time lightning strike overlay via Blitzortung WebSocket
- Gemini Nano on-device weather summaries (with template fallback)
- Live weather wallpaper with particle effects (rain, snow, thunder, sun rays, fog)
- Community weather reports via Firebase Firestore with rate limiting
- Multi-source forecast fallback system (Open-Meteo, NWS, OWM, Pirate Weather, Bright Sky, Environment Canada)
- Custom icon pack support (bundled assets + external APK discovery)
- International alert sources: MeteoAlarm (EU), JMA (Japan), Environment Canada
- Tablet two-pane layout (weather + radar side-by-side at 840dp+)
- Data source configuration UI with dropdown selectors and API key fields

### Changed
- Deep link scheme unified to `zeuswatch://` across all shortcuts
- Persistent weather notification now also updates from MainViewModel on weather load
- Share helpers consolidated into single ShareWeatherHelper (text + image)

## [1.1.0] - 2025-06-20

### Added
- CAPE field fully wired through API, response models, and SevereWeatherCard
- 5-day AQI forecast (increased from 3 days)
- Interactive temperature graph with drag-to-inspect and precipitation overlay bars
- Compact 4x1 forecast strip widget (NimbusForecastStripWidget)
- Beaufort color ring on WindCompass
- Dynamic version display using BuildConfig.VERSION_NAME
- Meteocons graceful fallback when Lottie assets missing
- Compare screen deep link shortcut
- ProGuard rules for Room, Lottie, and Coil

### Changed
- Version bump from 1.0.0 to 1.1.0 (versionCode 40→50)

## [1.0.0] - 2025-02-21

### Added
- Share weather as formatted text via system share sheet
- Share weather as rendered dark-themed image card via FileProvider
- App shortcuts: Search Location, Radar Map, Settings (long-press launcher icon)
- Deep link handling for `nimbus://` URI scheme
- Adaptive layout with WindowSizeClass (2-column FlowRow on landscape/tablet)
- TalkBack accessibility: content descriptions on all Canvas composables
- Predictive back gesture support (`enableOnBackInvokedCallback`)
- LGPL-3.0 license
- F-Droid fastlane metadata
- GitHub Actions CI/CD pipeline
- Comprehensive README with architecture diagram

### Testing
- Test infrastructure: JUnit 4, MockK, Turbine, kotlinx-coroutines-test
- WeatherFormatterTest: temperature, wind, pressure, UV, time formatting (20 tests)
- WeatherCodeTest: WMO code mapping, weather classification, icon selection (12 tests)
- AccessibilityHelperTest: content description generation for all composables (12 tests)
- AirQualityRepositoryTest: AQI mapping, moon phase, pollen thresholds, alert parsing (14 tests)
- AlertRepositoryTest: NWS integration, severity sorting, error handling (9 tests)
- MainViewModelTest: state transitions, permissions, caching, preferences (10 tests)
- LocationsViewModelTest: search, add/remove, debounce (7 tests)
- MainScreenTest: loading/error/weather content UI states (6 tests)
- SettingsScreenTest: sections, radio selection, toggle interaction (10 tests)
- LocationsScreenTest: search input, results display, empty state (8 tests)

### Changed
- Version bump from 0.7.0 to 1.0.0 across all version references
- MainScreen toolbar now includes share dropdown menu
- All card layouts use adaptive padding from LocalAdaptiveLayout

## [0.7.0] - 2025-02-15

### Added
- Multiple saved locations with Room database (saved_locations table)
- Location search with Open-Meteo geocoding autocomplete
- "My Location" GPS entry auto-created on first load
- Location list screen with search bar and add/remove
- Location picker button on main screen toolbar
- Navigate to weather for specific saved location
- Database migration v1 to v2 for saved_locations table

## [0.6.0] - 2025-02-08

### Added
- Air quality data: PM2.5, PM10, O3, NO2, SO2, CO
- AQI color-coded arc gauge with EPA and European scales
- 24-hour AQI trend chart
- Pollen data with per-species animated bars (alder, birch, grass, mugwort, olive, ragweed)
- Moon phase calculation (Conway's algorithm) with Canvas illumination drawing
- Astronomy section: moonrise/moonset, illumination percentage, day length

## [0.5.0] - 2025-02-01

### Added
- Jetpack Glance home screen widgets (3 sizes)
- Small widget (3x1): icon + temp + location + high/low
- Medium widget (3x2): current conditions + 3-day forecast columns
- Large widget (4x3): current + 6hr hourly + 5-day rows
- Widget refresh via WorkManager (30-minute interval)
- Tap-to-open deep links from widgets

## [0.4.0] - 2025-01-25

### Added
- NWS API integration for US severe weather alerts
- Alert banner on main screen with severity color coding
- Alert detail modal bottom sheet
- Push notifications via WorkManager (30-min background checks, Severe+ only)
- Notification channel with BigTextStyle
- Graceful degradation for non-US locations (returns empty alert list)

## [0.3.0] - 2025-01-18

### Added
- MapLibre integration with CartoCDN Dark Matter basemap
- RainViewer API integration for global radar tiles
- Radar timeline slider (past 2hr + 30min forecast)
- Play/pause animation with 450ms/frame crossfade
- Full-screen radar view with pinch-to-zoom and gesture pause
- Radar mini-preview card on main screen

## [0.2.0] - 2025-01-11

### Added
- Canvas weather particle effects (rain, snow, sun rays)
- Temperature line graph with gradient fill and touch tracking
- Wind direction compass rose with animated needle
- UV index color-coded bar with level descriptions
- Shimmer loading skeleton animation
- Expandable daily forecast rows with day/night detail
- DataStore preferences: temperature, wind, pressure, precipitation, time format
- Settings screen with unit selection and particle toggle

## [0.1.0] - 2025-01-04

### Added
- Initial release: core architecture with MVVM + Repository + Clean Architecture
- Kotlin 2.1.0, Jetpack Compose, Hilt DI
- Open-Meteo API integration (forecast + geocoding)
- Current conditions hero header (large temp, condition, feels-like, high/low)
- 48-hour horizontally scrollable hourly forecast strip
- 16-day daily forecast list
- Today's Details 8-cell grid
- Dark gradient theme (TWC-inspired deep navy palette)
- Glassmorphism card components
- WMO weather code to icon mapping
- Condition-based dynamic sky gradients
- Pull-to-refresh
- Location permission handling
- Edge-to-edge display
- ProGuard configuration
- F-Droid build flavor skeleton
