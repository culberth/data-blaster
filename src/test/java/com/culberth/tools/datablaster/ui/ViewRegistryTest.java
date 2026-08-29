package com.culberth.tools.datablaster.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ViewRegistryTest {

    private final ViewRegistry registry = new ViewRegistry();

    @Test
    void resolvesEveryViewRegisteredByDefault() {
        for (int i = 1; i <= 4; i++) {
            assertEquals("/fxml/view" + i + ".fxml", registry.resourceFor("view" + i));
        }
    }

    @Test
    void defaultViewIsTheFirstRegistered() {
        assertEquals("view1", registry.defaultViewId());
    }

    @Test
    void unknownIdFailsWithAMessageListingWhatIsKnown() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> registry.resourceFor("view9"));
        assertTrue(e.getMessage().contains("view9"), e.getMessage());
        assertTrue(e.getMessage().contains("view1"), e.getMessage());
    }

    @Test
    void registeredViewsAreResolvable() {
        registry.register("custom", "/fxml/custom.fxml");
        assertEquals("/fxml/custom.fxml", registry.resourceFor("custom"));
    }
}
