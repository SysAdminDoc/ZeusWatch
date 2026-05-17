# Security And Dependency Review

Date: 2026-05-17

## Summary

No OSV advisories were returned for the sampled current Maven coordinates, but
the project is behind on several high-value dependencies. The safest roadmap is
a staged upgrade ladder, not a one-shot version sweep.

## Current Dependency Snapshot

Source: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `wear/build.gradle.kts`.

| Component | Current | Latest observed metadata | Recommendation |
|---|---:|---:|---|
| Gradle wrapper | 8.9 | 9.5.1 | Delay until AGP compatibility plan. |
| Android Gradle Plugin | 8.7.3 | 9.3.0-alpha05 | Avoid alpha; research current stable before bump. |
| Kotlin | 2.1.0 | 2.3.21 stable docs, 2.4.0-RC metadata | Move to 2.3.x in dedicated compiler/KSP pass. |
| KSP | 2.1.0-1.0.29 | 2.3.8 metadata | Pair with Kotlin upgrade only. |
| Compose BOM | 2025.04.01 | 2026.05.00 | Medium-risk UI pass; run screenshot/UI tests. |
| Room | 2.6.1 | 2.8.4 | High-value upgrade. Room 2.8.x includes Flow/database bug fixes. |
| DataStore | 1.1.1 | 1.3.0-alpha09 | Stay stable unless a fix requires alpha. |
| Glance | 1.1.1 | 1.2.0-rc01 | Consider RC for widget tests/previews; otherwise wait for stable. |
| WorkManager | 2.10.0 | 2.11.2 | Good candidate; worker-heavy app. |
| Lifecycle | 2.8.7 | 2.11.0-beta01 | Wait for stable or pair with Compose BOM. |
| Navigation Compose | 2.8.5 | 2.10.0-alpha04 | Avoid alpha for now. |
| Core KTX | 1.15.0 | 1.19.0-alpha02 | Check current stable, avoid alpha. |
| Wear Compose Material3 | 1.0.0-alpha27 literal | 1.7.0-alpha02 metadata | Already alpha; bump only with Wear visual QA. |
| Wear Tiles | 1.4.1 literal | 1.6.0 | Good candidate with tile tests. |
| Wear ProtoLayout | 1.2.1 literal | 1.4.0 | Good candidate with tile tests. |
| Play Services Location | 21.3.0 | 21.3.0 | Current. |
| Play Services Wearable | 18.2.0 | 20.0.1 | Important but test DataLayer thoroughly. |
| Hilt | 2.53.1 | 2.59.2 | Medium; pair with KSP/compiler. |
| Retrofit | 2.11.0 | 3.0.0 | Major; dedicated networking pass. |
| OkHttp | 4.12.0 | 5.3.2 | Major; dedicated networking/log redaction/pinner tests. |
| kotlinx.serialization | 1.7.3 | 1.11.0 | Pair with Kotlin upgrade. |
| Coil 3 | 3.0.4 | 3.5.0-beta01 | Wait for stable unless bugfix needed. |
| Lottie | 6.6.2 | 6.7.1 | Low-risk patch. |
| MapLibre Android | 11.5.2 | 13.1.0 | Medium; radar regression tests and map smoke needed. |
| ACRA | 5.13.1 | 5.13.1 | Current. |
| Detekt | 1.23.8 | 1.23.8 | Current. |

## OSV Vulnerability Query

Endpoint: `https://api.osv.dev/v1/querybatch`

Sampled Maven coordinates:

