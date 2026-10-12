// Before 1.21.6 the game has no output override: our target stands in for its own.
//? if <1.21.6 {
/*package fr.clixmods.livinghorizon.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * With Fabulous graphics, the see-through parts of an entity (a slime's outer body, an
 * item in hand) are drawn into a target of their own. While a figure is baked into an
 * impostor, that target is ours too, so that the picture has every part of the figure.
 ^/
@Mixin(LevelRenderer.class)
abstract class LevelRendererTargetMixin {
    @Inject(method = "getItemEntityTarget", at = @At("HEAD"), cancellable = true)
    private void livinghorizon$impostorTarget(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget drawing = ImpostorAtlas.drawing();
        if (drawing != null) cir.setReturnValue(drawing);
    }
}
*///?}
