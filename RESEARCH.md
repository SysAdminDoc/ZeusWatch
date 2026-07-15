# Research — ZeusWatch
Date: 2026-07-14 — replaces all prior research.

## Executive Summary

ZeusWatch is a mature, privacy-first Android and Wear OS weather system: it already combines no-key forecast defaults, provider failover, offline Room caches, severe-weather overlays, confidence bands, eight Glance widgets, local diagnostics, settings migration, and unusually broad ecosystem surfaces without accounts or telemetry. Its strongest direction is therefore not more provider count; it is making the existing breadth trustworthy at security boundaries and failure points. The highest-value work is, in order:

1. Upgrade Wear Tiles/ProtoLayout to the 2026-07-01 security baseline.
2. Constrain radar WebView navigation to explicit HTTPS allowlists.
3. Coarsen and expire anonymous community-report coordinates.
4. Make route-weather output geometry-aware, or label its current straight-line estimate honestly.
5. Replace automatic permission prompts with contextual, recoverable flows.
6. Make onboarding persistence failures retryable instead of leaving a permanent spinner.
7. Make overlapping alert polygons render and select by severity.
8. Activate documented Glance 1.1.1 JVM structural tests for all widgets.
9. Repair documentation drift and make the existing checker authoritative.
10. Add local background-delivery diagnostics and opt-in update discovery for direct APK installs.

## Product Map

- Core workflows: current/hourly/daily forecasts; saved-place search, comparison, and source overrides; radar/lightning/alert/community layers; route, health, pet, activity, and custom-alert decisions; widget, Wear, wallpaper, notification, and external-provider delivery.
- User personas: privacy-conscious Android users; severe-weather and outdoor-planning users; multi-location households; Wear/widget-first users; F-Droid and direct-APK users; Tasker/KWGT, Gadgetbridge, Smartspacer, and Home Assistant integrators.
- Platforms and distribution: Android 8+ phone/tablet/foldable (`minSdk 26`), Wear OS (`minSdk 30`), `standard` and GMS-free `freenet` flavors, ABI-specific and universal APKs, GitHub Releases and F-Droid-oriented packaging.
- Key integrations: Open-Meteo and regional forecast/alert adapters flow through Hilt multibindings and `WeatherSourceManager`; Room v5 and DataStore provide cache/settings state; Tink stores API keys; MapLibre, LibreWXR/RainViewer, Windy/NWS WebViews, and Blitzortung drive maps; Firebase handles standard-flavor community reports.
- Design philosophy: no required key, no paywall, no account, no background-location requirement, local diagnostics instead of telemetry, graceful cached fallback, and a consistent dark/glass visual system (`README.md`, `CLAUDE.md`).

## Competitive Landscape

### Breezy Weather

- Does well: adapter documentation, accessibility, source breadth, configurable widgets, opt-in external sharing, and active localization.
- Learn: keep provider metadata/contracts machine-checkable and make widget configuration explainable.
- Avoid: breadth-driven maintenance and a provider-count race; ZeusWatch already has more surface area than its tests and docs fully protect.

### WeatherMaster

- Does well: responsive Compose layouts and per-widget icon, text, and layout controls.
- Learn: add per-instance widget density/metric controls after the Glance structural-test gate lands.
- Avoid: redundant widget variants when one size-aware surface can adapt.

### Rain

- Does well: scheduled notifications, widget refresh/error UX, a generated licenses surface, and broad localization.
- Learn: expose background-delivery state and dependency/provider attribution.
- Avoid: dozens of palettes, fonts, and icon packs that dilute ZeusWatch's deliberate visual identity.

### Tiny Weather Forecast Germany

- Does well: explicit optional-permission behavior, open-data defaults, widgets, offline operation, and Gadgetbridge compatibility.
- Learn: make the manual-location path first-class and document what each permission unlocks.
- Avoid: sync-account and background-location machinery that conflicts with ZeusWatch's foreground-location ceiling.

### Windy

- Does well: route planning, multi-model comparison, radar layers, and clear separation between free and paid decision tools.
- Learn: show route/model provenance and uncertainty without presenting approximations as navigation-grade data.
- Avoid: subscription coupling and a WebView becoming a general browser.

### Weather on the Way

- Does well: route geometry, ETA-at-point weather, future departure, alternatives, and GPX interoperability.
- Learn: sample weather along cumulative route distance, not a great-circle chord.
- Avoid: navigation scope creep; ZeusWatch should remain weather guidance and explicitly say it is not turn-by-turn navigation.

### CARROT Weather

- Does well: configurable Smart Forecast thresholds, radar widgets, polished complications, and strong upgrade discovery.
- Learn: turn the existing current-only `ActivityIndexEvaluator` into explainable best-time windows.
- Avoid: personality/gamification, accounts, and subscription mechanics that contradict the product philosophy.

### Flowx, meteoblue, RadarScope, and Atmos Weather

