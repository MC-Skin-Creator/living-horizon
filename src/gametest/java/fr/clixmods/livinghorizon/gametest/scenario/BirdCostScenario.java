package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.ambient.Ambience;
import fr.clixmods.livinghorizon.ambient.AmbientRenderer;
import fr.clixmods.livinghorizon.ambient.PosedBirds;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * What the birds alone cost: a flat world with a field, a pond, trees and a tall pillar,
 * every kind of bird called several times over, and nothing else of the mod drawn. Measured
 * with the birds off, in 3D, in 3D without their pictures, and in 2D, the camera turning once
 * round each time: the mod's own times for the birds, how many are drawn as models and as
 * pictures, and what the render thread allocates. A game test draws one frame per tick, so its
 * frame time says nothing here: run it with JFR in {@code JAVA_TOOL_OPTIONS} to see where the
 * time goes ({@code jfr print --events jdk.ExecutionSample}, the render thread is "main").
 */
public final class BirdCostScenario implements Scenario {
    private static final int SETTLE = 60, FRAMES = 300;
    /** Each kind and how many groups of it are called. */
    private static final Object[][] CALLED = {
            {Ambience.Type.FLOCKS, 8}, {Ambience.Type.GEESE, 4}, {Ambience.Type.RAPTORS, 6}, {Ambience.Type.PARROTS, 2},
            {Ambience.Type.BATS, 4}, {Ambience.Type.ROBINS, 4}, {Ambience.Type.TITS, 4}, {Ambience.Type.DUCKS, 4},
            {Ambience.Type.PIGEONS, 3},
    };

    private TestSingleplayerContext world;
    private int ground;

