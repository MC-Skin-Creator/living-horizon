package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.LightModels;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobScan;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the light models save on a crowd of mobs drawn as models: about 500 saddled horses in
 * diamond armour and 500 zombies in enchanted iron armour with a sword, remembered copies
 * standing 48 to 200 blocks in front of a still camera that looks through a narrow field of
 * view, every one within the impostor distance. The frame is timed with the setting at Never
 * and at a sweep of sizes, Never measured again between each so that a drift of the game
 * cancels out. Prints the frame times, the light models and the polygons at each size.
 */
public final class PerfMobsScenario implements Scenario {
    private static final int GROUND = -60;
    private static final int SETTLE = 60, FRAMES = 200;
    private static final int[] SIZES = {8, 16, 32, 64};
    private static final String GLINT = "components:{\"minecraft:enchantment_glint_override\":true}";
    private static final String HORSE = "{NoAI:1b,Tame:1b,PersistenceRequired:1b,Rotation:[180f,0f],equipment:{"
            + "saddle:{id:\"minecraft:saddle\",count:1},body:{id:\"minecraft:diamond_horse_armor\",count:1}}}";
    private static final String ZOMBIE = "{NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],equipment:{"
            + "head:{id:\"minecraft:iron_helmet\",count:1," + GLINT + "},chest:{id:\"minecraft:iron_chestplate\",count:1," + GLINT + "},"
            + "legs:{id:\"minecraft:iron_leggings\",count:1," + GLINT + "},feet:{id:\"minecraft:iron_boots\",count:1," + GLINT + "},"
            + "mainhand:{id:\"minecraft:iron_sword\",count:1," + GLINT + "}}}";

    @Override
    public String name() {
        return "perfmobs";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.maxDistantMobs = FarConfig.UNLIMITED_MOBS;
            config.mobMaxDistance = 1000;
            config.impostorDistance = 2000;
            config.animationMinPixels = 0;
            if (!config.mobTypes.contains("minecraft:zombie")) config.mobTypes.add("minecraft:zombie");
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // A wedge in front of the camera, as wide as the narrow view: horses and zombies by turns.
            int horses = 0, zombies = 0;
            for (int z = 48; z <= 200; z += 4) {
                double half = z * 0.26;
                for (double x = -half; x <= half; x += 2.5) {
                    boolean horse = (horses + zombies) % 2 == 0;
                    Scene.command(world, "summon " + (horse ? "horse " : "zombie ") + String.format(Locale.ROOT, "%.1f", x)
                            + " " + GROUND + " " + z + " " + (horse ? HORSE : ZOMBIE));
                    if (horse) horses++;
                    else zombies++;
                }
            }
            context.waitTicks(100);
            context.runOnClient(minecraft -> MobScan.start(minecraft, 16));
            context.waitTicks(100);
            // The server stops sending them past three chunks: their copies take over, as models.
            context.runOnClient(minecraft -> {
                minecraft.options.renderDistance().set(3);
                minecraft.options.fov().set(30);
            });
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 0");
            context.waitTicks(600);
            int shown = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().shown().size());
            int drawn = context.computeOnClient(minecraft -> Stats.drawn());
            Scene.log("perfmobs " + horses + " horses and " + zombies + " zombies summoned, " + shown + " copies, " + drawn + " drawn");
            if (shown < 800) throw new AssertionError("perfmobs: only " + shown + " remembered copies, the scene is wrong");

            measure(context, 0, "warm-up");
            List<Row> nevers = new ArrayList<>(), sizes = new ArrayList<>();
            nevers.add(measure(context, 0, "never"));
            for (int size : SIZES) {
                sizes.add(measure(context, size, size + " px"));
                nevers.add(measure(context, 0, "never"));
            }
            Scene.screenshot(context, this, "crowd");
            double never = nevers.stream().mapToDouble(row -> row.frame).average().orElse(0);
            double noise = 0;
            for (int i = 1; i < nevers.size(); i++) noise = Math.max(noise, Math.abs(nevers.get(i).frame - nevers.get(i - 1).frame));

            line("");
            line("======== What the light models save on mobs ========");
            line("Scene: " + horses + " saddled horses in diamond armour and " + zombies + " zombies in enchanted iron armour with a sword,");
            line("remembered copies 48 to 200 blocks in front of the camera (FOV 30), all drawn as models, " + drawn + " in view.");
            line(FRAMES + " frames each; llvmpipe draws on the CPU. The sizes are those of the 854x480 test window: about");
            line("half of what a 1080p screen shows, so each size here stands for twice as much there.");
            line(String.format(Locale.ROOT, "Never (0 px) ......... %6.1f ms per frame (%.1f FPS), %d polygons; noise up to %.1f ms",
                    never, 1000 / never, nevers.get(0).polygons, noise));
            for (Row row : sizes) {
                line(String.format(Locale.ROOT, "%-20s %6.1f ms per frame (%.1f FPS): %+.1f ms (%+.0f%%), %d light models, %d polygons, mod's code %.2f ms",
                        row.name + " ....", row.frame, 1000 / row.frame, row.frame - never, (row.frame - never) / never * 100,
                        row.lightened, row.polygons, row.own));
            }
            context.runOnClient(minecraft -> FarPlayerTracker.get().mobs().forgetAll());
        }
    }

    private static final class Row {
        String name;
        int lightened, polygons;
        double frame, own;
    }

    private static Row measure(ClientGameTestContext context, int pixels, String name) {
        context.runOnClient(minecraft -> FarConfig.get().lightModelPixels = pixels);
        System.gc();
        context.waitTicks(SETTLE);
        List<Long> frames = new ArrayList<>(), own = new ArrayList<>();
        for (int frame = 0; frame < FRAMES; frame++) {
            long started = System.nanoTime();
            context.waitTick();
            frames.add(System.nanoTime() - started);
            own.add(context.computeOnClient(minecraft -> Stats.lastTickNanos() + Stats.lastExtractNanos()));
        }
        Row row = new Row();
        row.name = name;
        row.frame = frames.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        row.own = own.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        row.lightened = context.computeOnClient(minecraft -> LightModels.count());
        row.polygons = context.computeOnClient(minecraft -> PolygonStats.polygons());
        return row;
    }

    private static void line(String text) {
        Scene.log("perfmobs " + text);
    }
}