- Do well: dense model visualization, uncertainty context, specialist radar controls, and severity-aware alert polygons.
- Learn: preserve ZeusWatch's existing confidence bands, improve specialist map correctness, and never hide source/model identity.
- Avoid: raw-model overload without guidance and paid-only data dependencies.

## Security, Privacy, and Reliability

- **Verified, P0:** `wear/build.gradle.kts` pins Tiles 1.6.0 and ProtoLayout 1.4.0. The 2026-07-01 ProtoLayout 1.4.1 security release adds image-size, layout-depth, malformed-URI, gap, and expression hardening; Tiles 1.6.1 fixes a service-connection race. Upgrade and run tile/complication tests.
- **Verified, P0:** `RadarScreen.kt:837-928` enables JavaScript and accepts `about:`, `blob:`, `data:`, and provider hosts without requiring HTTPS; blocked main-frame URIs are sent to `ACTION_VIEW` without a scheme restriction. Extract a pure navigation policy, permit only HTTPS allowlisted radar hosts in-WebView, and open only explicit HTTPS external links.
- **Verified, P0:** `CommunityReportRepository.kt:166-175` writes exact coordinates; `firestore.rules` makes reports append-only; `firestore.indexes.json` has no TTL field override. The UI only uses two-hour-old reports, so persistent precision has no product value. Round submitted coordinates, add a bounded `expiresAt` timestamp, configure Firestore TTL, enforce it in rules, and disclose the retention window.
- **Verified, P1:** `WeatherRepository.planDrivingRouteWeather()` interpolates up to six points on a straight line, estimates duration from a constant speed, and fails the whole plan if one waypoint has no data. The UI calls this a driving route. Accept bounded GPX 1.1 geometry and degrade per waypoint; label origin/destination-only output as a corridor estimate.
- **Verified, P1:** `MainScreen.kt:193-233` automatically requests location and then notification permission, but has no rationale/permanent-denial state or settings/location-services recovery action. Manual search exists and should remain fully usable without either permission.
- **Verified, P1:** `OnboardingViewModel.complete()` has no `try/finally` or error state; a DataStore failure leaves `isSaving=true` indefinitely. Add a typed retryable failure and a unit test.
- **Verified, P1:** alerts are sorted most-severe first (`AlertRepository.kt:240-249`) and emitted in that order to one GeoJSON source (`RadarMapView.kt:532-548`), allowing later lower-severity features to obscure or win taps over higher-severity overlaps. Reverse render order or use a severity sort key and test overlap selection.
- **Verified, P1:** Dagger 2.60 exposes duplicate map-key detection, while `WeatherSourceAdapterModule` has a large `@IntoMap` registry. Enable the opt-in KSP argument so an accidental duplicate source key fails compilation.
- **Verified:** the unreviewed Kotlin 2.4.20 advisory remains blocked correctly because its named patched version is unpublished; do not claim current exploitability (`Roadmap_Blocked.md`).

## Architecture Assessment

- **Verified:** `WeatherRepository.kt` is 1,134 lines and owns route planning beside core forecast fetching. Extract `DrivingRouteWeatherPlanner` with geometry parsing, sampling, partial-result policy, and deterministic tests; keep `WeatherRepository` as data access.
- **Verified:** `SettingsScreenContent.kt` is 2,298 lines, `MainScreenContent.kt` 1,783, and `RadarScreen.kt` 1,081. Split by existing product categories only after the JVM Compose gate covers navigation, permission, and failure states; avoid a design-system rewrite.
- **Verified:** Glance's official 1.1.1 test artifacts support `runGlanceAppWidgetUnitTest`; the “requires 1.2.x” blocker in `Roadmap_Blocked.md` is stale. Add structural assertions for actions, semantics, and size modes for all eight widgets; rendering and click execution remain outside that API's scope.
- **Verified:** the test tree is broad (91 app JVM files, 7 app instrumented files, 7 Wear JVM files) but has no dedicated UI flow coverage for onboarding failure, radar WebView policy, route import/degradation, update discovery, or permission recovery. Add focused tests with each roadmap item instead of another generic test initiative.
- **Verified:** `py -3 tools/check_docs_consistency.py` fails because README says Room 2.7.2 while the catalog uses 2.8.4; README also says OkHttp 5.3.2 and DB v3 while code uses 5.4.0 and v5. `docs/phases-pre-v1.md` retains historical `nimbus://` and GitHub Actions claims without identifying their current `zeuswatch://`/local-release replacements. Make the checker catalog/manifest/schema-aware.
- **Verified:** Arabic and Hebrew each contain 1,444 base keys but only 171 and 144 differ from English, respectively; Spanish differs on 1,335. Localization expansion remains correctly blocked under N-1 until a maintained translation workflow exists; do not add another roadmap item.
- **Verified:** direct GitHub APK users have post-update “What's New” but no pre-update discovery. Add a default-off, ETag-cached check for the `standard` direct-APK channel; keep `freenet` delegated to its package manager and never auto-install.
- **Verified:** `ActivityIndexEvaluator` scores six activities from current conditions only. Extending it across hourly forecasts with user thresholds is a medium-sized, high-fit decision-support feature; it reuses existing hourly/AQI data and should expose factor explanations.
- **Verified:** recent location searches persist full `GeocodingResult` coordinates in DataStore (`UserPreferences.kt:264-282`) with no clear API or UI action. Add clear-all/per-row deletion and include the data in privacy copy.

