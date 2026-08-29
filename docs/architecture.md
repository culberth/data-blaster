# Architecture

How Data Blaster is put together, and — more usefully — **why**, since several of the arrangements
here exist to prevent specific defects that a peer review found in earlier versions of this code.

| | |
|---|---|
| **Describes** | `rename-to-data-blaster`, after the rename commit |
| **Size** | ~1,180 lines of main Java, ~210 of test, 10 FXML files, one stylesheet |
| **Stack** | Java 21, JavaFX 21.0.2, Spring Boot 4.1.1 (servlet), Maven |
| **Origin** | Forked from JFXRibbon, which was written as a template. See [PRD.md](PRD.md) |

---

## 1. What this is

A desktop application shell: an Office-style ribbon, a switchable content area, two modal dialogs,
and a small loopback-only HTTP layer. It runs on the JavaFX Application Thread with a Spring
`ApplicationContext` behind it, so UI components are Spring beans and can be given dependencies by
constructor injection.

**This document describes the shell as inherited, not the tool being built on it.** The four views
behind the ribbon's Mode toggles are still placeholders; replacing them with the Log, Message, SOAP
and REST modes — and giving each its own persisted settings — is what [PRD.md](PRD.md) specifies and
has not been built yet. Where this document says the views are placeholders, that is still true and
is the thing about to change.

The shell existed to be forked, and this is the fork. That history shapes several decisions recorded
below: where duplication is tolerated, and where it is not.

---

## 2. Layers and dependency direction

```
  bootstrap    Launcher · DataBlasterApplication · AppConfig · ViewLoader
                     |
                     v
  controller   MainController · Preferences/About · View1-4
      |              |
      |              v
      |         ui   StageRegistry · ViewRegistry · ViewSwitcher · DialogService
      |              |
      v              v
        model        AppState  (imports nothing from the application)
                     ^
                     |
  web          StatusController · LoopbackHostFilter   (reads via Snapshot only)
```

**The rule: dependencies point inward, toward `model`.** `model` imports nothing from
`controller`, `ui`, or `web`. `web` imports nothing from `controller` or `ui`. No controller
imports another controller.

| Package | Holds | Depends on |
|---|---|---|
| `com.culberth.tools.datablaster` | Entry points, Spring config, FXML loading | `ui`, `controller` |
| `.model` | `AppState` — shared UI state and its off-thread projection | nothing in the app |
| `.ui` | Window ownership, view registry, view swapping, dialogs | `model` |
| `.controller` | The shell and the content views | `ui`, `model` |
| `.controller.ribbon` | One controller per ribbon group | `ui`, `model` |
| `.web` | The companion HTTP layer | `model` (via `Snapshot` only) |

---

## 3. Start-up

1. `Launcher.main` — a separate entry point that does **not** extend `Application`, so the Spring
   Boot fat jar can be run with `java -jar` without tripping JavaFX's launcher checks.
2. `DataBlasterApplication.init()` boots Spring, including embedded Tomcat.
3. `DataBlasterApplication.start(Stage)` records the FX thread, registers the primary stage, loads
   `main.fxml`, and shows the window.
4. `stop()` closes the Spring context. The JVM then exits because Tomcat's non-daemon thread ends.

### The HTTP layer must not be able to prevent launch

Tomcat starts in `init()`, before any window exists. If the port is taken, a naive implementation
aborts the launch and writes its only diagnostic to a console that the windowed `jpackage` build
does not have — the app simply never opens, with no explanation.

So `init()` catches a startup failure, checks whether its most specific cause is a
`PortInUseException` or `BindException`, and if so retries with
`--spring.main.web-application-type=none`. The UI starts without the HTTP layer, and the window
title becomes `Data Blaster (HTTP layer disabled)` so the degraded state is visible where the user
actually is.

**Anything that is not a port conflict is rethrown.** A broad catch here would mislabel every
startup failure as a web-server problem and boot the whole context a second time before failing
anyway.

---

## 4. State and threading

`AppState` is a singleton holding `simFactor`, `logFolder`, `theme`, `currentViewId` and
`contentOpacity` as JavaFX properties. It is the only channel through which the ribbon groups, the shell and the web
layer communicate.

### Three rules, each enforced rather than documented

