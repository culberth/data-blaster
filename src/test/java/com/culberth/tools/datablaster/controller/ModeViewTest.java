package com.culberth.tools.datablaster.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.culberth.tools.datablaster.HeadlessToolkit;
import com.culberth.tools.datablaster.ViewLoader;
import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.MessageType;
import com.culberth.tools.datablaster.model.Mode;
import com.culberth.tools.datablaster.model.PortTailMapping;
import com.culberth.tools.datablaster.model.Settings;
import com.culberth.tools.datablaster.ui.ViewRegistry;
import java.io.File;
import java.util.List;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The four mode views: that each names its mode, shows that mode's live configuration, and is
 * honest about having no behaviour.
 *
 * <p>These are still placeholders — no mode does anything, which is v1's stated boundary. What they
 * are not is <em>silent</em> placeholders. The read-outs are bound to {@link AppState}, so this
 * suite doubles as the end-to-end check that a setting written in the ribbon or in Preferences is
 * the same value a third, independent reader sees.
 */
@SpringBootTest
class ModeViewTest {

    @Autowired
    private ViewLoader viewLoader;

    @Autowired
    private AppState appState;

    @Autowired
    private ViewRegistry viewRegistry;

    @BeforeAll
    static void startToolkit() {
        HeadlessToolkit.start();
        HeadlessToolkit.onFxThread(AppState::markFxApplicationThread);
    }

    @AfterEach
    void resetSharedState() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setLogFolder(null);
            appState.setPlaybackSpeedFactor(Settings.PLAYBACK_SPEED_DEFAULT);
            appState.setPortTailMappings(List.of());
            appState.setMessageType(Settings.DEFAULTS.message().type());
            appState.setSoapPort(Settings.DEFAULTS.soap().port());
        });
    }

    private Parent viewFor(Mode mode) throws java.io.IOException {
        return viewLoader.loadParent(viewRegistry.resourceFor(mode));
    }

    private static String textOf(Parent view, String id) {
        Label label = (Label) view.lookup("#" + id);
        assertNotNull(label, id + " should be in the view");
        return label.getText();
    }

    /**
     * The rename is the point of this one. The registry used to map every mode to a
     * {@code viewN.fxml} that said nothing about it, and a view showing the wrong mode's name is a
     * mis-wiring no compiler catches.
     */
    @Test
    @DisplayName("every mode resolves to a view that names it")
    void everyModeResolvesToAViewThatNamesIt() {
        HeadlessToolkit.onFxThread(() -> {
            for (Mode mode : Mode.values()) {
                Parent view = viewFor(mode);
                boolean namesItsMode = view.lookupAll(".mode-view-title").stream()
                        .anyMatch(node -> node instanceof Label label
                                && mode.toString().equals(label.getText()));

                assertTrue(namesItsMode,
                        mode + " resolves to " + viewRegistry.resourceFor(mode)
                                + ", which does not have " + mode + " as its title");
            }
        });
    }

    @Test
    @DisplayName("the Log view shows the folder, the speed and the mapping count, live")
    void theLogViewShowsItsConfigurationLive() {
        HeadlessToolkit.onFxThread(() -> {
            Parent view = viewFor(Mode.LOG);
            assertEquals("(none selected)", textOf(view, "logFolderValue"));
            assertEquals("none", textOf(view, "mappingCountValue"));

            File folder = new File(System.getProperty("java.io.tmpdir"), "datablaster-logs");
            appState.setLogFolder(folder);
            appState.setPlaybackSpeedFactor(2.5);
            appState.addPortTailMapping(PortTailMapping.of(5001, "N12345"));

            // Bound, not assigned: the view was built before any of these were set.
            assertEquals(folder.getAbsolutePath(), textOf(view, "logFolderValue"));
            assertTrue(textOf(view, "playbackSpeedValue").startsWith("2.50"),
                    textOf(view, "playbackSpeedValue"));
            assertEquals("1 mapping", textOf(view, "mappingCountValue"));

            appState.addPortTailMapping(PortTailMapping.of(5002, "123456"));
            assertEquals("2 mappings", textOf(view, "mappingCountValue"),
                    "the count follows the collection, which a ChangeListener would not");
        });
    }

    @Test
    @DisplayName("the Message view shows the type using the same display name the choosers do")
    void theMessageViewShowsTheType() {
        HeadlessToolkit.onFxThread(() -> {
            Parent view = viewFor(Mode.MESSAGE);

            appState.setMessageType(MessageType.MESSAGE_3);

            assertEquals(MessageType.MESSAGE_3.toString(), textOf(view, "messageTypeValue"),
                    "three surfaces render this enum; they must agree on what it is called");
        });
    }

    @Test
    @DisplayName("the SOAP view shows the configured port, live")
    void theSoapViewShowsThePort() {
        HeadlessToolkit.onFxThread(() -> {
            Parent view = viewFor(Mode.SOAP);
            assertEquals("8081", textOf(view, "portValue"));

            appState.setSoapPort(9443);

            assertEquals("9443", textOf(view, "portValue"));
        });
    }

    /**
     * REST is the one mode with nothing to show, and the note is the whole content of its view. A
     * blank view would be indistinguishable from a broken one, which is exactly what shipping a
     * visible toggle instead of a disabled one was meant to avoid.
     */
    @Test
    @DisplayName("the REST view says plainly that it is not implemented")
    void theRestViewSaysPlainlyThatItIsNotImplemented() {
        HeadlessToolkit.onFxThread(() -> {
            String note = textOf(viewFor(Mode.REST), "notImplementedNote");

            assertTrue(note.toLowerCase().contains("not implemented"), note);
            assertFalse(note.isBlank());
        });
    }

    /**
     * The views used to carry {@code style="-fx-font-size: 22px;"} inline — a literal the theme
     * tokens could never reach, so the heading kept its light-theme colour when everything around
     * it went dark. Style classes are what let {@code ThemeContrastTest} have any say over them.
     */
    @Test
    @DisplayName("no mode view styles itself with an inline literal")
    void noModeViewStylesItselfWithAnInlineLiteral() {
        HeadlessToolkit.onFxThread(() -> {
            for (Mode mode : Mode.values()) {
                Parent view = viewFor(mode);
                assertTrue(view.getStyle().isBlank(), mode + " root carries an inline style");
                view.lookupAll(".mode-view-title").forEach(node ->
                        assertTrue(node.getStyle().isBlank(),
                                mode + " title carries an inline style: " + node.getStyle()));
                view.lookupAll(".mode-view-value").forEach(node ->
                        assertTrue(node.getStyle().isBlank(),
                                mode + " read-out carries an inline style: " + node.getStyle()));
            }
        });
    }
}
