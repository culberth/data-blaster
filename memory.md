# Memory — Data Blaster

Project-specific long-term memory. Claude maintains this file: read it at session start, update it
without being asked when status changes, a decision is made, or work lands.

## Snapshot
- **Product:** Data Blaster — a mode-based JavaFX/Spring Boot desktop tool
- **Coordinates:** `com.culberth.tools:data-blaster`, package `com.culberth.tools.datablaster`
- **Local path:** `P:\ClaudeCowork\Projects\data-blaster`
- **Remote:** `github.com/culberth/data-blaster`
- **Stack:** Java 21 (built on JDK 26), JavaFX 21.0.2, Spring Boot 4.1.1 (no web layer), Maven
- **Forked from:** JFXRibbon / `culberth/javafx-ribbon-view-switcher` (GPL-3.0), a template written
  to be forked. This is that fork.

## Current state (as of 2026-08-29)
- Branch `rename-to-data-blaster`, seven commits' worth of work ahead of `main` — which is still at
  `first commit`, so effectively the whole project lives on this branch. Merging it, and whether the
  branch name still fits what it now contains, are open.

  1. Baseline import of the inherited codebase (110 tests, 0 failures)
  2. The rename from JFXRibbon to Data Blaster
  3. The mode model and mode-scoped settings — 209 tests
  4. Removal of the loopback HTTP layer — 181 tests
  5. The contextual ribbon — 215 tests
  6. The tabbed Preferences rebuild — 238 tests
  7. The mode views (uncommitted at time of writing) — **245 tests, 0 failures**
- Version reset to **1.0.0-SNAPSHOT**. JFXRibbon's `2.0.0-SNAPSHOT` numbered its Spring Boot 4
  migration and means nothing for a renamed artifact that has never shipped.
- **The modes are real; their views and their editors are not.** `Mode` is a first-class enum,
  `ViewRegistry` is keyed by it, the selected mode persists, and every mode's settings persist under
  their own key namespace. The toggles read Log / Message / SOAP / REST.
- **There is no HTTP layer.** No `web` package, no embedded Tomcat, no bound port, no
  `spring-boot-starter-webmvc`. `AppState.Snapshot` went with it — its only reader was the HTTP
  layer. Spring is here for DI and the bean lifecycle only.
- **The ribbon follows the mode.** Three fixed slots (Mode, contextual, Appearance); the middle one
  swaps with `currentMode`. Log shows a log-scaled Playback Speed slider and the log folder; Message
  shows its type; SOAP and REST show nothing and the slot un-manages itself. The Tools group was
  deleted — the log folder was its only content and it belongs to Log mode.
- **Preferences is a TabPane** (General / Log / Message / SOAP; no REST tab, it has no settings).
  Per-tab Reset; the Log tab confirms first, but only when the mapping table is non-empty. The
  port-to-tail editor rejects bad entries at entry with the reason shown beside the controls.
- **Each mode has a view** that names it and shows its live configuration read-only; REST says
  plainly it is not implemented. The views were `view1`–`view4` and also carried an inline
  `-fx-font-size` literal the theme could not reach; both are fixed.
- **v1's feature scope is complete.** Every acceptance criterion passes except A1's Linux half,
  which is CI's to confirm. The remaining known task is the jpackage `--add-modules` trim, which
  needs a real packaged run.

## What is being built
See `docs/PRD.md` — Data Blaster v1. Four modes (Log, Message, SOAP, REST), each with its own
persisted settings. **v1 makes the modes configurable and implements none of their behaviour**; that
boundary is deliberate and is the thing to push back with when scope creeps.

Settled during the PRD interview: Playback Speed Factor is a multiplier (0.1–10.0, default 1.0), tail
numbers are exactly six alphanumeric characters, SOAP defaults to port 8081, mappings are stored one
key per port so the file format enforces port uniqueness.

**Every open question is now closed (all answered 2026-08-29):** mappings are global (Q5); REST ships
as a visible placeholder toggle (Q6); the loopback HTTP layer does **not** survive (Q10); the
contextual ribbon is **in** (Q7), which promoted R16 to *Must* and resolved D11's conditional half in
favour of a log-scaled ribbon slider.

## Standing constraints
- Writes to `AppState` happen on the FX thread; off-thread writers use `onFxThread(Runnable)`. There
  is deliberately no off-thread read path any more. Property accessors are read-only and the mapping
  collection is handed out unmodifiable — there must be no second, unguarded way in.
