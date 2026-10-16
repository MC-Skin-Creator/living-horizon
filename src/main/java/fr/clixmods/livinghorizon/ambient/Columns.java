package fr.clixmods.livinghorizon.ambient;

import fr.clixmods.livinghorizon.compat.LodWorld;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The top of many columns at once, for the easter eggs that look for a place: read here
 * where the chunk is loaded, from the far world (Voxy, Distant Horizons) off the game's
 * thread where it is not.
 */
final class Columns {
    /**
     * One column: the height to stand at, whether that is water, its biome; NaN where
     * nothing is known. {@code canopy}: the far world's top is leaves, the ground under them
     * unknown (a loaded column gives the ground under its trees).
     */
    record Column(double x, double z, double y, boolean water, String biome, boolean canopy) {
        boolean known() {
            return !Double.isNaN(y);
        }

        /** Known ground, out of the water and under no leaves: somewhere to walk. */
        boolean dry() {
            return known() && !water && !canopy;
        }
    }

    private Columns() {
    }

    /** Entry {@code i} is the column at {@code (xs[i], zs[i])}; called on the game's thread. */
    static CompletableFuture<Column[]> read(ClientLevel level, double[] xs, double[] zs) {
        Column[] found = new Column[xs.length];
        List<Integer> far = new ArrayList<>();
        for (int i = 0; i < xs.length; i++) {
            int bx = Mth.floor(xs[i]), bz = Mth.floor(zs[i]);
            if (!level.hasChunk(bx >> 4, bz >> 4)) {
                far.add(i);
                continue;
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            BlockPos under = new BlockPos(bx, top - 1, bz);
            String biome = level.getBiome(under).unwrapKey().map(key -> key.identifier().toString()).orElse("");
            found[i] = new Column(xs[i], zs[i], top, !level.getFluidState(under).isEmpty(), biome, false);
        }
        if (far.isEmpty()) return CompletableFuture.completedFuture(found);
        int[] fx = new int[far.size()], fz = new int[far.size()];
        for (int k = 0; k < far.size(); k++) {
            fx[k] = Mth.floor(xs[far.get(k)]);
            fz[k] = Mth.floor(zs[far.get(k)]);
        }
        return LodWorld.surfaces(fx, fz).handle((surfaces, error) -> {
            // A far world that fails leaves those columns unknown, never the search hanging.
            for (int k = 0; k < far.size(); k++) {
                int i = far.get(k);
                LodWorld.Surface surface = surfaces == null ? null : surfaces[k];
                found[i] = surface == null ? new Column(xs[i], zs[i], Double.NaN, false, "", false)
                        : new Column(xs[i], zs[i], surface.y() + 1, surface.water(), surface.biome(),
                        surface.block() != null && surface.block().is(BlockTags.LEAVES));
            }
            return found;
        });
    }
}
