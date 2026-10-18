package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Players who logged off, far away, are pictures like players online: test players 150
 * blocks off, past the impostor distance. One on foot gets a picture of its own seated
 * ({@code offlineLife} off), standing (on its loop) and lying down (at night); one on a horse
 * a picture of both together. The impostor page is shown last, to see every posture.
 */
public final class RestingImpostorsScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    private static final String NAME = "Far", RIDER = "FarRider";

    @Override
    public String name() {
        return "restingimpostors";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.offlineLife = false;
            config.impostorPlayers = true;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // One on foot, one on a horse, both 150 blocks south of the camera.
            Scene.command(world, "tp @a 0.5 " + GROUND + " 150.5 180 0");
            context.waitTicks(20);
            command(context, "livinghorizon debug offline test spawn " + NAME);
            Scene.command(world, "summon horse 6.5 " + GROUND + " 150.5 {Tame:1b,NoAI:1b,"
                    + "SaddleItem:{id:\"minecraft:saddle\",count:1},equipment:{saddle:{id:\"minecraft:saddle\",count:1}}}");
            Scene.command(world, "tp @a 6.5 " + GROUND + " 148 0 0");
            context.waitTicks(10);
            Scene.command(world, "ride @p mount @e[type=horse,limit=1,sort=nearest]");
            context.waitTicks(20);
            command(context, "livinghorizon debug offline test spawn " + RIDER);
            context.waitTicks(5);
            Scene.command(world, "ride @p dismount");
            Scene.command(world, "tp @e[type=horse] ~ -200 ~");
            Scene.command(world, "kill @e[type=horse]");
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 0");
            context.waitTicks(40);

            // Seated on the ground: a seated picture.
            String seated = pictured(context, NAME, RestingPuppet.Look.SIT, ImpostorKey.SEATED);
            if (seated != null) throw new AssertionError("restingimpostors seated: " + seated);
            Scene.screenshot(context, this, "seated");

            // Living, away from the camera: its loop, standing between its pauses.
            Scene.configure(context, config -> config.offlineLife = true);
            String standing = pictured(context, NAME, RestingPuppet.Look.STAND, ImpostorKey.STANDING);
            if (standing != null) throw new AssertionError("restingimpostors standing: " + standing);
            Scene.screenshot(context, this, "standing");

            // At night, lying down where it is: a lying picture.
            Scene.command(world, "time set midnight");
            String lying = pictured(context, NAME, RestingPuppet.Look.LIE, ImpostorKey.LYING);
            if (lying != null) throw new AssertionError("restingimpostors lying: " + lying);

            // The rider and its horse: one picture of both.
            String riding = null;
            for (int i = 0; i < 30; i++) {
                riding = context.computeOnClient(minecraft -> {
                    RestingPuppet rider = puppet(minecraft, RIDER);
                    if (!(rider.mount() instanceof LivingEntity horse)) return "no horse";
                    Vec3 seat = rider.position().subtract(horse.position()).yRot((float) Math.toRadians(horse.yBodyRot));
                    ImpostorKey key = ImpostorKey.riding(rider, horse, seat, Mth.wrapDegrees(rider.yBodyRot - horse.yBodyRot));
                    return ImpostorAtlas.sheet(key) != null ? null : "no picture yet (waiting " + ImpostorAtlas.waiting() + ")";
                });
                if (riding == null) break;
                context.waitTicks(10);
            }
            Scene.log("restingimpostors rider: " + (riding == null ? "pictured" : riding));
            if (riding != null) throw new AssertionError("restingimpostors rider: " + riding);
            Scene.screenshot(context, this, "night");
            Scene.screenshotAtlas(context, this);
            command(context, "livinghorizon debug offline test clear");
        }
    }

    private static void command(ClientGameTestContext context, String command) {
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(command));
    }

    /** Waits for a test player's picture in this look; null once there, else why not. */
    private static String pictured(ClientGameTestContext context, String name, RestingPuppet.Look look, int posture) {
        String why = null;
        for (int i = 0; i < 60; i++) {
            why = context.computeOnClient(minecraft -> {
                RestingPuppet puppet = puppet(minecraft, name);
                if (puppet.look() != look) return "look " + puppet.look();
                ImpostorKey key = ImpostorKey.of(puppet);
                if (ImpostorKey.posture(key) != posture) return "posture " + ImpostorKey.posture(key);
                return ImpostorAtlas.sheet(key) != null ? null : "no picture yet (waiting " + ImpostorAtlas.waiting() + ")";
            });
            if (why == null) break;
            context.waitTicks(5);
        }
        Scene.log("restingimpostors " + name + " " + look + ": " + (why == null ? "pictured" : why));
        return why;
    }

    private static RestingPuppet puppet(Minecraft minecraft, String name) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (spot.name().equals(name) && minecraft.level != null) return resting.puppet(minecraft.level, spot);
        }
        throw new AssertionError("restingimpostors: no test player");
    }
}