**Writes happen on the FX thread.** Every mutator calls a guard that throws `IllegalStateException`
naming the escape hatch. JavaFX properties perform no thread check of their own, so an off-thread
write would run the whole listener chain — including the snapshot rebuild, which reads several
properties non-atomically — on that thread.

**There is no second way in.** The property accessors return `ReadOnlyDoubleProperty` and friends.
Returning the mutable `Property` would leave `appState.currentViewIdProperty().set(...)` as an
unguarded door beside the guarded one. No call site needed changing: every reader binds, formats or
observes.

**Off-thread readers use `snapshot()`.** It returns an immutable record published through a
`volatile` field. `Snapshot` is deliberately narrower than `AppState` — it carries only what a
caller outside the UI could act on, so cosmetic window-scoped values do not end up in a record the
web layer parses.

### The FX thread is recorded, not probed

The guard compares against a `Thread` captured at start-up rather than calling
`Platform.isFxApplicationThread()`. That call initialises the JavaFX toolkit and loads native
libraries, which would make every test touching `AppState` require a display and fail on a headless
build agent.

### Subscribers must be weak

`AppState` lives for the life of the application; its subscribers are prototype-scoped FXML
controllers that come and go with their scene graphs. A plain lambda listener would pin a discarded
shell and its entire scene graph forever — and leave it *reacting*, so a click in a live ribbon
would also swap views in a detached one.

Subscribe with `WeakChangeListener`, holding the strong reference in a controller field.

---

## 5. FXML and controller lifecycle

`ViewLoader` loads FXML with the Spring context as the controller factory, so controllers get
constructor injection.

**Every FXML controller is `@Scope(SCOPE_PROTOTYPE)`.** The loader binds the returned instance's
`@FXML` fields to the node tree it just built. A singleton controller would be reused across loads,
still pointing at the previous, detached tree — and would accumulate one duplicate listener
registration per load.

`ViewLoader` also owns scene creation, so every scene it builds carries `ribbon.css`. Dialogs
previously constructed their own scenes and rendered in stock modena.

---

## 6. The ribbon

`main.fxml` holds the menu bar, the tab strip and the content host. Each ribbon group is a separate
FXML under `fxml/ribbon/` with its own controller in `controller.ribbon`, pulled in with
`<fx:include>`.

**Groups and the shell never hold each other's nodes.** The Mode group publishes a view id; the
shell observes it and performs the swap. The Appearance group writes `contentOpacity`; the content
area binds to it. The obvious alternative — handing each group a reference to the content pane —
recreates the shared-mutable-node defect that `ViewSwitcher` was made stateless to eliminate.

Adding a ribbon group is a new FXML, a new controller, and one `<fx:include>` line. No edit to
`MainController`; no edit to another group.

### Two contracts a new group must honour

**Ordering.** `FXMLLoader` builds depth-first, so a group's `initialize()` runs *before* the
shell's. A group may safely *subscribe* there, but must not *publish* — the shell has not run yet
and will overwrite it.

**Rendering current state, not transitions.** The shell renders whatever `currentViewId` already
holds, seeding a default only when nothing is set. Relying on a change event means a second shell
— `AppState` being a singleton — sees no transition and comes up blank.

### Failure rolls back

`ViewSwitcher` only replaces the container's children on success, and the shell restores
`currentViewId` to the view actually on screen if a load fails. Otherwise the ribbon highlights a
view that never rendered and `snapshot()` reports it to the web layer.

---

## 7. Error reporting

Exceptions escaping an FX event handler reach a default handler that reports to a console the
windowed build does not have — the control appears to do nothing at all.

`DialogService` guards the entire dialog path (load, scene creation, `showAndWait`), and
`showError` **logs the throwable before showing the alert**. Showing only `getMessage()` would trade
a full stack trace for one line, leaving the console build harder to diagnose than one with no
error handling at all. `showError` is itself guarded, since it is normally called from a catch block
and must not replace the real failure with its own.

---

## 8. The HTTP layer

An embedded Tomcat bound to `127.0.0.1:8080`, serving one endpoint:

```
GET http://localhost:8080/api/status  ->  {"app":"Data Blaster","state":"running"}
```

**`LoopbackHostFilter` validates the `Host` header on every path**, returning a bare, bodiless 404
otherwise. Three decisions are load-bearing:

