# Research - ZeusWatch

## Executive Summary
ZeusWatch (pkg `com.sysadmindoc.nimbus`) is an LGPL-3.0, no-required-key Android weather app at v1.25.0: 37 reorderable Compose cards, multi-source forecasts with primary/fallback failover, multi-region severe-weather alerts, native MapLibre radar + Blitzortung lightning, 8 Glance widgets, a Wear OS companion, live wallpaper, route weather planner, custom/health/driving/pet alerts, Gemini Nano summaries, community Firestore reports, a Breezy-compatible ContentProvider, and settings export/import. The tree is unusually disciplined (clean MVVM, 97 test files, zero TODO/FIXME) and its roadmap is already exhaustively tracked. The single highest-value shift this pass: the **AGP 9 / compileSdk 37 toolchain migration just landed** (commits `33c59b0`, `8295a00`, `c2af40e`), which clears the gate on several items that were parked in `Roadmap_Blocked.md`. Harvest those now-actionable wins before adding net-new surfaces.

Top opportunities, priority order:
1. Verified: Wear module still ships `wear-compose-material3:1.0.0-alpha27` on a release watch app — stable 1.5.x has existed since Aug 2025. Migrate off alpha (reliability, unblocks UC-6).
2. Verified: `Notification.MetricStyle` (API 37) is now compilable (compileSdk 37) — a 3-metric AOD/lock-screen weather notification (Temp/UV/AQI); previously toolchain-blocked.
3. Verified: persistent notification renders a weather glyph, not a temperature readout; a status-bar temp number is a repeatedly-requested competitor feature and works on all API levels.
4. Verified: Room 2.8.4 is stable with a prepared-statement cache; the blocked-item KSP crash was on Kotlin 2.1.0 — the tree is now Kotlin 2.3.21, so a 2.7.2→2.8.4 re-attempt is warranted.
5. Verified: ProtoLayout is already at 1.4.0 in the Wear module, so L-11 (Lottie on tiles) is unblocked and should move to active.
6. Likely: a time-travel (arbitrary past/future date) forecast/history scrub is a pure-UI feature on already-wired Open-Meteo archive+forecast endpoints; competitors paywall it.
7. Likely: no map/radar home-screen widget exists among the 8 Glance widgets; a cached-tile radar widget is a common competitor surface.

## Product Map
- Core workflows: onboard + sequential permissions; review current/hourly/daily for current or saved location; search / map-pick / per-location-source-override saved locations; radar + lightning + community map; create custom alert rules; plan route weather; widget/Wear/background refresh; export/import settings.
- User personas: privacy-first FOSS users; severe-weather + radar watchers; multi-location travelers; widget/Wear-first users; freenet/F-Droid users; source-comparison power users.
- Platforms/distribution: phone/tablet minSdk 26, compileSdk 37, targetSdk 36; Wear module; `standard` (Google/Firebase/Gemini Nano/Wear DataLayer) vs `freenet` (no proprietary deps); local signed per-ABI APK releases with provenance manifest.
- Integrations/data flows: Open-Meteo family (forecast/AQ/pollen/minutely/archive/single-runs/ensemble/climate/flood/marine), MET Norway, Environment Canada, FMI, HKO, BMKG, GeoSphere Austria, Bright Sky, OWM/Pirate (optional keys), Tempest PWS (optional token); NWS/MeteoAlarm/JMA/ECCC/HKO/BMKG/WMO alerts; LibreWXR/RainViewer/Windy/NWS radar; Blitzortung lightning; Firebase Auth/Firestore/App Check; Gadgetbridge broadcast; ContentProvider; Wear DataLayer.

