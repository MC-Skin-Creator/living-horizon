package fr.clixmods.livinghorizon.mixin;

// The F3 entries exist from 1.21.9.
//? if >=1.21.9 {
import fr.clixmods.livinghorizon.debug.FakeEntitiesDebugEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * The F3 screen shows the mod's line from the start, in the game's own profiles. Once the
 * player has chosen their own lines (F3 + F6), what they chose is kept.
 */
@Mixin(DebugScreenEntryList.class)
abstract class DebugScreenEntryListMixin {
    @Shadow
    private Map<Identifier, DebugScreenEntryStatus> allStatuses;

    @Shadow
    public abstract void rebuildCurrentList();

    @Inject(method = "loadProfile", at = @At("TAIL"))
    private void livinghorizon$profile(DebugScreenProfile profile, CallbackInfo ci) {
        allStatuses.putIfAbsent(FakeEntitiesDebugEntry.ID, DebugScreenEntryStatus.IN_OVERLAY);
        rebuildCurrentList();
    }

    //? if >=26.1 {
    /*@Inject(method = "load", at = @At("TAIL"))
    *///?} else {
    @Inject(method = "loadDefaultProfile", at = @At("TAIL"))
    //?}
    private void livinghorizon$defaultProfile(CallbackInfo ci) {
        allStatuses.putIfAbsent(FakeEntitiesDebugEntry.ID, DebugScreenEntryStatus.IN_OVERLAY);
    }
}
//?}
