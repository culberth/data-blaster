package com.culberth.tools.datablaster.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

    // --- the defaults ---------------------------------------------------------------------------

    /**
     * The defaults come from {@link Settings#DEFAULTS} rather than being repeated as literals in
     * this class, so this is the check that the two have not been wired to different values.
     */
    @Test
    @DisplayName("a fresh AppState starts at the documented defaults")
    void aFreshAppStateStartsAtTheDocumentedDefaults() {
        assertSame(Mode.LOG, appState.getCurrentMode());
        assertSame(Theme.LIGHT, appState.getTheme());
        assertEquals(1.0, appState.getPlaybackSpeedFactor());
        assertNull(appState.getLogFolder());
        assertTrue(appState.portTailMappings().isEmpty());
        assertSame(MessageType.MESSAGE_1, appState.getMessageType());
        assertEquals(8081, appState.getSoapPort());
    }

    /**
     * There is deliberately no off-thread read path any more.
     *
     * <p>This class used to publish an immutable {@code Snapshot} record for the loopback HTTP
     * layer, kept current by listeners on every field in it. That layer is gone, so the record went
     * with it rather than being maintained on every write for a reader that no longer exists.
     *
     * <p>Pinned as a test because the deletion is easy to undo by reflex — the obvious way to give
     * a future server access to this state is to hand it the live object, which is exactly what the
     * projection existed to prevent. When SOAP mode brings a server back, the pattern should come
     * back with it: an immutable record, published through a {@code volatile} field, deliberately
     * narrower than this class.
     */
    @Test
    @DisplayName("there is no off-thread read path to reintroduce by accident")
    void thereIsNoOffThreadReadPath() {
        assertEquals(0,
                java.util.Arrays.stream(AppState.class.getDeclaredClasses())
                        .filter(c -> c.getSimpleName().equals("Snapshot"))
                        .count(),
                "Snapshot was removed with the web layer; reinstating it needs a reader and a "
                        + "deliberate decision about what it may carry, not a quiet re-add");
    }

    // --- the thread guard ---------------------------------------------------------------------

    @Test
    void mutatingOffTheFxThreadFailsLoudlyRatherThanRacing() throws Exception {
        assertThrowsOffThread(() -> appState.setPlaybackSpeedFactor(1.5));
        assertThrowsOffThread(() -> appState.setCurrentMode(Mode.MESSAGE));
        assertThrowsOffThread(() -> appState.setLogFolder(new File(".")));
        assertThrowsOffThread(() -> appState.setContentOpacity(0.5));
        assertThrowsOffThread(() -> appState.setMessageType(MessageType.MESSAGE_2));
        assertThrowsOffThread(() -> appState.setSoapPort(9000));
    }

    /**
     * The collection is the easy one to leave unguarded — it is not a property, so it does not go
     * through a setter unless one is written for it.
     */
    @Test
    @DisplayName("the mapping mutators are guarded like every other one")
    void theMappingMutatorsAreGuardedLikeEveryOtherOne() throws Exception {
        assertThrowsOffThread(() -> appState.addPortTailMapping(PortTailMapping.of(5001, "N12345")));
        assertThrowsOffThread(() -> appState.setPortTailMappings(List.of()));
        assertThrowsOffThread(() -> appState.removePortTailMappingForPort(5001));
    }

    @Test
    void theThreadingFailureNamesTheEscapeHatch() throws Exception {
        Throwable thrown = assertThrowsOffThread(() -> appState.setPlaybackSpeedFactor(1.5));
        assertTrue(thrown.getMessage().contains("onFxThread"), thrown.getMessage());
    }

    // --- no second way in ---------------------------------------------------------------------

    @Test
    void propertyAccessorsExposeNoSetter() throws Exception {
        // The guard would be pointless if a caller could reach the mutable property instead —
        // this pins the accessors' return types as read-only.
        for (String accessor : new String[] {
                "currentModeProperty", "playbackSpeedFactorProperty", "logFolderProperty",
                "contentOpacityProperty", "messageTypeProperty", "soapPortProperty",
                "themeProperty"}) {
            Class<?> returned = AppState.class.getMethod(accessor).getReturnType();
            assertTrue(returned.getSimpleName().startsWith("ReadOnly"),
                    accessor + " returns " + returned.getSimpleName());
        }
    }

    /**
     * The collection equivalent of the rule above, and the one the obvious implementation gets
     * wrong: handing out the live {@code ObservableList} would let any caller, on any thread, add
     * an entry past both the guard and the uniqueness rules.
     */
    @Test
    @DisplayName("the mapping table is handed out unmodifiable")
    void theMappingTableIsHandedOutUnmodifiable() {
        // Populated first, deliberately: clear() and remove() on an empty list are no-ops that
        // throw nothing, so an empty fixture would let a fully mutable list pass this test.
        appState.addPortTailMapping(PortTailMapping.of(5001, "N12345"));
        ObservableList<PortTailMapping> mappings = appState.portTailMappings();

        assertThrows(UnsupportedOperationException.class,
                () -> mappings.add(PortTailMapping.of(5002, "123456")));
        assertThrows(UnsupportedOperationException.class, () -> mappings.remove(0));
        assertThrows(UnsupportedOperationException.class, mappings::clear);
        assertEquals(1, mappings.size(), "none of that should have got through");
    }

    // --- the mapping table --------------------------------------------------------------------

    @Test
    @DisplayName("the view tracks the state it is a view of")
    void theViewTracksTheStateItIsAViewOf() {
        ObservableList<PortTailMapping> mappings = appState.portTailMappings();
        assertTrue(mappings.isEmpty());

        appState.addPortTailMapping(PortTailMapping.of(5001, "N12345"));

        // Unmodifiable, but not a detached copy — a table bound to it must see later edits.
        assertEquals(1, mappings.size());
        assertEquals("N12345", mappings.get(0).tail());
    }

    @Test
    @DisplayName("a mapping can be removed by its port")
    void aMappingCanBeRemovedByItsPort() {
        appState.addPortTailMapping(PortTailMapping.of(5001, "N12345"));
        appState.addPortTailMapping(PortTailMapping.of(5002, "123456"));

        assertTrue(appState.removePortTailMappingForPort(5001));
        assertEquals(List.of(PortTailMapping.of(5002, "123456")), appState.portTailMappings());

        assertFalse(appState.removePortTailMappingForPort(9999),
                "removing a port that is not mapped is not an error, but it is not a change either");
    }

    @Test
    @DisplayName("a colliding mapping is rejected and changes nothing")
    void aCollidingMappingIsRejectedAndChangesNothing() {
        appState.addPortTailMapping(PortTailMapping.of(5001, "N12345"));

        assertThrows(IllegalArgumentException.class,
                () -> appState.addPortTailMapping(PortTailMapping.of(5001, "123456")));
        assertThrows(IllegalArgumentException.class,
                () -> appState.addPortTailMapping(PortTailMapping.of(5002, "n12345")));

        assertEquals(List.of(PortTailMapping.of(5001, "N12345")), appState.portTailMappings(),
                "a rejected edit must leave the table exactly as it was");
    }

    /**
     * One event per edit, not a remove followed by an add. A subscriber that persists on every
     * change would otherwise write twice for one edit, and a table would flicker its selection.
     */
    @Test
    @DisplayName("an edit is published as a single change")
    void anEditIsPublishedAsASingleChange() {
        AtomicInteger changes = new AtomicInteger();
        ListChangeListener<PortTailMapping> counter = change -> changes.incrementAndGet();
        appState.portTailMappings().addListener(counter);

        appState.addPortTailMapping(PortTailMapping.of(5001, "N12345"));
        assertEquals(1, changes.get());

        appState.removePortTailMappingForPort(5001);
        assertEquals(2, changes.get());

        appState.portTailMappings().removeListener(counter);
    }

    // --- the values that are not allowed to be absent or absurd -------------------------------

    @Test
    @DisplayName("there is no no-mode state")
    void thereIsNoNoModeState() {
        assertThrows(IllegalArgumentException.class, () -> appState.setCurrentMode(null));
        assertThrows(IllegalArgumentException.class, () -> appState.setMessageType(null));
    }

    @Test
    @DisplayName("the SOAP port is range-checked where a control writes it")
    void theSoapPortIsRangeCheckedWhereAControlWritesIt() {
        assertThrows(IllegalArgumentException.class, () -> appState.setSoapPort(0));
        assertThrows(IllegalArgumentException.class, () -> appState.setSoapPort(65536));

        appState.setSoapPort(65535);
        assertEquals(65535, appState.getSoapPort());
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
