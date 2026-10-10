package fr.clixmods.livinghorizon.render.impostor;

/**
 * Leans an impostor's quad back, without changing what it looks like on screen.
 *
 * <p>With a shader pack, Iris throws away the normal a quad is given and works out its own
 * from the corners. An upright picture turned to the camera is then lit as a wall facing
 * the player: dark whenever the sun is not behind them, though the figure it shows has a
 * back, a head and a top in the sun. Each corner is moved along its own line of sight onto
 * a plane leaning back between facing the camera and facing up, the way the visible half of
 * a model faces on average: the same pixels on screen, a little nearer or farther, and the
 * normal Iris finds is that plane's.
 */
public final class ImpostorLean {
    /** Degrees the plane leans back from facing the camera at most: past it, its corners run far along their lines of sight. */
    static final double MAX_TILT = 50.0;

    private ImpostorLean() {
    }

    /**
     * The normal of the plane the corners are moved onto: the camera's way back from the
     * figure, turned half way towards straight up, by {@link #MAX_TILT} at most.
     *
     * @param cx where the plane goes through, from the camera
     */
    static double[] normal(double cx, double cy, double cz) {
        double length = Math.sqrt(cx * cx + cy * cy + cz * cz);
        // Towards the camera.
        double bx = -cx / length, by = -cy / length, bz = -cz / length;
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, by)));
        if (angle < 1e-4) return new double[]{0, 1, 0};
        double tilt = Math.min(angle / 2, Math.toRadians(MAX_TILT));
        // Up, without its part along the way back: the direction to turn towards.
        double ux = -bx * by, uy = 1 - by * by, uz = -bz * by;
        double u = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (u < 1e-9) return new double[]{bx, by, bz};
        double cos = Math.cos(tilt), sin = Math.sin(tilt) / u;
        return new double[]{bx * cos + ux * sin, by * cos + uy * sin, bz * cos + uz * sin};
    }

    /**
     * Moves the corners of a quad, in place, along their lines of sight onto the plane
     * through {@code (cx, cy, cz)} with the normal {@link #normal} gives there.
     *
     * @param ox      the quad's origin, from the camera
     * @param corners x, y, z of each corner from the origin, rewritten
     */
    static void lean(double ox, double oy, double oz, double cx, double cy, double cz, float[] corners) {
        double[] n = normal(cx, cy, cz);
        double plane = cx * n[0] + cy * n[1] + cz * n[2];
        for (int i = 0; i < corners.length; i += 3) {
            double px = ox + corners[i], py = oy + corners[i + 1], pz = oz + corners[i + 2];
            double along = px * n[0] + py * n[1] + pz * n[2];
            // Seen edge-on, which the tilt limit keeps away from: left where it is.
            if (Math.abs(along) < 1e-6) continue;
            double t = plane / along;
            if (t <= 0) continue;
            corners[i] = (float) (px * t - ox);
            corners[i + 1] = (float) (py * t - oy);
            corners[i + 2] = (float) (pz * t - oz);
        }
    }
}
