package com.example.jfxribbon.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    // --- the happy path ---------------------------------------------------------------------

    @Test
    @DisplayName("a written setting is the setting that comes back")
    void aWrittenSettingIsTheSettingThatComesBack() {
        SettingsStore store = storeAt("settings.properties");
        store.write(new Settings(2.5, "C:\\logs\\jfxribbon", Theme.LIGHT));

        Settings read = store.read();
        assertEquals(2.5, read.simFactor(), 0.0001);
        assertEquals("C:\\logs\\jfxribbon", read.logFolderPath());
    }

    @Test
    @DisplayName("writing creates the directory it needs")
    void writingCreatesTheDirectoryItNeeds() {
        Path nested = directory.resolve("Vendor").resolve("JFXRibbon").resolve("settings.properties");
        new SettingsStore(nested).write(new Settings(1.0, null, Theme.LIGHT));

        assertTrue(Files.exists(nested), "the store should create its own directory");
    }

    @Test
    @DisplayName("no log folder round-trips as no log folder, not as an empty path")
    void noLogFolderRoundTripsAsNoLogFolder() {
        SettingsStore store = storeAt("settings.properties");
        store.write(new Settings(0.0, null, Theme.LIGHT));

        assertNull(store.read().logFolderPath(),
                "an absent folder must stay absent; \"\" would become a File pointing at the "
                        + "working directory");
    }

    @Test
    @DisplayName("writing twice leaves no temporary files behind")
    void writingTwiceLeavesNoTemporaryFilesBehind() throws IOException {
        SettingsStore store = storeAt("settings.properties");
        store.write(new Settings(1.0, null, Theme.LIGHT));
        store.write(new Settings(2.0, null, Theme.LIGHT));

        try (Stream<Path> entries = Files.list(directory)) {
            assertTrue(entries.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")),
                    "the write-and-move should not leave .tmp files in the settings directory");
        }
        assertEquals(2.0, store.read().simFactor(), 0.0001);
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
        assertEquals(Settings.DEFAULTS.simFactor(), read.simFactor(), 0.0001);
    }

    @ParameterizedTest(name = "simFactor={0}")
    @ValueSource(strings = {"banana", "", "  ", "1.2.3", "NaN", "1e999"})
    @DisplayName("an unreadable simFactor falls back to the default")
    void anUnreadableSimFactorFallsBackToTheDefault(String raw) throws IOException {
        Settings read = storeContaining("simFactor=" + raw).read();

        assertEquals(Settings.DEFAULTS.simFactor(), read.simFactor(), 0.0001,
                "'" + raw + "' should not have been accepted");
    }

    @ParameterizedTest(name = "simFactor={0}")
    @ValueSource(strings = {"5.1", "-5.1", "1000", "-1000"})
    @DisplayName("a simFactor outside the slider's range falls back to the default")
    void aSimFactorOutsideTheSlidersRangeFallsBackToTheDefault(String raw) throws IOException {
        Settings read = storeContaining("simFactor=" + raw).read();

        assertEquals(Settings.DEFAULTS.simFactor(), read.simFactor(), 0.0001,
                raw + " is outside [" + Settings.SIM_FACTOR_MIN + ", " + Settings.SIM_FACTOR_MAX
                        + "]; accepting it would leave the file and the slider disagreeing");
    }

    @ParameterizedTest(name = "simFactor={0}")
    @ValueSource(strings = {"-5.0", "5.0", "0", "-0.1", "2.5"})
    @DisplayName("a simFactor at or inside the range is kept")
    void aSimFactorAtOrInsideTheRangeIsKept(String raw) throws IOException {
        Settings read = storeContaining("simFactor=" + raw).read();

        assertEquals(Double.parseDouble(raw), read.simFactor(), 0.0001,
                raw + " is within range and should have been kept");
    }

    /**
     * The reason each value falls back independently. Losing a good folder because someone
     * mistyped a number is the kind of collateral damage that makes people stop trusting a
     * settings file.
     */
    @Test
    @DisplayName("one broken value does not discard the other")
    void oneBrokenValueDoesNotDiscardTheOther() throws IOException {
        Settings read = storeContaining("simFactor=banana\nlogFolder=C\\:\\\\logs").read();

        assertEquals(Settings.DEFAULTS.simFactor(), read.simFactor(), 0.0001);
        assertEquals("C:\\logs", read.logFolderPath(), "the readable value should have survived");
    }

    @Test
    @DisplayName("an empty log folder reads as none rather than as a blank path")
    void anEmptyLogFolderReadsAsNone() throws IOException {
        assertNull(storeContaining("logFolder=").read().logFolderPath());
        assertNull(storeContaining("logFolder=   ").read().logFolderPath());
    }

    /**
     * A folder that has gone away is kept deliberately — see {@code readLogFolder}. Nothing is
     * read from or written to it, and discarding the choice because a network drive was offline at
     * start-up is worse than showing a path that is not currently reachable.
     */
    @Test
    @DisplayName("a log folder that no longer exists is still restored")
    void aLogFolderThatNoLongerExistsIsStillRestored() throws IOException {
        Path gone = directory.resolve("removed-since");
        assertFalse(Files.exists(gone));

        SettingsStore store = storeAt("settings.properties");
        store.write(new Settings(0.0, gone.toString(), Theme.LIGHT));

        assertEquals(gone.toString(), store.read().logFolderPath());
    }

    // --- encoding, where an editor writes something Properties does not expect ----------------

    /**
     * The defect this guards, found by hand-editing the real settings file and watching Sim Factor
     * come back as 0.0 while the log folder on the next line restored perfectly.
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
        byte[] body = ("simFactor=-2.5" + System.lineSeparator()
                + "logFolder=C\\:\\\\logs" + System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(body, 0, withBom, bom.length, body.length);
        Files.write(file, withBom);

        Settings read = new SettingsStore(file).read();

        assertEquals(-2.5, read.simFactor(), 0.0001,
                "the first key must survive a BOM; without stripping it, this reads as the default "
                        + "while the second key loads fine");
        assertEquals("C:\\logs", read.logFolderPath());
    }

    @Test
    @DisplayName("a folder path with non-ASCII characters round-trips")
    void aFolderPathWithNonAsciiCharactersRoundTrips() {
        String path = "C:\\Benutzer\\Müller\\日誌";
        SettingsStore store = storeAt("settings.properties");
        store.write(new Settings(1.0, path, Theme.LIGHT));

        assertEquals(path, store.read().logFolderPath(),
                "read and write must agree on the charset");
    }

    @Test
    @DisplayName("the file the app writes is plain UTF-8 an editor can show")
    void theFileTheAppWritesIsPlainUtf8AnEditorCanShow() throws IOException {
        Path file = directory.resolve("settings.properties");
        new SettingsStore(file).write(new Settings(1.0, "C:\\Müller", Theme.LIGHT));

        String contents = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(contents.contains("Müller"),
                "the path should be readable, not escaped: " + contents);
    }

    // --- the location, which nothing else asserts --------------------------------------------

    @Test
    @DisplayName("the default location is per-user and named after the app")
    void theDefaultLocationIsPerUserAndNamedAfterTheApp() {
        Path location = SettingsStore.defaultLocation();

        assertEquals("settings.properties", location.getFileName().toString());
        assertEquals("JFXRibbon", location.getParent().getFileName().toString());
        assertTrue(location.isAbsolute(),
                "an absolute path, or the file would follow the working directory: " + location);
    }
}
