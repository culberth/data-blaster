package com.culberth.tools.datablaster.controller;

import com.culberth.tools.datablaster.model.AppState;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The SOAP mode view: the configured address, message type, data files and tail number, read-only,
 * and an honest note that nothing uses any of them yet.
 *
 * <p>SOAP has no ribbon group — these are set once for a run — so Preferences is the only place to
 * change them, and here is where you see them without opening that dialog.
 *
 * <p><strong>Bound, not assigned</strong>, for the reason {@code LogViewController} gives: this view
 * is an independent third reader of {@link AppState}, which is what makes it an end-to-end check
 * that the settings plumbing works rather than a static description of it.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SoapViewController {

    private static final String NO_TAIL_SET = "(none set)";

    @FXML
    private Label ipValue;

    @FXML
    private Label messageTypeValue;

    @FXML
    private Label dataFileCountValue;

    @FXML
    private Label tailValue;

    private final AppState appState;

    public SoapViewController(AppState appState) {
        this.appState = appState;
    }

    @FXML
    private void initialize() {
        ipValue.textProperty().bind(appState.soapIpProperty());

        messageTypeValue.textProperty().bind(appState.soapMessageTypeProperty().asString());

        // Bound to the list itself, so adding or removing a file in Preferences updates this count
        // while the view is on screen — the distinction a ChangeListener on an ObservableList gets
        // wrong, since the list object is never replaced.
        dataFileCountValue.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    int count = appState.soapDataFiles().size();
                    return count == 0 ? "none" : count + (count == 1 ? " file" : " files");
                },
                appState.soapDataFiles()));

        tailValue.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    String tail = appState.getSoapTail();
                    return tail == null ? NO_TAIL_SET : tail;
                },
                appState.soapTailProperty()));
    }
}
