package com.example.jfxribbon.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.jfxribbon.ViewLoader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * NFR4 and WCAG SC 2.4.11, enforced against the stylesheet rather than stated in a document.
 *
 * <p>The architecture document has long quoted a contrast figure for the read-outs. A number in
 * prose is a number that goes stale the first time someone nudges a hex value — and the failure is
 * invisible, because nothing looks broken at 3.9:1. Adding a second theme doubles the surface for
 * that to happen on, so the ratios are computed here from the tokens the application actually uses.
 *
 * <p>This parses {@code ribbon.css} rather than duplicating the palette. A test carrying its own
 * copy of the colours would keep passing after the stylesheet changed, which is the one thing it
 * must not do.
 *
 * <p>No toolkit and no Spring context: it is arithmetic over a text file.
 */
class ThemeContrastTest {

    /** {@code -jfx-name: #rrggbb;} inside a rule block. */
    private static final Pattern TOKEN =
            Pattern.compile("(-jfx-[a-z-]+)\\s*:\\s*(#[0-9a-fA-F]{6})\\s*;");

    private static final String LIGHT_SELECTOR = ".root {";
    private static final String DARK_SELECTOR = ".root.theme-dark {";

    /** The pairs that actually appear on screen, and the floor each one has to clear. */
    private record Pair(String foreground, String background, double minimum, String what) {
        @Override
        public String toString() {
            return foreground + " on " + background + " (" + what + ")";
        }
    }

    private static final List<Pair> PAIRS = List.of(
            new Pair("-jfx-text", "-jfx-surface-raised", 4.5, "body text on the content area"),
            new Pair("-jfx-text", "-jfx-surface", 4.5, "body text on the window background"),
            new Pair("-jfx-text", "-jfx-surface-header", 4.5, "tab labels on the ribbon header"),
            new Pair("-jfx-text", "-jfx-surface-hover", 4.5, "a hovered ribbon button label"),
            new Pair("-jfx-text", "-jfx-accent-surface", 4.5, "the selected ribbon button label"),
            new Pair("-jfx-text-secondary", "-jfx-surface-raised", 4.5, "group titles and hints"),
            new Pair("-jfx-text-readout", "-jfx-surface-raised", 4.5, "the value read-outs"),
            // Non-text: SC 1.4.11 for the graphics, SC 2.4.11 for the focus indicator.
            new Pair("-jfx-icon", "-jfx-surface-raised", 3.0, "icon strokes"),
            new Pair("-jfx-accent", "-jfx-surface-raised", 3.0, "the focus border"),
            new Pair("-jfx-accent", "-jfx-accent-surface", 3.0, "the selection border on its fill"),
            new Pair("-jfx-accent-dark", "-jfx-accent-surface", 3.0,
                    "the focus ring on a selected button"));

    static List<String> themes() {
        return List.of(LIGHT_SELECTOR, DARK_SELECTOR);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("themes")
    @DisplayName("every on-screen pair clears its contrast floor")
    void everyOnScreenPairClearsItsContrastFloor(String selector) throws IOException {
        Map<String, String> tokens = tokensUnder(selector);

        for (Pair pair : PAIRS) {
            String fg = tokens.get(pair.foreground());
            String bg = tokens.get(pair.background());
            assertTrue(fg != null && bg != null,
                    selector + " does not define both of " + pair + "; a theme that redefines only "
                            + "some tokens inherits the rest from .root and mixes the two palettes");

            double ratio = contrastRatio(fg, bg);
            assertTrue(ratio >= pair.minimum(),
                    String.format("%s in %s measures %.2f:1, below the %.1f:1 floor (%s on %s)",
                            pair, selector, ratio, pair.minimum(), fg, bg));
        }
    }

    /**
     * A theme that redefines only part of the palette would silently inherit the rest from
     * {@code .root} — half-dark text on a dark ground, and every pair above still passing because
     * they were measured against whatever was inherited.
     */
    @Test
    @DisplayName("the dark theme redefines every token the light one defines")
    void theDarkThemeRedefinesEveryTokenTheLightOneDefines() throws IOException {
        Map<String, String> light = tokensUnder(LIGHT_SELECTOR);
        Map<String, String> dark = tokensUnder(DARK_SELECTOR);

        assertEquals(light.keySet(), dark.keySet(),
                "the two palettes must define the same token names");
    }

    @Test
    @DisplayName("the two themes are actually different")
    void theTwoThemesAreActuallyDifferent() throws IOException {
        Map<String, String> light = tokensUnder(LIGHT_SELECTOR);
        Map<String, String> dark = tokensUnder(DARK_SELECTOR);

        // The bar for shipping this feature was that it changes what the user sees.
        assertTrue(luminance(dark.get("-jfx-surface")) < luminance(light.get("-jfx-surface")),
                "the dark theme's ground should be darker than the light theme's");
        assertTrue(luminance(dark.get("-jfx-text")) > luminance(light.get("-jfx-text")),
                "the dark theme's text should be lighter than the light theme's");
    }

    // --- reading the stylesheet ---------------------------------------------------------------

    /** The {@code -jfx-*} declarations in the first rule block opening with {@code selector}. */
    private static Map<String, String> tokensUnder(String selector) throws IOException {
        String css = stylesheet();
        int start = css.indexOf(selector);
        assertTrue(start >= 0, "no '" + selector + "' block in " + ViewLoader.STYLESHEET);
        int end = css.indexOf('}', start);
        assertTrue(end > start, "unterminated '" + selector + "' block");

        Map<String, String> tokens = new LinkedHashMap<>();
        Matcher matcher = TOKEN.matcher(css.substring(start, end));
        while (matcher.find()) {
            tokens.put(matcher.group(1), matcher.group(2).toLowerCase(java.util.Locale.ROOT));
        }
        assertTrue(tokens.size() >= 10,
                "expected a full palette under '" + selector + "' but found " + tokens.keySet());
        return tokens;
    }

    private static String stylesheet() throws IOException {
        try (InputStream in = ThemeContrastTest.class.getResourceAsStream(ViewLoader.STYLESHEET)) {
            assertTrue(in != null, ViewLoader.STYLESHEET + " is not on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // --- WCAG 2.x relative luminance and contrast ---------------------------------------------

    private static double contrastRatio(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double r = channel((rgb >> 16) & 0xFF);
        double g = channel((rgb >> 8) & 0xFF);
        double b = channel(rgb & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
