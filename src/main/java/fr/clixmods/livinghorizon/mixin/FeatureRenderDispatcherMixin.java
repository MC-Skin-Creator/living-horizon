package fr.clixmods.livinghorizon.mixin;

// The feature renderers came in 1.21.9; before, LevelRendererMixin brackets the entities itself.
//? if >=1.21.9 {
import fr.clixmods.livinghorizon.compat.FarDepth;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets the drawing of the world's entities with Voxy's depth, so that its terrain
 * hides them pixel by pixel. Only the call that follows the world's entity submission is
 * concerned; {@link FarDepth} is armed by {@code LevelRendererMixin} for that one.
 */
//? if >=26.2 {
/*// From 26.2 the world's features are prepared once and drawn in steps through the frame
// graph: the entities are the solid step, then the translucent one, in the main pass.
@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
abstract class FeatureRenderDispatcherMixin {
    @Inject(method = "executeSolid", at = @At("HEAD"))
    private void livinghorizon$voxyDepthIn(CallbackInfo ci) {
        FarDepth.before();
    }

    @Inject(method = "executeTranslucent", at = @At("TAIL"))
    private void livinghorizon$voxyDepthOut(CallbackInfo ci) {
        FarDepth.after();
    }
}
*///?} else {
@Mixin(FeatureRenderDispatcher.class)
abstract class FeatureRenderDispatcherMixin {
    @Inject(method = "renderAllFeatures", at = @At("HEAD"))
    private void livinghorizon$voxyDepthIn(CallbackInfo ci) {
        FarDepth.before();
    }

    @Inject(method = "renderAllFeatures", at = @At("TAIL"))
    private void livinghorizon$voxyDepthOut(CallbackInfo ci) {
        FarDepth.after();
    }
}
//?}
//?}
