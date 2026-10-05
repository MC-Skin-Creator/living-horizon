package fr.clixmods.farfarplayer.track;

import fr.clixmods.farfarplayer.compat.VoxyWorld;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Where a remembered mob may walk without falling or going through a wall, and the loop
 * that fits there.
 *
 * <p>The ground around the mob is read first: the height of every column within
 * {@link #RADIUS} blocks, from the chunks loaded here or else from Voxy's world. A column
 * is walkable when its ground is at the mob's own height - not a step down off a cliff,
 * not a wall, not water. Then the widest loop that stays on walkable ground is chosen:
 * a circle, else a walk to and fro, else standing where it is.
 */
public final class MobPaths {
    /** Blocks around the mob that are read. */
    static final int RADIUS = 4;
    static final int SIZE = RADIUS * 2 + 1;
    /** Blocks of difference with the mob's own height that still count as level ground. */
    private static final double LEVEL = 0.6;

    /** How a mob moves when it does. */
    public sealed interface Plan permits Circle, Pace, Stand {
    }

    /** Around a circle through the spot: {@code start} is where on the circle the spot is. */
    public record Circle(double radius, double start, int turn) implements Plan {
    }

    /** Out along {@code angle} (radians, the way {@code Math.sin}, {@code Math.cos} point) and back. */
    public record Pace(double angle, double length) implements Plan {
    }

    /** Nowhere to go: turning on the spot, looking around. */
    public record Stand() implements Plan {
    }

    private MobPaths() {
    }

    /**
     * Reads the ground around a mob and chooses its loop. Loaded columns are read at once;
     * the others come from Voxy's world, off the thread, so the answer may come later.
     */
    public static CompletableFuture<Plan> plan(ClientLevel level, double x, double y, double z, double width, long seed) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        double[][] ground = new double[SIZE][SIZE];
        CompletableFuture<?>[] pending = new CompletableFuture<?>[SIZE * SIZE];
        int waiting = 0;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                int cx = bx + i - RADIUS, cz = bz + j - RADIUS;
                ground[i][j] = Double.NaN;
                if (level.hasChunk(cx >> 4, cz >> 4)) {
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
                    boolean wet = !level.getFluidState(new BlockPos(cx, top - 1, cz)).isEmpty();
                    if (!wet) ground[i][j] = top;
                } else {
                    int fi = i, fj = j;
                    pending[waiting++] = VoxyWorld.surface(cx, cz).thenAccept(surface -> surface
                            .filter(s -> !s.water())
                            .ifPresent(s -> ground[fi][fj] = s.y() + 1));
                }
            }
        }
        return CompletableFuture.allOf(Arrays.copyOf(pending, waiting))
                .thenApply(done -> choose(ground, x, y, z, bx, bz, width, seed));
    }

    /**
     * The widest loop that stays on level ground. {@code ground[i][j]} is the height of the
     * column {@code (bx + i - RADIUS, bz + j - RADIUS)}, NaN where unknown or not walkable.
     */
    static Plan choose(double[][] ground, double x, double y, double z, int bx, int bz, double width, long seed) {
        int turnBias = (int) Math.floorMod(seed, 8L);
        int turn = ((seed >> 7) & 1) == 0 ? 1 : -1;
        // The widest circle still fits, with the mob's width, inside what was read.
        for (double radius : new double[]{1.8, 1.4, 1.0}) {
            for (int k = 0; k < 8; k++) {
                double start = (k + turnBias) * Math.PI / 4;
                double cx = x - radius * Math.cos(start), cz = z - radius * Math.sin(start);
                boolean fits = true;
                for (int s = 0; s < 24 && fits; s++) {
                    double a = s * Math.PI * 2 / 24;
                    fits = walkable(ground, cx + radius * Math.cos(a), y, cz + radius * Math.sin(a), bx, bz, width);
                }
                if (fits) return new Circle(radius, start, turn);
            }
        }
        for (double length : new double[]{3.0, 2.0, 1.2}) {
            for (int k = 0; k < 8; k++) {
                double angle = (k + turnBias) * Math.PI / 4;
                boolean fits = true;
                for (double d = 0; d <= length && fits; d += 0.4) {
                    fits = walkable(ground, x + Math.sin(angle) * d, y, z + Math.cos(angle) * d, bx, bz, width);
                }
                if (fits) return new Pace(angle, length);
            }
        }
        return new Stand();
    }

    /** Level ground under a point, and under its sides as wide as the mob. */
    static boolean walkable(double[][] ground, double x, double y, double z, int bx, int bz, double width) {
        double half = Math.max(0.2, width / 2 - 0.1);
        return level(ground, x, y, z, bx, bz) && level(ground, x - half, y, z, bx, bz) && level(ground, x + half, y, z, bx, bz)
                && level(ground, x, y, z - half, bx, bz) && level(ground, x, y, z + half, bx, bz);
    }

    private static boolean level(double[][] ground, double x, double y, double z, int bx, int bz) {
        int i = Mth.floor(x) - bx + RADIUS, j = Mth.floor(z) - bz + RADIUS;
        if (i < 0 || j < 0 || i >= SIZE || j >= SIZE) return false;
        double h = ground[i][j];
        return !Double.isNaN(h) && Math.abs(h - y) <= LEVEL;
    }
}
