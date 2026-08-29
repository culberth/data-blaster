package com.culberth.tools.datablaster.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Reads and writes {@link Settings} as a properties file in a per-user location.
 *
 * <p><strong>Why a properties file and not {@code java.util.prefs.Preferences}.</strong>
 * {@code Preferences} is zero-dependency and solves the per-user location for free, but on Windows
 * it writes into the registry. For a template whose main deliverable is that a reader can see what
 * it does, a file they can open, edit and delete is worth more than the convenience — "where did my
 * setting go" should be answerable with a file manager.
 *
 * <p><strong>This class knows nothing about {@link AppState} or JavaFX.</strong> That is what lets
 * the tolerance rules below be tested against hand-written broken files, with no toolkit and no
 * Spring context. {@link SettingsService} is the piece that joins the two.
 *
 * <p><strong>The format is keyed, not positional.</strong> A fork adding a setting adds a key;
 * older files stay readable, because every value falls back independently.
 */
@Component
public class SettingsStore {

    private static final System.Logger LOG = System.getLogger(SettingsStore.class.getName());

    private static final String DIRECTORY_NAME = "DataBlaster";
    private static final String FILE_NAME = "settings.properties";

    private static final String KEY_SIM_FACTOR = "simFactor";
    private static final String KEY_LOG_FOLDER = "logFolder";
    private static final String KEY_THEME = "theme";

    private final Path file;

    @Autowired
    public SettingsStore() {
        this(defaultLocation());
    }

    /** Package-private so tests can point the store at a temporary directory. */
    SettingsStore(Path file) {
        this.file = file;
    }

    /** Where the settings file lives, for the read-out in Preferences and for diagnostics. */
    public Path location() {
        return file;
    }

    /**
     * Reads the stored settings, falling back to {@link Settings#DEFAULTS} for anything missing,
     * unparsable or out of range.
     *
     * <p><strong>This method does not throw.</strong> Start-up depends on it, and the whole point
     * of NFR1 — the UI must start even when a companion part cannot — applies just as much to a
     * settings file someone has hand-edited into nonsense as it does to a taken port. A first run
     * is silent because there is nothing wrong with it; anything else is logged.
     *
     * <p>Each value falls back on its own. A broken {@code simFactor} does not discard a perfectly
     * good {@code logFolder} alongside it.
     */
    public Settings read() {
        if (!Files.exists(file)) {
            // First run. Not a problem, and not worth a log line that would appear once for every
            // new user and mean nothing to them.
            return Settings.DEFAULTS;
        }

        Properties properties = new Properties();
        try (Reader in = openForReading()) {
            properties.load(in);
        } catch (IOException | IllegalArgumentException e) {
            // IllegalArgumentException covers a malformed unicode escape in the file, which
            // Properties raises rather than an IOException. (Spelling that escape out here would
            // not compile: javac expands the sequence inside comments too.)
            LOG.log(System.Logger.Level.WARNING,
                    "Could not read settings from " + file + "; starting from defaults", e);
            return Settings.DEFAULTS;
        }

        return new Settings(
                readSimFactor(properties),
                readLogFolder(properties),
                Theme.fromStoredName(properties.getProperty(KEY_THEME), Settings.DEFAULTS.theme()));
    }

