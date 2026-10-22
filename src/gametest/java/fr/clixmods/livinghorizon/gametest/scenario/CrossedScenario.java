package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.LightModels;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobScan;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * What the crossed-planes prototype looks like: a zombie, a horse, a villager and a cow, as
 * remembered copies 80 blocks off (and a second row at 140), drawn whole and then with every
 * cube as two crossed planes, from the front, from a corner and from the side. Screenshots
 * only, with the polygon count of each.
 */
public final class CrossedScenario implements Scenario {
    private static final int GROUND = -60;

    @Override
    public String name() {
        return "crossed";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.impostorPixels = 0;
            config.impostorDistance = 2000;
            config.animationMinPixels = 0;
            if (!config.mobTypes.contains("minecraft:zombie")) config.mobTypes.add("minecraft:zombie");
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            for (int z : new int[]{80, 140}) {
                Scene.command(world, "summon zombie -4.5 " + GROUND + " " + z + ".5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],"
                        + "equipment:{head:{id:\"minecraft:iron_helmet\",count:1}}}");
                Scene.command(world, "summon horse -1.5 " + GROUND + " " + z + ".5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}");
                Scene.command(world, "summon villager 1.5 " + GROUND + " " + z + ".5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}");
                Scene.command(world, "summon cow 4.5 " + GROUND + " " + z + ".5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}");
            }
            // Scanned from among them: the server only sends a mob within its tracking range.
            Scene.command(world, "tp @a 0.5 " + GROUND + " 110.5 0 0");
            context.waitTicks(100);
            context.runOnClient(minecraft -> MobScan.start(minecraft, 8));
            context.waitTicks(60);
            // At half the entity distance the server stops sending a zombie past 64 blocks and a
            // horse past 80: their copies take over there, well inside the fog's 192 blocks.
            context.runOnClient(minecraft -> {
                minecraft.options.entityDistanceScaling().set(0.5);
                minecraft.options.fov().set(30);
            });
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 0");
            context.waitTicks(300);
            int shown = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().shown().size());
            Scene.log("crossed " + shown + " copies");

            view(context, world, "front", 0.5, 0.5, 0f);
            view(context, world, "corner", -49.5, 30.5, -45f);
            view(context, world, "side", -69.5, 80.5, -90f);
            context.runOnClient(minecraft -> {
                LightModels.crossedPlanes = false;
                FarPlayerTracker.get().mobs().forgetAll();
            });
        }
    }

    /** From one spot: whole models, then crossed planes, a screenshot of each with its polygons. */
    private void view(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double z, float yaw) {
        Scene.command(world, "tp @a " + x + " " + GROUND + " " + z + " " + yaw + " 0");
        context.runOnClient(minecraft -> {
            LightModels.crossedPlanes = false;
            FarConfig.get().lightModelPixels = 0;
        });
        context.waitTicks(30);
        Scene.screenshot(context, this, name + "-whole");
        int whole = context.computeOnClient(minecraft -> PolygonStats.polygons());
        context.runOnClient(minecraft -> {
            LightModels.crossedPlanes = true;
            FarConfig.get().lightModelPixels = FarConfig.MAX_LIGHT_MODEL_PIXELS;
        });
        context.waitTicks(30);
        Scene.screenshot(context, this, name + "-crossed");
        int crossed = context.computeOnClient(minecraft -> PolygonStats.polygons());
        Scene.log("crossed " + name + ": " + whole + " polygons whole, " + crossed + " crossed");
    }
}
