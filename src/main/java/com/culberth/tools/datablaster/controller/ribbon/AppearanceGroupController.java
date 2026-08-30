package com.culberth.tools.datablaster.controller.ribbon;

import com.culberth.tools.datablaster.model.AppState;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The "Appearance" ribbon group: the content opacity. It is written to {@link AppState}; the
 * content area observes it there rather than being bound to this group's slider directly.
 *
 * <p>Opacity is the only thing left here, and it is deliberately not a persisted setting — it is a
 * view control with a Reset beside it. The template's Sim Factor slider that used to share this
 * group became Log mode's Playback Speed Factor and moved to Preferences; see the group's FXML for
 * why it is not a slider any more.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AppearanceGroupController {

    private static final double OPACITY_DEFAULT = 1.0;

    @FXML
    private Label opacityCaption;

    @FXML
    private Slider opacitySlider;

    @FXML
    private Label opacityValueLabel;

    @FXML
    private Button opacityResetButton;

    private final AppState appState;

    public AppearanceGroupController(AppState appState) {
        this.appState = appState;
    }

    @FXML
    private void initialize() {
        // Set here, not in the FXML: labelFor="$opacitySlider" on the caption would be a forward
        // reference to an fx:id declared further down the file, which FXMLLoader resolves to null
        // without erroring — markup that asserts an association it does not actually make.
        opacityCaption.setLabelFor(opacitySlider);

        // The read-out and Reset exist because dragging to 0.3 leaves content at roughly 1.9:1
        // contrast, and without a numeric value or a default there is no way back but by eye.
        opacitySlider.setValue(appState.getContentOpacity());
        opacitySlider.valueProperty().addListener(
                (obs, oldValue, newValue) -> appState.setContentOpacity(newValue.doubleValue()));
        opacityValueLabel.textProperty().bind(
                Bindings.format("%.0f%%", appState.contentOpacityProperty().multiply(100)));
        opacityResetButton.disableProperty().bind(
                appState.contentOpacityProperty().isEqualTo(OPACITY_DEFAULT, 0.001));
    }

    @FXML
    private void onResetOpacity() {
        opacitySlider.setValue(OPACITY_DEFAULT);
    }
}
