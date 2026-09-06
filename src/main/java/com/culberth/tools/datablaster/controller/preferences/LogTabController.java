package com.culberth.tools.datablaster.controller.preferences;

import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.PortTailMapping;
import com.culberth.tools.datablaster.model.Settings;
import com.culberth.tools.datablaster.ui.DialogService;
import com.culberth.tools.datablaster.ui.LogFolderChooser;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.fxml.FXML;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The Log tab: playback speed, the capture folder, and the port-to-tail-number table.
 *
 * <p>
 * <strong>Bad entries are rejected where they are made.</strong> Every path that changes the table — Add, and an edit
 * committed in either column — funnels through {@link #apply}, which catches the {@code IllegalArgumentException} that
 * {@link PortTailMapping} and {@link AppState#setPortTailMappings} throw and shows its message beside the controls.
 * Those messages are written to be read by a person, which is why they are shown verbatim rather than translated here.
 * Nothing is deferred to close, and nothing opens a modal to say a port is out of range.
 *
 * <p>
 * <strong>Both columns are {@code String} columns, including Port.</strong> An {@code Integer} column would need a
 * converter, and the default one throws on unparsable text from inside the cell's commit — an exception escaping into
 * the table's own event handling, where this class cannot turn it into a message. Parsing the text here keeps every
 * validation failure on one path.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class LogTabController
{

    /**
     * How far the arrows move the playback speed.
     *
     * <p>
     * A convenience, not a validation rule: the store accepts any value in range, so 0.25 is typeable even though the
     * arrows step by 0.1. Letting a step size quietly become the set of legal values is how a control ends up narrower
     * than the setting it edits.
     */
    private static final double PLAYBACK_SPEED_STEP = 0.1;

    private static final String NO_FOLDER_SELECTED = "(none selected)";

    @FXML
    private Label playbackSpeedCaption;

    @FXML
    private Spinner<Double> playbackSpeedSpinner;

    @FXML
    private Label logFolderLabel;

    @FXML
    private Label mappingsCaption;

    @FXML
    private TableView<PortTailMapping> mappingTable;

    @FXML
    private TableColumn<PortTailMapping, String> portColumn;

    @FXML
    private TableColumn<PortTailMapping, String> tailColumn;

    @FXML
    private TextField newPortField;

    @FXML
    private TextField newTailField;

    @FXML
    private Button addButton;

    @FXML
    private Button removeButton;

    @FXML
    private Label mappingErrorLabel;

    @FXML
    private Label portCollisionLabel;

    @FXML
    private Button resetButton;

    private final AppState appState;
    private final LogFolderChooser logFolderChooser;
    private final DialogService dialogService;

    private SpinnerValueFactory.DoubleSpinnerValueFactory playbackSpeedFactory;

    /**
     * Recomputes the Blast Port collision notice whenever the table changes.
     *
     * <p>
     * Held strongly so the weak registration on the singleton {@link AppState} lives exactly as long as this
     * controller. A collection needs a {@code ListChangeListener}: a {@code ChangeListener} on an
     * {@code ObservableList} fires only when the list object itself is replaced, which never happens here.
     */
    private final ListChangeListener<PortTailMapping> mappingsListener = change -> refreshPortCollisionNotice();

    /**
     * The other half of that notice: the port it compares against can move too.
     *
     * <p>
     * It could not, when this compared against SOAP mode's listen port — that lived on a sibling tab of the same modal
     * dialog. The Blast Port is in the ribbon as well as on the General tab, so it can change while this tab is on
     * screen, and a warning about a port that is no longer the Blast Port is worse than no warning at all.
     */
    private final ChangeListener<Number> blastPortListener = (observable, old, port) -> refreshPortCollisionNotice();

    public LogTabController(AppState appState, LogFolderChooser logFolderChooser, DialogService dialogService)
    {
        this.appState = appState;
        this.logFolderChooser = logFolderChooser;
        this.dialogService = dialogService;
    }

    @FXML
    private void initialize()
    {
        // Set here rather than in the FXML: labelFor="$..." would be a forward reference to an
        // fx:id declared further down the file, which FXMLLoader quietly resolves to null.
        playbackSpeedCaption.setLabelFor(playbackSpeedSpinner);
        mappingsCaption.setLabelFor(mappingTable);

        initPlaybackSpeed();
        initLogFolder();
        initMappingTable();

        resetButton.disableProperty()
                .bind(appState.playbackSpeedFactorProperty()
                        .isEqualTo(Settings.DEFAULTS.log().playbackSpeedFactor(), 0.001)
                        .and(appState.logFolderProperty().isNull()).and(Bindings.isEmpty(appState.portTailMappings())));
    }

    private void initPlaybackSpeed()
    {
        // The bounds come from the same constants SettingsStore range-checks against, so a control
        // offering a value the store would reject on the next launch cannot be built by accident.
        playbackSpeedFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(Settings.PLAYBACK_SPEED_MIN,
                Settings.PLAYBACK_SPEED_MAX, appState.getPlaybackSpeedFactor(), PLAYBACK_SPEED_STEP);
        playbackSpeedFactory.setConverter(playbackSpeedConverter());
        playbackSpeedSpinner.setValueFactory(playbackSpeedFactory);
        playbackSpeedSpinner.valueProperty()
                .addListener((observable, old, now) -> appState.setPlaybackSpeedFactor(now));

        // An editable Spinner holds typed text in its editor until something commits it, so tabbing
        // away or pressing Close would otherwise discard a number the user had just typed and
        // watched appear. increment(0) is the commit; the converter keeps unparsable text from
        // throwing out of this listener.
        playbackSpeedSpinner.focusedProperty().addListener((observable, was, hasFocus) ->
        {
            if (!hasFocus)
            {
                playbackSpeedSpinner.increment(0);
            }
        });
    }

    private void initLogFolder()
    {
        logFolderLabel.textProperty().bind(Bindings.createStringBinding(() ->
        {
            File folder = appState.getLogFolder();
            return folder == null ? NO_FOLDER_SELECTED : folder.getAbsolutePath();
        }, appState.logFolderProperty()));
    }

    private void initMappingTable()
    {
        // AppState's unmodifiable view, not a copy: the table tracks later edits without being
        // rebuilt. Column sorting is off in the FXML because a sort would try to reorder this list
        // in place, and the entries already arrive in port order.
        mappingTable.setItems(appState.portTailMappings());
        mappingTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        mappingTable.setPlaceholder(new Label("No mappings. Add one below."));

        portColumn.setCellValueFactory(row -> new SimpleStringProperty(Integer.toString(row.getValue().port())));
        tailColumn.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().tail()));

        portColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        tailColumn.setCellFactory(TextFieldTableCell.forTableColumn());

        portColumn.setOnEditCommit(edit -> replace(edit.getRowValue(), edit.getNewValue(), edit.getRowValue().tail()));
        tailColumn.setOnEditCommit(
                edit -> replace(edit.getRowValue(), Integer.toString(edit.getRowValue().port()), edit.getNewValue()));

        removeButton.disableProperty().bind(mappingTable.getSelectionModel().selectedItemProperty().isNull());

        appState.portTailMappings().addListener(new WeakListChangeListener<>(mappingsListener));
        appState.blastPortProperty().addListener(new WeakChangeListener<>(blastPortListener));
        refreshPortCollisionNotice();
    }

    // --- editing ---------------------------------------------------------------------------

    @FXML
    private void onAddMapping()
    {
        String port = newPortField.getText();
        String tail = newTailField.getText();
        boolean added = apply(() ->
        {
            List<PortTailMapping> next = new ArrayList<>(appState.portTailMappings());
            next.add(PortTailMapping.of(parsePort(port), tail));
            appState.setPortTailMappings(next);
        });
        if (added)
        {
            newPortField.clear();
            newTailField.clear();
            newPortField.requestFocus();
        }
    }

    @FXML
    private void onRemoveMapping()
    {
        PortTailMapping selected = mappingTable.getSelectionModel().getSelectedItem();
        if (selected != null)
        {
            // No confirmation for one row: it is visible, it is one line, and retyping it is
            // cheaper than a dialog on every removal. Clearing the whole table does confirm.
            appState.removePortTailMappingForPort(selected.port());
            clearMappingError();
        }
    }

    /** Replaces {@code existing} with an edited port and tail, rejecting the edit if invalid. */
    private void replace(PortTailMapping existing, String port, String tail)
    {
        boolean applied = apply(() ->
        {
            List<PortTailMapping> next = new ArrayList<>(appState.portTailMappings());
            int at = next.indexOf(existing);
            if (at < 0)
            {
                return;
            }
            next.set(at, PortTailMapping.of(parsePort(port), tail));
            appState.setPortTailMappings(next);
        });
        if (!applied)
        {
            // The cell has already accepted the text visually; the state rejected it. Redrawing
            // from the items list is what puts the old value back in front of the user, next to
            // the reason it was refused.
            mappingTable.refresh();
        }
    }

    /**
     * Runs a table edit, turning a rejection into a message rather than an exception.
     *
     * @return {@code true} if the edit was applied
     */
    private boolean apply(Runnable edit)
    {
        try
        {
            edit.run();
            clearMappingError();
            return true;
        }
        catch (IllegalArgumentException rejected)
        {
            showMappingError(rejected.getMessage());
            return false;
        }
    }

    /**
     * Parses a typed port.
     *
     * <p>
     * Throws the same {@code IllegalArgumentException} that every other rejection on this path throws, with a message
     * in the same voice, so the caller needs one catch rather than two.
     */
    private static int parsePort(String text)
    {
        try
        {
            return Integer.parseInt(text == null ? "" : text.trim());
        }
        catch (NumberFormatException notANumber)
        {
            throw new IllegalArgumentException("Port must be a number between " + PortTailMapping.PORT_MIN + " and "
                    + PortTailMapping.PORT_MAX + ", but was '" + text + "'");
        }
    }

    private void showMappingError(String message)
    {
        mappingErrorLabel.setText(message);
        mappingErrorLabel.setVisible(true);
        mappingErrorLabel.setManaged(true);
    }

    private void clearMappingError()
    {
        mappingErrorLabel.setText("");
        mappingErrorLabel.setVisible(false);
        mappingErrorLabel.setManaged(false);
    }

    /**
     * Flags a mapping port that is also the Blast Port, without blocking it.
     *
     * <p>
     * It used to compare against SOAP mode's listen port, which no longer exists — SOAP sends rather than listens, and
     * its port became an address. The Blast Port is the other port setting this application has, so the notice moved to
     * it rather than being deleted: the reason it was worth raising has not changed with which port it names.
     *
     * <p>
     * It is not an error: nothing binds either port yet, the two settings are independent, and a person may well have a
     * reason. It is worth saying out loud because the failure it predicts would appear much later, at bind time,
     * somewhere else entirely, with nothing pointing back here.
     *
     * <p>
     * Recomputed on every table change and whenever the Blast Port moves, so it cannot go stale while the dialog is
     * open.
     */
    private void refreshPortCollisionNotice()
    {
        int blastPort = appState.getBlastPort();
        boolean collides = appState.portTailMappings().stream().anyMatch(mapping -> mapping.port() == blastPort);

        portCollisionLabel
                .setText(collides ? "Port " + blastPort + " is also the Blast Port. Allowed, but the two would "
                        + "conflict if both were ever bound at once." : "");
        portCollisionLabel.setVisible(collides);
        portCollisionLabel.setManaged(collides);
    }

    // --- the rest of the tab ----------------------------------------------------------------

    @FXML
    private void onChooseLogFolder(ActionEvent event)
    {
        // Owned by this dialog, not the primary stage: Preferences is application-modal, so a
        // chooser owned by the main window would open behind it.
        logFolderChooser.choose(windowOf(event)).ifPresent(appState::setLogFolder);
    }

    /**
     * Resets this tab, confirming first because it destroys the mapping table.
     *
     * <p>
     * The confirmation is conditional on there being something to destroy. Asking before putting a spinner back would
     * be noise, and a prompt that always appears is a prompt people learn to dismiss without reading — which is exactly
     * the habit that makes the one that matters ineffective.
     */
    @FXML
    private void onResetToDefaults()
    {
        if (!appState.portTailMappings().isEmpty() && !confirmClearingMappings())
        {
            return;
        }
        appState.setPlaybackSpeedFactor(Settings.DEFAULTS.log().playbackSpeedFactor());
        appState.setLogFolder(null);
        appState.setPortTailMappings(List.of());
        // The spinner does not observe AppState, so put it back explicitly.
        playbackSpeedFactory.setValue(Settings.DEFAULTS.log().playbackSpeedFactor());
        clearMappingError();
    }

    private boolean confirmClearingMappings()
    {
        int count = appState.portTailMappings().size();
        return dialogService.confirm("Reset the Log settings?",
                "This removes " + count + (count == 1 ? " mapping" : " mappings") + " and cannot be undone.", "Reset");
    }

    /**
     * Reads a typed speed, keeping the current value rather than throwing on nonsense — see
     * {@code Spinner.commitEditorText()}, which does not guard the conversion.
     */
    private StringConverter<Double> playbackSpeedConverter()
    {
        return new StringConverter<>()
        {
            @Override
            public String toString(Double value)
            {
                return value == null ? "" : String.format("%.2f", value);
            }

            @Override
            public Double fromString(String text)
            {
                if (text == null)
                {
                    return playbackSpeedFactory.getValue();
                }
                try
                {
                    return Double.valueOf(text.trim());
                }
                catch (NumberFormatException notANumber)
                {
                    return playbackSpeedFactory.getValue();
                }
            }
        };
    }

    private static Window windowOf(ActionEvent event)
    {
        return ((Node) event.getSource()).getScene().getWindow();
    }
}
