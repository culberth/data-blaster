# PRD — Data Blaster v1

**Product:** Data Blaster
**Repository:** `Projects/tool-boilerplate/` — an inherited directory name; see R27
**Type:** JavaFX + Spring Boot desktop tool, forked from JFXRibbon
**Created:** 2026-08-29
**Status:** v1 scope — supersedes the archetype PRD, which described a different project
**Baseline:** `main` at `9739cb3`, version `2.0.0-SNAPSHOT`

---

## 1. Problem

JFXRibbon is a working desktop shell — ribbon, view switching, persisted settings, theming, a
loopback HTTP layer — with four placeholder views behind four toggle buttons that mean nothing. It
was built to be forked, and this is the fork.

The tool being built runs in one of four **modes**: Log, Message, SOAP, REST. Each mode needs its own
configuration, and those configurations have nothing in common — Log needs a folder, a factor, and a
table of port-to-tail-number mappings; Message needs a single choice; SOAP needs a port; REST is not
designed yet.

The existing settings machinery does not support this. `Settings` is a flat record of three scalars,
`SettingsStore` reads three fixed keys, and `AppState` holds only scalar properties — there is no
collection anywhere in it. The Preferences dialog is a single flat pane with no notion of which mode
a setting belongs to. Every one of those has to grow a dimension it does not currently have.

**This PRD covers making the modes configurable. It does not cover making them do anything.**

## 2. Users

Bo, alone. No team, no external consumers, no onboarding burden beyond what future-Bo needs.

The inherited codebase carries a second, weaker audience — it was written as a readable reference,
which is why `docs/architecture.md` explains rationale rather than structure. That property is worth
keeping, but it is not a requirement here and should not be allowed to expand scope.

## 3. Success criteria

