# ZeusWatch Roadmap

**Current Version**: v1.14.0  
**Architecture**: Kotlin 2.1.0 / Jetpack Compose / Hilt / MVVM / Multi-module (phone + wear)  
**Flavors**: `standard` (Google Play) / `freenet` (F-Droid)

---

## Completed Milestones

| Version | Milestone | Date |
|---------|-----------|------|
| v1.0.0 | Initial release — share, widgets, a11y, CI/CD, 108 tests | — |
| v1.1.0 | CAPE, 5-day AQI, interactive graph, forecast strip widget | — |
| v1.2.0 | Lightning, Gemini Nano, live wallpaper, community reports, multi-source, international alerts, tablet layout | — |
| v1.3.x | 21 dynamic cards, weather-adaptive theme, NLG summaries, nowcasting, compare screen, driving/health alerts, yesterday comparison, drag-reorder locations, bug fixes, ProGuard hardening | — |
| v1.4.0 | Security hardening, offline detection, reduced motion, ImmutableList perf, OkHttp retry, parallel sub-fetches, 74 new tests | — |
| v1.5.0 | 4 new cards (25 total), single-LazyColumn perf, pull-to-refresh, card reorder, 22 crash fixes, Crashlytics removed | — |
| v1.6.x | Cloud cover + visibility cards (27 total), tabbed trend system, enhanced ephemeris arc, segmented AQI gauge, animated temp counter, gravity parallax, 4 QA audit passes | — |
| v1.7.0 | "On This Day" historical card (28 total), Open-Meteo Archive API, permanent cache | — |
| v1.8.0 | Precipitation nowcast notifications, NowcastAlertWorker, transition classifier | — |
| v1.9.0 | Custom alert rules, threshold CRUD, CustomAlertWorker, adaptive icon | — |
| v1.10.0 | Wear OS overhaul — multi-screen Compose, GPS, tile ANR fix, complication, signing | — |
| v1.11.0 | Weather source adapters — OWM, Pirate Weather, Bright Sky (DWD) fully wired | — |
| v1.12.0 | Health alert system — real barometric pressure, migraine/respiratory/arthritis triggers, HealthAlertWorker | — |
| v1.13.0 | Wear OS DataLayer sync — phone-to-watch push, SyncedWeatherStore, tile/complication prefer synced data | — |
| v1.14.0 | Wear OS alerts/daily/AQI — AlertsScreen, DailyScreen, alert banner, AQI chip, background sync via WidgetRefreshWorker | — |

---

## Research & Strategic Gaps (Auto-Generated Analysis)

*Generated 2026-04-14 via full codebase audit. Categorized by priority.*

---

### HIGH PRIORITY

- **Missing ProGuard rule for Gemini Nano (`com.google.ai.edge.aicore`)**  
  `app/proguard-rules.pro` has no `-keep` rule for the AI Core library used in standard flavor (`app/build.gradle.kts:162`). R8 will strip or obfuscate classes needed at runtime. Standard release builds will crash when invoking `GeminiNanoSummaryEngine`. Fix: add `-keep class com.google.ai.edge.** { *; }` and `-dontwarn com.google.ai.edge.**`.

- **No production crash reporting**  
  Crashlytics was explicitly removed in v1.5.0. Firebase Firestore is already a standard-flavor dependency (`firebase-bom:33.7.0`), so the infrastructure exists — Crashlytics just needs re-adding. Without it, production crashes are invisible. Only `Log.d`/`Log.e` statements exist, which are stripped from release builds by ProGuard. Consider: `firebase-crashlytics` for standard, no-op for freenet.

- **`showYesterdayComparison` setting is not wired to rendering**  
  The toggle exists in `UserPreferences` (line 348) and renders in `SettingsScreen` (line 675), but `MainScreen.kt:654` passes `state.yesterdayHigh` to `CurrentConditionsHeader` unconditionally. The component renders the comparison based on `yesterdayHigh != null`, never checking the boolean preference. The setting has no effect.

- **Environment Canada forecast adapter is stubbed**  
  `WeatherSourceManager` returns `NotImplementedError` for Environment Canada forecast requests (line 71). Only the alert adapter (`EnvironmentCanadaAlertAdapter.kt`) is functional. Canadian users who select Environment Canada as primary source get no forecast data.

- **No API rate-limit awareness**  
  5 background workers fire at 15–60 min intervals. Open-Meteo is permissive but OWM free tier allows only 1,000 calls/day. No response header parsing (`X-RateLimit-Remaining`), no client-side throttle, no backoff on 429 responses. Heavy multi-location usage could silently exhaust quotas.

---

### MEDIUM PRIORITY

- **Zero localization support**  
  No `values-*/strings.xml` locale variants exist. Only 10 `stringResource()`/`getString()` calls across 157 source files — all other user-facing text is hardcoded in Kotlin. Extracting strings retroactively across 28 card types, 17 settings sections, 5 screens, and 4 widget types is a significant effort that compounds with each new feature.

- **Accessibility gaps on Canvas-drawn elements**  
  65 `contentDescription` usages across 27 files is decent, but Canvas-drawn graphics (temperature graph, moon phase arc, sun ephemeris arc, AQI gauge, pressure trend, wind trend, cloud cover bars, visibility scale) lack semantic descriptions for screen readers. `semantics`/`liveRegion` only appear in 5 files. No automated a11y testing in CI.

