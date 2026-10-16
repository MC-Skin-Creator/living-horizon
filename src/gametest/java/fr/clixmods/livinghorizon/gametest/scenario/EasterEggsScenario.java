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
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
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
            // Their commands are for whoever works on them: not there for a player.
            boolean hidden = context.computeOnClient(minecraft -> {
                // The Fabric API renamed ClientCommandManager to ClientCommands on 26.1.
                //? if <26.1 {
                var root = net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.getActiveDispatcher()
                        .getRoot().getChild("livinghorizon");
                //?} else {
                /*var root = net.fabricmc.fabric.api.client.command.v2.ClientCommands.getActiveDispatcher()
                        .getRoot().getChild("livinghorizon");
                *///?}
                var egg = root == null ? null : root.getChild("ee1");
                return egg != null && !egg.canUse(null);
            });
            if (!hidden) throw new AssertionError("the easter eggs' commands are there for players");
            // First, while nothing else may come by chance: no cow is remembered yet.
            calendar(context, world);
            ufo(context, world);
            herobrine(context, world);
            ghosts(context, world);
            pass(context, world);
            waver(context);
            bigfoot(context, world);
            boat(context, world);
        }
    }

    /**
     * The calendar: a day for each dawn; from the second or third, the watcher (4) comes on his
     * own a while into the night, and goes once he is looked in the eyes.
     */
    private void calendar(ClientGameTestContext context, TestSingleplayerContext world) {
        int before = context.computeOnClient(minecraft -> ambience().calendar().days());
        for (int day = 0; day < 3; day++) {
            Scene.command(world, "time set 14000");
            context.waitTicks(5);
            Scene.command(world, "time set 1000");
            context.waitTicks(5);
        }
        int after = context.computeOnClient(minecraft -> ambience().calendar().days());
        Scene.log("eggs calendar " + before + " -> " + after + " days");
        if (after != before + 3) throw new AssertionError("the calendar counted " + (after - before) + " dawns, not 3");
        Scene.command(world, "time set 14000");
        Vec3 at = null;
        for (int i = 0; i < 2400 && at == null; i += 20) {
            context.waitTicks(20);
            at = context.computeOnClient(minecraft -> {
                RemotePlayer him = ambience().herobrine().figure();
                return him == null ? null : him.position();
            });
        }
        if (at == null) throw new AssertionError("ee4 did not come by night");
        Vec3 him = at;
        context.runOnClient(minecraft -> {
            Scene.log("eggs ee4 by night at " + him + ", " + (int) him.distanceTo(minecraft.player.position()) + " blocks");
            lookAt(minecraft.player, him.x, him.y + 1.6, him.z);
            minecraft.options.fov().set(30);
        });
        Scene.screenshot(context, this, "ee4-night");
        context.waitTicks(110);
        if (context.computeOnClient(minecraft -> ambience().herobrine().busy())) {
            throw new AssertionError("ee4 still there 5 seconds after being looked in the eyes");
        }
        Scene.command(world, "time set noon");
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
        Scene.command(world, "tp @a 0 ~ 0 0 0");
        context.waitTicks(20);
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
        // Away to the horizon, slowly: still to be seen fifteen seconds on.
        context.waitTicks(300);
        if (!context.computeOnClient(minecraft -> ambience().ufo().busy())) throw new AssertionError("ee1 gone too soon");
        Scene.screenshot(context, this, "ee1-away");
        stop(context);
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
    }

    /** 4: the watcher, seen from the corner of the eye, looked at, gone. */
    private void herobrine(ClientGameTestContext context, TestSingleplayerContext world) {
        boolean on = context.computeOnClient(minecraft -> ambience().herobrine().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee4 did not start");
        await(context, () -> {
            RemotePlayer him = ambience().herobrine().figure();
            return him == null ? null : him.position();
        }, "ee4");
        // Called again, he goes and stands somewhere else.
        RemotePlayer first = context.computeOnClient(minecraft -> {
            RemotePlayer him = ambience().herobrine().figure();
            ambience().herobrine().summon(minecraft.level, minecraft.player, true);
            return him;
        });
        await(context, () -> {
            RemotePlayer him = ambience().herobrine().figure();
            return him == null || him == first ? null : him.position();
        }, "ee4 again");
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

    /** 3: the ghost, at night, on dry ground only, and gone when the player comes near. */
    private void ghosts(ClientGameTestContext context, TestSingleplayerContext world) {
        Scene.command(world, "tp @a 0 ~ -160 0 0");
        // Water over much of what lies ahead: it walks where the ground is dry.
        Scene.command(world, "execute at @p run fill ~-40 ~-1 ~16 ~40 ~-1 ~96 minecraft:water");
        Scene.command(world, "time set midnight");
        context.waitTicks(20);
        boolean on = context.computeOnClient(minecraft -> ambience().ghosts().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee3 did not start");
        Vec3 first = await(context, () -> ambience().ghosts().position(), "ee3");
        context.runOnClient(minecraft -> {
            Scene.log("eggs ee3 at " + first + ", " + (int) first.distanceTo(minecraft.player.position()) + " blocks");
            lookAt(minecraft.player, first.x, first.y + 1.0, first.z);
            minecraft.options.fov().set(30);
        });
        for (int i = 0; i < 60; i += 5) {
            context.waitTicks(5);
            context.runOnClient(minecraft -> {
                Vec3 at = ambience().ghosts().position();
                if (at == null) throw new AssertionError("ee3 gone too soon");
                int x = Mth.floor(at.x), z = Mth.floor(at.z);
                int top = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (!minecraft.level.getFluidState(new BlockPos(x, top - 1, z)).isEmpty()) {
                    throw new AssertionError("ee3 over water at " + at);
                }
            });
        }
        Scene.screenshot(context, this, "ee3-night");
        Vec3 now = context.computeOnClient(minecraft -> ambience().ghosts().position());
        Scene.command(world, "tp @a " + (now.x + 3) + " ~ " + now.z);
        context.waitTicks(10);
        if (context.computeOnClient(minecraft -> ambience().ghosts().busy())) {
            throw new AssertionError("ee3 still there with the player next to it");
        }
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
        Scene.command(world, "time set noon");
        Scene.command(world, "tp @a 0 ~ -160 0 0");
        context.waitTicks(5);
        Scene.command(world, "execute at @p run fill ~-40 ~-1 ~16 ~40 ~-1 ~96 minecraft:grass_block");
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
        // Away to the horizon, slowly: still to be seen ten seconds on.
        context.waitTicks(200);
        if (!context.computeOnClient(minecraft -> ambience().pass().busy())) throw new AssertionError("ee2 gone too soon");
        Scene.screenshot(context, this, "ee2-away");
        stop(context);
        context.runOnClient(minecraft -> {
            minecraft.options.renderDistance().set(16);
            minecraft.player.setXRot(0f);
        });
    }

    /** 5: on the highest point ahead (all the same here), waving from the corner of the eye, gone once looked at. */
    private void waver(ClientGameTestContext context) {
        boolean on = context.computeOnClient(minecraft -> ambience().waver().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee5 did not start");
        Vec3 at = await(context, () -> ambience().waver().position(), "ee5");
        context.runOnClient(minecraft -> {
            Scene.log("eggs ee5 at " + at + ", " + (int) at.distanceTo(minecraft.player.position()) + " blocks");
            // Six degrees to the side: in view, not looked at.
            lookAt(minecraft.player, at.x, at.y + 1.2, at.z);
            minecraft.player.setYRot(minecraft.player.getYRot() + 6f);
            minecraft.options.fov().set(30);
        });
        context.waitTicks(45);
        if (!context.computeOnClient(minecraft -> ambience().waver().busy())) throw new AssertionError("ee5 gone before being looked at");
        Scene.screenshot(context, this, "ee5-wave");
        context.runOnClient(minecraft -> lookAt(minecraft.player, at.x, at.y + 1.2, at.z));
        context.waitTicks(3);
        if (context.computeOnClient(minecraft -> ambience().waver().busy())) throw new AssertionError("ee5 still there once looked at");
        Scene.screenshot(context, this, "ee5-gone");
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
    }

    /** 6: far off in a forest the test makes ahead, and gone behind the first thing that hides it. */
    private void bigfoot(ClientGameTestContext context, TestSingleplayerContext world) {
        Scene.command(world, "tp @a 0 ~ -160 0 0");
        // In strips, each under the game's limit on how many blocks one command changes.
        for (int z = 65; z < 195; z += 15) {
            Scene.command(world, "execute at @p run fillbiome ~-130 -64 ~" + z + " ~130 -57 ~" + (z + 14) + " minecraft:forest");
        }
        context.waitTicks(40);
        boolean on = context.computeOnClient(minecraft -> ambience().bigfoot().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee6 found no forest");
        context.waitTicks(80);
        context.runOnClient(minecraft -> {
            Vec3 now = ambience().bigfoot().position();
            if (now == null) throw new AssertionError("ee6 gone too soon");
            Scene.log("eggs ee6 at " + now + ", " + (int) now.distanceTo(minecraft.player.position()) + " blocks");
            lookAt(minecraft.player, now.x, now.y + 1.4, now.z);
            minecraft.options.fov().set(30);
        });
        Scene.screenshot(context, this, "ee6-walk");
        // A wall in front of the player, between them: it has been seen long enough, and goes.
        Scene.command(world, "execute at @p run fill ~-8 ~ ~2 ~8 ~5 ~2 minecraft:stone");
        context.waitTicks(15);
        if (context.computeOnClient(minecraft -> ambience().bigfoot().busy())) {
            throw new AssertionError("ee6 still there behind a wall");
        }
        Scene.command(world, "execute at @p run fill ~-8 ~ ~2 ~8 ~5 ~2 minecraft:air");
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
        Scene.command(world, "tp @a 0 ~ -160 0 0");
        context.waitTicks(20);
    }

    /** 7: on a lake dug ahead, at night, and gone when the player comes near. */
    private void boat(ClientGameTestContext context, TestSingleplayerContext world) {
        Scene.command(world, "execute at @p run fill ~-60 ~-1 ~30 ~60 ~-1 ~130 minecraft:water");
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

    /** Ends whatever easter egg is under way: the switch off and on again. */
    private static void stop(ClientGameTestContext context) {
        Scene.configure(context, config -> config.easterEggs = false);
        context.waitTicks(2);
        Scene.configure(context, config -> config.easterEggs = true);
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
