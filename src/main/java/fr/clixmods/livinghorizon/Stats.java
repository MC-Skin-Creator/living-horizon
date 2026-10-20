package fr.clixmods.livinghorizon;

/**
 * What the mod costs, for {@code /livinghorizon}: smoothed time per tick and per frame,
 * and how many puppets were drawn or skipped in the last frame. The last raw times are
 * kept too, for the {@code perf} game test to take its own averages and maxima.
 */
public final class Stats {
    private static double tickMillis, extractMillis;
    private static long lastTick, lastExtract;
    private static int drawn, skipped;

    private Stats() {
    }

    public static void tick(long nanos) {
        tickMillis = smooth(tickMillis, nanos);
        lastTick = nanos;
    }

    public static void extract(long nanos, int drawnNow, int skippedNow) {
        extractMillis = smooth(extractMillis, nanos);
        lastExtract = nanos;
        drawn = drawnNow;
        skipped = skippedNow;
    }

    private static double smooth(double average, long nanos) {
        return average * 0.95 + nanos / 1_000_000.0 * 0.05;
    }

    public static String tickMillis() {
        return String.format(java.util.Locale.ROOT, "%.2f", tickMillis);
    }

    public static String extractMillis() {
        return String.format(java.util.Locale.ROOT, "%.2f", extractMillis);
    }

    /** Nanoseconds the mod's last client tick took, not smoothed. */
    public static long lastTickNanos() {
        return lastTick;
    }

    /** Nanoseconds the last frame's extraction took, not smoothed. */
    public static long lastExtractNanos() {
        return lastExtract;
    }

    public static int drawn() {
        return drawn;
    }

    public static int skipped() {
        return skipped;
    }
}
