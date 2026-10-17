package fr.clixmods.livinghorizon.ambient;

import fr.clixmods.livinghorizon.compat.LodWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToDoubleFunction;

/**
 * What the player could see, turning their head: points in sight of their eye, through the
 * loaded blocks where the points stand in them, past the far world's terrain where they do
 * not. For the easter eggs that come anywhere around, as far as the horizon.
 */
final class Sight {
    private Sight() {
    }

    /**
     * Blocks across from the player within which the terrain is drawn and clear of fog: the
     * game's own render distance, or the far world's as far as its fog leaves it clear. Past
     * it an easter egg would stand in the fog, over terrain no one sees.
     */
    static double reach() {
        Minecraft minecraft = Minecraft.getInstance();
        return Math.max(minecraft.options.getEffectiveRenderDistance() * 16 - 16, farClear());
    }

    private static double farClear;
    private static long farClearAt = Long.MIN_VALUE;

    /** {@link LodWorld#clearDistance}, read again once a second: the settings it reads seldom change. */
    static double farClear() {
        long now = System.nanoTime();
        if (farClearAt == Long.MIN_VALUE || now - farClearAt > 1_000_000_000L) {
            farClear = LodWorld.clearDistance();
            farClearAt = now;
        }
        return farClear;
    }

    /**
     * One of the farther half of {@code spots}, by {@code distance} from the player: the near
     * ones are the ones most often in sight, and the easter eggs are for the horizon. Null if
     * there is none.
     */
    static <T> @Nullable T far(List<T> spots, ToDoubleFunction<T> distance, RandomSource random) {
        if (spots.isEmpty()) return null;
        List<T> sorted = new ArrayList<>(spots);
        sorted.sort(Comparator.comparingDouble(distance).reversed());
        return sorted.get(random.nextInt((sorted.size() + 1) / 2));
    }

    /**
     * For each point, whether it is in sight: the loaded ones read now, on the game's thread;
     * the far ones by the far world, off it (none without Voxy or Distant Horizons).
     */
    static CompletableFuture<boolean[]> of(ClientLevel level, LocalPlayer self, Vec3 eye,
                                           double[] xs, double[] ys, double[] zs) {
        boolean[] seen = new boolean[xs.length];
        List<Integer> far = new ArrayList<>();
        for (int i = 0; i < xs.length; i++) {
            if (level.hasChunk(Mth.floor(xs[i]) >> 4, Mth.floor(zs[i]) >> 4)) {
                seen[i] = Ghosts.inSight(level, self, eye, xs[i], ys[i], zs[i]);
            } else {
                far.add(i);
            }
        }
        if (far.isEmpty()) return CompletableFuture.completedFuture(seen);
        double[] fx = new double[far.size()], fy = new double[far.size()], fz = new double[far.size()];
        for (int k = 0; k < far.size(); k++) {
            fx[k] = xs[far.get(k)];
            fy[k] = ys[far.get(k)];
            fz[k] = zs[far.get(k)];
        }
        return LodWorld.blocked(eye, fx, fy, fz, 1.0).handle((blocked, error) -> {
            if (blocked != null) {
                for (int k = 0; k < far.size() && k < blocked.length; k++) seen[far.get(k)] = !blocked[k];
            }
            return seen;
        });
    }
}
