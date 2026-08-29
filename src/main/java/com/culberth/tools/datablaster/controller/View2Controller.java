package com.culberth.tools.datablaster.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class View2Controller {

    @FXML
    private Label viewLabel;

    @FXML
    private void initialize() {
        viewLabel.setText("This is View 2");
    }
}
