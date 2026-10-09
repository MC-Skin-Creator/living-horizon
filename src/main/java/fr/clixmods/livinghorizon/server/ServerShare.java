package fr.clixmods.livinghorizon.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.clixmods.livinghorizon.LivingHorizon;
import fr.clixmods.livinghorizon.platform.Network;
import fr.clixmods.livinghorizon.platform.ServerEvents;
import fr.clixmods.livinghorizon.share.Protocol;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The server side of the mod: what a server running it tells the clients running it, over
 * {@link Network}. Clients without the mod are never sent anything, and the server runs as
 * it would without it.
 *
 * <ul>
 *   <li>Every player online, five times a second, in every dimension: what the data pack
 *       shares through the scoreboard, sent only to the clients with the mod.</li>
 *   <li>Players who logged off, where they were last ({@code <world>/livinghorizon/players.json}).</li>
 *   <li>The mobs of {@link MobRegistry} around each client, every five seconds, a change
 *       from what that client was sent before: new ones with their saved data, so a client
 *       draws them as they are without ever having met them, moved ones by their position
 *       alone, and the ones it should forget.</li>
 * </ul>
 *
 * <p>It runs on whatever server the mod is on, the one a single player world runs too: a
 * world opened to LAN shares its mobs with the players who join it.
 */
public final class ServerShare {
    private static final Gson GSON = new GsonBuilder().create();
    /** Ticks between two looks at the loaded mobs, and between two rounds of mobs sent to one client. */
    private static final int MOB_TICKS = 100;
    /** Mobs new to a client sent in one round at most: the nearest first, the rest at the next round. */
    private static final int NEW_PER_ROUND = 400;
    /** A message stays well under the game's limit for a custom payload, 1 MiB. */
    private static final int MESSAGE_BYTES = 400_000;
    /** Saved data larger than this does not travel: the client builds the mob from its kind alone. */
    private static final int MAX_NBT = 200_000;

    private static @Nullable ServerShare current;

    /** What one client with the mod was sent. */
    private static final class Peer {
        /** Per mob: which move and which saved data of it the client has. */
        final Map<UUID, int[]> sent = new HashMap<>();
        /** Its rounds of mobs fall on their own ticks, so that all clients are not sent theirs at once. */
        final int phase;

        Peer(UUID id) {
            phase = Math.floorMod(id.hashCode(), MOB_TICKS);
        }
    }

    /** A player's last position, as written to the file. */
    private static final class Spot {
        UUID id;
        String name;
        String dimension;
        double x, y, z;
        float yaw;
    }

    private final MinecraftServer server;
    private final ServerConfig config;
    private final Path folder;
    private final @Nullable MobRegistry mobs;
    private final Map<UUID, Peer> peers = new HashMap<>();
    /** Every player who played here, where they were last. */
    private final Map<UUID, Protocol.Player> lastSeen = new LinkedHashMap<>();
    private Set<UUID> online = new HashSet<>();
    private long tick;
    private boolean playersDirty;

    private ServerShare(MinecraftServer server) {
        this.server = server;
        config = ServerConfig.load();
        folder = server.getWorldPath(LevelResource.ROOT).resolve("livinghorizon");
        mobs = config.enabled && config.shareMobs ? new MobRegistry(folder) : null;
        readPlayers();
        if (config.enabled) {
            LivingHorizon.LOGGER.info("Living Horizon shares positions with the clients running it ({} mobs remembered)",
                    mobs == null ? 0 : mobs.size());
        }
    }

    /** Hooks the server side up: called once, on the client and on a server alike. */
    public static void install() {
        ServerEvents.tick(server -> {
            if (current == null || current.server != server) {
                if (current != null) current.close();
                current = new ServerShare(server);
            }
            current.tick();
        });
        ServerEvents.stopping(server -> {
            if (current != null && current.server == server) {
                current.close();
                current = null;
            }
        });
        ServerEvents.entityUnload((entity, level) -> {
            if (current != null && current.mobs != null && level.getServer() == current.server) current.mobs.left(entity, level);
        });
    }

    private void tick() {
        tick++;
        if (!config.enabled) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        Set<UUID> here = new HashSet<>();
        for (ServerPlayer player : players) {
            here.add(player.getUUID());
            // A client says which channels it listens on a moment after it joins.
            if (tick % 20 == 0 && !peers.containsKey(player.getUUID()) && Network.canSend(player)) greet(player);
        }
        peers.keySet().retainAll(here);
        for (UUID id : online) {
            if (!here.contains(id)) leave(id);
        }
        online = here;

        if (tick % 4 == 0) sharePlayers(players);
        if (mobs != null) {
            if (tick % MOB_TICKS == 0) mobs.look(server, tick);
            for (ServerPlayer player : players) {
                Peer peer = peers.get(player.getUUID());
                if (peer != null && (tick + peer.phase) % MOB_TICKS == 0) shareMobs(peer, player);
            }
        }
        if (tick % 6000 == 0) write(false);
    }

    private void close() {
        write(true);
    }

    private void write(boolean now) {
        if (mobs != null) mobs.write(now);
        if (playersDirty) writePlayers();
    }

    // --- Players ---------------------------------------------------------------------------

    private void greet(ServerPlayer player) {
        peers.put(player.getUUID(), new Peer(player.getUUID()));
        if (!config.shareOfflinePlayers) return;
        List<Protocol.Player> resting = new ArrayList<>();
        for (Protocol.Player spot : lastSeen.values()) {
            if (!online.contains(spot.id())) resting.add(spot);
        }
        Network.send(player, Protocol.encode(new Protocol.Resting(true, resting)));
    }

