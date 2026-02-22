# Changelog

All notable changes to Nimbus Weather are documented here.

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
