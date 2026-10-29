package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.render.lod.LodRenderer;
import fr.clixmods.livinghorizon.ui.LodScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The levels of detail in game: a crowd in rows from 30 to 150 blocks, drawn by the mod, with
 * LOD Colours on (green, yellow, orange, red for impostors), then without the colours, without
 * Detail Levels, and without impostors (the simplest model goes on to the end). Each row should
 * keep its shape at every level, its boxes textured, with nothing doubled or missing.
 */
public final class LodsScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "pig", "villager", "chicken", "horse", "wolf", "iron_golem",
            "zombie", "creeper", "skeleton", "spider"};
    private static final double[] ROWS = {30, 55, 95, 150};

    @Override
    public String name() {
        return "lods";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.entityDistanceScaling().set(0.5));
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 128;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
            config.mobLods = true;
            config.debugLodColors = true;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            for (double row : ROWS) Scene.row(world, MOBS, 1, row, 2.2 + row / 40);
            // The levels are made and baked away from the game, one texture handed over a frame.
            context.waitTicks(160);

            int[] levels = counts(context, "colours");
            if (levels[1] + levels[2] + levels[3] == 0) {
                throw new AssertionError("No figure drawn at a simpler level of detail");
            }
            if (context.computeOnClient(minecraft -> PolygonStats.gain()) <= 0) {
                throw new AssertionError("The levels of detail saved nothing");
            }
            Scene.screenshot(context, this, "colours");
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "colours-zoom");

            Scene.configure(context, config -> config.debugLodColors = false);
            Scene.screenshot(context, this, "plain-zoom");
            Scene.configure(context, config -> config.mobLods = false);
            counts(context, "off");
            Scene.screenshot(context, this, "off-zoom");

            Scene.configure(context, config -> {
                config.mobLods = true;
                config.debugLodColors = true;
                config.impostors = false;
            });
            context.waitTicks(20);
            counts(context, "no impostors");
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));
            Scene.screenshot(context, this, "no-impostors");

            // The preview of the settings: the models made so far, each level side by side.
            context.setScreen(() -> new LodScreen(null));
            context.waitTicks(30);
            int previews = context.computeOnClient(minecraft -> LodRenderer.previews().size());
            Scene.log("lods preview: " + previews + " models");
            if (previews == 0) throw new AssertionError("The preview has no model to show");
            Scene.screenshot(context, this, "preview");
            context.setScreen(() -> null);

            // Close up: a few mobs side by side, at each level in turn, then as models.
            Scene.command(world, "kill @e[type=!player]");
            context.waitTicks(10);
            closeUp(context, world);
        }
    }

    private static final String[] CLOSE = {"villager", "cow", "horse", "sheep", "iron_golem", "pig", "wolf", "chicken"};

    /**
     * {@link #CLOSE} in a row 50 blocks ahead, past where the game draws them, zoomed in on:
     * each level forced on all of them in turn, then the full models.
     */
    private void closeUp(ClientGameTestContext context, TestSingleplayerContext world) {
        double spacing = 2.4;
        for (int i = 0; i < CLOSE.length; i++) {
            Scene.summon(world, CLOSE[i], (i - (CLOSE.length - 1) / 2.0) * spacing, 50, 150);
        }
        Scene.configure(context, config -> {
            config.mobLods = true;
            config.debugLodColors = false;
        });
        context.runOnClient(minecraft -> minecraft.options.fov().set(30));
        try {
            for (int level = 1; level < 4; level++) {
                int forced = level;
                context.runOnClient(minecraft -> LodRenderer.forceLevelForTests(forced));
                context.waitTicks(60);
                counts(context, "close-up level " + level);
                Scene.screenshot(context, this, "close-level" + level);
            }
            Scene.configure(context, config -> config.mobLods = false);
            Scene.screenshot(context, this, "close-model");
            // From above, as a player on a hill sees them: the tops must hide the insides.
            Scene.configure(context, config -> config.mobLods = true);
            Scene.command(world, "execute as @p at @s run tp @s ~ ~14 ~ 0 15");
            context.waitTicks(20);
            Scene.screenshot(context, this, "close-above-level3");
            Scene.configure(context, config -> config.mobLods = false);
            Scene.screenshot(context, this, "close-above-model");
        } finally {
            context.runOnClient(minecraft -> LodRenderer.forceLevelForTests(-1));
        }
    }

    /** Logs the counters, and gives the figures drawn at each level last frame. */
    private static int[] counts(ClientGameTestContext context, String what) {
        context.waitTicks(5);
        return context.computeOnClient(minecraft -> {
            int[] levels = new int[PolygonStats.LEVELS];
            StringBuilder line = new StringBuilder("lods " + what + ": triangles=" + PolygonStats.triangles()
                    + " without=" + PolygonStats.fullTriangles() + " gain=" + PolygonStats.gain() + "% levels:");
            for (int level = 0; level < PolygonStats.LEVELS; level++) {
                levels[level] = PolygonStats.figures(level);
                line.append(' ').append(levels[level]);
            }
            Scene.log(line.toString());
            return levels;
        });
    }
}
