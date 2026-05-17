# Research Log

Date: 2026-05-17

## Local Recon Pass

Commands and checks:

- Attempted `rtk git log -10 --oneline --decorate`; failed because `rtk` is not
  installed in this PowerShell session.
- Ran `git status --short --branch`.
- Ran `git log -10 --oneline --decorate`.
- Ran `git remote -v`.
- Listed tracked project files with `rg --files -uu` excluding `.git`, build,
  Gradle cache, and dist outputs.
- Read root Gradle files, version catalog, app/wear Gradle files, CI workflows,
  README, roadmap, changelog, release docs, factory state, and fastlane metadata.
- Counted tracked files and Kotlin/test files with `git ls-files`.
- Searched for `TODO`, `FIXME`, `HACK`, `NotImplementedError`, `Thread.sleep`,
  `!!`, and `valueOf(`.
- Counted base and Spanish Android string resources.
- Queried Maven metadata for current dependency release status.
- Queried OSV for sampled Maven vulnerability advisories.

## External Search Pass 1: Direct Competitors

Queries:

- `Breezy Weather GitHub Android weather app sources Wear OS widgets ContentProvider changelog`
- `WeatherMaster Android weather app GitHub feature weather sources`
- `Weather You Android weather app GitHub Wear OS TV Compose weather app`
- `open source Android weather app Forecastie Cirrus QuickWeather GitHub features`

Useful sources:

- Breezy Weather README and sources matrix.
- WeatherMaster README.
- WeatherYou README.
- Forecastie README.

Saturation note: search results repeated Breezy, WeatherMaster, WeatherYou,
Forecastie, and store/listing mirrors. Additional direct FOSS apps were weaker
or stale relative to ZeusWatch. Breezy remains the most important comparator.

## External Search Pass 2: Platform And Wear OS

Queries:

- `Android Developers Wear OS 6 Watch Face Format weather data fields 2026`
- `Android Developers Jetpack Glance release notes 1.2.0 widget testing`
- `Android Developers Compose Material 3 adaptive NavigationSuiteScaffold release notes`
- `MapLibre Native Android changelog 12.3.0 WebGPU`

Useful sources:

- Android WFF weather-data docs.
- AndroidX Glance release notes.
- Material3 adaptive and Material3 release notes.
- MapLibre Native project page and rendering backend references.

Saturation note: WFF sources converged on Android Developers docs. News/reddit
results reinforced user pain around Wear weather updates but did not add a more
authoritative implementation source than Android Developers.

## External Search Pass 3: Weather APIs, Datasets, Models

Queries:

- `Open-Meteo API documentation marine weather flood API pollen air quality weather models BOM ACCESS-G`
- `Meteo France API AROME PIAF Vigilance documentation open data weather API`
- `GeoSphere Austria INCA nowcasting API CC0 dataset`
- `NOAA SWPC aurora ovation Kp forecast JSON API`
- `MET Norway Locationforecast API User-Agent If-Modified-Since Expires terms`
- `Bureau of Meteorology API weather bom data feeds terms Android weather app`
- `Open-Meteo BOM API ACCESS-G Australia documentation`
- `Open-Meteo marine weather API flood API documentation`

Useful sources:

- Open-Meteo core docs and Marine API docs.
- MET Norway Locationforecast docs and FAQ.
- Meteo-France Confluence docs.
- NOAA SWPC product page and JSON directory.
- BOM data feeds page.

Saturation note: GeoSphere direct docs were less discoverable from search than
Breezy and Open-Meteo model references. The roadmap should keep GeoSphere as a
source candidate but require a focused implementation research slice before code.

## External Search Pass 4: Commercial/Adjacent Products

Queries:

- `CARROT Weather features premium weather app app support alerts radar complications`
- `Weather.com app features hourly radar alerts widgets Android weather app`
- `AccuWeather Android app MinuteCast alerts radar health pollen features`
- `Windy app weather radar satellite lightning route planner Android Auto CarPlay features`
- `Android weather app widget not updating complaints 2026 Reddit Wear OS weather complication not updating`
- `weather app battery drain background updates complaints Android widgets 2026`
- `weather app Android Auto radar route weather MyRadar Carrot Weather on the Way features`

Useful sources:

- CARROT support/pricing/subscription docs.
- AccuWeather Play listing.
- Windy.app features page.
- Weather.com Storm Radar page.
- Weather on the Way feature page.
- Android Central and Reddit results around Wear weather app/widget failures.

Saturation note: commercial sources repeated the same premium clusters: rain
alerts, lightning/storm-cell alerts, radar layers, maps widgets, route weather,
health/activity, and watch complications. These map well to ZeusWatch's free
differentiator strategy.

## Dependency And Security Pass

Dependency freshness sources:

- Google Maven metadata.
- Maven Central metadata.
- Gradle current-version endpoint.
- AndroidX release notes.
- Kotlin release history.
- OSV querybatch.

Result:

- No OSV vulnerabilities found for sampled current coordinates.
- Current repo has several upgrade opportunities. Some are safe patch/minor
  candidates, while Kotlin/AGP/Gradle and OkHttp/Retrofit major changes require
  dedicated compatibility work.

## Failed Or Thin Areas

- Direct BOM public API details remain risky. The safe path is Open-Meteo's
  ACCESS-G/BOM model surfacing before undocumented direct BOM integration.
- GeoSphere implementation details need a dedicated country-source spike.
- Android Auto/weather car templates had weaker direct doc discoverability in
  search results than route-weather commercial pages. Keep as Later until a
  source-backed implementation pass is done.
- No local Play Store/F-Droid metadata build was run in this research pass.

## Source Saturation Test

I stopped external searching after:

- Direct FOSS searches repeatedly returned Breezy, WeatherMaster, WeatherYou,
  Forecastie, and mirror pages.
- Commercial searches repeatedly returned CARROT, AccuWeather, Windy, Weather.com,
  MyRadar/route-weather products, and widget/Wear update complaints.
- API searches converged on Open-Meteo, MET Norway, Meteo-France, NOAA SWPC,
  BOM, and Breezy's source matrix.
- Dependency searches converged on Maven metadata plus AndroidX/Kotlin official
  release notes.

Remaining research would likely refine individual implementation details rather
than change the top roadmap priorities.

## Self-Audit

Completion criteria check:

- Required root `PROJECT_CONTEXT.md`: created.
- Required root `ROADMAP.md`: updated in place.
- Required dated research files: created.
- Local repo reconnaissance: complete for docs/planning purposes.
- Memory consolidation: complete.
- Multiple external research passes: complete.
- Source saturation tested: complete.
- Security/dependency review: complete.
- Dataset/model/integration review: complete.
- Remaining limitations documented: complete in this log and the specialized
  review files.

