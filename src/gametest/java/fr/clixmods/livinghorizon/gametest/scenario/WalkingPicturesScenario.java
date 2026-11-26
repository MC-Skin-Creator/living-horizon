package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.render.impostor.ImpostorWalk;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.ui.ImpostorScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Walking mobs drawn as pictures keep walking ({@code impostorAnimation}): a row of mobs past
 * the game's entity distance, walked to and fro by the server, shown as pictures. Their walk is
 * baked after their resting pictures; screenshots at two moments of a stride show the legs in
 * two places, one with the mobs stopped shows them at rest, and the atlas shows each look three
 * times: at rest, one leg forward, the other.
 */
public final class WalkingPicturesScenario implements Scenario {
    /**
     * The row: mobs, and from 1.21.9 a mannequin, one of the other entities, which walks on its legs
     * as a player does: its walk is baked too.
     */
    //? if >=1.21.9 {
    private static final String[] MOBS = {"cow", "pig", "sheep", "horse", "villager", "creeper", "wolf", "iron_golem",
            "mannequin", "cow"};
    //?} else
    /*private static final String[] MOBS = {"cow", "pig", "sheep", "horse", "villager", "creeper", "wolf", "iron_golem", "cow"};*/
    /** Blocks a mob walks a tick: about a strolling mob, legs at four times that. */
    private static final double PACE = 0.1;
    /** Ticks walked one way before turning back, so the row stays in view. */
    private static final int LEG = 60;
    /**
     * Blocks ahead of the player: within the 80 blocks the server still sends mobs with Entity
     * Distance at 0.5, past where the game draws the smaller ones itself (the horse and the iron
     * golem stay its models there). Farther, the mod draws remembered copies, which walk paths
     * of their own.
     */
    private static final int ROW = 66;

    private int walked;

