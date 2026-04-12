# ZeusWatch (Nimbus) - Android Weather App

**Version**: v1.6.5 (versionCode 67)
**Package**: `com.sysadmindoc.nimbus`
**Stack**: Kotlin 2.1.0, Jetpack Compose (BOM 2024.12), Hilt 2.53.1, Retrofit 2.11.0, Room 2.6.1, DataStore, MapLibre 11.5.2, Glance 1.1.1, WorkManager 2.10.0, Lottie 6.6.2, Coil 3.0.4, Firebase Firestore
**License**: LGPL-3.0

## Build

- Keystore: `zeuswatch.jks` (gitignored), credentials in `local.properties`
- Gradle 8.9 wrapper
- `./gradlew assembleStandardRelease` for release APK
- `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`, `ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"`

## Architecture

### Data Layer
- **APIs**: Open-Meteo (forecast + AQ + pollen + minutely_15 nowcast + historical), NWS (alerts, US-only), RainViewer (radar tiles + satellite), Blitzortung (lightning WebSocket), Open-Meteo Geocoding
- **Room DB**: `NimbusDatabase` with `WeatherDao` (cache, auto-evicts >6h) + `SavedLocationDao` (indexed on `isCurrentLocation`, `sortOrder`)
- **DataStore**: `UserPreferences` (30+ settings), `WidgetDataProvider` (per-widget keyed storage), `WidgetLocationPrefs`
- **Repositories**: `WeatherRepository`, `LocationRepository`, `AlertRepository`, `RadarRepository`, `AirQualityRepository`, `CommunityReportRepository` (Firestore)
- **Multi-source**: `WeatherSourceManager` with adapter pattern — Open-Meteo, NWS, OWM, Pirate Weather, Bright Sky, Environment Canada. Primary + fallback per data type
- **International alerts**: `AlertSourceAdapter` interface — NWS (US), MeteoAlarm (31 EU), JMA (JP), Environment Canada. Auto-detect via Geocoder
- All raw API values are **metric**. Conversion in `WeatherFormatter`

### UI Layer
- **Screens**: Main (Today/Hourly/Daily tabs + Radar tab), Radar (Windy WebView OR native MapLibre), Locations (drag-reorder), Settings (10 collapsible sections), Compare
- **Navigation**: `NimbusNavHost` with bottom nav (`BottomTab` enum)
- **Theme**: `NimbusTheme` with weather-adaptive colors (sunny=amber, rainy=blue, snowy=ice, storm=purple) or static dark
- **Dynamic Cards**: Single `LazyColumn` renders 25 card types in user-defined order (reorderable in Settings)
- **Tablet**: Two-pane layout at screenWidthDp >= 840 (weather 55% + radar 45%)
- **Offline**: `ConnectivityObserver` with persistent banner, radar offline guard
- **Reduced motion**: Respects system `ANIMATOR_DURATION_SCALE` for particles/shimmer
- **Accessibility**: TalkBack liveRegion on alerts, mergeDescendants on cards, 48dp touch targets, screen reader semantics on Canvas elements

### Background
- `AlertCheckWorker` — 15min severe weather checks with expiry filtering
- `WidgetRefreshWorker` — periodic widget refresh, skips at battery <= 15%, per-widget location support
- `WeatherNotificationHelper` — persistent current-weather notification
- `WeatherWallpaperService` — live weather wallpaper with particle effects

### Build Variants
- `standard`: Google Play Services (FusedLocationProvider) + Gemini Nano AI
- `freenet`: F-Droid compatible, no proprietary deps

## Key Components

### Card Types (27)
WEATHER_SUMMARY, RADAR_PREVIEW, NOWCAST, HOURLY_FORECAST, TEMPERATURE_GRAPH, DAILY_FORECAST, UV_INDEX, WIND_COMPASS, AIR_QUALITY, POLLEN, OUTDOOR_SCORE, SNOWFALL, SEVERE_WEATHER, GOLDEN_HOUR, SUNSHINE, DRIVING_CONDITIONS, HEALTH_ALERTS, MOON_PHASE, HUMIDITY, PRECIPITATION_CHART, PRESSURE_TREND, WIND_TREND, DETAILS_GRID, CLOTHING, PET_SAFETY, CLOUD_COVER, VISIBILITY

