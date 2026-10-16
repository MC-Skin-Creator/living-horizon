package fr.clixmods.livinghorizon.ambient;

import fr.clixmods.livinghorizon.compat.LodWorld;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * What the player could see, turning their head: points in sight of their eye, through the
 * loaded blocks where the points stand in them, past the far world's terrain where they do
 * not. For the easter eggs that come anywhere around, as far as the horizon.
 */
final class Sight {
    private Sight() {
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
