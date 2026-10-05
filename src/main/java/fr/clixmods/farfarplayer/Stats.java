package fr.clixmods.farfarplayer;

/**
 * What the mod costs, for {@code /farfarplayer}: smoothed time per tick and per frame,
 * and how many puppets were drawn or skipped in the last frame.
 */
public final class Stats {
    private static double tickMillis, extractMillis;
    private static int drawn, skipped;

    private Stats() {
    }

    public static void tick(long nanos) {
        tickMillis = smooth(tickMillis, nanos);
    }

    public static void extract(long nanos, int drawnNow, int skippedNow) {
        extractMillis = smooth(extractMillis, nanos);
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

    public static int drawn() {
        return drawn;
    }

    public static int skipped() {
        return skipped;
    }
}
