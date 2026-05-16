# Nimbus Weather - Phased Build Plan

## Phase 1: Core Architecture + Current Conditions (COMPLETE)
**Version:** v0.1.0
**Status:** Delivered

- Gradle build system with version catalog + Compose BOM
- Kotlin + Jetpack Compose + Hilt DI architecture
- MVVM + Repository pattern (Clean Architecture)
- Open-Meteo API integration (forecast + geocoding)
- Google Play Services location (standard flavor) + LocationManager fallback (freenet flavor)
- Current conditions hero header (large temp, condition, feels-like, high/low)
- Horizontally scrollable hourly forecast strip (24h, temp, icon, precip%)
- 16-day daily forecast list (day name, icon, high/low, precip%)
- Today's Details grid (humidity, wind, UV, pressure, visibility, dew point, sunrise/sunset, cloud cover, precipitation)
- Dark gradient theme (TWC-inspired deep navy palette)
- Semi-transparent glassmorphism cards
- Weather icon mapping (WMO codes -> Material Icons)
- Condition-based dynamic sky gradients
- Pull-to-refresh
- Location permission handling
- Edge-to-edge display
- ProGuard config, F-Droid flavor skeleton

---

## Phase 2: Polish + Expanded Hourly/Daily Detail
**Target:** v0.2.0
**Status:** Delivered

- Animated Lottie weather icons (replace Material Icons)
- Canvas-drawn particle effects (rain, snow, sun rays on header)
- Collapsing toolbar (large header -> compact on scroll)
- Tappable daily rows expanding to day/night detail breakdown
- Hourly detail bottom sheet on tap
- Temperature line graph (Canvas API or Vico)
- Wind direction compass visualization
- UV index color-coded bar
- Shimmer loading skeleton
- Error state improvements with offline cache (Room)
- DataStore for user preferences (units: F/C, mph/km/h, inHg/hPa)
- Settings screen (temperature unit, wind unit, pressure unit, theme)

---

## Phase 3: Interactive Radar Map
**Target:** v0.3.0
**Status:** Delivered

- MapLibre integration with CartoCDN Dark Matter basemap
- RainViewer API integration for global radar tiles
- Radar timeline slider (past 2hr + 30min forecast)
- Play/pause animation with frame crossfade
- Layer toggles (precipitation, clouds, temperature)
- Full-screen radar view with pinch-to-zoom
- Radar mini-preview card on main screen
- Tile caching for offline radar playback
- Iowa State Mesonet NEXRAD tiles (US high-res option)

---

## Phase 4: Severe Weather Alerts + Notifications
**Target:** v0.4.0
**Status:** Delivered

- NWS API integration (US severe weather alerts)
- Alert banner on main screen (tornado, thunderstorm, flood, winter storm, fire)
- Alert detail bottom sheet
- Push notifications via WorkManager
- Per-location alert preferences
- OpenWeatherMap alerts as global fallback
- Alert severity color coding
- Background refresh scheduling

---

## Phase 5: Home Screen Widgets
**Target:** v0.5.0
**Status:** Delivered

- Jetpack Glance widget framework
- Small widget (current temp + icon + location)
- Medium widget (current + 3-day forecast)
- Large widget (current + hourly strip + 5-day)
- Material You dynamic colors on Android 12+
- Dark mode widget variants
- Widget refresh via WorkManager
- Tap-to-open deep links
- Widget configuration activity

---

## Phase 6: Air Quality, Pollen, Astronomy
**Target:** v0.6.0
**Status:** Delivered

- Open-Meteo Air Quality API (PM2.5, PM10, O3, NO2, SO2, CO)
- AQI color-coded display (EPA + European scales)
- Pollen data (alder, birch, grass, mugwort, olive, ragweed)
- Moon phase calculation + display
- Astronomy section (moonrise/moonset, illumination)
- Health index cards (UV skin insights, breathing conditions)

---

## Phase 7: Location Management
**Target:** v0.7.0
**Status:** Delivered

- Multiple saved locations (Room database)
- Location search with Open-Meteo geocoding autocomplete
- "My Location" GPS entry auto-created on first load
- Add/remove saved locations
- Location list screen with search bar
- Navigate to weather for specific saved location
- Location picker button on main screen toolbar
- Database migration v1->v2 for saved_locations table

---

## Phase 8: Final Polish + Release
**Target:** v1.0.0
**Status:** Delivered

- Share weather as text via system share sheet
- Share weather as rendered image via FileProvider
- App shortcuts (long-press launcher: Search, Radar, Settings)
- Deep link handling (nimbus:// URI scheme)
- Adaptive layouts (WindowSizeClass + 2-column FlowRow)
- TalkBack accessibility (content descriptions on all Canvas composables)
- Predictive back gesture support
- Version bump to 1.0.0 (all references)
- LGPL-3.0 license
- Full README with architecture diagram and feature table
- CHANGELOG with complete version history
- GitHub Actions CI/CD (build + lint + test + release)
- F-Droid fastlane metadata
- @Stable annotations on all domain models (18 classes)
- PredictiveBackScaffold component (Radar, Settings, Locations)
- Test suite: 88 unit tests + 24 instrumented Compose UI tests (112 total)
- Test infrastructure: JUnit 4, MockK, Turbine, Compose UI Test
