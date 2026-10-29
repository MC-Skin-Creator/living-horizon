package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Leaves the mod's copies to the game's own model under Epic Fight. Epic Fight draws every
 * player and humanoid mob with a skeleton of its own, made with the entity and animated as it
 * joins a level and ticks; a copy of a player who logged off, or of a remembered mob, does
 * neither, and was drawn frozen in its first pose, whichever way it walked. Its renderer
 * leaves an entity it has no data for to the game, which this makes of every copy. Epic Fight
 * is optional: without it, this mixin applies to nothing.
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.world.capabilities.EpicFightCapabilities", remap = false)
abstract class EpicFightCapabilitiesMixin {
    @Inject(method = "getEntityPatch", at = @At("HEAD"), cancellable = true, require = 0)
    private static void livinghorizon$noPatch(Entity entity, Class<?> type, CallbackInfoReturnable<Object> cir) {
        if (Puppets.is(entity)) cir.setReturnValue(null);
    }
}
