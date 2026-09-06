package com.culberth.tools.datablaster.model;

import java.util.Locale;

/**
 * Which kind of message SOAP mode will send.
 *
 * <p>
 * <strong>A separate enum from {@link MessageType}, not a shared one.</strong> The two modes both happen to offer three
 * choices today, which is the whole of what they have in common: nothing says SOAP's third type is Message mode's third
 * type, and a shared enum would make adding a fourth to one of them a change to the other. Sharing it would also mean
 * one persisted value for two settings, so changing the type in one mode would silently change it in the other.
 *
 * <p>
 * <strong>Placeholder names on purpose.</strong> SOAP mode has no behaviour yet, so there is nothing to name these
 * after. They are deliberately not disguised as something more specific — a constant called {@code ENVELOPE} that turns
 * out to mean nothing is harder to correct later than one that never claimed to.
 *
 * <p>
 * Controls populate from {@link #values()} rather than listing the constants, so adding a fourth is a one-line change
 * here and nowhere else.
 */
public enum SoapMessageType
{

    TYPE_1("Type 1"), TYPE_2("Type 2"), TYPE_3("Type 3");

    private final String displayName;

    SoapMessageType(String displayName)
    {
        this.displayName = displayName;
    }

    /** Shown in the choosers and the mode view; see {@link Theme#toString()} for why it lives here. */
    @Override
    public String toString()
    {
        return displayName;
    }

    /**
     * Parses a stored type, falling back rather than throwing — the same tolerance every other stored value gets, for
     * the same reason.
     */
    public static SoapMessageType fromStoredName(String name, SoapMessageType fallback)
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
     * Upper case, like {@link MessageType#storedName()} and for the same reason: these constants already carry an
     * underscore, and {@code type_1} reads as a mangled key rather than a value. {@link #fromStoredName} upper-cases
     * before parsing, so either spelling in a hand-edited file is accepted.
     */
    public String storedName()
    {
        return name();
    }
}
