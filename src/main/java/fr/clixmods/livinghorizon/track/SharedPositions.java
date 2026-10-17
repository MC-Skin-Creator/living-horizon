package fr.clixmods.livinghorizon.track;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The positions a server with the mod shares ({@link PositionPublisher}), as this client last
 * received them ({@link PositionsMessage}): every player, online or logged off, and the
 * remembered mobs around this client. Nothing here is read from the world; a server without
 * the mod sends nothing, and {@link #active()} stays false.
 */
public final class SharedPositions {
    /** Ticks without news of the players before what was received is no longer trusted. */
    private static final int STALE_TICKS = 60;

    public record Report(double x, double y, double z, float yaw, int dimension, boolean riding) {
    }

    /** A mob the server publishes, under its UUID. */
    public record MobReport(UUID id, double x, double y, double z, int kind, int dimension, boolean baby, int variant) {
    }

    private final Map<String, PositionsMessage.Player> players = new HashMap<>();
    private final Map<String, PositionsMessage.Player> offline = new HashMap<>();
    private final Map<UUID, MobReport> mobs = new HashMap<>();
    private PositionsMessage.@Nullable Mobs lastMobs;
    private int ticksSinceNews = STALE_TICKS;
    private boolean active;

    /** A message from the server, on the game's thread. */
    void receive(byte[] data) {
        PositionsMessage.Decoded message = PositionsMessage.read(data);
        if (message == null) return;
        if (message.kind() == PositionsMessage.PLAYERS && message.players() != null) {
            players.clear();
            for (PositionsMessage.Player player : message.players()) players.put(player.name(), player);
            ticksSinceNews = 0;
            active = true;
        }
        if (message.kind() == PositionsMessage.OFFLINE && message.players() != null) {
            offline.clear();
            for (PositionsMessage.Player player : message.players()) offline.put(player.name(), player);
        }
        if (message.mobs() != null) {
            mobs.clear();
            for (PositionsMessage.Mob mob : message.mobs().mobs()) {
                mobs.put(mob.id(), new MobReport(mob.id(), mob.x(), mob.y(), mob.z(), mob.kind(), mob.dimension(),
                        mob.baby(), mob.variant()));
            }
            lastMobs = message.mobs();
        }
    }

    /** Once per tick: whether the server shares positions right now. */
    boolean update() {
        if (ticksSinceNews < STALE_TICKS) ticksSinceNews++;
        active = ticksSinceNews < STALE_TICKS;
        return active;
    }

    /** Another server or world: what the last one said means nothing here. */
    void clear() {
        players.clear();
        offline.clear();
        mobs.clear();
        lastMobs = null;
        ticksSinceNews = STALE_TICKS;
        active = false;
    }

    public boolean active() {
        return active;
    }

    /** What the server last said about one player, by name. Null when nothing. */
    public @Nullable Report read(String name) {
        PositionsMessage.Player player = active ? players.getOrDefault(name, offline.get(name)) : null;
        return player == null ? null
                : new Report(player.x(), player.y(), player.z(), player.yaw(), player.dimension(), player.riding());
    }

    /** Every player the server has a position for, online or not. */
    public List<String> holders() {
        List<String> names = new ArrayList<>(players.keySet());
        for (String name : offline.keySet()) if (!players.containsKey(name)) names.add(name);
        return names;
    }

    /** The published mob a player rides: null when they ride nothing the server publishes. */
    public @Nullable UUID vehicle(String name) {
        PositionsMessage.Player player = active ? players.get(name) : null;
        return player == null ? null : player.vehicle();
    }

    /** Every mob the server last published around this client. */
    public List<UUID> mobs() {
        return new ArrayList<>(mobs.keySet());
    }

    public @Nullable MobReport readMob(UUID id) {
        return active ? mobs.get(id) : null;
    }

    /**
     * Whether the last mobs sent were chosen where a mob stands: a remembered mob there that
     * was not sent is gone; one farther off was only out of reach.
     */
    boolean covers(int dimension, double x, double z) {
        return lastMobs != null && lastMobs.covers(dimension, x, z);
    }

    /** The code of a dimension: 0 overworld, 1 nether, 2 end, 3 anything else. */
    public static int dimensionCode(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) return 0;
        if (dimension == Level.NETHER) return 1;
        if (dimension == Level.END) return 2;
        return 3;
    }
}
