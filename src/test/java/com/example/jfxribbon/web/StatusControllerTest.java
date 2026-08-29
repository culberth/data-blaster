package com.example.jfxribbon.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class StatusControllerTest {

    private final StatusController controller = new StatusController();

    @Test
    void reportsTheApplicationAndItsState() {
        assertEquals("JFXRibbon", controller.status().get("app"));
        assertEquals("running", controller.status().get("state"));
    }

    @Test
    void doesNotReuseActuatorVocabulary() {
        // {"status":"UP"} would collide with Actuator's own health endpoint if it is ever added.
        assertFalse(controller.status().containsKey("status"), controller.status().toString());
    }
}
