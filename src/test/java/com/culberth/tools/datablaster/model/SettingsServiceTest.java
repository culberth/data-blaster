package com.culberth.tools.datablaster.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The join between {@link AppState} and {@link SettingsStore}.
 *
 * <p>No JavaFX toolkit: property listeners work without one, and {@link AppState}'s thread guard is
 * satisfied by recording this thread — the same stand-in for UI start-up that {@code AppStateTest}
 * uses. The static is put back afterwards, because leaving it set is what once made the FXML suite
 * pass on Windows and fail on Linux.
 *
 * <p>Writes are asynchronous and coalesced, so every test that expects one on disk goes through
 * {@code flushOnShutdown()} rather than sleeping — which also exercises the shutdown path that
 * stops a change made just before quitting from being lost.
 */
class SettingsServiceTest {

    @TempDir
    Path directory;

    private AppState appState;
    private SettingsStore store;
    private SettingsService service;
    private Path file;

    @BeforeEach
    void setUp() {
        AppState.markFxApplicationThread();
        appState = new AppState();
        file = directory.resolve("settings.properties");
        store = new SettingsStore(file);
        service = new SettingsService(store);
    }

    @AfterEach
    void tearDown() {
        AppState.forgetFxApplicationThread();
    }

    @Test
    @DisplayName("stored values are in AppState before anything reads it")
    void storedValuesAreInAppStateBeforeAnythingReadsIt() {
        store.write(new Settings(-3.5, directory.toString(), Theme.LIGHT));

        service.bind(appState);

        assertEquals(-3.5, appState.getSimFactor(), 0.0001);
        assertEquals(directory.toString(), appState.getLogFolder().getPath());
    }

    @Test
    @DisplayName("a first run binds to defaults and writes nothing")
    void aFirstRunBindsToDefaultsAndWritesNothing() {
        service.bind(appState);

        assertEquals(Settings.DEFAULTS.simFactor(), appState.getSimFactor(), 0.0001);
        assertNull(appState.getLogFolder());
        // The restore must not look like a user edit. If bind() subscribed before applying, every
        // launch would write the file straight back — churn that is invisible until someone
        // watches the file's timestamp and wonders what is touching it.
        assertFalse(Files.exists(file), "binding alone must not create a settings file");
    }

    /**
     * The restore must not look like a user edit.
     *
     * <p>The stored value is deliberately <em>not</em> the default: restoring a default onto a
     * fresh {@link AppState} sets a property to what it already holds, which fires no change event
     * and so cannot tell the two orderings apart. Written the obvious way, with defaults, this test
     * passes whether {@code bind()} subscribes before or after it restores — verified by making
     * that change and watching it stay green.
     */
    @Test
    @DisplayName("binding does not write back what it just read")
    void bindingDoesNotWriteBackWhatItJustRead() throws IOException {
        String handWritten = "simFactor=2.0" + System.lineSeparator();
        Files.writeString(file, handWritten);

        service.bind(appState);
        service.flushOnShutdown();

        assertEquals(2.0, appState.getSimFactor(), 0.0001, "the value should have been restored");
        assertEquals(handWritten, Files.readString(file),
                "the file was rewritten during startup; bind() must apply the stored values before "
                        + "it subscribes, or every launch writes the file straight back");
    }

    @Test
    @DisplayName("changing a bound setting persists it")
    void changingABoundSettingPersistsIt() {
        service.bind(appState);

        appState.setSimFactor(1.5);
        appState.setLogFolder(new File(directory.toFile(), "logs"));
        service.flushOnShutdown();

        Settings written = store.read();
        assertEquals(1.5, written.simFactor(), 0.0001);
        assertEquals(new File(directory.toFile(), "logs").getPath(), written.logFolderPath());
    }

    @Test
    @DisplayName("the values that survive a restart are the ones that were set")
    void theValuesThatSurviveARestartAreTheOnesThatWereSet() {
        service.bind(appState);
        appState.setSimFactor(-2.0);
        service.flushOnShutdown();

        // A second launch: new state, new service, same file.
        AppState relaunched = new AppState();
        new SettingsService(new SettingsStore(file)).bind(relaunched);

        assertEquals(-2.0, relaunched.getSimFactor(), 0.0001);
    }

    /**
     * The coalescing buffer's reason for existing. A slider drag fires a change every 0.1, and the
     * value that must end up on disk is the last one, not whichever the writer happened to catch.
     */
    @Test
    @DisplayName("a burst of changes persists the last value")
    void aBurstOfChangesPersistsTheLastValue() {
        service.bind(appState);

        for (int step = 0; step <= 40; step++) {
            appState.setSimFactor(-2.0 + step * 0.1);
        }
        service.flushOnShutdown();

        assertEquals(2.0, store.read().simFactor(), 0.0001);
    }

    /**
     * Deliberate scope. Opacity is a view control reset by the button beside it, and the view id
     * points at a placeholder — see {@link Settings}.
     */
    @Test
    @DisplayName("opacity and the current view are not persisted")
    void opacityAndTheCurrentViewAreNotPersisted() throws IOException {
        service.bind(appState);

        appState.setContentOpacity(0.4);
        appState.setCurrentViewId("view3");
        service.flushOnShutdown();

        if (Files.exists(file)) {
            String contents = Files.readString(file);
            assertFalse(contents.contains("0.4"), "opacity must not reach the file: " + contents);
            assertFalse(contents.contains("view3"), "the view id must not reach the file: " + contents);
        }
    }

    @Test
    @DisplayName("a change made just before quitting is not lost")
    void aChangeMadeJustBeforeQuittingIsNotLost() {
        service.bind(appState);

        appState.setSimFactor(4.2);
        // No pause: the write is very likely still queued on the daemon writer thread, which
        // nothing would wait for on the way down.
        service.flushOnShutdown();

        assertEquals(4.2, store.read().simFactor(), 0.0001);
    }

    @Test
    @DisplayName("a corrupt file leaves the app usable at defaults")
    void aCorruptFileLeavesTheAppUsableAtDefaults() throws IOException {
        Files.writeString(file, "simFactor=definitely-not-a-number\n");

        service.bind(appState);

        assertEquals(Settings.DEFAULTS.simFactor(), appState.getSimFactor(), 0.0001);

        // And the app can still save over it, rather than being stuck reading a broken file.
        appState.setSimFactor(3.0);
        service.flushOnShutdown();
        assertEquals(3.0, store.read().simFactor(), 0.0001);
    }

    @Test
    @DisplayName("writes stop cleanly once the writer is shut down")
    void writesStopCleanlyOnceTheWriterIsShutDown() {
        service.bind(appState);
        service.flushOnShutdown();

        // A late change from an FX listener still in flight must not throw out of the listener,
        // which would surface as an unhandled exception during teardown.
        appState.setSimFactor(1.0);

        assertTrue(true, "setting a value after shutdown did not throw");
    }
}
