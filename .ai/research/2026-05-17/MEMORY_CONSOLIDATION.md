# Memory Consolidation

Date: 2026-05-17

## Files Inspected

Repo-local:

- `AGENTS.md`
- `CLAUDE.md`
- `ROADMAP.md`
- `ROADMAP-COMPLETED.md`
- `CHANGELOG.md`
- `CODEX_CHANGELOG.md`
- `.factory/state.yaml`
- `.factory/rubrics/iter1.yaml`
- `docs/research-archive.md`
- `docs/RELEASE.md`
- `README.md`
- `fastlane/metadata/android/en-US/*`

Shared memory/instructions:

- `C:/Users/--/.claude/CLAUDE.md`
- `C:/Users/--/CLAUDE.md`
- `C:/Users/--/.claude/projects/c--Users----repos/memory/MEMORY.md`
- `C:/Users/--/.claude/projects/c--Users----repos/memory/zeuswatch.md`
- `C:/Users/--/.claude/projects/c--Users----repos/memory/stack-android.md`

## Reconciled Truths

1. `AGENTS.md` is intentionally a pointer file. It does not duplicate project
   detail; it sends future agents to `CLAUDE.md` and `PROJECT_CONTEXT.md`.
   It is ignored by the user's global gitignore, so it is local workspace
   guidance unless force-added intentionally.
2. `CLAUDE.md` remains the detailed tool-specific working note. The new
   `PROJECT_CONTEXT.md` is the consolidated project-memory entrypoint.
   `CLAUDE.md` is ignored by repo `.gitignore`, so it remains local workspace
   guidance unless force-added intentionally.
3. Shared ZeusWatch memory reports v1.20.1 in its header, but its release notes
   mention v1.20.3. The live repo confirms v1.20.3.
4. `ROADMAP.md` was already refreshed on 2026-05-16 and is the active plan. This
   run updates it with a 2026-05-17 delta rather than replacing it.
5. `docs/research-archive.md` is historical pre-v1 feature research. Many items
   in it have already shipped. Use it as source history, not active truth.
6. `.factory/state.yaml` and `.factory/rubrics/iter1.yaml` describe the old
   v1.17.0 factory loop. They are useful historical evidence but stale for
   current planning.
7. `ROADMAP-COMPLETED.md` is the authoritative completed-milestone ledger.
8. `docs/RELEASE.md` is the current release procedure and should be kept in sync
   with signing and pinning behavior.

## Conflicts And Resolution

### Current version

- Shared memory index says ZeusWatch v1.17.0 in one list entry and v1.20.1 in a
  project memory note header.
- Live repo says v1.20.3 in `CLAUDE.md`, `README.md`, `app/build.gradle.kts`,
  `wear/build.gradle.kts`, and `ROADMAP.md`.
- Resolution: v1.20.3 is current.

### Compose BOM version

- README badge initially said Jetpack Compose 2024.12.
- `gradle/libs.versions.toml` pins `compose-bom = "2025.04.01"`.
- `CLAUDE.md` header initially said Compose BOM 2024.12.
- Resolution: the build file is current for compilation. README and CLAUDE stack
  text were corrected during this research changeset.

### Tests default

- Shared global user instructions say "No tests unless explicitly requested."
- The repo's existing roadmap, CI, recent commits, and this task's research
  mandate require verification and test evidence.
- Resolution: for this repo, tests are already part of the Definition of Done
  and roadmap process. Continue to use focused tests and CI verification.

### AI working files

- Shared global instructions prefer keeping tool-specific files local.
- The user explicitly required `.ai/research/<date>/` artifacts and a root
  `PROJECT_CONTEXT.md` for future sessions.
- Resolution: create the requested durable research artifacts.

## Extracted Durable Project Facts

- ZeusWatch is an Android weather app with a phone module and Wear module.
- It has standard and freenet flavors.
- The freenet flavor is a hard product constraint, not a side build.
- The strongest moat is native Wear OS plus privacy-friendly FOSS distribution.
- Provider expansion should follow adapter boundaries and avoid required keys.
- Accessibility semantics on Canvas-heavy cards are a recurring quality bar.
- The app has a history of timezone, cancellation, and provider-edge bugs found
  during adversarial audits. Future roadmap items should include regression tests.
- Release workflow ships three APKs and may produce unsigned CI artifacts if
  signing secrets are not configured.

## Stale Or Superseded Claims

- `docs/research-archive.md` says early versions had single-source forecasting,
  no native MapLibre radar, limited widgets, and future Wear OS. Those have
  shipped.
- `.factory/state.yaml` says v1.17.0 was the target; current state is v1.20.3.
- Older memory says v1.17.0 and v1.20.1; current state is v1.20.3.
- No unresolved Compose BOM conflict remains after the README/CLAUDE correction
  in this changeset.

## Canonical Memory Output

Created root `PROJECT_CONTEXT.md` as the short future-session entrypoint.

Recommended future maintenance:

- Update `PROJECT_CONTEXT.md` after major version bumps, architecture changes,
  or roadmap refreshes.
- Keep `CLAUDE.md` as the detailed tool-specific working note.
- Keep `ROADMAP.md` strategic and source-backed.
- Move completed roadmap rows to `ROADMAP-COMPLETED.md` instead of leaving closed
  items in Now.