- **A filter, not a handler check.** The loopback bind does not stop DNS rebinding: a page on an
  attacker's domain resolving to 127.0.0.1 reaches this server same-origin. A per-handler check
  leaves `/`, unmapped paths falling through to the error controller, and every future endpoint
  answering rebound requests.
- **No response body.** A distinctive error message fingerprints the application just as reliably
  as leaking state does.
- **Not Actuator's vocabulary.** `{"status":"UP"}` would collide with Actuator's own health
  endpoint if it is ever added, leaving two endpoints that can disagree.

### Adding an endpoint

Handlers run on Tomcat worker threads, never the FX thread. Read via `AppState.snapshot()`; write
via `AppState.onFxThread(Runnable)`. Never touch a JavaFX node or property directly.

**Note the trade-off before exposing more:** `Snapshot` carries the absolute log-folder path, which
contains the OS username. Loopback is a *host* boundary, not a user boundary — it does not separate
local accounts on a shared or multi-session machine. Nothing under `/api` is authenticated.

---

## 8a. Settings persistence

`simFactor`, `logFolder` and `theme` survive a restart. Nothing else does — `contentOpacity` is a
view control with a Reset button beside it, and `currentViewId` points at a placeholder.

**`theme` is deliberately absent from `AppState.Snapshot`.** That record is documented as the fields
a caller outside the UI could meaningfully use, and which colours a window is painted in is not one
of them. So `Settings.from` takes the `AppState` and reads it on the FX thread — where its only
caller, a change listener, already runs — rather than widening a record whose contract says
otherwise. That is the third key the store was told to expect.

Three pieces, split so the fragile part is testable on its own:

| Piece | Knows about |
|---|---|
| `Settings` | Nothing. An immutable record, safe to hand to another thread. |
| `SettingsStore` | Files only — not `AppState`, not JavaFX. Which is why its tolerance rules can be tested against hand-written broken files with no toolkit and no context. |
| `SettingsService` | Both. The only piece that needs either. |

### Four rules

**Restore before the shell, not after.** The ribbon groups read their starting values from
`AppState` in `initialize()` and never re-read them, so binding afterwards leaves the controls
showing defaults while the state says otherwise — with nothing thrown and nothing logged.
`SettingsRestoreOrderTest` asserts both directions.

**Restore, then subscribe — inside `bind()`.** The other order makes the restore look like a user
edit and writes the file straight back on every launch. The two are one method precisely so a
caller cannot get the order wrong.

**Read tolerantly, and per value.** A missing file is a first run and is silent. Anything else falls
back to defaults and is logged, and each value falls back on its own — a mistyped number does not
discard a good folder path. `read()` does not throw: NFR1's argument about the HTTP layer applies
just as much to a settings file someone has hand-edited.

**Write off the FX thread, coalesced.** A slider drag fires a change per step, so the writer holds
only the latest value and a burst collapses into as few writes as the disk can take. The snapshot is
taken on the FX thread — `AppState.snapshot()` exists for exactly this — and a `@PreDestroy` flush
stops a change made just before quitting from being lost to a daemon thread.

### Two surfaces, one value

Sim Factor and the log folder appear both in the ribbon and in Preferences. Neither holds a copy —
both read from and write to `AppState`, and both read-outs are *bound* rather than assigned, so they
track it without being rebuilt.

Preferences applies edits immediately and closes with **Close**, not OK/Cancel. That is not a
shortcut: a Cancel needs somewhere to hold uncommitted edits, and that buffer is a second copy of
state `AppState` owns — the exact duplication consolidating the surfaces was meant to remove.
**Reset to defaults** answers "I did not mean that" and is a much smaller thing to get right.

The `DirectoryChooser` itself lives in `LogFolderChooser`, a singleton, so the two surfaces cannot
drift on title or remembered directory — and the remembered directory now outlives the
prototype-scoped ribbon controller that used to own it.

### Theming

The theme is a style class on the scene root, and the entire dark theme is one block of token
overrides in `ribbon.css`. That is the return on the stylesheet having been tokenised: JavaFX
resolves looked-up colours from the node outward, so redefining `-jfx-surface` and friends under
`.root.theme-dark` re-colours every rule at once. The alternative — a second stylesheet per theme —
is a parallel copy of every rule to keep in step.

