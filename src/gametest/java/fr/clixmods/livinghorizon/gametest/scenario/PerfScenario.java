package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobScan;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.function.Consumer;

/**
 * What the mod costs: a flat world full of animals, timed with each part of the mod on and
 * off. Each phase logs the wall time per tick (one frame is drawn per tick in a game test)
 * and the mod's own timings, with the moment it ran, to line up with a profiler recording.
 */
public final class PerfScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "pig", "chicken"};
    private static final int TICKS = 300;

    @Override
    public String name() {
        return "perf";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // A field of animals around the start, 16 blocks apart, as far as the loaded chunks go.
            int i = 0;
            for (int x = -176; x <= 176; x += 16) {
                for (int z = -176; z <= 176; z += 16) {
                    Scene.command(world, "summon " + MOBS[i++ % MOBS.length] + " " + x + " -60 " + z + " {NoAI:1b}");
                }
            }
            context.waitTicks(100);
            phases(context, "centre");

            // Off to one side, looking back at the field: most of it is remembered copies now.
            Scene.command(world, "tp @a 0 ~ -300 0 0");
            context.waitTicks(300);
            context.runOnClient(minecraft -> MobScan.start(minecraft, 32));
            context.waitTicks(100);
            phases(context, "side");
        }
    }

    private static void phases(ClientGameTestContext context, String where) {
        phase(context, where + " vanilla", config -> {
            config.enabled = false;
            config.distantMobs = false;
            config.skyBirds = false;
            config.ufo = false;
        });
        phase(context, where + " defaults", config -> {
        });
        phase(context, where + " mobs", config -> {
            config.skyBirds = false;
            config.ufo = false;
        });
        phase(context, where + " birds", config -> {
            config.distantMobs = false;
        });
        phase(context, where + " mobs-no-impostors", config -> {
            config.skyBirds = false;
            config.ufo = false;
            config.impostors = false;
        });
        phase(context, where + " mobs-no-occlusion", config -> {
            config.skyBirds = false;
            config.ufo = false;
            config.hideOccludedMobs = false;
        });
    }

    private static void phase(ClientGameTestContext context, String name, Consumer<FarConfig> change) {
        Scene.configure(context, config -> {
            FarConfig defaults = new FarConfig();
            for (var field : FarConfig.class.getFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.set(config, field.get(defaults));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
            change.accept(config);
        });
        context.waitTicks(60);
        long start = System.currentTimeMillis(), started = System.nanoTime();
        context.waitTicks(TICKS);
        double perTick = (System.nanoTime() - started) / 1e6 / TICKS;
        long end = System.currentTimeMillis();
        context.runOnClient(minecraft -> Scene.log(String.format(java.util.Locale.ROOT,
                "perf %-28s %7.2f ms/tick  extract %s ms  modtick %s ms  drawn %d skipped %d  shown %d live %d  [%d..%d]",
                name, perTick, Stats.extractMillis(), Stats.tickMillis(), Stats.drawn(), Stats.skipped(),
                FarPlayerTracker.get().mobs().shown().size(), FarPlayerTracker.get().mobs().live().size(), start, end)));
    }
}
