package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.PositionsMessage;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
//? if >=26.2
/*import net.minecraft.server.MinecraftServer;*/
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
//? if <26.3
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * A world played alone, then opened to LAN: the mod of the game's own server shares the
 * positions over the network from the start, and its client reads them back as a friend's
 * would: the host, a cow, the host moving, the pig the host rides and gets off, a test player
 * the host places (in what the host wears), shared too and gone once cleared. Nothing is written
 * into the scoreboard, and the positions are kept in the world's own file once it closes.
 */
public final class LanShareScenario implements Scenario {
    @Override
    public String name() {
        return "lanshare";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Path root;
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            root = context.computeOnClient(minecraft -> minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT));
            Scene.summon(world, "cow", 10, 10, 0);
            context.waitTicks(40);
            // Alone in the world: shared already, so that those who logged off here show without LAN.
            context.runOnClient(minecraft -> {
                if (!FarPlayerTracker.get().sharing()) throw new AssertionError("no sharing in a world played alone");
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
                SharedPositions shared = FarPlayerTracker.get().shared();
                SharedPositions.Report host = shared.read(minecraft.player.getScoreboardName());
                if (host == null) throw new AssertionError("the host has no position");
                if (Math.abs(host.x() - minecraft.player.getX()) > 1) {
                    throw new AssertionError("host x " + host.x() + ", expected about " + minecraft.player.getX());
                }
                if (shared.mobs().isEmpty()) throw new AssertionError("the cow is not published");
                if (minecraft.level.getScoreboard().getObjective("lh.x") != null) {
                    throw new AssertionError("something wrote into the scoreboard");
                }
                Scene.log("lanshare host x=" + host.x() + " mobs published=" + shared.mobs().size());
            });

            // The host moves: the position follows within a publication or two.
            Scene.command(world, "tp @a 40 ~ 0");
            context.waitTicks(20);
            context.runOnClient(minecraft -> {
                SharedPositions.Report host = FarPlayerTracker.get().shared().read(minecraft.player.getScoreboardName());
                if (host == null || Math.abs(host.x() - 40.5) > 1) {
                    throw new AssertionError("host x after moving " + (host == null ? "none" : host.x()) + ", expected about 40.5");
                }
                Scene.log("lanshare host moved, x=" + host.x());
            });

            // The host gets on a pig: the server says which mob it is, so a far rider is drawn on it.
            Scene.command(world, "execute at @p run summon pig ~ ~ ~ {NoAI:1b,Tags:[\"lh.mount\"]}");
            Scene.command(world, "ride @p mount @e[type=pig,tag=lh.mount,limit=1]");
            context.waitTicks(20);
            context.runOnClient(minecraft -> {
                Entity pig = minecraft.player.getVehicle();
                SharedPositions shared = FarPlayerTracker.get().shared();
                String name = minecraft.player.getScoreboardName();
                UUID vehicle = shared.vehicle(name);
                SharedPositions.Report host = shared.read(name);
                if (pig == null || host == null || !host.riding() || !pig.getUUID().equals(vehicle)) {
                    throw new AssertionError("the host's mount is not shared: pig " + (pig == null ? "none" : pig.getUUID())
                            + ", shared " + vehicle);
                }
                Scene.log("lanshare host rides the pig " + vehicle);
            });
            Scene.command(world, "ride @p dismount");
            context.waitTicks(20);
            // A test player in what the host wears: the server shares it, and forgets it once cleared.
            Scene.command(world, "item replace entity @p armor.head with minecraft:diamond_helmet");
            context.waitTicks(5);
            context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("livinghorizon debug offline test spawn Tester"));
            context.waitTicks(60);
            context.runOnClient(minecraft -> {
                SharedPositions shared = FarPlayerTracker.get().shared();
                SharedPositions.Report tester = shared.read("Tester");
                PositionsMessage.Look look = shared.look("Tester");
                if (tester == null || !tester.test()) throw new AssertionError("the test player is not shared by the server");
                if (look == null || look.gear().stream().noneMatch(item -> item.contains("diamond_helmet"))) {
                    throw new AssertionError("the test player is shared without the helmet: " + look);
                }
                RestingPuppet puppet = FarPlayerTracker.get().resting().puppet(minecraft.level,
                        FarPlayerTracker.get().resting().spots().stream().filter(spot -> spot.name().equals("Tester"))
                                .findFirst().orElseThrow());
                if (!puppet.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)) {
                    throw new AssertionError("the test player does not wear the helmet");
                }
                Scene.log("lanshare test player shared, in its helmet");
            });
            context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("livinghorizon debug offline test clear"));
            context.waitTicks(60);
            context.runOnClient(minecraft -> {
                if (FarPlayerTracker.get().shared().read("Tester") != null) {
                    throw new AssertionError("the server still shares the cleared test player");
                }
                Scene.log("lanshare test player cleared on the server too");
            });
            context.runOnClient(minecraft -> {
                String name = minecraft.player.getScoreboardName();
                SharedPositions.Report host = FarPlayerTracker.get().shared().read(name);
                if (host == null || host.riding() || FarPlayerTracker.get().shared().vehicle(name) != null) {
                    throw new AssertionError("the host still rides something after the dismount");
                }
                Scene.log("lanshare host got off");
            });
        }
        // The world closed: the positions are in its file, and nothing in its scoreboard.
        try {
            Path file = root.resolve("livinghorizon-positions.dat");
            if (!Files.isRegularFile(file) || Files.size(file) == 0) throw new AssertionError("no positions saved at " + file);
            // The saved scoreboard moved under data/minecraft/ in 26.1.
            Path scores = root.resolve("data")
                    //? if >=26.1
                    /*.resolve("minecraft")*/
                    .resolve("scoreboard.dat");
            if (Files.exists(scores)) {
                byte[] saved = new GZIPInputStream(Files.newInputStream(scores)).readAllBytes();
                if (new String(saved, StandardCharsets.ISO_8859_1).contains("lh.x")) {
                    throw new AssertionError("objectives were written into the saved world");
                }
            }
            Scene.log("lanshare saved " + Files.size(file) + " bytes of positions, scoreboard untouched");
        } catch (IOException e) {
            throw new AssertionError("could not read the saved world at " + root, e);
        }
    }
}
