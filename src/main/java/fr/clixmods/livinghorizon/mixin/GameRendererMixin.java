//? if <26.1 {
package fr.clixmods.livinghorizon.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if <1.21.6 {
/*import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

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

    //? if <1.21.6 {
    /*/^*
     * Bakes the impostors waiting, before the frame is drawn: the game sets up the world's
     * drawing and the GUI's afterwards, whatever the bake left. From 1.21.6 they are baked
     * in the GUI pass instead (GuiRendererMixin).
     ^/
    @Inject(method = "render", at = @At("HEAD"))
    private void livinghorizon$bakeImpostors(CallbackInfo ci) {
        ImpostorAtlas.bakePending();
    }
    *///?}
}
//?}
