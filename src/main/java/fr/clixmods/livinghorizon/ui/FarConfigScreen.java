package fr.clixmods.livinghorizon.ui;

import com.mojang.serialization.Codec;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.QualityPreset;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobScan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
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

    /** Beside the title: only the Discord logo and the changelog's icon, drawn over their buttons, which says what they are. */
    private static final int LOGO_BUTTON = 20;
    private static final int LOGO_GAP = 4;

    private final @Nullable Screen parent;
    private @Nullable Button discord;
    private @Nullable Button changelog;

    public FarConfigScreen(@Nullable Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
        this.parent = parent;
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        FarConfig c = FarConfig.get();

        header(Component.translatable(KEY + "general"));
        QualityPreset preset = QualityPreset.of(c);
        list.addSmall(
                Button.builder(Component.translatable(KEY + "preset",
                                Component.translatable(KEY + "preset." + (preset == null ? "custom" : preset.id()))), b -> {
                            QualityPreset.next(FarConfig.get()).apply(FarConfig.get());
                            // A fresh screen: rebuildWidgets() would stack a second layout on the first.
                            minecraft.setScreen(new FarConfigScreen(parent));
                        })
                        .tooltip(Tooltip.create(Component.translatable(KEY + "preset.tooltip"))).build(),
                bool("easterEggs", c.easterEggs, v -> c.easterEggs = v).createButton(minecraft.options, 0, 0, 150));
        list.addSmall(
                Button.builder(Component.translatable(KEY + "debug"), b -> minecraft.setScreen(new DebugScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "debug.tooltip"))).build(),
                null);

        // Distances take a whole row: their names are long, as the game's own are.
        header(Component.translatable(KEY + "impostorsSection"));
        list.addSmall(
                bool("impostors", c.impostors, v -> c.impostors = v),
                bool("impostorPlayers", c.impostorPlayers, v -> c.impostorPlayers = v));
        list.addSmall(bool("mobLods", c.mobLods, v -> c.mobLods = v), null);
        list.addBig(blocks("impostorDistance", 32, 2000, c.impostorDistance, v -> c.impostorDistance = v));
        list.addBig(unit("impostorPixels", "pixels", 0, FarConfig.MAX_IMPOSTOR_PIXELS, c.impostorPixels,
                v -> c.impostorPixels = v));
        list.addBig(unit("impostorMobMemory", "megabytes", FarConfig.MIN_MOB_MEMORY, FarConfig.MAX_MOB_MEMORY,
                c.impostorMobMemory, v -> c.impostorMobMemory = v));
        list.addBig(unit("impostorPlayerMemory", "megabytes", FarConfig.MIN_PLAYER_MEMORY, FarConfig.MAX_PLAYER_MEMORY,
                c.impostorPlayerMemory, v -> c.impostorPlayerMemory = v));
        list.addSmall(
                Button.builder(Component.translatable(KEY + "impostorPreview"), b -> minecraft.setScreen(new ImpostorScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "impostorPreview.tooltip"))).build(),
                null);

        header(Component.translatable(KEY + "players"));
        list.addSmall(
                bool("enabled", c.enabled, v -> c.enabled = v),
                bool("showVehicles", c.showVehicles, v -> c.showVehicles = v));
        list.addBig(blocks("playerMaxDistance", 0, 4000, c.playerMaxDistance, v -> c.playerMaxDistance = v));
        list.addSmall(
                bool("offlinePlayers", c.offlinePlayers, v -> c.offlinePlayers = v),
                integer("maxOfflinePlayers", 0, FarConfig.UNLIMITED_OFFLINE, c.maxOfflinePlayers,
                        v -> c.maxOfflinePlayers = v, true),
                bool("offlineLife", c.offlineLife, v -> c.offlineLife = v),
                blocks("offlineRoam", 8, 128, c.offlineRoam, v -> c.offlineRoam = v));
        list.addSmall(
                bool("offlineNames", c.offlineNames, v -> c.offlineNames = v).createButton(minecraft.options, 0, 0, 150),
                Button.builder(Component.translatable(KEY + "forgetSleepers"), b -> confirm("forgetSleepers",
                                () -> FarPlayerTracker.get().resting().forgetAll()))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "forgetSleepers.tooltip"))).build());

        header(Component.translatable(KEY + "mobs"));
        list.addSmall(
                bool("distantMobs", c.distantMobs, v -> c.distantMobs = v),
                integer("maxDistantMobs", 0, FarConfig.UNLIMITED_MOBS, c.maxDistantMobs, v -> c.maxDistantMobs = v, true));
        list.addBig(blocks("mobMaxDistance", 0, 4000, c.mobMaxDistance, v -> c.mobMaxDistance = v));
        list.addSmall(
                bool("rememberNamedMobs", c.rememberNamedMobs, v -> c.rememberNamedMobs = v),
                unit("animationMinPixels", "pixels", 0, 40, c.animationMinPixels, v -> c.animationMinPixels = v));
        list.addBig(blocks("animationMaxDistance", 0, 4000, c.animationMaxDistance, v -> c.animationMaxDistance = v));
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
                unit("birdDensity", "percent", 0, 500, c.birdDensity, v -> c.birdDensity = v));
        list.addBig(blocks("birdMaxDistance", 100, 3000, c.birdMaxDistance, v -> c.birdMaxDistance = v));
        list.addSmall(
                blocks("birdMinHeight", 0, 200, c.birdMinHeight, v -> c.birdMinHeight = v),
                unit("birdSize", "multiplier", 1, 6, c.birdSize, v -> c.birdSize = v));
        list.addSmall(
                choice("birdStyle", List.of("3d", "2d"), c.birdStyle, v -> c.birdStyle = v).createButton(minecraft.options, 0, 0, 150),
                Button.builder(Component.translatable(KEY + "chooseBirds"), b -> minecraft.setScreen(new BirdTypesScreen(this)))
                        .tooltip(Tooltip.create(Component.translatable(KEY + "chooseBirds.tooltip"))).build());
    }

    @Override
    protected void addTitle() {
        super.addTitle();
        discord = addRenderableWidget(Button.builder(Component.empty(), b -> DiscordLink.open(this))
                .size(LOGO_BUTTON, LOGO_BUTTON)
                .tooltip(Tooltip.create(Component.translatable(KEY + "discord.tooltip"))).build());
        changelog = addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.setScreen(new ChangelogScreen(this)))
                .size(LOGO_BUTTON, LOGO_BUTTON)
                .tooltip(Tooltip.create(Component.translatable(KEY + "changelog.tooltip"))).build());
    }

    /** The logos go where the title ends, in the middle of the header: Discord, then the changelog. */
    @Override
    protected void repositionElements() {
        super.repositionElements();
        if (discord == null || changelog == null) return;
        discord.setX(columnWidth() / 2 + font.width(title) / 2 + 6);
        discord.setY((layout.getHeaderHeight() - LOGO_BUTTON) / 2);
        changelog.setX(discord.getX() + LOGO_BUTTON + LOGO_GAP);
        changelog.setY(discord.getY());
    }

    @Override
    protected void drawOver(GuiGraphics graphics) {
        if (discord == null || changelog == null) return;
        Icons.discord(graphics, discord.getX() + (LOGO_BUTTON - Icons.SIZE) / 2, discord.getY() + (LOGO_BUTTON - Icons.SIZE) / 2);
        Icons.changelog(graphics, changelog.getX() + (LOGO_BUTTON - Icons.SIZE) / 2, changelog.getY() + (LOGO_BUTTON - Icons.SIZE) / 2);
    }

    /** Restore defaults sits beside Done: it is about every setting of the screen, not the birds. */
    @Override
    protected void addFooter() {
        //? if >=1.20.2 {
        LinearLayout row = LinearLayout.horizontal().spacing(8);
        //?} else {
        /*// Before 1.20.2 the row spreads its buttons over its own width: two of 150 and a gap of 8.
        LinearLayout row = new LinearLayout(2 * 150 + 8, 20, LinearLayout.Orientation.HORIZONTAL);
        *///?}
        row.addChild(Button.builder(Component.translatable(KEY + "restoreDefaults"), b -> restoreDefaults())
                .tooltip(Tooltip.create(Component.translatable(KEY + "restoreDefaults.tooltip"))).width(150).build());
        row.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(150).build());
        layout.addToFooter(row);
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
            if (yes) FarConfig.reset(MobList.all());
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

    /** A distance in blocks, said so: "512 Blocks"; zero reads as the {@code .zero} text. */
    static OptionInstance<Integer> blocks(String name, int min, int max, int value, Consumer<Integer> set) {
        return unit(name, "blocks", min, max, value, set);
    }

    /** A number in a unit, the way the game writes it ("30s", "6px", "2x", "100%"); zero reads as the {@code .zero} text. */
    static OptionInstance<Integer> unit(String name, String unit, int min, int max, int value, Consumer<Integer> set) {
        return new OptionInstance<>(KEY + name, tooltip(name),
                (caption, v) -> v == 0 && min == 0
                        ? Options.genericValueLabel(caption, Component.translatable(KEY + name + ".zero"))
                        : Options.genericValueLabel(caption, Component.translatable(KEY + unit, v)),
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