### Utility Files
- `WeatherSummaryEngine.kt` — Template-based NLG with time-aware greetings, UV/humidity warnings + Gemini Nano AI fallback
- `WeatherFormatter.kt` — 30+ unit-aware format methods, `convertedTemp()` for unit-correct comparisons
- `ConnectivityObserver.kt` — Reactive network state via ConnectivityManager callback
- `DrivingConditionEvaluator.kt` / `HealthAlertEvaluator.kt` / `ClothingSuggestionEvaluator.kt` / `PetSafetyEvaluator.kt` — Smart alert engines
- `MeteoconMapper.kt` — WMO code to Lottie filename mapping
- `BlitzortungService.kt` — WebSocket for real-time lightning (thread-safe with @Volatile/@Synchronized)
- `IconPackManager.kt` — Bundled + external APK icon pack discovery
- `GravitySensor.kt` — Accelerometer-based tilt for parallax (smoothed low-pass filter, respects reduced motion)
- `AnimatedTemperature.kt` — Animated rolling digit counter for header temperature
- `CloudCoverCard.kt` — 24h cloud cover bar chart with reference threshold lines
- `VisibilityCard.kt` — 6-tier graduated scale with hourly trend line chart

### Settings Enums (in `UserPreferences.kt`)
- `RadarProvider`, `IconStyle`, `ThemeMode`, `SummaryStyle`, `AlertMinSeverity`, `AlertSourcePreference`, `VisibilityUnit`
- All enum deserialization uses `safeValueOf()` (no unsafe `valueOf()`)

## Key Architecture Patterns
- `CompositionLocalProvider(LocalUnitSettings provides ...)` in MainScreen; standalone screens read from ViewModel
- `ImmutableList<>` in `MainUiState` fields to prevent unnecessary recompositions
- `computeDerivedData()` batches all derived values into single `_uiState.update` call
- `WeatherSourceManager` tries primary adapter, catches failure, tries fallback
- Per-widget data: `WidgetDataProvider` with `wKey(appWidgetId, field)` prefix
- OkHttp retry interceptor: 2 retries with exponential backoff on IOException
- Independent sub-fetches (alerts, AQI, astronomy, radar, nowcast) run in parallel via coroutines
- Weather cache auto-evicts entries older than 6 hours
- Today tab is a single `LazyColumn` — header items + card `items()` — truly lazy, no nested scroll
- Card rendering driven by `CardType` enum + `RenderCard` when-block in MainScreen

## Gotchas
- `LocalUnitSettings.current` cannot be called inside Canvas DrawScope (not @Composable) — extract before Canvas
- `RadarMapView` requires `currentTileUrl`, `previousTileUrl`, and `overlayTileUrl` params
- Gradle wrapper jar/scripts were missing from repo initially — generated with extracted Gradle dist
- `BlitzortungService.isConnected` must be `@Volatile` with `@Synchronized` connect/disconnect to avoid race conditions
- Unimplemented weather source adapters (OWM, Pirate Weather, Bright Sky) are hidden from Settings UI
- Radar overlay layers use RainViewer satellite tiles (OWM tile API requires key, removed in v1.5.0)
- `FOREGROUND_SERVICE_SPECIAL_USE` removed in v1.5.0 (no foregroundServiceType declared, crashes Android 14+)

