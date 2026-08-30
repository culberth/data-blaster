package com.culberth.tools.datablaster.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One entry in Log mode's port-to-tail-number table: traffic arriving on {@code port} belongs to
 * the aircraft registered as {@code tail}.
 *
 * <p><strong>The tail is a {@code String}, and that is not an oversight.</strong> A tail number may
 * be all digits, so an integer type is superficially tempting — and wrong twice over. It would turn
 * {@code 000042} into {@code 42} on the first round-trip through the settings file, and it cannot
 * hold {@code N12345} at all.
 *
 * <p><strong>What counts as a tail number: exactly six alphanumeric characters, upper-cased.</strong>
 * No fixed prefix — {@code N} has no special status, and a registration and a six-digit number are
 * equally valid. The length is exact rather than a maximum, which makes this a stricter validator
 * than a real-world registration rule would be; stricter is where its typo-catching value is, since
 * {@code N123} is far more likely to be a slip than a deliberate short tail.
 *
 * <p>This rejects hyphenated foreign registrations — {@code G-ABCD} is six characters only if the
 * hyphen counts, and it does not. That follows from the rule as specified rather than being a
 * decision about non-US aircraft; if such tails turn up it is one character in {@link #TAIL_PATTERN}
 * plus a test. It is flagged rather than allowed pre-emptively, because a character class that also
 * accepts {@code ------} has stopped validating anything.
 *
 * <p><strong>No instance of this record can be invalid.</strong> The canonical constructor
 * normalises and validates, so there is no back door around {@link #of}: a mapping either holds a
 * port in range and a normalised six-character tail, or it was never constructed.
 *
 * @param port the TCP port traffic arrives on, {@value #PORT_MIN}–{@value #PORT_MAX}
 * @param tail the aircraft tail number, already trimmed and upper-cased
 */
public record PortTailMapping(int port, String tail) {

    /**
     * The valid TCP port range, shared with the SOAP port setting.
     *
     * <p>Defined here rather than on {@link Settings} because this is the class that validates a
     * port; {@code Settings.SoapSettings} refers back to these so the two cannot drift into
     * disagreeing about what a port is.
     */
    public static final int PORT_MIN = 1;

    /** @see #PORT_MIN */
    public static final int PORT_MAX = 65535;

    /** Exactly six characters — see the class Javadoc for why this is not a maximum. */
    public static final int TAIL_LENGTH = 6;

    /** Applied after trimming and upper-casing, so it needs no case-insensitive flag. */
    public static final Pattern TAIL_PATTERN = Pattern.compile("[A-Z0-9]{" + TAIL_LENGTH + "}");

    public PortTailMapping {
        if (port < PORT_MIN || port > PORT_MAX) {
            throw new IllegalArgumentException(
                    "Port must be between " + PORT_MIN + " and " + PORT_MAX + ", but was " + port);
        }
        if (tail == null) {
            throw new IllegalArgumentException("Tail number is required");
        }
        // Normalise here rather than only in of(), so no construction path can produce a mapping
        // whose tail differs from another's by case alone — which would defeat the uniqueness
        // check in requireUniquePortsAndTails, since "n12345" and "N12345" are different Strings.
        tail = normaliseTail(tail);
        if (!TAIL_PATTERN.matcher(tail).matches()) {
            throw new IllegalArgumentException(
                    "Tail number must be exactly " + TAIL_LENGTH + " letters or digits, but was '"
                            + tail + "'");
        }
    }

    /**
     * A mapping for {@code port} and {@code rawTail}, normalising the tail on the way in.
     *
     * <p>The named entry point for the UI and the settings store. Both hand over text a person
     * typed, and both want the {@link IllegalArgumentException} message verbatim — it is written to
     * be shown, not just logged.
     *
     * @throws IllegalArgumentException if the port is out of range or the tail is malformed
     */
    public static PortTailMapping of(int port, String rawTail) {
        return new PortTailMapping(port, rawTail);
    }

    /** Trim and upper-case, the two steps every comparison in this class assumes have happened. */
    public static String normaliseTail(String rawTail) {
        return rawTail == null ? null : rawTail.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * A defensively copied, unmodifiable, port-ordered view of {@code mappings}, rejecting any
     * collision in either direction.
     *
     * <p><strong>Both directions.</strong> One tail per port and one port per tail: a port claimed
     * twice is an ambiguous route, and a tail claimed twice attributes one aircraft's traffic to two
     * ports. The settings file enforces the first for free — {@code log.mapping.<port>} is a key, and
     * a properties file cannot hold a key twice — but nothing about the format prevents the second,
     * so it is checked here, where every path that builds a mapping set goes through it.
     *
     * <p>Ordered by port so a written file and a rebuilt table are reproducible; the input order of
     * a {@code Set} is not.
     *
     * @throws IllegalArgumentException naming the colliding port or tail
     */
    public static List<PortTailMapping> requireUniquePortsAndTails(
            Collection<PortTailMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }

        Map<Integer, PortTailMapping> byPort = new HashMap<>();
        Set<String> tails = new HashSet<>();
        List<PortTailMapping> accepted = new ArrayList<>(mappings.size());

        for (PortTailMapping mapping : mappings) {
            if (mapping == null) {
                throw new IllegalArgumentException("A mapping is required");
            }
            PortTailMapping clashingPort = byPort.putIfAbsent(mapping.port(), mapping);
            if (clashingPort != null) {
                throw new IllegalArgumentException(
                        "Port " + mapping.port() + " is already mapped to "
                                + clashingPort.tail());
            }
            if (!tails.add(mapping.tail())) {
                throw new IllegalArgumentException(
                        "Tail number " + mapping.tail() + " is already mapped to another port");
            }
            accepted.add(mapping);
        }

        accepted.sort(Comparator.comparingInt(PortTailMapping::port));
        return Collections.unmodifiableList(accepted);
    }
}
