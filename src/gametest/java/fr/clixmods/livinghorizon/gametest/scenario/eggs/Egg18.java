package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.BirdShape;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Easter egg 18, on a clear day: a flock comes in from the side far ahead, its birds closing
 * in on their places until the face is formed, held a few seconds, then scattered and gone.
 */
public final class Egg18 {
    /** Ticks the birds take to their places at most, and a margin. */
    private static final int GATHER = 210 + 60;
    /** Ticks the face holds and the scattered birds take to go, and a margin. */
    private static final int AFTER = 70 + 120 + 60;

    private Egg18() {
    }

    private static BirdShape egg() {
        return FarPlayerTracker.get().ambience().birdShape();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a " + 18 * 1000 + " -60 0 0 0");
        context.waitTicks(100);
        Scene.command(world, "time set noon");
        Scene.command(world, "weather clear");
        context.waitTicks(20);
        boolean on = context.computeOnClient(minecraft -> egg().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee18 did not start");
        Vec3 at = await(context);
        double across = context.computeOnClient(minecraft -> Math.hypot(at.x - minecraft.player.getX(), at.z - minecraft.player.getZ()));
        double first = context.computeOnClient(minecraft -> egg().offSlots());
        Scene.log("eggs2 ee18 at " + at + ", " + (int) across + " blocks, the farthest bird " + (int) first + " blocks from its place");
        if (across < 120) throw new AssertionError("ee18 at " + (int) across + " blocks, too near");
        if (first < 40) throw new AssertionError("ee18's birds start only " + first + " blocks from their places");
        context.runOnClient(minecraft -> {
            lookAt(minecraft.player, at.x, at.y, at.z);
            minecraft.options.fov().set(30);
        });
        context.waitTicks(60);
        double halfway = context.computeOnClient(minecraft -> egg().offSlots());
        Scene.log("eggs2 ee18 after 3 seconds, the farthest bird " + (int) halfway + " blocks from its place");
        if (halfway >= first) throw new AssertionError("ee18's birds do not come closer: " + first + " then " + halfway);
        Scene.screenshot(context, scenario, "ee18-coming");

        // Formed: every bird on its pixel.
        int waited = 65;
        while (!context.computeOnClient(minecraft -> egg().formed())) {
            if (++waited > GATHER) throw new AssertionError("ee18 not formed after " + waited + " ticks");
            context.waitTick();
        }
        double off = context.computeOnClient(minecraft -> egg().offSlots());
        Scene.log("eggs2 ee18 formed after " + waited + " ticks, the farthest bird " + off + " blocks from its place");
        if (off > 1) throw new AssertionError("ee18 formed with a bird " + off + " blocks from its place");
        Scene.screenshot(context, scenario, "ee18-face");

        // Scattered, then gone.
        int after = 5;
        while (context.computeOnClient(minecraft -> egg().busy())) {
            if (++after > AFTER) throw new AssertionError("ee18 still there " + after + " ticks after it formed");
            if (after == 110) {
                double spread = context.computeOnClient(minecraft -> egg().offSlots());
                Scene.log("eggs2 ee18 scattering, the farthest bird " + (int) spread + " blocks from its place");
                if (spread < 5) throw new AssertionError("ee18 not scattering 110 ticks after it formed");
                Scene.screenshot(context, scenario, "ee18-scatter");
                after += 5;
            }
            context.waitTick();
        }
        Scene.log("eggs2 ee18 gone " + after + " ticks after it formed");
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
    }

    /** Where it came, once its search is done: a few seconds at most. */
    private static Vec3 await(ClientGameTestContext context) {
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> egg().position());
            if (at != null) return at;
            context.waitTick();
        }
        throw new AssertionError("ee18 found no place");
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
