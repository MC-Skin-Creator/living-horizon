package fr.clixmods.livinghorizon.mixin;

// The F3 entries exist from 1.21.9.
//? if >=1.21.9 {
import fr.clixmods.livinghorizon.debug.DebugHudEntry;
import fr.clixmods.livinghorizon.debug.FakeEntitiesDebugEntry;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
//? if >=26.4 {
/*import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The mod's entry, registered with the game's own, before any debug option is read. */
@Mixin(DebugScreenEntries.class)
abstract class DebugScreenEntriesMixin {
    // From 26.4 the entries are a client registry, filled by bootstrap.
    //? if >=26.4 {
    /*@Inject(method = "bootstrap", at = @At("TAIL"))
    private static void livinghorizon$register(WritableRegistry<DebugScreenEntry> registry, CallbackInfo ci) {
        Registry.register(registry, DebugHudEntry.ID, DebugHudEntry.INSTANCE);
        Registry.register(registry, FakeEntitiesDebugEntry.ID, new FakeEntitiesDebugEntry());
    }
    *///?} else {
    @Shadow
    private static Identifier register(Identifier id, DebugScreenEntry entry) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void livinghorizon$register(CallbackInfo ci) {
        register(DebugHudEntry.ID, DebugHudEntry.INSTANCE);
        register(FakeEntitiesDebugEntry.ID, new FakeEntitiesDebugEntry());
    }
    //?}
}
//?}
