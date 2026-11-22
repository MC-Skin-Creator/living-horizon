package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * What a crowd of players who logged off costs, up close: fifty test players around the player,
 * living their life, against the same scene with them off and with fifty mobs instead. Prints the
 * frame time, the mod's tick for them and its figures' preparation, for each.
 */
public final class RestingCostScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    private static final int CROWD = 50, RADIUS = 40;
    private static final int SETTLE = 100;
    /** Frames per measure: fewer under a shader pack, where llvmpipe takes a second a frame. */
    private static final int FRAMES = Integer.getInteger("lh.restingcost.frames", 200);

    @Override
    public String name() {
        return "restingcost";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.offlineLife = true);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // A few houses' worth of things to look at and beds, as a village has.
            for (int i = 0; i < 12; i++) {
                int x = (i % 4) * 16 - 24, z = (i / 4) * 16 - 16;
                Scene.command(world, "setblock " + x + " " + GROUND + " " + z + " crafting_table");
                Scene.command(world, "setblock " + (x + 2) + " " + GROUND + " " + z + " red_bed");
                Scene.command(world, "setblock " + (x + 4) + " " + GROUND + " " + (z + 2) + " campfire");
            }
            context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(
                    "livinghorizon debug offline test crowd " + CROWD + " " + RADIUS));
            context.waitTicks(200);
            int made = context.computeOnClient(minecraft -> {
                RestingPlayers resting = FarPlayerTracker.get().resting();
                return (int) resting.shown().stream().filter(spot -> resting.made(spot) != null).count();
            });
            Scene.log("restingcost: " + made + " puppets made");
            if (Boolean.getBoolean("lh.restingcost.names")) {
                // Their names alone, on and off in turn: what a shader pack makes of them.
                context.runOnClient(minecraft -> look(minecraft, FRAMES / 8));
                context.waitTicks(20);
                Scene.screenshot(context, this, "names");
                for (int i = 0; i < 3; i++) {
                    measure(context, "players, names", config -> { }, 40);
                    measure(context, "players, no names", config -> config.offlineNames = false, 40);
                }
                measure(context, "players off", config -> config.offlinePlayers = false, 40);
                return;
            }

            measure(context, "warm-up", config -> { }, SETTLE);
            measure(context, "players, defaults", config -> { }, SETTLE);
            measure(context, "players, no life", config -> config.offlineLife = false, SETTLE);
            measure(context, "players, no pictures", config -> config.impostorPlayers = false, SETTLE);
            measure(context, "players off", config -> config.offlinePlayers = false, SETTLE);
            measure(context, "players, no names", config -> config.offlineNames = false, SETTLE);
            measure(context, "players, defaults again", config -> { }, SETTLE);
            context.runOnClient(minecraft -> FarPlayerTracker.get().resting().clearFakes());
            context.waitTicks(20);

            // Far off, past the impostor distance: pictures, one sheet per skin.
            Scene.command(world, "tp @a 0.5 " + GROUND + " 160.5");
            context.waitTicks(40);
            context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(
                    "livinghorizon debug offline test crowd " + CROWD + " 30"));
            context.waitTicks(10);
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5");
            context.waitTicks(200);
            measure(context, "far, warm-up", config -> { }, SETTLE);
            measure(context, "far, defaults", config -> { }, SETTLE);
            measure(context, "far, no life", config -> config.offlineLife = false, SETTLE);
            measure(context, "far, no pictures", config -> config.impostorPlayers = false, SETTLE);
            measure(context, "far, players off", config -> config.offlinePlayers = false, SETTLE);
            Scene.command(world, "time set 18000");
            measure(context, "far, night", config -> { }, 400);
            measure(context, "far, night again", config -> { }, SETTLE);
            Scene.command(world, "time set 6000");
            context.runOnClient(minecraft -> FarPlayerTracker.get().resting().clearFakes());
            context.waitTicks(20);
            // The same number of mobs in their place, drawn by the game: what the user compares with.
            for (int i = 0; i < CROWD; i++) {
                double angle = i * Math.PI * (3 - Math.sqrt(5)), distance = 8 + (i * 13 % (RADIUS - 8));
                Scene.command(world, String.format(Locale.ROOT, "summon villager %.1f %d %.1f",
                        Math.cos(angle) * distance, GROUND, Math.sin(angle) * distance));
            }
            measure(context, "50 villagers", config -> config.offlinePlayers = false, SETTLE);
        }
    }

    private static void measure(ClientGameTestContext context, String what, Consumer<FarConfig> change, int settle) {
        context.runOnClient(minecraft -> {
            FarConfig config = FarConfig.get();
            config.offlinePlayers = true;
            config.offlineLife = true;
            config.impostorPlayers = true;
            config.offlineNames = true;
            change.accept(config);
            look(minecraft, 0);
        });
        context.waitTicks(settle);
        long thread = context.computeOnClient(minecraft -> Thread.currentThread().threadId());
        java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
        long cpuBefore = bean.getThreadCpuTime(thread);
        int bakesBefore = context.computeOnClient(minecraft -> ImpostorAtlas.bakes());
        double frame = 0, tick = 0, extract = 0, worstTick = 0;
        for (int i = 0; i < FRAMES; i++) {
            long started = System.nanoTime();
            context.waitTick();
            frame += System.nanoTime() - started;
            int next = i + 1;
            long[] mod = context.computeOnClient(minecraft -> {
                look(minecraft, next);
                return new long[]{Stats.lastTickNanos(), Stats.lastExtractNanos()};
            });
            tick += mod[0];
            extract += mod[1];
            worstTick = Math.max(worstTick, mod[0]);
        }
        double cpu = (bean.getThreadCpuTime(thread) - cpuBefore) / (double) FRAMES / 1e6;
        int bakes = context.computeOnClient(minecraft -> ImpostorAtlas.bakes()) - bakesBefore;
        double bake = context.computeOnClient(minecraft -> Profile.Section.BAKE.cpu().seen()
                ? Profile.Section.BAKE.cpu().average() / 1e6 : 0);
        double resting = context.computeOnClient(minecraft -> Profile.Section.TICK_RESTING.cpu().seen()
                ? Profile.Section.TICK_RESTING.cpu().average() / 1e6 : 0);
        int drawn = context.computeOnClient(minecraft -> Stats.drawn());
        Scene.log(String.format(Locale.ROOT,
                "restingcost %-24s frame %6.2f ms, game thread cpu %6.2f ms, mod tick %5.2f ms (worst %5.1f, resting %5.2f), extract %5.2f ms, drawn %d, bakes %d (%.2f ms)",
                what, frame / FRAMES / 1e6, cpu, tick / FRAMES / 1e6, worstTick / 1e6, resting, extract / FRAMES / 1e6, drawn, bakes, bake));
    }

    private static void look(Minecraft minecraft, int frame) {
        if (minecraft.player == null) return;
        float yaw = 360f * frame / FRAMES;
        minecraft.player.setYRot(yaw);
        minecraft.player.yRotO = yaw;
        minecraft.player.setYHeadRot(yaw);
        minecraft.player.setXRot(10);
        minecraft.player.xRotO = 10;
    }
}
