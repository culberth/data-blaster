package com.culberth.tools.datablaster.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.culberth.tools.datablaster.model.Settings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The arithmetic behind the Log group's Playback Speed slider, tested without a toolkit.
 *
 * <p>The whole reason this class exists is that a linear slider is unusable for a multiplier — so
 * the assertions worth having are about *where on the track* values land, not merely that the
 * conversion round-trips.
 */
class LogScaleTest {

    private final LogScale scale =
            new LogScale(Settings.PLAYBACK_SPEED_MIN, Settings.PLAYBACK_SPEED_MAX);

    /**
     * The claim the whole design rests on: the default is in the middle, where a hand expects it.
     * On a linear 0.1-10.0 track it would sit at 9%.
     */
    @Test
    @DisplayName("real time sits at the centre of the track")
    void realTimeSitsAtTheCentreOfTheTrack() {
        assertEquals(0.5, scale.positionOf(1.0), 0.0001,
                "1.0 is the geometric mean of 0.1 and 10.0, so it belongs at mid-track");
        assertEquals(1.0, scale.valueAt(0.5), 0.0001);
    }

    @Test
    @DisplayName("the bounds sit at the ends")
    void theBoundsSitAtTheEnds() {
        assertEquals(0.0, scale.positionOf(Settings.PLAYBACK_SPEED_MIN), 0.0001);
        assertEquals(1.0, scale.positionOf(Settings.PLAYBACK_SPEED_MAX), 0.0001);
        assertEquals(Settings.PLAYBACK_SPEED_MIN, scale.valueAt(0.0), 0.0001);
        assertEquals(Settings.PLAYBACK_SPEED_MAX, scale.valueAt(1.0), 0.0001);
    }

    /**
     * What "logarithmic" buys, stated as a property rather than as a formula: halving and doubling
     * move the thumb the same distance, which is what makes a multiplier scrubbable.
     */
    @Test
    @DisplayName("halving and doubling cover equal travel")
    void halvingAndDoublingCoverEqualTravel() {
        double toHalf = scale.positionOf(1.0) - scale.positionOf(0.5);
        double toDouble = scale.positionOf(2.0) - scale.positionOf(1.0);

        assertEquals(toHalf, toDouble, 0.0001,
                "0.5x and 2x are equally far from real time, so they must look it");
    }

    @Test
    @DisplayName("slow motion gets half the track, not a tenth of it")
    void slowMotionGetsHalfTheTrack() {
        assertEquals(0.5, scale.positionOf(1.0), 0.0001);
        assertTrue(scale.positionOf(0.5) > 0.2,
                "0.5x should be comfortably reachable, not crammed against the left end");
    }

    @ParameterizedTest(name = "value={0}")
    @ValueSource(doubles = {0.1, 0.25, 0.5, 1.0, 2.0, 4.0, 10.0})
    @DisplayName("a value survives a trip to the track and back")
    void aValueSurvivesATripToTheTrackAndBack(double value) {
        assertEquals(value, scale.valueAt(scale.positionOf(value)), 0.0001);
    }

    /**
     * A log scale naturally produces values like 1.0000000000000002. Rounding is in the scale
     * rather than in the display format on purpose: the rounded number is what gets stored, so
     * formatting it away would leave the settings file and the read-out disagreeing.
     */
    @Test
    @DisplayName("the rounded value is clean enough to store and to show")
    void theRoundedValueIsCleanEnoughToStoreAndToShow() {
        assertEquals(1.0, scale.valueAt(0.5, 2));
        assertEquals(Settings.PLAYBACK_SPEED_MIN, scale.valueAt(0.0, 2));
        assertEquals(Settings.PLAYBACK_SPEED_MAX, scale.valueAt(1.0, 2));
    }

    /**
     * Rounding must not push a value outside the range the store will accept, or the control would
     * write a setting that comes back as the default on the next launch.
     */
    @ParameterizedTest(name = "position={0}")
    @ValueSource(doubles = {0.0, 0.001, 0.25, 0.5, 0.75, 0.999, 1.0})
    @DisplayName("no rounded value falls outside what the store accepts")
    void noRoundedValueFallsOutsideWhatTheStoreAccepts(double position) {
        double value = scale.valueAt(position, 2);

        assertTrue(value >= Settings.PLAYBACK_SPEED_MIN && value <= Settings.PLAYBACK_SPEED_MAX,
                value + " is outside [" + Settings.PLAYBACK_SPEED_MIN + ", "
                        + Settings.PLAYBACK_SPEED_MAX + "] and would not survive a restart");
    }

    // --- the edges -----------------------------------------------------------------------------

    @Test
    @DisplayName("out-of-range input clamps rather than running off the track")
    void outOfRangeInputClamps() {
        assertEquals(0.0, scale.positionOf(0.0), 0.0001);
        assertEquals(0.0, scale.positionOf(-5.0), 0.0001);
        assertEquals(1.0, scale.positionOf(1000.0), 0.0001);
        assertEquals(Settings.PLAYBACK_SPEED_MIN, scale.valueAt(-1.0), 0.0001);
        assertEquals(Settings.PLAYBACK_SPEED_MAX, scale.valueAt(2.0), 0.0001);
    }

    /** A NaN reaching a slider leaves the thumb unrendered, which looks like a broken control. */
    @Test
    @DisplayName("NaN is absorbed rather than propagated into the control")
    void nanIsAbsorbedRatherThanPropagated() {
        assertEquals(0.0, scale.positionOf(Double.NaN));
        assertEquals(Settings.PLAYBACK_SPEED_MIN, scale.valueAt(Double.NaN));
    }

    @Test
    @DisplayName("a scale that cannot have a logarithm fails at construction")
    void aScaleThatCannotHaveALogarithmFailsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new LogScale(0.0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> new LogScale(-1.0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> new LogScale(10.0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> new LogScale(10.0, 1.0));
    }
}
