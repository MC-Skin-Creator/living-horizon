package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.ui.ChangelogScreen;
import fr.clixmods.livinghorizon.ui.DebugScreen;
import fr.clixmods.livinghorizon.ui.DiscordLink;
import fr.clixmods.livinghorizon.ui.FarConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The Discord and changelog buttons of the settings: Discord's opens the game's question about
 * the link, and cancelling it comes back to the settings; the changelog's opens the changelog
 * screen, and Done comes back. Both are icons beside the title. The version sits under the title of both screens.
 */
public final class DiscordScenario implements Scenario {
    /** Presses the button with the real mouse: the settings keep theirs in a list, which the helper does not search. */
    private static void click(ClientGameTestContext context, String key) {
        int[] at = context.computeOnClient(minecraft -> {
            double scale = minecraft.getWindow().getGuiScale();
            AbstractWidget button = find(minecraft.screen, key);
            if (button == null) throw new AssertionError("no button " + key);
            return new int[]{(int) ((button.getX() + button.getWidth() / 2) * scale),
                    (int) ((button.getY() + button.getHeight() / 2) * scale)};
        });
        context.getInput().setCursorPos(at[0], at[1]);
        context.waitTicks(2);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
    }

    private static AbstractWidget find(ContainerEventHandler parent, String key) {
        AbstractWidget found = null;
        for (GuiEventListener child : parent.children()) {
            AbstractWidget candidate = null;
            if (child instanceof AbstractWidget widget && matches(widget, key)) candidate = widget;
            else if (child instanceof ContainerEventHandler container) candidate = find(container, key);
            // The two icons have no text: Discord's is the left one, the changelog's the right one.
            if (candidate != null && (found == null || (candidate.getX() > found.getX()) == key.equals("changelog"))) found = candidate;
        }
        return found;
    }

    /** The icon buttons have no text: "" names Discord's, "changelog" the changelog's; any other key is a button's translation key. */
    private static boolean matches(AbstractWidget widget, String key) {
        if (key.isEmpty() || key.equals("changelog")) return widget instanceof net.minecraft.client.gui.components.Button && widget.getMessage().getString().isEmpty();
        return widget.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key);
    }

    @Override
    public String name() {
        return "discord";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            context.setScreen(() -> new FarConfigScreen(null));
            context.waitTicks(5);
            Scene.screenshot(context, this, "settings");

            click(context, "");
            context.waitTicks(5);
            if (!context.computeOnClient(minecraft -> minecraft.screen instanceof DiscordLink)) {
                throw new AssertionError("the Discord button did not open the link question");
            }
            Scene.screenshot(context, this, "link");

            click(context, "gui.cancel");
            context.waitTicks(5);
            if (!context.computeOnClient(minecraft -> minecraft.screen instanceof FarConfigScreen)) {
                throw new AssertionError("cancelling the link question did not come back to the settings");
            }

            click(context, "changelog");
            context.waitTicks(5);
            if (!context.computeOnClient(minecraft -> minecraft.screen instanceof ChangelogScreen)) {
                throw new AssertionError("the changelog button did not open the changelog");
            }
            Scene.screenshot(context, this, "changelog");

            click(context, "gui.done");
            context.waitTicks(5);
            if (!context.computeOnClient(minecraft -> minecraft.screen instanceof FarConfigScreen)) {
                throw new AssertionError("Done on the changelog did not come back to the settings");
            }

            context.setScreen(() -> new DebugScreen(null));
            context.waitTicks(5);
            Scene.screenshot(context, this, "debug");
            context.setScreen(() -> null);
        }
    }
}
