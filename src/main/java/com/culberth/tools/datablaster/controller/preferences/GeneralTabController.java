package com.culberth.tools.datablaster.controller.preferences;

import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.Settings;
import com.culberth.tools.datablaster.model.SettingsStore;
import com.culberth.tools.datablaster.model.Theme;
import com.culberth.tools.datablaster.ui.PortSpinner;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tooltip;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The General tab: the settings that belong to no mode, and where they all live on disk.
 *
 * <p>
 * The theme, the Blast Port, Single Message and Byte Hijack. The last three are also in the Global ribbon group;
 * neither surface owns the value, and both read and write {@link AppState}.
 *
 * <p>
 * The settings file path is here rather than under a mode because it answers "where did my setting go" for every tab at
 * once. The store is a plain properties file precisely so that path is something a person can act on.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GeneralTabController
{

    /**
     * Traces the theme's round trip through this tab: the choice box writing to {@link AppState}, and {@link AppState}
     * writing back. Both directions are logged because the write-back is what closes the loop — and that it closes
     * silently, by setting a value already set, is why no re-entrancy flag is needed here.
     */
    private static final System.Logger LOG = System.getLogger(GeneralTabController.class.getName());

    @FXML
    private Label themeCaption;

    @FXML
    private ChoiceBox<Theme> themeChoice;

    @FXML
    private Label blastPortCaption;

    @FXML
    private Spinner<Integer> blastPortSpinner;

    @FXML
    private CheckBox singleMessageCheck;

    @FXML
    private CheckBox byteHijackCheck;

    @FXML
    private Label settingsFileLabel;

    @FXML
    private Button resetButton;

    private final AppState appState;
    private final SettingsStore settingsStore;

    private SpinnerValueFactory.IntegerSpinnerValueFactory blastPortFactory;

    /**
     * Keeps the controls in step when these are changed elsewhere — the Global ribbon group edits the same three
     * settings, and this tab's own Reset moves all four. A control that silently disagrees with the state behind it is
     * the defect this whole dialog was reworked to avoid.
     *
     * <p>
     * Held strongly so the weak registrations on the singleton {@link AppState} live exactly as long as this
     * controller. Setting a control to the value it already holds fires nothing, so each write-back loop closes itself
     * with no re-entrancy flag.
     */
    private final ChangeListener<Theme> themeListener = (observable, old, theme) ->
    {
        LOG.log(System.Logger.Level.DEBUG,
                () -> "General tab " + id(this) + ": AppState theme is " + theme + "; writing it back to the choice box"
                        + (themeChoice.getValue() == theme ? " (already showing it, so this fires nothing)" : ""));
        themeChoice.setValue(theme);
    };

    private final ChangeListener<Number> blastPortListener = (observable, old, port) -> blastPortFactory
            .setValue(port.intValue());

    private final ChangeListener<Boolean> singleMessageListener = (observable, old, on) -> singleMessageCheck
            .setSelected(on);

    private final ChangeListener<Boolean> byteHijackListener = (observable, old, on) -> byteHijackCheck.setSelected(on);

    public GeneralTabController(AppState appState, SettingsStore settingsStore)
    {
        this.appState = appState;
        this.settingsStore = settingsStore;
    }

    @FXML
    private void initialize()
    {
        // A new instance on every open, bound to a fresh node tree. FXMLLoader builds fx:includes
        // depth-first, so this line arrives before the shell's own — the same order the ribbon
        // groups run in, and the reason a tab may subscribe to AppState here but must not publish.
        LOG.log(System.Logger.Level.DEBUG,
                () -> "General tab " + id(this) + " initializing; AppState theme is " + appState.getTheme());

        // Set here rather than in the FXML: labelFor="$themeChoice" would be a forward reference to
        // an fx:id declared further down the file, which FXMLLoader quietly resolves to null —
        // markup asserting an association it does not make.
        themeCaption.setLabelFor(themeChoice);
        blastPortCaption.setLabelFor(blastPortSpinner);

        initTheme();
        initGlobalSettings();
        initSettingsFilePath();

        // Nothing to reset when everything this tab shows is already at its default. Saying so with
        // the control itself beats a dialog that reports it after the fact.
        //
        // One binding over the four properties rather than a chain of isEqualTo().and(...):
        // BooleanExpression.isEqualTo takes another observable, not a literal, so the two check
        // boxes would have to be written as not() — which reads as "is off" and is only equivalent
        // to "is default" while the default happens to be false.
        resetButton.disableProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> appState.getTheme() == Settings.DEFAULTS.theme()
                                && appState.getBlastPort() == Settings.DEFAULTS.blastPort()
                                && appState.isSingleMessage() == Settings.DEFAULTS.singleMessage()
                                && appState.isByteHijack() == Settings.DEFAULTS.byteHijack(),
                        appState.themeProperty(), appState.blastPortProperty(), appState.singleMessageProperty(),
                        appState.byteHijackProperty()));
    }

    private void initTheme()
    {
        themeChoice.getItems().setAll(Theme.values());
        themeChoice.setValue(appState.getTheme());
        themeChoice.valueProperty().addListener((observable, old, theme) ->
        {
            // Where the use case begins: AppState, ThemeService and the settings writer all hang
            // off this one selection.
            LOG.log(System.Logger.Level.DEBUG,
                    () -> "General tab " + id(this) + ": choice box moved " + old + " -> " + theme);
            if (theme != null)
            {
                appState.setTheme(theme);
            }
        });
        appState.themeProperty().addListener(new WeakChangeListener<>(themeListener));
        // Weak, so this controller stops hearing about the theme once the closed dialog's node tree
        // is collected. A reopened dialog subscribes again, from its own new instance.
        LOG.log(System.Logger.Level.DEBUG, () -> "General tab " + id(this) + ": subscribed weakly to AppState.theme");
    }

    private void initGlobalSettings()
    {
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

    private void initSettingsFilePath()
    {
        settingsFileLabel.setText(settingsStore.location().toString());
        // The path outruns the dialog easily, so the full text stays reachable when it ellipsizes.
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(settingsFileLabel.textProperty());
        Tooltip.install(settingsFileLabel, tooltip);
    }

    /**
     * Resets what this tab shows, and nothing else.
     *
     * <p>
     * No confirmation: none of these destroys anything a person typed at length. Putting a theme or a check box back is
     * instantly visible and instantly undone, and a port is one number. The Log tab's reset asks first because it
     * clears a table someone typed by hand, which is a different kind of act.
     */
    @FXML
    private void onResetToDefaults()
    {
        LOG.log(System.Logger.Level.DEBUG, () -> "General tab " + id(this) + ": Reset to defaults");
        appState.setTheme(Settings.DEFAULTS.theme());
        appState.setBlastPort(Settings.DEFAULTS.blastPort());
        appState.setSingleMessage(Settings.DEFAULTS.singleMessage());
        appState.setByteHijack(Settings.DEFAULTS.byteHijack());
    }

    /** Matches ThemeService's form, so one instance reads the same way across the whole trace. */
    private static String id(Object o)
    {
        return o.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(o));
    }
}
