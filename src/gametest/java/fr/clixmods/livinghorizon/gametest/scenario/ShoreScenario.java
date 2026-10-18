package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;

/**
 * Players who logged off in water are never left standing in it. One logs off swimming in a
 * pond: they sit on its bank, then, living, stay on dry land. One logs off in the middle of a
 * lake, more than 10 blocks from any shore: they get a boat, rowed about the lake while they
 * live, waiting on their spot while they sit; the lake drained, the boat is gone.
 */
public final class ShoreScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;

    @Override
    public String name() {
        return "shore";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.offlineLife = false);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // A pond five blocks across and two deep, someone swimming in its middle.
            Scene.command(world, "fill 4 " + (GROUND - 2) + " 8 8 " + (GROUND - 1) + " 12 water");
            Scene.command(world, "tp @a 6.5 " + GROUND + " 0.5 0 20");
            context.waitTicks(20);
            fake(context, "LH_Swimmer", 6.5, GROUND - 1.2, 10.5);
            context.waitTicks(40);
            check(context, "pond seated", "LH_Swimmer", false, 4);
            Scene.screenshot(context, this, "pond-seated");

            Scene.configure(context, config -> config.offlineLife = true);
            context.waitTicks(60);
            check(context, "pond living", "LH_Swimmer", false, 12);
            Scene.screenshot(context, this, "pond-living");
            context.runOnClient(minecraft -> FarPlayerTracker.get().resting().clearFakes());

            // A lake 26 blocks across: no shore within 10 blocks of its middle.
            Scene.command(world, "fill -6 " + (GROUND - 2) + " 20 19 " + (GROUND - 1) + " 45 water");
            Scene.command(world, "tp @a 6.5 " + GROUND + " 16.5 0 15");
            context.waitTicks(20);
            fake(context, "LH_Rower", 6.5, GROUND - 1.2, 32.5);
            context.waitTicks(40);
            check(context, "lake living", "LH_Rower", true, 12);
            Scene.screenshot(context, this, "lake-living");

            Scene.configure(context, config -> config.offlineLife = false);
            context.waitTicks(40);
            check(context, "lake seated", "LH_Rower", true, 0.6);
            Scene.screenshot(context, this, "lake-seated");

            // Drained: out of the boat, on the ground.
            Scene.command(world, "fill -6 " + (GROUND - 2) + " 20 19 " + (GROUND - 1) + " 45 air");
            context.waitTicks(60);
            check(context, "lake drained", "LH_Rower", false, 0.5);
        }
    }

    private static void fake(ClientGameTestContext context, String name, double x, double y, double z) {
        context.runOnClient(minecraft -> FarPlayerTracker.get().resting().fake(name, minecraft.player.getGameProfile(),
                SharedPositions.dimensionCode(minecraft.level.dimension()), x, y, z, 180f, 0));
    }

    /**
     * Fails the run unless the player is in a boat (or on dry land, out of the water), within
     * {@code within} blocks across of where they logged off.
     */
    private static void check(ClientGameTestContext context, String step, String name, boolean boat, double within) {
        String wrong = context.computeOnClient(minecraft -> {
            RestingPuppet puppet = puppet(minecraft, name);
            RestingPlayers.Spot spot = spot(name);
            double off = Math.hypot(puppet.getX() - spot.x(), puppet.getZ() - spot.z());
            boolean wet = minecraft.level.getFluidState(BlockPos.containing(puppet.getX(), puppet.getY() + 0.05,
                    puppet.getZ())).is(FluidTags.WATER);
            boolean inBoat = puppet.mount() != null && puppet.look() == RestingPuppet.Look.RIDE;
            Scene.log("shore " + step + ": " + puppet.position() + ", " + off + " blocks off, boat " + inBoat
                    + ", wet " + wet + ", look " + puppet.look() + ", " + puppet.state());
            if (boat != inBoat) return "boat " + inBoat;
            if (!boat && wet) return "in the water";
            if (off > within) return off + " blocks from their spot";
            return null;
        });
        if (wrong != null) throw new AssertionError("shore " + step + ": " + wrong);
    }

    private static RestingPlayers.Spot spot(String name) {
        for (RestingPlayers.Spot spot : FarPlayerTracker.get().resting().spots()) {
            if (spot.name().equals(name)) return spot;
        }
        throw new AssertionError("shore: no " + name);
    }

    private static RestingPuppet puppet(Minecraft minecraft, String name) {
        return FarPlayerTracker.get().resting().puppet(minecraft.level, spot(name));
    }
}
