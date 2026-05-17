# ZeusWatch Project Context

Last consolidated: 2026-05-17.

This is the canonical short context for future repository sessions. It reconciles
the repo-local notes, shared memory, current code, recent commits, and the
2026-05-17 research run under `.ai/research/2026-05-17/`.

## Identity

- Repo: `C:\Users\--\repos\ZeusWatch`
- GitHub remote: `https://github.com/SysAdminDoc/ZeusWatch.git`
- App name: ZeusWatch
- Android package: `com.sysadmindoc.nimbus`
- Wear package: `com.sysadmindoc.nimbus.wear`
- License: LGPL-3.0, confirmed in `LICENSE`, `README.md`, and `CLAUDE.md`
- Current live repo version: v1.20.3
- Phone version: `versionName = "1.20.3"`, `versionCode = 86` in `app/build.gradle.kts`
- Wear version: `versionName = "1.20.3"`, `versionCode = 62` in `wear/build.gradle.kts`
- Branch state at consolidation: `main` was 6 commits ahead of `origin/main` before this research commit.

## Required Read Order

Read these first:

1. `AGENTS.md` - points to `CLAUDE.md`.
2. `CLAUDE.md` - living project notes, build commands, architecture, release history, gotchas.
3. `ROADMAP.md` - current source-backed planning document.
4. `ROADMAP-COMPLETED.md` - closed milestones.
5. `.ai/research/2026-05-17/STATE_OF_REPO.md` - current reconnaissance memo.
6. `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md` - latest scoring.
7. `docs/RELEASE.md` - release procedure and signing/TLS-pin checklist.

Global/shared rules used by this project come from:

- `C:/Users/--/.claude/CLAUDE.md`
- `C:/Users/--/CLAUDE.md`
- `C:/Users/--/.claude/projects/c--Users----repos/memory/zeuswatch.md`
- `C:/Users/--/.claude/projects/c--Users----repos/memory/stack-android.md`

Memory is point-in-time. Verify with `git log`, `git status`, and source files
before acting.

## Product Philosophy

ZeusWatch is a FOSS Android weather app trying to combine the depth of commercial
weather apps with the privacy and reproducibility posture of F-Droid apps.

Guardrails from `ROADMAP.md`:

- No required API keys. Optional keys are acceptable.
- Keep the `freenet` flavor free of proprietary dependencies.
- Normalize raw provider data to metric at adapter boundaries; convert only in
  `WeatherFormatter`.
- Treat every provider as an adapter behind `WeatherSourceManager`.
- Accessibility is part of the feature definition, especially for Canvas cards.
- Preserve the premium dark, dense, utilitarian visual language.
- Do not put features behind paywalls.

## Stack

- Kotlin 2.1.0 and KSP 2.1.0-1.0.29
- Android Gradle Plugin 8.7.3
- Gradle wrapper 8.9
- Jetpack Compose BOM 2025.04.01 in `gradle/libs.versions.toml`
- Hilt, Retrofit, OkHttp, kotlinx.serialization, Room, DataStore, WorkManager
- MapLibre, RainViewer tiles, Blitzortung WebSocket
- Jetpack Glance widgets
- Wear OS Compose, Tiles, complications, DataLayer sync
- ACRA mail/dialog crash reporting for standard and freenet flavors
- Firebase Firestore only in `standard` flavor for community reports

Local build environment from project/shared notes:

```powershell
$env:JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
$env:ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"
.\gradlew.bat :app:assembleStandardRelease
.\gradlew.bat :app:assembleFreenetRelease
.\gradlew.bat :wear:assembleRelease
```

## Modules And Flavors

- `:app` is the phone/tablet app.
- `:wear` is the Wear OS companion.
- `standard` flavor includes Google Play Services location, Wear DataLayer,
  Gemini Nano AI Core, and Firestore community reports.
- `freenet` flavor removes proprietary dependencies and keeps F-Droid viability.

## Current Feature Surface

Evidence: `README.md`, `CLAUDE.md`, `ROADMAP-COMPLETED.md`, and source files.

- 28 reorderable card types in `CardConfig.kt`.
- Main screens: Today, Hourly, Daily, Radar, Locations, Settings, Compare, Custom Alerts.
- Native MapLibre radar with RainViewer frames, optional Windy WebView, satellite/cloud overlays, and Blitzortung lightning.
- Open-Meteo forecast/AQI/pollen/minutely/archive baseline.
- Provider adapters: Open-Meteo, NWS alerts, OpenWeatherMap, Pirate Weather,
  Bright Sky, MET Norway, Environment Canada, MeteoAlarm, JMA.
- Smart alerts: severe weather, nowcast transitions, driving hazards, health,
  custom user thresholds.
- Widgets: Current, 3-Day, Forecast, Hourly Strip via Glance.
- Wear OS: current, hourly, daily, alerts screens; tile; complication service
  with `SHORT_TEXT`, `LONG_TEXT`, `RANGED_VALUE`, and `SMALL_IMAGE`;
  phone-to-watch DataLayer sync plus direct Open-Meteo fallback.
- Localization local coverage is complete for the current app string surface:
  `values/strings.xml` has 926 strings and `values-es/strings.xml` has 926
  strings as of the N-3/N-4 continuation pass on 2026-05-17. Wear Spanish
  coverage is complete for the current Wear string surface: 43 default strings
  and 43 Spanish strings.

## Repository Metrics

