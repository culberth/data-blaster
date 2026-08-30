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
import com.culberth.tools.datablaster.model.MessageType;
import com.culberth.tools.datablaster.model.PortTailMapping;
import com.culberth.tools.datablaster.model.Settings;
import com.culberth.tools.datablaster.model.Theme;
import com.culberth.tools.datablaster.ui.LogFolderChooser;
import java.io.File;
import java.util.List;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * That Preferences is a view of {@link AppState} rather than a copy of it, tab by tab.
 *
 * <p>Peer review #27 was about a user opening the only thing called Preferences and finding
 * nothing. The fix could easily have introduced the worse problem: a dialog holding its own copy of
 * a setting, agreeing with the ribbon right up until it did not. Every assertion here is about the
 * surfaces sharing state rather than mirroring each other.
 *
 * <p><strong>The tabs are loaded directly, not looked up through the dialog.</strong> A
 * {@code TabPane}'s skin does not build a tab's content until that tab is shown, so a
 * {@code lookup} on an unshown dialog finds whatever happens to be attached and nothing else — a
 * test written that way would pass or fail on which tab is selected rather than on what it meant to
 * check. This is the same reason {@code SettingsRestoreOrderTest} loads ribbon groups directly.
 * That the dialog wires four tabs together is asserted from the {@code TabPane}'s own model below,
 * and that every tab loads is {@code FxmlSmokeTest}'s job.
 */
@SpringBootTest
class PreferencesSurfaceTest {

