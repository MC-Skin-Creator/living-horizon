package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import static fr.clixmods.livinghorizon.ui.FarConfigScreen.bool;
import static fr.clixmods.livinghorizon.ui.FarConfigScreen.opens;
import static fr.clixmods.livinghorizon.ui.FarConfigScreen.unit;

/**
 * The settings of the simplified images that are rarely changed, behind <i>Advanced...</i> under
 * <i>Simplification</i>: kept off the main screen, and off Sodium's page, which opens this one.
 */
public final class SimplificationAdvancedScreen extends SideOptionsScreen {
    private static final String KEY = "livinghorizon.options.";

    public SimplificationAdvancedScreen(@Nullable Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "simplificationAdvanced.title"));
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        FarConfig c = FarConfig.get();
        list.addSmall(
                bool("impostorShading", c.impostorShading, v -> c.impostorShading = v),
                bool("impostorTurn", c.impostorTurn, v -> c.impostorTurn = v));
        // A whole row: its name is long in most languages.
        list.addBig(unit("impostorTurnDistance", "blocks", FarConfig.MIN_TURN_DISTANCE, FarConfig.MAX_TURN_DISTANCE,
                c.impostorTurnDistance, v -> c.impostorTurnDistance = v));
        list.addBig(unit("impostorPixels", "pixels", 0, FarConfig.MAX_IMPOSTOR_PIXELS, c.impostorPixels,
                v -> c.impostorPixels = v));
        list.addSmall(opens(this, "impostorPreview", ImpostorScreen::new), null);
    }

    @Override
    public void removed() {
        FarConfig.save();
    }
}
