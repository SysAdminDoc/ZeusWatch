# ZeusWatch (Nimbus) - Android Weather App

Package: `com.sysadmindoc.nimbus`
Kotlin + Jetpack Compose, Hilt DI, Room DB, MVVM architecture.

## Architecture

### Data Layer
- **APIs**: OpenMeteo (weather + AQ + pollen + minutely_15 nowcast + historical), NWS (alerts, US-only), RainViewer (radar tiles), Open-Meteo Geocoding
- **Room DB**: `NimbusDatabase` with `WeatherDao` (cache) + `SavedLocationDao`
- **DataStore**: `UserPreferences` (units, display, cards, notifications, health, haptics), `WidgetDataProvider`
- **Repositories**: `WeatherRepository`, `LocationRepository`, `AlertRepository`, `RadarRepository`, `AirQualityRepository`
- All raw API values are **metric** (Celsius, km/h, mm, hPa, meters). Conversion happens in `WeatherFormatter`.

### UI Layer
- **Screens**: Main (Today/Hourly/Daily tabs + Radar tab), Radar (Windy WebView OR native MapLibre), Locations, Settings, Compare
- **Navigation**: `NimbusNavHost` with bottom nav (`BottomTab` enum). Routes: main, settings, radar, locations, compare
- **Theme**: `NimbusTheme` with weather-adaptive color schemes (sunny=amber, rainy=blue, snowy=ice, storm=purple) or static dark
- **Adaptive**: `AdaptiveLayoutInfo` for phone vs tablet layouts (`isCompact` flag)
- **Dynamic Cards**: `ReorderableCardColumn` renders cards in user-defined order, respecting visibility toggles from `CardType` enum

### Background
- `AlertCheckWorker` - 15min periodic check for severe weather alerts across saved locations
- `WidgetRefreshWorker` - periodic widget data refresh
- `AlertNotificationHelper` - 4 notification channels (Extreme/Severe/Moderate/Minor)
- `WeatherNotificationHelper` - persistent current-weather notification

### Build Variants
- `standard` flavor: Google Play Services for location (FusedLocationProvider)
- `freenet` flavor: F-Droid compatible, no proprietary deps

## Settings & Customization System

### Enums (in `UserPreferences.kt`)
- `RadarProvider`: WINDY_WEBVIEW, NATIVE_MAPLIBRE
- `IconStyle`: MATERIAL, METEOCONS (Lottie)
- `ThemeMode`: STATIC_DARK, WEATHER_ADAPTIVE
- `SummaryStyle`: TEMPLATE, AI_GENERATED
- `AlertMinSeverity`: EXTREME, SEVERE, MODERATE, ALL
- `CardType`: 21 card types (WEATHER_SUMMARY, RADAR_PREVIEW, NOWCAST, HOURLY_FORECAST, TEMPERATURE_GRAPH, DAILY_FORECAST, UV_INDEX, WIND_COMPASS, AIR_QUALITY, POLLEN, OUTDOOR_SCORE, SNOWFALL, SEVERE_WEATHER, GOLDEN_HOUR, SUNSHINE, DRIVING_CONDITIONS, HEALTH_ALERTS, MOON_PHASE, DETAILS_GRID, CLOTHING, PET_SAFETY)

### Settings Sections (in SettingsScreen)
1. Display (radar provider, icon style, theme mode, summary style)
2. Cards (visibility toggles for each CardType)
3. Units (temperature, wind, pressure, precipitation, time format)
4. Notifications (alerts, persistent weather, nowcasting, driving, health)
5. Data Display (snowfall, CAPE, sunshine, golden hour, Beaufort, outdoor score, yesterday comparison)
6. Health (migraine alerts, pressure threshold)
7. Accessibility (haptic feedback)
8. Visual Effects (weather particles)
9. About

## New Components Created