## Release History
- v1.0.0 — Initial release with share, widgets, accessibility, CI/CD, 108 unit tests
- v1.1.0 — CAPE, 5-day AQI, interactive graph, forecast strip widget, Beaufort ring
- v1.2.0 — Lightning overlay, Gemini Nano, live wallpaper, community reports, multi-source, custom icons, international alerts, tablet layout, per-widget config, radar layers
- v1.3.0 — 21 dynamic cards, weather-adaptive theme, NLG summaries, nowcasting, compare screen, driving/health alerts, animated icons
- v1.3.1 — Clothing/pet safety cards, 72h hourly, dominant pollutant
- v1.3.2 — Dew point comfort, feels-like explanation, pressure trend, location previews, 5-day AQI bars
- v1.3.3 — Yesterday comparison, normals band, drag-reorder locations
- v1.3.4 — Bug fixes (unsafe !!, NaN guard, sourceManager safety), ProGuard hardening, accessibility contentDescriptions
- v1.3.5 — Signing credentials to local.properties, particle battery fix, stable LazyList keys, Room migration safety
- v1.3.6 — Coil ImageLoader config, WeatherSourceManager tests, battery-skip widget refresh
- v1.4.0 — Security hardening, offline detection, reduced motion, ImmutableList perf, OkHttp retry, parallel sub-fetches, 74 new unit tests
- **v1.5.0** — 4 new cards (humidity, precip chart, pressure trend, wind forecast — 25 total). Single-LazyColumn Today tab perf. Pull-to-refresh all tabs. Compare screen icons + value highlighting. Outdoor score factor breakdown. Location weather icons. Alert expiry countdown. Daily temp range bars. Feels-like graph overlay. Card reorder in Settings. Collapsible settings. Smart rain timeline. Sunrise/sunset countdown. Time-aware summary with UV/humidity warnings. Wind direction arrows in hourly. Dew point comfort colors. Data staleness coloring. 22 crash/bug fixes. Crashlytics removed. Dead code cleanup. README/CHANGELOG/CLAUDE.md updated.
- **v1.6.0** — Breezy Weather-inspired features: 2 new cards (Cloud Cover with 24h bar chart, Visibility with 6-tier graduated scale + trend line — 27 total). **Tabbed Trend System** on hourly forecast (6 tabs: Temp, Feels Like, Wind, Precip, Cloud Cover, Humidity) and daily forecast (5 tabs: Overview, Temp, Wind, UV, Precip). **Enhanced Ephemeris Arc** with twilight dawn/dusk zones, solid traversed-arc, and optional moon path overlay. **Segmented AQI Arc Gauge** with per-tier colored segments and needle indicator. **Animated Temperature Counter** with smooth rolling digit animation in header. **Animated Page Indicator** dots on location selector. **Gravity Sensor Parallax** on weather particle effects (rain/snow/stars tilt with device accelerometer).
- **v1.6.5** — Third QA audit pass. Locale-safe coordinate formatting: `NwsAlertAdapter` and `WeatherCacheEntity.makeKey` now use `Locale.US` for the `"%.4f,%.4f"` / `"%.2f,%.2f"` format strings, otherwise devices with comma-decimal locales (de_DE, fr_FR, etc.) would send malformed points to NWS and produce locale-dependent cache keys. `NwsAlertAdapter` now catches `retrofit2.HttpException` and checks `e.code()` directly instead of the fragile `e.message?.contains("404")` string-match. New regression test: `getAlertsFormatsPointWithDotDecimalOnLocalesThatUseCommaDecimal`. 25 other round-3 audit findings were verified and rejected (all the flagged "divide-by-zero", "array bounds", and "illFraction clamp" concerns were already guarded; AQI gauge colors are EPA-standard and shouldn't be themed).
- **v1.6.4** — Second QA audit pass. Fixes two latent bugs outside the v1.6.2 hot-file set: AirQualityRepository was comparing Open-Meteo's location-local timestamps (`timezone=auto`) against device-local `LocalDateTime.now()`, silently filtering the entire hourly AQI list to empty for distant locations — now anchored off `current.time` instead. SettingsViewModel was calling `IconPackManager.getAvailablePacks()` at construction, which does `PackageManager.getResourcesForApplication()` + `AssetManager.open()` for every external icon-pack APK on the main thread — now exposed as a `StateFlow` computed on `Dispatchers.IO` with `collectAsStateWithLifecycle` in the screen. 20 other audit findings were verified against the code and rejected as false positives (threshold semantics, intentional safety margins, misread guards, false races on `Dispatchers.Main.immediate`).
- **v1.6.3** — Post-v1.6.2 QA audit fixes. Widget ANR fix: `NimbusWidgetReceiverBase` now uses `goAsync()` + background dispatcher instead of `runBlocking` on the main thread (previously could ANR on `onDisabled` / `onDeleted`). Location dedupe made locale-independent: `SavedLocationMatching` normalizes via NFD + combining-mark strip + `Locale.ROOT` lowercase, so "Paris"/"París" and "Istanbul"/"istanbul" on Turkish devices dedupe correctly. `RadarViewModel.shouldLoadRadarFrames` now treats a negative `now - lastLoadedAt` (NTP rollback) as "stale" so frames refresh instead of stalling. `SettingsScreen` re-reads POST_NOTIFICATIONS state on `ON_RESUME` so returning from system Settings clears the permission banner immediately. New unit tests: `SavedLocationMatchingTest` (NFD + Turkish locale), `shouldLoadRadarFrames forces refresh when clock rolls backward`.
- **v1.6.2** — QA/UX/perf stabilization pass. Notification permission UX rebuilt (Android 13+ POST_NOTIFICATIONS gated). Coordinate-grouped widget refresh + orphan cleanup + per-widget display labels. Alert dedupe by coordinate. Saved-location duplicate prevention via `SavedLocationMatching`, current-location pinned at `sortOrder = -1`. Compare slot-token state machine kills race on location swap. Radar 5-minute frame throttle, status overlay, playback guards. Configurable weather cache TTL (30 min default). ConnectivityObserver requires validated internet. Tablet Radar tab normalization. `@DefaultDispatcher` Hilt qualifier for worker scheduling. Lifecycle-aware state collection in MainActivity/WearMainActivity. Expanded unit tests (LocationRepository, AlertCheckWorker, WeatherRepository, CompareViewModel, widget refresh/config, radar logic).
