//? if <26.1 {
package fr.clixmods.livinghorizon.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Pushes the far clipping plane out to the farthest distant player. The game clips at
 * four times the render distance; a player drawn past it would vanish, and drawing them
 * closer instead lets the terrain between - Voxy's - fail to hide them. Only the depth
 * range moves: what is loaded, culled or fogged is unchanged.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @ModifyReturnValue(method = "getDepthFar", at = @At("RETURN"))
    private float livinghorizon$reachDistantPlayers(float far) {
        return GhostRenderer.farPlane(far);
    }
}
//?}
