package com.culberth.tools.datablaster.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.stereotype.Component;

/**
 * Application-wide UI state, held apart from any controller so that several controllers (and the
 * web layer) can share it without depending on each other.
 *
 * <p><strong>Threading.</strong> This state belongs to the JavaFX Application Thread. The
 * mutators enforce that and throw if called from anywhere else; the property accessors are
 * read-only so there is no second, unguarded way in. Code running off the FX thread writes through
 * {@link #onFxThread(Runnable)}.
 *
 * <p><strong>There is no off-thread read path, and that is a deliberate absence.</strong> This
 * class used to publish an immutable {@code Snapshot} record through a {@code volatile} field, kept
 * current by listeners on every field in it, because the loopback HTTP layer read state from Tomcat
 * worker threads. That layer is gone, and a record with no reader — maintained on every write, and
 * tested as though it had one — is scaffolding rather than design.
 *
 * <p>When SOAP mode brings a server back, the pattern comes back with it and should be rebuilt
 * rather than improvised: an immutable record published through a {@code volatile} field, rewritten
 * on the FX thread by a listener, and deliberately narrower than this class. The narrowness is the
 * part worth remembering — tail numbers are the most identifying data this tool holds, and a
 * loopback bind separates hosts rather than users, so a projection built for one handler is what
 * stops the next one leaking them by accident. {@link Settings#from(AppState)} is the nearest live
 * example of the shape: read on the FX thread, immutable once it crosses.
 *
 * <p><strong>The same rule covers the collection.</strong> {@link #portTailMappings()} returns an
 * unmodifiable view, not the live list. Handing out the backing list would reopen exactly the hole
 * the read-only property accessors exist to close — a caller could add an entry from any thread,
 * past the guard, and bypass the uniqueness rules {@link #setPortTailMappings} enforces.
 *
 * <p><strong>Listeners.</strong> This is an application-lifetime singleton and its subscribers are
 * prototype-scoped FXML controllers, so a listener registered here outlives the scene graph that
 * registered it. Subscribe with {@code javafx.beans.value.WeakChangeListener} (keeping the strong
 * reference in the controller) so a discarded shell is collected instead of being retained — and
 * kept live enough to keep reacting. For the mapping list that means a
 * {@code WeakListChangeListener}: a {@code ChangeListener} on an {@code ObservableList} fires only
 * when the list object itself is replaced, which never happens here, so element edits would reach
 * nobody.
 */
@Component
public class AppState {

    /**
     * The JavaFX Application Thread, recorded when the UI starts.
     *
     * <p>Deliberately not {@code Platform.isFxApplicationThread()}: that call initialises the
     * JavaFX toolkit and loads native libraries, which would make the guard — and therefore every
     * test touching this class — require a display. Recording the thread keeps the check free.
     */
    private static volatile Thread fxApplicationThread;

    /**
     * The mode the application is in. Persisted.
     *
     * <p>The initial values throughout this class are read from {@link Settings#DEFAULTS} rather
     * than repeated as literals. Two copies of "the default playback speed is 1.0" is precisely the
     * drift the settings constants were renamed to prevent.
     */
    private final ObjectProperty<Mode> currentMode =
            new SimpleObjectProperty<>(Settings.DEFAULTS.mode());

    /**
     * Log mode's playback speed: a multiplier on real time, {@code 1.0} being real time.
     *
     * <p>The template's "Sim Factor", renamed and re-specified. It is a Log-mode setting that
     * happens to have existed before the modes did, which is why it sits in this flat list rather
     * than in a nested holder — see {@link Settings} for why the persisted form groups the
     * mode-scoped values and this class does not.
     */
    private final DoubleProperty playbackSpeedFactor =
            new SimpleDoubleProperty(Settings.DEFAULTS.log().playbackSpeedFactor());

    /** Log mode's capture folder, or {@code null} if none has been chosen. */
    private final ObjectProperty<File> logFolder = new SimpleObjectProperty<>(null);

