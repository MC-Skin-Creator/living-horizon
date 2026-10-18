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

/**
 * Players who logged off, far away, are pictures like players online: test players 150
 * blocks off, past the impostor distance. Seated first ({@code offlineLife} off), each
 * gets a picture of its own baked seated; then standing, a picture of its own again. The
 * impostor page is shown last, to see both poses.
 */
public final class RestingImpostorsScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    private static final String NAME = "Far";

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
            Scene.command(world, "tp @a 0.5 " + GROUND + " 150.5 180 0");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(
                    "livinghorizon debug offline test spawn " + NAME));
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 0");
            context.waitTicks(40);

            // Seated: a seated picture.
            String seated = pictured(context, RestingPuppet.Look.SIT, true);
            if (seated != null) throw new AssertionError("restingimpostors seated: " + seated);
            Scene.screenshot(context, this, "seated");

            // Standing: on guard, sword drawn and shield up, which stands it up for good.
            Scene.configure(context, config -> config.offlineLife = true);
            Scene.command(world, "summon husk 2.5 " + GROUND + " 150.5 {NoAI:1b}");
            context.waitTicks(40);
            String standing = pictured(context, RestingPuppet.Look.STAND, false);
            Scene.command(world, "kill @e[type=husk]");
            if (standing != null) throw new AssertionError("restingimpostors standing: " + standing);
            Scene.screenshot(context, this, "standing");
            Scene.screenshotAtlas(context, this);
            context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("livinghorizon debug offline test clear"));
        }
    }

    /** Waits for the test player's picture in this pose; null once there, else why not. */
    private static String pictured(ClientGameTestContext context, RestingPuppet.Look look, boolean seated) {
        String why = null;
        for (int i = 0; i < 30; i++) {
            why = context.computeOnClient(minecraft -> {
                RestingPuppet puppet = puppet(minecraft);
                if (puppet.look() != look) return "look " + puppet.look();
                ImpostorKey key = ImpostorKey.of(puppet);
                if (ImpostorKey.seated(key) != seated) return "seated key " + ImpostorKey.seated(key);
                return ImpostorAtlas.sheet(key) != null ? null : "no picture yet (waiting " + ImpostorAtlas.waiting() + ")";
            });
            if (why == null) break;
            context.waitTicks(10);
        }
        Scene.log("restingimpostors " + look + ": " + (why == null ? "pictured" : why));
        return why;
    }

    private static RestingPuppet puppet(Minecraft minecraft) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (spot.name().equals(NAME) && minecraft.level != null) return resting.puppet(minecraft.level, spot);
        }
        throw new AssertionError("restingimpostors: no test player");
    }
}
