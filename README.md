# ZeusWatch

![Version](https://img.shields.io/badge/version-1.3.0-blue)
![License](https://img.shields.io/badge/license-LGPL--3.0-green)
![Platform](https://img.shields.io/badge/platform-Android%208.0+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12-4285F4?logo=jetpackcompose&logoColor=white)
![API](https://img.shields.io/badge/API-26+-brightgreen)
![Build](https://github.com/SysAdminDoc/zeuswatch/actions/workflows/build.yml/badge.svg)

> A free, open-source Android weather app with a premium dark UI, deeply customizable cards, and smart alerts. No API keys required. Powered by Open-Meteo, RainViewer, Windy.com, and NWS.

<!-- Screenshots go here after first release build -->
<!-- ![Screenshots](docs/screenshots.png) -->

## Quick Start

```bash
git clone https://github.com/SysAdminDoc/zeuswatch.git
cd zeuswatch
./gradlew assembleStandardDebug
```

Install the APK from `app/build/outputs/apk/standard/debug/` or open in Android Studio and run directly.

**Requirements:** Android Studio Hedgehog+, JDK 17, Android SDK 35

---

## Features

### Core Weather

| Feature | Description |
|---------|-------------|
| **Current Conditions** | Large temp display, feels-like, condition, high/low, sky gradients |
| **Weather Summary** | Natural language forecast: "Partly cloudy today with afternoon showers. Highs near 78. 5 degrees warmer than yesterday." |
| **Hourly Forecast** | 48hr scrollable strip with temp, animated icons, precipitation probability + amount |
| **16-Day Forecast** | Expandable daily rows with rain hours, sunshine duration, snowfall, wind gusts, UV max |
| **Temperature Graph** | Interactive 24hr Canvas graph — drag to inspect any hour. Shows precipitation bars behind the curve |
| **Rain Next Hour** | 60-minute precipitation nowcasting bar chart from Open-Meteo minutely_15 data |
| **Yesterday Comparison** | "5 degrees warmer than yesterday" shown in hero area and weather summary |

### Data & Visualization

| Feature | Description |
|---------|-------------|
| **Today's Details** | 10+ cell grid: humidity, wind (with gusts), UV, pressure, visibility, dew point, sunrise/sunset, cloud cover, snowfall/snow depth |
| **Wind Compass** | Animated Canvas compass with direction needle, Beaufort scale coloring, gust display |
| **UV Index Bar** | Color gradient bar with level markers, descriptions, and "UV peaks at 2 PM" annotation |
| **Air Quality** | PM2.5, PM10, O3, NO2, SO2, CO with EPA/European AQI scales and circular gauge |
| **Pollen Data** | Per-species animated bars: alder, birch, grass, mugwort, olive, ragweed (hourly fallback) |
| **Moon Phase** | Canvas moon illumination with Conway's algorithm, moonrise/moonset, day length |
| **Sun Arc** | Semicircular sun position visualization showing current position from sunrise to sunset |
| **Snowfall Card** | Current snowfall rate + snow depth, auto-hidden when no snow |
| **Sunshine Duration** | Today's sunshine hours with circular progress ring |
| **Severe Weather Potential** | CAPE-based thunderstorm indicator with instability levels |
| **Outdoor Activity Score** | Weighted 0-100 composite score from temp, wind, UV, humidity, precipitation, AQI |
| **Golden Hour** | Morning and evening golden hour times for photographers |

### Smart Alerts & Safety

| Feature | Description |
|---------|-------------|
| **Severe Weather Alerts** | NWS tornado/storm/flood/fire warnings with severity color coding and pulsing borders |
| **4 Notification Channels** | Extreme (alarm sound, bypass DND), Severe (high), Moderate (default), Minor (low) |
| **Alert Deduplication** | Tracks seen alert IDs so the same warning is never re-notified |
| **Multi-Location Alerts** | Optionally monitors all saved locations, not just current GPS |
| **Driving Condition Alerts** | Black ice, fog, low visibility, hydroplaning, high wind, snow/ice — derived from forecast data |
| **Health Alerts** | Migraine triggers (pressure/temp swings), respiratory (humidity extremes), arthritis (temp swing) |
| **Haptic Feedback** | Severity-appropriate vibration patterns when alerts display |
| **Expired Alert Filtering** | Skips alerts past their expiration timestamp |

### Radar

| Feature | Description |
|---------|-------------|
| **Dual Radar Provider** | User-selectable: native MapLibre with RainViewer tiles, or Windy.com WebView embed |
| **Animated Radar Playback** | Play/pause, frame slider, past/forecast labels, timestamp overlay (native mode) |
| **Radar Preview Card** | Live RainViewer tile + CartoDB dark basemap on the Today tab |
| **Radar Tab** | Full-screen radar in the bottom nav with provider-aware rendering |

### Widgets (Glance)

| Widget | Size | Content |
|--------|------|---------|
| **Current** | 3x1 | Icon + temp + location + high/low |
| **3-Day** | 3x2 | Current conditions + 3-day forecast columns + staleness indicator |
| **Forecast** | 4x3 | Current + 6hr hourly strip + 5-day rows + staleness indicator |
| **Hourly Strip** | 4x1 | Current temp + next 5 hourly temps with icons and precip% |

All widgets support tap-to-refresh (when data is null) and tap-to-open (when loaded).

### Customization

| Setting | Options |
|---------|---------|
| **Radar Provider** | Windy WebView / Native MapLibre |
| **Icon Style** | Material Icons / Meteocons Animated (Lottie) |
| **Theme Mode** | Static Dark / Weather Adaptive (accent colors shift: amber for sun, blue for rain, purple for storms) |
| **Weather Summary** | Standard template / AI-Generated (Gemini Nano, future) |
| **Card Visibility** | Toggle each of 19 card types on/off |
| **Card Ordering** | User-defined card order on the Today tab |
| **Temperature** | Fahrenheit / Celsius |
| **Wind Speed** | mph / km/h / m/s / knots |
| **Pressure** | inHg / hPa / mbar |
| **Precipitation** | inches / mm |
| **Time Format** | 12-hour / 24-hour |
| **Notifications** | Alert notifications, persistent weather notification, nowcasting alerts, driving alerts, health alerts (each toggleable) |
| **Alert Severity** | Extreme only / Severe+ / Moderate+ / All |
| **Data Toggles** | Snowfall, CAPE, sunshine duration, golden hour, Beaufort colors, outdoor score, yesterday comparison |
| **Health** | Migraine alerts with configurable pressure threshold |
| **Haptics** | Vibration feedback for weather alerts |
| **Weather Particles** | Rain, snow, and sun ray animations on/off |

### More

| Feature | Description |
|---------|-------------|
| **Compare Weather** | Side-by-side comparison of two saved locations (toolbar icon) |
| **Location Search** | Open-Meteo geocoding with debounced search and error feedback |
| **Multi-Location** | Room database with add/remove/auto-GPS saved locations |
| **Share as Text** | Formatted weather summary via system share sheet |
| **Share as Image** | Rendered dark-themed weather card as PNG |
| **Persistent Notification** | Always-on notification showing current conditions (toggleable) |
| **Proactive Caching** | Background worker pre-fetches weather for all saved locations |
| **Offline Mode** | Room-cached weather data survives network failures with "Cached" indicator |
| **Adaptive Layout** | Dynamic card system works on phone + tablet (both use `ReorderableCardColumn`) |
| **Accessibility** | TalkBack descriptions on all Canvas components with unit-aware formatting |
| **App Shortcuts** | Long-press launcher: Search, Radar, Settings, Compare |
| **Deep Links** | `zeuswatch://` URI scheme for locations, radar, settings, compare |
| **F-Droid Ready** | `freenet` build flavor with no proprietary dependencies |

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)                                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐  │
│  │MainScreen│ │RadarScreen│ │ Settings │ │Locations │ │CompareScreen │  │
│  │+ViewModel│ │+ViewModel │ │ + VM     │ │+ VM      │ │+ ViewModel   │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────┬───────┘  │
│       │            │            │            │               │           │
│  19 Card Types via ReorderableCardColumn                                 │
│  WeatherSummary | NowcastCard | RadarPreview | HourlyStrip | TempGraph  │
│  DailyForecast | UvIndexBar | WindCompass | AqiCard | PollenCard | ...   │
│                                                                          │
├──────────────────────────────────────────────────────────────────────────┤
│  Domain Layer (Repositories + Evaluators)                                │
│  ┌────────────┐ ┌──────────┐ ┌───────────┐ ┌────────────┐              │
│  │WeatherRepo │ │AlertRepo │ │AirQuality │ │LocationRepo│              │
│  │(fetch+cache)│ │(NWS)     │ │Repo(AQ+   │ │(geocode+   │              │
│  │            │ │          │ │pollen)    │ │Room)       │              │
│  └────────────┘ └──────────┘ └───────────┘ └────────────┘              │
│  ┌────────────┐ ┌──────────────────┐ ┌──────────────────────┐          │
│  │RadarRepo   │ │WeatherSummary    │ │DrivingCondition      │          │
│  │(RainViewer)│ │Engine (NLG)      │ │Evaluator + HealthEval│          │
│  └────────────┘ └──────────────────┘ └──────────────────────┘          │
│                                                                          │
├──────────────────────────────────────────────────────────────────────────┤
│  Data Layer                                                              │
│  ┌───────────────────────────────┐  ┌───────────────────────────────┐   │
│  │ Retrofit APIs                  │  │ Room Database (v2)             │   │
│  │  Open-Meteo Forecast           │  │  weather_cache                │   │
│  │  Open-Meteo Geocoding          │  │  saved_locations              │   │
│  │  Open-Meteo Air Quality        │  │                               │   │
│  │  Open-Meteo minutely_15        │  │ DataStore Preferences         │   │
│  │  Open-Meteo Historical         │  │  Units, display, cards,       │   │
│  │  RainViewer Radar Tiles        │  │  notifications, health,       │   │
│  │  NWS Alerts                    │  │  haptics, seen alert IDs      │   │
│  └───────────────────────────────┘  └───────────────────────────────┘   │
│                                                                          │
│  DI: Hilt (NetworkModule + DatabaseModule)                               │
│  Background: AlertCheckWorker + WidgetRefreshWorker (WorkManager)        │
│  Notifications: 4 alert channels + 1 persistent weather channel          │
└──────────────────────────────────────────────────────────────────────────┘
```

**Stack:** Kotlin 2.1.0, Jetpack Compose (BOM 2024.12.01), Hilt 2.53.1, Retrofit 2.11.0, Room 2.6.1, DataStore 1.1.1, MapLibre 11.5.2, Glance 1.1.1, WorkManager 2.10.0, Lottie 6.6.2, Coil 3.0.4

---

## APIs

All APIs are free with no keys required:

| API | Purpose | Rate Limit |
|-----|---------|------------|
| [Open-Meteo Forecast](https://open-meteo.com/) | Current, hourly (48h), daily (16d), minutely_15 nowcasting | 10,000/day |
| [Open-Meteo Historical](https://open-meteo.com/en/docs/historical-weather-api) | Yesterday's weather for comparison | 10,000/day |
| [Open-Meteo Geocoding](https://open-meteo.com/en/docs/geocoding-api) | Location search + reverse geocode | 10,000/day |
| [Open-Meteo Air Quality](https://open-meteo.com/en/docs/air-quality-api) | AQI, pollutants, pollen (6 species) | 10,000/day |
| [RainViewer](https://www.rainviewer.com/api/weather-maps-api.html) | Radar tile images (past 2h + 30min forecast) | Fair use |
| [Windy.com](https://www.windy.com/) | Embedded interactive radar (WebView option) | Fair use |
| [NWS Alerts](https://www.weather.gov/documentation/services-web-api) | US severe weather alerts (degrades gracefully outside US) | Fair use |

### Open-Meteo Parameters Used

**Current:** temperature, humidity, feels-like, weather code, wind (speed/direction/gusts), pressure, UV, visibility, dew point, cloud cover, precipitation, snowfall, snow depth

**Hourly:** All current params + precipitation probability, sunshine duration (48h)

**Daily:** Weather code, temp high/low, sunrise/sunset, UV max, wind speed max, wind gusts max, precipitation sum/probability/hours, snowfall sum, sunshine duration (16d)

**Minutely_15:** Precipitation (24 intervals = 6 hours of 15-min resolution)

---

## Build Flavors

| Flavor | Description |
|--------|-------------|
| `standard` | Includes Google Play Services for FusedLocationProvider |
| `freenet` | F-Droid compatible — no proprietary dependencies (uses Android LocationManager) |

```bash
# Standard (Google Play)
./gradlew assembleStandardRelease

# F-Droid
./gradlew assembleFreenetRelease
```

---

## Project Structure

```
app/src/main/java/com/sysadmindoc/nimbus/
├── MainActivity.kt              # Deep links, WindowSizeClass, theme wiring
├── NimbusApplication.kt         # Hilt, WorkManager, notification channels
├── data/
│   ├── api/                     # Retrofit services (6), Room DAOs, database
│   ├── model/                   # Domain models, Room entities, API DTOs
│   ├── repository/              # Repositories (6), UserPreferences, CardConfig
│   └── location/                # GPS location provider, reverse geocoder
├── di/                          # Hilt modules (Network, Database)
├── ui/
│   ├── component/               # 25+ reusable Compose components
│   │   ├── WeatherSummaryCard   # NLG summary
│   │   ├── NowcastCard          # 60-min precipitation chart
│   │   ├── RadarPreviewCard     # Live radar tile
│   │   ├── TemperatureGraph     # Interactive drag-to-inspect graph
│   │   ├── OutdoorScoreCard     # Activity score gauge
│   │   ├── DrivingAlertCard     # Driving hazard alerts
│   │   ├── HealthAlertCard      # Health trigger alerts
│   │   ├── GoldenHourCard       # Photography times
│   │   ├── SunMoonArc           # Sun position visualization
│   │   ├── AqiGauge             # Circular AQI gauge
│   │   ├── AnimatedWeatherIcon  # Material/Lottie switcher
│   │   ├── ReorderableCardColumn# Dynamic card ordering engine
│   │   └── ...                  # 13 more components
│   ├── screen/
│   │   ├── main/                # Today/Hourly/Daily tabs
│   │   ├── radar/               # Dual-provider radar + playback controls
│   │   ├── settings/            # 9-section settings (30+ preferences)
│   │   ├── locations/           # Search + saved locations
│   │   └── compare/             # Side-by-side weather comparison
│   ├── navigation/              # NavHost with typed routes
│   └── theme/                   # Colors, typography, weather-adaptive themes
├── util/                        # 8 utility classes
│   ├── WeatherSummaryEngine     # Template-based NLG
│   ├── WeatherFormatter         # 25+ unit-aware format methods
│   ├── DrivingConditionEvaluator# Ice, fog, hydroplaning, wind detection
│   ├── HealthAlertEvaluator     # Migraine, respiratory, arthritis triggers
│   ├── MeteoconMapper           # WMO code to Lottie filename mapping
│   ├── HapticHelper             # Severity-based vibration patterns
│   ├── AlertNotificationHelper  # 4-channel alert notifications
│   ├── WeatherNotificationHelper# Persistent current-weather notification
│   ├── AlertCheckWorker         # Background alert monitoring
│   ├── AccessibilityHelper      # TalkBack descriptions
│   └── ShareWeatherHelper       # Text + image sharing
└── widget/                      # 4 Glance home screen widgets
    ├── NimbusSmallWidget        # 3x1: temp + icon
    ├── NimbusMediumWidget       # 3x2: current + 3-day
    ├── NimbusLargeWidget        # 4x3: hourly + 5-day
    ├── NimbusForecastStripWidget# 4x1: hourly temp strip
    ├── WidgetRefreshWorker      # Periodic refresh + proactive caching
    └── WidgetRefreshAction      # Tap-to-refresh Glance callback
```

---

## Configuration

### Settings Sections

| Section | Options |
|---------|---------|
| **Display** | Radar provider, icon style (Material/Meteocons), theme mode, summary style |
| **Cards** | Toggle visibility of each of 19 card types |
| **Units** | Temperature, wind, pressure, precipitation, visibility, time format |
| **Notifications** | Alert notifications (severity threshold, multi-location), persistent weather, nowcasting, driving, health |
| **Data Display** | Snowfall, CAPE, sunshine, golden hour, Beaufort colors, outdoor score, yesterday comparison |
| **Health** | Migraine alerts with pressure threshold |
| **Accessibility** | Haptic feedback for alerts |
| **Visual Effects** | Weather particle animations |
| **About** | Version, data source, license |

### Card Types (19)

All cards can be independently shown/hidden and reordered:

Weather Summary, Radar Preview, Rain Next Hour, Hourly Forecast, Temperature Graph, Daily Forecast, UV Index, Wind Compass, Air Quality, Pollen, Outdoor Activity Score, Snowfall, Severe Weather Potential, Golden Hour, Sunshine Duration, Driving Conditions, Health Alerts, Moon Phase, Today's Details

---

## Animated Icons (Optional)

ZeusWatch supports [Meteocons](https://github.com/basmilius/weather-icons) animated Lottie weather icons (MIT license). To enable:

1. Download the "fill" style Lottie JSON files from the Meteocons repository
2. Place them in `app/src/main/assets/meteocons/`
3. Enable "Animated (Meteocons)" in Settings > Display > Icon Style

See `app/src/main/assets/meteocons/README.md` for the full file list. The app gracefully falls back to Material Icons when Lottie files are missing.

---

## Testing

```bash
# Unit tests — formatters, models, repositories, ViewModels
./gradlew testStandardDebugUnitTest

# Instrumented Compose UI tests — screen rendering, interactions
./gradlew connectedStandardDebugAndroidTest
```

| Suite | Framework | Files |
|-------|-----------|-------|
| Unit | JUnit 4 + MockK + Turbine + coroutines-test | 7 |
| UI | Compose UI Test + JUnit4 + Hilt Testing | 3 |

---

## Contributing

Issues and PRs welcome. Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit changes (`git commit -m 'Add my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

### Development Notes

- All raw API values are **metric** (Celsius, km/h, mm, hPa, meters). Unit conversion happens in `WeatherFormatter`.
- Unit settings flow via `CompositionLocalProvider(LocalUnitSettings provides ...)`. Standalone screens (Radar, Compare) read from their ViewModel's `prefs.settings` flow.
- Card rendering is driven by `CardType` enum + `ReorderableCardColumn`. Add new cards by extending `CardType` and adding a `when` branch.
- Weather-adaptive theming reads from `LocalWeatherThemeState` CompositionLocal.
- See `CLAUDE.md` for detailed architecture notes and `RESEARCH.md` for the feature roadmap.

---

## Roadmap

See [RESEARCH.md](RESEARCH.md) for the full feature research and phased implementation roadmap.

**Upcoming (v2.0):**
- Native MapLibre animated radar (replace Windy WebView as default)
- Wear OS companion (complications + tile)
- Lightning strike overlay (Blitzortung integration)
- Gemini Nano on-device weather summaries
- Per-widget location configuration
- Live weather wallpaper

---

## License

This project is licensed under the [GNU Lesser General Public License v3.0](LICENSE).

Weather data provided by [Open-Meteo.com](https://open-meteo.com/) under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
Radar tiles by [RainViewer](https://www.rainviewer.com/).
Interactive radar by [Windy.com](https://www.windy.com/).
Alert data by [National Weather Service](https://www.weather.gov/).
Animated icons by [Meteocons](https://github.com/basmilius/weather-icons) (MIT).
