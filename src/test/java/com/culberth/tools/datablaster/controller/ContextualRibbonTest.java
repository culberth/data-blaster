package com.culberth.tools.datablaster.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.culberth.tools.datablaster.HeadlessToolkit;
import com.culberth.tools.datablaster.ViewLoader;
import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.Mode;
import com.culberth.tools.datablaster.controller.ribbon.ContextualGroupController;
import com.culberth.tools.datablaster.model.Settings;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * That the ribbon actually follows the selected mode.
 *
 * <p>The mechanism has three halves that fail differently: the slot has to show the right group,
 * it has to <em>swap</em> when the mode changes rather than only render what was current when it
 * was built, and it has to take no space at all for the modes that have no group. The last one is
 * the easiest to get wrong invisibly — an empty but managed container leaves a gap in the ribbon
 * and doubles the spacing between its neighbours, which no assertion about children would catch.
 *
 * <p>Needs a toolkit because it builds real node trees, and runs headless on Monocle like the other
 * three suites that do.
 */
@SpringBootTest
class ContextualRibbonTest {

    private static final String CONTEXTUAL_GROUP = "/fxml/ribbon/contextual-group.fxml";

    /** An fx:id unique to each mode's group, used to tell which one is on screen. */
    private static final String LOG_MARKER = "#playbackSpeedSlider";
    private static final String MESSAGE_MARKER = "#messageTypeChoice";

    @Autowired
    private ViewLoader viewLoader;

    @Autowired
    private AppState appState;

    @BeforeAll
    static void startToolkit() {
        HeadlessToolkit.start();
        HeadlessToolkit.onFxThread(AppState::markFxApplicationThread);
    }

    /**
     * {@link AppState} is an application-lifetime singleton shared across this context, so a mode
     * left selected here would decide another suite's starting state.
     */
    @AfterEach
    void resetSharedState() {
        HeadlessToolkit.onFxThread(() -> appState.setCurrentMode(Settings.DEFAULTS.mode()));
    }

    /** Declares the checked exception rather than swallowing it; every caller is inside a
     * {@code HeadlessToolkit.ThrowingRunnable}, which allows it through. */
    private Parent contextualSlot() throws java.io.IOException {
        return viewLoader.loadParent(CONTEXTUAL_GROUP);
    }

    @Test
    @DisplayName("the slot shows the group for the mode already selected when it is built")
    void theSlotShowsTheGroupForTheModeAlreadySelectedWhenItIsBuilt() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setCurrentMode(Mode.MESSAGE);

            Parent slot = contextualSlot();

            assertNotNull(slot.lookup(MESSAGE_MARKER),
                    "a slot built while Message is selected should already hold the Message group; "
                            + "waiting for a change event would leave a second shell empty");
            assertNull(slot.lookup(LOG_MARKER));
        });
    }

    @Test
    @DisplayName("changing the mode swaps the group")
    void changingTheModeSwapsTheGroup() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setCurrentMode(Mode.LOG);
            Parent slot = contextualSlot();
            assertNotNull(slot.lookup(LOG_MARKER), "Log mode should show the Log group");

            appState.setCurrentMode(Mode.MESSAGE);

            assertNull(slot.lookup(LOG_MARKER), "the Log group should have been removed");
            assertNotNull(slot.lookup(MESSAGE_MARKER), "the Message group should have replaced it");
        });
    }

    /**
     * SOAP and REST have no group by decision, not by omission. The slot must disappear rather than
     * render as an empty box: an unmanaged node takes no layout space, whereas an empty managed one
     * leaves a hole with the ribbon's spacing on both sides of it.
     */
    @Test
    @DisplayName("a mode with no group leaves the slot empty and unmanaged")
    void aModeWithNoGroupLeavesTheSlotEmptyAndUnmanaged() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setCurrentMode(Mode.LOG);
            Pane slot = (Pane) contextualSlot();
            assertTrue(slot.isManaged(), "Log has a group, so the slot should be taking space");

            appState.setCurrentMode(Mode.SOAP);

            assertTrue(slot.getChildren().isEmpty(), "SOAP has no contextual group");
            assertFalse(slot.isManaged(), "an empty slot must not reserve width in the ribbon");
            assertFalse(slot.isVisible());

            appState.setCurrentMode(Mode.REST);
            assertTrue(slot.getChildren().isEmpty(), "REST has no contextual group either");
            assertFalse(slot.isManaged());
        });
    }

    /**
     * The slot must survive a garbage collection.
     *
     * <p>This is a regression test for a real defect, not a hypothetical. Every other controller in
     * this project is reachable from its own nodes by accident — an {@code onAction} handler, or a
     * listener lambda on one of its own controls, gives the scene graph a strong reference back.
     * The contextual slot has neither: it only observes {@code AppState}, and it observes weakly,
     * as a prototype-scoped controller must. So once {@code FXMLLoader} returned, nothing referred
     * to it, the weak listener cleared at the next collection, and the ribbon stopped following the
     * mode — at an arbitrary later moment, looking like anything but a lifetime problem. It
     * surfaced here as one suite failing only under the allocation pressure of a full {@code verify}.
     *
     * <p>Asserted structurally rather than by forcing a collection and hoping: {@code System.gc()}
     * is a hint, so a behavioural version of this test could pass while the bug was present.
     */
    @Test
    @DisplayName("the node tree holds its controller, so a collection cannot detach the listener")
    void theNodeTreeHoldsItsController() {
        HeadlessToolkit.onFxThread(() -> {
            ViewLoader.LoadedView loaded = viewLoader.load(CONTEXTUAL_GROUP);

            assertSame(loaded.controller(),
                    loaded.root().getProperties().get(ContextualGroupController.CONTROLLER_KEY),
                    "nothing else refers to this controller; without this the weak mode listener "
                            + "clears at the next GC and the ribbon quietly stops swapping");
        });
    }

    /** And back again: the slot has to recover, not just collapse once. */
    @Test
    @DisplayName("the slot comes back when a mode with a group is reselected")
    void theSlotComesBackWhenAModeWithAGroupIsReselected() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setCurrentMode(Mode.SOAP);
            Pane slot = (Pane) contextualSlot();
            assertFalse(slot.isManaged());

            appState.setCurrentMode(Mode.LOG);

            assertTrue(slot.isManaged(), "the slot should take space again");
            assertTrue(slot.isVisible());
            assertNotNull(slot.lookup(LOG_MARKER));
        });
    }

    /**
     * The log folder moved out of the always-visible Tools group and into Log's contextual group,
     * because it is a Log-mode setting. This is the assertion that says so: it must not be reachable
     * while another mode is selected.
     */
    @Test
    @DisplayName("the log folder is only offered while Log mode is selected")
    void theLogFolderIsOnlyOfferedWhileLogModeIsSelected() {
        HeadlessToolkit.onFxThread(() -> {
            appState.setCurrentMode(Mode.LOG);
            Parent slot = contextualSlot();
            assertNotNull(slot.lookup("#logFolderLabel"),
                    "the log folder read-out belongs to the Log group");

            appState.setCurrentMode(Mode.MESSAGE);

            assertNull(slot.lookup("#logFolderLabel"),
                    "a Log-mode setting must not stay reachable from the ribbon in another mode");
        });
    }
}
