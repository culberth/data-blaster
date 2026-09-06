# Architecture

How Data Blaster is put together, and — more usefully — **why**, since several of the arrangements
here exist to prevent specific defects that a peer review found in earlier versions of this code.

| | |
|---|---|
| **Describes** | `rename-to-data-blaster`, after the mode views — v1's feature scope complete |
| **Size** | ~3,840 lines of main Java, ~3,650 of test, 16 FXML files, one stylesheet |
| **Stack** | Java 21, JavaFX 21.0.2, Spring Boot 4.1.1 (no web layer), Maven |
| **Origin** | Forked from JFXRibbon, which was written as a template. See [PRD.md](PRD.md) |

---

## 1. What this is

A desktop application shell: an Office-style ribbon, a switchable content area and two modal
dialogs. It runs on the JavaFX Application Thread with a Spring `ApplicationContext` behind it, so
UI components are Spring beans and can be given dependencies by constructor injection.

The template also carried a small loopback-only HTTP layer. It has been removed — see §8 for what
that bought and what is worth carrying forward when SOAP mode brings a server of its own.

**Every mode is configurable, and v1's feature scope is complete.** `Mode` is a first-class enum,
`ViewRegistry` is keyed by it, the selected mode persists, each mode has its own block of persisted
settings under its own key namespace, the ribbon follows the selected mode, Preferences is a tab per
scope with a working port-to-tail editor, and each mode has a view that names it and shows its live
configuration.

**None of the modes do anything.** That is deliberate and is the PRD's stated v1 boundary: v1 makes
the modes configurable and implements no mode behaviour. The views say so rather than being blank,
and REST — which is not designed at all, not merely unimplemented — says that instead.

The shell existed to be forked, and this is the fork. That history shapes several decisions recorded
below: where duplication is tolerated, and where it is not.

---

## 2. Layers and dependency direction

```
  bootstrap    Launcher · DataBlasterApplication · AppConfig · ViewLoader
                     |
                     v
  controller   MainController · Preferences/About · the four mode views
               ribbon: Mode · contextual slot (Log | Message) · Global
      |              |
      |              v
      |         ui   StageRegistry · ViewRegistry · RibbonGroupRegistry · ViewSwitcher
      |              DialogService · LogScale · PortSpinner
      |              LogFolderChooser · DataFileChooser
      |              |
      v              v
        model        AppState · Mode · Settings · TailNumber · IpAddress
                     (imports nothing from the application)
```

**The rule: dependencies point inward, toward `model`.** `model` imports nothing from `controller`
or `ui`. No controller imports another controller.

There used to be a fourth package, `web`, sitting alongside `controller` and reading `model` through
a narrow projection. It is gone (§8); the rule it demonstrated — that a package outside the UI
depends on `model` and on nothing else — is the one to reinstate if SOAP mode adds a server.

| Package | Holds | Depends on |
|---|---|---|
| `com.culberth.tools.datablaster` | Entry points, Spring config, FXML loading | `ui`, `controller` |
| `.model` | `AppState`; `Mode`, `MessageType`, `Theme`, `PortTailMapping`; the settings record, store and service | nothing in the app |
| `.ui` | Window ownership, the two registries, view swapping, dialogs, the log-track scale | `model` |
| `.controller` | The shell, the dialogs, and one view per mode | `ui`, `model` |
| `.controller.ribbon` | One controller per ribbon group | `ui`, `model` |
| `.controller.preferences` | One controller per Preferences tab | `ui`, `model` |

---

## 3. Start-up

1. `Launcher.main` — a separate entry point that does **not** extend `Application`, so the Spring
   Boot fat jar can be run with `java -jar` without tripping JavaFX's launcher checks.
2. `DataBlasterApplication.init()` boots Spring.
3. `DataBlasterApplication.start(Stage)` records the FX thread, registers the primary stage, loads
   `main.fxml`, and shows the window.
4. `stop()` closes the Spring context, which runs the `@PreDestroy` settings flush.

### `init()` no longer catches anything

This used to be the most intricate path in the project. Tomcat started in `init()` before any window
existed, so a taken port aborted the launch and wrote its only diagnostic to a console the windowed
`jpackage` build does not have — the app simply never opened, with no explanation. `init()`
therefore caught the failure, checked whether its most specific cause was a `PortInUseException` or
`BindException`, retried without the web layer, and put `(HTTP layer disabled)` in the window title
so the degraded state was visible where the user actually is. The retry deliberately narrowed to
port conflicts, and the warning was deliberately logged *after* the replacement context came up,
because Spring tears down Logback when a context fails and anything logged in that window is
silently discarded.

**All of it is gone**, because there is no server, no port, and therefore nothing a retry could fix.
`init()` is now three lines with no branching. A failure there is a real bug and propagates.

It is recorded here rather than deleted outright because the reasoning outlived the code: if SOAP
mode starts a server during launch, it inherits exactly this problem, and the shape of the answer —
narrow the retry to the specific cause, surface the degraded state in the UI, and log *after* the
context is back up — is the part that took the effort to get right.

**The one piece still live** is the `catch` in `start(Stage)`. JavaFX only calls `stop()` when
`start()` completed, so a failure there would leave the booted Spring context open with no window
and no shutdown hooks — including the settings flush.

---

## 4. State and threading

`AppState` is a singleton holding the global `currentMode`, `theme`, `blastPort`, `singleMessage`
and `byteHijack`; Log mode's `playbackSpeedFactor`, `logFolder` and port-to-tail mappings; Message
mode's `messageType`; and SOAP mode's `soapIp`, `soapMessageType`, data files and `soapTail`. It is
the only channel through which the ribbon groups, the shell and the Preferences dialog communicate.

Everything but the two lists is a JavaFX property. The mappings and the SOAP data files are
`ObservableList`s — which is what makes them the interesting case below, because none of the rules
this class enforces were written with a collection in mind.

