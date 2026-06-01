# ZeusWatch -- Research Report

This file is the current research synthesis. The detailed 2026-05-17 research
bundle remains under [.ai/research/2026-05-17/](.ai/research/2026-05-17/), and
active planning stays in [ROADMAP.md](ROADMAP.md).

## Product thesis

ZeusWatch is a free Android and Wear OS weather app that competes on privacy,
provider resilience, dense weather surfaces, native watch support, and
non-paywalled alert depth. The key product bet is that an open app can offer
the useful parts of commercial weather apps without ads, subscriptions, or
required API keys.

## Current conclusions

- Native Wear OS support remains the strongest open-source moat: watch app,
  tile, complications, and phone-to-watch sync are already shipped.
- Provider depth is still the largest parity gap against Breezy Weather, so
  the roadmap keeps regional source adapters and multi-provider agreement high.
- Health, driving, pet, nowcast, and custom alerts are differentiated enough to
  keep defending with tests and battery-aware scheduling.
- Localization, dependency runway, Detekt baseline reduction, and widget test
  enablement are the highest compounding engineering investments.

## Active risks

- The phone app is at v1.21.1 while the Wear module remains at v1.21.0; that
  divergence is intentional unless Play distribution requires aligned codes.
- The public roadmap and research bundle must be refreshed after each release
  because local agent notes are ignored and may lag tracked files.
- Optional keyed providers need pinned-key release hygiene, but freenet must
  continue to work without proprietary services.
- Direct reverse-engineered provider APIs remain risky unless their public terms
  are stable; prefer documented Open-Meteo proxy paths when available.

## Active planning rule

Use [ROADMAP.md](ROADMAP.md) for new work, [COMPLETED.md](COMPLETED.md) for
closed milestones, and this report for the research synthesis. Historical
pre-v1 plans remain in `docs/`, and the 2026-05-17 research bundle remains
under `.ai/research/2026-05-17/`.
