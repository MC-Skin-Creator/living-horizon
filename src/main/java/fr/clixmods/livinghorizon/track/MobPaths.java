package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.compat.LodWorld;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Which of the {@link MobAnimations} a remembered mob plays, from the blocks around it.
 *
 * <p>The ground around the mob is read once, when it is remembered: in every column
 * within {@link #RADIUS} blocks, the floor nearest the mob's own height with room above
 * it, from the chunks loaded here or else from the far world (Voxy or Distant Horizons).
 * What has no collision - grass, flowers, crops, snow, torches - is air to a mob, and a
 * path dug with a shovel is ground like any other. Water, leaves and what stands too tall to step on (a fence, a
 * wall) are not.
 *
 * <p>Then every animation is laid on that ground, turned eight ways and mirrored: one
 * fits when all along it the ground is there and never climbs nor drops more than a
 * block at a time. The widest that fit are kept and one of them is chosen, by the mob's
 * UUID; with none, it stands and turns on the spot.
 */
public final class MobPaths {
    /** Blocks around the mob that are read. */
    static final int RADIUS = 8;
    static final int SIZE = RADIUS * 2 + 1;
    /** The highest step, up or down, between two neighbouring points of the ground. */
    static final double STEP = 1.05;
    /** Blocks of difference with the mob's own height that still count as its own ground. */
    private static final double LEVEL = 0.6;
    /** The window of each column that is read, below and above the mob's feet. */
    static final int BELOW = 4, ABOVE = 6;
    /** Air above a floor for a mob to stand on it. */
    private static final int ROOM = 2;
    /** Blocks around a flying or swimming mob it moves within: nothing to fall from. */
    private static final double FREE = 3.0;
    /** Animations kept, out of those that fit: at least this share of the widest one. */
    private static final double WIDE_ENOUGH = 0.6;

    private MobPaths() {
    }

    /** The animation a mob plays, laid on the ground it was chosen for; no animation: it stands. */
    public record Choice(Ground ground, MobAnimations.@Nullable Animation animation, double angle, boolean mirror) {
    }

    /**
     * Reads the ground around a mob and chooses its animation. Loaded columns are read at
     * once; the others come from the far world, off the thread, so the answer may come later.
     */
    public static CompletableFuture<Choice> plan(ClientLevel level, double x, double y, double z, double width, long seed) {
        int bx = Mth.floor(x), bz = Mth.floor(z), bottom = Mth.floor(y) - BELOW, height = BELOW + ABOVE + 1;
        double[][] ground = new double[SIZE][SIZE];
        int[] farX = new int[SIZE * SIZE], farZ = new int[SIZE * SIZE];
        int far = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState[] column = new BlockState[height];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                int cx = bx + i - RADIUS, cz = bz + j - RADIUS;
                if (level.hasChunk(cx >> 4, cz >> 4)) {
                    for (int k = 0; k < height; k++) column[k] = level.getBlockState(pos.set(cx, bottom + k, cz));
                    ground[i][j] = floor(column, bottom, y);
                } else {
                    ground[i][j] = Double.NaN;
                    farX[far] = cx;
                    farZ[far] = cz;
                    far++;
                }
            }
        }
        if (far == 0) return CompletableFuture.completedFuture(choose(new Ground(ground, x, y, z), width, seed));
        return LodWorld.columns(Arrays.copyOf(farX, far), Arrays.copyOf(farZ, far), bottom, height).thenApply(read -> {
            for (int k = 0; k < read.length; k++) {
                if (read[k] != null) ground[farX[k] - bx + RADIUS][farZ[k] - bz + RADIUS] = floor(read[k], bottom, y);
            }
            return choose(new Ground(ground, x, y, z), width, seed);
        });
    }

    /** Around a flying or swimming mob: a few blocks every way, at its own height. */
    public static Choice free(double x, double y, double z, long seed) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        double[][] ground = new double[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                double dx = bx + i - RADIUS + 0.5 - x, dz = bz + j - RADIUS + 0.5 - z;
                ground[i][j] = dx * dx + dz * dz <= FREE * FREE ? y : Double.NaN;
            }
        }
        return choose(new Ground(ground, x, y, z), 0, seed);
    }

    /** Standing where it is: the ground around could not be read. */
    public static Choice stand(double x, double y, double z) {
        double[][] ground = new double[SIZE][SIZE];
        for (double[] row : ground) Arrays.fill(row, Double.NaN);
        return new Choice(new Ground(ground, x, y, z), null, 0, false);
    }

    /**
     * The animation for this ground: of all that fit, turned and mirrored, the widest ones,
     * and one of them by the seed so that a herd does not all do the same.
     */
    static Choice choose(Ground ground, double width, long seed) {
        int bias = (int) Math.floorMod(seed, 8L);
        boolean mirrorFirst = ((seed >> 7) & 1) == 1;
        List<Choice> fit = new ArrayList<>();
        double widest = 0;
        for (MobAnimations.Animation animation : MobAnimations.ALL) {
            search:
            for (int k = 0; k < 8; k++) {
                for (int m = 0; m < 2; m++) {
                    double angle = (k + bias) * Math.PI / 4;
                    boolean mirror = (m == 1) != mirrorFirst;
                    if (fits(ground, animation, angle, mirror, width)) {
                        fit.add(new Choice(ground, animation, angle, mirror));
                        widest = Math.max(widest, animation.extent);
                        break search;
                    }
                }
            }
        }
        double least = widest * WIDE_ENOUGH;
        fit.removeIf(choice -> choice.animation().extent < least);
        if (fit.isEmpty()) return new Choice(ground, null, 0, false);
        return fit.get((int) Math.floorMod(seed >> 11, (long) fit.size()));
    }

    /** All along the animation, ground under the mob, as wide as it, never a step higher than a block. */
    static boolean fits(Ground ground, MobAnimations.Animation animation, double angle, boolean mirror, double width) {
        double[] corners = animation.corners();
        double last = ground.height(ground.x, ground.z);
        for (int k = 0; k + 3 < corners.length; k += 2) {
            double ax = corners[k], az = corners[k + 1], bx = corners[k + 2], bz = corners[k + 3];
            int samples = Math.max(1, (int) Math.ceil(Math.hypot(bx - ax, bz - az) / 0.25));
            for (int s = 1; s <= samples; s++) {
                double u = s / (double) samples;
                double lx = ax + (bx - ax) * u, lz = az + (bz - az) * u;
                double h = walkable(ground, ground.x + MobAnimations.worldX(lx, lz, angle, mirror),
                        ground.z + MobAnimations.worldZ(lx, lz, angle, mirror), width);
                if (Double.isNaN(h) || Math.abs(h - last) > STEP) return false;
                last = h;
            }
        }
        return true;
    }

    /** The ground under a point, NaN unless there is ground as wide as the mob around it, with no wall nor drop. */
    static double walkable(Ground ground, double x, double z, double width) {
        double h = ground.height(x, z);
        if (Double.isNaN(h)) return h;
        double half = Math.max(0.2, width / 2 - 0.1);
        for (int side = 0; side < 4; side++) {
            double sx = x + (side == 0 ? -half : side == 1 ? half : 0), sz = z + (side == 2 ? -half : side == 3 ? half : 0);
            double s = ground.height(sx, sz);
            if (Double.isNaN(s) || Math.abs(s - h) > STEP) return Double.NaN;
        }
        return h;
    }

    /**
     * The height a mob stands at in one column: the top of the floor nearest {@code y}
     * with {@link #ROOM} blocks of air above it, NaN where that floor is water or leaves,
     * or where there is none. {@code column[k]} is the block at {@code bottom + k}, null
     * where unknown; above the column counts as air.
     */
    static double floor(BlockState[] column, int bottom, double y) {
        double best = Double.NaN, nearest = Double.POSITIVE_INFINITY;
        int open = ROOM;
        for (int k = column.length - 1; k >= 0; k--) {
            BlockState state = column[k];
            if (state == null) {
                open = 0;
                continue;
            }
            if (passable(state)) {
                open++;
                continue;
            }
            if (open >= ROOM && !(state.getBlock() instanceof LeavesBlock)) {
                boolean wet = !state.getFluidState().isEmpty();
                double top = bottom + k + (wet ? 1 : top(state));
                double gap = Math.abs(top - y);
                if (gap < nearest) {
                    nearest = gap;
                    best = wet ? Double.NaN : top;
                }
            }
            open = 0;
        }
        return best;
    }

    /** Nothing a mob bumps into: air, grass, flowers, crops, a thin layer of snow. */
    static boolean passable(BlockState state) {
        //? if >=26.3 {
        /*// 26.3 dropped blocksMotion; this is what it answered.
        boolean blocks = state.isSolid() && !state.is(Blocks.COBWEB) && !state.is(Blocks.BAMBOO_SAPLING);
        return !blocks && state.getFluidState().isEmpty();
        *///?} else {
        return !state.blocksMotion() && state.getFluidState().isEmpty();
        //?}
    }

    /** How high a mob stands on a block: a slab half, a path or farmland a little less than one, a fence one and a half. */
    private static double top(BlockState state) {
        try {
            VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return shape.isEmpty() ? 1 : shape.max(Direction.Axis.Y);
        } catch (RuntimeException e) {
            return 1;
        }
    }

    /** The height of the ground around a mob, as read: NaN where it cannot stand. */
    public static final class Ground {
        final double x, z;
        private final int bx, bz;
        private final double[][] height;

        /**
         * {@code ground[i][j]} is the height of the column {@code (floor(x) + i - RADIUS,
         * floor(z) + j - RADIUS)}; the mob's own block is where it stands, whatever was read there.
         */
        Ground(double[][] ground, double x, double y, double z) {
            this.x = x;
            this.z = z;
            bx = Mth.floor(x);
            bz = Mth.floor(z);
            height = new double[SIZE][];
            for (int i = 0; i < SIZE; i++) height[i] = ground[i].clone();
            double own = height[RADIUS][RADIUS];
            if (Double.isNaN(own) || Math.abs(own - y) > LEVEL) height[RADIUS][RADIUS] = y;
        }

        /** Read around this very spot. */
        public boolean around(double x, double z) {
            return this.x == x && this.z == z;
        }

        /** The height of the ground at a point, NaN where unknown or not walkable. */
        public double height(double x, double z) {
            int i = Mth.floor(x) - bx + RADIUS, j = Mth.floor(z) - bz + RADIUS;
            if (i < 0 || j < 0 || i >= SIZE || j >= SIZE) return Double.NaN;
            return height[i][j];
        }
    }
}