**`contentOpacity` used to be here and is gone.** It was a view control rather than a setting, and
it was the only one; when the three global settings took its slot in the ribbon (§6) it was removed
rather than relocated, because a lone view control with nowhere to live is a category with one
member. The state it needed — a `DoubleProperty` the content area bound to — went with it.

**The fields are flat; the persisted form is not.** `AppState` does not nest the mode-scoped values
in per-mode holders the way `Settings` does. That is an implementation choice rather than a product
one: a flat field is what a control binds to, and grouping them would buy a tidier class listing at
the cost of an indirection on every read. The grouping that matters — the one a person editing the
file sees — is in `Settings` and in the key namespaces.

### Three rules, each enforced rather than documented

**Writes happen on the FX thread.** Every mutator calls a guard that throws `IllegalStateException`
naming the escape hatch. JavaFX properties perform no thread check of their own, so an off-thread
write would run the whole listener chain on that thread — every binding, every control observing the
value, and the settings writer's change listener.

**There is no second way in.** The property accessors return `ReadOnlyDoubleProperty` and friends.
Returning the mutable `Property` would leave `appState.currentModeProperty().set(...)` as an
unguarded door beside the guarded one. No call site needed changing: every reader binds, formats or
observes.

**Off-thread writers use `onFxThread(Runnable)`.** It is the escape hatch the guard's own error
message names, which is why it survives even though nothing currently calls it.

**There is no off-thread read path, and that absence is deliberate.** This class used to publish an
immutable `Snapshot` record through a `volatile` field, rebuilt by listeners on every field it
carried, because the HTTP layer read state from Tomcat worker threads. That layer is gone (§8), so
the record went with it rather than being maintained on every write for a reader that no longer
exists.

When SOAP mode brings a server back, rebuild the pattern rather than improvising: an immutable
record, published through a `volatile` field, rewritten on the FX thread by a listener, and
deliberately **narrower** than this class. The narrowness is the part worth remembering — tail
numbers are the most identifying data this tool holds, and a loopback bind separates hosts rather
than users, so a projection built for one handler is what stops the next one leaking them by
accident. `AppStateTest` pins the absence, because the reflex answer to "the server needs this
state" is to hand it the live object, which is what the projection existed to prevent.
`Settings.from` is the nearest live example of the shape: read on the FX thread, immutable once it
crosses.

### The collection obeys the same rules, and does not get them for free

Every rule above was written for scalar properties, and a collection is where each of them fails by
default. Both halves have a test that goes red if the shortcut is taken.

**`portTailMappings()` hands out an unmodifiable view, not the list.** A read-only *property*
accessor is not enough when the thing being returned is a `List`: returning the live
`ObservableList` would let any caller, on any thread, add an entry past both the FX-thread guard and
the uniqueness rules. Writes go through `setPortTailMappings`, `addPortTailMapping` and
`removePortTailMappingForPort`, which are guarded like every other mutator.

**Edits replace the set rather than mutating it.** The uniqueness rules are checked against the
complete set every time, and a rejected edit therefore cannot leave the observable list
half-updated for whoever is watching it. It also means one change event per edit rather than a
remove followed by an add.

**Nothing outside the UI can read the mappings at all.** That was a design constraint on `Snapshot`
while it existed — tail numbers stayed out of it deliberately — and with the projection gone it is
now simply true of the whole class. It is the rule to carry forward, not a fact to rediscover: when
a server returns, the mappings do not go into its projection by default.

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

**Groups and the shell never hold each other's nodes.** The Mode group publishes a `Mode`; the
shell observes it and performs the swap. Every other group writes a setting to `AppState` and lets
whoever cares read it there. The obvious alternative — handing each group a reference to the content
pane — recreates the shared-mutable-node defect that `ViewSwitcher` was made stateless to eliminate.

**The toggles carry `Mode` constant names in `userData`.** Keying `ViewRegistry` by the enum turned
half of the old free-string wiring into a compiler error; the half that remains is the markup, where
a mode name is still just text. `ModeGroupViewIdTest` reads the FXML as XML and asserts every value
parses to a real constant, that every constant has a toggle, and that the toggle marked
`selected="true"` is the default mode — so a typo, a missing mode, or a default that no button
selects fails the build rather than the button.

Unlike `Mode.fromStoredName`, which falls back tolerantly because a hand-edited settings file is a
typo to recover from, `ModeGroupController` throws on an unknown `userData`. This is the
application's own markup, where a wrong value is a defect to surface rather than absorb.

Adding a ribbon group is a new FXML, a new controller, and one `<fx:include>` line. No edit to
`MainController`; no edit to another group.

### The contextual slot

Three groups are fixed — Mode, the contextual slot, and Global — and the middle one swaps its
contents with `currentMode`. Log shows playback speed and the log folder; Message shows its type;
SOAP and REST show nothing.

**The slot swaps itself.** `ContextualGroupController` observes `currentMode`, resolves it through
`RibbonGroupRegistry`, and replaces its own children. Putting that logic in `MainController` was the
obvious move and would have broken the property this section opens with: the shell includes the slot
with one `<fx:include>` exactly like the fixed groups, and knows nothing about what goes in it. It
is the same shape as the content-area swap, one level down.

**Only Log and Message have a group, by decision.** A ribbon is for controls reached for repeatedly.
Playback speed is scrubbed and message type is flipped between sends; SOAP's address, type, files
and tail are all set once for a run and belong in Preferences, and REST has no behaviour to
configure. Giving every mode a group would fill the ribbon with controls nobody reaches for and put
set-once values one mis-click away.
`RibbonGroupRegistry` is a separate class from `ViewRegistry` for exactly this reason: a missing
content view is a defect and throws, while a missing ribbon group is the normal answer and comes
back empty. One class with two lookups behaving oppositely on a miss would read worse than two.