`ThemeService` applies the class to each scene `ViewLoader` creates, remembers that root weakly, and
re-applies to all of them when the theme changes. **That second part is the feature.** A theme
control was removed from this project once already rather than shipped, because it persisted a value
nothing honoured; applying the theme only at scene creation would have been the same defect wearing
a different hat. `ThemeSwitchingTest` asserts a window already on screen changes, and switches twice
so a theme that applies once and then sticks fails.

**Its own list of roots, not `Window.getWindows()`.** The toolkit's list carries windows that are
*showing*, so a scene built but not yet shown is missed and the membership rules belong to JavaFX
rather than to this application. Weak references, so a dismissed dialog's scene graph stays
collectable.

**An invalidation listener, not a change listener.** As the only `ChangeListener` on the theme
property, `ThemeService` was notified of the first switch and not the second — the theme changed
once and then appeared stuck. Attaching any unrelated second listener made it work again, which is
the kind of symptom that sends you looking anywhere but at the listener. Invalidation is also
simply the right primitive: this class re-reads the current theme and never wants the previous
one.

**Two override blocks, not one.** The tokens re-colour everything this stylesheet draws; modena
paints its own control chrome from `-fx-base` and friends, which know nothing about those tokens. So
the dark theme redefines both. Miss the second and the ribbon goes dark around light sliders,
buttons and menus.

Contrast is enforced rather than documented: `ThemeContrastTest` parses the token blocks and checks
every on-screen pair in both themes. The tightest is 3.32:1 in light and 4.38:1 in dark, both
non-text pairs against a 3:1 floor.

### The file

A properties file under the platform's per-user config location (`%APPDATA%` on Windows,
`~/Library/Application Support` on macOS, `$XDG_CONFIG_HOME` or `~/.config` elsewhere), UTF-8, read
with a leading byte-order mark skipped.

`java.util.prefs.Preferences` was the alternative and is rejected on purpose: on Windows it writes
to the registry, and for a template whose deliverable is that a reader can see what it does, "where
did my setting go" should be answerable with a file manager.

**The BOM handling is not defensive padding.** Windows Notepad's "UTF-8" and PowerShell's
`Set-Content -Encoding utf8` both write one, `Properties` does not treat it as whitespace, and it
therefore joins the *first* key — so exactly one setting goes silently missing while the rest of the
file loads correctly. Found by hand-editing the real settings file and watching Sim Factor come back
as 0.0 while the log folder on the next line restored perfectly.

---

## 9. Testing

110 tests, no display required. Run on Linux and Windows for every push — see §10.

| Suite | Covers | Toolkit |
|---|---|---|
| `AppStateTest` | Snapshot derivation and freshness, the off-thread rejection contract, and — by reflection — that the accessors stay read-only | no |
| `LoopbackHostFilterTest` | 24 host vectors, including the suffix attacks (`localhost.evil.example`) that a refactor to `startsWith` would silently open | no |
| `ViewRegistryTest` | Resolution, defaults, and the unknown-id message | no |
| `StatusControllerTest` | Payload shape, and that it avoids Actuator's vocabulary | no |
| `SpringContextTest` | That the context starts, that every controller under `controller` is prototype-scoped, and that the shared services are not | no |
| `ModeGroupViewIdTest` | That every `userData` view id declared in the ribbon resolves through `ViewRegistry`, read from the FXML as XML | no |
| `FxmlSmokeTest` | That all ten FXML files load through the real Spring-backed controller factory | **yes** |
| `SettingsStoreTest` | The tolerance rules, against hand-written files: missing, corrupt, out of range, BOM-prefixed, non-ASCII | no |
| `SettingsServiceTest` | Restore, coalesced writes, the shutdown flush, and that binding does not write back what it just read | no |
| `SettingsRestoreOrderTest` | That restoring *before* the controls are built is what puts stored values on them — including the negative case | **yes** |
| `PreferencesSurfaceTest` | That Preferences and the ribbon are two views of one value: the dialog reads live state, writes through it, and both read-outs track it without a reopen | **yes** |
| `ThemeContrastTest` | Every on-screen colour pair in **both** themes, parsed from `ribbon.css`, against 4.5:1 for text and 3:1 for focus indicators | no |
| `ThemeSwitchingTest` | That switching restyles windows that are **already open**, not only the next one created | **yes** |

### Two claims that used to travel together

