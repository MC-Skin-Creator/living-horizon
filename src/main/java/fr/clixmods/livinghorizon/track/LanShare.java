package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * The companion data pack's job for a world opened to LAN: the host runs the server inside
 * their own game, so their mod publishes the positions ({@link PositionPublisher}) and the
 * friends who join only need the mod. A player who opens their single player world never
 * installs a data pack, which is why this exists.
 *
 * <p>Nothing runs in an unshared single player world, nor on another server (a dedicated
 * server runs the publisher itself, see {@code MinecraftServerMixin}). The objectives are
 * removed when the world closes, so the world is left as it was found.
 */
public final class LanShare {
    private static @Nullable Shared shared;
    private static int ticks;

    private LanShare() {
    }

    /** Every client tick: publishes while the world the player hosts is open to LAN. */
    public static void tick(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || !server.isPublished()) {
            close();
            return;
        }
        if (shared == null || shared.server != server) {
            close();
            shared = new Shared(server, new PositionPublisher(server, false));
            ticks = 0;
        }
        if (++ticks < PositionPublisher.PERIOD) return;
        ticks = 0;
        PositionPublisher publisher = shared.publisher;
        server.execute(() -> {
            try {
                publisher.run();
            } catch (RuntimeException e) {
                LivingHorizonClient.LOGGER.warn("Could not share the positions of the LAN world", e);
            }
        });
    }

    /** Removes what was written, when the world closes or stops being shared. */
    public static void close() {
        Shared closing = shared;
        shared = null;
        if (closing == null || !closing.server.isRunning()) return;
        try {
            // On the server's thread, where the scoreboard belongs; the game is leaving the world.
            closing.server.submit(closing.publisher::remove).get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            LivingHorizonClient.LOGGER.debug("Could not remove the shared positions", e);
        }
    }

    private record Shared(IntegratedServer server, PositionPublisher publisher) {
    }
}
