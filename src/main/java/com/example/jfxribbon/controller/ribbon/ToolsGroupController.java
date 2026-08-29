package com.example.jfxribbon.controller.ribbon;

import com.example.jfxribbon.model.AppState;
import com.example.jfxribbon.ui.LogFolderChooser;
import com.example.jfxribbon.ui.StageRegistry;
import java.io.File;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** The "Tools" ribbon group: choosing the log folder and showing which one is selected. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ToolsGroupController {

    @FXML
    private Label logFolderLabel;

    private final AppState appState;
    private final StageRegistry stageRegistry;
    private final LogFolderChooser logFolderChooser;

    public ToolsGroupController(AppState appState,
                                StageRegistry stageRegistry,
                                LogFolderChooser logFolderChooser) {
        this.appState = appState;
        this.stageRegistry = stageRegistry;
        this.logFolderChooser = logFolderChooser;
    }

    @FXML
    private void initialize() {
        // The path can easily outrun the ribbon, so the label ellipsizes and the full path stays
        // reachable through a tooltip.
        logFolderLabel.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    File folder = appState.getLogFolder();
                    return folder == null ? "(none selected)" : folder.getAbsolutePath();
                },
                appState.logFolderProperty()));
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(logFolderLabel.textProperty());
        Tooltip.install(logFolderLabel, tooltip);
    }

    @FXML
    private void onChooseLogFolder() {
        // Shared with the Preferences dialog, which offers the same setting: see LogFolderChooser
        // for why the chooser itself outlives this prototype-scoped controller.
        logFolderChooser.choose(stageRegistry.getPrimaryStage()).ifPresent(appState::setLogFolder);
    }
}
