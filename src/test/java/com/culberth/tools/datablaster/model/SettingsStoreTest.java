package com.culberth.tools.datablaster.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The store's tolerance rules, exercised against hand-written files.
 *
 * <p>Every "falls back to defaults" branch is only reachable from a broken file, so these fixtures
 * are the only way to reach them — a round-trip test alone would leave the entire failure path
 * unexecuted while looking like coverage.
 *
 * <p>No JavaFX toolkit and no Spring context: {@link SettingsStore} deliberately depends on
 * neither, which is what makes this possible.
 */
class SettingsStoreTest {

    @TempDir
    Path directory;

    private SettingsStore storeAt(String fileName) {
        return new SettingsStore(directory.resolve(fileName));
    }

    private SettingsStore storeContaining(String contents) throws IOException {
        Path file = directory.resolve("settings.properties");
        Files.writeString(file, contents);
        return new SettingsStore(file);
    }

    private static Settings settingsWith(double playbackSpeed, String folderPath) {
        return new Settings(
                Mode.LOG,
                Theme.LIGHT,
                new Settings.LogSettings(playbackSpeed, folderPath, List.of()),
                new Settings.MessageSettings(MessageType.MESSAGE_1),
                new Settings.SoapSettings(Settings.SOAP_PORT_DEFAULT));
    }

    // --- the happy path ---------------------------------------------------------------------

    @Test
    @DisplayName("a written setting is the setting that comes back")
    void aWrittenSettingIsTheSettingThatComesBack() {
        SettingsStore store = storeAt("settings.properties");
        store.write(settingsWith(2.5, "C:\\logs\\datablaster"));

        Settings read = store.read();
        assertEquals(2.5, read.log().playbackSpeedFactor(), 0.0001);
        assertEquals("C:\\logs\\datablaster", read.log().folderPath());
    }

    /** Every mode's settings, together, since they are written through one file and one call. */
    @Test
    @DisplayName("every mode's settings survive a round trip")
    void everyModesSettingsSurviveARoundTrip() {
        SettingsStore store = storeAt("settings.properties");
        Settings written = new Settings(
                Mode.SOAP,
                Theme.DARK,
                new Settings.LogSettings(0.5, "C:\\logs", List.of(
                        PortTailMapping.of(5001, "N12345"),
                        PortTailMapping.of(5002, "000042"))),
                new Settings.MessageSettings(MessageType.MESSAGE_3),
                new Settings.SoapSettings(9443));

        store.write(written);

        assertEquals(written, store.read(),
                "the whole record should round-trip, not just the scalars");
    }

    @Test
    @DisplayName("writing creates the directory it needs")
    void writingCreatesTheDirectoryItNeeds() {
        Path nested = directory.resolve("Vendor").resolve("DataBlaster").resolve("settings.properties");
        new SettingsStore(nested).write(settingsWith(1.0, null));

        assertTrue(Files.exists(nested), "the store should create its own directory");
    }

    @Test
    @DisplayName("no log folder round-trips as no log folder, not as an empty path")
    void noLogFolderRoundTripsAsNoLogFolder() {
        SettingsStore store = storeAt("settings.properties");
        store.write(settingsWith(1.0, null));

        assertNull(store.read().log().folderPath(),
                "an absent folder must stay absent; \"\" would become a File pointing at the "
                        + "working directory");
    }

    @Test
    @DisplayName("writing twice leaves no temporary files behind")
    void writingTwiceLeavesNoTemporaryFilesBehind() throws IOException {
        SettingsStore store = storeAt("settings.properties");
        store.write(settingsWith(1.0, null));
        store.write(settingsWith(2.0, null));

        try (Stream<Path> entries = Files.list(directory)) {
            assertTrue(entries.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")),
                    "the write-and-move should not leave .tmp files in the settings directory");
        }
        assertEquals(2.0, store.read().log().playbackSpeedFactor(), 0.0001);
    }

    // --- the failure paths, which only a broken file reaches ---------------------------------

