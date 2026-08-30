package com.culberth.tools.datablaster.controller.preferences;

import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.Settings;
import com.culberth.tools.datablaster.model.SettingsStore;
import com.culberth.tools.datablaster.model.Theme;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The General tab: the settings that belong to no mode, and where they all live on disk.
 *
 * <p>The settings file path is here rather than under a mode because it answers "where did my
 * setting go" for every tab at once. The store is a plain properties file precisely so that path is
 * something a person can act on.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GeneralTabController {

    @FXML
    private Label themeCaption;

    @FXML
    private ChoiceBox<Theme> themeChoice;

    @FXML
    private Label settingsFileLabel;

    @FXML
    private Button resetButton;

    private final AppState appState;
    private final SettingsStore settingsStore;

    /**
     * Keeps the chooser in step when the theme is changed elsewhere — today only by this tab's own
     * Reset, but a control that silently disagrees with the state behind it is the defect this
     * whole dialog was reworked to avoid.
     *
     * <p>Held strongly so the weak registration on the singleton {@link AppState} lives exactly as
     * long as this controller. Setting a {@code ChoiceBox} to the value it already holds fires
     * nothing, so the write-back loop closes itself with no re-entrancy flag.
     */
    private final ChangeListener<Theme> themeListener =
            (observable, old, theme) -> themeChoice.setValue(theme);

    public GeneralTabController(AppState appState, SettingsStore settingsStore) {
        this.appState = appState;
        this.settingsStore = settingsStore;
    }

    @FXML
    private void initialize() {
        // Set here rather than in the FXML: labelFor="$themeChoice" would be a forward reference to
        // an fx:id declared further down the file, which FXMLLoader quietly resolves to null —
        // markup asserting an association it does not make.
        themeCaption.setLabelFor(themeChoice);

        themeChoice.getItems().setAll(Theme.values());
        themeChoice.setValue(appState.getTheme());
        themeChoice.valueProperty().addListener((observable, old, theme) -> {
            if (theme != null) {
                appState.setTheme(theme);
            }
        });
        appState.themeProperty().addListener(new WeakChangeListener<>(themeListener));

        settingsFileLabel.setText(settingsStore.location().toString());
        // The path outruns the dialog easily, so the full text stays reachable when it ellipsizes.
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(settingsFileLabel.textProperty());
        Tooltip.install(settingsFileLabel, tooltip);

        // Nothing to reset when this tab's one setting is already at its default. Saying so with
        // the control itself beats a dialog that reports it after the fact.
        resetButton.disableProperty().bind(
                appState.themeProperty().isEqualTo(Settings.DEFAULTS.theme()));
    }

    /**
     * Resets what this tab shows, and nothing else.
     *
     * <p>No confirmation: putting a theme back is instantly visible and instantly undone. The Log
     * tab's reset asks first because it destroys a table someone typed by hand, which is a
     * different kind of act.
     */
    @FXML
    private void onResetToDefaults() {
        appState.setTheme(Settings.DEFAULTS.theme());
    }
}
