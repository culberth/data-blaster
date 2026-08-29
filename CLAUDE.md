# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo actually is

**Data Blaster** — a JavaFX + Spring Boot desktop tool, forked from JFXRibbon.

The code in `src/` is currently still JFXRibbon as inherited: a desktop application shell
(Office-style ribbon, switchable content views, modal dialogs) with a Spring Boot context behind it
and a small loopback-only HTTP companion API. The directory name `tool-boilerplate` and the package
`com.example.jfxribbon` are inherited and on their way out — the rename to Data Blaster
(groupId `com.culberth.tools`, package `com.culberth.tools.datablaster`) is PRD requirement R27 and
lands as its own commit before any feature work.

**When doing the rename: `JFXRibbon` goes, `ribbon` stays.** The app still has a ribbon, so
`ribbon.css`, the `-jfx-*` CSS tokens, `controller.ribbon`, `fxml/ribbon/`, and every `ribbon-*`
style class keep their names. `ribbon.css` is loaded by name and parsed by name in
`ThemeContrastTest`, so a careless find-and-replace on `ribbon` breaks theming at runtime rather
than at compile time. See PRD §4.4.

- [docs/architecture.md](docs/architecture.md) — how the **current** code works and why. Accurate as
  of `9739cb3`. Keep it that way: PRD requirement R21 says architecture.md is updated in the same
  change as the code it describes, not as a follow-up.
- [docs/PRD.md](docs/PRD.md) — what is being **built next**: renaming the fork to Data Blaster, then
  replacing the four placeholder views with four real modes (Log, Message, SOAP, REST) and giving
  each one its own persisted settings. v1 makes the modes configurable and deliberately implements
  none of their behaviour.

An earlier PRD in this repo described an unrelated Maven-archetype generator. That idea was
abandoned, not built — if you find references to archetypes, Velocity templating, or
`archetype-metadata.xml` anywhere, they are stale.

**Read [docs/architecture.md](docs/architecture.md) before making non-trivial changes.** It documents
not just the structure but *why* each constraint exists — most of them trace back to specific defects
found in a peer review, and undoing the arrangement reintroduces the original bug. This file only
summarizes; treat architecture.md as the source of truth on rationale.

## Commands

```bash
# build (jar + Spring Boot fat jar)
mvn clean package

# run the full test suite (headless, no display needed)
mvn test

# run a single test class
mvn test -Dtest=AppStateTest

# run a single test method
mvn test -Dtest=AppStateTest#methodName

# run the app in place
mvn spring-boot:run
# or
mvn exec:java

# Windows jpackage app-image builds (manual, not run in CI)
scripts/build-windowed.ps1   # dist/windowed/JFXRibbon/JFXRibbon.exe — no console
scripts/build-console.ps1    # dist/console/JFXRibbon/JFXRibbon.exe — console attached, for diagnosing startup failures
```

`mvn clean install` installs to `~/.m2`; there's no separate archetype build step despite what
`docs/PRD.md` implies.

Surefire's `runOrder` is pinned to `alphabetical` in `pom.xml` — do not remove this. The default
(`filesystem`) differs between Windows and Linux, which previously let a test-ordering/shared-state
leak pass locally and fail only on CI.

## Architecture (see docs/architecture.md for full detail)

**Layers, dependencies point inward toward `model`:**

```
bootstrap (Launcher, JFXRibbonApplication, AppConfig, ViewLoader)
  -> controller (MainController, Preferences/About, View1-4)
       -> ui (StageRegistry, ViewRegistry, ViewSwitcher, DialogService)
            -> model (AppState — imports nothing from the app)
web (StatusController, LoopbackHostFilter) -> model, via Snapshot only
```

`model` never imports from `controller`, `ui`, or `web`. `web` never imports from `controller` or
`ui`. No controller imports another controller.

**Key invariants enforced by the code (not just convention) — do not casually "clean up" these:**

- `AppState` is the single channel for shared state (`simFactor`, `logFolder`, `theme`,
  `currentViewId`, `contentOpacity`). Writes must happen on the FX thread (enforced by a guard that
  checks a `Thread` captured at startup, *not* `Platform.isFxApplicationThread()` — probing that
  would initialize the JavaFX toolkit from plain unit tests). Off-thread readers (the web layer) must
  use `AppState.snapshot()`, an immutable record that is deliberately narrower than `AppState`.
