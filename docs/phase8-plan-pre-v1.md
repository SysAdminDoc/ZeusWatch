# Nimbus Weather — Phase 8: Final Polish + Release
## Recalibrated Sub-Phase Plan (v1.0.0)

### Already Complete (found in codebase):
- Version bump 1.0.0 (all 5 locations)
- Deep link handling (MainActivity + NimbusNavHost + manifest intent-filters)
- App shortcuts (shortcuts.xml + manifest meta-data + string resources)
- FileProvider (manifest + file_paths.xml)
- Share buttons (text + image dropdown in MainScreen toolbar)
- Accessibility semantics (all Canvas composables via AccessibilityHelper)
- Adaptive layouts (WindowSizeClass + AdaptiveLayoutInfo + FlowRow 2-column)
- AdaptiveLayout.kt (CompositionLocal provider)

---

### Phase 8.1: Release Packaging (NEW FILES ONLY)
**Scope:** GitHub-ready packaging, distribution prep. No existing code changes.

- [ ] `README.md` — Full rewrite for v1.0.0 with badges, feature table, architecture, build instructions, API attribution
- [ ] `LICENSE` — LGPL-3.0 full text
- [ ] `CHANGELOG.md` — Version history v0.1.0 through v1.0.0
- [ ] `.github/workflows/build.yml` — CI/CD: checkout, JDK 17, Gradle build, lint, test, APK artifact
- [ ] `fastlane/metadata/android/en-US/` — F-Droid listing: full_description, short_description, title, changelogs
- [ ] Update `PHASES.md` — Mark all phases complete

**Deliverables:** ~8 new files, 1 updated file

---

### Phase 8.2: Predictive Back + Performance [COMPLETE]
**Scope:** Back gesture animations, runtime optimizations.

- [ ] `PredictiveBackHandler` in RadarScreen, SettingsScreen, LocationsScreen (shrink+fade)
- [ ] `@Stable` annotations on data classes used in Compose (WeatherData, CurrentConditions, etc.)
- [ ] `derivedStateOf` for computed UI values in ViewModels
- [ ] `remember` audit on lambdas passed to composables
- [ ] Proguard rules review for serialization + reflection

**Deliverables:** 6-8 modified files

---

### Phase 8.3: Unit Tests [COMPLETE]
**Scope:** Test infrastructure + repository/viewmodel/utility coverage.

- [ ] Add test deps to `libs.versions.toml` + `build.gradle.kts` (mockk, turbine, coroutines-test, junit5)
- [ ] `WeatherFormatterTest` — Temperature, wind, pressure, UV, time formatting
- [ ] `AccessibilityHelperTest` — Content description generation
- [ ] `WeatherRepositoryTest` — API mapping, cache logic, error handling
- [ ] `MainViewModelTest` — State transitions, permission flow, cached fallback
- [ ] `AlertRepositoryTest` — NWS parse, non-US degradation
- [ ] `AirQualityRepositoryTest` — AQI level mapping, pollen thresholds

**Deliverables:** 2 modified build files, 6 new test files

---

### Phase 8.4: UI Tests + Final Polish [COMPLETE]
**Scope:** Compose instrumented tests, final cleanup pass.

- [ ] Add compose test deps to build.gradle.kts
- [ ] `MainScreenTest` — Renders weather data, shimmer on loading, error state
- [ ] `SettingsScreenTest` — Radio selection, toggle interaction
- [ ] `LocationsScreenTest` — Search input, result display
- [ ] Final lint pass + code cleanup

**Deliverables:** 1 modified build file, 3 new androidTest files
