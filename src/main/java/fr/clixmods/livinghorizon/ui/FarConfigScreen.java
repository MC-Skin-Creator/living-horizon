package fr.clixmods.livinghorizon.ui;

import com.mojang.serialization.Codec;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobScan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
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
public final class FarConfigScreen extends SideOptionsScreen {
    private static final String KEY = "livinghorizon.options.";

    private final @Nullable Screen parent;

    public FarConfigScreen(@Nullable Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
        this.parent = parent;
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        FarConfig c = FarConfig.get();

        header(Component.translatable(KEY + "general"));
        list.addSmall(
                integer("minApparentPixels", 0, 32, (int) c.minApparentPixels, v -> c.minApparentPixels = v),
                bool("impostors", c.impostors, v -> c.impostors = v),
                integer("impostorDistance", 32, 2000, c.impostorDistance, v -> c.impostorDistance = v));
        list.addSmall(
                Button.builder(Component.translatable(KEY + "debug"), b -> minecraft.setScreen(new DebugScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "debug.tooltip"))).build(),
                Button.builder(Component.translatable(KEY + "impostorPreview"), b -> minecraft.setScreen(new ImpostorScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "impostorPreview.tooltip"))).build());

        header(Component.translatable(KEY + "players"));
        list.addSmall(
                bool("enabled", c.enabled, v -> c.enabled = v),
                bool("showVehicles", c.showVehicles, v -> c.showVehicles = v),
                choice("offlinePose", List.of("sleep", "sit", "hidden"), c.offlinePose, v -> c.offlinePose = v),
                integer("playerMaxDistance", 0, 4000, c.playerMaxDistance, v -> c.playerMaxDistance = v),
                integer("lostTimeoutSeconds", 5, 300, (int) c.lostTimeoutSeconds, v -> c.lostTimeoutSeconds = v),
                bool("renderTrackedVehiclesFar", c.renderTrackedVehiclesFar, v -> c.renderTrackedVehiclesFar = v));
        list.addSmall(
                Button.builder(Component.translatable(KEY + "forgetSleepers"), b -> confirm("forgetSleepers",
                                () -> FarPlayerTracker.get().resting().forgetAll()))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "forgetSleepers.tooltip"))).build(),
                null);

        header(Component.translatable(KEY + "mobs"));
        list.addSmall(
                bool("distantMobs", c.distantMobs, v -> c.distantMobs = v),
                integer("maxDistantMobs", 0, FarConfig.UNLIMITED_MOBS, c.maxDistantMobs, v -> c.maxDistantMobs = v, true),
                integer("mobMaxDistance", 0, 4000, c.mobMaxDistance, v -> c.mobMaxDistance = v),
                bool("rememberNamedMobs", c.rememberNamedMobs, v -> c.rememberNamedMobs = v));
        list.addSmall(
                Button.builder(Component.translatable(KEY + "chooseMobs"), b -> minecraft.setScreen(new MobTypesScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "chooseMobs.tooltip"))).build(),
                Button.builder(Component.translatable(KEY + "forgetMobs"), b -> confirm("forgetMobs",
                                () -> FarPlayerTracker.get().mobs().forgetAll()))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "forgetMobs.tooltip"))).build());
        list.addSmall(scanButton(), chunks("scanRadius", MobScan.MIN_RADIUS, MobScan.MAX_RADIUS, c.scanRadius,
                v -> c.scanRadius = v).createButton(minecraft.options, 0, 0, 150));

        header(Component.translatable(KEY + "birds"));
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
                Button.builder(Component.translatable(KEY + "restoreDefaults"), b -> restoreDefaults())
                        .tooltip(Tooltip.create(Component.translatable(KEY + "restoreDefaults.tooltip"))).build());
    }

    /**
     * Reads the mobs of the chunks around from the world itself: only in single player,
     * where the world is in this game. Back to the game, where the chat says what it found.
     */
    private Button scanButton() {
        boolean available = MobScan.available(minecraft);
        Button button = Button.builder(Component.translatable(KEY + "scanMobs"), b -> {
                    FarConfig.save();
                    minecraft.setScreen(null);
                    MobScan.start(minecraft, FarConfig.get().scanRadius);
                })
                .tooltip(Tooltip.create(Component.translatable(
                        KEY + (available ? "scanMobs.tooltip" : "scanMobs.unavailable"))))
                .build();
        button.active = available;
        return button;
    }

    @Override
    public void removed() {
        FarConfig.save();
    }

    /** Asks, then puts every setting back to its default and reopens the screen to show them. */
    private void restoreDefaults() {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) FarConfig.reset();
            minecraft.setScreen(yes ? new FarConfigScreen(parent) : this);
        }, Component.translatable(KEY + "restoreDefaults"), Component.translatable(KEY + "restoreDefaults.confirm")));
    }

    /** Asks before forgetting: what is forgotten does not come back. */
    private void confirm(String name, Runnable action) {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) action.run();
            minecraft.setScreen(this);
        }, Component.translatable(KEY + name), Component.translatable(KEY + name + ".confirm")));
    }

    static OptionInstance<Boolean> bool(String name, boolean value, Consumer<Boolean> set) {
        return OptionInstance.createBoolean(KEY + name, tooltip(name), value, set::accept);
    }

    static OptionInstance<Integer> integer(String name, int min, int max, int value, Consumer<Integer> set) {
        return integer(name, min, max, value, set, false);
    }

    /** With {@code maxIsUnlimited}, the top of the slider reads as the {@code .max} text. */
    static OptionInstance<Integer> integer(String name, int min, int max, int value, Consumer<Integer> set,
                                           boolean maxIsUnlimited) {
        return new OptionInstance<>(KEY + name, tooltip(name),
                (caption, v) -> v == 0 && min == 0
                        ? Options.genericValueLabel(caption, Component.translatable(KEY + name + ".zero"))
                        : maxIsUnlimited && v >= max
                        ? Options.genericValueLabel(caption, Component.translatable(KEY + name + ".max"))
                        : Options.genericValueLabel(caption, v),
                new OptionInstance.IntRange(min, max), Math.clamp(value, min, max), set::accept);
    }

    /** A distance in chunks, worded like the game's own render distance ("32 chunks"). */
    static OptionInstance<Integer> chunks(String name, int min, int max, int value, Consumer<Integer> set) {
        return new OptionInstance<>(KEY + name, tooltip(name),
                (caption, v) -> Options.genericValueLabel(caption, Component.translatable(KEY + name + ".value", v)),
                new OptionInstance.IntRange(min, max), Math.clamp(value, min, max), set::accept);
    }

    static OptionInstance<String> choice(String name, List<String> values, String value, Consumer<String> set) {
        return new OptionInstance<>(KEY + name, tooltip(name),
                // The cycle button puts the caption in front by itself.
                (caption, v) -> Component.translatable(KEY + name + "." + v),
                new OptionInstance.Enum<>(values, Codec.STRING), values.contains(value) ? value : values.get(0), set::accept);
    }

    private static <T> OptionInstance.TooltipSupplier<T> tooltip(String name) {
        return OptionInstance.cachedConstantTooltip(Component.translatable(KEY + name + ".tooltip"));
    }
}
