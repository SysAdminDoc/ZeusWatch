# ZeusWatch Roadmap

**Current Version**: v1.25.0 (phone versionCode 105, wear versionCode 77)
**Architecture**: Kotlin 2.3.21 / Jetpack Compose / Hilt / MVVM / multi-module (phone + wear)
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

## NEXT — 2-3 release cycles out (target v1.25 - v1.27)

### NX-14. Reproducible builds badge for F-Droid · **T-RELIABILITY**
Audit locale/timezone hashes, Hilt-generated code stability, AGP lockfile. Effort: medium.

### NX-17. Custom-alert rule expansion (residual) · **T-HEALTH**
Shipped: dew point, feels-like, snowfall, pressure, AQI. Remaining: severe weather event type (needs non-threshold rule structure). Effort: low.

### NX-18. WCAG 2.2 AA audit + dynamic font scaling pass · accessibility
Contrast audit of weather-adaptive palettes, font scaling stress test at 1.3/1.5/1.8, touch target 48dp audit, extend a11y checks to all screens. Effort: medium.

### NX-20. Provider metadata registry + regional auto-suggestion · **T-SOURCES** / **T-RELIABILITY**
`dataTypes`/`authMode` already on `WeatherSourceProvider`, and Settings API-key
gating is now registry-driven (`SourceConfig.selectedProviders()` + `requiresApiKey`).
Remaining: enrich the registry with coverage, attribution, license, quota,
`freenetAllowed`, fallback role, and cache namespace (needs verified per-provider
license/quota data), then add a regional resolver for default source bundles. Effort: medium.

---

## LATER — Beyond v1.26

### L-1. `freenet` flavor Wear OS sync via non-GMS path · **T-WEAR**
Default: document that `freenet` Wear users rely on direct API calls (already works). CompanionDeviceManager + sockets only if implementable in <2 weeks.

### L-4. Android Auto / Car App Library variant · **T-ECOSYSTEM**
Bare-bones AA module: alerts + radar + driving conditions card. Investigate GMS dependency.

### L-7. Home Assistant integration · **T-ECOSYSTEM**
Publish weather entities via ContentProvider (NX-13) or MQTT. Separate module.

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

---

> Decision-pending items (former UNDER CONSIDERATION UC-1..UC-7) moved to
> `Roadmap_Blocked.md` under "Product / Decision Pending".

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
| 3 | Meteo-France via Open-Meteo + Vigilance | None for forecast; JWT for Vigilance | P1 | No-key forecast/nowcast shipped; official Vigilance blocked pending keyed path. |
| 4 | HKO (Hong Kong) | None | P1/P2 | Compact regional win. |
| 6 | KNMI (Netherlands) | API key | P2 | Optional user-key only after NX-20. |
| 7 | CWA (Taiwan) | Free key | P2/P3 | Higher localization effort. |
| 8 | SMHI (Sweden) | None | P2 | Revisit after FMI/DMI. |

---

## Library Watch

| Lib | Current | Target | Notes |
|---|---|---|---|
| Compose BOM | 2026.06.01 | — | Current; shared-transition baseline. |
| Kotlin | 2.3.21 | — | Current verified K2/KSP2 line. |
| Hilt | 2.60 | — | Current AGP 9-compatible line; supports Kotlin 2.4 metadata from current dependencies. |
| Room | 2.8.4 | — | Current; 2.8.4 verified on Kotlin 2.3.21 / KSP 2.3.9 with schema export intact (prior 2.1.0 KSP crash resolved). |
| WorkManager | 2.11.2 | — | Current. |
| MapLibre | 13.3.1 | — | Current; 13.x Vulkan-backed default verified with radar/location `MapView` lifecycle hardening. |
| OkHttp | 5.4.0 | — | Current; 5.4.0 caps per-response HTTP/2 headers at 256 KiB. Note: 5.4.0 widened `Interceptor.Chain` — test doubles extend `FakeInterceptorChain`. |
| Retrofit | 3.0.0 | — | Current. |
| Glance | 1.1.1 | 1.2.0 stable | Enables widget unit tests. |
| Wear Compose M3 | 1.5.0 | — | On stable (migrated off alpha27); foundation/navigation aligned to 1.5.0. UC-6 Expressive redesign now unblocked. |
| Tiles | 1.6.0 | — | Current. |
| ProtoLayout | 1.4.0 | — | Current; L-11 Lottie-on-tiles now unblocked. |
| Vico | 3.2.3 | — | Current for trend-card charts. |

