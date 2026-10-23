package fr.clixmods.livinghorizon.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The game's own "Open link?" question for the mod's Discord server, where players come with a
 * problem or a question. Opened from the mod's screen and from Sodium's page; back to where it
 * came from, on yes and on no.
 */
public final class DiscordLink extends ConfirmLinkScreen {
    public static final String URL = "https://discord.gg/THSV9dexNj";

    /** Escape pressed over Sodium's screen: closed when it is released, see {@link SodiumReturn}. */
    private boolean closeOnRelease;

    private final @Nullable Screen parent;

    /** The link as the game's screen takes it: from 26.3, a URI. */
    //? if >=26.3 {
    /*private static final java.net.URI LINK = java.net.URI.create(URL);
    *///?} else {
    private static final String LINK = URL;
    //?}

    private DiscordLink(@Nullable Screen parent) {
        super(yes -> {
            if (yes) browse();
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(parent);
        }, LINK, true);
        this.parent = parent;
    }

    public static void open(@Nullable Screen from) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new DiscordLink(from));
    }

    /** Opens the link in the browser: from 26.3, through the GPU library's window code. */
    private static void browse() {
        //? if >=26.3 {
        /*com.mojang.blaze3d.Blaze3D.openUri(LINK);
        *///?} else {
        Util.getPlatform().openUri(LINK);
        //?}
    }

    private boolean overSodium() {
        return SodiumReturn.below(parent);
    }

    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && overSodium()) {
            closeOnRelease = true;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        if (closeOnRelease && event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            closeOnRelease = false;
            callback.accept(false);
            return true;
        }
        return super.keyReleased(event);
    }
    //?} else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && overSodium()) {
            closeOnRelease = true;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (closeOnRelease && keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            closeOnRelease = false;
            callback.accept(false);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    *///?}
}