**An empty slot un-manages itself.** Left visible-but-empty it would reserve width, leaving a hole
with the ribbon's spacing on both sides of it — a layout bug no assertion about children would
catch, which is why `ContextualRibbonTest` checks `isManaged()` directly.

**The slot pins itself to its own node tree, and has to.** Every other controller in this project
is reachable from its own nodes by accident: an `onAction` handler, or a listener lambda registered
on one of its own controls, gives the scene graph a strong reference back to the controller. The
contextual slot has neither — it only observes `AppState`, and it observes *weakly*, as a
prototype-scoped controller must. So once `FXMLLoader` returned, nothing referred to it at all: the
weak listener cleared at the next collection and the ribbon quietly stopped following the mode, at
an arbitrary later moment, looking like anything but a lifetime problem.

`initialize()` therefore parks the controller in its root node's property map. That is the intended
semantics rather than a workaround for the weak-listener rule: while the slot is on screen the
listener must live, and when the slot is discarded both go together and the listener detaches —
which is exactly what the rule asks for. `ContextualRibbonTest` asserts the reference structurally,
because `System.gc()` is a hint and a behavioural version of that test could pass while the bug was
present.

**A new controller that only observes needs the same treatment.** If it has no handler and registers
nothing on its own controls, it is collectable, and the symptom is silence.

**A group that fails to load is logged, not raised.** Unlike a content view, which gets a dialog, a
ribbon group failing during shell construction would put an alert on screen before there is a window
to own it — and `FxmlSmokeTest` loads every one of these files through the real factory, so a broken
group fails the build instead.

### The Global group, and the opacity slider it replaced

The third fixed slot holds the settings that belong to no mode: **Blast Port**, **Single Message**
and **Byte Hijack**. It was called Appearance and its only control was a content-opacity slider.

**Opacity was removed, not moved.** It was a view control rather than a setting — adjusted while
looking at something, reset by the button beside it, deliberately excluded from persistence — and it
was the only one in the application. There was no second home for it, and keeping it alongside three
real settings under a heading that no longer described any of them would have been worse than either
option. The group is called Global now because that is what the three have in common; "Appearance"
would have been a name that fitted nothing in it.

**All three are also in Preferences' General tab.** Neither surface owns the value; both read and
write `AppState`, and each control follows the property as well as writing to it. That last half is
what opacity never needed, because it had one surface: with two, a control that read `AppState` once
would sit there disagreeing with it the moment the other one changed anything. Setting a JavaFX
control to the value it already holds fires nothing, so each write-back loop closes itself and none
of these needs a re-entrancy flag — unlike the log-scaled slider below, whose round trip does not
land where it started.

**The port spinner is configured by `ui.PortSpinner`, not by each controller.** A port spinner needs
three things that are each easy to omit and invisible when they are: bounds from
`PortTailMapping`'s range, so a control cannot offer a value the store would reject on the next
launch; a converter that keeps the current value rather than throwing a `NumberFormatException` out
of a focus listener into a console the windowed build does not have; and an `increment(0)` on focus
loss, without which a typed port is discarded when the user tabs away or presses Close. Two copies
of that would be two chances for one surface to accept what the other refuses.

### The log-scaled slider

Playback Speed Factor is on the ribbon as a slider whose track is **logarithmic**, and in Preferences
as a `Spinner` for typing an exact number. Neither owns the value; both read and write `AppState`.

The track runs 0–1 in position units, not multipliers — `LogScale` converts, and nothing outside
`LogGroupController` ever sees a position. On a linear 0.1–10.0 track the default of 1.0 sits at 9%
of the travel, with every slow-motion setting crushed into the leftmost tenth; on a log track it is
dead centre, because 1.0 is the geometric mean of the bounds. Halving and doubling then cover equal
distance, which is what a multiplier means.

Two consequences worth knowing:

- **The scale rounds, and that rounding is stored.** A log conversion lands on values like
  `1.0000000000000002`. Rounding in `LogScale.valueAt(position, decimals)` rather than in the
  read-out's format is deliberate: the rounded number is what reaches the settings file, so
  formatting it away would leave the file holding noise the UI never showed.
- **This control follows `AppState` rather than reading it once.** Most controls here read at
  `initialize()` and only write afterwards. This one also listens, because Preferences can change
  the speed while the group exists — closing that dialog would otherwise leave the read-out showing
  the new value beside a thumb at the old position, and the next nudge would throw the new value
  away. That two-way link needs a re-entrancy guard: the slider writes a *rounded* speed, which
  converts back to a position that is not bit-identical to where the thumb is, so without the guard
  the thumb jumps under the cursor mid-drag.

Tick marks are deliberately off. Evenly spaced ticks on a logarithmic track would label positions
that are not evenly spaced in value; the numeric read-out is the scale instead.

### Two contracts a new group must honour

**Ordering.** `FXMLLoader` builds depth-first, so a group's `initialize()` runs *before* the
shell's. A group may safely *subscribe* there, but must not *publish* — the shell has not run yet
and will overwrite it.

**Rendering current state, not transitions.** The shell renders whatever `currentMode` already
holds. Relying on a change event means a second shell — `AppState` being a singleton — sees no
transition and comes up blank. There is no longer a "nothing selected yet" case to seed a default
for: `currentMode` starts at the default mode and rejects null, so the template's null branch went
with the string it was guarding.

### Failure rolls back

`ViewSwitcher` only replaces the container's children on success, and the shell restores
`currentMode` to the mode actually on screen if a load fails. Otherwise the ribbon highlights a
mode whose view never rendered, and every reader of `currentMode` believes it.

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

## 8. The HTTP layer, and why there isn't one