---

## Research-Driven Additions (2026-06-09)

### P0 — Security-Critical

### P1 — High-Impact Systemic Fixes

### P2 — Platform Opportunities & Quality

### P3 — Polish & Future


## Research-Driven Additions (2026-06-09, Round 2)

### P2 — Platform Opportunities

[Moved to Roadmap_Blocked.md: Android 17 MetricStyle (needs compileSdk 37), Compose Grid (non-lazy Grid API absent in Compose BOM 2026.06.01)]

[Moved to Roadmap_Blocked.md: Glance widget picker previews (needs Glance 1.2.0 stable)]

### P3 — Polish & Future

## Research-Driven Additions (2026-06-09, Round 3)

### P2 — Platform Upgrades & Opportunities

### P3 — Incremental Improvements


## Research-Driven Additions (2026-06-10, Round 4)

### P1 — Platform Deadline & Distribution

### P2 — Distribution & Quality

[Moved to Roadmap_Blocked.md: IzzyOnDroid submission (requires human-coordinated request)]

### P3 — Future / Evaluation

## Product Improvement Ideas (2026-06-11)

Net-new improvement opportunities identified during the v1.23.0 audit — distinct from the audit residuals below and from existing NOW/NEXT/LATER items. Closest existing items are cross-referenced instead of duplicated (per-location sources = P1 2026-06-09; light theme = UC-3; localization = N-1; battery instrumentation = UC-4; baseline profiles = NX-7).

## Audit Round 5 Residuals (2026-06-11)

Items found during the v1.23.0 deep audit that need design decisions or larger work. ~70 sibling findings were fixed and shipped in v1.23.0.






## Research-Driven Additions

### P1


### P2


### P3


## Research-Driven Additions (2026-06-13)

### P1 — Trust & Platform Compliance

### P2 — Product Differentiation


[Moved to Roadmap_Blocked.md: Wear OS 7 compatibility pass (needs Wear OS 7 SDK availability)]


### P3 — Future & Polish

## Code Audit Findings (2026-06-14)

Three parallel code audits (health/architecture, performance/Compose, testing/reliability/security) read the tree at v1.23.0 (versionCode 101). Each finding below was **re-verified against the current code** after the v1.21.x audit pass — items already fixed in v1.23.0 are noted as such and the residual is scoped to what remains. The codebase is unusually disciplined (clean MVVM, shared `WeatherCard` abstraction, truly-lazy `LazyColumn` with stable keys + `contentType`, `@Stable MainUiState` with `ImmutableList` fields, cert pinning populated, 47 unit-test files, zero TODO/FIXME). These are the residual gaps.

### P2 — Performance & Maintainability


### P3 — Polish


> Cross-references (already tracked, reinforced by this audit): Provider-metadata registry = **NX-20** (`WeatherSourceAdapterModule` is the first shipped registry slice; metadata remains). Already-fixed since the v1.21.x audit and intentionally **not** re-listed: Firestore world-delete vector, provider-aware cache, alert-worker total-failure retry, and the `WeatherParticles` lifecycle `derivedStateOf` bug (now uses `currentStateAsState()`).

## Research-Driven Additions

### P3 — Planning Hygiene


## Research-Driven Additions

### P1 - Reliability and Hardening

### P3 - Operational Maturity

## Research-Driven Additions

### P1 — Trust, Safety, And Release Integrity

### P2 — Forecast Trust And Source Depth

## Research-Driven Additions (2026-06-25)

### P1 — Security & Trust

[P1 items completed and removed — see git history]

### P2 — Product Differentiation & Coverage


