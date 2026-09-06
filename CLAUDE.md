# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo actually is

**Data Blaster** — `com.culberth.tools:data-blaster`, package `com.culberth.tools.datablaster`. A
JavaFX + Spring Boot desktop tool: an Office-style ribbon, switchable content views, modal dialogs,
persisted settings and light/dark theming. Spring is here for dependency injection and the bean
lifecycle only — the app serves nothing and binds no port.

Forked from JFXRibbon, a template written to be forked. The rename (PRD R27) is **done in full** —
including the two manual steps outside the build: the directory is `Projects/data-blaster` and the
remote is `culberth/data-blaster`. Nothing inherits the old name any more.

**Every mode is configurable; none of them does anything.** `Mode` is a first-class enum,
`ViewRegistry` is keyed by it, the selected mode persists, every mode's settings persist under their
own key namespace, the ribbon follows the selected mode, Preferences is a tab per scope with a
working port-to-tail editor, and each mode has a view that names it and shows its live configuration
(REST says plainly it is not implemented). **v1's feature scope is complete.** Mode *behaviour* is
out of scope for v1 by design — that boundary is the thing to push back with when scope creeps.

**The loopback HTTP layer has been removed** (PRD Q10, answered no). There is no `web` package, no
embedded Tomcat, and no `spring-boot-starter-webmvc` dependency. If you find references to
`StatusController`, `LoopbackHostFilter` or `AppState.Snapshot` anywhere, they are stale.

**`JFXRibbon` is gone; `ribbon` stays.** The app still has a ribbon, so `ribbon.css`, the `-jfx-*`
CSS tokens, `controller.ribbon`, `fxml/ribbon/`, and every `ribbon-*` style class are correct as they
are. `ribbon.css` is loaded by name and parsed by name in `ThemeContrastTest`, so a find-and-replace
on `ribbon` breaks theming at runtime rather than at compile time.

- [docs/architecture.md](docs/architecture.md) — how the **current** code works and why. Accurate as
  of the mode views. Keep it that way: PRD requirement R21 says architecture.md is updated in
  the same change as the code it describes, not as a follow-up.
- [docs/PRD.md](docs/PRD.md) — what is being **built next**: the four modes and their settings. v1
  makes the modes configurable and deliberately implements none of their behaviour. That boundary is
  the thing to push back with when scope creeps.

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

# Windows jpackage app-image builds (manual, not run in CI). The scripts are wrappers;
# the jpackage call is in pom.xml, under these two opt-in profiles.
scripts/build-windowed.ps1   # = mvn -Papp-image clean verify          -> dist/windowed/DataBlaster/DataBlaster.exe — no console
scripts/build-console.ps1    # = mvn -Papp-image-console clean verify  -> dist/console/DataBlaster/DataBlaster.exe — console attached, for diagnosing startup failures
```

Packaging is driven by `${jpackage.phase}`: the executions are declared unconditionally in
`build/plugins` so both variants share one argument list, but that property is `none` until an
`app-image` profile sets it to `verify`. A plain `mvn verify` therefore packages nothing. Do not
give those executions a literal phase — that is the only thing keeping them off the normal build.

Surefire's `runOrder` is pinned to `alphabetical` in `pom.xml` — do not remove this. The default
(`filesystem`) differs between Windows and Linux, which previously let a test-ordering/shared-state
leak pass locally and fail only on CI.

## Architecture (see docs/architecture.md for full detail)

**Layers, dependencies point inward toward `model`:**

```
bootstrap (Launcher, DataBlasterApplication, AppConfig, ViewLoader)
  -> controller (MainController, Preferences/About, the four mode views)
       -> ui (StageRegistry, ViewRegistry, RibbonGroupRegistry, ViewSwitcher,
              DialogService, LogScale, PortSpinner,
              LogFolderChooser, DataFileChooser)
     controller.ribbon      one controller per ribbon group
     controller.preferences one controller per Preferences tab
            -> model (AppState, Mode, Settings, TailNumber, IpAddress —
                      imports nothing from the app)
