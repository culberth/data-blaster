package com.culberth.tools.datablaster.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        store.write(new Settings(
                Mode.SOAP,
                Theme.DARK,
                new Settings.LogSettings(3.5, directory.toString(),
                        List.of(PortTailMapping.of(5001, "N12345"))),
                new Settings.MessageSettings(MessageType.MESSAGE_3),
                new Settings.SoapSettings(9443)));

        service.bind(appState);

        assertSame(Mode.SOAP, appState.getCurrentMode());
        assertSame(Theme.DARK, appState.getTheme());
        assertEquals(3.5, appState.getPlaybackSpeedFactor(), 0.0001);
        assertEquals(directory.toString(), appState.getLogFolder().getPath());
        assertEquals(List.of(PortTailMapping.of(5001, "N12345")), appState.portTailMappings());
        assertSame(MessageType.MESSAGE_3, appState.getMessageType());
        assertEquals(9443, appState.getSoapPort());
    }

    @Test
    @DisplayName("a first run binds to defaults and writes nothing")
    void aFirstRunBindsToDefaultsAndWritesNothing() {
        service.bind(appState);

        assertSame(Settings.DEFAULTS.mode(), appState.getCurrentMode());
        assertEquals(Settings.PLAYBACK_SPEED_DEFAULT, appState.getPlaybackSpeedFactor(), 0.0001);
        assertNull(appState.getLogFolder());
        assertTrue(appState.portTailMappings().isEmpty());
        // The restore must not look like a user edit. If bind() subscribed before applying, every
        // launch would write the file straight back — churn that is invisible until someone
        // watches the file's timestamp and wonders what is touching it.
        assertFalse(Files.exists(file), "binding alone must not create a settings file");
    }

    /**
     * The restore must not look like a user edit.
     *
     * <p>The stored values are deliberately <em>not</em> the defaults: restoring a default onto a
     * fresh {@link AppState} sets a property to what it already holds, which fires no change event
     * and so cannot tell the two orderings apart. Written the obvious way, with defaults, this test
     * passes whether {@code bind()} subscribes before or after it restores — verified by making
     * that change and watching it stay green.
     */
    @Test
    @DisplayName("binding does not write back what it just read")
    void bindingDoesNotWriteBackWhatItJustRead() throws IOException {
        String handWritten = "log.playbackSpeedFactor=2.0" + System.lineSeparator()
                + "mode=soap" + System.lineSeparator()
                + "log.mapping.5001=N12345" + System.lineSeparator();
        Files.writeString(file, handWritten);

        service.bind(appState);
        service.flushOnShutdown();

        assertEquals(2.0, appState.getPlaybackSpeedFactor(), 0.0001,
                "the value should have been restored");
        assertEquals(handWritten, Files.readString(file),
                "the file was rewritten during startup; bind() must apply the stored values before "
                        + "it subscribes, or every launch writes the file straight back");
    }

    /**
     * The mapping table is the one bind() could plausibly get wrong on its own: restoring it means
     * writing a collection, and a collection restored after the subscription would rewrite the file
     * on every launch just as a scalar would.
     */
    @Test
    @DisplayName("restoring the mapping table does not look like a user edit either")
    void restoringTheMappingTableDoesNotLookLikeAUserEdit() throws IOException {
        String handWritten = "log.mapping.5001=N12345" + System.lineSeparator()
                + "log.mapping.5002=000042" + System.lineSeparator();
        Files.writeString(file, handWritten);

        service.bind(appState);
        service.flushOnShutdown();

        assertEquals(2, appState.portTailMappings().size());
        assertEquals(handWritten, Files.readString(file));
    }

    @Test
    @DisplayName("changing a bound setting persists it")
    void changingABoundSettingPersistsIt() {
        service.bind(appState);

        appState.setPlaybackSpeedFactor(1.5);
        appState.setLogFolder(new File(directory.toFile(), "logs"));
        service.flushOnShutdown();

        Settings written = store.read();
        assertEquals(1.5, written.log().playbackSpeedFactor(), 0.0001);
        assertEquals(new File(directory.toFile(), "logs").getPath(), written.log().folderPath());
    }

    /**
     * R12, and the defect it names. A {@code ChangeListener} on an {@code ObservableList} fires
     * only when the list object itself is replaced — which never happens, because the list is a
     * final field — so mapping edits would persist silently nowhere: no exception, no log line,
     * just a table that is empty again on the next launch.
     */
    @Test
    @DisplayName("editing the mapping table persists it")
    void editingTheMappingTablePersistsIt() {
        service.bind(appState);

        appState.addPortTailMapping(PortTailMapping.of(5001, "N12345"));
        appState.addPortTailMapping(PortTailMapping.of(5002, "000042"));
        appState.removePortTailMappingForPort(5001);
        service.flushOnShutdown();

        assertEquals(List.of(PortTailMapping.of(5002, "000042")), store.read().log().mappings(),
                "a collection needs a ListChangeListener; a ChangeListener on it never fires");
    }

    @Test
    @DisplayName("every mode's settings survive a restart")
    void everyModesSettingsSurviveARestart() {
        service.bind(appState);
        appState.setCurrentMode(Mode.MESSAGE);
        appState.setTheme(Theme.DARK);
        appState.setPlaybackSpeedFactor(0.5);
        appState.setMessageType(MessageType.MESSAGE_2);
        appState.setSoapPort(9443);
        appState.addPortTailMapping(PortTailMapping.of(5001, "N12345"));
        service.flushOnShutdown();

        // A second launch: new state, new service, same file.
        AppState relaunched = new AppState();
        new SettingsService(new SettingsStore(file)).bind(relaunched);

        assertSame(Mode.MESSAGE, relaunched.getCurrentMode());
        assertSame(Theme.DARK, relaunched.getTheme());
        assertEquals(0.5, relaunched.getPlaybackSpeedFactor(), 0.0001);
        assertSame(MessageType.MESSAGE_2, relaunched.getMessageType());
        assertEquals(9443, relaunched.getSoapPort());
        assertEquals(List.of(PortTailMapping.of(5001, "N12345")), relaunched.portTailMappings());
    }

    /**
     * The coalescing buffer's reason for existing. A control that fires on every step of a drag
     * would otherwise mean a file per step, and the value that must end up on disk is the last one,
     * not whichever the writer happened to catch.
     */
    @Test
    @DisplayName("a burst of changes persists the last value")
    void aBurstOfChangesPersistsTheLastValue() {
        service.bind(appState);

        for (int step = 0; step <= 40; step++) {
            appState.setPlaybackSpeedFactor(0.1 + step * 0.1);
        }
        service.flushOnShutdown();

        assertEquals(4.1, store.read().log().playbackSpeedFactor(), 0.0001);
    }

    /**
     * Deliberate scope. Opacity is a view control reset by the button beside it — see
     * {@link Settings}. The selected mode, which the template deliberately did not persist, now
     * does; that half is covered by {@link #everyModesSettingsSurviveARestart()}.
     */
    @Test
    @DisplayName("opacity is not persisted")
    void opacityIsNotPersisted() throws IOException {
        service.bind(appState);

        appState.setContentOpacity(0.4);
        service.flushOnShutdown();

        if (Files.exists(file)) {
            String contents = Files.readString(file);
            assertFalse(contents.contains("0.4"), "opacity must not reach the file: " + contents);
        }
    }

    @Test
    @DisplayName("a change made just before quitting is not lost")
    void aChangeMadeJustBeforeQuittingIsNotLost() {
        service.bind(appState);

        appState.setPlaybackSpeedFactor(4.2);
        // No pause: the write is very likely still queued on the daemon writer thread, which
        // nothing would wait for on the way down.
        service.flushOnShutdown();

        assertEquals(4.2, store.read().log().playbackSpeedFactor(), 0.0001);
    }

    @Test
    @DisplayName("a corrupt file leaves the app usable at defaults")
    void aCorruptFileLeavesTheAppUsableAtDefaults() throws IOException {
        Files.writeString(file, "log.playbackSpeedFactor=definitely-not-a-number\n");

        service.bind(appState);

        assertEquals(Settings.PLAYBACK_SPEED_DEFAULT, appState.getPlaybackSpeedFactor(), 0.0001);

        // And the app can still save over it, rather than being stuck reading a broken file.
        appState.setPlaybackSpeedFactor(3.0);
        service.flushOnShutdown();
        assertEquals(3.0, store.read().log().playbackSpeedFactor(), 0.0001);
    }

    /**
     * Every subscription {@code bind()} makes is one {@code flushOnShutdown()} has to undo, and the
     * mapping listener is the easiest to forget because it is registered differently from the rest.
     *
     * <p>What this can actually observe is the quiet part: a late change from an FX listener still
     * in flight must not throw out of that listener, which would surface as an unhandled exception
     * during teardown. The unregistration itself has no visible effect once the writer is shut down
     * — it matters for a service detached while the application keeps running, which is routine in
     * tests and is how a service bound to a shared {@code AppState} once kept recreating a file in
     * a temp directory JUnit was trying to delete.
     */
    @Test
    @DisplayName("writes stop cleanly once the writer is shut down")
    void writesStopCleanlyOnceTheWriterIsShutDown() {
        service.bind(appState);
        service.flushOnShutdown();

        appState.setPlaybackSpeedFactor(1.0);
        appState.setCurrentMode(Mode.REST);
        appState.addPortTailMapping(PortTailMapping.of(5001, "N12345"));

        assertTrue(true, "changing values after shutdown did not throw");
    }
}
