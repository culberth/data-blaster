package com.culberth.tools.datablaster.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.culberth.tools.datablaster.HeadlessToolkit;
import com.culberth.tools.datablaster.ViewLoader;
import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.Settings;
import com.culberth.tools.datablaster.model.Theme;
import com.culberth.tools.datablaster.ui.LogFolderChooser;
import java.io.File;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * That Preferences and the ribbon are views of one value, not two values.
 *
 * <p>Peer review #27 was about a user opening the only thing called Preferences and finding
 * nothing. The fix could easily have introduced the worse problem: a dialog holding its own copy of
 * a setting, agreeing with the ribbon right up until it did not. These tests exist to keep that
 * from happening quietly — every assertion here is about the surfaces sharing {@link AppState}
 * rather than mirroring each other.
 *
 * <p>The log folder is now the only setting shown in both places, since Playback Speed Factor left
 * the ribbon along with the Appearance group's slider. The dialog is still checked against the
 * shared state directly, which is the property that mattered.
 */
@SpringBootTest(properties = "spring.main.web-application-type=none")
class PreferencesSurfaceTest {

    private static final String PREFERENCES = "/fxml/preferences.fxml";
    private static final String TOOLS_GROUP = "/fxml/ribbon/tools-group.fxml";
    private static final String APPEARANCE_GROUP = "/fxml/ribbon/appearance-group.fxml";

    @Autowired
    private ViewLoader viewLoader;

    @Autowired
    private AppState appState;

    @Autowired
    private ApplicationContext context;

    @BeforeAll
    static void startToolkit() {
        HeadlessToolkit.start();
        HeadlessToolkit.onFxThread(AppState::markFxApplicationThread);
    }

