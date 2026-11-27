package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.ui.FarConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Living Horizon..." button of the game's Options screen, as it opens and after coming
 * back from one of its sub-screens (issue #374): one button, overlapping none, and the screen
 * laid out the same both times, also when only laid out again with no init event, as on
 * NeoForge, and never on the row of "Done". Checked under "Telemetry Data" and, at a large GUI
 * scale, in the band above the menus.
 */
public final class OptionsButtonScenario implements Scenario {
    @Override
    public String name() {
        return "optionsbutton";
    }

    @Override
    public void run(ClientGameTestContext context) {
        List<String> problems = new ArrayList<>();
        int scale = context.computeOnClient(minecraft -> minecraft.options.guiScale().get());
        try {
            for (int gui : new int[] {1, 4}) {
                context.runOnClient(minecraft -> {
                    minecraft.options.guiScale().set(gui);
                    minecraft.resizeDisplay();
                });
                Screen[] options = new Screen[1];
                context.setScreen(() -> options[0] = new OptionsScreen(new TitleScreen(), Minecraft.getInstance().options));
                context.waitTicks(3);
                List<String> first = context.computeOnClient(minecraft -> layout(minecraft.screen));
                Scene.screenshot(context, this, "gui" + gui + "-opened");
                for (int round = 1; round <= 3; round++) {
                    context.setScreen(() -> new FarConfigScreen(options[0]));
                    context.waitTicks(3);
                    // What a sub-screen does as it closes.
                    context.setScreen(() -> options[0]);
                    context.waitTicks(3);
                    List<String> back = context.computeOnClient(minecraft -> layout(minecraft.screen));
                    Scene.log("optionsbutton: gui " + gui + " round " + round + ": " + back);
                    if (!back.equals(first)) {
                        problems.add("gui " + gui + ", back " + round + " times: " + back + " instead of " + first);
                    }
                }
                // NeoForge and Forge: shown again, the screen is only laid out again, with no init
                // event, and the mixin at the end of the set-up places the button.
                context.runOnClient(minecraft -> {
                    try {
                        java.lang.reflect.Method reposition = Screen.class.getDeclaredMethod("repositionElements");
                        reposition.setAccessible(true);
                        reposition.invoke(minecraft.screen);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                    fr.clixmods.livinghorizon.ui.OptionsButton.relayout(minecraft.screen);
                });
                List<String> laidOut = context.computeOnClient(minecraft -> layout(minecraft.screen));
                Scene.log("optionsbutton: gui " + gui + " laid out again: " + laidOut);
                if (!laidOut.equals(first)) {
                    problems.add("gui " + gui + ", laid out again without an init event: " + laidOut + " instead of " + first);
                }
                Scene.screenshot(context, this, "gui" + gui + "-back");
                problems.addAll(context.computeOnClient(minecraft -> overlaps(minecraft.screen, gui)));
            }
        } finally {
            context.runOnClient(minecraft -> {
                minecraft.options.guiScale().set(scale);
                minecraft.resizeDisplay();
            });
            context.setScreen(() -> null);
        }
        problems.forEach(problem -> Scene.log("optionsbutton: " + problem));
        if (!problems.isEmpty()) throw new AssertionError(problems.size() + " problems on the Options screen");
    }

    private static List<AbstractWidget> widgets(Screen screen) {
        List<AbstractWidget> found = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible) found.add(widget);
        }
        return found;
    }

    private static List<String> layout(Screen screen) {
        List<String> lines = new ArrayList<>();
        for (AbstractWidget widget : widgets(screen)) {
            lines.add(widget.getMessage().getString() + "@" + widget.getX() + "," + widget.getY()
                    + "+" + widget.getWidth() + "x" + widget.getHeight());
        }
        return lines;
    }

    private static List<String> overlaps(Screen screen, int gui) {
        List<String> problems = new ArrayList<>();
        List<AbstractWidget> widgets = widgets(screen);
        String open = net.minecraft.network.chat.Component.translatable("livinghorizon.options.open").getString();
        String done = net.minecraft.network.chat.CommonComponents.GUI_DONE.getString();
        int ours = 0;
        for (AbstractWidget widget : widgets) {
            if (!widget.getMessage().getString().equals(open)) continue;
            ours++;
            for (AbstractWidget other : widgets) {
                if (other.getMessage().getString().equals(done) && other.getY() == widget.getY()) {
                    problems.add("gui " + gui + ": the Living Horizon button is on the row of \"Done\"");
                }
            }
        }
        if (ours != 1) problems.add("gui " + gui + ": " + ours + " Living Horizon buttons");
        for (int i = 0; i < widgets.size(); i++) {
            for (int j = i + 1; j < widgets.size(); j++) {
                AbstractWidget a = widgets.get(i), b = widgets.get(j);
                if (a.getX() < b.getRight() && b.getX() < a.getRight()
                        && a.getY() < b.getBottom() && b.getY() < a.getBottom()) {
                    problems.add("gui " + gui + ": \"" + a.getMessage().getString() + "\" overlaps \""
                            + b.getMessage().getString() + "\"");
                }
            }
        }
        return problems;
    }
}