**Removed.** The application no longer serves anything, no longer binds a port, and no longer
depends on `spring-boot-starter-webmvc` — Spring is here for dependency injection and the bean
lifecycle only.

It existed in the template to demonstrate a Spring Boot context living behind a JavaFX app: an
embedded Tomcat on `127.0.0.1:8080` serving one `/api/status` endpoint, guarded by a
`LoopbackHostFilter` that validated the `Host` header on every path. It was well-reasoned for what
it was, and none of it was load-bearing for this application.

**What removing it cost, in the sense of what it took away:** an endpoint nothing consumed, a
DNS-rebinding filter guarding an endpoint nothing consumed, and the projection in §4 that existed to
feed them.

**What it bought:**

- No second bound port to maintain alongside the one SOAP mode will bind. This was the argument that
  settled it: keeping both would mean two servers, two port-conflict stories, and a security control
  on the one nobody asked for.
- No port-conflict fallback in `init()` (§3), which was the most intricate start-up path in the
  project and existed solely because a taken port must not stop a desktop app from opening.
- No `spring.main.web-application-type=none` on five test classes, which carried it because a plain
  context load would otherwise fail on any machine already running the app.
- No unauthenticated attack surface at all. Loopback is a *host* boundary, not a user boundary — it
  does not separate local accounts on a shared machine — and the settings this app now holds include
  tail numbers and a log-folder path containing the OS username.

**When SOAP mode brings a server back,** it starts from the mode's configured port rather than from
this one, and the two things worth carrying forward are the filter's reasoning (a loopback bind does
not stop DNS rebinding, so validate `Host` in a filter rather than per-handler, and fail with a bare
bodiless 404 so the error does not fingerprint the app) and §4's note on rebuilding the off-thread
read path. Neither survives as code, deliberately: a security control kept alive for a server that
does not exist yet is a control nobody is testing against a threat nobody currently has.

---

## 8a. Settings persistence

Everything survives a restart. Nothing is excluded any more: `contentOpacity` was the one exception,
and it no longer exists (§4, §6).

| Scope | Setting | Default |
|---|---|---|
| global | selected mode | `LOG` |
| global | theme | `LIGHT` |
| global | blast port (1–65535) | `8081` |
| global | single message | `false` |
| global | byte hijack | `false` |
| Log | playback speed factor (× real time, 0.1–10.0) | `1.0` |
| Log | log folder | none |
| Log | port → tail number mappings | empty |
| Message | message type | `MESSAGE_1` |
| SOAP | IP address (IPv4 dotted quad) | `127.0.0.1` |
| SOAP | message type | `TYPE_1` |
| SOAP | data files | empty |
| SOAP | tail number | none |

**The selected mode persists, and used not to.** The template excluded its `currentViewId` and said
why: the four views were placeholders, and restoring one was not behaviour a template should model.
The views are modes now, so the reason expired and the exclusion reversed. The Javadoc that
explained the old choice was rewritten rather than left to contradict the code.

**`Settings` is a record of per-mode records** — `LogSettings`, `MessageSettings`, `SoapSettings` —
mirroring the file's key namespaces and the Preferences tabs. A flat record would be a widening list
of unrelated scalars whose only clue to what belongs where is a name prefix.

**The global settings are components of `Settings` itself, not a fourth nested record.** Being
unprefixed *is* what says a setting belongs to no mode, which was already true of `mode` and `theme`
before there were any others; a `global.*` namespace would have made that true of three keys and
false of the two beside them. `LogSettings` groups a folder, a speed and a table because they have
nothing in common but their mode — five unrelated global scalars have nothing in common at all, and
a holder for them would be a box rather than a grouping.

`LogSettings` copies its mapping list into an unmodifiable, port-ordered one in its constructor
rather than trusting callers, and `SoapSettings` does the same with its data file paths — dropping
blanks and repeats rather than rejecting them, since a file picked twice from a chooser is a person
using a chooser, not an error worth failing a settings write over. `Settings` is handed to a
background writer thread on the strength of being immutable, and a record wrapping a mutable `List`
is not immutable however its accessors read.

**`Settings.from` reads `AppState` directly, on the FX thread** — where its only caller, a change
listener, already runs. It took an `AppState.Snapshot` once and was changed away from it because the
theme was deliberately not in that record; now that the record is gone entirely (§4), reading the
properties directly is the only path, and the immutable `Settings` it produces is what crosses to
the writer thread.

**`8081` is the Blast Port default**, inherited from the SOAP listen port it outlived. The original
reason was that the removed HTTP layer bound `8080`; that conflict is gone, but `8080` remains the
likeliest port for whatever else is already running, and the value is stored in files that exist, so
moving it now would relocate a configured port under someone for no benefit.

**SOAP's port became an address, which is a re-specification rather than a rename.** SOAP mode
sends; it does not listen. A listen port was a setting shaped like a question the mode does not ask,
and no amount of validation would have made it the right one. `soap.port` is therefore an unknown
key now, not a migrated one — the per-key tolerant read means an existing file simply loses a value
that meant nothing, rather than restoring a port into a field that would have had to reinterpret it.

**`127.0.0.1` is the SOAP default** because a mode with no behaviour should default to the address
that cannot reach anything, in case the behaviour arrives before anyone revisits the line.

**The address rule is IPv4 dotted-quad, and the octets are parsed rather than counted.** `999.1.1.1`
matches four dot-separated digit runs and is not an address; `010.1.1.1` is ten to this parser and
eight to some resolvers, so it is refused for the reason `log.mapping.080` is a collision rather
than a second spelling. Hostnames are out because a hostname pattern accepts very nearly any string,
which is the point at which a validator stops catching typos; IPv6 is out because it is validation
surface for a mode with no behaviour. Both are a widened pattern plus a test if they turn up, which
is a smaller change than narrowing a rule people have already stored values against.