    private static final String PREFERENCES = "/fxml/preferences.fxml";
    private static final String GENERAL_TAB = "/fxml/preferences/general-tab.fxml";
    private static final String LOG_TAB = "/fxml/preferences/log-tab.fxml";
    private static final String MESSAGE_TAB = "/fxml/preferences/message-tab.fxml";
    private static final String SOAP_TAB = "/fxml/preferences/soap-tab.fxml";
    private static final String LOG_GROUP = "/fxml/ribbon/log-group.fxml";
    private static final String MESSAGE_GROUP = "/fxml/ribbon/message-group.fxml";
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
            appState.setPortTailMappings(List.of());
            appState.setTheme(Theme.LIGHT);
            appState.setMessageType(Settings.DEFAULTS.message().type());
            appState.setSoapPort(Settings.DEFAULTS.soap().port());
        });
    }

    @SuppressWarnings("unchecked")
    private static Spinner<Double> playbackSpeedSpinner(Parent root) {
        Spinner<Double> spinner = (Spinner<Double>) root.lookup("#playbackSpeedSpinner");
        assertNotNull(spinner, "the Playback Speed spinner should be in the node tree");
        return spinner;
    }

    // --- the dialog shell ---------------------------------------------------------------------

    @Test
    @DisplayName("Preferences opens with one tab per scope, and none for REST")
    void preferencesOpensWithOneTabPerScope() {
        HeadlessToolkit.onFxThread(() -> {
            TabPane tabs = (TabPane) viewLoader.loadParent(PREFERENCES).lookup("#tabs");
            assertNotNull(tabs, "the dialog should be a TabPane");

            assertEquals(List.of("General", "Log", "Message", "SOAP"),
                    tabs.getTabs().stream().map(Tab::getText).toList(),
                    "REST has no settings, so it has no tab; the mode toggle carries that message");
        });
    }

    // --- Log tab ------------------------------------------------------------------------------

    @Test
    @DisplayName("the Log tab opens showing the live state, not the FXML default")
    void theLogTabOpensShowingTheLiveState() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setPlaybackSpeedFactor(1.5);

            assertEquals(1.5, playbackSpeedSpinner(viewLoader.loadParent(LOG_TAB)).getValue(),
                    0.0001, "the tab should read the live state, not a value baked into markup");
        });
    }

    @Test
    @DisplayName("editing the Log tab writes through to the shared state")
    void editingTheLogTabWritesThroughToTheSharedState() {
        HeadlessToolkit.onFxThread(() -> {
            Parent tab = viewLoader.loadParent(LOG_TAB);

            playbackSpeedSpinner(tab).getValueFactory().setValue(4.0);

            assertEquals(4.0, appState.getPlaybackSpeedFactor(), 0.0001);
        });
    }

    /**
     * The control's bounds and the store's range check read from the same constants, so a spinner
     * cannot offer a value the store would reject on the next launch — which would look to the user
     * like a setting that silently refuses to stick.
     */
    @Test
    @DisplayName("the speed spinner cannot offer a value the store would reject")
    void theSpeedSpinnerCannotOfferAValueTheStoreWouldReject() {
        HeadlessToolkit.onFxThread(() -> {
            SpinnerValueFactory.DoubleSpinnerValueFactory factory =
                    (SpinnerValueFactory.DoubleSpinnerValueFactory)
                            playbackSpeedSpinner(viewLoader.loadParent(LOG_TAB)).getValueFactory();

            assertEquals(Settings.PLAYBACK_SPEED_MIN, factory.getMin(), 0.0001);
            assertEquals(Settings.PLAYBACK_SPEED_MAX, factory.getMax(), 0.0001);
        });
    }

    /**
     * The log folder read-out is bound in both surfaces rather than assigned, so this holds without
     * either being rebuilt — the case a copy-and-sync implementation gets wrong.
     */
    @Test
    @DisplayName("the Log tab and the Log ribbon group show the same folder, live")
    void theLogTabAndTheLogRibbonGroupShowTheSameFolderLive() {
        HeadlessToolkit.onFxThread(() -> {
            Label inDialog = (Label) viewLoader.loadParent(LOG_TAB).lookup("#logFolderLabel");
            Label inRibbon = (Label) viewLoader.loadParent(LOG_GROUP).lookup("#logFolderLabel");
            assertEquals("(none selected)", inDialog.getText());
            assertEquals(inRibbon.getText(), inDialog.getText());

            File chosen = new File(System.getProperty("java.io.tmpdir"), "datablaster-logs");
            appState.setLogFolder(chosen);

            assertEquals(chosen.getAbsolutePath(), inDialog.getText(),
                    "the read-out should track AppState without being rebuilt");
            assertEquals(inDialog.getText(), inRibbon.getText());
        });
    }

    @Test
    @DisplayName("the Log tab reset restores its own settings and then disables itself")
    void theLogTabResetRestoresItsOwnSettings() {
        HeadlessToolkit.onFxThread(() -> {
            Parent tab = viewLoader.loadParent(LOG_TAB);
            Button reset = (Button) tab.lookup("#resetButton");
            assertTrue(reset.isDisabled(), "nothing to reset when everything is already default");

            playbackSpeedSpinner(tab).getValueFactory().setValue(2.0);
            appState.setLogFolder(new File(System.getProperty("java.io.tmpdir")));
            assertFalse(reset.isDisabled(), "Reset should become available once something differs");

            // Fired with an empty mapping table on purpose: a non-empty one routes through a modal
            // confirmation, and showAndWait would block this thread with nobody to dismiss it.
            reset.fire();

            assertEquals(Settings.PLAYBACK_SPEED_DEFAULT, appState.getPlaybackSpeedFactor(), 0.0001);
            assertNull(appState.getLogFolder());
            assertEquals(Settings.PLAYBACK_SPEED_DEFAULT,
                    playbackSpeedSpinner(tab).getValue(), 0.0001,
                    "the control must follow, not just the state behind it");
            assertTrue(reset.isDisabled());
        });
    }

    /** R18's point: a reset is per tab, so it must not reach across into another tab's settings. */
    @Test
    @DisplayName("a tab's reset does not touch another tab's settings")
    void aTabsResetDoesNotTouchAnotherTabsSettings() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setTheme(Theme.DARK);
            appState.setMessageType(MessageType.MESSAGE_3);
            appState.setSoapPort(9443);
            appState.setLogFolder(new File(System.getProperty("java.io.tmpdir")));

            ((Button) viewLoader.loadParent(LOG_TAB).lookup("#resetButton")).fire();

            assertNull(appState.getLogFolder(), "the Log tab's own setting should have been reset");
            assertSame(Theme.DARK, appState.getTheme(), "the theme belongs to the General tab");
            assertSame(MessageType.MESSAGE_3, appState.getMessageType());
            assertEquals(9443, appState.getSoapPort());
        });
    }

    // --- General tab --------------------------------------------------------------------------

    @Test
    @DisplayName("the theme chooser reads and writes the shared state")
    void theThemeChooserReadsAndWritesTheSharedState() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setTheme(Theme.DARK);

            @SuppressWarnings("unchecked")
            ChoiceBox<Theme> chooser =
                    (ChoiceBox<Theme>) viewLoader.loadParent(GENERAL_TAB).lookup("#themeChoice");
            assertNotNull(chooser);
            assertEquals(Theme.DARK, chooser.getValue(),
                    "the chooser should open on the live theme, not the default");

            chooser.setValue(Theme.LIGHT);
            assertSame(Theme.LIGHT, appState.getTheme());
        });
    }

    @Test
    @DisplayName("the General tab shows where the settings actually live")
    void theGeneralTabShowsWhereTheSettingsActuallyLive() {
        HeadlessToolkit.onFxThread(() -> {
            Label path = (Label) viewLoader.loadParent(GENERAL_TAB).lookup("#settingsFileLabel");

            assertNotNull(path);
            assertTrue(path.getText().endsWith("settings.properties"),
                    "answering \"where did my setting go\" is the point of this label: "
                            + path.getText());
        });
    }

    // --- Message and SOAP tabs ----------------------------------------------------------------

    @Test
    @DisplayName("the Message tab and the Message ribbon group share one value")
    void theMessageTabAndTheMessageRibbonGroupShareOneValue() {
        HeadlessToolkit.onFxThread(() -> {
            @SuppressWarnings("unchecked")
            ChoiceBox<MessageType> inDialog = (ChoiceBox<MessageType>)
                    viewLoader.loadParent(MESSAGE_TAB).lookup("#messageTypeChoice");
            @SuppressWarnings("unchecked")
            ChoiceBox<MessageType> inRibbon = (ChoiceBox<MessageType>)
                    viewLoader.loadParent(MESSAGE_GROUP).lookup("#messageTypeChoice");

            inDialog.setValue(MessageType.MESSAGE_3);

            assertSame(MessageType.MESSAGE_3, appState.getMessageType());
            assertSame(MessageType.MESSAGE_3, inRibbon.getValue(),
                    "the ribbon chooser follows the state rather than holding its own copy");
        });
    }

    @Test
    @DisplayName("the Message tab offers every constant, without listing them in the markup")
    void theMessageTabOffersEveryConstant() {
        HeadlessToolkit.onFxThread(() -> {
            @SuppressWarnings("unchecked")
            ChoiceBox<MessageType> chooser = (ChoiceBox<MessageType>)
                    viewLoader.loadParent(MESSAGE_TAB).lookup("#messageTypeChoice");

            assertEquals(List.of(MessageType.values()), List.copyOf(chooser.getItems()));
        });
    }

    @Test
    @DisplayName("the SOAP port reads and writes the shared state, within the valid range")
    void theSoapPortReadsAndWritesTheSharedState() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setSoapPort(9443);

            @SuppressWarnings("unchecked")
            Spinner<Integer> port =
                    (Spinner<Integer>) viewLoader.loadParent(SOAP_TAB).lookup("#portSpinner");
            assertNotNull(port);
            assertEquals(9443, port.getValue());

            SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                    (SpinnerValueFactory.IntegerSpinnerValueFactory) port.getValueFactory();
            assertEquals(PortTailMapping.PORT_MIN, factory.getMin());
            assertEquals(PortTailMapping.PORT_MAX, factory.getMax());

            factory.setValue(8082);
            assertEquals(8082, appState.getSoapPort());
        });
    }

    // --- what is deliberately absent ----------------------------------------------------------

    /**
     * Opacity is a view control with a Reset beside it in the ribbon, and it is not persisted.
     * Putting it in Preferences would advertise it as a setting that survives a restart.
     */
    @Test
    @DisplayName("content opacity is not offered as a preference")
    void contentOpacityIsNotOfferedAsAPreference() {
        HeadlessToolkit.onFxThread(() -> {
            for (String tab : List.of(GENERAL_TAB, LOG_TAB, MESSAGE_TAB, SOAP_TAB)) {
                assertNull(viewLoader.loadParent(tab).lookup("#opacitySlider"),
                        "opacity is a view control, not a persisted setting: " + tab);
            }
        });
    }

    /**
     * The mirror of the rule above: the Appearance group is opacity only. Playback Speed Factor is a
     * Log-mode setting and lives in the Log group and this dialog.
     */
    @Test
    @DisplayName("the Appearance group carries no persisted setting")
    void theAppearanceGroupCarriesNoPersistedSetting() {
        HeadlessToolkit.onFxThread(() -> {
            Parent appearance = viewLoader.loadParent(APPEARANCE_GROUP);

            assertNull(appearance.lookup("#simFactorSlider"),
                    "the template's Sim Factor slider should be gone, not renamed in place");
            assertNull(appearance.lookup("#playbackSpeedSpinner"));
            assertNotNull(appearance.lookup("#opacitySlider"));
        });
    }

    /**
     * The chooser is shared rather than duplicated, which keeps the remembered directory and the
     * dialog's title from drifting apart between the two surfaces.
     */
    @Test
    @DisplayName("both surfaces use one log-folder chooser")
    void bothSurfacesUseOneLogFolderChooser() {
        assertSame(context.getBean(LogFolderChooser.class), context.getBean(LogFolderChooser.class),
                "LogFolderChooser must be a singleton, or the remembered directory resets");
    }
}
