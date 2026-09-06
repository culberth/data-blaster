package com.culberth.tools.datablaster.controller.ribbon;

import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.ui.PortSpinner;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The "Global" ribbon group: the Blast Port, Single Message and Byte Hijack.
 *
 * <p>
 * This group replaced "Appearance", whose only control was a content-opacity slider. Opacity was a view control rather
 * than a setting and is gone rather than moved; these three are settings, are persisted, and are shown here
 * <em>and</em> in Preferences' General tab. Neither surface owns the value — both read and write {@link AppState}.
 *
 * <p>
 * <strong>Every control follows the state as well as writing to it.</strong> The same three settings are editable in
 * Preferences, so a control that read {@code AppState} once would sit there disagreeing with it the moment the dialog
 * changed anything. Setting a control to the value it already holds fires nothing in JavaFX, so the write-back loop
 * closes itself and none of these needs a re-entrancy flag — unlike the Log group's slider, whose log scale means a
 * round trip does not land on the value it started from.
 *
 * <p>
 * <strong>This controller is pinned by its own controls.</strong> The subscriptions below are weak, as {@code AppState}
 * requires, but the listeners registered on this group's own spinner and check boxes are lambdas capturing {@code this}
 * — a strong node-to-controller reference that keeps it alive exactly as long as the node tree.
 * {@code ContextualGroupController} has no such control and has to park itself in its host's properties; this one does
 * not.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GlobalGroupController
{

    @FXML
    private Label blastPortCaption;

    @FXML
    private Spinner<Integer> blastPortSpinner;

    @FXML
    private CheckBox singleMessageCheck;

    @FXML
    private CheckBox byteHijackCheck;

    private final AppState appState;

    private SpinnerValueFactory.IntegerSpinnerValueFactory blastPortFactory;

    /**
     * Kept in step when these are changed in Preferences.
     *
     * <p>
     * Held strongly so the weak registrations on the singleton {@link AppState} live exactly as long as this controller
     * — no longer, or a discarded ribbon would go on reacting.
     */
    private final ChangeListener<Number> blastPortListener = (observable, old, port) -> blastPortFactory
            .setValue(port.intValue());

    private final ChangeListener<Boolean> singleMessageListener = (observable, old, on) -> singleMessageCheck
            .setSelected(on);

    private final ChangeListener<Boolean> byteHijackListener = (observable, old, on) -> byteHijackCheck.setSelected(on);

    public GlobalGroupController(AppState appState)
    {
        this.appState = appState;
    }

    @FXML
    private void initialize()
    {
        // Set here, not in the FXML: labelFor="$blastPortSpinner" on the caption would be a forward
        // reference to an fx:id declared further down the file, which FXMLLoader resolves to null
        // without erroring — markup that asserts an association it does not actually make.
        blastPortCaption.setLabelFor(blastPortSpinner);

        blastPortFactory = PortSpinner.configure(blastPortSpinner, appState.getBlastPort());
        blastPortSpinner.valueProperty().addListener((observable, old, port) ->
        {
            if (port != null)
            {
                appState.setBlastPort(port);
            }
        });
        appState.blastPortProperty().addListener(new WeakChangeListener<>(blastPortListener));

        singleMessageCheck.setSelected(appState.isSingleMessage());
        singleMessageCheck.selectedProperty().addListener((observable, was, on) -> appState.setSingleMessage(on));
        appState.singleMessageProperty().addListener(new WeakChangeListener<>(singleMessageListener));

        byteHijackCheck.setSelected(appState.isByteHijack());
        byteHijackCheck.selectedProperty().addListener((observable, was, on) -> appState.setByteHijack(on));
        appState.byteHijackProperty().addListener(new WeakChangeListener<>(byteHijackListener));
    }
}
