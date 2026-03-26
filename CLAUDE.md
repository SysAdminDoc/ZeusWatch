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
- `CardType`: 19 card types (WEATHER_SUMMARY, RADAR_PREVIEW, NOWCAST, HOURLY_FORECAST, TEMPERATURE_GRAPH, DAILY_FORECAST, UV_INDEX, WIND_COMPASS, AIR_QUALITY, POLLEN, OUTDOOR_SCORE, SNOWFALL, SEVERE_WEATHER, GOLDEN_HOUR, SUNSHINE, DRIVING_CONDITIONS, HEALTH_ALERTS, MOON_PHASE, DETAILS_GRID)

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

## Release Checklist (v1.3.0)
- [x] Version bumped: 1.3.0, versionCode 50
- [x] All string resources present
- [x] ProGuard rules for all libraries
- [x] Widget info XMLs + manifest registrations
- [x] Deep links for all screens
- [x] Graceful fallback for missing Meteocons Lottie assets
- [x] Dynamic version display (no hardcoded version strings)
- [ ] Download Meteocons Lottie JSON files into `assets/meteocons/` (optional, Material Icons are the default)
- [ ] Run `./gradlew assembleStandardRelease` to verify build
- [ ] Run `./gradlew testStandardDebugUnitTest` to verify tests pass

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