Captured 2026-05-17:

- 375 tracked files after the N-6 batch is committed.
- 263 Kotlin files.
- 56 Kotlin test files.
- 176 app main Kotlin files.
- 6 app standard Kotlin files.
- 6 app freenet Kotlin files.
- 18 wear main Kotlin files.
- 5 wear test Kotlin files.

Current CI:

- `.github/workflows/build.yml` runs Detekt, Android lint, debug builds for
  standard/freenet/wear, app unit tests, wear unit tests, connected app UI tests
  with accessibility checks, and unsigned release build verification on main.
- `.github/workflows/release.yml` builds standard/freenet/wear release APKs on
  tags and uploads them to GitHub Releases. Signing is conditional on GitHub
  secrets.

## Current Planning State

Top Now items from the 2026-05-17 refresh:

1. Finish localization extraction and translation pipeline. Local extraction
   gates and Spanish coverage are complete; external Weblate setup and
   additional community locales remain.
2. Populate certificate pins for keyed endpoints. Local N-2 work captured
   OpenWeatherMap and Pirate Weather pins on 2026-05-17, added a PowerShell
   capture script, and documented the release update procedure.
3. Reduce Detekt baseline during feature work.
4. Upgrade dependencies in a staged runway, starting with low-risk patch/minor
   AndroidX moves and leaving Kotlin/AGP/Gradle major jumps for dedicated
   compatibility work.

The 2026-05-17 dependency check found no OSV advisories for sampled current
Maven coordinates, but several upgrades are important: Room 2.8.x, WorkManager
2.11.x, Wear Tiles 1.6.x, Play Services Wearable 20.x, Retrofit 3.x, OkHttp
5.x, MapLibre 13.x, Compose BOM 2026.05.00, and Kotlin 2.3.x stable.

The local localization gate is `python tools/check_localization.py`, and the
translator handoff is documented in `docs/TRANSLATION.md`. TLS pin capture for
keyed endpoints is handled by `tools/capture_api_pins.sh` and
`tools/capture_api_pins.ps1`; current public SPKI pins live in
`ApiCertificatePins.hostPins`. The safe Australian BOM forecast path is now a
selectable `Open-Meteo + BOM ACCESS-G` provider backed by Open-Meteo `/v1/bom`;
the undocumented direct BOM app API remains intentionally unused. Wear
complications now cover all locally declared watch-face slot types. WFF weather
provider interoperability is documented as a compatibility boundary in
`docs/WFF_WEATHER_INTEROP.md`: no public third-party publisher API is available
for ZeusWatch to write into the Wear OS system weather store. Wear service test
coverage now includes direct API fetch mapping, phone-sync short-circuiting, API
error handling, and tile request futures. The Detekt baseline is down to 4 IDs
after the RadarPreviewCard, CurrentConditionsHeader, CustomAlertsScreen
RuleEditor, SunArc, TemperatureGraph, WidgetRefreshWorker, and
SyncedWeatherStore, CompareScreen, RadarScreen, MainViewModel, and SettingsScreen
helper/payload extractions plus the MainScreen RenderCard split on 2026-05-17.

## High-Value Differentiators

- Native Wear OS support is the strongest open-source moat. Breezy is deeper on
  providers and widgets, but ZeusWatch already has a first-party watch app,
  tile, complications, and phone-to-watch sync.
- Health/driving/pet/custom alerts remain unusually strong for FOSS weather.
  Commercial apps monetize rain, lightning, storm-cell, and custom alerts; ZeusWatch
  can make these free with careful battery controls.
- Native radar plus lightning is a differentiator against Breezy, which explicitly
  declines radar in its README.
- `freenet` flavor plus ACRA mail reporting preserves privacy and F-Droid fit.

## Known Risks

- `ApiCertificatePins.hostPins` is still empty, so keyed endpoints are not
  actually pinned despite pinning scaffolding.
- `NetworkModule` still performs retry backoff inside an OkHttp interceptor using
  `Thread.sleep`; it is interruptible but blocks dispatcher slots.
- VersionCode divergence between phone and wear is intentional unless Play Store
  distribution requires alignment.
- `main` had six local commits ahead of `origin/main` at the start of the
  research run. Pushes from this checkout will publish those commits too.
- Some null assertions in production Compose screens are guarded by surrounding
  state checks, but the audit grep still flags them for future cleanup.
- `CLAUDE.md` and `CODEX_CHANGELOG.md` are repo-local AI/work notes; existing
  project rules keep them local-only unless the user explicitly asks otherwise.

## Research Artifacts

The 2026-05-17 research bundle contains:

- `STATE_OF_REPO.md` - repo state and architecture memo.
- `MEMORY_CONSOLIDATION.md` - instruction/memory reconciliation.
- `SOURCE_REGISTER.md` - local and external sources used.
- `RESEARCH_LOG.md` - queries, passes, failed searches, saturation check.
- `COMPETITOR_MATRIX.md` - direct and adjacent product comparison.
- `FEATURE_BACKLOG.md` - raw harvested opportunities.
- `PRIORITIZATION_MATRIX.md` - scored and tiered candidates.
- `SECURITY_AND_DEPENDENCY_REVIEW.md` - dependency freshness and hardening.
- `DATASET_MODEL_INTEGRATION_REVIEW.md` - datasets, APIs, models, integrations.
- `CHANGESET_SUMMARY.md` - files changed by the research pass.
