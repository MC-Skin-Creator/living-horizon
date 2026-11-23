package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A block placed or broken on this client: the players who logged off near it look there a
 * moment. Only a new block counts, never a change of state (a door opening, a crop growing).
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelBlockMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), require = 0)
    private void livinghorizon$blockChanged(BlockPos at, BlockState before, BlockState after, int flags, CallbackInfo ci) {
        if (before.getBlock() != after.getBlock()) FarPlayerTracker.get().resting().blockChanged(at);
    }
}
