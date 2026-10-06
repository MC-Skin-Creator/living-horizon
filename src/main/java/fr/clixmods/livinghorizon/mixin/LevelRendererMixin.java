package fr.clixmods.livinghorizon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.ambient.AmbientRenderer;
import fr.clixmods.livinghorizon.compat.VoxyDepth;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts the distant players in the frame. There is no public way to add a render state
 * to the world's entity list, and drawing them in a pass of our own would lose what
 * the entity pass gives: outlines, shader packs, the right order against translucency.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void livinghorizon$beginFrame(Camera camera, Frustum frustum, DeltaTracker deltaTracker,
                                         LevelRenderState frame, CallbackInfo ci) {
        GhostRenderer.beginFrame();
    }

    @Inject(method = "extractEntity", at = @At("HEAD"))
    private void livinghorizon$extracted(Entity entity, float partialTick,
                                        CallbackInfoReturnable<EntityRenderState> cir) {
        GhostRenderer.extracted(entity);
    }

    @Inject(method = "extractVisibleEntities", at = @At("TAIL"))
    private void livinghorizon$addDistantPlayers(Camera camera, Frustum frustum, DeltaTracker deltaTracker,
                                                LevelRenderState frame, CallbackInfo ci) {
        GhostRenderer.extract(camera, deltaTracker.getGameTimeDeltaPartialTick(true), frame);
    }

    /** The world's entities are all submitted: the next drawing of features is theirs. */
    @Inject(method = "submitBlockEntities", at = @At("TAIL"))
    private void livinghorizon$armVoxyDepth(PoseStack pose, LevelRenderState frame, SubmitNodeStorage storage,
                                          CallbackInfo ci) {
        VoxyDepth.arm();
    }

    /** The silhouette birds, with the entities so that shader packs treat them alike. */
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void livinghorizon$submitBirds(PoseStack pose, LevelRenderState frame, SubmitNodeCollector collector,
                                         CallbackInfo ci) {
        AmbientRenderer.submit(pose, frame.cameraRenderState.pos, collector);
    }

    /** Moves a distant player's state closer along the line of sight, and scales it to match. */
    @WrapOperation(method = "submitEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void livinghorizon$submit(EntityRenderDispatcher dispatcher, EntityRenderState state, CameraRenderState camera,
                                     double x, double y, double z, PoseStack pose, SubmitNodeCollector collector,
                                     Operation<Void> original) {
        GhostRenderer.Transform t = GhostRenderer.transform(state);
        if (t == null) {
            original.call(dispatcher, state, camera, x, y, z, pose, collector);
            return;
        }
        // The anchor (the feet of the player) slides in by `pull`; everything around it,
        // the mount under them, keeps its place relative to it at the new scale.
        pose.pushPose();
        pose.translate(
                (float) (t.anchorX() * t.pull() + (x - t.anchorX()) * t.scale()),
                (float) (t.anchorY() * t.pull() + (y - t.anchorY()) * t.scale()),
                (float) (t.anchorZ() * t.pull() + (z - t.anchorZ()) * t.scale()));
        pose.scale((float) t.scale(), (float) t.scale(), (float) t.scale());
        original.call(dispatcher, state, camera, 0.0, 0.0, 0.0, pose, collector);
        pose.popPose();
    }
}
