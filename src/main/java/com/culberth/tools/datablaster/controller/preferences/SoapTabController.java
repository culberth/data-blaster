package com.culberth.tools.datablaster.controller.preferences;

import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.PortTailMapping;
import com.culberth.tools.datablaster.model.Settings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.util.StringConverter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The SOAP tab: the port the mode will listen on.
 *
 * <p>This is the only surface for it — SOAP has no ribbon group, because a listen port is set once
 * and a ribbon is for controls reached for repeatedly.
 *
 * <p>The spinner's bounds come from {@link PortTailMapping}'s port range, the same constants
 * {@code SettingsStore} range-checks against and {@code AppState.setSoapPort} enforces, so a control
 * that offers a value the store would reject on the next launch cannot be built by accident.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SoapTabController {

    @FXML
    private Label portCaption;

    @FXML
    private Spinner<Integer> portSpinner;

    @FXML
    private Button resetButton;

    private final AppState appState;

    private SpinnerValueFactory.IntegerSpinnerValueFactory portFactory;

    /**
     * Held strongly so the weak registration on the singleton {@link AppState} lives exactly as long
     * as this controller. Setting the factory to the value it already holds fires nothing, so the
     * write-back loop closes itself.
     */
    private final ChangeListener<Number> portListener =
            (observable, old, port) -> portFactory.setValue(port.intValue());

    public SoapTabController(AppState appState) {
        this.appState = appState;
    }

    @FXML
    private void initialize() {
        // Set here, not in the FXML: labelFor="$portSpinner" would be a forward reference to an
        // fx:id declared further down, which FXMLLoader resolves to null without erroring.
        portCaption.setLabelFor(portSpinner);

        portFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(
                PortTailMapping.PORT_MIN, PortTailMapping.PORT_MAX, appState.getSoapPort());
        portFactory.setConverter(portConverter());
        portSpinner.setValueFactory(portFactory);
        portSpinner.valueProperty().addListener((observable, old, port) -> {
            if (port != null) {
                appState.setSoapPort(port);
            }
        });
        appState.soapPortProperty().addListener(new WeakChangeListener<>(portListener));

        // An editable Spinner holds typed text in its editor until something commits it, so tabbing
        // away or pressing Close would otherwise discard a port the user had just typed and watched
        // appear. increment(0) is the commit; the converter keeps unparsable text from throwing out
        // of this listener into a handler with no console to report to.
        portSpinner.focusedProperty().addListener((observable, was, hasFocus) -> {
            if (!hasFocus) {
                portSpinner.increment(0);
            }
        });

        resetButton.disableProperty().bind(
                appState.soapPortProperty().isEqualTo(Settings.DEFAULTS.soap().port()));
    }

    /**
     * Reads a typed port, keeping the current value rather than throwing on nonsense.
     *
     * <p>{@code Spinner.commitEditorText()} does not guard the conversion, so the default
     * {@code IntegerStringConverter} would turn a stray keystroke into a
     * {@code NumberFormatException} escaping a focus listener. The factory clamps to the range
     * afterwards, so a typed 0 becomes 1 rather than a rejected setting.
     */
    private StringConverter<Integer> portConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : Integer.toString(value);
            }

            @Override
            public Integer fromString(String text) {
                if (text == null) {
                    return portFactory.getValue();
                }
                try {
                    return Integer.valueOf(text.trim());
                } catch (NumberFormatException notANumber) {
                    return portFactory.getValue();
                }
            }
        };
    }

    @FXML
    private void onResetToDefaults() {
        appState.setSoapPort(Settings.DEFAULTS.soap().port());
    }
}
