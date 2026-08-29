package com.culberth.tools.datablaster.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Maps a stable view id to the FXML that renders it, so adding a view is a registry entry
 * rather than another hardcoded path in another handler.
 */
@Component
public class ViewRegistry {

    private final Map<String, String> viewsById = new LinkedHashMap<>();

    public ViewRegistry() {
        register("view1", "/fxml/view1.fxml");
        register("view2", "/fxml/view2.fxml");
        register("view3", "/fxml/view3.fxml");
        register("view4", "/fxml/view4.fxml");
    }

    public final void register(String viewId, String fxmlClasspathResource) {
        viewsById.put(viewId, fxmlClasspathResource);
    }

    /** @throws IllegalArgumentException if no view is registered under {@code viewId}. */
    public String resourceFor(String viewId) {
        String resource = viewsById.get(viewId);
        if (resource == null) {
            throw new IllegalArgumentException(
                    "No view registered under id '" + viewId + "'; known ids: " + viewsById.keySet());
        }
        return resource;
    }

    public String defaultViewId() {
        return viewsById.keySet().iterator().next();
    }
}
