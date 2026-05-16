# ZeusWatch Feature Research & Roadmap

> Comprehensive audit of every ZeusWatch feature against open-source weather apps, libraries, and modern UX patterns. Each section covers: current state, what FOSS projects do better, specific repos/APIs to adopt, and a prioritized roadmap.

---

## Table of Contents

1. [Feature Breakdown & Comparison](#1-feature-breakdown--comparison)
2. [New Feature Opportunities](#2-new-feature-opportunities)
3. [Key Open-Source References](#3-key-open-source-references)
4. [Implementation Roadmap](#4-implementation-roadmap)

---

## 1. Feature Breakdown & Comparison

### 1.1 Weather Data & Forecasting

**Current:** Open-Meteo forecast API. 48-hour hourly, 16-day daily. Single source.

**What Breezy Weather does better:**
- 50+ data sources with user-configurable **secondary sources per data type** (e.g., Open-Meteo for forecast + Meteo-France for minutely precipitation + NWS for alerts)
- Temperature normals overlay showing historical max/min for context ("is today unusually warm?")

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| Add Open-Meteo minutely_15 endpoint for 15-min precipitation nowcasting | [Open-Meteo docs](https://open-meteo.com/en/docs) | Low |
| Historical normals via Open-Meteo Historical API (ERA5, 1940-present) | [Historical API](https://open-meteo.com/en/docs/historical-weather-api) | Medium |
| "5 degrees warmer than yesterday" comparison (cache yesterday's data in Room) | Custom logic | Low |
| "Warmest day this week" label on daily forecast | Custom logic | Low |
| Use Open-Meteo Kotlin SDK for FlatBuffer efficiency | [open-meteo-api-kotlin](https://github.com/open-meteo/open-meteo-api-kotlin) | Medium |
| Unused API fields: snowfall, snow depth, CAPE, sunshine duration, freezing level, soil temp/moisture | [Open-Meteo docs](https://open-meteo.com/en/docs) | Low-Medium |

---

### 1.2 Radar & Precipitation Maps

**Current:** Windy.com WebView embed for full radar. RainViewer static tile for preview card. MapLibre is a dependency but unused for radar.

**What open-source projects do better:**
- Native MapLibre + RainViewer tile overlay (animated, no WebView overhead) -- reference: [rainviewer-api-example](https://github.com/rainviewer/rainviewer-api-example)
- Overmorrow: 15-minute rain trend visualization across 6-hour windows, predicted storm paths
- Meteocool: radar + lightning overlay + push notifications for incoming rain

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| Replace Windy WebView with native MapLibre + RainViewer raster layers | [RainViewer API](https://www.rainviewer.com/api/weather-maps-api.html), [MapLibre Android](https://maplibre.org/) | High |
| Animated radar playback (cycle through past[] frames as raster source swaps) | [rainviewer-api-example](https://github.com/rainviewer/rainviewer-api-example) | Medium |
| Add temperature and wind map overlay layers | [MapTiler Weather SDK](https://docs.maptiler.com/sdk-js/examples/weather-radar/) | Medium |
| "Rain starting in X minutes" nowcasting card (bar chart, 60-min timeline) | Breezy Weather, Open-Meteo minutely_15 | Medium |
| Lightning strike overlay via Blitzortung | [bo-android](https://github.com/wuan/bo-android) | High |

---

### 1.3 Severe Weather Alerts & Notifications

**Current:** NWS alerts (US only), 4 notification channels, dedup, multi-location, severity filtering, expired alert filtering.

**What could be better:**
- International alert coverage (WMO, national services)
- Persistent current-weather notification in notification shade (QuickWeather pattern)
- Rain-approaching push notifications (Meteocool pattern)

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| Persistent current-weather notification (toggleable) | [QuickWeather](https://github.com/TylerWilliamson/QuickWeather) | Low-Medium |
| Rain-approaching push notifications using nowcasting data | [Meteocool](https://github.com/meteocool/android) | Medium |
| International alert sources beyond NWS | Breezy Weather's multi-source pattern | High |
| CAPE-based severe thunderstorm potential indicator | Open-Meteo `cape` field | Low |

---

### 1.4 Air Quality & Pollen

**Current:** Open-Meteo AQ API, US/EU AQI, 6 pollen types, hourly AQI chart, pollen bars with levels. Hourly fallback for pollen data.

**What could be better:**
- AQI gauge visualization (3/4 circle) like Breezy Weather
- Dominant allergen highlighting
- Mold data
- 5-day AQI forecast (Overmorrow shows this)

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| AQI circular gauge component (Canvas arc, color gradient) | Breezy Weather | Low-Medium |
| Highlight worst pollutant contributing to AQI | Custom logic | Low |
| 5-day AQI forecast using Open-Meteo hourly aggregation | Open-Meteo AQ API `forecast_days=5` | Low |
| Health advice per AQI level (sensitive groups, outdoor activity) | WHO/EPA guidelines | Low |

---

### 1.5 Widgets (Glance)

**Current:** 3 sizes (small 2x1, medium 3x2, large 4x3). Staleness indicator. Tap-to-open.

**What Breezy Weather does better:**
- 16+ widget variants with configurable content
- Per-widget location selection
- Material You dynamic colors
- Nowcasting precipitation ring widget

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| Tap-to-refresh action (one-shot WorkManager + GlanceAppWidget.update) | [Glance docs](https://developer.android.com/develop/ui/compose/glance) | Low |
| Material You dynamic colors for widget backgrounds | [Glance theming](https://developer.android.com/develop/ui/compose/glance/theme) | Low |
| Compact 4x1 forecast strip widget (hourly temps) | Breezy Weather | Medium |
| Per-widget location configuration | Breezy Weather | Medium |
| Precipitation chance indicator ring/bar on small widget | Breezy Weather | Medium |

---

### 1.6 Weather Animations & Icons

**Current:** Material Icons mapped to WMO codes. WeatherParticles Canvas (rain, snow, sun rays). Lottie dependency exists but unused.

**What could be better:**
- Animated Lottie weather icons (Meteocons) for a polished look
- Day/night icon variants
- Weather-adaptive theme colors

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| Replace Material Icons with Meteocons Lottie animations (MIT license) | [weather-icons](https://github.com/basmilius/weather-icons) | Medium |
| Weather-adaptive color theming (rain=cool blue, sunny=warm amber) | Overmorrow | Low-Medium |
| Custom weather icon pack support | Breezy Weather | High |

---

### 1.7 Temperature Graph

**Current:** 24-hour cubic bezier curve with gradient fill. High/low dot markers. 6-hour time labels. Division-by-zero fix for flat temps.

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| Precipitation overlay bars behind the temperature line | Forecastie | Low-Medium |
| Historical normal range band (shaded area showing typical temp range) | Breezy Weather | Medium |
| Interactive: drag to see exact temp at any point | Breezy Weather nowcasting UX | Medium |
| Use ComposeCharts library for richer charting | [ComposeCharts](https://github.com/ehsannarmani/ComposeCharts) | Medium |

---

### 1.8 Hourly & Daily Forecasts

**Current:** Hourly LazyRow strip (time, temp, icon, precip%, precip amount). Daily expandable list with details.

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| 72-hour hourly view option (Overmorrow shows 72h) | Increase `forecast_hours=72` | Low |
| Precipitation hours per day ("3 hours of rain expected") | Open-Meteo `precipitation_hours` field | Low |
| Sunshine duration per day | Open-Meteo `sunshine_duration` field | Low |
| Wind gust max per day in daily details | Open-Meteo `wind_gusts_10m_max` | Low |
| Snowfall amount in daily details | Open-Meteo `snowfall_sum` | Low |

---

### 1.9 Moon Phase & Astronomy

**Current:** Moon phase calculation from lunar cycle. Illumination %. Approximate moonrise/moonset. Day length. Canvas moon visualization.

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| Sun/moon arc visualization (track across sky from rise to set) | Breezy Weather | Medium |
| Golden hour / blue hour times for photographers | Sunrise/sunset + offset calculation | Low |
| Current sun/moon altitude indicator (above/below horizon) | Solar position math | Low-Medium |

---

### 1.10 Wind Compass

**Current:** Canvas compass with cardinal labels, direction arrow, speed + gusts. Unit-aware.

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| Beaufort scale color coding on the compass ring | Breezy Weather | Low |
| Animated arrow transition when wind direction changes | Compose animation | Low |

---

### 1.11 UV Index

**Current:** Bar visualization with severity levels and description.

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| "Safe sun exposure time" estimate based on skin type | WHO UV guidelines | Low |
| Hourly UV forecast strip (when will UV peak today?) | Open-Meteo hourly `uv_index` | Low |

---

### 1.12 Location Management

**Current:** Search via Open-Meteo Geocoding. Saved locations in Room. "My Location" GPS. Swipeable location bar.

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| Reorderable saved locations (drag to sort) | Breezy Weather, Compose `LazyColumn` reorder | Medium |
| Weather comparison: side-by-side two locations | Custom UI | Medium |
| Show current temp next to each saved location in the list | Prefetch weather for saved locations | Medium |

---

### 1.13 Sharing

**Current:** Share as text (ShareWeatherHelper) and share as image (WeatherShareHelper). Two separate helpers.

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| Consolidate into single ShareHelper with format parameter | Refactor | Low |
| Weather card image with branded template (condition bg + data overlay) | Canvas rendering | Medium |
| Clean up bitmap memory after sharing (recycle + cache eviction) | Bug fix | Low |

---

### 1.14 Caching & Offline

**Current:** Room DB cache with 30-min TTL. Cached data shown with "Cached" indicator.

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| User-configurable cache TTL | UserPreferences | Low |
| Offline-first architecture: always show cache, refresh in background | Repository pattern refinement | Medium |
| Cache weather for all saved locations proactively | Background WorkManager job | Medium |

---

### 1.15 Accessibility

**Current:** AccessibilityHelper with TalkBack descriptions for all Canvas components. Unit-aware descriptions.

**Improvements:**
| Improvement | Source | Effort |
|---|---|---|
| Merge related semantics with `mergeDescendants = true` for cleaner TalkBack | [Compose accessibility](https://developer.android.com/codelabs/jetpack-compose-accessibility) | Low |
| `liveRegion` announcements for weather alert banners | Compose semantics | Low |
| High-contrast weather icon variants | Design work | Medium |
| Haptic feedback patterns per alert severity | VibrationEffect API | Low |

---

## 2. New Feature Opportunities

### 2.1 Natural Language Weather Summaries

**"Partly cloudy today with afternoon showers likely. Highs near 78."**

| Approach | Pros | Cons | Effort |
|---|---|---|---|
| Kotlin template engine (rule-based) | Works everywhere, zero latency, no API cost | Less natural phrasing | Low |
| SimpleNLG library | Grammatically correct, Java-compatible | Library overhead, still rule-based | Low-Medium |
| Gemini Nano on-device (ML Kit GenAI) | Rich, natural summaries | Pixel-only, alpha API | Medium |
| Cloud LLM API (Gemini/OpenAI) | Works on all devices | API cost, latency, privacy | Medium |

**Recommendation:** Template engine as default + optional Gemini Nano on supported devices.

- [SimpleNLG](https://github.com/simplenlg/simplenlg) - Java NLG library
- [ML Kit GenAI Prompt API](https://developers.google.com/ml-kit/genai/prompt/android)

### 2.2 Health & Lifestyle Features

| Feature | Data Source | Effort |
|---|---|---|
| Migraine/arthritis trigger alerts (pressure rate-of-change >0.2 inHg/6h) | Open-Meteo hourly `surface_pressure` | Low |
| Outdoor activity score (running, cycling, hiking suitability) | Temp + humidity + wind + UV + precip weighted score | Low |
| Driving condition alerts (black ice, fog, hydroplaning risk) | Temp near freezing + precip, dewpoint spread <3F | Low-Medium |
| Pet weather safety (pavement temp estimate, heat index thresholds) | Air temp + sun exposure rules | Low |
| Clothing suggestions (bracket-based temp/wind/rain mapping) | Custom rule engine | Low |

### 2.3 Wear OS Companion

| Component | Technology | Effort |
|---|---|---|
| Weather complications (SHORT_TEXT temp, SMALL_IMAGE icon) | ComplicationDataSource | Medium |
| Weather tile (current + 3-hour forecast) | ProtoLayout / Glance for Wear OS | Medium |
| Watch face format integration (WEATHER.* expressions) | Watch Face Format XML | Medium |

- Reference: [weather-you](https://github.com/rodrigmatrix/weather-you) - Compose on phone/tablet/TV/Wear OS
- [Wear OS samples](https://github.com/android/wear-os-samples)

### 2.4 Live Weather Wallpaper

Render weather-appropriate particle effects over the user's existing wallpaper.

- Android `WallpaperService` + Canvas rendering
- Reference: Breezy Weather ships a live wallpaper
- Android 16 "Live Effects" does this natively on Pixel
- **Effort:** High

### 2.5 Community Weather Reports

Let users submit condition reports (sunny/rain/snow/fog + optional photo) displayed as map pins.

- Requires backend (Firebase/Supabase)
- Reference: AccuWeather AccUcast, mPING
- **Effort:** High

---

## 3. Key Open-Source References

| Repository | What to learn from it |
|---|---|
| [breezy-weather](https://github.com/breezy-weather/breezy-weather) | Gold standard FOSS weather: Kotlin, 50+ sources, 16 widgets, nowcasting, reorderable cards, sun/moon arcs, temp normals |
| [weather-you](https://github.com/rodrigmatrix/weather-you) | Material You + Compose on phone/tablet/TV/Wear OS, Clean Architecture + MVI |
| [weather-icons (Meteocons)](https://github.com/basmilius/weather-icons) | MIT-licensed animated Lottie + SVG weather icons, multiple styles |
| [rainviewer-api-example](https://github.com/rainviewer/rainviewer-api-example) | Reference for MapLibre + RainViewer animated radar overlay |
| [meteocool/android](https://github.com/meteocool/android) | Radar + lightning + rain-approaching push notifications |
| [bo-android (Blitzortung)](https://github.com/wuan/bo-android) | Real-time lightning strike tracking with proximity alerts |
| [ComposeCharts](https://github.com/ehsannarmani/ComposeCharts) | Animated Compose charts for precipitation/pressure/temperature |
| [WeatherGlanceWidget](https://github.com/PiotrPrus/WeatherGlanceWidget) | Jetpack Glance weather widget patterns |
| [open-meteo-api-kotlin](https://github.com/open-meteo/open-meteo-api-kotlin) | Official Kotlin SDK with FlatBuffer efficiency |
| [simplenlg](https://github.com/simplenlg/simplenlg) | Java NLG library for weather summary generation |
| [supercell-wx](https://github.com/dpaulat/supercell-wx) | Advanced open-source radar viewer |

---

## 4. Implementation Roadmap

### Phase 1: Quick Wins (1-2 weeks each)

Low effort, high impact. No new dependencies or APIs.

| # | Feature | Files Affected | Effort |
|---|---|---|---|
| 1.1 | **Natural language weather summary** (template engine) | New `WeatherSummaryGenerator.kt`, `MainScreen.kt` | Low |
| 1.2 | **"Warmer than yesterday" comparison** | `WeatherRepository.kt` (cache yesterday), `CurrentConditionsHeader.kt` | Low |
| 1.3 | **Precipitation hours per day** ("3h of rain expected") | `OpenMeteoApi.kt` (add field), `DailyForecastList.kt` | Low |
| 1.4 | **Sunshine duration per day** | `OpenMeteoApi.kt`, `DailyForecastList.kt` | Low |
| 1.5 | **Snowfall & snow depth display** | `OpenMeteoApi.kt`, `WeatherDetailsGrid.kt` | Low |
| 1.6 | **CAPE severe weather indicator** | `OpenMeteoApi.kt`, `AlertBanner.kt` or new card | Low |
| 1.7 | **Widget tap-to-refresh** | All 3 widget files + `WidgetRefreshWorker.kt` | Low |
| 1.8 | **Widget Material You colors** | `WidgetTheme.kt` | Low |
| 1.9 | **Golden hour / blue hour times** | `MoonPhaseCard.kt` or new card | Low |
| 1.10 | **Hourly UV forecast note** ("UV peaks at 1 PM") | `UvIndexBar.kt` | Low |
| 1.11 | **Outdoor activity score** | New `ActivityScoreCard.kt` | Low |
| 1.12 | **Beaufort wind scale colors** | `WindCompass.kt` | Low |
| 1.13 | **Merge TalkBack semantics** | Various component files | Low |
| 1.14 | **Haptic feedback for alerts** | `AlertBanner.kt` | Low |
| 1.15 | **Consolidate share helpers** | `ShareWeatherHelper.kt`, `WeatherShareHelper.kt` | Low |

### Phase 2: Medium Features (2-4 weeks each)

Moderate effort. May require new API calls or UI components.

| # | Feature | Key Dependencies | Effort |
|---|---|---|---|
| 2.1 | **Rain-in-next-hour nowcasting card** (60-min interactive timeline) | Open-Meteo `minutely_15` endpoint | Medium |
| 2.2 | **Persistent current-weather notification** | New notification channel + WorkManager | Medium |
| 2.3 | **Meteocons Lottie animated weather icons** | [weather-icons](https://github.com/basmilius/weather-icons), Lottie (already a dep) | Medium |
| 2.4 | **Weather-adaptive color theming** | Material 3 `ColorScheme.copy()` | Medium |
| 2.5 | **Sun/moon arc visualization** | Canvas arc drawing | Medium |
| 2.6 | **Historical temperature normals band** | Open-Meteo Historical API | Medium |
| 2.7 | **Interactive temperature graph** (drag to inspect) | Compose gesture handling | Medium |
| 2.8 | **Reorderable home screen cards** | Compose LazyColumn drag reorder | Medium |
| 2.9 | **Compact 4x1 forecast strip widget** | Glance AppWidget | Medium |
| 2.10 | **AQI circular gauge visualization** | Canvas arc + gradient | Medium |
| 2.11 | **5-day AQI forecast** | Open-Meteo AQ API `forecast_days=5` | Medium |
| 2.12 | **Driving condition alerts** | Pressure + temp + dewpoint logic | Medium |
| 2.13 | **Migraine/pressure trigger alerts** | Pressure rate-of-change tracking | Medium |
| 2.14 | **Weather comparison between two locations** | Dual forecast fetch + comparison UI | Medium |
| 2.15 | **Proactive cache for all saved locations** | Background WorkManager job | Medium |

### Phase 3: Major Features (4-8 weeks each)

High effort. New subsystems or significant architecture changes.

| # | Feature | Key Dependencies | Effort |
|---|---|---|---|
| 3.1 | **Native MapLibre radar** (replace Windy WebView) | MapLibre Android SDK (already a dep), RainViewer tiles | High |
| 3.2 | **Animated radar playback** on native map | MapLibre raster source swapping | High |
| 3.3 | **Wear OS companion** (complications + tile) | Wear OS SDK, ProtoLayout | High |
| 3.4 | **Lightning strike overlay** (Blitzortung integration) | WebSocket to Blitzortung, MapLibre overlay | High |
| 3.5 | **Per-widget location configuration** | Glance configuration activity | High |
| 3.6 | **Gemini Nano weather summaries** (on-device LLM) | ML Kit GenAI SDK | High |
| 3.7 | **Live weather wallpaper** | WallpaperService + Canvas | High |
| 3.8 | **Community weather reports** (crowd-sourced) | Firebase/Supabase backend | High |

### Phase 4: Stretch Goals

| # | Feature | Notes |
|---|---|---|
| 4.1 | Multi-source fallback system (Breezy Weather pattern) | User configures secondary sources per data type |
| 4.2 | Custom weather icon pack support | Load icon mappings from assets/files |
| 4.3 | International alert sources (WMO, national services) | Requires per-country API integration |
| 4.4 | Weather map layers (wind, temp, pressure overlays) | MapLibre + tile sources |
| 4.5 | Tablet-specific two-pane layout | Adaptive layout enhancement |

---

## Version Targets

**v1.3.0** - Phase 1 (Quick Wins)
- Weather summaries, comparisons, unused API fields, widget improvements, accessibility

**v1.4.0** - Phase 2a (Data & Visualization)
- Nowcasting card, Meteocons icons, weather theming, sun/moon arcs, temperature normals

**v1.5.0** - Phase 2b (UX & Health)
- Reorderable cards, interactive graphs, health alerts, driving conditions, AQI forecast

**v2.0.0** - Phase 3 (Platform)
- Native MapLibre radar, Wear OS companion, animated radar playback

**v2.x** - Phase 4 (Ecosystem)
- Multi-source fallback, community reports, live wallpaper, LLM summaries
