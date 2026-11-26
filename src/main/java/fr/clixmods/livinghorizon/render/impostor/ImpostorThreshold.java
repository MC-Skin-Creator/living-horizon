package fr.clixmods.livinghorizon.render.impostor;

/**
 * When a figure is drawn as a picture of itself instead of its model: past the impostor
 * distance, or smaller on screen than the impostor size, each of which may be off (0).
 * Asks the game nothing.
 *
 * <p>A figure already pictured keeps its picture until it is a little inside both: 5% closer
 * than the distance and 5% taller than the size. None of it blinks between a model and a picture.
 *
 * <p>A picture is never blown up far past the pixels it holds: a huge mob just past the
 * distance (an Ice and Fire dragon twenty blocks long) keeps its model until it is small enough.
 *
 * <p>A model nearing its picture turns to the way the picture will show it ({@link #approach}),
 * so that the picture takes over from a figure already standing as it does.
 */
public final class ImpostorThreshold {
    /** How far inside a threshold a pictured figure must come to get its model back. */
    public static final double RETURN = 0.95;

    /** How many times larger than its pixels a picture may be shown. */
    public static final double MAX_ENLARGED = 2;

    private ImpostorThreshold() {
    }

    /**
     * Whether a picture would be shown too large for what it holds, and its model drawn instead.
     * A pictured figure keeps its picture until it is 5% larger than that.
     *
     * @param tilePixels a tile's height on screen, in pixels
     * @param heldPixels the pixels along a side of a tile
     * @param pictured   the figure was a picture last frame
     */
    public static boolean enlarged(double tilePixels, int heldPixels, boolean pictured) {
        return tilePixels > heldPixels * MAX_ENLARGED / (pictured ? RETURN : 1);
    }

    /**
     * @param distance     blocks from the eye to the figure
     * @param pixels       the figure's height (or width, the larger) on screen, in pixels
     * @param pictured     the figure was a picture last frame
     * @param fromDistance blocks past which a figure is a picture; 0 for none
     * @param belowPixels  pixels under which a figure is a picture; 0 for none
     */
    public static boolean pictured(double distance, double pixels, boolean pictured, int fromDistance, int belowPixels) {
        if (distance < 1e-3) return false;
        if (fromDistance > 0 && distance > (pictured ? fromDistance * RETURN : fromDistance)) return true;
        return belowPixels > 0 && pixels < (pictured ? belowPixels / RETURN : belowPixels);
    }

    /**
     * How far a model has come towards its picture, 0 to 1: 0 until it is {@code span} blocks from
     * where a picture would come back to its model ({@link #RETURN}), 1 from there on, so that
     * neither switch moves it; in between, eased. Where the size decides, the distance at which it
     * would is worked out from how its size on screen falls with the distance. The same number of
     * blocks at any distance: a share of the thresholds turned a figure over 20 blocks at 128, and
     * over two at 16, at once.
     *
     * @param distance     blocks from the eye to the figure
     * @param pixels       the figure's height (or width, the larger) on screen, in pixels
     * @param fromDistance blocks past which a figure is a picture; 0 for none
     * @param belowPixels  pixels under which a figure is a picture; 0 for none
     * @param span         blocks over which it turns ({@code FarConfig.impostorTurnDistance})
     */
    public static double approach(double distance, double pixels, int fromDistance, int belowPixels, double span) {
        if (distance < 1e-3) return 0;
        double back = Double.MAX_VALUE;
        if (fromDistance > 0) back = fromDistance * RETURN;
        // Its size on screen falls as one over the distance: it comes back to its model where it would be that tall.
        if (belowPixels > 0 && pixels > 0) back = Math.min(back, distance * pixels * RETURN / belowPixels);
        if (back == Double.MAX_VALUE) return 0;
        if (span <= 0) return distance >= back ? 1 : 0;
        return ease(distance, back - span, back);
    }

    /** 0 at {@code from}, 1 at {@code to}, a smooth step in between. */
    private static double ease(double value, double from, double to) {
        double t = Math.min(1, Math.max(0, (value - from) / (to - from)));
        return t * t * (3 - 2 * t);
    }
}