    @Test
    @DisplayName("a missing file is a first run, not a failure")
    void aMissingFileIsAFirstRunNotAFailure() {
        Settings read = storeAt("does-not-exist.properties").read();

        assertEquals(Settings.DEFAULTS, read);
    }

    @Test
    @DisplayName("a file of nonsense falls back to defaults rather than throwing")
    void aFileOfNonsenseFallsBackToDefaults() throws IOException {
        Settings read = storeContaining("\u0000\u0001 not a properties file at all \uFFFF").read();

        assertNotNull(read, "read() must never return null");
        assertEquals(Settings.DEFAULTS, read);
    }

    // --- the playback speed factor -----------------------------------------------------------

    @ParameterizedTest(name = "log.playbackSpeedFactor={0}")
    @ValueSource(strings = {"banana", "", "  ", "1.2.3", "NaN", "1e999"})
    @DisplayName("an unreadable playback speed falls back to real time")
    void anUnreadablePlaybackSpeedFallsBack(String raw) throws IOException {
        Settings read = storeContaining("log.playbackSpeedFactor=" + raw).read();

        assertEquals(Settings.PLAYBACK_SPEED_DEFAULT, read.log().playbackSpeedFactor(), 0.0001,
                "'" + raw + "' should not have been accepted");
    }

    /**
     * Rejected rather than clamped, which is the whole of D12. Both ends name a behaviour the
     * setting does not offer — {@code 0.0} is frozen and a negative is reverse — and clamping
     * {@code 0.0} up to {@code 0.1} would start playback crawling while reporting nothing.
     */
    @ParameterizedTest(name = "log.playbackSpeedFactor={0}")
    @ValueSource(strings = {"0", "0.0", "0.09", "-1", "-5.0", "10.1", "1000"})
    @DisplayName("a playback speed outside the range falls back rather than being clamped")
    void aPlaybackSpeedOutsideTheRangeFallsBack(String raw) throws IOException {
        Settings read = storeContaining("log.playbackSpeedFactor=" + raw).read();

        assertEquals(Settings.PLAYBACK_SPEED_DEFAULT, read.log().playbackSpeedFactor(), 0.0001,
                raw + " is outside [" + Settings.PLAYBACK_SPEED_MIN + ", "
                        + Settings.PLAYBACK_SPEED_MAX + "] and must not be clamped into it");
    }

    /**
     * The store validates the range; the control chooses the step. A value between the spinner's
     * increments is still a legal setting, or a convenient step size would have quietly become a
     * validation rule.
     */
    @ParameterizedTest(name = "log.playbackSpeedFactor={0}")
    @ValueSource(strings = {"0.1", "0.25", "1.0", "2.5", "10.0"})
    @DisplayName("a playback speed at or inside the range is kept, whatever the step size")
    void aPlaybackSpeedAtOrInsideTheRangeIsKept(String raw) throws IOException {
        Settings read = storeContaining("log.playbackSpeedFactor=" + raw).read();

        assertEquals(Double.parseDouble(raw), read.log().playbackSpeedFactor(), 0.0001,
                raw + " is within range and should have been kept");
    }

    /**
     * Why the key was renamed rather than reused. Under the template's semantics {@code 0.0} was
     * the valid default; under a multiplier it means frozen playback. Had the key stayed
     * {@code simFactor}, an existing file would have restored a frozen speed as a well-formed,
     * in-nothing-out-of-range value — no warning, no fallback, no way to notice.
     */
    @Test
    @DisplayName("the template's keys are unknown keys, not silently reused ones")
    void theTemplatesKeysAreUnknownKeys() throws IOException {
        Settings read = storeContaining(
                "simFactor=0.0\nlogFolder=C\\:\\\\logs\\\\old\n").read();

        assertEquals(Settings.PLAYBACK_SPEED_DEFAULT, read.log().playbackSpeedFactor(), 0.0001,
                "an old simFactor must not restore as a frozen playback speed");
        assertNull(read.log().folderPath(),
                "logFolder moved under log.; the old spelling is simply an unknown key");
    }