This section previously said "no JavaFX toolkit required" and meant two different things by it.
They have come apart, and only one of them is a requirement:

- **No display required (NFR7) — still true, and now proven.** `FxmlSmokeTest` runs on Monocle's
  software-only Glass platform via `HeadlessToolkit`. The Linux CI runner has no display, so this
  is checked by a machine rather than asserted here.
- **No toolkit initialized — now narrowed** to "initialized only by `FxmlSmokeTest`." Every other
  suite must stay toolkit-free. `AppStateTest` matters most: recording the FX thread instead of
  probing it (§4) exists precisely so that class never pulls in native libraries, and calling
  `HeadlessToolkit.start()` from it would undo that for no gain.

### Why the FXML tests need to load rather than inspect

A dropped `fx:id`, a renamed `onAction` handler, and a controller that lost its `@Component` all
fail during `FXMLLoader.load()` — because every `initialize()` in this project dereferences its
injected fields, so an unbound field throws rather than leaving a control that quietly does
nothing. Each of those was verified by breaking it on purpose and watching the suite go red.

### Shared static state between test classes

Surefire runs every class in one JVM, so `AppState`'s recorded FX thread — a JVM-wide static — is
shared across the whole suite. `AppStateTest` records its own thread and must put it back
afterwards, and `FxmlSmokeTest` records the real FX thread the way `DataBlasterApplication.start()`
does, which keeps the threading guard live while FXML loads rather than bypassed by a null.

Surefire's run order is pinned to `alphabetical` in `pom.xml`. The default is `filesystem`, which
differs between Windows and Linux — that is how this exact leak passed locally and failed only on
CI. Pinning does not prevent shared-state leaks; it makes them fail the same way everywhere.

The remaining gap is behavioural: nothing drives the UI. A button whose handler runs but does the
wrong thing still passes. Covering that means a robot or a UI test framework, which is a larger
commitment than this project has made.

---

## 10. Build and packaging

- `mvn clean package` — jar plus Spring Boot fat jar
- `mvn exec:java` / `mvn spring-boot:run` — run in place
- `scripts/build-windowed.ps1` / `build-console.ps1` — `jpackage` app-images under `dist/`

The console variant exists for diagnosis: the windowed launcher has no stderr, so a startup failure
there is invisible without it.

`--add-modules` is a hand-maintained list, five of whose entries exist solely for embedded Tomcat
and were found empirically. A dependency change that reaches a new JDK module fails at native
launch; recovery is to run the console variant and read the stack trace. Packaging is Windows-only.
Both are accepted limits for a single-platform example.

---

## 11. Standing constraints

**The supported-branch position is deliberate, and has a date on it.** This project ran on Spring
Boot 3.5 until that branch reached open-source end of life on 30 June 2026, and moved to **4.1.1**
rather than 4.0, which reaches end of life on 31 December 2026. 4.1 is maintained through
**31 July 2027**.

That date is the constraint, not a footnote. The app-image has no update mechanism, so a fork
still on 4.1 after July 2027 has no patch path for a vulnerability in the embedded Tomcat. **A fork
intended to ship must plan to move again**, and the reason this is stated rather than left implicit
is that the previous silence about 3.5 read as an oversight.

**The app-image is unsigned**, with no published hash and no update path. Anyone with write access
to where it was unpacked can replace its contents.

**Boot's failure banner prints on a successful fallback.** In the console build,
"APPLICATION FAILED TO START" appears from the first attempt immediately before the app starts
fine. Suppressing it means disabling the failure analyzers wholesale.

**Nothing may be logged while a failed context is being replaced.** Spring Boot tears its logging
system down when a context fails, so anything logged between that failure and the next context
starting goes to a stopped Logback and is discarded without a trace. The port-conflict fallback in
`DataBlasterApplication.init()` logs *after* its replacement context is up for exactly this reason.

This is a live constraint, not a historical note: any future recovery path that retries a context
has the same window, and the symptom is a log line that simply is not there. It is not visible from
a green build, and it has no test — it is verified by starting the application twice and reading the
console build's output.

---

## 12. Where the reasoning came from

Most of the "why" above is the residue of a four-specialist peer review — 46 findings across two
verification rounds, including several fixes that compiled, launched cleanly, passed every test,
and were still wrong. See [peer-review.md](peer-review.md).
