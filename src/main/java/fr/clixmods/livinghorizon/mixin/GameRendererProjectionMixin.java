//? if >=26.1 {
/*package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.compat.FarDepth;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/^*
 * The projection the world is drawn with: the camera's, with view bobbing, the hurt tilt and
 * nausea multiplied in. From 26.1 only the GPU is handed it, and the level renderer gets the
 * camera's own: tested against the world's depth with that one, a distant mob was looked for
 * a few degrees off at every step, and shown or hidden by turns.
 ^/
@Mixin(GameRenderer.class)
abstract class GameRendererProjectionMixin {
    //? if >=26.3 {
    /^@ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    ^///?} else {
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    //?}
    private Matrix4f livinghorizon$noteWorldProjection(Matrix4f projection) {
        FarDepth.noteWorldProjection(projection);
        return projection;
    }
}
*///?}
