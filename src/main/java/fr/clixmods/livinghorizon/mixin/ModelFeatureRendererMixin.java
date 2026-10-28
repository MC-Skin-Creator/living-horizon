package fr.clixmods.livinghorizon.mixin;

// The feature renderers came in 1.21.9; before, GhostRenderer counts what it draws itself.
//? if >=1.21.9 {
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.render.lod.LodRenderer;
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
//?} else
/*import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;*/

/**
 * Tells {@link PolygonStats} which entity a model belongs to while it is turned into polygons,
 * and {@link LodRenderer} what that layer is textured with.
 */
@Mixin(ModelFeatureRenderer.class)
//? if >=26.2 {
/*abstract class ModelFeatureRendererMixin extends RenderTypeFeatureRenderer<ModelFeatureRenderer.Submit<?>> {
    // From 26.2 a model is turned into polygons while the frame is prepared, one submit at a time.
    @Inject(method = "prepareModel", at = @At("HEAD"))
    private void livinghorizon$enter(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
        PolygonStats.enter(submit.state());
        // An atlas sprite's texture coordinates (a uv mapping from 26.3) are not the texture's own.
        //? if >=26.3 {
        /^if (PolygonStats.counting()) LodRenderer.layer(submit.renderType(), submit.uvMapping() != null);
        ^///?} else
        if (PolygonStats.counting()) LodRenderer.layer(submit.renderType(), submit.sprite() != null);
    }

    @Inject(method = "prepareModel", at = @At("TAIL"))
    private void livinghorizon$leave(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
        PolygonStats.leave();
    }

    /^*
     * The merged boxes of the group of models just prepared, into that group: other features
     * may be prepared before the models, so they cannot wait for one of their own. Once a group,
     * not once a model: each change of render type in a group costs a lookup, or a draw.
     ^/
    @Inject(method = "buildGroup", at = @At("TAIL"))
    private void livinghorizon$flush(CallbackInfo ci) {
        LodRenderer.flush(this::getVertexBuilder);
    }
}
*///?} else {
abstract class ModelFeatureRendererMixin {
    @Inject(method = "renderModel", at = @At("HEAD"))
    private void livinghorizon$enter(SubmitNodeStorage.ModelSubmit<?> submit, RenderType type, VertexConsumer consumer,
                                     OutlineBufferSource outline, MultiBufferSource.BufferSource buffers, CallbackInfo ci) {
        PolygonStats.enter(submit.state());
        if (PolygonStats.counting()) LodRenderer.layer(type, submit.sprite() != null);
    }

    @Inject(method = "renderModel", at = @At("TAIL"))
    private void livinghorizon$leave(SubmitNodeStorage.ModelSubmit<?> submit, RenderType type, VertexConsumer consumer,
                                     OutlineBufferSource outline, MultiBufferSource.BufferSource buffers, CallbackInfo ci) {
        PolygonStats.leave();
    }
}
//?}
//?}
