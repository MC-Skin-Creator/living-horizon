package fr.clixmods.livinghorizon.compat;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A mod that keeps the world past the render distance, read through {@link LodWorld}:
 * Voxy or Distant Horizons. Neither is required, and neither is linked against: each
 * source reaches its mod at runtime, so the mod loads with either, both or none.
 *
 * <p>Every method but the first three runs on a reader thread, never on the game's.
 */
interface LodSource {
    String name();

    /** The mod is installed. */
    boolean loaded();

    /** The mod is installed and its classes are the ones this source knows. */
    boolean available();

    /** Why this source turned itself off, or null. */
    @Nullable String failure();

    /** Another world: whatever readers hold of the old one is let go. */
    void clear();

    /** The world to read on this thread, or null while the mod has none loaded. */
    @Nullable Object open() throws Throwable;

    /** The highest block of a column and its biome; null where the mod knows nothing. */
    LodWorld.@Nullable Surface column(Object world, int x, int z) throws Throwable;

    /**
     * The blocks of a column from {@code bottom} up, {@code height} of them, air where the
     * mod has nothing; null where it knows nothing of the column. By default only the top
     * block is known: that block, solid ground under it, air over it.
     */
    default BlockState @Nullable [] slice(Object world, int x, int z, int bottom, int height) throws Throwable {
        LodWorld.Surface surface = column(world, x, z);
        if (surface == null) return null;
        BlockState top = surface.block() != null ? surface.block()
                : surface.water() ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState();
        BlockState[] slice = new BlockState[height];
        for (int k = 0; k < height; k++) {
            int y = bottom + k;
            slice[k] = y > surface.y() ? Blocks.AIR.defaultBlockState() : y == surface.y() ? top : Blocks.STONE.defaultBlockState();
        }
        return slice;
    }

    /** Distance to the first opaque block along a unit direction, between two distances; NaN if none. */
    double firstHit(Object world, double ax, double ay, double az, double dx, double dy, double dz,
                    double from, double to) throws Throwable;

    /** What the reads found so far, for {@code /livinghorizon lod}; empty when there is nothing to say. */
    default String details() {
        return "";
    }

    /** The mod did not answer as expected: this source is off for the session. */
    void fail(Throwable e);
}
