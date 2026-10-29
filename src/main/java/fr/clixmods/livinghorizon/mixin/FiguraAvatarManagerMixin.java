package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the mod's copies no Figura avatar: they are drawn in their skin. Before 1.21.2 Figura
 * finds the avatar of the entity it is handed by the player's UUID, and runs its scripts on
 * it; a copy of a player who logged off or is far away never ticks and is in no level, so the
 * avatar's scripts fail on it or leave it in its first pose. From 1.21.2 Figura finds the
 * entity by its number in the level, where no copy is ({@link Puppets#numbered}), and this
 * has nothing to do. Figura is optional: without it, this mixin applies to nothing.
 */
@Pseudo
@Mixin(targets = "org.figuramc.figura.avatar.AvatarManager", remap = false)
abstract class FiguraAvatarManagerMixin {
    @Inject(method = "getAvatar", at = @At("HEAD"), cancellable = true, require = 0)
    private static void livinghorizon$noAvatar(Entity entity, CallbackInfoReturnable<Object> cir) {
        if (Puppets.is(entity)) cir.setReturnValue(null);
    }
}
