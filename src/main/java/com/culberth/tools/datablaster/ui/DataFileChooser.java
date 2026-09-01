package com.culberth.tools.datablaster.ui;

import java.io.File;
import java.util.List;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

/**
 * Presents the "choose data files" dialog for SOAP mode.
 *
 * <p>The sibling of {@link LogFolderChooser}, and a singleton for the same reason: the remembered
 * directory has to outlive the control that opened it. A {@code FileChooser} held as a field on a
 * prototype controller is discarded with its node tree, so the next Browse starts back at the
 * default location — which is the whole of what "remembered" was supposed to mean.
 *
 * <p><strong>The owner is a parameter, not {@link StageRegistry}.</strong> A chooser opened from the
 * modal Preferences dialog must be owned by that dialog; owning it from the primary stage instead
 * lets the modal window sit in front of the chooser the user just asked for.
 *
 * <p><strong>Multiple selection, and no extension filter.</strong> The setting is a list, so
 * choosing four files should be one trip through the dialog rather than four. Nothing reads these
 * files yet, so there is no format to filter on — inventing one now would refuse files the mode
 * turns out to accept, and a filter is easier to add later than to loosen after people have worked
 * around it.
 *
 * <p>Call on the JavaFX Application Thread.
 */
@Component
public class DataFileChooser {

    private final FileChooser chooser = new FileChooser();

    /**
     * Shows the chooser and returns the selected files, or an empty list if it was cancelled.
     *
     * @param owner the window the chooser should be modal to
     */
    public List<File> choose(Window owner) {
        chooser.setTitle("Choose Data Files");
        List<File> selected = chooser.showOpenMultipleDialog(owner);
        if (selected == null || selected.isEmpty()) {
            // showOpenMultipleDialog returns null on cancel. An empty list says the same thing to
            // a caller without making every one of them write the null check.
            return List.of();
        }
        // Reopen where they left off rather than at the default location. The parent of a chosen
        // file, not the file itself: setInitialDirectory expects a directory and silently ignores
        // anything else, which would look like the setting simply not working.
        File parent = selected.get(0).getParentFile();
        if (parent != null && parent.isDirectory()) {
            chooser.setInitialDirectory(parent);
        }
        return List.copyOf(selected);
    }
}
