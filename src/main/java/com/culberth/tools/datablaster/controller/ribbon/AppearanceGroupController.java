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
 * The "Appearance" ribbon group: the Sim Factor preference and the content opacity. Both are
 * written to {@link AppState}; the content area observes the opacity there rather than being
 * bound to this group's slider directly.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AppearanceGroupController {

    private static final double OPACITY_DEFAULT = 1.0;

    @FXML
    private Label simFactorCaption;

    @FXML
    private Slider simFactorSlider;

    @FXML
    private Label simFactorValueLabel;

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
        // Set here, not in the FXML: labelFor="$simFactorSlider" on the caption would be a forward
        // reference to an fx:id declared further down the file, which FXMLLoader resolves to null
        // without erroring — markup that asserts an association it does not actually make.
        simFactorCaption.setLabelFor(simFactorSlider);
        opacityCaption.setLabelFor(opacitySlider);

        simFactorSlider.setValue(appState.getSimFactor());
        simFactorSlider.valueProperty().addListener(
                (obs, oldValue, newValue) -> appState.setSimFactor(newValue.doubleValue()));
        simFactorValueLabel.textProperty().bind(
                Bindings.format("%.1f", appState.simFactorProperty()));

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
