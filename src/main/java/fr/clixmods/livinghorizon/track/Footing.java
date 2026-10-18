package fr.clixmods.livinghorizon.track;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a figure of a player can be, read from the blocks the client has: room for its
 * body, something under its feet, the floor it falls onto, the first free place above it.
 * Positions are those of its feet, at the middle of its 0.6 block wide body.
 */
final class Footing {
    /** A player's height, standing. */
    static final double STANDING = 1.8;

    private Footing() {
    }

    /** Half a player's width. */
    static final double HALF = 0.3;

    private static AABB body(double x, double y, double z, double half, double height) {
        return new AABB(x - half, y + 0.05, z - half, x + half, y + height, z + half);
    }

    /** Room for it there: no block in its body. */
    static boolean fits(ClientLevel level, double x, double y, double z, double height) {
        return fits(level, x, y, z, HALF, height);
    }

    /** Room there for a body {@code half} a width across each way: a mount and its rider. */
    static boolean fits(ClientLevel level, double x, double y, double z, double half, double height) {
        return free(level, body(x, y, z, half, height));
    }

    /** Something to rest on: a block right under it, or water or lava to float on. */
    static boolean supported(ClientLevel level, double x, double y, double z) {
        return supported(level, x, y, z, HALF);
    }

    /** Nothing in the way between where it is and where it goes, straight up or down. */
    static boolean clear(ClientLevel level, double x, double y, double target, double z, double height) {
        return free(level, body(x, Math.min(y, target), z, HALF, height + Math.abs(target - y)));
    }

    /**
     * No block in the box, doors and fence gates left out: a figure walks through them, shut
     * or not, as it cannot open them. Blocks alone, read straight: this runs thousands of times
     * when a figure looks for its way.
     */
    static boolean free(ClientLevel level, AABB box) {
        for (BlockPos at : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = level.getBlockState(at);
            if (state.isAir() || passable(state)) continue;
            for (AABB part : state.getCollisionShape(level, at).toAabbs()) {
                if (part.move(at.getX(), at.getY(), at.getZ()).intersects(box)) return false;
            }
        }
        return true;
    }

    /** Doors and fence gates: walked through. */
    static boolean passable(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock;
    }

    /**
     * The top of the highest block under its feet, between {@code bottom} and {@code y};
     * NaN if there is none.
     */
    static double ground(ClientLevel level, double x, double y, double z, double bottom) {
        return ground(level, x, y, z, bottom, HALF);
    }

    /** {@link #ground(ClientLevel, double, double, double, double)} under a body this wide. */
    static double ground(ClientLevel level, double x, double y, double z, double bottom, double half) {
        AABB column = new AABB(x - half, bottom, z - half, x + half, y, z + half);
        double top = Double.NaN;
        for (BlockPos at : BlockPos.betweenClosed(BlockPos.containing(column.minX, column.minY, column.minZ),
                BlockPos.containing(column.maxX, column.maxY, column.maxZ))) {
            BlockState state = level.getBlockState(at);
            if (state.isAir() || passable(state)) continue;
            for (AABB part : state.getCollisionShape(level, at).toAabbs()) {
                AABB placed = part.move(at.getX(), at.getY(), at.getZ());
                if (!placed.intersects(column)) continue;
                double max = placed.maxY;
                if (max <= y + 1e-4 && !(max <= top)) top = max;
            }
        }
        return top;
    }

    /** The first thing under it to land on, a block or the surface of water; where it is if there is none. */
    static double floorBelow(ClientLevel level, double x, double y, double z) {
        return floorBelow(level, x, y, z, HALF);
    }

    /** {@link #floorBelow(ClientLevel, double, double, double)} under a body this wide. */
    static double floorBelow(ClientLevel level, double x, double y, double z, double half) {
        double floor = ground(level, x, y, z, level.getMinY(), half);
        double lowest = Double.isNaN(floor) ? level.getMinY() : floor;
        for (int by = BlockPos.containing(x, y, z).getY(); by >= Math.floor(lowest); by--) {
            BlockPos at = BlockPos.containing(x, by, z);
            FluidState fluid = level.getFluidState(at);
            if (fluid.isEmpty()) continue;
            double surface = by + fluid.getHeight(level, at);
            if (surface <= y + 1e-4) return Double.isNaN(floor) ? surface : Math.max(floor, surface);
        }
        return Double.isNaN(floor) ? y : floor;
    }

    /** The first place above where it fits, standing on what is there; where it is if there is none. */
    static double freeAbove(ClientLevel level, double x, double y, double z, double height) {
        return freeAbove(level, x, y, z, HALF, height);
    }

    /** {@link #freeAbove(ClientLevel, double, double, double, double)} for a body this wide. */
    static double freeAbove(ClientLevel level, double x, double y, double z, double half, double height) {
        int top = level.getMinY() + level.getHeight();
        for (int by = (int) Math.floor(y) + 1; by < top; by++) {
            if (!fits(level, x, by, z, half, height)) continue;
            double landed = ground(level, x, by, z, by - 1, half);
            return !Double.isNaN(landed) && fits(level, x, landed, z, half, height) ? landed : by;
        }
        return y;
    }

    /** Something under a body this wide: a block right under it, or water or lava to float on. */
    static boolean supported(ClientLevel level, double x, double y, double z, double half) {
        if (!free(level, new AABB(x - half, y - 0.1, z - half, x + half, y + 0.01, z + half))) return true;
        return !level.getFluidState(BlockPos.containing(x, y + 0.05, z)).isEmpty()
                || !level.getFluidState(BlockPos.containing(x, y - 0.1, z)).isEmpty();
    }

