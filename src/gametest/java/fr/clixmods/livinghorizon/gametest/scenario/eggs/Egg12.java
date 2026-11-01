package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.Glider;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Easter egg 12, by day: a player gliding with an elytra comes far off, across the view, and
 * moves along; seen again from 44 blocks, then left to finish its way, which ends in a dive
 * under the ground, gone within the time its flight takes.
 */
public final class Egg12 {
    /** Ticks its whole flight takes at most (crossing and dive), and a margin. */
    private static final int LONGEST = 760 + 140;
    /** Ticks its whole flight takes at least. */
    private static final int SHORTEST = 440;

    private Egg12() {
    }

    private static Glider egg() {
        return FarPlayerTracker.get().ambience().glider();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a " + 12 * 1000 + " -60 0 0 0");
        context.waitTicks(100);
        Scene.command(world, "time set noon");
        Scene.command(world, "weather clear");
        context.waitTicks(20);
        boolean on = context.computeOnClient(minecraft -> egg().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee12 did not start");
        Vec3 at = await(context);
        int started = 0;
        double across = context.computeOnClient(minecraft -> Math.hypot(at.x - minecraft.player.getX(), at.z - minecraft.player.getZ()));
        Scene.log("eggs2 ee12 at " + at + ", " + (int) across + " blocks");
        if (across < 100) throw new AssertionError("ee12 at " + (int) across + " blocks, too near");
        if (at.y < -50) throw new AssertionError("ee12 starts at height " + at.y + ", not over the ground");
        context.runOnClient(minecraft -> {
            List<? extends Entity> figures = egg().figures();
            if (figures.size() != 1 || !(figures.get(0) instanceof RemotePlayer him)) {
                throw new AssertionError("ee12 shows " + figures + ", not one player");
            }
            if (!him.isFallFlying()) throw new AssertionError("ee12's player does not glide");
            if (!him.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                throw new AssertionError("ee12's player wears no elytra: " + him.getItemBySlot(EquipmentSlot.CHEST));
            }
            lookAt(minecraft.player, at.x, at.y, at.z);
            minecraft.options.fov().set(30);
        });

        // Along its crossing.
        context.waitTicks(100);
        started += 100;
        Vec3 later = context.computeOnClient(minecraft -> egg().position());
        if (later == null) throw new AssertionError("ee12 gone after 5 seconds");
        double moved = later.distanceTo(at);
        Scene.log("eggs2 ee12 moved " + (int) moved + " blocks in 100 ticks, now at " + later);
        if (moved < 15) throw new AssertionError("ee12 moved only " + moved + " blocks in 100 ticks");
        context.runOnClient(minecraft -> lookAt(minecraft.player, later.x, later.y, later.z));
        Scene.screenshot(context, scenario, "ee12-far");
        started += 5;

        // From 44 blocks, on the player's side of it, still farther than where it goes.
        Vec3 now = context.computeOnClient(minecraft -> egg().position());
        if (now == null) throw new AssertionError("ee12 gone mid-flight");
        Vec3 near = context.computeOnClient(minecraft -> {
            double dx = minecraft.player.getX() - now.x, dz = minecraft.player.getZ() - now.z, length = Math.hypot(dx, dz);
            return new Vec3(now.x + dx / length * 44, -60, now.z + dz / length * 44);
        });
        Scene.command(world, "tp @a " + near.x + " -60 " + near.z);
        context.waitTicks(3);
        started += 3;
        context.runOnClient(minecraft -> {
            Vec3 here = egg().position();
            if (here == null) throw new AssertionError("ee12 gone with the player 44 blocks away");
            lookAt(minecraft.player, here.x, here.y + 0.3, here.z);
        });
        Scene.screenshot(context, scenario, "ee12-close");
        started += 5;

        // The rest of its way: the dive, under the ground, then gone.
        double lowest = Double.MAX_VALUE;
        Vec3 last = now;
        while (context.computeOnClient(minecraft -> egg().busy())) {
            if (started > LONGEST) throw new AssertionError("ee12 still there after " + started + " ticks");
            Vec3 where = context.computeOnClient(minecraft -> egg().position());
            if (where != null) {
                last = where;
                lowest = Math.min(lowest, where.y);
            }
            context.waitTick();
            started++;
        }
        Scene.log("eggs2 ee12 gone after " + started + " ticks, last at " + last + ", lowest " + lowest);
        if (started < SHORTEST) throw new AssertionError("ee12 gone after " + started + " ticks, too soon");
        if (lowest > -60) throw new AssertionError("ee12 never went under the ground, lowest at " + lowest);
    }

    /** Where it came, once its search is done: a few seconds at most. */
    private static Vec3 await(ClientGameTestContext context) {
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> egg().position());
            if (at != null) return at;
            context.waitTick();
        }
        throw new AssertionError("ee12 found no place");
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
