package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.ui.OfflineTabList;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.List;

/**
 * Players who logged off in the player list (Tab): listed after the one player online, in a world
 * played alone, where the game opens the list only when someone else is in it; and gone from it
 * with *Offline in Player List* off.
 */
public final class OfflineTabListScenario implements Scenario {
    private static final int TESTS = 5;

    @Override
    public String name() {
        return "offlinetab";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(
                    "livinghorizon debug offline test crowd " + TESTS + " 20"));
            context.waitTicks(60);
            int listed = context.computeOnClient(minecraft -> OfflineTabList.after(List.<PlayerInfo>of()).size());
            Scene.log("offlinetab: " + listed + " offline players listed");
            if (listed != TESTS) throw new AssertionError("offlinetab: " + listed + " listed, not " + TESTS);

            // Held as a player holds Tab: the list opens, alone in the world.
            context.runOnClient(minecraft -> minecraft.options.keyPlayerList.setDown(true));
            context.waitTicks(5);
            Scene.screenshot(context, this, "list");

            context.runOnClient(minecraft -> FarConfig.get().offlineTabList = false);
            context.waitTicks(5);
            int off = context.computeOnClient(minecraft -> OfflineTabList.after(List.<PlayerInfo>of()).size());
            Scene.screenshot(context, this, "off");
            context.runOnClient(minecraft -> {
                minecraft.options.keyPlayerList.setDown(false);
                FarConfig.get().offlineTabList = true;
                minecraft.player.connection.sendCommand("livinghorizon debug offline test clear");
            });
            Scene.log("offlinetab: " + off + " listed with the setting off");
            if (off != 0) throw new AssertionError("offlinetab: " + off + " listed with the setting off");
        }
    }
}
