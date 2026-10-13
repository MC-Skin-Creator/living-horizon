package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.ambient.Ambience;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tits on a tree top, ducks on a pond, robins in a field, left to themselves: a perched
 * bird must stand on what it belongs on (leaves, water, dry ground) at its height, and a
 * hop must never cross more ground than a hop. Then the player walks up to the tits and
 * away again, and the leaves under one of them are broken: each time the birds must land
 * properly, without darting to and fro. Last, the player walks up to the ducks: they must
 * fly off and land farther along the pond, out of reach, not vanish.
 */
public final class PerchersScenario implements Scenario {
    /** Blocks a bird may cross in a tick while it is not flying: a hop is about 0.1. */
    private static final double HOP = 0.45;

    private TestSingleplayerContext world;
    private int ground;
    private final Map<Ambience.Flyer, double[]> last = new IdentityHashMap<>();
    private int misplaced, darts, checked;

    @Override
    public String name() {
        return "perchers";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.skyBirds = true;
            config.birdDensity = 500;
            config.hiddenBirds = new ArrayList<>(List.of("flocks", "geese", "raptors", "gulls", "pigeons", "bats", "parrots"));
        });
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(6));
        try (TestSingleplayerContext flat = Scene.flatWorld(context)) {
            world = flat;
            ground = context.computeOnClient(minecraft -> minecraft.player.getBlockY());
            int canopy = ground + 5;
            // A wide tree top, with a few bumps a block higher, on trunks.
            Scene.command(world, "fill -24 " + canopy + " 20 24 " + canopy + " 68 oak_leaves[persistent=true]");
            for (int x = -20; x <= 20; x += 8) {
                Scene.command(world, "fill " + x + " " + (canopy + 1) + " 24 " + (x + 2) + " " + (canopy + 1) + " 64 oak_leaves[persistent=true]");
                Scene.command(world, "fill " + x + " " + ground + " 30 " + x + " " + (canopy - 1) + " 30 oak_log");
            }
            // A pond, and a field of wheat beside it.
            Scene.command(world, "fill -24 " + (ground - 1) + " -68 24 " + (ground - 1) + " -20 water");
            Scene.command(world, "fill 30 " + (ground - 1) + " -24 70 " + (ground - 1) + " 24 farmland");
            Scene.command(world, "fill 30 " + ground + " -24 70 " + ground + " 24 wheat");

            int waited = 0;
            while (waited < 3000 && (count(context, Ambience.Species.TIT) == 0 || count(context, Ambience.Species.DUCK) == 0)) {
                context.waitTicks(20);
                waited += 20;
            }
            Scene.log("perchers settled after " + waited + " ticks: tits=" + count(context, Ambience.Species.TIT)
                    + " ducks=" + count(context, Ambience.Species.DUCK) + " robins=" + count(context, Ambience.Species.ROBIN));
            watch(context, 400, "left alone");

            // From the edge of the tree top and of the pond, out of their reach, zoomed in.
            // Spectating, so as not to fall from where the camera is put.
            Scene.command(world, "gamemode spectator @a");
            context.runOnClient(minecraft -> minecraft.options.fov().set(40));
            look(context, 0, canopy + 5, 8, 0f, 30f);
            Scene.screenshot(context, this, "tits");
            look(context, 0, ground + 6, -6, 180f, 25f);
            Scene.screenshot(context, this, "ducks");
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));
            Scene.command(world, "gamemode survival @a");

            // Up to the tits, a while, then away again.
            double[] tit = position(context, Ambience.Species.TIT);
            if (tit != null) {
                look(context, tit[0] + 2, canopy + 1, tit[2] + 2, 0f, 30f);
                watch(context, 200, "scared");
                look(context, 0, ground, 0, 0f, 30f);
                watch(context, 600, "coming back");
            }

            // The leaves under a tit broken: it must not stay standing on nothing.
            tit = position(context, Ambience.Species.TIT);
            if (tit != null) {
                int bx = Mth.floor(tit[0]), bz = Mth.floor(tit[2]);
                Scene.command(world, "fill " + (bx - 1) + " " + (canopy - 1) + " " + (bz - 1) + " " + (bx + 1) + " "
                        + (canopy + 1) + " " + (bz + 1) + " air");
                watch(context, 200, "leaves broken");
            }
            // Up to the ducks: the same ducks must be on the water afterwards, out of reach.
            double[] duck = position(context, Ambience.Species.DUCK);
            List<Ambience.Flyer> ducks = context.computeOnClient(minecraft -> {
                List<Ambience.Flyer> all = new ArrayList<>();
                for (Ambience.Flyer flyer : FarPlayerTracker.get().ambience().flyers()) {
                    if (flyer.species == Ambience.Species.DUCK) all.add(flyer);
                }
                return all;
            });
            if (duck != null) {
                pond(context, "ducks-before");
                look(context, duck[0] + 3, ground, duck[2], 0f, 20f);
                watch(context, 300, "ducks approached");
                pond(context, "ducks-after");
                String lost = context.computeOnClient(minecraft -> {
                    List<Ambience.Flyer> now = FarPlayerTracker.get().ambience().flyers();
                    for (Ambience.Flyer flyer : ducks) {
                        if (!now.contains(flyer) || flyer.scale < 1f) return "a duck left";
                        double away = Math.hypot(flyer.x - minecraft.player.getX(), flyer.z - minecraft.player.getZ());
                        if (away < 14) return "a duck " + away + " blocks away";
                    }
                    return null;
                });
                Scene.log("perchers ducks after being approached: " + (lost == null ? "all moved on the water" : lost));
                if (lost != null) throw new AssertionError("Ducks approached: " + lost);
            }
            Scene.log("perchers checked=" + checked + " misplaced=" + misplaced + " darts=" + darts);
            if (checked == 0) throw new AssertionError("No perched bird to check");
            if (misplaced * 50 > checked) throw new AssertionError("Birds standing where they should not: " + misplaced);
            if (darts > 0) throw new AssertionError("Birds darting faster than a hop: " + darts);
        }
    }

    /** The whole pond from above its south bank, out of the ducks' reach. */
    private void pond(ClientGameTestContext context, String name) {
        Scene.command(world, "gamemode spectator @a");
        context.runOnClient(minecraft -> minecraft.options.fov().set(60));
        look(context, 0, ground + 22, 6, 180f, 38f);
        Scene.screenshot(context, this, name);
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
        Scene.command(world, "gamemode survival @a");
    }

    private void look(ClientGameTestContext context, double x, double y, double z, float yaw, float pitch) {
        Scene.command(world, "tp @a " + x + " " + y + " " + z + " " + yaw + " " + pitch);
        context.waitTicks(10);
    }

    private static int count(ClientGameTestContext context, Ambience.Species species) {
        return context.computeOnClient(minecraft -> {
            int n = 0;
            for (Ambience.Flyer flyer : FarPlayerTracker.get().ambience().flyers()) if (flyer.species == species) n++;
            return n;
        });
    }

    private static double[] position(ClientGameTestContext context, Ambience.Species species) {
        return context.computeOnClient(minecraft -> {
            for (Ambience.Flyer flyer : FarPlayerTracker.get().ambience().flyers()) {
                if (flyer.species == species && flyer.perched) return new double[]{flyer.x, flyer.y, flyer.z};
            }
            return null;
        });
    }

    /** Every tick for a while: where each perched bird stands, and how far it moved. */
    private void watch(ClientGameTestContext context, int ticks, String phase) {
        int before = misplaced, dartsBefore = darts;
        last.clear();
        for (int tick = 0; tick < ticks; tick++) {
            context.waitTick();
            context.runOnClient(minecraft -> {
                ClientLevel level = minecraft.level;
                for (Ambience.Flyer flyer : FarPlayerTracker.get().ambience().flyers()) {
                    if (flyer.kind != Ambience.Kind.SILHOUETTE) continue;
                    double[] was = last.put(flyer, new double[]{flyer.x, flyer.z, flyer.perched ? 1 : 0});
                    if (!flyer.perched || flyer.scale < 1f) continue;
                    if (was != null && was[2] == 1 && Math.hypot(flyer.x - was[0], flyer.z - was[1]) > HOP) {
                        darts++;
                        Scene.log("perchers dart " + flyer.species + " " + Math.hypot(flyer.x - was[0], flyer.z - was[1]));
                    }
                    int bx = Mth.floor(flyer.x), bz = Mth.floor(flyer.z);
                    if (!level.hasChunk(bx >> 4, bz >> 4)
                            || level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) <= level.getMinY()) continue;
                    checked++;
                    boolean tree = flyer.species == Ambience.Species.TIT, duck = flyer.species == Ambience.Species.DUCK;
                    int top = level.getHeight(tree || duck ? Heightmap.Types.WORLD_SURFACE
                            : Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                    BlockState below = level.getBlockState(new BlockPos(bx, top - 1, bz));
                    boolean water = below.getFluidState().is(FluidTags.WATER);
                    boolean on = tree ? below.is(BlockTags.LEAVES) : duck == water;
                    // A peck dips a bird a little, a duck rocks a little below the surface.
                    double off = flyer.y - top;
                    if (!on || off > 0.4 || off < -0.25) {
                        misplaced++;
                        if (misplaced - before <= 3) {
                            Scene.log("perchers misplaced " + flyer.species + " on " + below.getBlock() + " off=" + off);
                        }
                    }
                }
            });
        }
        Scene.log("perchers " + phase + ": misplaced=" + (misplaced - before) + " darts=" + (darts - dartsBefore));
    }
}
