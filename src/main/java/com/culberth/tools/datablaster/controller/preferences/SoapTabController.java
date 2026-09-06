package com.culberth.tools.datablaster.controller.preferences;

import com.culberth.tools.datablaster.model.AppState;
import com.culberth.tools.datablaster.model.Settings;
import com.culberth.tools.datablaster.model.SoapMessageType;
import com.culberth.tools.datablaster.ui.DataFileChooser;
import com.culberth.tools.datablaster.ui.DialogService;
import java.io.File;
import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The SOAP tab: the address, the message type, the data files and the tail number.
 *
 * <p>
 * This is the only surface for all four — SOAP has no ribbon group, because these are set once for a run and a ribbon
 * is for controls reached for repeatedly.
 *
 * <p>
 * <strong>Bad entries are rejected where they are made</strong>, the same rule the mapping editor follows. The address
 * and the tail both funnel through a commit method that catches the {@code IllegalArgumentException} their validators
 * throw and shows its message verbatim beside the field. Those messages are written to be read by a person, which is
 * why they are not translated here. Nothing is deferred to close, and nothing opens a modal to say an octet is out of
 * range.
 *
 * <p>
 * <strong>A {@code TextField} commits on Enter and on losing focus.</strong> Unlike a {@code Spinner} there is no
 * editor to flush, but there is the same failure: typing an address and pressing Close would otherwise discard it.
 * Committing on focus loss as well as on Enter is what makes tabbing to the next field behave the way it looks like it
 * does.
 *
 * <p>
 * <strong>A rejected entry leaves the field as the user typed it</strong>, next to the reason. Snapping the text back
 * to the stored value would erase what they were correcting.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SoapTabController
{

    private static final String NO_DATA_FILES = "No data files. Add some below.";

    @FXML
    private Label ipCaption;

    @FXML
    private TextField ipField;

    @FXML
    private Label ipErrorLabel;

    @FXML
    private Label messageTypeCaption;

    @FXML
    private ChoiceBox<SoapMessageType> soapMessageTypeChoice;

    @FXML
    private Label dataFilesCaption;

    @FXML
    private ListView<File> dataFileList;

    @FXML
    private Button addFilesButton;

    @FXML
    private Button removeFileButton;

    @FXML
    private Label tailCaption;

    @FXML
    private TextField tailField;

    @FXML
    private Label tailErrorLabel;

    @FXML
    private Button resetButton;

    private final AppState appState;
    private final DataFileChooser dataFileChooser;
    private final DialogService dialogService;

    /**
     * Kept in step when these are changed elsewhere — today only by this tab's own Reset, but a control that silently
     * disagrees with the state behind it is the defect this whole dialog was reworked to avoid.
     *
     * <p>
     * Held strongly so the weak registrations on the singleton {@link AppState} live exactly as long as this
     * controller. Setting a control to the value it already holds fires nothing, so each write-back loop closes itself
     * with no re-entrancy flag.
     */
    private final ChangeListener<String> ipListener = (observable, old, ip) -> ipField.setText(ip);

    private final ChangeListener<SoapMessageType> messageTypeListener = (observable, old, type) -> soapMessageTypeChoice
            .setValue(type);

    private final ChangeListener<String> tailListener = (observable, old, tail) -> tailField
            .setText(tail == null ? "" : tail);

    public SoapTabController(AppState appState, DataFileChooser dataFileChooser, DialogService dialogService)
    {
        this.appState = appState;
        this.dataFileChooser = dataFileChooser;
        this.dialogService = dialogService;
    }

    @FXML
    private void initialize()
    {
        // Set here rather than in the FXML: labelFor="$..." would be a forward reference to an
        // fx:id declared further down the file, which FXMLLoader resolves to null without erroring.
        ipCaption.setLabelFor(ipField);
        messageTypeCaption.setLabelFor(soapMessageTypeChoice);
        dataFilesCaption.setLabelFor(dataFileList);
        tailCaption.setLabelFor(tailField);

        initIp();
        initMessageType();
        initDataFiles();
        initTail();

        resetButton.disableProperty()
                .bind(appState.soapIpProperty().isEqualTo(Settings.DEFAULTS.soap().ip())
                        .and(appState.soapMessageTypeProperty().isEqualTo(Settings.DEFAULTS.soap().type()))
                        .and(appState.soapTailProperty().isNull()).and(Bindings.isEmpty(appState.soapDataFiles())));
    }

    // --- the address ---------------------------------------------------------------------------

    private void initIp()
    {
        ipField.setText(appState.getSoapIp());
        ipField.setOnAction(event -> commitIp());
        ipField.focusedProperty().addListener((observable, was, hasFocus) ->
        {
            if (!hasFocus)
            {
                commitIp();
            }
        });
        appState.soapIpProperty().addListener(new WeakChangeListener<>(ipListener));
    }

    private void commitIp()
    {
        try
        {
            appState.setSoapIp(ipField.getText());
            clear(ipErrorLabel);
        }
        catch (IllegalArgumentException rejected)
        {
            show(ipErrorLabel, rejected.getMessage());
        }
    }

    // --- the message type ----------------------------------------------------------------------

    private void initMessageType()
    {
        // Populated from values() rather than listed in the markup, so adding a constant stays a
        // one-line change in the enum.
        soapMessageTypeChoice.getItems().setAll(SoapMessageType.values());
        soapMessageTypeChoice.setValue(appState.getSoapMessageType());
        soapMessageTypeChoice.valueProperty().addListener((observable, old, type) ->
        {
            if (type != null)
            {
                appState.setSoapMessageType(type);
            }
        });
        appState.soapMessageTypeProperty().addListener(new WeakChangeListener<>(messageTypeListener));
    }

    // --- the data files ------------------------------------------------------------------------

    private void initDataFiles()
    {
        // AppState's unmodifiable view, not a copy: the list tracks later edits without being
        // rebuilt, and cannot become a second way to change the setting.
        dataFileList.setItems(appState.soapDataFiles());
        dataFileList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        dataFileList.setPlaceholder(new Label(NO_DATA_FILES));
        // A cell factory rather than File.toString(): the full path is what identifies a file, and
        // toString() on a relative File would show something that is not where the file is.
        dataFileList.setCellFactory(view -> new ListCell<>()
        {
            @Override
            protected void updateItem(File file, boolean empty)
            {
                super.updateItem(file, empty);
                setText(empty || file == null ? null : file.getAbsolutePath());
            }
        });

        removeFileButton.disableProperty().bind(dataFileList.getSelectionModel().selectedItemProperty().isNull());
    }

    @FXML
    private void onAddDataFiles(ActionEvent event)
    {
        // Owned by this dialog, not the primary stage: Preferences is application-modal, so a
        // chooser owned by the main window would open behind it.
        List<File> chosen = dataFileChooser.choose(windowOf(event));
        if (!chosen.isEmpty())
        {
            // Duplicates are dropped by AppState rather than refused here — a file picked twice is
            // a person using a chooser, not an error worth a message.
            appState.addSoapDataFiles(chosen);
        }
    }

    @FXML
    private void onRemoveDataFile()
    {
        File selected = dataFileList.getSelectionModel().getSelectedItem();
        if (selected != null)
        {
            // No confirmation for one row: it is visible, it is one line, and choosing it again is
            // cheaper than a dialog on every removal. Clearing the whole list does confirm.
            appState.removeSoapDataFile(selected);
        }
    }

    // --- the tail number -----------------------------------------------------------------------

    private void initTail()
    {
        String tail = appState.getSoapTail();
        tailField.setText(tail == null ? "" : tail);
        tailField.setOnAction(event -> commitTail());
        tailField.focusedProperty().addListener((observable, was, hasFocus) ->
        {
            if (!hasFocus)
            {
                commitTail();
            }
        });
        appState.soapTailProperty().addListener(new WeakChangeListener<>(tailListener));
    }

    private void commitTail()
    {
        try
        {
            // Blank clears the setting rather than failing the tail rule: a field someone emptied
            // is them saying "none", which is a state this setting has and a mapping's tail has not.
            appState.setSoapTail(tailField.getText());
            clear(tailErrorLabel);
        }
        catch (IllegalArgumentException rejected)
        {
            show(tailErrorLabel, rejected.getMessage());
        }
    }

    // --- the rest of the tab -------------------------------------------------------------------

    /**
     * Resets this tab, confirming first because it discards the data file list.
     *
     * <p>
     * The confirmation is conditional on there being something to discard, for the reason the Log tab's is: a prompt
     * that always appears is a prompt people learn to dismiss without reading, which is exactly the habit that makes
     * the one that matters ineffective.
     */
    @FXML
    private void onResetToDefaults()
    {
        if (!appState.soapDataFiles().isEmpty() && !confirmClearingDataFiles())
        {
            return;
        }
        Settings.SoapSettings defaults = Settings.DEFAULTS.soap();
        appState.setSoapIp(defaults.ip());
        appState.setSoapMessageType(defaults.type());
        appState.setSoapDataFiles(List.of());
        appState.setSoapTail(defaults.tail());
        clear(ipErrorLabel);
        clear(tailErrorLabel);
    }

    private boolean confirmClearingDataFiles()
    {
        int count = appState.soapDataFiles().size();
        return dialogService.confirm("Reset the SOAP settings?", "This removes " + count
                + (count == 1 ? " data file" : " data files") + " from the list and cannot be undone.", "Reset");
    }

    private static void show(Label errorLabel, String message)
    {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private static void clear(Label errorLabel)
    {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private static Window windowOf(ActionEvent event)
    {
        return ((Node) event.getSource()).getScene().getWindow();
    }
}
