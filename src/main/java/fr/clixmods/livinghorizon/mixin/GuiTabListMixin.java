package fr.clixmods.livinghorizon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fr.clixmods.livinghorizon.ui.OfflineTabList;
//? if >=26.2 {
/*import net.minecraft.client.gui.Hud;
*///?} else {
import net.minecraft.client.gui.Gui;
//?}
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * In a world played alone the game opens the player list (Tab) only when someone else is
 * listed: the players who logged off count. Where the game asks whether the world is played
 * here, it is not taken for one played alone while there are such players to list. Forge before
 * 1.20.6 draws the list in an overlay of its own, which this misses: there, alone, it stays shut.
 */
//? if >=26.2 {
/*@Mixin(Hud.class)
*///?} else {
@Mixin(Gui.class)
//?}
abstract class GuiTabListMixin {
    // Inside the whole overlay's render before 1.20.5, in a method of its own after.
    //? if <1.20.5 {
    /*@ModifyExpressionValue(method = "render", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isLocalServer()Z"))
    *///?} else {
    @ModifyExpressionValue(method = {"renderTabList", "extractTabList"}, require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isLocalServer()Z"))
    //?}
    private boolean livinghorizon$listOffline(boolean local) {
        return local && !OfflineTabList.any();
    }
}
