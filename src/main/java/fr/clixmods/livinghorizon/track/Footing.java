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

    private static AABB body(double x, double y, double z, double height) {
        return new AABB(x - 0.3, y + 0.05, z - 0.3, x + 0.3, y + height, z + 0.3);
    }

    /** Room for it there: no block in its body. */
    static boolean fits(ClientLevel level, double x, double y, double z, double height) {
        return free(level, body(x, y, z, height));
    }

    /** Something to rest on: a block right under it, or water or lava to float on. */
    static boolean supported(ClientLevel level, double x, double y, double z) {
        if (!free(level, new AABB(x - 0.3, y - 0.1, z - 0.3, x + 0.3, y + 0.01, z + 0.3))) return true;
        return !level.getFluidState(BlockPos.containing(x, y + 0.05, z)).isEmpty()
                || !level.getFluidState(BlockPos.containing(x, y - 0.1, z)).isEmpty();
    }

    /** Nothing in the way between where it is and where it goes, straight up or down. */
    static boolean clear(ClientLevel level, double x, double y, double target, double z, double height) {
        return free(level, body(x, Math.min(y, target), z, height + Math.abs(target - y)));
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
        AABB column = new AABB(x - 0.3, bottom, z - 0.3, x + 0.3, y, z + 0.3);
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
        double floor = ground(level, x, y, z, level.getMinY());
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
        int top = level.getMinY() + level.getHeight();
        for (int by = (int) Math.floor(y) + 1; by < top; by++) {
            if (!fits(level, x, by, z, height)) continue;
            double landed = ground(level, x, by, z, by - 1);
            return !Double.isNaN(landed) && fits(level, x, landed, z, height) ? landed : by;
        }
        return y;
    }

    /**
     * The height a player stands at with its feet in block ({@code x}, {@code y}, {@code z}),
     * in the middle of it; NaN where a player would not stand: no floor, no room, water,
     * lava or fire.
     */
    static double stand(ClientLevel level, int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        if (!level.hasChunkAt(feet) || !level.getFluidState(feet).isEmpty() || burns(level.getBlockState(feet))) {
            return Double.NaN;
        }
        double cx = x + 0.5, cz = z + 0.5;
        double top = ground(level, cx, y + 0.6, cz, y - 0.5);
        if (Double.isNaN(top) || top < y - 1e-3) return Double.NaN;
        if (burns(level.getBlockState(feet.below())) && top <= y + 1e-3) return Double.NaN;
        return fits(level, cx, top, cz, STANDING) ? top : Double.NaN;
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

    /** Its head under water. */
    static boolean underWater(ClientLevel level, double x, double eyes, double z) {
        BlockPos at = BlockPos.containing(x, eyes, z);
        FluidState fluid = level.getFluidState(at);
        return fluid.is(FluidTags.WATER) && eyes < at.getY() + fluid.getHeight(level, at);
    }
}
