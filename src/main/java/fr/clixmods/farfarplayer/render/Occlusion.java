package fr.clixmods.farfarplayer.render;

import fr.clixmods.farfarplayer.FarConfig;
import fr.clixmods.farfarplayer.compat.VoxyWorld;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a distant mob stands behind terrain, for {@link FarConfig#hideOccludedMobs}.
 *
 * <p>A frame only reads the last answer. Twice a second, the mobs the frames asked about
 * are tested in one go on Voxy's reader threads: a line from the eye to the middle of the
 * mob and one to its top. Hidden means both meet a block before the mob.
 */
public final class Occlusion {
    /** Ticks between two tests. */
    private static final int EVERY = 10;

    private static final Map<Object, Boolean> HIDDEN = new ConcurrentHashMap<>();
    /** Asked about since the last test: where, and how tall. */
    private static final Map<Object, double[]> ASKED = new HashMap<>();
    private static volatile boolean busy;
    private static int clock;

    private Occlusion() {
    }

    /** The last answer for this mob, false until there is one; asks again for the next test. */
    static boolean hidden(Object key, double x, double y, double z, double height) {
        ASKED.put(key, new double[]{x, y, z, height});
        return HIDDEN.getOrDefault(key, false);
    }

    /** Every client tick, from where the camera is. */
    public static void tick(Vec3 eye) {
        if (!FarConfig.get().hideOccludedMobs) {
            ASKED.clear();
            HIDDEN.clear();
            return;
        }
        if (++clock % EVERY != 0 || busy || ASKED.isEmpty()) return;
        List<Object> keys = new ArrayList<>(ASKED.keySet());
        int n = keys.size();
        double[] xs = new double[n * 2], ys = new double[n * 2], zs = new double[n * 2];
        for (int i = 0; i < n; i++) {
            double[] at = ASKED.get(keys.get(i));
            xs[2 * i] = xs[2 * i + 1] = at[0];
            zs[2 * i] = zs[2 * i + 1] = at[2];
            ys[2 * i] = at[1] + at[3] * 0.5;
            ys[2 * i + 1] = at[1] + at[3] * 0.95;
        }
        ASKED.clear();
        busy = true;
        VoxyWorld.blocked(eye, xs, ys, zs, 1.0).whenComplete((blocked, error) -> {
            if (blocked != null) {
                for (int i = 0; i < n; i++) HIDDEN.put(keys.get(i), blocked[2 * i] && blocked[2 * i + 1]);
            }
            HIDDEN.keySet().retainAll(new HashSet<>(keys));
            busy = false;
        });
    }

    /** How many of the mobs tested last are hidden, for the debug panel. */
    public static int hiddenCount() {
        int hidden = 0;
        for (boolean value : HIDDEN.values()) if (value) hidden++;
        return hidden;
    }
}