    /**
     * Log mode's port-to-tail-number table.
     *
     * <p>Private and never handed out: {@link #portTailMappings()} exposes an unmodifiable view of
     * it. Every write goes through {@link #setPortTailMappings}, which is where the FX-thread guard
     * and the uniqueness rules both live.
     */
    private final ObservableList<PortTailMapping> portTailMappings =
            FXCollections.observableArrayList();

    private final ObservableList<PortTailMapping> portTailMappingsView =
            FXCollections.unmodifiableObservableList(portTailMappings);

    /** Message mode's only setting. */
    private final ObjectProperty<MessageType> messageType =
            new SimpleObjectProperty<>(Settings.DEFAULTS.message().type());

    /** SOAP mode's listen port. Validated for range, never checked for availability. */
    private final IntegerProperty soapPort =
            new SimpleIntegerProperty(Settings.DEFAULTS.soap().port());

    /** The colour theme. Read by {@link Settings} directly, on the FX thread. */
    private final ObjectProperty<Theme> theme =
            new SimpleObjectProperty<>(Settings.DEFAULTS.theme());

    /**
     * Opacity applied to the content area. Lives here rather than as a binding between the
     * Appearance ribbon group and the content host, so neither has to hold a reference to the
     * other's nodes.
     */
    private final DoubleProperty contentOpacity = new SimpleDoubleProperty(1.0);

    /**
     * Records the calling thread as the JavaFX Application Thread. Called once during UI start-up;
     * until it is, the mutators do not enforce a thread (there is no UI to protect).
     */
    public static void markFxApplicationThread() {
        fxApplicationThread = Thread.currentThread();
    }

    /**
     * Forgets the recorded thread, restoring the state a fresh JVM starts in.
     *
     * <p>Package-private, and for tests only — which is why it is not next to a public setter.
     * {@link #markFxApplicationThread()} writes a JVM-wide static, so a test that records its own
     * thread leaves every later test in the same JVM measured against a thread that is not the FX
     * one. That is not hypothetical: it made the FXML smoke tests pass on Windows and fail on Linux
     * purely on the order Surefire happened to run the classes in, with the guard reporting that
     * the JavaFX Application Thread was not the JavaFX Application Thread.
     */
    static void forgetFxApplicationThread() {
        fxApplicationThread = null;
    }

    // --- mode ---------------------------------------------------------------------------------

    public ReadOnlyObjectProperty<Mode> currentModeProperty() {
        return currentMode;
    }

    public Mode getCurrentMode() {
        return currentMode.get();
    }

    /**
     * Selects a mode.
     *
     * <p>Null is rejected rather than accepted as "no mode". The template's string view id started
     * null and the shell treated that as "nothing chosen yet"; a closed enum with a persisted
     * default has no such state, and allowing one back in would mean every reader needed a null
     * branch for a case that cannot happen.
     */
    public void setCurrentMode(Mode value) {
        requireFxThread();
        if (value == null) {
            throw new IllegalArgumentException("A mode is required; there is no 'no mode' state");
        }
        currentMode.set(value);
    }

    // --- Log mode -----------------------------------------------------------------------------

    public ReadOnlyDoubleProperty playbackSpeedFactorProperty() {
        return playbackSpeedFactor;
    }

    public double getPlaybackSpeedFactor() {
        return playbackSpeedFactor.get();
    }

    public void setPlaybackSpeedFactor(double value) {
        requireFxThread();
        playbackSpeedFactor.set(value);
    }

    public ReadOnlyObjectProperty<File> logFolderProperty() {
        return logFolder;
    }

    public File getLogFolder() {
        return logFolder.get();
    }

    public void setLogFolder(File value) {
        requireFxThread();
        logFolder.set(value);
    }

    /**
     * The port-to-tail-number table, as an unmodifiable observable view.
     *
     * <p>Observable so a table can track it, unmodifiable so it cannot become a second way in. A
     * caller that wants to change the set calls {@link #setPortTailMappings},
     * {@link #addPortTailMapping} or {@link #removePortTailMappingForPort} — all of which run the
     * thread guard and the uniqueness rules.
     */
    public ObservableList<PortTailMapping> portTailMappings() {
        return portTailMappingsView;
    }

