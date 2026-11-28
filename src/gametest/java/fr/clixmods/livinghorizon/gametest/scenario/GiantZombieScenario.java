package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.compat.OcclusionQueries;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The game's giant zombie 200 blocks away, as Giant Spawn lets it spawn ({@code --with
 * giant-spawn,collective}): straight ahead, then behind a low wall that hides its feet only.
 */
public final class GiantZombieScenario implements Scenario {
    @Override
    public String name() {
        return "giantzombie";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.log("giantzombie Giant Spawn loaded: " + FabricLoader.getInstance().isModLoaded("giantspawn"));
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> {
            config.impostors = false;
            config.hideOccludedMobs = false;
            if (!config.mobTypes.contains("minecraft:giant")) config.mobTypes.add("minecraft:giant");
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.summon(world, "giant", 0, 40, 180);
            context.waitTicks(100);
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);
            int shown = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().shown().size());
            Scene.log("giantzombie remembered shown=" + shown);
            if (shown < 1) throw new AssertionError("The giant is not remembered");

            context.waitTicks(20);
            boolean ahead = drawn(context);
            Scene.log("giantzombie ahead: " + counts(context));
            Scene.screenshot(context, this, "ahead");

            // A wall halfway, hiding the lowest 4 blocks of its 12 from the eye.
            Scene.configure(context, config -> {
                config.hideOccludedMobs = true;
                config.optOcclusionQueries = true;
            });
            Scene.command(world, "execute at @p run fill ~-20 ~-1 ~100 ~20 ~2 ~100 stone");
            context.waitTicks(40);
            int hidden = context.computeOnClient(minecraft -> OcclusionQueries.culled());
            boolean behind = drawn(context) && hidden == 0;
            Scene.log("giantzombie behind the wall: " + hidden + " hidden, " + counts(context));
            Scene.screenshot(context, this, "wall");

            Scene.log("giantzombie result: ahead " + ahead + ", behind the wall " + behind);
        }
    }

    private static boolean drawn(ClientGameTestContext context) {
        return context.computeOnClient(minecraft -> Stats.drawn()) > 0;
    }

    private static String counts(ClientGameTestContext context) {
        return context.computeOnClient(minecraft -> "drawn " + Stats.drawn() + ", skipped " + Stats.skipped());
    }
}
