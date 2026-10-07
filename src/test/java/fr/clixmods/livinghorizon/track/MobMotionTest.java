package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobMotionTest {
    @Test
    void calledBackToItsSpotOnlyAsTheHandoverNears() {
        assertEquals(0f, MobMotion.home(200, 64), 1e-6);
        assertEquals(0f, MobMotion.home(112, 64), 1e-6);
        assertEquals(1f, MobMotion.home(72, 64), 1e-6);
        assertEquals(1f, MobMotion.home(10, 64), 1e-6);
        float last = 0;
        for (double d = 112; d >= 72; d -= 1) {
            float now = MobMotion.home(d, 64);
            assertTrue(now >= last, "never goes back as it gets closer");
            last = now;
        }
    }

    @Test
    void glanceStaysWithinAHalfTurnAndMovesContinuously() {
        float last = MobMotion.glance(99L, 0);
        for (int tick = 1; tick < 2000; tick++) {
            float now = MobMotion.glance(99L, tick);
            assertTrue(Math.abs(now) <= 45.001f);
            // At most 90 degrees over 20 ticks, eased: 1.5 * 90 / 20 = 6.75 degrees a tick.
            assertTrue(Math.abs(now - last) <= 6.8f, "no snap at tick " + tick);
            last = now;
        }
    }
}
