package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.platform.Events;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.List;

/**
 * A "Living Horizon..." button on the game's Options screen, so the settings are one click
 * from the pause menu without Mod Menu. It sits under "Telemetry Data"; when the screen is
 * too short for that, it takes the left half of the "Done" row. The game runs the screen's
 * init again when it comes back from a sub-screen, without clearing its widgets: the
 * button from the previous run is removed first, so there is never more than one.
 */
public final class OptionsButton {
    private static final String OPEN = "livinghorizon.options.open";
    private static final String TELEMETRY = "options.telemetry";

    private OptionsButton() {}

    public static void register() {
        Events.screenInit((screen, buttons) -> {
            if (!(screen instanceof OptionsScreen)) return;
            Minecraft client = Minecraft.getInstance();
            List<AbstractWidget> widgets = buttons.list();

            AbstractWidget done = null;
            AbstractWidget telemetry = null;
            AbstractWidget previous = null;
            for (AbstractWidget widget : widgets) {
                if (!(widget instanceof Button)) continue;
                if (CommonComponents.GUI_DONE.equals(widget.getMessage())) done = widget;
                else if (isKey(widget, TELEMETRY)) telemetry = widget;
                else if (isKey(widget, OPEN)) previous = widget;
            }
            if (done == null) return;
            if (previous != null) {
                buttons.remove(previous);
                // A previous run that shared the "Done" row: give "Done" its width back.
                if (previous.getY() == done.getY()) {
                    done.setX(previous.getX());
                    done.setWidth(previous.getWidth() * 2 + 4);
                }
            }

            Component label = Component.translatable(OPEN);
            Button.OnPress open = b -> client.setScreen(new FarConfigScreen(screen));
            if (telemetry != null) {
                int y = telemetry.getY() + telemetry.getHeight() + 4;
                if (y + telemetry.getHeight() + 4 <= done.getY()) {
                    buttons.add(Button.builder(label, open)
                            .bounds(telemetry.getX(), y, telemetry.getWidth(), telemetry.getHeight()).build());
                    return;
                }
            }
            int half = (done.getWidth() - 4) / 2;
            int left = done.getX();
            done.setX(left + half + 4);
            done.setWidth(half);
            buttons.add(Button.builder(label, open).bounds(left, done.getY(), half, done.getHeight()).build());
        });
    }

    private static boolean isKey(AbstractWidget widget, String key) {
        return widget.getMessage().getContents() instanceof TranslatableContents contents
                && key.equals(contents.getKey());
    }
}
