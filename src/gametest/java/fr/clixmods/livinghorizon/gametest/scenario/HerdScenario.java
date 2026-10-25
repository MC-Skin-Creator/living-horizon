package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.OcclusionQueries;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Skip Hidden Mobs in a crowd: a herd of remembered cows 200 blocks away, packed in staggered
 * rows on open ground, the camera moving slowly sideways so that the cows at the back show a
 * sliver, then none, then another. Nothing but other cows stands in front of any of them: none
 * may be skipped, as only the terrain hides a mob (with OpenGL's queries, the depth before the
 * figures are drawn). Tested against the depth after them (26.3's GPU path, Vulkan or Distant
 * Horizons), a cow showing a few pixels was skipped now and then, and blinked. Seen with Distant
 * Horizons in {@code -Plh.mods} on 26.3, the copies standing on its terrain: up to 3 cows on a
 * quarter of the ticks. {@code run.sh herd --vulkan} takes the same path but never caught it.
 */
public final class HerdScenario implements Scenario {
    private static final int ROWS = 8, COLUMNS = 10;

    @Override
    public String name() {
        return "herd";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(6);
            minecraft.options.fov().set(30);
        });
        Scene.configure(context, config -> {
            config.impostors = false;
            config.hideOccludedMobs = true;
            config.optOcclusionQueries = true;
            // Past the game's chunks: without a far terrain mod, nothing is drawn under them.
            config.onlyOverTerrain = false;
            // Standing where they are.
            config.stillMobTypes.add("minecraft:cow");
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            for (int row = 0; row < ROWS; row++) {
                for (int column = 0; column < COLUMNS; column++) {
                    // Each row half a cow to the side of the one in front: every cow shows in part.
                    // Past 32 blocks: a mob that vanishes closer is taken as killed or despawned.
                    double x = (column - (COLUMNS - 1) / 2.0) * 1.4 + (row % 2) * 0.7;
                    Scene.summon(world, "cow", x, 40 + row * 1.6, (row * 7 + column * 45) % 360);
                }
            }
            context.waitTicks(100);
            // Walk away: the copies take over once the real cows are no longer sent.
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(400);

            int shown = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().shown().size());
            int most = 0, ticks = 0;
            for (int step = 0; step < 80; step++) {
                // A tenth of a block to the side every tick: the rows slide past one another.
                Scene.command(world, String.format(java.util.Locale.ROOT, "tp @a %.1f ~ -160 0 0", -4 + step * 0.1));
                context.waitTick();
                int hidden = context.computeOnClient(minecraft -> OcclusionQueries.culled());
                if (hidden > 0) ticks++;
                most = Math.max(most, hidden);
            }
            Scene.log("herd " + shown + " remembered, at most " + most + " skipped, on " + ticks + " ticks of 80; "
                    + context.computeOnClient(minecraft -> OcclusionQueries.state()));
            Scene.screenshot(context, this, "herd");
            if (shown < ROWS * COLUMNS) throw new AssertionError("Only " + shown + " of " + ROWS * COLUMNS + " cows remembered");
            if (most > 0) throw new AssertionError("Cows skipped behind other cows: up to " + most + ", on " + ticks + " ticks");
        }
    }
}
