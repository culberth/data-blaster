package com.example.jfxribbon.ui;

import com.example.jfxribbon.ViewLoader;
import java.io.IOException;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import org.springframework.stereotype.Component;

/**
 * Swaps the content view inside a host pane. Which view is current is recorded in
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
     * Replaces {@code container}'s content with the view registered under {@code viewId}.
     *
     * @throws IOException if the view's FXML cannot be loaded
     * @throws IllegalArgumentException if no view is registered under {@code viewId}
     */
    public void switchTo(Pane container, String viewId) throws IOException {
        Parent view = viewLoader.loadParent(viewRegistry.resourceFor(viewId));
        container.getChildren().setAll(view);
    }
}
