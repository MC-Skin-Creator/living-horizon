package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.ui.FarConfigScreen;
import fr.clixmods.livinghorizon.ui.NumberSlider;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The sliders of the mod's screen, driven with the mouse and the keyboard as a player would: a
 * value typed after a right click, Escape leaving it as it was, the right end with no limit asked
 * first (no keeps the value, yes sets it) whether typed or dragged to, and the arrow keys moving
 * a slider one value, ten with Shift.
 */
public final class TypedValuesScenario implements Scenario {
    private static final int LEFT = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    private static final int RIGHT = GLFW.GLFW_MOUSE_BUTTON_RIGHT;

    @Override
    public String name() {
        return "typedvalues";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, c -> {
            c.maxDistantMobs = 512;
            c.mobMaxDistance = 512;
            c.impostorDistance = 256;
        });
        context.runOnClient(minecraft -> minecraft.options.guiScale().set(2));
        List<String> wrong = new ArrayList<>();
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            context.setScreen(() -> new FarConfigScreen(null));
            context.waitTicks(5);

            // 1. Typed: right click, a number, Enter.
            type(context, "maxDistantMobs", "1234");
            Scene.screenshot(context, this, "typing");
            context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            context.waitTicks(2);
            expect(wrong, "typed 1234", context, c -> c.maxDistantMobs, 1234);
            expectShown(wrong, context, "maxDistantMobs", "1234");

            // 2. Escape leaves the value, and the screen stays open.
            type(context, "maxDistantMobs", "7");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(2);
            expect(wrong, "Escape", context, c -> c.maxDistantMobs, 1234);
            if (!context.computeOnClient(minecraft -> minecraft.screen instanceof FarConfigScreen)) {
                wrong.add("Escape while typing closed the screen");
                context.setScreen(() -> new FarConfigScreen(null));
                context.waitTicks(3);
            }

            // 3. The word for no limit, typed: asked, and no keeps the value.
            type(context, "maxDistantMobs", "unlim");
            context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            context.waitTicks(3);
            Scene.screenshot(context, this, "asked-typed");
            if (answer(context, false)) {
                expect(wrong, "no to a typed Unlimited", context, c -> c.maxDistantMobs, 1234);
                expectShown(wrong, context, "maxDistantMobs", "1234");
            } else {
                wrong.add("typing Unlimited did not ask");
            }

            // 4. A number past the end: asked, and yes sets no limit.
            type(context, "maxDistantMobs", "99999");
            context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            context.waitTicks(3);
            if (answer(context, true)) {
                expect(wrong, "yes to 99999", context, c -> c.maxDistantMobs, FarConfig.UNLIMITED_MOBS);
            } else {
                wrong.add("typing 99999 did not ask");
            }

            // 5. Dragged to the right end: shown, asked on release, no puts it back.
            drag(context, "mobMaxDistance", 1.2);
            Scene.screenshot(context, this, "asked-dragged");
            if (answer(context, false)) {
                expect(wrong, "no to a dragged Unlimited", context, c -> c.mobMaxDistance, -1);
                int kept = context.computeOnClient(minecraft -> FarConfig.get().mobMaxDistance);
                if (kept <= 0) wrong.add("mobMaxDistance kept no limit after no");
                expectShown(wrong, context, "mobMaxDistance", String.valueOf(kept));
            } else {
                wrong.add("dragging Mob Render Distance to the end did not ask");
            }

