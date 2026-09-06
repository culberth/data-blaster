package com.culberth.tools.datablaster.model;

import java.util.Locale;

/**
 * What the application is currently doing. Each mode owns a content view and its own block of settings; nothing is
 * shared between them but the theme and which mode is selected.
 *
 * <p>
 * <strong>An enum rather than a view id string.</strong> This replaces the free-string {@code currentViewId} the
 * template carried. The two would otherwise be separate concepts kept in step by hand — a mode to configure and a view
 * to show — and a typo in either was a run-time discovery in an otherwise green build. A closed set the compiler checks
 * removes both problems, at the cost of the FXML declaring constant names in {@code userData} that
 * {@code ModeGroupViewIdTest} has to keep honest.
 *
 * <p>
 * {@link #REST} has no settings and no behaviour yet. It ships as a visible toggle anyway: a disabled button with no
 * explanation is worse than a view that says plainly it is not implemented.
 */
public enum Mode
{

    LOG("Log"), MESSAGE("Message"), SOAP("SOAP"), REST("REST");

    private final String displayName;

    Mode(String displayName)
    {
        this.displayName = displayName;
    }

    /**
     * Shown on the ribbon toggle and anywhere a mode is named to the user. {@code toString()} rather than a cell
     * factory, for the same reason {@link Theme} does it — one override keeps every control that renders a {@code Mode}
     * from disagreeing with the others.
     */
    @Override
    public String toString()
    {
        return displayName;
    }

    /**
     * Parses a stored mode name, falling back rather than throwing.
     *
     * <p>
     * The settings file is meant to be hand-editable, so an unrecognised value is a typo to recover from, not a reason
     * to fail a launch — the same rule every other stored value follows.
     */
    public static Mode fromStoredName(String name, Mode fallback)
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

    /** The form written to the settings file: stable, and independent of {@link #toString()}. */
    public String storedName()
    {
        return name().toLowerCase(Locale.ROOT);
    }
}
