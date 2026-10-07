package fr.clixmods.livinghorizon.mixin;

// The feature renderers came in 1.21.9; before, GhostRenderer counts what it draws itself.
//? if >=1.21.9 {
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <26.2 {
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;
//?}

/** Tells {@link PolygonStats} which entity a model belongs to while it is turned into polygons. */
@Mixin(ModelFeatureRenderer.class)
abstract class ModelFeatureRendererMixin {
    //? if >=26.2 {
    /*// From 26.2 a model is turned into polygons while the frame is prepared, one submit at a time.
    @Inject(method = "prepareModel", at = @At("HEAD"))
    private void livinghorizon$enter(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
        PolygonStats.enter(submit.state());
    }

    @Inject(method = "prepareModel", at = @At("TAIL"))
    private void livinghorizon$leave(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
        PolygonStats.leave();
    }
    *///?} else {
    @Inject(method = "renderModel", at = @At("HEAD"))
    private void livinghorizon$enter(SubmitNodeStorage.ModelSubmit<?> submit, RenderType type, VertexConsumer consumer,
                                     OutlineBufferSource outline, MultiBufferSource.BufferSource buffers, CallbackInfo ci) {
        PolygonStats.enter(submit.state());
    }

    @Inject(method = "renderModel", at = @At("TAIL"))
    private void livinghorizon$leave(SubmitNodeStorage.ModelSubmit<?> submit, RenderType type, VertexConsumer consumer,
                                     OutlineBufferSource outline, MultiBufferSource.BufferSource buffers, CallbackInfo ci) {
        PolygonStats.leave();
    }
    //?}
}
//?}
