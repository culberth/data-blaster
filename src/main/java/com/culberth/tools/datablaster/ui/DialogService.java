package com.culberth.tools.datablaster.ui;

import com.culberth.tools.datablaster.ViewLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

/**
 * Builds and shows the application's modal dialogs: owner, modality, stylesheet and
 * Esc-to-cancel are applied in one place rather than at each call site.
 */
@Component
public class DialogService {

    private static final System.Logger LOG = System.getLogger(DialogService.class.getName());

    private final ViewLoader viewLoader;
    private final StageRegistry stageRegistry;

    public DialogService(ViewLoader viewLoader, StageRegistry stageRegistry) {
        this.viewLoader = viewLoader;
        this.stageRegistry = stageRegistry;
    }

    /**
     * Loads {@code fxmlResource} and shows it as an application-modal dialog, blocking until
     * it closes.
     *
     * <p>Everything from the load through {@code showAndWait} is guarded: an exception escaping
     * here would reach the FX default handler, which reports to a console the windowed build
     * does not have, leaving the menu item looking as though it does nothing.
     */
    public void showModal(String fxmlResource, String title) {
        try {
            ViewLoader.LoadedView loaded = viewLoader.load(fxmlResource);

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            Stage owner = stageRegistry.getPrimaryStage();
            if (owner != null) {
                dialog.initOwner(owner);
            }
            dialog.setTitle(title);
            dialog.setResizable(false);
            dialog.setScene(viewLoader.newScene(loaded.root()));
            dialog.showAndWait();
        } catch (Exception e) {
            showError("Unable to open " + title, e);
        }
    }

    /**
     * Reports a failure to the user, and logs it. The log matters: the dialog carries only the
     * message, so without this the stack trace would be discarded entirely and the console build
     * would be harder to diagnose than one with no error handling at all.
     */
    public void showError(String message, Throwable cause) {
        LOG.log(System.Logger.Level.ERROR, message, cause);
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            Stage owner = stageRegistry.getPrimaryStage();
            if (owner != null) {
                alert.initOwner(owner);
            }
            viewLoader.style(alert.getDialogPane());
            alert.setTitle("Data Blaster");
            alert.setHeaderText(message);
            alert.setContentText(cause == null ? null : String.valueOf(cause.getMessage()));
            alert.showAndWait();
        } catch (RuntimeException reportingFailure) {
            // This is usually called from a catch block, so letting a dialog failure escape would
            // replace the real problem with a less useful one. The log above is the record.
            LOG.log(System.Logger.Level.ERROR, "Could not display the error dialog", reportingFailure);
        }
    }
}
