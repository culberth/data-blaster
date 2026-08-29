package com.example.jfxribbon.model;

import java.util.Locale;

/**
 * The application's colour theme.
 *
 * <p>Each constant names a style class that {@code ribbon.css} redefines the design tokens under.
 * Because every rule in that stylesheet refers to a token rather than a hex literal, a theme is one
 * block of overrides rather than a parallel copy of the stylesheet — which is the whole return on
 * having tokenised it.
 *
 * <p>{@link #LIGHT} carries a style class it does not strictly need: the bare {@code .root} block
 * already defines the light values. Naming it anyway keeps the switch symmetric — remove every
 * theme class, add exactly one — and gives a fork somewhere to hang light-only overrides without
 * restructuring anything.
 */
public enum Theme {

    LIGHT("theme-light", "Light"),
    DARK("theme-dark", "Dark");

    private final String styleClass;
    private final String displayName;

    Theme(String styleClass, String displayName) {
        this.styleClass = styleClass;
        this.displayName = displayName;
    }

    public String styleClass() {
        return styleClass;
    }

    /**
     * Shown in the Preferences chooser. {@code toString()} rather than a cell factory, because a
     * {@code ChoiceBox} uses it for both the list and the button, and one override keeps them from
     * disagreeing.
     */
    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Parses a stored theme name, falling back rather than throwing.
     *
     * <p>The settings file is meant to be hand-editable, so an unrecognised value is a typo to
     * recover from, not a reason to fail a launch — the same rule every other stored value follows.
     */
    public static Theme fromStoredName(String name, Theme fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }

    /** The form written to the settings file: stable, and independent of {@link #toString()}. */
    public String storedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