### Utility Files
- `WeatherSummaryEngine.kt` - Template-based NLG: "Partly cloudy today with afternoon showers. Highs near 78."
- `HapticHelper.kt` - Severity-based vibration patterns for alerts
- `DrivingConditionEvaluator.kt` - Evaluates ice, fog, hydroplaning, wind, snow from standard forecast data
- `HealthAlertEvaluator.kt` - Migraine triggers (pressure/temp swings), respiratory (humidity), arthritis (temp swing)
- `MeteoconMapper.kt` - Maps WMO codes to Meteocons Lottie filenames (day/night variants)
- `WeatherNotificationHelper.kt` - Persistent current-weather notification
- `WidgetRefreshAction.kt` - Glance ActionCallback for tap-to-refresh

### UI Cards
- `WeatherSummaryCard.kt` - NLG weather summary
- `NowcastCard.kt` - 60-min precipitation bar chart with "Rain starting in X min" summary
- `OutdoorScoreCard.kt` - Circular gauge 0-100 with weighted scoring
- `DrivingAlertCard.kt` - Severity-coded driving hazard alerts
- `HealthAlertCard.kt` - Health-related weather trigger alerts
- `GoldenHourCard.kt` - Photography golden/blue hour times
- `SunMoonArc.kt` - Sun position arc visualization (semicircle, horizon line, current position dot)
- `AqiGauge.kt` - Circular 270-degree AQI gauge with gradient colors
- `AnimatedWeatherIcon.kt` - Switches between Material Icons and Meteocons Lottie based on settings
- `ReorderableCardColumn.kt` - Renders cards in user-defined order respecting visibility

### Screens
- `CompareScreen.kt` + `CompareViewModel.kt` - Side-by-side weather comparison between two saved locations
- `RadarPlaybackControls.kt` - Play/pause, frame slider, timestamp overlay for native MapLibre radar

## API Expansion
- **New OpenMeteo params**: `snowfall`, `snow_depth` (current/hourly), `wind_gusts_10m`, `sunshine_duration` (hourly), `snowfall_sum`, `sunshine_duration`, `wind_gusts_10m_max` (daily)
- **New endpoints**: `getMinutely15Forecast()` for precipitation nowcasting, `getHistoricalForecast()` for yesterday comparison
- **New data classes**: `MinutelyPrecipitation`, `Minutely15Weather`
- **New WeatherFormatter methods**: `formatSnowfall`, `formatSnowDepth`, `formatSunshineDuration`, `formatPrecipitationHours`, `formatCape`, `capeDescription`, `beaufortScale` (13-level), `goldenHourTimes`, `outdoorActivityScore` (weighted composite), `outdoorScoreLabel`

## Audit Fixes (All Rounds)