    /**
     * Replaces the whole mapping set.
     *
     * <p>Replaced wholesale rather than mutated in place, so the uniqueness rules are checked
     * against the complete set every time, and so a subscriber sees one change event per edit
     * rather than a remove followed by an add.
     *
     * @throws IllegalArgumentException if a port or a tail appears twice, leaving the current set
     *                                  untouched
     */
    public void setPortTailMappings(Collection<PortTailMapping> mappings) {
        requireFxThread();
        // Validated into a new list before anything is published, so a rejected edit cannot leave
        // the observable list half-updated for whoever is watching it.
        List<PortTailMapping> validated = PortTailMapping.requireUniquePortsAndTails(mappings);
        portTailMappings.setAll(validated);
    }

    /**
     * Adds one mapping, rejecting it if either its port or its tail is already spoken for.
     *
     * @throws IllegalArgumentException with a message written to be shown to the user
     */
    public void addPortTailMapping(PortTailMapping mapping) {
        requireFxThread();
        List<PortTailMapping> candidate = new ArrayList<>(portTailMappings);
        candidate.add(mapping);
        setPortTailMappings(candidate);
    }

    /** Removes the mapping for {@code port}, if there is one. */
    public boolean removePortTailMappingForPort(int port) {
        requireFxThread();
        List<PortTailMapping> remaining = new ArrayList<>(portTailMappings);
        boolean removed = remaining.removeIf(mapping -> mapping.port() == port);
        if (removed) {
            setPortTailMappings(remaining);
        }
        return removed;
    }

    // --- Message mode -------------------------------------------------------------------------

    public ReadOnlyObjectProperty<MessageType> messageTypeProperty() {
        return messageType;
    }

    public MessageType getMessageType() {
        return messageType.get();
    }

    public void setMessageType(MessageType value) {
        requireFxThread();
        if (value == null) {
            throw new IllegalArgumentException("A message type is required");
        }
        messageType.set(value);
    }

    // --- SOAP mode ----------------------------------------------------------------------------

    public ReadOnlyIntegerProperty soapPortProperty() {
        return soapPort;
    }

    public int getSoapPort() {
        return soapPort.get();
    }

    /**
     * Sets SOAP mode's listen port.
     *
     * <p>Range-checked here as well as in the store, because this is the path a UI control takes
     * and the store's tolerance rules are about files, not about what the application will accept
     * from itself.
     */
    public void setSoapPort(int value) {
        requireFxThread();
        if (value < PortTailMapping.PORT_MIN || value > PortTailMapping.PORT_MAX) {
            throw new IllegalArgumentException(
                    "Port must be between " + PortTailMapping.PORT_MIN + " and "
                            + PortTailMapping.PORT_MAX + ", but was " + value);
        }
        soapPort.set(value);
    }

    // --- global -------------------------------------------------------------------------------

    public ReadOnlyDoubleProperty contentOpacityProperty() {
        return contentOpacity;
    }

    public double getContentOpacity() {
        return contentOpacity.get();
    }

    public void setContentOpacity(double value) {
        requireFxThread();
        contentOpacity.set(value);
    }

    public ReadOnlyObjectProperty<Theme> themeProperty() {
        return theme;
    }

    public Theme getTheme() {
        return theme.get();
    }

    public void setTheme(Theme value) {
        requireFxThread();
        theme.set(value);
    }

    /**
     * Fails loudly when a mutator is called off the JavaFX Application Thread.
     *
     * <p>JavaFX properties perform no thread check of their own, so without this an off-thread
     * write would run the whole listener chain on that thread — every binding, every control that
     * observes the value, and the settings writer's change listener — producing unsynchronized
     * writes to UI-owned state and a settings snapshot read while another thread was still editing
     * it.
     */
    private static void requireFxThread() {
        Thread fxThread = fxApplicationThread;
        if (fxThread != null && Thread.currentThread() != fxThread) {
            throw new IllegalStateException(
                    "AppState may only be modified on the JavaFX Application Thread; "
                            + "call AppState.onFxThread(Runnable) from " + Thread.currentThread().getName());
        }
    }

    /** Runs {@code action} on the JavaFX Application Thread, from wherever the caller is. */
    public static void onFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
