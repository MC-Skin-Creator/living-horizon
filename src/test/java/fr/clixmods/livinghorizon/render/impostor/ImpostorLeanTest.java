package fr.clixmods.livinghorizon.render.impostor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpostorLeanTest {
    /** An upright quad two blocks wide and tall, its feet at (x, y, z) from the camera, turned to it. */
    private static float[] quad(double x, double z) {
        double across = Math.sqrt(x * x + z * z);
        float rightX = (float) (-z / across), rightZ = (float) (x / across);
        return new float[]{-rightX, -0.1f, -rightZ, rightX, -0.1f, rightZ, rightX, 1.9f, rightZ, -rightX, 1.9f, -rightZ};
    }

    /** The normal Iris works out for a quad: its diagonals crossed. */
    private static double[] irisNormal(double ox, double oy, double oz, float[] c) {
        double ax = c[6] - c[0], ay = c[7] - c[1], az = c[8] - c[2];
        double bx = c[9] - c[3], by = c[10] - c[4], bz = c[11] - c[5];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return new double[]{nx / length, ny / length, nz / length};
    }

    @Test
    void cornersStayWhereTheCameraSeesThem() {
        double[][] places = {{0, 0, 120}, {80, -30, 60}, {-200, 40, -15}, {5, -90, 3}, {300, 2, 300}};
        for (double[] at : places) {
            float[] before = quad(at[0], at[2]);
            float[] after = before.clone();
            ImpostorLean.lean(at[0], at[1], at[2], at[0], at[1] + 0.95, at[2], after);
            for (int i = 0; i < before.length; i += 3) {
                double[] p = {at[0] + before[i], at[1] + before[i + 1], at[2] + before[i + 2]};
                double[] q = {at[0] + after[i], at[1] + after[i + 1], at[2] + after[i + 2]};
                double lp = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
                double lq = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
                for (int k = 0; k < 3; k++) assertEquals(p[k] / lp, q[k] / lq, 1e-5, "direction of corner " + i / 3);
                // A little nearer or farther, never far off.
                assertTrue(Math.abs(lq - lp) < 2.5, "corner " + i / 3 + " moved " + (lq - lp));
            }
        }
    }

    @Test
    void theQuadFacesTheCameraAndUp() {
        double[][] places = {{0, 0, 120}, {80, -30, 60}, {-200, 40, -15}, {300, 2, 300}};
        for (double[] at : places) {
            float[] corners = quad(at[0], at[2]);
            ImpostorLean.lean(at[0], at[1], at[2], at[0], at[1] + 0.95, at[2], corners);
            double[] n = irisNormal(at[0], at[1], at[2], corners);
            double back = -(n[0] * at[0] + n[1] * at[1] + n[2] * at[2]);
            assertTrue(back > 0, "faces away from the camera");
            assertTrue(n[1] > 0.3, "not turned up: " + n[1]);
        }
    }

    @Test
    void seenLevelItLeansHalfWayUp() {
        double[] n = ImpostorLean.normal(0, 0, 100);
        assertEquals(Math.sin(Math.toRadians(45)), n[1], 1e-9);
        assertEquals(-Math.cos(Math.toRadians(45)), n[2], 1e-9);
    }

    @Test
    void seenFromBelowItLeansNoFurtherThanTheLimit() {
        double[] n = ImpostorLean.normal(0, 100, 100);
        // The way back from the figure points down at 45 degrees; the tilt stops at the limit.
        double toCamera = Math.toDegrees(Math.acos(-(n[1] * 100 + n[2] * 100) / Math.sqrt(2 * 100 * 100)));
        assertEquals(ImpostorLean.MAX_TILT, toCamera, 1e-6);
    }

    @Test
    void seenFromStraightAboveItFacesUp() {
        double[] n = ImpostorLean.normal(0, -100, 0);
        assertEquals(1, n[1], 1e-9);
    }
}