### P3 — Polish & Future

## Research-Driven Additions

### P1

### P2


## Research-Driven Additions (2026-07-12)

Post-toolchain-migration sweep. The AGP 9 / compileSdk 37 migration (`33c59b0`,
`8295a00`, `c2af40e`) cleared gates on several `Roadmap_Blocked.md` items and the
Wear module still ships an alpha UI lib on a release path. Items below are
verified against the current tree and are net-new (not duplicating active
ROADMAP items). L-11 Lottie-on-tiles is now unblocked (ProtoLayout already 1.4.0)
— leave it in LATER but treat as active. UC-6 is unblocked by item R-1.

### P2 — Now-actionable platform features (compileSdk 37 gate cleared)

> `Notification.MetricStyle` moved to `Roadmap_Blocked.md`: the platform-37.0
> stub exposes `MetricStyle`/`Metric` but `Notification.Metric.MetricValue` is
> abstract with no public constructor or factory, so a third-party app cannot
> build a `Metric`. Blocked on a usable public `MetricValue` API, not compileSdk.

> The on-device "No compose hierarchies found" harness fix is superseded: JVM
> Robolectric Compose testing now works (`ForecastDetailSheetRobolectricTest`).
> Remaining migration tracked under "Port the remaining instrumented Compose
> tests to the proven JVM Robolectric path".

### P3 — Product & polish

- [ ] P3 — Map/radar home-screen Glance widget
  Why: none of the 8 Glance widgets shows radar; a cached-tile radar widget is a common competitor surface and reuses existing tile URLs.
  Evidence: CARROT radar widget https://support.meetcarrot.com/weather/; `data/repository/RadarRepository.kt`
  Touches: new `widget/NimbusRadarWidget.kt`, `RadarRepository`, `widget/WidgetRefreshWorker.kt`, `WidgetConfigActivity`
  Acceptance: widget renders the most recent cached radar tile bitmap for its configured location with a freshness badge; tap opens the Radar tab; no live map engine in-widget; refreshes with the widget worker.
  Complexity: M

> In-app source capability matrix moved to `Roadmap_Blocked.md` (blocked on the
> NX-20 `ProviderMetadata` registry, which must land first).

> Location Button adoption moved to `Roadmap_Blocked.md` (blocked on the
> `androidx.core.locationbutton` artifact leaving alpha).

## Research-Driven Additions (2026-07-13)

Grounded in the 2026-07-13 research pass (see RESEARCH.md). Net-new; not
duplicating existing items. The Robolectric item is the recommended resolution of
the existing "Fix the instrumented Compose test harness" P2 (device-independent
strategy vs. on-device debugging) and the prerequisite that makes L-15's Roborazzi
screenshot layer trivial.

### P2 — Reliability, Security & Source Depth

- [ ] P2 — Port the remaining instrumented Compose tests to the proven JVM Robolectric path
  Why: the JVM Robolectric Compose harness is now working (`ForecastDetailSheetRobolectricTest` in `src/test` runs green with no device); the other 5 `androidTest` Compose tests still only run on the broken on-device harness, so the `accessibilityGate` remains red. Migrate them to `src/test` to restore the gate.
  Evidence: `app/src/test/.../ForecastDetailSheetRobolectricTest.kt` (proof); broken on-device suite (`MainScreenTest`, `SettingsScreenTest`, `LocationsScreenTest`, `AccessibilityAuditTest`, `ReportSubmitSheetTest`).
  Touches: `app/src/androidTest/**` Compose tests → `app/src/test/**` with `@RunWith(RobolectricTestRunner)` + `@Config(sdk=[34], application=...)` (Hilt-Robolectric `HiltTestApplication` where injection is needed; `@GraphicsMode(NATIVE)` for screenshots), port `testing/AccessibilityTestHelpers.kt`, rewire the `accessibilityGate` task to `:app:testStandardDebugUnitTest`.
  Acceptance: all former instrumented Compose tests run green under `:app:testStandardDebugUnitTest` with no device; `accessibilityGate` green.
  Complexity: L

