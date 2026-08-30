package com.culberth.tools.datablaster.model;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * The subset of {@link AppState} that survives a restart, grouped the way the modes are.
 *
 * <p><strong>Why it is nested rather than flat.</strong> The modes' settings have nothing in common
 * — a folder and a table of mappings for Log, one choice for Message, a port for SOAP — so a flat
 * record would be a widening list of unrelated scalars whose only clue to what belongs where is a
 * name prefix. The nesting mirrors the file's key namespaces ({@code log.*}, {@code message.*},
 * {@code soap.*}) and the Preferences tabs, so all three can be read against each other.
 *
 * <p><strong>What is deliberately not here.</strong> {@code contentOpacity} is a view control, not
 * a setting — it is adjusted while looking at something and reset with the button next to it, and
 * restoring a half-transparent window would look like a rendering fault.
 *
 * <p><strong>The selected mode is here, and used not to be.</strong> The template excluded its
 * {@code currentViewId} because the four views were placeholders and restoring one modelled
 * behaviour a fork would not want. The views are modes now, and which mode you were last in is
 * exactly the kind of thing a tool should remember, so the exclusion reverses.
 *
 * <p>An immutable carrier rather than a live object: it is handed to a background thread for
 * writing, and a record that cannot change underneath that thread needs no synchronisation. That
 * only holds if every collection it carries is copied and unmodifiable — a record wrapping a
 * mutable {@code List} is not immutable, whatever its accessors say. {@link LogSettings} enforces
 * that in its constructor rather than trusting callers.
 *
 * @param mode    the mode that was selected when the application last closed
 * @param theme   the colour theme, falling back to {@link Theme#LIGHT} on an unknown name
 * @param log     Log mode's settings
 * @param message Message mode's settings
 * @param soap    SOAP mode's settings
 */
public record Settings(Mode mode,
                       Theme theme,
                       LogSettings log,
                       MessageSettings message,
                       SoapSettings soap) {

    /**
     * The slowest playback the setting offers.
     *
     * <p><strong>Renamed and re-valued from the template's {@code SIM_FACTOR_MIN}, not
     * re-pointed.</strong> The old range was -5.0 to 5.0, which had no meaning once the value
     * became a multiplier of real time: {@code 0.0} would be frozen and a negative would be
     * reverse, and neither is a speed this setting offers. Leaving the old name on the new meaning
     * is how a constant and the thing it constrains drift apart, so the name moved with the value.
     */
    public static final double PLAYBACK_SPEED_MIN = 0.1;

    /** @see #PLAYBACK_SPEED_MIN */
    public static final double PLAYBACK_SPEED_MAX = 10.0;

    /** Real time. The value a user returns to, and what a first run starts at. */
    public static final double PLAYBACK_SPEED_DEFAULT = 1.0;

    /**
     * The SOAP port's default.
     *
     * <p>Deliberately not 8080: the embedded HTTP layer binds that, and defaulting the two to the
     * same port would make the out-of-box state a conflict.
     */
    public static final int SOAP_PORT_DEFAULT = 8081;

    /** What a first run — or an unreadable store — starts from. */
    public static final Settings DEFAULTS = new Settings(
            Mode.LOG,
            Theme.LIGHT,
            new LogSettings(PLAYBACK_SPEED_DEFAULT, null, List.of()),
            new MessageSettings(MessageType.MESSAGE_1),
            new SoapSettings(SOAP_PORT_DEFAULT));

    /**
     * Log mode's settings.
     *
     * @param playbackSpeedFactor a multiplier on real time — {@code 1.0} is real time, {@code 2.0}
     *                            twice as fast, {@code 0.5} half speed. Range
     *                            {@value Settings#PLAYBACK_SPEED_MIN}–{@value Settings#PLAYBACK_SPEED_MAX};
     *                            a stored value outside it falls back rather than being clamped,
     *                            because clamping {@code 0.0} up to {@code 0.1} would start
     *                            playback crawling and report nothing
     * @param folderPath          the chosen log folder, or {@code null} if none has been chosen
     * @param mappings            port-to-tail-number mappings, unique in both directions
     */
    public record LogSettings(double playbackSpeedFactor,
                              String folderPath,
                              List<PortTailMapping> mappings) {

        public LogSettings {
            // Copied, ordered and made unmodifiable here so the enclosing record's claim to be
            // immutable is true of the whole object graph, not just its top level.
            mappings = PortTailMapping.requireUniquePortsAndTails(mappings);
        }

        /** The same values with a different mapping set — the shape every mapping edit takes. */
        public LogSettings withMappings(Collection<PortTailMapping> replacement) {
            return new LogSettings(playbackSpeedFactor, folderPath, List.copyOf(replacement));
        }
    }

    /**
     * Message mode's settings.
     *
     * @param type which kind of message the mode will send
     */
    public record MessageSettings(MessageType type) {
    }

    /**
     * SOAP mode's settings.
     *
     * <p>The port is validated for range but never checked for availability — that is a bind-time
     * concern, and the mode behaviour that would bind it does not exist yet.
     *
     * @param port the port SOAP mode will listen on,
     *             {@value PortTailMapping#PORT_MIN}–{@value PortTailMapping#PORT_MAX}
     */
    public record SoapSettings(int port) {
    }

    /**
     * The values currently held in {@code appState}, ready to be written.
     *
     * <p><strong>Read on the JavaFX Application Thread.</strong> This used to take an
     * {@link AppState.Snapshot}, which reads safely from anywhere — but the theme, the message
     * type, the SOAP port and the mappings are all deliberately absent from that record, and
     * widening it to carry them would break the contract its Javadoc states. Reading the properties
     * directly is correct here because the only caller is a change listener, which already runs on
     * the FX thread, where nothing else can interleave.
     */
    public static Settings from(AppState appState) {
        File folder = appState.getLogFolder();
        return new Settings(
                appState.getCurrentMode(),
                appState.getTheme(),
                new LogSettings(
                        appState.getPlaybackSpeedFactor(),
                        folder == null ? null : folder.getAbsolutePath(),
                        List.copyOf(appState.portTailMappings())),
                new MessageSettings(appState.getMessageType()),
                new SoapSettings(appState.getSoapPort()));
    }
}
