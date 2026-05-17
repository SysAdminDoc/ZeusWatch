# Source Register

Date: 2026-05-17

Every meaningful claim in the research bundle maps to a local file or URL below.

## Local Repository Sources

| ID | Source | Used for |
|---|---|---|
| L1 | `AGENTS.md` | Repo instruction entrypoint and `CLAUDE.md` delegation. |
| L2 | `CLAUDE.md` | Current version, architecture, build, release history, gotchas. |
| L3 | `README.md` | User-facing feature inventory, screenshots, badges, architecture summary. |
| L4 | `ROADMAP.md` | Existing strategic plan and source-backed backlog. |
| L5 | `ROADMAP-COMPLETED.md` | Closed milestone ledger. |
| L6 | `CHANGELOG.md` | Current `[Unreleased]` localization work and v1.20.x history. |
| L7 | `docs/RELEASE.md` | Release procedure, signing secrets, TLS pin capture, rollback. |
| L8 | `docs/research-archive.md` | Historical pre-v1 research and shipped feature lineage. |
| L9 | `.factory/state.yaml` | v1.17 factory-loop context and stale state marker. |
| L10 | `.factory/rubrics/iter1.yaml` | Old acceptance criteria for ECCC, MET Norway, pinning, widget, a11y. |
| L11 | `settings.gradle.kts` | Module list and repositories. |
| L12 | `build.gradle.kts` | Shared plugin and Detekt config. |
| L13 | `gradle/libs.versions.toml` | Current dependency versions. |
| L14 | `app/build.gradle.kts` | App version, flavors, deps, signing behavior. |
| L15 | `wear/build.gradle.kts` | Wear version, deps, signing behavior. |
| L16 | `.github/workflows/build.yml` | CI build, test, lint, emulator a11y, release verification. |
| L17 | `.github/workflows/release.yml` | Tag release build and GitHub Release upload behavior. |
| L18 | `app/src/main/java/com/sysadmindoc/nimbus/data/repository/WeatherSource.kt` | Provider enum and source capabilities. |
| L19 | `app/src/main/java/com/sysadmindoc/nimbus/data/repository/CardConfig.kt` | 28 card types and default enabled state. |
| L20 | `app/src/main/java/com/sysadmindoc/nimbus/di/NetworkModule.kt` | Retry behavior, User-Agent, rate limiting, pinner wiring, redaction. |
| L21 | `app/src/main/java/com/sysadmindoc/nimbus/data/api/ApiCertificatePins.kt` | Empty `hostPins` and no-op pinner state. |
| L22 | `app/src/main/res/values/strings.xml` | Base localization count. |
| L23 | `app/src/main/res/values-es/strings.xml` | Spanish localization count. |
| L24 | `wear/src/test/java/com/sysadmindoc/nimbus/wear/testing/FakeSharedPreferences.kt` | Wear test infrastructure evidence. |
| L25 | `fastlane/metadata/android/en-US/full_description.txt` | Store listing state reconciled to v1.20.3. |

## Shared Memory Sources

| ID | Source | Used for |
|---|---|---|
| M1 | `C:/Users/--/.claude/CLAUDE.md` | Global behavior, no pill backdrops, roadmap auto-continue, rtk preference. |
| M2 | `C:/Users/--/CLAUDE.md` | Session start ritual, Definition of Done, Android stack list, auto-commit/push. |
| M3 | `C:/Users/--/.claude/projects/c--Users----repos/memory/MEMORY.md` | Project index and ZeusWatch memory pointer. |
| M4 | `C:/Users/--/.claude/projects/c--Users----repos/memory/zeuswatch.md` | Prior ZeusWatch project memory, release/build facts, gotchas. |
| M5 | `C:/Users/--/.claude/projects/c--Users----repos/memory/stack-android.md` | Android stack conventions. |
| M6 | `C:/Users/--/.codex/memories/MEMORY.md` | Prior Codex ZeusWatch and autonomous roadmap workflow memory. |

## External Competitor Sources

| ID | URL | Used for |
|---|---|---|
| E1 | https://github.com/breezy-weather/breezy-weather | Breezy feature baseline: 50+ sources, widgets, icon packs, live wallpaper, data sharing, privacy. |
| E2 | https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md | Source coverage matrix and provider ideas: FMI, KNMI, Meteo-France, GeoSphere, AccuWeather, Open-Meteo. |
| E3 | https://github.com/breezy-weather/breezy-weather/blob/main/CHANGELOG.md | Recent provider additions and ContentProvider signal. |
| E4 | https://github.com/PranshulGG/WeatherMaster | WeatherMaster positioning, Crowdin translation, stars/activity. |
| E5 | https://github.com/rodrigmatrix/weather-you | WeatherYou multi-device Compose baseline: phone, tablet, TV, Wear OS. |
| E6 | https://github.com/martykan/forecastie | Forecastie simple/offline/OpenWeather baseline. |
| E7 | https://support.meetcarrot.com/weather/index.html | CARROT premium sources, alerts, maps, widgets, complications. |
| E8 | https://support.meetcarrot.com/weather/subscription-mobile.html | CARROT subscription economics, maps, watch complications, update cadence. |
| E9 | https://play.google.com/store/apps/details?id=com.accuweather.android&hl=en-us | AccuWeather Android/Wear/TV, MinuteCast, health/activity, data safety. |
| E10 | https://windy.app/features | Windy.app model comparison, professional outdoor/weather layers. |
| E11 | https://weather.com/storm-radar | Storm Radar map layers, 6-hour future radar, storm tracks, alerts. |
| E12 | https://weatherontheway.app/features | Route-weather feature idea. |

