# ZeusWatch Roadmap

**Current Version**: v1.20.3
**Architecture**: Kotlin 2.1.0 / Jetpack Compose / Hilt / MVVM / Multi-module (phone + wear)  
**Flavors**: `standard` (Google Play) / `freenet` (F-Droid)

---

## Completed Milestones

> Moved to `ROADMAP-COMPLETED.md`.

---

## Research & Strategic Gaps (Auto-Generated Analysis)

*Generated 2026-04-14 via full codebase audit. Categorized by priority.*

---

### HIGH PRIORITY

- [CLOSED v1.17.0] ~~Environment Canada forecast adapter is stubbed~~ — `EnvironmentCanadaForecastAdapter` consumes `api.weather.gc.ca/collections/citypageweather-realtime`, picks the nearest feature inside a 0.5° → 1.5° fallback bbox, and maps `currentConditions` + `forecastGroup.forecast[]` to `WeatherData`. kPa → hPa pressure, km → m visibility, day/night forecast period pairs merged into a single `DailyConditions`. Hourly is empty by design (not published on ECCC's free OGC tier).
- [CLOSED v1.16.0] ~~Missing ProGuard rule for Gemini Nano~~ — added `-keep class com.google.ai.edge.**` + `-dontwarn` in `app/proguard-rules.pro`.
- [CLOSED v1.16.0] ~~No production crash reporting~~ — ACRA 5.13.1 (GMS-free, both flavors) with consent-gated mail sender + PII redaction. Replaces the Crashlytics path dropped in v1.5.0.
- [CLOSED v1.16.0] ~~`showYesterdayComparison` setting is not wired~~ — `MainScreen` now passes `null` when the preference is off.
- [CLOSED v1.16.0] ~~No API rate-limit awareness~~ — `RateLimitInterceptor` (GCRA token bucket + `Retry-After` single retry + fail-fast cap) wired onto OWM / OWM-AQI / Pirate Weather at rates sized to free tiers. OkHttp retry also covers 5xx with exponential backoff.

---

### MEDIUM PRIORITY

- **[PARTIAL 2026-05-14] Zero localization support**
  No `values-*/strings.xml` locale variants exist. Only 10 `stringResource()`/`getString()` calls across 157 source files — all other user-facing text is hardcoded in Kotlin. Extracting strings retroactively across 28 card types, 17 settings sections, 5 screens, and 4 widget types is a significant effort that compounds with each new feature.
  - [x] Added resource-backed strings for core bottom navigation, Locations, Compare, and Wear OS loading/empty/header/status copy, plus an initial Spanish `values-es` set for those high-traffic surfaces.
  - [ ] Continue extraction across Settings sections, Today card copy, widgets, notifications, dialogs, and data-source labels before marking this item closed.

- **[CLOSED v1.18.0] ~~Accessibility gaps on Canvas-drawn elements~~**
  v1.17.0 added `mergeDescendants` `contentDescription` + `liveRegion` semantics to 5 Canvas-heavy cards: AqiGauge, TemperatureGraph, PressureTrendCard, MoonPhaseCard, CloudCoverCard. v1.18.0 extends chart/gauge summaries to wind compass, sun ephemeris arc, wind trend, visibility scale, precipitation chart, humidity card, UV index bar, nowcast card, on-this-day card, outdoor score card, and sunshine duration card, with focused `AccessibilityHelper` coverage so TalkBack copy stays regression-tested. v1.18.0 also adds Compose accessibility checks to the instrumented UI tests and a GitHub Actions emulator job running `connectedStandardDebugAndroidTest` on API 35. Decorative particle canvases remain intentionally silent.

- **[PARTIAL] Certificate pinning on API endpoints**  
  v1.17.0 shipped the scaffolding: `ApiCertificatePins.build()` is wired into the OWM / OWM-AQI / Pirate Weather OkHttp clients. `hostPins` is intentionally empty until captured per-release via `tools/capture_api_pins.sh`. Keyless public endpoints (Open-Meteo, NWS, Bright Sky, MET Norway, ECCC) stay unpinned by design. Next step: run the capture script as part of the v1.17.1 release prep and populate the map.

- [CLOSED v1.16.0] ~~CI/CD does not build Wear OS module~~ — `build.yml` now compiles + tests the wear module on every push; `release.yml` builds wear release variants.
- [CLOSED v1.16.0] ~~CI release workflow builds debug APKs~~ — `release.yml` now builds `assembleStandardRelease` / `assembleFreenetRelease` / `wear:assembleRelease`, reconstructs signing credentials from GitHub secrets, and uploads renamed APKs per variant to the matching Release.
- [CLOSED v1.16.0] ~~No static analysis beyond Android Lint~~ — Detekt 1.23.8 with baseline mode wired into `build.yml` + Gradle root `detekt` task. Dependabot (weekly Gradle, monthly Actions).
- [CLOSED v1.16.0] ~~Room `exportSchema = false`~~ — flipped to `true` with output routed to `app/schemas/` via KSP arg; baseline schema checked in.
- [CLOSED v1.16.0] ~~Wear OS has no error recovery UI for sync failures~~ — CurrentScreen renders a footer pill showing data source ("Phone" vs "Watch") + freshness ("4m ago") and taps to force a re-sync through `WearWeatherViewModel.loadWeather()`.

---

### LOW PRIORITY

- **[CLOSED v1.19.0 + v1.20.0] ~~Test coverage gaps in adapters and Wear OS~~**
  v1.18.0: `OwmForecastAdapter`, `OwmAlertAdapter`, `OwmAqiAdapter`, `BrightSkyForecastAdapter`, `BrightSkyAlertAdapter`, `EnvironmentCanadaAlertAdapter`, `MeteoAlarmAdapter`, `JmaAlertAdapter` — 87 assertions. v1.19.0: `PirateWeatherForecastAdapterTest` (13 tests) + `PwIconMapperTest` (22 tests). v1.20.0: `WeatherSummaryEngineTest` (25 tests), `WeatherSummaryEnginePrecipTest` (8 tests), `WeatherSummaryEngineWithStyleTest` (5 tests) — covers all time-of-day slots, all condition phrases, wind/UV/humidity notes, yesterday comparison, all 7 precipitation outlook branches, and AI engine delegate/fallback/null/exception paths. Remaining gaps: no tests for any Wear OS code (tile, complication, sync, screens) or `GeminiNanoSummaryEngine`.

- **OkHttp retry uses `Thread.sleep()` in coroutine context**  
  `NetworkModule` retry interceptor (lines 47–67) uses `Thread.sleep()` for exponential backoff. While this works (OkHttp interceptors run on OkHttp's dispatcher thread, not the coroutine dispatcher), it blocks the OkHttp thread pool slot. Using `delay()` isn't possible in an interceptor, but an alternative is to implement retry at the repository level with coroutine `delay()`.

- **Phone/Wear versionCode divergence**  
  Phone is at versionCode 75, Wear at 53. If distributing as paired APKs via Play Store, this mismatch could confuse the pairing logic. Not an issue if published as independent listings. Consider aligning if Play Store distribution is planned.

- **`WeatherSummaryEngine` duplicates context between template and AI paths**  
  Both `generate()` (template) and `generateWithStyle()` (AI wrapper) construct weather context strings independently. A shared context builder would reduce drift between what the template sees and what the AI prompt contains.

- **[CLOSED v1.20.0] ~~No database VACUUM or WAL checkpoint~~**
  `DatabaseMaintenanceWorker` (`@HiltWorker CoroutineWorker`) runs `PRAGMA wal_checkpoint(TRUNCATE)` weekly. Scheduled unconditionally at app startup via `ExistingPeriodicWorkPolicy.KEEP`; no network required.

- **Freenet flavor cannot sync to Wear OS at all**  
  `WearSyncManager` is a no-op in `freenet`. F-Droid users with a Wear OS watch get zero phone-to-watch sync. The watch falls back to direct API calls, which works but defeats the efficiency gain. Consider a non-GMS sync mechanism (Bluetooth serial, companion device manager) for freenet.

- **[CLOSED 2026-04-26] ~~Deep link handling for notifications~~**
  Custom-rule notifications route to `Routes.CUSTOM_ALERTS`; severe alert notifications now open the Today alert surface, nowcast notifications open the Rain Next Hour card, and health notifications open the Health Alerts card. MainScreen scrolls to the targeted surface and temporarily exposes hidden target cards for notification-open flows without changing the saved card layout.

---

### OPPORTUNITIES (Not Bugs, But High-Impact Features)

- **Wear OS watch face**  
  The watch has tile, complication, and 3 screens — but no custom watch face. A weather-aware watch face (temp on dial, condition background, complication slots) would be the highest-visibility Wear feature.

- [CLOSED v1.17.0] ~~Weather source: MET Norway (Yr.no)~~ — `MetNorwayForecastAdapter` + `MetNorwayApi` + `MetSymbolMapper` shipped; selectable in Settings > Data Sources. Global coverage, highest detail in Nordic region. CC BY 4.0 attribution in README.

- **Weather source: Bureau of Meteorology (Australia)**  
  BOM provides free forecast and warning APIs. Australian users currently rely on Open-Meteo or OWM. A dedicated adapter would provide better severe weather coverage for the Southern Hemisphere.

- [CLOSED v1.17.0] ~~Widget interaction: tap-to-refresh~~ — freshness pill on each loaded widget is now clickable (routes through `WidgetRefreshAction`). Empty-state body tap behavior preserved. Battery-saver short-circuit in `WidgetRefreshWorker` (skip at ≤15%) still guards the enqueued work.

- [CLOSED v1.16.0] ~~Notification grouping~~ — nowcast + health + custom-rule notifications now share `AMBIENT_GROUP_ID` with a group-summary row; severe alerts keep their existing severity group.

- **Offline-first architecture for saved locations**  
  Currently, switching to a saved location triggers a fresh API call. Caching the last-known weather per saved location (Room, keyed by location ID) would allow instant location switching with a background refresh — better perceived performance.

- **Compose Multiplatform (future)**  
  The UI layer is 100% Compose. If Compose Multiplatform matures for iOS, the existing screen code could be shared cross-platform with platform-specific data layers. Not actionable today, but the architecture is aligned for it.

---

## External Ecosystem & Resource Intelligence

*Researched 2026-04-14 via GitHub API, live API verification, and ecosystem analysis.*

---

### Comparable Open-Source Projects

#### Tier 1 -- Direct Competitors

| Project | Stars | Stack | Sources | Wear OS | License |
|---------|-------|-------|---------|---------|---------|
| [Breezy Weather](https://github.com/breezy-weather/breezy-weather) | ~9,800 | Kotlin/Compose | 58 providers | No (Gadgetbridge bridge) | LGPL-3.0 |
| [WeatherMaster](https://github.com/PranshulGG/WeatherMaster) | ~2,650 | Flutter/Dart | 15 providers | No | GPL-3.0 |
| [Rain](https://github.com/darkmoonight/Rain) | ~980 | Flutter/Dart | 1 (Open-Meteo) | No | MIT |
| [Overmorrow](https://github.com/bmaroti9/Overmorrow) | ~730 | Flutter/Dart | 4 providers | No | GPL-3.0 |
| [Forecastie](https://github.com/martykan/forecastie) | ~900 | Java | 1 (OWM) | No | GPL-3.0+ |
| [Bura](https://github.com/davidtakac/bura) | ~370 | Kotlin/Compose | 1 (Open-Meteo) | No | GPL-3.0 |

- **Breezy Weather** is the dominant FOSS weather app. Its 58-source provider system is the gold standard. Architecture: modular Kotlin/Compose with plugin-style source directories. Localization: 60 locales via Weblate. CI: full GitHub Actions with Spotless lint, per-ABI signed APKs, SHA-256 checksums, draft GitHub Releases. Distribution: GitHub Releases + F-Droid (official + IzzyOnDroid). Not on Google Play by design. Forked from Geometric Weather (now archived at [WangDaYeeeeee/GeometricWeather](https://github.com/WangDaYeeeeee/GeometricWeather), 2,524 stars).
- **WeatherMaster** is the fastest-growing competitor (0 to 2,650 stars in under 2 years). Flutter cross-platform with customizable widget layout system (users rearrange cards). 15 sources including Met Norway, ECMWF, BOM, CMA, KNMI, DMI. Localization via Crowdin.
- **Rain** is notable for MIT license (most permissive in the space), multi-platform via Flutter (Android, iOS, Linux, macOS, Windows, Web), and zero API key requirement (Open-Meteo only).

**Strategic takeaways:**
- **Wear OS is a wide-open gap.** Zero competitors have native Wear OS support. Breezy Weather's Gadgetbridge bridge is the closest but not a native experience. ZeusWatch's DataLayer sync is unique in the FOSS ecosystem.
- **Health alerts are completely uncontested.** No competitor offers migraine pressure detection, respiratory alerts, or arthritis sensitivity. This is a blue-ocean differentiator.
- **Source count is the primary gap vs. Breezy Weather.** 58 vs. ZeusWatch's 6 (Open-Meteo, OWM, Pirate Weather, Bright Sky, NWS, Environment Canada alerts). Adding MET Norway and Environment Canada forecasts would close the most important regional gaps.
- **Flutter dominates the challenger tier.** 3 of the top 5 growing apps use Flutter. Being native Kotlin gives ZeusWatch performance and Wear OS advantages.
- **No competitor combines Wear OS + multi-source + health alerts.** ZeusWatch is uniquely positioned.

#### Tier 2 -- Niche / Educational

| Project | Stars | Notes |
|---------|-------|-------|
| [FengYunWeather](https://github.com/wdsqjq/FengYunWeather) | ~2,400 | Kotlin, Chinese market, no releases |
| [Instant-Weather](https://github.com/mayokunadeniyi/Instant-Weather) | ~760 | Kotlin MVVM showcase (Hilt, Coroutines, Room). Tutorial-quality. MIT. |
| [Feather](https://github.com/jhomlala/Feather) | ~690 | Flutter, beautiful UI/UX focused. Apache-2.0. |
| [PlayWeather](https://github.com/zhujiang521/PlayWeather) | ~530 | Kotlin Compose + MVVM + Room + Hilt. Chinese market. MIT. |

---

### Data Sources & APIs

#### Priority 1 -- High-Impact, Verified Live

**MET Norway LocationForecast 2.0** -- Richest free weather API available. No API key.
| Field | Details |
|-------|---------|
| Forecast | `https://api.met.no/weatherapi/locationforecast/2.0/complete` |
| Nowcast | `https://api.met.no/weatherapi/nowcast/2.0/complete` (5-min precip, Nordic) |
| Sun | `https://api.met.no/weatherapi/sunrise/3.0/sun` |
| Moon | `https://api.met.no/weatherapi/sunrise/3.0/moon` (includes phase angle 0-360) |
| Alerts | `https://api.met.no/weatherapi/metalerts/2.0/all.json` (Norway) |
| Ocean | `https://api.met.no/weatherapi/oceanforecast/2.0/complete` |
| Air Quality | `https://api.met.no/weatherapi/airqualityforecast/0.1/` (Norway stations) |
| Docs | https://api.met.no/weatherapi/locationforecast/2.0/documentation |
| Terms | https://api.met.no/doc/TermsOfService |
| Format | JSON (GeoJSON Feature) |
| Auth | None. **Must set custom User-Agent** (banned agents: `okhttp`, `Dalvik`, `Java`). |
| Rate Limit | 20 req/s aggregate. Must honor `Expires` header + `If-Modified-Since`. |
| License | CC BY 4.0. Attribution required. |
| Coverage | Global (9 days). Highest detail in Nordic region. |

The `/complete` endpoint provides precipitation probability, thunder probability, temperature/wind percentiles (10th/90th), UV index, fog fraction, cloud layers (low/med/high), dew point, and wind gusts. This is premium-tier data unavailable from most free sources.

**Environment Canada Forecast (OGC API)** -- Fills the stubbed forecast gap.
| Field | Details |
|-------|---------|
| City Forecast | `https://api.weather.gc.ca/collections/citypageweather-realtime/items?f=json&lang=en` |
| HRDPS Model | `https://api.weather.gc.ca/collections/prognos-hrdps-realtime/items` (2.5 km) |
| AQI | `https://api.weather.gc.ca/collections/aqhi-forecasts-realtime/items` |
| Docs | https://eccc-msc.github.io/open-data/readme_en/ |
| OpenAPI | https://api.weather.gc.ca/openapi |
| Format | GeoJSON (OGC API Features) |
| Auth | None |
| License | Open Government Licence - Canada |

The `citypageweather-realtime` collection returns full city page weather as structured JSON: `currentConditions`, `forecastGroup` (multi-day text), `hourlyForecastGroup`, `riseSet`, `warnings`. This directly resolves the HIGH-priority gap of the stubbed Canada forecast adapter.

**SMHI (Sweden) -- SNOW1gv1** -- Recently migrated endpoint.
| Field | Details |
|-------|---------|
| Endpoint | `https://opendata-download-metfcst.smhi.se/api/category/snow1g/version/1/geotype/point/lon/{lon}/lat/{lat}/data.json` |
| Format | JSON |
| Auth | None |
| License | CC BY 4.0 |
| Coverage | Sweden + nearby Scandinavia, ~10 days |

**Critical**: The old `pmp3g/version/2` endpoint was deprecated 2026-03-31 and returns 404. Parameters now use flat `data.{name}` objects: `air_temperature`, `wind_speed`, `symbol_code`, `thunderstorm_probability`, `precipitation_amount_mean/min/max/median`, `probability_of_precipitation`, `cloud_area_fraction`, etc.

#### Priority 2 -- Valuable Additions

**Open-Meteo New Endpoints** (beyond what ZeusWatch already uses):
| Endpoint | URL | Data |
|----------|-----|------|
| Pollen | `https://air-quality-api.open-meteo.com/v1/air-quality` (add `&hourly=alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen`) | 6 pollen types in grains/m3. Free, no key. European coverage (CAMS/Copernicus). |
| Marine | `https://marine-api.open-meteo.com/v1/marine` | Wave height, direction, period, swell, ocean current, sea surface temp. |
| Flood | `https://flood-api.open-meteo.com/v1/flood` | River discharge (m3/s) from GloFAS v4, 210-day forecast + seasonal. |
| Ensemble | `https://ensemble-api.open-meteo.com/v1/ensemble` | Individual ensemble members from ICON, ECMWF, GFS, showing forecast uncertainty. |
| Climate | `https://climate-api.open-meteo.com/v1/climate` | CMIP6 projections to 2050. |

The pollen endpoint is the highest-value addition here -- free, no key, already returning data (e.g., birch_pollen: 17.6 grains/m3 for Berlin). This could power a pollen card without needing Google Pollen API ($) or Ambee (rate-limited).

**NOAA SWPC -- Aurora / Space Weather** (all verified, no key, JSON):
| Endpoint | Data |
|----------|------|
| `https://services.swpc.noaa.gov/json/ovation_aurora_latest.json` | Aurora probability grid (65,160 points, intensity 0-100) |
| `https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json` | Kp index forecast (geomagnetic activity, aurora visibility) |
| `https://services.swpc.noaa.gov/products/noaa-scales.json` | Current NOAA space weather scale levels (R/S/G storms) |

Unique differentiator potential -- an "aurora probability at your location" card would be uncontested in the weather app space.

**IEM NEXRAD Tiles** (US radar alternative to RainViewer):
| Field | Details |
|-------|---------|
| Tiles | `https://mesonet.agron.iastate.edu/cache/tile.py/1.0.0/nexrad-n0q-900913/{z}/{x}/{y}.png` |
| WMS | `https://mesonet.agron.iastate.edu/cgi-bin/wms/nexrad/n0q.cgi` |
| Auth | None |
| License | Public domain (US government data) |
| Coverage | CONUS (160 WSR-88D radars), archive from 2012 |

Standard Z/X/Y slippy map tile format -- works directly with MapLibre. No rate limits, no API key.

#### Priority 3 -- Regional / Niche

| Source | Coverage | Auth | Format | Notes |
|--------|----------|------|--------|-------|
| **AEMET** (Spain) | Spain | Free API key (register at opendata.aemet.es) | JSON (two-step: fetch URL, then fetch data) | Municipality-coded. Spanish parameter names. |
| **Meteo France** | France + global (ARPEGE) | JWT token (register at portail-api.meteofrance.fr) | JSON, GRIB | AROME 1.5 km model. Open-Meteo already proxies their data, so value is mainly direct PIAF nowcast + vigilance alerts. |
| **BOM Australia** | Australia | None | JSON | **Legal risk**: copyright notice explicitly prohibits third-party use. Undocumented API. Many apps use it anyway, but no legal safety net. |
| **WAQI** | Global (11,000+ stations) | Free key (aqicn.org/data-platform/token) | JSON | Station-level AQI. **Non-commercial license only.** |
| **EPA AirNow** | US/Canada/Mexico | Free key (airnowapi.org) | JSON/XML | 2,500+ stations. AQI observations + city forecasts. |
| **Google Pollen API** | 65+ countries, 1 km res | Google Maps Platform key | JSON | 15 plant species, 5-day forecast. 10,000 calls/month free tier. Requires billing account. |

**Astronomical (verified live, no key):**
- **sunrise-sunset.org**: `https://api.sunrise-sunset.org/json?lat={lat}&lng={lng}&date={date}&formatted=0` -- includes civil/nautical/astronomical twilight begin+end (MET Norway doesn't include these).
- **Open Notify ISS**: `http://api.open-notify.org/iss-now.json` -- real-time ISS lat/lon.

---

### Recommended Libraries / Tools

#### Crash Reporting

| Library | Artifact | Version | License | Best For |
|---------|----------|---------|---------|----------|
| **ACRA** | `ch.acra:acra-core` (+ `acra-http`, `acra-mail`, `acra-notification`) | 5.13.1 | Apache-2.0 | `freenet` flavor. Zero GMS dependency. Pluggable senders (HTTP, email). No external service required. |
| **Sentry** | `io.sentry:sentry-android` | 8.38.0 | MIT | `standard` flavor. Breadcrumbs, performance traces, session replay. Self-hosted option available. No GMS dependency in SDK. |

Recommendation: ACRA for both flavors (simplest, no external service) or flavor-split with Sentry (standard) + ACRA (freenet).

#### Static Analysis

| Tool | Artifact / Plugin ID | Version | What It Does |
|------|---------------------|---------|--------------|
| **Detekt** | `id("io.gitlab.arturbosch.detekt")` | 1.23.8 | 200+ Kotlin static analysis rules. Complexity, naming, style, bugs. |
| **Compose Rules** | `io.nlopez.compose.rules:detekt` | 0.5.7 | Detekt plugin for Compose best practices (modifier ordering, state hoisting, unstable params). |
| **ktlint** | `id("org.jlleitschuh.gradle.ktlint")` | 14.2.0 | Kotlin formatting + style linter. `ktlintCheck` and `ktlintFormat` Gradle tasks. |

#### Accessibility Testing

| Tool | Artifact | Version | Integration |
|------|----------|---------|-------------|
| **Accessibility Test Framework** | `com.google.android.apps.common.testing.accessibility.framework:accessibility-test-framework` | 4.1.1 | Foundation library. |
| **Espresso Accessibility** | `androidx.test.espresso:espresso-accessibility` | 3.6.1 | One-line `AccessibilityChecks.enable()` in existing instrumented tests. Auto-audits every ViewAction. |

#### Localization

| Tool | Artifact | Notes |
|------|----------|-------|
| **Crowdin CLI** | `npm i @crowdin/cli` (v4.14.1) | CI/CD integration. Upload `strings.xml`, translators work on web, CLI pulls translations. Free for open source. |
| **Crowdin Android SDK** | `com.github.crowdin.mobile-sdk-android:sdk:1.15.0` (JitPack) | Over-the-air translation delivery. New translations appear without app update. MIT license. |

Breezy Weather uses **Weblate** (self-hosted). WeatherMaster uses **Crowdin**. Both are free for FOSS projects.

#### Compose Performance

| Tool | Artifact | Version | What It Does |
|------|----------|---------|--------------|
| **Rebugger** | `io.github.theapache64:rebugger` | 1.0.1 | Drop into any composable to log which parameter triggered recomposition. Dev-only. |
| **Compose Compiler Metrics** | Built-in (`composeCompiler { reportsDestination = ... }`) | -- | Generates stability/skippability reports for every composable. Zero dependency. |
| **Compose Runtime Tracing** | `androidx.compose.runtime:runtime-tracing` (via Compose BOM) | -- | Labels composable functions in Perfetto/Studio profiler system traces. |

#### Charts

| Library | Artifact | Version | License | Why |
|---------|----------|---------|---------|-----|
| **Vico** | `com.patrykandpatrick.vico:compose-m3` | 3.0.1 | Apache-2.0 | Top Compose-native chart library. Line, bar, stacked, combined charts. Material 3 theming. Ideal for hourly temp graphs, pressure trends, precip charts. |

#### Wear OS

| Library | Artifact | Version | Why |
|---------|----------|---------|-----|
| **Horologist** | `com.google.android.horologist:horologist-compose-layout`, `horologist-datalayer`, `horologist-tiles` | 0.8.3-alpha | Google's official Wear supplementary libs. DataLayer abstractions simplify phone-watch sync. Responsive layout improvements. |
| **Wear Compose Material 3** | `androidx.wear.compose:compose-material3` | 1.5.0-beta01 | M3 Expressive for Wear. Aligns watch UI with phone-side Material 3 design language. |
| **ProtoLayout Material 3** | `androidx.wear.protolayout:protolayout-material3` | 1.3.0 | M3 tiles with Lottie support. ZeusWatch already uses Lottie 6.6.2. |

#### Security & CI

| Tool | Artifact / Config | Version | What It Does |
|------|-------------------|---------|--------------|
| **OkHttp CertificatePinner** | Built into `com.squareup.okhttp3:okhttp` | 4.12.0 | Pin SHA-256 cert hashes for API-key-bearing endpoints. Zero extra dependency. |
| **OWASP Dependency-Check** | `id("org.owasp.dependencycheck")` | 12.2.1 | Scans dependency tree against NVD for known CVEs. HTML/JSON reports. |
| **GitHub Dependabot** | `.github/dependabot.yml` | -- | Auto-PRs for dependency updates. Supports Gradle KTS + Version Catalogs. |

Dependabot config for ZeusWatch:
```yaml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 10
```

#### Rate Limiting

No library needed. Implement a custom OkHttp `Interceptor` using `AtomicInteger` token bucket (~30 lines). Resilience4j v1.7.1 (`io.github.resilience4j:resilience4j-ratelimiter`, Apache-2.0) is the only version compatible with Android's runtime -- v2.x+ requires Java 17 which Android doesn't support.

## Open-Source Research (Round 2)

### Related OSS Projects
- **aarash709/Weather** — https://github.com/aarash709/Weather — Kotlin/Compose, Open-Meteo backend, offline-first, WorkManager background fetch; Detekt/Kotlinter pipeline
- **GustavLindberg99/AndroidWeather** — https://github.com/GustavLindberg99/AndroidWeather — GPL weather app with four launcher widgets (transparent clock + three weather variants) — reference for widget design
- **Breezy Weather** — https://github.com/breezy-weather/breezy-weather — multi-provider forecasts (Open-Meteo, MET Norway, Accu, DWD, etc.), air quality, allergen/pollen, radar, Material You; best-in-class FOSS weather app
- **Geometric Weather** — https://github.com/WangDaYeeeeee/GeometricWeather — mature weather app with animated icons and atmosphere particle effects
- **aishwarya-kamal/Weather** — https://github.com/aishwarya-kamal/Weather — clean MVVM/Compose reference
- **daniel-waiguru/WeatherApp** — https://github.com/daniel-waiguru/WeatherApp — offline-first Compose architecture with Jetpack components
- **ChrisTs8920/Weatherly** — https://github.com/ChrisTs8920/Weatherly — Compose + OpenWeatherMap

### Features to Borrow
- Multi-provider forecast fusion: run 3+ providers, show agreement range and divergence flag (Breezy Weather)
- Air-quality, pollen, and allergen cards with local thresholds (Breezy Weather)
- Animated precipitation particle overlay keyed to current conditions (Geometric Weather)
- Glance widgets in 3 sizes (2x1 transparent clock, 2x2 current, 4x2 forecast) with same rendering path as in-app cards (GustavLindberg99)
- Offline-first repository that serves cached forecast instantly, updates in background (aarash709)
- Per-location background-update cadence tied to BatteryManager state (Breezy Weather pattern)
- Material You dynamic-color card theming derived from weather condition (Material You docs; Breezy applies this)
- Per-card settings menu: reorder, hide, customize unit on a card-by-card basis (Breezy Weather)
- SMS-on-severe-weather rule with a user-defined trigger (hail > X mm, wind > Y mph) (niche, community fork pattern)
- Wear OS Complication types: short-text, ranged-value for UV index, photo for radar snapshot (Wear samples)

### Patterns & Architectures Worth Studying
- Modular provider pattern: every forecast source is a WeatherSource interface implementation, pluggable and user-enable-able (Breezy Weather)
- Single Repository + Flow + StateFlow single-source-of-truth with WorkManager for background refresh (aarash709)
- Glance + Compose shared rendering: write the card once as a Composable and a matching GlanceComposable, share the data class (Google Glance samples)
- Provider-agnostic unit system: store SI internally, convert only at render (Breezy Weather)
- DataLayer sync via MessageClient with "lastMessageId" dedupe (Wear OS DataLayer patterns)

## Implementation Deep Dive (Round 3)

### Reference Implementations to Study
- **breezy-weather/breezy-weather/app/src/main/java/org/breezyweather/sources/** — https://github.com/breezy-weather/breezy-weather/tree/main/app/src/main/java/org/breezyweather/sources — canonical multi-provider architecture. Each source is a separate package implementing a common `WeatherSource` interface. Direct blueprint for ZeusWatch's `WeatherSourceManager` + `AlertSourceAdapter` generalization.
- **breezy-weather/breezy-weather/docs/SOURCES.md** — https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md — 50+ sources with per-source capability matrix (supports forecast? alerts? AQI? pollen?). Template for ZeusWatch's roadmap item "per-card settings menu".
- **breezy-weather/breezy-weather/app/src/main/java/org/breezyweather/sources/openmeteo/OpenMeteoService.kt** — https://github.com/breezy-weather/breezy-weather/blob/main/app/src/main/java/org/breezyweather/sources/openmeteo/OpenMeteoService.kt — reference Open-Meteo adapter with proper unit normalization. Compare against ZeusWatch's `OpenMeteoApi`.
- **android/wear-os-samples/DataLayer/Wearable/src/main/java/com/example/android/wearable/datalayer/MainActivity.kt** — https://github.com/android/wear-os-samples/tree/master/DataLayer — official DataClient + `setUrgent()` pattern. Compare against ZeusWatch's `WearSyncManager`.
- **google/horologist/datalayer** — https://github.com/google/horologist/tree/main/datalayer — higher-level wrapper for `DataClient` with DataStore-backed proto persistence. Template for removing boilerplate from `SyncedWeatherStore` (currently SharedPreferences).
- **WangDaYeeeeee/GeometricWeather/app/src/main/java/wangdaye/com/geometricweather/ui/widget/weatherView/WeatherView.kt** — https://github.com/WangDaYeeeeee/GeometricWeather — animated precipitation particle overlay reference. Direct template for ZeusWatch's roadmap item "animated precipitation overlay".
- **breezy-weather/breezy-weather/app/src/main/java/org/breezyweather/common/basic/models/options/unit/TemperatureUnit.kt** — https://github.com/breezy-weather/breezy-weather/blob/main/app/src/main/java/org/breezyweather/common/basic/models/options/unit — provider-agnostic unit enum with conversion. Template for ZeusWatch's unit system cleanup.
- **KieronQuinn/Smartspacer/app-legacy/src/main/java/com/kieronquinn/app/smartspacer/targets/weather/WeatherTarget.kt** — https://github.com/KieronQuinn/Smartspacer — reference Smartspacer target exposing weather as a system-level at-a-glance. Future-hook for ZeusWatch to participate in Pixel Launcher at-a-glance via Smartspacer.
- **nwrkhd/breezy-weather-nwrkhd-fork** or various Breezy forks — check for per-card reorder persistence patterns; ZeusWatch already has card reorder but look at their bug-fix history for edge cases.

### Known Pitfalls from Similar Projects
- **DataClient has a 30-minute default sync delay without `.setUrgent()`** — battery-saver throttling. ZeusWatch must call `PutDataMapRequest...asPutDataRequest().setUrgent()` or weather updates land up to 30min late on watch. https://developer.android.com/training/wearables/data/data-items
- **DataClient silently drops duplicate payloads** — if content of `DataMap` is byte-identical to the previously-sent item, `onDataChanged` does not fire. Always include a nonce (timestamp or requestId) field. ZeusWatch's sync already includes timestamp — verify.
- **DataLayer payload limit 100KB** — exceed that and `putDataItem` silently fails. 12-hour hourly forecast with extended fields can approach this; use `Asset` for larger payloads or truncate to essentials.
- **Force-stopping the app breaks DataClient sync until reopen** — known platform behavior. User-facing mitigation: a "Troubleshoot sync" button that opens app settings + an explicit re-sync call.
- **WearableListenerService needs `RECEIVE_BOOT_COMPLETED` permission and intent filter** — missing either silently suppresses reconnect after device reboot. ZeusWatch's `WeatherDataListenerService` must declare both.
- **Open-Meteo `timezone=auto` returns observations in location-local time, not UTC** — v1.14.1 already documents this bug fix in `AirQualityRepository`; verify all other adapters follow the same pattern.
- **NWS `/alerts/active` 404s silently for coordinates outside US** — already handled in v1.6.5 (HttpException check). If adding MeteoAlarm / JMA, replicate the same pattern.
- **`Geocoder` is unreliable on devices without Google Play Services on F-Droid build** — `freenet` flavor must use `Nominatim` or similar free alternative. Currently Nominatim via HTTP is in ZeusWatch; verify rate-limit compliance (1 req/sec per IP).
- **MapLibre 11.x native library not included in x86_64 emulator build by default** — requires explicit `abiFilters` declaration. Check `app/build.gradle.kts`.
- **Lottie 6.6 breaking change: `LottieCompositionFactory.fromRawRes` is now suspending on newer versions** — ZeusWatch's Meteocon renderer must handle async composition load.
- **RainViewer radar tiles API — 5-minute update interval, historical tiles expire after 2h** — v1.6.2 already throttles 5-min; verify radar playback handles expired tiles gracefully (404 → skip frame).

### Library Integration Checklist
- **Google Play Wearable DataClient** — `com.google.android.gms:play-services-wearable:18.2.0` (already pinned) — entry: `Wearable.getDataClient(context).putDataItem(PutDataMapRequest.create("/weather/current").apply { dataMap.putFloat("temp", t) }.asPutDataRequest().setUrgent())`. Gotcha: `.setUrgent()` is mandatory for near-real-time; without it the 30min battery-saver throttle applies.
- **MapLibre Android (native radar)** — `org.maplibre.gl:android-sdk:11.5.2` — entry: `MapLibre.getInstance(context); mapView.getMapAsync { map -> map.setStyle(Style.Builder().fromUri("...")) }`. Gotcha: MapLibre requires OpenGL ES 3.0 minimum; on devices reporting GL ES 2.0 (rare, old), fall back to Windy WebView provider.
- **Horologist DataLayer (Wear sync helper)** — `com.google.android.horologist:horologist-datalayer:0.6.17` — entry: `wearDataLayerRegistry.protoFlow(key = "weather")` + `wearDataLayerRegistry.serializedProto(Weather.serializer(), data)`. Gotcha: Horologist requires `com.google.android.horologist:horologist-datalayer-phone` on phone and `-watch` on wear module — mismatched versions silently break sync.


