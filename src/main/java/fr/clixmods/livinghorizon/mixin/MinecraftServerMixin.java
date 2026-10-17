package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.track.PositionPublisher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Makes a dedicated server share the positions with the players who have the mod
 * ({@link PositionPublisher}), so that putting the mod in its {@code mods} folder is enough, and
 * saves what it keeps as the server stops. A world opened to LAN is the client's
 * ({@code LanShare}): this does nothing on the integrated server.
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @Unique
    private PositionPublisher livinghorizon$publisher;
    @Unique
    private boolean livinghorizon$failed;

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void livinghorizon$sharePositions(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        MinecraftServer self = (MinecraftServer) (Object) this;
        if (livinghorizon$failed || !(self instanceof DedicatedServer) || self.getTickCount() % PositionPublisher.PERIOD != 0) return;
        try {
            if (livinghorizon$publisher == null) livinghorizon$publisher = new PositionPublisher(self, true);
            livinghorizon$publisher.run();
        } catch (RuntimeException e) {
            Logger logger = LoggerFactory.getLogger("Living Horizon");
            logger.warn("Could not share the positions of this server, giving up", e);
            livinghorizon$failed = true;
        }
    }

    // Saved every minute anyway: a server whose stop is not found loses one minute at most.
    @Inject(method = "stopServer", at = @At("HEAD"), require = 0)
    private void livinghorizon$savePositions(CallbackInfo ci) {
        if (livinghorizon$publisher != null) livinghorizon$publisher.save();
    }
}
