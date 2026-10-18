package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.platform.Network;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a server with the mod tells the clients with the mod, so that they draw every player
 * at any distance, and the remembered mobs past what the server sends them
 * ({@link PositionsMessage}, received by {@link SharedPositions}).
 *
 * <p>Run by {@link LanShare} for a world opened to LAN, and by {@code MinecraftServerMixin}
 * on a dedicated server. It touches nothing of the client, so a server can load it. A client
 * without the mod is sent nothing.
 *
 * <p>It keeps the last position of every player who logged off, with the mount they took away
 * with them, and of the mobs in chunks no
 * longer loaded, in a file of the world ({@link #FILE}), so that a player who joins later sees
 * them too. A mob is forgotten when its chunk is loaded and it is not there any more.
 *
 * <p>Everything here runs on the server's thread.
 */
public final class PositionPublisher {
    /** Ticks between two publications of the players. */
    public static final int PERIOD = 4;
    /** Publications between two of the mobs: five seconds. */
    private static final int MOB_EVERY = 25;
    /** How far around a player the mobs sent to them reach, in blocks. */
    private static final double MOB_RANGE = 2048;
    /** Bounds the file and the messages; the mobs seen longest ago go first. */
    private static final int MAX_MOBS = 4000;
    /** Publications between two saves of the file, when something changed: a minute. */
    private static final int SAVE_EVERY = 300;
    /** The file in the world's folder. */
    static final String FILE = "livinghorizon-positions.dat";
    private static final Map<String, Integer> KINDS = new HashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");

    static {
        for (int i = 0; i < MobKinds.TYPES.size(); i++) KINDS.put(MobKinds.TYPES.get(i), i + 1);
    }

    private final MinecraftServer server;
    private final boolean dedicated;
    /** Every player who ever played here, online or not, by name. */
    private final Map<String, PositionsMessage.Player> players = new LinkedHashMap<>();
    /** Every remembered mob, the one seen longest ago first. */
    private final LinkedHashMap<UUID, PositionsMessage.Mob> mobs = new LinkedHashMap<>();
    /** What each player online rides alone, by name: the server takes it away with them when they log off. */
    private final Map<String, PositionsMessage.Mount> riding = new HashMap<>();
    /** Which mount {@link #riding} saved, by name: it is saved again when it changes, and every few seconds. */
    private final Map<String, UUID> ridden = new HashMap<>();
    private boolean loaded;
    private boolean dirty;
    private boolean told;
    private int runs;

    public PositionPublisher(MinecraftServer server, boolean dedicated) {
        this.server = server;
        this.dedicated = dedicated;
    }

    /** One publication; call it every {@link #PERIOD} ticks. */
    public void run() {
        if (!server.isRunning()) return;
        if (!loaded) load();
        if (!told) {
            told = true;
            LOGGER.info(dedicated
                    ? "Living Horizon: sharing the positions of this server with the players who have the mod"
                    : "Living Horizon: sharing the positions of this LAN world with the players who join with the mod");
        }
        players();
        if (runs % MOB_EVERY == 0) {
            offline();
            mobs();
        }
        if (++runs % SAVE_EVERY == 0 && dirty) save();
    }

    private void players() {
        Set<String> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String name = player.getScoreboardName();
            online.add(name);
            Entity vehicle = player.getVehicle();
            PositionsMessage.Mount mount = vehicle != null ? mountOf(player, name) : null;
            if (mount != null) {
                riding.put(name, mount);
            } else {
                riding.remove(name);
                ridden.remove(name);
            }
            UUID published = vehicle != null && KINDS.containsKey(EntityType.getKey(vehicle.getType()).toString())
                    ? vehicle.getUUID() : null;
            players.put(name, new PositionsMessage.Player(name, player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), SharedPositions.dimensionCode(player.level().dimension()), true,
                    vehicle != null, published));
            dirty = true;
        }
        // Those who logged off stay where they were last.
        for (Map.Entry<String, PositionsMessage.Player> entry : players.entrySet()) {
            PositionsMessage.Player player = entry.getValue();
            if (player.online() && !online.contains(entry.getKey())) {
                PositionsMessage.Mount mount = riding.remove(entry.getKey());
                ridden.remove(entry.getKey());
                entry.setValue(new PositionsMessage.Player(player.name(), player.x(), player.y(), player.z(),
                        player.yaw(), player.dimension(), false, mount != null, null, mount));
            }
        }
        List<PositionsMessage.Player> now = new ArrayList<>();
        for (PositionsMessage.Player player : players.values()) if (player.online()) now.add(player);
        byte[] message = PositionsMessage.players(now);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (Network.canSend(player)) Network.send(player, message);
        }
    }

    /**
     * What {@code player} rides, as it would rest under them once they log off; null when they
     * share it with another player (the server leaves it to them), or it cannot be saved.
     */
    private PositionsMessage.@Nullable Mount mountOf(ServerPlayer player, String name) {
        Entity root = player.getRootVehicle();
        for (Entity passenger : root.getIndirectPassengers()) {
            if (passenger instanceof Player other && other != player) return null;
        }
        PositionsMessage.Mount last = riding.get(name);
        String nbt;
        if (last != null && root.getUUID().equals(ridden.get(name)) && runs % MOB_EVERY != 0) {
            nbt = last.nbt();
        } else {
            try {
                nbt = EntityNbt.save(root, root.level()).toString();
            } catch (RuntimeException e) {
                return null;
            }
            if (nbt.getBytes(StandardCharsets.UTF_8).length > PositionsMessage.Mount.MAX_NBT) return null;
            ridden.put(name, root.getUUID());
        }
        Vec3 seat = player.position().subtract(root.position()).yRot((float) Math.toRadians(root.getYRot()));
        return new PositionsMessage.Mount(EntityType.getKey(root.getType()).toString(), nbt, seat.x, seat.y, seat.z,
                root.getYRot(), player.getYRot() - root.getYRot());
    }

    /** Those who logged off, with the mobs: they move no more, every five seconds is enough. */
    private void offline() {
        List<PositionsMessage.Player> gone = new ArrayList<>();
        for (PositionsMessage.Player player : players.values()) if (!player.online()) gone.add(player);
        byte[] message = PositionsMessage.offline(gone);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (Network.canSend(player)) Network.send(player, message);
        }
    }

    private void mobs() {
        Set<UUID> seen = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            int dimension = SharedPositions.dimensionCode(level.dimension());
            for (Entity entity : level.getAllEntities()) {
                if (entity.isRemoved()) continue;
                String type = EntityType.getKey(entity.getType()).toString();
                Integer kind = KINDS.get(type);
                if (kind == null) continue;
                int variant = 0;
                String field = MobKinds.variantField(type);
                if (field != null) variant = number(EntityNbt.save(entity, level), field) & 2047;
                boolean baby = entity instanceof LivingEntity living && living.isBaby();
                UUID id = entity.getUUID();
                seen.add(id);
                // Moved to the end: the mob seen longest ago comes first, and goes first.
                mobs.remove(id);
                mobs.put(id, new PositionsMessage.Mob(id, entity.getX(), entity.getY(), entity.getZ(), kind, dimension,
                        baby, variant));
                dirty = true;
            }
            // Its chunk is loaded and it is not there: dead, despawned, or gone through a portal.
            mobs.values().removeIf(mob -> mob.dimension() == dimension && !seen.contains(mob.id())
                    && level.areEntitiesLoaded(chunk(mob.x(), mob.z())));
        }
        while (mobs.size() > MAX_MOBS) mobs.remove(mobs.keySet().iterator().next());

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!Network.canSend(player)) continue;
            int dimension = SharedPositions.dimensionCode(player.level().dimension());
            PositionsMessage.Mobs around = new PositionsMessage.Mobs(new ArrayList<>(), dimension,
                    player.getX(), player.getZ(), MOB_RANGE);
            for (PositionsMessage.Mob mob : mobs.values()) {
                if (around.covers(mob.dimension(), mob.x(), mob.z())) around.mobs().add(mob);
            }
            Network.send(player, PositionsMessage.mobs(around));
        }
    }

    // --- The file ------------------------------------------------------------------------

    private Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE);
    }

    private void load() {
        loaded = true;
        Path file = file();
        if (!Files.isRegularFile(file)) return;
        try {
            PositionsMessage.Saved saved = PositionsMessage.readSaved(Files.readAllBytes(file));
            if (saved == null) return;
            for (PositionsMessage.Player player : saved.players()) {
                players.put(player.name(), new PositionsMessage.Player(player.name(), player.x(), player.y(), player.z(),
                        player.yaw(), player.dimension(), false, player.mount() != null, null, player.mount()));
            }
            for (PositionsMessage.Mob mob : saved.mobs()) mobs.put(mob.id(), mob);
        } catch (IOException e) {
            LOGGER.warn("Could not read the shared positions of this world", e);
        }
    }

    /** Writes the file, when something changed; also when the world closes. */
    public void save() {
        if (!loaded || !dirty) return;
        dirty = false;
        Path file = file();
        try {
            Path written = file.resolveSibling(FILE + ".tmp");
            // Those still online when the server stops log off on what they ride.
            List<PositionsMessage.Player> kept = new ArrayList<>();
            for (PositionsMessage.Player player : players.values()) {
                PositionsMessage.Mount mount = player.online() ? riding.get(player.name()) : player.mount();
                kept.add(new PositionsMessage.Player(player.name(), player.x(), player.y(), player.z(), player.yaw(),
                        player.dimension(), player.online(), mount != null, null, mount));
            }
            Files.write(written, PositionsMessage.saved(kept, new ArrayList<>(mobs.values())));
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not save the shared positions of this world", e);
        }
    }

    // ChunkPos became a record in 26.1, its packed form a method of its own.
    private static long chunk(double x, double z) {
        ChunkPos pos = new ChunkPos((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        //? if >=26.1 {
        /*return pos.pack();
        *///?} else {
        return pos.toLong();
        //?}
    }

    private static int number(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getIntOr(key, 0);
        //?} else {
        /*return tag.getInt(key);
        *///?}
    }
}
