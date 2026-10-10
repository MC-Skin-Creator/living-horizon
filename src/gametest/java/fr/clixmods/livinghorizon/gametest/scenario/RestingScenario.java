package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * A player who logged off, sitting on a pillar 6 blocks ahead. The data pack's scores are
 * written by hand, as the pack would: the sleeper is someone who left before. The pillar
 * is broken (they fall to the ground), they are buried (they reappear on top), the pillar
 * is built again (back on it), then a bed is put next to it (asleep in the bed).
 */
public final class RestingScenario implements Scenario {
    private static final String NAME = "LH_Sleeper";
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;

    @Override
    public String name() {
        return "resting";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "fill 0 " + GROUND + " 8 0 " + (GROUND + 2) + " 8 stone");
            // The pack's four objectives, shown in sidebars nobody sees so that the client gets them.
            String[] slots = {"black", "dark_blue", "dark_green", "dark_aqua"};
            String[] objectives = {"lh.x", "lh.y", "lh.z", "lh.m"};
            for (int i = 0; i < 4; i++) {
                Scene.command(world, "scoreboard objectives add " + objectives[i] + " dummy");
                Scene.command(world, "scoreboard objectives setdisplay sidebar.team." + slots[i] + " " + objectives[i]);
            }
            Scene.command(world, "scoreboard players set #version lh.m 1");
            // On top of the pillar, facing the camera.
            Scene.command(world, "scoreboard players set " + NAME + " lh.x 5");
            Scene.command(world, "scoreboard players set " + NAME + " lh.y " + (GROUND + 3) * 10);
            Scene.command(world, "scoreboard players set " + NAME + " lh.z 85");
            Scene.command(world, "scoreboard players set " + NAME + " lh.m 180");
            Scene.command(world, "tp @a 0.5 " + GROUND + " 1.5 0 0");
            wait(context, world, 60);
            check(context, "pillar", GROUND + 3, false);
            Scene.screenshot(context, this, "pillar");

            Scene.command(world, "fill 0 " + GROUND + " 8 0 " + (GROUND + 2) + " 8 air");
            wait(context, world, 40);
            check(context, "fallen", GROUND, false);
            Scene.screenshot(context, this, "fallen");

            Scene.command(world, "fill 0 " + GROUND + " 8 0 " + (GROUND + 1) + " 8 dirt");
            wait(context, world, 20);
            check(context, "buried", GROUND + 2, false);
            Scene.screenshot(context, this, "buried");

            Scene.command(world, "fill 0 " + GROUND + " 8 0 " + (GROUND + 2) + " 8 stone");
            wait(context, world, 20);
            check(context, "rebuilt", GROUND + 3, false);
            Scene.screenshot(context, this, "rebuilt");

            Scene.command(world, "setblock 3 " + GROUND + " 9 red_bed[facing=north,part=foot]");
            Scene.command(world, "setblock 3 " + GROUND + " 8 red_bed[facing=north,part=head]");
            wait(context, world, 40);
            check(context, "bed", GROUND + 0.6875, true);
            Scene.screenshot(context, this, "bed");
        }
    }

    /** Waits, moving the pack's clock as the pack does, so that its scores stay trusted. */
    private static void wait(ClientGameTestContext context, TestSingleplayerContext world, int ticks) {
        for (int done = 0; done < ticks; done += 10) {
            Scene.command(world, "scoreboard players add #clock lh.m 1");
            context.waitTicks(10);
        }
    }

    /** Logs where the sleeper is, and fails the run when it is not where it should be. */
    private static void check(ClientGameTestContext context, String step, double y, boolean bed) {
        String where = context.computeOnClient(minecraft -> {
            ClientLevel level = minecraft.level;
            RestingPlayers resting = FarPlayerTracker.get().resting();
            for (RestingPlayers.Spot spot : resting.spots()) {
                if (!spot.name().equals(NAME) || level == null) continue;
                var puppet = resting.puppet(level, spot);
                Scene.log("resting " + step + " y=" + puppet.getY() + " bed=" + puppet.inBed()
                        + " standing=" + puppet.standing());
                return Math.abs(puppet.getY() - y) < 1e-3 && puppet.inBed() == bed ? null
                        : "y=" + puppet.getY() + " bed=" + puppet.inBed();
            }
            return "no sleeper";
        });
        if (where != null) throw new AssertionError("resting " + step + ": expected y=" + y + " bed=" + bed + ", got " + where);
    }
}
