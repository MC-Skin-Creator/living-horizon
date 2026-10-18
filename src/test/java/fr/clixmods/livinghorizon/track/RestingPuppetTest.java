package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A sleeper whose blocks give way falls like anyone, lands exactly, and climbs back steadily. */
class RestingPuppetTest {
    @Test
    void fallsFasterAndFasterThenLandsOnTheFloor() {
        double y = 70, fall = 0, last = 0;
        int ticks = 0;
        while (y > 64) {
            RestingPuppet.Motion step = RestingPuppet.Motion.step(y, fall, 64);
            assertTrue(step.y() < y, "moves down every tick");
            assertTrue(y - step.y() >= last - 1e-9 || step.y() == 64, "speeds up until it lands");
            last = y - step.y();
            y = step.y();
            fall = step.fall();
            assertTrue(++ticks < 100);
        }
        assertEquals(64, y);
        assertEquals(0, fall);
        // Six blocks take about a second, as for a player.
        assertTrue(ticks > 10 && ticks < 25, ticks + " ticks");
    }

    @Test
    void neverFallsFasterThanThePlayersTerminalSpeed() {
        double y = 300, fall = 0;
        for (int i = 0; i < 200; i++) {
            RestingPuppet.Motion step = RestingPuppet.Motion.step(y, fall, -60);
            assertTrue(y - step.y() <= 3.92 + 1e-9);
            y = step.y();
            fall = step.fall();
        }
    }

    @Test
    void climbsBackWithoutOvershooting() {
        RestingPuppet.Motion step = RestingPuppet.Motion.step(64, 1.5, 64.5);
        assertEquals(64.3, step.y(), 1e-9);
        assertEquals(0, step.fall());
        assertEquals(64.5, RestingPuppet.Motion.step(step.y(), 0, 64.5).y(), 1e-9);
    }

    @Test
    void staysPutAtItsPlace() {
        RestingPuppet.Motion step = RestingPuppet.Motion.step(64, 0.4, 64);
        assertEquals(64, step.y());
        assertEquals(0, step.fall());
    }
}
