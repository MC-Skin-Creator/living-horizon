package fr.clixmods.livinghorizon.mixin;

// The GUI renders pictures-in-pictures from 1.21.6; before, GameRendererMixin bakes the impostors.
//? if >=1.21.6 {
//? if >=1.21.9 {
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.compat.FarDepth;
//?}
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where the game itself draws entities into textures (the inventory's player, for one):
 * once its pictures-in-pictures are done, the impostors waiting to be baked are drawn the
 * same way, so the render state they need is already the one the game leaves behind.
 */
@Mixin(GuiRenderer.class)
abstract class GuiRendererMixin {
    @Inject(method = "preparePictureInPicture", at = @At("TAIL"))
    private void livinghorizon$bakeImpostors(CallbackInfo ci) {
        ImpostorAtlas.bakePending();
    }

    // Before 1.21.9 the debug panel draws the depth view itself (DebugHud).
    //? if >=1.21.9 {
    /** The depth view, over the world once it is drawn and under the GUI. */
    @Inject(method = "render", at = @At("HEAD"))
    private void livinghorizon$depthView(CallbackInfo ci) {
        int mode = FarConfig.get().debugDepthView;
        if (mode != 0) FarDepth.drawDebugView(mode);
        // With Vulkan, through the game's device.
        //? if >=26.3
        /*if (mode != 0) fr.clixmods.livinghorizon.compat.FarDepthGpu.drawView(mode);*/
    }
    //?}
}
//?}
