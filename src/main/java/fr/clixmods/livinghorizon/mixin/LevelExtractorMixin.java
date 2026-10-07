package fr.clixmods.livinghorizon.mixin;

//? if >=26.2 {
/*import fr.clixmods.livinghorizon.render.GhostRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * Puts the distant players in the frame's entities, as {@code LevelRendererMixin} did before
 * 26.2: from there the world is extracted by a class of its own, and drawn afterwards.
 ^/
@Mixin(LevelExtractor.class)
abstract class LevelExtractorMixin {
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
        GhostRenderer.extract(camera, deltaTracker.getGameTimeDeltaPartialTick(true), frame.entityRenderStates);
    }
}
*///?}