### Round 1 — Integration Fixes
- **Race condition fixed**: `fetchYesterdayComparison` changed to `suspend fun` so it completes before `computeDerivedData` reads `yesterdayHigh`
- **RadarScreen settings**: No longer uses `LocalUnitSettings` (wasn't in nav context). Now reads from `RadarViewModel.settings` flow directly
- **Weather-adaptive theming wired**: `MainActivity` injects `UserPreferences`, reads `settings.themeMode`, passes `useWeatherAdaptive` to `NimbusTheme`
- **Compare screen accessible**: CompareArrows toolbar icon in MainScreen, routed through `onNavigateToCompare` callback chain
- **Widget tap-to-refresh**: All 3 widgets use `actionRunCallback<WidgetRefreshAction>()` when null, `actionStartActivity` when loaded
- **SunArc integrated**: `MoonPhaseCard` now renders `SunArc` between sunrise/sunset row and moon data

### Round 2 — Feature Completion Audit
- **Tablet layout → ReorderableCardColumn**: Wide layout branch replaced FlowRow with same dynamic card system as compact. Both phone and tablet respect card ordering/visibility settings
- **AnimatedWeatherIcon wired**: `HourlyForecastStrip` now uses `AnimatedWeatherIcon` which respects `settings.iconStyle` (Material vs Meteocons Lottie)
- **Beaufort wind colors**: `WindCompass` now shows `Beaufort X • Label` with color when `settings.showBeaufortColors` enabled
- **UV peak annotation**: `UvIndexBar` accepts optional `hourly` list, shows "Peaks at 2 PM" when future UV is higher than current
- **Snowfall in details grid**: `WeatherDetailsGrid` shows snowfall/snow depth row when either is > 0
- **DailyForecastList expanded**: Detail view now shows precipitationHours, windGustsMax, sunshineDuration, snowfallSum
- **Proactive location cache**: `WidgetRefreshWorker` now pre-fetches weather for ALL saved locations after widget update
- **FOREGROUND_SERVICE permission**: Added to AndroidManifest.xml for persistent notification support

### Round 3 — Stub Cards & Wiring Completion
- **SnowfallCard.kt**: New card showing current snowfall rate + snow depth, auto-hidden when no snow
- **SevereWeatherCard.kt**: CAPE-based thunderstorm potential indicator with severity coloring
- **SunshineDurationCard.kt**: Sunshine hours with progress ring gauge
- **AnimatedWeatherIcon everywhere**: Wired into `CurrentConditionsHeader` (hero icon), `DailyForecastList` (daily row icons), plus already done `HourlyForecastStrip`
- **Weather-adaptive theming fully wired**: `WeatherThemeState` CompositionLocal provided by MainScreen with current `weatherCode`/`isDay`. `NimbusTheme` reads it via `LocalWeatherThemeState.current` — accent colors shift automatically
- **Persistent weather notification**: `WidgetRefreshWorker` now shows/updates persistent notification when `settings.persistentWeatherNotif` enabled, dismisses when disabled
- **Haptic feedback on alerts**: `AlertBanner` reads `settings.hapticFeedbackForAlerts`, triggers `HapticHelper.vibrateForAlert()` via `LaunchedEffect` when top alert changes

### Round 4 — Interactive Graph, Forecast Widget, Beaufort Ring
- **Interactive TemperatureGraph**: Complete rewrite with drag-to-inspect (horizontal drag + tap). Shows vertical guide line, highlighted dot, tooltip with temp/time/precip%. Also added precipitation probability bars behind the curve.
- **NimbusForecastStripWidget**: New 4x1 compact widget showing "Now" temp + next 5 hourly temps with icons and precip%. Registered in manifest, included in WidgetRefreshWorker.
- **Beaufort color ring on WindCompass**: Compass outer circle changes color based on Beaufort scale when `showBeaufortColors` enabled. Ring is thicker (3px vs 1.5px) when colored.
- **Code quality audit**: Verified all 16 recently-modified files. All "missing imports" were false positives — all referenced classes are same-package (`ui.component`) and resolve automatically in Kotlin.

### Round 5 — Release Preparation (v1.3.0)
- **Version bump**: versionCode 40→50, versionName "1.2.0"→"1.3.0" across build.gradle.kts, strings.xml, NetworkModule User-Agent headers
- **Dynamic version display**: MainScreen footer and Settings About section now use `BuildConfig.VERSION_NAME` instead of hardcoded strings
- **Widget string resource**: Added `widget_forecast_strip_desc` to strings.xml (was missing, would crash)
- **Meteocons graceful fallback**: `AnimatedWeatherIcon` now checks `if (composition != null)` before rendering Lottie — falls back to Material Icons when assets missing
- **Meteocons assets directory**: Created `assets/meteocons/` with README listing required files
- **ProGuard rules**: Added Room, Lottie, and Coil rules for release builds
- **Compare deep link**: Added "compare" to shortcuts.xml and MainActivity's deep link resolver
- **New string resources**: Added 10+ new feature strings (weather summary, nowcast, outdoor score, etc.)

### Round 6 — Roadmap Items & Code Consolidation
- **CAPE fully wired**: Added `cape` to OpenMeteoApi CURRENT_PARAMS + HOURLY_PARAMS, OpenMeteoResponse (`CurrentWeather.cape`, `HourlyWeather.cape`), `CurrentConditions.cape`, WeatherRepository mapping. Both compact and tablet SevereWeatherCard stubs now render actual CAPE data.
- **AQI 5-day forecast**: Changed `AirQualityApi.forecast_days` from 3 to 5
- **Deep link scheme fixed**: All shortcuts.xml entries now use `zeuswatch://` consistently (was mixing `nimbus://` and `zeuswatch://`)
- **Persistent notification from ViewModel**: `MainViewModel.computeDerivedData()` now calls `WeatherNotificationHelper.showOrUpdate()` when `persistentWeatherNotif` enabled, giving instant notification updates on weather load (not just periodic worker)
- **Share helpers consolidated**: Merged `WeatherShareHelper.kt` (image) into `ShareWeatherHelper.kt` (text + image). Single file, single import. Fixed bitmap memory leak with `bitmap.recycle()` in `finally` block. Deleted `WeatherShareHelper.kt`.
- **WidgetTheme cleaned**: Removed unused imports, improved weatherIconRes code mapping granularity (drizzle vs rain ranges)

### Round 7 — Phase 3 Items + Cache Config
- **Phase 3.1+3.2 verified**: Native MapLibre radar with animated playback was already fully wired in `RadarScreen`/`RadarTab` — both pass `radarState.currentFrame?.tileUrl` to `RadarMapView` and wire `RadarPlaybackControls` with `RadarViewModel`. Settings toggle between Windy WebView and native.
- **Configurable cache TTL**: Added `cacheTtlMinutes` pref (15/30/60/120 min radio in new "Advanced" settings section). `WeatherCacheEntity` now has `isExpired(maxAgeMs)` method + `ageMinutes` property. Default: 30 min.
- **Wear OS module (Phase 3.3)**: Full scaffolding created as `wear/` Gradle module:
  - `WearMainActivity` + `WearWeatherViewModel` — basic current conditions screen
  - `WeatherComplicationService` — SHORT_TEXT (temp), LONG_TEXT (temp+condition), RANGED_VALUE (UV index) complications for any watch face
  - `WeatherTileService` — ProtoLayout tile showing temp, condition, high/low
  - `WearWeatherRepository` — lightweight OkHttp direct call to Open-Meteo (no Retrofit, minimal APK)
  - `WearNetworkModule` — Hilt DI for OkHttpClient
  - Registered in `settings.gradle.kts` as `:wear` module
  - Separate ProGuard rules for Wear libraries

### Round 8 — Per-Widget Config + Map Layers
- **Phase 3.5: Per-widget location configuration**:
  - `WidgetLocationPrefs.kt` — DataStore mapping `appWidgetId -> locationId` with CRUD operations
  - `WidgetConfigActivity.kt` — Hilt-injected config activity showing saved locations list. User picks which location a widget displays. Supports "Default (GPS)" option
  - Registered in AndroidManifest.xml with `APPWIDGET_CONFIGURE` intent filter
  - Added `android:configure` attribute to medium, large, and forecast strip widget info XMLs
- **Phase 4.4: Radar map layer selector**:
  - `RadarLayerSelector.kt` — Horizontal chip row with 5 layers: Radar, Temperature, Wind, Clouds, Precipitation
  - `RadarLayer` enum with tile URL templates for each layer type
  - Wired into `RadarTab` native MapLibre mode as overlay above the map, below playback controls

### Round 9 — Bug Fixes + Full Roadmap Implementation
- **Widget per-location wiring**: `WidgetRefreshWorker` now reads `WidgetLocationPrefs.getAllMappings()`, fetches weather per unique location, saves per-widget via `WidgetDataProvider.save(ctx, data, widgetId)`. All 4 widget classes (`NimbusSmallWidget`, `NimbusMediumWidget`, `NimbusLargeWidget`, `NimbusForecastStripWidget`) load per-widget data via `GlanceAppWidgetManager.getAppWidgetId()`, with fallback to global data.
- **Radar layer selector wired**: `RadarMapView` now accepts `overlayTileUrl` parameter. `RadarTab` and `RadarScreen` pass the selected layer's tile URL template to the map. Non-radar layers (Temperature, Wind, Clouds, Precipitation) render as raster overlays at 0.6 opacity. Playback controls hidden when viewing non-radar layers.
- **Phase 3.4: Lightning strike overlay**: `BlitzortungService` connects to `wss://ws1.blitzortung.org/` WebSocket for real-time global lightning data. Renders strikes as GeoJSON circle markers (yellow glow + white points) on MapLibre. Added `LIGHTNING` to `RadarLayer` enum. Strikes shown as overlay in both Lightning and Radar modes.
- **Phase 3.6: Gemini Nano summaries**: `GeminiNanoSummaryEngine` wraps `com.google.ai.edge.aicore` GenerativeModel. `WeatherSummaryEngine.generateWithStyle()` delegates to AI when `SummaryStyle.AI_GENERATED`, with automatic template fallback. Non-blocking: template renders instantly, AI result replaces it asynchronously.
- **Phase 3.7: Live weather wallpaper**: `WeatherWallpaperService` with `WallpaperParticleSystem` rendering rain, snow, thunderstorm (with lightning flash), sun rays, cloud wisps, and fog effects. Translucent surface over user's existing wallpaper. 30fps animation loop, battery-conscious visibility management. Registered in manifest with `weather_wallpaper.xml` metadata.
- **Phase 3.8: Community weather reports**: Firebase Firestore-backed crowd-sourced condition reporting. `CommunityReportRepository` with submit/query/delete, rate limiting (1 per 5 min), device-ID-scoped. `ReportSubmitSheet` bottom sheet with condition chips + note field. Reports rendered as colored circle markers on RadarMapView. FAB on radar screen to submit reports.
- **Phase 4.1: Multi-source fallback**: `WeatherSourceManager` with adapter pattern for forecast, alerts, AQI, minutely data. `SourceConfig` in settings for primary + fallback source per data type. `WeatherSourceProvider` enum: Open-Meteo, NWS, OpenWeatherMap, Pirate Weather, Bright Sky, Environment Canada. Settings UI with source dropdowns and API key fields.
- **Phase 4.2: Custom icon packs**: `IconPackManager` discovers packs from `assets/iconpacks/` (bundled) and external APKs (via `com.sysadmindoc.nimbus.ICON_PACK` intent). `IconPack` model with manifest.json format. `AnimatedWeatherIcon` extended for `CUSTOM` icon style with bitmap loading. Settings shows pack selector when CUSTOM style is chosen.
- **Phase 4.3: International alert sources**: `AlertSourceAdapter` interface with 4 implementations: `NwsAlertAdapter` (US), `MeteoAlarmAdapter` (31 EU countries), `JmaAlertAdapter` (JP), `EnvironmentCanadaAlertAdapter` (CA with province resolver). `AlertRepository` auto-detects country via Geocoder, queries matching adapters in parallel, merges and deduplicates. `AlertSourcePreference` enum in settings.
- **Phase 4.5: Tablet two-pane layout**: `MainScreen` detects tablets (screenWidthDp >= 840) and renders weather + radar side-by-side (55/45 split). Bottom nav filters out RADAR tab on tablets. `ZeusWatchBottomNav` accepts `visibleTabs` parameter.

### Round 10 — Settings Gaps, Accessibility & Polish
- **Visibility unit setting**: Added `VisibilityUnit` section to SettingsScreen (miles/km). Key and setter existed in UserPreferences but had no UI. Added SettingsViewModel method.
- **Alert source preference UI**: `AlertSourcePreference` radio group added to Notifications section in SettingsScreen (Auto-detect, NWS only, MeteoAlarm, JMA, ECCC, All sources). Was only wired in data layer, not exposed in UI.
- **Migraine pressure threshold selector**: Replaced static `SettingInfo` display with radio buttons (3.0/5.0/7.0/10.0 hPa/3h) with sensitivity labels. Users can now customize the threshold.
- **Performance: batched state updates**: `computeDerivedData()` now computes all derived values (summary, outdoor score, driving alerts, health alerts, golden hour) and applies them in a single `_uiState.update` call — reduces from 5 recompositions to 1.
- **TalkBack: liveRegion on AlertBanner**: Added `LiveRegionMode.Assertive` semantics so TalkBack announces new weather alerts automatically, with content description summarizing all active alerts.
- **TalkBack: mergeDescendants on WeatherCard**: Added `semantics(mergeDescendants = true)` to the shared `WeatherCard` component, so TalkBack reads each card as a single unified element instead of focusing on individual text nodes.
- **"Warmest" day label**: `DailyForecastList` now highlights the warmest day in the next 7 days with an amber "Warmest" label below the day name.
- **UV safe sun exposure**: `UvIndexBar` now shows estimated safe sun exposure time without SPF (formula: 200/(UV*3), clamped 5-120 min).
- **CHANGELOG updated**: Added v1.1.0, v1.2.0, v1.3.0 entries covering all features from rounds 5-10.
- **Stale TODO removed**: Removed misleading `// TODO: Replace FusedLocationProvider` comment in build.gradle.kts (freenet flavor already uses LocationManager).

## Release Checklist (v1.3.4)
- [x] Version bumped: 1.3.4, versionCode 54
- [x] All string resources present
- [x] ProGuard rules for all libraries (including Firebase, Coroutines, DataStore, WorkManager, Hilt)
- [x] Widget info XMLs + manifest registrations
- [x] Deep links for all screens
- [x] Graceful fallback for missing Meteocons Lottie assets
- [x] Dynamic version display (no hardcoded version strings)
- [x] Accessibility contentDescriptions on all alert/safety icons
- [x] No unsafe `!!` assertions on nullable API responses
- [x] `assembleStandardDebug` builds successfully
- [x] `testStandardDebugUnitTest` all tests pass
- [ ] Download Meteocons Lottie JSON files into `assets/meteocons/` (optional, Material Icons are the default)
- [ ] Run `./gradlew assembleStandardRelease` to verify release build
- [ ] Test on physical device

## Key Architecture Patterns
- Unit settings: `CompositionLocalProvider(LocalUnitSettings provides ...)` in MainScreen for child composables. Standalone screens (Radar, Compare) read from their own ViewModel's `prefs.settings` flow instead
- All API calls return `Result<T>` for consistent error handling
- Weather data cached in Room as serialized JSON (`WeatherCacheEntity`)
- Tile math: `latLonToTile()` in MainViewModel for slippy map XYZ coordinates
- Card system: `CardType` enum + `ReorderableCardColumn` — cards are dynamically ordered/toggled via settings
- Radar provider: `RadarScreen`/`RadarTab` check `settings.radarProvider` and render either Windy WebView or native MapLibre+playback
- Weather-adaptive theming: `MainActivity` reads theme mode from prefs, passes to `NimbusTheme`
- Icon delegation: `AnimatedWeatherIcon` checks `settings.iconStyle` to choose Material vs Meteocons Lottie
- Derived data: `MainViewModel.computeDerivedData()` runs summary engine, outdoor score, driving eval, health eval after weather + yesterday data loads
- Per-widget data: `WidgetDataProvider` supports global save/load (fallback) and per-widget keyed save/load via `wKey(appWidgetId, field)` prefix
- Multi-source fallback: `WeatherSourceManager` tries primary adapter, catches failure, tries fallback. Adapters implement `WeatherSourceAdapter` interface
- Alert source selection: `AlertRepository` auto-detects country via `Geocoder`, selects `AlertSourceAdapter` implementations. Configurable via `AlertSourcePreference`
- Lightning overlay: `BlitzortungService` WebSocket → `recentStrikes: StateFlow` → `RadarMapView` GeoJSON circle layers
- Community reports: `CommunityReportRepository` → Firestore → `RadarMapView` colored circle + symbol layers
- Custom icon packs: `IconPackManager` discovers bundled (`assets/iconpacks/`) and external APK packs → `AnimatedWeatherIcon` loads bitmaps when `CUSTOM` style
- Tablet layout: `MainScreen` checks `screenWidthDp >= 840` → two-pane Row with weather (0.55) + radar (0.45)

## Round 11 — Clothing, Pet Safety, 72h Hourly, Dominant Pollutant (v1.3.1)

### New features
- **Clothing suggestions card** (`ClothingSuggestionEvaluator.kt` + `ClothingSuggestionCard.kt`): Rule-based outfit recommendations from feels-like temp, rain, snow, UV, wind. `CLOTHING` CardType added.
- **Pet safety card** (`PetSafetyEvaluator.kt` + `PetSafetyCard.kt`): Pavement temperature estimates, heat stress (heat index), cold exposure, storm anxiety alerts. `PET_SAFETY` CardType added.
- **72-hour hourly forecast**: `hourlyForecastHours` setting in `NimbusSettings` (48 or 72), threaded through `UserPreferences` → `WeatherRepository` → `OpenMeteoApi.getForecast(forecastHours=)`. UI toggle in Settings > Data Display.
- **Dominant pollutant highlighting**: `AqiCard` computes normalized EPA breakpoint scores for PM2.5/PM10/O3/NO2, highlights the worst with amber background and label color.

### Files changed
- `CardConfig.kt`: Added `CLOTHING`, `PET_SAFETY` entries
- `MainUiState`: Added `clothingSuggestions`, `petSafetyAlerts` fields
- `MainViewModel.computeDerivedData()`: Calls `ClothingSuggestionEvaluator` and `PetSafetyEvaluator`
- `MainScreen.kt`: Wires both new cards in compact and wide layouts
- `UserPreferences.kt`: Added `HOURLY_FORECAST_HOURS` key, `hourlyForecastHours` field, setter
- `WeatherRepository.kt`: Injected `UserPreferences`, reads `hourlyForecastHours` for API call
- `SettingsViewModel.kt`: Added `setHourlyForecastHours`
- `SettingsScreen.kt`: Added 48h/72h radio toggle, `onHourlyForecastHours` callback
- `AqiCard.kt`: Added pollutant score computation and `isWorst` parameter to `PollutantChip`

## Round 13 — Yesterday Comparison, Normals Band, Reorderable Locations (v1.3.3)

### New features
- **"Warmer/cooler than yesterday" label**: `CurrentConditionsHeader` now accepts `yesterdayHigh` and shows "5° warmer than yesterday" (amber) or "3° cooler than yesterday" (blue) when diff >= 2°. Wired from `MainUiState.yesterdayHigh`.
- **Temperature normals band**: `TemperatureGraph` accepts `normalHigh`/`normalLow` params. Draws a shaded band with dashed boundary lines behind the temperature curve. Normals computed from 16-day daily forecast average high/low in MainScreen.
- **Drag-to-reorder saved locations**: Long-press the drag handle icon on any non-GPS location to reorder. `SavedLocationDao.updateSortOrder()` persists order. `LocationRepository.reorderLocations()` batch-updates sort orders. `LocationsViewModel.moveLocation()` exposes reorder to UI.

### Files changed
- `CurrentConditionsHeader.kt`: Added `yesterdayHigh` param, comparison label with warm/cool colors
- `TemperatureGraph.kt`: Added `normalHigh`/`normalLow` params, shaded normals band with dashed lines
- `MainScreen.kt`: Added missing imports (ClothingSuggestionCard, PetSafetyCard), passes `yesterdayHigh` to header, computes normals for graph
- `SavedLocationDao.kt`: Added `updateSortOrder(id, order)` query
- `LocationRepository.kt`: Added `reorderLocations(orderedIds)` method
- `LocationsViewModel.kt`: Added `moveLocation(fromIndex, toIndex)` method
- `LocationsScreen.kt`: Added drag handle icon, long-press drag gesture detection, drag offset tracking, reorder callback threading
- `LocationsViewModelTest.kt`: Updated to pass mock `WeatherRepository`

## Round 12 — Details Polish, Location Previews, AQI Forecast (v1.3.2)

### New features
- **Dew point comfort descriptor**: `WeatherFormatter.dewPointComfort()` maps dew point to "Dry"/"Comfortable"/"Muggy"/"Oppressive". Shown as subtitle in `WeatherDetailsGrid` pressure cell.
- **Feels-like explanation**: `WeatherFormatter.feelsLikeReason()` explains "Wind chill"/"Heat index" in `CurrentConditionsHeader` when feels-like differs by 2+ degrees.
- **Pressure trend indicator**: Added `surface_pressure` to hourly API params. `WeatherFormatter.pressureTrend()` computes Rising/Falling/Steady from first 6 hourly pressure readings. `WeatherDetailsGrid` now accepts `hourly` parameter.
- **Saved location temperature preview**: `LocationsViewModel` injects `WeatherRepository`, loads cached temps for all saved locations. `SavedLocationItem` shows temperature next to location name.
- **5-day daily AQI forecast**: `AirQualityRepository` aggregates hourly AQI into daily max. New `DailyAqi` data class. `AqiCard` shows 5-day forecast as colored vertical bars.
- **Hourly feels-like temperature**: `HourlyForecastStrip` shows feels-like temp when it differs by 3+ degrees from actual.

### Files changed
- `WeatherFormatter.kt`: Added `dewPointComfort()`, `feelsLikeReason()`, `pressureTrend()`
- `WeatherDetailsGrid.kt`: Added hourly param, dew point subtitle, pressure trend subtitle
- `CurrentConditionsHeader.kt`: Feels-like text now includes reason
- `OpenMeteoApi.kt`: Added `surface_pressure` to HOURLY_PARAMS
- `OpenMeteoResponse.kt`: Added `surfacePressure` to `HourlyWeather`
- `WeatherData.kt`: Added `surfacePressure` to `HourlyConditions`
- `WeatherRepository.kt`: Maps `surfacePressure` in hourly mapping
- `LocationsViewModel.kt`: Injected `WeatherRepository`, added `locationTemps` StateFlow
- `LocationsScreen.kt`: Threads `locationTemps` through to `SavedLocationItem`, shows temp
- `AirQualityData.kt`: Added `DailyAqi` data class, `dailyAqi` field
- `AirQualityRepository.kt`: Aggregates hourly AQI into daily max by date
- `AqiCard.kt`: Added `DailyAqiBar` composable, 5-day forecast section
- `HourlyForecastStrip.kt`: Shows feels-like temp when significantly different

## Round 14 — Release Audit & Bug Fixes (v1.3.4)

### Bugs fixed
- **Unsafe `!!` removed** in `AirQualityRepository.kt` line 53: `response.hourly!!` changed to `response.hourly` — the null-assertion was redundant since the enclosing `if` block already verified `response.hourly != null`
- **NaN guard added** to `TemperatureGraph.kt`: Added `!normalHigh.isNaN() && !normalLow.isNaN()` checks before rendering the temperature normals band. Prevents rendering artifacts if daily averages produce NaN (e.g. empty daily list)
- **`sourceManager` lateinit safety** in `WeatherRepository.kt`: `getWeather()` now checks `::sourceManager.isInitialized` before accessing the field-injected `sourceManager`. Falls back to `getWeatherDirect()` if not yet initialized (e.g. in tests or early lifecycle)

### Hardening
- **ProGuard rules expanded** in `proguard-rules.pro`: Added rules for Firebase (`com.google.firebase.**`), Coroutines (MainDispatcherFactory, CoroutineExceptionHandler, volatile fields), DataStore (protobuf GeneratedMessageLite), WorkManager (Worker/ListenableWorker constructors), and Hilt (`_HiltModules*`, `dagger.hilt.**`)
- **Accessibility contentDescriptions** added to 7 alert/safety icon components:
  - `AlertBanner.kt`: Warning icon now describes alert event name
  - `AlertDetailSheet.kt`: Warning icon describes alert severity
  - `SevereWeatherCard.kt`: Thunderstorm icon says "Severe weather potential"
  - `HealthAlertCard.kt`: Health icon describes alert type
  - `PetSafetyCard.kt`: Pets icon says "Pet safety alert"
  - `DrivingAlertCard.kt`: Car icon describes driving alert type

### Files changed
- `AirQualityRepository.kt`: Removed `!!` on line 53
- `TemperatureGraph.kt`: Added NaN guard to normals band condition
- `WeatherRepository.kt`: Added `isInitialized` check on `sourceManager`
- `proguard-rules.pro`: Added Firebase, Coroutines, DataStore, WorkManager, Hilt rules
- `AlertBanner.kt`, `AlertDetailSheet.kt`, `SevereWeatherCard.kt`, `HealthAlertCard.kt`, `PetSafetyCard.kt`, `DrivingAlertCard.kt`: Added meaningful contentDescriptions
- `build.gradle.kts`: Version bumped to 1.3.4 (versionCode 54)
- `CHANGELOG.md`: Added v1.3.4 section

### Release verification
- `assembleStandardDebug` — BUILD SUCCESSFUL
- `testStandardDebugUnitTest` — ALL TESTS PASS
