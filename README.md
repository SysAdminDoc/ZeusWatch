# ZeusWatch

![Version](https://img.shields.io/badge/version-1.5.0-blue)
![License](https://img.shields.io/badge/license-LGPL--3.0-green)
![Platform](https://img.shields.io/badge/platform-Android%208.0+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12-4285F4?logo=jetpackcompose&logoColor=white)
![API](https://img.shields.io/badge/API-26+-brightgreen)
![Build](https://github.com/SysAdminDoc/zeuswatch/actions/workflows/build.yml/badge.svg)

> A free, open-source Android weather app with a premium dark UI, deeply customizable cards, multi-source forecasts, and smart alerts. No API keys required. Powered by Open-Meteo, RainViewer, Blitzortung, NWS, MeteoAlarm, JMA, and Environment Canada.

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
| **Current Conditions** | Large temp display, feels-like with wind chill/heat index explanation, condition, high/low, sky gradients |
| **Yesterday Comparison** | "5° warmer than yesterday" label in hero header with color-coded warm/cool indicator |
| **Weather Summary** | Natural language forecast via template engine or Gemini Nano on-device AI (with automatic template fallback) |
| **Hourly Forecast** | 48h or 72h scrollable strip with temp, animated icons, precip probability, feels-like when significantly different |
| **16-Day Forecast** | Expandable daily rows with temperature range bars, rain hours, sunshine, snowfall, wind gusts, UV max. "Warmest" day highlighted |
| **Temperature Graph** | Interactive Canvas graph with drag-to-inspect, precipitation bars, forecast average normals band, and feels-like overlay |
| **Rain Next Hour** | 60-minute precipitation nowcasting bar chart from Open-Meteo minutely_15 data |
| **Temperature Normals** | Shaded band on temperature graph showing forecast average range with dashed boundary lines |

### Data & Visualization

| Feature | Description |
|---------|-------------|
| **Today's Details** | 10+ cell grid: humidity, wind (with gusts), UV, pressure (with trend indicator), visibility, dew point (with comfort descriptor), sunrise/sunset, cloud cover, snowfall/snow depth |
| **Wind Compass** | Animated Canvas compass with direction needle, Beaufort scale coloring, gust display |
| **UV Index Bar** | Color gradient bar with level markers, descriptions, "UV peaks at 2 PM" annotation, and safe sun exposure time |
| **Air Quality** | PM2.5, PM10, O3, NO2, SO2, CO with EPA/European AQI scales, circular gauge, dominant pollutant highlighting, and 5-day daily AQI forecast bars |
| **Pollen Data** | Per-species animated bars: alder, birch, grass, mugwort, olive, ragweed (hourly fallback) |
| **Moon Phase** | Canvas moon illumination with Conway's algorithm, moonrise/moonset, day length |
| **Sun Arc** | Semicircular sun position visualization showing current position from sunrise to sunset |
| **Snowfall Card** | Current snowfall rate + snow depth + daily total, auto-hidden when no snow |
| **Sunshine Duration** | Today's sunshine hours with circular progress ring |
| **Severe Weather Potential** | CAPE-based thunderstorm indicator with instability levels |
| **Outdoor Activity Score** | Weighted 0-100 composite score from temp, wind, UV, humidity, precipitation, AQI with factor breakdown bars |
| **Golden Hour** | Morning and evening golden hour times for photographers |
| **Humidity & Comfort** | Humidity gauge with comfort level indicator, dew point, and zone markers |
| **Precipitation Forecast** | 24-hour precipitation probability bars with peak callout and accumulation total |
| **Pressure Trend** | 24-hour barometric pressure line graph with trend direction and delta |
| **Wind Forecast** | 24-hour wind speed line graph with gust overlay bars and peak callout |
| **Clothing Suggestions** | Rule-based outfit recommendations from feels-like temp, rain, snow, UV, wind |
| **Pet Safety** | Pavement temperature estimates, heat stress, cold exposure, storm anxiety alerts |

### Smart Alerts & Safety

| Feature | Description |
|---------|-------------|
| **Severe Weather Alerts** | Multi-source: NWS (US), MeteoAlarm (31 EU countries), JMA (Japan), Environment Canada — auto-detected by country |
| **Alert Source Preference** | Configurable: Auto-detect, NWS only, MeteoAlarm, JMA, Environment Canada, All sources |
| **4 Notification Channels** | Extreme (alarm sound, bypass DND), Severe (high), Moderate (default), Minor (low) |
| **Alert Deduplication** | Tracks seen alert IDs so the same warning is never re-notified |
| **Multi-Location Alerts** | Optionally monitors all saved locations, not just current GPS |
| **Driving Condition Alerts** | Black ice, fog, low visibility, hydroplaning, high wind, snow/ice — derived from forecast data |
| **Health Alerts** | Migraine triggers (pressure/temp swings, configurable threshold), respiratory (humidity extremes), arthritis (temp swing) |
| **Haptic Feedback** | Severity-appropriate vibration patterns when alerts display |
| **Expired Alert Filtering** | Skips alerts past their expiration timestamp |
| **Pressure Trend** | Rising/Falling/Steady indicator computed from hourly surface pressure data |

### Radar & Maps

| Feature | Description |
|---------|-------------|
| **Dual Radar Provider** | User-selectable: native MapLibre with RainViewer tiles, or Windy.com WebView embed |
| **Animated Radar Playback** | Play/pause, frame slider, past/forecast labels, timestamp overlay (native mode) |
| **Radar Preview Card** | Live RainViewer tile + CartoDB dark basemap on the Today tab |
| **Radar Tab** | Full-screen radar in the bottom nav with provider-aware rendering |
| **Map Layer Selector** | Overlay layers: Radar, Lightning, Satellite, Clouds |
| **Lightning Strike Overlay** | Real-time global lightning data via Blitzortung WebSocket with GeoJSON rendering |
| **Community Weather Reports** | Firebase Firestore-backed crowd-sourced condition reporting with rate limiting |

### Multi-Source Forecast System

| Source | Data Types | Region |
|--------|------------|--------|
| **Open-Meteo** | Forecast, AQI, Pollen, Minutely, Historical | Global |
| **NWS** | Alerts | United States |
| **MeteoAlarm** | Alerts | 31 EU countries |
| **JMA** | Alerts | Japan |
| **Environment Canada** | Alerts | Canada |
| **OpenWeatherMap** | Forecast (fallback) | Global (API key) |
| **Pirate Weather** | Forecast (fallback) | Global (API key) |
| **Bright Sky** | Forecast (fallback) | Germany |

The `WeatherSourceManager` supports primary + fallback source per data type with automatic failover. Sources are configurable in Settings with optional API key fields.

### Widgets (Jetpack Glance)

| Widget | Size | Content |
|--------|------|---------|
| **Current** | 3x1 | Icon + temp + location + high/low |
| **3-Day** | 3x2 | Current conditions + 3-day forecast columns + staleness indicator |
| **Forecast** | 4x3 | Current + 6hr hourly strip + 5-day rows + staleness indicator |
| **Hourly Strip** | 4x1 | Current temp + next 5 hourly temps with icons and precip% |

- Per-widget location configuration via config activity
- Tap-to-refresh (when data is null) and tap-to-open (when loaded)
- Proactive background caching for all saved locations
- Material You theming with `GlanceTheme`

### Customization

| Setting | Options |
|---------|---------|
| **Radar Provider** | Windy WebView / Native MapLibre |
| **Icon Style** | Material Icons / Meteocons Animated (Lottie) / Custom Icon Packs |
| **Theme Mode** | Static Dark / Weather Adaptive (accent colors shift: amber for sun, blue for rain, purple for storms) |
| **Weather Summary** | Standard template / AI-Generated (Gemini Nano on-device) |
| **Card Visibility** | Toggle each of the 25 card types on/off |
| **Card Ordering** | Reorderable card list in Settings with move up/down arrows |
| **Temperature** | Fahrenheit / Celsius |
| **Wind Speed** | mph / km/h / m/s / knots |
| **Pressure** | inHg / hPa / mbar |
| **Precipitation** | inches / mm |
| **Visibility** | miles / km |
| **Time Format** | 12-hour / 24-hour |
| **Hourly Range** | 48 hours / 72 hours |
| **Notifications** | Alert notifications, persistent weather notification, nowcasting alerts, driving alerts, health alerts (each toggleable) |
| **Alert Severity** | Extreme only / Severe+ / Moderate+ / All |
| **Alert Source** | Auto-detect / NWS / MeteoAlarm / JMA / Environment Canada / All |
| **Data Toggles** | Snowfall, CAPE, sunshine duration, golden hour, Beaufort colors, outdoor score, yesterday comparison |
| **Health** | Migraine alerts with configurable pressure threshold (3.0/5.0/7.0/10.0 hPa/3h) |
| **Haptics** | Vibration feedback for weather alerts |
| **Weather Particles** | Rain, snow, and sun ray animations on/off |
| **Cache TTL** | Configurable: 15 / 30 / 60 / 120 minutes |

### More

| Feature | Description |
|---------|-------------|
| **Compare Weather** | Side-by-side comparison with weather icons, visibility, cloud cover, and value highlighting |
| **Location Search** | Open-Meteo geocoding with debounced search and error feedback |
| **Multi-Location** | Room database with add/remove/auto-GPS saved locations |
| **Drag-to-Reorder Locations** | Long-press drag handles to reorder saved locations with batch persistence |
| **Location Temperature Preview** | Saved location list shows cached temperatures with weather condition icons |
| **Share as Text** | Formatted weather summary via system share sheet |
| **Share as Image** | Rendered dark-themed weather card as PNG |
| **Persistent Notification** | Always-on notification showing current conditions (toggleable) |
| **Proactive Caching** | Background worker pre-fetches weather for all saved locations |
| **Offline Mode** | Room-cached weather data survives network failures with "Cached" indicator |
| **Adaptive Layout** | Phone and tablet layouts via LazyColumn. Tablets get two-pane weather + radar (840dp+) |
| **Live Weather Wallpaper** | Rain, snow, thunderstorm, sun rays, cloud wisps, fog particle effects over existing wallpaper |
| **Accessibility** | TalkBack descriptions on all Canvas components, alert icons, and merged card semantics with liveRegion alerts |
| **App Shortcuts** | Long-press launcher: Search, Radar, Settings, Compare |
| **Deep Links** | `zeuswatch://` URI scheme for locations, radar, settings, compare |
| **F-Droid Ready** | `freenet` build flavor with no proprietary dependencies |
| **Custom Icon Packs** | Discover bundled + external APK icon packs via intent |
| **Alert Expiry Countdown** | Alert banners show "3h 15m left" / "Expired" |
| **Sunrise/Sunset Countdown** | Astronomy card shows "Sunset in 2h 15m" with auto-switch |
| **Smart Rain Timeline** | Hourly strip shows "Rain likely within 3h" or "Rain ending soon" |
| **Data Staleness Indicator** | "Updated" text turns amber when data is 1+ hours old |
| **Wind Direction Arrows** | Hourly forecast items show wind direction when > 10 km/h |
| **Dew Point Comfort Colors** | Color-coded comfort level (blue=dry, green=comfortable, orange=muggy, red=oppressive) |
| **Time-Aware Summary** | "Clear skies this morning" / "Overcast this afternoon" / "tonight" |
| **Collapsible Settings** | All sections collapsible with tap-to-toggle, Data Sources + Advanced start collapsed |

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
│  25 Card Types via LazyColumn items()                                    │
│  WeatherSummary | NowcastCard | RadarPreview | HourlyStrip | TempGraph  │
│  DailyForecast | UvIndexBar | WindCompass | AqiCard | PollenCard | ...   │
│  ClothingSuggestion | PetSafety | DrivingAlert | HealthAlert | ...      │
│                                                                          │
├──────────────────────────────────────────────────────────────────────────┤
│  Domain Layer (Repositories + Evaluators + Source Manager)               │
│  ┌────────────────┐ ┌──────────────────┐ ┌───────────────────────────┐  │
│  │WeatherRepo     │ │WeatherSource     │ │AlertRepo + AlertSource   │  │
│  │(fetch+cache+   │ │Manager (primary/ │ │Adapters (NWS, MeteoAlarm,│  │
│  │ direct+history)│ │fallback adapters)│ │JMA, ECCC)                │  │
│  └────────────────┘ └──────────────────┘ └───────────────────────────┘  │
│  ┌────────────┐ ┌───────────┐ ┌────────────┐ ┌────────────────┐        │
│  │AirQuality  │ │LocationRepo│ │RadarRepo   │ │Community       │        │
│  │Repo(AQ+   │ │(geocode+   │ │(RainViewer)│ │ReportRepo      │        │
│  │pollen)    │ │Room+sort)  │ │            │ │(Firestore)     │        │
│  └───────────┘ └────────────┘ └────────────┘ └────────────────┘        │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────────┐    │
│  │WeatherSummary    │ │ClothingSuggestion│ │DrivingCondition      │    │
│  │Engine (NLG/AI)   │ │Evaluator         │ │Evaluator + HealthEval│    │
│  └──────────────────┘ └──────────────────┘ │+ PetSafetyEvaluator │    │
│                                             └──────────────────────┘    │
│                                                                          │
├──────────────────────────────────────────────────────────────────────────┤
│  Data Layer                                                              │
│  ┌───────────────────────────────┐  ┌───────────────────────────────┐   │
│  │ Retrofit APIs                  │  │ Room Database (v2)             │   │
│  │  Open-Meteo Forecast           │  │  weather_cache                │   │
│  │  Open-Meteo Geocoding          │  │  saved_locations (sortOrder)  │   │
│  │  Open-Meteo Air Quality        │  │                               │   │
│  │  Open-Meteo minutely_15        │  │ DataStore Preferences         │   │
│  │  Open-Meteo Historical         │  │  Units, display, cards,       │   │
│  │  RainViewer Radar Tiles        │  │  notifications, health,       │   │
│  │  NWS / MeteoAlarm / JMA / ECCC │  │  haptics, seen alert IDs,    │   │
│  │  Blitzortung WebSocket         │  │  widget location prefs       │   │
│  │  Firebase Firestore             │  │                               │   │
│  └───────────────────────────────┘  └───────────────────────────────┘   │
│                                                                          │
│  DI: Hilt (NetworkModule + DatabaseModule)                               │
│  Background: AlertCheckWorker + WidgetRefreshWorker (WorkManager)        │
│  Notifications: 4 alert channels + 1 persistent weather channel          │
└──────────────────────────────────────────────────────────────────────────┘
```

**Stack:** Kotlin 2.1.0, Jetpack Compose (BOM 2024.12.01), Hilt 2.53.1, Retrofit 2.11.0, Room 2.6.1, DataStore 1.1.1, MapLibre 11.5.2, Glance 1.1.1, WorkManager 2.10.0, Lottie 6.6.2, Coil 3.0.4, Firebase Firestore 33.7.0

---

## APIs

All core APIs are free with no keys required:

| API | Purpose | Rate Limit |
|-----|---------|------------|
| [Open-Meteo Forecast](https://open-meteo.com/) | Current, hourly (48-72h), daily (16d), minutely_15 nowcasting | 10,000/day |
| [Open-Meteo Historical](https://open-meteo.com/en/docs/historical-weather-api) | Yesterday's weather for comparison | 10,000/day |
| [Open-Meteo Geocoding](https://open-meteo.com/en/docs/geocoding-api) | Location search + reverse geocode | 10,000/day |
| [Open-Meteo Air Quality](https://open-meteo.com/en/docs/air-quality-api) | AQI, pollutants, pollen (6 species), 5-day daily forecast | 10,000/day |
| [RainViewer](https://www.rainviewer.com/api/weather-maps-api.html) | Radar tile images (past 2h + 30min forecast) | Fair use |
| [Windy.com](https://www.windy.com/) | Embedded interactive radar (WebView option) | Fair use |
| [NWS Alerts](https://www.weather.gov/documentation/services-web-api) | US severe weather alerts | Fair use |
| [MeteoAlarm](https://www.meteoalarm.org/) | EU severe weather alerts (31 countries) | Fair use |
| [JMA](https://www.jma.go.jp/) | Japan severe weather alerts | Fair use |
| [Environment Canada](https://weather.gc.ca/) | Canadian severe weather alerts | Fair use |
| [Blitzortung](https://www.blitzortung.org/) | Real-time lightning strike data (WebSocket) | Fair use |
| [Firebase Firestore](https://firebase.google.com/) | Community weather reports | Free tier |

Optional fallback sources (require API keys configured in Settings):

| API | Purpose |
|-----|---------|
| [OpenWeatherMap](https://openweathermap.org/api) | Forecast fallback |
| [Pirate Weather](https://pirateweather.net/) | Forecast fallback |
| [Bright Sky](https://brightsky.dev/) | Germany forecast fallback |

### Open-Meteo Parameters Used

**Current:** temperature, humidity, feels-like, weather code, wind (speed/direction/gusts), pressure, UV, visibility, dew point, cloud cover, precipitation, snowfall, snow depth, CAPE

**Hourly:** All current params + precipitation probability, sunshine duration, surface pressure (48-72h configurable)

**Daily:** Weather code, temp high/low, sunrise/sunset, UV max, wind speed max/gusts max/direction dominant, precipitation sum/probability/hours, snowfall sum, sunshine duration (16d)

**Minutely_15:** Precipitation (24 intervals = 6 hours of 15-min resolution)

---

## Build Flavors

| Flavor | Description |
|--------|-------------|
| `standard` | Includes Google Play Services for FusedLocationProvider + Gemini Nano AI |
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
│   ├── api/                     # Retrofit services (8), Room DAOs, database
│   ├── model/                   # Domain models, Room entities, API DTOs
│   ├── repository/              # Repositories (7), UserPreferences, CardConfig
│   │   ├── WeatherRepository    # Direct fetch + cache + history + minutely
│   │   ├── WeatherSourceManager # Multi-source primary/fallback adapter system
│   │   ├── AlertRepository      # Country-auto-detecting multi-source alerts
│   │   ├── AirQualityRepository # AQI + pollen + 5-day daily forecast
│   │   ├── LocationRepository   # Geocoding + Room + reordering
│   │   ├── RadarRepository      # RainViewer tile URLs + frame list
│   │   └── CommunityReportRepo  # Firestore crowd-sourced reports
│   └── location/                # GPS location provider, reverse geocoder
├── di/                          # Hilt modules (Network, Database)
├── ui/
│   ├── component/               # 30+ reusable Compose components
│   │   ├── WeatherSummaryCard   # NLG / AI-generated summary
│   │   ├── NowcastCard          # 60-min precipitation chart
│   │   ├── RadarPreviewCard     # Live radar tile
│   │   ├── TemperatureGraph     # Interactive drag-to-inspect graph + normals band
│   │   ├── OutdoorScoreCard     # Activity score gauge
│   │   ├── DrivingAlertCard     # Driving hazard alerts
│   │   ├── HealthAlertCard      # Health trigger alerts
│   │   ├── ClothingSuggestionCard # Outfit recommendations
│   │   ├── PetSafetyCard        # Pet weather safety alerts
│   │   ├── GoldenHourCard       # Photography times
│   │   ├── SunMoonArc           # Sun position visualization
│   │   ├── AqiGauge             # Circular AQI gauge + daily forecast
│   │   ├── AnimatedWeatherIcon  # Material/Lottie/Custom switcher
│   │   ├── HumidityCard         # Humidity gauge with comfort
│   │   ├── RadarLayerSelector   # Map overlay layer chips
│   │   ├── AlertBanner          # Pulsing severity-colored alert bar
│   │   └── ...                  # 14 more components
│   ├── screen/
│   │   ├── main/                # Today/Hourly/Daily tabs + tablet two-pane
│   │   ├── radar/               # Dual-provider radar + playback + layers
│   │   ├── settings/            # 9-section settings (35+ preferences)
│   │   ├── locations/           # Search + drag-to-reorder saved locations
│   │   └── compare/             # Side-by-side weather comparison
│   ├── navigation/              # NavHost with typed routes
│   └── theme/                   # Colors, typography, weather-adaptive themes
├── util/                        # 12 utility classes
│   ├── WeatherSummaryEngine     # Template-based NLG
│   ├── GeminiNanoSummaryEngine  # On-device AI summaries (standard flavor)
│   ├── WeatherFormatter         # 30+ unit-aware format methods
│   ├── DrivingConditionEvaluator# Ice, fog, hydroplaning, wind detection
│   ├── HealthAlertEvaluator     # Migraine, respiratory, arthritis triggers
│   ├── ClothingSuggestionEvaluator # Outfit recommendation engine
│   ├── PetSafetyEvaluator      # Pet weather hazard detection
│   ├── MeteoconMapper           # WMO code to Lottie filename mapping
│   ├── HapticHelper             # Severity-based vibration patterns
│   ├── AlertNotificationHelper  # 4-channel alert notifications
│   ├── WeatherNotificationHelper# Persistent current-weather notification
│   ├── AlertCheckWorker         # Background alert monitoring
│   ├── AccessibilityHelper      # TalkBack descriptions
│   ├── ShareWeatherHelper       # Text + image sharing
│   ├── IconPackManager          # Bundled + external icon pack discovery
│   └── BlitzortungService       # Real-time lightning WebSocket
├── wallpaper/
│   └── WeatherWallpaperService  # Live weather wallpaper with particle effects
└── widget/                      # 4 Glance home screen widgets
    ├── NimbusSmallWidget        # 3x1: temp + icon
    ├── NimbusMediumWidget       # 3x2: current + 3-day
    ├── NimbusLargeWidget        # 4x3: hourly + 5-day
    ├── NimbusForecastStripWidget# 4x1: hourly temp strip
    ├── WidgetRefreshWorker      # Periodic refresh + proactive caching
    ├── WidgetRefreshAction      # Tap-to-refresh Glance callback
    ├── WidgetConfigActivity     # Per-widget location picker
    └── WidgetLocationPrefs      # Widget-to-location DataStore mapping
```

---

## Configuration

### Settings Sections

| Section | Options |
|---------|---------|
| **Display** | Radar provider, icon style (Material/Meteocons/Custom), theme mode, summary style (template/AI) |
| **Cards** | Toggle + reorder each of 25 card types with move up/down arrows |
| **Units** | Temperature, wind, pressure, precipitation, visibility, time format |
| **Notifications** | Alert notifications (severity threshold, multi-location, source preference), persistent weather, nowcasting, driving, health |
| **Data Display** | Hourly range (48/72h), snowfall, CAPE, sunshine, golden hour, Beaufort colors, outdoor score, yesterday comparison |
| **Health** | Migraine alerts with pressure threshold (3.0/5.0/7.0/10.0 hPa/3h) |
| **Accessibility** | Haptic feedback for alerts |
| **Visual Effects** | Weather particle animations |
| **Advanced** | Cache TTL (15/30/60/120 min) |
| **About** | Version, data source, license |

### Card Types (25)

All cards can be independently shown/hidden and reordered:

Weather Summary, Radar Preview, Rain Next Hour, Hourly Forecast, Temperature Graph, Daily Forecast, UV Index, Wind Compass, Air Quality, Pollen, Outdoor Activity Score, Snowfall, Severe Weather Potential, Golden Hour, Sunshine Duration, Driving Conditions, Health Alerts, Moon Phase, Humidity & Comfort, Precipitation Forecast, Pressure Trend, Wind Forecast, Today's Details, Clothing Suggestions, Pet Safety

---

## Animated Icons (Optional)

ZeusWatch supports [Meteocons](https://github.com/basmilius/weather-icons) animated Lottie weather icons (MIT license). To enable:

1. Download the "fill" style Lottie JSON files from the Meteocons repository
2. Place them in `app/src/main/assets/meteocons/`
3. Enable "Animated (Meteocons)" in Settings > Display > Icon Style

See `app/src/main/assets/meteocons/README.md` for the full file list. The app gracefully falls back to Material Icons when Lottie files are missing.

### Custom Icon Packs

Third-party icon packs are discoverable via:
- Bundled packs in `app/src/main/assets/iconpacks/` with `manifest.json`
- External APKs exposing the `com.sysadmindoc.nimbus.ICON_PACK` intent

---

## Testing

```bash
# Unit tests — formatters, models, repositories, ViewModels
./gradlew testStandardDebugUnitTest

# Instrumented Compose UI tests — screen rendering, interactions
./gradlew connectedStandardDebugAndroidTest
```

| Suite | Framework | Coverage |
|-------|-----------|----------|
| Unit | JUnit 4 + MockK + Turbine + coroutines-test | WeatherFormatter (20), WeatherCode (12), Accessibility (12), AirQuality (14), Alerts (9), MainViewModel (10), LocationsViewModel (7) |
| UI | Compose UI Test + JUnit4 + Hilt Testing | MainScreen (6), SettingsScreen (10), LocationsScreen (8) |

**180+ tests** across 14 test suites.

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
- Card rendering is driven by `CardType` enum + `LazyColumn items()` in MainScreen. Add new cards by extending `CardType` and adding a `when` branch in `RenderCard`.
- Weather-adaptive theming reads from `LocalWeatherThemeState` CompositionLocal.
- Multi-source forecasts use `WeatherSourceManager` with adapter pattern — add new sources by implementing `WeatherSourceAdapter`.
- Alert sources use `AlertSourceAdapter` interface — auto-detected by country via Geocoder.
- See `CLAUDE.md` for detailed architecture notes and `RESEARCH.md` for the feature roadmap.

---

## Roadmap

See [RESEARCH.md](RESEARCH.md) for the full feature research and phased implementation roadmap.

**Implemented through v1.5.0:**
- 25 dynamic card types with user-configurable order and visibility
- 4 new data cards: Humidity, Precipitation Chart, Pressure Trend, Wind Forecast
- Feels-like overlay on temperature graph
- Temperature range bars in daily forecast
- Alert expiry countdown
- Collapsible settings sections
- Pull-to-refresh on all tabs
- Compare screen with weather icons and value highlighting
- Outdoor score with factor breakdown bars
- Location screen with weather condition icons
- Native MapLibre animated radar with layer selector
- Real-time lightning strike overlay (Blitzortung WebSocket)
- Gemini Nano on-device weather summaries (standard flavor)
- Live weather wallpaper with particle effects
- Multi-source forecast fallback (6 providers)
- International alert sources (NWS, MeteoAlarm, JMA, Environment Canada)
- Community weather reports (Firebase Firestore)
- Tablet two-pane layout

---

## License

This project is licensed under the [GNU Lesser General Public License v3.0](LICENSE).

Weather data provided by [Open-Meteo.com](https://open-meteo.com/) under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
Radar tiles by [RainViewer](https://www.rainviewer.com/).
Interactive radar by [Windy.com](https://www.windy.com/).
Lightning data by [Blitzortung.org](https://www.blitzortung.org/).
Alert data by [National Weather Service](https://www.weather.gov/), [MeteoAlarm](https://www.meteoalarm.org/), [JMA](https://www.jma.go.jp/), [Environment Canada](https://weather.gc.ca/).
Animated icons by [Meteocons](https://github.com/basmilius/weather-icons) (MIT).
