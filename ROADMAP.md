# ZeusWatch Roadmap

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Actionable Items

- [ ] **LGPL-3.0 + F-Droid `freenet` parity.** No proprietary blob in `freenet`.

- [ ] **Multi-source resilience by default.** New data types ship with primary + fallback.

- [ ] **T-PERF** — Cache warm, frame fast, battery flat

- [ ] **T-RELIABILITY** — Adversarial audits, test surfaces, dependency runway

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

### NX-22. Add `hilt-android-testing` to unlock skipped runtime tests · **T-RELIABILITY**
Two v1.27.0 fixes shipped without automated coverage because the app module has
no `hilt-android-testing` dependency: the ecosystem ContentProvider opt-in
gating (`EntryPointAccessors.fromApplication` can't bootstrap under Robolectric)
and the `WidgetConfigActivity` follow-app-location unpin purge (activity/WorkManager
glue). Add the dependency and backfill both. Effort: low.

---

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

- [ ] P3 — Map/radar home-screen Glance widget
  Why: none of the 8 Glance widgets shows radar; a cached-tile radar widget is a common competitor surface and reuses existing tile URLs.
  Evidence: CARROT radar widget https://support.meetcarrot.com/weather/; `data/repository/RadarRepository.kt`
  Touches: new `widget/NimbusRadarWidget.kt`, `RadarRepository`, `widget/WidgetRefreshWorker.kt`, `WidgetConfigActivity`
  Acceptance: widget renders the most recent cached radar tile bitmap for its configured location with a freshness badge; tap opens the Radar tab; no live map engine in-widget; refreshes with the widget worker.
  Complexity: M

- [ ] P2 — Port the remaining instrumented Compose tests to the proven JVM Robolectric path
  Why: the JVM Robolectric Compose harness is now working (`ForecastDetailSheetRobolectricTest` in `src/test` runs green with no device); the other 5 `androidTest` Compose tests still only run on the broken on-device harness, so the `accessibilityGate` remains red. Migrate them to `src/test` to restore the gate.
  Evidence: `app/src/test/.../ForecastDetailSheetRobolectricTest.kt` (proof); broken on-device suite (`MainScreenTest`, `SettingsScreenTest`, `LocationsScreenTest`, `AccessibilityAuditTest`, `ReportSubmitSheetTest`).
  Touches: `app/src/androidTest/**` Compose tests → `app/src/test/**` with `@RunWith(RobolectricTestRunner)` + `@Config(sdk=[34], application=...)` (Hilt-Robolectric `HiltTestApplication` where injection is needed; `@GraphicsMode(NATIVE)` for screenshots), port `testing/AccessibilityTestHelpers.kt`, rewire the `accessibilityGate` task to `:app:testStandardDebugUnitTest`.
  Acceptance: all former instrumented Compose tests run green under `:app:testStandardDebugUnitTest` with no device; `accessibilityGate` green.
  Complexity: L

- [ ] P2 — Add selectable Open-Meteo AI models (ECMWF AIFS 0.25°, NCEP GFS GraphCast)
  Why: Open-Meteo now exposes AI forecast models; ZeusWatch already parameterizes `models=` for regional wrappers, so adding AI models is a near-zero-cost differentiator competitors are only starting to add.
  Evidence: https://open-meteo.com/en/docs/ecmwf-api ; https://open-meteo.com/en/docs ; existing wrappers in `data/api/OpenMeteoApi.kt` (`ukmo_seamless`, `dmi_seamless`) + `WeatherSourceProvider` registry.
  Touches: `data/api/OpenMeteoApi.kt`, `data/repository/WeatherSource.kt` (new `OPEN_METEO_AIFS`/`OPEN_METEO_GRAPHCAST` entries + adapter bindings), `tools/check_provider_contracts.py`, Data Sources strings.
  Acceptance: AIFS + GraphCast are selectable forecast sources with primary/fallback routing, a live/mocked contract check, and freenet parity; JVM adapter test covers the new model query values.
  Complexity: M

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

- [ ] P2 — NX-33. Generate OSS notices and provider attribution
  Why: Settings shows only ZeusWatch's LGPL label despite a large dependency/provider surface; Rain demonstrates a compact in-app licenses pattern.
  Evidence: `SettingsScreenContent.kt:1429`, `gradle/libs.versions.toml`, `WeatherSourceProvider`; https://github.com/darkmoonight/Rain/releases/tag/v1.3.19
  Touches: deterministic notices generator, packaged notice data, searchable About/Licenses screen; consume NX-20 provider metadata when available.
  Acceptance: release builds expose dependency name/version/license/source plus required provider/data attribution, generation is reproducible and checked for missing/unknown licenses, links open externally, and freenet output excludes standard-only dependencies.
  Complexity: M

- [ ] P2 — NX-34. Add local background-delivery health diagnostics
  Why: provider health is visible, but users cannot diagnose when widgets, daily briefing, Gadgetbridge, Wear sync, or alert workers last ran, failed, or will retry.
  Evidence: `ProviderHealthPanel`, `WidgetRefreshWorker`, `DailyBriefingWorker`, `GadgetbridgeWeatherBroadcaster`; recurring widget failures in https://github.com/breezy-weather/breezy-weather/issues/937
  Touches: bounded/redacted delivery-health store, worker/broadcaster sync outcomes, Settings diagnostics panel, retry/battery-restriction actions, tests.
  Acceptance: diagnostics show last attempt/success, normalized failure class, next scheduled run, and manual retry for each enabled delivery surface; store contains no coordinates, URLs, keys, or raw exceptions; export/share is explicit and redacted.
  Complexity: M

- [ ] P2 — NX-35. Add opt-in update discovery for direct APK installs
  Why: direct GitHub users see release notes only after updating and have no in-app way to discover a compatible signed release.
  Evidence: README GitHub Releases distribution, `lastSeenVersionCode`/What's New flow; Breezy Weather's default-off standard-flavor check and browser-only freenet behavior: https://github.com/breezy-weather/breezy-weather/releases/tag/v5.2.6
  Touches: standard-flavor release metadata client/store, Settings “Check now” and default-off periodic toggle, WorkManager, update-result UI, tests.
  Acceptance: standard direct-APK installs can manually check and optionally enable an ETag-cached check no more than once per 24 hours; version/flavor/ABI selection is deterministic; UI links to release notes, checksums, and provenance in the browser; no APK is downloaded or installed automatically; freenet performs no GitHub check.
  Complexity: M

- [ ] P2 — NX-36. Turn Activity Index into explainable best-time windows
  Why: the existing six-activity card scores only current conditions, while users need the best upcoming window and commercial products charge for configurable threshold planning.
  Evidence: `util/ActivityIndexEvaluator.kt`, `ui/component/ActivityIndexCard.kt`; CARROT Smart Forecast precedent: https://apps.apple.com/app/carrot-weather/id961390574
  Touches: hourly activity-window evaluator, activity preferences/settings transfer, `ActivityIndexCard`, accessibility descriptions, evaluator/UI tests.
  Acceptance: each enabled activity shows its best contiguous window over the next 24 hours, score and limiting factors; users can adjust temperature/rain/wind/AQI/UV thresholds with reset-to-default; missing AQI or sparse hours lower confidence rather than invent values; settings export/import round-trips.
  Complexity: M

- [ ] P2 — Extend accessibility instrumentation gates to the newest surfaces
  Why: AccessibilityAuditTest fixtures skip the PWS, Provider Agreement, and On This Day cards, the route planner sheet, and the Compare overlay; the JVM contrast gate now covers theme tokens but instrumented touch-target/semantics audits do not exercise these screens.
  Where: app/src/androidTest/.../AccessibilityAuditTest.kt (fixture card set + new screen scaffolds)

- [ ] P3 — Gadgetbridge broadcast target-package selection
  Why: coordinates are now coarsened to ~1 km, but the weather payload still broadcasts to every app resolving the public Gadgetbridge action; a user-selected target package (Breezy Weather pattern) would close the harvest channel entirely.
  Where: util/GadgetbridgeWeatherBroadcaster.kt, Settings > Notifications

- [ ] P2 — Complete Arabic and Hebrew translations
  Why: es is a full locale, but values-ar (~888) and values-he (~909) strings are still English left in place for lint parity (RTL core-copy only). Users of those locales see a mostly-English RTL UI, including empty/error/notification copy and accessibility content descriptions. Needs native-speaker translation, not machine translation — human decision.
  Where: app/src/main/res/values-ar/strings.xml, app/src/main/res/values-he/strings.xml

- [ ] P3 — Cancel the wallpaper preload thread post on engine destroy
  Why: WeatherWallpaperService.onCreate spawns a raw thread that posts applyWeatherCode via Handler; if the engine is destroyed before the disk read completes (fast preview open/close) the post still runs against a dead engine. Harmless today (no-ops when !visible) but unmanaged.
  Where: wallpaper/WeatherWallpaperService.kt (~62-66)

- [ ] P3 — Drop redundant @Volatile on monitor-guarded Blitzortung fields
  Why: nine @Volatile fields are only ever touched inside @Synchronized methods; the mixed idiom invites a future off-lock edit that assumes @Volatile is sufficient for the non-atomic increments. Maintainability only; behavior is correct.
  Where: data/api/BlitzortungService.kt (~52-57)
