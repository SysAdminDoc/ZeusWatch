# Research - ZeusWatch

## Executive Summary
ZeusWatch (pkg `com.sysadmindoc.nimbus`, LGPL-3.0) is a mature, no-required-key Android weather app at v1.25.0: 37 reorderable Compose cards, multi-source forecasts with primary/fallback failover, multi-region severe alerts, native MapLibre radar + Blitzortung lightning, 8 Glance widgets, a Wear OS companion, live wallpaper, route weather planner, custom/health/driving/pet alerts, on-device summaries, community Firestore reports, a Breezy-compatible ContentProvider, settings export/import, and a time-travel history scrub. The tree is disciplined (clean MVVM, 100 test files, zero TODO/FIXME). The single highest-value finding this pass is a **reliability root cause, not a feature**: the on-device instrumented Compose test suite fails tree-wide (`No compose hierarchies found`), and the fix is not more on-device debugging — it is to run Compose UI tests on the JVM via **Robolectric (already a declared-but-unused dependency, 4.15.1)**, which sidesteps the device entirely and unblocks the WCAG gate (NX-18) plus all future UI verification. After that, the actionable wins are two verified dependency/security bumps and a few low-cost, differentiating Open-Meteo data additions.

Top opportunities, priority order:
1. Verified: pivot Compose UI tests to Robolectric JVM (`src/test`, `isIncludeAndroidResources = true`) — device-independent, fixes the tree-wide harness failure; Robolectric 4.15.1 is already in `gradle/libs.versions.toml` but has zero usages.
2. Verified: bump OkHttp 5.3.2 → 5.4.0+ — 5.4.0 caps HTTP/2 response headers at 256 KiB (header-flood DoS mitigation).
3. Verified: bump the Kotlin Gradle plugin 2.3.21 → 2.4.20+ — CVE-2026-53914 (build-cache unsafe deserialization, CWE-502) is fixed in Kotlin 2.4.20; larger toolchain change (Compose-compiler + KSP alignment).
4. Verified: expose Open-Meteo AI models (ECMWF AIFS 0.25°, NCEP GFS GraphCast) as selectable sources — extends the existing `models=` wrapper pattern for near-zero cost.
5. Likely: adopt Arm MTE memory hardening (`android:memtagMode`) — Breezy shipped this in v6.2.1; a manifest-level hardening with no code change.
6. Likely: a route-weather Live Update (API 36 `ProgressStyle` promoted-ongoing) for an active "trip in progress" flow — the one platform API that genuinely fits, scoped to user-initiated navigation per Google policy.
7. Likely: satellite-derived solar radiation (SARAH3 / Himawari-9 / DWD MTG) to upgrade the existing Solar card.

## Product Map
- Core workflows: onboard + sequential permissions; review current/hourly/daily for the current or a saved location; search / map-pick / per-location source override; radar + lightning + community map; create custom alert rules; plan route weather; widget/Wear/background refresh; export/import settings; scrub to an arbitrary date.
- User personas: privacy-first FOSS users; severe-weather + radar watchers; multi-location travelers; widget/Wear-first users; freenet/F-Droid users; source-comparison power users.
- Platforms/distribution: phone/tablet minSdk 26, compileSdk 37, targetSdk 36 (meets the Aug 31 2026 Play API-36 bar — no bump needed); Wear module; `standard` vs `freenet` flavors; local signed per-ABI APK releases + provenance manifest; GitHub Releases (not Play).
- Integrations/data flows: Open-Meteo family (forecast/AQ/pollen/minutely/archive/single-runs/ensemble/climate/flood/marine + model wrappers), MET Norway, Environment Canada, FMI, HKO, BMKG, GeoSphere Austria, Bright Sky, OWM/Pirate (optional keys), Tempest PWS; NWS/MeteoAlarm/JMA/ECCC/HKO/BMKG/WMO alerts; LibreWXR/RainViewer/Windy/NWS radar; Blitzortung; Firebase Auth/Firestore/App Check; Gadgetbridge broadcast; ContentProvider; Wear DataLayer.