    @Override
    public String name() {
        return "walkingpictures";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.entityDistanceScaling().set(0.5));
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorAnimation = true;
            config.impostorDistance = 32;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
            config.optStillTiny = false;
        });
        walked = 0;
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            for (int i = 0; i < MOBS.length; i++) Scene.summon(world, MOBS[i], (i - (MOBS.length - 1) / 2.0) * 3.6, ROW, -90);
            context.waitTicks(40);

            // Walked long enough for their resting pictures, then their walk, to be baked.
            walk(context, world, 400);
            boolean failed = context.computeOnClient(minecraft -> {
                Scene.log("walkingpictures baked=" + ImpostorAtlas.sheets() + " waiting=" + ImpostorAtlas.waiting()
                        + " failed=" + ImpostorAtlas.failed() + " mob memory=" + ImpostorAtlas.memory(false) / 1024 + " KB");
                return ImpostorAtlas.failed();
            });
            if (failed) throw new AssertionError("The impostor bake failed, see the log");
            int live = context.computeOnClient(minecraft -> row(minecraft).size());
            if (live < MOBS.length - 1) throw new AssertionError("Only " + live + " mobs of the row are sent");
            List<String> missing = context.computeOnClient(WalkingPicturesScenario::unbaked);
            Scene.log("walkingpictures walks not baked: " + missing);
            if (missing.contains("minecraft:cow")) throw new AssertionError("The cow's walk was not baked: " + missing);
            // Two cows of one look: its resting picture and its walk come from one of them, not one each.
            String cows = context.computeOnClient(minecraft -> {
                ImpostorKey key = ImpostorKey.of(row(minecraft).get(0));
                return ImpostorAtlas.walker(key) + " " + ImpostorAtlas.bakedFrom(key);
            });
            Scene.log("walkingpictures cows' walk from, resting picture from: " + cows);
            String[] from = cows.split(" ");
            if (!from[0].equals(from[1])) throw new AssertionError("A look's walk and resting picture show two mobs: " + cows);
            //? if >=1.21.9 {
            if (missing.contains("minecraft:mannequin")) throw new AssertionError("The mannequin's walk was not baked");
            //?}

            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            // Two moments of a stride: the cow's legs one way, then the other.
            int[] first = shotAt(context, world, 1, "walk-a");
            int[] second = shotAt(context, world, ImpostorWalk.FRAMES, "walk-b");
            Scene.log("walkingpictures frames at walk-a " + java.util.Arrays.toString(first)
                    + ", at walk-b " + java.util.Arrays.toString(second));
            walk(context, world, 2);
            int striding = context.computeOnClient(minecraft -> GhostRenderer.striding());
            Scene.log("walkingpictures pictures striding while walking: " + striding);

            // Past Animated Picture Distance: walking, yet standing in their pictures.
            Scene.configure(context, config -> config.impostorAnimationDistance = 32);
            walk(context, world, 20);
            int past = context.computeOnClient(minecraft -> GhostRenderer.striding());
            Scene.log("walkingpictures pictures striding past the distance: " + past);
            if (past != 0) throw new AssertionError(past + " pictures stride past Animated Picture Distance");
            Scene.configure(context, config -> config.impostorAnimationDistance = new fr.clixmods.livinghorizon.FarConfig()
                    .impostorAnimationDistance);
            walk(context, world, 20);
            int back = context.computeOnClient(minecraft -> GhostRenderer.striding());
            Scene.log("walkingpictures pictures striding back within it: " + back);

            // The preview: one row per mob, marked animated, its views stepping through its walk.
            context.setScreen(() -> new ImpostorScreen(null));
            Scene.screenshot(context, this, "preview-a");
            context.waitTicks(5);
            Scene.screenshot(context, this, "preview-b");
            context.setScreen(() -> null);

            // Stopped: their legs come to rest, and so do their pictures.
            context.waitTicks(40);
            int[] still = context.computeOnClient(WalkingPicturesScenario::frames);
            Scene.log("walkingpictures frames standing " + java.util.Arrays.toString(still));
            if (still.length < MOBS.length - 1) throw new AssertionError("The row is no longer sent: " + still.length);
            for (int frame : still) {
                if (frame != 0) throw new AssertionError("A mob standing still shows a picture of its walk: "
                        + java.util.Arrays.toString(still));
            }
            Scene.screenshot(context, this, "standing");
            // The same mobs as their models, standing too, to compare their pose with the pictures'.
            Scene.configure(context, config -> config.impostors = false);
            Scene.screenshot(context, this, "standing-models");
            Scene.configure(context, config -> config.impostors = true);
            context.runOnClient(minecraft ->
                    Scene.log("walkingpictures drawn impostors=" + PolygonStats.impostors() + " models=" + PolygonStats.models()));

            // Off: one picture, standing still, even walking.
            Scene.configure(context, config -> config.impostorAnimation = false);
            walk(context, world, 20);
            context.takeScreenshot(name() + "-off");
            int off = context.computeOnClient(minecraft -> GhostRenderer.striding());
            if (off != 0) throw new AssertionError(off + " pictures stride with Animated Pictures off");
            Scene.configure(context, config -> config.impostorAnimation = true);

            context.runOnClient(minecraft -> minecraft.options.fov().set(70));
            Scene.screenshotAtlas(context, this);
        }
    }

    /**
     * Walks until the cow shows picture {@code frame} of its walk, then takes a screenshot at
     * once, before its legs slow down; the picture each mob shows then.
     */
    private int[] shotAt(ClientGameTestContext context, TestSingleplayerContext world, int frame, String shot) {
        for (int tick = 0; tick < 200; tick++) {
            walk(context, world, 1);
            int[] frames = context.computeOnClient(WalkingPicturesScenario::frames);
            if (frames.length > 0 && frames[0] == frame) {
                context.takeScreenshot(name() + "-" + shot);
                return frames;
            }
        }
        throw new AssertionError("The cow never showed picture " + frame + " of its walk");
    }

    /** {@code ticks} ticks of the server walking every mob along x, turning back every {@link #LEG} ticks. */
    private void walk(ClientGameTestContext context, TestSingleplayerContext world, int ticks) {
        for (int i = 0; i < ticks; i++, walked++) {
            boolean back = walked / LEG % 2 == 1;
            float yaw = back ? 90f : -90f;
            double step = back ? -PACE : PACE;
            world.getServer().runOnServer(server -> {
                for (Entity entity : server.overworld().getAllEntities()) {
                    if (!(entity instanceof LivingEntity mob) || entity instanceof Player) continue;
                    mob.setPos(mob.getX() + step, mob.getY(), mob.getZ());
                    mob.setYRot(yaw);
                    mob.setYBodyRot(yaw);
                    mob.setYHeadRot(yaw);
                }
            });
            context.waitTick();
        }
    }

    /** The mobs of the row, west to east, the cow first. */
    private static List<LivingEntity> row(Minecraft minecraft) {
        List<LivingEntity> row = new ArrayList<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && !(entity instanceof Player)) row.add(living);
        }
        row.sort((a, b) -> Double.compare(a.getX(), b.getX()));
        return row;
    }

    /** The picture of its walk each mob of the row shows now, as the renderer picks it. */
    private static int[] frames(Minecraft minecraft) {
        List<LivingEntity> row = row(minecraft);
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int[] frames = new int[row.size()];
        for (int i = 0; i < frames.length; i++) {
            LivingEntity mob = row.get(i);
            frames[i] = ImpostorWalk.frame(mob.walkAnimation.position(partialTick), mob.walkAnimation.speed(partialTick));
        }
        return frames;
    }

    /** The kinds of the row whose walk is not baked yet, every picture of it. */
    private static List<String> unbaked(Minecraft minecraft) {
        List<String> missing = new ArrayList<>();
        for (LivingEntity mob : row(minecraft)) {
            ImpostorKey key = ImpostorKey.of(mob);
            for (int frame = 1; frame <= ImpostorWalk.FRAMES; frame++) {
                if (ImpostorAtlas.sheet(ImpostorKey.frame(key, frame)) == null) {
                    missing.add(key.type());
                    break;
                }
            }
        }
        return missing;
    }
}
