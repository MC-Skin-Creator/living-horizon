package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.QualityPreset;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The mod's pages in Sodium's video settings: each one on screen, in a 720p window at GUI
 * scale 2, and a preset chosen there setting the sliders it covers. Needs
 * Sodium 0.8 or later ({@code --sodium}); without it there is nothing to see.
 */
public final class SodiumPagesScenario implements Scenario {
    private static final String SODIUM = "net.caffeinemc.mods.sodium.client.";

    @Override
    public String name() {
        return "sodium";
    }

    @Override
    public void run(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("sodium")) {
            Scene.log("sodium: not loaded, nothing to check (run with --sodium)");
            return;
        }
        //? if sodium {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // A 720p window at GUI scale 2, as many play; no HUD under the screen, the cursor out of the way.
            context.getInput().resizeWindow(1280, 720);
            context.runOnClient(minecraft -> {
                minecraft.options.guiScale().set(2);
                hideGui(minecraft, true);
            });
            // Birds on, as by default: their settings greyed out otherwise.
            Scene.configure(context, config -> config.skyBirds = true);
            List<?> pages = context.computeOnClient(minecraft -> pages());
            Scene.log("sodium: " + pages.size() + " pages");
            if (pages.isEmpty()) throw new AssertionError("Sodium shows no Living Horizon page");

            for (int i = 0; i < pages.size(); i++) {
                Object page = pages.get(i);
                context.setScreen(() -> open(page));
                // Opening a screen puts the cursor back in the middle, over a setting.
                context.getInput().setCursorPos(1279, 719);
                screenshot(context, "page" + (i + 1));
            }
            // A tooltip: the cursor over a setting of the last page.
            context.getInput().setCursorPos(700, 260);
            screenshot(context, "tooltip");

            // A preset chosen on the page, then "Apply": it sets the distances, which the page shows at once.
            context.runOnClient(minecraft -> {
                Object config = config();
                Object preset = call(config, "getOption", new Class<?>[]{Identifier.class}, id("preset"));
                call(preset, "modifyValue", new Class<?>[]{Object.class},
                        fr.clixmods.livinghorizon.ui.SodiumOptions.Preset.HIGH);
                call(config, "applyAllOptions", new Class<?>[0]);
            });
            int saved = context.computeOnClient(minecraft -> FarConfig.get().impostorDistance);
            int shown = context.computeOnClient(minecraft -> (Integer) call(
                    call(config(), "getOption", new Class<?>[]{Identifier.class}, id("impostordistance")),
                    "getValidatedValue", new Class<?>[0]));
            boolean high = context.computeOnClient(minecraft -> QualityPreset.HIGH.matches(FarConfig.get()));
            Scene.log("sodium: preset High, impostor distance saved " + saved + ", shown " + shown);
            context.setScreen(() -> open(pages.get(1)));
            context.getInput().setCursorPos(1279, 719);
            screenshot(context, "preset-high");
            context.setScreen(() -> null);
            context.runOnClient(minecraft -> hideGui(minecraft, false));
            context.getInput().resizeWindow(854, 480);
            if (!high || shown != saved) {
                throw new AssertionError("Preset from Sodium's page: saved " + saved + ", shown " + shown);
            }
        }
        //?}
    }

    /** The HUD hidden, as F1 does; from 26.2 it is the overlay's own, and stays shown here. */
    private static void hideGui(net.minecraft.client.Minecraft minecraft, boolean hidden) {
        //? if <26.2 {
        minecraft.options.hideGui = hidden;
        //?}
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.waitTicks(5);
        Scene.log("screenshot " + context.takeScreenshot("sodium-" + name));
    }

    /** Living Horizon's pages, as Sodium holds them. */
    private static List<?> pages() {
        for (Object mod : (List<?>) call(config(), "getModOptions", new Class<?>[0])) {
            if ("livinghorizon".equals(call(mod, "configId", new Class<?>[0]))) return (List<?>) call(mod, "pages", new Class<?>[0]);
        }
        return List.of();
    }

    /** Sodium's video settings, opened on one page. */
    private static Screen open(Object page) {
        try {
            Class<?> screen = Class.forName(SODIUM + "gui.VideoSettingsScreen");
            Class<?> optionPage = Class.forName(SODIUM + "config.structure.OptionPage");
            return (Screen) screen.getMethod("createScreen", Screen.class, optionPage).invoke(null, null, page);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not open Sodium's screen", e);
        }
    }

    private static Object config() {
        try {
            return Class.forName(SODIUM + "config.ConfigManager").getField("CONFIG").get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Sodium has no config", e);
        }
    }

    private static Object call(Object target, String method, Class<?>[] types, Object... args) {
        try {
            return target.getClass().getMethod(method, types).invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Sodium: " + method, e);
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("livinghorizon", path);
    }
}