- FXML controllers are prototype-scoped; their subscriptions to `AppState` are weak. **A controller
  that only observes — no `onAction`, no listener on its own controls — has nothing referring to it
  and must pin itself to its node tree** (see `ContextualGroupController`), or the weak listener
  clears at the next GC and the feature silently stops.
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
- 2026-08-29 — **Decision: the Sim Factor slider left the ribbon rather than being retargeted.**
  Playback Speed Factor is a Log-mode setting, not an appearance one, and as a 0.1–10.0 multiplier a
  linear slider strands `1.0` at 9% of its travel (PRD R-2). It is a `Spinner<Double>` in
  Preferences (D11); the Appearance group is opacity only. `SettingsRestoreOrderTest` lost its
  subject to this and now uses the Preferences spinner and the Mode ribbon group instead.
- 2026-08-29 — **Decision (Q10): removed the loopback HTTP layer entirely.** It was inherited from
  the template, where it demonstrated a Spring Boot context behind a JavaFX app; nothing consumed it,
  and keeping it would have meant a second bound port alongside the one SOAP mode will bind, plus a
  DNS-rebinding filter guarding an endpoint nobody asked for. Removing it took `web/`, two test
  classes, the port-conflict fallback in `init()`, the webmvc dependency, the
  `web-application-type=none` override on five test classes, and `AppState.Snapshot` — whose only
  reader it was. 209 → 181 tests. The jpackage `--add-modules` list still carries five Tomcat-era
  entries: trimming them needs a real packaged run, since a missing module fails at launch, not build.
- 2026-08-29 — **Decision (Q7): the contextual ribbon is in**, so R16 is promoted to *Must* and
  built. `ContextualGroupController` + `RibbonGroupRegistry` swap a ribbon slot with `currentMode`;
  the slot swaps itself rather than being swapped by `MainController`, which keeps "adding a group
  needs no shell edit" true. **Only Log and Message get a group** — SOAP's port is set once and REST
  has no behaviour, so their slot is empty *and un-managed* (a visible-but-empty slot would leave a
  gap with doubled spacing). The Tools group was deleted and the log folder moved into the Log group.
  181 → 215 tests.
- 2026-08-29 — **Decision: Playback Speed Factor now has two controls.** Q7 satisfied D11's condition,
  so the ribbon gets a log-scaled slider (`LogScale`: 0–1 track position <-> 0.1–10.0 multiplier,
  1.0 dead centre because it is the geometric mean of the bounds) alongside the Preferences spinner.
  Two things to preserve: the *rounded* value is what gets stored, so that rounding must stay in
  `LogScale` and not move into the display format; and the slider follows `AppState` rather than
  reading it once, which is why `LogGroupController` needs its re-entrancy guard.
- 2026-08-29 — **Built the tabbed Preferences rebuild** (PRD R15, R17–R19): four tab FXMLs under
  `fxml/preferences/` with controllers in `controller.preferences`, the port-to-tail `TableView`
  with add/edit/remove and at-entry rejection, per-tab Reset, and the non-blocking SOAP-port
  collision notice. A3, A4 and A7 now pass. 215 → 238 tests.
- 2026-08-29 — **Two things found while building it.** The confirmation `Alert` was inline and got
  neither owner nor stylesheet, so it would have rendered light under the dark theme; it moved to
  `DialogService.confirm`, where `showModal`/`showError` already handle that. And tests must load
  Preferences tabs **directly** — a `TabPane` skin does not build a tab's content until it is shown,
  so a lookup through `preferences.fxml` silently finds only the selected tab. `PreferencesSurfaceTest`
  failed exactly that way before being rewritten.
- 2026-08-29 — **Found a real defect via an order-dependent test failure.**
  `ContextualGroupController` observes `AppState` weakly and had no `onAction` and no listener on its
  own controls, so nothing referenced it: the weak listener cleared at the next collection and the
  contextual ribbon stopped swapping. It only showed up under the allocation pressure of a full
  `mvn verify`, not `mvn test`. Fixed by pinning the controller to its root node's property map, with
  a structural assertion rather than a `System.gc()` guess. Worth remembering as a class of bug, not
  a one-off.
- 2026-08-29 — **Built the four mode views (PRD R14), completing v1's feature scope.**
  `view1`–`view4` became `log-view`/`message-view`/`soap-view`/`rest-view` with matching controllers,
  and `ViewRegistry` no longer maps `LOG` to `view1`. Each shows that mode's live configuration
  read-only — bound, not assigned, so the ribbon, Preferences and the view are three independent
  readers of one `AppState`. REST shows a note instead, because it is not *designed*, not merely
  unimplemented. Also removed the inline `style="-fx-font-size: 22px;"` each view carried: a literal
  the theme tokens could never reach, so headings kept light-theme colour under the dark theme.
  238 → 245 tests.
- 2026-08-29 — R27 is **fully** done, manual steps included: the directory is
  `Projects/data-blaster` and the remote is `culberth/data-blaster`. Three files still claimed
  otherwise and have been corrected.
