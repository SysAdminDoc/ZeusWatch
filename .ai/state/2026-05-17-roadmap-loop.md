# Roadmap Continuation State - 2026-05-17

## Batch: N-1 Local Localization Gate

Implemented local N-1 work:

- Added `tools/check_localization.py`.
- Added CI step `Check hardcoded user-facing strings`.
- Moved radar, report FAB, current weather notification, crash dialog, share
  text/image, Wear tile, driving alert, and health alert copy onto string
  resources.
- Converted `DrivingAlert` and `HealthAlert` models from English message text
  to resource-backed message metadata so Today cards and health notifications
  localize at render/delivery time.
- Completed app Spanish string coverage: 925 default strings and 925 Spanish
  strings.
- Fixed the `forecast_precip_rain_next_hours` placeholder mismatch caught by
  Android lint.
- Added `docs/TRANSLATION.md` with local checks and Weblate component setup
  notes.

## Verification

- `python tools/check_localization.py`
- `git diff --check`
- `.\gradlew.bat :app:testStandardDebugUnitTest --tests com.sysadmindoc.nimbus.util.DrivingConditionEvaluatorTest --tests com.sysadmindoc.nimbus.util.HealthAlertEvaluatorTest --console=plain`
- `.\gradlew.bat :wear:testDebugUnitTest --console=plain`
- `.\gradlew.bat lintStandardDebug --console=plain`

## Remaining N-1 Boundary

External service/human work remains:

- Connect the repository to a Weblate project.
- Merge at least three community-maintained locales.

This requires Weblate service access and translators; it is the current blocker
before N-1 can be marked fully closed.
