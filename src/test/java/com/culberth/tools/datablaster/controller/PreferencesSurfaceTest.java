package com.culberth.tools.datablaster.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.culberth.tools.datablaster.HeadlessToolkit;
import com.culberth.tools.datablaster.ViewLoader;
import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.MessageType;
import com.culberth.tools.datablaster.model.PortTailMapping;
import com.culberth.tools.datablaster.model.Settings;
import com.culberth.tools.datablaster.model.SoapMessageType;
import com.culberth.tools.datablaster.model.TailNumber;
import com.culberth.tools.datablaster.model.Theme;
import com.culberth.tools.datablaster.ui.DataFileChooser;
import com.culberth.tools.datablaster.ui.LogFolderChooser;
import java.io.File;
import java.util.List;
import javafx.event.ActionEvent;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
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
 * <p>
 * Peer review #27 was about a user opening the only thing called Preferences and finding nothing. The fix could easily
 * have introduced the worse problem: a dialog holding its own copy of a setting, agreeing with the ribbon right up
 * until it did not. Every assertion here is about the surfaces sharing state rather than mirroring each other.
 *
 * <p>
 * <strong>The tabs are loaded directly, not looked up through the dialog.</strong> A {@code TabPane}'s skin does not
 * build a tab's content until that tab is shown, so a {@code lookup} on an unshown dialog finds whatever happens to be
 * attached and nothing else — a test written that way would pass or fail on which tab is selected rather than on what
 * it meant to check. This is the same reason {@code SettingsRestoreOrderTest} loads ribbon groups directly. That the
 * dialog wires four tabs together is asserted from the {@code TabPane}'s own model below, and that every tab loads is
 * {@code FxmlSmokeTest}'s job.
 */
@SpringBootTest
class PreferencesSurfaceTest
{

    private static final String PREFERENCES = "/fxml/preferences.fxml";
    private static final String GENERAL_TAB = "/fxml/preferences/general-tab.fxml";
    private static final String LOG_TAB = "/fxml/preferences/log-tab.fxml";
    private static final String MESSAGE_TAB = "/fxml/preferences/message-tab.fxml";
    private static final String SOAP_TAB = "/fxml/preferences/soap-tab.fxml";
    private static final String LOG_GROUP = "/fxml/ribbon/log-group.fxml";
    private static final String MESSAGE_GROUP = "/fxml/ribbon/message-group.fxml";
    private static final String GLOBAL_GROUP = "/fxml/ribbon/global-group.fxml";

    @Autowired
    private ViewLoader viewLoader;

    @Autowired
    private AppState appState;

    @Autowired
    private ApplicationContext context;

    @BeforeAll
    static void startToolkit()
    {
        HeadlessToolkit.start();
        HeadlessToolkit.onFxThread(AppState::markFxApplicationThread);
    }

