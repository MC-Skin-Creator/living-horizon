package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.ambient.Ambience;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * {@code /livinghorizon bird <kind>}, run as the chat runs it, for every kind, with no bird
 * of their own about: each kind must arrive, in daylight too for bats, but gulls, which
 * have no coast in a flat world. A tree, a pond, a field and a brick tower stand around, and
 * the player looks at the pond, where the ducks must go.
 */
public final class BirdCommandScenario implements Scenario {
    @Override
    public String name() {
        return "birdcommand";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.skyBirds = true;
            config.birdDensity = 0;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            int ground = context.computeOnClient(minecraft -> minecraft.player.getBlockY());
            Scene.command(world, "fill -6 " + (ground + 4) + " 16 6 " + (ground + 5) + " 26 oak_leaves[persistent=true]");
            Scene.command(world, "fill 0 " + ground + " 21 0 " + (ground + 3) + " 21 oak_log");
            Scene.command(world, "fill -12 " + (ground - 1) + " -40 12 " + (ground - 1) + " -18 water");
            Scene.command(world, "fill 18 " + (ground - 1) + " -8 30 " + (ground - 1) + " 8 farmland");
            Scene.command(world, "fill 18 " + ground + " -8 30 " + ground + " 8 wheat");
            Scene.command(world, "fill -24 " + ground + " -2 -22 " + (ground + 9) + " 0 bricks");
            // Looking down at the middle of the pond.
            Scene.command(world, "tp @a 0.5 " + ground + " 0.5 180 30");
            context.waitTicks(20);

            StringBuilder missing = new StringBuilder();
            for (Ambience.Type type : Ambience.Type.values()) {
                int before = flyers(context);
                context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("livinghorizon bird " + type.id));
                context.waitTicks(5);
                int added = flyers(context) - before;
                Scene.log("birdcommand " + type.id + ": " + added + " birds");
                boolean expected = type != Ambience.Type.GULLS;
                if (expected && added <= 0) missing.append(' ').append(type.id);
                if (!expected && added > 0) missing.append(" gulls (no coast here)");
            }
            double[] ducks = context.computeOnClient(minecraft -> {
                for (Ambience.Flyer flyer : FarPlayerTracker.get().ambience().flyers()) {
                    if (flyer.species == Ambience.Species.DUCK) return new double[]{flyer.x, flyer.z};
                }
                return null;
            });
            Scene.log("birdcommand ducks at " + (ducks == null ? "nowhere" : ducks[0] + ", " + ducks[1]));
            if (ducks == null || ducks[1] > -17 || ducks[1] < -41) missing.append(" ducks (not on the pond)");
            context.waitTicks(60);
            Scene.screenshot(context, this, "pond");
            Scene.command(world, "tp @a 0.5 " + ground + " 0.5 0 -10");
            context.waitTicks(20);
            Scene.screenshot(context, this, "tree");
            if (!missing.isEmpty()) throw new AssertionError("Not summoned as they should be:" + missing);
        }
    }

    private static int flyers(ClientGameTestContext context) {
        return context.computeOnClient(minecraft -> FarPlayerTracker.get().ambience().flyers().size());
    }
}