**The tail rule moved to `TailNumber` and `PortTailMapping` delegates to it.** SOAP's tail is
specified as having the same constraints as a mapping's, and "the same constraints" only stays true
with one implementation of them. Two copies of a six-character pattern would be a validator and a
table that eventually disagreed about whether `N123` is a tail. SOAP's tail differs in exactly one
way, and it is deliberate: it may be absent. A mapping without a tail is not a mapping; a run with
no tail configured is an ordinary state, and the same one the log folder starts in.

Three pieces, split so the fragile part is testable on its own:

| Piece | Knows about |
|---|---|
| `Settings` | Nothing. An immutable record, safe to hand to another thread. |
| `SettingsStore` | Files only — not `AppState`, not JavaFX. Which is why its tolerance rules can be tested against hand-written broken files with no toolkit and no context. |
| `SettingsService` | Both. The only piece that needs either. |

### Five rules

**Restore before the shell, not after.** Controls read their starting values from `AppState` in
`initialize()` and never re-read them, so binding afterwards leaves them showing defaults while the
state says otherwise — with nothing thrown and nothing logged. `SettingsRestoreOrderTest` asserts
both directions, using the Preferences spinner and the Mode ribbon group as its subjects.

**Restore, then subscribe — inside `bind()`.** The other order makes the restore look like a user
edit and writes the file straight back on every launch. The two are one method precisely so a
caller cannot get the order wrong. This applies to the mapping table as much as to the scalars: a
collection restored after the subscription rewrites the file on every launch just as a number would.

**A collection needs a `ListChangeListener`.** `bind()` subscribes to the mapping list separately
from the properties, because a `ChangeListener` on an `ObservableList` fires only when the property
holding the list is set to a *different* list — which never happens, the list being a final field.
Written the obvious way, mapping edits persist silently nowhere: no exception, no log line, just a
table that is empty again on the next launch. `flushOnShutdown()` unregisters it along with the
rest; every subscription `bind()` makes is one the shutdown has to undo.

**Read tolerantly, per value, and — for the mappings — per entry.** A missing file is a first run
and is silent. Anything else falls back to defaults and is logged, and each value falls back on its
own: a mistyped number does not discard a good folder path, and one malformed mapping does not
discard the other nineteen. `read()` does not throw — the argument that once applied to the HTTP
layer (a companion part must not be able to prevent launch) applies just as much to a settings file
someone has hand-edited, and now that the HTTP layer is gone this is the only place it still binds.

**Write off the FX thread, coalesced.** A control that fires on every step of a drag would otherwise
mean a file per step, so the writer holds only the latest value and a burst collapses into as few
writes as the disk can take. What crosses to the writer is an immutable `Settings` captured at the
moment of the change — including a copy of the mapping set, since handing the writer the live
observable list would put a collection the FX thread is still editing under a background reader. A
`@PreDestroy` flush stops a change made just before quitting from being lost to a daemon thread.

### The playback speed factor, and why its key was renamed

The template's Sim Factor was a `double` on a −5.0…5.0 slider with a default of `0.0`. It is now
Log mode's Playback Speed Factor: a multiplier on real time, `1.0` being real time, valid over
0.1–10.0 with a default of `1.0`.

**The out-of-range value is rejected, not clamped.** Clamping `0.0` up to `0.1` would start playback
crawling and report nothing; falling back to `1.0` with a log line says what happened. Both ends
name a behaviour the setting does not offer — `0.0` is frozen, a negative is reverse.

**The key moved from `simFactor` to `log.playbackSpeedFactor`, and that is what defuses the default
change.** Had the key been reused, an existing file holding `simFactor=0.0` would have restored as a
perfectly well-formed `0.0` — frozen playback, no warning, nothing out of range, because `0.0` *was*
the valid default under the old semantics. Because the key is new, the old spelling is simply an
unknown key and the new default applies. `SettingsStoreTest` pins that.

**The store validates the range; the control chooses the step.** A value between the spinner's
increments is still a legal setting, so `0.25` is typeable even though the arrows move by `0.1` —
otherwise a convenient step size quietly becomes a validation rule.

`Settings.SIM_FACTOR_MIN`/`MAX` were renamed to `PLAYBACK_SPEED_MIN`/`MAX` rather than re-pointed in
place. The constants are read by both the store's range check and the spinner's bounds, and leaving
a stale name on a changed meaning is how those two drift into disagreeing.

### Port-to-tail mappings

A tail number is **exactly six alphanumeric characters, upper-cased**: `N12345` and `123456` are
both valid, `N` has no special status, and the length is exact rather than a maximum. That is
stricter than any real registration rule, which is where its typo-catching value is.

**It is a `String`, despite being allowed to look like a number.** An integer type turns `000042`
into `42` on the first round-trip and cannot hold `N12345` at all.

**No instance can be invalid.** The canonical constructor normalises and validates, so there is no
back door around the factory — and normalising *there* rather than only in `of` is what makes the
uniqueness check meaningful, since `n12345` and `N12345` are otherwise different strings.

**Ports and tails are each unique, and the two are enforced in different places.** Port uniqueness
is a property of the file format — `log.mapping.<port>` is a key, and a properties file cannot hold
a key twice — so no read-path check has to be remembered. Tail uniqueness is the direction the
format cannot enforce, so it is checked in code. The one hole the format leaves is a port spelled
two ways (`80` and `080`), which the reader catches by resolving keys to ports in a first pass.

The reader takes two passes for that reason and one more: `Properties`' iteration order is
unspecified, so walking ports in ascending order is what makes "which of two mappings sharing a tail
survives" a fact about the file rather than about the hash order of the day.

This rejects hyphenated foreign registrations — `G-ABCD` is six characters only if the hyphen
counts. That follows from the rule as specified rather than being a decision about non-US aircraft;
if such tails turn up it is one character in the pattern plus a test. Flagged rather than allowed
pre-emptively, because a character class that also accepts `------` has stopped validating anything.

