package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.compat.FarDepth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Without a shader pack, Voxy draws its water into the same depth as its opaque terrain:
 * just before, {@link FarDepth} copies that depth, the one without the water. Voxy is
 * optional: without it, this mixin applies to nothing.
 */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.NormalRenderPipeline", remap = false)
abstract class VoxyNormalPipelineMixin {
    @Inject(method = "setupAndBindTranslucent", at = @At("HEAD"), require = 0)
    private void livinghorizon$noteOpaque(@Coerce Object viewport, CallbackInfo ci) {
        FarDepth.noteOpaque(this);
    }
}
