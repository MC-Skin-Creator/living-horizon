package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.track.PositionPublisher;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Makes every server share the positions with the players who have the mod
 * ({@link PositionPublisher}): a dedicated one, so that putting the mod in its {@code mods}
 * folder is enough, and the one inside the game of a player alone in their world or hosting it
 * on LAN. Saves what it keeps as the server stops.
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @Unique
    private boolean livinghorizon$failed;

    // At the start of the tick: what is sent goes out with the rest when the tick flushes the
    // connections. Sent at its end, it would wait for the next tick, or for a paused game to go on.
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void livinghorizon$sharePositions(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        MinecraftServer self = (MinecraftServer) (Object) this;
        if (livinghorizon$failed || self.getTickCount() % PositionPublisher.PERIOD != 0) return;
        try {
            PositionPublisher.of(self).run();
        } catch (RuntimeException e) {
            Logger logger = LoggerFactory.getLogger("Living Horizon");
            logger.warn("Could not share the positions of this server, giving up", e);
            livinghorizon$failed = true;
        }
    }

    // Saved every minute anyway: a server whose stop is not found loses one minute at most.
    @Inject(method = "stopServer", at = @At("HEAD"), require = 0)
    private void livinghorizon$savePositions(CallbackInfo ci) {
        PositionPublisher publisher = PositionPublisher.existing((MinecraftServer) (Object) this);
        if (publisher != null) publisher.save();
    }
}
