package com.example.jfxribbon.controller;

import com.example.jfxribbon.model.AppState;
import com.example.jfxribbon.model.Settings;
import com.example.jfxribbon.model.SettingsStore;
import com.example.jfxribbon.model.Theme;
import com.example.jfxribbon.ui.LogFolderChooser;
import java.io.File;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Controller for the Preferences dialog: the canonical surface for the settings that persist.
 *
 * <p><strong>What this dialog is for.</strong> Sim Factor and the log folder are the two values
 * that survive a restart, and until now the only place to change either was the ribbon — so someone
 * looking for "where do I configure the log folder" opened the one thing called Preferences and
 * found an apology. The ribbon keeps both for quick access; this is where they are documented to
 * live. Content opacity is deliberately absent: it is a view control with a Reset beside it, not a
 * setting, and it is not persisted.
 *
 * <p><strong>Edits apply immediately, and the button still says Close.</strong> OK/Cancel would
 * need somewhere to hold uncommitted edits — and that buffer is a second copy of state
 * {@link AppState} owns, which is exactly what consolidating these surfaces was meant to avoid. It
 * also matches the ribbon, where every control already applies as you move it, so the two surfaces
 * behave the same way. Reset to defaults is the answer to "I did not mean that", and it is a
 * smaller thing to get right than a staging buffer.
 *
 * <p>Prototype-scoped like every FXML controller: the loader binds these {@code @FXML} fields to
 * the node tree it has just built.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PreferencesController {

    private static final String NO_FOLDER_SELECTED = "(none selected)";

    @FXML
    private Label themeCaption;

    @FXML
    private ChoiceBox<Theme> themeChoice;

    @FXML
    private Label simFactorCaption;

    @FXML
    private Slider simFactorSlider;

    @FXML
    private Label simFactorValueLabel;

    @FXML
    private Label logFolderLabel;

    @FXML
    private Label settingsFileLabel;

    @FXML
    private Button resetButton;

    private final AppState appState;
    private final LogFolderChooser logFolderChooser;
    private final SettingsStore settingsStore;

    public PreferencesController(AppState appState,
                                 LogFolderChooser logFolderChooser,
                                 SettingsStore settingsStore) {
        this.appState = appState;
        this.logFolderChooser = logFolderChooser;
        this.settingsStore = settingsStore;
    }

    @FXML
    private void initialize() {
        // Set here rather than in the FXML: labelFor="$simFactorSlider" would be a forward
        // reference to an fx:id declared further down the file, which FXMLLoader quietly resolves
        // to null — markup asserting an association it does not make.
        simFactorCaption.setLabelFor(simFactorSlider);
        themeCaption.setLabelFor(themeChoice);

        // Same one-way pair as the slider below: read the live value, then write changes through.
        // ThemeService does the rest — it observes AppState and restyles the open windows.
        themeChoice.getItems().setAll(Theme.values());
        themeChoice.setValue(appState.getTheme());
        themeChoice.valueProperty().addListener(
                (observable, old, now) -> appState.setTheme(now));

        // Read once, then push changes out. AppState exposes read-only properties by design, so
        // there is no bidirectional binding to be had — and the one-way pair is the same shape the
        // Appearance ribbon group uses, which is worth more here than cleverness.
        simFactorSlider.setValue(appState.getSimFactor());
        simFactorSlider.valueProperty().addListener(
                (observable, old, now) -> appState.setSimFactor(now.doubleValue()));
        simFactorValueLabel.textProperty().bind(
                Bindings.format("%.1f", appState.simFactorProperty()));

        logFolderLabel.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    File folder = appState.getLogFolder();
                    return folder == null ? NO_FOLDER_SELECTED : folder.getAbsolutePath();
                },
                appState.logFolderProperty()));
        installTooltip(logFolderLabel);

        // Answers "where did my setting go" without needing the documentation open. The store is
        // a plain file precisely so this path is useful rather than a curiosity.
        settingsFileLabel.setText(settingsStore.location().toString());
        installTooltip(settingsFileLabel);

        // Nothing to reset when everything is already at its default, and saying so with the
        // control itself beats a dialog that reports it after the fact.
        resetButton.disableProperty().bind(
                appState.simFactorProperty()
                        .isEqualTo(Settings.DEFAULTS.simFactor(), 0.001)
                        .and(appState.logFolderProperty().isNull())
                        .and(appState.themeProperty().isEqualTo(Settings.DEFAULTS.theme())));
    }

    /** Keeps the full text reachable when the label ellipsizes; paths outrun the dialog easily. */
    private static void installTooltip(Label label) {
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(label.textProperty());
        Tooltip.install(label, tooltip);
    }

    @FXML
    private void onChooseLogFolder(ActionEvent event) {
        // Owned by this dialog, not the primary stage: this dialog is application-modal, so a
        // chooser owned by the main window would open behind it.
        logFolderChooser.choose(windowOf(event)).ifPresent(appState::setLogFolder);
    }

    @FXML
    private void onResetToDefaults() {
        appState.setSimFactor(Settings.DEFAULTS.simFactor());
        appState.setLogFolder(null);
        appState.setTheme(Settings.DEFAULTS.theme());
        themeChoice.setValue(Settings.DEFAULTS.theme());
        // The slider does not observe AppState, so put it back explicitly — the same one-way
        // relationship initialize() sets up, running in the other direction.
        simFactorSlider.setValue(Settings.DEFAULTS.simFactor());
    }

    @FXML
    private void onClose(ActionEvent event) {
        ((Stage) windowOf(event)).close();
    }

    private static Window windowOf(ActionEvent event) {
        return ((Node) event.getSource()).getScene().getWindow();
    }
}
