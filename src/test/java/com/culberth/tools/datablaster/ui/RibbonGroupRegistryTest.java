package com.culberth.tools.datablaster.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.culberth.tools.datablaster.model.Mode;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which modes carry a contextual ribbon group, and that the files they name exist.
 *
 * <p>Toolkit-free: this is a fact about a map and about what is on the classpath. That the groups
 * also <em>load</em> is {@code FxmlSmokeTest}'s job, and that they are swapped in at the right
 * moment is {@code ContextualRibbonTest}'s.
 */
class RibbonGroupRegistryTest {

    private final RibbonGroupRegistry registry = new RibbonGroupRegistry();

    /**
     * The product decision, pinned. A ribbon is for controls reached for repeatedly: playback speed
     * is scrubbed and message type is flipped between sends, while SOAP's port is set once and REST
     * has no behaviour. If a group is added or removed, this is the line that should have to change.
     */
    @Test
    @DisplayName("only Log and Message have a contextual group")
    void onlyLogAndMessageHaveAContextualGroup() {
        Set<Mode> withGroups = EnumSet.noneOf(Mode.class);
        for (Mode mode : Mode.values()) {
            if (registry.groupFor(mode).isPresent()) {
                withGroups.add(mode);
            }
        }

        assertEquals(EnumSet.of(Mode.LOG, Mode.MESSAGE), withGroups);
    }

    /**
     * A mode with no group is the normal answer, not a failure — unlike {@link ViewRegistry}, where
     * a miss throws. That difference is the whole reason these are two classes.
     */
    @Test
    @DisplayName("a mode without a group returns empty rather than throwing")
    void aModeWithoutAGroupReturnsEmpty() {
        assertTrue(registry.groupFor(Mode.SOAP).isEmpty());
        assertTrue(registry.groupFor(Mode.REST).isEmpty());
        assertTrue(registry.groupFor(null).isEmpty(),
                "the slot asks before it knows the mode is set; null must not blow up the ribbon");
    }

    /**
     * The registry names files by string, which is the one thing the compiler cannot check here.
     * A renamed or misspelled FXML would otherwise surface as a ribbon group that silently fails to
     * load at run time, logged and swallowed by the contextual slot.
     */
    @Test
    @DisplayName("every registered group is actually on the classpath")
    void everyRegisteredGroupIsActuallyOnTheClasspath() {
        for (Mode mode : Mode.values()) {
            Optional<String> resource = registry.groupFor(mode);
            if (resource.isEmpty()) {
                continue;
            }
            assertNotNull(getClass().getResource(resource.get()),
                    mode + " registers " + resource.get() + ", which is not on the classpath");
        }
    }

    @Test
    @DisplayName("no two modes share a group")
    void noTwoModesShareAGroup() {
        long distinct = EnumSet.allOf(Mode.class).stream()
                .map(registry::groupFor)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .count();

        assertEquals(2, distinct, "two modes pointing at one group would look like a broken swap");
    }

    @Test
    @DisplayName("a re-registered mode resolves to its new group")
    void aReRegisteredModeResolvesToItsNewGroup() {
        registry.register(Mode.SOAP, "/fxml/ribbon/soap-group.fxml");

        assertEquals(Optional.of("/fxml/ribbon/soap-group.fxml"), registry.groupFor(Mode.SOAP));
    }
}
