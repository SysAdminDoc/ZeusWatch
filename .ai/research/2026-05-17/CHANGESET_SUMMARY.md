# Changeset Summary

Date: 2026-05-17

## Files Created

- `PROJECT_CONTEXT.md` - canonical consolidated project memory for future sessions.
- `.ai/research/2026-05-17/STATE_OF_REPO.md` - local repository reconnaissance memo.
- `.ai/research/2026-05-17/MEMORY_CONSOLIDATION.md` - instruction and memory reconciliation.
- `.ai/research/2026-05-17/SOURCE_REGISTER.md` - local/external source index.
- `.ai/research/2026-05-17/RESEARCH_LOG.md` - searches, methods, saturation notes, self-audit.
- `.ai/research/2026-05-17/COMPETITOR_MATRIX.md` - direct and adjacent competitor comparison.
- `.ai/research/2026-05-17/FEATURE_BACKLOG.md` - raw harvested opportunities.
- `.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md` - scored/tiered candidate matrix.
- `.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md` - dependency freshness, OSV check, hardening ideas.
- `.ai/research/2026-05-17/DATASET_MODEL_INTEGRATION_REVIEW.md` - datasets, APIs, models, and integration paths.

## Files Modified

- `ROADMAP.md` - updated last refresh date, added a 2026-05-17 research delta,
  added dependency runway item, refreshed Glance/MapLibre dependency notes, and
  clarified the pinning boundary for keyless providers.
- `AGENTS.md` - ignored local instruction file; added a pointer to
  `PROJECT_CONTEXT.md` beside `CLAUDE.md`.
- `CLAUDE.md` - ignored local instruction file; added a canonical-context pointer
  and corrected the Compose BOM text to match `gradle/libs.versions.toml`.
- `README.md` - corrected Compose BOM badge/stack text to match
  `gradle/libs.versions.toml`.
- `.ai/research/2026-05-17/MEMORY_CONSOLIDATION.md` - reconciled the resolved
  Compose BOM conflict.

## Why This Change Exists

The repository already had a strong roadmap, but context was split across
`CLAUDE.md`, shared memory, an older research archive, `.factory` state, and the
roadmap itself. This change creates a durable research bundle for future sessions
and reconciles the current live repo state with external ecosystem changes.

## Verification

- `git diff --check` passed.
- Required artifact existence check passed.
- `.\gradlew.bat :app:testStandardDebugUnitTest --tests com.sysadmindoc.nimbus.widget.WidgetThemeTest --console=plain` passed.
- `.\gradlew.bat :wear:testDebugUnitTest --console=plain` passed.
- Commit and push from `main` were planned after verification, preserving the six
  pre-existing local commits.
