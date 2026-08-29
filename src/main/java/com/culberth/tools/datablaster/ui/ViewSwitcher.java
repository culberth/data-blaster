package com.culberth.tools.datablaster.ui;

import com.culberth.tools.datablaster.ViewLoader;
import com.culberth.tools.datablaster.model.Mode;
import java.io.IOException;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import org.springframework.stereotype.Component;

/**
 * Swaps the content view inside a host pane. Which mode is current is recorded in
 * {@code AppState} by whoever requests the change; this class only performs the swap.
 *
 * <p>This is a stateless singleton on purpose: the host pane is passed in per call rather than
 * retained. Holding it in a field would put a node owned by a prototype, FXML-created controller
 * inside a singleton, so a second shell would silently take over the first shell's content pane.
 */
@Component
public class ViewSwitcher {

    private final ViewLoader viewLoader;
    private final ViewRegistry viewRegistry;

    public ViewSwitcher(ViewLoader viewLoader, ViewRegistry viewRegistry) {
        this.viewLoader = viewLoader;
        this.viewRegistry = viewRegistry;
    }

    /**
     * Replaces {@code container}'s content with the view registered for {@code mode}.
     *
     * @throws IOException if the view's FXML cannot be loaded
     * @throws IllegalArgumentException if no view is registered for {@code mode}
     */
    public void switchTo(Pane container, Mode mode) throws IOException {
        Parent view = viewLoader.loadParent(viewRegistry.resourceFor(mode));
        container.getChildren().setAll(view);
    }
}
