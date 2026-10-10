package fr.clixmods.livinghorizon.gametest;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * What the scenarios share: a known starting point, a flat world, mobs in rows,
 * screenshots, and log lines easy to find.
 *
 * <p>The window is 854x480 and the GUI scale 1, so a screenshot shows the world in real
 * pixels. Screenshots land in {@code build/run/clientGameTest/screenshots/}, numbered in
 * the order they are taken.
 */
public final class Scene {
    /** Every line a scenario logs starts with this, to grep the log for. */
    public static final String LOG = "LHTEST ";

    private Scene() {
    }

    public static void log(String line) {
        System.out.println(LOG + line);
    }

    /**
     * Turns on the shader pack named by {@code -Plh.shaderpack=<folder>} (run.sh's
     * {@code --shaders}), when Iris is loaded: copied into Iris's folder, chosen and loaded.
     * Through reflection, Iris being no dependency of the mod. Nothing without the property.
     */
    static void shaderPack(ClientGameTestContext context) {
        String folder = System.getProperty("livinghorizon.shaderpack", "");
        if (folder.isBlank()) return;
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) {
            throw new AssertionError("A shader pack was given, but Iris is not loaded");
        }
        context.runOnClient(minecraft -> {
            try {
                java.nio.file.Path source = java.nio.file.Path.of(folder);
                Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                java.nio.file.Path packs = (java.nio.file.Path) iris.getMethod("getShaderpacksDirectory").invoke(null);
                java.nio.file.Path target = packs.resolve(source.getFileName());
                try (var files = java.nio.file.Files.walk(source)) {
                    for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files::iterator) {
                        java.nio.file.Path to = target.resolve(source.relativize(file).toString());
                        if (java.nio.file.Files.isDirectory(file)) java.nio.file.Files.createDirectories(to);
                        else java.nio.file.Files.copy(file, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                Object config = iris.getMethod("getIrisConfig").invoke(null);
                config.getClass().getMethod("setShaderPackName", String.class).invoke(config, source.getFileName().toString());
                config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true);
                config.getClass().getMethod("save").invoke(config);
                iris.getMethod("reload").invoke(null);
                log("shader pack " + source.getFileName());
            } catch (ReflectiveOperationException | java.io.IOException e) {
                throw new IllegalStateException("Could not load the shader pack " + folder, e);
            }
        });
    }

    /** Back to the defaults between scenarios: settings, options, no screen. */
    static void reset(ClientGameTestContext context) {
        context.setScreen(() -> null);
        context.runOnClient(minecraft -> {
            minecraft.options.guiScale().set(1);
            minecraft.options.fov().set(70);
            minecraft.options.renderDistance().set(12);
            minecraft.options.entityDistanceScaling().set(1.0);
            FarConfig defaults = new FarConfig();
            FarConfig config = FarConfig.get();
            // Field by field: the config object is the one the mod holds.
            for (var field : FarConfig.class.getFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.set(config, field.get(defaults));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
            // Birds and the saucer move on their own: off, so that two screenshots compare.
            config.skyBirds = false;
            config.ufo = false;
        });
    }

    /** Changes the mod's settings, on the render thread. Nothing is saved. */
    public static void configure(ClientGameTestContext context, Consumer<FarConfig> change) {
        context.runOnClient(minecraft -> change.accept(FarConfig.get()));
    }

    /**
     * A new flat world at noon, still: no day cycle, no weather, no mobs spawning. The
     * player stands at 0, 0 facing south (+z). Close it when done (try-with-resources).
     */
    public static TestSingleplayerContext flatWorld(ClientGameTestContext context) {
        TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(true).create();
        // The chunks round the player draw slowly without a graphics card.
        context.waitTicks(100);
        command(world, "gamerule doDaylightCycle false");
        command(world, "gamerule doMobSpawning false");
        command(world, "weather clear");
        command(world, "time set noon");
        command(world, "tp @a 0 ~ 0 0 0");
        context.waitTicks(5);
        return world;
    }

    public static void command(TestSingleplayerContext world, String command) {
        world.getServer().runCommand(command);
    }

    /**
     * One mob standing still ({@code NoAI}) relative to the player, turned to {@code yaw}
     * (0 faces south, away from a player at the start; 180 faces the player).
     */
    public static void summon(TestSingleplayerContext world, String type, double x, double z, float yaw) {
        command(world, "execute at @p run summon " + type + " ~" + x + " ~ ~" + z
                + " {NoAI:1b,Rotation:[" + yaw + "f,0f]}");
    }

    /**
     * A row of mobs across the view, {@code z} blocks ahead, {@code spacing} apart, each
     * kind {@code each} times, turned 45 degrees more each time.
     */
    public static void row(TestSingleplayerContext world, String[] types, int each, double z, double spacing) {
        int count = types.length * each, i = 0;
        for (String type : types) {
            for (int n = 0; n < each; n++, i++) {
                summon(world, type, (i - (count - 1) / 2.0) * spacing, z, i * 45 % 360);
            }
        }
    }

    /** Takes a screenshot after letting a few frames settle. */
    public static void screenshot(ClientGameTestContext context, Scenario scenario, String name) {
        context.waitTicks(5);
        log("screenshot " + context.takeScreenshot(scenario.name() + "-" + name));
    }

    /**
     * The last frame the game drew itself, as it is on screen. A test screenshot draws a
     * frame of its own, outside the game's loop, where the debug gizmos (boxes, crosses)
     * cannot be collected: this one has them. Saved next to the other screenshots.
     */
    public static void screenshotFrame(ClientGameTestContext context, Scenario scenario, String name) {
        context.waitTicks(5);
        //? if >=1.21.11 {
        java.nio.file.Path path = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                .resolve("screenshots").resolve("frame_" + scenario.name() + "-" + name + ".png");
        java.util.concurrent.CompletableFuture<Void> saved = new java.util.concurrent.CompletableFuture<>();
        context.runOnClient(minecraft -> net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget(),
                image -> {
                    try (image) {
                        java.nio.file.Files.createDirectories(path.getParent());
                        image.writeToFile(path);
                        saved.complete(null);
                    } catch (java.io.IOException e) {
                        saved.completeExceptionally(e);
                    }
                }));
        while (!saved.isDone()) context.waitTick();
        saved.join();
        log("screenshot " + path);
        //?} else {
        /*screenshot(context, scenario, name);
        *///?}
    }

    /** The first impostor page as it is in memory: the first four rows of sheets, at 1:1. */
    public static void screenshotAtlas(ClientGameTestContext context, Scenario scenario) {
        context.setScreen(AtlasScreen::new);
        screenshot(context, scenario, "atlas");
        context.setScreen(() -> null);
    }

    /** The impostor page, for {@link #screenshotAtlas}. */
    private static final class AtlasScreen extends Screen {
        AtlasScreen() {
            super(Component.literal("atlas"));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF7090B0);
            // Impostors, and so a page to show, exist from 1.21.9.
            //? if >=1.21.9 {
            var page = ImpostorAtlas.id(0);
            // 1:1 the first 854 pixels of the first four rows, then two sheets per row at a quarter.
            graphics.blit(page, 0, 0, 854, 256, 0f, 854f / 2048f, 0f, 256f / 2048f);
            graphics.blit(page, 0, 270, 512, 334, 0f, 1f, 0f, 256f / 2048f);
            //?}
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    public static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
