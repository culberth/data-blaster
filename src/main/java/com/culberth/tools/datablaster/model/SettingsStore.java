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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Reads and writes {@link Settings} as a properties file in a per-user location.
 *
 * <p><strong>Why a properties file and not {@code java.util.prefs.Preferences}.</strong>
 * {@code Preferences} is zero-dependency and solves the per-user location for free, but on Windows
 * it writes into the registry. A file someone can open, edit and delete is worth more than the
 * convenience — "where did my setting go" should be answerable with a file manager.
 *
 * <p><strong>Keys are namespaced, not nested.</strong> {@code Properties} is flat, so a mode's
 * settings are grouped by a key prefix: {@code log.*}, {@code message.*}, {@code soap.*}, with
 * {@code theme} and {@code mode} unprefixed because they are not mode-scoped. {@code log.mapping.5001}
 * is one key with dots in it, so reading the mapping set means filtering
 * {@link Properties#stringPropertyNames()} by prefix rather than calling a fixed getter.
 *
 * <p><strong>The mapping port is the key, deliberately.</strong> A properties file cannot hold the
 * same key twice, so port uniqueness is a property of the format rather than a check someone has to
 * remember on the read path. Tail uniqueness is the direction the format cannot enforce, so
 * {@link #readMappings} checks it.
 *
 * <p><strong>This class knows nothing about {@link AppState} or JavaFX.</strong> That is what lets
 * the tolerance rules below be tested against hand-written broken files, with no toolkit and no
 * Spring context. {@link SettingsService} is the piece that joins the two.
 *
 * <p><strong>The format is keyed, not positional.</strong> A fork adding a setting adds a key;
 * older files stay readable, because every value falls back independently. An old key that no
 * longer means anything is simply unknown and ignored — which is why the template's
 * {@code simFactor} was not reused for the playback speed. Its old default of {@code 0.0} is a
 * well-formed number in the new range check's eyes only because it is not: reusing the key would
 * have restored a frozen playback speed from an existing file without tripping any validation,
 * whereas an unknown key falls back to {@value Settings#PLAYBACK_SPEED_DEFAULT} the way a first run
 * does.
 */
@Component
public class SettingsStore {

    private static final System.Logger LOG = System.getLogger(SettingsStore.class.getName());

    private static final String DIRECTORY_NAME = "DataBlaster";
    private static final String FILE_NAME = "settings.properties";

    private static final String KEY_MODE = "mode";
    private static final String KEY_THEME = "theme";

    private static final String KEY_PLAYBACK_SPEED_FACTOR = "log.playbackSpeedFactor";
    private static final String KEY_LOG_FOLDER = "log.folder";

    /** Everything after this prefix is the port; the value is the tail number. */
    private static final String KEY_MAPPING_PREFIX = "log.mapping.";

    private static final String KEY_MESSAGE_TYPE = "message.type";

    private static final String KEY_SOAP_PORT = "soap.port";

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
     * <p><strong>This method does not throw.</strong> Start-up depends on it, and the rule that the
     * UI must start even when a companion part cannot applies just as much to a settings file
     * someone has hand-edited into nonsense as it does to a taken port. A first run is silent
     * because there is nothing wrong with it; anything else is logged.
     *
     * <p>Each value falls back on its own, and that goes for the mapping set entry by entry: a
     * malformed port does not discard a perfectly good playback speed, and one bad mapping does not
     * discard the other nineteen.
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
                Mode.fromStoredName(properties.getProperty(KEY_MODE), Settings.DEFAULTS.mode()),
                Theme.fromStoredName(properties.getProperty(KEY_THEME), Settings.DEFAULTS.theme()),
                new Settings.LogSettings(
                        readPlaybackSpeedFactor(properties),
                        readLogFolder(properties),
                        readMappings(properties)),
                new Settings.MessageSettings(MessageType.fromStoredName(
                        properties.getProperty(KEY_MESSAGE_TYPE),
                        Settings.DEFAULTS.message().type())),
                new Settings.SoapSettings(readSoapPort(properties)));
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
        properties.setProperty(KEY_MODE, settings.mode().storedName());
        properties.setProperty(KEY_THEME, settings.theme().storedName());

        Settings.LogSettings log = settings.log();
        properties.setProperty(KEY_PLAYBACK_SPEED_FACTOR, Double.toString(log.playbackSpeedFactor()));
        if (log.folderPath() != null) {
            properties.setProperty(KEY_LOG_FOLDER, log.folderPath());
        }
        for (PortTailMapping mapping : log.mappings()) {
            properties.setProperty(KEY_MAPPING_PREFIX + mapping.port(), mapping.tail());
        }

        properties.setProperty(KEY_MESSAGE_TYPE, settings.message().type().storedName());
        properties.setProperty(KEY_SOAP_PORT, Integer.toString(settings.soap().port()));

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
     * setting is quietly ignored. Found by hand-editing the real settings file and watching the
     * first value come back as its default while the log folder restored perfectly.
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

    private double readPlaybackSpeedFactor(Properties properties) {
        double fallback = Settings.DEFAULTS.log().playbackSpeedFactor();
        String raw = properties.getProperty(KEY_PLAYBACK_SPEED_FACTOR);
        if (raw == null) {
            return fallback;
        }
        double value;
        try {
            value = Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Ignoring unreadable " + KEY_PLAYBACK_SPEED_FACTOR + " '" + raw + "' in " + file);
            return fallback;
        }
        if (Double.isNaN(value)
                || value < Settings.PLAYBACK_SPEED_MIN
                || value > Settings.PLAYBACK_SPEED_MAX) {
            // Rejected rather than clamped. Clamping 0.0 up to 0.1 would start playback crawling
            // and report nothing, and both ends of an out-of-range value name a behaviour this
            // setting does not offer — 0.0 is frozen, a negative is reverse. Falling back to real
            // time with a log line at least says what happened.
            LOG.log(System.Logger.Level.WARNING,
                    "Ignoring out-of-range " + KEY_PLAYBACK_SPEED_FACTOR + " " + value + " in " + file);
            return fallback;
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

    private int readSoapPort(Properties properties) {
        int fallback = Settings.DEFAULTS.soap().port();
        String raw = properties.getProperty(KEY_SOAP_PORT);
        if (raw == null) {
            return fallback;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Ignoring unreadable " + KEY_SOAP_PORT + " '" + raw + "' in " + file);
            return fallback;
        }
        if (value < PortTailMapping.PORT_MIN || value > PortTailMapping.PORT_MAX) {
            LOG.log(System.Logger.Level.WARNING,
                    "Ignoring out-of-range " + KEY_SOAP_PORT + " " + value + " in " + file);
            return fallback;
        }
        return value;
    }

    /**
     * Every readable {@code log.mapping.<port>=<tail>} entry, in port order.
     *
     * <p><strong>Entry by entry.</strong> A malformed port, a malformed tail, or a tail already
     * claimed by a lower-numbered port is logged and dropped on its own, and the rest of the table
     * loads. Discarding twenty good mappings because someone fat-fingered one is the kind of
     * collateral damage that makes people stop trusting a settings file — and this is the one
     * setting that is plausibly long enough to have been typed by hand.
     *
     * <p>Read in two passes so the outcome does not depend on {@code Properties}' iteration order,
     * which is unspecified. The first pass resolves keys to ports and is where {@code log.mapping.80}
     * and {@code log.mapping.080} are caught colliding — the file format prevents a duplicate key,
     * but not two spellings of the same number. The second walks the ports in ascending order, so
     * which of two mappings sharing a tail survives is decided by the file's content rather than by
     * the hash order of the day.
     */
    private List<PortTailMapping> readMappings(Properties properties) {
        SortedMap<Integer, String> tailsByPort = new TreeMap<>();

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(KEY_MAPPING_PREFIX)) {
                continue;
            }
            String portText = key.substring(KEY_MAPPING_PREFIX.length()).trim();
            int port;
            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Ignoring mapping '" + key + "' in " + file + ": '" + portText
                                + "' is not a port number");
                continue;
            }
            String previous = tailsByPort.putIfAbsent(port, properties.getProperty(key));
            if (previous != null) {
                LOG.log(System.Logger.Level.WARNING,
                        "Ignoring mapping '" + key + "' in " + file + ": port " + port
                                + " is already spelled another way in this file");
            }
        }

        List<PortTailMapping> accepted = new ArrayList<>(tailsByPort.size());
        Set<String> seenTails = new HashSet<>();

        for (Map.Entry<Integer, String> entry : tailsByPort.entrySet()) {
            PortTailMapping mapping;
            try {
                mapping = PortTailMapping.of(entry.getKey(), entry.getValue());
            } catch (IllegalArgumentException malformed) {
                LOG.log(System.Logger.Level.WARNING,
                        "Ignoring mapping for port " + entry.getKey() + " in " + file + ": "
                                + malformed.getMessage());
                continue;
            }
            if (!seenTails.add(mapping.tail())) {
                // The direction the file format cannot enforce: distinct keys, same value.
                LOG.log(System.Logger.Level.WARNING,
                        "Ignoring mapping for port " + entry.getKey() + " in " + file
                                + ": tail number " + mapping.tail()
                                + " is already mapped to a lower port");
                continue;
            }
            accepted.add(mapping);
        }
        return accepted;
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
