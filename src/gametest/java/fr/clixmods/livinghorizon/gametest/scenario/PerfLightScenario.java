package fr.clixmods.livinghorizon.gametest.scenario;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.LightModels;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the light models save with a crowd of geared figures drawn as models: 306 test players
 * who logged off in enchanted diamond armour, a sword and a shield, on a grid 20 to 105 blocks
 * in front of a still camera, all within the impostor distance. The frame is timed with the
 * setting at Never, at its default and at the top of its range, Never measured again between
 * each so that a drift of the game cancels out. Prints the frame times, the mod's own time
 * and the polygons.
 */
public final class PerfLightScenario implements Scenario {
    private static final int GROUND = -60;
    private static final int SETTLE = 60, FRAMES = 200;
    private static final List<String> GEAR = List.of(
            "{id:\"minecraft:diamond_sword\",count:1,components:{\"minecraft:enchantment_glint_override\":true}}",
            "{id:\"minecraft:shield\",count:1,components:{\"minecraft:enchantment_glint_override\":true}}",
            "{id:\"minecraft:diamond_boots\",count:1,components:{\"minecraft:enchantment_glint_override\":true}}",
            "{id:\"minecraft:diamond_leggings\",count:1,components:{\"minecraft:enchantment_glint_override\":true}}",
            "{id:\"minecraft:diamond_chestplate\",count:1,components:{\"minecraft:enchantment_glint_override\":true}}",
            "{id:\"minecraft:diamond_helmet\",count:1,components:{\"minecraft:enchantment_glint_override\":true}}");

    @Override
    public String name() {
        return "perflight";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.offlineLife = false;
            config.offlineNames = false;
            config.maxOfflinePlayers = FarConfig.UNLIMITED_OFFLINE;
            // Models, never pictures, whatever their size on screen.
            config.impostorPixels = 0;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            context.runOnClient(PerfLightScenario::crowd);
            Scene.command(world, "tp @a 0.5 " + GROUND + " 0.5 0 0");
            context.waitTicks(200);
            int shown = context.computeOnClient(minecraft -> FarPlayerTracker.get().resting().spots().size());
            if (shown < 300) throw new AssertionError("perflight: only " + shown + " players who logged off");

            measure(context, 0, "warm-up");
            Row never1 = measure(context, 0, "never");
            Row some = measure(context, 64, "default");
            Row never2 = measure(context, 0, "never");
            Row all = measure(context, FarConfig.MAX_LIGHT_MODEL_PIXELS, "top");
            Row never3 = measure(context, 0, "never");
            double never = (never1.frame + never2.frame + never3.frame) / 3;
            double noise = Math.max(Math.abs(never1.frame - never2.frame), Math.abs(never2.frame - never3.frame));
            Scene.screenshot(context, this, "crowd");

            line("");
            line("======== What the light models save ========");
            line("Scene: " + shown + " players who logged off in enchanted diamond armour with a sword and a shield,");
            line("20 to 105 blocks in front of the camera, all drawn as models. " + FRAMES + " frames each; llvmpipe draws on the CPU.");
            line(String.format(Locale.ROOT, "Never (0 px) ............. %6.1f ms per frame (%.1f FPS), noise up to %.1f ms", never, 1000 / never, noise));
            for (Row row : List.of(some, all)) {
                line(String.format(Locale.ROOT, "%-24s %6.1f ms per frame (%.1f FPS): %+.1f ms (%+.0f%%), %d light models, %d polygons (%d at Never), mod's code %.2f ms",
                        row.name + " (" + row.pixels + " px) ....", row.frame, 1000 / row.frame, row.frame - never, (row.frame - never) / never * 100,
                        row.lightened, row.polygons, never1.polygons, row.own));
            }
            context.runOnClient(minecraft -> FarPlayerTracker.get().resting().clearFakes());
        }
    }

    /** 17 columns 5 blocks apart, 18 rows from 20 to 105 blocks south, every one facing the camera. */
    private static void crowd(Minecraft minecraft) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        String dimension = SharedPositions.dimensionId(minecraft.level.dimension());
        int n = 0;
        for (int z = 20; z <= 105; z += 5) {
            for (int x = -40; x <= 40; x += 5) {
                String name = "Geared" + n++;
                resting.fake(name, new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name), dimension,
                        x + 0.5, GROUND, z + 0.5, 180f, 10, null, GEAR);
            }
        }
    }

    private static final class Row {
        String name;
        int pixels, lightened, polygons;
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
        row.pixels = pixels;
        row.frame = frames.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        row.own = own.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        row.lightened = context.computeOnClient(minecraft -> LightModels.count());
        row.polygons = context.computeOnClient(minecraft -> PolygonStats.polygons());
        return row;
    }

    private static void line(String text) {
        Scene.log("perflight " + text);
    }
}
