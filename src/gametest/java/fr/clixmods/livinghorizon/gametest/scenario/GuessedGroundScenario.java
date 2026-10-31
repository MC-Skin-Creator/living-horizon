package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.LodWorld;
import fr.clixmods.livinghorizon.debug.DebugMarks;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.track.SeedDraws;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Guessed mobs over Distant Horizons' terrain (`--with distanthorizons`, 26.3): with <i>Only Over
 * Terrain</i> on, a guessed mob standing where Distant Horizons holds terrain must not be judged
 * over the void. Reads, for the guessed mobs chosen, whether Distant Horizons has their column and
 * what the mod decided, and fails when terrain is there and the mod says void.
 */
public final class GuessedGroundScenario implements Scenario {
    @Override
    public String name() {
        return "guessedground";
    }

    @Override
    public void run(ClientGameTestContext context) {
        if (!Platform.isModLoaded("distanthorizons")) {
            Scene.log("guessedground skipped: Distant Horizons is not installed (run.sh guessedground --with distanthorizons)");
            return;
        }
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(4));
        Scene.configure(context, config -> {
            config.seedMobs = true;
            config.onlyOverTerrain = true;
            config.hideOccludedMobs = false;
            config.mobMaxDistance = 512;
            config.debugBoxes = true;
        });
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setSeed("living horizon"))
                .create()) {
            Scene.command(world, "gamerule advance_time false");
            Scene.command(world, "time set noon");
            Scene.command(world, "weather clear");
            int[] last = null;
            for (int round = 0; round < 18; round++) {
                context.waitTicks(200);
                int[] counts = count(context);
                last = counts;
                Scene.log("guessedground after " + (round + 1) * 10 + " s: guessed chosen " + counts[0]
                        + ", Distant Horizons has their column " + counts[1] + ", judged void with terrain there " + counts[2]
                        + ", void without terrain " + counts[3] + ", unknown " + counts[4] + ", terrain " + counts[5]
                        + "; marks void " + context.computeOnClient(minecraft -> DebugMarks.count(DebugMarks.Mark.VOID))
                        + "; " + context.computeOnClient(minecraft -> LodWorld.status() + " " + LodWorld.details()
                        + " drawn to " + (int) LodWorld.drawnDistance()));
                if (counts[1] > 50 && round >= 5) break;
            }
            Scene.screenshot(context, this, "guessed");
            if (last != null && last[2] > Math.max(3, last[1] / 20)) {
                throw new AssertionError("guessedground: " + last[2] + " guessed mobs judged over the void where Distant Horizons has terrain, of "
                        + last[1]);
            }
        } finally {
            Scene.configure(context, config -> config.debugBoxes = false);
        }
    }

    /**
     * {chosen, with terrain in Distant Horizons, void though terrain, void without, unknown, terrain}
     * for the guessed mobs chosen now: Distant Horizons read directly, the mod's answer asked of
     * DrawnGround as the frame does.
     */
    private static int[] count(ClientGameTestContext context) {
        List<double[]> spots = context.computeOnClient(minecraft -> {
            List<double[]> found = new ArrayList<>();
            for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                if (SeedDraws.foreseen(mob.id())) found.add(new double[]{mob.x(), mob.z()});
            }
            return found;
        });
        int[] xs = new int[spots.size()], zs = new int[spots.size()];
        for (int i = 0; i < spots.size(); i++) {
            xs[i] = (int) Math.floor(spots.get(i)[0]);
            zs[i] = (int) Math.floor(spots.get(i)[1]);
        }
        LodWorld.Surface[] surfaces;
        try {
            surfaces = LodWorld.surfaces(xs, zs).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("reading Distant Horizons failed: " + e, e);
        }
        String[] answers = context.computeOnClient(minecraft -> {
            String[] out = new String[spots.size()];
            for (int i = 0; i < out.length; i++) out[i] = answer(minecraft, spots.get(i)[0], spots.get(i)[1]);
            return out;
        });
        int[] counts = new int[6];
        counts[0] = spots.size();
        for (int i = 0; i < surfaces.length; i++) {
            boolean terrain = surfaces[i] != null;
            if (terrain) counts[1]++;
            switch (answers[i]) {
                case "VOID" -> counts[terrain ? 2 : 3]++;
                case "UNKNOWN" -> counts[4]++;
                default -> counts[5]++;
            }
        }
        return counts;
    }

    private static String answer(Minecraft minecraft, double x, double z) {
        try {
            Class<?> ground = Class.forName("fr.clixmods.livinghorizon.compat.DrawnGround");
            Method answer = ground.getDeclaredMethod("answer", net.minecraft.client.multiplayer.ClientLevel.class, Vec3.class,
                    double.class, double.class);
            answer.setAccessible(true);
            return String.valueOf(answer.invoke(null, minecraft.level, minecraft.player.getEyePosition(), x, z));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("DrawnGround.answer: " + e, e);
        }
    }
}
