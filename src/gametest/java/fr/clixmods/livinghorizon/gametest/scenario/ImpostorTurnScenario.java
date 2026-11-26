package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/**
 * Models turning to their picture's angle as they near the impostor distance: a row of mobs,
 * each turned about 20 degrees off a picture's angle, and the camera walking back from 36 to
 * 50 blocks (the switch at 44) and forward again, one screenshot per step. Played one after
 * another, the frames show the mobs turning, then the pictures taking over without a jump.
 * Each screenshot's name gives the distance and how many mobs were pictures.
 */
public final class ImpostorTurnScenario implements Scenario {
    private static final String[] MOBS = {"cow", "villager", "sheep", "pig", "cow", "villager"};
    /** Each 20 degrees past a picture's angle, seen from the camera: front, sides, back. */
    private static final float[] YAWS = {200, 110, 155, 290, 65, 20};
    private static final double ROW = 50;
    private static final double NEAR = 36;
    private static final double STEP = 0.08;
    /** A player online beside the row, which the game draws itself, 20 degrees off a picture's angle too. */
    private Entity turner;

    @Override
    public String name() {
        return "impostorturn";
    }

    @Override
    public void run(ClientGameTestContext context) {
        // The game draws a cow itself up to 34 blocks only: past that, the mod draws it.
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(8);
        });
        Scene.configure(context, config -> {
            config.impostorDistance = 44;
            config.impostorPixels = 0;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            for (int i = 0; i < MOBS.length; i++) {
                Scene.summon(world, MOBS[i], (i - (MOBS.length - 1) / 2.0) * 2.2, ROW, YAWS[i]);
            }
            context.waitTicks(40);
            context.runOnClient(minecraft -> {
                turner = PlayerSizeScenario.online(minecraft, "Turner", (MOBS.length + 1) / 2.0 * 2.2, ROW);
                turner.setYRot(200);
                ((LivingEntity) turner).setYBodyRot(200);
                ((LivingEntity) turner).yBodyRotO = 200;
                ((LivingEntity) turner).setYHeadRot(200);
                ImpostorAtlas.request(ImpostorKey.of(turner), turner);
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                        ImpostorAtlas.request(ImpostorKey.of(entity), entity);
                    }
                }
                minecraft.options.fov().set(30);
                // The HUD hidden, as F1 does; from 26.2 it is the overlay's own, and stays shown.
                //? if <26.2 {
                minecraft.options.hideGui = true;
                //?}
            });
            context.waitTicks(80);
            context.runOnClient(minecraft -> describe(minecraft, "baked"));

            int steps = (int) Math.round((ROW - NEAR) / STEP);
            int frame = 0, nearPictured = -1, farPictured = -1;
            java.util.List<Float> bodies = new java.util.ArrayList<>();
            for (int pass = 0; pass < 2; pass++) {
                for (int s = 0; s <= steps; s++, frame++) {
                    double distance = pass == 0 ? NEAR + s * STEP : ROW - s * STEP;
                    double z = ROW - distance;
                    context.runOnClient(minecraft -> place(minecraft.player, z));
                    context.waitTick();
                    int pictured = context.computeOnClient(minecraft -> {
                        place(minecraft.player, z);
                        int count = 0;
                        for (Entity entity : minecraft.level.entitiesForRendering()) {
                            if (entity instanceof LivingEntity && !(entity instanceof Player)
                                    && GhostRenderer.pictured(entity)) {
                                count++;
                            }
                        }
                        return count;
                    });
                    context.takeScreenshot(String.format(Locale.ROOT, "%s-%04d-d%.2f-p%d", name(), frame, distance, pictured));
                    if (pass == 0) bodies.add(context.computeOnClient(this::turnerBody));
                    if (pass == 0 && s == 0) nearPictured = pictured;
                    if (pass == 0 && s == steps) {
                        farPictured = pictured;
                        context.runOnClient(minecraft -> describe(minecraft, "far"));
                    }
                }
            }
            Scene.log("impostorturn frames=" + frame + " pictured at " + NEAR + "=" + nearPictured
                    + " at " + ROW + "=" + farPictured);
            // The player the game draws turns too, a little at each step, never all at once.
            float first = bodies.get(0);
            float last = bodies.get(bodies.size() - 1);
            int between = 0;
            float largest = 0;
            for (int i = 0; i < bodies.size(); i++) {
                float body = bodies.get(i);
                if (Float.isNaN(body)) continue;
                if (Math.abs(net.minecraft.util.Mth.wrapDegrees(body - first)) > 1
                        && Math.abs(net.minecraft.util.Mth.wrapDegrees(body - last)) > 1) between++;
                if (i > 0 && !Float.isNaN(bodies.get(i - 1))) {
                    largest = Math.max(largest, Math.abs(net.minecraft.util.Mth.wrapDegrees(body - bodies.get(i - 1))));
                }
            }
            StringBuilder series = new StringBuilder();
            for (int i = 0; i < bodies.size(); i += 4) series.append(String.format(Locale.ROOT, " %.1f", bodies.get(i)));
            Scene.log("impostorturn player online body every 0.32 blocks from " + NEAR + ":" + series);
            Scene.log(String.format(Locale.ROOT, "impostorturn player online: body %.1f to %.1f, %d steps between,"
                    + " largest step %.1f degrees", first, last, between, largest));
            if (between < 10) throw new AssertionError("The player online turned at once: " + between + " steps between");
            if (farPictured < MOBS.length) throw new AssertionError("Not every mob became a picture at " + ROW + " blocks");
        }
    }

    /** One line per mob: how far, drawn as a picture or not, its picture baked or not. */
    /** The player online's body as the game extracts it this frame, turned or not; NaN once it is a picture. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private float turnerBody(net.minecraft.client.Minecraft minecraft) {
        net.minecraft.client.renderer.entity.EntityRenderer renderer = minecraft.getEntityRenderDispatcher().getRenderer(turner);
        Object state = renderer.createRenderState(turner, 1f);
        return state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living
                ? living.bodyRot : Float.NaN;
    }

    private static void describe(net.minecraft.client.Minecraft minecraft, String when) {
        Scene.log("impostorturn " + when + " sheets=" + ImpostorAtlas.sheets() + " waiting=" + ImpostorAtlas.waiting()
                + " failed=" + ImpostorAtlas.failed());
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || entity instanceof Player) continue;
            Scene.log(String.format(Locale.ROOT, "impostorturn %s %s d=%.1f pictured=%s baked=%s", when,
                    entity.getType().toShortString(), Math.sqrt(entity.distanceToSqr(minecraft.gameRenderer.getMainCamera().position())),
                    GhostRenderer.pictured(entity), ImpostorAtlas.sheet(ImpostorKey.of(entity)) != null));
        }
    }

    /** The player {@code z} blocks south of the start, facing the row, with no glide from the last frame. */
    private static void place(LocalPlayer player, double z) {
        player.setPos(0, player.getY(), z);
        player.xo = player.xOld = 0;
        player.zo = player.zOld = z;
        player.yo = player.yOld = player.getY();
        player.setYRot(0);
        player.setXRot(0);
        player.yRotO = 0;
        player.xRotO = 0;
        player.setDeltaMovement(0, 0, 0);
    }
}