- **CI/CD does not build Wear OS module**  
  `build.yml` and `release.yml` only build `app/` module (`assembleStandardDebug`, `assembleFreenetDebug`). The `wear/` module is never built or tested in CI. Wear regressions are caught only during local builds.

- **CI release workflow builds debug APKs, not release**  
  `release.yml` (triggered on `v*` tags) runs `assembleStandardDebug` and `assembleFreenetDebug` — not release variants. The `build.yml` release job builds unsigned release APKs (no keystore in CI). Neither workflow produces signed release artifacts suitable for distribution.

- **No static analysis beyond Android Lint**  
  CI runs `lintStandardDebug` only. No Detekt (Kotlin static analysis), no ktlint (formatting), no dependency vulnerability scanning (e.g., Dependabot, OWASP dependency-check). Lint alone misses Kotlin-specific code smells, complexity warnings, and security patterns.

- **Room `exportSchema = false` prevents migration validation**  
  `NimbusDatabase` (line 5) disables schema export. This means Room cannot validate migration correctness at compile time. If a future migration (v2 -> v3) is needed, there's no JSON schema to diff against. Re-enabling `exportSchema = true` and committing `schemas/` to the repo is a low-cost safety net.

- **No certificate pinning on API endpoints**  
  `network_security_config.xml` uses system trust anchors only. Acceptable for public weather APIs, but if OWM/Pirate Weather API keys are user-supplied, a MITM proxy on untrusted networks could intercept them. Consider pinning for endpoints that transmit API keys.

- **Wear OS has no error recovery UI for sync failures**  
  If `WeatherDataListenerService.onDataChanged()` throws (caught and logged), the watch shows stale data with no user-visible indication of sync failure. No "Last synced X min ago" indicator exists on watch screens. The 30-minute TTL silently expires and the watch falls back to direct API, but the user has no visibility into why data might be outdated.

---

### LOW PRIORITY

- **Test coverage gaps in adapters and Wear OS**  
  31 test files (5,535 LOC) cover repos, ViewModels, evaluators, and workers well. Missing: no tests for `OwmAdapters`, `PirateWeatherAdapter`, `BrightSkyAdapters`, `EnvironmentCanadaAlertAdapter`, `MeteoAlarmAdapter`, `JmaAlertAdapter`. No tests for any Wear OS code (tile, complication, sync, screens). No tests for `GeminiNanoSummaryEngine`.

- **OkHttp retry uses `Thread.sleep()` in coroutine context**  
  `NetworkModule` retry interceptor (lines 47–67) uses `Thread.sleep()` for exponential backoff. While this works (OkHttp interceptors run on OkHttp's dispatcher thread, not the coroutine dispatcher), it blocks the OkHttp thread pool slot. Using `delay()` isn't possible in an interceptor, but an alternative is to implement retry at the repository level with coroutine `delay()`.

- **Phone/Wear versionCode divergence**  
  Phone is at versionCode 75, Wear at 53. If distributing as paired APKs via Play Store, this mismatch could confuse the pairing logic. Not an issue if published as independent listings. Consider aligning if Play Store distribution is planned.

- **`WeatherSummaryEngine` duplicates context between template and AI paths**  
  Both `generate()` (template) and `generateWithStyle()` (AI wrapper) construct weather context strings independently. A shared context builder would reduce drift between what the template sees and what the AI prompt contains.

- **No database VACUUM or WAL checkpoint**  
  `NimbusDatabase` uses Room defaults (WAL mode). Long-running devices that accumulate and evict cache entries may grow the WAL file. Not critical at current data volumes (2 tables, <1000 rows typical), but a periodic `PRAGMA wal_checkpoint(TRUNCATE)` in a maintenance worker would be defensive.

- **Freenet flavor cannot sync to Wear OS at all**  
  `WearSyncManager` is a no-op in `freenet`. F-Droid users with a Wear OS watch get zero phone-to-watch sync. The watch falls back to direct API calls, which works but defeats the efficiency gain. Consider a non-GMS sync mechanism (Bluetooth serial, companion device manager) for freenet.

- **No deep link handling for weather alerts**  
  `zeuswatch://` scheme supports location and radar deep links, but notification taps for alerts/nowcast/health/custom don't deep link to the relevant screen — they open `MainActivity` at the default tab. Tapping a migraine alert should navigate to the health detail card.

---

### OPPORTUNITIES (Not Bugs, But High-Impact Features)

- **Wear OS watch face**  
  The watch has tile, complication, and 3 screens — but no custom watch face. A weather-aware watch face (temp on dial, condition background, complication slots) would be the highest-visibility Wear feature.

- **Weather source: MET Norway (Yr.no)**  
  Free, no-key, high-quality forecasts for Northern Europe. Would complement Bright Sky (Germany-focused) and fill the gap for Scandinavian users. Uses LocationForecast 2.0 API with WMO-compatible codes.

- **Weather source: Bureau of Meteorology (Australia)**  
  BOM provides free forecast and warning APIs. Australian users currently rely on Open-Meteo or OWM. A dedicated adapter would provide better severe weather coverage for the Southern Hemisphere.

- **Widget interaction: tap-to-refresh**  
  Widgets currently only refresh on the WorkManager schedule. Adding a tap-to-refresh action (via `GlanceAppWidget` action callback) would let users force-update without opening the app.

- **Notification grouping**  
  4 notification channels can fire independently (alerts, health, nowcast, custom). When multiple triggers fire simultaneously, notifications stack ungrouped. `NotificationCompat.Builder.setGroup()` with a summary notification would clean up the shade.

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
