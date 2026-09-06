package com.culberth.tools.datablaster.ui;

import com.culberth.tools.datablaster.model.Mode;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Maps a {@link Mode} to the FXML that renders it, so adding or repointing a mode's view is a registry entry rather
 * than another hardcoded path in another handler.
 *
 * <p>
 * <strong>Keyed by the enum, not by a view id string.</strong> The template kept a free-string id here and a matching
 * string in the ribbon's {@code userData}, wired together by nothing — a typo in either produced a button that threw
 * the first time it was pressed, in a build that was otherwise green. Half of that risk is now a compiler error; what
 * remains is the {@code userData} side, which {@code ModeGroupViewIdTest} checks parses to a real constant.
 *
 * <p>
 * <strong>Each view now names its mode.</strong> The template's {@code view1.fxml} through {@code view4.fxml} said
 * nothing about Log, Message, SOAP or REST; keying this registry by the enum first is what let replacing them be a
 * rename rather than a rewiring. The views are still placeholders in the sense that no mode has behaviour — but they
 * say so, and the three modes with settings show them live.
 */
@Component
public class ViewRegistry
{

    private final Map<Mode, String> viewsByMode = new EnumMap<>(Mode.class);

    public ViewRegistry()
    {
        register(Mode.LOG, "/fxml/log-view.fxml");
        register(Mode.MESSAGE, "/fxml/message-view.fxml");
        register(Mode.SOAP, "/fxml/soap-view.fxml");
        register(Mode.REST, "/fxml/rest-view.fxml");
    }

    public final void register(Mode mode, String fxmlClasspathResource)
    {
        viewsByMode.put(mode, fxmlClasspathResource);
    }

    /** @throws IllegalArgumentException if no view is registered for {@code mode}. */
    public String resourceFor(Mode mode)
    {
        String resource = viewsByMode.get(mode);
        if (resource == null)
        {
            // Constant names, not Mode.toString(): this message is read by whoever is looking for
            // the missing registration, and "Log" is not what they would grep for.
            throw new IllegalArgumentException("No view registered for mode " + (mode == null ? "null" : mode.name())
                    + "; registered modes: " + viewsByMode.keySet().stream().map(Mode::name).toList());
        }
        return resource;
    }
}
