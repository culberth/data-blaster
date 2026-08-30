package com.culberth.tools.datablaster.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What a port-to-tail mapping will and will not accept.
 *
 * <p>At the model layer and with no JavaFX toolkit, deliberately. These are the rules the mapping
 * editor will enforce at entry, and pinning them here rather than through a {@code TableView} means
 * they stay checked whatever that control ends up looking like — and that the check itself does not
 * need a display.
 */
class PortTailMappingTest {

    // --- the tail number format -----------------------------------------------------------------

    @ParameterizedTest(name = "tail={0}")
    @ValueSource(strings = {"N12345", "123456", "000042", "ABCDEF", "A1B2C3", "N7377X"})
    @DisplayName("six letters or digits, in any combination, is a tail number")
    void sixLettersOrDigitsIsATailNumber(String tail) {
        assertEquals(tail, PortTailMapping.of(5001, tail).tail());
    }

    /**
     * The leading zero is the reason this is a {@code String}. An integer type would round-trip
     * {@code 000042} to {@code 42} and then reject it for being four characters short.
     */
    @Test
    @DisplayName("a tail that looks like a number keeps its leading zeros")
    void aTailThatLooksLikeANumberKeepsItsLeadingZeros() {
        assertEquals("000042", PortTailMapping.of(5001, "000042").tail());
    }

    @Test
    @DisplayName("a tail is trimmed and upper-cased on the way in")
    void aTailIsTrimmedAndUpperCasedOnTheWayIn() {
        assertEquals("N12345", PortTailMapping.of(5001, "  n12345 ").tail());
        // Normalising in the canonical constructor, not only in of(), is what makes this hold for
        // every construction path — and therefore what makes the uniqueness check below meaningful.
        assertEquals("N12345", new PortTailMapping(5001, "n12345").tail());
    }

    @ParameterizedTest(name = "tail={0}")
    @ValueSource(strings = {"N123", "N123456", "", "   ", "N1234!", "N-1234", "G-ABCD", "N 1234"})
    @DisplayName("anything but exactly six alphanumerics is rejected")
    void anythingButExactlySixAlphanumericsIsRejected(String tail) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PortTailMapping.of(5001, tail));
        assertTrue(e.getMessage().contains("Tail number"), e.getMessage());
    }

    /**
     * The length is exact rather than a maximum, which is stricter than any real registration rule
     * — and stricter is where the value is, since {@code N123} is far more likely to be a truncated
     * typo than a deliberate short tail.
     */
    @Test
    @DisplayName("a hyphenated registration is rejected, and the message says why")
    void aHyphenatedRegistrationIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PortTailMapping.of(5001, "G-ABCD"));
        assertTrue(e.getMessage().contains("letters or digits"), e.getMessage());
    }

    @Test
    @DisplayName("a null tail is rejected rather than reaching the pattern")
    void aNullTailIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PortTailMapping.of(5001, null));
    }

    // --- the port range -------------------------------------------------------------------------

    @ParameterizedTest(name = "port={0}")
    @ValueSource(ints = {1, 80, 5001, 8080, 65535})
    @DisplayName("a port anywhere in range is accepted, including both ends")
    void aPortAnywhereInRangeIsAccepted(int port) {
        assertEquals(port, PortTailMapping.of(port, "N12345").port());
    }

    @ParameterizedTest(name = "port={0}")
    @ValueSource(ints = {0, -1, 65536, Integer.MAX_VALUE, Integer.MIN_VALUE})
    @DisplayName("a port outside 1-65535 is rejected")
    void aPortOutsideTheRangeIsRejected(int port) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PortTailMapping.of(port, "N12345"));
        assertTrue(e.getMessage().contains("Port"), e.getMessage());
    }

    // --- uniqueness, in both directions ---------------------------------------------------------

    @Test
    @DisplayName("a set with distinct ports and tails is accepted")
    void aSetWithDistinctPortsAndTailsIsAccepted() {
        List<PortTailMapping> mappings = PortTailMapping.requireUniquePortsAndTails(List.of(
                PortTailMapping.of(5002, "N12345"),
                PortTailMapping.of(5001, "123456")));

        assertEquals(2, mappings.size());
    }

    @Test
    @DisplayName("the same port twice is rejected, naming the port")
    void theSamePortTwiceIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PortTailMapping.requireUniquePortsAndTails(List.of(
                        PortTailMapping.of(5001, "N12345"),
                        PortTailMapping.of(5001, "123456"))));
        assertTrue(e.getMessage().contains("5001"), e.getMessage());
    }

    @Test
    @DisplayName("the same tail on two ports is rejected, naming the tail")
    void theSameTailOnTwoPortsIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PortTailMapping.requireUniquePortsAndTails(List.of(
                        PortTailMapping.of(5001, "N12345"),
                        PortTailMapping.of(5002, "N12345"))));
        assertTrue(e.getMessage().contains("N12345"), e.getMessage());
    }

    /**
     * The reason normalisation happens before the comparison rather than after it. Two tails
     * differing only in case are two different Strings, so a set that compared raw text would
     * accept both and leave one aircraft mapped to two ports.
     */
    @Test
    @DisplayName("two tails differing only in case collide rather than both being accepted")
    void twoTailsDifferingOnlyInCaseCollide() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PortTailMapping.requireUniquePortsAndTails(List.of(
                        PortTailMapping.of(5001, "n12345"),
                        PortTailMapping.of(5002, "N12345"))));
        assertTrue(e.getMessage().contains("N12345"), e.getMessage());
    }

    // --- the collection contract ----------------------------------------------------------------

    @Test
    @DisplayName("the returned set is ordered by port, whatever order it arrived in")
    void theReturnedSetIsOrderedByPort() {
        List<PortTailMapping> mappings = PortTailMapping.requireUniquePortsAndTails(List.of(
                PortTailMapping.of(5003, "AAAAAA"),
                PortTailMapping.of(5001, "BBBBBB"),
                PortTailMapping.of(5002, "CCCCCC")));

        assertEquals(List.of(5001, 5002, 5003), mappings.stream().map(PortTailMapping::port).toList(),
                "a written file and a rebuilt table should be reproducible");
    }

    /**
     * {@link Settings} is handed to a background writer thread on the strength of being immutable,
     * and a record wrapping a mutable list is not.
     */
    @Test
    @DisplayName("the returned set is unmodifiable")
    void theReturnedSetIsUnmodifiable() {
        List<PortTailMapping> mappings = PortTailMapping.requireUniquePortsAndTails(
                List.of(PortTailMapping.of(5001, "N12345")));

        assertThrows(UnsupportedOperationException.class,
                () -> mappings.add(PortTailMapping.of(5002, "123456")));
    }

    @Test
    @DisplayName("an empty or absent set is empty, not a failure")
    void anEmptyOrAbsentSetIsEmpty() {
        assertTrue(PortTailMapping.requireUniquePortsAndTails(List.of()).isEmpty());
        assertTrue(PortTailMapping.requireUniquePortsAndTails(null).isEmpty());
    }
}
