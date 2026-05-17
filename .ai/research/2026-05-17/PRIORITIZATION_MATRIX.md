# Prioritization Matrix

Date: 2026-05-17

Scoring: 1 low, 5 high. Priority favors impact, project fit, source confidence,
and dependency readiness. Risk is inverse: 5 means high risk.

## Now Candidates

| ID | Candidate | Impact | Fit | Source confidence | Effort | Risk | Priority | Evidence |
|---|---|---:|---:|---:|---:|---:|---|---|
| P1 | Finish localization extraction and Weblate pipeline | 5 | 5 | 5 | 4 | 2 | NOW | Local 846 base strings / 783 Spanish; F-Droid Weblate docs. |
| P2 | Dependency runway pass: Room/Work/Wear/Glance/MapLibre/Kotlin audit ladder | 5 | 5 | 5 | 4 | 3 | NOW | Maven metadata, AndroidX release notes, OSV clear. |
| P3 | Wear OS complication and WFF weather-data interoperability spike | 5 | 5 | 5 | 4 | 3 | NOW | Local Wear module; Android WFF weather docs. |
| P4 | Complete Wear service test coverage | 4 | 5 | 5 | 3 | 2 | NOW | Local wear tests only cover repo/store helpers. |
| P5 | Populate or explicitly defer TLS pins with real captured certs | 4 | 5 | 4 | 2 | 4 | NOW | Local `ApiCertificatePins.hostPins` empty; release docs. |
| P6 | Move retry from OkHttp interceptor to coroutine helper | 4 | 5 | 5 | 3 | 2 | NOW | Local `Thread.sleep`; existing roadmap item. |
| P7 | Baseline Profiles + Macrobenchmark startup/radar/settings gate | 4 | 5 | 5 | 4 | 2 | NOW | Android docs show startup/runtime value. |
| P8 | Lightning proximity notification | 4 | 5 | 4 | 3 | 3 | NOW/NEXT | Local Blitzortung; CARROT premium comparator. |

## Next Candidates

| ID | Candidate | Impact | Fit | Source confidence | Effort | Risk | Priority | Evidence |
|---|---|---:|---:|---:|---:|---:|---|---|
| P9 | ContentProvider + weather-update broadcast | 4 | 5 | 5 | 3 | 2 | NEXT | Breezy ContentProvider and Gadgetbridge data sharing. |
| P10 | Open-Meteo ACCESS-G/BOM surfacing for Australia | 4 | 5 | 5 | 2 | 1 | NEXT | Open-Meteo ACCESS-G/BOM model docs. |
| P11 | Meteo-France adapter | 4 | 4 | 4 | 4 | 3 | NEXT | Meteo-France API docs; Breezy source matrix. |
| P12 | FMI + KNMI adapter bundle | 3 | 4 | 4 | 3 | 2 | NEXT | Breezy source matrix/changelog. |
| P13 | Open-Meteo Marine opt-in card | 3 | 4 | 5 | 3 | 2 | NEXT | Open-Meteo Marine docs. |
| P14 | NOAA aurora/Kp card | 3 | 4 | 5 | 2 | 2 | NEXT | NOAA SWPC JSON and product docs. |
| P15 | Custom alert rule expansion | 4 | 5 | 4 | 3 | 2 | NEXT | Local evaluator; CARROT custom alert comparator. |
| P16 | Multi-provider agreement card | 4 | 5 | 4 | 4 | 4 | NEXT | Windy compare-mode idea; local multi-source manager. |

## Later Candidates

| ID | Candidate | Impact | Fit | Source confidence | Effort | Risk | Priority | Evidence |
|---|---|---:|---:|---:|---:|---:|---|---|
| P17 | Android Auto route/driving weather module | 4 | 3 | 3 | 5 | 4 | LATER | Weather on the Way, MyRadar Android Auto coverage. |
| P18 | Android TV module | 3 | 3 | 4 | 4 | 3 | LATER | WeatherYou TV support. |
| P19 | Direct BOM undocumented API adapter | 4 | 3 | 2 | 4 | 5 | LATER/RESEARCH | BOM feeds page; legal/API stability uncertain. |
| P20 | Vico chart migration | 3 | 3 | 4 | 4 | 3 | LATER | Existing roadmap; needs visual QA. |
| P21 | Alternate numerals/calendars | 3 | 4 | 4 | 3 | 2 | LATER | Breezy localization/accessibility references. |

## Recommended Execution Order

1. Close localization extraction because it is already in progress and causes
   merge churn every release.
2. Do the dependency runway as small compatibility commits, starting with
   low-risk AndroidX patch/minor upgrades and explicit build/test evidence.
3. Expand Wear tests and complication/WFF support while the Wear OS ecosystem is
   actively shifting away from older weather app assumptions.
4. Finish reliability work: TLS pin decision and coroutine retry.
5. Add one high-value free premium feature: lightning proximity or custom alert
   expansion.
6. Add provider/data expansions only after the reliability/test runway is stable.

## Rejected For Current Planning

| Candidate | Reason |
|---|---|
| Cloud LLM summaries | Conflicts with privacy/no-backend posture; Gemini Nano/template already exists. |
| Mandatory API-key provider | Breaks no-required-key guardrail. |
| Direct undocumented BOM API as first AU solution | Legal/stability uncertainty; Open-Meteo ACCESS-G is safer. |
| Full custom Wear watch face | WFF policy changes make data provider/complication strategy safer. |
| Telemetry by default | F-Droid/privacy trust risk. Any usage metrics must be opt-in and debated first. |

