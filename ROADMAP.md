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

### L-16. Calendar-based / alternate numeral support · **T-I18N**
Non-Latin numeral systems + alternate calendars. Gated on core extraction.

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
| OkHttp | 5.3.2 | — | Current; debug logging uses built-in query-parameter redaction plus ZeusWatch's Pirate Weather path-key scrubber. |
| Retrofit | 3.0.0 | — | Current. |
| Glance | 1.1.1 | 1.2.0 stable | Enables widget unit tests. |
| Wear Compose M3 | alpha27 | stable | Gates UC-6. |
| Tiles | 1.4.1 | 1.6.0 | Tile regression tests needed. |
| ProtoLayout | 1.2.1 | 1.4.0 | Gates L-11. |
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

### P1 — Reliability (ship off pre-release deps)

- [ ] P1 — Migrate Wear `wear-compose-material3` off `1.0.0-alpha27` to stable 1.5.x
  Why: a release watch app is shipping an alpha UI library; stable has existed since Aug 2025.
  Evidence: `wear/build.gradle.kts:89`; https://developer.android.com/jetpack/androidx/releases/wear-compose-m3
  Touches: `wear/build.gradle.kts`, wear Compose screens/tile/complication UI, `gradle/libs.versions.toml`
  Acceptance: wear module builds on `compose-material3` >=1.5.x stable, tile/complication/app UI unregressed on Wear OS 5+, unblocks UC-6.
  Complexity: M

### P2 — Now-actionable platform features (compileSdk 37 gate cleared)

- [ ] P2 — API 37 `Notification.MetricStyle` weather notification (3 metrics)
  Why: previously toolchain-blocked; compileSdk is now 37, so a Temp/UV/AQI AOD + lock-screen metric card is compilable behind a runtime guard.
  Evidence: `Roadmap_Blocked.md` "Android 17 Notification.MetricStyle" (gated on compileSdk 37, now met); `AlertNotificationHelper.kt` already uses ProgressStyle; https://developer.android.com/develop/ui/views/notifications/metric-style
  Touches: `util/WeatherNotificationHelper.kt`, `util/AlertNotificationHelper.kt`, notification channel wiring
  Acceptance: on API >=37 the persistent weather notification renders up to 3 semantic metrics; API <=36 keeps existing BigText/glyph; `POST_PROMOTED_NOTIFICATIONS` handled; unit test covers metric selection. Retire the corresponding blocked entry.
  Complexity: M

- [ ] P2 — Status-bar temperature readout in the persistent notification
  Why: persistent notification shows a weather glyph, not the temperature; a status-bar temp number is a high-demand competitor feature and works on all API levels.
  Evidence: `util/WeatherNotificationHelper.kt:74` (`setSmallIcon(weatherNotificationIcon(...))`); WeatherMaster demand https://github.com/PranshulGG/WeatherMaster/issues/437
  Touches: `util/WeatherNotificationHelper.kt`, a new temp-glyph bitmap generator, Settings toggle in `UserPreferences.kt`
  Acceptance: opt-in setting renders current temperature as the status-bar small icon (unit-aware, DPI-safe bitmap), falls back to the weather glyph when disabled; JVM test covers glyph text formatting.
  Complexity: M

- [ ] P2 — Time-travel: arbitrary past/future date forecast + history scrub
  Why: On This Day exists but there is no date picker to view any past or upcoming date; competitors paywall this and the data is already wired.
  Evidence: CARROT Time Travel https://support.meetcarrot.com/weather/; existing `OpenMeteoArchiveApi` + forecast endpoints
  Touches: `data/repository/WeatherRepository.kt` (archive/forecast reuse), a new date-scrub UI entry (Compare screen or a card action), `ui/screen/main/`
  Acceptance: user picks a date within the archive/forecast horizon and sees temperature/precip/conditions for the current location; offline shows a clear unavailable state; timezone-anchored to the viewed location.
  Complexity: M

### P3 — Product & polish

- [ ] P3 — Map/radar home-screen Glance widget
  Why: none of the 8 Glance widgets shows radar; a cached-tile radar widget is a common competitor surface and reuses existing tile URLs.
  Evidence: CARROT radar widget https://support.meetcarrot.com/weather/; `data/repository/RadarRepository.kt`
  Touches: new `widget/NimbusRadarWidget.kt`, `RadarRepository`, `widget/WidgetRefreshWorker.kt`, `WidgetConfigActivity`
  Acceptance: widget renders the most recent cached radar tile bitmap for its configured location with a freshness badge; tap opens the Radar tab; no live map engine in-widget; refreshes with the widget worker.
  Complexity: M

- [ ] P3 — In-app source capability matrix (Breezy parity, builds on NX-20)
  Why: users cannot see which selectable source supplies which data type (forecast/AQ/pollen/nowcast/alerts/normals); Breezy exposes this and it drives informed source choice.
  Evidence: Breezy `docs/SOURCES.md` https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md; depends on NX-20 `ProviderMetadata`
  Touches: `data/repository/WeatherSourceManager.kt` metadata, `ui/screen/settings/` Data Sources section
  Acceptance: Data Sources shows a per-source capability grid (dataTypes, coverage, auth, freenet-allowed); accessible + non-color cues; no new network calls.
  Complexity: M

> Location Button adoption moved to `Roadmap_Blocked.md` (blocked on the
> `androidx.core.locationbutton` artifact leaving alpha).
