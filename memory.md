# Memory — javafx-ribbon-view-switcher

Project-specific long-term memory. Claude maintains this file: read it at session start, update it
without being asked when status changes, a decision is made, or work lands. Global context lives in
`AboutMe/memory.md` at the workspace root.

## Snapshot
- **Repo:** https://github.com/culberth/javafx-ribbon-view-switcher (GPL-3.0)
- **Local path:** `P:\ClaudeCowork\Projects\javafx-ribbon-view-switcher`
- **Stack:** Java 21 (developed against JDK 26), JavaFX 21.0.2, Spring Boot 4.1.1, embedded Tomcat, Maven
- **Purpose:** Bo's learning/reference project for Spring Boot and JavaFX; also a forkable template.
- **Cloned into the workspace:** 2026-08-29

## Current state (as of 2026-08-29)
- `main` at `d6c23cb` "Plan phase 3" (2026-08-28). Working tree clean.
- Version **2.0.0-SNAPSHOT**, unreleased. 2.0.0 is a major bump because the Spring Boot 3.5.16 →
  4.1.1 migration is breaking for forks (`spring-boot-starter-web` → `spring-boot-starter-webmvc`).
- Phase 2 is complete: dark theme, Preferences as the canonical settings surface, persisted settings,
  Spring Boot 4.1.1 migration. The peer review's 46 findings are dispositioned.
- **Phase 3 is proposed, not started.**

## Phase 3 backlog (from docs/phase-3-plan.md)
Theme of the phase: make staleness *reported* rather than noticed.

| Item | What | Size |
|---|---|---|
| P3.1 | Spike the JavaFX patch bump (pinned at 21.0.2; 21.0.12 is current in the same LTS line) | Small, one real unknown |
| P3.2 | Ship 2.0.0 | Small |
| P3.3 | Report dependency staleness automatically | Small |
| P3.4 | Build and launch the app-image in CI | Medium |
| P3.5 | Reconcile the PRD with what the project actually does | Small, mostly writing |
| P3.6 | Decide on behavioral UI tests | A decision, then medium or nothing |

P3.1–P3.3 are the core. P3.6 could reasonably be answered "no".

## Standing constraints
- Headless tests only; `HeadlessToolkit` is for scene-graph tests exclusively.
- `EXPECTED_FXML_COUNT` / `EXPECTED_CONTROLLER_COUNT` must track new FXML files and controllers.
- HTTP layer stays loopback-bound; `LoopbackHostFilter` guards every path against DNS rebinding.
- `ribbon.css` uses design tokens, never hex literals — `ThemeContrastTest` parses the stylesheet.
- The four near-identical views and the icon/content mismatch are documented non-goals, not debt.
- `jpackage` is a manual Windows step, deliberately not in CI.

## Log
- 2026-08-29 — Cloned into the workspace. Scaffolded `CLAUDE.md` (thin, deferring to the repo's
  existing `AGENTS.md`) and this file. No code changes yet.