- `com.android.tools.build:gradle:8.7.3`
- `org.jetbrains.kotlin.android:org.jetbrains.kotlin.android.gradle.plugin:2.1.0`
- `com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.1.0-1.0.29`
- `com.google.dagger:hilt-android:2.53.1`
- `com.squareup.retrofit2:retrofit:2.11.0`
- `com.squareup.okhttp3:okhttp:4.12.0`
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3`
- `androidx.room:room-runtime:2.6.1`
- `androidx.datastore:datastore-preferences:1.1.1`
- `androidx.glance:glance-appwidget:1.1.1`
- `androidx.work:work-runtime-ktx:2.10.0`
- `io.coil-kt.coil3:coil-compose:3.0.4`
- `com.airbnb.android:lottie-compose:6.6.2`
- `org.maplibre.gl:android-sdk:11.5.2`
- `ch.acra:acra-core:5.13.1`

Result: zero vulnerabilities returned for all sampled coordinates.

Limitations:

- OSV package coverage is not exhaustive for Android ecosystem issues.
- This did not inspect transitive runtime dependency graphs beyond named
  coordinates.
- A full Gradle dependency lock or SBOM scan should be a future CI enhancement.

## Security Posture Review

### API key protection

Local source:

- `NetworkModule.kt`
- `ApiKeyRedactionTest`
- `ApiCertificatePins.kt`

Good:

- Query-param and Pirate Weather path-key log redaction exist.
- OWM, OWM AQI, and Pirate Weather clients are the only keyed endpoints with
  certificate pinner wiring.
- Keyless providers are intentionally unpinned.

Gap:

- `ApiCertificatePins.hostPins` is empty, so pinner wiring is currently a no-op.

Plan:

- Run `tools/capture_api_pins.sh`.
- Add a Windows/PowerShell equivalent for the release checklist.
- Populate only real captured leaf/intermediate pins.
- Add expiry/provenance comments.
- Keep release docs updated.

### Background execution and battery

Local workers:

- `AlertCheckWorker`
- `NowcastAlertWorker`
- `CustomAlertWorker`
- `HealthAlertWorker`
- `WidgetRefreshWorker`
- `DatabaseMaintenanceWorker`

Good:

- Widget refresh skips at low battery.
- CancellationException swallowing was fixed in v1.20.2.
- CI runs connected UI tests and release variant builds.

Gaps:

- More alert types will increase worker wakeups.
- Play is increasingly surfacing battery/background warnings for apps with heavy
  background activity.

Plan:

- Any new alert worker must declare cadence, battery gate, notification value,
  and dedupe behavior.
- Add diagnostic UI/log state for last worker run and last skip reason.

### Networking resilience

Good:

- `RateLimitInterceptor` exists for API-key providers.
- Global retry handles IO and transient 5xx.
- Custom User-Agent exists globally; MET Norway has a contact-specific header.

Gaps:

- Retry sleeps inside OkHttp dispatcher thread.
- MET Norway caching headers and If-Modified-Since handling remain incomplete.
- Retrofit 3/OkHttp 5 migration will likely affect logging/redaction APIs.

Plan:

- Move retry into repository-level coroutine helper.
- Add cache/conditional request support for MET Norway.
- Treat OkHttp 5/Retrofit 3 as one dedicated networking migration with tests.

### Distribution trust

Good:

- Freenet flavor keeps proprietary deps split out.
- ACRA is consent/mail based and works in both flavors.
- Release workflow can build all APK variants.

Gaps:

- CI may upload unsigned APKs if signing secrets are absent.
- Reproducible-build status is not established.

Plan:

- Add explicit artifact signing verification to release checklist output.
- Start F-Droid reproducible-build audit with dependency locking and deterministic
  release artifact checks.

## Dependency Upgrade Ladder

Recommended order:

1. Patch/low-risk: Lottie 6.7.1, WorkManager 2.11.2, Wear Tiles 1.6.0,
   Wear ProtoLayout 1.4.0.
2. Database: Room 2.8.4 with DAO/schema regression tests.
3. Wear sync: Play Services Wearable 20.0.1 with DataLayer sync and watch tests.
4. Widget: Glance 1.2.0-rc01 only if widget test APIs are worth RC risk.
5. Map: MapLibre 13.1.0 with radar smoke tests and screenshots.
6. Compiler stack: Kotlin 2.3.x + KSP + Hilt + kotlinx.serialization.
7. Build system: AGP/Gradle once a stable AGP target is confirmed.
8. Networking major: Retrofit 3 + OkHttp 5 with redaction, pinner, retry, and
   adapter tests.

