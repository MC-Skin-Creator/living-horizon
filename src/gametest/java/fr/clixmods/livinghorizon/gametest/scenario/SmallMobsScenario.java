package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.debug.DebugMarks;
import fr.clixmods.livinghorizon.debug.DebugMarks.Mark;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Network;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Small mobs and a cow, on a server without the mod, with the client's "Entity Distance" at
 * 50 %: the game stops drawing each one at a distance that grows with its size (a chicken
 * past 16 blocks, a cow past 34). The player walks slowly backwards, looking at them, and at
 * every step each one is drawn once: by the game, by the mod for the real mob, or as its copy.
 *
 * <p>Twice: on a server that sends them far, then on one that sends them to 16 blocks only,
 * where each copy takes over right where the server stops sending its mob.
 */
public final class SmallMobsScenario implements Scenario {
    private static final String[] MOBS = {"chicken", "rabbit", "cat", "wolf", "cow"};
    /** How far ahead of where the player starts the mobs stand, side by side. */
    private static final int AHEAD = 6;
    /** How far back the player walks, and how slowly: one block every two ticks. */
    private static final int BACK = 50, PACE = 2;

    @Override
    public String name() {
        return "smallmobs";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Network.receiver(data -> {
        });
        context.runOnClient(minecraft -> {
            minecraft.options.renderDistance().set(8);
            minecraft.options.entityDistanceScaling().set(0.5);
        });
        Scene.configure(context, config -> {
            config.hideOccludedMobs = false;
            config.onlyOverTerrain = false;
            config.debugHud = true;
        });
        List<String> gaps = new ArrayList<>();
        try {
            // 100 %: animals sent to 160 blocks. 10 %: to 16.
            walk(context, "100", gaps);
            walk(context, "10", gaps);
        } finally {
            // As the client's entry point hands it in.
            Network.receiver(data -> Minecraft.getInstance().execute(() -> FarPlayerTracker.get().receive(data)));
        }
        if (!gaps.isEmpty()) throw new AssertionError("mobs drawn by nobody, or twice: " + String.join("; ", gaps));
    }

    private void walk(ClientGameTestContext context, String percentage, List<String> gaps) {
        Properties properties = new Properties();
        properties.setProperty("entity-broadcast-range-percentage", percentage);
        try (TestDedicatedServerContext server = context.worldBuilder().setUseConsistentSettings(true).createServer(properties)) {
            server.runCommand("forceload add -16 " + (-BACK - 16) + " 16 16");
            for (int i = 0; i < MOBS.length; i++) {
                server.runCommand("summon " + MOBS[i] + " " + (i * 2 - MOBS.length + 1) + " -60 " + AHEAD
                        + " {NoAI:1b,Silent:1b,Rotation:[180f,0f]}");
            }
            try (var connection = server.connect()) {
                // The two servers are one world to the mod (same seed, same address): none of the first's
                // copies here, once the memory is open.
                context.waitTicks(20);
                context.runOnClient(minecraft -> FarPlayerTracker.get().mobs().forgetAll());
                server.runCommand("tp @a 0 -60 0 0 0");
                context.waitTicks(200);
                step(context, percentage, 0, gaps);
                for (int z = -1; z >= -BACK; z--) {
                    server.runCommand("tp @a 0 -60 " + z + " 0 0");
                    context.waitTicks(PACE);
                    step(context, percentage, z, gaps);
                }
                context.waitTicks(40);
                step(context, percentage, -BACK, gaps);
                context.runOnClient(minecraft -> Scene.log("smallmobs " + percentage + "% reach "
                        + FarPlayerTracker.get().mobs().reach()));
                Scene.screenshot(context, this, "far-" + percentage);
            }
        }
    }

    /** What drew each mob in the last frame; a step where one is drawn by nobody, or twice, is a gap. */
    private static void step(ClientGameTestContext context, String percentage, int z, List<String> gaps) {
        context.runOnClient(minecraft -> {
            int game = DebugMarks.count(Mark.GAME), live = DebugMarks.count(Mark.LIVE), copy = DebugMarks.count(Mark.FAKE);
            StringBuilder others = new StringBuilder();
            for (Mark mark : Mark.values()) {
                if (mark == Mark.GAME || mark == Mark.LIVE || mark == Mark.FAKE || DebugMarks.count(mark) == 0) continue;
                others.append(' ').append(mark.name().toLowerCase()).append('=').append(DebugMarks.count(mark));
            }
            String line = percentage + "% z=" + z + " game=" + game + " live=" + live + " copy=" + copy + others;
            Scene.log("smallmobs " + line);
            if (game + live + copy != MOBS.length) gaps.add(line);
        });
    }
}