    /**
     * The reason each value falls back independently. Losing a good folder because someone
     * mistyped a number is the kind of collateral damage that makes people stop trusting a
     * settings file.
     */
    @Test
    @DisplayName("one broken value does not discard the other")
    void oneBrokenValueDoesNotDiscardTheOther() throws IOException {
        Settings read = storeContaining(
                "log.playbackSpeedFactor=banana\nlog.folder=C\\:\\\\logs").read();

        assertEquals(Settings.PLAYBACK_SPEED_DEFAULT, read.log().playbackSpeedFactor(), 0.0001);
        assertEquals("C:\\logs", read.log().folderPath(), "the readable value should have survived");
    }

    // --- the log folder ----------------------------------------------------------------------

    @Test
    @DisplayName("an empty log folder reads as none rather than as a blank path")
    void anEmptyLogFolderReadsAsNone() throws IOException {
        assertNull(storeContaining("log.folder=").read().log().folderPath());
        assertNull(storeContaining("log.folder=   ").read().log().folderPath());
    }

    /**
     * A folder that has gone away is kept deliberately — see {@code readLogFolder}. Nothing is
     * read from or written to it, and discarding the choice because a network drive was offline at
     * start-up is worse than showing a path that is not currently reachable.
     */
    @Test
    @DisplayName("a log folder that no longer exists is still restored")
    void aLogFolderThatNoLongerExistsIsStillRestored() {
        Path gone = directory.resolve("removed-since");
        assertFalse(Files.exists(gone));

        SettingsStore store = storeAt("settings.properties");
        store.write(settingsWith(1.0, gone.toString()));

        assertEquals(gone.toString(), store.read().log().folderPath());
    }

    // --- the mode, theme, message type and SOAP port ------------------------------------------

    @Test
    @DisplayName("the selected mode is restored, and an unknown one falls back")
    void theSelectedModeIsRestored() throws IOException {
        assertSame(Mode.MESSAGE, storeContaining("mode=message").read().mode());
        assertSame(Settings.DEFAULTS.mode(), storeContaining("mode=banana").read().mode());
        assertSame(Settings.DEFAULTS.mode(), storeContaining("theme=dark").read().mode());
    }

    @Test
    @DisplayName("the theme keeps its unprefixed key so an existing file still reads")
    void theThemeKeepsItsUnprefixedKey() throws IOException {
        assertSame(Theme.DARK, storeContaining("theme=dark").read().theme());
    }

    @Test
    @DisplayName("the message type is restored, and an unknown one falls back")
    void theMessageTypeIsRestored() throws IOException {
        assertSame(MessageType.MESSAGE_2,
                storeContaining("message.type=MESSAGE_2").read().message().type());
        assertSame(Settings.DEFAULTS.message().type(),
                storeContaining("message.type=MESSAGE_9").read().message().type());
    }

    @ParameterizedTest(name = "soap.port={0}")
    @ValueSource(strings = {"0", "-1", "65536", "banana", "", "8080.5"})
    @DisplayName("an unreadable or out-of-range SOAP port falls back to the default")
    void anUnreadableOrOutOfRangeSoapPortFallsBack(String raw) throws IOException {
        assertEquals(Settings.SOAP_PORT_DEFAULT, storeContaining("soap.port=" + raw).read().soap().port(),
                "'" + raw + "' should not have been accepted");
    }

    @Test
    @DisplayName("a SOAP port in range is kept")
    void aSoapPortInRangeIsKept() throws IOException {
        assertEquals(9443, storeContaining("soap.port=9443").read().soap().port());
        assertEquals(1, storeContaining("soap.port=1").read().soap().port());
        assertEquals(65535, storeContaining("soap.port=65535").read().soap().port());
    }

