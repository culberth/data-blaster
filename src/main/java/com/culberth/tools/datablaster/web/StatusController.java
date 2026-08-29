package com.culberth.tools.datablaster.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness endpoint for a companion tool. Deliberately NOT {@code /api/health} returning
 * {@code {"status":"UP"}}: that is Spring Boot Actuator's vocabulary, and reusing it would leave
 * two endpoints that can disagree if Actuator is ever added, with monitors pointed at the wrong one.
 *
 * <p>Host validation is not done here — {@link LoopbackHostFilter} applies it to every path,
 * including the ones this class does not define.
 */
@RestController
public class StatusController {

    @GetMapping("/api/status")
    public Map<String, String> status() {
        return Map.of("app", "Data Blaster", "state", "running");
    }
}
