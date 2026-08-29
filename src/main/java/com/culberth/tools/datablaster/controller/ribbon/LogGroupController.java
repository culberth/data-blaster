package com.culberth.tools.datablaster.controller.ribbon;

import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.Settings;
import com.culberth.tools.datablaster.ui.LogFolderChooser;
import com.culberth.tools.datablaster.ui.LogScale;
import com.culberth.tools.datablaster.ui.StageRegistry;
import java.io.File;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The "Log" ribbon group: playback speed and the log folder, shown only while Log mode is selected.
 *
 * <p><strong>The slider is in track units, not multipliers.</strong> Its 0–1 range is a position on
 * a logarithmic scale that {@link LogScale} converts to and from — see that class for why a linear
 * track is unusable for a 0.1–10.0 multiplier. Nothing outside this controller ever sees a position:
 * {@link AppState} holds the real speed, and the read-out shows it.
 *
 * <p><strong>Two controls edit this setting and neither owns it.</strong> Preferences has a
 * {@code Spinner} for typing an exact value; this has a slider for scrubbing. Both read and write
 * {@link AppState}, so there is no copy to keep in step.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class LogGroupController {

    /**
     * Decimal places kept when the slider writes a speed.
     *
     * <p>A log scale lands on values like 1.0000000000000002. Rounding here rather than only in the
     * read-out's format matters, because the rounded number is what gets stored — formatting it
     * away would leave the settings file holding noise the UI never showed.
     */
    private static final int SPEED_DECIMALS = 2;

    private static final String NO_FOLDER_SELECTED = "(none selected)";

    private final LogScale speedScale =
            new LogScale(Settings.PLAYBACK_SPEED_MIN, Settings.PLAYBACK_SPEED_MAX);

    @FXML
    private Label playbackSpeedCaption;

    @FXML
    private Slider playbackSpeedSlider;

    @FXML
    private Label playbackSpeedValueLabel;

    @FXML
    private Button playbackSpeedResetButton;

    @FXML
    private Label logFolderLabel;

    private final AppState appState;
    private final StageRegistry stageRegistry;
    private final LogFolderChooser logFolderChooser;

    /**
     * True while this controller is writing a slider movement into {@link AppState}.
     *
     * <p>Without it the two listeners below form a loop: the slider writes a *rounded* speed, the
     * speed listener converts that back to a position, and the position it computes is not
     * bit-identical to where the thumb is — so the thumb jumps under the cursor mid-drag. The guard
     * makes the relationship one-way for the duration of the write, which is the only moment it
     * needs to be.
     *
     * <p>Declared before {@code speedListener} because that field's initializer reads it, and a
     * field initializer may not refer forward to a later one.
     */
    private boolean writingFromSlider;

    /**
     * Moves the thumb when something else changes the speed — Preferences, or a reset.
     *
     * <p>Unlike most controls in this project, which read {@link AppState} once in
     * {@code initialize()} and only write afterwards, this one has to keep following it. Preferences
     * is modal, so the two are never manipulated at the same instant, but closing it after changing
     * the speed would otherwise leave the read-out showing the new value beside a thumb still at the
     * old position — and the next nudge of that thumb would silently throw the new value away.
     *
     * <p>Held strongly here so the weak registration on the singleton {@code AppState} lives exactly
     * as long as this controller, and no longer.
     */
    private final ChangeListener<Number> speedListener = (observable, old, speed) -> {
        if (!writingFromSlider) {
            playbackSpeedSlider.setValue(speedScale.positionOf(speed.doubleValue()));
        }
    };

    public LogGroupController(AppState appState,
                              StageRegistry stageRegistry,
                              LogFolderChooser logFolderChooser) {
        this.appState = appState;
        this.stageRegistry = stageRegistry;
        this.logFolderChooser = logFolderChooser;
    }

    @FXML
    private void initialize() {
        // Set here, not in the FXML: labelFor="$playbackSpeedSlider" on the caption would be a
        // forward reference to an fx:id declared further down the file, which FXMLLoader resolves
        // to null without erroring — markup that asserts an association it does not make.
        playbackSpeedCaption.setLabelFor(playbackSpeedSlider);

        // Read the live value once and convert it onto the track. A ribbon group may read AppState
        // in initialize() but must not publish: it runs before the shell's initialize(), which
        // would overwrite anything written here.
        playbackSpeedSlider.setValue(speedScale.positionOf(appState.getPlaybackSpeedFactor()));
        playbackSpeedSlider.valueProperty().addListener((observable, old, position) -> {
            writingFromSlider = true;
            try {
                appState.setPlaybackSpeedFactor(
                        speedScale.valueAt(position.doubleValue(), SPEED_DECIMALS));
            } finally {
                writingFromSlider = false;
            }
        });
        appState.playbackSpeedFactorProperty().addListener(new WeakChangeListener<>(speedListener));

        // Bound to the state rather than to the slider, so it is right whichever surface last set
        // the speed.
        playbackSpeedValueLabel.textProperty().bind(
                Bindings.format("%.2fx", appState.playbackSpeedFactorProperty()));

        // Real time is the value you come back to, and finding it by eye on a log track is exactly
        // what a scrubbing control is bad at.
        playbackSpeedResetButton.disableProperty().bind(
                appState.playbackSpeedFactorProperty()
                        .isEqualTo(Settings.PLAYBACK_SPEED_DEFAULT, 0.001));

        logFolderLabel.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    File folder = appState.getLogFolder();
                    return folder == null ? NO_FOLDER_SELECTED : folder.getAbsolutePath();
                },
                appState.logFolderProperty()));
        // The path easily outruns the ribbon, so the label ellipsizes and the full path stays
        // reachable through a tooltip.
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(logFolderLabel.textProperty());
        Tooltip.install(logFolderLabel, tooltip);
    }

    @FXML
    private void onResetPlaybackSpeed() {
        appState.setPlaybackSpeedFactor(Settings.PLAYBACK_SPEED_DEFAULT);
    }

    @FXML
    private void onChooseLogFolder() {
        // Shared with the Preferences dialog, which offers the same setting: see LogFolderChooser
        // for why the chooser itself outlives this prototype-scoped controller.
        logFolderChooser.choose(stageRegistry.getPrimaryStage()).ifPresent(appState::setLogFolder);
    }
}