    /**
     * The default is 8081 rather than 8080 because the embedded HTTP layer binds 8080, and an
     * out-of-box state that is already a port conflict is not a default.
     */
    @Test
    @DisplayName("the SOAP port does not default to the port the HTTP layer binds")
    void theSoapPortDoesNotDefaultToTheHttpLayersPort() {
        assertEquals(8081, Settings.DEFAULTS.soap().port());
    }

    // --- the mapping table, which is read entry by entry --------------------------------------

    @Test
    @DisplayName("mappings are read from one key per port, in port order")
    void mappingsAreReadFromOneKeyPerPort() throws IOException {
        Settings read = storeContaining(
                "log.mapping.5003=ABCDEF\nlog.mapping.5001=N12345\nlog.mapping.5002=000042\n").read();

        assertEquals(List.of(
                        PortTailMapping.of(5001, "N12345"),
                        PortTailMapping.of(5002, "000042"),
                        PortTailMapping.of(5003, "ABCDEF")),
                read.log().mappings());
    }

    @Test
    @DisplayName("a tail is normalised on the way in from the file too")
    void aTailIsNormalisedOnTheWayInFromTheFile() throws IOException {
        Settings read = storeContaining("log.mapping.5001= n12345 \n").read();

        assertEquals(List.of(PortTailMapping.of(5001, "N12345")), read.log().mappings());
    }

    /**
     * The heart of R10, and the reason this is not a single parse of the whole set. One entry
     * someone fat-fingered must not take the other nineteen down with it.
     */
    @Test
    @DisplayName("a malformed entry is dropped on its own and the rest load")
    void aMalformedEntryIsDroppedOnItsOwn() throws IOException {
        Settings read = storeContaining(
                "log.mapping.5001=N12345\n"
                        + "log.mapping.notaport=123456\n"
                        + "log.mapping.5002=N123\n"
                        + "log.mapping.70000=ABCDEF\n"
                        + "log.mapping.5003=000042\n").read();

        assertEquals(List.of(
                        PortTailMapping.of(5001, "N12345"),
                        PortTailMapping.of(5003, "000042")),
                read.log().mappings(),
                "a bad port, a short tail and an out-of-range port should each be dropped alone");
    }

    /**
     * The direction the file format cannot enforce. Two distinct keys can hold the same value, so
     * tail uniqueness is checked in code — and which one survives is decided by port order rather
     * than by whatever order Properties happened to hash them into.
     */
    @Test
    @DisplayName("a duplicated tail is dropped, keeping the lower port")
    void aDuplicatedTailIsDroppedKeepingTheLowerPort() throws IOException {
        Settings read = storeContaining(
                "log.mapping.5002=N12345\nlog.mapping.5001=N12345\n").read();

        assertEquals(List.of(PortTailMapping.of(5001, "N12345")), read.log().mappings());
    }

    @Test
    @DisplayName("a tail duplicated only by case is still a duplicate")
    void aTailDuplicatedOnlyByCaseIsStillADuplicate() throws IOException {
        Settings read = storeContaining(
                "log.mapping.5001=N12345\nlog.mapping.5002=n12345\n").read();

        assertEquals(List.of(PortTailMapping.of(5001, "N12345")), read.log().mappings());
    }

    /**
     * The one hole the "port is the key" design leaves: a properties file cannot repeat a key, but
     * it can spell the same number two ways.
     */
    @Test
    @DisplayName("the same port spelled two ways is caught, since the format cannot catch it")
    void theSamePortSpelledTwoWaysIsCaught() throws IOException {
        Settings read = storeContaining(
                "log.mapping.80=N12345\nlog.mapping.080=123456\n").read();

        assertEquals(1, read.log().mappings().size(),
                "080 and 80 are the same port and must not both be loaded");
        assertEquals(80, read.log().mappings().get(0).port());
    }

    @Test
    @DisplayName("no mapping keys is an empty table, not a failure")
    void noMappingKeysIsAnEmptyTable() throws IOException {
        assertTrue(storeContaining("theme=dark\n").read().log().mappings().isEmpty());
    }

