package fr.clixmods.livinghorizon.render.impostor;

/**
 * The stride of a walking figure in its pictures: which picture of its walk a copy shows, and
 * how each one is baked. Asks the game nothing, so {@code ImpostorWalkTest} checks it alone.
 *
 * <p>The game's models swing their legs and arms by {@code cos(position * 0.6662)}, times the
 * walk's speed: one stride every {@link #PERIOD} of walk position. It is cut into
 * {@link #PHASES} equal steps. Half way between two ends of the stride the legs cross straight
 * down, where they stand at rest: those steps show the resting picture every figure already has
 * (frame 0), and only the others are baked, {@link #FRAMES} of them. With four steps, a walk is
 * rest, one leg forward, rest, the other leg forward: two pictures more per look.
 */
public final class ImpostorWalk {
    /** Walk position of one stride: the period of the models' {@code cos(position * 0.6662)}. */
    public static final float PERIOD = (float) (2 * Math.PI / 0.6662);

    /** Steps a stride is cut into: a multiple of 4, so that two of them are the legs crossing. */
    public static final int PHASES = 4;

    /** Pictures baked per look for its walk, besides the resting one: every step but the two crossings. */
    public static final int FRAMES = PHASES - 2;

    /**
     * How fast the walk is baked: about a mob walking about (the copies' legs go at four times
     * the blocks they move a tick, a cow strolling at an eighth of a block). At the game's full
     * 1, a cow's legs reach out at 80 degrees, which no mob strolling shows.
     */
    public static final float BAKED_SPEED = 0.5f;

    /** Below this walk speed a figure is shown at rest: its legs barely move, and the picture would wobble. */
    public static final float WALKING = 0.12f;

    private ImpostorWalk() {
    }

    /** The step of the stride at walk {@code position}, 0 to {@link #PHASES} - 1, the nearest. */
    static int phase(float position) {
        return Math.floorMod(Math.round(position / PERIOD * PHASES), PHASES);
    }

    /**
     * The picture of a figure at walk {@code position} going at {@code speed}: 0 for its resting
     * one (standing, or its legs crossing), 1 to {@link #FRAMES} for one of its walk.
     */
    public static int frame(float position, float speed) {
        if (!(speed >= WALKING) || !Float.isFinite(position)) return 0;
        return frame(phase(position));
    }

    /**
     * The picture of a figure {@code cycles} of the way through a movement that repeats (a boat's
     * oars: a turn of 2 pi), whole turns left out: the nearest step, as for a stride.
     */
    public static int frameAt(double cycles) {
        if (!Double.isFinite(cycles)) return 0;
        return frame((int) Math.floorMod(Math.round(cycles * PHASES), (long) PHASES));
    }

    /** How far through its movement picture {@code frame} (1 to {@link #FRAMES}) is baked, 0 to 1. */
    public static double cycle(int frame) {
        return position(frame) / PERIOD;
    }

    /** The picture of a step: 0 where the legs cross, otherwise the steps in order, the crossings skipped. */
    public static int frame(int phase) {
        int half = PHASES / 2, quarter = PHASES / 4;
        int within = phase % half;
        if (within == quarter) return 0;
        // In each half of the stride, the steps before its crossing, then those after it.
        return phase / half * (half - 1) + (within < quarter ? within : within - 1) + 1;
    }

    /** The walk position picture {@code frame} (1 to {@link #FRAMES}) is baked at. */
    public static float position(int frame) {
        for (int phase = 0; phase < PHASES; phase++) {
            if (frame(phase) == frame) return phase * PERIOD / PHASES;
        }
        throw new IllegalArgumentException("No walk picture " + frame);
    }
}
