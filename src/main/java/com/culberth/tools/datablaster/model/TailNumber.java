package com.culberth.tools.datablaster.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The rule for what counts as an aircraft tail number, in one place.
 *
 * <p>
 * <strong>Extracted from {@link PortTailMapping}, which used to own it alone.</strong> SOAP mode now carries a
 * standalone tail number under the same constraints, and "the same constraints" is a claim that only stays true if
 * there is one implementation of them. Two copies of a six-character pattern is exactly the drift this project's
 * settings constants were consolidated to prevent — and here the two copies would be a validator and a table that
 * disagreed about whether {@code N123} is a tail.
 *
 * <p>
 * <strong>A tail is a {@code String}, and that is not an oversight.</strong> A tail number may be all digits, so an
 * integer type is superficially tempting — and wrong twice over. It would turn {@code 000042} into {@code 42} on the
 * first round-trip through the settings file, and it cannot hold {@code N12345} at all.
 *
 * <p>
 * <strong>The rule: exactly six alphanumeric characters, upper-cased.</strong> No fixed prefix — {@code N} has no
 * special status, and a registration and a six-digit number are equally valid. The length is exact rather than a
 * maximum, which makes this stricter than a real-world registration rule; stricter is where its typo-catching value is,
 * since {@code N123} is far more likely to be a slip than a deliberate short tail.
 *
 * <p>
 * This rejects hyphenated foreign registrations — {@code G-ABCD} is six characters only if the hyphen counts, and it
 * does not. That follows from the rule as specified rather than being a decision about non-US aircraft; if such tails
 * turn up it is one character in {@link #PATTERN} plus a test. It is flagged rather than allowed pre-emptively, because
 * a character class that also accepts {@code ------} has stopped validating anything.
 */
public final class TailNumber
{

    /** Exactly six characters — see the class Javadoc for why this is not a maximum. */
    public static final int LENGTH = 6;

    /** Applied after trimming and upper-casing, so it needs no case-insensitive flag. */
    public static final Pattern PATTERN = Pattern.compile("[A-Z0-9]{" + LENGTH + "}");

    private TailNumber()
    {
    }

    /** Trim and upper-case, the two steps every comparison against a tail assumes have happened. */
    public static String normalise(String rawTail)
    {
        return rawTail == null ? null : rawTail.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * The normalised form of {@code rawTail}, rejecting anything that is not a tail number.
     *
     * <p>
     * The message is written to be shown to a person, not just logged: both the mapping editor and the SOAP tab put it
     * verbatim beside the control that was refused.
     *
     * @throws IllegalArgumentException if {@code rawTail} is null or malformed
     */
    public static String requireValid(String rawTail)
    {
        if (rawTail == null)
        {
            throw new IllegalArgumentException("Tail number is required");
        }
        String tail = normalise(rawTail);
        if (!PATTERN.matcher(tail).matches())
        {
            throw new IllegalArgumentException(
                    "Tail number must be exactly " + LENGTH + " letters or digits, but was '" + tail + "'");
        }
        return tail;
    }

    /**
     * The normalised form of {@code rawTail}, or {@code null} if it is absent.
     *
     * <p>
     * Absent and malformed are different answers, which is why this exists alongside {@link #requireValid}. SOAP mode's
     * tail is optional — nothing is configured on a first run — whereas a mapping without a tail is not a mapping at
     * all.
     *
     * @throws IllegalArgumentException if {@code rawTail} is present but malformed
     */
    public static String requireValidOrAbsent(String rawTail)
    {
        if (rawTail == null || rawTail.isBlank())
        {
            return null;
        }
        return requireValid(rawTail);
    }
}
