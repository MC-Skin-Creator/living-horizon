package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.ui.FarConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;

/**
 * The settings screen in a world: everything, the rows of options included, sits in the
 * column on the left, so that the game stays in view on the right.
 */
public final class OptionsScreenScenario implements Scenario {
    @Override
    public String name() {
        return "options";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            context.setScreen(() -> new FarConfigScreen(null));
            context.waitTicks(5);
            Scene.screenshot(context, this, "screen");
            int overflow = context.computeOnClient(minecraft -> {
                int panel = Math.min(360, minecraft.screen.width);
                return overflow(minecraft.screen, panel);
            });
            Scene.log("options widgets past the column: " + overflow + " px");
            context.setScreen(() -> null);
            if (overflow > 0) throw new AssertionError("Options widgets " + overflow + " px past the left column");
        }
    }

    /** How far the widgets inside {@code parent}, rows of lists included, reach past {@code panel}. */
    private static int overflow(ContainerEventHandler parent, int panel) {
        int worst = 0;
        for (GuiEventListener child : parent.children()) {
            if (child instanceof AbstractWidget widget) worst = Math.max(worst, widget.getRight() - panel);
            if (child instanceof ContainerEventHandler container) worst = Math.max(worst, overflow(container, panel));
        }
        return worst;
    }
}
