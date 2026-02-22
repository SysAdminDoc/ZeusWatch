# ZeusWatch

![Version](https://img.shields.io/badge/version-1.2.0-blue)
![License](https://img.shields.io/badge/license-LGPL--3.0-green)
![Platform](https://img.shields.io/badge/platform-Android%208.0+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12-4285F4?logo=jetpackcompose&logoColor=white)
![API](https://img.shields.io/badge/API-26+-brightgreen)
![Build](https://github.com/SysAdminDoc/zeuswatch/actions/workflows/build.yml/badge.svg)

> A free, open-source Android weather app with a premium dark UI. No API keys required. Powered by Open-Meteo, Windy.com, and NWS.

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

## Features

| Feature | Description | Version |
|---------|-------------|---------|
| Current Conditions | Large temp display, feels-like, condition, high/low, condition-based sky gradients | v0.1.0 |
| Hourly Forecast | 48hr scrollable strip with temp, icon, precipitation probability | v0.1.0 |
| 16-Day Forecast | Expandable daily rows with day/night detail breakdown | v0.1.0 |
| Today's Details | 8-cell grid: humidity, wind, UV, pressure, visibility, dew point, sunrise/sunset, cloud cover | v0.1.0 |
| Dark Theme | Deep navy gradient palette with glassmorphism cards, never light | v0.1.0 |
| Pull to Refresh | Swipe-to-refresh all data sources | v0.1.0 |
| Weather Particles | Canvas rain, snow, and sun ray animations on the hero header | v0.2.0 |
| Temperature Graph | 24hr Canvas line graph with gradient fill and touch tracking | v0.2.0 |
| Wind Compass | Animated Canvas compass rose with direction needle | v0.2.0 |
| UV Index Bar | Color gradient bar with level markers and descriptions | v0.2.0 |
| Shimmer Loading | Skeleton loading animation while fetching data | v0.2.0 |
| Unit Preferences | DataStore persistence: F/C, mph/kmh/ms/kn, inHg/hPa/mbar, 12/24hr | v0.2.0 |
| Interactive Radar | Windy.com embedded radar with dark mode and location tracking | v0.3.0 |
| Radar Timeline | Windy.com interactive radar with zoom, pan, and layer controls | v0.3.0 |
| Radar Preview | Tappable mini-card on main screen linking to full radar | v0.3.0 |
| Severe Weather Alerts | NWS tornado/storm/flood/fire warnings with severity color coding | v0.4.0 |
| Alert Notifications | Background WorkManager checks every 30min, Severe+ only | v0.4.0 |
| Alert Detail Sheet | Modal bottom sheet with full alert text and instructions | v0.4.0 |
| Small Widget (3x1) | Icon + temp + location + high/low | v0.5.0 |
| Medium Widget (3x2) | Current conditions + 3-day forecast columns | v0.5.0 |
| Large Widget (4x3) | Current + 6hr hourly strip + 5-day rows | v0.5.0 |
| Air Quality | PM2.5, PM10, O3, NO2, SO2, CO with EPA/European AQI scales | v0.6.0 |
| AQI Gauge | Arc gauge visualization with 24hr trend chart | v0.6.0 |
| Pollen Data | Per-species animated bars: alder, birch, grass, mugwort, olive, ragweed | v0.6.0 |
| Moon Phase | Canvas moon illumination drawing with Conway's algorithm | v0.6.0 |
| Astronomy | Moonrise/moonset, illumination percentage, day length | v0.6.0 |
| Multi-Location | Room database with add/remove saved locations | v0.7.0 |
| Location Search | Open-Meteo geocoding autocomplete with debounced search | v0.7.0 |
| GPS Auto-Location | "My Location" entry auto-created on first load | v0.7.0 |
| Share as Text | Formatted weather summary via system share sheet | v1.0.0 |
| Share as Image | Rendered dark-themed weather card as PNG via FileProvider | v1.0.0 |
| App Shortcuts | Long-press launcher: Search Location, Radar Map, Settings | v1.0.0 |
| Deep Links | `zeuswatch://` URI scheme for shortcuts and external intents | v1.0.0 |
| Adaptive Layout | WindowSizeClass-driven 2-column layout for landscape/tablet | v1.0.0 |
| TalkBack Support | Content descriptions on all Canvas composables | v1.0.0 |
| Offline Cache | Room-cached weather data survives network failures | v1.0.0 |
| F-Droid Ready | `freenet` build flavor with no proprietary dependencies | v1.0.0 |

## Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)                                        │
│  ┌─────────────┐ ┌─────────────┐ ┌──────────┐ ┌───────────────┐  │
│  │ MainScreen   │ │ RadarScreen  │ │ Settings │ │ LocationsScreen│ │
│  │ + ViewModel  │ │ + ViewModel  │ │ + VM     │ │ + ViewModel   │  │
│  └──────┬───────┘ └──────┬──────┘ └────┬─────┘ └───────┬───────┘  │
│         │                │             │               │           │
├─────────┴────────────────┴─────────────┴───────────────┴───────────┤
│  Domain Layer (Repositories)                                       │
│  ┌──────────────┐ ┌───────────┐ ┌──────────────┐ ┌─────────────┐ │
│  │ WeatherRepo   │ │ AlertRepo  │ │ AirQualityRepo│ │ LocationRepo│ │
│  │ (fetch+cache) │ │ (NWS)      │ │ (AQ+pollen)   │ │ (geocode)   │ │
│  └──────┬────────┘ └─────┬─────┘ └──────┬────────┘ └──────┬──────┘ │
│         │                │              │                 │         │
├─────────┴────────────────┴──────────────┴─────────────────┴────────┤
│  Data Layer                                                        │
│  ┌─────────────────────────────┐  ┌─────────────────────────────┐  │
│  │ Retrofit APIs               │  │ Room Database (v2)           │  │
│  │  Open-Meteo Forecast        │  │  weather_cache              │  │
│  │  Open-Meteo Geocoding       │  │  saved_locations            │  │
│  │  Open-Meteo Air Quality     │  │                             │  │
│  │  Windy.com Radar            │  │ DataStore Preferences       │  │
│  │  NWS Alerts                 │  │  Units, particles, etc.     │  │
│  └─────────────────────────────┘  └─────────────────────────────┘  │
│                                                                    │
│  DI: Hilt (NetworkModule + DatabaseModule)                         │
└────────────────────────────────────────────────────────────────────┘
```

**Stack:** Kotlin 2.1.0, Jetpack Compose (BOM 2024.12.01), Hilt 2.53.1, Retrofit 2.11.0, Room 2.6.1, DataStore 1.1.1, MapLibre 11.5.2, Glance 1.1.1, WorkManager 2.10.0

**Codebase:** 70 Kotlin source + 10 test files, 15 XML resources, ~8,600 lines + ~1,800 test lines

## APIs

All APIs are free with no keys required:

| API | Purpose | Rate Limit |
|-----|---------|------------|
| [Open-Meteo Forecast](https://open-meteo.com/) | Current, hourly, daily weather | 10,000/day |
| [Open-Meteo Geocoding](https://open-meteo.com/en/docs/geocoding-api) | Location search + reverse geocode | 10,000/day |
| [Open-Meteo Air Quality](https://open-meteo.com/en/docs/air-quality-api) | AQI, pollutants, pollen | 10,000/day |
| [Windy.com](https://www.windy.com/) | Embedded interactive radar | Fair use |
| [NWS Alerts](https://www.weather.gov/documentation/services-web-api) | US severe weather alerts (degrades gracefully outside US) | Fair use |

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

## Project Structure

```
app/src/main/java/com/sysadmindoc/nimbus/
├── MainActivity.kt              # Deep link routing, WindowSizeClass
├── NimbusApplication.kt         # Hilt, WorkManager, notification channels
├── data/
│   ├── api/                     # Retrofit services, Room DAOs, database
│   ├── model/                   # Domain models, Room entities, API DTOs
│   ├── repository/              # Business logic, data orchestration
│   └── location/                # GPS location provider
├── di/                          # Hilt modules (Network, Database)
├── ui/
│   ├── component/               # 18 reusable Compose components
│   ├── screen/                  # 4 screens with ViewModels
│   ├── navigation/              # NavHost with typed routes
│   └── theme/                   # Colors, typography, Material3 theme
├── util/                        # Formatters, notifications, workers, sharing
└── widget/                      # 3 Glance home screen widgets
```

## Configuration

Unit preferences are persisted via DataStore and accessible from Settings:

| Setting | Options | Default |
|---------|---------|---------|
| Temperature | Fahrenheit, Celsius | Fahrenheit |
| Wind Speed | mph, km/h, m/s, knots | mph |
| Pressure | inHg, hPa, mbar | inHg |
| Precipitation | inches, mm | inches |
| Time Format | 12-hour, 24-hour | 12-hour |
| Weather Particles | On/Off | On |

## Testing

112 tests total across two suites:

```bash
# Unit tests (88) - formatters, models, repositories, ViewModels
./gradlew testStandardDebugUnitTest

# Instrumented Compose UI tests (24) - screen rendering, interactions
./gradlew connectedStandardDebugAndroidTest
```

| Suite | Framework | Tests |
|-------|-----------|-------|
| Unit | JUnit 4 + MockK + Turbine + coroutines-test | 88 |
| UI | Compose UI Test + JUnit4 | 24 |

## Contributing

Issues and PRs welcome. Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit changes (`git commit -m 'Add my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

## License

This project is licensed under the [GNU Lesser General Public License v3.0](LICENSE).

Weather data provided by [Open-Meteo.com](https://open-meteo.com/) under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
Radar powered by [Windy.com](https://www.windy.com/).
Alert data by [National Weather Service](https://www.weather.gov/).
