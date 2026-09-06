package com.culberth.tools.datablaster.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The REST mode view.
 *
 * <p>
 * REST is the one mode with nothing to show. The other three are configured but unimplemented; REST is not designed yet
 * and has no settings at all, so this view holds a note and no read-outs. It exists rather than the toggle being
 * disabled because a dead button with no explanation leaves the user guessing whether the application is broken.
 *
 * <p>
 * <strong>It reads no {@code AppState}, and still has a field.</strong> The note's {@code fx:id} is dereferenced below
 * so that {@code FxmlSmokeTest} keeps its leverage over this file: that suite catches a dropped {@code fx:id} or a
 * renamed controller precisely because every {@code initialize()} here touches its injected fields, and a controller
 * with none would load clean however badly its FXML had been broken.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RestViewController
{

    @FXML
    private Label notImplementedNote;

    @FXML
    private void initialize()
    {
        if (notImplementedNote.getText().isBlank())
        {
            throw new IllegalStateException("The REST view must say why it is empty; an unexplained blank view is the "
                    + "thing this placeholder exists to avoid");
        }
    }
}
