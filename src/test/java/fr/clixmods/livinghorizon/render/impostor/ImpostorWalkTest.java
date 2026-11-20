package fr.clixmods.livinghorizon.render.impostor;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A walk is cut into its pictures as the game's models swing their legs. */
class ImpostorWalkTest {
    /** How far a model swings a leg at walk {@code position}: {@code cos(position * 0.6662)}. */
    private static double swing(float position) {
        return Math.cos(position * 0.6662);
    }

    @Test
    void standingIsAtRest() {
        assertEquals(0, ImpostorWalk.frame(3.1f, 0f));
        assertEquals(0, ImpostorWalk.frame(0f, ImpostorWalk.WALKING / 2));
        assertEquals(0, ImpostorWalk.frame(Float.NaN, 1f));
        assertEquals(0, ImpostorWalk.frame(0f, Float.NaN));
    }

    @Test
    void eachPictureIsBakedWhereItIsShown() {
        Set<Integer> frames = new HashSet<>();
        for (int frame = 1; frame <= ImpostorWalk.FRAMES; frame++) {
            float position = ImpostorWalk.position(frame);
            assertEquals(frame, ImpostorWalk.frame(position, 1f));
            // Shown a little before and after the step it was baked at too.
            assertEquals(frame, ImpostorWalk.frame(position + ImpostorWalk.PERIOD * 0.1f, 1f));
            assertEquals(frame, ImpostorWalk.frame(position - ImpostorWalk.PERIOD * 0.1f, 1f));
            // A whole stride later, the same picture.
            assertEquals(frame, ImpostorWalk.frame(position + 7 * ImpostorWalk.PERIOD, 1f));
            frames.add(frame);
        }
        assertEquals(ImpostorWalk.FRAMES, frames.size());
    }

    @Test
    void theLegsCrossingAreTheRestingPicture() {
        for (int phase = 0; phase < ImpostorWalk.PHASES; phase++) {
            float position = phase * ImpostorWalk.PERIOD / ImpostorWalk.PHASES;
            int frame = ImpostorWalk.frame(position, 1f);
            if (Math.abs(swing(position)) < 1e-4) assertEquals(0, frame, "phase " + phase);
            else assertNotEquals(0, frame, "phase " + phase);
        }
    }

    @Test
    void theTwoEndsOfTheStrideAreTwoPictures() {
        int forward = ImpostorWalk.frame(0f, 1f), back = ImpostorWalk.frame(ImpostorWalk.PERIOD / 2, 1f);
        assertTrue(forward > 0 && back > 0);
        assertNotEquals(forward, back);
        assertEquals(1, swing(ImpostorWalk.position(forward)), 1e-4);
        assertEquals(-1, swing(ImpostorWalk.position(back)), 1e-4);
    }

    @Test
    void negativePositionsWrap() {
        assertEquals(ImpostorWalk.frame(ImpostorWalk.PERIOD / 2, 1f), ImpostorWalk.frame(-ImpostorWalk.PERIOD / 2, 1f));
    }
}
