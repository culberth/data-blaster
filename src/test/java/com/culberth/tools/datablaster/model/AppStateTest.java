package com.culberth.tools.datablaster.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * These run with no JavaFX toolkit. The thread guard deliberately records the FX thread rather
 * than calling {@code Platform.isFxApplicationThread()}, which would initialise the toolkit and
 * load native libraries — making this class require a display and fail on a headless agent.
 */
class AppStateTest {

    private AppState appState;

    @BeforeEach
    void markThisThreadAsTheFxThread() {
        // Stands in for UI start-up. Re-marked per test so the static cannot leak between them.
        AppState.markFxApplicationThread();
        appState = new AppState();
    }

    /**
     * Puts the JVM-wide static back, which the re-mark above does not do.
     *
     * <p>Surefire runs every test class in one JVM, so leaving this thread recorded made
     * {@code FxmlSmokeTest} measure the real JavaFX Application Thread against Surefire's — which
     * passed on Windows and failed on Linux purely on the order the classes happened to run in.
     */
    @AfterEach
    void forgetThisThreadAsTheFxThread() {
        AppState.forgetFxApplicationThread();
    }

    @Test
    void initialSnapshotIsDerivedFromThePropertyDefaults() {
        AppState.Snapshot snapshot = appState.snapshot();
        assertNotNull(snapshot);
        assertEquals(0.0, snapshot.simFactor());
        assertNull(snapshot.logFolderPath());
        assertNull(snapshot.currentViewId());
    }

    @Test
    void snapshotReflectsAWriteRatherThanBeingStale() {
        // Not snapshot()==snapshot(): that would pass even if snapshot() returned null.
        appState.setCurrentViewId("view3");
        appState.setSimFactor(2.5);
        AppState.Snapshot snapshot = appState.snapshot();
        assertEquals("view3", snapshot.currentViewId());
        assertEquals(2.5, snapshot.simFactor());
    }

    @Test
    void snapshotOmitsCosmeticState() {
        // Snapshot is the web layer's contract; window-scoped display values do not belong in it.
        assertEquals(3, AppState.Snapshot.class.getRecordComponents().length);
    }

    @Test
    void mutatingOffTheFxThreadFailsLoudlyRatherThanRacing() throws Exception {
        assertThrowsOffThread(() -> appState.setSimFactor(1.5));
        assertThrowsOffThread(() -> appState.setCurrentViewId("view2"));
        assertThrowsOffThread(() -> appState.setLogFolder(new File(".")));
        assertThrowsOffThread(() -> appState.setContentOpacity(0.5));
    }

    @Test
    void theThreadingFailureNamesTheEscapeHatch() throws Exception {
        Throwable thrown = assertThrowsOffThread(() -> appState.setSimFactor(1.5));
        assertTrue(thrown.getMessage().contains("onFxThread"), thrown.getMessage());
    }

    @Test
    void propertyAccessorsExposeNoSetter() throws Exception {
        // The guard would be pointless if a caller could reach the mutable property instead —
        // this pins the accessors' return types as read-only.
        for (String accessor : new String[] {
                "simFactorProperty", "logFolderProperty", "contentOpacityProperty", "currentViewIdProperty"}) {
            Class<?> returned = AppState.class.getMethod(accessor).getReturnType();
            assertTrue(returned.getSimpleName().startsWith("ReadOnly"),
                    accessor + " returns " + returned.getSimpleName());
        }
    }

    /** Runs {@code mutation} on a non-FX thread and returns the exception it threw. */
    private static Throwable assertThrowsOffThread(Runnable mutation) throws InterruptedException {
        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                mutation.run();
            } catch (Throwable t) {
                caught.set(t);
            }
        }, "not-the-fx-thread");
        worker.start();
        worker.join(5000);
        Throwable thrown = caught.get();
        assertNotNull(thrown, "expected the mutation to be rejected off the FX thread");
        return assertInstanceOf(IllegalStateException.class, thrown);
    }
}