### Preferences: one tab per scope

`preferences.fxml` is a `TabPane` — General, Log, Message, SOAP — and owns nothing but the Close
button. Each tab is its own FXML with its own prototype-scoped controller under
`controller.preferences`, the same arrangement the ribbon groups use, so a tab gaining a control
needs no edit to the shell.

**Tabs rather than only the current mode's settings.** Configuring Message mode while running in Log
mode is the normal case, not the exception. Showing only the active mode's settings would make that
impossible and would change the dialog's shape under the user for a reason that is not their fault.

**REST has no tab**, because it has no settings. A tab saying "nothing here" would be a promise that
something is coming; the mode toggle already carries that message.

**Reset is per tab, and only the Log tab asks first.** A reset that silently cleared a mapping table
someone typed by hand is a different act from putting a spinner back — and a confirmation that
always appeared would be one people learn to dismiss without reading, which is exactly what makes
the one that matters ineffective. So the Log tab confirms only when the table is non-empty. The
prompt goes through `DialogService.confirm`, not an inline `Alert`: an `Alert` built at the call
site gets neither owner nor stylesheet, and opens in stock light chrome under the dark theme.

**Two surfaces, one value.** The log folder, the playback speed, the message type and the three
global settings each appear in both Preferences and the ribbon. None of them holds a copy — all read
and write `AppState`, and the read-outs are *bound* rather than assigned, so they track it without
being rebuilt.

**The General tab's Reset covers four settings and still does not ask.** None of them destroys
anything typed at length: a theme and two check boxes are instantly visible and instantly undone,
and a port is one number. Its disabled-when-nothing-to-reset condition is a single
`createBooleanBinding` over the four properties rather than a chain of `isEqualTo(...).and(...)`,
because `BooleanExpression.isEqualTo` takes another observable rather than a literal — the check
boxes would have had to be written as `not()`, which reads as "is off" and is only equivalent to "is
default" while the default happens to be `false`.

**The SOAP tab is the only surface for any of its four settings**, and two of them validate at
entry. The address and the tail commit on Enter and on focus loss — a `TextField` has no editor to
flush the way a `Spinner` does, but it has the same failure, where typing a value and pressing Close
discards it. Both funnel through a commit method that catches the validator's
`IllegalArgumentException` and shows its message verbatim beside the field, and a rejected entry
leaves the text as the user typed it, because snapping it back to the stored value would erase what
they were correcting. Its Reset confirms only when the data file list is non-empty, for the same
reason the Log tab's does.

### The mode views

Each mode's content view names it and shows that mode's live configuration read-only: Log its
folder, speed and mapping count; Message its type; SOAP its address, message type, data file count
and tail. REST shows none, because it has none.

SOAP's data files are a count rather than a list, the way Log's mappings are. These views are a
read-out of how a mode is configured; the place to see and change the list itself is the one place
that can change it.

**Read-only on purpose.** Every value is already editable in the ribbon or in Preferences. A third
editing surface would be a third thing to keep in step; a read-out is not — and being *bound* rather
than assigned makes these views the cheapest end-to-end demonstration that the settings plumbing
works, since the ribbon, the dialog and the view are three independent readers of one `AppState`.

**They are placeholders that say so.** No mode has behaviour, and each view states that rather than
leaving the reader to infer it from an empty pane. REST is different again and says something
different: it is not *designed*, not merely unimplemented, so it has no settings anywhere and its
note says that instead of implying something is switched off. That is also why its ribbon toggle
ships enabled — a dead button with no explanation leaves the user guessing whether the app is broken.

**The rename mattered more than it looks.** These were `view1.fxml` through `view4.fxml`, and
`ViewRegistry` mapped `LOG` to `view1`. Keying the registry by the enum first is what let this be a
rename rather than a rewiring; `ModeViewTest` now asserts each mode's view actually carries that
mode's title, which is a mis-wiring no compiler catches.

**They also carried a theming defect.** Each had `style="-fx-font-size: 22px;"` inline — a literal
the theme tokens could never reach, so the heading kept its light-theme text colour when everything
around it went dark. They use `.mode-view-*` classes now, and `ModeViewTest` fails if an inline
style comes back.

### The mapping editor

A `TableView` over `AppState.portTailMappings()` — the unmodifiable view itself, not a copy, so the
table tracks edits made anywhere without being rebuilt.

**Sorting is off on both columns.** A sort would try to reorder that list in place, and it is
unmodifiable by design; the entries already arrive in port order.

**Both columns are `String` columns, including Port.** An `Integer` column needs a converter, and
the default one throws on unparsable text from *inside* the cell's commit — an exception escaping
into the table's own event handling, where the controller cannot turn it into a message. Parsing the
text in the controller keeps every validation failure on one path.

**Rejection happens at entry, with the reason shown.** Add and both edit-commit handlers funnel
through one method that catches the `IllegalArgumentException` from `PortTailMapping` or
`AppState.setPortTailMappings` and shows its message beside the controls. Those messages are written
to be read by a person, which is why they are shown verbatim rather than rewritten in the UI. A
rejected edit also calls `refresh()`, because the cell has already accepted the text visually and
redrawing from the items list is what puts the old value back in front of the user.

**The Blast Port collision is a warning, not a block.** Nothing binds either port yet and the two
settings are independent, so refusing the entry would be inventing a rule — but the conflict it
predicts would surface much later, at bind time, with nothing pointing back here. The notice names
the port in text; the `-jfx-warning-text` colour is a secondary signal only, since a reader who
cannot distinguish it from a hint must still get the message.

