package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.render.GhostRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Another player the game draws itself, turned towards its picture as it nears the distance or
 * size it becomes one at, as the mod turns the models it draws (*Smooth Simplification*): the
 * game hands players over to the mod only once they are pictures, so they turned all at once.
 * From 1.21.2, where the game extracts a state the turn can be given to.
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererTurnMixin {
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("RETURN"))
    private void livinghorizon$turnToPicture(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        GhostRenderer.turnedByGame(entity, cir.getReturnValue(), partialTick);
    }
}
