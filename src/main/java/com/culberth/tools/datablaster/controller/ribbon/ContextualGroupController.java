package com.culberth.tools.datablaster.controller.ribbon;

import com.culberth.tools.datablaster.ViewLoader;
import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.Mode;
import com.culberth.tools.datablaster.ui.RibbonGroupRegistry;
import java.io.IOException;
import java.util.Optional;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.layout.HBox;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The slot in the ribbon that follows the selected mode, swapping in that mode's group.
 *
 * <p><strong>It is itself an ordinary ribbon group, and that is the point.</strong> The shell
 * includes it with one {@code <fx:include>} like any other, and the swapping happens in here rather
 * than in {@code MainController}. Putting it in the shell would have been the obvious move and
 * would have broken the property this ribbon is built on — that adding a group is a new FXML, a new
 * controller and one include, with no edit to the shell and none to another group.
 *
 * <p>It is the same shape as the content-area swap the shell performs, one level down: observe
 * {@code currentMode}, resolve it through a registry, replace the children. The two registries are
 * deliberately separate; see {@link RibbonGroupRegistry} for why.
 *
 * <p><strong>Not every mode has a group.</strong> SOAP and REST have none, so this slot renders
 * nothing and takes no space — it un-manages itself rather than leaving an empty box with a stray
 * separator beside it.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ContextualGroupController {

    private static final System.Logger LOG =
            System.getLogger(ContextualGroupController.class.getName());

    /**
     * Where {@link #initialize()} parks a reference to this controller on its own root node.
     *
     * <p>Public so {@code ContextualRibbonTest} can assert the reference is there. That check is
     * deterministic, where a test that forced a collection and hoped would not be.
     */
    public static final String CONTROLLER_KEY = "datablaster.contextualGroupController";

    @FXML
    private HBox groupHost;

    private final AppState appState;
    private final ViewLoader viewLoader;
    private final RibbonGroupRegistry ribbonGroups;

    /**
     * Held strongly here so the weak registration on the singleton {@link AppState} lives exactly
     * as long as this controller. A plain lambda would pin this slot's scene graph for the life of
     * the application and keep a discarded ribbon loading groups.
     */
    private final ChangeListener<Mode> modeListener = (obs, old, mode) -> showGroupFor(mode);

    /** The mode whose group is on screen, so a repeat of the same mode does not rebuild it. */
    private Mode displayedMode;

    public ContextualGroupController(AppState appState,
                                     ViewLoader viewLoader,
                                     RibbonGroupRegistry ribbonGroups) {
        this.appState = appState;
        this.viewLoader = viewLoader;
        this.ribbonGroups = ribbonGroups;
    }

    @FXML
    private void initialize() {
        // Pin this controller to the node tree it drives. Without this the slot silently stops
        // swapping at some arbitrary later moment, and the failure looks like anything but a
        // lifetime problem.
        //
        // Every other controller here is reachable from its own nodes by accident: an onAction
        // handler, or a listener lambda registered on one of its controls, gives the scene graph a
        // strong reference back. This one has neither -- it only observes AppState, and it observes
        // weakly, as a prototype-scoped controller must. So after FXMLLoader returns, nothing
        // refers to it at all, the weak listener clears at the next collection, and the ribbon stops
        // following the mode.
        //
        // Tying its lifetime to the node it owns is exactly the intended semantics, not a
        // workaround for the weak-listener rule: while this slot is on screen the listener must
        // live, and when the slot is discarded both go together and the listener detaches. That is
        // what the rule asks for.
        groupHost.getProperties().put(CONTROLLER_KEY, this);

        appState.currentModeProperty().addListener(new WeakChangeListener<>(modeListener));

        // Reading AppState here is allowed and publishing is not: FXMLLoader builds depth-first, so
        // this runs before the shell's initialize(). Rendering what is already current also covers
        // the second-shell case, where the mode is set and no change event will ever arrive.
        showGroupFor(appState.getCurrentMode());
    }

    private void showGroupFor(Mode mode) {
        if (mode == displayedMode) {
            return;
        }
        displayedMode = mode;

        Optional<String> resource = mode == null
                ? Optional.empty()
                : ribbonGroups.groupFor(mode);

        if (resource.isEmpty()) {
            // A mode with no contextual group is the normal case for SOAP and REST, not a failure.
            setContent(null);
            return;
        }

        try {
            setContent(viewLoader.loadParent(resource.get()));
        } catch (IOException | RuntimeException e) {
            // Deliberately not a dialog. A ribbon group failing to load is not worth a modal in
            // front of a window that is otherwise working, and this runs during shell construction
            // where an alert would arrive before there is anything to own it. FxmlSmokeTest loads
            // every one of these files through the real factory, so a broken group fails the build
            // rather than reaching a user.
            LOG.log(System.Logger.Level.ERROR,
                    "Could not load the " + mode + " ribbon group from " + resource.get(), e);
            setContent(null);
        }
    }

    private void setContent(Parent group) {
        if (group == null) {
            groupHost.getChildren().clear();
        } else {
            groupHost.getChildren().setAll(group);
        }
        // An empty slot must not reserve width, or the ribbon shows a gap and the spacing between
        // the neighbouring groups doubles for SOAP and REST.
        boolean occupied = group != null;
        groupHost.setVisible(occupied);
        groupHost.setManaged(occupied);
    }
}
