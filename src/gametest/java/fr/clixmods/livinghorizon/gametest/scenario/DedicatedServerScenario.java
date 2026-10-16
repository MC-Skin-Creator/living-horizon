package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

/**
 * A dedicated server with the mod and no data pack: the server must publish the positions
 * from its own tick, before anyone joins, and a client that joins read them as the pack's.
 */
public final class DedicatedServerScenario implements Scenario {
    @Override
    public String name() {
        return "dedicated";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestDedicatedServerContext server = context.worldBuilder().setUseConsistentSettings(true).createServer()) {
            context.waitTicks(40);
            // Nobody has joined yet: the layout and the clock are there all the same.
            int[] clock = new int[2];
            for (int i = 0; i < 2; i++) {
                clock[i] = server.computeOnServer(minecraft -> {
                    Scoreboard scoreboard = minecraft.getScoreboard();
                    Objective meta = scoreboard.getObjective("lh.m");
                    if (meta == null || scoreboard.getObjective("lh.x") == null) {
                        throw new AssertionError("the dedicated server publishes nothing");
                    }
                    ReadOnlyScoreInfo now = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly("#clock"), meta);
                    if (now == null) throw new AssertionError("the dedicated server has no clock");
                    return now.value();
                });
                context.waitTicks(20);
            }
            if (clock[1] <= clock[0]) throw new AssertionError("the dedicated server's clock does not move: " + clock[0] + " then " + clock[1]);
            Scene.log("dedicated clock " + clock[0] + " -> " + clock[1]);

            try (TestServerConnection connection = server.connect()) {
                // The scoreboard is all this needs: no waiting for the chunks, slow to make without a graphics card.
                context.waitTicks(60);
                server.runCommand("tp @a 30 ~ 0");
                context.waitTicks(40);
                context.runOnClient(minecraft -> {
                    if (!FarPlayerTracker.get().sharing()) throw new AssertionError("the client does not find the server's sharing");
                    Scoreboard scoreboard = minecraft.level.getScoreboard();
                    Objective x = scoreboard.getObjective("lh.x");
                    ReadOnlyScoreInfo score = x == null ? null
                            : scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(minecraft.player.getScoreboardName()), x);
                    if (score == null) throw new AssertionError("the player has no position on the client");
                    int expected = (int) Math.floor(minecraft.player.getX() * 10);
                    if (Math.abs(score.value() - expected) > 10) {
                        throw new AssertionError("player x " + score.value() + ", expected about " + expected);
                    }
                    Scene.log("dedicated player x=" + score.value() + " (at " + minecraft.player.getX() + ")");
                });
            }
        }
    }
}
