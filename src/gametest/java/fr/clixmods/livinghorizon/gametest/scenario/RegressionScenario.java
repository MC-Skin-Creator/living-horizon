package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.Links;
import fr.clixmods.livinghorizon.debug.Benchmark;
import fr.clixmods.livinghorizon.debug.BenchmarkWorld;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CloudStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * The regression bench's scene (`.claude/skills/regression-bench/`): the same pictures from the
 * same spot, on every game version and with every set of mods, to compare from one build to
 * the next and between versions.
 *
 * <p>The world is the benchmark's ({@link BenchmarkWorld}): a flat world of a fixed seed, every
 * mob summoned at a fixed place with a fixed id and no AI, a wall and a hill. Nothing moves:
 * the game's ticks are frozen ({@code /tick freeze}) before the mobs are there (a horse
 * without AI still grazes), the distant copies' animations are held from the start (their
 * loop never begins, so each stands where its id puts it), players who logged off stand, no
 * birds, no easter eggs, no clouds, no view bobbing, no hand. Each shot is taken twice, a second apart: what differs
 * between the two (a shader pack's animated water, its temporal noise) is left out of the
 * comparison.
 *
 * <p>Views: the render distance at 12 (the game draws the ground, the mod the mobs past its
 * entity distance), then at 5 (Voxy's or Distant Horizons' terrain takes over past 80 blocks),
 * and zoomed in on the far herd. Each with full models and with simplified mobs, with the
 * debug boxes (which mobs are drawn, and by whom), and the depth view. The numbers the
 * bench compares are logged as {@code LHTEST regression stat <key>=<value>}.
 *
 * <p>With {@code -Plh.perf=true}, {@code /livinghorizon benchmark} runs at the end, from the
 * same spot: its report is logged as {@code LHTEST regression perf | <line>}.
 */
public final class RegressionScenario implements Scenario {
    /** Ticks between the two shots of a pair. */
    private static final int PAIR_GAP = 20;
    /** Ticks for a change to settle before a shot: shader packs blend several frames together. */
    private static final int SETTLE = 40;

    @Override
    public String name() {
        return "regression";
    }

    @Override
    public void run(ClientGameTestContext context) {
        still(context);
        context.runOnClient(BenchmarkWorld::start);
        boolean frozen = false;
        for (int i = 0; i < 20 * 60 * 4 && context.computeOnClient(minecraft -> BenchmarkWorld.running()); i++) {
            // Frozen before its mobs are summoned: no entity ever ticks, so none plays the game's
            // own idle animations (a horse grazing, a chicken laying), which a mob without AI keeps.
            if (!frozen) frozen = context.computeOnClient(RegressionScenario::freeze);
            context.waitTicks(10);
        }
        if (!context.computeOnClient(BenchmarkWorld::isIn)) throw new AssertionError("The benchmark world was never built");
        // The world's own options were saved with it: set again once in.
        still(context);
        context.runOnClient(minecraft -> {
            minecraft.options.renderDistance().set(12);
            minecraft.options.entityDistanceScaling().set(1.0);
            minecraft.options.fov().set(70);
            BenchmarkWorld.toSpot(minecraft);
            BenchmarkWorld.placeCrowd(minecraft);
        });
        // The chunks drawn, the far terrain mods given what they read as chunks arrive.
        context.waitTicks(400);
        stat("placed", BenchmarkWorld.mobs());
        stat("crowd", context.computeOnClient(minecraft -> BenchmarkWorld.crowdPlaced()));
        context.runOnClient(minecraft -> {
            for (Links.Report link : Links.report()) Scene.log("regression stat link." + link.mod() + "=" + link.status());
        });

        view(context, "near", 12, 70);
        view(context, "far", 5, 70);
        view(context, "zoom", 5, 30);

        if (Boolean.getBoolean("livinghorizon.perf")) perf(context);

        context.runOnClient(minecraft -> {
            BenchmarkWorld.clearCrowd();
            BenchmarkWorld.leave(minecraft);
        });
        for (int i = 0; i < 600 && (net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl.isServerRunning
                || context.computeOnClient(minecraft -> minecraft.level != null)); i++) {
            context.waitTicks(5);
        }
        context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
    }

    /** {@code /tick freeze} in the world just opened; false while there is none yet. */
    private static boolean freeze(net.minecraft.client.Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.level == null) return false;
        server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), "tick freeze"));
        return true;
    }

    /** Everything that moves on its own, stopped; the hand and the crosshair hidden. */
    private static void still(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.cloudStatus().set(CloudStatus.OFF);
            minecraft.options.bobView().set(false);
            hideGui(minecraft, true);
        });
        Scene.configure(context, config -> {
            config.skyBirds = false;
            config.easterEggs = false;
            config.offlineLife = false;
            // Every copy past a block away is still from the moment it is made.
            config.animationMaxDistance = 1;
            config.debugHud = false;
            config.debugBoxes = false;
            config.debugDepthView = 0;
        });
    }

    /** The interface hidden (F1) or shown. From 26.2 the HUD holds it, and only toggles. */
    private static void hideGui(net.minecraft.client.Minecraft minecraft, boolean hide) {
        //? if >=26.2 {
        /*if (minecraft.gui.hud.isHidden() != hide) minecraft.gui.hud.toggle();
        *///?} else
        minecraft.options.hideGui = hide;
    }

    /** The shots of one view: models, simplified mobs, the debug boxes, the depth view. */
    private void view(ClientGameTestContext context, String view, int renderDistance, int fov) {
        context.runOnClient(minecraft -> {
            minecraft.options.renderDistance().set(renderDistance);
            minecraft.options.fov().set(fov);
            BenchmarkWorld.toSpot(minecraft);
        });
        context.waitTicks(200);
        for (boolean impostors : new boolean[]{false, true}) {
            Scene.configure(context, config -> config.impostors = impostors);
            if (impostors) context.waitFor(minecraft -> ImpostorAtlas.sheets() > 0 || ImpostorAtlas.failed(), 2000);
            String mode = impostors ? "impostors" : "models";
            pair(context, view + "-" + mode, false);
            numbers(context, view + "." + mode);
        }
        Scene.configure(context, config -> {
            config.impostors = true;
            config.debugBoxes = true;
        });
        pair(context, view + "-boxes", true);
        Scene.configure(context, config -> {
            config.debugBoxes = false;
            config.debugDepthView = 4;
        });
        // The depth view is drawn with the HUD.
        context.runOnClient(minecraft -> hideGui(minecraft, false));
        pair(context, view + "-depth", true);
        context.runOnClient(minecraft -> hideGui(minecraft, true));
        Scene.configure(context, config -> config.debugDepthView = 0);
    }

    /** Two shots of the same view, a second apart: {@code <name>-a} and {@code <name>-b}. */
    private void pair(ClientGameTestContext context, String name, boolean frame) {
        context.waitTicks(SETTLE);
        for (String suffix : new String[]{"a", "b"}) {
            if (suffix.equals("b")) context.waitTicks(PAIR_GAP);
            if (frame) Scene.screenshotFrame(context, this, name + "-" + suffix);
            else Scene.screenshot(context, this, name + "-" + suffix);
        }
    }

    /** What the mod draws in this view, as numbers to compare. */
    private static void numbers(ClientGameTestContext context, String prefix) {
        context.runOnClient(minecraft -> {
            var mobs = FarPlayerTracker.get().mobs();
            stat(prefix + ".remembered", mobs.size());
            stat(prefix + ".shown", mobs.shown().size());
            stat(prefix + ".live", mobs.live().size());
            stat(prefix + ".models", PolygonStats.models());
            stat(prefix + ".impostors", PolygonStats.impostors());
            stat(prefix + ".sheets", ImpostorAtlas.sheets());
            Scene.log("regression stat " + prefix + ".depth=" + FarDepth.state());
            Scene.log("regression stat " + prefix + ".order=" + (FarDepth.outOfOrder() == null ? "ok" : FarDepth.outOfOrder()));
        });
    }

    private static void stat(String key, int value) {
        Scene.log("regression stat " + key + "=" + value);
    }

    /** {@code /livinghorizon benchmark} from the spot, its report in the log. */
    private static void perf(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(BenchmarkWorld.RENDER_DISTANCE));
        context.waitTicks(100);
        if (!context.computeOnClient(minecraft -> Benchmark.start(minecraft, Benchmark.DEFAULT_SECONDS))) {
            throw new AssertionError("The benchmark did not start");
        }
        for (int i = 0; i < 20 * 60 * 15 && context.computeOnClient(minecraft -> Benchmark.running()); i++) {
            context.waitTicks(10);
        }
        if (context.computeOnClient(minecraft -> Benchmark.running())) throw new AssertionError("The benchmark never ended");
        Path folder = context.computeOnClient(minecraft -> minecraft.gameDirectory.toPath())
                .resolve("livinghorizon").resolve("benchmarks");
        try (Stream<Path> files = Files.list(folder)) {
            Path report = files.max(Comparator.comparing(Path::toString)).orElseThrow(() -> new AssertionError("No report"));
            for (String line : Files.readAllLines(report)) Scene.log("regression perf | " + line);
        } catch (IOException e) {
            throw new AssertionError("Could not read the benchmark report", e);
        }
    }
}
