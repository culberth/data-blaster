package com.culberth.tools.datablaster.ui;

import javafx.stage.Stage;
import org.springframework.stereotype.Component;

/**
 * Holds the primary {@link Stage} so that any bean needing a window owner — a dialog, an
 * error alert raised by a background service — can obtain one without depending on
 * {@code MainController}. The stage is registered here once, during application start-up.
 */
@Component
public class StageRegistry {

    private volatile Stage primaryStage;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    /** The primary stage, or {@code null} if the UI has not started yet. */
    public Stage getPrimaryStage() {
        return primaryStage;
    }
}
