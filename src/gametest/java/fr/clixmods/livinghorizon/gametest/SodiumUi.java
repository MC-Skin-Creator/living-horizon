package fr.clixmods.livinghorizon.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Sodium's video settings screen as a player meets it, read by reflection: Sodium's classes
 * outside its API are not on the test's classpath, and change between its versions. Positions
 * are in GUI units; {@link #window} turns them into the cursor positions the test input takes.
 */
public final class SodiumUi {
    private static final String SODIUM = "net.caffeinemc.mods.sodium.client.";

    private SodiumUi() {}

    /** Sodium's settings, as the screen shows them. */
    public static Object config() {
        try {
            return Class.forName(SODIUM + "config.ConfigManager").getField("CONFIG").get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Sodium has no config", e);
        }
    }

    /** Living Horizon's pages, as Sodium holds them. */
    public static List<?> pages() {
        for (Object mod : (List<?>) call(config(), "getModOptions")) {
            if ("livinghorizon".equals(call(mod, "configId"))) return (List<?>) call(mod, "pages");
        }
        return List.of();
    }

    /** Sodium's video settings, opened on one page. */
    public static Screen open(Object page) {
        try {
            Class<?> screen = Class.forName(SODIUM + "gui.VideoSettingsScreen");
            Class<?> optionPage = Class.forName(SODIUM + "config.structure.OptionPage");
            return (Screen) screen.getMethod("createScreen", Screen.class, optionPage).invoke(null, null, page);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not open Sodium's screen", e);
        }
    }

    public static boolean isSodiumScreen(@Nullable Screen screen) {
        return screen != null && screen.getClass().getName().equals(SODIUM + "gui.VideoSettingsScreen");
    }

    /** The scrolling list of options on Sodium's screen. */
    public static Object list(Screen screen) {
        try {
            Class<?> type = Class.forName(SODIUM + "gui.options.control.AbstractOptionList");
            for (GuiEventListener child : screen.children()) if (type.isInstance(child)) return child;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("No option list on " + screen);
    }

    /** The rows of the mod's settings, in the order of the pages, buttons included. */
    public static List<Object> controls(Screen screen) {
        List<Object> mine = new ArrayList<>();
        for (Object control : (List<?>) call(list(screen), "getControls")) {
            if (key(control).startsWith("livinghorizon.options.")) mine.add(control);
        }
        return mine;
    }

    /** One row, by the setting's name in the mod's settings ({@code "birdDensity"}). */
    public static Object control(Screen screen, String name) {
        for (Object control : controls(screen)) if (name(control).equals(name)) return control;
        throw new IllegalStateException("No row " + name + " on Sodium's screen");
    }

    public static Object option(Object control) {
        return call(control, "getOption");
    }

    /** The setting a row stands for: the last part of its name's translation key. */
    public static String name(Object control) {
        return key(control).substring("livinghorizon.options.".length()).replaceAll("\\.name$", "");
    }

    private static String key(Object control) {
        Component name = (Component) call(option(control), "getName");
        return name.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    /** Tick box, slider, cycling or button: the name of the row's class, inside Sodium. */
    public static String kind(Object control) {
        String name = control.getClass().getSimpleName();
        if (name.startsWith("TickBox")) return "tickbox";
        if (name.startsWith("Slider")) return "slider";
        if (name.startsWith("Cycling")) return "cycling";
        if (name.startsWith("ExternalButton")) return "button";
        return name;
    }

    /** The value the row shows now: changed but not applied yet, or the saved one. */
    public static Object value(Object control) {
        return call(option(control), "getValidatedValue");
    }

    public static boolean enabled(Object control) {
        return (Boolean) call(option(control), "isEnabled");
    }

    /** {x, y, width, height} of a row, where it is drawn now (the list's scroll included). */
    public static int[] bounds(Object widget) {
        Object dim = call(widget, "getDimensions");
        int y = (Integer) call(widget, "getY");
        return new int[]{(Integer) call(dim, "x"), y, (Integer) call(dim, "width"), (Integer) call(dim, "height")};
    }

    /** {x, y, width} of a slider's track, in GUI units: its y is the track's middle. */
    public static int[] track(Object slider) {
        return new int[]{(Integer) call(slider, "getSliderX"), (Integer) call(slider, "getSliderY") + 5, 90};
    }

    /** {top, bottom} of the list's visible part. */
    public static int[] viewport(Screen screen) {
        int[] b = bounds(list(screen));
        return new int[]{b[1], b[1] + b[3]};
    }

    /** Sodium's own "Apply" button. */
    public static Object applyButton(Screen screen) {
        return field(screen, "applyButton");
    }

    /** A GUI position as a cursor position in the window. */
    public static double window(double gui) {
        return gui * Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static Object field(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                // Up the hierarchy.
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("No field " + name + " on " + target.getClass());
    }

    /** A public or inherited method without parameters, even on Sodium's package-private classes. */
    public static Object call(Object target, String method) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method m : type.getDeclaredMethods()) {
                if (!m.getName().equals(method) || m.getParameterCount() != 0 || m.isBridge()) continue;
                try {
                    m.setAccessible(true);
                    return m.invoke(target);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Sodium: " + method, e);
                }
            }
            for (Class<?> face : type.getInterfaces()) {
                for (Method m : face.getMethods()) {
                    if (!m.getName().equals(method) || m.getParameterCount() != 0) continue;
                    try {
                        return m.invoke(target);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Sodium: " + method, e);
                    }
                }
            }
        }
        throw new IllegalStateException("No method " + method + " on " + target.getClass());
    }
}