## External Platform And Dependency Sources

| ID | URL | Used for |
|---|---|---|
| P1 | https://developer.android.com/training/wearables/wff/weather | WFF weather fields and connected-handheld location behavior. |
| P2 | https://developer.android.com/jetpack/androidx/releases/glance | Glance 1.2.0-rc01, widget previews, `runGlanceAppWidgetUnitTest`. |
| P3 | https://developer.android.com/jetpack/androidx/releases/room | Room 2.8.x changes and bug fixes. |
| P4 | https://kotlinlang.org/docs/releases.html | Kotlin 2.3.x stable train and 2.3.21 release. |
| P5 | https://developer.android.com/develop/ui/compose/performance/baseline-profiles | Baseline Profile performance value and Macrobenchmark path. |
| P6 | https://developer.android.com/jetpack/androidx/releases/navigation3 | Navigation 3 status and Compose navigation direction. |
| P7 | https://developer.android.com/jetpack/androidx/releases/compose-material3 | Material3/Expressive release surface. |
| P8 | https://maplibre.org/projects/native/ | MapLibre supported platforms and graphics backends. |
| P9 | https://f-droid.org/docs/Translation_and_Localization/ | F-Droid localization/Weblate process. |
| P10 | https://f-droid.org/en/docs/Reproducible_Builds/ | F-Droid reproducible-build expectations. |
| P11 | https://api.osv.dev/v1/querybatch | Vulnerability query endpoint used for sampled Maven coords. |
| P12 | https://services.gradle.org/versions/current | Gradle current version metadata. |

## External Weather/Data/API Sources

| ID | URL | Used for |
|---|---|---|
| D1 | https://open-meteo.com/en/docs | Open-Meteo variables, model coverage, ACCESS-G/BOM model, KNMI, GeoSphere, MET Nordic. |
| D2 | https://open-meteo.com/en/docs/marine-weather-api | Marine variables, wave/current/SST, caution note, 15-min variables. |
| D3 | https://docs.api.met.no/doc/locationforecast/FAQ.html | MET Norway caching and If-Modified-Since guidance. |
| D4 | https://api.met.no/weatherapi/locationforecast/2.0/documentation | MET Norway Locationforecast User-Agent requirement. |
| D5 | https://confluence-meteofrance.atlassian.net/wiki/spaces/OpenDataMeteoFrance/pages/853737487/Documentation%2BAPI | Meteo-France public API documentation. |
| D6 | https://confluence-meteofrance.atlassian.net/wiki/spaces/OpenDataMeteoFrance/pages/854032416 | Meteo-France AROME/PIAF model API docs. |
| D7 | https://www.swpc.noaa.gov/products/aurora-30-minute-forecast | NOAA OVATION aurora forecast product. |
| D8 | https://services.swpc.noaa.gov/json/ | NOAA SWPC JSON index and current timestamps. |
| D9 | https://www.bom.gov.au/catalogue/data-feeds.shtml | BOM data feed discovery and direct-data caution source. |

## Maven Metadata Sources

Fetched with `Invoke-WebRequest` or `Invoke-RestMethod` on 2026-05-17:

- `https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml`
- `https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml`
- `https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/maven-metadata.xml`
- `https://dl.google.com/dl/android/maven2/androidx/glance/glance-appwidget/maven-metadata.xml`
- `https://dl.google.com/dl/android/maven2/androidx/work/work-runtime-ktx/maven-metadata.xml`
- `https://dl.google.com/dl/android/maven2/androidx/wear/tiles/tiles/maven-metadata.xml`
- `https://dl.google.com/dl/android/maven2/androidx/wear/protolayout/protolayout/maven-metadata.xml`
- `https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-wearable/maven-metadata.xml`
- `https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/android/org.jetbrains.kotlin.android.gradle.plugin/maven-metadata.xml`
- `https://repo.maven.apache.org/maven2/com/squareup/retrofit2/retrofit/maven-metadata.xml`
- `https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml`
- `https://repo.maven.apache.org/maven2/org/maplibre/gl/android-sdk/maven-metadata.xml`

