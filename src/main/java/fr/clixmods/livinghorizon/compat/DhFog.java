package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import org.jspecify.annotations.Nullable;

/**
 * Distant Horizons' far fog, for the world's entities. Distant Horizons takes the game's fog away
 * (its start and end pushed out to 4×10¹⁴ blocks) and fogs its own terrain in a pass of its own:
 * a far mob, drawn by the game, then has no fog at all and stands out sharp against the
 * fogged hills around it.
 *
 * <p>Its fog's thickness at a distance {@code d}, its render distance {@code R} in blocks
 * ({@code fog/frag.fsh}): {@code x = d / R}, {@code u = (x - start) / (end - start)}, then
 * {@code min + (max - min)} times {@code u} kept to 0..1 (linear), {@code 1 - e^(-u·density)}
 * (exponential) or {@code 1 - e^(-(u·density)²)} (exponential squared), {@code u} at least 0;
 * across the ground, or in every direction when its height fog mixes spherically.
 *
 * <p>The game's fog on an entity is the larger of two straight ramps, one over the distance in
 * every direction, one across the ground, times the fog colour's alpha. So: the alpha is the
 * thickest fog, one ramp a floor at the thinnest ({@code min}, flat over any distance seen), the
 * other the slope, exactly the linear one and as close as a straight line gets to the
 * exponential ones ({@link #fit}). The fog colour stays the world's: with Distant Horizons'
 * <i>sky colour</i> fog, far mobs fade to the world's fog colour instead. Its height fog is
 * left out.
 */
public final class DhFog {
    /** Distant Horizons' settings, as read: its render distance in blocks and its far fog. */
    record Settings(double radius, float start, float end, float min, float max, int falloff, float density,
                    boolean spherical) {
    }

    /**
     * The fog the game is to give the entities: its two ramps in blocks (in every direction, then
     * across the ground) and the factor on its colour's alpha.
     */
    public record Ramps(float environmentalStart, float environmentalEnd, float renderDistanceStart,
                        float renderDistanceEnd, float alpha) {
    }

    static final int LINEAR = 0, EXPONENTIAL = 1, EXPONENTIAL_SQUARED = 2;
    /** Blocks: a ramp from this far behind, ending as far ahead, is flat over any distance seen. */
    private static final float FLAT = 1e7f;
    /** A ramp that never starts. */
    private static final float NEVER = 1e10f;
    /** Relative distances fitted over, past which a far mob is past Distant Horizons' terrain anyway. */
    private static final double FIT_TO = 2.0;
    private static final int SAMPLES = 256;

    private static long readAt;
    private static @Nullable Settings read;
    private static @Nullable Settings fitted;
    private static @Nullable Ramps ramps;
    private static boolean broken;

    private DhFog() {
    }

    /** The fog for this frame, from Distant Horizons' settings (read again every second); null where it draws none. */
    static @Nullable Ramps now() {
        long time = System.nanoTime();
        if (time - readAt > 1_000_000_000L || readAt == 0) {
            readAt = time;
            read = settings();
        }
        Settings settings = read;
        if (settings == null) return null;
        if (!settings.equals(fitted)) {
            fitted = settings;
            ramps = fit(settings);
        }
        return ramps;
    }

    /** The ramps closest to Distant Horizons' fog, in blocks. */
    static Ramps fit(Settings s) {
        float alpha = Math.min(1, Math.max(s.max(), s.min()));
        if (alpha <= 0) return new Ramps(NEVER, 2 * NEVER, NEVER, 2 * NEVER, 0);
        double floor = Math.min(1, Math.max(0, s.min() / alpha));
        double[] slope = slope(s, alpha, floor);
        float rampStart = (float) (slope[0] * s.radius()), rampEnd = (float) (slope[1] * s.radius());
        float flatStart = floor <= 0 ? NEVER : -FLAT;
        float flatEnd = floor <= 0 ? 2 * NEVER : (float) (FLAT * (1 - floor) / floor);
        if (floor >= 1) flatEnd = 1;
        return s.spherical()
                ? new Ramps(rampStart, rampEnd, flatStart, flatEnd, alpha)
                : new Ramps(flatStart, flatEnd, rampStart, rampEnd, alpha);
    }

