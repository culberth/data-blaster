package com.culberth.tools.datablaster.ui;

import com.culberth.tools.datablaster.model.Mode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Maps a {@link Mode} to the ribbon group that follows it, for the modes that have one.
 *
 * <p><strong>Separate from {@link ViewRegistry}, and not merged with it.</strong> The two look
 * alike — an {@code EnumMap} from mode to FXML path — but they differ on the case that matters:
 * every mode must have a content view, so a miss there is a defect and {@code resourceFor} throws;
 * only some modes have a contextual group, so a miss here is the normal answer for SOAP and REST
 * and comes back as an empty {@link Optional}. One class with two lookups that behave oppositely on
 * a missing key would be a worse thing to read than two small classes.
 *
 * <p><strong>Why only Log and Message have one.</strong> A ribbon is for controls you reach for
 * repeatedly. Playback speed is scrubbed while watching something and message type is flipped
 * between sends, so both earn the space. SOAP's port is set once and belongs in Preferences, and
 * REST has no behaviour to configure — giving them groups would fill the ribbon with controls
 * nobody reaches for and put a set-once port one mis-click away. The mechanism handles all four
 * regardless, so adding a group later is one FXML and one line here.
 */
@Component
public class RibbonGroupRegistry {

    private final Map<Mode, String> groupsByMode = new EnumMap<>(Mode.class);

    public RibbonGroupRegistry() {
        register(Mode.LOG, "/fxml/ribbon/log-group.fxml");
        register(Mode.MESSAGE, "/fxml/ribbon/message-group.fxml");
        // SOAP and REST deliberately have none. See the class Javadoc.
    }

    public final void register(Mode mode, String fxmlClasspathResource) {
        groupsByMode.put(mode, fxmlClasspathResource);
    }

    /**
     * The contextual group for {@code mode}, or empty when that mode has none.
     *
     * <p>Empty is a normal answer, not a failure — the ribbon simply shows nothing for that mode.
     */
    public Optional<String> groupFor(Mode mode) {
        return Optional.ofNullable(groupsByMode.get(mode));
    }
}
