package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.track.MobKinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Which mobs are shown far away: every mob of the game, one switch each, with a search
 * field. The ones the data pack publishes are marked; the others are only shown when this
 * client met them itself.
 */
public final class MobTypesScreen extends SideOptionsScreen {
    private static final String KEY = "livinghorizon.options.mobTypes.";

    /** Every mob type, sorted by its name in the current language. */
    private final List<EntityType<?>> types;
    private String filter = "";
    /** The switches on screen, by mob id: updated in place by All, None and Defaults. */
    private final Map<String, CycleButton<Mode>> buttons = new LinkedHashMap<>();
    /** The icon drawn beside each switch: the mob's spawn egg, the boat's item. */
    private final Map<String, ItemStack> icons = new LinkedHashMap<>();

    public MobTypesScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
        types = MobList.all();
        types.sort(Comparator.comparing(type -> type.getDescription().getString().toLowerCase(Locale.ROOT)));
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        EditBox search = new EditBox(font, 0, 0, 310, 20, Component.translatable(KEY + "search"));
        search.setHint(Component.translatable(KEY + "search"));
        search.setValue(filter);
        search.setResponder(text -> {
            if (text.equals(filter)) return;
            filter = text;
            rebuildWidgets();
        });
        list.addSmall(search, null);
        setInitialFocus(search);

        FarConfig config = FarConfig.get();
        String needle = filter.toLowerCase(Locale.ROOT).trim();
        List<AbstractWidget> switches = new ArrayList<>();
        buttons.clear();
        icons.clear();
        for (EntityType<?> type : types) {
            String id = EntityType.getKey(type).toString();
            Component name = type.getDescription();
            if (!needle.isEmpty() && !name.getString().toLowerCase(Locale.ROOT).contains(needle)
                    && !id.contains(needle)) {
                continue;
            }
            boolean shared = MobKinds.TYPES.contains(id);
            Component label = shared ? Component.translatable(KEY + "shared", name) : name;
            //? if >=1.21.11 {
            CycleButton<Mode> button = CycleButton.builder(Mode::label, Mode.of(id))
            //?} else {
            /*CycleButton<Mode> button = CycleButton.builder(Mode::label).withInitialValue(Mode.of(id))
            *///?}
                    .withValues(List.of(Mode.values()))
                    .withTooltip(OptionInstance.cachedConstantTooltip(Component.translatable(
                            KEY + (shared ? "shared.tooltip" : "local.tooltip"), id)))
                    .create(0, 0, ICON_ROOM, 20, label, (b, mode) -> mode.apply(id));
            buttons.put(id, button);
            ItemStack icon = icon(id);
            if (!icon.isEmpty()) icons.put(id, icon);
            switches.add(button);
        }
        list.addSmall(switches);
    }

    /** A switch leaves 20 pixels of its 150-wide cell for the icon. */
    private static final int ICON_ROOM = 130;
    private static final int PARKED = -10000;

    private static Map<String, Item> items;

    /** The spawn egg of a mob, or the item of a boat: what the game itself shows for it. */
    private static ItemStack icon(String id) {
        if (items == null) {
            items = new java.util.HashMap<>();
            for (Item item : BuiltInRegistries.ITEM) {
                items.put(BuiltInRegistries.ITEM.getKey(item).toString(), item);
            }
        }
        Item item = items.getOrDefault(id + "_spawn_egg", items.get(id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * The list only positions the rows it draws: a row scrolled out of view keeps the place
     * it had when last seen, so drawing icons from those places stacks them up. Every switch
     * is parked far off first; the ones the list draws this frame come back by themselves.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        buttons.values().forEach(button -> button.setY(PARKED));
        super.render(graphics, mouseX, mouseY, partialTick);
        if (list == null) return;
        graphics.enableScissor(list.getX(), list.getY(), list.getRight(), list.getBottom());
        for (Map.Entry<String, ItemStack> entry : icons.entrySet()) {
            CycleButton<Mode> button = buttons.get(entry.getKey());
            int y = button.getY() + 2;
            if (y + 16 <= list.getY() || y >= list.getBottom()) continue;
            graphics.renderItem(entry.getValue(), button.getX() + button.getWidth() + 2, y);
        }
        graphics.disableScissor();
    }

    /** How one kind of mob is shown far away. */
    private enum Mode {
        HIDDEN, ANIMATED, STILL;

        Component label() {
            return Component.translatable(KEY + "mode." + name().toLowerCase(Locale.ROOT));
        }

        static Mode of(String id) {
            FarConfig config = FarConfig.get();
            if (!config.mobTypes.contains(id)) return HIDDEN;
            return config.stillMobTypes.contains(id) ? STILL : ANIMATED;
        }

        void apply(String id) {
            FarConfig config = FarConfig.get();
            config.mobTypes.remove(id);
            config.stillMobTypes.remove(id);
            if (this != HIDDEN) config.mobTypes.add(id);
            if (this == STILL) config.stillMobTypes.add(id);
        }
    }

    @Override
    protected void addFooter() {
        //? if >=1.20.2 {
        LinearLayout row = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        //?} else {
        /*// No spacing before 1.20.2: each button keeps half of it on either side.
        LinearLayout row = layout.addToFooter(new LinearLayout(0, 0, LinearLayout.Orientation.HORIZONTAL));
        row.defaultChildLayoutSetting().paddingHorizontal(4);
        *///?}
        row.addChild(Button.builder(Component.translatable(KEY + "all"), b -> setAll(true)).width(74).build());
        row.addChild(Button.builder(Component.translatable(KEY + "none"), b -> setAll(false)).width(74).build());
        row.addChild(Button.builder(Component.translatable(KEY + "defaults"), b -> {
            FarConfig.get().mobTypes.clear();
            FarConfig.get().mobTypes.addAll(MobKinds.TYPES);
            FarConfig.get().stillMobTypes.clear();
            FarConfig.get().stillMobTypes.add("minecraft:happy_ghast");
            refreshButtons();
        }).width(74).build());
        row.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(74).build());
    }

    /** Every mob the search shows: hidden, or shown (still ones stay still). */
    private void setAll(boolean on) {
        String needle = filter.toLowerCase(Locale.ROOT).trim();
        for (EntityType<?> type : types) {
            String id = EntityType.getKey(type).toString();
            if (!needle.isEmpty() && !type.getDescription().getString().toLowerCase(Locale.ROOT).contains(needle)
                    && !id.contains(needle)) {
                continue;
            }
            Mode current = Mode.of(id);
            if (!on) Mode.HIDDEN.apply(id);
            else if (current == Mode.HIDDEN) Mode.ANIMATED.apply(id);
        }
        refreshButtons();
    }

    /** Shows the config on the switches already there, without building the screen again. */
    private void refreshButtons() {
        buttons.forEach((id, button) -> button.setValue(Mode.of(id)));
    }

    @Override
    public void removed() {
        FarConfig.save();
    }
}
