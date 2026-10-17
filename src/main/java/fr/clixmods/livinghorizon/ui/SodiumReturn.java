package fr.clixmods.livinghorizon.ui;

import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Going back to Sodium's video settings with Escape. Sodium runs its own Escape shortcut,
 * "close", when the key is released: a screen of the mod opened from Sodium's that closed on
 * the press would hand the release to Sodium's, which would close too, back to the game. Over
 * Sodium's screen the mod's screens close on the release instead, which Sodium's never sees.
 */
public final class SodiumReturn {
    private static final String SODIUM_SCREEN = "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen";

    private SodiumReturn() {}

    /** Whether {@code parent}, the screen gone back to, is Sodium's video settings. */
    public static boolean below(@Nullable Screen parent) {
        return parent != null && parent.getClass().getName().equals(SODIUM_SCREEN);
    }
}
