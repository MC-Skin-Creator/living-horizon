package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.render.GhostRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws a mount carrying another player as far as the player on it.
 *
 * <p>The game culls every entity past a distance that grows with its size, and makes
 * an exception for other players only ({@code RemotePlayer} draws to ten times the
 * usual distance). A boat is small, so at 100 blocks a friend in one sits on nothing.
 */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "shouldRenderAtSqrDistance", at = @At("HEAD"), cancellable = true)
    private void livinghorizon$mountsOfPlayers(double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (GhostRenderer.alwaysRender((Entity) (Object) this)) cir.setReturnValue(true);
    }
}
