package com.culberth.tools.datablaster.model;

import java.util.regex.Pattern;

/**
 * The rule for what counts as an IP address in this application: an IPv4 dotted quad.
 *
 * <p><strong>IPv4 only, deliberately.</strong> A pattern that also accepted hostnames would accept
 * very nearly any string, which is the point at which a validator stops catching typos and starts
 * costing a round-trip to discover them; and IPv6 is validation and test surface for a mode that
 * has no behaviour yet. Both are a widened pattern plus a test if they turn up — which is a smaller
 * change than narrowing a rule people have already stored values against.
 *
 * <p><strong>Each octet is range-checked, not just shaped.</strong> {@code 999.1.1.1} matches four
 * dot-separated digit runs and is not an address, so the digits are parsed rather than only
 * counted. Leading zeros are rejected for the same reason {@code SettingsStore} catches
 * {@code log.mapping.080}: {@code 010} is ten to this parser and eight to some resolvers, and a
 * value that means two things is worse than one that is refused.
 *
 * <p>Nothing binds or connects to this address. Whether it is reachable is a connect-time question,
 * and the mode behaviour that would ask it does not exist yet — so this validates the form and
 * stops there, exactly as the port range does.
 */
public final class IpAddress {

    /** Shape only; {@link #requireValid} range-checks the octets this captures. */
    private static final Pattern DOTTED_QUAD =
            Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    private static final int OCTET_MAX = 255;

    private IpAddress() {
    }

    /** Trim, the one step every comparison against an address assumes has happened. */
    public static String normalise(String rawAddress) {
        return rawAddress == null ? null : rawAddress.trim();
    }

    /**
     * The normalised form of {@code rawAddress}, rejecting anything that is not a dotted quad.
     *
     * <p>The message is written to be shown to a person: the SOAP tab puts it verbatim beside the
     * field that was refused, the same way the mapping editor does.
     *
     * @throws IllegalArgumentException if {@code rawAddress} is null, blank or malformed
     */
    public static String requireValid(String rawAddress) {
        String address = normalise(rawAddress);
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("An IP address is required");
        }
        java.util.regex.Matcher shape = DOTTED_QUAD.matcher(address);
        if (!shape.matches()) {
            throw new IllegalArgumentException(
                    "IP address must be four numbers separated by dots, but was '" + address + "'");
        }
        for (int octet = 1; octet <= 4; octet++) {
            String digits = shape.group(octet);
            if (digits.length() > 1 && digits.charAt(0) == '0') {
                // 010 is ten here and eight to some resolvers. Refusing it is cheaper than
                // shipping a value whose meaning depends on who reads it.
                throw new IllegalArgumentException(
                        "IP address must not have leading zeros, but was '" + address + "'");
            }
            if (Integer.parseInt(digits) > OCTET_MAX) {
                throw new IllegalArgumentException(
                        "Each part of an IP address must be 0 to " + OCTET_MAX + ", but was '"
                                + address + "'");
            }
        }
        return address;
    }

    /** Whether {@code rawAddress} would be accepted, for controls that ask before committing. */
    public static boolean isValid(String rawAddress) {
        try {
            requireValid(rawAddress);
            return true;
        } catch (IllegalArgumentException rejected) {
            return false;
        }
    }
}
