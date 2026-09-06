package com.culberth.tools.datablaster.controller.ribbon;

import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.MessageType;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The "Message" ribbon group: the message type, shown only while Message mode is selected.
 *
 * <p>
 * Populated from {@link MessageType#values()} rather than from a list in the FXML, so adding a constant is a one-line
 * change in the enum and nowhere else. The choices render through {@code MessageType.toString()}, which is why that
 * override exists — a {@code ChoiceBox} uses it for both the list and the button, and one override keeps them from
 * disagreeing.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MessageGroupController
{

    @FXML
    private Label messageTypeCaption;

    @FXML
    private ChoiceBox<MessageType> messageTypeChoice;

    private final AppState appState;

    /**
     * Keeps the chooser in step when Preferences changes the type while this group exists.
     *
     * <p>
     * Held strongly so the weak registration on the singleton {@link AppState} lives exactly as long as this
     * controller. The guard against writing back is the value comparison rather than a flag: setting a
     * {@code ChoiceBox} to the value it already holds fires nothing, so the loop closes itself.
     */
    private final ChangeListener<MessageType> typeListener = (observable, old, type) -> messageTypeChoice
            .setValue(type);

    public MessageGroupController(AppState appState)
    {
        this.appState = appState;
    }

    @FXML
    private void initialize()
    {
        // Set here, not in the FXML: labelFor="$messageTypeChoice" on the caption would be a
        // forward reference to an fx:id declared further down the file, which FXMLLoader resolves
        // to null without erroring — markup that asserts an association it does not make.
        messageTypeCaption.setLabelFor(messageTypeChoice);

        messageTypeChoice.getItems().setAll(MessageType.values());

        // Read AppState here, do not publish: this runs before the shell's initialize().
        messageTypeChoice.setValue(appState.getMessageType());
        messageTypeChoice.valueProperty().addListener((observable, old, type) ->
        {
            if (type != null)
            {
                appState.setMessageType(type);
            }
        });
        appState.messageTypeProperty().addListener(new WeakChangeListener<>(typeListener));
    }
}