It compared against SOAP's listen port until that port became an address. The notice moved to the
Blast Port rather than being deleted, because the reason it was worth raising did not change with
which port it names — but it did acquire a subscription it never needed before. The SOAP port lived
on a sibling tab of the same modal dialog and could not move while the Log tab was on screen; the
Blast Port is in the ribbon, so it can. A warning about a port that is no longer the Blast Port is
worse than no warning at all, so the tab listens to the property as well as to the table.

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

```properties
mode=log
theme=dark
blastPort=8081
singleMessage=false
byteHijack=false

log.playbackSpeedFactor=1.5
log.folder=C:\\logs\\capture
log.mapping.5001=N12345
log.mapping.5002=N7377X
log.mapping.5003=123456

message.type=MESSAGE_2

soap.ip=127.0.0.1
soap.messageType=TYPE_1
soap.tail=N54321
soap.dataFile.0=C:\\data\\one.bin
soap.dataFile.1=C:\\data\\two.bin
```

`java.util.prefs.Preferences` was the alternative and is rejected on purpose: on Windows it writes
to the registry, and the point of a plain file is that "where did my setting go" is answerable with
a file manager.

**Keys are namespaced, not nested.** `Properties` is flat, so `log.mapping.5001` is one key with
dots in it, and reading the mapping set means filtering `stringPropertyNames()` by prefix rather
than calling a fixed getter. That is a different read shape from the rest of the file and has its
own tests.

**Unprefixed is the global namespace**, not an absence of one: `mode`, `theme`, `blastPort`,
`singleMessage` and `byteHijack` are not mode-scoped. `simFactor` and `logFolder` moved under `log.`
and their old spellings are simply unknown keys, which the store already ignores; `soap.port` joined
them when SOAP's port became an address.

**`soap.dataFile.<index>` is indexed for a different reason than `log.mapping.<port>` is keyed.** A
port is the mapping's identity, so making it the key buys port uniqueness from the file format for
free. A file list has no such key; the index exists only to make the order reproducible, so it is
read as a sort key rather than a slot. Gaps and odd numbering in a hand-edited file are therefore
harmless, and a repeated path is dropped with a log line rather than stored twice.

**Booleans are parsed explicitly, not with `Boolean.parseBoolean`.** That method reads every value
that is not `"true"` as `false`, so a typo, a stray word or an accidentally blanked line would all
silently mean "off" — indistinguishable from someone having turned the setting off, with nothing
logged. An unreadable flag falls back to its default and says so, like every other value here.

**Old keys are not migrated, deliberately.** The version is unreleased and single-user, so a missing
key falling back silently is the whole migration story the per-key tolerant read already implements.

**The BOM handling is not defensive padding.** Windows Notepad's "UTF-8" and PowerShell's
`Set-Content -Encoding utf8` both write one, `Properties` does not treat it as whitespace, and it
therefore joins the *first* key — so exactly one setting goes silently missing while the rest of the
file loads correctly. Found by hand-editing the real settings file and watching the first value come
back as its default while the log folder on the next line restored perfectly.

---

## 9. Testing

342 tests, no display required. Run on Linux and Windows for every push — see §10.

| Suite | Covers | Toolkit |
|---|---|---|
| `AppStateTest` | The documented defaults, the off-thread rejection contract, and — by reflection — that the accessors stay read-only and that no off-thread read projection has crept back. Plus the collection's own versions of those rules: the mapping mutators are guarded, the handed-out list is unmodifiable, and an edit is one change event | no |
| `PortTailMappingTest` | The tail format, the port range, and uniqueness in both directions — including that two tails differing only in case collide rather than both being accepted | no |
| `StoredEnumParsingTest` | The tolerant-parse contract `Theme`, `Mode`, `MessageType` and `SoapMessageType` share: every constant round-trips, unknown values fall back rather than throwing, the stored form is not the shown form, and the two message-type enums do not answer for each other | no |
| `IpAddressTest` | The address rule itself: which strings are addresses, and — the half a shape-only pattern gets wrong — which of the strings that *look* like addresses are not. Pins hostnames and IPv6 as deliberately out of scope | no |
| `ViewRegistryTest` | That every `Mode` resolves to a view, that no two share one, and the miss message | no |
| `RibbonGroupRegistryTest` | That only Log and Message carry a contextual group, that a mode without one gets an empty `Optional` rather than a throw, and that every registered group is actually on the classpath | no |
| `LogScaleTest` | The log-track arithmetic: real time at mid-track, halving and doubling covering equal travel, round-trips, and that no rounded value falls outside what the store accepts | no |
| `ModeViewTest` | That every mode resolves to a view naming it, that the Log, Message and SOAP views show that mode's whole configuration **live**, that REST says plainly it is not implemented, and that no view styles itself with an inline literal the theme cannot reach | **yes** |
| `ContextualRibbonTest` | That the ribbon follows the mode: the right group for the mode already selected when the slot is built, a swap on change, and an empty *unmanaged* slot for SOAP and REST | **yes** |
| `SpringContextTest` | That the context starts, that every controller under `controller` is prototype-scoped, and that the shared services are not | no |
| `ModeGroupViewIdTest` | That every `userData` in the ribbon names a real `Mode`, that every `Mode` has a toggle, that each resolves through `ViewRegistry`, and that the toggle marked selected is the default mode — read from the FXML as XML | no |
| `FxmlSmokeTest` | That all sixteen FXML files load through the real Spring-backed controller factory | **yes** |
| `SettingsStoreTest` | The tolerance rules, against hand-written files: missing, corrupt, out of range, BOM-prefixed, non-ASCII, plus the mapping table and the data file list entry by entry (bad port, bad tail, duplicate tail, a port spelled two ways; a bad index, a blank path, a repeat), that a flag falls back rather than quietly reading as off, and that the template's keys are unknown keys | no |
| `SettingsServiceTest` | Restore of every setting global and mode-scoped, coalesced writes, the shutdown flush, that binding does not write back what it just read, that both list edits persist at all, and that the three settings which replaced opacity reach the file | no |
| `SettingsRestoreOrderTest` | That restoring *before* the controls are built is what puts stored values on them — including the negative case. Its subjects are the Preferences spinner and the Mode ribbon group, both of which read `AppState` once in `initialize()` | **yes** |
| `PreferencesSurfaceTest` | Tab by tab: that each reads live state and writes through it rather than holding a copy, that the log folder, message type and the three global settings track across both surfaces without a reopen, that the spinners' bounds come from the model's constants, that a tab's Reset touches only its own settings, that the SOAP tab refuses a malformed address or tail at entry with the reason visible, and that the dialog has one tab per scope and none for REST. Plus what is deliberately absent: the opacity slider from every surface, and the SOAP listen port | **yes** |
| `MappingTableTest` | A4: that the editor adds, edits and removes mappings, and rejects a malformed port or tail, an out-of-range port and a duplicate in either direction **at entry with the reason visible** — the half of the rules that lives in the UI and that `PortTailMappingTest` cannot reach. Plus the non-blocking Blast Port collision notice | **yes** |
| `ThemeContrastTest` | Every on-screen colour pair in **both** themes, parsed from `ribbon.css`, against 4.5:1 for text and 3:1 for focus indicators | no |
| `ThemeSwitchingTest` | That switching restyles windows that are **already open**, not only the next one created | **yes** |

