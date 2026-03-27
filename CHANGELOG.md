# Changelog

All notable changes to Nimbus Weather are documented here.

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
