package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.LastSeen;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.ZoneId;

/** Under the crosshair, while it is on a player who logged off: who it is, and when they were last seen. */
public final class OfflineHud {
    private OfflineHud() {
    }

    public static void draw(GuiGraphics graphics) {
        FarConfig config = FarConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (!config.offlinePlayers || !config.offlineNames || hidden(minecraft) || minecraft.screen != null) return;
        RestingPlayers.Spot spot = FarPlayerTracker.get().resting().lookedAt();
        if (spot == null) return;
        String date = LastSeen.format(spot.lastSeen(), minecraft.getLanguageManager().getSelected(), ZoneId.systemDefault());
        Component text = date == null
                ? Component.translatable("livinghorizon.offline.unknown", spot.name())
                : Component.translatable("livinghorizon.offline.lastSeen", spot.name(), date);
        graphics.drawCenteredString(minecraft.font, text, graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 10, 0xFFFFFFFF);
    }

    /** The GUI hidden (F1). From 26.2 the overlay says so itself. */
    private static boolean hidden(Minecraft minecraft) {
        //? if >=26.2 {
        /*return minecraft.gui.hud.isHidden();
        *///?} else
        return minecraft.options.hideGui;
    }
}
