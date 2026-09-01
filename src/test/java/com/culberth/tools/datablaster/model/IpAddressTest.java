package com.culberth.tools.datablaster.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The address rule, at the model layer and without a toolkit.
 *
 * <p>{@code SettingsStoreTest} covers what a stored address does when it is unreadable, and
 * {@code AppStateTest} covers what a control's write does. This is the rule itself: which strings
 * are addresses, which are not, and — the half a shape-only pattern gets wrong — which of the
 * strings that <em>look</em> like addresses are still not addresses.
 */
class IpAddressTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"0.0.0.0", "127.0.0.1", "10.20.30.40", "255.255.255.255",
            "192.168.1.1", "8.8.8.8"})
    @DisplayName("a dotted quad in range is an address")
    void aDottedQuadInRangeIsAnAddress(String address) {
        assertEquals(address, IpAddress.requireValid(address));
        assertTrue(IpAddress.isValid(address));
    }

    @Test
    @DisplayName("an address is trimmed on the way in")
    void anAddressIsTrimmedOnTheWayIn() {
        assertEquals("10.0.0.1", IpAddress.requireValid("  10.0.0.1  "));
    }

    /**
     * The reason the octets are parsed rather than only counted. Every string here matches "four
     * dot-separated runs of up to three digits", which is as far as a shape-only pattern gets.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"256.0.0.1", "1.256.0.1", "1.0.256.1", "1.0.0.256", "999.999.999.999"})
    @DisplayName("an octet above 255 is rejected, however well-shaped the string is")
    void anOctetAbove255IsRejected(String address) {
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> IpAddress.requireValid(address));
        assertTrue(rejected.getMessage().contains("0 to 255"), rejected.getMessage());
    }

    /**
     * {@code 010} is ten to this parser and eight to some resolvers. A value that means two things
     * is worse than one that is refused — the same reasoning that makes {@code log.mapping.080} a
     * collision rather than a second spelling.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"010.1.1.1", "1.01.1.1", "1.1.1.010", "00.0.0.0"})
    @DisplayName("a leading zero is rejected rather than silently reinterpreted")
    void aLeadingZeroIsRejected(String address) {
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> IpAddress.requireValid(address));
        assertTrue(rejected.getMessage().contains("leading zeros"), rejected.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"banana", "10.0.0", "10.0.0.1.2", "10.0.0.", ".10.0.0",
            "10 .0.0.1", "10,0,0,1", "1.2.3.4/24", "10.0.0.-1"})
    @DisplayName("anything that is not a dotted quad is rejected")
    void anythingThatIsNotADottedQuadIsRejected(String address) {
        assertThrows(IllegalArgumentException.class, () -> IpAddress.requireValid(address));
        assertFalse(IpAddress.isValid(address));
    }

    /**
     * Hostnames and IPv6 are out of scope by decision, not by accident, so they are pinned: a
     * pattern that started accepting either would be a widening nobody chose.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"localhost", "soap-host.local", "example.com", "::1",
            "fe80::1", "2001:db8::8a2e:370:7334"})
    @DisplayName("hostnames and IPv6 are out of scope, and stay out")
    void hostnamesAndIpv6AreOutOfScope(String address) {
        assertFalse(IpAddress.isValid(address),
                address + " is deliberately not accepted; widening this is a decision plus a test");
    }

    @Test
    @DisplayName("absent is rejected, and says so in words a person can act on")
    void absentIsRejected() {
        for (String nothing : new String[] {null, "", "   "}) {
            IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                    () -> IpAddress.requireValid(nothing));
            assertTrue(rejected.getMessage().contains("required"), rejected.getMessage());
        }
    }

    /**
     * The messages are shown verbatim beside the field that was refused, so they have to read as
     * something said to a person rather than as a validator's internal state.
     */
    @Test
    @DisplayName("a rejection names the value it rejected")
    void aRejectionNamesTheValueItRejected() {
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> IpAddress.requireValid("banana"));

        assertTrue(rejected.getMessage().contains("banana"), rejected.getMessage());
    }
}
