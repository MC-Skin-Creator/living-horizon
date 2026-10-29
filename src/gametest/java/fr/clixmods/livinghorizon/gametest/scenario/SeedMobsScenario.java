package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.debug.DebugMarks;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobKinds;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.track.SeedMobs;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A world of the usual terrain, with a seed of its own: the server foresees the animals of the
 * chunks it has not generated yet and shares them; the client draws them, and hides them once
 * its setting is off. Then a square of those chunks is generated: the animals the game put there
 * are compared with those foreseen (how many stand within 2 blocks of one of the same kind, the
 * world frozen so that none wandered off), and the server stops sharing the foreseen ones there.
 */
public final class SeedMobsScenario implements Scenario {
    /** How far from the herd the square generated reaches, in chunks: 11 by 11. */
    private static final int SIDE = 5;

    @Override
    public String name() {
        return "seedmobs";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(4));
        Scene.configure(context, config -> {
            config.seedMobs = true;
            config.onlyOverTerrain = false;
            config.hideOccludedMobs = false;
        });
        // Not the consistent settings: they make a flat world, which has no first animals.
        try (TestSingleplayerContext world = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(settings -> settings.setSeed("living horizon"))
                .create()) {
            context.waitTicks(100);
            //? if >=1.21.11 {
            Scene.command(world, "gamerule advance_time false");
            //?} else {
            /*Scene.command(world, "gamerule doDaylightCycle false");
            *///?}
            Scene.command(world, "time set noon");
            Scene.command(world, "weather clear");
            // Two full shares: the first starts the pass, the next sends what it found.
            context.waitTicks(260);

            List<SharedPositions.MobReport> foreseen = new ArrayList<>();
            List<SharedPositions.MobReport> loaded = new ArrayList<>();
            context.runOnClient(minecraft -> {
                SharedPositions shared = FarPlayerTracker.get().shared();
                for (UUID id : shared.mobs()) {
                    SharedPositions.MobReport mob = shared.readMob(id);
                    if (mob != null && SeedMobs.foreseen(id)) foreseen.add(mob);
                }
                if (foreseen.isEmpty()) throw new AssertionError("the server foresees no animal around");
                // A chunk loaded since the last share: never drawn, and no longer shared at the next.
                for (SharedPositions.MobReport mob : foreseen) {
                    if (minecraft.level.getChunkSource().hasChunk((int) Math.floor(mob.x()) >> 4, (int) Math.floor(mob.z()) >> 4)) loaded.add(mob);
                }
                for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                    if (SeedMobs.foreseen(mob.id()) && minecraft.level.getChunkSource().hasChunk((int) Math.floor(mob.x()) >> 4,
                            (int) Math.floor(mob.z()) >> 4)) {
                        throw new AssertionError("a foreseen mob drawn in a chunk already there: " + mob.type());
                    }
                }
                long drawn = FarPlayerTracker.get().mobs().shown().stream().filter(m -> SeedMobs.foreseen(m.id())).count();
                if (drawn == 0) {
                    throw new AssertionError("no foreseen mob among those drawn: remembered " + FarPlayerTracker.get().mobs().size()
                            + ", drawn " + FarPlayerTracker.get().mobs().shown().size() + ", player at " + minecraft.player.position()
                            + ", first foreseen at " + foreseen.get(0).x() + ", " + foreseen.get(0).z() + " " + MobKinds.type(foreseen.get(0).kind()));
                }
                Scene.log("seedmobs foreseen=" + foreseen.size() + " drawn=" + drawn + " in chunks loaded since=" + loaded.size());
            });
            // Past the render distance the game's fog hides them without Voxy or Distant Horizons: their
            // boxes show where they stand, the camera turned to the nearest herd.
            float[] look = context.computeOnClient(minecraft -> {
                SharedPositions.MobReport herd = foreseen.stream()
                        .min(java.util.Comparator.comparingDouble(mob -> Math.hypot(mob.x() - minecraft.player.getX(),
                                mob.z() - minecraft.player.getZ())))
                        .orElseThrow();
                double dx = herd.x() - minecraft.player.getX(), dz = herd.z() - minecraft.player.getZ();
                double dy = herd.y() - minecraft.player.getEyeY();
                return new float[]{(float) Math.toDegrees(Math.atan2(-dx, dz)),
                        (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))};
            });
            Scene.command(world, "tp @p ~ ~ ~ " + look[0] + " " + look[1]);
            Scene.configure(context, config -> config.debugBoxes = config.debugLabels = true);
            context.waitTicks(20);
            Scene.screenshot(context, this, "foreseen");
            int copies = context.computeOnClient(minecraft -> DebugMarks.count(DebugMarks.Mark.FAKE));
            if (copies == 0) throw new AssertionError("no foreseen herd drawn in front of the camera");
            Scene.log("seedmobs copies drawn in view: " + copies);
            Scene.configure(context, config -> config.debugBoxes = config.debugLabels = false);

            // The client's setting off: none drawn, though the server still shares them.
            Scene.configure(context, config -> config.seedMobs = false);
            context.waitTicks(40);
            context.runOnClient(minecraft -> {
                for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                    if (SeedMobs.foreseen(mob.id())) throw new AssertionError("a foreseen mob drawn with the setting off");
                }
                Scene.log("seedmobs setting off: none drawn");
            });
            Scene.configure(context, config -> config.seedMobs = true);

            // A square of the chunks generated, as a player walking there would: the game's own animals.
            // Around a foreseen herd past the render distance, so that the square has some.
            double[] at = context.computeOnClient(minecraft -> {
                SharedPositions.MobReport herd = foreseen.stream()
                        .min(java.util.Comparator.comparingDouble(mob -> Math.abs(Math.hypot(mob.x() - minecraft.player.getX(),
                                mob.z() - minecraft.player.getZ()) - 250)))
                        .orElseThrow();
                return new double[]{herd.x(), herd.z()};
            });
            int cx0 = (int) Math.floor(at[0]) >> 4, cz0 = (int) Math.floor(at[1]) >> 4;
            int west = cx0 - SIDE, east = cx0 + SIDE, north = cz0 - SIDE, south = cz0 + SIDE;
            List<SharedPositions.MobReport> inSquare = new ArrayList<>();
            for (SharedPositions.MobReport mob : foreseen) {
                int cx = (int) Math.floor(mob.x()) >> 4, cz = (int) Math.floor(mob.z()) >> 4;
                if (cx >= west && cx <= east && cz >= north && cz <= south) inSquare.add(mob);
            }
            // Frozen, so that the animals stand where the game put them, not where they wandered since.
            Scene.command(world, "tick freeze");
            Scene.command(world, "forceload add " + west * 16 + " " + north * 16 + " " + (east * 16 + 15) + " " + (south * 16 + 15));
            context.waitTicks(400);
            int[] near = {0};
            List<String> missed = new ArrayList<>();
            context.runOnClient(minecraft -> {
                ServerLevel level = minecraft.getSingleplayerServer().getLevel(Level.OVERWORLD);
                for (SharedPositions.MobReport mob : inSquare) {
                    String type = MobKinds.type(mob.kind());
                    double best = Double.MAX_VALUE;
                    for (Entity entity : level.getAllEntities()) {
                        if (!EntityType.getKey(entity.getType()).toString().equals(type)) continue;
                        best = Math.min(best, Math.hypot(entity.getX() - mob.x(), entity.getZ() - mob.z()));
                    }
                    if (best <= 2) near[0]++;
                    else missed.add(type + " at " + (int) mob.x() + ", " + (int) mob.y() + ", " + (int) mob.z() + " (nearest " + (int) best + ")");
                }
                Scene.log("seedmobs square: " + near[0] + " of " + inSquare.size() + " foreseen stand within 2 blocks of the game's"
                        + (missed.isEmpty() ? "" : ", missed " + missed));
            });
            Scene.command(world, "tick unfreeze");
            // Shared again after the next full share: the foreseen ones of those chunks are gone.
            context.waitTicks(140);
            List<SharedPositions.MobReport> generated = new ArrayList<>(inSquare);
            generated.addAll(loaded);
            context.runOnClient(minecraft -> {
                SharedPositions shared = FarPlayerTracker.get().shared();
                for (SharedPositions.MobReport mob : generated) {
                    if (shared.readMob(mob.id()) != null) {
                        throw new AssertionError("still foreseen in a generated chunk: " + MobKinds.type(mob.kind()));
                    }
                }
                Scene.log("seedmobs generated chunks: foreseen ones no longer shared");
            });
            // About half stand where the game puts them; the others are herds the game turned down for
            // what the bare terrain lacks (grass, a tree in the way). Draws gone wrong would leave none.
            if (inSquare.isEmpty() || near[0] * 3 < inSquare.size()) {
                throw new AssertionError("fewer than a third of the foreseen mobs stand where the game put them: "
                        + near[0] + " of " + inSquare.size());
            }
            Scene.command(world, "forceload remove all");
        }
    }
}
