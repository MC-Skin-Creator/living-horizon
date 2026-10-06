package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The locator bar replayed the way the server sends it: the angle from the receiver to
 * the target, sent again only once it moved by more than half a degree.
 */
class PositionFilterTest {
    private static final double SEND_THRESHOLD = 0.008726646;

    /** The server's own formula, from {@code WaypointTransmitter.EntityAzimuthConnection}. */
    private static double serverAngle(double rx, double rz, double tx, double tz) {
        double dx = rx - tx, dz = rz - tz;
        return Math.atan2(dx, -dz);
    }

    private static final class Bar {
        double angle = Double.NaN;

        double receive(double rx, double rz, double tx, double tz) {
            double a = serverAngle(rx, rz, tx, tz);
            if (Double.isNaN(angle) || Math.abs(a - angle) > SEND_THRESHOLD) angle = a;
            return angle;
        }
    }

    private static void step(PositionFilter filter, Bar bar, double ox, double oz, double tx, double tz) {
        filter.predict(0.05);
        double angle = bar.receive(ox, oz, tx, tz);
        boolean ok = filter.observeBearing(ox, oz, angle, Math.toRadians(0.6), Math.toRadians(0.6))
                && filter.observeDistance(ox, oz, 330, Double.POSITIVE_INFINITY, 6.0);
        if (!ok) throw new AssertionError("filter lost every guess");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void walkingSidewaysFindsTheDistance(int seed) {
        double tx = 900, tz = 1500; // ~1750 blocks away
        PositionFilter filter = new PositionFilter(new Random(seed));
        Bar bar = new Bar();
        filter.resetAlongBearing(0, 0, bar.receive(0, 0, tx, tz), 340, 3000);

        // Walking at 5.6 blocks per second, roughly across the line of sight, for 60 s.
        for (int tick = 0; tick < 1200; tick++) {
            double ox = -tick * 0.28 * 0.86, oz = tick * 0.28 * 0.5;
            step(filter, bar, ox, oz, tx, tz);
        }
        double error = Math.hypot(filter.x() - tx, filter.z() - tz);
        assertTrue(error < 250, "error " + error + " at (" + filter.x() + ", " + filter.z() + ")");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void standingStillKeepsTheDirection(int seed) {
        double tx = -2000, tz = 300;
        PositionFilter filter = new PositionFilter(new Random(seed));
        Bar bar = new Bar();
        filter.resetAlongBearing(0, 0, bar.receive(0, 0, tx, tz), 340, 3000);
        for (int tick = 0; tick < 600; tick++) step(filter, bar, 0, 0, tx, tz);

        double estimated = Math.atan2(-filter.x(), filter.z());
        double truth = Math.atan2(-tx, tz);
        assertTrue(Math.abs(estimated - truth) < Math.toRadians(2), "direction off by "
                + Math.toDegrees(Math.abs(estimated - truth)) + " degrees");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void followsAPlayerWalkingAway(int seed) {
        // Last seen at 350 blocks, then walks away at 4 blocks per second for 90 s.
        PositionFilter filter = new PositionFilter(new Random(seed));
        Bar bar = new Bar();
        double tx = 350, tz = 0;
        filter.resetAt(tx, tz, 4, 0, 1);
        for (int tick = 0; tick < 1800; tick++) {
            tx += 4 * 0.05;
            double ox = 0, oz = Math.sin(tick / 200.0) * 60; // wandering a little
            step(filter, bar, ox, oz, tx, tz);
        }
        double error = Math.hypot(filter.x() - tx, filter.z() - tz);
        assertTrue(error < 200, "error " + error + ", truth " + tx + ", estimate " + filter.x());
    }
}
