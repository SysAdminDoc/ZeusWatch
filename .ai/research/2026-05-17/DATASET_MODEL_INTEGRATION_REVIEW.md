# Dataset, Model, And Integration Review

Date: 2026-05-17

ZeusWatch is not an ML research product, but it is data-heavy: weather models,
radar tiles, lightning streams, alert feeds, geocoding, on-device Gemini Nano,
Wear OS DataLayer, widgets, and distribution metadata all matter.

## Existing Data And Model Integrations

Local evidence:

- `OpenMeteoApi.kt`, `AirQualityApi.kt`, `OpenMeteoArchiveApi.kt`
- `RainViewerApi.kt`
- `BlitzortungService.kt`
- `NwsAlertAdapter.kt`, `MeteoAlarmAdapter.kt`, `JmaAlertAdapter.kt`,
  `EnvironmentCanadaAlertAdapter.kt`
- `MetNorwayAdapter.kt`, `BrightSkyAdapters.kt`, `OwmAdapters.kt`,
  `PirateWeatherAdapter.kt`, `EnvironmentCanadaForecastAdapter.kt`
- `GeminiNanoSummaryEngine.kt`, `WeatherSummaryEngine.kt`
- `WearSyncManager.kt`, `SyncedWeatherStore.kt`

Current integration map:

| Integration | Type | Status |
|---|---|---|
| Open-Meteo forecast | Forecast/current/hourly/daily | Primary default. |
| Open-Meteo AQ/pollen | AQI/pollen | Existing via AQ repository. |
| Open-Meteo minutely_15 | Nowcast | Existing rain next hour and nowcast alerts. |
| Open-Meteo archive | Historical normals/on-this-day | Existing. |
| RainViewer | Radar/satellite tiles | Existing. |
| Blitzortung | Lightning WebSocket | Existing. |
| NWS | US alerts | Existing. |
| MeteoAlarm | EU alerts | Existing. |
| JMA | Japan alerts | Existing. |
| Environment Canada | Canada forecast/alerts | Existing. |
| MET Norway | Nordic forecast | Existing. |
| Bright Sky/DWD | Germany forecast/alerts | Existing. |
| OpenWeatherMap | Optional-key forecast/AQI/alerts | Existing fallback. |
| Pirate Weather | Optional-key forecast | Existing fallback. |
| Gemini Nano AI Core | On-device summary generation | Standard flavor only. |
| Firebase Firestore | Community reports | Standard flavor only. |
| Wear DataLayer | Phone-to-watch sync | Standard flavor only. |

## High-Value API/Dataset Opportunities

| Candidate | Evidence | Fit | Notes |
|---|---|---|---|
| Open-Meteo ACCESS-G/BOM model surfacing | https://open-meteo.com/en/docs | High | Safe Australian coverage without direct undocumented BOM API. |
| Open-Meteo Marine API | https://open-meteo.com/en/docs/marine-weather-api | Medium | Coastal opt-in card: waves, swell, SST, currents. Must include "not for navigation" caution. |
| Open-Meteo Flood API | https://open-meteo.com/en/docs | Medium | River/flood risk card. Need UX caution and location relevance. |
| NOAA SWPC OVATION/Kp | https://www.swpc.noaa.gov/products/aurora-30-minute-forecast and https://services.swpc.noaa.gov/json/ | High | Aurora/Kp opt-in card is free, global-ish, and distinctive. |
| Meteo-France AROME/PIAF/Vigilance | Meteo-France Confluence docs | High for France | Strong source parity item; token/auth handling required. |
| GeoSphere Austria INCA | Breezy source matrix / Open-Meteo model list | Medium | Good Alpine nowcast source; needs dedicated implementation research. |
| FMI and KNMI | Breezy source matrix/changelog | Medium | Regional parity with proven comparator implementation. |
| SPC Conditional Intensity MapServer | Existing roadmap source | Medium | US severe-weather overlay and push candidate. |

## Model And Evaluation Opportunities

### Gemini Nano summaries

Local state:

- Standard flavor has Gemini Nano AI Core.
- `GeminiNanoSummaryEngineTest` now covers prompt shape and lifecycle fallback.
- Template engine remains fallback.

Plan:

- Add a golden prompt corpus with weather scenarios and expected safety/content
  constraints.
- Track supported-device behavior separately from JVM unit tests.
- Do not add cloud LLM calls by default.

### Forecast agreement evaluation

Opportunity:

- Use existing provider manager to fetch 2-3 enabled providers and compute
  agreement bands for temperature/precipitation/wind.

Metrics:

- Agreement within 24h temp high/low.
- Rain/no-rain disagreement.
- Precipitation timing disagreement.
- Source freshness and failure rate.

Risks:

- Quota multiplication for keyed providers.
- User confusion if provider disagreement is shown without explanation.

### Alert-evaluator regression corpus

Existing pure-function evaluators:

- `DrivingConditionEvaluator`
- `HealthAlertEvaluator`
- `ClothingSuggestionEvaluator`
- `PetSafetyEvaluator`
- `CustomAlertEvaluator`
- `NowcastAlertLogic`

Plan:

- Add fixture corpora for edge cases: freezing rain, hot pavement, smoke/AQI,
  high dewpoint heat, wind chill, fast pressure drops, null humidity.
- Consider mutation testing later for these pure engines.

## Integration Opportunities

| Integration | Evidence | Priority |
|---|---|---|
| ContentProvider for Tasker/Gadgetbridge/KWGT | Breezy data sharing/ContentProvider | High |
| Weather update broadcast | Breezy/Gadgetbridge pattern | High |
| WFF weather data provider path | Android WFF weather docs | High research |
| Android Auto route weather | Weather on the Way, MyRadar Android Auto | Later |
| Android TV module | WeatherYou | Later |
| Home Assistant | Existing roadmap | Later |
| Smartspacer target | Existing roadmap | Later |

## Data Governance Notes

- Keep `freenet` free of proprietary SDKs and trackers.
- New data source adapters must document licensing, attribution, rate limits,
  coverage, units, and cache behavior.
- API-key sources must remain optional.
- All adapters must normalize to metric and location-timezone aware values.
- Background fetches must justify cadence and battery behavior.

## Thin Areas And Why

There is no training pipeline or server-side ML dataset to review. The relevant
"model" work is weather model/API selection, on-device Gemini prompt behavior,
and evaluator regression data. A future ML-heavy direction would be a separate
product decision and should not displace the app's current privacy-first path.

