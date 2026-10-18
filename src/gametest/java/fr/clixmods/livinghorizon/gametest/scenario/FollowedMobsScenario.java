package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A mob the single player server no longer sends but still moves: its copy follows it.
 *
 * <p>With "Entity Distance" at half, a cow is sent up to 80 blocks away; the simulation
 * distance, 12 chunks like the render distance, keeps it moving well beyond. The player steps back 120 blocks, the
 * server moves the cow 12 blocks, and its copy must stand where the cow now is. Then the
 * cow dies there, and its copy must go.
 */
public final class FollowedMobsScenario implements Scenario {
    private static final double MOVE = 12;

    @Override
    public String name() {
        return "followed";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.simulationDistance().set(12);
            minecraft.options.fov().set(30);
        });
        Scene.configure(context, config -> config.hideOccludedMobs = false);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.summon(world, "cow", 0, 40, 90);
            context.waitTicks(60);
            // Past the 80 blocks a cow is sent at, well inside the simulation distance.
            Scene.command(world, "tp @a 0 ~ -80 0 0");
            context.waitTicks(80);
            Vec3 before = copy(context, "before");
            if (before == null) throw new AssertionError("The cow has no copy once the server stops sending it");

            Scene.command(world, "execute as @e[type=cow] at @s run tp @s ~" + MOVE + " ~ ~");
            context.waitTicks(80);
            Vec3 after = copy(context, "after");
            if (after == null) throw new AssertionError("The cow's copy is gone after the cow moved");
            double moved = after.x - before.x;
            Scene.log("followed copy moved " + moved + " blocks for " + MOVE);
            if (Math.abs(moved - MOVE) > 0.5) {
                throw new AssertionError("The cow's copy did not follow the cow: moved " + moved + " blocks for " + MOVE);
            }
            Scene.screenshot(context, this, "moved");

            Scene.command(world, "kill @e[type=cow]");
            context.waitTicks(60);
            if (copy(context, "killed") != null) throw new AssertionError("The cow died and its copy stays");
        }
    }

    /** Where the cow's copy is drawn, or null when there is none. */
    private static Vec3 copy(ClientGameTestContext context, String when) {
        return context.computeOnClient(minecraft -> {
            for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                Entity puppet = mob.puppet();
                if (mob.type().equals("minecraft:cow") && puppet != null) {
                    Scene.log("followed copy " + when + " at " + puppet.position());
                    return puppet.position();
                }
            }
            Scene.log("followed no copy " + when);
            return null;
        });
    }
}
