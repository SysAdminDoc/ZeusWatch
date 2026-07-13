# Research - ZeusWatch

## Executive Summary
ZeusWatch (pkg `com.sysadmindoc.nimbus`, LGPL-3.0) is a mature, privacy-first, no-required-key Android weather app at v1.25.0: 37 reorderable Compose cards, multi-source forecasts with primary/fallback failover, multi-region severe alerts, native MapLibre radar + Blitzortung lightning, 8 Glance widgets, a Wear OS companion, live wallpaper, route planner, custom/health/driving/pet alerts, community Firestore reports, a Breezy-compatible ContentProvider, and a time-travel history scrub. The feature surface and dependency/toolchain runway are already exhaustively tracked in ROADMAP.md and Roadmap_Blocked.md; three prior research passes covered competitors, platform APIs, and dependency bumps (Robolectric JVM tests, OkHttp 5.4.0, Kotlin 2.4.20, AI models — all still open in the roadmap). **This pass is a deep code audit** (three parallel read-only audits: concurrency/reliability, security/privacy, data-correctness), and it surfaces concrete, file:line-grounded defects the feature-focused passes missed. The highest-value direction now is defect remediation over new features: one genuine privacy-rule gap, a user-facing unit bug, and a cluster of reliability races.

Top findings (verified against source), priority order:
1. Verified (privacy, P1): `firestore.rules:61` allows any anonymously-authenticated client to read the **entire** `community_reports` collection — full-precision coordinates + stable `ownerUid` — with no server-side geohash/time/limit bound; client-side `.limit(50)`/radius filtering is cosmetic. Contradicts the zero-telemetry philosophy and enables movement-history correlation by `ownerUid`.
2. Verified (correctness, P2): snowfall custom-alert unit mismatch — `CustomAlertEvaluator.kt:61` sums Open-Meteo `snowfall` (centimeters, per `WeatherFormatter.formatSnowfall(cm)`) against a threshold declared `CustomAlertUnit.MM` (`CustomAlertRule.kt:72`) and converts inches with `/25.4` instead of `/2.54` — an ~10× magnitude error on a safety-adjacent alert.
3. Verified (reliability, P2): `BlitzortungService.kt` reconnect coroutine mutates `@Synchronized`-guarded state (`reconnectJob`, `shouldReconnect`) outside the monitor (line ~140), racing `disconnect()` into duplicate sockets; `onFailure` (line 107) ignores the non-null `Response`, leaking connection bodies under backoff.
4. Verified (correctness, P2): `AirQualityRepository.kt:157` labels a moonset as next-day whenever the moon sets before it rises on the same calendar day — a normal astronomical case — so moonset renders ~24h late.
5. Verified (reliability/battery, P2): `WeatherLoadCoordinator.kt:253` launches the AI summary on the ViewModel `scope` fire-and-forget with no prior-job cancellation; rapid location/tab switching stacks uncancelled `generateWithStyle` jobs on the default dispatcher.
6. Verified (reliability, P2): notification dedupe stores do non-atomic SharedPreferences read-modify-write (`CustomAlertWorker.kt`, `HealthAlertWorker.kt`), and `WidgetRefreshWorker` runs manual vs periodic work under **different** unique names (no serialization) — dedupe races (double notifications) and lost-update widget writes.
7. Verified (security, P2/P3): `network_security_config.xml:7` `<certificateTransparency enabled="true"/>` is not an AOSP NSC element and no CT library is in the dependency graph — a silent no-op giving false MITM assurance.

