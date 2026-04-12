# Changelog

All notable changes to Nimbus Weather are documented here.

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