| # | Criterion | How it's checked | By when |
|---|---|---|---|
| S1 | The four toggles select Log, Message, SOAP and REST, and the selected mode survives a restart | `ModeGroupViewIdTest` (every toggle's `userData` resolves to a `Mode`), plus a `SettingsService` round-trip test | v1 |
| S2 | Every mode's settings can be viewed and changed from Preferences, including while running in a different mode | `PreferencesSurfaceTest`, extended per tab | v1 |
| S3 | All settings survive a restart, and a hand-broken settings file degrades per value rather than wholesale | `SettingsStoreTest` against hand-written files: missing, corrupt, out-of-range, duplicate, BOM-prefixed | v1 |
| S4 | A port-to-tail mapping cannot be saved that violates the format or uniqueness rules | `PortTailMappingTest` — toolkit-free, at the model layer | v1 |
| S5 | The suite still runs headless | Existing CI on Linux and Windows; no new test needs a display. The other half of this criterion — that the app still launches when the HTTP port is taken — retired with the HTTP layer (Q10): there is no port to take | v1 |

S3 and S4 are separate on purpose. S4 is about what the UI will accept; S3 is about what the reader
will tolerate from a file someone edited by hand. The store must not assume the UI wrote the file —
that assumption is what NFR1 and the whole tolerant-read design exist to reject.

## 4. Scope

### 4.1 Mode becomes a first-class concept

A `Mode` enum in `model` — `LOG`, `MESSAGE`, `SOAP`, `REST` — replaces the free-string
`currentViewId`. `ViewRegistry` is keyed by `Mode`, the toggles' `userData` carries the constant
name, and `AppState.currentMode` is an `ObjectProperty<Mode>`.

**The selected mode now persists.** `Settings` currently excludes `currentViewId`, and its Javadoc
gives the reason: *"the four views are placeholders; restoring one is not behaviour a template should
model for a fork whose views will mean something."* They now mean something. The exclusion was
correct for the template and is wrong for the fork, so it reverses — and the Javadoc explaining the
old choice must be replaced rather than left to contradict the code.

### 4.2 Settings become mode-scoped

| Mode | Setting | Type | Default |
|---|---|---|---|
| Log | Log folder | directory path, may be unset | none |
| Log | Playback Speed Factor | double multiplier of real time, 0.1–10.0 | 1.0 |
| Log | Port → tail number mappings | set of (port, 6-character tail) pairs | empty |
| Message | Message Type | `MESSAGE_1` \| `MESSAGE_2` \| `MESSAGE_3` | `MESSAGE_1` |
| SOAP | Port | int, 1–65535 | 8081 |
| REST | — | — | — |
| *(global)* | Theme | `LIGHT` \| `DARK` | `LIGHT` |
| *(global)* | Current mode | `Mode` | `LOG` |

Playback Speed Factor and the log folder are **not** global any more. They are Log-mode settings that
happen to have existed before the modes did.

**Playback Speed Factor is the inherited Sim Factor slider, renamed and re-specified.** It is a
multiplier on real time: `1.0` is real time, `2.0` is twice as fast, `0.5` is half speed.

The `double` type survives that change; nothing else about the control does. The −5.0…5.0 range has
no meaning as a multiplier — `0.0` would mean frozen and negatives would mean reverse, and neither is
a speed this setting offers. So the range becomes 0.1–10.0 and **the default changes from 0.0 to
1.0**.

That default change is a trap the key rename (§7) happens to defuse. Had the key stayed `simFactor`,
an existing settings file holding `simFactor=0.0` would restore as a perfectly well-formed `0.0` —
playback frozen, no warning, nothing out of range, because `0.0` *was* the valid default under the
old semantics. Because the key is now `log.playbackSpeedFactor`, the old spelling is simply an
unknown key and the new default applies. The same reasoning is why the range is validated on read
rather than clamped: clamping `0.0` up to `0.1` would start playback crawling instead of saying why.

`8081` is the SOAP default specifically because the embedded HTTP layer binds `8080`; defaulting the
two to the same port would make the out-of-box state a conflict. Confirmed — see Q3.

**Q10 has since removed that `8080` layer**, so the conflict this default avoided no longer exists.
The default stands anyway: it is already a stored value in existing files, `8080` remains the
likeliest port for whatever else a developer is running, and moving a configured port for a reason
that no longer applies is churn.

### 4.3 Preferences becomes tabbed

A `TabPane` — General, Log, Message, SOAP — with each tab its own FXML and its own prototype-scoped
controller, matching the pattern the ribbon groups already use. General holds Theme and the settings
file path.

Rejected alternative: showing only the current mode's settings. It makes configuring Message mode
while running in Log mode impossible, and it makes the dialog's contents change shape under the user
for a reason that is not their fault.

### 4.4 The fork is renamed to Data Blaster

The inherited name reaches further than a window title: the package (`com.example.jfxribbon`), the
Maven coordinates, the main class, the settings **directory**, the HTTP status payload, the About
dialog, and the `jpackage` script and its output paths.

| Surface | From | To |
|---|---|---|
| Maven groupId | `com.example` | `com.culberth.tools` |
| Maven artifactId | `tool-boilerplate` | `data-blaster` |
| Package | `com.example.jfxribbon` | `com.culberth.tools.datablaster` |
| Main / application class | `JFXRibbonApplication` | `DataBlasterApplication` |
| Window title | `JFXRibbon` | `Data Blaster` |
| Settings directory | `%APPDATA%\JFXRibbon\` | `%APPDATA%\DataBlaster\` |
| Status payload `app` field | `JFXRibbon` | `Data Blaster` |
| jpackage script + output | `Build-JFXRibbonAppImage.ps1`, `dist\…\JFXRibbon.exe` | `Build-DataBlasterAppImage.ps1`, `dist\…\DataBlaster.exe` |

**The display name has a space; the directory and executable names do not.** A path with a space in
it is legal and works, but it is an irritation in every script, shortcut and command line that ever
touches it, and it is far cheaper to decide now than to migrate a settings directory later.

#### The trap: "JFXRibbon" goes, "ribbon" stays

A find-and-replace on `ribbon` would be a serious mistake. The application still *has* a ribbon, and
that vocabulary is correct and load-bearing throughout:

- `src/main/resources/css/ribbon.css`, and its `-jfx-*` design tokens — `ThemeContrastTest` parses
  this file by name
- `com.example.jfxribbon.controller.ribbon` → `com.culberth.tools.datablaster.controller.ribbon`
- `src/main/resources/fxml/ribbon/`, the `ribbon-group`, `ribbon-button`, `ribbon-readout` style
  classes, and every `ribbon-*` identifier in the FXML

Only the product name `JFXRibbon` (and its lowercase package form `jfxribbon`) is being replaced.
The `-jfx-` CSS token prefix stays: it reads as JavaFX, not as the old product, and renaming it is
churn that `ThemeContrastTest` would have to be rewritten to follow.

#### Sequencing

**The rename lands as its own commit, before any mode work.** It is a large, mechanical, almost
entirely uninteresting diff, and interleaving it with the model and Preferences changes would bury
every decision in this document inside a wall of import statements.

### 4.5 Everything the modes actually do

Out. See §5.

## 5. Non-goals

Stated explicitly, because the previous PRD carried "undefined scope boundary" as a live risk with
nothing to push back against.

| Non-goal | Why |
|---|---|
| **Implementing any mode's behaviour** — no log reading or replay, no message sending, no SOAP endpoint, no REST anything | v1 is the configuration surface. A mode that can be configured and does nothing is a complete, testable increment; a half-implemented Log mode is not |
| **REST mode settings** | Explicitly deferred by the requester. The toggle ships (§6, R14) but has no settings and no Preferences tab |
| **A contextual ribbon** that swaps groups with the selected mode | Desirable, and the architecture supports it, but it is a mechanism built before knowing which controls deserve quick access. Carried as R16 at *Should*, not *Must* |
| **Exposing mode settings over HTTP** | Moot since Q10: there is no HTTP layer to expose them through. It was a non-goal on its own merits first — the API was loopback-bound but unauthenticated, and loopback is a host boundary rather than a user boundary. Tail numbers are the most identifying data this tool will hold. See D6 |
| **Migrating existing settings files** | `2.0.0-SNAPSHOT` is unreleased and single-user. Old keys simply go missing and fall back silently, which the per-key tolerant read already does correctly. See D5 |
| **Multi-profile / named configuration sets** | One configuration per mode. If several sets are ever wanted, that is a second dimension on the file format and deserves its own decision |
| **Validating that a configured port is actually free** | Bind-time concern, and the mode behaviour that would bind it is out of scope |

## 6. Requirements

### Model

| # | Requirement | Traces to | Priority |
|---|---|---|---|
| R1 | `Mode` enum (`LOG`, `MESSAGE`, `SOAP`, `REST`) in `model`, with a `fromStoredName(String, Mode)` tolerant parse mirroring `Theme` | S1, S3 | Must |
| R2 | `MessageType` enum (`MESSAGE_1`, `MESSAGE_2`, `MESSAGE_3`) in `model`, with the same tolerant parse. Choice controls populate from `values()` so adding a constant is a one-line change | S2, S3 | Must |
| R3 | `PortTailMapping` record with a validating factory: port 1–65535, tail matching `[A-Z0-9]{6}` after trim and upper-case (§7). **The tail is a `String`** — it may be all digits, and an integer type would drop the leading zeros in `000042` and reject `N12345` outright | S4 | Must |
| R4 | Tail numbers and ports are each unique across the mapping set — one tail per port and one port per tail. Tails are normalised (trimmed, upper-cased) before the uniqueness check, so `n12345` and `N12345` collide rather than both being accepted | S4 | Must |
| R5 | `AppState` gains `currentMode`, `messageType`, `soapPort`, and an observable collection of mappings. Every mutator keeps the FX-thread guard | S1, S2 | Must |
| R6 | The mapping collection is exposed **unmodifiable** (`FXCollections.unmodifiableObservable…`). Returning the live collection reopens the "no second, unguarded way in" hole that the read-only property accessors exist to close | S2 | Must |
| R7 | `Settings` becomes a record of per-mode records. Any collection it carries is defensively copied and unmodifiable — a record holding a mutable `Map` is not immutable, and `Settings` is handed to the writer thread on the strength of being immutable | S3 | Must |
| R24 | Playback Speed Factor is a **double multiplier**, valid range 0.1–10.0, default 1.0. A stored value outside the range falls back to 1.0 and is logged; `0.0` and negatives are rejected rather than clamped, since both name a behaviour (frozen, reverse) the setting does not offer | S3 | Must |
| R25 | `Settings.SIM_FACTOR_MIN`/`MAX` are renamed and re-valued to 0.1/10.0, not re-pointed in place — the constants are referenced by both the store's range check and the FXML slider bounds, and leaving a stale name on a changed meaning is how the two drift apart | S3 | Must |
| R26 | The store validates the **range**; the control chooses the **granularity**. A typed value anywhere in 0.1–10.0 is accepted, so a convenient step size never silently becomes a validation rule (0.25× is representable even if the stepper moves by 0.1) | S3 | Should |

### Persistence

| # | Requirement | Traces to | Priority |
|---|---|---|---|
| R8 | Settings keys are namespaced by mode: `log.*`, `message.*`, `soap.*`; `theme` and `mode` stay unprefixed | S3 | Must |
| R9 | Mappings are stored one key per port — `log.mapping.<port>=<tail>` — so port uniqueness is enforced by the file format rather than by code that has to remember to check | S3, S4 | Must |
| R10 | Mapping entries are read tolerantly and **individually**: a malformed port, a malformed tail, or a tail duplicating one already read is logged and dropped, and the remaining entries load | S3 | Must |
| R11 | `SettingsStore.read()` still does not throw, and a missing file is still a silent first run | S3, S5 | Must |
| R12 | `SettingsService.bind()` subscribes to the new properties **and** to the mapping collection. A collection needs a `ListChangeListener`/`MapChangeListener`; a `ChangeListener` on it fires only on wholesale replacement, so edits would persist silently nowhere | S3 | Must |
| R13 | `flushOnShutdown()` unregisters every listener it registered, including the collection listener | S3 | Must |

### UI

| # | Requirement | Traces to | Priority |
|---|---|---|---|
| R14 | The four Mode toggles read Log, Message, SOAP, REST, each carrying its `Mode` constant name in `userData`. REST selects a view that says plainly that it is not implemented — a disabled toggle with no explanation is worse than an honest placeholder | S1 | Must |
| R15 | Preferences is a `TabPane` (General, Log, Message, SOAP), one FXML and one prototype controller per tab | S2 | Must |
| R16 | The ribbon shows a group whose controls follow the selected mode | — | Should |
| R17 | The mapping editor is a `TableView` with Port and Tail No. columns and Add/Remove, rejecting invalid or duplicate entries at entry with the reason shown, not on close | S4 | Must |
| R18 | Edits still apply immediately and the button still says Close. Reset to defaults is **per tab**, and the Log tab's reset clears the mapping table — which is destructive enough to confirm first | S2 | Must |
| R19 | Preferences flags a mapping port that collides with the SOAP port, without blocking it. The second half of this — flagging a collision with the HTTP layer's 8080 — is dropped: Q10 removed that layer, so there is no longer anything bound at 8080 to collide with | — | Should |
| R20 | Every new control keeps the inherited accessibility pattern: a caption `Label` with `labelFor` set in `initialize()`, mnemonics, and `accessibleText` on unlabelled controls — but never `accessibleText` on a read-out `Label`, which would hide the value it exists to announce | S2 | Should |

### Fork hygiene

| # | Requirement | Traces to | Priority |
|---|---|---|---|
| R21 | `docs/architecture.md` is updated in the same change as the code it describes — §4 (state), §6 (the ribbon), §8a (settings), §9 (tests). It is currently accurate and that is worth more than it looks | — | Must |
| R22 | The FXML and controller counts asserted in `FxmlSmokeTest` / `SpringContextTest` are updated to match | S5 | Must |
| R23 | `docs/PRD.md` (this file) replaces the archetype PRD, and `memory.md` records that the archetype idea was abandoned rather than completed | — | Must |
| R27 | The product is renamed to Data Blaster across every surface in §4.4, as a standalone commit preceding the mode work. The repository directory and its GitHub remote are renamed to `data-blaster` — both are manual steps outside the build | S5 | Must |
| R28 | The rename does not touch the `ribbon` vocabulary: `ribbon.css`, the `-jfx-*` tokens, `controller.ribbon`, `fxml/ribbon/`, and the `ribbon-*` style classes all stay as they are | S5 | Must |
| R29 | The version resets to `1.0.0-SNAPSHOT`. `2.0.0-SNAPSHOT` numbered JFXRibbon's Spring Boot 4 migration and means nothing for a differently-named artifact that has never shipped | — | Should |

## 7. Interfaces and data

### Settings file

Unchanged format, renamed location — a UTF-8 properties file at the platform's per-user config path,
now `%APPDATA%\DataBlaster\settings.properties` on Windows, read with a leading BOM skipped.

The rename orphans any existing `JFXRibbon\settings.properties`. That costs nothing today (nothing
has shipped, and the file holds three inherited demo values) and would cost a migration path later,
which is the entire reason Q1 was marked blocking.

```properties
theme=DARK
mode=LOG

log.playbackSpeedFactor=1.5
log.folder=C:\\logs\\capture
log.mapping.5001=N12345
log.mapping.5002=N7377X
log.mapping.5003=123456

message.type=MESSAGE_2

soap.port=8081
```

Three properties of this shape are load-bearing:

- **The port is the key.** Two mappings cannot claim the same port, because a properties file cannot
  hold the same key twice. The last one written wins, silently and predictably, and no uniqueness
  check has to be remembered on the read path. Tail uniqueness still needs checking in code (R10) —
  it is the direction the format cannot enforce.
- **Keys are namespaced, not nested.** `Properties` is flat; `log.mapping.5001` is one key with dots
  in it, and reading the set means filtering `stringPropertyNames()` by prefix. That is a different
  read shape from the fixed-key getters and needs its own tests.
- **`theme` keeps its unprefixed key** so it reads unchanged from an existing file. `simFactor` and
  `logFolder` move under `log.` and their old spellings simply become unknown keys, which the store
  already ignores. For `simFactor` that is not merely tidy — see §4.2 on why reusing the key would
  have restored a frozen playback speed from an old file without tripping any validation.

### What counts as a tail number

**Exactly six alphanumeric characters, upper-cased.** No fixed prefix — it may be a registration
like `N12345`, or all digits like `123456`.

```
trim, upper-case, then match  [A-Z0-9]{6}
```

The three things this settles, each having been ambiguous at some point:

- **The length is exact, not a maximum.** Six characters is the rule, so `N123` is rejected. That is
  a stronger validator than a variable-length registration rule, and stronger is what catches typos.
- **The first character is unconstrained.** It may be a letter or a digit; `N` has no special status.
- **It is not a number**, despite being allowed to look like one. It stays a `String` — an integer
  type turns `000042` into `42` on the first round-trip, and cannot hold `N12345` at all.

**This rejects hyphenated foreign registrations** — `G-ABCD` and `C-FABC` are six characters only if
the hyphen counts, and it does not. That follows from what was specified rather than being a
deliberate exclusion of non-US aircraft, and if such tails do turn up it is one character in the
pattern (`[A-Z0-9-]{6}`) plus a test. Flagged here rather than pre-emptively allowed, because a
character class that accepts `------` is not a validator.

### `AppState` shape after this change

```
currentMode       ObjectProperty<Mode>         persisted
theme             ObjectProperty<Theme>        persisted
contentOpacity    DoubleProperty               not persisted

playbackSpeed     DoubleProperty               persisted   (Log)
logFolder         ObjectProperty<File>         persisted   (Log)
portTailMappings  ObservableList               persisted   (Log)

messageType       ObjectProperty<MessageType>  persisted   (Message)
soapPort          IntegerProperty              persisted   (SOAP)
```

The `Snapshot` column this table originally carried is gone with the record itself — see Q10. Every
value above is now reachable only from the FX thread, which is what D6 was protecting a subset of.

Whether `AppState` grows flat fields or nested per-mode holders is an implementation choice, not a
product one — but the read-only accessor contract and the FX-thread guard apply either way, and for
the mapping collection that means an unmodifiable view rather than merely a read-only property type.

### HTTP layer

**None.** Q10 removed it: no `web/` package, no embedded Tomcat, no bound port, and no
`spring-boot-starter-webmvc` dependency. Spring remains for dependency injection and the bean
lifecycle.

The `/api/status` endpoint, its `LoopbackHostFilter`, and the rename of the payload's `app` field
that §4.4 specified are all void. When SOAP mode brings a server, it starts from that mode's
configured port and inherits none of this by default.

## 8. Constraints

Everything in `docs/architecture.md` §11 still applies. The ones this change is most likely to
violate:

| Constraint | Hard / Soft | Source |
|---|---|---|
| Writes to `AppState` happen on the FX thread; off-thread readers use `snapshot()` | Hard | architecture.md §4 |
| Property and collection accessors are read-only — no second way in | Hard | architecture.md §4 |
| FXML controllers are prototype-scoped; subscriptions to `AppState` from them are weak | Hard | architecture.md §4, §5 |
| The test suite runs headless; only scene-graph tests initialise the toolkit | Hard | architecture.md §9 |
| `ribbon.css` uses design tokens, never hex literals — `ThemeContrastTest` parses it | Hard | memory.md |
| Settings restore happens before the shell loads, and restore-then-subscribe is one call | Hard | architecture.md §8a |
| `SettingsStore.read()` never throws | Hard | architecture.md §8a |
| Runs on Windows; `jpackage` packaging is a manual Windows step | Hard | Stated |
| Spring Boot 4.1 is maintained to 31 July 2027 — a fork that ships must plan to move again | Hard | architecture.md §11 |

## 9. Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-1 | **The collection breaks an invariant the scalars never tested.** Every existing rule — read-only accessors, FX-thread writes, persist-on-change — was written for scalar properties. An `ObservableList` handed out live is mutable, and a `ChangeListener` on it does not fire for element edits | High. Both are the default outcome of the obvious implementation | Silent: settings that do not save, and state mutated off-thread past the guard | R6 and R12, each with a test that fails if the shortcut is taken |
| R-2 | **The Playback Speed control is inherited from a ±5 slider and needs rebuilding, not relabelling.** A linear 0.1–10.0 slider puts `1.0` — the default, and the value a user returns to most — at 9% of the track, with the entire slow-motion range squeezed left of it and 90% of the travel spent above real time | Medium | An unusable control for the one value it exists to set | D11 |
| R-7 | **A transposed character still passes.** `[A-Z0-9]{6}` is as tight as a format rule can get here — `N13245` is as well-formed as `N12345` — so a typo produces a mapping that silently belongs to no aircraft | Medium | Data attributed to the wrong tail, or to none | Not mitigable by validation. The only real defence is checking against a known fleet roster, which v1 has no source for; noted rather than solved |
| R-3 | **Mode settings drift from mode behaviour.** v1 defines what is configurable with nothing consuming it, so nothing proves the settings are the right ones | Medium | Rework when Log mode is built | Accepted. The alternative is designing the config against imagined behaviour, which is not obviously better |
| R-4 | **`architecture.md` goes stale.** It is unusually accurate and its value is entirely in that | Medium | The one document worth reading stops being trustworthy | R21 — same change, not a follow-up |
| R-5 | **Tail numbers are PII-adjacent** and land in a plaintext file next to a log path containing the OS username | Reduced by Q10: the unauthenticated loopback API that was the exposure route is gone, leaving only file-system access, which is the same boundary the rest of the user's profile sits behind | Disclosure to anything that can read the settings file | Was D6. Now structural — nothing outside the FX thread can read this state at all. The rule to carry forward is that mappings do not go into a server's projection by default when SOAP mode adds one |
| R-6 | **The rename is a find-and-replace waiting to go wrong.** `ribbon` is both half the old product name and the correct word for a UI component this app still has, and the package form is lowercase `jfxribbon` while the display form is not | Medium | A broken stylesheet reference or a mangled style class, found at runtime rather than at compile time — `ribbon.css` is loaded by name and `ThemeContrastTest` parses it by name | R28 names exactly what must not move; R27 keeps the rename in its own commit so the diff is reviewable |

## 10. Acceptance

- [ ] **A1** — `mvn verify` passes on Linux and Windows, no display required *(passing on Windows; Linux is CI's to confirm)*
- [x] **A2** — The four toggles read Log, Message, SOAP, REST; selecting one switches the view; the choice survives a restart
- [ ] **A3** — Preferences opens with General, Log, Message and SOAP tabs; each mode's settings are editable regardless of which mode is selected
- [ ] **A4** — A port-to-tail mapping can be added, edited and removed; invalid ports, malformed tails, and duplicates in either direction are rejected at entry with the reason visible
- [x] **A5** — Every setting in the §4.2 table survives a restart
- [x] **A6** — A hand-broken settings file — one bad mapping, one bad scalar, a duplicate tail, a BOM — loads everything else and logs what it dropped
- [ ] **A7** — Reset to defaults on each tab restores that tab's values and nothing else; the Log tab confirms before clearing mappings
- [x] **A8** — `docs/architecture.md` describes the code as it now stands
- [x] **A9** — Nothing outside `docs/` mentions JFXRibbon: not the package, window title, About dialog, settings directory or jpackage output. (The status payload dropped off this list with the HTTP layer — Q10.) Remaining mentions in `README.md`, `CLAUDE.md`, `memory.md` and a `pom.xml` comment are deliberate statements of provenance. The `ribbon` UI vocabulary (R28) is untouched and the app still themes correctly, which is what proves `ribbon.css` survived the rename

## 11. Open questions

| #   | Question                                                                                                                                                                                                                                                                                                                                                                                                                                 | Owner | Blocking?                                                        |
| --- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----- | ---------------------------------------------------------------- |
| Q1  | ~~What is this tool called?~~ **Answered 2026-08-29: Data Blaster.** Surfaces and sequencing in §4.4, requirements R27–R29                                                                                                                                                                                                                                                                                                               | —     | Closed                                                           |
| Q9  | ~~Confirm the `groupId`.~~ **Answered 2026-08-29:** `com.culberth.tools`, with the package `com.culberth.tools.datablaster` | — | Closed |
| Q2  | ~~What does Sim Factor mean in Log mode?~~ **Answered 2026-08-29:** it is Playback Speed Factor, a factor by which to speed up or slow down relative to real time. Range, default and control follow in D11                                                                                                                                                                                                                              | —     | Closed                                                           |
| Q3  | ~~Confirm 8081 as the SOAP default.~~ **Answered 2026-08-29: accepted**                                                                                                                                                                                                                                                                                                                                                                  | —     | Closed                                                           |
| Q4  | ~~What is a tail number?~~ **Answered 2026-08-29:** exactly six alphanumeric characters, no fixed prefix — `N12345` or `123456` both valid. Rule and consequences in §7                                                                                                                                                                                                                                                                  | —     | Closed                                                           |
| Q8  | ~~Confirm the 0.1–10.0 playback range.~~ **Answered 2026-08-29: accepted**                                                                                                                                                                                                                                                                                                                                                               | —     | Closed                                                           |
| Q10 | ~~Should the loopback HTTP layer stay at all?~~ **Answered 2026-08-29: no.** Removed in full — `web/`, its two test classes, the port-conflict fallback in `init()`, the `spring-boot-starter-webmvc` dependency and the `web-application-type=none` override on five test classes. `AppState.Snapshot` went with it: its only reader was the HTTP layer, so it was dead code maintained on every write. SOAP mode starts from its own configured port; architecture.md §3, §4 and §8 keep the reasoning that outlived the code | — | Closed |
| Q5  | ~~Is one mapping set global, or one per something else?~~ **Answered 2026-08-29: global.** One mapping set, as §5 assumed. A second dimension on the file format would need its own decision | — | Closed |
| Q6  | ~~Does REST ship as a visible placeholder toggle (R14) or not at all in v1?~~ **Answered 2026-08-29: it ships.** The toggle is live and labelled; the view behind it still has to say plainly that it is not implemented | — | Closed |
| Q7  | R16 (contextual ribbon) is *Should*. In or out for v1?                                                                                                                                                                                                                                                                                                                                                                                   | Bo    | No                                                               |

## 12. Decision log

| # | Decision | Alternatives considered | Trade-off accepted |
|---|---|---|---|
| D1 | Fork JFXRibbon rather than start fresh or generate from an archetype | The archetype project described by the previous PRD; a new empty project | The shell, persistence, theming, headless test harness and error handling already exist and are reasoned about. The cost is inheriting a name and a set of placeholders that all need renaming |
| D2 | `Mode` as an enum replacing the free-string view id | Keeping `String` ids; a separate mode concept alongside view ids | A closed set the compiler checks, and one concept instead of two that would need keeping in step. Costs a small change to `ViewRegistry` and the FXML `userData` test |
| D3 | The selected mode persists, reversing `Settings`' documented exclusion | Leaving it unpersisted | The stated reason for the exclusion — placeholder views — no longer holds. The Javadoc must be rewritten, not just contradicted |
| D4 | Keep the properties file; namespace keys by mode | JSON or YAML; a nested format | The store's whole rationale is a file a person can open, edit and delete. A flat namespaced file keeps that and keeps per-key tolerant reads. Costs an awkward prefix-scan for the mapping set |
| D5 | Port as the mapping key (`log.mapping.<port>`) | An indexed list (`log.mapping.1.port` / `.tail`); one delimited value | Port uniqueness becomes a property of the format instead of a check someone has to remember. Costs the ability to store a mapping with no port, which is not a thing |
| D6 | ~~Mappings, message type and SOAP port stay out of `Snapshot`~~ **Superseded by Q10.** `Snapshot` was the boundary to an unauthenticated loopback API, and keeping it narrow meant a future endpoint could not leak tail numbers by accident. With the API removed the record had no reader at all, so it went too. The reasoning is preserved in architecture.md §4 for whoever rebuilds the projection when SOAP mode needs one — the narrowness was the point, not the record | Widening `Snapshot` to the full configuration; keeping the record with no reader | The decision held for as long as the thing it guarded existed |
| D7 | Preferences as tabs, all modes always reachable | Showing only the current mode's settings; one long scrolling pane | Configuring a mode you are not running is the normal case, not the exception. Costs four FXML files and four controllers where there was one |
| D8 | Per-tab Reset to defaults, with confirmation on the Log tab | One global reset; no reset | A global reset that silently clears a mapping table someone typed by hand is a different act from resetting a slider. Costs a confirmation step |
| D9 | v1 configures the modes and implements none of them | Building Log mode alongside its settings | A shippable, testable increment with a defensible boundary — which the previous PRD explicitly lacked and carried as a risk. Costs the risk that the settings turn out wrong when behaviour arrives (R-3) |
| D10 | The tail number is a `String`, normalised to upper case, exactly `[A-Z0-9]{6}` | An `int`; a variable-length registration rule; allowing hyphens | Tails may be all digits *or* contain letters, so no numeric type works — and `000042` must survive a round-trip. Fixing the length at six makes the validator as strict as the format allows, which is where its typo-catching value is. Costs hyphenated foreign registrations, reversible in one character |
| D11 | Playback Speed Factor is a multiplier (`1.0` = real time), edited with a `Spinner<Double>` in Preferences; a log-scaled slider only if R16 puts it in the ribbon | A linear slider; discrete steps (0.25/0.5/1/2/4/8); an integer percent | A spinner takes an exact value without the log/linear problem, which suits a setting configured once rather than scrubbed live. A linear slider strands `1.0` at 9% of its travel (R-2); discrete steps foreclose values before anyone knows which are wanted. Costs the drag-to-adjust feel the inherited slider had |
| D12 | The playback range is validated on read and **not clamped** | Clamping out-of-range values into 0.1–10.0 | Clamping `0.0` to `0.1` starts playback crawling and reports nothing; falling back to `1.0` with a log line says what happened. Consistent with how the store already treats an out-of-range value as different from an unparsable one |
| D13 | The product is Data Blaster; the rename lands as its own commit before the mode work | Renaming alongside the feature work; deferring the rename until v1 ships | The rename is a large mechanical diff touching almost every file. Keeping it separate leaves the mode commits reviewable. Deferring it would mean shipping a settings directory named after the template and then needing a migration |
| D14 | Display name `Data Blaster`; directory and executable `DataBlaster`, package `datablaster` | A space everywhere; a hyphen (`data-blaster`) everywhere | A space in `%APPDATA%` paths and executable names is legal but quoted-or-broken in every script that touches it. The hyphen form is kept for the Maven artifactId, where it is conventional |
| D15 | Version resets to `1.0.0-SNAPSHOT` | Continuing from `2.0.0-SNAPSHOT` | The `2.0.0` bump numbered JFXRibbon's Spring Boot 4 migration, which is meaningless for a renamed artifact that has never been released. Costs a discontinuity with the inherited git history, which the rename commit already makes obvious |