    @AfterEach
    void resetSharedState()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            appState.setPlaybackSpeedFactor(Settings.PLAYBACK_SPEED_DEFAULT);
            appState.setLogFolder(null);
            appState.setPortTailMappings(List.of());
            appState.setTheme(Theme.LIGHT);
            appState.setBlastPort(Settings.DEFAULTS.blastPort());
            appState.setSingleMessage(Settings.DEFAULTS.singleMessage());
            appState.setByteHijack(Settings.DEFAULTS.byteHijack());
            appState.setMessageType(Settings.DEFAULTS.message().type());
            appState.setSoapIp(Settings.DEFAULTS.soap().ip());
            appState.setSoapMessageType(Settings.DEFAULTS.soap().type());
            appState.setSoapDataFiles(List.of());
            appState.setSoapTail(Settings.DEFAULTS.soap().tail());
        });
    }

    @SuppressWarnings("unchecked")
    private static Spinner<Integer> blastPortSpinner(Parent root)
    {
        Spinner<Integer> spinner = (Spinner<Integer>) root.lookup("#blastPortSpinner");
        assertNotNull(spinner, "the Blast Port spinner should be in the node tree");
        return spinner;
    }

    @SuppressWarnings("unchecked")
    private static Spinner<Double> playbackSpeedSpinner(Parent root)
    {
        Spinner<Double> spinner = (Spinner<Double>) root.lookup("#playbackSpeedSpinner");
        assertNotNull(spinner, "the Playback Speed spinner should be in the node tree");
        return spinner;
    }

    // --- the dialog shell ---------------------------------------------------------------------

    @Test
    @DisplayName("Preferences opens with one tab per scope, and none for REST")
    void preferencesOpensWithOneTabPerScope()
    {
        HeadlessToolkit.onFxThread(() ->
        {
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
    void theLogTabOpensShowingTheLiveState()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            appState.setPlaybackSpeedFactor(1.5);

            assertEquals(1.5, playbackSpeedSpinner(viewLoader.loadParent(LOG_TAB)).getValue(), 0.0001,
                    "the tab should read the live state, not a value baked into markup");
        });
    }

    @Test
    @DisplayName("editing the Log tab writes through to the shared state")
    void editingTheLogTabWritesThroughToTheSharedState()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(LOG_TAB);

            playbackSpeedSpinner(tab).getValueFactory().setValue(4.0);

            assertEquals(4.0, appState.getPlaybackSpeedFactor(), 0.0001);
        });
    }

    /**
     * The control's bounds and the store's range check read from the same constants, so a spinner cannot offer a value
     * the store would reject on the next launch — which would look to the user like a setting that silently refuses to
     * stick.
     */
    @Test
    @DisplayName("the speed spinner cannot offer a value the store would reject")
    void theSpeedSpinnerCannotOfferAValueTheStoreWouldReject()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            SpinnerValueFactory.DoubleSpinnerValueFactory factory = (SpinnerValueFactory.DoubleSpinnerValueFactory) playbackSpeedSpinner(
                    viewLoader.loadParent(LOG_TAB)).getValueFactory();

            assertEquals(Settings.PLAYBACK_SPEED_MIN, factory.getMin(), 0.0001);
            assertEquals(Settings.PLAYBACK_SPEED_MAX, factory.getMax(), 0.0001);
        });
    }

    /**
     * The log folder read-out is bound in both surfaces rather than assigned, so this holds without either being
     * rebuilt — the case a copy-and-sync implementation gets wrong.
     */
    @Test
    @DisplayName("the Log tab and the Log ribbon group show the same folder, live")
    void theLogTabAndTheLogRibbonGroupShowTheSameFolderLive()
    {
        HeadlessToolkit.onFxThread(() ->
        {
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
    void theLogTabResetRestoresItsOwnSettings()
    {
        HeadlessToolkit.onFxThread(() ->
        {
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
            assertEquals(Settings.PLAYBACK_SPEED_DEFAULT, playbackSpeedSpinner(tab).getValue(), 0.0001,
                    "the control must follow, not just the state behind it");
            assertTrue(reset.isDisabled());
        });
    }

    /** R18's point: a reset is per tab, so it must not reach across into another tab's settings. */
    @Test
    @DisplayName("a tab's reset does not touch another tab's settings")
    void aTabsResetDoesNotTouchAnotherTabsSettings()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            appState.setTheme(Theme.DARK);
            appState.setBlastPort(9443);
            appState.setSingleMessage(true);
            appState.setMessageType(MessageType.MESSAGE_3);
            appState.setSoapIp("10.20.30.40");
            appState.setLogFolder(new File(System.getProperty("java.io.tmpdir")));

            ((Button) viewLoader.loadParent(LOG_TAB).lookup("#resetButton")).fire();

            assertNull(appState.getLogFolder(), "the Log tab's own setting should have been reset");
            assertSame(Theme.DARK, appState.getTheme(), "the theme belongs to the General tab");
            assertEquals(9443, appState.getBlastPort(), "so do the three global settings");
            assertTrue(appState.isSingleMessage());
            assertSame(MessageType.MESSAGE_3, appState.getMessageType());
            assertEquals("10.20.30.40", appState.getSoapIp());
        });
    }

    // --- General tab --------------------------------------------------------------------------

    @Test
    @DisplayName("the theme chooser reads and writes the shared state")
    void theThemeChooserReadsAndWritesTheSharedState()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            appState.setTheme(Theme.DARK);

            @SuppressWarnings("unchecked")
            ChoiceBox<Theme> chooser = (ChoiceBox<Theme>) viewLoader.loadParent(GENERAL_TAB).lookup("#themeChoice");
            assertNotNull(chooser);
            assertEquals(Theme.DARK, chooser.getValue(), "the chooser should open on the live theme, not the default");

            chooser.setValue(Theme.LIGHT);
            assertSame(Theme.LIGHT, appState.getTheme());
        });
    }

    /**
     * The three settings that replaced the opacity slider. Opacity was a view control and lived in one place; these are
     * settings, so they are in the ribbon and here, and neither surface holds its own copy.
     */
    @Test
    @DisplayName("the General tab and the Global ribbon group share the three global settings")
    void theGeneralTabAndTheGlobalRibbonGroupShareTheThreeGlobalSettings()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(GENERAL_TAB);
            Parent ribbon = viewLoader.loadParent(GLOBAL_GROUP);

            blastPortSpinner(tab).getValueFactory().setValue(9443);
            ((CheckBox) tab.lookup("#singleMessageCheck")).setSelected(true);
            ((CheckBox) tab.lookup("#byteHijackCheck")).setSelected(true);

            assertEquals(9443, appState.getBlastPort());
            assertTrue(appState.isSingleMessage());
            assertTrue(appState.isByteHijack());

            assertEquals(9443, blastPortSpinner(ribbon).getValue(),
                    "the ribbon follows the state rather than holding its own copy");
            assertTrue(((CheckBox) ribbon.lookup("#singleMessageCheck")).isSelected());
            assertTrue(((CheckBox) ribbon.lookup("#byteHijackCheck")).isSelected());
        });
    }

    /**
     * The same rule the playback speed spinner is held to: a control that offers a value the store would reject on the
     * next launch looks to the user like a setting that refuses to stick.
     */
    @Test
    @DisplayName("both Blast Port spinners are bounded by the valid port range")
    void bothBlastPortSpinnersAreBoundedByTheValidPortRange()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            for (String surface : List.of(GENERAL_TAB, GLOBAL_GROUP))
            {
                SpinnerValueFactory.IntegerSpinnerValueFactory factory = (SpinnerValueFactory.IntegerSpinnerValueFactory) blastPortSpinner(
                        viewLoader.loadParent(surface)).getValueFactory();

                assertEquals(PortTailMapping.PORT_MIN, factory.getMin(), surface);
                assertEquals(PortTailMapping.PORT_MAX, factory.getMax(), surface);
            }
        });
    }

    @Test
    @DisplayName("the General tab reset restores all four of its settings and then disables itself")
    void theGeneralTabResetRestoresAllFourOfItsSettings()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(GENERAL_TAB);
            Button reset = (Button) tab.lookup("#resetButton");
            assertTrue(reset.isDisabled(), "nothing to reset when everything is already default");

            appState.setTheme(Theme.DARK);
            appState.setBlastPort(9443);
            appState.setSingleMessage(true);
            appState.setByteHijack(true);
            assertFalse(reset.isDisabled(), "Reset should become available once something differs");

            reset.fire();

            assertSame(Settings.DEFAULTS.theme(), appState.getTheme());
            assertEquals(Settings.DEFAULTS.blastPort(), appState.getBlastPort());
            assertFalse(appState.isSingleMessage());
            assertFalse(appState.isByteHijack());
            assertEquals(Settings.DEFAULTS.blastPort(), blastPortSpinner(tab).getValue(),
                    "the control must follow, not just the state behind it");
            assertFalse(((CheckBox) tab.lookup("#singleMessageCheck")).isSelected());
            assertTrue(reset.isDisabled());
        });
    }

    @Test
    @DisplayName("the General tab shows where the settings actually live")
    void theGeneralTabShowsWhereTheSettingsActuallyLive()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Label path = (Label) viewLoader.loadParent(GENERAL_TAB).lookup("#settingsFileLabel");

            assertNotNull(path);
            assertTrue(path.getText().endsWith("settings.properties"),
                    "answering \"where did my setting go\" is the point of this label: " + path.getText());
        });
    }

    // --- Message and SOAP tabs ----------------------------------------------------------------

    @Test
    @DisplayName("the Message tab and the Message ribbon group share one value")
    void theMessageTabAndTheMessageRibbonGroupShareOneValue()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            @SuppressWarnings("unchecked")
            ChoiceBox<MessageType> inDialog = (ChoiceBox<MessageType>) viewLoader.loadParent(MESSAGE_TAB)
                    .lookup("#messageTypeChoice");
            @SuppressWarnings("unchecked")
            ChoiceBox<MessageType> inRibbon = (ChoiceBox<MessageType>) viewLoader.loadParent(MESSAGE_GROUP)
                    .lookup("#messageTypeChoice");

            inDialog.setValue(MessageType.MESSAGE_3);

            assertSame(MessageType.MESSAGE_3, appState.getMessageType());
            assertSame(MessageType.MESSAGE_3, inRibbon.getValue(),
                    "the ribbon chooser follows the state rather than holding its own copy");
        });
    }

    @Test
    @DisplayName("the Message tab offers every constant, without listing them in the markup")
    void theMessageTabOffersEveryConstant()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            @SuppressWarnings("unchecked")
            ChoiceBox<MessageType> chooser = (ChoiceBox<MessageType>) viewLoader.loadParent(MESSAGE_TAB)
                    .lookup("#messageTypeChoice");

            assertEquals(List.of(MessageType.values()), List.copyOf(chooser.getItems()));
        });
    }

    @Test
    @DisplayName("the SOAP address reads and writes the shared state")
    void theSoapAddressReadsAndWritesTheSharedState()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            appState.setSoapIp("10.20.30.40");

            Parent tab = viewLoader.loadParent(SOAP_TAB);
            TextField ip = (TextField) tab.lookup("#ipField");
            assertNotNull(ip);
            assertEquals("10.20.30.40", ip.getText(), "the tab should open on the live address, not the default");

            ip.setText("192.168.0.9");
            ip.fireEvent(new ActionEvent(ip, null));

            assertEquals("192.168.0.9", appState.getSoapIp());
        });
    }

    /**
     * The mapping editor's rule, applied to this tab: a bad entry is refused where it is made, the reason is shown
     * beside the field, and the state is left exactly as it was.
     */
    @Test
    @DisplayName("a malformed address is refused with the reason beside the field")
    void aMalformedAddressIsRefusedWithTheReasonBesideTheField()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(SOAP_TAB);
            TextField ip = (TextField) tab.lookup("#ipField");
            Label error = (Label) tab.lookup("#ipErrorLabel");
            assertFalse(error.isVisible(), "nothing is wrong yet");

            ip.setText("10.0.0.256");
            ip.fireEvent(new ActionEvent(ip, null));

            assertEquals(Settings.DEFAULTS.soap().ip(), appState.getSoapIp(), "a rejected entry must change nothing");
            assertTrue(error.isVisible(), "and must say why");
            assertTrue(error.getText().contains("10.0.0.256"), error.getText());
            assertEquals("10.0.0.256", ip.getText(),
                    "the field keeps what was typed, so there is something to correct");
        });
    }

    @Test
    @DisplayName("the SOAP tab offers every message type, without listing them in the markup")
    void theSoapTabOffersEveryMessageType()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            @SuppressWarnings("unchecked")
            ChoiceBox<SoapMessageType> chooser = (ChoiceBox<SoapMessageType>) viewLoader.loadParent(SOAP_TAB)
                    .lookup("#soapMessageTypeChoice");
            assertNotNull(chooser);

            assertEquals(List.of(SoapMessageType.values()), List.copyOf(chooser.getItems()));

            chooser.setValue(SoapMessageType.TYPE_3);
            assertSame(SoapMessageType.TYPE_3, appState.getSoapMessageType());
            assertSame(Settings.DEFAULTS.message().type(), appState.getMessageType(),
                    "SOAP's type is its own setting, not Message mode's");
        });
    }

    /**
     * The list is AppState's unmodifiable view rather than a copy, so it tracks edits without being rebuilt — and
     * cannot become a second way to change the setting.
     */
    @Test
    @DisplayName("the data file list is a live view of the shared state")
    void theDataFileListIsALiveViewOfTheSharedState()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            @SuppressWarnings("unchecked")
            ListView<File> list = (ListView<File>) viewLoader.loadParent(SOAP_TAB).lookup("#dataFileList");
            assertNotNull(list);
            assertTrue(list.getItems().isEmpty());

            File file = new File(System.getProperty("java.io.tmpdir"), "capture.bin");
            appState.addSoapDataFiles(List.of(file));

            assertEquals(List.of(file), List.copyOf(list.getItems()),
                    "the list should track AppState without being rebuilt");
            assertThrows(UnsupportedOperationException.class, () -> list.getItems().clear(),
                    "and must not be a second way in");
        });
    }

    @Test
    @DisplayName("the tail number reads and writes the shared state, normalised")
    void theTailNumberReadsAndWritesTheSharedState()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(SOAP_TAB);
            TextField tail = (TextField) tab.lookup("#tailField");
            assertNotNull(tail);
            assertEquals("", tail.getText(), "no tail is configured on a first run");

            tail.setText("n12345");
            tail.fireEvent(new ActionEvent(tail, null));

            assertEquals("N12345", appState.getSoapTail());
            assertEquals("N12345", tail.getText(), "the field follows the normalised value");
        });
    }

    /** The same rule as the mapping table's tails, which is the whole point of sharing it. */
    @Test
    @DisplayName("a malformed tail is refused with the reason beside the field")
    void aMalformedTailIsRefusedWithTheReasonBesideTheField()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(SOAP_TAB);
            TextField tail = (TextField) tab.lookup("#tailField");
            Label error = (Label) tab.lookup("#tailErrorLabel");

            tail.setText("N123");
            tail.fireEvent(new ActionEvent(tail, null));

            assertNull(appState.getSoapTail(), "a rejected entry must change nothing");
            assertTrue(error.isVisible());
            assertTrue(error.getText().contains("N123"), error.getText());
            assertTrue(error.getText().contains(Integer.toString(TailNumber.LENGTH)),
                    "the reason should name the rule, not just repeat the value: " + error.getText());
        });
    }

    /**
     * Empty is a state this setting has and a mapping's tail does not, so clearing the field must clear the setting
     * rather than fail the six-character rule.
     */
    @Test
    @DisplayName("emptying the tail field clears the setting rather than failing the rule")
    void emptyingTheTailFieldClearsTheSetting()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            appState.setSoapTail("N12345");
            Parent tab = viewLoader.loadParent(SOAP_TAB);
            TextField tail = (TextField) tab.lookup("#tailField");
            Label error = (Label) tab.lookup("#tailErrorLabel");
            assertEquals("N12345", tail.getText());

            tail.setText("   ");
            tail.fireEvent(new ActionEvent(tail, null));

            assertNull(appState.getSoapTail());
            assertFalse(error.isVisible(), "clearing a setting is not an error");
        });
    }

    @Test
    @DisplayName("the SOAP tab reset restores its own settings and then disables itself")
    void theSoapTabResetRestoresItsOwnSettings()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(SOAP_TAB);
            Button reset = (Button) tab.lookup("#resetButton");
            assertTrue(reset.isDisabled(), "nothing to reset when everything is already default");

            appState.setSoapIp("10.20.30.40");
            appState.setSoapMessageType(SoapMessageType.TYPE_3);
            appState.setSoapTail("N12345");
            assertFalse(reset.isDisabled());

            // Fired with an empty data file list on purpose: a non-empty one routes through a modal
            // confirmation, and showAndWait would block this thread with nobody to dismiss it.
            reset.fire();

            assertEquals(Settings.DEFAULTS.soap().ip(), appState.getSoapIp());
            assertSame(Settings.DEFAULTS.soap().type(), appState.getSoapMessageType());
            assertNull(appState.getSoapTail());
            assertTrue(reset.isDisabled());
        });
    }

    // --- what is deliberately absent ----------------------------------------------------------

    /**
     * Opacity was a view control rather than a setting, and it is gone rather than moved: it is not in Preferences, and
     * it is not in the ribbon group that used to hold it. Its slot in the ribbon went to three settings that
     * <em>are</em> persisted.
     */
    @Test
    @DisplayName("the opacity slider is gone from every surface, not relocated")
    void theOpacitySliderIsGoneFromEverySurface()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            for (String surface : List.of(GENERAL_TAB, LOG_TAB, MESSAGE_TAB, SOAP_TAB, GLOBAL_GROUP, LOG_GROUP,
                    MESSAGE_GROUP))
            {
                assertNull(viewLoader.loadParent(surface).lookup("#opacitySlider"),
                        "opacity was a view control and was removed, not moved: " + surface);
            }
        });
    }

    /**
     * The Global group holds the settings that belong to no mode, and only those. Playback Speed Factor is a Log-mode
     * setting and lives in the Log group and the Log tab; the template's Sim Factor slider that used to share this
     * group is gone entirely.
     */
    @Test
    @DisplayName("the Global group carries no mode-scoped setting")
    void theGlobalGroupCarriesNoModeScopedSetting()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent global = viewLoader.loadParent(GLOBAL_GROUP);

            assertNull(global.lookup("#simFactorSlider"),
                    "the template's Sim Factor slider should be gone, not renamed in place");
            assertNull(global.lookup("#playbackSpeedSpinner"));
            assertNull(global.lookup("#messageTypeChoice"));
            assertNotNull(global.lookup("#blastPortSpinner"));
            assertNotNull(global.lookup("#singleMessageCheck"));
            assertNotNull(global.lookup("#byteHijackCheck"));
        });
    }

    /**
     * SOAP mode sends rather than listens, so the setting it needs is a destination. A listen port that nothing could
     * ever bind was a setting shaped like a question the mode does not ask.
     */
    @Test
    @DisplayName("the SOAP listen port is gone, replaced by an address")
    void theSoapListenPortIsGone()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(SOAP_TAB);

            assertNull(tab.lookup("#portSpinner"), "the listen port should be gone, not sitting alongside the address");
            assertNotNull(tab.lookup("#ipField"));
        });
    }

    /**
     * The chooser is shared rather than duplicated, which keeps the remembered directory and the dialog's title from
     * drifting apart between the two surfaces.
     */
    @Test
    @DisplayName("both surfaces use one log-folder chooser")
    void bothSurfacesUseOneLogFolderChooser()
    {
        assertSame(context.getBean(LogFolderChooser.class), context.getBean(LogFolderChooser.class),
                "LogFolderChooser must be a singleton, or the remembered directory resets");
    }

    /**
     * The data file chooser has one surface today, so the singleton is not about sharing — it is about the remembered
     * directory outliving the prototype controller that opened it. A FileChooser held as a field on a prototype
     * controller is discarded with its node tree, and the next Add starts back at the default location.
     */
    @Test
    @DisplayName("the data file chooser is a singleton, so it remembers where you were")
    void theDataFileChooserIsASingleton()
    {
        assertSame(context.getBean(DataFileChooser.class), context.getBean(DataFileChooser.class),
                "DataFileChooser must be a singleton, or the remembered directory resets");
    }
}
