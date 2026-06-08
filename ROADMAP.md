# ZeusWatch Roadmap

**Current Version**: v1.21.1 (phone versionCode 89, wear v1.21.0 versionCode 64)
**Architecture**: Kotlin 2.1.0 / Jetpack Compose / Hilt / MVVM / multi-module (phone + wear)
**Flavors**: `standard` (Google Play services, Gemini Nano, Firestore, Wear DataLayer) / `freenet` (F-Droid clean)
**License**: LGPL-3.0
**Last refreshed**: 2026-06-06 - autonomous continuation pass covering repo state, dependency runway, cache architecture, community-report hardening, provider-depth ranking, and continuation scheduling.

> This document is the working plan. It is dense by design. Every claim in the prose maps to a source in the Appendix. Items are organized by horizon (Now / Next / Later) and by theme. Closed items move to [COMPLETED.md](COMPLETED.md). Current research synthesis lives in [RESEARCH_REPORT.md](RESEARCH_REPORT.md).

---

## Completed Milestones

Moved to [COMPLETED.md](COMPLETED.md). High-water marks for context: 7 forecast/alert providers wired, 28 reorderable cards, native MapLibre radar with Blitzortung lightning, Wear OS DataLayer sync with on-device fallback, ACRA crash reporting, multi-source resilience, full Glance widget set, accessibility semantics on every Canvas-heavy card, Detekt baseline + CI emulator a11y, and 55 Kotlin test files as of the 2026-05-17 repository inventory.

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
- **T-HEALTH — Defend the blue-ocean differentiators.** Migraine/pressure/arthritis/driving/pet are uncontested. Expand them, harden them, ship them by default. Already validated in [v1.12.0 health alert system](COMPLETED.md).
- **T-I18N — Make it speak everyone's language.** The localization extraction is in flight (single `values-es` locale). It is the longest open thread in the codebase. Source: [Unreleased CHANGELOG entries](CHANGELOG.md).
- **T-PERF — Cache warm, frame fast, battery flat.** Baseline Profiles + offline-first per-location cache + smarter background cadence. Battery drain is the #1 user complaint against weather apps. Source: [unstar.app 2026 complaint ranking](https://unstar.app/blog/weather-apps-ranked-by-user-complaints-2026).
- **T-RELIABILITY — Adversarial audits keep working.** v1.20.1/2 found 11 latent bugs across timezones, sync, modulo, dispatch. Schedule the next round; expand test surfaces (Wear, AI engine, WFF).
- **T-ECOSYSTEM — Be a citizen, not an island.** ContentProvider for other apps, Tasker intents, Smartspacer target, Home Assistant integration, Android Auto. Breezy added a [ContentProvider in v6.1.0](https://github.com/breezy-weather/breezy-weather/blob/main/CHANGELOG.md) — table-stakes signal.

---

## 2026-05-17 Research Delta

The 2026-05-17 autonomous audit created a canonical context file and a durable research bundle under [.ai/research/2026-05-17/](.ai/research/2026-05-17/). The repository inventory in [STATE_OF_REPO.md](.ai/research/2026-05-17/STATE_OF_REPO.md) confirmed that run's live project was v1.20.3, had 353 tracked files and 259 Kotlin files, and was six commits ahead of `origin/main` before the research commit.

Key changes to planning:

- Localization is still the highest-compounding Now item: the local sweep now has 925 default app strings, 925 Spanish app strings, and a CI hardcoded-string gate. See [docs/TRANSLATION.md](docs/TRANSLATION.md) and N-1.
- Security/dependency work deserves its own explicit Now item. The sampled OSV query for current Maven coordinates returned no known advisories, but the dependency runway is wide enough to require staged compatibility work rather than incidental bumps. See [SECURITY_AND_DEPENDENCY_REVIEW.md](.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md) and N-10.
- External source saturation did not change the strategic order: Wear OS remains the moat, provider depth remains the biggest parity gap against Breezy, and health/safety cards remain the most differentiated feature family. See [COMPETITOR_MATRIX.md](.ai/research/2026-05-17/COMPETITOR_MATRIX.md) and [PRIORITIZATION_MATRIX.md](.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md).
- Dataset/API research added concrete candidates for Météo-France, GeoSphere Austria, NOAA SWPC aurora/Kp, Open-Meteo Marine/Flood/Ensemble, and BOM via Open-Meteo's ACCESS-G model path. See [DATASET_MODEL_INTEGRATION_REVIEW.md](.ai/research/2026-05-17/DATASET_MODEL_INTEGRATION_REVIEW.md).

---

## 2026-06-06 Autonomous Continuation Delta

This pass resumed from the current tracked roadmap after `git log -10 --oneline` showed `a3d98b7 docs: consolidate roadmap planning` at `HEAD` and `git status --short --branch` showed a clean `main...origin/main`. `rtk git log -10` was attempted per the shared session-start ritual but `rtk` was not on PATH in this PowerShell shell, so normal `git` was used and the tool gap is tracked in Continuation State.

Local verification changed the near-term plan in four places:

- The app and README are current at phone `versionName = "1.21.1"` / `versionCode = 89`, while Wear remains `versionName = "1.21.0"` / `versionCode = 64`; the version divergence is still deliberate unless Play distribution needs alignment. Evidence: [app/build.gradle.kts](app/build.gradle.kts), [wear/build.gradle.kts](wear/build.gradle.kts), [README.md](README.md).
- Localization is stronger than the May roadmap text: app English/Spanish string counts are 926/926 and Wear English/Spanish counts are 43/43 in this checkout. Keep N-1 focused on Weblate/community locales, not local Spanish parity. Evidence: [docs/TRANSLATION.md](docs/TRANSLATION.md), `app/src/main/res/values*/strings.xml`, `wear/src/main/res/values*/strings.xml`.
- Dependency runway needs a more precise Lane B: official AndroidX release pages now list WorkManager 2.11.2 stable, Room 2.8.4 stable, Wear Tiles 1.6.0 stable, ProtoLayout 1.4.0 stable, and Glance 1.2.0-rc01 / 1.3.0-alpha01 while the project remains on WorkManager 2.10.0, Room 2.6.1, Tiles 1.4.1, ProtoLayout 1.2.1, and Glance 1.1.1. Sources: [WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work), [Room releases](https://developer.android.com/jetpack/androidx/releases/room), [Wear Tiles releases](https://developer.android.com/jetpack/androidx/releases/wear-tiles), [ProtoLayout releases](https://developer.android.com/jetpack/androidx/releases/wear-protolayout), [Glance releases](https://developer.android.com/jetpack/androidx/releases/glance).
- Community reports need a security/product decision before expanding: `CommunityReportRepository.deleteReport()` performs a local device-id check, but [firestore.rules](firestore.rules) uses `request.resource.data.deviceId` in a delete rule even though official Firestore ownership examples use `resource.data` plus `request.auth.uid` for delete. Without Auth or App Check, anonymous report create/delete abuse remains possible. Sources: [Firestore insecure-rules guide](https://firebase.google.com/docs/firestore/security/insecure-rules), [Firebase App Check](https://firebase.google.com/docs/app-check).

Strategic market signal also remains consistent: Breezy Weather's live README still positions it as a Material 3 Expressive app with 50+ weather sources, and Breezy's source docs auto-suggest national providers plus secondary sources for missing features. That keeps T-SOURCES and provider transparency ahead of broad visual novelty. Sources: [Breezy Weather](https://github.com/breezy-weather/breezy-weather), [Breezy SOURCES.md](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md).

---

## 2026-06-06 Cycle 6 Provider-Depth Gap Ranking

Cycle 6 compared ZeusWatch's local provider surface in [WeatherSource.kt](app/src/main/java/com/sysadmindoc/nimbus/data/repository/WeatherSource.kt) and [WeatherSourceManager.kt](app/src/main/java/com/sysadmindoc/nimbus/data/repository/WeatherSourceManager.kt) against Breezy Weather's live source catalog plus official national API docs. Local reality: ZeusWatch has selectable forecast providers for Open-Meteo, Open-Meteo BOM ACCESS-G, OpenWeatherMap, Pirate Weather, Bright Sky, MET Norway, and Environment Canada; alert providers cover NWS, MeteoAlarm, JMA, Environment Canada, OpenWeatherMap, and Bright Sky; air quality is Open-Meteo or OWM; minutely is Open-Meteo only. Breezy's catalog still shows a much wider regional matrix and, more importantly, an auto-suggest pattern: national source first, secondary sources for missing features, Open-Meteo fallback when no national source is available. Source: [Breezy SOURCES.md](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md).

### Provider Expansion Priority Table

| Rank | Provider | Coverage / Data | Auth / Terms | Why It Matters For ZeusWatch | Recommended Roadmap Action | Priority |
|---|---|---|---|---|---|---|
| 1 | FMI | Finland / Åland forecast, current, AQ, alerts, normals per Breezy; official WFS has stored queries and CC terms; FMI says registration is no longer required. | No API key; CC license; documented request limits. | Best source-count win with low proprietary risk. It extends the existing Nordic MET Norway story and adds alert/AQ depth without a required key. | Promote NX-3 from "FMI + KNMI bundle" to "FMI first, KNMI after key-handling decision." Add WFS adapter spike before code. | P1 |
| 2 | DMI Forecast EDR | Denmark, Greenland, Faroe forecast/alerts in Breezy; DMI documents Open Data APIs, Forecast Data EDR, HARMONIE model collections, and smaller point/parameter queries. | Free/open but registration/API-key handling still needs exact verification before implementation. | Fills Denmark/Greenland/Faroe gap and pairs naturally with MET Norway + FMI for Nordic/Arctic credibility. Forecast EDR is closer to mobile use than downloading full STAC files. | Queue after FMI and NX-20 metadata; start with forecast-only, then alerts if the warning API is stable. | P1 |
| 3 | Météo-France via Open-Meteo + official Vigilance | France/overseas; Open-Meteo `/v1/meteofrance` exposes ARPEGE/AROME hourly forecast and 15-minute AROME families; official Vigilance API data is on data.gouv but accessed through Météo-France API portal. | Forecast path is no-key through Open-Meteo; Vigilance needs Météo-France API portal/JWT. | Highest nowcast upgrade for France and neighboring regions, and a direct answer to commercial minute-forecast products without bundling commercial keys. | Keep NX-1 high, but split into no-key forecast/nowcast first and keyed official Vigilance second. | P1 |
| 4 | HKO | Hong Kong 9-day forecast, current report, local forecast, weather warnings, special weather tips, rainfall/nowcast datasets. | Public HKO/data.gov.hk open data; likely no app-specific key. | Compact, high-quality regional win with forecast + current + warnings + short-nowcast data and clear update cadences. | Add HKO as a P1/P2 Asia-Pacific adapter after the Nordic/France batch. | P1/P2 |
| 5 | BMKG | Indonesia forecast/current/AQ/alerts per Breezy; official BMKG warning CAP endpoint is public, bilingual, and rate-limited to 60 requests/minute/IP. | No key seen for warning CAP; attribution required; forecast endpoint needs current official-shape verification. | Strong safety/alert value for a large weather-risk region; CAP format fits the existing alert-adapter interface. | Add BMKG alert adapter before full forecast adapter; document attribution in provider metadata. | P2 |
| 6 | KNMI | Netherlands forecast/alerts/normals in Breezy; official KNMI Open Data API is file-based and protected by API keys, with anonymous/shared and registered key modes. | API key in Authorization header; anonymous key rotates and has shared quota. | Valuable but not compatible with "no required API keys" as a default source unless exposed as optional and user-provided. | Keep candidate, but do not bundle with FMI. Gate behind provider metadata, optional key UI, and cache/deprecation-header handling. | P2 |
| 7 | CWA Taiwan | Taiwan forecast/current/AQ/alerts/normals/address in Breezy; official open-data site exposes warning and forecast datasets and API formats. | Requires free key per Breezy; official datasets are documented but localization/model mapping is higher effort. | High regional value but key requirement and Chinese-language schemas make it less immediate than HKO/BMKG. | Keep in Later unless a Taiwan user signal appears. | P2/P3 |
| 8 | SMHI | Sweden forecast in Breezy; official Open Data portal has meteorological forecast docs, including current SNOW model paths. | Keyless open-data docs appear available; endpoint migration from `pmp3g` to `snow1g` needs exact verification. | Sweden is partly covered by MET Norway/Open-Meteo but a national source would complete the Nordic set. | Revisit after FMI/DMI; first run a live endpoint and parameter mapping spike. | P2 |

### Cross-Cutting Source Lessons

- **Source expansion needs metadata, not just enum entries.** New providers need attribution text, license URL, auth type, quota/rate-limit notes, region coverage, fallback behavior, and whether they are allowed in `freenet`. Otherwise Settings will keep accreting special cases like the current OWM/Pirate key field checks in [SettingsScreen.kt](app/src/main/java/com/sysadmindoc/nimbus/ui/screen/settings/SettingsScreen.kt).
- **Do not bundle rotating or shared anonymous keys.** KNMI's anonymous key model and shared quota are a warning sign. Optional user-provided keys preserve the no-required-key guardrail; no-key national APIs and Open-Meteo model proxies should be preferred.
- **Alert-first can be higher value than forecast-first in risk regions.** BMKG CAP, HKO warnings, DMI/FMI alerts, and Météo-France Vigilance can improve safety without taking over the primary forecast source.
- **Provider-aware cache (NX-6) is a dependency for serious source depth.** Adding more providers without normalized provider-aware caching increases API traffic, weakens offline switching, and makes fallback overwrite behavior ambiguous.
- **Auto-suggestion is a UX differentiator.** Breezy suggests national sources and supplements missing features. ZeusWatch should eventually auto-recommend "FMI forecast + Open-Meteo pollen + MeteoAlarm alerts" style bundles instead of forcing users to understand every provider's strengths.

---

## 2026-06-06 Cycle 7 Radar / Lightning Lifecycle Findings

Cycle 7 inspected [RadarScreen.kt](app/src/main/java/com/sysadmindoc/nimbus/ui/screen/radar/RadarScreen.kt), [RadarMapView.kt](app/src/main/java/com/sysadmindoc/nimbus/ui/screen/radar/RadarMapView.kt), [RadarViewModel.kt](app/src/main/java/com/sysadmindoc/nimbus/ui/screen/radar/RadarViewModel.kt), [RadarRepository.kt](app/src/main/java/com/sysadmindoc/nimbus/data/repository/RadarRepository.kt), [RainViewerApi.kt](app/src/main/java/com/sysadmindoc/nimbus/data/api/RainViewerApi.kt), [BlitzortungService.kt](app/src/main/java/com/sysadmindoc/nimbus/data/api/BlitzortungService.kt), and the existing radar unit tests. The native radar stack is useful and already has ViewModel-level guards, but the next roadmap item should be reliability/compliance hardening, not only a MapLibre version bump.

### Radar / Lightning Gap Ranking

| Rank | Gap | Local Evidence | External Evidence | Recommended Roadmap Action | Priority |
|---|---|---|---|---|---|
| 1 | RainViewer attribution, host, and cache compliance | `RainViewerApi.buildTileUrl()` hardcodes `https://tilecache.rainviewer.com`; `RadarRepository` ignores `RainViewerResponse.host`; native radar UI relies on MapLibre attribution but does not show a visible RainViewer credit; offline mode hides any in-memory frames. | RainViewer docs say clients should use `host` + `path`, display Rain Viewer attribution, cache aggressively, and expect no SLA. Sources: [Weather Maps API](https://www.rainviewer.com/api/weather-maps-api.html), [RainViewer API terms](https://www.rainviewer.com/api.html). | Add NX-21. Use response host, add visible data-source attribution, persist frame metadata/last frame, and degrade gracefully when JSON/tile fetch fails. | P1 |
| 2 | MapLibre lifecycle completeness | `RadarMapView` forwards `onStart/onResume/onPause/onStop/onDestroy` and calls `onDestroy()` again in `onDispose`, but does not call `onCreate(null)`, `onLowMemory()`, or `onSaveInstanceState()`. | MapLibre docs require parent lifecycle forwarding; the official Android quickstart includes `onLowMemory()` and `onSaveInstanceState()` in addition to start/resume/pause/stop/destroy. Sources: [MapView docs](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-view/), [MapLibre Android quickstart](https://maplibre.org/maplibre-native/android/examples/getting-started/). | NX-21 should introduce a guarded lifecycle wrapper, avoid double destroy, persist camera state, and add instrumentation or Robolectric coverage. | P1 |
| 3 | Blitzortung source-policy risk for push alerts | `BlitzortungService` connects to a global WebSocket and NX-16 currently proposes high-priority lightning proximity notifications. | LightningMaps/Blitzortung docs describe the network as private/entertainment/non-commercial and not for protection of life or property. Sources: [LightningMaps About](https://www.lightningmaps.org/about?lang=en), [LightningMaps docs](https://docs.lightningmaps.org/?1740696779=). | Gate NX-16 on explicit permission or an alternate alert-grade lightning source; keep current overlay as informational only until source policy is settled. | P1 |
| 4 | WebSocket backoff and spatial throttling | `BlitzortungService.onFailure()` clears the socket but does not schedule reconnect/backoff; the buffer keeps the last 500 global strikes and `RadarMapView` rebuilds GeoJSON for every strike-list update. | Community lightning feeds can be bursty and are not guaranteed services; app-side throttling protects battery and renderer stability. | Add reconnect backoff, dedupe/throttle, distance or viewport filtering, and tests for buffer eviction + reconnect policy. | P2 |
| 5 | Test surface stops before rendering/source behavior | Existing tests cover `RadarViewModel` playback guards and `RadarScreen` helper predicates, but there are no tests for `RainViewerApi.buildTileUrl`, `RadarRepository` host/path mapping, Blitzortung JSON parse/buffer eviction, or MapLibre layer lifecycle. | RainViewer and MapLibre behavior is integration-heavy; without tests, dependency upgrades can silently break radar. | Add targeted unit tests plus a macrobenchmark/instrumented smoke test for native radar tab open, playback start, layer switch, and offline fallback. | P2 |

---

## NOW — Current cycle (target v1.21.x – v1.22.x)

In-flight or top of the queue. Each item already has enough scope context that an engineer can pick it up cold.

### N-1. Finish the localization extraction and add a translation pipeline · **T-I18N**
**Status**: PARTIAL / EXTERNAL-SERVICE BLOCKER (local extraction gate landed; app `values-es` now matches default coverage; Weblate project setup + community locales still require external service access and translators).
**Why it stays Now**: Hardcoded strings compound with every new feature. Closing extraction once is cheaper than retrofitting after every release.
**Scope**: Sweep remaining Today card internals + dialogs. Add a `values/strings.xml` lint gate in CI. Wire **Weblate** (FOSS-friendly, used by Breezy) for community translation; budget 4–6 weeks for initial pulls. Configure Crowdin Android SDK only if OTA translation delivery is desired (adds JitPack dep). Sources: [Translation_and_Localization on F-Droid](https://f-droid.org/docs/Translation_and_Localization/), [Crowdin mobile SDK](https://github.com/crowdin/mobile-sdk-android).
**Local progress**: `tools/check_localization.py` now gates high-signal hardcoded Kotlin UI strings in CI; app Spanish coverage is 925/925 strings with 0 missing entries; Wear Spanish coverage is complete for the current Wear string surface; [docs/TRANSLATION.md](docs/TRANSLATION.md) documents local checks and Weblate component setup.
**Done when**: external Weblate project is connected; ≥3 community locales are merged; the existing CI localization gate and Android lint stay green.

### N-2. Populate `ApiCertificatePins.hostPins` · **T-RELIABILITY**
**Status**: CLOSED locally on 2026-05-17 (`hostPins` populated for the current keyed hosts; Bash and PowerShell capture paths verified).
**Scope**: Run `tools/capture_api_pins.sh` or `tools/capture_api_pins.ps1` against `api.openweathermap.org` and `api.pirateweather.net` once a release. `OpenWeatherMapApi.AIR_POLLUTION_BASE_URL` is also `api.openweathermap.org`, so the same host entry covers OWM forecast and OWM AQI. The [two-pin invariant test](app/src/test/java/com/sysadmindoc/nimbus/data/api/ApiCertificatePinsTest.kt) now also checks current host coverage and non-placeholder pins. Keep Open-Meteo/NWS/Bright Sky/ECCC/MeteoAlarm/JMA/MET Norway unpinned unless a keyed or high-risk path is added.
**Completion evidence**: `ApiCertificatePins.hostPins` has live captured leaf + intermediate/root SPKI pins for OpenWeatherMap and Pirate Weather; `tools/capture_api_pins.sh` no longer fails under strict mode; `tools/capture_api_pins.ps1` resolves Git-for-Windows OpenSSL when PowerShell PATH lacks `openssl`; `docs/RELEASE.md` documents both capture paths and the committed-public-pin model.

### N-3. Bureau of Meteorology (Australia) forecast adapter · **T-SOURCES**
**Status**: CLOSED locally on 2026-05-17 via the safe indirect path. Existing roadmap flags **legal risk** with the undocumented `api.weather.bom.gov.au`, so the implementation intentionally uses Open-Meteo's documented [BOM ACCESS-G model proxy](https://open-meteo.com/en/docs/bom-api).
**Scope**: The selectable forecast provider is `Open-Meteo + BOM ACCESS-G`, backed by `/v1/bom` and a BOM-specific hourly/daily variable set. Direct reverse-engineered BOM app APIs remain deferred unless BOM publishes stable terms for them. AU severe weather warnings continue through the existing alert-source fallback surface; no direct BOM warnings API was added in this batch.
**Completion evidence**: `WeatherSourceProvider.OPEN_METEO_BOM` is exposed as a forecast source, `OpenMeteoApi.getBomForecast` calls `/v1/bom`, `WeatherRepository.getBomWeatherDirect` maps hourly-only BOM responses into current/hourly/daily `WeatherData`, and `WeatherSourceManagerTest` + `WeatherRepositoryTest` cover provider routing and hourly-only mapping.

### N-4. Watch face complication suite (extend, don't build a face) · **T-WEAR**
**Status**: CLOSED locally on 2026-05-17. `WeatherComplicationService` now advertises `SHORT_TEXT,LONG_TEXT,RANGED_VALUE,SMALL_IMAGE`; `LONG_TEXT` renders condition plus high/low title and `SMALL_IMAGE` exposes a weather icon.
**Why now, not "build a watch face"**: Wear OS 6+ requires the [declarative Watch Face Format](https://developer.android.com/training/wearables/wff) for new installs (as of Jan 2026 per [WFF release notes](https://developer.android.com/training/wearables/wff/release-notes)); building a custom runtime face is now blocked. Complications are how third-party data reaches user-chosen faces.
**Completion evidence**: [WeatherComplicationDataFactoryTest](wear/src/test/java/com/sysadmindoc/nimbus/wear/complication/WeatherComplicationDataFactoryTest.kt) covers preview and current-weather data for all declared types, UV range clamping, high/low title text, and small-image propagation. [WEAR_OS.md](docs/WEAR_OS.md) documents the install/use flow. Tile preview drawable already exists.

### N-5. WFF `[WEATHER.*]` data provider · **T-WEAR**
**Status**: CLOSED as a compatibility decision on 2026-05-17. WFF 2+ exposes [`[WEATHER.TEMPERATURE]`, `[WEATHER.CONDITION]`, `[WEATHER.HOURS.N.*]`, `[WEATHER.DAYS.N.*]`](https://developer.android.com/training/wearables/wff/weather), but the public docs and AndroidX release notes do not expose a normal third-party publisher API for apps to write into the Wear OS system weather store. AndroidX briefly exposed a weather data-source path during `wear-watchface` 1.2.0 beta work, then [removed it in beta02](https://developer.android.com/jetpack/androidx/releases/wear-watchface).
**Completion evidence**: [WFF_WEATHER_INTEROP.md](docs/WFF_WEATHER_INTEROP.md) documents the source-backed decision, compatibility matrix, local DataLayer boundary, and future API watchlist. No settings toggle was added because there is no public sink for it to control. ZeusWatch data reaches arbitrary user-selected faces through the complication suite closed in N-4.

### N-6. Test coverage: Wear OS code path · **T-RELIABILITY**
**Status**: CLOSED locally on 2026-05-17 — wear test coverage now spans repository helpers, `SyncedWeatherStore`, complication data, direct API fetch mapping, phone-sync short-circuit behavior, API error handling, and tile request futures.
**Completion evidence**: [WearWeatherRepositoryTest](wear/src/test/java/com/sysadmindoc/nimbus/wear/data/WearWeatherRepositoryTest.kt) covers mocked OkHttp success/failure and fresh sync bypass. [WeatherTileServiceTest](wear/src/test/java/com/sysadmindoc/nimbus/wear/tile/WeatherTileServiceTest.kt) covers tile data loading, the `CallbackToFutureAdapter` happy path through `WeatherTileRequestRunner`, and tile resource futures. Full `:wear:testDebugUnitTest`, `detekt`, and `:wear:lintDebug` passed.

### N-7. `GeminiNanoSummaryEngine` test coverage · **T-RELIABILITY**
**Status**: **CLOSED** — `GeminiNanoSummaryEngineTest` (10 assertions) lives in `app/src/testStandard/` (new flavor-specific test source set). Covers:
- `buildPrompt` shape: preamble, full weather context interpolation, ordering invariant (high/low → rain → wind), `precipChance == 0` omission, `precipChance > 0` inclusion, `uvIndex.toInt()` truncation, unit-symbol passthrough.
- Lifecycle fallbacks: constructor degrades to unavailable when AI Core runtime is missing (the unit-test JVM and the unsupported-device path), `generate` returns null when unavailable, `generate` returns null after `close()`, `close()` is idempotent, `SummaryEngine` interface contract preserved.
- Not planned for this item: live `generateContent` happy-path (GenerativeModel isn't openly mockable; the delegate-and-fallback path is already covered by `WeatherSummaryEngineWithStyleTest` from v1.20.0).
Supporting refactor: extracted `buildPrompt` to a companion object `internal fun` so the prompt format is unit-testable without touching the model.

### N-8. Detekt baseline reduction · **T-RELIABILITY**
**Status**: ongoing. Baseline is down to **4 IDs** after extracting `RadarPreviewCard`, `CurrentConditionsHeader`, `CustomAlertsScreen.RuleEditor`, `SunArc`, `TemperatureGraph`, `WidgetRefreshWorker`, `SyncedWeatherStore`, `CompareScreen`, `RadarScreen`, `MainViewModel`, `SettingsScreen`, and `MainScreen.RenderCard` helpers/payloads on 2026-05-17, then regenerating `config/detekt/baseline.xml`. Earlier delta: **-2 entries** (MeteoconMapper.getLottieAsset and OwmConditionMapper.toWmoCode refactored from 28-/40-branch `when` expressions to O(1) static lookup maps); **+3 entries** captured during the i18n sweep are now cleared. Remaining baseline entries are in `MainScreen` and `WeatherContent`.
**Scope**: Chip away during normal feature work. Target: baseline empty by v1.25.0. Extract Compose helpers, not refactors-for-the-sake-of-refactor.

### N-9. Widget pure-function test coverage · **T-RELIABILITY**
**Status**: **PARTIAL** — `WidgetThemeTest` (16 assertions) covers `widgetUpdatedLabel` (freshness badge, NTP rollback guard, locale-aware format overrides) and `weatherIconRes` (every WMO band → drawable, day/night clear-sky variant). Rescoped from the original "runGlanceAppWidgetUnitTest per widget" plan because that helper ships in `androidx.glance:glance-testing` 1.2.0+ and the repo is on Glance 1.1.1. Source: [Glance release notes](https://developer.android.com/jetpack/androidx/releases/glance).
**Scope**: To use `runGlanceAppWidgetUnitTest`, upgrade Glance to 1.2.0+ — bump-and-test as a separate item once the rest of the Compose BOM is stable on 2025.04.01 (the current pin). Until then, keep adding pure-function tests for any helpers introduced into the widget package.

### N-10. Dependency runway and platform compatibility pass · **T-RELIABILITY** / **T-PERF**
**Status**: not started; refreshed on 2026-06-06 against official AndroidX release pages.
**Scope**: Split into three lanes. Lane A: low-risk patch upgrades with unchanged APIs, one PR, normal unit/lint/build verification. Lane B: feature-enabling AndroidX upgrades with dedicated verification slices: WorkManager 2.10.0 -> 2.11.2 plus worker scheduling tests; Room 2.6.1 -> 2.8.4 plus schema diff/migration tests; Wear Tiles 1.4.1 -> 1.6.0 and ProtoLayout 1.2.1 -> 1.4.0 plus tile/complication tests; Glance 1.1.1 -> 1.2.0-rc01 only when the widget-test payoff is worth riding an RC, otherwise wait for stable and keep N-9 on pure-function tests. Lane C: architecture-affecting upgrades (Retrofit 3.0.0, OkHttp 5.3.2, MapLibre 13.2.0, Kotlin stable 2.3.x/2.4 preview, Gradle 9.x, AGP 9.x alpha) only after an issue/branch names the migration risk and rollback path.
**Done when**: [SECURITY_AND_DEPENDENCY_REVIEW.md](.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md) is converted into actionable dependency issues or PRs; CI still covers Detekt, lint, unit tests, debug builds, connected a11y tests, and release build verification; each Lane B bump records the exact before/after version and any test gaps in this roadmap or release notes.

### N-11. Community reports integrity and ownership hardening · **T-RELIABILITY** / security
**Status**: newly opened on 2026-06-06 from local rules/code inspection.
**Problem**: Community reports are a useful differentiator on the Radar screen, but the current integrity boundary is mostly client-side. `CommunityReportRepository.submitReport()` rate-limits only in process memory, `deleteReport()` checks local `deviceId` before calling Firestore delete, and [firestore.rules](firestore.rules) validates create shape but allows any `condition` string up to 50 chars and attempts delete ownership with `request.resource.data.deviceId`. Firestore's own examples use `request.auth.uid == resource.data.author_uid` for delete ownership, so this path should be assumed fragile until rules tests prove otherwise. App Check can reduce unauthorized-client abuse, but Firebase documents it as complementary to Auth, not a user-ownership replacement.
**Recommended decision**: choose one explicit product model before adding report features.
- **Append-only anonymous model**: remove user deletion from the public UX, add Firestore TTL/cleanup for stale reports, tighten create rules to `keys().hasOnly([...])`, constrain `condition` to the enum names in [CommunityReport.kt](app/src/main/java/com/sysadmindoc/nimbus/data/model/CommunityReport.kt), require `timestamp` near `request.time`, and enable App Check for the `standard` flavor.
- **Owner-managed model**: add Firebase Anonymous Auth in `standard`, store `ownerUid`, update rules to `allow create: if request.auth.uid == request.resource.data.ownerUid` and `allow delete: if request.auth.uid == resource.data.ownerUid`, keep hashed device id only as local telemetry/abuse signal, and add emulator rules tests.
**Acceptance criteria**:
- [ ] Firestore emulator tests cover valid create, malformed create, stale timestamp, invalid condition, owner delete, non-owner delete, and anonymous/no-auth behavior.
- [ ] Radar report submit/delete UI reflects the chosen model and never promises deletion if the backend cannot enforce it.
- [ ] App Check rollout is documented for `standard`; `freenet` remains unaffected and never depends on Firebase.
- [ ] Abuse controls are server-enforced, not only local process-memory rate limits.
**Impact / effort / confidence**: User value 4, product value 4, strategic differentiation 3, confidence 5, effort 3 => score 13, P1. Risk: adding Auth/App Check increases proprietary surface in `standard`; keep it flavor-contained and document the F-Droid boundary.

---

## NEXT — 2–3 release cycles out (target v1.23 – v1.26)

Sequenced after Now items land. Each entry is concrete enough to scope; none of them require research that hasn't already happened.

### NX-1. Météo-France adapter (forecast + AROME nowcast + vigilance alerts) · **T-SOURCES**
Free API key registration on [portail-api.meteofrance.fr](https://portail-api.meteofrance.fr). Adds: 14-day forecast, PIAF 5-min nowcast for France (180-min look-ahead), Vigilance severe-alert color codes for FR. Source: [Météo-France API doc](https://open-meteo.com/en/docs/meteofrance-api), [Breezy MF entry](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md). Effort: medium (JWT token, two-step fetch).

### NX-2. GeoSphere Austria adapter (INCA nowcast + alerts) · **T-SOURCES**
[CC0 license](https://data.hub.geosphere.at/dataset/nowcast-v1-15min-1km), no key, 15-min/1-km nowcast for Austria + nearby Alps. Highest-quality Alpine coverage; fills a gap Open-Meteo can't match at that resolution. Source: [GeoSphere data hub](https://data.hub.geosphere.at/), [Breezy SOURCES.md](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md). Effort: medium.

### NX-3. FMI (Finland) adapter first; KNMI optional after key-policy decision · **T-SOURCES**
Cycle 6 changed this from a bundled FMI+KNMI source-count PR into a staged provider-compliance item. FMI is the safer first adapter: Breezy lists forecast/current/AQI/alerts/normals for Finland/Åland, the official FMI WFS 2.0 docs expose stored queries such as point forecasts, and FMI says registration/API keys are no longer required. Scope: add an FMI API interface, response parser, source metadata, attribution/license text, and tests for forecast + alert/AQI availability; start with forecast if WFS alert/AQI mapping needs a second PR. KNMI remains valuable for the Netherlands, but the official KNMI Open Data API requires an Authorization key, with anonymous/shared and registered-key quota models. Do not bundle KNMI with FMI or ship a shared key; revisit after NX-20 gives Settings a generic optional-key/auth-mode model. Sources: [FMI WFS manual](https://en.ilmatieteenlaitos.fi/open-data-manual-fmi-wfs-services?doAsUserLanguageId=en_US), [FMI no-key announcement](https://en.ilmatieteenlaitos.fi/news/963113482), [KNMI Open Data API](https://developer.dataplatform.knmi.nl/open-data-api), [Breezy SOURCES.md](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md). Effort: medium.

### NX-4. Open-Meteo pollen card refresh + Marine + Flood APIs · **T-SOURCES** / **T-HEALTH**
Pollen is already in `AirQualityRepository`; the [docs](https://open-meteo.com/en/docs/air-quality-api) note CAMS coverage is European-only — surface this explicitly in the empty state. Add **Marine** ([wave height/period/direction, ocean current, sea surface temp](https://open-meteo.com/en/docs/marine-weather-api)) as a new opt-in card for coastal users. Add **Flood** (GloFAS river discharge, 30-day) as a new opt-in card for river-near users. Effort: low each.

### NX-5. Multi-provider agreement card · **T-RELIABILITY**
Fetch the next 24h temp + precip from 2–3 enabled providers in parallel; render a "providers agree within ±X°" or "diverge >5°" badge with a tap-to-expand showing each provider's value. This is the FOSS answer to AccuWeather's MinuteCast / Carrot's multi-model. Borrowed from the [Breezy idea](https://github.com/breezy-weather/breezy-weather) of leveraging the multi-source system for transparency, not just resilience. Effort: medium. Risk: API quota multiplication — gate behind opt-in.

### NX-6. Per-location offline-first cache · **T-PERF**
Existing roadmap calls this out: "switching to a saved location triggers a fresh API call." The 2026-06-06 code pass found that the current cache is useful but narrower than this item previously implied: [WeatherCacheEntity](app/src/main/java/com/sysadmindoc/nimbus/data/model/WeatherCacheEntity.kt) stores a serialized `OpenMeteoResponse` keyed by rounded coordinates, [WeatherRepository.getCachedWeather](app/src/main/java/com/sysadmindoc/nimbus/data/repository/WeatherRepository.kt) decodes only that Open-Meteo payload shape, and [WeatherSourceManager](app/src/main/java/com/sysadmindoc/nimbus/data/repository/WeatherSourceManager.kt) now routes many forecast providers. Upgrade this item from "wire a flow" to "make cache provider-aware." Recommended path: add a normalized `WeatherDataCacheEntity` or extend the existing entity with `sourceProvider`, `savedLocationId`, schema version, and normalized `WeatherData` JSON; serve cached normalized data immediately for every provider; refresh in background; show provider/staleness in UI. Done when saved-location switch renders <100ms with cached data, works for Open-Meteo/BOM/OWM/Pirate/Bright Sky/MET Norway/ECCC, and source fallback refresh does not overwrite fresh primary cache with older fallback data. Effort: medium.

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
Blitzortung WebSocket is already wired ([`BlitzortungService`](app/src/main/java/com/sysadmindoc/nimbus/data/api/BlitzortungService.kt)), but Cycle 7 found a source-policy blocker for push alerts: LightningMaps/Blitzortung docs describe the data as private/entertainment/non-commercial and not intended for protection of life or property. Do not ship a high-priority lightning warning from Blitzortung data without written permission or an alternate alert-grade source. First subtask: decide source policy and attribution; second subtask: if permitted, add a `LightningProximityWorker` with configurable radius (default off), distance-filtered buffering, backoff, quiet-hours/DND channel behavior, and tests. Mirrors [Carrot Ultra's storm cell alerts](https://support.meetcarrot.com/weather/) only after the data-source constraint is resolved. Effort: medium. Risk: source terms may force this to remain an informational overlay instead of a notification feature.

### NX-17. Custom-alert rule expansion · **T-HEALTH**
Custom alerts today cover 5 metrics (high, low, gusts, 24h precip, UV peak). Add: AQI threshold, dewpoint, heat index, wind chill, snowfall sum, lightning-within-N-km (depends on NX-16), and any-source severe weather event of type X. Pure expansion of `CustomAlertEvaluator`'s rule enum + UI editor. Effort: medium.

### NX-18. WCAG 2.2 AA audit + dynamic font scaling pass · accessibility / **T-RELIABILITY**
Strategic Compass #5 says "accessibility-first"; v1.18.0 closed the Canvas-a11y front and added Espresso `AccessibilityChecks.enable()` to the instrumented suite. The next pass:
1. **Contrast audit** of every weather-adaptive theme variant against [WCAG 2.2 AA](https://www.w3.org/TR/WCAG21/) (4.5:1 normal text, 3:1 large text, 3:1 graphics). The amber/blue/purple condition palettes are the highest-risk areas.
2. **Font scaling stress test** at `fontScale = 1.3 / 1.5 / 1.8` on every screen — particularly the temperature graph axis labels, daily forecast rows, and Wear badge rows. Use Compose `LocalDensity` previews.
3. **Touch target ≥48dp audit** on dense settings rows and the location selector.
4. Extend Compose UI tests with accessibility checks beyond MainScreen/Settings/Locations to all 5 phone screens.
Source: [Mobile App Accessibility 2026 guide](https://www.accessibilitychecker.org/guides/mobile-apps-accessibility/). Effort: medium.

### NX-19. Release ops + docs consolidation · upgrade-path / docs
**Status: MOSTLY CLOSED** — only the versionCode-alignment subtask remains, and it's gated on a Play Store decision.
- [x] `PHASES.md` and `PHASE8_PLAN.md` moved to `docs/phases-pre-v1.md` and `docs/phase8-plan-pre-v1.md` (frozen pre-v1.0 planning docs are no longer in the repo root).
- [x] `RESEARCH.md` moved to `docs/research-archive.md`. README pointers updated.
- [x] `docs/RELEASE.md` added documenting the per-release checklist, pin-capture procedure, CI signing-secrets contract, ACRA report-email location, distribution channels, and rollback procedure.
- [x] Fastlane `title.txt` / `short_description.txt` / `full_description.txt` reconciled with v1.20.3 reality (28 cards, 4 widget sizes, 72h hourly, 7 providers, native Wear OS, multi-source, "ZeusWatch" branding). Added `changelogs/86.txt`.
- [ ] Sync `versionCode` divergence between phone (86) and wear (62) at the next release **if** Play Store distribution is on the table; ignore otherwise (current channels treat them as independent listings).
Repo root markdown count: README, CHANGELOG, ROADMAP, COMPLETED, RESEARCH_REPORT, AGENTS, LICENSE, plus ignored local working notes — within the target once ignored local notes are excluded.

### NX-20. Provider metadata registry + regional auto-suggestion · **T-SOURCES** / **T-RELIABILITY**
Cycle 6 found that provider depth is now constrained by metadata and routing clarity as much as by missing adapters. [WeatherSource.kt](app/src/main/java/com/sysadmindoc/nimbus/data/repository/WeatherSource.kt) exposes provider capabilities, but provider-specific operational facts live implicitly in code and UI: [SettingsScreen.kt](app/src/main/java/com/sysadmindoc/nimbus/ui/screen/settings/SettingsScreen.kt) still special-cases OpenWeatherMap/Pirate Weather key fields, and [WeatherSourceManager.kt](app/src/main/java/com/sysadmindoc/nimbus/data/repository/WeatherSourceManager.kt) routes providers without a shared registry for auth, attribution, quota, region, fallback, or `freenet` compatibility. Scope: add a `ProviderMetadata` registry keyed by `WeatherSourceProvider` with `dataTypes`, country/region coverage, `authMode` (`NONE`, `USER_KEY`, `PROXY`, `UNSUPPORTED`), preference key name, attribution text, license URL, quota/rate-limit notes, freshness/update cadence, `freenetAllowed`, fallback role, and cache namespace. Replace Settings hardcoded key checks with metadata-driven UI; expose provider detail rows that explain why a source is recommended or unavailable; add a regional resolver that suggests a default bundle from lat/lon/country, for example FMI forecast + MeteoAlarm alerts + Open-Meteo AQ/pollen in Finland, DMI forecast + MeteoAlarm alerts in Denmark, or HKO forecast/warnings in Hong Kong. Acceptance criteria: source Settings render from metadata; no provider can be selected in `freenet` if metadata marks it unavailable; optional-key providers show one generic secure key workflow; provider agreement/cache logic can identify a provider namespace; tests cover recommendation output for US, Finland, France, Denmark, Hong Kong, Netherlands, Australia, and Indonesia. Risks: country detection can be wrong near borders, some national APIs have sub-region coverage quirks, and auto-suggestion must never silently replace a user-selected provider.

### NX-21. Native radar compliance, cache, and lifecycle hardening · **T-PERF** / **T-RELIABILITY**
Cycle 7 found that the native MapLibre/RainViewer path should be hardened before radar feature expansion or MapLibre 13.x adoption. Scope: update `RadarRepository` and `RainViewerApi` to use `RainViewerResponse.host` instead of a hardcoded tile host; store recent frame metadata and the last-rendered frame per location/provider so native radar can show a stale-but-labeled fallback offline; add the RainViewer coverage mask as an optional "coverage" overlay or empty-state hint where radar data is unavailable; show visible "Weather data by Rain Viewer" attribution in native radar and radar preview cards; wrap `MapView` lifecycle in a tested helper that calls `onCreate(null)`, start/resume/pause/stop/destroy once, handles `onLowMemory`, and preserves camera state; throttle lightning GeoJSON updates and filter strikes by viewport or distance before rendering; add reconnect/backoff to `BlitzortungService` if the overlay remains enabled. Acceptance criteria: native radar opens, rotates, backgrounds, returns, and disposes without duplicate-destroy crashes or camera reset; offline mode shows cached frame metadata when available and a clear no-cache state when unavailable; RainViewer attribution is visible; unit tests cover RainViewer URL/host mapping and Blitzortung parse/buffer/backoff logic; an instrumented or macrobenchmark smoke test covers native radar tab open, playback, layer switch, and offline fallback. Risks: MapLibre lifecycle behavior can differ across Android versions; RainViewer free terms are personal/small-community/no-SLA, so cache and attribution are compliance requirements, not polish.

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

### L-8. MapLibre 13.x radar compatibility and performance audit · **T-PERF**
GitHub releases now show MapLibre Android 13.2.0 as latest while ZeusWatch is pinned to 11.5.2. Keep the version bump Later until NX-21 hardens the current MapView lifecycle, RainViewer compliance/cache behavior, and radar smoke tests. The first 13.x branch should measure radar tab open time, tile load behavior, memory after repeated tab open/close, and native crash rate before adopting the renderer update. Sources: [MapLibre android-v13.2.0 release](https://github.com/maplibre/maplibre-native/releases/tag/android-v13.2.0), [MapLibre Android quickstart](https://maplibre.org/maplibre-native/android/examples/getting-started/).

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
Today crash reports are emailed via consent dialog to `matt_parker@outlook.com`. A self-hosted ACRA backend would scale better but reintroduces a "server to maintain" surface that conflicts with the no-backend stance. **Open question**: is the volume actually a problem yet? If yes, self-host. If no, status quo.

### UC-2. Anonymous usage telemetry (opt-in)
Plausible-style, no PII, opt-in toggle. Would inform "which provider do users actually pick" and "which card do users actually keep enabled" — both currently blind. Friction: an outbound endpoint at all is a F-Droid red flag and a privacy concern. **Open question**: can we live with continued blindness? If yes, reject.

### UC-3. Light theme / weather-adaptive light mode
Dark-only is part of the brand. But screen-reader users with high-photophobia inversions exist, and a few users will ask. **Open question**: opt-in scheduled light theme, or no? Existing roadmap silently treats this as out. Make it explicit.

### UC-4. Background-fetch budget controls
Battery is the #1 complaint against weather apps (28% per [unstar.app](https://unstar.app/blog/weather-apps-ranked-by-user-complaints-2026); reinforced by [howtogeek](https://www.howtogeek.com/heres-how-i-found-android-apps-that-were-secretly-draining-battery-in-the-background/) — though that piece is generic). WidgetRefreshWorker already skips at battery ≤15%. Should we be more aggressive: skip below 25%, halve cadence when in Doze, fully halt under restrictive bucket? **Open question**: is the current loss material? Instrument first (depends on UC-2 decision).

### UC-5. AccuWeather adapter via bundled key
Breezy ships a bundled AccuWeather key. AccuWeather covers global pollen + better US alerts than NWS in some cases. Friction: bundled key is paywall-adjacent and revokable. **Open question**: drop or accept the brittleness?

### UC-6. Pixel Watch / Wear OS 6 M3 Expressive UI refresh
[wear-compose-material3 1.5.0-beta01](https://developer.android.com/jetpack/androidx/releases/wear-compose-m3) lands the M3 Expressive Wear UI. Refresh once stable. Decide at that point; today's wear UI is functional. Held from NEXT only because of "wait for stable."

### UC-7. ScrollAware widget refresh on home-screen interaction
A long-tail efficiency: refresh the widget at the moment the user wakes the phone, not on a 15-min cadence. Possible via lifecycle callbacks. Friction: increases code complexity; might not actually save power. **Open question**: needs measurement (see UC-4).

---

## REJECTED — and why

Each is closed with a one-line rationale so we don't relitigate.

- **Bundled AccuWeather/Apple WeatherKit keys as default fallback.** Violates "no required API keys" + "no proprietary dep" guardrails when bundled defaults effectively bind users to those terms.
- **Custom Wear OS runtime watch face (not WFF).** [As of Jan 2026 WFF is required for Wear OS install](https://developer.android.com/training/wearables/wff). Runtime faces are deprecated for new installs.
- **Built-in ad slots / interstitials.** Antithetical to the brand; [#2 user complaint vector in 2026](https://unstar.app/blog/weather-apps-ranked-by-user-complaints-2026).
- **Subscription tier / Premium Club.** Antithetical to the brand; [drives the most complaints at Carrot/AccuWeather](https://unstar.app/blog/weather-apps-ranked-by-user-complaints-2026).
- **Audio severe-weather TTS played out of system mixer.** Leave this to TalkBack and the system-level alarm DND-bypass channels already implemented for users who want it.
- **Replace MapLibre with proprietary map SDK (Mapbox/Maps SDK for Android).** Locks `freenet`.
- **Migrating to Firebase as primary state store.** Conflicts with `freenet` parity; today Firestore is used only for opt-in community reports (`standard` flavor only) and that's the right boundary.
- **Android 16 "Local" weather wallpaper integration.** [Pixel-only and no public third-party API](https://gadgets.beebom.com/guides/how-to-use-lock-screen-live-effects-on-pixel-phones); not actionable.
- **Built-in webcam / live photo feed.** Conflicts with the product philosophy (third-party hosting, copyright, image moderation).
- **Replace Hilt with Koin/Dagger pure.** Hilt works; the [Kotlin 2.1.0 + KSP2 compatibility friction](https://github.com/google/dagger/issues/4582) is real but already navigated.
- **Move Wear app to Compose Multiplatform.** No upside today; CMP-on-Wear isn't a target.

---

## Research Log

| Date | Cycle | Research Area | Sources / Files Reviewed | Key Findings | Roadmap Changes |
|---|---|---|---|---|---|
| 2026-06-06 | Cycle 1: Repository comprehension | Current repo state, recent commits, canonical notes | `git log -10`, `git status`, [CLAUDE.md](CLAUDE.md), [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md), [README.md](README.md), [ROADMAP.md](ROADMAP.md), [RESEARCH_REPORT.md](RESEARCH_REPORT.md) | Repo is clean at `a3d98b7`; public context files are partly stale against v1.21.1; `ROADMAP.md` is canonical and should be resumed, not replaced. | Added 2026-06-06 delta and Continuation State. |
| 2026-06-06 | Cycle 2: Feature inventory | Build/version files, localization, cards, navigation, cache | [app/build.gradle.kts](app/build.gradle.kts), [wear/build.gradle.kts](wear/build.gradle.kts), [CardConfig.kt](app/src/main/java/com/sysadmindoc/nimbus/data/repository/CardConfig.kt), [WeatherRepository.kt](app/src/main/java/com/sysadmindoc/nimbus/data/repository/WeatherRepository.kt), [WeatherCacheEntity.kt](app/src/main/java/com/sysadmindoc/nimbus/data/model/WeatherCacheEntity.kt), [docs/TRANSLATION.md](docs/TRANSLATION.md) | App string parity is 926/926 and Wear parity is 43/43; 28 card types remain current; existing weather cache is Open-Meteo-response-shaped and not provider-aware. | Tightened N-1 status interpretation and rewrote NX-6 as provider-aware normalized cache work. |
| 2026-06-06 | Cycle 3: Security and backend integrity | Community reports, Firestore rules, Firebase docs | [CommunityReportRepository.kt](app/src/standard/java/com/sysadmindoc/nimbus/data/repository/CommunityReportRepository.kt), [CommunityReport.kt](app/src/main/java/com/sysadmindoc/nimbus/data/model/CommunityReport.kt), [firestore.rules](firestore.rules), [Firestore insecure-rules guide](https://firebase.google.com/docs/firestore/security/insecure-rules), [Firebase App Check](https://firebase.google.com/docs/app-check) | Delete ownership and abuse controls need a backend-enforced model; local device-id checks and process-memory rate limits are insufficient for a public report surface. | Added N-11 with append-only vs owner-managed implementation options and acceptance criteria. |
| 2026-06-06 | Cycle 4: Dependency/platform research | Official AndroidX release pages | [WorkManager](https://developer.android.com/jetpack/androidx/releases/work), [Room](https://developer.android.com/jetpack/androidx/releases/room), [Wear Tiles](https://developer.android.com/jetpack/androidx/releases/wear-tiles), [ProtoLayout](https://developer.android.com/jetpack/androidx/releases/wear-protolayout), [Glance](https://developer.android.com/jetpack/androidx/releases/glance) | WorkManager 2.11.2, Room 2.8.4, Tiles 1.6.0, and ProtoLayout 1.4.0 are stable; Glance is still stable at 1.1.1 with 1.2.0-rc01 and 1.3.0-alpha01 available. | Refreshed N-10 and Appendix D library watch. |
| 2026-06-06 | Cycle 5: Competitive/product signal | OSS competitor/source docs and Open-Meteo/F-Droid docs | [Breezy Weather](https://github.com/breezy-weather/breezy-weather), [Breezy SOURCES.md](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md), [Open-Meteo Ensemble API](https://open-meteo.com/en/docs/ensemble-api), [F-Droid Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/) | Breezy still pressures provider depth and national-source auto-suggestion; Open-Meteo ensemble remains a good uncertainty source; F-Droid reproducibility guidance supports NX-14. | Reinforced T-SOURCES, NX-5, NX-14, and future ensemble research. |
| 2026-06-06 | Cycle 6: Provider-depth gap ranking | Local provider routing plus Breezy and official national API docs | [WeatherSource.kt](app/src/main/java/com/sysadmindoc/nimbus/data/repository/WeatherSource.kt), [WeatherSourceManager.kt](app/src/main/java/com/sysadmindoc/nimbus/data/repository/WeatherSourceManager.kt), [SettingsScreen.kt](app/src/main/java/com/sysadmindoc/nimbus/ui/screen/settings/SettingsScreen.kt), [Breezy SOURCES.md](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md), [FMI WFS manual](https://en.ilmatieteenlaitos.fi/open-data-manual-fmi-wfs-services?doAsUserLanguageId=en_US), [DMI Forecast Data EDR](https://www.dmi.dk/friedata/dokumentation/forecast-data-edr-api), [HKO open data](https://www.hko.gov.hk/en/abouthko/opendata_intro.htm), [BMKG CAP warnings](https://data.bmkg.go.id/peringatan-dini-cuaca/), [KNMI Open Data API](https://developer.dataplatform.knmi.nl/open-data-api) | FMI is the best next no-key regional adapter; KNMI should not be bundled because official access is key-based; DMI, HKO, and BMKG add high-value regional depth; provider metadata is now required before safe scale-out. | Added Cycle 6 ranking, rewrote NX-3, added NX-20, refined Appendix C, and moved continuation to Cycle 7. |
| 2026-06-06 | Cycle 7: Radar and lightning lifecycle | Native radar, RainViewer, MapLibre, Blitzortung | [RadarScreen.kt](app/src/main/java/com/sysadmindoc/nimbus/ui/screen/radar/RadarScreen.kt), [RadarMapView.kt](app/src/main/java/com/sysadmindoc/nimbus/ui/screen/radar/RadarMapView.kt), [RadarViewModel.kt](app/src/main/java/com/sysadmindoc/nimbus/ui/screen/radar/RadarViewModel.kt), [RadarRepository.kt](app/src/main/java/com/sysadmindoc/nimbus/data/repository/RadarRepository.kt), [RainViewerApi.kt](app/src/main/java/com/sysadmindoc/nimbus/data/api/RainViewerApi.kt), [BlitzortungService.kt](app/src/main/java/com/sysadmindoc/nimbus/data/api/BlitzortungService.kt), [RainViewer Weather Maps API](https://www.rainviewer.com/api/weather-maps-api.html), [RainViewer API terms](https://www.rainviewer.com/api.html), [MapLibre quickstart](https://maplibre.org/maplibre-native/android/examples/getting-started/), [LightningMaps About](https://www.lightningmaps.org/about?lang=en) | Native radar needs attribution/cache/host compliance, fuller MapView lifecycle handling, Blitzortung source-policy gating before push alerts, WebSocket backoff/spatial throttling, and deeper tests. | Added Cycle 7 findings, added NX-21, tightened NX-16, refreshed L-8 and MapLibre dependency watch, and moved continuation to Cycle 8. |

---

## Next Research Cycles

1. Cycle 8: Deep-audit background jobs (`AlertCheckWorker`, `NowcastAlertWorker`, `CustomAlertWorker`, `HealthAlertWorker`, `WidgetRefreshWorker`, `DatabaseMaintenanceWorker`) for WorkManager 2.11.2 compatibility and battery budget controls.
2. Cycle 9: Inspect Wear UI and tile code against Wear Tiles 1.6.0 / ProtoLayout 1.4.0 migration requirements, including whether Lottie ProtoLayout is now practical for L-11.
3. Cycle 10: Build a rules-test plan for community reports using the Firebase emulator and decide whether anonymous Auth or append-only reports best preserve the FOSS/privacy model.
4. Cycle 11: Audit all cache consumers and saved-location flows to design the provider-aware normalized cache schema, migration path, and fallback overwrite rules.
5. Cycle 12: Re-run dependency freshness and OSV checks for every coordinate in `gradle/libs.versions.toml`, then split N-10 into exact issues/PR-ready batches.
6. Cycle 13: Review screenshots and UI source for Material 3 Expressive pressure, text scaling, touch-target density, and weather-adaptive contrast risks.
7. Cycle 14: Research Android Auto, Smartspacer, Home Assistant, Tasker, and Gadgetbridge integration expectations to prioritize T-ECOSYSTEM work.
8. Cycle 15: Inspect release/distribution docs and F-Droid metadata requirements to turn NX-14 into a reproducible-build verification checklist.
9. Cycle 16: Turn NX-20 into a schema/test plan by inspecting existing Settings, `SourceConfig`, DataStore, `freenet`, and cache consumers for every place provider metadata must replace hardcoded logic.
10. Cycle 17: Turn NX-21 into implementation steps by checking MapLibre lifecycle patterns in Compose, RainViewer status/coverage endpoints, and existing app cache primitives.

---

## Appendix A — Adversarial Review Notes

What a hostile reviewer would ask, with answers.

- **"You shipped 28 cards and 7 providers — why isn't the ROADMAP shorter?"** Because the per-card density is exactly what makes the app stand out from Breezy-style minimalism. The roadmap reflects breadth obligations: i18n compound cost, Wear OS moat extension, regional source coverage, and audit cycles. Each Now item has a closure criterion.
- **"Why no Android 16 M3 Expressive sweep in NOW?"** It's UC-6; Wear Compose M3 is still in beta01. Premature work invites churn. Phone-side: when 1.5.0 stable lands, schedule.
- **"Where's the security story?"** N-2 (cert pinning hostmap), N-10 (dependency runway), NX-14 (reproducible builds), L-13 (no-Geocoder fallback). Plus the [v1.16.0 RateLimitInterceptor + API-key debug-log redaction](COMPLETED.md) already shipped. The 2026-05-17 sampled OSV query found no known advisories in the checked Maven coordinates.
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
| [Carrot Weather Premium Ultra](https://support.meetcarrot.com/weather/) | Storm-cell + lightning push, super-res radar, multi-model forecasts, weather-map widget | NX-5 (multi-model agreement) covers the transparency angle; NX-16 only covers lightning proximity after source-policy clearance. |
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
| **Météo-France** | FR + global ARPEGE/AROME | None for Open-Meteo forecast proxy; JWT for official Vigilance | [Open-Meteo Météo-France docs](https://open-meteo.com/en/docs/meteofrance-api) for forecast/15-minute AROME families; [data.gouv Vigilance dataset](https://www.data.gouv.fr/fr/datasets/bulletin-vigilance/) and Météo-France API portal for official alert colors. Split forecast/nowcast before keyed Vigilance. |
| **GeoSphere Austria** | AT + Alps | None | [data.hub.geosphere.at](https://data.hub.geosphere.at/dataset/nowcast-v1-15min-1km). CC0. INCA 15-min/1km. |
| **FMI** | FI / Åland | None | Official [WFS manual](https://en.ilmatieteenlaitos.fi/open-data-manual-fmi-wfs-services?doAsUserLanguageId=en_US) documents stored queries and request limits; [FMI says registration is no longer required](https://en.ilmatieteenlaitos.fi/news/963113482). First Cycle 6 provider candidate. |
| **DMI Forecast Data EDR** | DK / Greenland / Faroe Islands | Free/open; key/registration flow needs final verification | [DMI Open Data APIs](https://www.dmi.dk/friedata/dokumentation/apis) and [Forecast Data EDR API](https://www.dmi.dk/friedata/dokumentation/forecast-data-edr-api) expose HARMONIE forecast collections and point/parameter style queries. Forecast-only first, alerts second. |
| **KNMI** | NL | Key | [KNMI Open Data API](https://developer.dataplatform.knmi.nl/open-data-api) requires Authorization. Anonymous/shared key rotates and has shared quota; registered keys have stronger limits. Optional-user-key only after NX-20. |
| **SMHI Sweden** | SE | None | [SMHI Open Data](https://opendata.smhi.se/) and [meteorological forecast docs](https://opendata.smhi.se/metfcst/) remain candidates; verify current `snow1g` endpoint shape before implementation. |
| **AEMET Spain** | ES | Free key | Two-step fetch URL pattern. |
| **HKO Hong Kong** | HK | None | [HKO open data](https://www.hko.gov.hk/en/abouthko/opendata_intro.htm) includes 9-day forecast, current report, local forecast, warnings, special weather tips, and rainfall nowcast datasets. High-value compact regional adapter. |
| **CWA Taiwan** | TW | Free key | [Breezy SOURCES.md CWA entry](https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md). |
| **BMKG Indonesia** | ID | None for warning CAP seen in Cycle 6 | [BMKG weather warning CAP](https://data.bmkg.go.id/peringatan-dini-cuaca/) is public, bilingual, attribution-bound, and rate-limited to 60 requests/min/IP. Add alert adapter before full forecast. |
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
| Compose BOM | 2025.04.01 | 2026.05.00 watch | M3 Expressive landings; keep this in N-10 because it can pull broad AndroidX churn. |
| Kotlin | 2.1.0 | stable 2.3.x / 2.4.0-RC watch | [Data-flow exhaustiveness](https://kotlinlang.org/docs/whatsnew22.html), Multiplatform Swift export, KSP/Hilt compatibility risk. |
| Hilt | 2.53.1 | 2.59.2 watch | [KSP2 + Kotlin 2.1+ compat caveats](https://github.com/google/dagger/issues/4907) — bump cautiously, run full test suite. |
| Room | 2.6.1 | 2.8.4 | [KMP-ready](https://developer.android.com/jetpack/androidx/releases/room), SQLiteDriver. Gated on L-2 decision but safe to test for Android-only benefits. |
| WorkManager | 2.10.0 | 2.11.2 | Stable as of 2026-03-25; pair with worker scheduling tests for alert, health, widget, custom-alert, nowcast, and DB maintenance jobs. |
| MapLibre | 11.5.2 | 13.2.0 watch | Latest GitHub Android release includes renderer/backend fixes; finish NX-21 lifecycle/cache/radar smoke tests before adopting. |
| OkHttp | 4.12.0 | 5.3.2 watch | [`redactQueryParameters(vararg)` added in 5.x](https://square.github.io/okhttp/changelogs/changelog_5x/); reduces our custom redactor. Watch CVE feeds. |
| Retrofit | 2.11.0 | 3.0.0 watch | Coordinate with OkHttp 5 and serialization adapter compatibility. |
| Glance | 1.1.1 | 1.2.0-rc01 | [Unit-test helper `runGlanceAppWidgetUnitTest`](https://developer.android.com/jetpack/androidx/releases/glance) — enables N-9. |
| Wear Compose M3 | (alpha27) | 1.7.0-alpha02 watch | [Wear OS 6 M3 Expressive](https://developer.android.com/jetpack/androidx/releases/wear-compose-m3); gates UC-6 until stable. |
| Tiles | 1.4.1 | 1.6.0 | Stable as of 2026-03-25; automatic resource handling should reduce tile-loading cost but needs Wear tile regression tests. |
| ProtoLayout | 1.2.1 | 1.4.0 | Stable as of 2026-03-25; pairs with Tiles 1.6.0 and gates L-11. |
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
- [Room releases](https://developer.android.com/jetpack/androidx/releases/room)
- [WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work)
- [Wear Tiles releases](https://developer.android.com/jetpack/androidx/releases/wear-tiles)
- [Glance theme docs](https://developer.android.com/develop/ui/compose/glance/theme)
- [Compose Baseline Profiles](https://developer.android.com/develop/ui/compose/performance/baseline-profiles)
- [Macrobenchmark capture metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics)
- [Strong skipping mode](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping)
- [Predictive back setup](https://developer.android.com/develop/ui/compose/system/predictive-back-setup)
- [Android for Cars: Build a weather app](https://developer.android.com/training/cars/apps/weather)
- [DataLayer data items](https://developer.android.com/training/wearables/data/data-items)
- [Hilt KSP2 issue 4907](https://github.com/google/dagger/issues/4907)

**Firebase / Firestore**
- [Firestore insecure rules guide](https://firebase.google.com/docs/firestore/security/insecure-rules)
- [Firebase App Check](https://firebase.google.com/docs/app-check)

**Kotlin / JetBrains**
- [What's new in Kotlin 2.1.20](https://kotlinlang.org/docs/whatsnew2120.html)
- [What's new in Kotlin 2.2](https://kotlinlang.org/docs/whatsnew22.html)
- [Compose Multiplatform 1.8.0 / iOS Stable](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)

**Charts / libraries**
- [patrykandpatrick/vico](https://github.com/patrykandpatrick/vico)
- [MapLibre native CHANGELOG.md (Android)](https://github.com/maplibre/maplibre-native/blob/main/platform/android/CHANGELOG.md)
- [MapLibre Android quickstart](https://maplibre.org/maplibre-native/android/examples/getting-started/)
- [MapLibre MapView docs](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-view/)
- [MapLibre Android v13.2.0 release](https://github.com/maplibre/maplibre-native/releases/tag/android-v13.2.0)
- [OkHttp 4.x changelog](https://square.github.io/okhttp/changelogs/changelog_4x/)
- [OkHttp 5.x changelog](https://square.github.io/okhttp/changelogs/changelog_5x/)
- [open-meteo/sdk (Kotlin/Flatbuffer)](https://github.com/open-meteo/sdk)

**Data sources**
- [Open-Meteo Marine API](https://open-meteo.com/en/docs/marine-weather-api)
- [Open-Meteo Flood API](https://open-meteo.com/en/docs/flood-api)
- [Open-Meteo Ensemble API](https://open-meteo.com/en/docs/ensemble-api)
- [Open-Meteo Météo-France API](https://open-meteo.com/en/docs/meteofrance-api)
- [Open-Meteo BOM API](https://open-meteo.com/en/docs/bom-api)
- [Météo-France Vigilance dataset](https://www.data.gouv.fr/fr/datasets/bulletin-vigilance/)
- [FMI WFS Services manual](https://en.ilmatieteenlaitos.fi/open-data-manual-fmi-wfs-services?doAsUserLanguageId=en_US)
- [FMI open-data no-registration announcement](https://en.ilmatieteenlaitos.fi/news/963113482)
- [DMI Open Data APIs](https://www.dmi.dk/friedata/dokumentation/apis)
- [DMI Forecast Data EDR API](https://www.dmi.dk/friedata/dokumentation/forecast-data-edr-api)
- [HKO open data introduction](https://www.hko.gov.hk/en/abouthko/opendata_intro.htm)
- [BMKG early warning CAP](https://data.bmkg.go.id/peringatan-dini-cuaca/)
- [KNMI Open Data API](https://developer.dataplatform.knmi.nl/open-data-api)
- [SMHI Open Data](https://opendata.smhi.se/)
- [SMHI meteorological forecasts](https://opendata.smhi.se/metfcst/)
- [RainViewer Weather Maps API](https://www.rainviewer.com/api/weather-maps-api.html)
- [RainViewer API terms](https://www.rainviewer.com/api.html)
- [LightningMaps About](https://www.lightningmaps.org/about?lang=en)
- [LightningMaps project docs](https://docs.lightningmaps.org/?1740696779=)
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

---

## Continuation State

### Last Completed Cycle

Cycle 7: Radar and lightning lifecycle review on 2026-06-06. Native radar, RainViewer, MapLibre lifecycle, Blitzortung source policy, radar tests, and radar dependency runway were reviewed and written into this roadmap.

### Current Focus

Continue autonomous roadmap expansion from Cycle 8: WorkManager/background-job compatibility, battery-budget controls, expedited-work policy, unique-work naming, notification channels, and test gaps across alert, nowcast, custom-alert, health-alert, widget, and database-maintenance workers.

### Important Findings So Far

- `main` was clean and aligned with `origin/main` during this pass; recent history started at `a3d98b7 docs: consolidate roadmap planning`.
- `rtk git log -10` could not run because `rtk` was not available on PATH in this PowerShell shell; normal `git log -10 --oneline --decorate` was used instead.
- Current tracked version evidence is phone v1.21.1 / versionCode 89 and Wear v1.21.0 / versionCode 64.
- Local Spanish parity is current for app and Wear string resources; N-1 should focus on Weblate/community locale rollout.
- `WeatherCacheEntity` is still Open-Meteo-response-shaped; NX-6 now needs provider-aware normalized cache design before implementation.
- `firestore.rules` and `CommunityReportRepository` need a backend-enforced ownership/abuse model before community reporting expands.
- Official AndroidX release pages now make WorkManager 2.11.2, Room 2.8.4, Wear Tiles 1.6.0, and ProtoLayout 1.4.0 stable upgrade targets; Glance should remain watchlisted unless the RC is accepted for widget tests.
- Provider expansion ranking is now concrete: FMI is the first safe no-key adapter; DMI, HKO, and BMKG are high-value regional candidates; KNMI must be optional/user-keyed, not bundled with FMI; Météo-France should split no-key Open-Meteo forecast/nowcast from keyed official Vigilance.
- Provider scale-out needs NX-20 before broad adapter work: a metadata registry for auth mode, region coverage, attribution, license, quotas, `freenet` availability, cache namespace, fallback role, and Settings key UI.
- Native radar needs NX-21 before bigger radar work: RainViewer attribution is mandatory, `RainViewerResponse.host` should be honored, frame metadata should be cached for offline fallback, MapLibre lifecycle should be fully guarded, and native radar needs a smoke/macrobenchmark test.
- Blitzortung can stay as an informational overlay while source policy is reviewed, but NX-16 lightning push must not proceed until permission or an alternate alert-grade source is selected.
- MapLibre latest Android release observed in this pass is 13.2.0; treat it as a renderer-risk upgrade after NX-21, not an incidental dependency bump.

### Next Best Actions

1. Run Cycle 8: inspect all WorkManager jobs against WorkManager 2.11.2 and build a battery-budget plan for alert/widget/custom-alert/health/nowcast scheduling.
2. Run Cycle 9: inspect Wear UI and tile code against Wear Tiles 1.6.0 / ProtoLayout 1.4.0 migration requirements.
3. Run Cycle 10: build a Firebase emulator rules-test plan for community reports.

### Unprocessed Leads

- WeatherMaster v3 alpha remains a fast-moving OSS challenger; run a deeper feature/release diff before changing competitive priorities.
- Open-Meteo Ensemble API can power uncertainty bands or provider-agreement confidence without multiplying third-party provider traffic as much as NX-5 might.
- F-Droid reproducible-build docs call out working-directory stability, R8 nondeterminism, APK signature copying, and 16 KB page-size native-library checks; convert these into NX-14 acceptance criteria.
- Firestore App Check can reduce unauthorized-client abuse but does not replace ownership identity; choose append-only anonymous reports or Firebase Anonymous Auth for standard flavor.
- DMI Forecast Data EDR, HKO warnings/nowcast, and BMKG CAP need endpoint-shape spikes after NX-20 defines metadata and attribution fields.
- KNMI remains useful but should wait for a generic optional-key workflow and explicit no-shared-key policy.
- RainViewer coverage tiles can support a "no radar coverage here" state and should be evaluated during NX-21.
- If lightning proximity remains desired, research non-Blitzortung sources with clear alert/notification terms before implementing a worker.

### Files Still To Inspect

- `app/src/main/java/com/sysadmindoc/nimbus/util/AlertCheckWorker.kt`
- `app/src/main/java/com/sysadmindoc/nimbus/util/NowcastAlertWorker.kt`
- `app/src/main/java/com/sysadmindoc/nimbus/util/CustomAlertWorker.kt`
- `app/src/main/java/com/sysadmindoc/nimbus/util/HealthAlertWorker.kt`
- `app/src/main/java/com/sysadmindoc/nimbus/util/DatabaseMaintenanceWorker.kt`
- `app/src/main/java/com/sysadmindoc/nimbus/widget/WidgetRefreshWorker.kt`
- `wear/src/main/java/com/sysadmindoc/nimbus/wear/tile/WeatherTileService.kt`
- `.github/workflows/release.yml`

### Searches Still To Run

- `WorkManager 2.11.2 Android expedited work periodic battery quota constraints`
- `Android WorkManager foreground service notification channel exact alarm weather alerts`
- `Android 15 16 background execution limits WorkManager weather notifications`
- `WeatherMaster v3 alpha Android weather app release notes sources AQI widgets`
- `Open-Meteo ensemble API uncertainty weather app user confidence`
- `Firebase Firestore emulator security rules Android anonymous auth App Check delete ownership`
- `F-Droid reproducible builds Android AGP 8.7 R8 nondeterminism 16 KB page size`
