# Research - ZeusWatch

## Executive Summary
ZeusWatch is an LGPL-3.0 Android weather app with a phone/tablet Compose UI, Wear OS companion, eight Glance widgets, live wallpaper, Quick Settings tile, radar/lightning/community map surfaces, custom alert rules, optional Gemini Nano summaries, and `standard`/`freenet` flavors. Its strongest current shape is privacy-preserving, no-required-key weather coverage across many Android surfaces. The highest-value direction is trust maintenance: provider availability, alert routing, community-report locality, release provenance, widget freshness, and source-of-truth drift gates should land before more visible forecast cards.

Top opportunities, in priority order:
1. Verified: keep the Open-Meteo KMA quarantine item until selectable-provider tests prove suspended sources are hidden, stale imports normalize, and fallback behavior is visible.
2. Verified: finish route alert-source country detection from selected/saved location metadata instead of device timezone fallback.
3. Verified: finish Firestore geohash migration for community reports so dense same-latitude reports cannot starve nearby longitude matches.
4. Verified: expand provider contract checks from five public endpoints to every selectable no-key provider and every Open-Meteo model wrapper.
5. Verified: add a local release provenance manifest beside APKs and `SHA256SUMS.txt` now that releases are intentionally local, not workflow-attested.
6. Likely: provider metadata, diagnostics, and health history should precede additional regional adapters; competitors win on breadth, but ZeusWatch needs coverage/availability/attribution as first-class data.
7. Likely: WFF weather publishing is still not a public third-party API, so Wear effort should focus on complication parity and tests rather than a publisher toggle.

## Product Map
- Core workflows: onboarding and permission setup; current/location weather review; saved-location search, map pick, and per-location source override; radar/lightning/community map review; custom alert rule creation; widget/Wear/background refresh; settings backup/import.
- User personas: privacy-first FOSS users; severe-weather and radar users; multi-location travelers; widget/Wear-first users; freenet/F-Droid users; source-comparison power users.
- Platforms and distribution: Android app minSdk 26, target/compile SDK 36; Wear module minSdk 30; `standard` flavor with Google/Firebase/Wear DataLayer/Gemini Nano; `freenet` flavor without proprietary Google/Firebase dependencies; local signed APK release path.
- Key integrations and data flows: Open-Meteo family, OpenWeatherMap/Pirate Weather optional API-key sources, Bright Sky, MET Norway, Environment Canada, NWS/MeteoAlarm/JMA/ECCC/WMO alerts, RainViewer/Windy/NWS radar, Blitzortung lightning, Firebase Auth/Firestore/App Check for community reports, Gadgetbridge broadcast, Wear DataLayer.

## Competitive Landscape
- Breezy Weather: broad FOSS source coverage, widgets, localization, and F-Droid distribution. Learn from its explicit source discipline and integration surface; avoid adding source breadth without provider metadata and diagnostics.
- WeatherMaster: fast-moving Material Android weather UX and many regional providers. Learn from widget/source-search issue signal; avoid provider additions that lack fallback, coverage, and source-health contracts.
- Rain / Bura / Overmorrow: compact Compose weather presentation and small-surface polish. Learn from clear empty/error/offline states; avoid reducing ZeusWatch's power-user surfaces into a simple one-scroll clone.
- OSS Weather / Forecastie: legacy Android weather complaints around offline display, provider discrepancies, and local-time behavior. Learn from their community issues; avoid broad platform expansion before Android trust surfaces are stable.
- Windy: model comparison, map layers, and expert forecast visualization. Learn from source-agreement and layer clarity; avoid aviation/marine expert bloat before reliability work.
- CARROT Weather: paid source switching, notifications, widgets, watch surfaces, and customization. Learn that source trust and alert delivery are premium-tier features elsewhere; avoid personality/gimmick copy that conflicts with ZeusWatch's safety tone.
- MyRadar and RoadTripRadar: radar-first and route/weather-ahead workflows. Learn from route-aware weather planning already in `ROADMAP.md`; avoid logistics/commercial fleet workflows.
- Gadgetbridge and Home Assistant: weather as an integration contract, not only an app screen. Prefer stable ContentProvider/broadcast/API-style surfaces over a plugin marketplace.