    @Override
    public String name() {
        return "birdcost";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.enabled = false;
            config.distantMobs = false;
            config.distantEntities = false;
            config.offlinePlayers = false;
            config.skyBirds = false;
            config.birdDensity = 0;
        });
        try (TestSingleplayerContext flat = Scene.flatWorld(context)) {
            world = flat;
            ground = context.computeOnClient(minecraft -> minecraft.player.getBlockY());
            // A field, a pond, a few trees' leaves and a tall pillar, 30 to 40 blocks round.
            Scene.command(world, "fill 25 " + (ground - 1) + " 25 40 " + (ground - 1) + " 40 farmland");
            Scene.command(world, "fill 25 " + ground + " 25 40 " + ground + " 40 wheat[age=7]");
            Scene.command(world, "fill -40 " + (ground - 1) + " 25 -25 " + (ground - 1) + " 40 water");
            Scene.command(world, "fill -40 " + ground + " -40 -28 " + (ground + 5) + " -28 oak_leaves[persistent=true]");
            Scene.command(world, "fill 28 " + ground + " -32 31 " + (ground + 14) + " -29 stone_bricks");
            context.waitTicks(40);

            fog(context);
            measure(context, "warm-up (3D)", config -> config.skyBirds = true);
            measure(context, "birds off", config -> config.skyBirds = false);
            measure(context, "3D birds", config -> config.skyBirds = true);
            measure(context, "3D birds, Simplified Birds off", config -> {
                config.skyBirds = true;
                config.impostorBirds = false;
            });
            measure(context, "2D birds", config -> {
                config.skyBirds = true;
                config.birdStyle = "2d";
            });

        }
    }

    /**
     * Raptors eight times their size at 100 to 400 blocks, 15 degrees up, with no far terrain:
     * past the render distance (192 blocks here) the fog leaves only pale ghosts of them, and
     * they are not drawn at all.
     */
    private void fog(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.skyBirds = true;
            config.birdStyle = "3d";
            config.impostorBirds = false;
        });
        int[] distances = {100, 160, 220, 300, 400};
        for (int i = 0; i < distances.length; i++) {
            double d = distances[i], across = (i - 2) * d * 0.12;
            PosedBirds.add(Ambience.Species.RAPTOR, 2.2f * 8, across, ground + d * Math.tan(Math.toRadians(15)), d,
                    90f, false, 8f, 0f);
        }
        context.runOnClient(minecraft -> {
            minecraft.player.setYRot(0);
            minecraft.player.setXRot(-15);
        });
        Scene.screenshot(context, this, "fog");
        // Past the render distance the fog hides them: only the two within it are drawn.
        int drawn = context.computeOnClient(minecraft -> AmbientRenderer.drawn());
        Scene.log("birdcost fog: " + drawn + " of " + distances.length + " drawn");
        if (drawn != 2) throw new AssertionError("Birds past the fog are drawn: " + drawn + " of " + distances.length);
        PosedBirds.clear();
        Scene.configure(context, config -> config.skyBirds = false);
    }

    /** Clears the birds, applies the settings, calls every kind, and measures a turn of the camera. */
    private void measure(ClientGameTestContext context, String name, Consumer<FarConfig> change) {
        Scene.configure(context, config -> {
            config.skyBirds = false;
            config.birdStyle = "3d";
            config.impostorBirds = true;
        });
        context.waitTicks(2);
        Scene.configure(context, change);
        context.runOnClient(BirdCostScenario::call);
        context.waitTicks(SETTLE);
        long thread = context.computeOnClient(minecraft -> Thread.currentThread().threadId());
        long allocated = allocated(thread);
        int birds = 0, asModels = 0, asPictures = 0;
        for (int frame = 0; frame < FRAMES; frame++) {
            context.waitTick();
            int next = frame + 1;
            int[] counts = context.computeOnClient(minecraft -> {
                look(minecraft, next);
                return new int[]{FarPlayerTracker.get().ambience().flyers().size(), AmbientRenderer.asModels(),
                        AmbientRenderer.asPictures()};
            });
            birds += counts[0];
            asModels += counts[1];
            asPictures += counts[2];
        }
        long bytes = (allocated(thread) - allocated) / FRAMES;
        double[] times = context.computeOnClient(minecraft -> new double[]{
                average(Profile.Section.TICK_AMBIENCE), average(Profile.Section.SUBMIT_BIRDS),
                average(Profile.Section.SUBMIT_IMPOSTORS), average(Profile.Section.EXTRACT)});
        Scene.log(String.format(Locale.ROOT, "birdcost %-32s birds %5.1f (models %5.1f, pictures %5.1f), tick %.3f ms, "
                        + "submit birds %.3f ms, submit impostors %.3f ms, extract %.3f ms, %d KB allocated/frame", name,
                birds / (double) FRAMES, asModels / (double) FRAMES, asPictures / (double) FRAMES, times[0], times[1],
                times[2], times[3], bytes / 1024));
    }

    /** Every kind, several times: the perching ones on the field, the pond, the leaves, the pillar. */
    private static void call(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return;
        Ambience ambience = FarPlayerTracker.get().ambience();
        int y = minecraft.player.getBlockY();
        for (Object[] entry : CALLED) {
            Ambience.Type type = (Ambience.Type) entry[0];
            BlockPos looked = switch (type) {
                case ROBINS -> new BlockPos(32, y, 32);
                case DUCKS -> new BlockPos(-32, y - 1, 32);
                case TITS -> new BlockPos(-34, y + 5, -34);
                case PIGEONS -> new BlockPos(29, y + 14, -30);
                default -> null;
            };
            for (int i = 0; i < (int) entry[1]; i++) {
                ambience.summon(type, minecraft.level, minecraft.player, FarConfig.get(), looked);
            }
        }
    }

    private static double average(Profile.Section section) {
        return section.cpu().seen() ? section.cpu().average() / 1_000_000.0 : 0;
    }

    /** The camera path: once round, level. */
    private static void look(Minecraft minecraft, int frame) {
        if (minecraft.player == null) return;
        float yaw = 360f * frame / FRAMES;
        minecraft.player.setYRot(yaw);
        minecraft.player.yRotO = yaw;
        minecraft.player.setYHeadRot(yaw);
        minecraft.player.setXRot(-15);
        minecraft.player.xRotO = -15;
    }

    private static long allocated(long thread) {
        if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean) {
            return bean.getThreadAllocatedBytes(thread);
        }
        return -1;
    }
}
