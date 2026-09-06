package com.culberth.tools.datablaster.controller.ribbon;

import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.Mode;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The "Mode" ribbon group: the mode switcher. Publishes the chosen {@link Mode} to {@link AppState}; the shell observes
 * that and performs the view swap, so this group never touches the content area.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ModeGroupController
{

    @FXML
    private ToggleGroup modeGroup;

    private final AppState appState;

    /**
     * Held strongly here so the weak registration on the singleton {@link AppState} lives exactly as long as this
     * controller. A plain lambda would pin this group's scene graph for the life of the application and keep a
     * discarded ribbon reacting to mode changes.
     */
    private final ChangeListener<Mode> modeListener = (obs, old, mode) -> select(mode);

    public ModeGroupController(AppState appState)
    {
        this.appState = appState;
    }

    @FXML
    private void initialize()
    {
        // ToggleButton.fire() toggles even inside a ToggleGroup (unlike RadioButton), so clicking
        // the already-selected mode would clear the selection while still switching to that mode,
        // leaving the ribbon with nothing highlighted. Put the selection back.
        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) ->
        {
            if (newToggle == null && oldToggle != null)
            {
                modeGroup.selectToggle(oldToggle);
            }
        });

        // Keep the ribbon in step when the mode is changed by something other than these buttons.
        appState.currentModeProperty().addListener(new WeakChangeListener<>(modeListener));

        // A ribbon group may subscribe to AppState here but must not publish: FXMLLoader builds
        // depth-first, so this runs before the shell's initialize(), and anything written now would
        // be overwritten. Reading is safe, and is what puts the restored mode on the buttons —
        // the FXML's selected="true" is only a starting point for a mode that was never stored.
        select(appState.getCurrentMode());
    }

    @FXML
    private void onModeSelected()
    {
        Toggle selected = modeGroup.getSelectedToggle();
        if (selected != null)
        {
            appState.setCurrentMode(modeOf(selected));
        }
    }

    private void select(Mode mode)
    {
        for (Toggle toggle : modeGroup.getToggles())
        {
            if (modeOf(toggle) == mode)
            {
                modeGroup.selectToggle(toggle);
                return;
            }
        }
    }

    /**
     * The mode a toggle stands for, carried in its {@code userData} so the button-to-mode mapping is declared in the
     * FXML rather than hardcoded in four handler methods.
     *
     * <p>
     * The {@code userData} is the constant's name, which is a string in the markup and therefore the one half of this
     * wiring the compiler cannot check. {@code ModeGroupViewIdTest} reads the FXML and asserts each value parses, so a
     * typo fails the build rather than the button.
     */
    private static Mode modeOf(Toggle toggle)
    {
        Object declared = toggle.getUserData();
        if (declared == null)
        {
            throw new IllegalStateException("A Mode toggle is missing its userData mode name");
        }
        try
        {
            return Mode.valueOf(declared.toString());
        }
        catch (IllegalArgumentException unknown)
        {
            // Deliberately not Mode.fromStoredName's tolerant fallback: that exists for a
            // hand-edited settings file, where recovering beats failing a launch. This is the
            // application's own markup, where a wrong value is a defect to surface, not absorb.
            throw new IllegalStateException("A Mode toggle declares userData '" + declared + "', which is not a Mode",
                    unknown);
        }
    }
}
