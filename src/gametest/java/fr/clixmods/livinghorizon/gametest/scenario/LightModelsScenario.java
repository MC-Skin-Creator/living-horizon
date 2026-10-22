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
 * The light models: test players who logged off in full enchanted diamond armour, a sword and a
 * shield, still drawn as models, at three sizes on screen. With the default setting the near
 * one is whole, the middle one keeps its gear without its shimmer, the far one loses its sword
 * and shield too; at Never every one is whole; at the top of the range every one is lightest.
 * The polygons drawn say so (the shimmer is a second pass over the armour), and a screenshot of each.
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
                Scene.command(world, "item replace entity @p " + slot + "[enchantment_glint_override=true]");
            }
            context.waitTicks(10);
            // Facing north, towards the camera at the origin: about 77, 44 and 24 pixels tall in the
            // test window, above the default size, under it, and under half of it.
            // Side by side on screen, the nearest to the left, none hiding another.
            spawn(context, world, NEAR, -2.5, 8.5);
            spawn(context, world, MID, 0.5, 14.5);
            spawn(context, world, FAR, 3.5, 26.5);
            // The camera's own hands out of the picture.
            Scene.command(world, "item replace entity @p weapon.mainhand with air");
            Scene.command(world, "item replace entity @p weapon.offhand with air");
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

    private static void spawn(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double z) {
        Scene.command(world, "tp @a " + x + " " + GROUND + " " + z + " 180 0");
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
