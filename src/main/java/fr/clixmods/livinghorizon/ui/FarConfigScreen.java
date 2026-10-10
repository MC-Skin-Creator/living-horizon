package fr.clixmods.livinghorizon.ui;

import com.mojang.serialization.Codec;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * The settings, in game: the same list of buttons and sliders as the game's own option
 * screens. Opened from Mod Menu's "Configure" button, the settings key, or
 * {@code /livinghorizon config}. Every change applies at once and is written to
 * {@code config/livinghorizon.json} when the screen closes. Which mobs are shown is
 * chosen on {@link MobTypesScreen}.
 */
public final class FarConfigScreen extends OptionsSubScreen {
    private static final String KEY = "livinghorizon.options.";

    public FarConfigScreen(@Nullable Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        FarConfig c = FarConfig.get();

        list.addHeader(Component.translatable(KEY + "players"));
        list.addSmall(
                bool("enabled", c.enabled, v -> c.enabled = v),
                bool("showVehicles", c.showVehicles, v -> c.showVehicles = v),
                choice("offlinePose", List.of("sleep", "sit", "hidden"), c.offlinePose, v -> c.offlinePose = v),
                bool("offlineOnGround", c.offlineOnGround, v -> c.offlineOnGround = v),
                integer("offlineBedRadius", 0, 8, c.offlineBedRadius, v -> c.offlineBedRadius = v),
                integer("lostTimeoutSeconds", 5, 300, (int) c.lostTimeoutSeconds, v -> c.lostTimeoutSeconds = v),
                bool("glowOutline", c.glowOutline, v -> c.glowOutline = v),
                bool("renderTrackedVehiclesFar", c.renderTrackedVehiclesFar, v -> c.renderTrackedVehiclesFar = v));
        list.addSmall(
                Button.builder(Component.translatable(KEY + "forgetSleepers"), b -> confirm("forgetSleepers",
                                () -> FarPlayerTracker.get().resting().forgetAll()))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "forgetSleepers.tooltip"))).build(),
                null);

        list.addHeader(Component.translatable(KEY + "mobs"));
        list.addSmall(
                bool("distantMobs", c.distantMobs, v -> c.distantMobs = v),
                integer("maxDistantMobs", 0, 1000, c.maxDistantMobs, v -> c.maxDistantMobs = v),
                bool("rememberNamedMobs", c.rememberNamedMobs, v -> c.rememberNamedMobs = v));
        list.addSmall(
                Button.builder(Component.translatable(KEY + "chooseMobs"), b -> minecraft.setScreen(new MobTypesScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "chooseMobs.tooltip"))).build(),
                Button.builder(Component.translatable(KEY + "forgetMobs"), b -> confirm("forgetMobs",
                                () -> FarPlayerTracker.get().mobs().forgetAll()))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "forgetMobs.tooltip"))).build());

        list.addHeader(Component.translatable(KEY + "birds"));
        list.addSmall(
                bool("skyBirds", c.skyBirds, v -> c.skyBirds = v),
                integer("birdDensity", 0, 500, c.birdDensity, v -> c.birdDensity = v),
                integer("birdMaxDistance", 100, 3000, c.birdMaxDistance, v -> c.birdMaxDistance = v),
                integer("birdMinHeight", 0, 200, c.birdMinHeight, v -> c.birdMinHeight = v),
                integer("birdSize", 1, 6, c.birdSize, v -> c.birdSize = v),
                bool("ufo", c.ufo, v -> c.ufo = v));
        list.addSmall(
                Button.builder(Component.translatable(KEY + "chooseBirds"), b -> minecraft.setScreen(new BirdTypesScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "chooseBirds.tooltip"))).build(),
                null);

        list.addHeader(Component.translatable(KEY + "rendering"));
        list.addSmall(
                bool("voxyOcclusion", c.voxyOcclusion, v -> c.voxyOcclusion = v),
                bool("extendFarPlane", c.extendFarPlane, v -> c.extendFarPlane = v),
                integer("minApparentPixels", 0, 32, (int) c.minApparentPixels, v -> c.minApparentPixels = v),
                bool("hideOccludedMobs", c.hideOccludedMobs, v -> c.hideOccludedMobs = v));
        list.addSmall(
                Button.builder(Component.translatable(KEY + "debug"), b -> minecraft.setScreen(new DebugScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "debug.tooltip"))).build(),
                null);
    }

    @Override
    public void removed() {
        FarConfig.save();
    }

    /** Asks before forgetting: what is forgotten does not come back. */
    private void confirm(String name, Runnable action) {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) action.run();
            minecraft.setScreen(this);
        }, Component.translatable(KEY + name), Component.translatable(KEY + name + ".confirm")));
    }

    static OptionInstance<Boolean> bool(String name, boolean value, Consumer<Boolean> set) {
        return OptionInstance.createBoolean(KEY + name, tooltip(name), value, set);
    }

    static OptionInstance<Integer> integer(String name, int min, int max, int value, Consumer<Integer> set) {
        return new OptionInstance<>(KEY + name, tooltip(name),
                (caption, v) -> v == 0 && min == 0
                        ? Options.genericValueLabel(caption, Component.translatable(KEY + name + ".zero"))
                        : Options.genericValueLabel(caption, v),
                new OptionInstance.IntRange(min, max), Math.clamp(value, min, max), set);
    }

    static OptionInstance<String> choice(String name, List<String> values, String value, Consumer<String> set) {
        return new OptionInstance<>(KEY + name, tooltip(name),
                (caption, v) -> Options.genericValueLabel(caption, Component.translatable(KEY + name + "." + v)),
                new OptionInstance.Enum<>(values, Codec.STRING), values.contains(value) ? value : values.getFirst(), set);
    }

    private static <T> OptionInstance.TooltipSupplier<T> tooltip(String name) {
        return OptionInstance.cachedConstantTooltip(Component.translatable(KEY + name + ".tooltip"));
    }
}
