package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.LightModels;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The light models: test players who logged off in full diamond armour, a sword and a shield,
 * one near the camera and two farther, still drawn as models. With the default setting the
 * near one keeps its gear and the far ones lose it; at Never every one keeps it; at the top of
 * the range every one loses it. The polygons drawn say so, and a screenshot of each.
 */
public final class LightModelsScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    private static final String NEAR = "LightNear", MID = "LightMid", FAR = "LightFar";

    @Override
    public String name() {
        return "lightmodels";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.offlineLife = false;
            config.offlineNames = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            for (String slot : new String[]{"armor.head with diamond_helmet", "armor.chest with diamond_chestplate",
                    "armor.legs with diamond_leggings", "armor.feet with diamond_boots",
                    "weapon.mainhand with diamond_sword", "weapon.offhand with shield"}) {
                Scene.command(world, "item replace entity @p " + slot);
            }
            context.waitTicks(10);
            // Facing north, towards the camera at the origin: 8, 26 and 60 blocks south of it.
            spawn(context, world, NEAR, 8.5);
            spawn(context, world, MID, 26.5);
            spawn(context, world, FAR, 60.5);
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 0");
            context.waitTicks(40);

            int defaultPixels = context.computeOnClient(minecraft -> FarConfig.get().lightModelPixels);
            Measure light = measure(context, defaultPixels, "default");
            Measure whole = measure(context, 0, "never");
            Measure all = measure(context, FarConfig.MAX_LIGHT_MODEL_PIXELS, "all");
            Scene.log("lightmodels default " + defaultPixels + " px: " + light + "; never: " + whole + "; all: " + all);
            if (light.lightened != 2) throw new AssertionError("lightmodels: " + light.lightened + " light models by default, not the two far ones");
            if (whole.lightened != 0) throw new AssertionError("lightmodels: " + whole.lightened + " light models at Never");
            if (all.lightened != 3) throw new AssertionError("lightmodels: " + all.lightened + " light models at the top, not all three");
            if (!(all.polygons < light.polygons && light.polygons < whole.polygons)) {
                throw new AssertionError("lightmodels: polygons do not fall with the setting: " + whole.polygons + " whole, "
                        + light.polygons + " default, " + all.polygons + " all light");
            }
            context.runOnClient(minecraft -> FarPlayerTracker.get().resting().clearFakes());
        }
    }

    private static void spawn(ClientGameTestContext context, TestSingleplayerContext world, String name, double z) {
        Scene.command(world, "tp @a 0.5 " + GROUND + " " + z + " 180 0");
        context.waitTicks(10);
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("livinghorizon debug offline test spawn " + name));
        context.waitTicks(5);
    }

    private record Measure(int lightened, int polygons) {
        @Override
        public String toString() {
            return lightened + " light models, " + polygons + " polygons";
        }
    }

    /** Sets the pixel threshold, lets a few frames go by, takes a screenshot and reads the counts. */
    private static Measure measure(ClientGameTestContext context, int pixels, String shot) {
        context.runOnClient(minecraft -> FarConfig.get().lightModelPixels = pixels);
        context.waitTicks(20);
        Scene.screenshot(context, new LightModelsScenario(), shot);
        return context.computeOnClient(minecraft -> new Measure(LightModels.count(), PolygonStats.modelPolygons()));
    }
}
