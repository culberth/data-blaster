package com.culberth.tools.datablaster.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The Preferences dialog shell: a tab per scope, and the Close button.
 *
 * <p>
 * <strong>It owns nothing else.</strong> Each tab is its own FXML with its own prototype-scoped controller under
 * {@code controller.preferences}, the same arrangement the ribbon groups use. This class holds no setting, reads no
 * {@link com.culberth.tools.datablaster.model.AppState} value, and needs no edit when a tab gains a control — which is
 * the property that made the ribbon easy to extend and is worth having here too.
 *
 * <p>
 * <strong>Tabs rather than only the current mode's settings.</strong> Configuring Message mode while running in Log
 * mode is the normal case, not the exception. Showing only the active mode's settings would make that impossible and
 * would change the dialog's shape under the user for a reason that is not their fault.
 *
 * <p>
 * <strong>Edits apply immediately, and the button still says Close.</strong> OK/Cancel would need somewhere to hold
 * uncommitted edits, and that buffer is a second copy of state {@code AppState} owns — exactly the duplication this
 * dialog was reworked to remove. It also matches the ribbon, where every control applies as you move it. Reset is the
 * answer to "I did not mean that", and it is per tab: a global reset that silently cleared a mapping table someone
 * typed by hand is a different act from putting a spinner back.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PreferencesController
{

    private static final System.Logger LOG = System.getLogger(PreferencesController.class.getName());

    /**
     * Bound only so the loader has something to bind; nothing here reads it.
     *
     * <p>
     * Kept because {@code FxmlSmokeTest} leans on every {@code initialize()} dereferencing its injected fields — that
     * is what turns a dropped {@code fx:id} into a load failure rather than a control that quietly does nothing.
     * Without a field to check, this shell would load clean even if the {@code TabPane} were renamed out from under it.
     */
    @FXML
    private TabPane tabs;

    @FXML
    private void initialize()
    {
        if (tabs.getTabs().isEmpty())
        {
            throw new IllegalStateException("Preferences loaded with no tabs; check the fx:includes");
        }
        // Prototype-scoped: a second open logs a second, different instance here. If the same one
        // ever appeared twice, the shell would be bound to a node tree no longer on screen. This
        // line closes the open sequence rather than starting it — the tab controllers have already
        // logged their own, because FXMLLoader builds fx:includes depth-first.
        LOG.log(System.Logger.Level.DEBUG, () -> "Preferences shell " + id(this) + " initialized with "
                + tabs.getTabs().size() + " tab(s); its tab controllers have already run");
    }

    @FXML
    private void onClose(ActionEvent event)
    {
        LOG.log(System.Logger.Level.DEBUG, () -> "Preferences shell " + id(this) + ": Close pressed");
        ((Stage) windowOf(event)).close();
    }

    /** Matches ThemeService's form, so one instance reads the same way across the whole trace. */
    private static String id(Object o)
    {
        return o.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(o));
    }

    private static Window windowOf(ActionEvent event)
    {
        return ((Node) event.getSource()).getScene().getWindow();
    }
}
