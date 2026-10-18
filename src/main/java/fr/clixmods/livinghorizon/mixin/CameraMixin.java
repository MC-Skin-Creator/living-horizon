//? if >=26.1 {
/*package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.render.GhostRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Pushes the far clipping plane out to the farthest distant player. The game clips at
 * four times the render distance; a player drawn past it would vanish, and drawing them
 * closer instead lets the terrain between - Voxy's - fail to hide them. Only the depth
 * range moves: what is loaded, culled or fogged is unchanged. From 26.1 the camera holds
 * that plane, where it was the game renderer's; see {@code GameRendererMixin}. Moved as soon
 * as it is set, before the projection is made from it: moved at the end of the update, the
 * projection kept the game's plane while everything reading the plane got ours, and Distant
 * Horizons' terrain was put in the depth nearer than it is, over the mobs in front of it.
 ^/
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    private float depthFar;

    @Inject(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;depthFar:F",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void livinghorizon$reachDistantPlayers(DeltaTracker deltaTracker, CallbackInfo ci) {
        depthFar = GhostRenderer.farPlane(depthFar);
    }
}
*///?}
