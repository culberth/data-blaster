package com.culberth.tools.datablaster.controller.preferences;

import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.MessageType;
import com.culberth.tools.datablaster.model.Settings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The Message tab: one chooser, because Message mode has one setting.
 *
 * <p>The Message ribbon group offers the same control. Neither holds a copy — both read and write
 * {@link AppState} — so the two cannot disagree, and this one follows the state rather than reading
 * it once so a change made in the ribbon is visible here without reopening the dialog.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MessageTabController {

    @FXML
    private Label messageTypeCaption;

    @FXML
    private ChoiceBox<MessageType> messageTypeChoice;

    @FXML
    private Button resetButton;

    private final AppState appState;

    /**
     * Held strongly so the weak registration on the singleton {@link AppState} lives exactly as long
     * as this controller. Setting a {@code ChoiceBox} to the value it already holds fires nothing,
     * so the write-back loop closes itself without a re-entrancy flag.
     */
    private final ChangeListener<MessageType> typeListener =
            (observable, old, type) -> messageTypeChoice.setValue(type);

    public MessageTabController(AppState appState) {
        this.appState = appState;
    }

    @FXML
    private void initialize() {
        // Set here, not in the FXML: labelFor="$messageTypeChoice" would be a forward reference to
        // an fx:id declared further down, which FXMLLoader resolves to null without erroring.
        messageTypeCaption.setLabelFor(messageTypeChoice);

        // Populated from values() rather than listed in the markup, so a fourth constant is a
        // one-line change in the enum and nowhere else.
        messageTypeChoice.getItems().setAll(MessageType.values());
        messageTypeChoice.setValue(appState.getMessageType());
        messageTypeChoice.valueProperty().addListener((observable, old, type) -> {
            if (type != null) {
                appState.setMessageType(type);
            }
        });
        appState.messageTypeProperty().addListener(new WeakChangeListener<>(typeListener));

        resetButton.disableProperty().bind(
                appState.messageTypeProperty().isEqualTo(Settings.DEFAULTS.message().type()));
    }

    @FXML
    private void onResetToDefaults() {
        appState.setMessageType(Settings.DEFAULTS.message().type());
    }
}
