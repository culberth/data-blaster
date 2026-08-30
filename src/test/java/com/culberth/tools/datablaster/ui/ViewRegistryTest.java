package com.culberth.tools.datablaster.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.culberth.tools.datablaster.model.Mode;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ViewRegistryTest {

    private final ViewRegistry registry = new ViewRegistry();

    /**
     * Every mode, not four hardcoded lookups. A fifth constant added without a view would fail here
     * rather than the first time someone pressed its button.
     */
    @Test
    @DisplayName("every mode resolves to a view")
    void everyModeResolvesToAView() {
        for (Mode mode : Mode.values()) {
            assertDoesNotThrow(() -> registry.resourceFor(mode),
                    mode + " has no view registered");
        }
    }

    @Test
    @DisplayName("no two modes share a view")
    void noTwoModesShareAView() {
        Map<Mode, String> resources = new EnumMap<>(Mode.class);
        for (Mode mode : Mode.values()) {
            resources.put(mode, registry.resourceFor(mode));
        }

        assertEquals(Mode.values().length, resources.values().stream().distinct().count(),
                "two modes pointing at one view would look like a broken switch: " + resources);
    }

    /**
     * The lookup can still miss — {@code register} is public and a fork may repoint the registry —
     * so the failure names what it does know rather than surfacing as a null two frames later.
     */
    @Test
    @DisplayName("a lookup that misses fails with a message listing what is known")
    void aLookupThatMissesFailsWithAMessageListingWhatIsKnown() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> registry.resourceFor(null));

        assertTrue(e.getMessage().contains("registered modes"), e.getMessage());
        assertTrue(e.getMessage().contains("LOG"), e.getMessage());
    }

    @Test
    @DisplayName("a re-registered mode resolves to its new view")
    void aReRegisteredModeResolvesToItsNewView() {
        registry.register(Mode.REST, "/fxml/custom.fxml");
        assertEquals("/fxml/custom.fxml", registry.resourceFor(Mode.REST));
    }
}
