package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.platform.Events;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A "Living Horizon..." button on the game's Options screen, so the settings are one click
 * from the pause menu without Mod Menu. It sits under "Telemetry Data"; when the screen is
 * too short for that, in the empty band the game leaves between the first row ("FOV",
 * "Online...") and the menus, across both columns. Never beside "Done" (issue #374).
 *
 * <p>Coming back from a sub-screen, or after a resize, the game keeps the screen's widgets and
 * only lays them out again, with no init event on NeoForge and Forge: {@link #relayout} runs at
 * the end of every set-up of the screen ({@code ScreenInitMixin}) and places the button again.
 * Where the init event does come again (Fabric), the button from the previous run is removed
 * first, so there is never more than one.
 */
public final class OptionsButton {
    private static final String OPEN = "livinghorizon.options.open";
    private static final String TELEMETRY = "options.telemetry";
    private static final int GAP = 4;

    private OptionsButton() {}

    public static void register() {
        Events.screenInit((screen, buttons) -> {
            if (!(screen instanceof OptionsScreen)) return;
            List<AbstractWidget> widgets = buttons.list();
            AbstractWidget previous = find(widgets, OPEN);
            if (previous != null) buttons.remove(previous);
            AbstractWidget telemetry = find(widgets, TELEMETRY);
            if (telemetry == null) return;
            Minecraft client = Minecraft.getInstance();
            Button open = Button.builder(Component.translatable(OPEN), b -> client.setScreen(new FarConfigScreen(screen)))
                    .bounds(0, 0, telemetry.getWidth(), telemetry.getHeight()).build();
            place(screen, open, telemetry, buttons.list());
            buttons.add(open);
        });
    }

    /** Places the button again after the game laid the Options screen out, whichever loader. */
    public static void relayout(Screen screen) {
        if (!(screen instanceof OptionsScreen)) return;
        List<AbstractWidget> widgets = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget) widgets.add(widget);
        }
        AbstractWidget ours = find(widgets, OPEN);
        AbstractWidget telemetry = find(widgets, TELEMETRY);
        if (ours != null && telemetry != null) place(screen, ours, telemetry, widgets);
    }

    private static AbstractWidget find(List<AbstractWidget> widgets, String key) {
        for (AbstractWidget widget : widgets) {
            if (widget instanceof Button && widget.getMessage().getContents() instanceof TranslatableContents contents
                    && key.equals(contents.getKey())) {
                return widget;
            }
        }
        return null;
    }

    /**
     * Under "Telemetry Data" when nothing is in the way; otherwise in the tallest empty band
     * between two rows above it, across the width of the two columns. Hidden when neither has
     * room, rather than laid over another button.
     */
    private static void place(Screen screen, AbstractWidget ours, AbstractWidget telemetry, List<AbstractWidget> widgets) {
        int height = telemetry.getHeight();
        List<AbstractWidget> others = new ArrayList<>();
        for (AbstractWidget widget : widgets) {
            // The title is a line of text, not a row of buttons.
            if (widget != ours && widget.visible && widget.getHeight() >= height) others.add(widget);
        }
        ours.visible = true;
        int below = telemetry.getY() + telemetry.getHeight() + GAP;
        if (free(others, telemetry.getX(), telemetry.getX() + telemetry.getWidth(), below - GAP, below + height + GAP)) {
            move(ours, telemetry.getX(), below, telemetry.getWidth());
            return;
        }
        // The band between two rows, above "Telemetry Data", tallest first.
        others.sort(Comparator.comparingInt(AbstractWidget::getY));
        int left = telemetry.getX();
        int right = screen.width / 2 * 2 - left;
        int best = -1;
        int bestRoom = height + 2 * GAP - 1;
        int top = Integer.MIN_VALUE;
        for (AbstractWidget widget : others) {
            if (widget.getY() > telemetry.getY()) break;
            if (top != Integer.MIN_VALUE && widget.getY() - top > bestRoom) {
                bestRoom = widget.getY() - top;
                best = top;
            }
            top = Math.max(top, widget.getY() + widget.getHeight());
        }
        if (best >= 0) {
            move(ours, left, best + (bestRoom - height) / 2, right - left);
            return;
        }
        ours.visible = false;
    }

    /** Whether no widget meets the rectangle. */
    private static boolean free(List<AbstractWidget> widgets, int left, int right, int top, int bottom) {
        for (AbstractWidget widget : widgets) {
            if (widget.getX() < right && left < widget.getX() + widget.getWidth()
                    && widget.getY() < bottom && top < widget.getY() + widget.getHeight()) {
                return false;
            }
        }
        return true;
    }

    private static void move(AbstractWidget widget, int x, int y, int width) {
        widget.setX(x);
        widget.setY(y);
        widget.setWidth(width);
    }
}