## Security, Privacy, and Reliability
- Verified: `AlertRepository.detectCountry()` still falls back to `TimeZone.getDefault()` when Geocoder fails. This is already in `ROADMAP.md`, but remains a safety-routing risk for remote saved locations.
- Verified: `app/src/standard/java/com/sysadmindoc/nimbus/data/repository/CommunityReportRepository.kt` still uses latitude/timestamp query plus client-side longitude filtering. Firebase recommends geohash bounds for this query class; this is already in `ROADMAP.md`.
- Verified: `tools/check_provider_contracts.py` covers WMO, RainViewer, Open-Meteo forecast, NWS active alerts, and MET Norway. It does not yet cover every selectable provider/model wrapper, so KMA-style upstream availability drift can still escape.
- Verified: local release docs now describe signed local builds and GitHub Release uploads, but release assets still lack one machine-readable provenance file tying APK names, SHA-256 hashes, signing cert, commit SHA, source tree cleanliness, Gradle/JDK versions, and commands run.
- Verified: `docs/WFF_WEATHER_INTEROP.md` correctly concludes there is no documented third-party API for publishing into the WFF system weather store. Wear roadmap work should reinforce complications and tests until Android publishes a normal app publisher contract.
- Verified: `npm audit --omit=dev` found 0 vulnerabilities for the Firebase rules-test package set.
- Missing guardrails: full selectable-provider contract matrix; release provenance manifest generation/validation; geohash legacy-doc migration tests; country-hint alert routing tests; WFF-equivalent complication parity tests.
- Recovery and rollback needs: stale imported settings pointing at unavailable providers should fall back with a warning; release upload should fail on dirty source trees or unsigned APKs; community-report migration must tolerate recent legacy docs without geohash.

## Architecture Assessment
- `WeatherSourceManager.kt` remains the highest-value boundary to formalize. The existing provider metadata registry item should own coverage, availability, attribution, auth requirement, fallback eligibility, and contract-test status before more provider adapters land.
- `AlertRepository.kt` needs a country-hint path from saved/API location metadata before using device timezone heuristics.
- `CommunityReportRepository.kt` needs a geohash write/query migration with Firestore rules and emulator coverage.
- `tools/check_docs_consistency.py` is now useful for workflow-truth and inventory drift, but provider contracts and release provenance should become similarly deterministic local gates.
- `docs/WFF_WEATHER_INTEROP.md`, `wear/.../WeatherComplicationService.kt`, `SyncedWeatherStore.kt`, and Wear tile tests should form the Wear interoperability boundary until WFF exposes a publisher API.
- Test and documentation gaps: no provider-contract test fails when every selectable provider/model wrapper is unavailable or schema-broken; no local release command emits a provenance manifest; no test proves WFF-like Wear payload fields remain available through complications.

## Rejected Ideas
- Restore GitHub Actions only to regain artifact attestations: rejected because the project intentionally moved to local builds; the actionable need is local provenance, not workflow rollback.
- Add a WFF weather publisher toggle now: rejected because Android documents WFF weather expressions and availability checks, not a normal third-party publisher API.
- Keep unavailable providers selectable with only a toast: rejected because source availability is a trust contract and settings import/background refresh need deterministic fallback.
- Add a plugin marketplace: rejected because no researched competitor signal outweighed maintenance, review, and F-Droid risk; stable provider/integration contracts fit ZeusWatch better.
- Add proprietary APIs as default sources: rejected because README/freenet philosophy is no required keys and no mandatory proprietary services.
- Make Firebase the primary state store: rejected because community reports are optional, append-only, and absent from freenet by design.
- Android Auto as near-term standalone work: rejected because route-aware phone planning is already the right prerequisite.
- Replace MapLibre with a proprietary map SDK: rejected because it conflicts with freenet/F-Droid parity and existing MapLibre investment.
- Ship direct KMA scraping as a workaround: rejected because source-contract gating is lower-risk until official model delivery stabilizes.

## Sources
FOSS competitors:
https://github.com/breezy-weather/breezy-weather
https://github.com/PranshulGG/WeatherMaster
https://github.com/darkmoonight/Rain
https://github.com/breezy-weather/breezy-weather/issues
https://github.com/PranshulGG/WeatherMaster/issues
https://github.com/ossappscollective/oss-weather
https://github.com/martykan/forecastie

Commercial and adjacent products:
https://www.windy.com/
https://www.meetcarrot.com/weather/presskit.html
https://support.myradar.com/
https://www.roadtripradar.com/
https://gadgetbridge.org/internals/development/weather-support/
https://developers.home-assistant.io/docs/core/entity/weather/

Weather APIs and standards:
https://open-meteo.com/en/docs/kma-api
https://open-meteo.com/en/docs/dmi-api
https://open-meteo.com/en/docs/ecmwf-api
https://www.rainviewer.com/api/weather-maps-api.html
https://www.weather.gov/documentation/services-web-alerts
https://docs.oasis-open.org/emergency/cap/v1.2/CAP-v1.2-os.html
https://firebase.google.com/docs/firestore/solutions/geoqueries

Platform, dependency, security:
https://developer.android.com/training/wearables/wff/weather
https://developer.android.com/design/ui/cars/guides/app-types/weather-apps
https://developer.android.com/google/play/requirements/target-sdk
https://developer.android.com/jetpack/androidx/releases/room
https://developer.android.com/jetpack/androidx/releases/glance
https://firebase.google.com/support/release-notes/android
https://square.github.io/okhttp/changelogs/changelog/
https://maplibre.org/news/2024-12-12-maplibre-android-vulkan/

## Open Questions
- Needs live validation: after Open-Meteo completes KMA/KIM migration, does the KMA endpoint provide stable enough coverage/update timing for ZeusWatch to re-enable it by default in selectors?
