package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The debug counters of the levels of detail: a row of mobs at 50 blocks drawn as models,
 * a row at 95 drawn as impostors, with the debug panel on. With LOD Colours on the
 * impostors are red and the models in their level's colour; the triangles "without LOD"
 * count each figure as its full model, so they are more than the triangles drawn.
 */
public final class LodStatsScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "pig", "villager", "chicken", "horse", "wolf", "iron_golem"};

    @Override
    public String name() {
        return "lodstats";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.entityDistanceScaling().set(0.5));
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 70;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.row(world, MOBS, 3, 50, 2.6);
            Scene.row(world, MOBS, 3, 95, 4.5);
            context.waitTicks(60);
            context.runOnClient(minecraft -> {
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity instanceof LivingEntity) ImpostorAtlas.request(ImpostorKey.of(entity), entity);
                }
            });
            context.waitTicks(80);

            Scene.configure(context, config -> config.debugHud = true);
            Scene.screenshot(context, this, "panel");
            int[] counts = context.computeOnClient(minecraft -> {
                String levels = "";
                for (int level = 0; level < PolygonStats.LEVELS; level++) levels += " " + PolygonStats.figures(level);
                Scene.log("lodstats triangles=" + PolygonStats.triangles() + " without=" + PolygonStats.fullTriangles()
                        + " gain=" + PolygonStats.gain() + "% quads=" + PolygonStats.polygons() + " models="
                        + PolygonStats.models() + " impostors=" + PolygonStats.impostors() + " levels:" + levels);
                int modelFigures = 0;
                for (int level = 0; level < PolygonStats.IMPOSTOR; level++) modelFigures += PolygonStats.figures(level);
                return new int[]{PolygonStats.models(), PolygonStats.impostors(), PolygonStats.triangles(),
                        PolygonStats.fullTriangles(), modelFigures, PolygonStats.figures(PolygonStats.IMPOSTOR)};
            });
            if (counts[0] == 0 || counts[1] == 0) {
                throw new AssertionError("Expected models and impostors, got " + counts[0] + " and " + counts[1]);
            }
            if (counts[3] <= counts[2]) {
                throw new AssertionError("Impostors saved nothing: " + counts[3] + " triangles without LOD, " + counts[2]
                        + " drawn");
            }
            if (counts[4] != counts[0] || counts[5] != counts[1]) {
                throw new AssertionError("Figures per level " + counts[4] + "/" + counts[5] + " differ from "
                        + counts[0] + " models and " + counts[1] + " impostors");
            }

            Scene.configure(context, config -> config.debugHud = false);
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "plain-zoom");
            Scene.configure(context, config -> config.debugLodColors = true);
            Scene.screenshot(context, this, "colours-zoom");
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));
            Scene.screenshot(context, this, "colours");
        }
    }
}
