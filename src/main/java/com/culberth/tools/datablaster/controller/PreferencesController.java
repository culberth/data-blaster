package com.culberth.tools.datablaster.controller;

import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.Settings;
import com.culberth.tools.datablaster.model.SettingsStore;
import com.culberth.tools.datablaster.model.Theme;
import com.culberth.tools.datablaster.ui.LogFolderChooser;
import java.io.File;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Controller for the Preferences dialog: the canonical surface for the settings that persist.
 *
 * <p><strong>What this dialog is for.</strong> The values that survive a restart live here, and it
 * is what someone looking for "where do I configure the log folder" will open. Content opacity is
 * deliberately absent: it is a view control with a Reset beside it, not a setting, and it is not
 * persisted.
 *
 * <p><strong>It is not yet the whole configuration surface.</strong> Message mode's type, SOAP's
 * port and Log's port-to-tail table are persisted settings with no control here yet — the tabbed
 * rebuild (General, Log, Message, SOAP) is the change that gives them one. Until then this dialog
 * edits what it edits, and {@link #onResetToDefaults()} resets exactly that, rather than quietly
 * clearing settings it does not show.
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

    /**
     * How far the spinner arrows move the playback speed.
     *
     * <p>A convenience, not a validation rule. The store accepts any value in range, so 0.25 is
     * typeable even though the arrows step by 0.1 — letting a step size quietly become the set of
     * legal values is how a control ends up narrower than the setting it edits.
     */
    private static final double PLAYBACK_SPEED_STEP = 0.1;

    @FXML
    private Label themeCaption;

    @FXML
    private ChoiceBox<Theme> themeChoice;

    @FXML
    private Label playbackSpeedCaption;

    @FXML
    private Spinner<Double> playbackSpeedSpinner;

    @FXML
    private Label logFolderLabel;

    @FXML
    private Label settingsFileLabel;

    @FXML
    private Button resetButton;

    private final AppState appState;
    private final LogFolderChooser logFolderChooser;
    private final SettingsStore settingsStore;

    /** Held so the reset can put the spinner back: {@code Spinner} has no direct value setter. */
    private SpinnerValueFactory.DoubleSpinnerValueFactory playbackSpeedFactory;

    public PreferencesController(AppState appState,
                                 LogFolderChooser logFolderChooser,
                                 SettingsStore settingsStore) {
        this.appState = appState;
        this.logFolderChooser = logFolderChooser;
        this.settingsStore = settingsStore;
    }

    @FXML
    private void initialize() {
        // Set here rather than in the FXML: labelFor="$playbackSpeedSpinner" would be a forward
        // reference to an fx:id declared further down the file, which FXMLLoader quietly resolves
        // to null — markup asserting an association it does not make.
        playbackSpeedCaption.setLabelFor(playbackSpeedSpinner);
        themeCaption.setLabelFor(themeChoice);

        // Same one-way pair as the spinner below: read the live value, then write changes through.
        // ThemeService does the rest — it observes AppState and restyles the open windows.
        themeChoice.getItems().setAll(Theme.values());
        themeChoice.setValue(appState.getTheme());
        themeChoice.valueProperty().addListener(
                (observable, old, now) -> appState.setTheme(now));

        // Read once, then push changes out. AppState exposes read-only properties by design, so
        // there is no bidirectional binding to be had, and the one-way pair is the same shape the
        // ribbon groups use.
        //
        // The bounds come from the same constants SettingsStore range-checks against, so a control
        // offering a value the store would reject on the next launch cannot be built by accident.
        playbackSpeedFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(
                Settings.PLAYBACK_SPEED_MIN,
                Settings.PLAYBACK_SPEED_MAX,
                appState.getPlaybackSpeedFactor(),
                PLAYBACK_SPEED_STEP);
        playbackSpeedFactory.setConverter(playbackSpeedConverter());
        playbackSpeedSpinner.setValueFactory(playbackSpeedFactory);
        playbackSpeedSpinner.valueProperty().addListener(
                (observable, old, now) -> appState.setPlaybackSpeedFactor(now));

        // An editable Spinner holds typed text in its editor until something commits it, so tabbing
        // away or pressing Close would otherwise discard a number the user had just typed and
        // watched appear. increment(0) is the commit; the converter is what keeps unparsable text
        // from throwing out of this listener and into a handler with no console to report to.
        playbackSpeedSpinner.focusedProperty().addListener((observable, was, hasFocus) -> {
            if (!hasFocus) {
                playbackSpeedSpinner.increment(0);
            }
        });

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

        // Nothing to reset when everything this dialog shows is already at its default, and saying
        // so with the control itself beats a dialog that reports it after the fact.
        resetButton.disableProperty().bind(
                appState.playbackSpeedFactorProperty()
                        .isEqualTo(Settings.DEFAULTS.log().playbackSpeedFactor(), 0.001)
                        .and(appState.logFolderProperty().isNull())
                        .and(appState.themeProperty().isEqualTo(Settings.DEFAULTS.theme())));
    }

    /** Keeps the full text reachable when the label ellipsizes; paths outrun the dialog easily. */
    private static void installTooltip(Label label) {
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(label.textProperty());
        Tooltip.install(label, tooltip);
    }

    /**
     * Reads a typed number, keeping the current value rather than throwing on nonsense.
     *
     * <p>{@code Spinner.commitEditorText()} does not guard the conversion, so the default
     * {@code DoubleStringConverter} would turn a stray keystroke into a
     * {@code NumberFormatException} escaping a focus listener — reaching a default handler that
     * reports to a console the windowed build does not have. Rejecting the text by keeping the
     * value already held is both safer and what the control appears to do.
     */
    private StringConverter<Double> playbackSpeedConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return value == null ? "" : String.format("%.2f", value);
            }

            @Override
            public Double fromString(String text) {
                if (text == null) {
                    return playbackSpeedFactory.getValue();
                }
                try {
                    return Double.valueOf(text.trim());
                } catch (NumberFormatException notANumber) {
                    return playbackSpeedFactory.getValue();
                }
            }
        };
    }

    @FXML
    private void onChooseLogFolder(ActionEvent event) {
        // Owned by this dialog, not the primary stage: this dialog is application-modal, so a
        // chooser owned by the main window would open behind it.
        logFolderChooser.choose(windowOf(event)).ifPresent(appState::setLogFolder);
    }

    /**
     * Resets the settings this dialog shows, and only those.
     *
     * <p>The port-to-tail table in particular is left alone. Clearing a table someone typed by hand
     * is a different act from putting a spinner back, and it gets its own confirmation when the Log
     * tab that owns it exists.
     */
    @FXML
    private void onResetToDefaults() {
        appState.setPlaybackSpeedFactor(Settings.DEFAULTS.log().playbackSpeedFactor());
        appState.setLogFolder(null);
        appState.setTheme(Settings.DEFAULTS.theme());
        themeChoice.setValue(Settings.DEFAULTS.theme());
        // The controls do not observe AppState, so put them back explicitly — the same one-way
        // relationship initialize() sets up, running in the other direction.
        playbackSpeedFactory.setValue(Settings.DEFAULTS.log().playbackSpeedFactor());
    }

    @FXML
    private void onClose(ActionEvent event) {
        ((Stage) windowOf(event)).close();
    }

    private static Window windowOf(ActionEvent event) {
        return ((Node) event.getSource()).getScene().getWindow();
    }
}
