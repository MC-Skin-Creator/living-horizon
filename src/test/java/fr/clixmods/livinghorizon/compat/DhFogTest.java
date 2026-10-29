package fr.clixmods.livinghorizon.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Distant Horizons' far fog as the game's two ramps give it to the entities. */
class DhFogTest {
    private static final double RADIUS = 512 * 16;

    /** The game's fog on a mob at a distance across the ground: {@code total_fog_value} times the alpha. */
    private static double game(DhFog.Ramps ramps, double distance) {
        return Math.max(linear(distance, ramps.environmentalStart(), ramps.environmentalEnd()),
                linear(distance, ramps.renderDistanceStart(), ramps.renderDistanceEnd())) * ramps.alpha();
    }

    private static double linear(double d, float start, float end) {
        if (d <= start) return 0;
        if (d >= end) return 1;
        return (d - start) / (end - start);
    }

    private static double worst(DhFog.Settings settings) {
        DhFog.Ramps ramps = DhFog.fit(settings);
        double worst = 0;
        for (int i = 0; i <= 200; i++) {
            double x = 1.5 * i / 200;
            worst = Math.max(worst, Math.abs(game(ramps, x * RADIUS) - DhFog.thickness(settings, x)));
        }
        return worst;
    }

    @Test
    void aLinearFogIsMatchedExactly() {
        assertTrue(worst(new DhFog.Settings(RADIUS, 0.4f, 1f, 0f, 1f, DhFog.LINEAR, 2.5f, false)) < 1e-3);
        // Some fog from the start, never all of it.
        assertTrue(worst(new DhFog.Settings(RADIUS, 0.2f, 0.9f, 0.1f, 0.8f, DhFog.LINEAR, 2.5f, false)) < 1e-3);
    }

    @Test
    void anExponentialFogIsCloselyFollowed() {
        double squared = worst(new DhFog.Settings(RADIUS, 0f, 1f, 0f, 1f, DhFog.EXPONENTIAL_SQUARED, 2.5f, false));
        double plain = worst(new DhFog.Settings(RADIUS, 0f, 1f, 0f, 1f, DhFog.EXPONENTIAL, 2.5f, false));
        assertTrue(squared < 0.12, "exponential squared off by " + squared);
        // Thickening fastest at its start, which no straight ramp can follow: closest on average.
        assertTrue(plain < 0.2, "exponential off by " + plain);
    }

    @Test
    void thinnestFogIsAFloorAndTheSlopeTakesTheRightDistance() {
        DhFog.Ramps across = DhFog.fit(new DhFog.Settings(RADIUS, 0.5f, 1f, 0.3f, 1f, DhFog.LINEAR, 1f, false));
        assertEquals(0.3, game(across, 0), 1e-3);
        assertEquals(0.3, game(across, 0.25 * RADIUS), 1e-3);
        assertTrue(across.renderDistanceEnd() < 2 * RADIUS, "the slope is across the ground: the render distance ramp");
        DhFog.Ramps spherical = DhFog.fit(new DhFog.Settings(RADIUS, 0.5f, 1f, 0.3f, 1f, DhFog.LINEAR, 1f, true));
        assertTrue(spherical.environmentalEnd() < 2 * RADIUS, "the slope in every direction: the environmental ramp");
        // No fog at all: nothing changes the mobs.
        assertEquals(0, DhFog.fit(new DhFog.Settings(RADIUS, 0f, 1f, 0f, 0f, DhFog.LINEAR, 1f, false)).alpha());
    }
}