## Competitive Landscape
- **Breezy Weather** (v6.2.1, 2026-06-07): added Infoplaza as a worldwide source, per-model selection (ECMWF IFS HRES 9 km, NCEP NAM), and **enabled Arm MTE** for hardening. Learn: MTE manifest hardening (free win); per-model granularity in the source picker. Avoid: chasing raw source count without the metadata/contract discipline ZeusWatch already enforces.
- **WeatherMaster** (v3.6.0, 2026-06-30): deep per-widget customization (icon/font sizing, multiple layout variants), humidity/pressure/visibility as first-class daily fields, Kvaesitso launcher weather sharing (#145). Learn: per-widget size/variant tuning is a real UX axis ZeusWatch lacks. Avoid: FCM-coupled push in the freenet flavor.
- **CARROT / Windy** (unchanged since prior pass): time-travel (now shipped in ZeusWatch), model-vs-model comparison (covered by existing confidence-band + provider-agreement cards). Learn: nothing net-new this pass. Avoid: aviation/marine bloat ahead of reliability.
- **Roborazzi / Now-in-Android testing** (adjacent): JVM Robolectric-based Compose screenshot + interaction testing is now the mainstream pattern for device-free UI verification. Learn: this is the direct answer to ZeusWatch's broken on-device harness. Avoid: keeping the entire UI suite on-device where flakiness and device-specific root registration break it.

## Security, Privacy, and Reliability
- Verified (reliability, highest impact): on-device instrumented Compose tests fail tree-wide with `IllegalStateException: No compose hierarchies found` from `getAllSemanticsNodes`. Prior investigation ruled out test code (bare `Text` fails), rule version (v1 and v2 `createComposeRule` both fail), and the common missing-`ui-test-manifest` cause (it is present at `app/build.gradle.kts:276`). The robust resolution is the Robolectric JVM path, not further on-device debugging.
- Verified (security): `okhttp = "5.3.2"` (`gradle/libs.versions.toml:11`). OkHttp 5.4.0 (2026-06-08) caps per-response HTTP/2 headers at 256 KiB — a header-flood DoS mitigation ZeusWatch does not yet have across its ~20 network sources.
- Verified (security): Kotlin 2.3.21. CVE-2026-53914 (GHSA-r937-wjx7-w2jp, CVSS 6.7) is a Kotlin build-cache unsafe-deserialization issue fixed in Kotlin 2.4.20. It is a build-time toolchain risk, not a runtime app risk, but still roadmap-eligible.
- Verified (hardening gap): no `android:memtagMode` in `AndroidManifest.xml` — Breezy enabled Arm MTE in v6.2.1; ZeusWatch has not.
- Missing guardrails: no device-independent UI verification (all Compose tests are on-device androidTest); the `accessibilityGate` therefore cannot run, so WCAG regressions (NX-18) go unverified.
- Recovery/rollback needs (carried, still valid): stale imported settings pointing at unavailable providers should fall back with a warning; provider-contract checks should fail deterministically when a selectable no-key provider drifts.
- Unverified (needs a lockfile advisory scan, not web search): 2026 CVE status for Firestore, Coil 3.x, Lottie, Glance, Room — web search surfaced none, which is not proof of absence.

## Architecture Assessment
- `app/build.gradle.kts` test config is the highest-value boundary: `testOptions { unitTests.isReturnDefaultValues = true }` is set but `isIncludeAndroidResources = true` is not, and Compose tests live only in `src/androidTest`. Robolectric-based Compose tests belong in `src/test` with Hilt-Robolectric wiring (`HiltTestApplication`), reusing the existing `testing/AccessibilityTestHelpers.kt`.
- `data/api/OpenMeteoApi.kt` + `data/repository/WeatherSource.kt` (`WeatherSourceProvider` registry) already parameterize `models=` (`ukmo_seamless`, `dmi_seamless`, …); adding AIFS/GraphCast is a registry + query-value addition, not new plumbing.
- `util/AlertNotificationHelper.kt` already uses `ProgressStyle`; a route-weather Live Update is a new promoted-ongoing notification driven by `RouteWeatherPlannerSheet` state, guarded to an active trip.
- `ui/component/SolarIrradianceCard` + `OpenMeteoApi` solar params are the boundary for satellite-radiation data.
- Test/doc gaps: no JVM UI test exists; once Robolectric lands, L-15's Roborazzi screenshot layer becomes a thin add-on; document the JVM-vs-device test split in CLAUDE.md.

## Rejected Ideas
- targetSdk 36 → 37 bump for Play compliance: rejected — the Aug 31 2026 Play bar is API 36 (support.google.com/11926878); ZeusWatch already targets 36 and declares `USE_FULL_SCREEN_INTENT`, so it is compliant. Bumping would only drag in Health-Connect permission + full-screen-intent rework for no gain.
- Android's `com.android.compose.screenshot` (`screenshotTest` source set): rejected as the harness fix — it is alpha and only tests `@Preview` composables, so it cannot replace the failing interaction/semantics tests. Robolectric + `createComposeRule` is the correct substitute; Roborazzi (L-15) is the screenshot layer.
- Kvaesitso launcher weather provider (WeatherMaster #145): rejected — the existing Breezy-compatible ContentProvider + Smartspacer already cover launcher integration; a bespoke Kvaesitso adapter is redundant.
- Infoplaza worldwide source (Breezy v6.2.0): rejected near-term — provider breadth is gated on the NX-20 metadata registry, and Open-Meteo AIFS/GraphCast deliver more differentiation per unit effort.
- Wear OS 6.1 location-based automatic time zone: rejected — niche; the Wear companion already anchors sun/time to the synced location.
- Live Updates for passive/ambient weather: rejected — Google explicitly restricts Live Updates to user-initiated active activities (navigation/rideshare), so only the active-trip route flow qualifies.

## Sources
Test harness / JVM UI testing:
https://robolectric.org/androidx_test/
https://robolectric.org/javadoc/4.15/org/robolectric/annotation/GraphicsMode.html
https://github.com/takahirom/roborazzi
https://developer.android.com/studio/preview/compose-screenshot-testing
https://developer.android.com/develop/ui/compose/testing
https://issuetracker.google.com/issues/361250553
https://developer.android.com/training/testing/espresso/setup

Security / dependencies:
https://square.github.io/okhttp/changelogs/changelog/
https://square.github.io/okhttp/security/security/
https://github.com/advisories/GHSA-r937-wjx7-w2jp
https://nvd.nist.gov/vuln/detail/CVE-2026-53914
https://github.com/maplibre/maplibre-native/releases

Competitors:
https://github.com/breezy-weather/breezy-weather/blob/main/CHANGELOG.md
https://github.com/breezy-weather/breezy-weather/releases
https://github.com/PranshulGG/WeatherMaster/releases
https://github.com/PranshulGG/WeatherMaster/issues

Platform / data:
https://developer.android.com/about/versions/16/features/progress-centric-notifications
https://developer.android.com/develop/ui/compose/notifications/live-update
https://support.google.com/googleplay/android-developer/answer/11926878
https://open-meteo.com/en/docs/ecmwf-api
https://open-meteo.com/en/features
https://open-meteo.com/en/docs/model-updates

## Open Questions
- Needs live validation: under Robolectric 4.15.1 + Compose BOM 2026.06.01, does `createComposeRule()` + `@HiltAndroidTest` + `HiltTestApplication` run green on the JVM for a representative existing test (e.g. the ported `ForecastDetailSheetTest`), and does `AccessibilityTestHelpers.setContentWithAccessibilityChecks` work under `@GraphicsMode(NATIVE)`? This determines whether the WCAG gate can move to JVM wholesale or needs a thin on-device remainder.
- Needs live validation: does bumping the Kotlin Gradle plugin to 2.4.20 keep the Compose compiler + KSP 2.x + Hilt toolchain green (the same class of migration that previously required care), or does it require coordinated version bumps?
