# ZeusWatch (Nimbus) - Android Weather App

**Version**: v1.5.0 (versionCode 61)
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
- **APIs**: Open-Meteo (forecast + AQ + pollen + minutely_15 nowcast + historical), NWS (alerts, US-only), RainViewer (radar tiles), Blitzortung (lightning WebSocket), Open-Meteo Geocoding
- **Room DB**: `NimbusDatabase` with `WeatherDao` (cache, auto-evicts >6h) + `SavedLocationDao` (indexed on `isCurrentLocation`, `sortOrder`)
- **DataStore**: `UserPreferences` (30+ settings), `WidgetDataProvider` (per-widget keyed storage), `WidgetLocationPrefs`
- **Repositories**: `WeatherRepository`, `LocationRepository`, `AlertRepository`, `RadarRepository`, `AirQualityRepository`, `CommunityReportRepository` (Firestore)
- **Multi-source**: `WeatherSourceManager` with adapter pattern — Open-Meteo, NWS, OWM, Pirate Weather, Bright Sky, Environment Canada. Primary + fallback per data type
- **International alerts**: `AlertSourceAdapter` interface — NWS (US), MeteoAlarm (31 EU), JMA (JP), Environment Canada. Auto-detect via Geocoder
- All raw API values are **metric**. Conversion in `WeatherFormatter`

### UI Layer
- **Screens**: Main (Today/Hourly/Daily tabs + Radar tab), Radar (Windy WebView OR native MapLibre), Locations (drag-reorder), Settings (9 sections), Compare
- **Navigation**: `NimbusNavHost` with bottom nav (`BottomTab` enum)
- **Theme**: `NimbusTheme` with weather-adaptive colors (sunny=amber, rainy=blue, snowy=ice, storm=purple) or static dark
- **Dynamic Cards**: LazyColumn renders 25 card types in user-defined order
- **Tablet**: Two-pane layout at screenWidthDp >= 840 (weather 55% + radar 45%)
- **Offline**: `ConnectivityObserver` with persistent banner, radar offline guard
- **Reduced motion**: Respects system `ANIMATOR_DURATION_SCALE` for particles/shimmer
- **Accessibility**: TalkBack liveRegion on alerts, mergeDescendants on cards, 48dp touch targets, screen reader semantics on Canvas elements

### Background
- `AlertCheckWorker` — 15min severe weather checks
- `WidgetRefreshWorker` — periodic widget refresh, skips at battery <= 15%, per-widget location support
- `WeatherNotificationHelper` — persistent current-weather notification
- `WeatherWallpaperService` — live weather wallpaper with particle effects

### Build Variants
- `standard`: Google Play Services (FusedLocationProvider)
- `freenet`: F-Droid compatible, no proprietary deps

## Key Components

### Utility Files
- `WeatherSummaryEngine.kt` — Template-based NLG + Gemini Nano AI summaries
- `ConnectivityObserver.kt` — Reactive network state via ConnectivityManager callback
- `ReducedMotion.kt` — Composable checking ANIMATOR_DURATION_SCALE
- `DrivingConditionEvaluator.kt` — Ice, fog, hydroplaning, wind, snow alerts
- `HealthAlertEvaluator.kt` — Migraine, respiratory, arthritis triggers
- `ClothingSuggestionEvaluator.kt` — Rule-based outfit recommendations
- `PetSafetyEvaluator.kt` — Pavement temp, heat stress, cold exposure, storm anxiety
- `MeteoconMapper.kt` — WMO code to Lottie filename mapping
- `WidgetUtils.kt` — Shared widget contentDescription + staleness timestamp
- `BlitzortungService.kt` — WebSocket for real-time lightning (thread-safe with @Volatile/@Synchronized)
- `IconPackManager.kt` — Bundled + external APK icon pack discovery

### Settings Enums (in `UserPreferences.kt`)
- `RadarProvider`, `IconStyle`, `ThemeMode`, `SummaryStyle`, `AlertMinSeverity`, `AlertSourcePreference`, `VisibilityUnit`
- `CardType`: 25 types (WEATHER_SUMMARY, RADAR_PREVIEW, NOWCAST, HOURLY_FORECAST, TEMPERATURE_GRAPH, DAILY_FORECAST, UV_INDEX, WIND_COMPASS, AIR_QUALITY, POLLEN, OUTDOOR_SCORE, SNOWFALL, SEVERE_WEATHER, GOLDEN_HOUR, SUNSHINE, DRIVING_CONDITIONS, HEALTH_ALERTS, MOON_PHASE, HUMIDITY, PRECIPITATION_CHART, PRESSURE_TREND, WIND_TREND, DETAILS_GRID, CLOTHING, PET_SAFETY)
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
- `ReorderableCardColumn` still available as utility but Today tab inlines card rendering directly

## Gotchas
- `LocalUnitSettings.current` cannot be called inside Canvas DrawScope (not @Composable) — extract before Canvas
- `RadarMapView` requires `currentTileUrl`, `previousTileUrl`, and `overlayTileUrl` params
- Gradle wrapper jar/scripts were missing from repo initially — generated with extracted Gradle dist
- MSVC raw string limit doesn't apply here, but Wear OS module is scaffolding-only (`:wear` in settings.gradle.kts)
- `BlitzortungService.isConnected` must be `@Volatile` with `@Synchronized` connect/disconnect to avoid race conditions
- Unimplemented weather source adapters (OWM, Pirate Weather, Bright Sky) are hidden from Settings UI
- `Icons.Filled.CompareArrows` is deprecated — migrated to `Icons.AutoMirrored.Filled.CompareArrows` in v1.5.0

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
- **v1.5.0** — 4 new cards (humidity, precip chart, pressure trend, wind forecast — 25 total). Single-LazyColumn Today tab (perf). Pull-to-refresh all tabs. Compare screen with icons+highlighting. Outdoor score factor breakdown. Location weather icons. Alert expiry countdown. Temp range bars. Feels-like graph overlay. Card reorder in Settings. Collapsible settings. Smart rain timeline. Sun countdown. Time-aware summary with UV/humidity warnings. Wind direction arrows. Dew point comfort colors. Data staleness coloring. 20+ crash/bug fixes (coordinate checks, widget safety, wallpaper overflow, manifest). Crashlytics removed. Dead code cleanup. README/CHANGELOG updated.
- **v1.4.0** — Security hardening (allowBackup=false, network_security_config, debug-only logging, Firestore rules, safeValueOf), Crashlytics, offline detection + banner, reduced motion, tab crossfade, LazyColumn card perf, ImmutableList recomposition fix, shared RenderCard (~260 lines deduped), constructor injection for sourceManager, 6h cache eviction, OkHttp retry, parallel sub-fetches, user-friendly errors, widget staleness/accessibility/battery-skip, 74 new unit tests
