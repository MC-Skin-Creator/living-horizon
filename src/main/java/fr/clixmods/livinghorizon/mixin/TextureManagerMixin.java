package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.lod.LodRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * The resource packs changed: skins and models may look different, so the impostors and the
 * levels of detail are baked again.
 */
@Mixin(TextureManager.class)
abstract class TextureManagerMixin {
    @Inject(method = "reload", at = @At("HEAD"))
    private void livinghorizon$reloaded(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        ImpostorAtlas.invalidate();
        LodRenderer.invalidate();
    }
}
