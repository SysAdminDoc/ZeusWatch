# Feature Backlog

Date: 2026-05-17

Raw harvested ideas before final prioritization. Scores and sequencing live in
`PRIORITIZATION_MATRIX.md`.

## Provider And Data Sources

| Idea | Source evidence | Local fit |
|---|---|---|
| Finish localization extraction and Weblate pipeline. | F-Droid localization docs: https://f-droid.org/docs/Translation_and_Localization/; Breezy Weblate references in README. | High. Local `values-es` has 783/846 base strings. |
| Surface Open-Meteo ACCESS-G/BOM model for Australian users before direct BOM API. | Open-Meteo docs list ACCESS-G/BOM model: https://open-meteo.com/en/docs | High. Safe path respects no-key/no-risk guardrail. |
| Direct BOM adapter spike. | BOM feeds: https://www.bom.gov.au/catalogue/data-feeds.shtml | Medium. Needs legal/API stability check. |
| Meteo-France forecast/AROME/PIAF/Vigilance adapter. | Meteo-France docs: https://confluence-meteofrance.atlassian.net/wiki/spaces/OpenDataMeteoFrance/pages/853737487/Documentation%2BAPI | High for France; key/token flow required. |
| GeoSphere Austria INCA/alerts adapter. | Breezy source matrix and Open-Meteo GeoSphere model reference. | Medium. Needs implementation spike. |
| FMI + KNMI provider bundle. | Breezy sources/changelog: https://github.com/breezy-weather/breezy-weather/blob/main/docs/SOURCES.md | Medium-high. Good parity win. |
| Open-Meteo Marine card. | Open-Meteo Marine API: https://open-meteo.com/en/docs/marine-weather-api | Medium. Coastal opt-in card. |
| Open-Meteo Flood/GloFAS card. | Open-Meteo docs list Flood: https://open-meteo.com/en/docs | Medium. Opt-in safety card. |
| NOAA SWPC aurora/Kp card. | NOAA OVATION product and JSON index: https://www.swpc.noaa.gov/products/aurora-30-minute-forecast | High novelty, low implementation complexity. |

## Wear OS And Widgets

| Idea | Source evidence | Local fit |
|---|---|---|
| Finish Wear service tests. | Local tests only cover repository/store helpers. | High. Existing wear module is a differentiator. |
| Expand complication types and verify data freshness. | WFF/complication docs: https://developer.android.com/training/wearables/wff/weather | High. Local `WeatherComplicationService` exists. |
| Investigate WFF weather provider interoperability. | WFF docs say weather relies on connected handheld/network location. | High. Could put ZeusWatch data onto more watch faces. |
| Upgrade Glance to 1.2.0-rc01 for widget unit-test APIs. | Glance release notes: https://developer.android.com/jetpack/androidx/releases/glance | Medium. RC risk; valuable for N-9. |
| Add widget debug/freshness diagnostics screen. | Widget update complaints in search results; local WidgetRefreshWorker exists. | Medium. Helps trust and support. |
| Map/weather widget for premium parity. | CARROT maps widget requires Ultra: https://support.meetcarrot.com/weather/index.html | Medium. Needs battery/cache design. |

## Alerts And Safety

| Idea | Source evidence | Local fit |
|---|---|---|
| Lightning proximity notification. | CARROT Ultra lightning notifications; local Blitzortung service. | High. Free differentiator. |
| Storm-cell or warning-polygon radar overlay. | Storm Radar layers and NOAA/NWS warnings; SPC MapServer in existing roadmap. | Medium-high. US-first. |
| Expand custom alerts: AQI, dewpoint, heat index, wind chill, snowfall, lightning. | CARROT custom notifications; local CustomAlertEvaluator. | High. Pure enum/UI/work expansion. |
| Battery-budget controls for all background alert workers. | Play background activity warning coverage; local multiple workers. | High. Prevent future store/battery issues. |
| Route-weather/driving mode. | Weather on the Way and MyRadar Android Auto. | Medium. Later after Android Auto spike. |

## Reliability, Security, Performance

| Idea | Source evidence | Local fit |
|---|---|---|
| Populate certificate pins for keyed APIs. | Local `ApiCertificatePins.kt`, `docs/RELEASE.md`. | High if pins captured safely. |
| Move retry from OkHttp interceptor to coroutine repository helper. | Local `NetworkModule.kt` and roadmap. | High. Reduces dispatcher blocking. |
| Upgrade Room to 2.8.x. | Room 2.8.2 fixed Flow/database reopening deadlock: https://developer.android.com/jetpack/androidx/releases/room | High. DB/cache-heavy app. |
| Upgrade WorkManager to 2.11.x. | Maven metadata. | Medium. Worker-heavy app. |
| Upgrade Play Services Wearable to 20.x. | Maven metadata. | Medium. Wear sync critical path. |
| Upgrade Retrofit 3 and OkHttp 5 in a dedicated networking pass. | Maven metadata. | Medium-high. Major-version risk. |
| Upgrade MapLibre 13.x and reassess rendering backend/perf. | MapLibre project docs and Maven metadata. | Medium. Radar-critical. |
| Baseline Profiles + Macrobenchmark module. | Android docs: https://developer.android.com/develop/ui/compose/performance/baseline-profiles | High. Compose-heavy app. |
| Reproducible-build audit for F-Droid. | F-Droid reproducible builds: https://f-droid.org/en/docs/Reproducible_Builds/ | High trust value. |

## UX, Ecosystem, And Distribution

| Idea | Source evidence | Local fit |
|---|---|---|
| ContentProvider + broadcast for Gadgetbridge/Tasker/KWGT. | Breezy ContentProvider and data sharing references. | High. Makes ZeusWatch a platform. |
| Model agreement/forecast confidence card. | Windy model compare; multi-provider local architecture. | Medium-high. Watch API quota. |
| Android Auto weather/driving module. | Weather on the Way, MyRadar, Android for Cars search results. | Later. Needs driver-distraction design. |
| Android TV variant. | WeatherYou TV support. | Later. Good reuse of Compose but new module cost. |
| Template authoring for weather summaries. | Local template `WeatherSummaryEngine`; power-user pattern. | Medium. Useful but secondary to i18n. |
| Alternate numeral/calendar support. | Breezy README accessibility/localization claims. | Later after translation pipeline. |

