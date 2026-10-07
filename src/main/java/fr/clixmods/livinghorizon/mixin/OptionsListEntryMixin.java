package fr.clixmods.livinghorizon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.clixmods.livinghorizon.ui.SideOptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The game places each row of options, and each header, on the middle of the screen
 * rather than of the list. On the mod's screens the list is a column on the left: the
 * rows are placed on the middle of that column instead.
 */
// The list has had headers of its own since 1.21.11.
//? if >=1.21.11 {
@Mixin(targets = {
        "net.minecraft.client.gui.components.OptionsList$Entry",
        "net.minecraft.client.gui.components.OptionsList$HeaderEntry"})
//?} else {
/*@Mixin(targets = "net.minecraft.client.gui.components.OptionsList$Entry")
*///?}
abstract class OptionsListEntryMixin {
    // An entry draws itself in renderContent from 1.21.9, in render before.
    //? if >=1.21.9 {
    @WrapOperation(method = "renderContent",
    //?} else
    /*@WrapOperation(method = "render",*/
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/Screen;width:I"))
    private int livinghorizon$columnWidth(Screen screen, Operation<Integer> width) {
        return screen instanceof SideOptionsScreen side ? side.columnWidth() : width.call(screen);
    }
}
