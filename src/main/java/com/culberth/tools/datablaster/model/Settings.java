package com.culberth.tools.datablaster.model;

import java.io.File;

/**
 * The subset of {@link AppState} that survives a restart.
 *
 * <p><strong>What is deliberately not here.</strong> {@code contentOpacity} is a view control, not
 * a setting — it is adjusted while looking at something and reset with the button next to it, and
 * restoring a half-transparent window would look like a rendering fault. {@code currentViewId} is
 * left out because the four views are placeholders; restoring one is not behaviour a template
 * should model for a fork whose views will mean something.
 *
 * <p>An immutable carrier rather than a live object: it is handed to a background thread for
 * writing, and a record that cannot change underneath that thread needs no synchronisation.
 *
 * @param simFactor     the Sim Factor preference, clamped to the slider's range on read
 * @param logFolderPath the chosen log folder, or {@code null} if none has been chosen
 * @param theme         the colour theme, falling back to {@link Theme#LIGHT} on an unknown name
 */
public record Settings(double simFactor, String logFolderPath, Theme theme) {

    /** Matches the Sim Factor slider in {@code appearance-group.fxml}. */
    public static final double SIM_FACTOR_MIN = -5.0;

    /** Matches the Sim Factor slider in {@code appearance-group.fxml}. */
    public static final double SIM_FACTOR_MAX = 5.0;

    /** What a first run — or an unreadable store — starts from. */
    public static final Settings DEFAULTS = new Settings(0.0, null, Theme.LIGHT);

    /**
     * The values currently held in {@code appState}, ready to be written.
     *
     * <p><strong>Read on the JavaFX Application Thread.</strong> This used to take an
     * {@link AppState.Snapshot}, which reads safely from anywhere — but the theme is deliberately
     * not in that record, and widening it to carry a cosmetic value would break the contract its
     * Javadoc states. Reading the properties directly is correct here because the only caller is a
     * change listener, which already runs on the FX thread, where nothing else can interleave.
     */
    public static Settings from(AppState appState) {
        File folder = appState.getLogFolder();
        return new Settings(
                appState.getSimFactor(),
                folder == null ? null : folder.getAbsolutePath(),
                appState.getTheme());
    }
}
