package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.track.MobKinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Which mobs are shown far away: every mob of the game, one switch each, with a search
 * field. The ones the data pack publishes are marked; the others are only shown when this
 * client met them itself.
 */
public final class MobTypesScreen extends OptionsSubScreen {
    private static final String KEY = "livinghorizon.options.mobTypes.";

    /** Every mob type, sorted by its name in the current language. */
    private final List<EntityType<?>> types;
    private String filter = "";

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
        for (EntityType<?> type : types) {
            String id = EntityType.getKey(type).toString();
            Component name = type.getDescription();
            if (!needle.isEmpty() && !name.getString().toLowerCase(Locale.ROOT).contains(needle)
                    && !id.contains(needle)) {
                continue;
            }
            boolean shared = MobKinds.TYPES.contains(id);
            Component label = shared ? Component.translatable(KEY + "shared", name) : name;
            switches.add(CycleButton.builder(Mode::label, Mode.of(id))
                    .withValues(List.of(Mode.values()))
                    .withTooltip(OptionInstance.cachedConstantTooltip(Component.translatable(
                            KEY + (shared ? "shared.tooltip" : "local.tooltip"), id)))
                    .create(0, 0, 150, 20, label, (button, mode) -> mode.apply(id)));
        }
        list.addSmall(switches);
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
        LinearLayout row = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        row.addChild(Button.builder(Component.translatable(KEY + "all"), b -> setAll(true)).width(74).build());
        row.addChild(Button.builder(Component.translatable(KEY + "none"), b -> setAll(false)).width(74).build());
        row.addChild(Button.builder(Component.translatable(KEY + "defaults"), b -> {
            FarConfig.get().mobTypes.clear();
            FarConfig.get().mobTypes.addAll(MobKinds.TYPES);
            FarConfig.get().stillMobTypes.clear();
            FarConfig.get().stillMobTypes.add("minecraft:happy_ghast");
            rebuildWidgets();
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
        rebuildWidgets();
    }

    @Override
    public void removed() {
        FarConfig.save();
    }
}