## Rejected Ideas

- More forecast providers now — rejected: NX-20 already targets metadata/coverage, and peer issue histories show normalization regressions rise with source count; harden contracts first.
- A runtime provider/plugin loader — rejected: Hilt compile-time adapters, flavor isolation, and Android's code-loading risk make it a poor fit; external interoperability already exists through ContentProvider, Gadgetbridge, Smartspacer, icon APKs, and planned Home Assistant work.
- Shared or bundled commercial API keys — rejected: RadarWeather's final release documents the failure mode when a provider starts requiring billing credentials; keep user-owned optional keys and no-key defaults.
- Accounts, cloud sync, family sharing, or multi-user collaboration — rejected: conflicts with the no-account/local-first philosophy and adds identity, retention, and breach obligations without repository or community demand.
- iOS, TV, desktop, or Compose Multiplatform expansion — rejected: already parked in `Roadmap_Blocked.md`; it would multiply release/test surfaces before Android/Wear gates are reliable.
- Background location for widget freshness — rejected: WorkManager plus cached coordinates is sufficient; background location violates the manifest/privacy ceiling and does not solve OEM scheduling restrictions.
- Weather news, social feed, or CARROT-style gamification — rejected: commercial differentiation, but it adds content moderation/clutter and does not improve forecast trust.
- Another uncertainty/model-comparison feature — rejected as a duplicate: `ConfidenceBandRepository`, `ForecastDetailSheet`, and `ProviderAgreement` already provide ensemble percentile ranges and cross-provider divergence. Improve those only when measured usability evidence appears.
- A second settings backup/migration system — rejected as a duplicate: `SettingsTransfer.kt` already has schema versioning, preview, validation limits, rollback behavior, and tests.
- Per-day briefing scheduling as a new feature — rejected as a duplicate: `DailyBriefingWorker` already provides an opt-in local-time schedule; expand only if user evidence justifies weekday selection.

## Sources

### Open-source and discovery

- https://github.com/breezy-weather/breezy-weather
- https://github.com/PranshulGG/WeatherMaster/releases/tag/v3.6.0
- https://github.com/darkmoonight/Rain/releases/tag/v1.3.19
- https://github.com/bmaroti9/Overmorrow/releases/tag/v2.6.3
- https://codeberg.org/Starfish/TinyWeatherForecastGermany
- https://github.com/atticuscornett/AtmosWeather/releases/tag/v3.1.0
- https://github.com/offa/android-foss
- https://github.com/woheller69/weather/releases/tag/V6.7
- https://github.com/breezy-weather/breezy-weather/releases/tag/v5.2.6

### Commercial and adjacent products

- https://www.windy.com/articles/23730
- https://weatherontheway.app/features
- https://apps.apple.com/app/carrot-weather/id961390574
- https://flowx.io/
- https://content.meteoblue.com/en/private-customers/website-subscriptions
- https://radarscope.zendesk.com/hc/en-us/articles/8991862852754-Upgrading-Your-RadarScope-Experience
- https://www.home-assistant.io/integrations/weather/
- https://gadgetbridge.org/internals/development/weather-support/

### Community signal

- https://github.com/breezy-weather/breezy-weather/issues/937
- https://github.com/bmaroti9/Overmorrow/issues/231

### Platform, security, dependencies, and research

- https://developer.android.com/privacy-and-security/risks/unsafe-uri-loading
- https://developer.android.com/training/permissions/requesting
- https://developer.android.com/privacy-and-security/about
- https://firebase.google.com/docs/firestore/ttl
- https://firebase.google.com/docs/reference/firestore/indexes
- https://developer.android.com/develop/ui/compose/glance/testing
- https://developer.android.com/jetpack/androidx/releases/wear-tiles
- https://developer.android.com/jetpack/androidx/releases/wear-protolayout
- https://github.com/google/dagger/releases/tag/dagger-2.60
- https://developer.android.com/blog/posts/android-developer-verification-rolling-out-to-all-developers-on-play-console-and-android-developer-console
- https://journals.sagepub.com/doi/10.1177/1071181320641255

## Open Questions

- **Needs owner validation:** has `com.sysadmindoc.nimbus` and its release signing certificate been registered for Android developer verification? Initial regional enforcement begins 2026-09-30 and cannot be confirmed from the repository.
