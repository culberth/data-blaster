package com.culberth.tools.datablaster.ui;

import com.culberth.tools.datablaster.model.Mode;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Maps a {@link Mode} to the FXML that renders it, so adding or repointing a mode's view is a
 * registry entry rather than another hardcoded path in another handler.
 *
 * <p><strong>Keyed by the enum, not by a view id string.</strong> The template kept a free-string
 * id here and a matching string in the ribbon's {@code userData}, wired together by nothing — a
 * typo in either produced a button that threw the first time it was pressed, in a build that was
 * otherwise green. Half of that risk is now a compiler error; what remains is the {@code userData}
 * side, which {@code ModeGroupViewIdTest} checks parses to a real constant.
 *
 * <p><strong>The views themselves are still the inherited placeholders.</strong> {@code view1.fxml}
 * through {@code view4.fxml} say nothing about Log, Message, SOAP or REST. Renaming them, and
 * giving REST a view that admits plainly that it is not implemented, is the next change; keying
 * them by mode first is what lets that one be a rename rather than a rewiring.
 */
@Component
public class ViewRegistry {

    private final Map<Mode, String> viewsByMode = new EnumMap<>(Mode.class);

    public ViewRegistry() {
        register(Mode.LOG, "/fxml/view1.fxml");
        register(Mode.MESSAGE, "/fxml/view2.fxml");
        register(Mode.SOAP, "/fxml/view3.fxml");
        register(Mode.REST, "/fxml/view4.fxml");
    }

    public final void register(Mode mode, String fxmlClasspathResource) {
        viewsByMode.put(mode, fxmlClasspathResource);
    }

    /** @throws IllegalArgumentException if no view is registered for {@code mode}. */
    public String resourceFor(Mode mode) {
        String resource = viewsByMode.get(mode);
        if (resource == null) {
            // Constant names, not Mode.toString(): this message is read by whoever is looking for
            // the missing registration, and "Log" is not what they would grep for.
            throw new IllegalArgumentException(
                    "No view registered for mode " + (mode == null ? "null" : mode.name())
                            + "; registered modes: " + viewsByMode.keySet().stream()
                            .map(Mode::name).toList());
        }
        return resource;
    }
}