    /**
     * Writes {@code settings}, creating the directory if needed.
     *
     * <p>Written to a temporary file and moved into place, so an interrupted write cannot leave a
     * half-written file behind. {@link #read()} tolerates a corrupt file, but not creating one in
     * the first place is better than recovering from it — and a truncated file would silently drop
     * whichever settings had not been flushed yet.
     *
     * <p>Failures are logged, not thrown. This runs on a background thread where nothing could act
     * on an exception, and a settings file that cannot be written is not a reason to interrupt
     * someone's work.
     */
    public void write(Settings settings) {
        Properties properties = new Properties();
        properties.setProperty(KEY_SIM_FACTOR, Double.toString(settings.simFactor()));
        if (settings.logFolderPath() != null) {
            properties.setProperty(KEY_LOG_FOLDER, settings.logFolderPath());
        }
        properties.setProperty(KEY_THEME, settings.theme().storedName());

        try {
            Path directory = file.getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }

            Path temporary = Files.createTempFile(directory, FILE_NAME, ".tmp");
            // store(Writer) rather than store(OutputStream): the stream form emits ISO-8859-1 and
            // escapes everything else, so a folder path with non-ASCII characters comes out as
            // unreadable escapes in a file this class invites people to open. Symmetric with
            // openForReading().
            try (Writer out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(out, "Data Blaster settings. Safe to delete; defaults are restored.");
            }
            moveIntoPlace(temporary);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not write settings to " + file, e);
        }
    }

    /**
     * Opens the file as UTF-8, skipping a byte-order mark if one is there.
     *
     * <p>Both of those are load-bearing, and neither is what {@code Properties.load(InputStream)}
     * would do.
     *
     * <p><strong>The BOM.</strong> Windows Notepad saving as "UTF-8", and PowerShell's
     * {@code Set-Content -Encoding utf8}, both prepend one. {@code Properties} does not treat it as
     * whitespace, so it becomes part of the first key — leaving that one setting silently at its
     * default while every later line loads correctly. That asymmetry is what makes it worth
     * handling rather than documenting: the file looks fine, most of it works, and only the first
     * setting is quietly ignored. Found by hand-editing the real settings file and watching Sim
     * Factor come back as 0.0 while the log folder restored perfectly.
     *
     * <p><strong>UTF-8.</strong> {@code load(InputStream)} decodes ISO-8859-1, which mangles a
     * hand-typed folder path containing non-ASCII characters. Files this class writes are
     * unaffected either way, so switching costs nothing and makes an edited file behave the way its
     * editor said it would.
     */
    private Reader openForReading() throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        int start = hasByteOrderMark(bytes) ? 3 : 0;
        return new InputStreamReader(
                new ByteArrayInputStream(bytes, start, bytes.length - start), StandardCharsets.UTF_8);
    }

    private static boolean hasByteOrderMark(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }

    private void moveIntoPlace(Path temporary) throws IOException {
        try {
            Files.move(temporary, file,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Some filesystems cannot do it atomically. A non-atomic replace still beats writing
            // over the live file in place, which is the case this whole dance exists to avoid.
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private double readSimFactor(Properties properties) {
        String raw = properties.getProperty(KEY_SIM_FACTOR);
        if (raw == null) {
            return Settings.DEFAULTS.simFactor();
        }
        double value;
        try {
            value = Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Ignoring unreadable " + KEY_SIM_FACTOR + " '" + raw + "' in " + file);
            return Settings.DEFAULTS.simFactor();
        }
        if (Double.isNaN(value) || value < Settings.SIM_FACTOR_MIN || value > Settings.SIM_FACTOR_MAX) {
            // Out of range rather than unparsable: a value the slider cannot represent would be
            // silently clamped by the control on the way in, leaving the file and the UI
            // disagreeing about what the setting is.
            LOG.log(System.Logger.Level.WARNING,
                    "Ignoring out-of-range " + KEY_SIM_FACTOR + " " + value + " in " + file);
            return Settings.DEFAULTS.simFactor();
        }
        return value;
    }

    private static String readLogFolder(Properties properties) {
        String raw = properties.getProperty(KEY_LOG_FOLDER);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // Deliberately not checked for existence. Nothing is read from or written to this folder,
        // and discarding someone's choice because a network drive happened to be offline at
        // start-up is a worse outcome than showing a path that is not currently reachable.
        return raw;
    }

    /**
     * The conventional per-user configuration location for the current platform.
     *
     * <p>Resolved from environment variables with home-relative fallbacks, so an unset
     * {@code APPDATA} or {@code XDG_CONFIG_HOME} degrades to the documented default rather than
     * throwing during construction — which would happen before any UI exists to report it.
     */
    static Path defaultLocation() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home", "."));
        Path base;

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            base = isSet(appData) ? Path.of(appData) : home.resolve("AppData").resolve("Roaming");
        } else if (os.contains("mac")) {
            base = home.resolve("Library").resolve("Application Support");
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            base = isSet(xdg) ? Path.of(xdg) : home.resolve(".config");
        }

        return base.resolve(DIRECTORY_NAME).resolve(FILE_NAME);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
