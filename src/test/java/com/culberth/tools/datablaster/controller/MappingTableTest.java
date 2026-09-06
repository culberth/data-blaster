package com.culberth.tools.datablaster.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.culberth.tools.datablaster.HeadlessToolkit;
import com.culberth.tools.datablaster.ViewLoader;
import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.PortTailMapping;
import java.util.List;
import javafx.event.Event;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The port-to-tail mapping editor: acceptance criterion A4.
 *
 * <p>
 * {@code PortTailMappingTest} already pins the rules themselves, at the model layer and without a toolkit. What this
 * adds is that the editor actually enforces them <em>at entry</em> and says why — the half that lives in the UI and
 * that a model test cannot reach. A validator nothing calls, or one whose message is swallowed, would leave that suite
 * green and the feature broken.
 *
 * <p>
 * The Log tab is loaded directly rather than through the dialog, for the {@code TabPane} reason given in
 * {@code PreferencesSurfaceTest}.
 */
@SpringBootTest
class MappingTableTest
{

    private static final String LOG_TAB = "/fxml/preferences/log-tab.fxml";

    @Autowired
    private ViewLoader viewLoader;

    @Autowired
    private AppState appState;

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
            appState.setPortTailMappings(List.of());
            appState.setBlastPort(8081);
        });
    }

    // --- helpers ------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static TableView<PortTailMapping> table(Parent tab)
    {
        TableView<PortTailMapping> table = (TableView<PortTailMapping>) tab.lookup("#mappingTable");
        assertNotNull(table, "the mapping table should be in the Log tab");
        return table;
    }

    private static void typeAndAdd(Parent tab, String port, String tail)
    {
        ((TextField) tab.lookup("#newPortField")).setText(port);
        ((TextField) tab.lookup("#newTailField")).setText(tail);
        ((Button) tab.lookup("#addButton")).fire();
    }

    private static Label error(Parent tab)
    {
        return (Label) tab.lookup("#mappingErrorLabel");
    }

    /**
     * Commits an edit the way the cell does, by firing the column's edit-commit handler.
     *
     * <p>
     * Driving the cell's text field itself would need a shown stage and a focus model; the handler is where this
     * controller's logic actually lives, and firing it is the same event the cell would raise.
     */
    @SuppressWarnings("unchecked")
    private static void commitEdit(Parent tab, String header, int row, String newValue)
    {
        TableView<PortTailMapping> table = table(tab);
        // Found by header text, not by lookup: a TableColumn is not a Node, so it is not in the
        // scene graph a CSS lookup walks.
        TableColumn<PortTailMapping, String> column = table.getColumns().stream()
                .filter(c -> header.equals(c.getText())).map(c -> (TableColumn<PortTailMapping, String>) c).findFirst()
                .orElseThrow(() -> new AssertionError("no column headed " + header));

        Event.fireEvent(column, new TableColumn.CellEditEvent<>(table, new TablePosition<>(table, row, column),
                TableColumn.editCommitEvent(), newValue));
    }

    // --- adding -------------------------------------------------------------------------------

    @Test
    @DisplayName("a valid mapping is added and the entry fields are cleared")
    void aValidMappingIsAdded()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(LOG_TAB);

            typeAndAdd(tab, "5001", "N12345");

            assertEquals(List.of(PortTailMapping.of(5001, "N12345")), appState.portTailMappings());
            assertEquals(1, table(tab).getItems().size(), "the table shows the shared state");
            assertEquals("", ((TextField) tab.lookup("#newPortField")).getText(),
                    "the fields should be ready for the next entry");
            assertFalse(error(tab).isVisible(), "a successful add leaves no error behind");
        });
    }

    @Test
    @DisplayName("a tail is normalised on the way in")
    void aTailIsNormalisedOnTheWayIn()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(LOG_TAB);

            typeAndAdd(tab, " 5001 ", " n12345 ");

            assertEquals(List.of(PortTailMapping.of(5001, "N12345")), appState.portTailMappings());
        });
    }

    // --- rejection, at entry, with the reason shown -------------------------------------------

    @Test
    @DisplayName("a malformed tail is rejected at entry and the reason is shown")
    void aMalformedTailIsRejectedAtEntry()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(LOG_TAB);

            typeAndAdd(tab, "5001", "N123");

            assertTrue(appState.portTailMappings().isEmpty(), "nothing should have been added");
            assertTrue(error(tab).isVisible(), "the reason must be visible, not swallowed");
            assertTrue(error(tab).isManaged(), "an invisible-but-managed label is a blank gap");
            assertTrue(error(tab).getText().contains("Tail number"), error(tab).getText());
        });
    }

    @Test
    @DisplayName("a port that is not a number is rejected with a message about ports")
    void aPortThatIsNotANumberIsRejected()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(LOG_TAB);

            typeAndAdd(tab, "not-a-port", "N12345");

            assertTrue(appState.portTailMappings().isEmpty());
            assertTrue(error(tab).getText().contains("Port"), error(tab).getText());
        });
    }

    @Test
    @DisplayName("a port outside the range is rejected")
    void aPortOutsideTheRangeIsRejected()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(LOG_TAB);

            typeAndAdd(tab, "70000", "N12345");

            assertTrue(appState.portTailMappings().isEmpty());
            assertTrue(error(tab).getText().contains("65535"), error(tab).getText());
        });
    }

    @Test
    @DisplayName("a duplicate port is rejected, naming the port")
    void aDuplicatePortIsRejected()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(LOG_TAB);
            typeAndAdd(tab, "5001", "N12345");

            typeAndAdd(tab, "5001", "123456");

            assertEquals(1, appState.portTailMappings().size(), "the first mapping should survive");
            assertTrue(error(tab).getText().contains("5001"), error(tab).getText());
        });
    }

    /** The direction the file format cannot enforce, so the editor has to. */
    @Test
    @DisplayName("a duplicate tail is rejected even when it differs only in case")
    void aDuplicateTailIsRejectedEvenWhenItDiffersOnlyInCase()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(LOG_TAB);
            typeAndAdd(tab, "5001", "N12345");

            typeAndAdd(tab, "5002", "n12345");

            assertEquals(1, appState.portTailMappings().size());
            assertTrue(error(tab).getText().contains("N12345"), error(tab).getText());
        });
    }

    @Test
    @DisplayName("a later valid entry clears the previous error")
    void aLaterValidEntryClearsThePreviousError()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(LOG_TAB);
            typeAndAdd(tab, "5001", "bad");
            assertTrue(error(tab).isVisible());

            typeAndAdd(tab, "5001", "N12345");

            assertFalse(error(tab).isVisible(), "a stale error beside a successful edit misleads");
        });
    }

    // --- editing ------------------------------------------------------------------------------

    @Test
    @DisplayName("an existing tail can be edited in place")
    void anExistingTailCanBeEditedInPlace()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(LOG_TAB);
            typeAndAdd(tab, "5001", "N12345");

            commitEdit(tab, "Tail No.", 0, "123456");

            assertEquals(List.of(PortTailMapping.of(5001, "123456")), appState.portTailMappings());
        });
    }

    @Test
    @DisplayName("an existing port can be edited in place")
    void anExistingPortCanBeEditedInPlace()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(LOG_TAB);
            typeAndAdd(tab, "5001", "N12345");

            commitEdit(tab, "Port", 0, "5002");

            assertEquals(List.of(PortTailMapping.of(5002, "N12345")), appState.portTailMappings());
        });
    }

    @Test
    @DisplayName("an edit that breaks a rule is refused and the old value stands")
    void anEditThatBreaksARuleIsRefused()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(LOG_TAB);
            typeAndAdd(tab, "5001", "N12345");
            typeAndAdd(tab, "5002", "123456");

            // Would collide with the tail already on port 5001.
            commitEdit(tab, "Tail No.", 1, "N12345");

            assertEquals(List.of(PortTailMapping.of(5001, "N12345"), PortTailMapping.of(5002, "123456")),
                    appState.portTailMappings(), "a rejected edit must leave the table exactly as it was");
            assertTrue(error(tab).isVisible(), "and must say why");
        });
    }

    // --- removing -----------------------------------------------------------------------------

    @Test
    @DisplayName("Remove is disabled until a row is selected, then removes that row")
    void removeIsDisabledUntilARowIsSelected()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            Parent tab = viewLoader.loadParent(LOG_TAB);
            Button remove = (Button) tab.lookup("#removeButton");
            typeAndAdd(tab, "5001", "N12345");
            typeAndAdd(tab, "5002", "123456");

            assertTrue(remove.isDisabled(), "nothing is selected yet");

            table(tab).getSelectionModel().select(0);
            assertFalse(remove.isDisabled());
            remove.fire();

            assertEquals(List.of(PortTailMapping.of(5002, "123456")), appState.portTailMappings());
        });
    }

    // --- the Blast Port collision notice (R19) ------------------------------------------------

    /**
     * A warning, not a block. Nothing binds either port yet and the two settings are independent, so refusing the entry
     * would be inventing a rule; but the conflict it predicts would surface much later, at bind time, in a different
     * mode, with nothing pointing back here.
     */
    @Test
    @DisplayName("a mapping port that matches the Blast Port is flagged but allowed")
    void aMappingPortThatMatchesTheBlastPortIsFlaggedButAllowed()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            appState.setBlastPort(8081);
            Parent tab = viewLoader.loadParent(LOG_TAB);
            Label notice = (Label) tab.lookup("#portCollisionLabel");
            assertFalse(notice.isVisible(), "nothing collides yet");

            typeAndAdd(tab, "8081", "N12345");

            assertEquals(1, appState.portTailMappings().size(), "the mapping is allowed");
            assertTrue(notice.isVisible(), "and flagged");
            assertTrue(notice.getText().contains("8081"), notice.getText());
        });
    }

    @Test
    @DisplayName("the collision notice clears when the collision goes away")
    void theCollisionNoticeClearsWhenTheCollisionGoesAway()
    {
        HeadlessToolkit.onFxThread(() ->
        {
            appState.setBlastPort(8081);
            Parent tab = viewLoader.loadParent(LOG_TAB);
            Label notice = (Label) tab.lookup("#portCollisionLabel");
            typeAndAdd(tab, "8081", "N12345");
            assertTrue(notice.isVisible());

            appState.removePortTailMappingForPort(8081);

            assertFalse(notice.isVisible(), "a warning about a state that no longer holds is noise");
        });
    }
}
