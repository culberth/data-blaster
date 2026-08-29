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
import com.culberth.tools.datablaster.model.Theme;
import com.culberth.tools.datablaster.ui.LogFolderChooser;
import java.io.File;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * That Preferences and the ribbon are two views of one value, not two values.
 *
 * <p>Peer review #27 was about a user opening the only thing called Preferences and finding
 * nothing. The fix could easily have introduced the worse problem: a dialog holding its own copy of
 * a setting, agreeing with the ribbon right up until it did not. These tests exist to keep that
 * from happening quietly — every assertion here is about the two surfaces sharing
 * {@link AppState} rather than mirroring each other.
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
            appState.setSimFactor(0.0);
            appState.setLogFolder(null);
            appState.setTheme(Theme.LIGHT);
        });
    }

    private static Slider simFactorSlider(Parent root) {
        Slider slider = (Slider) root.lookup("#simFactorSlider");
        assertNotNull(slider, "the Sim Factor slider should be in the node tree");
        return slider;
    }

    @Test
    @DisplayName("Preferences opens showing the value the ribbon last set")
    void preferencesOpensShowingTheValueTheRibbonLastSet() {
        HeadlessToolkit.onFxThread(() -> {
            Slider ribbonSlider = simFactorSlider(viewLoader.loadParent(APPEARANCE_GROUP));
            ribbonSlider.setValue(1.5);

            Parent preferences = viewLoader.loadParent(PREFERENCES);

            assertEquals(1.5, simFactorSlider(preferences).getValue(), 0.0001,
                    "the dialog should read the live state, not the FXML default");
            assertEquals("1.5", ((Label) preferences.lookup("#simFactorValueLabel")).getText());
        });
    }

    @Test
    @DisplayName("editing in Preferences reaches the ribbon through AppState")
    void editingInPreferencesReachesTheRibbonThroughAppState() {
        HeadlessToolkit.onFxThread(() -> {
            Parent preferences = viewLoader.loadParent(PREFERENCES);

            simFactorSlider(preferences).setValue(-4.0);

            assertEquals(-4.0, appState.getSimFactor(), 0.0001,
                    "the dialog must write through to the shared state");
            // A ribbon built afterwards reads that same state — which is the whole claim.
            assertEquals(-4.0, simFactorSlider(viewLoader.loadParent(APPEARANCE_GROUP)).getValue(),
                    0.0001);
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
    @DisplayName("Reset to defaults clears both settings and then disables itself")
    void resetToDefaultsClearsBothSettingsAndThenDisablesItself() {
        HeadlessToolkit.onFxThread(() -> {
            Parent preferences = viewLoader.loadParent(PREFERENCES);
            Button reset = (Button) preferences.lookup("#resetButton");
            assertNotNull(reset);
            assertTrue(reset.isDisabled(), "nothing to reset when everything is already default");

            simFactorSlider(preferences).setValue(2.0);
            appState.setLogFolder(new File(System.getProperty("java.io.tmpdir")));
            assertFalse(reset.isDisabled(), "Reset should become available once something differs");

            reset.fire();

            assertEquals(0.0, appState.getSimFactor(), 0.0001);
            assertNull(appState.getLogFolder());
            assertEquals(0.0, simFactorSlider(preferences).getValue(), 0.0001,
                    "the control must follow, not just the state behind it");
            assertTrue(reset.isDisabled(), "back at defaults, so there is nothing left to reset");
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
     * R5's control lands here rather than in the ribbon, because a persisted theme is a setting and
     * this is where settings live — the rule R1 established. Wired the same one-way way as the
     * slider: read the live value, write changes through.
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
}
