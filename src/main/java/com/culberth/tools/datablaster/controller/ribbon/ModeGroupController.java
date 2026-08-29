package com.culberth.tools.datablaster.controller.ribbon;

import com.culberth.tools.datablaster.model.AppState;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The "Mode" ribbon group: the view switcher. Publishes the chosen view id to {@link AppState};
 * the shell observes that and performs the swap, so this group never touches the content area.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ModeGroupController {

    @FXML
    private ToggleGroup modeGroup;

    private final AppState appState;

    /**
     * Held strongly here so the weak registration on the singleton {@link AppState} lives exactly
     * as long as this controller. A plain lambda would pin this group's scene graph for the life
     * of the application and keep a discarded ribbon reacting to view changes.
     */
    private final ChangeListener<String> viewIdListener = (obs, old, viewId) -> select(viewId);

    public ModeGroupController(AppState appState) {
        this.appState = appState;
    }

    @FXML
    private void initialize() {
        // ToggleButton.fire() toggles even inside a ToggleGroup (unlike RadioButton), so clicking
        // the already-selected view would clear the selection while still switching to that view,
        // leaving the ribbon with nothing highlighted. Put the selection back.
        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                modeGroup.selectToggle(oldToggle);
            }
        });

        // Keep the ribbon in step when the view is changed by something other than these buttons.
        appState.currentViewIdProperty().addListener(new WeakChangeListener<>(viewIdListener));
    }

    @FXML
    private void onModeSelected() {
        Toggle selected = modeGroup.getSelectedToggle();
        if (selected != null) {
            appState.setCurrentViewId(viewIdOf(selected));
        }
    }

    private void select(String viewId) {
        for (Toggle toggle : modeGroup.getToggles()) {
            if (viewIdOf(toggle).equals(viewId)) {
                modeGroup.selectToggle(toggle);
                return;
            }
        }
    }

    /** The view id a toggle stands for, carried in its userData so it is declared in the FXML. */
    private static String viewIdOf(Toggle toggle) {
        Object id = toggle.getUserData();
        if (id == null) {
            throw new IllegalStateException("A Mode toggle is missing its userData view id");
        }
        return id.toString();
    }
}
