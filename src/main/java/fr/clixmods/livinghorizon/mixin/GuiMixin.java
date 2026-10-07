package fr.clixmods.livinghorizon.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import fr.clixmods.livinghorizon.debug.DebugHud;
//? if >=26.2 {
/*import net.minecraft.client.gui.Hud;
*///?} else {
import net.minecraft.client.gui.Gui;
//?}
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The debug panel and depth view, over the world and under the screens. */
// From 26.2 the game's own overlay is a part of the GUI of its own, the HUD.
//? if >=26.2 {
/*@Mixin(Hud.class)
*///?} else {
@Mixin(Gui.class)
//?}
abstract class GuiMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractRenderState", at = @At("TAIL"))
    *///?} else {
    @Inject(method = "render", at = @At("TAIL"))
    //?}
    // What the method takes besides changed in 1.21: only the first is asked for.
    private void livinghorizon$debug(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics graphics) {
        DebugHud.draw(graphics);
    }
}
