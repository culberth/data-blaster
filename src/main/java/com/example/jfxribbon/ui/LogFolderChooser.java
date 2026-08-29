package com.example.jfxribbon.ui;

import java.io.File;
import java.util.Optional;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

/**
 * Presents the "choose a log folder" dialog, for the one setting that has two surfaces.
 *
 * <p>Extracted when Preferences gained the same control the ribbon already had. The two surfaces
 * were never going to duplicate <em>state</em> — both write {@link com.example.jfxribbon.model.AppState} —
 * but they would have duplicated the chooser, and the title, the remembered directory and the
 * null-means-cancelled convention are the kind of thing that drifts once it exists twice.
 *
 * <p><strong>A singleton, which is the point.</strong> The remembered directory now outlives the
 * control that opened it. Previously the {@code DirectoryChooser} was a field on the prototype
 * ribbon controller, so it was discarded with the ribbon and the next Browse started back at the
 * default location.
 *
 * <p><strong>The owner is a parameter, not {@link StageRegistry}.</strong> A chooser opened from
 * the modal Preferences dialog must be owned by that dialog; owning it from the primary stage
 * instead lets the modal window sit in front of the chooser the user just asked for.
 *
 * <p>Call on the JavaFX Application Thread.
 */
@Component
public class LogFolderChooser {

    private final DirectoryChooser chooser = new DirectoryChooser();

    /**
     * Shows the chooser and returns the selected folder, or empty if it was cancelled.
     *
     * @param owner the window the chooser should be modal to
     */
    public Optional<File> choose(Window owner) {
        chooser.setTitle("Choose Log Folder");
        File selected = chooser.showDialog(owner);
        if (selected != null) {
            // Reopen where they left off rather than at the default location.
            chooser.setInitialDirectory(selected);
        }
        return Optional.ofNullable(selected);
    }
}
