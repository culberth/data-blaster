package com.culberth.tools.datablaster.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The tolerant-parse contract every enum in the settings file follows.
 *
 * <p>{@link Theme} established the shape and {@link Mode}, {@link MessageType} and
 * {@link SoapMessageType} copy it, which makes it the kind of convention that decays quietly: a
 * fifth enum written the obvious way would throw {@code IllegalArgumentException} out of
 * {@code SettingsStore.read()}, and the one thing that method must never do is throw. These tests
 * state the contract once, per enum, so a new constant set has something to be checked against.
 *
 * <p>The rule itself: the settings file is meant to be hand-editable, so an unrecognised value is a
 * typo to recover from rather than a reason to fail a launch — and whatever is written must read
 * back as itself.
 */
class StoredEnumParsingTest {

    // --- Mode -------------------------------------------------------------------------------

    @Test
    @DisplayName("every Mode round-trips through its stored name")
    void everyModeRoundTripsThroughItsStoredName() {
        for (Mode mode : Mode.values()) {
            assertSame(mode, Mode.fromStoredName(mode.storedName(), Mode.REST),
                    mode + " did not survive a write-then-read");
        }
    }

    @ParameterizedTest(name = "mode={0}")
    @ValueSource(strings = {"LOG", "log", "  Log  ", "lOg"})
    @DisplayName("a stored mode is read case-insensitively and trimmed")
    void aStoredModeIsReadCaseInsensitivelyAndTrimmed(String stored) {
        assertSame(Mode.LOG, Mode.fromStoredName(stored, Mode.REST));
    }

    @ParameterizedTest(name = "mode={0}")
    @ValueSource(strings = {"", "   ", "banana", "view1", "LOGGING"})
    @DisplayName("an unrecognised mode falls back rather than throwing")
    void anUnrecognisedModeFallsBack(String stored) {
        assertSame(Mode.SOAP, Mode.fromStoredName(stored, Mode.SOAP));
    }

    @Test
    @DisplayName("a missing mode falls back rather than throwing")
    void aMissingModeFallsBack() {
        assertSame(Mode.SOAP, Mode.fromStoredName(null, Mode.SOAP));
    }

    /**
     * The template stored a view id here. Those spellings are now unknown keys' worth of nothing,
     * and must not resolve to a mode by accident.
     */
    @Test
    @DisplayName("the template's view ids do not resolve to a mode")
    void theTemplatesViewIdsDoNotResolveToAMode() {
        for (String viewId : new String[] {"view1", "view2", "view3", "view4"}) {
            assertSame(Mode.LOG, Mode.fromStoredName(viewId, Mode.LOG));
        }
    }

    // --- MessageType ------------------------------------------------------------------------

    @Test
    @DisplayName("every MessageType round-trips through its stored name")
    void everyMessageTypeRoundTripsThroughItsStoredName() {
        for (MessageType type : MessageType.values()) {
            assertSame(type, MessageType.fromStoredName(type.storedName(), MessageType.MESSAGE_3),
                    type + " did not survive a write-then-read");
        }
    }

    @ParameterizedTest(name = "type={0}")
    @ValueSource(strings = {"MESSAGE_2", "message_2", "  Message_2  "})
    @DisplayName("a stored message type is read case-insensitively and trimmed")
    void aStoredMessageTypeIsReadCaseInsensitivelyAndTrimmed(String stored) {
        assertSame(MessageType.MESSAGE_2, MessageType.fromStoredName(stored, MessageType.MESSAGE_1));
    }

    @ParameterizedTest(name = "type={0}")
    @ValueSource(strings = {"", "  ", "MESSAGE_4", "MESSAGE 2", "Message 2", "banana"})
    @DisplayName("an unrecognised message type falls back rather than throwing")
    void anUnrecognisedMessageTypeFallsBack(String stored) {
        assertSame(MessageType.MESSAGE_1, MessageType.fromStoredName(stored, MessageType.MESSAGE_1));
    }

    // --- SoapMessageType --------------------------------------------------------------------

    @Test
    @DisplayName("every SoapMessageType round-trips through its stored name")
    void everySoapMessageTypeRoundTripsThroughItsStoredName() {
        for (SoapMessageType type : SoapMessageType.values()) {
            assertSame(type, SoapMessageType.fromStoredName(type.storedName(),
                            SoapMessageType.TYPE_3),
                    type + " did not survive a write-then-read");
        }
    }

    @ParameterizedTest(name = "type={0}")
    @ValueSource(strings = {"TYPE_2", "type_2", "  Type_2  "})
    @DisplayName("a stored SOAP message type is read case-insensitively and trimmed")
    void aStoredSoapMessageTypeIsReadCaseInsensitivelyAndTrimmed(String stored) {
        assertSame(SoapMessageType.TYPE_2,
                SoapMessageType.fromStoredName(stored, SoapMessageType.TYPE_1));
    }

    @ParameterizedTest(name = "type={0}")
    @ValueSource(strings = {"", "  ", "TYPE_4", "TYPE 2", "Type 2", "banana"})
    @DisplayName("an unrecognised SOAP message type falls back rather than throwing")
    void anUnrecognisedSoapMessageTypeFallsBack(String stored) {
        assertSame(SoapMessageType.TYPE_1,
                SoapMessageType.fromStoredName(stored, SoapMessageType.TYPE_1));
    }

    /**
     * The two type settings are separate enums so that adding a constant to one is not a change to
     * the other. Sharing the stored names would undo half of that on the read path.
     */
    @Test
    @DisplayName("the two message-type enums do not answer for each other")
    void theTwoMessageTypeEnumsDoNotAnswerForEachOther() {
        assertSame(MessageType.MESSAGE_1,
                MessageType.fromStoredName("TYPE_2", MessageType.MESSAGE_1),
                "a SOAP type name is not a Message mode type");
        assertSame(SoapMessageType.TYPE_1,
                SoapMessageType.fromStoredName("MESSAGE_2", SoapMessageType.TYPE_1),
                "and the other way round");
    }

    // --- display names ----------------------------------------------------------------------

    /**
     * The stored form and the shown form are deliberately different, and a control that rendered
     * the constant name would be the first sign they had been collapsed into one.
     */
    @Test
    @DisplayName("the shown name is not the stored name")
    void theShownNameIsNotTheStoredName() {
        assertEquals("Log", Mode.LOG.toString());
        assertEquals("log", Mode.LOG.storedName());
        assertEquals("Message 1", MessageType.MESSAGE_1.toString());
        assertEquals("MESSAGE_1", MessageType.MESSAGE_1.storedName());
        assertEquals("Type 1", SoapMessageType.TYPE_1.toString());
        assertEquals("TYPE_1", SoapMessageType.TYPE_1.storedName());
    }
}
