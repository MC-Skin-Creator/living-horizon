package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import net.minecraft.client.gui.GuiGraphics;
//? if <1.21.2
//import com.mojang.blaze3d.systems.RenderSystem;
//? if >=1.21.2 && <1.21.6
//import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** The pictures the mod's screens draw: 16 by 16 textures, drawn at their own size. */
final class Icons {
    static final int SIZE = 16;

    private static final Identifier DISCORD = Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "textures/gui/discord.png");

    private Icons() {}

    /** The Discord logo, its top left corner at {@code x}, {@code y}. */
    static void discord(GuiGraphics graphics, int x, int y) {
        //? if >=1.21.6 {
        graphics.blit(DISCORD, x, y, x + SIZE, y + SIZE, 0f, 1f, 0f, 1f);
        //?} elif >=1.21.2 {
        /*graphics.blit(RenderTypes::guiTextured, DISCORD, x, y, 0f, 0f, SIZE, SIZE, SIZE, SIZE, SIZE, SIZE);
        *///?} else {
        /*// Blended, as the newer versions draw it: the logo's edges are see-through.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(DISCORD, x, y, SIZE, SIZE, 0f, 0f, SIZE, SIZE, SIZE, SIZE);
        RenderSystem.disableBlend();
        *///?}
    }
}
