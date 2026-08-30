package com.culberth.tools.datablaster.controller;

import com.culberth.tools.datablaster.model.AppState;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The SOAP mode view: the configured listen port, read-only, and an honest note that nothing binds
 * it.
 *
 * <p>SOAP has no ribbon group — a listen port is set once — so Preferences is the only place to
 * change this, and here is where you see it without opening that dialog.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SoapViewController {

    @FXML
    private Label portValue;

    private final AppState appState;

    public SoapViewController(AppState appState) {
        this.appState = appState;
    }

    @FXML
    private void initialize() {
        portValue.textProperty().bind(appState.soapPortProperty().asString());
    }
}
