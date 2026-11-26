package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.ui.OfflineTabList;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** The players who logged off, listed after those online in the player list (Tab). */
@Mixin(PlayerTabOverlay.class)
abstract class PlayerTabOverlayMixin {
    @Inject(method = "getPlayerInfos", at = @At("RETURN"), cancellable = true, require = 0)
    private void livinghorizon$offline(CallbackInfoReturnable<List<PlayerInfo>> cir) {
        List<PlayerInfo> all = OfflineTabList.after(cir.getReturnValue());
        if (all != cir.getReturnValue()) cir.setReturnValue(all);
    }
}
