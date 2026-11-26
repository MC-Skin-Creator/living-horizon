package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.MapMarkers;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.Screen;

/**
 * Players on the maps of other mods (`--with journeymap --with xaeros-minimap --with
 * xaeros-world-map`): test players who logged off become markers, and JourneyMap's full map is
 * opened to look at them. Without those mods there is nothing to hand them to: the list stays empty.
 */
public final class MapMarkersScenario implements Scenario {
    private static final int TESTS = 5;

    @Override
    public String name() {
        return "maps";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(
                    "livinghorizon debug offline test crowd " + TESTS + " 24"));
            context.waitTicks(80);
            int markers = context.computeOnClient(minecraft -> MapMarkers.markers().size());
            boolean journeyMap = context.computeOnClient(minecraft ->
                    net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("journeymap"));
            Scene.log("maps: " + markers + " markers, JourneyMap " + (journeyMap ? "loaded" : "absent"));
            if (journeyMap && markers != TESTS) throw new AssertionError("maps: " + markers + " markers, not " + TESTS);
            if (journeyMap) {
                context.runOnClient(minecraft -> {
                    try {
                        minecraft.setScreen((Screen) Class.forName("journeymap.client.ui.fullscreen.Fullscreen")
                                .getConstructor().newInstance());
                    } catch (ReflectiveOperationException e) {
                        Scene.log("maps: JourneyMap's full map not opened: " + e);
                    }
                });
                context.waitTicks(60);
                Scene.screenshot(context, this, "journeymap");
                context.runOnClient(minecraft -> minecraft.setScreen(null));
            }
            context.waitTicks(10);
            Scene.screenshot(context, this, "minimap");
            context.runOnClient(minecraft -> minecraft.player.connection.sendCommand("livinghorizon debug offline test clear"));
        }
    }
}
