package fr.clixmods.livinghorizon.render.impostor;

/**
 * How an impostor sheet is laid out, and which picture of it is shown.
 *
 * <p>A sheet holds {@link #VIEWS} pictures of one figure, turned a {@code 360 / VIEWS}
 * degrees step further each, side by side in one row of tiles. Picture 0 is the figure
 * seen from in front, picture 1 from its left-front, and so on round to its right-front.
 */
public final class ImpostorViews {
    /** Pictures per sheet: the figure seen from every 45 degrees round it. */
    public static final int VIEWS = 8;

    /** Pixels along one side of a tile. */
    public static final int TILE = 64;

    /**
     * Degrees past the edge of its picture a figure must turn before the next one is shown:
     * a figure whose yaw wavers round an edge would otherwise flick between two pictures.
     */
    static final double STICK = 8.0;

    private ImpostorViews() {
    }

    /**
     * Which picture shows a figure to a camera.
     *
     * @param figureYaw the way the figure faces, in the game's yaw degrees (0 south, 90 west)
     * @param dx        from the figure to the camera, east-west
     * @param dz        from the figure to the camera, north-south
     */
    public static int view(double figureYaw, double dx, double dz) {
        return view(figureYaw, dx, dz, -1);
    }

    /**
     * The same, keeping {@code previous} (the picture shown last frame, or -1) until the
     * figure is clearly closer to another one.
     */
    public static int view(double figureYaw, double dx, double dz, int previous) {
        double relative = relative(figureYaw, dx, dz);
        if (previous >= 0 && previous < VIEWS) {
            double off = Math.abs(Math.IEEEremainder(relative - previous * (360.0 / VIEWS), 360.0));
            if (off <= 180.0 / VIEWS + STICK) return previous;
        }
        return (int) Math.floor((relative + 180.0 / VIEWS) / (360.0 / VIEWS)) % VIEWS;
    }

    /** Degrees round the figure's front where the camera stands, 0 to 360. */
    private static double relative(double figureYaw, double dx, double dz) {
        // The yaw of the line from the figure to the camera, in the same convention.
        double toCamera = Math.toDegrees(Math.atan2(-dx, dz));
        return Math.floorMod(Math.round((toCamera - figureYaw) * 1000), 360_000L) / 1000.0;
    }

    /**
     * Blocks along one side of a tile: wide enough for the whole figure seen side-on, and
     * tall enough for what stands out of its bounding box, with a margin. A cow is longer
     * than its box is wide, a chicken's head is above its box.
     */
    public static double worldSize(double height, double width) {
        return Math.max(height * 1.5, width * 1.9);
    }

    /** Left edge of a picture in its sheet, as a fraction of the sheet's width. */
    public static float u0(int view) {
        return view / (float) VIEWS;
    }

    /** Right edge of a picture in its sheet. */
    public static float u1(int view) {
        return (view + 1) / (float) VIEWS;
    }
}