    /**
     * The sloped ramp, from where to where in fractions of the render distance: exact for a linear
     * fog, the least squares over {@link #FIT_TO} render distances for the others.
     */
    static double[] slope(Settings s, float alpha, double floor) {
        double length = Math.max(1e-4, s.end() - s.start());
        if (floor >= 1) return new double[]{NEVER, 2 * NEVER};
        if (s.falloff() == LINEAR) {
            // Through (start, floor) and (end, 1).
            return new double[]{s.start() - floor * length / (1 - floor), s.start() + length};
        }
        double[] best = {0, 1};
        double bestError = Double.MAX_VALUE;
        // Coarse, then fine around the best.
        for (double step : new double[]{0.02, 0.001}) {
            double fromA = step == 0.02 ? -2 : best[0] - 0.03, toA = step == 0.02 ? 3 : best[0] + 0.03;
            double fromWidth = step == 0.02 ? 0.02 : Math.max(step, best[1] - best[0] - 0.03);
            double toWidth = step == 0.02 ? 6 : best[1] - best[0] + 0.03;
            double[] around = best.clone();
            for (double a = fromA; a <= toA; a += step) {
                for (double width = fromWidth; width <= toWidth; width += step) {
                    double error = error(s, alpha, floor, a, a + width, bestError);
                    if (error < bestError) {
                        bestError = error;
                        around = new double[]{a, a + width};
                    }
                }
            }
            best = around;
        }
        return best;
    }

    private static double error(Settings s, float alpha, double floor, double a, double b, double stopAt) {
        double sum = 0;
        for (int i = 0; i <= SAMPLES; i++) {
            double x = FIT_TO * i / SAMPLES;
            double ramp = Math.min(1, Math.max(0, (x - a) / (b - a)));
            double off = Math.max(floor, ramp) - thickness(s, x) / alpha;
            sum += off * off;
            if (sum >= stopAt) return sum;
        }
        return sum;
    }

    /** Distant Horizons' fog at a distance, in render distances: {@code getFarFogThickness}, kept to 0..1. */
    static double thickness(Settings s, double x) {
        double length = Math.max(1e-4, s.end() - s.start());
        double u = (x - s.start()) / length;
        double range = s.max() - s.min();
        double t = switch (s.falloff()) {
            case LINEAR -> s.min() + range * Math.min(1, Math.max(0, u));
            case EXPONENTIAL -> s.min() + range - range / Math.exp(Math.max(0, u) * s.density());
            default -> {
                double v = Math.max(0, u) * s.density();
                yield s.min() + range - range / Math.exp(v * v);
            }
        };
        return Math.min(1, Math.max(0, t));
    }

    /** Distant Horizons' far fog settings through its API; null without it, its rendering or its fog. */
    private static @Nullable Settings settings() {
        if (broken || !Platform.isModLoaded("distanthorizons")) return null;
        String api = DhLink.API, config = api + "interfaces.config.", client = config + "client.";
        try {
            Object configs = Class.forName(api + "DhApi$Delayed").getField("configs").get(null);
            if (configs == null) return null;
            Object graphics = call(config + "IDhApiConfig", configs, "graphics");
            String graphicsType = client + "IDhApiGraphicsConfig";
            if (!(Boolean) value(call(graphicsType, graphics, "renderingEnabled"))) return null;
            double radius = ((Number) value(call(graphicsType, graphics, "chunkRenderDistance"))).doubleValue() * 16;
            Object fog = call(graphicsType, graphics, "fog");
            String fogType = client + "IDhApiFogConfig";
            if (!(Boolean) value(call(fogType, fog, "enableDhFog"))) return null;
            if ("FOG_DISABLED".equals(String.valueOf(value(call(fogType, fog, "drawMode"))))) return null;
            Object far = call(fogType, fog, "farFog");
            String farType = client + "IDhApiFarFogConfig";
            Object falloff = value(call(farType, far, "farFogFalloff"));
            boolean spherical;
            try {
                Object height = call(fogType, fog, "heightFog");
                spherical = "SPHERICAL".equals(String.valueOf(value(call(client + "IDhApiHeightFogConfig", height, "heightFogMixMode"))));
            } catch (ReflectiveOperationException | RuntimeException e) {
                spherical = false;
            }
            return new Settings(radius,
                    number(value(call(farType, far, "farFogStartDistance"))),
                    number(value(call(farType, far, "farFogEndDistance"))),
                    number(value(call(farType, far, "farFogMinThickness"))),
                    number(value(call(farType, far, "farFogMaxThickness"))),
                    switch (String.valueOf(falloff)) {
                        case "LINEAR" -> LINEAR;
                        case "EXPONENTIAL" -> EXPONENTIAL;
                        default -> EXPONENTIAL_SQUARED;
                    },
                    number(value(call(farType, far, "farFogDensity"))),
                    spherical);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            LivingHorizonClient.LOGGER.warn("Distant Horizons found, but not its fog settings: distant mobs get no fog over its terrain", e);
            return null;
        }
    }

    private static float number(Object value) {
        return ((Number) value).floatValue();
    }

    /** A method of no arguments of one of Distant Horizons' API interfaces. */
    private static Object call(String type, Object on, String method) throws ReflectiveOperationException {
        return Class.forName(type).getMethod(method).invoke(on);
    }

    /** What one of its settings is set to. */
    private static Object value(Object setting) throws ReflectiveOperationException {
        return call(DhLink.API + "interfaces.config.IDhApiConfigValue", setting, "getValue");
    }
}
