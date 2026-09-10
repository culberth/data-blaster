package com.culberth.tools.datablaster.controller;

import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.Mode;
import com.culberth.tools.datablaster.ui.DialogService;
import com.culberth.tools.datablaster.ui.ViewSwitcher;
import java.io.IOException;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import javafx.stage.WindowEvent;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Controller for the application shell: the menu bar and the content host.
 *
 * <p>
 * The ribbon groups are {@code <fx:include>}s with their own controllers, and they communicate with this class only
 * through {@link AppState} — they set the current mode, and the shell reacts. Neither side holds a reference to the
 * other's nodes, so a new ribbon group needs no change here.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MainController
{

    private static final System.Logger LOG = System.getLogger(MainController.class.getName());

    @FXML
    private VBox contentArea;

    private final ViewSwitcher viewSwitcher;
    private final DialogService dialogService;
    private final AppState appState;

    /**
     * Held strongly here so the weak registration on the singleton {@link AppState} lives exactly as long as this
     * controller — and no longer. Registering a plain lambda would pin this shell and its whole scene graph for the
     * life of the application, and leave a discarded shell still reacting to mode changes.
     */
    private final ChangeListener<Mode> modeListener = (obs, old, mode) -> showMode(mode);

    /** The mode actually on screen, so a failed load can roll the shared state back to it. */
    private Mode displayedMode;

    public MainController(ViewSwitcher viewSwitcher, DialogService dialogService, AppState appState)
    {
        this.viewSwitcher = viewSwitcher;
        this.dialogService = dialogService;
        this.appState = appState;
    }

    @FXML
    private void initialize()
    {
        appState.currentModeProperty().addListener(new WeakChangeListener<>(modeListener));

        // Render whatever is current, rather than relying on a value transition: AppState is an
        // application-lifetime singleton, so on a second shell the mode is already set and no
        // change event would ever arrive, leaving this shell blank. There is no "nothing selected
        // yet" case to handle any more — AppState starts at the default mode and rejects null.
        showMode(appState.getCurrentMode());
    }

    // --- Menu: File ---

    @FXML
    private void onExit()
    {
        // Fire a close request rather than Platform.exit(), so this takes the same vetoable path
        // as the window's own close button and any future unsaved-changes guard sees both.
        contentArea.getScene().getWindow()
                .fireEvent(new WindowEvent(contentArea.getScene().getWindow(), WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    // --- Menu: Edit ---

    @FXML
    private void onPreferences()
    {
        // The start of the trace. showModal blocks, so the next line runs only once the dialog has
        // been dismissed — which is what makes the pair of them a usable open/close bracket.
        LOG.log(System.Logger.Level.DEBUG, "Edit > Preferences selected");
        dialogService.showModal("/fxml/preferences.fxml", "Preferences");
        LOG.log(System.Logger.Level.DEBUG, "Preferences dismissed; back in the shell");
    }

    // --- Menu: Help ---

    @FXML
    private void onAbout()
    {
        dialogService.showModal("/fxml/about.fxml", "About Data Blaster");
    }

    private void showMode(Mode mode)
    {
        if (mode == null || mode == displayedMode)
        {
            return;
        }
        try
        {
            viewSwitcher.switchTo(contentArea, mode);
            displayedMode = mode;
        }
        catch (IOException | RuntimeException e)
        {
            // Exceptions escaping an FX event handler reach a default handler that reports to a
            // console the windowed build does not have, so report the failure in the UI instead.
            dialogService.showError("Unable to load the " + mode + " view", e);
            // The swap failed, so roll the shared state back to what is actually on screen —
            // otherwise the ribbon highlights a mode that never loaded and snapshot() reports it.
            if (displayedMode != null)
            {
                appState.setCurrentMode(displayedMode);
            }
        }
    }
}
