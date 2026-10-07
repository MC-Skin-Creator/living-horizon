package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.ambient.Ambience;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Which kinds of birds there are: one switch each. */
public final class BirdTypesScreen extends SideOptionsScreen {
    private static final String KEY = "livinghorizon.options.birdTypes.";

    public BirdTypesScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        List<OptionInstance<?>> switches = new ArrayList<>();
        FarConfig config = FarConfig.get();
        for (Ambience.Type type : Ambience.Type.values()) {
            switches.add(OptionInstance.createBoolean(KEY + type.id,
                    OptionInstance.cachedConstantTooltip(Component.translatable(KEY + type.id + ".tooltip")),
                    !config.hiddenBirds.contains(type.id), on -> {
                        config.hiddenBirds.remove(type.id);
                        if (!on) config.hiddenBirds.add(type.id);
                    }));
        }
        list.addSmall(switches.toArray(OptionInstance[]::new));
    }

    @Override
    public void removed() {
        FarConfig.save();
    }
}
