# State Of Repo

Date: 2026-05-17
Repo: `C:\Users\--\repos\ZeusWatch`

## Git State

- Branch: `main`
- Remote: `origin https://github.com/SysAdminDoc/ZeusWatch.git`
- Start state: `main...origin/main [ahead 6]`
- `rtk git log -10` was attempted per shared instructions, but `rtk` is not
  installed in this PowerShell session. Plain `git log -10 --oneline --decorate`
  was used instead.
- Latest six local commits before this research run:
  - `cdf313f docs: consolidate planning docs and add release runbook (NX-19)`
  - `08e12c4 docs(fastlane): reconcile store metadata with v1.20.3 reality (NX-19)`
  - `9afe7d6 refactor: convert two large when expressions to lookup maps (N-8)`
  - `193065a test(standard): cover GeminiNanoSummaryEngine prompt + lifecycle (N-7)`
  - `0014c30 test(widget): cover widgetUpdatedLabel + weatherIconRes pure helpers (N-9)`
  - `1c518d9 test(wear): bootstrap wear OS unit test infrastructure (N-6)`

## Version Surfaces

- `CLAUDE.md`: v1.20.3, phone versionCode 86, wear versionCode 62.
- `README.md`: version badge 1.20.3.
- `app/build.gradle.kts`: `versionName = "1.20.3"`, `versionCode = 86`.
- `wear/build.gradle.kts`: `versionName = "1.20.3"`, `versionCode = 62`.
- `CHANGELOG.md`: latest released section is `[1.20.3] - 2026-05-13`; current
  `[Unreleased]` is localization-heavy.
- `ROADMAP.md`: current version line is v1.20.3.

## Project Shape

- Modules: `:app`, `:wear`.
- App flavors:
  - `standard`: Google Play Services location, Wearable DataLayer, Gemini Nano
    AI Core, Firebase Firestore community reports.
  - `freenet`: F-Droid-compatible, no proprietary deps, no-op Wear sync manager,
    Android `LocationManager` path.
- Release artifacts: standard phone APK, freenet phone APK, wear APK.
- Signing: `zeuswatch.jks` and `local.properties` are local/gitignored secrets.

## File Inventory

Captured with `git ls-files` and PowerShell:

| Metric | Count |
|---|---:|
| Tracked files | 353 |
| Kotlin files | 259 |
| Kotlin test files | 55 |
| App main Kotlin files | 176 |
| App standard flavor Kotlin files | 6 |
| App freenet flavor Kotlin files | 6 |
| Wear main Kotlin files | 16 |
| Wear test Kotlin files | 3 |

## Agent And Planning Files

Found:

- `AGENTS.md` - repo-local pointer to `CLAUDE.md`.
- `CLAUDE.md` - living project notes.
- `CODEX_CHANGELOG.md` - existing tool-local changelog.
- `ROADMAP.md` - active roadmap, tracked by Git as lowercase `roadmap.md`.
- `ROADMAP-COMPLETED.md` - completed milestone ledger.
- `.factory/state.yaml` and `.factory/rubrics/iter1.yaml` - older v1.17.0
  factory-loop state.
- `docs/research-archive.md` - older pre-v1 research, superseded by live roadmap
  but still useful historical context.
- `docs/RELEASE.md` - current release runbook.

No nested `.claude/**`, `.cursor/**`, `GEMINI.md`, or Copilot instruction file
was found in tracked sources.

## Architecture Snapshot

Local evidence:

- `app/src/main/java/com/sysadmindoc/nimbus/data/repository/WeatherSource.kt`
- `app/src/main/java/com/sysadmindoc/nimbus/data/repository/WeatherSourceManager.kt`
- `app/src/main/java/com/sysadmindoc/nimbus/di/NetworkModule.kt`
- `app/src/main/java/com/sysadmindoc/nimbus/data/repository/CardConfig.kt`
- `wear/src/main/java/com/sysadmindoc/nimbus/wear/sync/SyncedWeatherStore.kt`
- `wear/src/main/java/com/sysadmindoc/nimbus/wear/data/WearWeatherRepository.kt`

Current card enum has 28 entries:

`WEATHER_SUMMARY`, `RADAR_PREVIEW`, `NOWCAST`, `HOURLY_FORECAST`,
`TEMPERATURE_GRAPH`, `DAILY_FORECAST`, `UV_INDEX`, `WIND_COMPASS`,
`AIR_QUALITY`, `POLLEN`, `OUTDOOR_SCORE`, `SNOWFALL`, `SEVERE_WEATHER`,
`GOLDEN_HOUR`, `SUNSHINE`, `DRIVING_CONDITIONS`, `HEALTH_ALERTS`,
`CLOTHING`, `PET_SAFETY`, `MOON_PHASE`, `HUMIDITY`,
`PRECIPITATION_CHART`, `PRESSURE_TREND`, `WIND_TREND`, `DETAILS_GRID`,
`CLOUD_COVER`, `VISIBILITY`, `ON_THIS_DAY`.

Current provider enum exposes:

- Open-Meteo: forecast, air quality, minutely.
- NWS: alerts.
- OpenWeatherMap: forecast, alerts, air quality, requires API key.
- Pirate Weather: forecast, requires API key.
- Bright Sky (DWD): forecast, alerts.
- MET Norway: forecast.
- Environment Canada: forecast, alerts.
- MeteoAlarm: alerts.
- JMA: alerts.

## CI And Verification

`.github/workflows/build.yml` currently runs:

- Detekt.
- `lintStandardDebug`.
- Standard debug APK build.
- Freenet debug APK build.
- Wear debug APK build.
- Phone unit tests.
- Wear unit tests.
- Connected standard debug Android tests with accessibility checks.
- Unsigned release variant verification on pushes to main.

`.github/workflows/release.yml`:

- Runs on `v*` tags and manual dispatch.
- Reconstructs signing config only when secrets are present.
- Builds standard, freenet, and wear release APKs.
- Uploads APKs to GitHub Release when a tag is present.

## Local Findings

- `ApiCertificatePins.hostPins` is empty. The pinner is wired to OWM, OWM AQI,
  and Pirate Weather clients, but currently returns the no-op default pinner.
- `NetworkModule` still uses interruptible `Thread.sleep` inside the OkHttp retry
  interceptor. The roadmap already tracks migration to coroutine retry.
- `values/strings.xml` has 846 string entries. `values-es/strings.xml` has 783,
  leaving 63 base entries without Spanish counterparts.
- Grep found no active TODO/FIXME in production source. The main production
  quality hits are known null assertions, the retry backoff location, and
  certificate pin activation.
- `CommunityReportRepository` uses `ReportCondition.valueOf(...)` on Firestore
  payloads. It defaults to `SUNNY` but should eventually use safe parsing.

## Current Repository Direction

The live repo is beyond the older memory entry that described v1.17.0. The
current checkout is v1.20.3 with localization work in progress and six local
commits already implementing roadmap slices N-6, N-7, N-8, N-9, and NX-19.

The next plan should not re-open those completed slices. It should continue with
localization, dependency runway, Wear OS/WFF, provider expansion, and cache/perf
work.