            // 6. The arrow keys: one value, ten with Shift, on a slider that never asks.
            type(context, "impostorDistance", "500");
            context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            context.waitTicks(2);
            expect(wrong, "typed 500", context, c -> c.impostorDistance, 500);
            focus(context, "impostorDistance");
            context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
            context.waitTicks(2);
            if (context.computeOnClient(minecraft -> FarConfig.get().impostorDistance) == 500) {
                // The game's slider takes the arrows once Enter is pressed on it.
                context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
                context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT);
                context.waitTicks(2);
            }
            expect(wrong, "right arrow", context, c -> c.impostorDistance, 501);
            context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
            context.getInput().pressKey(GLFW.GLFW_KEY_LEFT);
            context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
            context.waitTicks(2);
            expect(wrong, "Shift and left arrow", context, c -> c.impostorDistance, 491);
            Scene.screenshot(context, this, "arrows");

            context.setScreen(() -> null);
        } finally {
            context.runOnClient(minecraft -> minecraft.options.guiScale().set(1));
        }
        wrong.forEach(line -> Scene.log("typedvalues: " + line));
        if (!wrong.isEmpty()) throw new AssertionError("typedvalues: " + wrong);
        Scene.log("typedvalues: typed, cancelled, asked, dragged and stepped as expected");
    }

    /** Right click on the setting's slider, then the text: the field holds the value, all selected. */
    private static void type(ClientGameTestContext context, String setting, String text) {
        double[] at = middle(context, setting);
        context.getInput().setCursorPos(at[0], at[1]);
        context.getInput().pressMouse(RIGHT);
        context.waitTicks(2);
        context.getInput().typeChars(text);
        context.waitTicks(2);
    }

    /** A left click on the slider, which gives it the keys; the value it lands on is typed back. */
    private static void focus(ClientGameTestContext context, String setting) {
        double[] at = middle(context, setting);
        int before = context.computeOnClient(minecraft -> FarConfig.get().impostorDistance);
        context.getInput().setCursorPos(at[0], at[1]);
        context.getInput().pressMouse(LEFT);
        context.waitTicks(2);
        // The click put the slider where it landed: typed back to where it was.
        type(context, setting, String.valueOf(before));
        context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        context.waitTicks(2);
    }

    private static void drag(ClientGameTestContext context, String setting, double fraction) {
        double[] b = show(context, setting);
        double[] track = {b[0], b[2], b[1] + b[3] / 2};
        context.getInput().setCursorPos(track[0] + track[1] * 0.3, track[2]);
        context.getInput().holdMouse(LEFT);
        context.waitTicks(1);
        context.getInput().setCursorPos(track[0] + track[1] * fraction, track[2]);
        context.waitTicks(1);
        context.getInput().releaseMouse(LEFT);
        context.waitTicks(3);
    }

    /** Yes or no on the question, when there is one; whether there was. */
    private static boolean answer(ClientGameTestContext context, boolean yes) {
        double[] at = context.computeOnClient(minecraft -> {
            if (!(minecraft.screen instanceof ConfirmScreen confirm)) return null;
            List<Button> buttons = confirm.children().stream().filter(c -> c instanceof Button).map(c -> (Button) c).toList();
            Button button = buttons.get(yes ? 0 : buttons.size() - 1);
            double scale = minecraft.getWindow().getGuiScale();
            return new double[]{(button.getX() + button.getWidth() / 2.0) * scale, (button.getY() + button.getHeight() / 2.0) * scale};
        });
        if (at == null) return false;
        context.getInput().setCursorPos(at[0], at[1]);
        context.getInput().pressMouse(LEFT);
        context.waitTicks(3);
        return true;
    }

    private static double[] middle(ClientGameTestContext context, String setting) {
        double[] b = show(context, setting);
        return new double[]{b[0] + b[2] / 2, b[1] + b[3] / 2};
    }

    private static void expect(List<String> wrong, String step, ClientGameTestContext context,
                               ToIntFunction<FarConfig> value, int expected) {
        int actual = context.computeOnClient(minecraft -> value.applyAsInt(FarConfig.get()));
        if (expected >= 0 && actual != expected) wrong.add(step + ": " + actual + ", not " + expected);
    }

    private static void expectShown(List<String> wrong, ClientGameTestContext context, String setting, String value) {
        String shown = context.computeOnClient(minecraft -> slider(minecraft, setting).getMessage().getString());
        if (!shown.contains(value)) wrong.add(setting + " reads \"" + shown + "\", not " + value);
    }

    /**
     * The setting's slider in window pixels, {x, y, width, height}, the list scrolled until it is
     * in view: the rows move when the next frame draws them.
     */
    private static double[] show(ClientGameTestContext context, String setting) {
        for (int i = 0; i < 20; i++) {
            double[] at = context.computeOnClient(minecraft -> {
                NumberSlider slider = slider(minecraft, setting);
                AbstractSelectionList<?> list = list(minecraft.screen);
                if (list != null && (slider.getY() < list.getY() || slider.getY() + slider.getHeight() > list.getBottom())) {
                    list.setScrollAmount(list.scrollAmount() + slider.getY() - list.getY() - 40);
                    return null;
                }
                double scale = minecraft.getWindow().getGuiScale();
                return new double[]{slider.getX() * scale, slider.getY() * scale, slider.getWidth() * scale, slider.getHeight() * scale};
            });
            if (at != null) return at;
            context.waitTicks(2);
        }
        throw new IllegalStateException("Could not scroll to " + setting);
    }

    private static NumberSlider slider(Minecraft minecraft, String setting) {
        String caption = Component.translatable("livinghorizon.options." + setting).getString();
        NumberSlider slider = find(minecraft.screen, caption);
        if (slider == null) throw new IllegalStateException("No slider " + caption + " on " + minecraft.screen);
        return slider;
    }

    private static @Nullable NumberSlider find(GuiEventListener listener, String caption) {
        if (listener instanceof NumberSlider slider && slider.getMessage().getString().startsWith(caption)) return slider;
        if (listener instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                NumberSlider found = find(child, caption);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static @Nullable AbstractSelectionList<?> list(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractSelectionList<?> list) return list;
        }
        return null;
    }
}