- [ ] P2 — Bump Kotlin Gradle plugin 2.3.21 → 2.4.20+
  Why: CVE-2026-53914 (build-cache unsafe deserialization, CWE-502, CVSS 6.7) is fixed in Kotlin 2.4.20; build-time toolchain hardening.
  Evidence: https://github.com/advisories/GHSA-r937-wjx7-w2jp ; https://nvd.nist.gov/vuln/detail/CVE-2026-53914 ; current `kotlin = "2.3.21"`.
  Touches: `gradle/libs.versions.toml` (`kotlin`, `compose-compiler`, likely `ksp`), verify Hilt/KSP2/Compose-compiler alignment; run KSP + unit suite.
  Acceptance: `:app:kspStandardDebugKotlin` + full unit suite + `:wear` compile green on Kotlin 2.4.20; schema export intact; versions synced across catalog + README + Library Watch.
  Complexity: M

- [ ] P2 — Add selectable Open-Meteo AI models (ECMWF AIFS 0.25°, NCEP GFS GraphCast)
  Why: Open-Meteo now exposes AI forecast models; ZeusWatch already parameterizes `models=` for regional wrappers, so adding AI models is a near-zero-cost differentiator competitors are only starting to add.
  Evidence: https://open-meteo.com/en/docs/ecmwf-api ; https://open-meteo.com/en/docs ; existing wrappers in `data/api/OpenMeteoApi.kt` (`ukmo_seamless`, `dmi_seamless`) + `WeatherSourceProvider` registry.
  Touches: `data/api/OpenMeteoApi.kt`, `data/repository/WeatherSource.kt` (new `OPEN_METEO_AIFS`/`OPEN_METEO_GRAPHCAST` entries + adapter bindings), `tools/check_provider_contracts.py`, Data Sources strings.
  Acceptance: AIFS + GraphCast are selectable forecast sources with primary/fallback routing, a live/mocked contract check, and freenet parity; JVM adapter test covers the new model query values.
  Complexity: M

### P3 — Hardening, Platform & Data

- [ ] P3 — Enable Arm MTE memory hardening (`android:memtagMode`)
  Why: manifest-level heap/stack memory-tagging hardening on supported devices with no code change; Breezy shipped it in v6.2.1.
  Evidence: https://github.com/breezy-weather/breezy-weather/blob/main/CHANGELOG.md ; no `memtagMode` in `app/src/main/AndroidManifest.xml`.
  Touches: `app/src/main/AndroidManifest.xml` (`<application android:memtagMode="sync">` or `async`), verify no regression on release build.
  Acceptance: release APK declares `memtagMode`; app launches and passes the unit suite; README/CLAUDE note the hardening.
  Complexity: S