## Competitive Landscape
- **Breezy Weather** (v6.2.x): 50+ sources, in-app per-source capability matrix (`docs/SOURCES.md`), CWA typhoon warnings. Learn: expose a user-facing source-capability comparison (a UX layer over NX-20). Avoid: chasing raw source count without metadata/contract/fallback discipline.
- **WeatherMaster** (v3.6.x): persistent status-bar temperature (issue #437), granular per-widget theming, UnifiedPush (freenet-aligned, #799). Learn: status-bar temp glyph, richer widget theming. Avoid: FCM-coupled push in the freenet flavor.
- **CARROT Weather**: Time Travel (arbitrary date forecast/history), storm-cell push, radar-in-a-widget, wildfire/fronts/tropical map layers. Learn: time-travel scrub, map widget. Avoid: personality/gimmick copy that conflicts with the safety tone.
- **Windy.com**: multi-model tap-to-switch, combined meteogram/airgram (upper-air profile), per-layer color palettes. Learn: dense meteogram presentation. Avoid: aviation/marine expert bloat ahead of reliability work (already covered by existing confidence bands + provider-agreement cards, so multi-model duplication is low-value).
- **Weather Underground**: 250k+ PWS network, rain/snow-accumulation map layer, personal historical dashboards. Learn: accumulation map layer is uncontested in OSS. Avoid: a full sensor-PWS network (ZeusWatch already ingests Tempest; per-vendor OAuth for Netatmo/Wunderground is high-cost).
- **Bura / Overmorrow**: graph-first data viz; condition-adaptive photographic backgrounds. Learn: clean per-element graph scrubbing. Avoid: photographic backgrounds (conflicts with dense-dark brand).

## Security, Privacy, and Reliability
- Verified: `wear/build.gradle.kts:89` ships `wear-compose-material3:1.0.0-alpha27` on the release watch app. Alpha UI libraries on a release path are a stability/maintenance risk; stable 1.5.x is available.
- Verified: `USE_FULL_SCREEN_INTENT` is declared (`AndroidManifest.xml:27`) — the 2026 Play full-screen-intent requirement is met; no action.
- Verified: `SettingsTransfer.kt` + `BackupRulesCoverageTest.kt` already implement settings export/import — the competitor "config backup" gap is closed; no action.
- Verified: `AlertNotificationHelper.kt` already uses `ProgressStyle`; `WeatherNotificationHelper.kt:74` uses `setSmallIcon(weatherNotificationIcon(...))` (a weather glyph, not a temp value) — status-bar temperature and `MetricStyle` are genuine gaps.
- Verified: no tropical-cyclone/hurricane/typhoon track product exists (grep hits are Beaufort "hurricane-force" wording + HKO signal labels only).
- Verified: Firebase Firestore community-report rules risk class is rules-misconfiguration, not an SDK CVE; the append-only + geohash + App Check posture is already in place. Firebase BoM is 34.12.0 vs latest 34.15.0 — a minor, optional bump.
- Verified: OkHttp 5.3.2, Retrofit 3.0.0, MapLibre 13.3.1, Coil 3.1.0 show no 2025-2026 CVEs; Lottie zip path-traversal (airbnb #1112) does not apply — icon packs are external APK drawables, not `.lottie`/zip archives (`IconPackManager` has no zip/Lottie-URL loading).
- Recovery/rollback needs (carried, still valid): stale imported settings pointing at unavailable providers must fall back with a warning; provider-contract matrix should fail deterministically when any selectable no-key provider drifts.

## Architecture Assessment
- `WeatherNotificationHelper.kt` / `AlertNotificationHelper.kt` are the boundary for the two notification wins (status-bar temp glyph; API-37 `MetricStyle` behind a `SDK_INT >= 37` guard alongside the existing `ProgressStyle` path).
- Wear module (`wear/build.gradle.kts`, tile/complication services) is the boundary for the alpha→stable M3 migration and L-11 Lottie tiles (ProtoLayout already 1.4.0).
- `RadarRepository` (tile URLs) + `widget/` Glance surfaces are the boundary for a radar/map widget: render a cached tile bitmap, no live map engine in-widget.
- `WeatherRepository` archive/forecast paths already exist for a time-travel date scrub; the work is a date-picker UI + reuse of `OpenMeteoArchiveApi` / forecast, not new data plumbing.
- `WeatherSourceManager` + NX-20 provider-metadata registry remain the substrate for an in-app source-capability matrix (Breezy parity).
- Test/doc gaps: no test covers `MetricStyle` metric selection or status-bar-temp icon generation; Room 2.8.x migration would need a schema-export + migration test pass.

## Rejected Ideas
- Multi-model comparison card (Windy): rejected — existing Confidence Bands (ensemble) + Provider Agreement + Forecast Evolution cards already cover model/source spread; a fourth is redundant bloat.
- Full sensor-PWS network (Netatmo/Wunderground/PWSweather): rejected near-term — Tempest ingestion already exists; per-vendor OAuth + rate-limit management is high-cost for marginal gain.
- Condition-adaptive photographic backgrounds (Overmorrow): rejected — conflicts with the dense premium-dark brand guardrail.
- Live webcam layer (Windy): rejected — matches the existing roadmap "built-in webcam / live photo feed" rejection (third-party hosting, copyright, moderation).
- Rain/snow-accumulation tile layer: deferred, not added — LibreWXR/RainViewer do not expose accumulation tiles; needs a new paid/self-rendered raster pipeline. Revisit only with a no-key accumulation source.
- targetSdk 36→37 bump for Play: rejected — Play's Aug 31 2026 bar is API 36; compileSdk 37 + runtime `SDK_INT` guards already enable API-37 features without the bump.
- Glance 1.1.1→1.2.0 for widget-picker previews: unchanged — 1.2.0 is still only `-rc01`, not stable; keep the existing blocked item.

## Sources
Competitors (OSS + commercial):
https://github.com/breezy-weather/breezy-weather
https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md
https://github.com/PranshulGG/WeatherMaster/issues/437
https://github.com/PranshulGG/WeatherMaster/issues/799
https://github.com/davidtakac/bura
https://github.com/bmaroti9/Overmorrow
https://support.meetcarrot.com/weather/
https://www.wunderground.com/pws/overview
https://windyapp.live/

Platform / dependency / API:
https://developer.android.com/about/versions/17/features
https://developer.android.com/develop/ui/views/notifications/metric-style
https://developer.android.com/about/versions/16/features/progress-centric-notifications
https://developer.android.com/google/play/requirements/target-sdk
https://developer.android.com/jetpack/androidx/releases/glance
https://developer.android.com/jetpack/androidx/releases/room
https://developer.android.com/jetpack/androidx/releases/wear-compose-m3
https://developer.android.com/jetpack/androidx/releases/wear-protolayout
https://developer.android.com/jetpack/androidx/releases/compose-foundation
https://developer.android.com/jetpack/androidx/releases/core-locationbutton
https://open-meteo.com/en/docs/historical-weather-api

Security:
https://github.com/firebase/firebase-android-sdk/security
https://square.github.io/okhttp/security/security/
https://github.com/airbnb/lottie-android/issues/1112

## Open Questions
- Needs live validation: does Compose Foundation 1.11.x (BOM 2026.06.01) actually expose a non-lazy `Grid` layout API? Research indicates it shipped in foundation 1.11.0; `Roadmap_Blocked.md` records it as absent. Resolve by build check before scheduling the DETAILS_GRID refactor.
- Needs live validation: after the Kotlin 2.3.21 / KSP2 migration, does Room 2.8.4 still hit the `FieldBundle$$serializer` schema-export crash, or is the blocked item now stale?
