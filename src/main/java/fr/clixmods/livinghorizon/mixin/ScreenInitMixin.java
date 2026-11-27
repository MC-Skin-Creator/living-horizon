package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.ui.OptionsButton;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The end of a screen's set-up, also when it is shown again or resized and only laid out
 * again: NeoForge and Forge send no init event then, and the "Living Horizon..." button of the
 * Options screen must follow the layout ({@link OptionsButton#relayout}).
 */
@Mixin(Screen.class)
abstract class ScreenInitMixin {
    //? if >=1.21.11 {
    @Inject(method = "init(II)V", at = @At("TAIL"))
    private void livinghorizon$laidOut(int width, int height, CallbackInfo ci) {
        OptionsButton.relayout((Screen) (Object) this);
    }
    //?} else {
    /*@Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("TAIL"))
    private void livinghorizon$laidOut(net.minecraft.client.Minecraft minecraft, int width, int height, CallbackInfo ci) {
        OptionsButton.relayout((Screen) (Object) this);
    }
    *///?}
}
