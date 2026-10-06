package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.debug.DebugHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The debug panel and depth view, over the world and under the screens. */
@Mixin(Gui.class)
abstract class GuiMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void livinghorizon$debug(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        DebugHud.render(graphics);
    }
}
