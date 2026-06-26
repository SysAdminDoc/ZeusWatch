# Research — ZeusWatch

## Executive Summary
ZeusWatch is an LGPL-3.0 Android weather app with phone/tablet UI, Wear OS companion, eight Glance widgets, live wallpaper, Quick Settings tile, radar/lightning/community map surfaces, custom alert rules, optional Gemini Nano summaries, and `standard`/`freenet` flavors. Its strongest current shape is not a single visual feature; it is privacy-preserving, no-required-key weather coverage across many Android surfaces. The highest-value direction is trust maintenance: keep provider availability, release provenance, alert routing, community-report locality, widget freshness, and docs/source claims correct before adding more forecast cards.

Top opportunities, in priority order:
1. Verified: make the release/distribution story truthful after `.github/` and Dependabot removal; README and release docs still advertise nonexistent Actions, attestations, SBOM release uploads, and CI gates.
2. Verified: quarantine or mark the Open-Meteo KMA forecast source unavailable until Open-Meteo's KIM migration is complete; ZeusWatch exposes KMA in settings while Open-Meteo says KMA updates are suspended.
3. Verified: finish the already-roadmapped alert-country fix so remote saved locations do not fall back to the device timezone when Geocoder fails.
4. Verified: finish the already-roadmapped Firestore geohash migration so dense same-latitude community reports cannot starve nearby reports.
5. Verified: expand source-of-truth drift gates for app-visible strings, radar provider inventory, release-workflow claims, dependency versions, and roadmap acceptance wording.
6. Likely: complete the provider metadata/health registry before adding more regional adapters; competitors win on source breadth, but ZeusWatch now needs availability, coverage, attribution, and fallback status as first-class data.
7. Likely: finish widget freshness, accessibility/color checks, baseline profiles, and Wear parity already in `ROADMAP.md`; these are higher-value than new commercial-style novelty.

## Product Map
- Core workflows: onboarding and permission setup; current/location weather review; saved-location search, map pick, and per-location source override; radar/lightning/community map review; custom alert rule creation; widget/Wear/background refresh; settings backup/import.
- User personas: privacy-first FOSS users; severe-weather and radar users; multi-location travelers; widget/Wear-first users; freenet/F-Droid users; source-comparison power users.
- Platforms and distribution: Android app minSdk 26, target/compile SDK 36; Wear module minSdk 30; `standard` flavor with Google/Firebase/Wear DataLayer/Gemini Nano; `freenet` flavor without proprietary Google/Firebase dependencies; local Gradle/fastlane release path after workflow removal.
- Key integrations and data flows: Open-Meteo family, OpenWeatherMap/Pirate Weather optional API-key sources, Bright Sky, MET Norway, Environment Canada, NWS/MeteoAlarm/JMA/ECCC/WMO alerts, RainViewer/Windy/NWS radar, Blitzortung lightning, Firebase Auth/Firestore/App Check for community reports, Gadgetbridge broadcast, Wear DataLayer.

## Competitive Landscape
- Breezy Weather: does broad FOSS source coverage, widgets, localization, and F-Droid distribution well. ZeusWatch should learn from its explicit source discipline and widget issue triage; avoid copying source breadth without ZeusWatch's planned provider metadata and diagnostics.
- WeatherMaster: does fast-moving Material Android weather UX and many regional providers well. ZeusWatch should learn from its widget/source-search issue signal; avoid adding providers faster than fallback, coverage, and source-health contracts can verify them.
- Rain / Bura / Overmorrow: do compact Compose weather presentation and small-surface polish well. ZeusWatch should learn from clear empty/error/offline states; avoid reducing its power-user surfaces into a simple one-scroll clone.
- OSS Weather / Forecastie: show cross-platform and legacy Android weather complaints around offline display, provider discrepancies, and local-time behavior. ZeusWatch should learn from their community issues; avoid broad platform expansion before Android trust surfaces are stable.
- Windy: does model comparison, map layers, and expert forecast visualization well. ZeusWatch should learn from source-agreement and layer clarity; avoid aviation/marine expert bloat before reliability work.
- CARROT Weather: does paid source switching, notifications, widgets, watch surfaces, and customization well. ZeusWatch should learn that source trust and alert delivery are premium-tier features elsewhere; avoid personality/gimmick copy that conflicts with its safety tone.
- MyRadar and RoadTripRadar: do radar-first, route/weather-ahead workflows well. ZeusWatch should learn from route-aware weather planning already on the roadmap; avoid logistics/commercial fleet workflows.
- Gadgetbridge and Home Assistant: show weather as an integration contract, not only an app screen. ZeusWatch should prefer stable ContentProvider/broadcast/API-style surfaces over a plugin marketplace.