- All property accessors return read-only types (`ReadOnlyDoubleProperty`, etc.) — there is
  intentionally no second, unguarded way to mutate state.
- `AppState` subscriptions from FXML controllers must use `WeakChangeListener` — controllers are
  prototype-scoped and come and go with the scene graph, but `AppState` itself is a long-lived
  singleton.
- Every FXML controller is `@Scope(SCOPE_PROTOTYPE)`. A singleton controller would stay bound to a
  stale, detached node tree after a reload.
- `Launcher` (not `JFXRibbonApplication`) is the JAR's main class specifically so `java -jar` doesn't
  trip JavaFX's launcher checks.
- `JFXRibbonApplication.init()` starts Spring/Tomcat before any window exists, and retries with
  `--spring.main.web-application-type=none` **only** on a port conflict (`PortInUseException` /
  `BindException`, checked via `NestedExceptionUtils.getMostSpecificCause`); anything else is
  rethrown. Any log line about the fallback must be emitted *after* the replacement context is up —
  Spring tears down Logback when a context fails, so anything logged in that window is silently lost.
- `LoopbackHostFilter` validates the `Host` header on **every** path (not a per-handler check) to
  block DNS-rebinding attacks against the loopback-bound HTTP API, and returns a bare 404 with no
  body on failure (a distinctive error/body would fingerprint the app).
- Settings persistence (`Settings` / `SettingsStore` / `SettingsService`) restores `AppState` values
  *before* the ribbon controls subscribe to them (inside one `bind()` call, so the order can't be
  gotten wrong), reads tolerantly per-field (a bad value falls back to default and logs; a missing
  file is a silent first run), and writes off the FX thread, coalesced, with a `@PreDestroy` flush.
  `theme` is deliberately excluded from `AppState.Snapshot` (that record is for the web layer) but is
  still persisted, read directly off `AppState` on the FX thread.
- Dark theme is implemented as token overrides in `ribbon.css` under `.root.theme-dark`, applied via
  `ThemeService` using an **invalidation** listener (not a `ChangeListener` — a `ChangeListener` here
  previously fired once and then silently stopped) and tracking scene roots weakly. `ThemeContrastTest`
  parses the stylesheet and asserts contrast ratios in both themes — don't hardcode hex colors outside
  the token blocks.

**Adding a ribbon group:** new FXML under `fxml/ribbon/` + new controller in
`controller.ribbon` + one `<fx:include>` in `main.fxml`. No edits to `MainController` or other groups
needed. A group's `initialize()` runs before the shell's (FXMLLoader builds depth-first) — a group may
*subscribe* to `AppState` there but must not *publish*, since the shell hasn't run yet and will
overwrite it.

**Adding an HTTP endpoint:** handlers run on Tomcat worker threads, never the FX thread. Read via
`AppState.snapshot()`; write via `AppState.onFxThread(Runnable)`. Never touch a JavaFX node/property
directly from `web`.

## Testing

110 tests, all headless by default (`HeadlessToolkit` / Monocle software Glass platform). Only tests
that need a real scene graph (`FxmlSmokeTest`, `SettingsRestoreOrderTest`, `PreferencesSurfaceTest`,
`ThemeSwitchingTest`) initialize the JavaFX toolkit — everything else, especially `AppStateTest`, must
stay toolkit-free so CI's headless runner keeps working. See the test table in
[docs/architecture.md §9](docs/architecture.md) for what each suite covers.

If you add a new FXML file or controller, update `EXPECTED_FXML_COUNT` / `EXPECTED_CONTROLLER_COUNT`
(referenced in `memory.md`) so the relevant smoke tests keep tracking reality.

## Project tracking

`memory.md` at the repo root is a live project log (current phase, backlog, standing constraints) —
check it for current status before starting substantial work. It still describes the upstream
project (`javafx-ribbon-view-switcher`, GPL-3.0) and its Phase 3 backlog, which is inherited context
rather than this fork's plan; `docs/PRD.md` is the plan. `memory.md` needs updating to reflect the
fork — that is PRD requirement R23.
