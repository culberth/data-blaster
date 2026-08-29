# Memory — Data Blaster

Project-specific long-term memory. Claude maintains this file: read it at session start, update it
without being asked when status changes, a decision is made, or work lands.

## Snapshot
- **Product:** Data Blaster — a mode-based JavaFX/Spring Boot desktop tool
- **Coordinates:** `com.culberth.tools:data-blaster`, package `com.culberth.tools.datablaster`
- **Local path:** `P:\ClaudeCowork\Projects\tool-boilerplate` (directory name still inherited)
- **Stack:** Java 21 (built on JDK 26), JavaFX 21.0.2, Spring Boot 4.1.1, embedded Tomcat, Maven
- **Forked from:** JFXRibbon / `culberth/javafx-ribbon-view-switcher` (GPL-3.0), a template written
  to be forked. This is that fork.

## Current state (as of 2026-08-29)
- Branch `rename-to-data-blaster`, two commits ahead of `main`:
  1. Baseline import of the inherited codebase (110 tests, 0 failures)
  2. The rename from JFXRibbon to Data Blaster
- Version reset to **1.0.0-SNAPSHOT**. JFXRibbon's `2.0.0-SNAPSHOT` numbered its Spring Boot 4
  migration and means nothing for a renamed artifact that has never shipped.
- **The four modes do not exist yet.** The Mode toggles still select four placeholder views. The
  shell, settings persistence, theming and headless test harness all work.

## What is being built
See `docs/PRD.md` — Data Blaster v1. Four modes (Log, Message, SOAP, REST), each with its own
persisted settings. **v1 makes the modes configurable and implements none of their behaviour**; that
boundary is deliberate and is the thing to push back with when scope creeps.

Settled during the PRD interview: Playback Speed Factor is a multiplier (0.1–10.0, default 1.0), tail
numbers are exactly six alphanumeric characters, SOAP defaults to port 8081, mappings are stored one
key per port so the file format enforces port uniqueness.

Still open, none blocking: whether the inherited loopback HTTP layer should survive at all now that
SOAP mode brings its own server (Q10); mappings global vs. per-profile (Q5); whether REST ships as a
visible placeholder (Q6); contextual ribbon in or out of v1 (Q7).

## Standing constraints
- Writes to `AppState` happen on the FX thread; off-thread readers use `snapshot()`. Property
  accessors are read-only — there must be no second, unguarded way in.
- FXML controllers are prototype-scoped; their subscriptions to `AppState` are weak.
- Headless tests only; `HeadlessToolkit` is for scene-graph tests exclusively.
- `EXPECTED_FXML_COUNT` / `EXPECTED_CONTROLLER_COUNT` must track new FXML files and controllers.
- HTTP layer stays loopback-bound; `LoopbackHostFilter` guards every path against DNS rebinding.
- `ribbon.css` uses design tokens, never hex literals — `ThemeContrastTest` parses the stylesheet.
- **"JFXRibbon" is gone; "ribbon" stays.** The app still has a ribbon: `ribbon.css`, the `-jfx-*`
  tokens, `controller.ribbon`, `fxml/ribbon/` and the `ribbon-*` style classes are all correct.
- `jpackage` is a manual Windows step, deliberately not in CI.
- Spring Boot 4.1 is maintained to 31 July 2027. A fork intended to ship must plan to move again.

## Log
- 2026-08-29 — Rewrote `docs/PRD.md` from scratch. The previous PRD described a Maven archetype
  generator; **that idea was abandoned, not built.** References to archetypes, Velocity templating or
  `archetype-metadata.xml` anywhere are stale.
- 2026-08-29 — Renamed JFXRibbon → Data Blaster across code, scripts and docs, as its own commit
  before any feature work. 110 tests green before and after.
