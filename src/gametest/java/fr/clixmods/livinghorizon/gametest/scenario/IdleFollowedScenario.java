package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Animals the single player server still simulates, far from the player: their copies walk.
 *
 * <p>The game stops an animal wandering once no player has come within 32 blocks of it for
 * five seconds: it stands and looks around. A copy that only followed it would turn on the
 * spot for good. Cows with their AI on are left 120 blocks away, past the 80 blocks a cow is
 * sent at with "Entity Distance" at half and well inside the simulation distance: most of
 * their copies must walk. Then the mobs are forgotten, the server's shared positions bring
 * them back at once, and they must walk again.
 */
public final class IdleFollowedScenario implements Scenario {
    private static final int COWS = 8;

    @Override
    public String name() {
        return "idlefollowed";
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
            for (int i = 0; i < COWS; i++) {
                Scene.command(world, "execute at @p run summon cow ~" + (i * 6 - COWS * 3) + " ~ ~40");
            }
            context.waitTicks(60);
            Scene.command(world, "tp @a 0 ~ -80 0 0");
            // The server stops them wandering five seconds after the player left.
            context.waitTicks(300);
            check(context, "followed");
            Scene.screenshot(context, this, "followed");

            context.runOnClient(minecraft -> FarPlayerTracker.get().mobs().forgetAll());
            context.waitTicks(100);
            check(context, "forgotten");
            Scene.screenshot(context, this, "forgotten");
        }
    }

    /** Most copies walk a few blocks within half a minute. */
    private static void check(ClientGameTestContext context, String when) {
        Map<UUID, Vec3> last = new HashMap<>();
        Map<UUID, Double> walked = new HashMap<>();
        for (int k = 0; k <= 60; k++) {
            context.runOnClient(minecraft -> {
                for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                    Entity puppet = mob.puppet();
                    if (puppet == null || !mob.type().equals("minecraft:cow")) continue;
                    Vec3 now = puppet.position(), before = last.put(mob.id(), now);
                    if (before != null) walked.merge(mob.id(), Math.hypot(now.x - before.x, now.z - before.z), Double::sum);
                }
            });
            context.waitTicks(10);
        }
        long walking = walked.values().stream().filter(d -> d > 2).count();
        Scene.log("idlefollowed " + when + ": " + last.size() + " copies, " + walking + " walked, distances " + walked.values());
        if (last.size() < COWS / 2) throw new AssertionError("Only " + last.size() + " cows have a copy (" + when + ")");
        if (walking < last.size() / 2) {
            throw new AssertionError("Only " + walking + " of " + last.size() + " copies walked (" + when + ")");
        }
    }
}
