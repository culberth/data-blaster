package com.culberth.tools.datablaster.ui;

/**
 * Maps a value onto a slider track logarithmically, so a multiplier range is usable.
 *
 * <p><strong>The problem this solves.</strong> Playback Speed Factor runs 0.1–10.0 with a default of
 * 1.0. On a linear track that default sits at 9% of the travel: every slow-motion setting is crushed
 * into the leftmost tenth, 90% of the slider is spent above real time, and the value a user returns
 * to most is the hardest one to land on. On a log scale the same range puts 1.0 dead centre, with
 * 0.1 and 10.0 at the ends — halving and doubling cover equal distance, which is what a multiplier
 * means.
 *
 * <p>The mapping is {@code value = min * (max/min)^position} for a position in 0–1, and its inverse
 * {@code position = ln(value/min) / ln(max/min)}. The midpoint is therefore the geometric mean of
 * the bounds, which for 0.1 and 10.0 is exactly 1.0 — that is not a coincidence arranged for this
 * setting, it is why the bounds were chosen symmetric about real time.
 *
 * <p><strong>Not in {@code model}, and not a JavaFX class either.</strong> This is presentation
 * arithmetic: the stored value is always the real multiplier, and nothing outside the control that
 * draws it ever sees a position. Keeping it a plain class with no toolkit dependency is what lets
 * {@code LogScaleTest} pin the round-trip without a display.
 */
public final class LogScale {

    /** The lowest value the scale represents, at position 0. Must be positive. */
    private final double min;

    /** The highest value the scale represents, at position 1. */
    private final double max;

    /** {@code ln(max/min)}, precomputed because both directions divide by it. */
    private final double logRatio;

    /**
     * @throws IllegalArgumentException unless {@code 0 < min < max}. A non-positive minimum has no
     *     logarithm, and an inverted or degenerate range would divide by zero on the first call —
     *     failing at construction says which of those it was.
     */
    public LogScale(double min, double max) {
        if (!(min > 0)) {
            throw new IllegalArgumentException("A log scale needs a positive minimum, but was " + min);
        }
        if (!(max > min)) {
            throw new IllegalArgumentException(
                    "A log scale needs max > min, but was min=" + min + " max=" + max);
        }
        this.min = min;
        this.max = max;
        this.logRatio = Math.log(max / min);
    }

    /** The 0–1 track position that represents {@code value}, clamped to the ends. */
    public double positionOf(double value) {
        if (Double.isNaN(value)) {
            // A NaN would propagate silently into the slider and leave the thumb unrendered.
            // Position 0 is wrong but visible, which beats a control that vanishes.
            return 0.0;
        }
        return clampToUnit(Math.log(clamp(value, min, max) / min) / logRatio);
    }

    /** The value at 0–1 track position {@code position}, clamped to the scale's bounds. */
    public double valueAt(double position) {
        if (Double.isNaN(position)) {
            return min;
        }
        return clamp(min * Math.exp(clampToUnit(position) * logRatio), min, max);
    }

    /**
     * {@code valueAt(position)} rounded to {@code decimals} places.
     *
     * <p>A log scale produces values like 1.0000000000000002, and a control that wrote those
     * straight through would show a nonsense read-out and persist a settings file full of noise.
     * Rounding belongs here rather than in the display format: the rounded number is the value that
     * gets stored, so formatting it away would leave the file and the read-out disagreeing.
     */
    public double valueAt(double position, int decimals) {
        double factor = Math.pow(10, decimals);
        return clamp(Math.round(valueAt(position) * factor) / factor, min, max);
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    private static double clampToUnit(double position) {
        return clamp(position, 0.0, 1.0);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
}
