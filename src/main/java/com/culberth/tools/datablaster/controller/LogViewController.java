package com.culberth.tools.datablaster.controller;

import com.culberth.tools.datablaster.model.AppState;
import java.io.File;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The Log mode view: a read-only account of what Log mode is configured to do, and an honest note that it does not do
 * it yet.
 *
 * <p>
 * <strong>Read-only on purpose.</strong> Every value here is editable in the Log ribbon group or the Preferences Log
 * tab. A third editing surface would be a third thing to keep in step; a read-out is not.
 *
 * <p>
 * <strong>Bound, not assigned.</strong> Changing a setting anywhere is visible here immediately, without the view being
 * rebuilt — which is also the cheapest end-to-end demonstration that the settings plumbing actually works, since the
 * ribbon, the dialog and this view are three independent readers of one {@link AppState}.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class LogViewController
{

    private static final String NO_FOLDER_SELECTED = "(none selected)";

    @FXML
    private Label logFolderValue;

    @FXML
    private Label playbackSpeedValue;

    @FXML
    private Label mappingCountValue;

    private final AppState appState;

    public LogViewController(AppState appState)
    {
        this.appState = appState;
    }

    @FXML
    private void initialize()
    {
        logFolderValue.textProperty().bind(Bindings.createStringBinding(() ->
        {
            File folder = appState.getLogFolder();
            return folder == null ? NO_FOLDER_SELECTED : folder.getAbsolutePath();
        }, appState.logFolderProperty()));

        playbackSpeedValue.textProperty()
                .bind(Bindings.format("%.2f× real time", appState.playbackSpeedFactorProperty()));

        // Bound to the list itself, so adding or removing a mapping in Preferences updates this
        // count while the view is on screen. Bindings.size observes the collection rather than a
        // property holding it, which is the distinction a ChangeListener would get wrong here.
        mappingCountValue.textProperty().bind(Bindings.createStringBinding(() ->
        {
            int count = appState.portTailMappings().size();
            return count == 0 ? "none" : count + (count == 1 ? " mapping" : " mappings");
        }, appState.portTailMappings()));
    }
}
