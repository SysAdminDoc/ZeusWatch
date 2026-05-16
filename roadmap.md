# ZeusWatch Roadmap

**Current Version**: v1.20.3 (phone versionCode 86, wear versionCode 62)
**Architecture**: Kotlin 2.1.0 / Jetpack Compose / Hilt / MVVM / multi-module (phone + wear)
**Flavors**: `standard` (Google Play services, Gemini Nano, Firestore, Wear DataLayer) / `freenet` (F-Droid clean)
**License**: LGPL-3.0
**Last refreshed**: 2026-05-16 — full repo + ecosystem audit pass.

> This document is the working plan. It is dense by design. Every claim in the prose maps to a source in the Appendix. Items are organized by horizon (Now / Next / Later) and by theme. Closed items move to [ROADMAP-COMPLETED.md](ROADMAP-COMPLETED.md).

---

## Completed Milestones

Moved to [ROADMAP-COMPLETED.md](ROADMAP-COMPLETED.md). High-water marks for context: 7 forecast/alert providers wired, 28 reorderable cards, native MapLibre radar with Blitzortung lightning, Wear OS DataLayer sync with on-device fallback, ACRA crash reporting, multi-source resilience, full Glance widget set, accessibility semantics on every Canvas-heavy card, Detekt baseline + CI emulator a11y, 180+ unit tests.

---

## Strategic Compass — Guardrails For Every Decision

These predate the roadmap and constrain what gets accepted in.

1. **No required API keys.** Optional keys are fine. Anything that bricks first-launch without registration is rejected.
2. **LGPL-3.0 + F-Droid `freenet` parity.** No proprietary blob in `freenet`. New deps must compile in both flavors or be flavor-split.
3. **Multi-source resilience by default.** New data types ship with a primary + fallback path through `WeatherSourceManager`.
4. **All raw API values are metric.** Conversion happens once in `WeatherFormatter`. New adapters MUST normalize at the edge.
5. **Accessibility-first.** Every new card needs `mergeDescendants` semantics + a TalkBack-readable summary. No silent Canvas.
6. **Dense premium dark UI is the brand.** Calm, opt-out backgrounds are fine. Cartoon themes and ad-driven layouts are not.
7. **No paywalled features.** ZeusWatch is the FOSS answer to Carrot/AccuWeather/Weather.com — every cell in the matrix stays free.
8. **Trust but isolate.** Add a new source only if its quirks are quarantined behind an adapter; never let provider-specific bugs leak.

Items that break a guardrail must call it out and argue the exception, not paper over it.

---

## Themes (FY2026)

Every item below maps to at least one theme. Themes are the lens for prioritization.

