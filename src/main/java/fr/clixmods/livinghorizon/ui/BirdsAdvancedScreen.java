package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.DistancePreview.Shape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import static fr.clixmods.livinghorizon.ui.FarConfigScreen.blocks;
import static fr.clixmods.livinghorizon.ui.FarConfigScreen.unit;

/** The settings of the birds that are rarely changed, behind <i>Advanced...</i> under <i>Birds</i>. */
public final class BirdsAdvancedScreen extends SideOptionsScreen {
    private static final String KEY = "livinghorizon.options.";

    public BirdsAdvancedScreen(@Nullable Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "birdsAdvanced.title"));
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        FarConfig c = FarConfig.get();
        list.addSmall(
                distance(Shape.ABOVE, blocks("birdMinHeight", 0, 200, c.birdMinHeight, v -> c.birdMinHeight = v)),
                unit("birdSize", "multiplier", 1, 6, c.birdSize, v -> c.birdSize = v));
    }

    @Override
    public void removed() {
        FarConfig.save();
    }
}