## Security, Privacy, and Reliability
- Verified: `.github/` is absent and latest local history includes `e6dba34 Remove GitHub Actions workflows — local builds only`, but `README.md:9`, `README.md:59-60`, `docs/RELEASE.md:42-45`, and `docs/RELEASE.md:76-84` still reference Actions, release workflow outputs, SBOM upload, and GitHub artifact attestations.
- Verified: `tools/check_docs_consistency.py` passes while those workflow claims remain stale; the checker currently covers versions, README inventory, privacy wording, and forbidden planning files, not release-workflow truth.
- Verified: `WeatherSourceProvider.OPEN_METEO_KMA` is selectable in `WeatherSource.kt`, routed in `WeatherSourceManager.kt`, exposed in Settings and Locations source dropdowns, and backed by `OpenMeteoApi.getKmaForecast()`. Open-Meteo's KMA docs say KMA data updates are suspended while migrating to the new KIM model source.
- Verified: `AlertRepository.detectCountry()` still falls back to `TimeZone.getDefault()` when Geocoder fails. This is already in `ROADMAP.md`, but remains a safety-routing risk for remote saved locations.
- Verified: `app/src/standard/java/.../CommunityReportRepository.kt` still uses latitude/timestamp query + client-side longitude filtering. Firebase's own geoquery guidance recommends geohash bounds for this class of locality query; this is already in `ROADMAP.md`.
- Verified: RainViewer public-API compatibility appears fixed in current code: `RainViewerApi.buildTileUrl()` uses the response host, forces Universal Blue, clamps max zoom to 7, and `RadarRepository.kt` consumes past frames only. Remaining radar work is provider evaluation and user-visible limitations, already in `ROADMAP.md`.
- Verified: `npm audit --omit=dev` found 0 npm vulnerabilities for the Firebase rules-test package set. No specific current CVE advisory was found in the researched sources for the pinned OkHttp, Firebase Android SDK, Tink, or MapLibre versions; keep this as an update-check task, not a bug claim.
- Missing guardrails: local release provenance/checksum/SBOM contract after workflow removal; provider availability/status contract that can disable suspended sources; tests for KMA unavailable fallback/import behavior; docs gates for workflow claims and app-visible localized counts; migration tests for geohash legacy docs.
- Recovery and rollback needs: stale imported settings that point at KMA should fall back to Open-Meteo with a visible reason; release docs should describe one authoritative local or CI-backed verification path; community-report migration must tolerate recent legacy docs without geohash.

## Architecture Assessment
- `WeatherSourceManager.kt` remains the highest-value boundary to formalize. The existing provider metadata registry item should own coverage, availability, attribution, auth requirement, fallback eligibility, and contract-test status before more provider adapters land.
- `MainViewModel.kt`, `SettingsScreenContent.kt`, `LocationsScreen.kt`, and `WidgetRefreshWorker.kt` are large orchestrators/components. Existing decomposition, widget parity, baseline profile, and accessibility roadmap items remain the right risk reducers.
- `tools/check_docs_consistency.py` is useful but too narrow for the current project shape. It should learn release-workflow existence, app-visible localized string counts, radar provider inventory, and dependency fingerprint checks.
- `docs/RELEASE.md`, `README.md`, `CLAUDE.md`, and older roadmap acceptance text now disagree with the local-only build decision. Distribution/release documentation is a trust surface because users are told to verify attestations that the repo can no longer generate locally.
- `OpenMeteoApi.kt`, `WeatherSource.kt`, `WeatherSourceManager.kt`, Settings source dropdowns, location per-source overrides, and settings transfer/import need an unavailable-provider path rather than treating every enum entry as selectable.
- Test and documentation gaps: no provider-contract test fails when Open-Meteo marks a model source suspended; no docs check fails when `.github/workflows/*.yml` references survive after workflows are deleted; no acceptance wording standard distinguishes "local check" from "CI gate"; i18n drift is partially visible in onboarding card-count strings.
- Category coverage: security, privacy, reliability, accessibility, i18n, observability, testing, docs, distribution, mobile/Wear, offline/resilience, migration, and upgrade strategy are active or covered by existing items. Plugin ecosystem and multi-user expansion are consciously excluded because current evidence favors stable integrations and optional anonymous community reports over account/plugin systems.

