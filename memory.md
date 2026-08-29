# Memory — Data Blaster

Project-specific long-term memory. Claude maintains this file: read it at session start, update it
without being asked when status changes, a decision is made, or work lands.

## Snapshot
- **Product:** Data Blaster — a mode-based JavaFX/Spring Boot desktop tool
- **Coordinates:** `com.culberth.tools:data-blaster`, package `com.culberth.tools.datablaster`
- **Local path:** `P:\ClaudeCowork\Projects\tool-boilerplate` (directory name still inherited)
- **Stack:** Java 21 (built on JDK 26), JavaFX 21.0.2, Spring Boot 4.1.1 (no web layer), Maven
- **Forked from:** JFXRibbon / `culberth/javafx-ribbon-view-switcher` (GPL-3.0), a template written
  to be forked. This is that fork.

## Current state (as of 2026-08-29)
- Branch `rename-to-data-blaster`, three commits' worth of work ahead of `main`:
  1. Baseline import of the inherited codebase (110 tests, 0 failures)
  2. The rename from JFXRibbon to Data Blaster
  3. The mode model and mode-scoped settings — 209 tests
  4. Removal of the loopback HTTP layer (uncommitted at time of writing) — **181 tests, 0 failures**
- Version reset to **1.0.0-SNAPSHOT**. JFXRibbon's `2.0.0-SNAPSHOT` numbered its Spring Boot 4
  migration and means nothing for a renamed artifact that has never shipped.
- **The modes are real; their views and their editors are not.** `Mode` is a first-class enum,
  `ViewRegistry` is keyed by it, the selected mode persists, and every mode's settings persist under
  their own key namespace. The toggles read Log / Message / SOAP / REST.
- **There is no HTTP layer.** No `web` package, no embedded Tomcat, no bound port, no
  `spring-boot-starter-webmvc`. `AppState.Snapshot` went with it — its only reader was the HTTP
  layer. Spring is here for DI and the bean lifecycle only.
- **Still to do in v1:** the tabbed Preferences rebuild (General / Log / Message / SOAP), the
  port-to-tail `TableView`, the four mode views including an honest REST placeholder, and per-tab
  Reset. Until the tabs land, Message's type, SOAP's port and Log's mapping table persist correctly
  but have nowhere to be edited.

## What is being built
See `docs/PRD.md` — Data Blaster v1. Four modes (Log, Message, SOAP, REST), each with its own
persisted settings. **v1 makes the modes configurable and implements none of their behaviour**; that
boundary is deliberate and is the thing to push back with when scope creeps.

Settled during the PRD interview: Playback Speed Factor is a multiplier (0.1–10.0, default 1.0), tail
numbers are exactly six alphanumeric characters, SOAP defaults to port 8081, mappings are stored one
key per port so the file format enforces port uniqueness.

**Q5, Q6 and Q10 were answered 2026-08-29:** mappings are global; REST ships as a visible placeholder
toggle; the loopback HTTP layer does **not** survive. Only Q7 (contextual ribbon in or out of v1)
remains open, and it is not blocking.

## Standing constraints
- Writes to `AppState` happen on the FX thread; off-thread writers use `onFxThread(Runnable)`. There
  is deliberately no off-thread read path any more. Property accessors are read-only and the mapping
  collection is handed out unmodifiable — there must be no second, unguarded way in.
- FXML controllers are prototype-scoped; their subscriptions to `AppState` are weak.
- Headless tests only; `HeadlessToolkit` is for scene-graph tests exclusively.
- `EXPECTED_FXML_COUNT` / `EXPECTED_CONTROLLER_COUNT` must track new FXML files and controllers.
- No HTTP layer, no bound port. If SOAP mode adds a server, it starts from that mode's configured
  port — and the reasoning worth reusing (validate `Host` in a filter not per-handler, fail with a
  bare bodiless 404, narrow any start-up retry to the specific cause) is in architecture.md §3/§8.
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
- 2026-08-29 — Built the mode model and mode-scoped settings (PRD R1–R13, R24–R26). `Mode`,
  `MessageType` and `PortTailMapping` added; `Settings` became a record of per-mode records;
  `AppState` gained `currentMode`, `messageType`, `soapPort` and an observable mapping list;
  `SettingsStore` moved to namespaced keys with per-entry tolerant mapping reads; `SettingsService`
  grew a `ListChangeListener`. `ViewRegistry` is keyed by `Mode`. 110 → 209 tests, all green.
- 2026-08-29 — **Decision (Q10): removed the loopback HTTP layer entirely.** It was inherited from
  the template, where it demonstrated a Spring Boot context behind a JavaFX app; nothing consumed it,
  and keeping it would have meant a second bound port alongside the one SOAP mode will bind, plus a
  DNS-rebinding filter guarding an endpoint nobody asked for. Removing it took `web/`, two test
  classes, the port-conflict fallback in `init()`, the webmvc dependency, the
  `web-application-type=none` override on five test classes, and `AppState.Snapshot` — whose only
  reader it was. 209 → 181 tests. The jpackage `--add-modules` list still carries five Tomcat-era
  entries: trimming them needs a real packaged run, since a missing module fails at launch, not build.
- 2026-08-29 — **Decision: the Sim Factor slider left the ribbon rather than being retargeted.**
  Playback Speed Factor is a Log-mode setting, not an appearance one, and as a 0.1–10.0 multiplier a
  linear slider strands `1.0` at 9% of its travel (PRD R-2). It is a `Spinner<Double>` in
  Preferences (D11); the Appearance group is opacity only. `SettingsRestoreOrderTest` lost its
  subject to this and now uses the Preferences spinner and the Mode ribbon group instead.
