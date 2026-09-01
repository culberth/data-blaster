package com.culberth.tools.datablaster.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.culberth.tools.datablaster.HeadlessToolkit;
import com.culberth.tools.datablaster.ViewLoader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * That restoring settings <em>before</em> the controls are built is what puts the stored values in
 * front of the user — the claim {@link SettingsService#bind} and {@code DataBlasterApplication.start}
 * both make in comments, checked against the real controllers rather than asserted.
 *
 * <p>Controls read their starting values from {@link AppState} in their {@code initialize()} methods
 * and never re-read them. So the ordering is not a matter of taste: get it wrong and the controls
 * show defaults while the state says something else, with nothing failing and nothing logged.
 *
 * <p><strong>The subjects are the Preferences Log tab's spinner and the Mode ribbon group.</strong>
 * The template used the Appearance group's Sim Factor slider, which no longer exists — Playback
 * Speed Factor moved to Preferences, and the Appearance group itself became the Global group. The
 * Mode group is the replacement on the ribbon side: it reads the restored mode once in
 * {@code initialize()} and selects the matching toggle, which is exactly the read-once shape this
 * test is about.
 *
 * <p><strong>The groups are loaded directly rather than through the shell.</strong>
 * {@code main.fxml} puts them inside a {@code TabPane}, whose skin does not build tab content until
 * the tab is shown — so a {@code lookup} on an unshown shell finds nothing, and a test written that
 * way would fail for a reason that has nothing to do with settings. That the shell includes these
 * groups correctly is {@code FxmlSmokeTest}'s job.
 */
@SpringBootTest
class SettingsRestoreOrderTest {

    private static final String LOG_TAB = "/fxml/preferences/log-tab.fxml";
    private static final String MODE_GROUP = "/fxml/ribbon/mode-group.fxml";
    private static final String LOG_GROUP = "/fxml/ribbon/log-group.fxml";

    /** Deliberately neither the default nor a value any control starts at. */
    private static final double STORED_PLAYBACK_SPEED = 3.5;

    /** Deliberately not the default mode, for the same reason. */
    private static final Mode STORED_MODE = Mode.SOAP;

    @TempDir
    Path directory;

    @Autowired
    private ViewLoader viewLoader;

    /**
     * The context's own singleton — the same instance Spring injects into the controllers that
     * {@code viewLoader} is about to create. A fresh {@code AppState} here would be invisible to
     * them and the test would prove nothing.
     */
    @Autowired
    private AppState appState;

    @BeforeAll
    static void startToolkit() {
        HeadlessToolkit.start();
        HeadlessToolkit.onFxThread(AppState::markFxApplicationThread);
    }

    /** Every service this test binds, so none is left listening to the shared {@link AppState}. */
    private final List<SettingsService> bound = new ArrayList<>();

    /**
     * {@link AppState} is an application-lifetime singleton and this test writes to it, so it is
     * put back. Leaving state behind in a shared context is the same class of bug as leaving the
     * recorded FX thread behind: it makes another test's result depend on this one having run.
     */
    @AfterEach
    void resetSharedState() {
        // Detach before resetting, or the reset below looks like a user edit to a service still
        // listening — which schedules a write into a @TempDir JUnit is about to delete.
        bound.forEach(SettingsService::flushOnShutdown);
        bound.clear();

        HeadlessToolkit.onFxThread(() -> {
            appState.setCurrentMode(Settings.DEFAULTS.mode());
            appState.setPlaybackSpeedFactor(Settings.PLAYBACK_SPEED_DEFAULT);
            appState.setLogFolder(null);
            appState.setPortTailMappings(List.of());
        });
    }

    private SettingsService serviceStoring(String contents) throws IOException {
        Path file = directory.resolve("settings.properties");
        Files.writeString(file, contents);
        return track(new SettingsService(new SettingsStore(file)));
    }

    private SettingsService track(SettingsService service) {
        bound.add(service);
        return service;
    }

    private static double playbackSpeedOf(Parent logTab) {
        @SuppressWarnings("unchecked")
        Spinner<Double> spinner = (Spinner<Double>) logTab.lookup("#playbackSpeedSpinner");
        assertNotNull(spinner, "the Playback Speed spinner should be in the tab's node tree");
        return spinner.getValue();
    }

    private static Mode selectedModeOf(Parent modeGroup) {
        for (Node node : modeGroup.lookupAll(".ribbon-button")) {
            if (node instanceof ToggleButton button && button.isSelected()) {
                return Mode.valueOf(button.getUserData().toString());
            }
        }
        throw new AssertionError("no Mode toggle is selected");
    }

    @Test
    @DisplayName("restoring before the controls are built puts stored values on them")
    void restoringBeforeTheControlsAreBuiltPutsStoredValuesOnThem() throws IOException {
        String storedFolder = directory.resolve("logs").toString();
        SettingsService service = serviceStoring(
                "log.playbackSpeedFactor=" + STORED_PLAYBACK_SPEED + "\n"
                        + "mode=" + STORED_MODE.storedName() + "\n"
                        + "log.folder=" + storedFolder.replace("\\", "\\\\") + "\n");

        HeadlessToolkit.onFxThread(() -> {
            service.bind(appState);

            assertEquals(STORED_PLAYBACK_SPEED, playbackSpeedOf(viewLoader.loadParent(LOG_TAB)),
                    0.0001, "the spinner should show the stored value, not the FXML default");

            assertSame(STORED_MODE, selectedModeOf(viewLoader.loadParent(MODE_GROUP)),
                    "the ribbon should highlight the stored mode, not the toggle marked "
                            + "selected in the markup");

            Label logFolder = (Label) viewLoader.loadParent(LOG_GROUP).lookup("#logFolderLabel");
            assertNotNull(logFolder, "the log folder read-out should be in the group's node tree");
            assertEquals(new File(storedFolder).getAbsolutePath(), logFolder.getText(),
                    "the read-out should show the stored folder, not \"(none selected)\"");
        });
    }

    /**
     * The failure this ordering prevents, demonstrated rather than described. Binding after the
     * controls exist leaves them and the state disagreeing — and nothing throws, which is exactly
     * what makes it worth a test.
     */
    @Test
    @DisplayName("restoring after the controls are built leaves them showing the default")
    void restoringAfterTheControlsAreBuiltLeavesThemShowingTheDefault() throws IOException {
        SettingsService service = serviceStoring(
                "log.playbackSpeedFactor=" + STORED_PLAYBACK_SPEED + "\n");

        HeadlessToolkit.onFxThread(() -> {
            Parent preferences = viewLoader.loadParent(LOG_TAB);
            service.bind(appState);

            assertEquals(STORED_PLAYBACK_SPEED, appState.getPlaybackSpeedFactor(), 0.0001,
                    "the state should have been restored either way");
            assertNotEquals(STORED_PLAYBACK_SPEED, playbackSpeedOf(preferences), 0.0001,
                    "the spinner was built before the restore, so it must still show the default — "
                            + "if this ever passes, the ordering constraint has been removed and "
                            + "the comments explaining it are stale");
        });
    }

    @Test
    @DisplayName("a first run leaves the controls at their documented defaults")
    void aFirstRunLeavesTheControlsAtTheirDocumentedDefaults() {
        SettingsService service = track(new SettingsService(
                new SettingsStore(directory.resolve("never-written.properties"))));

        HeadlessToolkit.onFxThread(() -> {
            service.bind(appState);

            assertEquals(Settings.PLAYBACK_SPEED_DEFAULT,
                    playbackSpeedOf(viewLoader.loadParent(LOG_TAB)), 0.0001);
            assertSame(Settings.DEFAULTS.mode(), selectedModeOf(viewLoader.loadParent(MODE_GROUP)));

            Label logFolder = (Label) viewLoader.loadParent(LOG_GROUP).lookup("#logFolderLabel");
            assertEquals("(none selected)", logFolder.getText());
        });
    }
}