    @Test
    @DisplayName("a mapping key with nothing after the prefix is dropped")
    void aMappingKeyWithNothingAfterThePrefixIsDropped() throws IOException {
        assertTrue(storeContaining("log.mapping.=N12345\n").read().log().mappings().isEmpty());
    }

    // --- encoding, where an editor writes something Properties does not expect ----------------

    /**
     * The defect this guards, found by hand-editing the real settings file and watching the first
     * value come back as its default while the log folder on the next line restored perfectly.
     *
     * <p>Windows Notepad's "UTF-8" and PowerShell's {@code Set-Content -Encoding utf8} both write a
     * byte-order mark. {@code Properties} does not treat it as whitespace, so it joins the first
     * key. Only the first setting is lost, which is what makes it so easy to miss.
     */
    @Test
    @DisplayName("a byte-order mark does not swallow the first setting")
    void aByteOrderMarkDoesNotSwallowTheFirstSetting() throws IOException {
        Path file = directory.resolve("settings.properties");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = ("log.playbackSpeedFactor=2.5" + System.lineSeparator()
                + "log.folder=C\\:\\\\logs" + System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(body, 0, withBom, bom.length, body.length);
        Files.write(file, withBom);

        Settings read = new SettingsStore(file).read();

        assertEquals(2.5, read.log().playbackSpeedFactor(), 0.0001,
                "the first key must survive a BOM; without stripping it, this reads as the default "
                        + "while the second key loads fine");
        assertEquals("C:\\logs", read.log().folderPath());
    }

    @Test
    @DisplayName("a folder path with non-ASCII characters round-trips")
    void aFolderPathWithNonAsciiCharactersRoundTrips() {
        String path = "C:\\Benutzer\\Müller\\日誌";
        SettingsStore store = storeAt("settings.properties");
        store.write(settingsWith(1.0, path));

        assertEquals(path, store.read().log().folderPath(),
                "read and write must agree on the charset");
    }

    @Test
    @DisplayName("the file the app writes is plain UTF-8 an editor can show")
    void theFileTheAppWritesIsPlainUtf8AnEditorCanShow() throws IOException {
        Path file = directory.resolve("settings.properties");
        new SettingsStore(file).write(settingsWith(1.0, "C:\\Müller"));

        String contents = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(contents.contains("Müller"),
                "the path should be readable, not escaped: " + contents);
    }

    /** The file is meant to be opened and edited, so its keys should read as what they configure. */
    @Test
    @DisplayName("the file the app writes uses the namespaced keys")
    void theFileTheAppWritesUsesTheNamespacedKeys() throws IOException {
        Path file = directory.resolve("settings.properties");
        new SettingsStore(file).write(new Settings(
                Mode.LOG,
                Theme.LIGHT,
                new Settings.LogSettings(1.0, null, List.of(PortTailMapping.of(5001, "N12345"))),
                new Settings.MessageSettings(MessageType.MESSAGE_1),
                new Settings.SoapSettings(8081)));

        String contents = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(contents.contains("log.playbackSpeedFactor="), contents);
        assertTrue(contents.contains("log.mapping.5001=N12345"), contents);
        assertTrue(contents.contains("message.type="), contents);
        assertTrue(contents.contains("soap.port="), contents);
        assertTrue(contents.contains("mode="), contents);
        assertTrue(contents.contains("theme="), contents);
    }

    // --- the location, which nothing else asserts --------------------------------------------

    @Test
    @DisplayName("the default location is per-user and named after the app")
    void theDefaultLocationIsPerUserAndNamedAfterTheApp() {
        Path location = SettingsStore.defaultLocation();

        assertEquals("settings.properties", location.getFileName().toString());
        assertEquals("DataBlaster", location.getParent().getFileName().toString());
        assertTrue(location.isAbsolute(),
                "an absolute path, or the file would follow the working directory: " + location);
    }
}
