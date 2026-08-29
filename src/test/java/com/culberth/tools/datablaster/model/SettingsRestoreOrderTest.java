package com.culberth.tools.datablaster.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.culberth.tools.datablaster.HeadlessToolkit;
import com.culberth.tools.datablaster.ViewLoader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
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
 * <p>The ribbon groups read their starting values from {@link AppState} in their
 * {@code initialize()} methods and never re-read them. So the ordering is not a matter of taste:
 * get it wrong and the controls show defaults while the state says something else, with nothing
 * failing and nothing logged.
 *
 * <p><strong>The ribbon groups are loaded directly rather than through the shell.</strong>
 * {@code main.fxml} puts them inside a {@code TabPane}, whose skin does not build tab content until
 * the tab is shown — so a {@code lookup} on an unshown shell finds nothing, and a test written that
 * way would fail for a reason that has nothing to do with settings. That the shell includes these
 * groups correctly is {@code FxmlSmokeTest}'s job.
 */
@SpringBootTest(properties = "spring.main.web-application-type=none")
class SettingsRestoreOrderTest {

    private static final String APPEARANCE_GROUP = "/fxml/ribbon/appearance-group.fxml";
    private static final String TOOLS_GROUP = "/fxml/ribbon/tools-group.fxml";

    private static final double STORED_SIM_FACTOR = -3.5;

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
            appState.setSimFactor(Settings.DEFAULTS.simFactor());
            appState.setLogFolder(null);
        });
    }

    /** Every service this test binds, so none is left listening to the shared {@link AppState}. */
    private final List<SettingsService> bound = new ArrayList<>();

    private SettingsService serviceStoring(String contents) throws IOException {
        Path file = directory.resolve("settings.properties");
        Files.writeString(file, contents);
        return track(new SettingsService(new SettingsStore(file)));
    }

    private SettingsService track(SettingsService service) {
        bound.add(service);
        return service;
    }

    private static Slider simFactorSliderOf(Parent appearanceGroup) {
        Slider slider = (Slider) appearanceGroup.lookup("#simFactorSlider");
        assertNotNull(slider, "the Sim Factor slider should be in the group's node tree");
        return slider;
    }

    @Test
    @DisplayName("restoring before the controls are built puts stored values on them")
    void restoringBeforeTheControlsAreBuiltPutsStoredValuesOnThem() throws IOException {
        String storedFolder = directory.resolve("logs").toString();
        SettingsService service = serviceStoring(
                "simFactor=" + STORED_SIM_FACTOR + "\n"
                        + "logFolder=" + storedFolder.replace("\\", "\\\\") + "\n");

        HeadlessToolkit.onFxThread(() -> {
            service.bind(appState);

            assertEquals(STORED_SIM_FACTOR,
                    simFactorSliderOf(viewLoader.loadParent(APPEARANCE_GROUP)).getValue(), 0.0001,
                    "the slider should show the stored value, not the FXML default");

            Label logFolder = (Label) viewLoader.loadParent(TOOLS_GROUP).lookup("#logFolderLabel");
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
        SettingsService service = serviceStoring("simFactor=" + STORED_SIM_FACTOR + "\n");

        HeadlessToolkit.onFxThread(() -> {
            Slider slider = simFactorSliderOf(viewLoader.loadParent(APPEARANCE_GROUP));
            service.bind(appState);

            assertEquals(STORED_SIM_FACTOR, appState.getSimFactor(), 0.0001,
                    "the state should have been restored either way");
            assertNotEquals(STORED_SIM_FACTOR, slider.getValue(), 0.0001,
                    "the slider was built before the restore, so it must still show the default — "
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

            assertEquals(Settings.DEFAULTS.simFactor(),
                    simFactorSliderOf(viewLoader.loadParent(APPEARANCE_GROUP)).getValue(), 0.0001);

            Label logFolder = (Label) viewLoader.loadParent(TOOLS_GROUP).lookup("#logFolderLabel");
            assertEquals("(none selected)", logFolder.getText());
        });
    }
}
