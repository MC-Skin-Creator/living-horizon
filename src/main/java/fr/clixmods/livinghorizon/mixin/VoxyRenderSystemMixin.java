package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.compat.FarDepth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes which framebuffer Voxy draws its terrain into: the one bound while Sodium draws
 * the world's terrain, which is where the entities' depth goes too. Between render passes
 * Minecraft leaves nothing bound, so this is the moment to ask - and to copy the game's
 * depth before Voxy's is added to it. Voxy is optional: without it, this mixin applies to
 * nothing.
 */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.VoxyRenderSystem", remap = false)
abstract class VoxyRenderSystemMixin {
    @Inject(method = "renderOpaque", at = @At("HEAD"), require = 0)
    private void livinghorizon$noteTarget(CallbackInfo ci) {
        FarDepth.noteTarget();
    }

    /** Apart from the one above: a Voxy that takes other arguments still gets its target noted. */
    @Inject(method = "renderOpaque", at = @At("HEAD"), require = 0)
    private void livinghorizon$noteBelow(@Coerce Object viewport, int sourceDepth, int sourceColour, CallbackInfo ci) {
        FarDepth.noteBelow(sourceDepth);
    }
}
