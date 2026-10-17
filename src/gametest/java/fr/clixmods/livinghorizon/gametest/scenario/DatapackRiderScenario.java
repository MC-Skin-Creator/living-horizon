package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * The companion data pack itself, installed in a world that is not shared, so that only it
 * writes: a player on a pig gives the pig their number in {@code lh.r}, and the pig loses it
 * once the player gets off. The pack is copied with this version's own pack.mcmeta and files.
 */
public final class DatapackRiderScenario implements Scenario {
    @Override
    public String name() {
        return "datapackrider";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Path packs = context.computeOnClient(minecraft -> minecraft.getSingleplayerServer()
                    .getWorldPath(LevelResource.DATAPACK_DIR));
            install(packs.resolve("living-horizon"));
            Scene.command(world, "reload");
            context.waitTicks(40);
            context.runOnClient(minecraft -> {
                if (!FarPlayerTracker.get().sharing()) throw new AssertionError("the data pack does not run");
            });

            Scene.command(world, "execute at @p run summon pig ~ ~ ~ {NoAI:1b}");
            Scene.command(world, "ride @p mount @e[type=pig,limit=1,sort=nearest]");
            context.waitTicks(20);
            context.runOnClient(minecraft -> {
                Scoreboard scoreboard = minecraft.level.getScoreboard();
                Objective riders = scoreboard.getObjective("lh.r");
                Entity pig = minecraft.player.getVehicle();
                if (riders == null || pig == null) throw new AssertionError("no lh.r objective, or the player rides nothing");
                ReadOnlyScoreInfo player = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(minecraft.player.getScoreboardName()), riders);
                ReadOnlyScoreInfo mount = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(pig.getUUID().toString()), riders);
                if (player == null || player.value() < 1 || mount == null || mount.value() != player.value()) {
                    throw new AssertionError("the pig does not carry the player's number: player "
                            + (player == null ? "none" : player.value()) + ", pig " + (mount == null ? "none" : mount.value()));
                }
                Scene.log("datapackrider player " + player.value() + " rides the pig carrying " + mount.value());
            });

            // The pack publishes the mobs every five seconds: the pig lets go by then.
            Scene.command(world, "ride @p dismount");
            context.waitTicks(120);
            context.runOnClient(minecraft -> {
                Scoreboard scoreboard = minecraft.level.getScoreboard();
                Entity pig = minecraft.level.getEntities((Entity) null, minecraft.player.getBoundingBox().inflate(8),
                        entity -> entity.getType() == EntityType.PIG).stream().findFirst().orElse(null);
                ReadOnlyScoreInfo mount = pig == null ? null
                        : scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(pig.getUUID().toString()), scoreboard.getObjective("lh.r"));
                if (pig == null || mount != null) throw new AssertionError("the pig still carries a rider's number after the dismount");
                Scene.log("datapackrider pig let go");
            });
        }
    }

    /** The pack's folder, with this version's pack.mcmeta and the files it writes over the pack's. */
    private static void install(Path target) {
        Path pack = Path.of(System.getProperty("livinghorizon.datapack"));
        Path overrides = Path.of(System.getProperty("livinghorizon.datapackOverrides"));
        copy(pack, target);
        copy(overrides, target);
    }

    private static void copy(Path from, Path to) {
        try (Stream<Path> files = Files.walk(from)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Path copy = to.resolve(from.relativize(file).toString());
                if (Files.isDirectory(file)) Files.createDirectories(copy);
                else Files.copy(file, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
