package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
//? if >=26.2
/*import net.minecraft.server.MinecraftServer;*/
//? if <26.3
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * A world opened to LAN, with no data pack: the host's mod must publish the positions
 * through the scoreboard, the client read them back as the pack's, follow the host as it
 * moves, and leave no objective in the world once it is closed.
 */
public final class LanShareScenario implements Scenario {
    @Override
    public String name() {
        return "lanshare";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Path scores;
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // The saved scoreboard moved under data/minecraft/ in 26.1.
            scores = context.computeOnClient(minecraft -> minecraft.getSingleplayerServer()
                    .getWorldPath(LevelResource.ROOT).resolve("data")
                    //? if >=26.1
                    /*.resolve("minecraft")*/
                    .resolve("scoreboard.dat"));
            Scene.summon(world, "cow", 10, 10, 0);
            context.waitTicks(40);
            context.runOnClient(minecraft -> {
                if (FarPlayerTracker.get().sharing()) throw new AssertionError("sharing before the world is opened to LAN");
            });

            // 26.2 asks who may join (the LAN), and 26.3 no longer asks for the game mode.
            //? if >=26.3 {
            /*context.runOnClient(minecraft -> minecraft.getSingleplayerServer()
                    .publishServer(MinecraftServer.MultiplayerScope.LAN, false, 25599));
            *///?} elif >=26.2 {
            /*context.runOnClient(minecraft -> minecraft.getSingleplayerServer()
                    .publishServer(MinecraftServer.MultiplayerScope.LAN, GameType.SURVIVAL, false, 25599));
            *///?} else {
            context.runOnClient(minecraft -> minecraft.getSingleplayerServer().publishServer(GameType.SURVIVAL, false, 25599));
            //?}
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

            // The host moves: the score follows within a publication or two.
            Scene.command(world, "tp @a 40 ~ 0");
            context.waitTicks(20);
            context.runOnClient(minecraft -> {
                Scoreboard scoreboard = minecraft.level.getScoreboard();
                ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(
                        ScoreHolder.forNameOnly(minecraft.player.getScoreboardName()), scoreboard.getObjective("lh.x"));
                if (score == null || Math.abs(score.value() - 400) > 10) {
                    throw new AssertionError("host x after moving " + (score == null ? "none" : score.value()) + ", expected about 400");
                }
                Scene.log("lanshare host moved, x=" + score.value());
            });
        }
        // Leaving the world removes what the share wrote: the saved scoreboard no longer has it.
        try {
            if (!Files.exists(scores)) throw new AssertionError("no saved scoreboard at " + scores);
            byte[] saved = new GZIPInputStream(Files.newInputStream(scores)).readAllBytes();
            if (new String(saved, StandardCharsets.ISO_8859_1).contains("lh.x")) {
                throw new AssertionError("the objectives are still in the saved world");
            }
            Scene.log("lanshare cleaned up, saved scoreboard " + saved.length + " bytes");
        } catch (IOException e) {
            throw new AssertionError("could not read the saved scoreboard " + scores, e);
        }
    }
}
