package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A dedicated server with the mod: it shares the positions over the network from its own
 * tick, a client that joins reads its own back and follows it as it moves, and the server
 * keeps them in its world's file once it stops.
 */
public final class DedicatedServerScenario implements Scenario {
    @Override
    public String name() {
        return "dedicated";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Path root;
        try (TestDedicatedServerContext server = context.worldBuilder().setUseConsistentSettings(true).createServer()) {
            root = server.computeOnServer(minecraft -> minecraft.getWorldPath(LevelResource.ROOT));
            // Its type was renamed in the Fabric API for 26.3; both close the connection.
            try (var connection = server.connect()) {
                // The positions are all this needs: no waiting for the chunks, slow to make without a graphics card.
                context.waitTicks(60);
                server.runCommand("tp @a 30 ~ 0");
                context.waitTicks(40);
                context.runOnClient(minecraft -> {
                    if (!FarPlayerTracker.get().sharing()) throw new AssertionError("the client does not find the server's sharing");
                    SharedPositions.Report self = FarPlayerTracker.get().shared().read(minecraft.player.getScoreboardName());
                    if (self == null) throw new AssertionError("the player has no position on the client");
                    if (Math.abs(self.x() - minecraft.player.getX()) > 1) {
                        throw new AssertionError("player x " + self.x() + ", expected about " + minecraft.player.getX());
                    }
                    if (minecraft.level.getScoreboard().getObjective("lh.x") != null) {
                        throw new AssertionError("something wrote into the scoreboard");
                    }
                    Scene.log("dedicated player x=" + self.x() + " (at " + minecraft.player.getX() + ")");
                });
            }
        }
        // Stopped: what it kept is in the world's file, for the players who join next time.
        try {
            Path file = root.resolve("livinghorizon-positions.dat");
            if (!Files.isRegularFile(file) || Files.size(file) == 0) throw new AssertionError("no positions saved at " + file);
            Scene.log("dedicated saved " + Files.size(file) + " bytes of positions");
        } catch (IOException e) {
            throw new AssertionError("could not read " + root, e);
        }
    }
}