## Rejected Ideas
- Restore GitHub Actions only because stale docs mention it: rejected as a conclusion; the actionable need is one truthful release contract, which can be restored CI or fully local verification.
- Keep KMA selectable with only an error toast: rejected because source availability is a trust contract and imported settings/background refresh need deterministic fallback.
- Add a plugin marketplace: rejected because no researched competitor signal outweighed the maintenance, review, and F-Droid risk; stable provider/integration contracts fit ZeusWatch better.
- Add proprietary APIs as default sources: rejected because README/freenet philosophy is no required keys and no mandatory proprietary services.
- Make Firebase the primary state store: rejected because community reports are optional, append-only, and absent from freenet by design.
- Android Auto weather app as near-term work: rejected because Android's weather app guidance is relevant, but route/weather planning is already roadmapped and current trust issues rank higher.
- Android TV as near-term work: rejected because WeatherMaster's TV issue signal is weaker than phone/widget/Wear/radar reliability work.
- Replace MapLibre with a proprietary map SDK: rejected because it conflicts with freenet/F-Droid parity and existing MapLibre investment.
- Ship direct KMA scraping as a workaround: rejected because Open-Meteo's own suspension is tied to KMA's distribution change; source-contract gating is lower-risk until official model delivery stabilizes.

## Sources
FOSS competitors:
https://github.com/breezy-weather/breezy-weather
https://github.com/breezy-weather/breezy-weather/issues/937
https://github.com/breezy-weather/breezy-weather/issues/2842
https://github.com/PranshulGG/WeatherMaster
https://github.com/PranshulGG/WeatherMaster/issues/925
https://github.com/PranshulGG/WeatherMaster/issues/919
https://github.com/darkmoonight/Rain
https://github.com/darkmoonight/Rain/issues/221
https://github.com/ossappscollective/oss-weather/issues/479
https://github.com/digiexchris/RoadTripRadar

Commercial and adjacent products:
https://play.google.com/store/apps/details?id=com.windyty.android
https://www.meetcarrot.com/weather/presskit.html
https://support.myradar.com/support/solutions/articles/44001261763-main-toolbar
https://gadgetbridge.org/internals/development/weather-support/
https://developers.home-assistant.io/docs/core/entity/weather/

Weather APIs and standards:
https://open-meteo.com/en/docs/kma-api
https://open-meteo.com/en/docs/ukmo-api
https://open-meteo.com/en/docs/dmi-api
https://www.rainviewer.com/api/weather-maps-api.html
https://www.weather.gov/documentation/services-web-alerts
https://docs.oasis-open.org/emergency/cap/v1.2/CAP-v1.2-os.html

Platform, dependency, security:
https://developer.android.com/google/play/requirements/target-sdk
https://developer.android.com/guide/practices/page-sizes
https://developer.android.com/about/versions/16/features/progress-centric-notifications
https://developer.android.com/design/ui/cars/guides/app-types/weather-apps
https://developer.android.com/jetpack/androidx/releases/room
https://firebase.google.com/support/release-notes/android
https://firebase.google.com/docs/firestore/solutions/geoqueries
https://square.github.io/okhttp/changelogs/changelog/
https://maplibre.org/news/2024-12-12-maplibre-android-vulkan/

## Open Questions
- Needs live validation: after Open-Meteo completes KMA/KIM migration, does the KMA endpoint provide stable enough coverage/update timing for ZeusWatch to re-enable it by default in the selector?
