package fr.clixmods.livinghorizon;

/**
 * The game's {@code Mth.clamp}, which 26.4 removed: from there the sources' calls to it come
 * here (a replacement in {@code stonecutter.gradle.kts}). Unlike {@code Math.clamp} it never
 * throws when the lower bound is above the upper one: the value below the lower bound gives
 * the lower bound, any other the smaller of the value and the upper bound.
 */
public final class Clamp {
    private Clamp() {
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    public static long clamp(long value, long min, long max) {
        return value < min ? min : Math.min(value, max);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}
