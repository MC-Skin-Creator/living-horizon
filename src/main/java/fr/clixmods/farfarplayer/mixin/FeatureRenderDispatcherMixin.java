package fr.clixmods.farfarplayer.mixin;

import fr.clixmods.farfarplayer.compat.VoxyDepth;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets the drawing of the world's entities with Voxy's depth, so that its terrain
 * hides them pixel by pixel. Only the call that follows the world's entity submission is
 * concerned; {@link VoxyDepth} is armed by {@code LevelRendererMixin} for that one.
 */
@Mixin(FeatureRenderDispatcher.class)
abstract class FeatureRenderDispatcherMixin {
    @Inject(method = "renderAllFeatures", at = @At("HEAD"))
    private void farfarplayer$voxyDepthIn(CallbackInfo ci) {
        VoxyDepth.before();
    }

    @Inject(method = "renderAllFeatures", at = @At("TAIL"))
    private void farfarplayer$voxyDepthOut(CallbackInfo ci) {
        VoxyDepth.after();
    }
}
