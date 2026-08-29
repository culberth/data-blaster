package com.culberth.tools.datablaster.model;

import java.io.File;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.springframework.stereotype.Component;

/**
 * Application-wide UI state, held apart from any controller so that several controllers (and the
 * web layer) can share it without depending on each other.
 *
 * <p><strong>Threading.</strong> This state belongs to the JavaFX Application Thread. The
 * mutators enforce that and throw if called from anywhere else; the property accessors are
 * read-only so there is no second, unguarded way in. Code running off the FX thread — notably
 * Tomcat worker threads in {@code com.culberth.tools.datablaster.web} — must read through
 * {@link #snapshot()} and write through {@link #onFxThread(Runnable)}.
 *
 * <p><strong>Listeners.</strong> This is an application-lifetime singleton and its subscribers are
 * prototype-scoped FXML controllers, so a listener registered here outlives the scene graph that
 * registered it. Subscribe with {@code javafx.beans.value.WeakChangeListener} (keeping the strong
 * reference in the controller) so a discarded shell is collected instead of being retained — and
 * kept live enough to keep reacting.
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

    /** Demo user-preference value driven by the "Sim Factor" slider; -5.0 to 5.0 in 0.1 steps. */
    private final DoubleProperty simFactor = new SimpleDoubleProperty(0.0);

    private final ObjectProperty<File> logFolder = new SimpleObjectProperty<>(null);

    private final StringProperty currentViewId = new SimpleStringProperty(null);

    /**
     * The colour theme. Deliberately absent from {@link Snapshot}: that record is documented as the
     * fields a caller outside the UI could meaningfully use, and which colours a window is painted
     * in is not one of them. {@link Settings} therefore reads this directly, on the FX thread.
     */
    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(Theme.LIGHT);

    /**
     * Opacity applied to the content area. Lives here rather than as a binding between the
     * Appearance ribbon group and the content host, so neither has to hold a reference to the
     * other's nodes.
     */
    private final DoubleProperty contentOpacity = new SimpleDoubleProperty(1.0);

    /**
     * Snapshot of the fields off-thread callers can act on. Written on the FX thread, read on any
     * thread; {@code volatile} publishes the whole record safely.
     */
    private volatile Snapshot snapshot;

    public AppState() {
        Runnable resync = this::resnapshot;
        simFactor.addListener((obs, old, now) -> resync.run());
        logFolder.addListener((obs, old, now) -> resync.run());
        currentViewId.addListener((obs, old, now) -> resync.run());
        contentOpacity.addListener((obs, old, now) -> resync.run());
        // Derived rather than duplicated: a hardcoded initial Snapshot would silently drift from
        // the property defaults above the moment one of them changed.
        resnapshot();
    }

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

    public ReadOnlyDoubleProperty simFactorProperty() {
        return simFactor;
    }

    public double getSimFactor() {
        return simFactor.get();
    }

    public void setSimFactor(double value) {
        requireFxThread();
        simFactor.set(value);
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

    public ReadOnlyStringProperty currentViewIdProperty() {
        return currentViewId;
    }

    public String getCurrentViewId() {
        return currentViewId.get();
    }

    public void setCurrentViewId(String value) {
        requireFxThread();
        currentViewId.set(value);
    }

    /**
     * An immutable view of the state an off-thread caller can act on. This is the only supported
     * way for non-FX threads (e.g. HTTP request handlers) to observe app state.
     *
     * <p>Keep this to fields a caller outside the UI could meaningfully use. Cosmetic, window-scoped
     * values do not belong in a record the web layer reads.
     */
    public Snapshot snapshot() {
        return snapshot;
    }

    private void resnapshot() {
        File folder = logFolder.get();
        snapshot = new Snapshot(
                simFactor.get(),
                folder == null ? null : folder.getAbsolutePath(),
                currentViewId.get());
    }

    /** Immutable, thread-safe carrier for {@link AppState#snapshot()}. */
    public record Snapshot(
            double simFactor,
            String logFolderPath,
            String currentViewId) {
    }

    /**
     * Fails loudly when a mutator is called off the JavaFX Application Thread. JavaFX properties
     * perform no thread check of their own, so without this an off-thread write would run the
     * listener chain — and {@link #resnapshot()}, which reads the properties non-atomically — on
     * that thread, producing a torn snapshot and an unsynchronized write to UI-owned state.
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