    /**
     * The height a player stands at with its feet in block ({@code x}, {@code y}, {@code z}),
     * in the middle of it; NaN where a player would not stand: no floor, no room, water,
     * lava or fire.
     */
    static double stand(ClientLevel level, int x, int y, int z) {
        return stand(level, x, y, z, HALF, STANDING);
    }

    /** {@link #stand(ClientLevel, int, int, int)} for a body this wide and tall: a mount and its rider. */
    static double stand(ClientLevel level, int x, int y, int z, double half, double height) {
        BlockPos feet = new BlockPos(x, y, z);
        if (!level.hasChunkAt(feet) || !level.getFluidState(feet).isEmpty() || burns(level.getBlockState(feet))) {
            return Double.NaN;
        }
        double cx = x + 0.5, cz = z + 0.5;
        double top = ground(level, cx, y + 0.6, cz, y - 0.5, half);
        if (Double.isNaN(top) || top < y - 1e-3) return Double.NaN;
        if (burns(level.getBlockState(feet.below())) && top <= y + 1e-3) return Double.NaN;
        return fits(level, cx, top, cz, half, height) ? top : Double.NaN;
    }

    /**
     * The height a boat floats at in block ({@code x}, {@code y}, {@code z}): the surface of
     * the water there, with room above it for the boat and its rider; NaN where there is none.
     */
    static double afloat(ClientLevel level, int x, int y, int z, double half, double height) {
        BlockPos at = new BlockPos(x, y, z);
        if (!level.hasChunkAt(at)) return Double.NaN;
        FluidState fluid = level.getFluidState(at);
        if (!fluid.is(FluidTags.WATER) || !level.getFluidState(at.above()).isEmpty()) return Double.NaN;
        double surface = y + fluid.getHeight(level, at);
        return fits(level, x + 0.5, surface, z + 0.5, half, height) ? surface : Double.NaN;
    }

    /** Fire, a lit campfire, magma: nobody stands there on purpose. */
    static boolean burns(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK)
                || state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
    }

    /** In lava or fire, some part of a body this high. */
    static boolean inFire(ClientLevel level, double x, double y, double z, double height) {
        for (int by = (int) Math.floor(y); by <= Math.floor(y + height); by++) {
            BlockPos at = BlockPos.containing(x, by, z);
            if (level.getFluidState(at).is(FluidTags.LAVA) || burns(level.getBlockState(at))) return true;
        }
        return false;
    }

    /**
     * Its feet in water: wading, swimming, or floating on the surface with nothing under it.
     * Where a player who logged off is never left: see {@link #shore}.
     */
    static boolean wet(ClientLevel level, double x, double y, double z) {
        if (level.getFluidState(BlockPos.containing(x, y + 0.05, z)).is(FluidTags.WATER)) return true;
        return level.getFluidState(BlockPos.containing(x, y - 0.1, z)).is(FluidTags.WATER)
                && free(level, new AABB(x - 0.3, y - 0.1, z - 0.3, x + 0.3, y + 0.01, z + 0.3));
    }

    /** The top of the water it is in: the surface a boat floats on. */
    static double surface(ClientLevel level, double x, double y, double z) {
        int top = level.getMinY() + level.getHeight();
        BlockPos.MutableBlockPos at = BlockPos.containing(x, y + 0.05, z).mutable();
        // Floating on it: the water is just under its feet.
        if (!level.getFluidState(at).is(FluidTags.WATER)) at.move(0, -1, 0);
        double surface = y;
        for (; at.getY() < top; at.move(0, 1, 0)) {
            FluidState fluid = level.getFluidState(at);
            if (!fluid.is(FluidTags.WATER)) break;
            surface = at.getY() + fluid.getHeight(level, at);
        }
        return surface;
    }

    /** Whether every block within {@code radius} blocks across is known to the client. */
    static boolean loadedAround(ClientLevel level, double x, double z, int radius) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int dx = -radius; dx <= radius; dx += radius) {
            for (int dz = -radius; dz <= radius; dz += radius) {
                if (!level.hasChunkAt(new BlockPos(bx + dx, 0, bz + dz))) return false;
            }
        }
        return true;
    }

    /**
     * The nearest place out of the water to stand on, within {@code radius} blocks across of
     * where it is: a bank or a beach, from the surface up to three blocks above it, never a
     * pocket of air under the water. Null when there is none.
     */
    static @Nullable Vec3 shore(ClientLevel level, double x, double y, double z, int radius) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int water = (int) Math.floor(surface(level, x, y, z) - 1e-3);
        Vec3 best = null;
        double bestCost = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int across = dx * dx + dz * dz;
                if (across > radius * radius || across >= bestCost) continue;
                for (int by = water; by <= water + 3; by++) {
                    double height = stand(level, bx + dx, by, bz + dz);
                    if (Double.isNaN(height)) continue;
                    // Across first; a climb weighs a little, to break ties between banks.
                    double cost = across + (by - water) * 0.1;
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = new Vec3(bx + dx + 0.5, height, bz + dz + 0.5);
                    }
                    break;
                }
            }
        }
        return best;
    }

    /** Its head under water. */
    static boolean underWater(ClientLevel level, double x, double eyes, double z) {
        BlockPos at = BlockPos.containing(x, eyes, z);
        FluidState fluid = level.getFluidState(at);
        return fluid.is(FluidTags.WATER) && eyes < at.getY() + fluid.getHeight(level, at);
    }
}
