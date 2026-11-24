package fr.clixmods.livinghorizon.ambient;

import fr.clixmods.livinghorizon.compat.LodWorld;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Which birds past the render distance no far terrain can hide. Anything drawn there has the
 * far terrain's depth worked on that frame ({@code FarDepth.needed}), around it: with birds
 * all over the sky, that was most of the picture, every frame, for birds nothing could hide.
 *
 * <p>Once a second the far world is asked, off the game's thread ({@link LodWorld#blocked}),
 * whether its terrain stands on the line from the eye to a point {@link #BELOW} blocks under
 * each such bird. Terrain is a height field: a line clear to that point is clear to the bird
 * too, with room for the bird to move, or the eye, until the next answer. A bird found clear
 * keeps the answer ({@link Ambience.Flyer#clear}) while the camera stays within
 * {@link #MOVED} blocks of where it was asked from, for {@link #KEPT} at most; without an
 * answer, or without a far world to ask, it is drawn as before.
 */
final class OpenSky {
    /** Ticks between two questions. */
    private static final int EVERY = 20;
    /** Blocks under a bird the line is asked to. */
    private static final double BELOW = 8;
    /** Blocks the camera may move from where an answer was asked and keep it. */
    private static final double MOVED = 6;
    /** Nanoseconds an answer is kept. */
    private static final long KEPT = 3_000_000_000L;

    /** The line from {@code (x, y, z)} to the bird was clear, asked at {@code nanos}. */
    record Clear(double x, double y, double z, long nanos) {
        /** Whether it still holds for a camera at {@code camera}, at {@code now}. */
        boolean holds(Vec3 camera, long now) {
            double dx = camera.x - x, dy = camera.y - y, dz = camera.z - z;
            return now - nanos < KEPT && dx * dx + dy * dy + dz * dz < MOVED * MOVED;
        }
    }

    private int countdown;
    /** A question is out: the next waits for its answer, however slow the far world reads. */
    private final AtomicBoolean asking = new AtomicBoolean();

    /**
     * Asks about the birds farther than {@code within} blocks from {@code eye}; the others
     * forget their answer.
     */
    void tick(List<Ambience.Flyer> flyers, Vec3 eye, double within) {
        if (--countdown > 0 || asking.get()) return;
        countdown = EVERY;
        List<Ambience.Flyer> asked = new ArrayList<>();
        for (Ambience.Flyer flyer : flyers) {
            double dx = flyer.x - eye.x, dy = flyer.y - eye.y, dz = flyer.z - eye.z;
            if (flyer.kind == Ambience.Kind.SILHOUETTE && dx * dx + dy * dy + dz * dz > within * within) {
                asked.add(flyer);
            } else {
                flyer.clear = null;
            }
        }
        if (asked.isEmpty()) return;
        double[] xs = new double[asked.size()], ys = new double[asked.size()], zs = new double[asked.size()];
        for (int i = 0; i < xs.length; i++) {
            Ambience.Flyer flyer = asked.get(i);
            xs[i] = flyer.x;
            ys[i] = flyer.y - BELOW - flyer.span * flyer.scale;
            zs[i] = flyer.z;
        }
        long now = System.nanoTime();
        asking.set(true);
        LodWorld.blocked(eye, xs, ys, zs, 1).whenComplete((blocked, error) -> {
            asking.set(false);
            for (int i = 0; i < xs.length; i++) {
                boolean clear = error == null && blocked != null && !blocked[i];
                asked.get(i).clear = clear ? new Clear(eye.x, eye.y, eye.z, now) : null;
            }
        });
    }
}
