package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A cow at (10.5, 64, 20.5), on different grounds: the loop it gets never leaves level ground. */
class MobPathsTest {
    private static final double X = 10.5, Y = 64, Z = 20.5, WIDTH = 0.9;
    private static final int BX = 10, BZ = 20, R = MobPaths.RADIUS, N = MobPaths.SIZE;

    private static double[][] ground(double fill) {
        double[][] ground = new double[N][N];
        for (double[] row : ground) Arrays.fill(row, fill);
        return ground;
    }

    /** Every point the plan walks through is level ground, as wide as the cow. */
    private static void staysOnGround(double[][] ground, MobPaths.Plan plan) {
        switch (plan) {
            case MobPaths.Circle c -> {
                double cx = X - c.radius() * Math.cos(c.start()), cz = Z - c.radius() * Math.sin(c.start());
                for (int s = 0; s < 360; s += 5) {
                    double a = Math.toRadians(s);
                    assertTrue(MobPaths.walkable(ground, cx + c.radius() * Math.cos(a), Y, cz + c.radius() * Math.sin(a),
                            BX, BZ, WIDTH), "circle leaves the ground at " + s + " degrees: " + c);
                }
                // And it does pass through the spot the cow stands on.
                assertEquals(X, cx + c.radius() * Math.cos(c.start()), 1e-9);
            }
            case MobPaths.Pace p -> {
                for (double d = 0; d <= p.length(); d += 0.1) {
                    assertTrue(MobPaths.walkable(ground, X + Math.sin(p.angle()) * d, Y, Z + Math.cos(p.angle()) * d,
                            BX, BZ, WIDTH), "walk leaves the ground at " + d + ": " + p);
                }
            }
            case MobPaths.Stand s -> {
            }
        }
    }

    @Test
    void openGroundGetsTheWidestCircle() {
        double[][] ground = ground(64);
        MobPaths.Plan plan = MobPaths.choose(ground, X, Y, Z, BX, BZ, WIDTH, 42);
        assertEquals(1.8, assertInstanceOf(MobPaths.Circle.class, plan).radius(), 1e-9);
        staysOnGround(ground, plan);
    }

    @Test
    void cliffEdgeKeepsItOnTheTop() {
        double[][] ground = ground(64);
        // Everything east of the cow's own block drops by four blocks.
        for (int i = R + 1; i < N; i++) Arrays.fill(ground[i], 60);
        for (long seed = 0; seed < 16; seed++) {
            MobPaths.Plan plan = MobPaths.choose(ground, X, Y, Z, BX, BZ, WIDTH, seed);
            assertTrue(!(plan instanceof MobPaths.Stand), "room enough to move on the cliff top");
            staysOnGround(ground, plan);
        }
    }

    @Test
    void wallCountsAsNoGround() {
        double[][] ground = ground(64);
        // A wall three blocks high, north of the cow.
        for (int i = 0; i < N; i++) ground[i][R + 1] = 67;
        MobPaths.Plan plan = MobPaths.choose(ground, X, Y, Z, BX, BZ, WIDTH, 7);
        staysOnGround(ground, plan);
    }

    @Test
    void narrowLedgeGetsAWalkToAndFro() {
        double[][] ground = ground(Double.NaN);
        // One block wide, running north-south through the cow.
        Arrays.fill(ground[R], 64);
        MobPaths.Plan plan = MobPaths.choose(ground, X, Y, Z, BX, BZ, WIDTH, 3);
        assertInstanceOf(MobPaths.Pace.class, plan);
        staysOnGround(ground, plan);
    }

    @Test
    void pillarLeavesItStanding() {
        double[][] ground = ground(Double.NaN);
        ground[R][R] = 64;
        assertInstanceOf(MobPaths.Stand.class, MobPaths.choose(ground, X, Y, Z, BX, BZ, WIDTH, 5));
    }
}
