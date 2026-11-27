package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.platform.Events;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A "Living Horizon..." button on the game's Options screen, so the settings are one click
 * from the pause menu without Mod Menu. It sits under "Telemetry Data"; when the screen is
 * too short for that, it takes the left half of the "Done" row.
 *
 * <p>Coming back from a sub-screen, or after a resize, the game keeps the screen's widgets and
 * only lays them out again, with no init event on NeoForge and Forge: the layout centred "Done"
 * at the half width this button gave it, over the button (issue #374). {@link #relayout} runs
 * at the end of every set-up of the screen ({@code ScreenInitMixin}) and gives "Done" its row
 * back before placing the button again. Where the init event does come again (Fabric), the
 * button from the previous run is removed first, so there is never more than one.
 */
public final class OptionsButton {
    private static final String OPEN = "livinghorizon.options.open";
    private static final String TELEMETRY = "options.telemetry";
    private static final int GAP = 4;
    /** Each "Done" button this one shares the row of, and its own width. */
    private static final Map<AbstractWidget, Integer> SHRUNK = new WeakHashMap<>();

    private OptionsButton() {}

    public static void register() {
        Events.screenInit((screen, buttons) -> {
            if (!(screen instanceof OptionsScreen)) return;
            Row row = find(buttons.list());
            if (row.done == null) return;
            if (row.ours != null) {
                release(row.ours, row.done);
                buttons.remove(row.ours);
            }
            Minecraft client = Minecraft.getInstance();
            Button open = Button.builder(Component.translatable(OPEN), b -> client.setScreen(new FarConfigScreen(screen)))
                    .bounds(0, 0, row.done.getWidth(), row.done.getHeight()).build();
            place(open, row.done, row.telemetry);
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
        Row row = find(widgets);
        if (row.done == null || row.ours == null) return;
        release(row.ours, row.done);
        place(row.ours, row.done, row.telemetry);
    }

    private record Row(AbstractWidget done, AbstractWidget telemetry, AbstractWidget ours) {}

    private static Row find(List<AbstractWidget> widgets) {
        AbstractWidget done = null;
        AbstractWidget telemetry = null;
        AbstractWidget ours = null;
        for (AbstractWidget widget : widgets) {
            if (!(widget instanceof Button)) continue;
            if (CommonComponents.GUI_DONE.equals(widget.getMessage())) done = widget;
            else if (isKey(widget, TELEMETRY)) telemetry = widget;
            else if (isKey(widget, OPEN)) ours = widget;
        }
        return new Row(done, telemetry, ours);
    }

    /**
     * Gives "Done" its whole row back: where it was, or, when the layout moved it, centred
     * where the layout centred it at the half width.
     */
    private static void release(AbstractWidget ours, AbstractWidget done) {
        Integer width = SHRUNK.remove(done);
        if (width == null) return;
        boolean moved = done.getX() != ours.getX() + ours.getWidth() + GAP || done.getY() != ours.getY();
        int left = moved ? done.getX() + done.getWidth() / 2 - width / 2 : ours.getX();
        done.setX(left);
        done.setWidth(width);
    }

    /** Under "Telemetry Data", or the left half of the "Done" row when there is no room. */
    private static void place(AbstractWidget ours, AbstractWidget done, AbstractWidget telemetry) {
        if (telemetry != null) {
            int y = telemetry.getY() + telemetry.getHeight() + GAP;
            if (y + telemetry.getHeight() + GAP <= done.getY()) {
                ours.setX(telemetry.getX());
                ours.setY(y);
                ours.setWidth(telemetry.getWidth());
                return;
            }
        }
        int width = done.getWidth();
        int half = (width - GAP) / 2;
        int left = done.getX();
        ours.setX(left);
        ours.setY(done.getY());
        ours.setWidth(half);
        done.setX(left + half + GAP);
        done.setWidth(half);
        SHRUNK.put(done, width);
    }

    private static boolean isKey(AbstractWidget widget, String key) {
        return widget.getMessage().getContents() instanceof TranslatableContents contents
                && key.equals(contents.getKey());
    }
}
