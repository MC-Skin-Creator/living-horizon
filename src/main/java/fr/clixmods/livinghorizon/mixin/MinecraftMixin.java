// Before 1.21.6 the game has no output override: our target stands in for its main one.
//? if <1.21.6 {
/*package fr.clixmods.livinghorizon.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * While a figure is baked into an impostor, our target is the game's main one: the game
 * draws the figure into it, and a render type that switches to a target of its own and
 * back comes back to ours.
 ^/
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void livinghorizon$impostorTarget(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget drawing = ImpostorAtlas.drawing();
        if (drawing != null) cir.setReturnValue(drawing);
    }
}
*///?}
