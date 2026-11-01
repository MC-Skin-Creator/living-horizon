package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.Mirage;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Easter egg 14, by day, in a desert of sand the test makes ahead: it comes far off, grows
 * paler as the player is brought nearer, and is gone once they are within 80 blocks.
 */
public final class Egg14 {
    private Egg14() {
    }

    private static Mirage egg() {
        return FarPlayerTracker.get().ambience().mirage();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a " + 14 * 1000 + " -60 0 0 0");
        context.waitTicks(100);
        Scene.command(world, "time set noon");
        Scene.command(world, "weather clear");
        // In strips, each under the game's limit on how many blocks one command changes.
        for (int z = 105; z < 240; z += 20) {
            Scene.command(world, "execute at @p run fillbiome ~-85 -64 ~" + z + " ~85 -57 ~" + (z + 19) + " minecraft:desert");
        }
        Scene.command(world, "execute at @p run fill ~-80 ~-1 ~110 ~80 ~-1 ~235 minecraft:sand");
        context.waitTicks(40);
        boolean on = context.computeOnClient(minecraft -> egg().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee14 did not start");
        Vec3 at = await(context, "ee14");
        double start = context.computeOnClient(minecraft -> Math.hypot(at.x - minecraft.player.getX(), at.z - minecraft.player.getZ()));
        Scene.log("eggs2 ee14 at " + at + ", " + (int) start + " blocks");
        if (start < 115) throw new AssertionError("ee14 at " + (int) start + " blocks, too near");
        context.runOnClient(minecraft -> {
            lookAt(minecraft.player, at.x, at.y + 3, at.z);
            minecraft.options.fov().set(30);
        });
        // Shown in full once it has come, the player still where they were.
        context.waitTicks(70);
        float full = context.computeOnClient(minecraft -> egg().shown());
        Scene.log("eggs2 ee14 shown " + full + " from " + (int) start + " blocks");
        if (full < 0.9f) throw new AssertionError("ee14 shown only " + full + " from where it came");
        Scene.screenshot(context, scenario, "ee14-far");

        // Brought to 80 % of that distance: halfway between full and gone (at 60 %).
        Vec3 nearer = context.computeOnClient(minecraft -> toward(minecraft.player.position(), at, 0.8));
        Scene.command(world, "tp @a " + nearer.x + " ~ " + nearer.z);
        context.waitTicks(5);
        context.runOnClient(minecraft -> lookAt(minecraft.player, at.x, at.y + 3, at.z));
        float paler = context.computeOnClient(minecraft -> egg().shown());
        Scene.log("eggs2 ee14 shown " + paler + " at " + (int) (start * 0.8) + " blocks");
        if (!context.computeOnClient(minecraft -> egg().busy())) throw new AssertionError("ee14 gone at 80 % of its distance");
        if (paler <= 0.2f || paler >= 0.8f) throw new AssertionError("ee14 shown " + paler + " at 80 % of its distance, not paler");
        Scene.screenshot(context, scenario, "ee14-nearer");

        // Within 80 blocks: gone.
        Vec3 near = context.computeOnClient(minecraft -> toward(minecraft.player.position(), at, 50 / Math.hypot(
                at.x - minecraft.player.getX(), at.z - minecraft.player.getZ())));
        Scene.command(world, "tp @a " + near.x + " ~ " + near.z);
        context.waitTicks(10);
        if (context.computeOnClient(minecraft -> egg().busy())) throw new AssertionError("ee14 still there 50 blocks away");
        Scene.screenshot(context, scenario, "ee14-gone");
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
    }

    /** The point {@code share} of the way from {@code to} back to {@code from}, across. */
    private static Vec3 toward(Vec3 from, Vec3 to, double share) {
        return new Vec3(to.x + (from.x - to.x) * share, from.y, to.z + (from.z - to.z) * share);
    }

    /** Where it came, once its search is done: a few seconds at most. */
    private static Vec3 await(ClientGameTestContext context, String egg) {
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> egg().position());
            if (at != null) {
                drawn(context, at, egg);
                return at;
            }
            context.waitTick();
        }
        throw new AssertionError(egg + " found no place");
    }

    /** Standing where the game draws the ground, on it: no far world here to draw farther. */
    private static void drawn(ClientGameTestContext context, Vec3 at, String egg) {
        context.runOnClient(minecraft -> {
            double across = Math.hypot(at.x - minecraft.player.getX(), at.z - minecraft.player.getZ());
            double edge = minecraft.options.getEffectiveRenderDistance() * 16;
            if (across > edge - 16) throw new AssertionError(egg + " at " + (int) across + " blocks, past the "
                    + (int) edge + " drawn");
            int ground = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
            if (at.y <= minecraft.level.getMinY() || Math.abs(at.y - ground) > 3) {
                throw new AssertionError(egg + " at height " + at.y + ", the ground there at " + ground);
            }
        });
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