## Product Map
- Core workflows: onboard + sequential permissions; review current/hourly/daily for current or saved location; search / map-pick / per-location source override; radar + lightning + community map; create custom alert rules; plan route weather; widget/Wear/background refresh; export/import settings; scrub to an arbitrary date.
- User personas: privacy-first FOSS users; severe-weather + radar watchers; multi-location travelers; widget/Wear-first users; freenet/F-Droid users; source-comparison power users.
- Platforms/distribution: minSdk 26, compileSdk 37, targetSdk 36 (meets the Aug-2026 Play API-36 bar); Wear module; `standard` vs `freenet` flavors; local signed per-ABI APK releases + provenance manifest; GitHub Releases.
- Integrations/data flows: Open-Meteo family + model wrappers, MET Norway, ECCC, FMI, HKO, BMKG, GeoSphere Austria, Bright Sky, OWM/Pirate (optional keys), Tempest PWS; NWS/MeteoAlarm/JMA/ECCC/HKO/BMKG/WMO alerts; LibreWXR/RainViewer/Windy/NWS radar; Blitzortung; Firebase Auth/Firestore/App Check; Gadgetbridge broadcast; ContentProvider; Wear DataLayer.

## Competitive Landscape
(Unchanged since the 2026-07-13 pass — no new competitor signal this cycle; this pass is a code audit.)
- **Breezy Weather** (v6.2.1): Arm MTE hardening, Infoplaza source, per-model granularity — MTE is already a tracked P3.
- **WeatherMaster** (v3.6): per-widget size/variant/font customization — a real UX axis ZeusWatch lacks (candidate, not added here to keep this pass defect-focused).
- **Roborazzi / NowInAndroid testing**: JVM Robolectric Compose testing — already the tracked P1 resolution for the broken on-device harness.

