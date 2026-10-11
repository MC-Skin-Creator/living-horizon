package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.ambient.Ambience;
import fr.clixmods.livinghorizon.ambient.Ufo;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The easter eggs, each called the way {@code /livinghorizon eeN} calls it, one after the
 * other: the saucer over a remembered cow (1), the pass across the sky (2), the ghosts (3)
 * and the watcher (4). Screenshots of each, and checks that they go when they should.
 *
 * <p>The cows must be remembered, so the scene is the one of {@link RememberedMobsScenario}:
 * met up close, then left 200 blocks behind.
 */
public final class EasterEggsScenario implements Scenario {
    @Override
    public String name() {
        return "eggs";
    }

    private static Ambience ambience() {
        return FarPlayerTracker.get().ambience();
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.easterEggs = true;
            config.impostors = false;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Farther once the world is there: the loading screen would wait for all of it.
            context.runOnClient(minecraft -> {
                minecraft.options.entityDistanceScaling().set(0.5);
                minecraft.options.renderDistance().set(16);
            });
            context.waitTicks(60);
            ufo(context, world);
            herobrine(context, world);
            ghosts(context, world);
            pass(context, world);
            waver(context);
            bigfoot(context, world);
            boat(context, world);
        }
    }

    /** 1: a cow beside a remembered one, taken up. */
    private void ufo(ClientGameTestContext context, TestSingleplayerContext world) {
        Scene.row(world, new String[]{"cow"}, 3, 40, 6.0);
        context.waitTicks(100);
        Scene.command(world, "tp @a 0 ~ -160 0 0");
        context.waitTicks(300);
        Ufo.Call call = context.computeOnClient(minecraft -> {
            for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                Entity puppet = mob.puppet();
                Scene.log("eggs remembered " + mob.type() + " at " + (puppet == null ? "nowhere" : puppet.position()));
            }
            return ambience().ufo().summon(minecraft.level, minecraft.player, true);
        });
        Scene.log("eggs ee1 " + call);
        if (call == Ufo.Call.NOTHING) throw new AssertionError("ee1 found no remembered cow");
        context.runOnClient(minecraft -> minecraft.options.fov().set(30));
        context.waitTicks(290);
        Scene.screenshot(context, this, "ee1-beam");
        context.waitTicks(120);
        context.runOnClient(minecraft -> Scene.log("eggs ee1 cow taken at "
                + (ambience().ufo().cow() == null ? "nowhere" : ambience().ufo().cow().position())));
        Scene.screenshot(context, this, "ee1-lift");
        for (int i = 0; i < 600 && context.computeOnClient(minecraft -> ambience().ufo().busy()); i += 20) {
            context.waitTicks(20);
        }
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
    }

    /** 4: the watcher, seen from the corner of the eye, looked at, gone. */
    private void herobrine(ClientGameTestContext context, TestSingleplayerContext world) {
        boolean on = context.computeOnClient(minecraft -> ambience().herobrine().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee4 found no spot");
        context.waitTicks(10);
        Scene.screenshot(context, this, "ee4-corner");
        // Closer, 28 blocks (he goes under 24), looking at him: his eyes. Three degrees to the
        // side keep the crosshair off his face and still count as looking at him (8).
        String spot = context.computeOnClient(minecraft -> {
            RemotePlayer him = ambience().herobrine().figure();
            if (him == null) throw new AssertionError("ee4 gone before being looked at");
            Scene.log("eggs ee4 at " + him.position() + ", " + (int) him.distanceTo(minecraft.player) + " blocks");
            Vec3 away = minecraft.player.position().subtract(him.position()).multiply(1, 0, 1).normalize();
            Vec3 at = him.position().add(away.scale(28));
            double dx = him.getX() - at.x, dz = him.getZ() - at.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + 3f;
            float pitch = (float) -Math.toDegrees(Math.atan2(him.getY() + 1.6 - (at.y + 1.62), Math.hypot(dx, dz)));
            minecraft.options.fov().set(30);
            return String.format(java.util.Locale.ROOT, "%.3f %.3f %.3f %.2f %.2f", at.x, at.y, at.z, yaw, pitch);
        });
        Scene.command(world, "tp @a " + spot);
        Scene.screenshot(context, this, "ee4-looked-at");
        context.waitTicks(110);
        if (context.computeOnClient(minecraft -> ambience().herobrine().busy())) {
            throw new AssertionError("ee4 still there 5 seconds after being looked at");
        }
        Scene.screenshot(context, this, "ee4-gone");
        context.runOnClient(minecraft -> {
            minecraft.options.fov().set(70);
            minecraft.player.setXRot(0f);
            minecraft.player.setYRot(0f);
        });
    }

    /** 3: the ghosts, at night, and gone when the player comes near. */
    private void ghosts(ClientGameTestContext context, TestSingleplayerContext world) {
        Scene.command(world, "time set midnight");
        boolean on = context.computeOnClient(minecraft -> ambience().ghosts().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee3 found no spot");
        context.runOnClient(minecraft -> minecraft.options.fov().set(40));
        context.waitTicks(60);
        Scene.screenshot(context, this, "ee3-night");
        Scene.command(world, "time set noon");
        context.waitTicks(20);
        Scene.screenshot(context, this, "ee3-day");
        Vec3 first = context.computeOnClient(minecraft -> {
            RemotePlayer leader = ambience().ghosts().figures().get(0);
            Scene.log("eggs ee3 " + ambience().ghosts().figures().size() + " at " + leader.position());
            return leader.position();
        });
        Scene.command(world, "tp @a " + (first.x + 3) + " ~ " + first.z);
        context.waitTicks(10);
        if (context.computeOnClient(minecraft -> ambience().ghosts().busy())) {
            throw new AssertionError("ee3 still there with the player next to them");
        }
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
        Scene.command(world, "tp @a 0 ~ -160 0 0");
        context.waitTicks(20);
    }

    /** 2: the pass across the sky; the fog of 32 chunks lets it be seen. */
    private void pass(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(minecraft -> {
            minecraft.options.renderDistance().set(32);
            minecraft.player.setXRot(-20f);
        });
        boolean on = context.computeOnClient(minecraft -> ambience().pass().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee2 did not start");
        context.waitTicks(100);
        Scene.screenshot(context, this, "ee2-cruise");
        context.waitTicks(110);
        Scene.screenshot(context, this, "ee2-stop");
        for (int i = 0; i < 400 && context.computeOnClient(minecraft -> ambience().pass().busy()); i += 20) {
            context.waitTicks(20);
        }
        if (context.computeOnClient(minecraft -> ambience().pass().busy())) throw new AssertionError("ee2 never left");
        context.runOnClient(minecraft -> {
            minecraft.options.renderDistance().set(16);
            minecraft.player.setXRot(0f);
        });
    }

    /** 5: on the highest point ahead (all the same here), waving, then gone. */
    private void waver(ClientGameTestContext context) {
        boolean on = context.computeOnClient(minecraft -> ambience().waver().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee5 did not start");
        Vec3 at = await(context, () -> ambience().waver().position(), "ee5");
        context.runOnClient(minecraft -> {
            Scene.log("eggs ee5 at " + at + ", " + (int) at.distanceTo(minecraft.player.position()) + " blocks");
            lookAt(minecraft.player, at.x, at.y + 1.2, at.z);
            minecraft.options.fov().set(30);
        });
        context.waitTicks(45);
        Scene.screenshot(context, this, "ee5-wave");
        context.waitTicks(80);
        if (context.computeOnClient(minecraft -> ambience().waver().busy())) throw new AssertionError("ee5 never left");
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
    }

    /** 6: walking across, no forest here, and gone when the player comes near. */
    private void bigfoot(ClientGameTestContext context, TestSingleplayerContext world) {
        boolean on = context.computeOnClient(minecraft -> ambience().bigfoot().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee6 found no ground");
        context.waitTicks(80);
        Vec3 at = context.computeOnClient(minecraft -> {
            Vec3 now = ambience().bigfoot().position();
            if (now == null) throw new AssertionError("ee6 gone too soon");
            Scene.log("eggs ee6 at " + now + ", " + (int) now.distanceTo(minecraft.player.position()) + " blocks");
            lookAt(minecraft.player, now.x, now.y + 1.4, now.z);
            minecraft.options.fov().set(30);
            return now;
        });
        Scene.screenshot(context, this, "ee6-walk");
        Scene.command(world, "tp @a " + (at.x + 4) + " ~ " + at.z);
        context.waitTicks(10);
        if (context.computeOnClient(minecraft -> ambience().bigfoot().busy())) {
            throw new AssertionError("ee6 still there with the player next to it");
        }
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
        Scene.command(world, "tp @a 0 ~ -160 0 0");
        context.waitTicks(20);
    }

    /** 7: on a lake dug ahead, at night, and gone when the player comes near. */
    private void boat(ClientGameTestContext context, TestSingleplayerContext world) {
        Scene.command(world, "execute at @p run fill ~-30 ~-1 ~30 ~30 ~-1 ~90 minecraft:water");
        Scene.command(world, "time set midnight");
        context.waitTicks(20);
        boolean on = context.computeOnClient(minecraft -> ambience().ghostBoat().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee7 did not start");
        Vec3 at = await(context, () -> ambience().ghostBoat().position(), "ee7");
        context.runOnClient(minecraft -> {
            Scene.log("eggs ee7 at " + at + ", " + (int) at.distanceTo(minecraft.player.position()) + " blocks");
            lookAt(minecraft.player, at.x, at.y + 0.6, at.z);
            minecraft.options.fov().set(30);
        });
        context.waitTicks(20);
        Scene.screenshot(context, this, "ee7-night");
        Scene.command(world, "tp @a " + (at.x + 6) + " ~ " + (at.z - 6));
        context.waitTicks(10);
        if (context.computeOnClient(minecraft -> ambience().ghostBoat().busy())) {
            throw new AssertionError("ee7 still there with the player next to it");
        }
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
        Scene.command(world, "time set noon");
    }

    /** What an easter egg that looks for its place found, once it has: a few seconds at most. */
    private static Vec3 await(ClientGameTestContext context, java.util.function.Supplier<Vec3> where, String egg) {
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> where.get());
            if (at != null) return at;
            context.waitTick();
        }
        throw new AssertionError(egg + " found no place");
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
