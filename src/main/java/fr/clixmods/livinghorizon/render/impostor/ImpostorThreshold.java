package fr.clixmods.livinghorizon.render.impostor;

/**
 * When a figure is drawn as a picture of itself instead of its model: past the impostor
 * distance, or smaller on screen than the impostor size. Asks the game nothing.
 *
 * <p>A figure already pictured keeps its picture until it is a little inside both: 5% closer
 * than the distance and 5% taller than the size. None of it blinks between a model and a picture.
 */
public final class ImpostorThreshold {
    /** How far inside a threshold a pictured figure must come to get its model back. */
    public static final double RETURN = 0.95;

    private ImpostorThreshold() {
    }

    /**
     * @param distance     blocks from the eye to the figure
     * @param pixels       the figure's height (or width, the larger) on screen, in pixels
     * @param pictured     the figure was a picture last frame
     * @param fromDistance blocks past which a figure is a picture
     * @param belowPixels  pixels under which a figure is a picture; 0 for none
     */
    public static boolean pictured(double distance, double pixels, boolean pictured, int fromDistance, int belowPixels) {
        if (distance < 1e-3) return false;
        if (distance > (pictured ? fromDistance * RETURN : fromDistance)) return true;
        return belowPixels > 0 && pixels < (pictured ? belowPixels / RETURN : belowPixels);
    }
}
