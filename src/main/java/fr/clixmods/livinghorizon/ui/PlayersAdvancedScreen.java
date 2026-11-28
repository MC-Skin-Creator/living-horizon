package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import static fr.clixmods.livinghorizon.ui.FarConfigScreen.bool;
import static fr.clixmods.livinghorizon.ui.FarConfigScreen.forgets;
import static fr.clixmods.livinghorizon.ui.FarConfigScreen.unit;

/** The settings of distant players that are rarely changed, behind <i>Advanced...</i> under <i>Distant Players</i>. */
public final class PlayersAdvancedScreen extends SideOptionsScreen {
    private static final String KEY = "livinghorizon.options.";

    public PlayersAdvancedScreen(@Nullable Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "playersAdvanced.title"));
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        FarConfig c = FarConfig.get();
        list.addSmall(
                bool("showVehicles", c.showVehicles, v -> c.showVehicles = v),
                bool("offlineNames", c.offlineNames, v -> c.offlineNames = v),
                bool("offlineTag", c.offlineTag, v -> c.offlineTag = v),
                bool("offlineTabList", c.offlineTabList, v -> c.offlineTabList = v));
        list.addBig(unit("minApparentPixels", "pixels", 0, 32, (int) c.minApparentPixels, v -> c.minApparentPixels = v));
        list.addSmall(forgets(this, "forgetSleepers", () -> FarPlayerTracker.get().resting().forgetAll()), null);
    }

    @Override
    public void removed() {
        FarConfig.save();
    }
}
