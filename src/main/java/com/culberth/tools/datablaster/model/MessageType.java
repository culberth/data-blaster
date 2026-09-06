package com.culberth.tools.datablaster.model;

import java.util.Locale;

/**
 * The single setting Message mode has: which kind of message it will send.
 *
 * <p>
 * <strong>Placeholder names on purpose.</strong> Message mode has no behaviour yet, so there is nothing to name these
 * after. They are deliberately not disguised as something more specific — a constant called {@code HEARTBEAT} that
 * turns out to mean nothing is harder to correct later than one that never claimed to.
 *
 * <p>
 * Controls populate from {@link #values()} rather than listing the constants, so adding a fourth is a one-line change
 * here and nowhere else.
 */
public enum MessageType
{

    MESSAGE_1("Message 1"), MESSAGE_2("Message 2"), MESSAGE_3("Message 3");

    private final String displayName;

    MessageType(String displayName)
    {
        this.displayName = displayName;
    }

    /** Shown in the Preferences chooser; see {@link Theme#toString()} for why it lives here. */
    @Override
    public String toString()
    {
        return displayName;
    }

    /**
     * Parses a stored message type, falling back rather than throwing — the same tolerance every other stored value
     * gets, for the same reason.
     */
    public static MessageType fromStoredName(String name, MessageType fallback)
    {
        if (name == null || name.isBlank())
        {
            return fallback;
        }
        try
        {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException unknown)
        {
            return fallback;
        }
    }

    /**
     * The form written to the settings file.
     *
     * <p>
     * Upper case, unlike {@link Theme#storedName()} and {@link Mode#storedName()}: these constants already carry an
     * underscore, and {@code message_1} reads as a mangled key rather than a value. {@link #fromStoredName} upper-cases
     * before parsing, so either spelling in a hand-edited file is accepted.
     */
    public String storedName()
    {
        return name();
    }
}
