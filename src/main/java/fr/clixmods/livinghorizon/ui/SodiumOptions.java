//? if sodium {
package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.QualityPreset;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobScan;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.ControlValueFormatter;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The settings as pages of Sodium's video settings screen (Sodium 0.8 and later), next to
 * Sodium's own: the same settings, names and tooltips as {@link FarConfigScreen}, and its
 * sub-screens behind buttons. Found by Sodium through the {@code sodium:config_api_user}
 * entry point (Fabric, Quilt) or mod property (NeoForge); never loaded without it.
 *
 * <p>Sodium holds changes until "Apply", then saves each changed option through its binding
 * and calls {@link #afterSave} once. A preset sets other settings: it is applied there, after
 * the sliders, and Sodium's copy of every value is read again so that the page shows it.
 */
public final class SodiumOptions implements ConfigEntryPoint {
    private static final String KEY = "livinghorizon.options.";

    /** The preset chosen on the page, applied once Sodium has saved everything else. */
    private @Nullable Preset chosenPreset;

    private final StorageEventHandler storage = this::afterSave;

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        FarConfig defaults = new FarConfig();
        builder.registerOwnModOptions()
                .setColorTheme(builder.createColorTheme().setBaseThemeRGB(0xF28C28))
                .setNonTintedIcon(id("icon.png"))
                .addPage(general(builder, defaults))
                .addPage(impostors(builder, defaults))
                .addPage(players(builder, defaults))
                .addPage(mobs(builder, defaults))
                .addPage(birds(builder, defaults));
    }

    private OptionPageBuilder general(ConfigBuilder b, FarConfig d) {
        return b.createOptionPage().setName(Component.translatable(KEY + "general"))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(b.createEnumOption(id("preset"), Preset.class)
                                .setName(Component.translatable(KEY + "preset.name"))
                                .setTooltip(tooltip("preset"))
                                .setElementNameProvider(p -> Component.translatable(KEY + "preset." + p.id()))
                                .setDefaultValue(Preset.NORMAL)
                                .setStorageHandler(storage)
                                .setBinding(p -> chosenPreset = p, () -> Preset.of(FarConfig.get())))
                        .addOption(bool(b, "easterEggs", d.easterEggs, v -> FarConfig.get().easterEggs = v,
                                () -> FarConfig.get().easterEggs)))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(opens(b, "debug", DebugScreen::new))
                        // Only Discord here: the store pages and the website stay on the mod's own screen.
                        .addOption(button(b, "discord", parent -> LinkScreen.open(parent, ProjectLink.DISCORD)))
                        .addOption(opens(b, "changelog", ChangelogScreen::new))
                        .addOption(button(b, "restoreDefaults", parent -> confirm(parent, "restoreDefaults", () -> {
                            FarConfig.reset(MobList.all());
                            reload();
                        }))));
    }

    private OptionPageBuilder impostors(ConfigBuilder b, FarConfig d) {
        return b.createOptionPage().setName(Component.translatable(KEY + "impostorsSection"))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(bool(b, "impostors", d.impostors, v -> FarConfig.get().impostors = v,
                                () -> FarConfig.get().impostors))
                        .addOption(bool(b, "impostorEntities", d.impostorEntities, v -> FarConfig.get().impostorEntities = v,
                                () -> FarConfig.get().impostorEntities))
                        .addOption(bool(b, "impostorPlayers", d.impostorPlayers, v -> FarConfig.get().impostorPlayers = v,
                                () -> FarConfig.get().impostorPlayers))
                        .addOption(blocks(b, "impostorDistance", 32, 2000, d.impostorDistance,
                                v -> FarConfig.get().impostorDistance = v, () -> FarConfig.get().impostorDistance)
                                .setEnabledProvider(any(PICTURED), ids(PICTURED)))
                        .addOption(integer(b, "impostorPixels", 0, FarConfig.MAX_IMPOSTOR_PIXELS, d.impostorPixels,
                                v -> FarConfig.get().impostorPixels = v, () -> FarConfig.get().impostorPixels,
                                unit("impostorPixels", "pixels", 0))
                                .setEnabledProvider(any(PICTURED), ids(PICTURED)))
                        .addOption(integer(b, "impostorMobMemory", FarConfig.MIN_MOB_MEMORY, FarConfig.MAX_MOB_MEMORY,
                                d.impostorMobMemory, v -> FarConfig.get().impostorMobMemory = v,
                                () -> FarConfig.get().impostorMobMemory,
                                unit("impostorMobMemory", "megabytes", FarConfig.MIN_MOB_MEMORY))
                                .setEnabledProvider(any(NOT_PLAYERS), ids(NOT_PLAYERS)))
                        .addOption(integer(b, "impostorPlayerMemory", FarConfig.MIN_PLAYER_MEMORY, FarConfig.MAX_PLAYER_MEMORY,
                                d.impostorPlayerMemory, v -> FarConfig.get().impostorPlayerMemory = v,
                                () -> FarConfig.get().impostorPlayerMemory,
                                unit("impostorPlayerMemory", "megabytes", FarConfig.MIN_PLAYER_MEMORY))
                                .setEnabledProvider(on("impostorPlayers"), id("impostorPlayers"))))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(opens(b, "impostorPreview", ImpostorScreen::new)));
    }

    private OptionPageBuilder players(ConfigBuilder b, FarConfig d) {
        return b.createOptionPage().setName(Component.translatable(KEY + "players"))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(bool(b, "enabled", d.enabled, v -> FarConfig.get().enabled = v,
                                () -> FarConfig.get().enabled))
                        .addOption(bool(b, "showVehicles", d.showVehicles, v -> FarConfig.get().showVehicles = v,
                                () -> FarConfig.get().showVehicles))
                        .addOption(blocks(b, "playerMaxDistance", 0, 4000, d.playerMaxDistance,
                                v -> FarConfig.get().playerMaxDistance = v, () -> FarConfig.get().playerMaxDistance)))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(bool(b, "offlinePlayers", d.offlinePlayers, v -> FarConfig.get().offlinePlayers = v,
                                () -> FarConfig.get().offlinePlayers))
                        .addOption(integer(b, "maxOfflinePlayers", 0, FarConfig.UNLIMITED_OFFLINE, d.maxOfflinePlayers,
                                v -> FarConfig.get().maxOfflinePlayers = v, () -> FarConfig.get().maxOfflinePlayers,
                                plain("maxOfflinePlayers", 0, true))
                                .setEnabledProvider(on("offlinePlayers"), id("offlinePlayers")))
                        .addOption(bool(b, "offlineLife", d.offlineLife, v -> FarConfig.get().offlineLife = v,
                                () -> FarConfig.get().offlineLife)
                                .setEnabledProvider(on("offlinePlayers"), id("offlinePlayers")))
                        .addOption(blocks(b, "offlineRoam", 8, 128, d.offlineRoam, v -> FarConfig.get().offlineRoam = v,
                                () -> FarConfig.get().offlineRoam)
                                .setEnabledProvider(s -> s.readBooleanOption(id("offlinePlayers"))
                                        && s.readBooleanOption(id("offlineLife")), id("offlinePlayers"), id("offlineLife")))
                        .addOption(bool(b, "offlineNames", d.offlineNames, v -> FarConfig.get().offlineNames = v,
                                () -> FarConfig.get().offlineNames)
                                .setEnabledProvider(on("offlinePlayers"), id("offlinePlayers")))
                        .addOption(button(b, "forgetSleepers", parent -> confirm(parent, "forgetSleepers",
                                () -> FarPlayerTracker.get().resting().forgetAll()))));
    }

    private OptionPageBuilder mobs(ConfigBuilder b, FarConfig d) {
        // In three parts, as on the mod's own screen: what goes for both, the mobs, the other entities.
        return b.createOptionPage().setName(Component.translatable(KEY + "mobs"))
                .addOptionGroup(scan(b, d, b.createOptionGroup().setName(Component.translatable(KEY + "mobs.general"))
                        .addOption(opens(b, "chooseMobs", MobTypesScreen::new))
                        .addOption(button(b, "forgetMobs", parent -> confirm(parent, "forgetMobs", () -> {
                            FarPlayerTracker.get().mobs().forgetAll();
                            FarPlayerTracker.get().structures().forgetAll();
                        })))))
                .addOptionGroup(b.createOptionGroup().setName(Component.translatable(KEY + "mobs.creatures"))
                        .addOption(bool(b, "distantMobs", d.distantMobs, v -> FarConfig.get().distantMobs = v,
                                () -> FarConfig.get().distantMobs))
                        .addOption(integer(b, "maxDistantMobs", 0, FarConfig.UNLIMITED_MOBS, d.maxDistantMobs,
                                v -> FarConfig.get().maxDistantMobs = v, () -> FarConfig.get().maxDistantMobs,
                                plain("maxDistantMobs", 0, true)))
                        .addOption(blocks(b, "mobMaxDistance", 0, 4000, d.mobMaxDistance,
                                v -> FarConfig.get().mobMaxDistance = v, () -> FarConfig.get().mobMaxDistance))
                        .addOption(bool(b, "rememberNamedMobs", d.rememberNamedMobs,
                                v -> FarConfig.get().rememberNamedMobs = v, () -> FarConfig.get().rememberNamedMobs))
                        .addOption(integer(b, "animationMinPixels", 0, 40, d.animationMinPixels,
                                v -> FarConfig.get().animationMinPixels = v, () -> FarConfig.get().animationMinPixels,
                                unit("animationMinPixels", "pixels", 0)))
                        .addOption(blocks(b, "animationMaxDistance", 0, 4000, d.animationMaxDistance,
                                v -> FarConfig.get().animationMaxDistance = v, () -> FarConfig.get().animationMaxDistance)))
                .addOptionGroup(b.createOptionGroup().setName(Component.translatable(KEY + "mobs.entities"))
                        .addOption(bool(b, "distantEntities", d.distantEntities, v -> FarConfig.get().distantEntities = v,
                                () -> FarConfig.get().distantEntities))
                        .addOption(integer(b, "maxDistantEntities", 0, FarConfig.UNLIMITED_MOBS, d.maxDistantEntities,
                                v -> FarConfig.get().maxDistantEntities = v, () -> FarConfig.get().maxDistantEntities,
                                plain("maxDistantEntities", 0, true)))
                        .addOption(blocks(b, "entityMaxDistance", 0, 4000, d.entityMaxDistance,
                                v -> FarConfig.get().entityMaxDistance = v, () -> FarConfig.get().entityMaxDistance))
                        .addOption(bool(b, "distantStructures", d.distantStructures,
                                v -> FarConfig.get().distantStructures = v, () -> FarConfig.get().distantStructures)));
    }

    /** "Scan the chunks around": only in a single player world, read again whenever the screen is built. */
    /** The scan's two settings, added to {@code group}. */
    private OptionGroupBuilder scan(ConfigBuilder b, FarConfig d, OptionGroupBuilder group) {
        return group
                .addOption(b.createIntegerOption(id("scanRadius"))
                        .setName(Component.translatable(KEY + "scanRadius"))
                        .setTooltip(tooltip("scanRadius"))
                        .setRange(MobScan.MIN_RADIUS, MobScan.MAX_RADIUS, 1)
                        .setValueFormatter(v -> Component.translatable(KEY + "scanRadius.value", v))
                        .setDefaultValue(d.scanRadius)
                        .setStorageHandler(storage)
                        .setBinding(v -> FarConfig.get().scanRadius = v,
                                () -> Math.clamp(FarConfig.get().scanRadius, MobScan.MIN_RADIUS, MobScan.MAX_RADIUS)))
                .addOption(b.createExternalButtonOption(id("scanMobs"))
                        .setName(Component.translatable(KEY + "scanMobs"))
                        .setTooltip(Component.translatable(KEY + "scanMobs.tooltip"))
                        .setEnabledProvider(s -> MobScan.available(Minecraft.getInstance()), ConfigState.UPDATE_ON_REBUILD)
                        .setScreenConsumer(parent -> {
                            Minecraft minecraft = Minecraft.getInstance();
                            if (!MobScan.available(minecraft)) return;
                            FarConfig.save();
                            minecraft.setScreen(null);
                            MobScan.start(minecraft, FarConfig.get().scanRadius);
                        }));
    }

    private OptionPageBuilder birds(ConfigBuilder b, FarConfig d) {
        Function<ConfigState, Boolean> birds = on("skyBirds");
        return b.createOptionPage().setName(Component.translatable(KEY + "birds"))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(bool(b, "skyBirds", d.skyBirds, v -> FarConfig.get().skyBirds = v,
                                () -> FarConfig.get().skyBirds))
                        .addOption(integer(b, "birdDensity", 0, 500, d.birdDensity,
                                v -> FarConfig.get().birdDensity = v, () -> FarConfig.get().birdDensity,
                                unit("birdDensity", "percent", 0))
                                .setEnabledProvider(birds, id("skyBirds")))
                        .addOption(blocks(b, "birdMaxDistance", 100, 3000, d.birdMaxDistance,
                                v -> FarConfig.get().birdMaxDistance = v, () -> FarConfig.get().birdMaxDistance)
                                .setEnabledProvider(birds, id("skyBirds")))
                        .addOption(blocks(b, "birdMinHeight", 0, 200, d.birdMinHeight,
                                v -> FarConfig.get().birdMinHeight = v, () -> FarConfig.get().birdMinHeight)
                                .setEnabledProvider(birds, id("skyBirds"))))
                .addOptionGroup(b.createOptionGroup()
                        .addOption(integer(b, "birdSize", 1, 6, d.birdSize,
                                v -> FarConfig.get().birdSize = v, () -> FarConfig.get().birdSize,
                                unit("birdSize", "multiplier", 1))
                                .setEnabledProvider(birds, id("skyBirds")))
                        .addOption(b.createEnumOption(id("birdStyle"), BirdStyle.class)
                                .setName(Component.translatable(KEY + "birdStyle"))
                                .setTooltip(tooltip("birdStyle"))
                                .setElementNameProvider(s -> Component.translatable(KEY + "birdStyle." + s.id))
                                .setDefaultValue(BirdStyle.of(d.birdStyle))
                                .setStorageHandler(storage)
                                .setBinding(s -> FarConfig.get().birdStyle = s.id, () -> BirdStyle.of(FarConfig.get().birdStyle))
                                .setEnabledProvider(birds, id("skyBirds")))
                        .addOption(opens(b, "chooseBirds", BirdTypesScreen::new)));
    }

    /** Once Sodium has saved the changed options: the preset on top of them, then the file. */
    private void afterSave() {
        Preset preset = chosenPreset;
        chosenPreset = null;
        if (preset != null && preset.preset != null) {
            preset.preset.apply(FarConfig.get());
            reload();
        }
        FarConfig.save();
    }

    /**
     * Sodium's copy of every value read again from the settings, so that the page shows what a
     * preset or "Restore defaults" just set. Sodium's own class, not its API: left as it is when
     * a version of Sodium no longer has it, until the screen is opened again.
     */
    private static void reload() {
        try {
            Object config = Class.forName("net.caffeinemc.mods.sodium.client.config.ConfigManager")
                    .getField("CONFIG").get(null);
            if (config != null) config.getClass().getMethod("resetAllOptionsFromBindings").invoke(config);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LivingHorizonClient.LOGGER.debug("Could not refresh Sodium's options", e);
        }
    }

    private OptionBuilder bool(ConfigBuilder b, String name, boolean fallback, Consumer<Boolean> set,
                               java.util.function.Supplier<Boolean> get) {
        return b.createBooleanOption(id(name))
                .setName(Component.translatable(KEY + name))
                .setTooltip(tooltip(name))
                .setDefaultValue(fallback)
                .setStorageHandler(storage)
                .setBinding(set, get);
    }

    private OptionBuilder integer(ConfigBuilder b, String name, int min, int max, int fallback, IntConsumer set,
                                  IntSupplier get, ControlValueFormatter format) {
        return b.createIntegerOption(id(name))
                .setName(Component.translatable(KEY + name))
                .setTooltip(tooltip(name))
                .setRange(min, max, 1)
                .setValueFormatter(format)
                .setDefaultValue(Math.clamp(fallback, min, max))
                .setStorageHandler(storage)
                // A value out of range in the file would read as the default: shown at the nearest end instead.
                .setBinding(set::accept, () -> Math.clamp(get.getAsInt(), min, max));
    }

    /** A distance in blocks, "512 Blocks", the zero of a range from 0 read as its {@code .zero} text. */
    private OptionBuilder blocks(ConfigBuilder b, String name, int min, int max, int fallback, IntConsumer set,
                                 IntSupplier get) {
        return integer(b, name, min, max, fallback, set, get, unit(name, "blocks", min));
    }

    /** A number in a unit, as {@link FarConfigScreen} writes it ("30s", "6px", "100%"); the zero of a range from 0 as its text. */
    private static ControlValueFormatter unit(String name, String unit, int min) {
        return v -> v == 0 && min == 0 ? Component.translatable(KEY + name + ".zero") : Component.translatable(KEY + unit, v);
    }

    /** A bare number; the zero of a range from 0 and, with {@code maxIsUnlimited}, the top read as text. */
    private static ControlValueFormatter plain(String name, int min, boolean maxIsUnlimited) {
        return v -> v == 0 && min == 0 ? Component.translatable(KEY + name + ".zero")
                : maxIsUnlimited && v >= FarConfig.UNLIMITED_MOBS ? Component.translatable(KEY + name + ".max")
                : Component.literal(String.valueOf(v));
    }

    /** A button opening one of the mod's screens, which comes back to Sodium's when closed. */
    private static OptionBuilder button(ConfigBuilder b, String name, Consumer<Screen> open) {
        return b.createExternalButtonOption(id(name))
                .setName(Component.translatable(KEY + name))
                .setTooltip(tooltip(name))
                .setScreenConsumer(open);
    }

    private static OptionBuilder opens(ConfigBuilder b, String name, Function<Screen, Screen> screen) {
        return button(b, name, parent -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(screen.apply(parent));
        });
    }

    /** Asks before an action that cannot be undone, then back to Sodium's screen. */
    private static void confirm(Screen parent, String name, Runnable action) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new Confirm(yes -> {
            if (yes) action.run();
            minecraft.setScreen(parent);
        }, Component.translatable(KEY + name), Component.translatable(KEY + name + ".confirm")));
    }

    /** The game's confirmation, which over Sodium's screen answers "no" to Escape on its release. */
    private static final class Confirm extends ConfirmScreen {
        Confirm(it.unimi.dsi.fastutil.booleans.BooleanConsumer callback, Component title, Component message) {
            super(callback, title, message);
        }

    /** Escape pressed over Sodium's screen: closed when it is released, see {@link SodiumReturn}. */
    private boolean closeOnRelease;

    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && true) {
            closeOnRelease = true;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        if (closeOnRelease && event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            closeOnRelease = false;
            callback.accept(false);
            return true;
        }
        return super.keyReleased(event);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && true) {
            closeOnRelease = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (closeOnRelease && keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            closeOnRelease = false;
            callback.accept(false);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    *///?}
    }

    private static Function<ConfigState, Boolean> on(String name) {
        return state -> state.readBooleanOption(id(name));
    }

    /** The switches of the flat pictures: what the pictures' distance and size follow, and the mobs' memory. */
    private static final String[] PICTURED = {"impostors", "impostorEntities", "impostorPlayers"};
    private static final String[] NOT_PLAYERS = {"impostors", "impostorEntities"};

    /** On while any of these switches is on. */
    private static Function<ConfigState, Boolean> any(String[] names) {
        return state -> {
            for (String name : names) {
                if (state.readBooleanOption(id(name))) return true;
            }
            return false;
        };
    }

    private static Identifier[] ids(String[] names) {
        Identifier[] ids = new Identifier[names.length];
        for (int i = 0; i < names.length; i++) ids[i] = id(names[i]);
        return ids;
    }

    private static Component tooltip(String name) {
        return Component.translatable(KEY + name + ".tooltip");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, path.toLowerCase(Locale.ROOT));
    }

    /** {@link QualityPreset}, and the settings that match none of them. */
    public enum Preset {
        LOW(QualityPreset.LOW), NORMAL(QualityPreset.NORMAL), HIGH(QualityPreset.HIGH), ULTRA(QualityPreset.ULTRA),
        CUSTOM(null);

        final @Nullable QualityPreset preset;

        Preset(@Nullable QualityPreset preset) {
            this.preset = preset;
        }

        String id() {
            return preset == null ? "custom" : preset.id();
        }

        static Preset of(FarConfig config) {
            QualityPreset current = QualityPreset.of(config);
            for (Preset p : values()) if (p.preset == current) return p;
            return CUSTOM;
        }
    }

    /** {@link FarConfig#birdStyle}, which the file keeps as text. */
    public enum BirdStyle {
        MODELS("3d"), SPRITES("2d");

        final String id;

        BirdStyle(String id) {
            this.id = id;
        }

        static BirdStyle of(String id) {
            return "2d".equals(id) ? SPRITES : MODELS;
        }
    }
}
//?}
