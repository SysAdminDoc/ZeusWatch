# ZeusWatch Roadmap

**Current Version**: v1.25.0 (phone versionCode 105, wear versionCode 77)
**Architecture**: Kotlin 2.1.0 / Jetpack Compose / Hilt / MVVM / multi-module (phone + wear)
**Flavors**: `standard` (Google Play services, Gemini Nano, Firestore, Wear DataLayer) / `freenet` (F-Droid clean)
**License**: LGPL-3.0

---

## Strategic Compass

1. **No required API keys.** Optional keys are fine.
2. **LGPL-3.0 + F-Droid `freenet` parity.** No proprietary blob in `freenet`.
3. **Multi-source resilience by default.** New data types ship with primary + fallback.
4. **All raw API values are metric.** Conversion happens once in `WeatherFormatter`.
5. **Accessibility-first.** Every new card needs `mergeDescendants` semantics + TalkBack summary.
6. **Dense premium dark UI is the brand.**
7. **No paywalled features.**
8. **Trust but isolate.** Provider quirks stay quarantined behind adapters.

---

## Themes (FY2026)

- **T-WEAR** — Watch dominance (WFF data, Wear OS 6, complications, `freenet` sync)
- **T-SOURCES** — Provider depth (close the gap against Breezy's 50+ sources)
- **T-HEALTH** — Defend health/driving/pet/custom alert differentiators
- **T-I18N** — Localization pipeline and community translations
- **T-PERF** — Cache warm, frame fast, battery flat
- **T-RELIABILITY** — Adversarial audits, test surfaces, dependency runway
- **T-ECOSYSTEM** — ContentProvider, Tasker, Smartspacer, Home Assistant, Android Auto

---

## NOW — Current cycle (target v1.24.x)

### N-9. Widget pure-function test coverage · **T-RELIABILITY**
**Status**: PARTIAL. `WidgetThemeTest` (16 assertions) covers freshness badge + icon mapping. Full Glance widget unit tests require Glance 1.2.0+ (repo is on 1.1.1). Keep adding pure-function tests until Glance upgrade lands.

### N-10. Dependency runway and platform compatibility pass · **T-RELIABILITY** / **T-PERF**
Split into remaining lanes:
- **Lane B**: feature-enabling AndroidX upgrades with dedicated test slices — Room 2.6.1->2.8.4, Glance 1.1.1->1.2.0 (only when stable).
- **Lane C**: architecture-affecting upgrades (Retrofit 3, OkHttp 5, MapLibre 13, Kotlin 2.3+, Gradle 9, AGP 9) only after migration risk/rollback are documented.

---

## NEXT — 2-3 release cycles out (target v1.25 - v1.27)

### NX-1. Meteo-France adapter (forecast + AROME nowcast + vigilance alerts) · **T-SOURCES**
Split: no-key forecast/nowcast first via Open-Meteo, keyed official Vigilance second. Effort: medium.

### NX-2. GeoSphere Austria adapter (INCA nowcast + alerts) · **T-SOURCES**
CC0 license, no key, 15-min/1-km nowcast for Austria + Alps. Effort: medium.

### NX-3. FMI (Finland) adapter first; KNMI optional after key-policy decision · **T-SOURCES**
FMI is no-key, documented WFS 2.0. KNMI requires Authorization key -- defer until NX-20 provides generic optional-key UI. Effort: medium.

### NX-5. Multi-provider agreement card · **T-RELIABILITY**
Fetch 24h temp + precip from 2-3 providers; render agreement/divergence badge. Gate behind opt-in (API quota multiplication). Effort: medium.

### NX-6. Per-location offline-first cache · **T-PERF**
Make cache provider-aware. Add normalized `WeatherDataCacheEntity` with `sourceProvider`, `savedLocationId`, schema version. Serve cached data immediately; refresh in background. Done when location-switch renders <100ms with cached data across all providers. Effort: medium.

### NX-7. Baseline Profiles + Macrobenchmark startup gate · **T-PERF**
Add `:benchmark` module. Local release verification asserts p95 cold start <1200ms. Effort: medium.

### NX-10. Localized condition strings from native services · **T-I18N**
Prefer upstream localized alert/condition text when user locale matches. Effort: low-medium.

### NX-11. Vico chart migration for trend cards · **T-PERF** / UX
Replace custom Canvas trend charts with Vico 3.x. Keep `TemperatureGraph` custom. Removes ~600 lines. Effort: medium.

### NX-13. ContentProvider for ecosystem · **T-ECOSYSTEM**
Gadgetbridge broadcast already shipped (v1.23.0). Remaining: read-only ContentProvider mirroring Breezy's schema for Tasker/KWGT compatibility. Opt-in toggle. Effort: medium.

### NX-14. Reproducible builds badge for F-Droid · **T-RELIABILITY**
Audit locale/timezone hashes, Hilt-generated code stability, AGP lockfile. Effort: medium.

### NX-17. Custom-alert rule expansion (residual) · **T-HEALTH**
Shipped: dew point, feels-like, snowfall, pressure, AQI. Remaining: severe weather event type (needs non-threshold rule structure). Effort: low.

### NX-18. WCAG 2.2 AA audit + dynamic font scaling pass · accessibility
Contrast audit of weather-adaptive palettes, font scaling stress test at 1.3/1.5/1.8, touch target 48dp audit, extend a11y checks to all screens. Effort: medium.

### NX-20. Provider metadata registry + regional auto-suggestion · **T-SOURCES** / **T-RELIABILITY**
Add `ProviderMetadata` registry with `dataTypes`, coverage, `authMode`, attribution, license, quota, `freenetAllowed`, fallback role, cache namespace. Replace Settings hardcoded key checks. Add regional resolver for default bundles. Effort: medium-high.

### NX-21. Native radar compliance, cache, and lifecycle hardening · **T-PERF** / **T-RELIABILITY**
Use `RainViewerResponse.host`; cache frame metadata for offline; add visible RainViewer attribution; wrap MapView lifecycle properly; throttle lightning GeoJSON; add Blitzortung reconnect/backoff. Effort: medium.

---

## LATER — Beyond v1.26

### L-1. `freenet` flavor Wear OS sync via non-GMS path · **T-WEAR**
Default: document that `freenet` Wear users rely on direct API calls (already works). CompanionDeviceManager + sockets only if implementable in <2 weeks.

### L-2. Compose Multiplatform iOS port · **T-ECOSYSTEM**
Deferred until freenet Wear is settled and there is an iOS user audience signal.

### L-3. Provider-agnostic per-card unit override · UX
Show wind in knots on marine card but km/h elsewhere. Gated on i18n completion.

### L-4. Android Auto / Car App Library variant · **T-ECOSYSTEM**
Bare-bones AA module: alerts + radar + driving conditions card. Investigate GMS dependency.

### L-5. Android TV variant · **T-ECOSYSTEM**
Gated on demand signal.

### L-6. Smartspacer target plugin · **T-ECOSYSTEM**
Pixel "At a Glance" without root. Out-of-tree plugin preferred.

### L-7. Home Assistant integration · **T-ECOSYSTEM**
Publish weather entities via ContentProvider (NX-13) or MQTT. Separate module.

### L-8. MapLibre 13.x radar compatibility audit · **T-PERF**
Wait for NX-21 to land. Measure radar tab open time, tile load, memory, crash rate.

### L-9. Marine / Aviation power-user mode · **T-SOURCES**
Storm Glass / METAR/TAF/NOTAM. Gate behind explicit "power-user mode" preference.

### L-10. SPC Conditional Intensity overlay (US tornado/hail/wind) · **T-HEALTH** (safety)
Polygon overlay on radar + push when user enters Day-1 Enhanced+ polygon. US-only.

### L-11. Lottie ProtoLayout on Wear tiles · **T-WEAR**
Animated weather icons on tiles via ProtoLayout Material 3 + Lottie 6.6.2.

### L-12. Open-Meteo Kotlin/FlatBuffer SDK migration · **T-PERF**
~2x faster than JSON for large payloads. Risk: schema drift.

### L-13. Reverse-geocoding without Play Services for `freenet` · **T-RELIABILITY**
Verify Nominatim rate-limit compliance; consider bundled offline GeoNames dataset.

### L-14. Adversarial audit round 5 · **T-RELIABILITY**
Targets: WFF data publisher, Vico chart migrations, freenet Wear path, new adapters. Budget every 6-8 releases.

### L-15. Mutation testing + Compose screenshot tests · **T-RELIABILITY**
Pitest-android for pure-function evaluators. Paparazzi/Roborazzi for golden-image diffs.

### L-16. Calendar-based / alternate numeral support · **T-I18N**
Non-Latin numeral systems + alternate calendars. Gated on core extraction.

---

## UNDER CONSIDERATION

### UC-1. Self-hosted ACRA crash report endpoint
Open question: is email volume a problem yet?

### UC-2. Anonymous usage telemetry (opt-in)
Plausible-style. Open question: can we live with blindness on provider/card usage?

### UC-3. Light theme / weather-adaptive light mode
Open question: opt-in scheduled light theme, or no?

### UC-4. Background-fetch budget controls
Open question: is current battery loss material? Instrument first (depends on UC-2).

### UC-5. AccuWeather adapter via bundled key
Open question: accept brittleness of bundled revokable key?

### UC-6. Pixel Watch / Wear OS 6 M3 Expressive UI refresh
Wait for wear-compose-material3 stable.

### UC-7. ScrollAware widget refresh on home-screen interaction
Open question: needs measurement.

---

## REJECTED

- **Bundled AccuWeather/Apple WeatherKit keys as default fallback.** Violates no-required-key + no-proprietary guardrails.
- **Custom Wear OS runtime watch face.** Deprecated for new installs as of Jan 2026.
- **Built-in ad slots / interstitials.** Antithetical to brand.
- **Subscription tier / Premium Club.** Antithetical to brand.
- **Audio severe-weather TTS.** Leave to TalkBack and system alarm channels.
- **Replace MapLibre with proprietary map SDK.** Locks `freenet`.
- **Firebase as primary state store.** Conflicts with `freenet` parity.
- **Android 16 "Local" weather wallpaper integration.** Pixel-only, no public API.
- **Built-in webcam / live photo feed.** Third-party hosting, copyright, moderation conflicts.
- **Replace Hilt with Koin/Dagger pure.** Hilt works; K2+KSP2 friction already navigated.
- **Move Wear app to Compose Multiplatform.** No upside today.

---

## Provider Expansion Priority

| Rank | Provider | Auth | Priority | Notes |
|---|---|---|---|---|
| 1 | FMI (Finland) | None | P1 | Best no-key regional adapter. Nordic story extension. |
| 2 | DMI (Denmark/Greenland/Faroe) | Free/open, verify key flow | P1 | Forecast EDR. Queue after FMI. |
| 3 | Meteo-France via Open-Meteo + Vigilance | None for forecast; JWT for Vigilance | P1 | Split: no-key first. |
| 4 | HKO (Hong Kong) | None | P1/P2 | Compact regional win. |
| 5 | BMKG (Indonesia) | None for CAP | P2 | Alert adapter first. |
| 6 | KNMI (Netherlands) | API key | P2 | Optional user-key only after NX-20. |
| 7 | CWA (Taiwan) | Free key | P2/P3 | Higher localization effort. |
| 8 | SMHI (Sweden) | None | P2 | Revisit after FMI/DMI. |

---

## Library Watch

| Lib | Current | Target | Notes |
|---|---|---|---|
| Compose BOM | 2025.04.01 | 2026.05.00 | M3 Expressive. N-10 scope. |
| Kotlin | 2.1.0 | 2.3.x stable | KSP/Hilt compat risk. |
| Hilt | 2.53.1 | 2.59.2 | Bump cautiously. |
| Room | 2.6.1 | 2.8.4 | KMP-ready. |
| WorkManager | 2.11.2 | — | Current. |
| MapLibre | 11.5.2 | 13.2.0 | After NX-21. |
| OkHttp | 4.12.0 | 5.3.2 | `redactQueryParameters` in 5.x. |
| Retrofit | 3.0.0 | — | Current. |
| Glance | 1.1.1 | 1.2.0 stable | Enables widget unit tests. |
| Wear Compose M3 | alpha27 | stable | Gates UC-6. |
| Tiles | 1.4.1 | 1.6.0 | Tile regression tests needed. |
| ProtoLayout | 1.2.1 | 1.4.0 | Gates L-11. |
| Vico | not added | 3.0.x | NX-11 dep. |

---

## Research-Driven Additions (2026-06-09)

### P0 — Security-Critical

### P1 — High-Impact Systemic Fixes

### P2 — Platform Opportunities & Quality

### P3 — Polish & Future

- [ ] P3 — Compose shared element transitions for card-to-detail · UX
  Why: Shared element transitions are now stable in Compose 1.10 (BOM 2025.12.00). Tapping a forecast card could animate into a detailed view instead of a hard screen swap.
  Evidence: Jetpack Compose December 2025 release blog (android-developers.googleblog.com).
  Touches: `NimbusNavHost`, `MainScreen` card tap handlers, detail screen composables. Requires Compose BOM upgrade to 2025.12.00+ (part of N-10).
  Acceptance: At least one card type (e.g., hourly → hourly detail) uses shared element animation; transition feels natural at 60fps.
  Complexity: M

- [ ] P3 — WeatherFlow Tempest PWS integration · **T-SOURCES**
  Why: Personal weather stations provide hyper-local data. WeatherFlow Tempest offers a free WebSocket API (`wss://ws.weatherflow.com/swd/data`) for 1-minute real-time observations from personal stations. No FOSS weather app integrates PWS data.
  Evidence: WeatherFlow Tempest WebSocket API documentation; Weather Underground PWS network (250k+ stations).
  Touches: New `TempestWebSocketService`, new `PwsRepository`, optional card type showing nearest-station data. User provides station token in Settings. freenet-compatible (no proprietary deps).
  Acceptance: Users with a Tempest token see real-time 1-minute observations from their station; data complements (doesn't replace) forecast providers.
  Complexity: L

## Research-Driven Additions (2026-06-09, Round 2)

### P2 — Platform Opportunities

[Moved to Roadmap_Blocked.md: Android 17 MetricStyle (needs compileSdk 37), Compose Grid (needs BOM 2026.04.01)]

- [ ] P2 — ProtoLayout Material3 tile upgrade · **T-WEAR**
  Why: ProtoLayout 1.4.0 adds `Material3TileService` with automatic `MaterialScope`, inlined `ImageResource`, ambient mode awareness, and Lottie animation support for weather icons. Direct upgrade for `WeatherTileService` that currently uses the older ProtoLayout API.
  Evidence: Wear ProtoLayout 1.4.0 release notes; M3 Expressive for Wear OS blog post.
  Touches: `wear/.../tile/WeatherTileService.kt`, `wear/.../tile/WeatherTileRequestRunner.kt` — migrate to `Material3TileService` with suspend function returning layout + resources. Add Lottie weather icon support. `wear/build.gradle.kts` (bump protolayout dependency).
  Acceptance: Weather tile renders with M3 Expressive styling; animated weather icons on tiles; ambient mode works correctly; tile refresh time unchanged.
  Complexity: M

[Moved to Roadmap_Blocked.md: Glance widget picker previews (needs Glance 1.2.0 stable)]

- [ ] P2 — RainViewer free tier limitations documentation and Rainbow.ai evaluation · **T-SOURCES**
  Why: RainViewer's commercial API shut down January 2026. The free tiled map service continues but with degraded quality: max zoom 7, Universal Blue color only, past radar only (no nowcast/satellite), PNG only. Rainbow.ai offers 30K free tiles/month with XYZ compatibility, nowcast support, and custom color schemes.
  Evidence: RainViewer API documentation (rainviewer.com/api.html); Rainbow.ai developer docs (developer.rainbow.ai); RainViewer blog post on 2025 API changes.
  Touches: Evaluate Rainbow.ai as supplementary radar source. If adopted: new `RainbowRadarApi` Retrofit interface, `RadarRepository` (add source selection), `RadarScreen` (surface quality indicator), Settings (radar source picker). Keep RainViewer as default free option.
  Acceptance: Document current RainViewer limitations in-app (zoom/color/nowcast). If Rainbow.ai is viable: radar tiles load from Rainbow.ai with nowcast; RainViewer remains as fallback.
  Complexity: M

### P3 — Polish & Future

- [ ] P3 — WCAG 2.2 AA automated local gate · accessibility
  Why: The European Accessibility Act (EAA) took effect June 28, 2025, making WCAG 2.2 AA legally required in the EU. ZeusWatch already has some accessibility support (TalkBack descriptions, `AccessibilityHelper`, Canvas semantics) but lacks automated local release enforcement. Compose UI tests with `compose-ui-test-junit4-accessibility` are partially enabled but not gating.
  Evidence: European Accessibility Act enforcement date; WCAG 2.2 criteria (1.4.11 non-text contrast 3:1, 2.5.8 touch target 24px minimum); `compose-ui-test-junit4-accessibility` already in dependencies.
  Touches: local release verification command set, existing `androidTest` files (expand coverage to all screens), new `AccessibilityAuditTest.kt` for contrast and touch target checks.
  Acceptance: Local accessibility verification fails on new WCAG 2.2 AA violations; all screens pass contrast and touch target checks; existing tests continue to pass.
  Complexity: M

- [ ] P3 — Open-Meteo FlatBuffer SDK for large payload performance · **T-PERF**
  Why: Open-Meteo's FlatBuffer format avoids JSON parsing overhead for large payloads (historical data, hourly arrays). The SDK (`com.open-meteo:sdk`) is at v1.26.0 on Maven Central. The high-level Kotlin wrapper library is stalled, but the generated FlatBuffer classes work directly with `&format=flatbuffers`.
  Evidence: Open-Meteo SDK releases (github.com/open-meteo/sdk); `open-meteo-api-kotlin` stalled at v0.1.0 due to KMP FlatBuffers limitation.
  Touches: `app/.../data/api/OpenMeteoApi.kt` (add `format=flatbuffers` variant), new `OpenMeteoFlatBufferAdapter` (decode FlatBuffer responses), `app/build.gradle.kts` (add `com.open-meteo:sdk` dependency). Opt-in behind a setting initially.
  Acceptance: Historical weather (On This Day) and large hourly arrays decode faster than JSON; API response size reduced; existing JSON path unaffected.
  Complexity: L

- [ ] P3 — Smartspacer target plugin for At a Glance · **T-ECOSYSTEM**
  Why: Smartspacer (9.7k GitHub stars) replaces Pixel's At a Glance widget without root. It has an SDK for third-party plugins. ZeusWatch could provide a weather target (current conditions + next-hour precipitation) and complication (temperature). Smartspacer is actively maintained for Android 16+.
  Evidence: Smartspacer GitHub (github.com/KieronQuinn/Smartspacer); Smartspacer SDK documentation.
  Touches: New out-of-tree module or in-tree `smartspacer/` module with Smartspacer SDK dependency. Implements `SmartspacerTargetProvider` and `SmartspacerComplicationProvider`. Reads from `WeatherRepository` cache.
  Acceptance: ZeusWatch appears as a Smartspacer target/complication provider; shows current temp, condition, and next-hour precipitation on the home screen.
  Complexity: M


## Research-Driven Additions (2026-06-09, Round 3)

### P2 — Platform Upgrades & Opportunities

[Moved to Roadmap_Blocked.md: Compose BOM 2026.04.01 upgrade (needs dedicated N-10 Lane B pass)]



### P3 — Incremental Improvements


## Research-Driven Additions (2026-06-10, Round 4)

### P1 — Platform Deadline & Distribution

### P2 — Distribution & Quality

[Moved to Roadmap_Blocked.md: IzzyOnDroid submission (requires human-coordinated request)]

### P3 — Future / Evaluation

- [ ] P3 — Migrate Gemini Nano off experimental AICore SDK · **T-RELIABILITY**
  Why: `com.google.ai.edge.aicore:aicore:0.0.1-exp02` is an experimental artifact Google labels "not for production usage", effectively Pixel-9-class only. The supported successor is ML Kit GenAI (`com.google.mlkit:genai-summarization:1.0.0-beta1`, API 26+, broader OEM support) — but it is summarization-shaped (article/conversation input, 400+ chars, EN/JA/KO) while `WeatherSummaryEngine` uses free-form prompting; the general Prompt API is still AICore dev-preview. Template NLG is already primary, so this is an evaluation, not an urgent swap.
  Evidence: developer.android.com/ai/gemini-nano (experimental access "not for production"); developers.google.com/ml-kit/genai/summarization/android; `app/build.gradle.kts:188`.
  Touches: `app/src/standard/.../GeminiNanoSummaryEngine.kt`, `app/build.gradle.kts` (standardImplementation), ProGuard keep rules for `com.google.ai.edge.**`.
  Acceptance: Decision documented (migrate to ML Kit GenAI / wait for Prompt API GA / drop AI fallback); if migrated, AI summaries work on at least one non-Pixel device; freenet flavor unaffected.
  Complexity: M

## Product Improvement Ideas (2026-06-11)

Net-new improvement opportunities identified during the v1.23.0 audit — distinct from the audit residuals below and from existing NOW/NEXT/LATER items. Closest existing items are cross-referenced instead of duplicated (per-location sources = P1 2026-06-09; light theme = UC-3; localization = N-1; battery instrumentation = UC-4; baseline profiles = NX-7).

## Audit Round 5 Residuals (2026-06-11)

Items found during the v1.23.0 deep audit that need design decisions or larger work. ~70 sibling findings were fixed and shipped in v1.23.0.






## Research-Driven Additions

### P1


### P2


- [ ] P2 - Route-aware driving weather planner
  - Why: Point-location driving conditions miss the competitive route-weather use case surfaced by MyRadar RouteCast, CARROT CarPlay, and Android Auto user expectations.
  - Evidence: `DrivingConditionEvaluator.kt` currently evaluates local/current conditions only; commercial apps now present hazards along a route; existing Android Auto roadmap work can later reuse a phone-first route model.
  - Touches: `app/src/main/java/com/sysadmindoc/nimbus/data/weather/DrivingConditionEvaluator.kt`, `app/src/main/java/com/sysadmindoc/nimbus/data/weather/WeatherRepository.kt`, `app/src/main/java/com/sysadmindoc/nimbus/ui/screen/radar/RadarScreen.kt`, `app/src/main/java/com/sysadmindoc/nimbus/ui/screen/main/MainViewModel.kt`.
  - Acceptance: User can enter or share a route with departure time, see waypoint/time-indexed precipitation, wind, visibility, ice, and alert risk, and use the feature without background tracking.
  - Complexity: L

### P3


## Research-Driven Additions (2026-06-13)

### P1 — Trust & Platform Compliance

### P2 — Product Differentiation

- [ ] P2 — RTL layout support · **T-I18N** / accessibility
  Why: Arabic, Hebrew, Persian, and Urdu (820M+ speakers combined) are among the most-requested FOSS locales. Weather UIs have unique RTL challenges: left-anchored temperature graphs, LTR timeline scrubbers, hour-label progression, wind compass orientation, and radar playback controls all assume LTR. Without explicit RTL support, adding Arabic/Hebrew translations produces a broken layout.
  Evidence: Breezy Weather community translation requests (Arabic is top-3 requested); European Accessibility Act enforcement (EAA, June 2025) includes locale-appropriate presentation; Compose `LayoutDirection` support is built-in but requires explicit handling for Canvas components.
  Touches: All Canvas-drawn components in `ui/component/` (TemperatureGraph, PressureTrendCard, WindTrendCard, PrecipitationChartCard, CloudCoverCard, VisibilityCard, HourlyForecastStrip, SunMoonArc), `ui/screen/radar/RadarScreen.kt` (playback controls), `ui/screen/main/MainScreen.kt` (LazyColumn card ordering). Add `values-ar/strings.xml` and `values-he/strings.xml` as first RTL locales.
  Acceptance: All screens render correctly in forced-RTL mode (developer options); Canvas charts mirror axis/label orientation; no text clipping or overlap; at least one RTL locale (Arabic) has translated strings.
  Complexity: L


[Moved to Roadmap_Blocked.md: Wear OS 7 compatibility pass (needs Wear OS 7 SDK availability)]


### P3 — Future & Polish

- [ ] P3 — IMD (India) forecast and alert adapter · **T-SOURCES**
  Why: India (1.4B population) is absent from ZeusWatch's Provider Expansion Priority table. IMD offers a REST/JSON API (`api.imd.gov.in`) with 14 endpoints covering city/district/state 7-day forecasts, marine bulletins, and river basin precipitation. Requires free API key registration.
  Evidence: api.imd.gov.in documentation; IMD open data initiative (2024).
  Touches: New `ImdApi` Retrofit interface + `ImdForecastAdapter` + `@Named("imd")` Retrofit in `NetworkModule`. Add IMD to `WeatherSourceManager` dispatch and Provider Expansion table. API key field in Settings (conditional display like OWM/PW).
  Acceptance: Users with an IMD key see Indian-region forecasts from IMD; alerts surface via IMD's warning system; freenet flavor unaffected.
  Complexity: M

## Code Audit Findings (2026-06-14)

Three parallel code audits (health/architecture, performance/Compose, testing/reliability/security) read the tree at v1.23.0 (versionCode 101). Each finding below was **re-verified against the current code** after the v1.21.x audit pass — items already fixed in v1.23.0 are noted as such and the residual is scoped to what remains. The codebase is unusually disciplined (clean MVVM, shared `WeatherCard` abstraction, truly-lazy `LazyColumn` with stable keys + `contentType`, `@Stable MainUiState` with `ImmutableList` fields, cert pinning populated, 47 unit-test files, zero TODO/FIXME). These are the residual gaps.

### P2 — Performance & Maintainability


- [ ] P2 — `WeatherSourceManager`: replace parallel `when(provider)` dispatch with a Hilt `@IntoMap` adapter registry · maintainability · extends NX-20
  Status: STILL APPLIES.
  Why: `WeatherSourceManager.kt:26-42` injects 14 adapters (`@Suppress("LongParameterList")`) and dispatches via four parallel `when(provider)` blocks (`getForecastFrom:87`, `getAlertsFrom:149`, `getAirQualityFrom:192`, `getMinutelyFrom:214`). Adding a provider means editing the enum, the constructor, and up to four `when`s. Dovetails with NX-20's provider-metadata registry.
  Evidence: `WeatherSourceManager.kt:26-42` (fan-in), `:87/:149/:192/:214` (parallel dispatch).
  Touches: `app/.../data/repository/WeatherSourceManager.kt` — define a capability-bearing `WeatherSourceAdapter` interface, bind adapters with Hilt `@IntoMap`/`@Provider`, inject `Map<Provider, Adapter>`, loop instead of switch. Removes the constructor fan-in.
  Acceptance: adding a provider is one `@IntoMap` binding + metadata, no `when` edits; existing `WeatherSourceManagerTest` routing/fallback cases pass.
  Complexity: M

- [ ] P2 — Decompose `MainViewModel` orchestration · maintainability
  Status: PARTIAL — tile math moved to `RadarRepository.buildPreviewTileUrls()` with tests. Remaining: extract `WeatherLoadCoordinator` for sub-fetch/derived-data orchestration.
  Touches: `app/.../ui/screen/main/MainViewModel.kt` — extract orchestration classes so VM holds only UI state + intent handling.
  Complexity: L

### P3 — Polish


> Cross-references (already tracked, reinforced by this audit): **Baseline Profile / Macrobenchmark startup gate = NX-7** (still unstarted — no `:baselineprofile` module, plugin not applied; recommend pulling forward, it is the single biggest cold-start win for a card-heavy Compose app). Provider-metadata registry = **NX-20** (the `@IntoMap` P2 above is its natural first step). Provider-aware cache = **NX-6** (prereq for the stale-cache P1). Already-fixed since the v1.21.x audit and intentionally **not** re-listed: Firestore world-delete vector, alert-worker total-failure retry, and the `WeatherParticles` lifecycle `derivedStateOf` bug (now uses `currentStateAsState()`).

## Research-Driven Additions

### P2 — Safety UX And Forecast Trust

- [ ] P2 — Add a plain-language forecast-uncertainty explanation layer
  Status: PARTIAL — TemperatureGraph now shows a compact forecast-spread legend when ensemble bands are present and includes the same average-spread explanation in TalkBack semantics. Remaining: extend the explanation into forecast detail sheets and add screenshot/accessibility coverage.
  Why: Confidence bands and accuracy badges are valuable trust signals, but users need to know whether a wide band means "less certain" and what action to take. Without legend text and accessibility summaries, uncertainty data is easy to misread.
  Evidence: `TemperatureGraph.kt:70` accepts `ConfidenceBandData`; `TemperatureGraph.kt:311-355` draws the band; `strings.xml:484-485` only says "Confidence bands" and "Show ensemble spread on the temperature graph"; WMO forecast-uncertainty guidance emphasizes clear service-provider communication.
  Touches: `TemperatureGraph.kt`, forecast detail sheets, daily/hourly forecast UI, settings strings, TalkBack semantics, screenshot/accessibility tests.
  Acceptance: Temperature graph shows a compact legend and low/medium/high spread summary when bands are enabled; forecast details explain p10/p90 or equivalent in plain language; TalkBack announces uncertainty meaning without reading raw chart internals; screenshots show no chart clutter on narrow screens.
  Complexity: M

### P3 — Planning Hygiene


## Research-Driven Additions

### P1 - Reliability and Hardening

### P3 - Operational Maturity

## Research-Driven Additions

### P1 — Trust, Safety, And Release Integrity

### P2 — Forecast Trust And Source Depth

- [ ] P2 — Provider health history and diagnostics export
  Why: Source fallback is a core trust feature, but users cannot inspect last success, last failure, cache age, quota/rate-limit failures, or a support-ready source-health snapshot.
  Evidence: `AlertFetchResult.failedSources` is transient; `WeatherSourceManager.kt` logs primary/fallback failures; Settings has provider selectors but no durable source-health panel; competitor/community issues repeatedly cite stale or wrong provider data.
  Touches: `WeatherSourceManager.kt`, `AlertRepository.kt`, `WeatherRepository.kt`, `UserPreferences.kt` or Room diagnostics table, Settings advanced/data-source section, settings export/support text.
  Acceptance: Settings shows per-provider last success, last failure reason class, last cache age, and active fallback state without telemetry; users can export a redacted diagnostics bundle for support.
  Complexity: M

- [ ] P2 — DMI forecast adapter through the provider registry
  Why: DMI covers Denmark, Greenland, Faroe, and nearby Nordic weather with high-resolution HARMONIE/AROME data; use Open-Meteo's no-key DMI endpoint first and keep official DMI EDR behind the provider metadata/auth decision.
  Evidence: Provider priority table lists DMI but no actionable item; DMI Forecast Data docs; Open-Meteo DMI API documents 2 km updates every 3 hours and up to 15-day combined forecasts.
  Touches: provider metadata registry item NX-20, `WeatherSourceProvider`, `WeatherSourceManager.kt`, `NetworkModule.kt`, Open-Meteo/DMI adapter tests, Settings labels, attribution strings.
  Acceptance: Users in DMI coverage can select DMI forecasts via the no-key Open-Meteo DMI path; the adapter routes through the metadata registry; fallback and attribution work; tests cover coordinates inside/outside coverage and metric-unit mapping.
  Complexity: M

- [ ] P2 — Widget/app freshness parity contract for all eight widgets
  Why: Widgets are a major distribution surface and stale-widget mismatch is a visible competitor pain; ZeusWatch now has eight widget receivers while docs/tests still reflect the older four-widget mental model in places.
  Evidence: `AndroidManifest.xml` registers eight `Nimbus*WidgetReceiver` classes; `WidgetRefreshWorker.kt` updates all eight; README widget table/source tree still list four; WeatherMaster issues #792 and #876 report app/widget data divergence.
  Touches: `WidgetRefreshWorker.kt`, `WidgetDataProvider`, all `Nimbus*Widget.kt` classes, widget tests, README widget docs, docs consistency gate.
  Acceptance: A single contract test verifies every widget receiver reads the same source, observation time, cache age, and manual-refresh result as the app for a fake forecast; README/docs list all eight widget surfaces.
  Complexity: M

## Research-Driven Additions (2026-06-25)

### P1 — Security & Trust

[P1 items completed and removed — see git history]

### P2 — Product Differentiation & Coverage

- [ ] P2 — Evaluate LibreWXR as FOSS radar source · **T-SOURCES** / **T-RELIABILITY**
  Why: RainViewer's free tier degraded significantly in January 2026 (max zoom 7, single color, no nowcast/satellite, PNG only). LibreWXR is a new 2026 FOSS project providing a RainViewer v2 API-compatible drop-in with real radar composites from 7 national services (MRMS/US, MSC/Canada, OPERA/24 EU countries, JMA/Japan, CWA/Taiwan, MET Malaysia) plus ECMWF IFS global fallback and WMO alerts. Extends the existing P2 RainViewer/Rainbow.ai evaluation with a FOSS-native option.
  Evidence: LibreWXR GitHub documentation; RainViewer API changelog (Jan 2026 free-tier restrictions); existing P2 RainViewer+Rainbow.ai roadmap item covers commercial alternatives but not FOSS ones.
  Touches: `RadarRepository.kt` (add source selection), `RadarScreen.kt` (surface quality/source indicator), Settings radar section (source picker if viable). If API-compatible: minimal code — URL swap. If not: new `LibreWxrRadarApi` Retrofit interface.
  Acceptance: Evaluation documents LibreWXR's uptime, hosting model, coverage quality, and API compatibility. If viable: radar tiles load from LibreWXR with higher zoom/color fidelity than current RainViewer free tier; RainViewer remains as fallback.
  Complexity: M






- [ ] P2 — Color-blind-safe palette verification · accessibility · extends NX-18
  Why: Yr (Norwegian Met Office app) demonstrates that color-blind-safe weather palettes build user trust. ZeusWatch uses color as a primary information channel in AQI gauge, UV index bar, pollen bars, precipitation intensity, alert severity banners, driving condition severity, dew point comfort, and temperature graph. None have been verified for deuteranopia (red-green, 8% of males) or protanopia. European Accessibility Act enforcement (June 2025) includes color-as-sole-channel as a compliance requirement.
  Evidence: Yr accessibility design documentation; WCAG 2.2 criterion 1.4.1 (Use of Color); EAA enforcement date June 28, 2025; EN 301 549 requirements; 8% male deuteranopia prevalence.
  Touches: All color-coded components in `ui/component/` — `AqiGauge.kt`, `UvIndexBar.kt`, `PollenCard.kt`, `PrecipitationChartCard.kt`, `AlertBanner.kt`, `DrivingAlertCard.kt`, `HealthAlertCard.kt`, `DewPointComfort` section in `WeatherDetailsGrid.kt`, `TemperatureGraph.kt`. Add secondary visual channel (pattern, icon, text label) where color is currently sole differentiator.
  Acceptance: All color-coded elements pass deuteranopia and protanopia simulation (Android developer options → Simulate color space); no information is conveyed by color alone; existing visual design remains cohesive for normal-vision users.
  Complexity: M

- [ ] P2 — HKO (Hong Kong) forecast and alert adapter · **T-SOURCES**
  Why: HKO offers 48 datasets via clean JSON REST at `data.weather.gov.hk/weatherAPI/` — no key, no registration, no XML parsing. Coverage includes 9-day forecast, rainfall nowcast, UV index, tropical cyclone tracking, and earthquake data. Free for commercial and non-commercial use. Compact regional win for 7.5M residents + travellers.
  Evidence: HKO Open Data API documentation (`data.weather.gov.hk`); Provider Expansion Priority table (rank 4, P1/P2); no existing roadmap item with implementation detail.
  Touches: New `HkoApi.kt` Retrofit interface + `HkoForecastAdapter.kt` + response models. `WeatherSourceManager.kt` dispatch (or `@IntoMap` if NX-20 lands first). `NetworkModule.kt` (`@Named("hko")` Retrofit). Settings labels and attribution. freenet-compatible (no proprietary deps).
  Acceptance: Users can select HKO as forecast source for Hong Kong locations; current conditions, 9-day forecast, and alerts surface correctly; fallback to Open-Meteo works; tests cover JSON parsing and metric-unit mapping.
  Complexity: M

### P3 — Polish & Future


- [ ] P3 — BMKG (Indonesia) CAP alert adapter · **T-SOURCES**
  Why: Indonesia (270M+ population) is the 4th most populous country with no weather alert coverage in ZeusWatch. BMKG publishes CAP XML alerts for all provinces down to district level — no API key required. ZeusWatch's alert stack already parses CAP XML for ECCC and NWS; adding BMKG is primarily a new `AlertSourceAdapter` implementation.
  Evidence: BMKG CAP data at `data.bmkg.go.id` and GitHub `infoBMKG/data-cap`; Provider Expansion Priority table (rank 5, P2); existing `AlertSourceAdapter` interface and CAP XML parsing in `EnvironmentCanadaAlertAdapter`.
  Touches: New `BmkgAlertAdapter.kt` implementing `AlertSourceAdapter`, new `BmkgAlertApi.kt` Retrofit interface (CAP XML endpoint), `AlertRepository.kt` (add BMKG to country-code dispatch), `NetworkModule.kt` (`@Named("bmkg")` Retrofit).
  Acceptance: Users in Indonesia see BMKG severe weather alerts; alerts display with correct severity mapping; auto-detect via country code works; freenet flavor unaffected; tests cover CAP XML parsing for BMKG's specific element structure.
  Complexity: M


- [ ] P3 — Stacked multi-source forecast overlay on Compare screen · UX / **T-SOURCES**
  Why: Windy and Flowx demonstrate that overlaying temperature or precipitation lines from multiple sources on a single chart reveals forecast agreement and divergence at a glance. ZeusWatch's Compare screen shows side-by-side values but no graphical overlay. NX-5 (multi-provider agreement card) tracks a badge/indicator; this is the complementary visual.
  Evidence: Windy Compare Mode; Flowx stacked model graphs; Ventusky dual-axis overlays; existing `CompareScreen.kt` with side-by-side layout; existing `TemperatureGraph.kt` Canvas rendering.
  Touches: `CompareScreen.kt` (add chart overlay mode), new `MultiSourceChart.kt` composable (extend `TemperatureGraph` Canvas logic to plot 2-3 source lines with distinct colors), `CompareViewModel.kt` (fetch hourly data from selected sources in parallel).
  Acceptance: Compare screen has a toggle for "chart overlay" that plots temperature (and optionally precipitation) from 2-3 selected sources on one graph with a legend; sources use distinct colors; TalkBack announces source names and agreement/divergence summary.
  Complexity: L

## Research-Driven Additions

### P1

### P2

- [ ] P2 — Expand source-of-truth drift gates for user-visible inventory and dependency claims
  Why: User-facing and agent-facing claims have drifted from code: onboarding still says 29 cards while `CardType` has 35, README radar/provider/dependency text is stale in places, and CLAUDE.md still reports v1.24.2.
  Evidence: `RESEARCH.md`; `app/src/main/res/values/strings.xml`; `app/src/main/res/values-es/strings.xml`; `README.md`; `CLAUDE.md`; `tools/check_docs_consistency.py`.
  Touches: `tools/check_docs_consistency.py`, onboarding strings, README, CLAUDE.md, `gradle/libs.versions.toml`, `CardConfig.kt`, `UserPreferences.kt` `RadarProvider`.
  Acceptance: The docs checker fails when onboarding card counts, radar provider inventory, dependency versions, or root version claims drift from source-of-truth code/manifests; current stale claims are corrected.
  Complexity: M