### Two claims that used to travel together

This section previously said "no JavaFX toolkit required" and meant two different things by it.
They have come apart, and only one of them is a requirement:

- **No display required (NFR7) — still true, and now proven.** `FxmlSmokeTest` runs on Monocle's
  software-only Glass platform via `HeadlessToolkit`. The Linux CI runner has no display, so this
  is checked by a machine rather than asserted here.
- **No toolkit initialized — now narrowed** to the seven suites that genuinely need a scene graph:
  `FxmlSmokeTest`, `SettingsRestoreOrderTest`, `PreferencesSurfaceTest`, `MappingTableTest`,
  `ContextualRibbonTest`, `ModeViewTest` and `ThemeSwitchingTest`.
  Every other suite must stay toolkit-free, and the model layer in particular has no excuse — the
  mode enums, the mapping rules and the whole settings store are testable without one, which is why
  `PortTailMappingTest` and `StoredEnumParsingTest` are in the "no" column despite covering rules a
  UI will enforce. `AppStateTest` matters most: recording the FX thread instead of probing it (§4)
  exists precisely so that class never pulls in native libraries, and calling
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
- `mvn -Papp-image clean verify` / `-Papp-image-console` — `jpackage` app-images under `dist/`
- `scripts/build-windowed.ps1` / `build-console.ps1` — wrappers over those two commands

The console variant exists for diagnosis: the windowed launcher has no stderr, so a startup failure
there is invisible without it.

**Packaging is a Maven concern, not a script's.** The `jpackage` invocation used to live in
`scripts/Build-DataBlasterAppImage.ps1`, which had to reach back into Maven to do its job: it ran
`mvn package`, then made six `mvn help:evaluate` sub-invocations to read the version, artifactId,
JavaFX version and local repository location back out, and assembled `--module-path` by
concatenating filenames into the local repository by hand. That last part bypassed dependency
resolution entirely — it hardcoded all four JavaFX modules although the pom declares only two, and
hardcoded the `win` classifier — so it would have broken silently on a JavaFX version bump. In the
pom those values simply interpolate, and the module path comes from
`maven-dependency-plugin:copy-dependencies`.

Four things about that arrangement are load-bearing:

- **The executions are declared unconditionally but bound to `${jpackage.phase}`**, which is `none`
  in `properties` and `verify` in the two profiles. That is what lets both variants share one
  argument list while a plain `mvn verify` still packages nothing. Giving them a literal phase
  would package on every build; putting them inside the profiles would mean two copies of the
  argument list, and the console one differs from the windowed one by a single flag.
- **`copy-dependencies` must filter on the classifier.** Resolution also brings three ~300-byte
  classifier-less stubs (`javafx-graphics-21.0.2.jar` and friends) holding a manifest and nothing
  else. On a module path those become automatic modules named `javafx.graphics`/`base`/`controls`,
  colliding with the real modules in the platform jars, and jlink fails with "module found in two
  locations".
- **The extra `maven-clean-plugin` execution needs `excludeDefaultDirectories`**, or it would
  delete `target/` at `verify` — after the jar it is about to package was built. Its filesets name
  one variant, because `dist/` sits outside `target/` precisely so building one variant does not
  destroy the other's image.
- **The staged jar is matched by exact name, never a glob.** jpackage copies its whole input
  directory into the image, so only the runnable jar is staged; and adding `maven-source-plugin`
  would otherwise let `...-sources.jar` be picked as the main jar, giving an exe that fails with
  "Failed to launch JVM".

`--add-modules` is a hand-maintained list found empirically. Five of its entries were added for the
embedded Tomcat that no longer exists and are deliberately still there: a module missing from the
image fails at native launch rather than at build time, and packaging is a manual Windows step CI
never runs, so a wrong trim would stay invisible until someone ran the shipped app. Narrowing it
means building both variants and launching them. Recovery from any such failure is to run the
console variant and read the stack trace. Packaging is Windows-only; both are accepted limits.

---

## 11. Standing constraints

**The supported-branch position is deliberate, and has a date on it.** This project ran on Spring
Boot 3.5 until that branch reached open-source end of life on 30 June 2026, and moved to **4.1.1**
rather than 4.0, which reaches end of life on 31 December 2026. 4.1 is maintained through
**31 July 2027**.

That date is the constraint, not a footnote. The app-image has no update mechanism, so a fork
still on 4.1 after July 2027 has no patch path for a vulnerability in Spring itself. **A fork
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