- **T-WEAR — Watch dominance.** Zero FOSS competitor has a native Wear OS story. We do. We extend the moat with WFF data, Wear OS 6 M3 Expressive, complications, and `freenet` sync. Sources: [Breezy ecosystem (no native Wear)](https://github.com/breezy-weather/breezy-weather), [Wear OS 6 release notes](https://android-developers.googleblog.com/2025/05/whats-new-in-wear-os-6.html), [WFF weather data fields](https://developer.android.com/training/wearables/wff/weather).
- **T-SOURCES — Provider depth.** Breezy ships 50+ sources; we ship 7. Closing the gap on regional coverage (BOM, Météo-France, GeoSphere, FMI, KNMI, AEMET) is the single biggest user-visible parity gap. Source: [Breezy SOURCES.md](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md).
- **T-HEALTH — Defend the blue-ocean differentiators.** Migraine/pressure/arthritis/driving/pet are uncontested. Expand them, harden them, ship them by default. Already validated in [v1.12.0 health alert system](ROADMAP-COMPLETED.md).
- **T-I18N — Make it speak everyone's language.** The localization extraction is in flight (single `values-es` locale). It is the longest open thread in the codebase. Source: [Unreleased CHANGELOG entries](CHANGELOG.md).
- **T-PERF — Cache warm, frame fast, battery flat.** Baseline Profiles + offline-first per-location cache + smarter background cadence. Battery drain is the #1 user complaint against weather apps. Source: [unstar.app 2026 complaint ranking](https://unstar.app/blog/weather-apps-ranked-by-user-complaints-2026).
- **T-RELIABILITY — Adversarial audits keep working.** v1.20.1/2 found 11 latent bugs across timezones, sync, modulo, dispatch. Schedule the next round; expand test surfaces (Wear, AI engine, WFF).
- **T-ECOSYSTEM — Be a citizen, not an island.** ContentProvider for other apps, Tasker intents, Smartspacer target, Home Assistant integration, Android Auto. Breezy added a [ContentProvider in v6.1.0](https://github.com/breezy-weather/breezy-weather/blob/main/CHANGELOG.md) — table-stakes signal.

---

## NOW — Current cycle (target v1.21.x – v1.22.x)

In-flight or top of the queue. Each item already has enough scope context that an engineer can pick it up cold.

### N-1. Finish the localization extraction and add a translation pipeline · **T-I18N**
**Status**: PARTIAL (CHANGELOG `[Unreleased]` lists 24+ localized surfaces; `values-es` seeded).
**Why it stays Now**: Hardcoded strings compound with every new feature. Closing extraction once is cheaper than retrofitting after every release.
**Scope**: Sweep remaining Today card internals + dialogs. Add a `values/strings.xml` lint gate in CI. Wire **Weblate** (FOSS-friendly, used by Breezy) for community translation; budget 4–6 weeks for initial pulls. Configure Crowdin Android SDK only if OTA translation delivery is desired (adds JitPack dep). Sources: [Translation_and_Localization on F-Droid](https://f-droid.org/docs/Translation_and_Localization/), [Crowdin mobile SDK](https://github.com/crowdin/mobile-sdk-android).
**Done when**: zero `getString`-with-string-literal violations in main module; ≥3 community locales merged; CI lint blocks hardcoded user-facing text.

### N-2. Populate `ApiCertificatePins.hostPins` · **T-RELIABILITY**
**Status**: PARTIAL (scaffolding shipped v1.17.0, `hostPins` empty, `tools/capture_api_pins.sh` exists).
**Scope**: Run `tools/capture_api_pins.sh` against `api.openweathermap.org`, `api.pirateweather.net`, `air-quality-api.open-meteo.com` (keyed endpoints only) once a release. Two pins per host (leaf + intermediate); the [two-pin invariant test](app/src/test/java/com/sysadmindoc/nimbus/data/api/ApiCertificatePinsTest.kt) already exists. Add a PowerShell variant for Windows CI agents. Out of scope: pinning Open-Meteo/NWS/Bright Sky/ECCC/MeteoAlarm/JMA/MET Norway (keyless, low value).
**Done when**: pins exist for every keyed endpoint; pin-update procedure documented in `docs/RELEASE.md`.

### N-3. Bureau of Meteorology (Australia) forecast adapter · **T-SOURCES**
**Status**: not started. Existing roadmap flags **legal risk** with the undocumented `api.weather.bom.gov.au`.
**Scope**: Two paths. (a) **Indirect**: continue routing AU users through Open-Meteo's [BOM ACCESS-G model proxy](https://open-meteo.com/en/docs/bom-api) (BOM model, Open-Meteo terms — safest licensing-wise). (b) **Direct**: implement a `BomForecastAdapter` against the reverse-engineered API but mark it experimental/off-by-default with an in-app disclaimer pointing at [BOM's catalogue](https://www.bom.gov.au/catalogue/data-feeds.shtml). Prefer (a). Add an "Indirect (Open-Meteo + BOM model)" label so AU users can see the chain.
**Done when**: AU users have a non-Open-Meteo-default option that doesn't violate BOM terms; severe weather warnings for AU still route through MeteoAlarm-style aggregator if available, else the existing Open-Meteo path.

### N-4. Watch face complication suite (extend, don't build a face) · **T-WEAR**
**Status**: `WeatherComplicationService` declared in [wear AndroidManifest.xml](wear/src/main/AndroidManifest.xml) with `SHORT_TEXT,LONG_TEXT,RANGED_VALUE` types. Verify each type renders today's data correctly and add `SMALL_IMAGE` (weather icon) + `LONG_TEXT` (condition + H/L).
**Why now, not "build a watch face"**: Wear OS 6+ requires the [declarative Watch Face Format](https://developer.android.com/training/wearables/wff) for new installs (as of Jan 2026 per [WFF release notes](https://developer.android.com/training/wearables/wff/release-notes)); building a custom runtime face is now blocked. Complications are how third-party data reaches user-chosen faces.
**Scope**: Add the two missing complication types, write tests for each, document install. Tile preview drawable already exists.

### N-5. WFF `[WEATHER.*]` data provider · **T-WEAR**
**Status**: not started. WFF 2+ exposes [`[WEATHER.TEMPERATURE]`, `[WEATHER.CONDITION]`, `[WEATHER.HOURS.N.*]`, `[WEATHER.DAYS.N.*]`](https://developer.android.com/training/wearables/wff/weather) but only if the system has a weather provider registered. The system pulls from "the connected handheld device" first.
**Scope**: Investigate whether the standard-flavor phone-to-watch DataLayer push (already shipped v1.13.0) needs to also publish into the system weather provider for WFF consumers. If yes, implement and add a settings toggle. This is the closest thing to "ZeusWatch on every Wear watch face" without writing one.
**Risk**: API surface may be Pixel-only; document compatibility matrix.

### N-6. Test coverage: Wear OS code path · **T-RELIABILITY**
**Status**: **PARTIAL** — wear test infra bootstrapped, 18 assertions across `WearWeatherRepository.wmoDescription/wmoEmoji` (10) and `SyncedWeatherStore` (8). Remaining: `WeatherTileService` (CallbackToFutureAdapter happy path), `WeatherComplicationService` (data → ComplicationData per type), `WearWeatherRepository.getCurrentWeather` with mocked OkHttp.
**Scope**: Continue with the remaining services. The infra in [wear/src/test/java/com/sysadmindoc/nimbus/wear/testing/FakeSharedPreferences.kt](wear/src/test/java/com/sysadmindoc/nimbus/wear/testing/FakeSharedPreferences.kt) is reusable for any prefs-backed service.
**Done when**: ≥30 wear unit assertions; CI fails on a deleted Wear path.

### N-7. `GeminiNanoSummaryEngine` test coverage · **T-RELIABILITY**
**Status**: zero tests today (`WeatherSummaryEngine` has 38 from v1.20.0; the AI delegate path is covered for `null`/exception fallbacks but not for live happy-path serialization).
**Scope**: Mock the AI Core entry point; assert prompt construction includes location-local time, current condition, daily H/L, wind/UV/humidity context. Test the model-unavailable, model-timeout, and model-returns-empty branches. Falls under standard-flavor only — add `androidTest` if device emulator is needed; otherwise pure unit with mocked `GenerativeModel`.

### N-8. Detekt baseline reduction · **T-RELIABILITY**
**Status**: 22 baseline findings (LongMethod + CyclomaticComplexMethod, mostly in Compose screens). Tracked in `config/detekt/baseline.xml`.
**Scope**: Chip away during normal feature work. Target: baseline empty by v1.25.0. Extract Compose helpers, not refactors-for-the-sake-of-refactor.

### N-9. Compose `runGlanceAppWidgetUnitTest` coverage · **T-RELIABILITY**
**Status**: Glance 1.1.0+ ships `runGlanceAppWidgetUnitTest` for testing individual composable widget functions. Source: [Glance release notes](https://developer.android.com/jetpack/androidx/releases/glance).
**Scope**: One unit test per widget (Small/Medium/Large/ForecastStrip) verifying rendering against a stub `WeatherData`. Catches widget-side regressions that today only surface visually.

---

## NEXT — 2–3 release cycles out (target v1.23 – v1.26)

Sequenced after Now items land. Each entry is concrete enough to scope; none of them require research that hasn't already happened.

### NX-1. Météo-France adapter (forecast + AROME nowcast + vigilance alerts) · **T-SOURCES**
Free API key registration on [portail-api.meteofrance.fr](https://portail-api.meteofrance.fr). Adds: 14-day forecast, PIAF 5-min nowcast for France (180-min look-ahead), Vigilance severe-alert color codes for FR. Source: [Météo-France API doc](https://open-meteo.com/en/docs/meteofrance-api), [Breezy MF entry](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md). Effort: medium (JWT token, two-step fetch).

### NX-2. GeoSphere Austria adapter (INCA nowcast + alerts) · **T-SOURCES**
[CC0 license](https://data.hub.geosphere.at/dataset/nowcast-v1-15min-1km), no key, 15-min/1-km nowcast for Austria + nearby Alps. Highest-quality Alpine coverage; fills a gap Open-Meteo can't match at that resolution. Source: [GeoSphere data hub](https://data.hub.geosphere.at/), [Breezy SOURCES.md](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md). Effort: medium.

### NX-3. FMI (Finland) + KNMI (Netherlands) adapters · **T-SOURCES**
Both added to Breezy in 2026 (v6.2.0 / v6.1.0). FMI adds: forecast, current, AQI, alerts, normals for Finland/Åland. KNMI adds Netherlands forecasts. Both keyless. Source: [Breezy CHANGELOG.md](https://github.com/breezy-weather/breezy-weather/blob/main/CHANGELOG.md). Effort: low–medium each. Bundle the two into one provider-expansion PR.

### NX-4. Open-Meteo pollen card refresh + Marine + Flood APIs · **T-SOURCES** / **T-HEALTH**
Pollen is already in `AirQualityRepository`; the [docs](https://open-meteo.com/en/docs/air-quality-api) note CAMS coverage is European-only — surface this explicitly in the empty state. Add **Marine** ([wave height/period/direction, ocean current, sea surface temp](https://open-meteo.com/en/docs/marine-weather-api)) as a new opt-in card for coastal users. Add **Flood** (GloFAS river discharge, 30-day) as a new opt-in card for river-near users. Effort: low each.

### NX-5. Multi-provider agreement card · **T-RELIABILITY**
Fetch the next 24h temp + precip from 2–3 enabled providers in parallel; render a "providers agree within ±X°" or "diverge >5°" badge with a tap-to-expand showing each provider's value. This is the FOSS answer to AccuWeather's MinuteCast / Carrot's multi-model. Borrowed from the [Breezy idea](https://github.com/breezy-weather/breezy-weather) of leveraging the multi-source system for transparency, not just resilience. Effort: medium. Risk: API quota multiplication — gate behind opt-in.

### NX-6. Per-location offline-first cache · **T-PERF**
Existing roadmap calls this out: "switching to a saved location triggers a fresh API call." Cache the last-known weather per `savedLocationId` in Room and serve instantly while a background fetch refreshes. Already 80% of the infrastructure is in place (`WeatherCacheEntity` keyed by coord); just need a `getInstantThenRefresh` flow at the repository layer. Done when location switch renders <100ms with cached data + an "updating…" pill. Effort: low–medium.

### NX-7. Baseline Profiles + Macrobenchmark startup gate · **T-PERF**
Compose-heavy apps see ~30% startup wins from Baseline Profiles. Source: [Android Developers](https://developer.android.com/develop/ui/compose/performance/baseline-profiles). Add `:benchmark` module with a Macrobenchmark covering: cold start → first frame, location switch, radar tab open, settings open. CI publishes the trace + asserts a ceiling (e.g. p95 cold start <1200ms). Profile is generated once per release; the AGP plugin handles bundling. Effort: medium.

### NX-8. Move OkHttp retry to repository-level coroutine `delay` · **T-RELIABILITY**
Existing roadmap LOW item. Today `NetworkModule` uses `Thread.sleep` for exponential backoff inside an interceptor (correct but blocks OkHttp dispatcher slot). Move retry into a `withRetry { … }` repository helper using `kotlinx.coroutines.delay`. Audit: all repositories that currently rely on the interceptor's retry must be updated. Effort: medium.

### NX-9. Quick-Settings Tile + Lockscreen complication · **T-ECOSYSTEM**
Android `TileService` for QS — at a glance temp + condition icon + tap-to-open. On Android 14+ phones, the [Lockscreen widget API](https://developer.android.com/develop/ui/views/appwidgets/host) accepts Glance widgets — surface NimbusSmallWidget there. Sources: [Pixel weather widget](https://9to5google.com/2021/10/18/google-weather-material-you-widgets/). Effort: low (QS), medium (lockscreen-widget compliance).

### NX-10. Localized condition strings from native services · **T-I18N**
NWS, JMA, MeteoAlarm, ECCC, MET Norway all expose multilingual condition + alert text. Today we map upstream codes to our own WMO + summary engine vocabulary; for `AlertSourceAdapter` outputs we should prefer the upstream localized string when the user's locale matches one published by the source. Effort: low–medium.

### NX-11. Vico chart migration for trend cards · **T-PERF** / UX
Replace custom Canvas trend charts (`PressureTrendCard`, `WindTrendCard`, `PrecipitationChartCard`) with [Vico 3.x](https://github.com/patrykandpatrick/vico) (Apache-2.0, Compose-native, M3-themed, KMP-ready). Removes ~600 lines of custom drawing, gets pinch-zoom/hover for free, fits the Material 3 Expressive direction. Keep `TemperatureGraph` custom (it has the drag-to-inspect interaction Vico doesn't cleanly do). Effort: medium.

### NX-12. NLG template authoring · **T-ECOSYSTEM**
Surface a settings-level "edit summary template" textarea with documented variables (`{temp}`, `{cond}`, `{rain_window}`). The template engine is already pure-function (`WeatherSummaryEngine`); add a template-validator. Power users get the same lever Tasker/Macrodroid power users want. Effort: low.

### NX-13. ContentProvider + broadcast for ecosystem · **T-ECOSYSTEM**
Breezy v6.1.0 added a `ContentProvider` so other apps (Gadgetbridge, Tasker, KWGT, custom widgets) can query current weather. Source: [Breezy CHANGELOG.md](https://github.com/breezy-weather/breezy-weather/blob/main/CHANGELOG.md). Mirror the schema for compatibility (matters because Gadgetbridge already speaks Breezy's intent). Two surfaces:
- Read-only `ContentProvider` at `com.sysadmindoc.nimbus.provider/weather`.
- Broadcast `com.sysadmindoc.nimbus.ACTION_WEATHER_UPDATE` (sticky-style) consumable by Tasker.
Honor an opt-in toggle. Effort: medium.

### NX-14. Reproducible builds badge for F-Droid · **T-RELIABILITY**
Tiny Weather Forecast Germany, Breezy, Cirrus all have F-Droid [reproducibility status pages](https://f-droid.org/en/docs/Reproducible_Builds/). ZeusWatch should aim for the same. Audit: locale/timezone-sensitive resource hashes, Hilt-generated code stability, AGP `dependencies.gradle.lockfile`. Effort: medium; mostly removing nondeterminism.

### NX-15. Aurora / Kp index card · **T-HEALTH** (lifestyle)
NOAA SWPC endpoints already documented in the existing roadmap appendix:
- `https://services.swpc.noaa.gov/json/ovation_aurora_latest.json`
- `https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json`
Uncontested in the FOSS Android weather space. New card type (29th); off by default; uses the existing card-toggle infra. Effort: low.

### NX-16. Lightning proximity push · **T-HEALTH** (safety)
Blitzortung WebSocket is already wired ([`BlitzortungService`](app/src/main/java/com/sysadmindoc/nimbus/data/api/BlitzortungService.kt)). Add a `LightningProximityWorker` that fires a high-priority notification when a strike is detected within X km of the user's location (configurable; default 10 km / off). Mirrors [Carrot Ultra's storm cell alerts](https://support.meetcarrot.com/weather/) — but free. Effort: low–medium.

### NX-17. Custom-alert rule expansion · **T-HEALTH**
Custom alerts today cover 5 metrics (high, low, gusts, 24h precip, UV peak). Add: AQI threshold, dewpoint, heat index, wind chill, snowfall sum, lightning-within-N-km (depends on NX-16), and any-source severe weather event of type X. Pure expansion of `CustomAlertEvaluator`'s rule enum + UI editor. Effort: medium.

### NX-18. WCAG 2.2 AA audit + dynamic font scaling pass · accessibility / **T-RELIABILITY**
Strategic Compass #5 says "accessibility-first"; v1.18.0 closed the Canvas-a11y front and added Espresso `AccessibilityChecks.enable()` to the instrumented suite. The next pass:
1. **Contrast audit** of every weather-adaptive theme variant against [WCAG 2.2 AA](https://www.w3.org/TR/WCAG21/) (4.5:1 normal text, 3:1 large text, 3:1 graphics). The amber/blue/purple condition palettes are the highest-risk areas.
2. **Font scaling stress test** at `fontScale = 1.3 / 1.5 / 1.8` on every screen — particularly the temperature graph axis labels, daily forecast rows, and Wear pill rows. Use Compose `LocalDensity` previews.
3. **Touch target ≥48dp audit** on dense settings rows and the location selector.
4. Extend Compose UI tests with accessibility checks beyond MainScreen/Settings/Locations to all 5 phone screens.
Source: [Mobile App Accessibility 2026 guide](https://www.accessibilitychecker.org/guides/mobile-apps-accessibility/). Effort: medium.

### NX-19. Release ops + docs consolidation · upgrade-path / docs
Concrete maintenance items that don't fit a feature box:
- Consolidate `PHASES.md` + `PHASE8_PLAN.md` (both pre-v1.0) into a single `docs/history.md` and drop the planning docs from the repo root — they're frozen.
- Move `RESEARCH.md` into `docs/research-archive.md` so the repo root is README + CHANGELOG + ROADMAP only.
- Document the per-release [pin-capture procedure](tools/capture_api_pins.sh) and the `release.yml` signing-secrets contract in a new `docs/RELEASE.md`.
- Reconcile fastlane `full_description.txt` — currently advertises 48h hourly (we ship 72h) and 3 widget sizes (we ship 4).
- Sync `versionCode` divergence between phone (86) and wear (62) at the next release if Play Store distribution is on the table; ignore if not.
Effort: low. Done when repo root has ≤6 markdown files and fastlane copy matches reality.

---

## LATER — Beyond v1.26, conditional or large-scope

Items that need more dependency landing, more research, or a major architectural commit. Listed so they don't get forgotten.

### L-1. `freenet` flavor Wear OS sync via non-GMS path · **T-WEAR**
F-Droid users with a Wear OS watch currently get zero phone-to-watch sync. Existing roadmap calls this out as a candidate for Bluetooth serial or `CompanionDeviceManager`. Realistic options:
1. **CompanionDeviceManager + sockets**: pair-once, then a custom `ParcelFileDescriptor` socket pushes a serialized payload. Requires foreground service when the phone is the source.
2. **MQTT broker over local network**: too far from the platform conventions; users would balk.
3. **NFC tap-to-sync**: too manual.
4. **Drop the requirement**: document that `freenet` Wear users rely on direct API calls (already works).
Default to (4) unless option (1) is implementable in <2 weeks. Effort if pursued: high.

### L-2. Compose Multiplatform iOS port · **T-ECOSYSTEM**
[CMP for iOS is Stable since 1.8.0 (May 2025)](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/). Room is KMP-ready in 2.7+. The UI layer is 100% Compose. The architectural cost is real (DataStore/Hilt/Glance are Android-only — would need swap). Decision deferred until a) the freenet Wear question is settled and b) there's an iOS user audience signal. Out today.

### L-3. Provider-agnostic per-card unit override · UX
Breezy's "per-card settings menu: reorder, hide, customize unit on a card-by-card basis" — e.g. show wind in knots on the marine card but km/h elsewhere. Requires a `LocalUnitSettings` override propagation through the card render path. Effort: medium. Belongs Later because it's a polish item gated on i18n completion.

### L-4. Android Auto / Car App Library variant · **T-ECOSYSTEM**
[Car App Library templates](https://developer.android.com/training/cars/apps/weather) for weather are driving-optimized. A bare-bones AA module exposing alerts + radar map + driving conditions card would be unique among FOSS weather apps. Effort: medium; new module `:auto`. Risk: GMS-dependent? Investigate before commit.

### L-5. Android TV variant · **T-ECOSYSTEM**
[weather-you reference](https://github.com/rodrigmatrix/weather-you) ships Compose for TV. Useful for kitchen/garage screens. Effort: medium; gated on actual demand signal.

### L-6. Smartspacer target plugin · **T-ECOSYSTEM**
Reference: [KieronQuinn/Smartspacer](https://github.com/KieronQuinn/Smartspacer). Lets ZeusWatch surface in Pixel "At a Glance" without root. Existing roadmap appendix already documents this. Effort: low–medium; out-of-tree plugin if we don't want runtime cost in the main APK.

### L-7. Home Assistant integration · **T-ECOSYSTEM**
Publish weather entities via the ContentProvider from NX-13 + REST endpoint, or via MQTT if user has a broker. Multiple [HA community blueprints](https://www.home-assistant.io/integrations/weather/) consume `weather.*` entities. Effort: medium; doable as a separate module so HA users opt in.

### L-8. WebGPU radar via newer MapLibre · **T-PERF**
[MapLibre 12.3.0 ships a WebGPU backend](https://github.com/maplibre/maplibre-native/blob/main/platform/android/CHANGELOG.md). Speculative until landed broadly; today the GL ES 3 path is fine. Watch for upgrade pressure once 12.x stabilizes.

### L-9. Marine / Aviation power-user mode · **T-SOURCES**
Layered atop NX-4 + Météo-France. Storm Glass / Open-Meteo marine + METAR/TAF/NOTAM (Windy-style). Off-philosophy unless we maintain a strict "no paywall, no clutter" stance — gate behind an explicit "power-user mode" preference. Effort: high.

### L-10. SPC Conditional Intensity overlay (US tornado/hail/wind) · **T-HEALTH** (safety)
The SPC's [March 2026 overhaul](https://www.weather.gov/news/262402-spc) added explicit Conditional Intensity tiers and a public [MapServer endpoint](https://mapservices.weather.noaa.gov/vector/rest/services/outlooks/SPC_wx_outlks/MapServer). Polygon overlay on the radar tab + push notification when the user's location enters a Day-1 Enhanced+ polygon. Effort: medium; US-only.

### L-11. Migrate select cards to Lottie ProtoLayout on Wear tiles · **T-WEAR**
Wear OS 6's [ProtoLayout Material 3](https://developer.android.com/jetpack/androidx/releases/wear-protolayout) supports Lottie animations in tiles. ZeusWatch already ships Lottie 6.6.2; the conversion path is short. Adds animated weather icons to the tile experience. Effort: low–medium.

### L-12. Open-Meteo Kotlin/FlatBuffer SDK migration · **T-PERF**
[Open-Meteo's official SDK](https://github.com/open-meteo/sdk) ships FlatBuffer schemas; the JVM artifact `com.open-meteo:sdk` decodes responses ~2× faster than JSON for large payloads. Forecast + AQI + minutely combined hits ~80 KB JSON — meaningful. Effort: medium (replace serialization, keep Retrofit shape). Risk: schema drift; the SDK doesn't ship the HTTP layer.

### L-13. Reverse-geocoding without Play Services for `freenet` · **T-RELIABILITY**
Existing gotcha: `Geocoder` is unreliable without Play Services. Nominatim is already used; verify rate-limit compliance (1 req/sec/IP per their TOS) and consider bundling an offline GeoNames cities dataset for the common case. Effort: medium.

### L-14. Adversarial audit round 5 · **T-RELIABILITY**
Cadence: every 6–8 releases. Targets for round 5: WFF data publisher (if NX added), Vico chart migrations, freenet Wear path, and any new adapters. The first four rounds netted 38 latent bugs; budget for round 5.

### L-15. Mutation testing + Compose screenshot tests · **T-RELIABILITY**
Pitest-android for the pure-function evaluators (clothing, health, driving, pet, custom-alert). Paparazzi/Roborazzi for screen-level golden-image diffs. Both are "raise the floor" investments after the basic test coverage is solid. Effort: high.

### L-16. Calendar-based / alternate numeral support · **T-I18N**
Breezy added [non-Latin numeral systems + alternate calendars](https://github.com/breezy-weather/breezy-weather/blob/main/CHANGELOG.md). Useful in fa-IR, ar, th locales. Gated on completing the core extraction first.

---

## UNDER CONSIDERATION — needs a call before scoping

Each of these has a real argument but also a real cost. Not scheduled until we make a decision.

### UC-1. Self-hosted ACRA crash report endpoint
Today crash reports are emailed via consent dialog to `snafumatthew+zeuswatch@gmail.com`. A self-hosted ACRA backend would scale better but reintroduces a "server to maintain" surface that conflicts with the no-backend stance. **Open question**: is the volume actually a problem yet? If yes, self-host. If no, status quo.

### UC-2. Anonymous usage telemetry (opt-in)
Plausible-style, no PII, opt-in toggle. Would inform "which provider do users actually pick" and "which card do users actually keep enabled" — both currently blind. Friction: an outbound endpoint at all is a F-Droid red flag and a privacy concern. **Open question**: can we live with continued blindness? If yes, reject.

### UC-3. Light theme / weather-adaptive light mode
Dark-only is part of the brand. But screen-reader users with high-photophobia inversions exist, and a few users will ask. **Open question**: opt-in scheduled light theme, or no? Existing roadmap silently treats this as out. Make it explicit.

### UC-4. Background-fetch budget controls
Battery is the #1 complaint against weather apps (28% per [unstar.app](https://unstar.app/blog/weather-apps-ranked-by-user-complaints-2026); reinforced by [howtogeek](https://www.howtogeek.com/heres-how-i-found-android-apps-that-were-secretly-draining-battery-in-the-background/) — though that piece is generic). WidgetRefreshWorker already skips at battery ≤15%. Should we be more aggressive: skip below 25%, halve cadence when in Doze, fully halt under restrictive bucket? **Open question**: is the current loss material? Instrument first (depends on UC-2 decision).

### UC-5. AccuWeather adapter via bundled key
Breezy ships a bundled AccuWeather key. AccuWeather covers global pollen + better US alerts than NWS in some cases. Friction: bundled key is paywall-adjacent and revokable. **Open question**: drop or accept the brittleness?

### UC-6. Pixel Watch / Wear OS 6 M3 Expressive UI refresh
[wear-compose-material3 1.5.0-beta01](https://developer.android.com/jetpack/androidx/releases/wear-compose-m3) lands the M3 Expressive Wear UI. Refresh once stable. Decide at that point; today's wear UI is functional. Out of NEXT only because of "wait for stable."

### UC-7. ScrollAware widget refresh on home-screen interaction
A long-tail efficiency: refresh the widget at the moment the user wakes the phone, not on a 15-min cadence. Possible via lifecycle callbacks. Friction: increases code complexity; might not actually save power. **Open question**: needs measurement (see UC-4).

---

## REJECTED — and why

Each is closed with a one-line rationale so we don't relitigate.

- **Bundled AccuWeather/Apple WeatherKit keys as default fallback.** Violates "no required API keys" + "no proprietary dep" guardrails when bundled defaults effectively bind users to those terms.
- **Custom Wear OS runtime watch face (not WFF).** [As of Jan 2026 WFF is required for Wear OS install](https://developer.android.com/training/wearables/wff). Runtime faces are deprecated for new installs.
- **Built-in ad slots / interstitials.** Antithetical to the brand; [#2 user complaint vector in 2026](https://unstar.app/blog/weather-apps-ranked-by-user-complaints-2026).
- **Subscription tier / Premium Club.** Antithetical to the brand; [drives the most complaints at Carrot/AccuWeather](https://unstar.app/blog/weather-apps-ranked-by-user-complaints-2026).
- **Audio severe-weather TTS played out of system mixer.** Out of scope; let TalkBack and the system-level alarm DND-bypass channels (already implemented) handle that for users who want it.
- **Replace MapLibre with proprietary map SDK (Mapbox/Maps SDK for Android).** Locks `freenet`.
- **Migrating to Firebase as primary state store.** Conflicts with `freenet` parity; today Firestore is used only for opt-in community reports (`standard` flavor only) and that's the right boundary.
- **Android 16 "Local" weather wallpaper integration.** [Pixel-only and no public third-party API](https://gadgets.beebom.com/guides/how-to-use-lock-screen-live-effects-on-pixel-phones); not actionable.
- **Built-in webcam / live photo feed.** Out of philosophy (third-party hosting, copyright, image moderation).
- **Replace Hilt with Koin/Dagger pure.** Hilt works; the [Kotlin 2.1.0 + KSP2 compatibility friction](https://github.com/google/dagger/issues/4582) is real but already navigated.
- **Move Wear app to Compose Multiplatform.** No upside today; CMP-on-Wear isn't a target.

---

## Appendix A — Adversarial Review Notes

What a hostile reviewer would ask, with answers.

- **"You shipped 28 cards and 7 providers — why isn't the ROADMAP shorter?"** Because the per-card density is exactly what makes the app stand out from Breezy-style minimalism. The roadmap reflects breadth obligations: i18n compound cost, Wear OS moat extension, regional source coverage, and audit cycles. Each Now item has a closure criterion.
- **"Why no Android 16 M3 Expressive sweep in NOW?"** It's UC-6; Wear Compose M3 is still in beta01. Premature work invites churn. Phone-side: when 1.5.0 stable lands, schedule.
- **"Where's the security story?"** N-2 (cert pinning hostmap), NX-14 (reproducible builds), L-13 (no-Geocoder fallback). Plus the [v1.16.0 RateLimitInterceptor + API-key debug-log redaction](ROADMAP-COMPLETED.md) already shipped. CVE watch is implicit in Dependabot config from v1.16.0.
- **"You're handwaving on observability."** UC-1 + UC-2 + NX-7 form the observability arc and are explicitly held under "decide first." Anonymous telemetry is a F-Droid-sensitive call.
- **"Distribution is thin."** NX-14 (reproducible builds) + per-ABI signed APKs (Breezy pattern) are the immediate steps. Play Store is not pursued; F-Droid + IzzyOnDroid + GitHub Releases is the explicit channel.
- **"Multi-user/collab story?"** Already present via opt-in Firestore community reports (`standard` flavor). Not a primary axis — weather is a single-user product.
- **"Plugin ecosystem?"** Custom icon packs (shipped, intent-discovered), NLG template authoring (NX-12), ContentProvider/Tasker (NX-13), Smartspacer target (L-6), Home Assistant (L-7), Gadgetbridge already works via Breezy-compatible broadcast (NX-13). Plugin surface is intentional and bounded.
- **"Mobile/offline/migration paths?"** Offline: NX-6 + the existing 30-min cache + connectivity observer. Migration: Room schemas exported as of v1.16.0, DataStore prefs use `safeValueOf` (no unsafe `valueOf`), `SourceConfig.normalized()` coerces stale provider preferences. The provider EOS pattern (Recosanté at Breezy v6.2.0) is handled by `WeatherSourceProvider.isImplementedFor()`.
- **"Why isn't a watch face (not complication) in Now?"** Custom runtime watch faces are deprecated for new Wear installs as of January 2026. Complications are the supported pathway. See N-4/N-5.

---

## Appendix B — External Ecosystem Intelligence (refreshed)

### Comparable open-source projects

| Project | Stars (approx) | Stack | Sources | Wear OS | License | Notes |
|---|---|---|---|---|---|---|
| [Breezy Weather](https://github.com/breezy-weather/breezy-weather) | 10.1k | Kotlin/Compose | 50+ (v6.2.0_freenet, 2026-05-02) | No (Gadgetbridge bridge) | LGPL-3.0 | Gold standard. M3 Expressive. Weblate. F-Droid official + IzzyOnDroid. ContentProvider for ecosystem. |
| [WeatherMaster](https://github.com/PranshulGG/WeatherMaster) | ~2.9k | Kotlin Compose (v3 rewrite May 2026, from Flutter v2) | 15 | No | GPL-3.0 | Fastest-growing challenger. Active v3.0 alpha cycle, AQI added in alpha4. Crowdin pipeline. |
| [Rain](https://github.com/darkmoonight/Rain) | ~980 | Flutter | 1 (Open-Meteo) | No | MIT | Multi-platform via Flutter (Android/iOS/Linux/macOS/Windows/Web). |
| [Overmorrow](https://github.com/bmaroti9/Overmorrow) | ~730 | Flutter | 4 | No | GPL-3.0 | UI/UX-focused. |
| [Forecastie](https://github.com/martykan/forecastie) | ~900 | Java | 1 (OWM) | No | GPL-3.0+ | Legacy reference. |
| [Bura](https://github.com/davidtakac/bura) | ~370 | Kotlin/Compose | 1 (Open-Meteo) | No | GPL-3.0 | Single-source clean architecture reference. |
| [Geometric Weather](https://github.com/WangDaYeeeeee/GeometricWeather) | 2.5k | Java | 7 | No | LGPL-3.0 | Archived but the animated particle-overlay code is referenceable. |
| [Tiny Weather Forecast Germany](https://f-droid.org/packages/de.kaffeemitkoffein.tinyweatherforecastgermany/) | — | Java | DWD only | No | GPL-3.0 | Reproducible builds reference. |
| [Cirrus](https://f-droid.org/packages/org.woheller69.omweather/) | — | Java | Open-Meteo | No | GPL-3.0 | Privacy-focused, F-Droid native. |
| [weather-you](https://github.com/rodrigmatrix/weather-you) | — | Kotlin/Compose | OWM | Yes (basic) | — | Phone/Tablet/TV/Wear reference. Useful for TV/tablet patterns (L-5). |

**Strategic position**: Wear OS native + multi-source resilience + health alerts remains uncontested. Source-count parity with Breezy is the biggest gap (covered by NX-1/2/3). Material 3 Expressive UI refresh is the biggest table-stakes pressure (UC-6).

### Commercial reference (what's paywalled is what FOSS undervalues)

| App | Notable paywalled features | Implication for us |
|---|---|---|
| [Carrot Weather Premium Ultra](https://support.meetcarrot.com/weather/) | Storm-cell + lightning push, super-res radar, multi-model forecasts, weather-map widget | NX-16 (lightning proximity) + NX-5 (multi-model agreement) cover ~80% free. |
| [1Weather Premium](https://play.google.com/store/apps/details?id=com.handmark.expressweather) | Ad-free, 10-day daily, AQI card | Already free in ZeusWatch. |
| [Windy.com](https://www.windy.com/) | METAR/TAF/NOTAM, marine layers, drone forecast | L-9 power-user mode if pursued; otherwise out. |
| [AccuWeather MinuteCast](https://www.accuweather.com/) | Per-minute precipitation 2–4h | ZeusWatch's `NowcastCard` already provides 60-min from Open-Meteo + 5-min from MET Norway nowcast for Nordics; expand via Météo-France PIAF in NX-1. |
| [Apple WeatherKit](https://developer.apple.com/weatherkit/) | Cross-platform (iOS-aligned) | Not pursued (key required). |

### Community signal (top complaints in 2026 ranked)

Source: [unstar.app weather-app complaint analysis 2026](https://unstar.app/blog/weather-apps-ranked-by-user-complaints-2026) + [androidpolice "most accurate" piece](https://www.androidpolice.com/this-is-the-most-accurate-weather-app-for-android/) + [androidauthority Pixel data-source critique](https://www.androidauthority.com/android-weather-app-setting-data-source-3663273/).

1. **Accuracy issues (28%)** — "said sunny, rained all day." Mitigation: NX-5 multi-provider agreement card surfaces divergence explicitly.
2. **Ads (23%)** — already rejected.
3. **Subscription creep (19%)** — already rejected.
4. **Widget bugs (Android-specific)** — widgets showing wrong city after updates, location lost. ZeusWatch's per-widget location pref + sortOrder=-1 current-location pin (v1.6.2) handles this; NX-9 (lockscreen widget) inherits the same pattern.
5. **Notification problems (12%)** — irrelevant locations, seasonal mismatch. Mitigation: multi-location alert dedupe + expired alert filter (shipped); custom-rule expansion (NX-17) reduces noise.
6. **Battery drain** — UC-4 + NX-7 (Macrobenchmark for visibility).
7. **Missing source selection** — Pixel Weather called out for not letting users pick data source ([androidauthority](https://www.androidauthority.com/android-weather-app-setting-data-source-3663273/)). ZeusWatch ships 7 + 2 alert-aggregators; this complaint is already addressed and is a differentiation point.

---

## Appendix C — Data Sources Catalog (refreshed)

Verified live in this audit pass unless noted otherwise.

### Currently integrated

| Source | Data | Auth | Notes |
|---|---|---|---|
| [Open-Meteo](https://open-meteo.com/) | Forecast / AQI / pollen / minutely_15 / archive | None | Primary. Free 10k/day. |
| [OpenWeatherMap One Call 3.0](https://openweathermap.org/api/one-call-3) | Forecast / alerts / AQI | Key | Fallback. [2.5 deprecated June 2024](https://openweathermap.org/api/one-call-3). |
| [Pirate Weather](https://pirateweather.net/) | Forecast | Key | Dark-Sky-compatible fallback. |
| [Bright Sky (DWD)](https://brightsky.dev/) | Forecast / alerts | None | German Weather Service relay. |
| [MET Norway](https://api.met.no/) | Forecast | Custom UA required, [20 req/s aggregate cap](https://api.met.no/doc/TermsOfService) | CC BY 4.0. Best Nordic coverage. |
| [Environment Canada (OGC)](https://api.weather.gc.ca/openapi) | Forecast / alerts | None | Canada-only. |
| [NWS](https://www.weather.gov/documentation/services-web-api) | Alerts | None | US-only. |
| [MeteoAlarm](https://www.meteoalarm.org/) | Alerts | None | 31 EU countries. |
| [JMA](https://www.jma.go.jp/) | Alerts | None | Japan-only. |
| [RainViewer](https://www.rainviewer.com/api/weather-maps-api.html) | Radar tiles | None | 5-min update; tiles expire 2h. |
| [Blitzortung](https://www.blitzortung.org/) | Lightning WebSocket | None | Real-time global. |

### Provider candidates (NEXT/LATER)

| Source | Coverage | Auth | Notes / Source link |
|---|---|---|---|
| **BOM Australia (indirect via Open-Meteo)** | AU | None | [ACCESS-G model docs](https://open-meteo.com/en/docs/bom-api). Preferred path. |
| **BOM Australia (direct)** | AU | None (undocumented) | [Reverse-engineered weather-au](https://github.com/tonyallan/weather-au). Legal risk. |
| **Météo-France** | FR + global ARPEGE | JWT (free) | [portail-api.meteofrance.fr](https://portail-api.meteofrance.fr); [Open-Meteo MF docs](https://open-meteo.com/en/docs/meteofrance-api). |
| **GeoSphere Austria** | AT + Alps | None | [data.hub.geosphere.at](https://data.hub.geosphere.at/dataset/nowcast-v1-15min-1km). CC0. INCA 15-min/1km. |
| **FMI** | FI / Åland | None | [Breezy SOURCES.md FMI entry](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md). |
| **KNMI** | NL | None | [Breezy SOURCES.md KNMI entry](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md). |
| **SMHI Sweden** | SE | None | New endpoint `pmp3g/v2 → snow1g/v1` (migrated 2026-03-31, old returns 404). |
| **AEMET Spain** | ES | Free key | Two-step fetch URL pattern. |
| **HKO Hong Kong** | HK | None | [Breezy SOURCES.md HKO entry](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md). |
| **CWA Taiwan** | TW | Free key | [Breezy SOURCES.md CWA entry](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md). |
| **BMKG Indonesia** | ID | None | [Breezy SOURCES.md BMKG entry](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md). |
| **NOAA SWPC** (aurora / Kp) | Global | None | `services.swpc.noaa.gov/json/ovation_aurora_latest.json`. |
| **SPC outlook polygons** | US | None | [MapServer](https://mapservices.weather.noaa.gov/vector/rest/services/outlooks/SPC_wx_outlks/MapServer). Includes 2026 Conditional Intensity tiers. |
| **Open-Meteo Marine** | Global coastal | None | [docs](https://open-meteo.com/en/docs/marine-weather-api). |
| **Open-Meteo Flood** | Global rivers | None | [docs](https://open-meteo.com/en/docs/flood-api). GloFAS 30-day. |
| **Open-Meteo Ensemble** | Global | None | [docs](https://open-meteo.com/en/docs/ensemble-api). For uncertainty visualization. |
| **NEXRAD via IEM** | US | None | `mesonet.agron.iastate.edu/cache/tile.py/.../nexrad-n0q-900913/{z}/{x}/{y}.png`. Slippy tiles. |

### Rejected (re-evaluated each cycle)

- **AccuWeather Free** — bundled key path; revokable; license-restrictive.
- **WAQI** — non-commercial license only; F-Droid concern.
- **Tomorrow.io** — [no actual free tier despite marketing](https://www.tomorrow.io/weather-api/); pricing brittle.
- **Apple WeatherKit REST** — key required + Apple Developer account; not freenet-compatible.

---

## Appendix D — Library / Tooling Watch

Versions tracked in `gradle/libs.versions.toml`. Notable upgrade horizons:

| Lib | Current | Next | Why care |
|---|---|---|---|
| Compose BOM | 2025.04.01 | next quarterly | M3 Expressive landings. |
| Kotlin | 2.1.0 | 2.2.x | [Data-flow exhaustiveness](https://kotlinlang.org/docs/whatsnew22.html), Multiplatform Swift export. |
| Hilt | 2.53.1 | 2.56.x | [KSP2 + Kotlin 2.1+ compat caveats](https://github.com/google/dagger/issues/4907) — bump cautiously, run full test suite. |
| Room | 2.6.1 | 2.7.x → 3.0 | [KMP-ready](https://developer.android.com/jetpack/androidx/releases/room), SQLiteDriver. Gated on L-2 decision. |
| MapLibre | 11.5.2 | 12.x | [WebGPU backend in 12.3.0](https://github.com/maplibre/maplibre-native/blob/main/platform/android/CHANGELOG.md); breaking init API. |
| OkHttp | 4.12.0 | 4.13.x / 5.x | [`redactQueryParameters(vararg)` added in 5.x](https://github.com/square/okhttp); reduces our custom redactor. Watch CVE feeds. |
| Glance | 1.1.1 | 1.2.x | [Unit-test helper `runGlanceAppWidgetUnitTest`](https://developer.android.com/jetpack/androidx/releases/glance) — enables N-9. |
| Wear Compose M3 | (alpha27) | 1.5.0 stable | [Wear OS 6 M3 Expressive](https://developer.android.com/jetpack/androidx/releases/wear-compose-m3); gates UC-6. |
| ProtoLayout | 1.2.1 | 1.3.x | [Lottie support](https://developer.android.com/jetpack/androidx/releases/wear-protolayout) — gates L-11. |
| Detekt | 1.23.8 | 1.23.x | Baseline reduction (N-8) is the live work, not version. |
| ACRA | 5.13.1 | 5.13.x | Stable. |
| Vico | not added | 3.0.x | [Compose-native multiplatform](https://github.com/patrykandpatrick/vico); NX-11 dep. |

---

## Appendix E — Sources Cited

All direct citations used in this roadmap. (URLs only — no marketing prose, no invented sources.)

**OSS competitors / references**
- [breezy-weather/breezy-weather](https://github.com/breezy-weather/breezy-weather) — main repo
- [breezy-weather CHANGELOG.md](https://github.com/breezy-weather/breezy-weather/blob/main/CHANGELOG.md) — 2026 releases
- [breezy-weather docs/SOURCES.md](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md) — 50+ sources catalog
- [breezy-weather issues](https://github.com/breezy-weather/breezy-weather/issues) — open feature requests
- [PranshulGG/WeatherMaster](https://github.com/PranshulGG/WeatherMaster) — main repo
- [WeatherMaster releases](https://github.com/PranshulGG/WeatherMaster/releases) — 2026 alpha cycle
- [rodrigmatrix/weather-you](https://github.com/rodrigmatrix/weather-you) — TV/Wear reference
- [WangDaYeeeeee/GeometricWeather](https://github.com/WangDaYeeeeee/GeometricWeather) — archived ref
- [KieronQuinn/Smartspacer](https://github.com/KieronQuinn/Smartspacer)
- [tonyallan/weather-au](https://github.com/tonyallan/weather-au) — BOM reverse engineering
- [crowdin/mobile-sdk-android](https://github.com/crowdin/mobile-sdk-android)

**Android platform docs**
- [Wear OS 6 — Android Developers Blog (2025-05)](https://android-developers.googleblog.com/2025/05/whats-new-in-wear-os-6.html)
- [Watch Face Format — weather data](https://developer.android.com/training/wearables/wff/weather)
- [Watch Face Format — release notes](https://developer.android.com/training/wearables/wff/release-notes)
- [wear-protolayout releases](https://developer.android.com/jetpack/androidx/releases/wear-protolayout)
- [wear-compose-m3 releases](https://developer.android.com/jetpack/androidx/releases/wear-compose-m3)
- [Jetpack Glance releases](https://developer.android.com/jetpack/androidx/releases/glance)
- [Glance theme docs](https://developer.android.com/develop/ui/compose/glance/theme)
- [Compose Baseline Profiles](https://developer.android.com/develop/ui/compose/performance/baseline-profiles)
- [Macrobenchmark capture metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)
- [Strong skipping mode](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping)
- [Predictive back setup](https://developer.android.com/develop/ui/compose/system/predictive-back-setup)
- [Android for Cars: Build a weather app](https://developer.android.com/training/cars/apps/weather)
- [DataLayer data items](https://developer.android.com/training/wearables/data/data-items)
- [Hilt KSP2 issue 4907](https://github.com/google/dagger/issues/4907)

**Kotlin / JetBrains**
- [What's new in Kotlin 2.1.20](https://kotlinlang.org/docs/whatsnew2120.html)
- [What's new in Kotlin 2.2](https://kotlinlang.org/docs/whatsnew22.html)
- [Compose Multiplatform 1.8.0 / iOS Stable](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)

**Charts / libraries**
- [patrykandpatrick/vico](https://github.com/patrykandpatrick/vico)
- [MapLibre native CHANGELOG.md (Android)](https://github.com/maplibre/maplibre-native/blob/main/platform/android/CHANGELOG.md)
- [OkHttp 4.x changelog](https://square.github.io/okhttp/changelogs/changelog_4x/)
- [open-meteo/sdk (Kotlin/Flatbuffer)](https://github.com/open-meteo/sdk)

**Data sources**
- [Open-Meteo Marine API](https://open-meteo.com/en/docs/marine-weather-api)
- [Open-Meteo Flood API](https://open-meteo.com/en/docs/flood-api)
- [Open-Meteo Ensemble API](https://open-meteo.com/en/docs/ensemble-api)
- [Open-Meteo Météo-France API](https://open-meteo.com/en/docs/meteofrance-api)
- [Open-Meteo BOM API](https://open-meteo.com/en/docs/bom-api)
- [MET Norway Welcome](https://api.met.no/)
- [MET Norway Terms of Service](https://api.met.no/doc/TermsOfService)
- [MET Norway Nowcast](https://api.met.no/weatherapi/nowcast/2.0/documentation)
- [NOAA SPC Conditional Intensity announcement](https://www.weather.gov/news/262402-spc)
- [NOAA SPC MapServer](https://mapservices.weather.noaa.gov/vector/rest/services/outlooks/SPC_wx_outlks/MapServer)
- [GeoSphere Austria data hub](https://data.hub.geosphere.at/dataset/nowcast-v1-15min-1km)
- [BOM Catalogue data feeds](https://www.bom.gov.au/catalogue/data-feeds.shtml)
- [OWM One Call 3.0](https://openweathermap.org/api/one-call-3)

**F-Droid / distribution**
- [F-Droid Reproducible Builds docs](https://f-droid.org/en/docs/Reproducible_Builds/)
- [F-Droid Translation and Localization](https://f-droid.org/docs/Translation_and_Localization/)

**Accessibility / standards**
- [WCAG 2.1 / 2.2 AA spec](https://www.w3.org/TR/WCAG21/)
- [Mobile App Accessibility — 2026 guide](https://www.accessibilitychecker.org/guides/mobile-apps-accessibility/)

**Community signal / complaints**
- [unstar.app — Weather apps ranked by complaints 2026](https://unstar.app/blog/weather-apps-ranked-by-user-complaints-2026)
- [Android Authority — most accurate Android weather app](https://www.androidpolice.com/this-is-the-most-accurate-weather-app-for-android/)
- [Android Authority — Pixel Weather data source critique](https://www.androidauthority.com/android-weather-app-setting-data-source-3663273/)
- [Beebom — Android 16 lock-screen live effects](https://gadgets.beebom.com/guides/how-to-use-lock-screen-live-effects-on-pixel-phones)
- [How-To Geek — battery drain investigation](https://www.howtogeek.com/heres-how-i-found-android-apps-that-were-secretly-draining-battery-in-the-background/)
- [9to5google — Pixel weather Material You widgets](https://9to5google.com/2021/10/18/google-weather-material-you-widgets/)

**Commercial reference**
- [Carrot Weather support](https://support.meetcarrot.com/weather/)
- [Apple WeatherKit](https://developer.apple.com/weatherkit/)
- [Tomorrow.io API](https://www.tomorrow.io/weather-api/)
- [Windy.com](https://www.windy.com/)
