package com.example.jfxribbon;

/**
 * Separate entry point (does not extend {@link javafx.application.Application}) so the app
 * can be started from a repackaged Spring Boot fat jar without JavaFX's launcher checks
 * complaining about a missing "main class must not extend Application" setup.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        JFXRibbonApplication.main(args);
    }
}
