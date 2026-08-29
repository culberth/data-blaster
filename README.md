# Data Blaster

A JavaFX desktop tool with an Office-style ribbon, backed by a Spring Boot context.

Data Blaster runs in one of four modes — **Log**, **Message**, **SOAP**, **REST** — selected from the
ribbon, each with its own persisted configuration.

> **Status: the modes are real; they do not do anything yet.** Selecting a mode switches the view and
> the choice survives a restart, and every mode's settings persist under their own namespace in the
> settings file, and the ribbon changes with the mode you are in. What is still missing is the
> interface to some of them — Preferences is one flat pane, so SOAP's port and Log's port-to-tail
> table persist correctly with nowhere to be edited — and the four content views are still abstract
> placeholders.
>
> No mode has any behaviour: nothing reads a log, sends a message or serves SOAP. That boundary is
> deliberate and is specified in [docs/PRD.md](docs/PRD.md), which is the work in progress.

## Build and run

```bash
mvn clean package
```

```bash
mvn spring-boot:run
```

```bash
mvn test
```

The test suite is headless — 215 tests, no display required — so it runs unchanged on a build agent.
To run one class or one method:

```bash
mvn test -Dtest=SettingsStoreTest
```

```bash
mvn test -Dtest=SettingsStoreTest#aWrittenSettingIsTheSettingThatComesBack
```

## Windows packaging

`jpackage` app-images, bundling a JRE so no separate Java install is needed. Windows only, and a
manual step — deliberately not in CI.

```bash
scripts/build-windowed.ps1
```

```bash
scripts/build-console.ps1
```

The console variant exists for diagnosis: the windowed launcher has no stderr, so a start-up failure
there is otherwise invisible. Output lands in `dist/`.

## Settings

A UTF-8 properties file at the per-user config location — `%APPDATA%\DataBlaster\settings.properties`
on Windows. Plain text on purpose: "where did my setting go" should be answerable with a file
manager. Safe to delete; defaults are restored. Preferences shows the exact path.

## Documentation

| | |
|---|---|
| [docs/architecture.md](docs/architecture.md) | How the current code works, and why — most constraints trace to a specific defect |
| [docs/PRD.md](docs/PRD.md) | What is being built next: the four modes and their settings |
| [CLAUDE.md](CLAUDE.md) | Working notes for Claude Code |

## Licence

GPL-3.0. See [LICENSE](LICENSE).
