package com.culberth.tools.datablaster.controller;

import com.culberth.tools.datablaster.model.AppState;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The Message mode view: its one setting, read-only, and an honest note that the mode does nothing yet.
 *
 * <p>
 * Bound rather than assigned, so flipping the type in the ribbon is visible here at once. See {@link LogViewController}
 * for why these views read rather than edit.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MessageViewController
{

    @FXML
    private Label messageTypeValue;

    private final AppState appState;

    public MessageViewController(AppState appState)
    {
        this.appState = appState;
    }

    @FXML
    private void initialize()
    {
        // asString() goes through MessageType.toString(), which is the display form — the same one
        // the choosers render, so the three surfaces cannot disagree about what a type is called.
        messageTypeValue.textProperty().bind(Bindings
                .createStringBinding(() -> String.valueOf(appState.getMessageType()), appState.messageTypeProperty()));
    }
}