    @AfterEach
    void resetSharedState() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setPlaybackSpeedFactor(Settings.PLAYBACK_SPEED_DEFAULT);
            appState.setLogFolder(null);
            appState.setTheme(Theme.LIGHT);
        });
    }

    @SuppressWarnings("unchecked")
    private static Spinner<Double> playbackSpeedSpinner(Parent root) {
        Spinner<Double> spinner = (Spinner<Double>) root.lookup("#playbackSpeedSpinner");
        assertNotNull(spinner, "the Playback Speed spinner should be in the node tree");
        return spinner;
    }

    @Test
    @DisplayName("Preferences opens showing the live state, not the FXML default")
    void preferencesOpensShowingTheLiveState() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setPlaybackSpeedFactor(1.5);

            Parent preferences = viewLoader.loadParent(PREFERENCES);

            assertEquals(1.5, playbackSpeedSpinner(preferences).getValue(), 0.0001,
                    "the dialog should read the live state, not a value baked into the markup");
        });
    }

    @Test
    @DisplayName("editing in Preferences writes through to the shared state")
    void editingInPreferencesWritesThroughToTheSharedState() {
        HeadlessToolkit.onFxThread(() -> {
            Parent preferences = viewLoader.loadParent(PREFERENCES);

            playbackSpeedSpinner(preferences).getValueFactory().setValue(4.0);

            assertEquals(4.0, appState.getPlaybackSpeedFactor(), 0.0001,
                    "the dialog must write through to the shared state");
        });
    }

    /**
     * The control's bounds and the store's range check read from the same constants, so a spinner
     * cannot offer a value that the store would then reject on the next launch — which would look
     * to the user like a setting that silently refuses to stick.
     */
    @Test
    @DisplayName("the spinner cannot offer a value the store would reject")
    void theSpinnerCannotOfferAValueTheStoreWouldReject() {
        HeadlessToolkit.onFxThread(() -> {
            Parent preferences = viewLoader.loadParent(PREFERENCES);
            SpinnerValueFactory<Double> factory =
                    playbackSpeedSpinner(preferences).getValueFactory();

            SpinnerValueFactory.DoubleSpinnerValueFactory bounded =
                    (SpinnerValueFactory.DoubleSpinnerValueFactory) factory;
            assertEquals(Settings.PLAYBACK_SPEED_MIN, bounded.getMin(), 0.0001);
            assertEquals(Settings.PLAYBACK_SPEED_MAX, bounded.getMax(), 0.0001);
        });
    }

    /**
     * The log folder read-out is bound in both surfaces rather than assigned, so this holds without
     * either dialog being reopened — the case a copy-and-sync implementation gets wrong.
     */
    @Test
    @DisplayName("both surfaces show the same log folder, live")
    void bothSurfacesShowTheSameLogFolderLive() {
        HeadlessToolkit.onFxThread(() -> {
            Parent preferences = viewLoader.loadParent(PREFERENCES);
            Parent ribbon = viewLoader.loadParent(TOOLS_GROUP);

            Label inDialog = (Label) preferences.lookup("#logFolderLabel");
            Label inRibbon = (Label) ribbon.lookup("#logFolderLabel");
            assertEquals("(none selected)", inDialog.getText());
            assertEquals(inRibbon.getText(), inDialog.getText());

            File chosen = new File(System.getProperty("java.io.tmpdir"), "datablaster-logs");
            appState.setLogFolder(chosen);

            assertEquals(chosen.getAbsolutePath(), inDialog.getText(),
                    "the dialog's read-out should track AppState without being rebuilt");
            assertEquals(inDialog.getText(), inRibbon.getText(),
                    "both surfaces should be showing the same thing at all times");
        });
    }

    @Test
    @DisplayName("Reset to defaults clears the settings it shows and then disables itself")
    void resetToDefaultsClearsTheSettingsItShowsAndThenDisablesItself() {
        HeadlessToolkit.onFxThread(() -> {
            Parent preferences = viewLoader.loadParent(PREFERENCES);
            Button reset = (Button) preferences.lookup("#resetButton");
            assertNotNull(reset);
            assertTrue(reset.isDisabled(), "nothing to reset when everything is already default");

            playbackSpeedSpinner(preferences).getValueFactory().setValue(2.0);
            appState.setLogFolder(new File(System.getProperty("java.io.tmpdir")));
            assertFalse(reset.isDisabled(), "Reset should become available once something differs");

            reset.fire();

            assertEquals(Settings.PLAYBACK_SPEED_DEFAULT, appState.getPlaybackSpeedFactor(), 0.0001);
            assertNull(appState.getLogFolder());
            assertEquals(Settings.PLAYBACK_SPEED_DEFAULT,
                    playbackSpeedSpinner(preferences).getValue(), 0.0001,
                    "the control must follow, not just the state behind it");
            assertTrue(reset.isDisabled(), "back at defaults, so there is nothing left to reset");
        });
    }

    /**
     * Reset touches what the dialog shows and nothing else. Clearing a mapping table someone typed
     * by hand is a different act from putting a spinner back, and it gets its own confirmation when
     * the Log tab that owns it exists.
     */
    @Test
    @DisplayName("Reset to defaults does not clear settings the dialog does not show")
    void resetToDefaultsDoesNotClearSettingsTheDialogDoesNotShow() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setPortTailMappings(java.util.List.of(
                    com.culberth.tools.datablaster.model.PortTailMapping.of(5001, "N12345")));
            appState.setLogFolder(new File(System.getProperty("java.io.tmpdir")));

            Parent preferences = viewLoader.loadParent(PREFERENCES);
            ((Button) preferences.lookup("#resetButton")).fire();

            assertEquals(1, appState.portTailMappings().size(),
                    "the mapping table is not on this surface and must not be cleared by its reset");

            appState.setPortTailMappings(java.util.List.of());
        });
    }

    /**
     * The chooser is shared rather than duplicated, which is what keeps the remembered directory
     * and the dialog's title from drifting apart between the two surfaces.
     */
    @Test
    @DisplayName("both surfaces use one log-folder chooser")
    void bothSurfacesUseOneLogFolderChooser() {
        assertSame(context.getBean(LogFolderChooser.class), context.getBean(LogFolderChooser.class),
                "LogFolderChooser must be a singleton, or the remembered directory resets");
    }

    /**
     * A persisted theme is a setting, and this is where settings live. Wired the same one-way way
     * as the spinner: read the live value, write changes through.
     */
    @Test
    @DisplayName("the theme chooser reads and writes the shared state")
    void theThemeChooserReadsAndWritesTheSharedState() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setTheme(Theme.DARK);

            Parent preferences = viewLoader.loadParent(PREFERENCES);
            @SuppressWarnings("unchecked")
            ChoiceBox<Theme> chooser = (ChoiceBox<Theme>) preferences.lookup("#themeChoice");
            assertNotNull(chooser, "the theme chooser should be in the dialog");
            assertEquals(Theme.DARK, chooser.getValue(),
                    "the chooser should open on the live theme, not the default");

            chooser.setValue(Theme.LIGHT);
            assertEquals(Theme.LIGHT, appState.getTheme(),
                    "choosing a theme must write through to the shared state");
        });
    }

    /**
     * Opacity is a view control with a Reset beside it in the ribbon, and it is not persisted.
     * Putting it in Preferences would advertise it as a setting that survives a restart.
     */
    @Test
    @DisplayName("content opacity is not offered as a preference")
    void contentOpacityIsNotOfferedAsAPreference() {
        HeadlessToolkit.onFxThread(() -> {
            Parent preferences = viewLoader.loadParent(PREFERENCES);

            assertNull(preferences.lookup("#opacitySlider"),
                    "opacity is a view control, not a persisted setting");
        });
    }

    /**
     * The mirror of the rule above: the Appearance group is now opacity only. Playback Speed Factor
     * is a Log-mode setting rather than an appearance one, and as a multiplier over 0.1-10.0 it is
     * not a linear slider either — 1.0 would sit at 9% of the track.
     */
    @Test
    @DisplayName("the Appearance group carries no persisted setting")
    void theAppearanceGroupCarriesNoPersistedSetting() {
        HeadlessToolkit.onFxThread(() -> {
            Parent appearance = viewLoader.loadParent(APPEARANCE_GROUP);

            assertNull(appearance.lookup("#simFactorSlider"),
                    "the template's Sim Factor slider should be gone, not renamed in place");
            assertNull(appearance.lookup("#playbackSpeedSpinner"),
                    "Playback Speed Factor belongs in Preferences, not the Appearance group");
            assertNotNull(appearance.lookup("#opacitySlider"),
                    "opacity is what the group is left with");
        });
    }
}