## Security, Privacy, and Reliability
- Verified (P1): `firestore.rules:61` global authenticated read of `community_reports` with full-precision coords + `ownerUid`; server-side bounds absent. Fix: bounded geohash-prefix + `timestamp > now-2h` reads, enforce `request.query.limit`, and move `ownerUid` to an owner-private subdoc so readers can't correlate.
- Verified (P2): `BlitzortungService.kt:107` unclosed `onFailure` `Response`; reconnect state mutated outside the `@Synchronized` monitor → duplicate sockets / leaked bodies under flaky networks.
- Verified (P2/P3): `network_security_config.xml:7` no-op `certificateTransparency` element (no CT lib present) — remove or wire an OkHttp CT interceptor.
- Verified (P3): cert pinning (`ApiCertificatePins.kt`) covers only OWM/Pirate; the Open-Meteo **geocoding** host (which carries the user's typed search text — a privacy signal) is unpinned. Add SPKI pins.
- Verified (P3, mitigated): `ZeusWatchWeatherProvider` is `exported` behind a `protectionLevel="dangerous"` permission (`AndroidManifest.xml:12`) serving full-precision coords, but it is opt-in default-off (`weather_content_provider_enabled` = false) and enforced at query time (`ZeusWatchWeatherProvider.kt:124,141`). Residual risk: a user enabling it + granting a malicious app. Consider coordinate coarsening or a per-consumer allowlist. (Note: `signature` level would break the intended Tasker/KWGT use.)
- Verified (P3, low): `MainActivity.kt:120` auto-navigates on any external `ACTION_SEND text/plain` into the route flow with an unvalidated string; add an in-app confirmation.
- Sound (checked, no defect): `EncryptedApiKeyStore` (Tink AES-GCM + Keystore, atomic writes, corrupt-keyset self-heal); backup/device-transfer exclusion of secrets (`data_extraction_rules.xml`); DEBUG-only log key redaction; ACRA consent-gated + PII-stripped; Firestore owner-binding/timestamp/throttle-ledger rules; ContentProvider id path is `toLongOrNull`→parameterized Room (no injection); the "Windy WebView" is actually MapLibre GL (no JS bridge).

## Architecture Assessment
- `util/CustomAlertEvaluator.kt` + `data/model/CustomAlertRule.kt`: the canonical-unit model lacks a `CM` unit, forcing the snowfall bug; introducing `CustomAlertUnit.CM` (or normalizing snowfall cm→mm at read) is the clean fix and prevents recurrence for future snow-based metrics.
- `data/api/BlitzortungService.kt`: connect/disconnect need reference counting (it is an `@Singleton` driven by one screen's `LaunchedEffect`/`onDispose`) and a single monitor covering the reconnect coroutine; also add a trailing-flush/periodic tick so a passing storm's final partial batch and the 10-minute age-out still emit.
- `ui/screen/main/WeatherLoadCoordinator.kt`: the AI-summary launch should hold a cancellable `Job` (cancel prior on new request) or run inside the structured `coroutineScope`.
- Notification/widget dedupe state (`CustomAlertWorker`, `HealthAlertWorker`, `WidgetRefreshWorker`): move dedupe to a single DataStore transaction or a process lock, and serialize manual+periodic widget work under one unique name.
- `data/repository/AirQualityRepository.kt` hosts moon astronomy day-rollover logic — the moonset/rise pairing should be computed as a chronologically-ordered pair.
- Test/doc gaps: the moonset, snowfall-unit, Beaufort-boundary, and evaluator-threshold bugs are all pure-function and belong in the existing JVM unit suite; add regression tests with each fix. L-14 ("adversarial audit round 5") is effectively this pass — its output is the roadmap items below.

## Rejected Ideas
- Change the ContentProvider permission to `signature` (audit suggestion): rejected — it is designed for third-party Tasker/KWGT consumers, which are not same-signature; opt-in default-off + a coarsening/allowlist option is the right mitigation.
- Health frontal-proxy total rewrite: rejected — the min/max-span heuristic over-fires on ordinary diurnal swings, but the fix is a co-timed/signed-delta tweak, not a redesign (see P3 item).
- Adding a dedicated storm-passage lightning timer thread: rejected — a coroutine trailing-flush on the existing scope covers the stale-batch case without a new thread.
- Re-opening the "Windy WebView" JS-bridge hardening (from stale README naming): rejected — the radar is MapLibre GL, no WebView/JS bridge exists.

## Sources
Code audit (in-repo, file:line):
firestore.rules
app/src/main/java/com/sysadmindoc/nimbus/util/CustomAlertEvaluator.kt
app/src/main/java/com/sysadmindoc/nimbus/data/model/CustomAlertRule.kt
app/src/main/java/com/sysadmindoc/nimbus/util/WeatherFormatter.kt
app/src/main/java/com/sysadmindoc/nimbus/data/api/BlitzortungService.kt
app/src/main/java/com/sysadmindoc/nimbus/ui/screen/main/WeatherLoadCoordinator.kt
app/src/main/java/com/sysadmindoc/nimbus/data/repository/AirQualityRepository.kt
app/src/main/java/com/sysadmindoc/nimbus/util/CustomAlertWorker.kt
app/src/main/java/com/sysadmindoc/nimbus/util/HealthAlertWorker.kt
app/src/main/java/com/sysadmindoc/nimbus/widget/WidgetRefreshWorker.kt
app/src/main/java/com/sysadmindoc/nimbus/util/HealthAlertEvaluator.kt
app/src/main/java/com/sysadmindoc/nimbus/util/PetSafetyEvaluator.kt
app/src/main/res/xml/network_security_config.xml
app/src/main/java/com/sysadmindoc/nimbus/data/api/ApiCertificatePins.kt
app/src/main/AndroidManifest.xml
app/src/main/java/com/sysadmindoc/nimbus/MainActivity.kt

External (standards for the correctness fixes):
https://open-meteo.com/en/docs
https://www.weather.gov/media/epz/wxcalc/windChill.pdf
https://en.wikipedia.org/wiki/Beaufort_scale
https://square.github.io/okhttp/security/security/

## Open Questions
- Needs live validation: does Firestore already have a companion index/composite that would make a bounded geohash-prefix + timestamp read rule enforceable without breaking the existing `CommunityReportRepository` query, and can `ownerUid` be moved to an owner-private subdoc without losing the throttle-ledger's owner binding?
- Needs live validation: is the snowfall custom-alert metric in active user configs (migration concern) — i.e. must a `CM` unit change re-interpret already-stored `thresholdCanonical` values, or is a one-time ×10 migration of existing snowfall rules required?