```

`model` never imports from `controller` or `ui`. No controller imports another controller.

**Key invariants enforced by the code (not just convention) — do not casually "clean up" these:**

- `AppState` is the single channel for shared state: global `currentMode`, `theme`, `blastPort`,
  `singleMessage`, `byteHijack`; Log's `playbackSpeedFactor`, `logFolder` and the port-to-tail
  mappings; Message's `messageType`; SOAP's `soapIp`, `soapMessageType`, data files and `soapTail`.
  There is no `contentOpacity` — it was a view control rather than a setting and was removed, not
  relocated, when the three global settings took its slot in the ribbon. Writes
  must happen on the FX thread (enforced by a guard that checks a `Thread` captured at startup, *not*
  `Platform.isFxApplicationThread()` — probing that would initialize the JavaFX toolkit from plain
  unit tests). Off-thread writers use `AppState.onFxThread(Runnable)`; there is deliberately **no**
  off-thread read path since the web layer went, and `AppStateTest` pins that absence.
- Both collections obey the same rules, and get none of them for free: `portTailMappings()` and
  `soapDataFiles()` hand out **unmodifiable** views (returning the live `ObservableList` reopens the
  hole the read-only accessors close), edits replace the whole set rather than mutating it, and the
  mutators run the thread guard. `SettingsService` subscribes to each with its own
  `ListChangeListener` — a `ChangeListener` on an `ObservableList` never fires here, so edits would
  persist nowhere. The mapping set rejects a duplicate; the data file list drops one, because a file
  picked twice from a chooser is a person using a chooser rather than an error.
- All property accessors return read-only types (`ReadOnlyDoubleProperty`, etc.) — there is
  intentionally no second, unguarded way to mutate state.
- `AppState` subscriptions from FXML controllers must use `WeakChangeListener` — controllers are
  prototype-scoped and come and go with the scene graph, but `AppState` itself is a long-lived
  singleton.
- Every FXML controller is `@Scope(SCOPE_PROTOTYPE)`. A singleton controller would stay bound to a
  stale, detached node tree after a reload.
- `Launcher` (not `DataBlasterApplication`) is the JAR's main class specifically so `java -jar` doesn't
  trip JavaFX's launcher checks.
- `DataBlasterApplication.init()` boots Spring and catches nothing. It used to retry without the web
  layer on a port conflict; with no server there is nothing a retry could fix. The reasoning is kept
  in architecture.md §3 because SOAP mode will inherit the same problem.
- Settings persistence (`Settings` / `SettingsStore` / `SettingsService`) restores `AppState` values
  *before* the ribbon controls subscribe to them (inside one `bind()` call, so the order can't be
  gotten wrong), reads tolerantly per-field (a bad value falls back to default and logs; a missing
  file is a silent first run), and writes off the FX thread, coalesced, with a `@PreDestroy` flush.
  Keys are namespaced by mode (`log.*`, `message.*`, `soap.*`); **unprefixed is the global
  namespace**, not an absence of one — `mode`, `theme`, `blastPort`, `singleMessage`, `byteHijack`.
  Mappings are stored one key per port (`log.mapping.<port>=<tail>`) so the file format enforces port
  uniqueness; tail uniqueness is checked in code, and each entry is read tolerantly on its own. SOAP
  data files use `soap.dataFile.<index>=<path>`, where the index is only a sort key — gaps are
  harmless and a repeated path is dropped. Booleans are parsed explicitly: `Boolean.parseBoolean`
  reads anything that is not `"true"` as `false`, which would make a typo silently mean "off".
- Dark theme is implemented as token overrides in `ribbon.css` under `.root.theme-dark`, applied via
  `ThemeService` using an **invalidation** listener (not a `ChangeListener` — a `ChangeListener` here
  previously fired once and then silently stopped) and tracking scene roots weakly. `ThemeContrastTest`
  parses the stylesheet and asserts contrast ratios in both themes — don't hardcode hex colors outside
  the token blocks.

**A controller that only observes must pin itself to its node tree.** `AppState` subscriptions are
weak, and most controllers are kept alive by accident (an `onAction` handler, or a listener lambda on
one of their own controls, is a strong node→controller reference). `ContextualGroupController` has
neither, so it parks itself in `groupHost.getProperties()`; without that the weak listener clears at
the next GC and the ribbon silently stops swapping. Same applies to any future observe-only
controller.

**Preferences is a `TabPane`** (General / Log / Message / SOAP; REST has no settings so it has no
tab). Each tab is its own FXML under `fxml/preferences/` with its own prototype controller in
`controller.preferences` — the shell owns only the Close button. **Reset is per tab**, and the Log
and SOAP tabs confirm first *only when their list is non-empty*, via `DialogService.confirm` — an
inline `Alert` gets neither owner nor stylesheet and renders light under the dark theme.

**The ribbon's third slot is the `Global` group**, not `Appearance`: Blast Port, Single Message,
Byte Hijack, all also on the General tab and neither surface holding a copy. Port spinners are
configured through `ui.PortSpinner` rather than per controller — bounds from `PortTailMapping`'s
range, a converter that survives nonsense, and an `increment(0)` commit on focus loss.

**SOAP mode sends; it does not listen.** Its settings are an IPv4 address (`IpAddress`, dotted quad
with parsed octets and no leading zeros — hostnames and IPv6 are deliberately out), its own
`SoapMessageType` enum, a list of data files, and an optional tail number. The tail rule lives in
`TailNumber` and `PortTailMapping` delegates to it, so "the same constraints as a mapping's tail"
has one implementation; SOAP's differs only in being allowed to be absent.

**The mapping editor** binds to `AppState.portTailMappings()` directly (the unmodifiable view, so
column sorting is off — a sort would reorder it in place). Both columns are `String` columns
*including Port*: an `Integer` column's converter throws from inside the cell commit, where the
controller cannot turn it into a message. Every add and edit funnels through one method that catches
`IllegalArgumentException` and shows its message verbatim beside the controls.

**Tests must load Preferences tabs directly** (`/fxml/preferences/log-tab.fxml`), not look them up
through `preferences.fxml`: a `TabPane` skin does not build a tab's content until it is shown, so a
lookup on the unshown dialog finds only whichever tab happens to be selected.

**Adding a ribbon group:** new FXML under `fxml/ribbon/` + new controller in
`controller.ribbon` + one `<fx:include>` in `main.fxml`. No edits to `MainController` or other groups
needed. A group's `initialize()` runs before the shell's (FXMLLoader builds depth-first) — a group may
*subscribe* to `AppState` there but must not *publish*, since the shell hasn't run yet and will
overwrite it.

**The ribbon is contextual.** Three fixed slots — Mode, the contextual slot, Global — and the
middle one swaps with `currentMode` via `ContextualGroupController` + `RibbonGroupRegistry`. Only
`LOG` and `MESSAGE` have a group; SOAP and REST leave the slot empty **and un-managed**, so it
reserves no width. The slot swaps *itself* rather than being swapped by `MainController` — that is
what keeps "adding a group needs no shell edit" true. `RibbonGroupRegistry` is separate from
`ViewRegistry` because a missing content view is a defect (throws) while a missing ribbon group is
normal (empty `Optional`).

**Playback Speed has two controls and neither owns the value:** a log-scaled slider in the Log
ribbon group (`LogScale` converts 0–1 track position <-> 0.1–10.0 multiplier, putting 1.0 mid-track)
and a `Spinner<Double>` in Preferences. `LogScale.valueAt(position, decimals)` rounds, and the
rounded number is what gets stored — don't move that rounding into the display format. The slider
also *follows* `AppState` rather than reading it once, which needs the re-entrancy guard in
`LogGroupController`.

**Adding a setting:** a field on `AppState` (read-only accessor + guarded mutator), a component on
the matching nested record in `Settings` — or on `Settings` itself if it is global — a key with a
tolerant read in `SettingsStore`, and a restore + subscribe line in `SettingsService.bind()` —
restore before subscribe, or every launch rewrites the file.

## Testing

342 tests, all headless by default (`HeadlessToolkit` / Monocle software Glass platform). Only tests
that need a real scene graph (`FxmlSmokeTest`, `SettingsRestoreOrderTest`, `PreferencesSurfaceTest`,
`MappingTableTest`, `ContextualRibbonTest`, `ModeViewTest`, `ThemeSwitchingTest`) initialize the
JavaFX toolkit — everything else, especially `AppStateTest`, must
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