    /** Logged off: everyone with the mod is told where they rest. */
    private void leave(UUID id) {
        Protocol.Player last = lastSeen.get(id);
        if (last == null || !config.shareOfflinePlayers) return;
        broadcast(Protocol.encode(new Protocol.Resting(false, List.of(last))));
    }

    private void sharePlayers(List<ServerPlayer> players) {
        List<Protocol.Player> shared = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            // A spectator is seen by nobody in the game; it is not shown far away either.
            if (player.isSpectator()) continue;
            Protocol.Player spot = new Protocol.Player(player.getUUID(), player.getScoreboardName(),
                    player.level().dimension().identifier().toString(),
                    player.getX(), player.getY(), player.getZ(), player.getYRot(), player.isPassenger());
            shared.add(spot);
            Protocol.Player before = lastSeen.put(spot.id(), spot);
            if (before == null || before.x() != spot.x() || before.y() != spot.y() || before.z() != spot.z()) playersDirty = true;
        }
        // Sent even empty: it is how a client knows the server is there.
        if (!peers.isEmpty()) broadcast(Protocol.encode(new Protocol.Players(config.sharePlayers ? shared : List.of())));
    }

    private void broadcast(byte[] message) {
        if (peers.isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (peers.containsKey(player.getUUID())) Network.send(player, message);
        }
    }

    private void readPlayers() {
        Path file = folder.resolve("players.json");
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            List<Spot> read = GSON.fromJson(reader, new TypeToken<List<Spot>>() { }.getType());
            if (read == null) return;
            for (Spot spot : read) {
                if (spot == null || spot.id == null || spot.name == null || spot.dimension == null) continue;
                lastSeen.put(spot.id, new Protocol.Player(spot.id, spot.name, spot.dimension, spot.x, spot.y, spot.z, spot.yaw, false));
            }
        } catch (IOException | RuntimeException e) {
            LivingHorizon.LOGGER.warn("Could not read {}", file, e);
        }
    }

    /** A few hundred players at most: written on the server's thread. */
    private void writePlayers() {
        playersDirty = false;
        Path file = folder.resolve("players.json");
        List<Spot> spots = new ArrayList<>(lastSeen.size());
        for (Protocol.Player player : lastSeen.values()) {
            Spot spot = new Spot();
            spot.id = player.id();
            spot.name = player.name();
            spot.dimension = player.dimension();
            spot.x = player.x();
            spot.y = player.y();
            spot.z = player.z();
            spot.yaw = player.yaw();
            spots.add(spot);
        }
        try {
            Files.createDirectories(folder);
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(spots, writer);
            }
        } catch (IOException e) {
            LivingHorizon.LOGGER.warn("Could not write {}", file, e);
        }
    }

    // --- Mobs ------------------------------------------------------------------------------

    /** The mobs around one client, as a change from what it has. */
    private void shareMobs(Peer peer, ServerPlayer player) {
        String dimension = player.level().dimension().identifier().toString();
        double px = player.getX(), pz = player.getZ();
        double radius = (double) config.mobRadius * config.mobRadius;
        List<MobRegistry.Known> near = new ArrayList<>();
        Map<MobRegistry.Known, Double> distances = new HashMap<>();
        for (MobRegistry.Known mob : mobs.all()) {
            if (mob.nbt == null || !dimension.equals(mob.dimension)) continue;
            double dx = mob.x - px, dz = mob.z - pz;
            double distance = dx * dx + dz * dz;
            if (distance > radius) continue;
            near.add(mob);
            distances.put(mob, distance);
        }
        near.sort(Comparator.comparingDouble(distances::get));
        if (near.size() > config.maxMobsPerPlayer) near = near.subList(0, config.maxMobsPerPlayer);

        Set<UUID> kept = new HashSet<>();
        List<Protocol.Mob> changed = new ArrayList<>();
        int fresh = 0;
        for (MobRegistry.Known mob : near) {
            int[] sent = peer.sent.get(mob.id);
            if (sent == null) {
                if (fresh >= NEW_PER_ROUND) continue;
                fresh++;
                changed.add(message(mob, true));
                peer.sent.put(mob.id, new int[] {mob.moved, mob.changed});
            } else if (sent[1] != mob.changed) {
                changed.add(message(mob, true));
                sent[0] = mob.moved;
                sent[1] = mob.changed;
            } else if (sent[0] != mob.moved) {
                changed.add(message(mob, false));
                sent[0] = mob.moved;
            }
            kept.add(mob.id);
        }
        List<UUID> forgotten = new ArrayList<>();
        peer.sent.keySet().removeIf(id -> {
            if (kept.contains(id)) return false;
            forgotten.add(id);
            return true;
        });
        if (changed.isEmpty() && forgotten.isEmpty()) return;

        // Cut into messages of a bounded size; the mobs to forget go with the last one.
        List<Protocol.Mob> batch = new ArrayList<>();
        int bytes = 0;
        for (Protocol.Mob mob : changed) {
            int size = 128 + (mob.nbt() == null ? 0 : mob.nbt().length() * 3);
            if (!batch.isEmpty() && bytes + size > MESSAGE_BYTES) {
                Network.send(player, Protocol.encode(new Protocol.Mobs(batch, List.of())));
                batch = new ArrayList<>();
                bytes = 0;
            }
            batch.add(mob);
            bytes += size;
        }
        Network.send(player, Protocol.encode(new Protocol.Mobs(batch, forgotten)));
    }

    private static Protocol.Mob message(MobRegistry.Known mob, boolean withNbt) {
        String nbt = withNbt && mob.nbt != null && mob.nbt.length() <= MAX_NBT ? mob.nbt : null;
        return new Protocol.Mob(mob.id, mob.type, mob.dimension, mob.x, mob.y, mob.z, mob.yaw, mob.named, nbt);
    }
}