- [ ] P3 — Route-weather Live Update (API 36 ProgressStyle promoted-ongoing) for an active trip
  Why: the one API-36 notification API that genuinely fits — a segment-by-segment "trip in progress" surface for the route planner, on the AOD/lock screen/status-bar chip.
  Evidence: https://developer.android.com/about/versions/16/features/progress-centric-notifications ; https://developer.android.com/develop/ui/compose/notifications/live-update ; existing `ProgressStyle` use in `util/AlertNotificationHelper.kt` + `RouteWeatherPlannerSheet`.
  Touches: `util/AlertNotificationHelper.kt` (or a new helper), `RouteWeatherPlannerSheet`/`RoutePlannerUiState`, `POST_PROMOTED_NOTIFICATIONS` permission, runtime `SDK_INT >= 36` guard.
  Acceptance: on API >=36, starting a route plan optionally shows a promoted-ongoing Live Update with per-waypoint precip/risk segments, gated to a user-initiated active trip (per Google's Live-Update policy) and dismissible; older devices unaffected.
  Complexity: M

- [ ] P3 — Satellite-derived solar radiation for the Solar card
  Why: Open-Meteo now serves geostationary satellite radiation (EUMETSAT CM SAF SARAH3, JMA Himawari-9, DWD MTG) at higher cadence than models — a fidelity upgrade for the existing Solar/UV surface.
  Evidence: https://open-meteo.com/en/features ; existing Solar card (`ui/component/SolarIrradianceCard`, `OpenMeteoApi` solar params).
  Touches: `data/api/OpenMeteoApi.kt` (satellite radiation params), Solar card rendering, opt-in in Data Sources.
  Acceptance: when available for the location, the Solar card reflects satellite-derived shortwave/direct radiation with graceful fallback to model data; parser/format test covers the new fields.
  Complexity: M

## Research-Driven Additions (2026-07-13, Code Audit)

Output of three parallel read-only code audits (concurrency, security/privacy,
data-correctness). Every item verified against source at the cited file:line;
these are the actionable content of L-14 (adversarial audit round 5). Not
duplicating existing items.

### P2 — Correctness & Reliability

- [ ] P2 — BlitzortungService: close onFailure Response and synchronize reconnect
  Why: `onFailure` ignores the non-null `Response` (leaked connection bodies under backoff), and the reconnect coroutine mutates `@Synchronized`-guarded `reconnectJob`/`shouldReconnect` outside the monitor, racing `disconnect()` into duplicate sockets.
  Evidence: `data/api/BlitzortungService.kt:107` (`onFailure(..., response: Response?)` unclosed); reconnect body near `:140` mutates guarded state off-lock.
  Touches: `BlitzortungService.kt` (`response?.close()`; move reconnect null-set + `shouldReconnect` re-check + `connect()` under the monitor; reference-count connect/disconnect for the `@Singleton`).
  Acceptance: no leaked responses on repeated handshake failures; rapid radar-layer flipping cannot open two concurrent sockets; existing lightning tests green.
  Complexity: M

- [ ] P2 — Fix moonset mislabeled as next-day
  Why: moonset is pushed to `date+1` whenever the moon sets before it rises on the same calendar day — a normal case — so moonset renders ~24h late.
  Evidence: `data/repository/AirQualityRepository.kt:157` (`if (times.rise != null && setTime.isBefore(times.rise)) date.plusDays(1)`); `MoonAstronomy.riseSetForDate` returns first rise/set within the same day.
  Touches: `AirQualityRepository.kt` (compute rise/set as a chronologically-ordered pair; same-day for set), moon astronomy unit test.
  Acceptance: a same-day set-before-rise produces a same-day moonset timestamp; regression test with a morning-set/evening-rise fixture.
  Complexity: S

- [ ] P2 — Cancel the previous AI-summary job on new weather requests
  Why: the AI summary is launched fire-and-forget on the ViewModel `scope` with no prior-job cancellation, so rapid location/tab switching stacks uncancelled `generateWithStyle` jobs on the default dispatcher (CPU/battery).
  Evidence: `ui/screen/main/WeatherLoadCoordinator.kt:253` (`scope.launch { ... generateWithStyle ... }`).
  Touches: `WeatherLoadCoordinator.kt` (hold a cancellable `Job?`, cancel on new request, or run inside the structured `coroutineScope`).
  Acceptance: switching locations N times leaves at most one in-flight summary job; existing MainViewModel tests green.
  Complexity: S

- [ ] P2 — Make notification/widget dedupe state race-safe
  Why: `CustomAlertWorker`/`HealthAlertWorker` dedupe via non-atomic SharedPreferences read-modify-write, and `WidgetRefreshWorker` runs manual vs periodic work under different unique names (no WorkManager serialization) — dedupe races (double notifications) and lost-update widget-state writes.
  Evidence: `util/CustomAlertWorker.kt` `markAndCheckNew`; `util/HealthAlertWorker.kt` `record/prune`; `widget/WidgetRefreshWorker.kt` (`nimbus_widget_refresh` vs `nimbus_widget_refresh_manual_refresh`).
  Touches: dedupe stores to a single DataStore transaction or process lock; widget worker to one shared unique name (or `APPEND_OR_REPLACE`) so manual+periodic serialize.
  Acceptance: concurrent worker runs cannot emit a duplicate custom/health notification for the same (rule,date); manual + periodic widget refresh cannot interleave writes; tests cover the dedupe path.
  Complexity: M

### P3 — Hardening & Polish

- [ ] P3 — Remove or implement the no-op certificateTransparency config
  Why: `<certificateTransparency enabled="true"/>` is not an AOSP network-security-config element and no CT library is present, so it is silently ignored — false MITM assurance.
  Evidence: `app/src/main/res/xml/network_security_config.xml:7`; no CT dependency in `gradle/libs.versions.toml`.
  Touches: `network_security_config.xml` (delete the element) or add an OkHttp CT interceptor wired in `NetworkModule`.
  Acceptance: either the element is gone, or CT is actually enforced on the OkHttp client with a test; no misleading config remains.
  Complexity: S

- [ ] P3 — Pin the Open-Meteo geocoding endpoint
  Why: cert pinning covers only OWM/Pirate; the geocoding host carries the typed place-name search text over an unpinned channel — a privacy signal in a zero-telemetry app.
  Evidence: `data/api/ApiCertificatePins.kt:46-57` (only OWM/Pirate hosts); geocoding client provided in `NetworkModule.kt`.
  Touches: `ApiCertificatePins.kt` (add SPKI leaf+intermediate pins for the Open-Meteo geocoding host), pin-verification test.
  Acceptance: geocoding requests fail closed on pin mismatch; `ApiCertificatePinsTest` covers the new host.
  Complexity: S

- [ ] P3 — Reduce false positives in health frontal-proxy and pet pavement alerts
  Why: the migraine/frontal heuristic uses independent min/max spans (not co-timed/directional) so ordinary diurnal swings trigger it when pressure is absent; the pet pavement WARNING fires on air temp alone even when the pavement estimate is far below the burn floor.
  Evidence: `util/HealthAlertEvaluator.kt:154` (`humidities.max()-min()` paired with a temp span); `util/PetSafetyEvaluator.kt:55` (air-temp OR clause bypasses `pavementEstimate`).
  Touches: `HealthAlertEvaluator.kt` (consecutive signed deltas / correlation), `PetSafetyEvaluator.kt` (gate WARNING on the pavement estimate), evaluator tests.
  Acceptance: clear-day diurnal swings no longer raise a frontal/migraine warning; a sunny 25°C morning with low pavement estimate no longer shows a pavement WARNING; tests cover both.
  Complexity: S

- [ ] P3 — ContentProvider location-precision hardening
  Why: the ecosystem provider serves full-precision saved-location coordinates behind a user-grantable `dangerous` permission; already opt-in default-off + query-time enforced, but a coarsening/allowlist option would bound residual exposure.
  Evidence: `AndroidManifest.xml:12` (`protectionLevel="dangerous"`); `ecosystem/ZeusWatchWeatherProvider.kt:124,141` (opt-in enforcement); `UserPreferences` `weather_content_provider_enabled` default false.
  Touches: `ZeusWatchWeatherProvider.kt` (optional coordinate coarsening), Settings copy noting the exposure.
  Acceptance: an opt-in "share coarse location only" mode rounds provider coordinates; default behavior documented; consumer contract unchanged when disabled.
  Complexity: S

- [ ] P3 — Small correctness/main-thread polish (Beaufort boundary, wallpaper prefs I/O)
  Why: Beaufort labels 1.0-1.9 km/h as Calm (`<2` vs standard `<1`); the live wallpaper does first-load SharedPreferences disk I/O on the render/main thread on create and every 5 min.
  Evidence: `util/WeatherFormatter.kt:390` (`kmh < 2 -> Calm`); `wallpaper/WeatherWallpaperService.kt` `refreshWeatherCode()` main-thread prefs read.
  Touches: `WeatherFormatter.kt` (force-0 boundary `< 1`), `WeatherWallpaperService.kt` (warm/cache prefs off-thread), formatter unit test.
  Acceptance: Beaufort force 0/1 boundary matches the standard; wallpaper no longer reads prefs on the main thread; formatter test updated.
  Complexity: S
