# ZeusWatch — Completed Milestones

| Version | Milestone | Date |
|---------|-----------|------|
| v1.0.0 | Initial release — share, widgets, a11y, CI/CD, 108 tests | — |
| v1.1.0 | CAPE, 5-day AQI, interactive graph, forecast strip widget | — |
| v1.2.0 | Lightning, Gemini Nano, live wallpaper, community reports, multi-source, international alerts, tablet layout | — |
| v1.3.x | 21 dynamic cards, weather-adaptive theme, NLG summaries, nowcasting, compare screen, driving/health alerts, yesterday comparison, drag-reorder locations, bug fixes, ProGuard hardening | — |
| v1.4.0 | Security hardening, offline detection, reduced motion, ImmutableList perf, OkHttp retry, parallel sub-fetches, 74 new tests | — |
| v1.5.0 | 4 new cards (25 total), single-LazyColumn perf, pull-to-refresh, card reorder, 22 crash fixes, Crashlytics removed | — |
| v1.6.x | Cloud cover + visibility cards (27 total), tabbed trend system, enhanced ephemeris arc, segmented AQI gauge, animated temp counter, gravity parallax, 4 QA audit passes | — |
| v1.7.0 | "On This Day" historical card (28 total), Open-Meteo Archive API, permanent cache | — |
| v1.8.0 | Precipitation nowcast notifications, NowcastAlertWorker, transition classifier | — |
| v1.9.0 | Custom alert rules, threshold CRUD, CustomAlertWorker, adaptive icon | — |
| v1.10.0 | Wear OS overhaul — multi-screen Compose, GPS, tile ANR fix, complication, signing | — |
| v1.11.0 | Weather source adapters — OWM, Pirate Weather, Bright Sky (DWD) fully wired | — |
| v1.12.0 | Health alert system — real barometric pressure, migraine/respiratory/arthritis triggers, HealthAlertWorker | — |
| v1.13.0 | Wear OS DataLayer sync — phone-to-watch push, SyncedWeatherStore, tile/complication prefer synced data | — |
| v1.14.0 | Wear OS alerts/daily/AQI — AlertsScreen, DailyScreen, alert banner, AQI chip, background sync via WidgetRefreshWorker | — |
| v1.14.1 | Engineering audit round 1 — 10 bugs across 9 files (visibility unit mismatch, Set ordering, CAS lambda side effects, BrightSky UTC, Mercator clamp, notif ID collisions, null humidity, recomposition storm) | 2026-04-17 |
| v1.14.2 | Engineering audit round 2 — 4 bugs across 2 files (WeatherSummaryEngine timezone greetings, wind band, precip labels, OnThisDay Feb 29 crash) | 2026-04-17 |
| v1.15.0 | UI modernization — shared chrome layer (`NimbusChrome.kt` + `WearChrome.kt` + `WidgetTheme.kt`) collapses ~1400 lines across 6 screens + 11 components + 4 wear screens + 4 widgets | 2026-04-22 |
| v1.16.0 | Resilience + audit-closure pass — ACRA crash reporting (GMS-free, both flavors), RateLimitInterceptor (GCRA + Retry-After + 5xx retry), API-key debug-log redaction, Wear sync freshness indicator + refresh hint, Gemini Nano R8 keep rule, Room schema export, showYesterdayComparison wiring, ambient notification grouping + custom-rule deep link, CI wear module + release variants + Detekt, Dependabot | 2026-04-24 |
