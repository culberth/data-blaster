package com.culberth.tools.datablaster.model;

import jakarta.annotation.PreDestroy;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import org.springframework.stereotype.Component;

/**
 * Joins {@link AppState} to {@link SettingsStore}: restores the stored values at start-up, then writes them back
 * whenever they change.
 *
 * <p>
 * <strong>Restore and subscribe are one call.</strong> {@link #bind(AppState)} does them in that order deliberately —
 * subscribing first would make the restore itself look like a user edit and write the file straight back on every
 * launch. Exposing them as two public methods would let a caller get that order wrong, so it is not offered.
 */
@Component
public class SettingsService
{

    private static final System.Logger LOG = System.getLogger(SettingsService.class.getName());

    /** How long {@link #flushOnShutdown()} waits for a queued write before giving up. */
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final SettingsStore store;

    /**
     * Single thread, so writes cannot interleave, and daemon so a pending write can never be the reason the JVM stays
     * alive after the last window closes.
     */
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable ->
    {
        Thread thread = new Thread(runnable, "settings-writer");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * The most recent settings not yet written, or {@code null} when nothing is outstanding.
     *
     * <p>
     * This is the coalescing buffer. A control that fires on every step of a drag would otherwise mean a file per step,
     * so holding only the latest collapses a burst into as few writes as the disk can keep up with, without a timer to
     * tune or a delay before the value is safe.
     */
    private final AtomicReference<Settings> pending = new AtomicReference<>();

    /** Strongly held: see {@link #bind(AppState)} for why this one need not be weak. */
    private final ChangeListener<Object> persistListener;

    /**
     * The mapping table's subscription, which cannot be the listener above.
     *
     * <p>
     * A {@code ChangeListener} on an {@code ObservableList} fires only when the property holding the list is set to a
     * different list — which never happens, because the list is a final field. Adding, editing or removing a mapping
     * would therefore reach nobody, and the edits would persist silently nowhere: no exception, no log line, just a
     * table that is empty again on the next launch.
     */
    private final ListChangeListener<PortTailMapping> mappingsListener;

    /** SOAP mode's data files, for the same reason and with the same failure mode. */
    private final ListChangeListener<File> dataFilesListener;

    private AppState boundState;

    public SettingsService(SettingsStore store)
    {
        this.store = store;
        this.persistListener = (observable, old, now) -> scheduleWrite();
        this.mappingsListener = change -> scheduleWrite();
        this.dataFilesListener = change -> scheduleWrite();
    }

    /**
     * Applies the stored settings to {@code appState}, then keeps the file in step with it.
     *
     * <p>
     * <strong>Call on the JavaFX Application Thread, before the shell is loaded.</strong> {@link AppState}'s mutators
     * require that thread, and the controls read their initial values from {@code AppState} in their
     * {@code initialize()} methods — so restoring after the shell exists would leave the controls showing defaults
     * while the state said otherwise.
     *
     * <p>
     * <strong>Why this is not part of Spring's lifecycle.</strong> Restoring in a {@code @PostConstruct} would run
     * during context refresh, where a failure fails the context — and the context starting is what the
     * start-even-when-a-part-fails rule protects. A settings file cannot be allowed to stop the application launching,
     * so it is read after the context is up and the UI thread is known.
     *
     * <p>
     * <strong>Why a plain listener rather than a weak one.</strong> {@code AppState}'s Javadoc requires weak
     * registration because its usual subscribers are prototype-scoped controllers that outlive their scene graph. This
     * subscriber is a singleton with the same lifetime as {@code AppState} itself, so there is nothing to leak — and a
     * weak listener here would be liable to collection, silently stopping the app from saving anything.
     */
    public void bind(AppState appState)
    {
        Settings restored = store.read();

        appState.setCurrentMode(restored.mode());
        appState.setTheme(restored.theme());

        appState.setBlastPort(restored.blastPort());
        appState.setSingleMessage(restored.singleMessage());
        appState.setByteHijack(restored.byteHijack());

        Settings.LogSettings log = restored.log();
        appState.setPlaybackSpeedFactor(log.playbackSpeedFactor());
        appState.setLogFolder(log.folderPath() == null ? null : new File(log.folderPath()));
        appState.setPortTailMappings(log.mappings());

        appState.setMessageType(restored.message().type());

        Settings.SoapSettings soap = restored.soap();
        appState.setSoapIp(soap.ip());
        appState.setSoapMessageType(soap.type());
        appState.setSoapDataFiles(soap.dataFilePaths().stream().map(File::new).toList());
        appState.setSoapTail(soap.tail());

        appState.currentModeProperty().addListener(persistListener);
        appState.themeProperty().addListener(persistListener);
        appState.blastPortProperty().addListener(persistListener);
        appState.singleMessageProperty().addListener(persistListener);
        appState.byteHijackProperty().addListener(persistListener);
        appState.playbackSpeedFactorProperty().addListener(persistListener);
        appState.logFolderProperty().addListener(persistListener);
        appState.portTailMappings().addListener(mappingsListener);
        appState.messageTypeProperty().addListener(persistListener);
        appState.soapIpProperty().addListener(persistListener);
        appState.soapMessageTypeProperty().addListener(persistListener);
        appState.soapDataFiles().addListener(dataFilesListener);
        appState.soapTailProperty().addListener(persistListener);

        this.boundState = appState;
        LOG.log(System.Logger.Level.DEBUG, () -> "Settings restored from " + store.location());
    }

    /**
     * Queues the current state for writing, coalescing with anything already outstanding.
     *
     * <p>
     * The values are read here, on the JavaFX Application Thread, rather than in the writer. What crosses to the
     * background thread is an immutable {@link Settings} captured at the moment of the change — not a reference to live
     * state the writer would have to read at whatever moment the disk got around to it. That includes the mapping set,
     * which {@code Settings} copies on the way in; handing the writer the live observable list would put a collection
     * the FX thread is still editing under a background reader.
     */
    private void scheduleWrite()
    {
        Settings settings = Settings.from(boundState);

        // Only submit a task when nothing is already queued. If a write is outstanding it has not
        // read the buffer yet, or it is about to be re-submitted below by the task that drains it.
        if (pending.getAndSet(settings) != null)
        {
            // The second theme switch of a session often lands here rather than queueing a task of
            // its own — one disk write covering both. A trace that logged only the queueing branch
            // would read as though the change had been dropped.
            LOG.log(System.Logger.Level.DEBUG,
                    () -> "scheduleWrite: coalesced into the outstanding write; theme=" + settings.theme());
        }
        else
        {
            LOG.log(System.Logger.Level.DEBUG, () -> "scheduleWrite: queued a write; theme=" + settings.theme());
            try
            {
                writer.execute(this::drain);
            }
            catch (java.util.concurrent.RejectedExecutionException shuttingDown)
            {
                // The app is closing and flushOnShutdown() has already run. The value is in the
                // buffer either way; losing a keystroke made during teardown is acceptable, and
                // failing here would surface as an exception in an FX change listener.
                LOG.log(System.Logger.Level.DEBUG, "Settings write skipped; writer is shut down");
            }
        }
    }

    private void drain()
    {
        Settings settings = pending.getAndSet(null);
        if (settings != null)
        {
            store.write(settings);
        }
    }

    /**
     * Writes anything still buffered, on the way down.
     *
     * <p>
     * Without this, changing a setting and immediately quitting would lose it: the coalescing buffer exists precisely
     * so that not every change reaches the disk straight away, and the writer thread is a daemon, so nothing else would
     * keep the JVM alive long enough to finish.
     */
    @PreDestroy
    void flushOnShutdown()
    {
        // Unregister first, and unregister everything that was registered — the mapping listener
        // included, or a detached service goes on writing every time the table changes. AppState
        // outlives this service in every context that has more than one, which in production is
        // none but in tests is routine: a service bound to the shared AppState and never detached
        // keeps writing to a directory the test has finished with. That showed up as a temp
        // directory JUnit could not delete because something kept recreating a file in it, on Linux
        // only, well away from anything that looked related. Both list subscriptions count: they
        // are registered differently from the rest, which is what makes them easy to leave behind.
        if (boundState != null)
        {
            boundState.currentModeProperty().removeListener(persistListener);
            boundState.themeProperty().removeListener(persistListener);
            boundState.blastPortProperty().removeListener(persistListener);
            boundState.singleMessageProperty().removeListener(persistListener);
            boundState.byteHijackProperty().removeListener(persistListener);
            boundState.playbackSpeedFactorProperty().removeListener(persistListener);
            boundState.logFolderProperty().removeListener(persistListener);
            boundState.portTailMappings().removeListener(mappingsListener);
            boundState.messageTypeProperty().removeListener(persistListener);
            boundState.soapIpProperty().removeListener(persistListener);
            boundState.soapMessageTypeProperty().removeListener(persistListener);
            boundState.soapDataFiles().removeListener(dataFilesListener);
            boundState.soapTailProperty().removeListener(persistListener);
        }

        writer.shutdown();
        try
        {
            if (!writer.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            {
                LOG.log(System.Logger.Level.WARNING, "Timed out waiting for the settings write");
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        // Whatever arrived after the last drain, or was rejected once shutdown() had been called.
        drain();
    }
}
