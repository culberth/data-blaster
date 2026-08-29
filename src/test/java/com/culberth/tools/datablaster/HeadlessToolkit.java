package com.culberth.tools.datablaster;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;

/**
 * Starts the JavaFX toolkit on Monocle's headless Glass platform, so tests that build a real scene
 * graph need no display.
 *
 * <p><strong>Why this exists.</strong> Most of this project's tests deliberately avoid the toolkit
 * entirely — see {@code AppStateTest}, which records the FX thread rather than probing it for
 * exactly that reason. But the FXML wiring cannot be verified without instantiating controls, so
 * {@code FxmlSmokeTest} needs a toolkit. Monocle keeps that from costing a display, which is what
 * lets CI's Linux runner prove the suite is headless rather than the developer asserting it.
 *
 * <p><strong>Only tests that build a scene graph may use this class.</strong> Calling
 * {@link #start()} from a test that does not need one silently widens the toolkit's footprint and
 * re-introduces the coupling the rest of the suite avoids. Public only so those tests can live in
 * the package they belong to rather than being dragged into this one.
 */
public final class HeadlessToolkit {

    /** Toolkit start-up is a JVM-wide, one-shot operation; this guards the second call. */
    private static boolean started;

    private HeadlessToolkit() {
    }

    /**
     * Initialises the toolkit if it is not already running. Idempotent, so every test class that
     * needs a scene graph can call it from {@code @BeforeAll} without coordinating with the others.
     */
    public static synchronized void start() {
        if (started) {
            return;
        }

        // Must be set before Platform.startup(): Glass reads them while selecting a platform, and
        // a toolkit already running would ignore them.
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        // Software pipeline. CI runners have no GPU, and falling back to it at run time prints a
        // warning that reads like a failure in the build log.
        System.setProperty("prism.order", "sw");
        System.setProperty("java.awt.headless", "true");

        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyRunning) {
            // Another test class in this JVM got here first without going through this method.
            started = true;
            return;
        }

        await(ready, "JavaFX toolkit did not start");

        // Without this, a test that shows a Stage and then closes it takes the toolkit down with
        // it: JavaFX exits implicitly when the last window closes, and every later runLater is
        // silently never run — which surfaces as unrelated tests timing out rather than as anything
        // pointing at the window. The application does not need it because DataBlasterApplication
        // owns its own lifecycle; a test JVM that outlives its windows does.
        Platform.setImplicitExit(false);

        started = true;
    }

    /**
     * Runs {@code action} on the JavaFX Application Thread and waits for it, re-throwing whatever
     * it threw.
     *
     * <p>Failures are carried back rather than left to the FX thread's default handler, which would
     * print a stack trace and let the test pass — the precise failure mode these tests exist to
     * catch.
     */
    public static void onFxThread(ThrowingRunnable action) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        await(done, "Action on the JavaFX Application Thread did not complete");

        Throwable thrown = failure.get();
        if (thrown instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (thrown instanceof Error error) {
            throw error;
        }
        if (thrown != null) {
            throw new IllegalStateException(thrown);
        }
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message + " within 30s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message + "; interrupted while waiting", e);
        }
    }

    /** An action that may throw a checked exception, so FXML loading can be passed in directly. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
