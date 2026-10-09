package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.LanShare;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreHolder;

/**
 * A world opened to LAN, with no data pack: the host's mod must publish the positions
 * through the scoreboard, and the client read them back as the pack's.
 */
public final class LanShareScenario implements Scenario {
    @Override
    public String name() {
        return "lanshare";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.summon(world, "cow", 10, 10, 0);
            context.waitTicks(40);
            context.runOnClient(minecraft -> {
                if (FarPlayerTracker.get().sharing()) throw new AssertionError("sharing before the world is opened to LAN");
            });

            context.runOnClient(minecraft -> minecraft.getSingleplayerServer().publishServer(GameType.SURVIVAL, false, 25599));
            context.waitTicks(60);

            context.runOnClient(minecraft -> {
                if (!FarPlayerTracker.get().sharing()) throw new AssertionError("the LAN share is not found by the client");
                Scoreboard scoreboard = minecraft.level.getScoreboard();
                Objective x = scoreboard.getObjective("lh.x");
                if (x == null) throw new AssertionError("no lh.x objective on the client");
                String self = minecraft.player.getScoreboardName();
                ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(self), x);
                if (score == null) throw new AssertionError("the host has no position");
                int expected = (int) Math.floor(minecraft.player.getX() * 10);
                if (Math.abs(score.value() - expected) > 10) {
                    throw new AssertionError("host x " + score.value() + ", expected about " + expected);
                }
                long mobs = scoreboard.listPlayerScores(x).stream().filter(entry -> entry.owner().length() == 36).count();
                if (mobs < 1) throw new AssertionError("the cow is not published");
                Scene.log("lanshare host x=" + score.value() + " mobs published=" + mobs);
            });

            // Leaving the world removes what the share wrote.
            context.runOnClient(minecraft -> LanShare.close());
            context.waitTicks(10);
            context.runOnClient(minecraft -> {
                if (minecraft.getSingleplayerServer().getScoreboard().getObjective("lh.x") != null) {
                    throw new AssertionError("the objectives are still in the world");
                }
                Scene.log("lanshare cleaned up");
            });
        }
    }
}
