package fr.clixmods.livinghorizon.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.clixmods.livinghorizon.LivingHorizon;
import fr.clixmods.livinghorizon.track.EntityNbt;
import fr.clixmods.livinghorizon.track.MobKinds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every shared mob the server has seen loaded, kept where it was last while its chunk is
 * unloaded: the server's own memory, which the clients are sent from. A mob is dropped when
 * it dies or is removed for good; it is never dropped for being out of sight.
 *
 * <p>Kept across restarts in {@code <world>/livinghorizon/mobs.json}.
 */
final class MobRegistry {
    /** One mob, as the clients are told of it. Gson writes every field that is not transient. */
    static final class Known {
        UUID id;
        String type;
        String dimension;
        double x, y, z;
        float yaw;
        boolean named;
        /** As the game saves it, less what never shows: see {@link #HIDDEN}. Null until first saved. */
        @Nullable String nbt;
        /** Raised whenever it moves; what a client was last sent is compared to it. */
        transient int moved;
        /** Raised whenever its saved data changes. */
        transient int changed;
        /** The tick its saved data was last taken; 0 when read from the file, so taken again. */
        transient long savedAt;

        Known copy() {
            Known copy = new Known();
            copy.id = id;
            copy.type = type;
            copy.dimension = dimension;
            copy.x = x;
            copy.y = y;
            copy.z = z;
            copy.yaw = yaw;
            copy.named = named;
            copy.nbt = nbt;
            return copy;
        }
    }

    private static final Gson GSON = new GsonBuilder().create();
    /** Bounds the memory and the file; the mobs first met go first. */
    private static final int MAX_KNOWN = 50_000;
    /** How often a loaded mob's saved data is taken again, in ticks. */
    private static final long RESAVE_TICKS = 6000;
    /** Saved data taken per look at the worlds, at most: the rest waits for the next one. */
    private static final int SAVES_PER_LOOK = 256;
    /** Closer than this, in blocks, a mob has not moved. */
    private static final double STILL = 0.25;

    /**
     * What the clients are never sent: memories, trades, attributes, effects and the state of
     * the moment. Large, changing all the time, and no part of how the mob looks.
     */
    private static final Set<String> HIDDEN = Set.of(
            "Brain", "Gossips", "Offers", "Attributes", "attributes", "ActiveEffects", "active_effects",
            "Pos", "Motion", "Rotation", "UUID", "Air", "FallDistance", "fall_distance", "Fire",
            "HurtTime", "HurtByTimestamp", "DeathTime", "PortalCooldown", "OnGround", "Xp",
            "LastRestock", "RestocksToday", "LastGossipDecay", "Leash", "leash", "InLove", "LoveCause",
            "Paper.Origin", "Paper.SpawnReason", "Bukkit.updateLevel", "Spigot.ticksLived");

    private final Map<UUID, Known> known = new LinkedHashMap<>();
    private final Path file;
    /** Held while the file is written: a write in the background and the last one, at stop, never overlap. */
    private final Object writing = new Object();
    private int version;
    private boolean dirty;

    MobRegistry(Path folder) {
        file = folder.resolve("mobs.json");
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            List<Known> read = GSON.fromJson(reader, new TypeToken<List<Known>>() { }.getType());
            if (read != null) {
                for (Known mob : read) {
                    if (mob != null && mob.id != null && mob.type != null && mob.dimension != null) known.put(mob.id, mob);
                }
            }
        } catch (IOException | RuntimeException e) {
            LivingHorizon.LOGGER.warn("Could not read {}", file, e);
        }
    }

    Collection<Known> all() {
        return known.values();
    }

    int size() {
        return known.size();
    }

    /** A look at every loaded mob of every world: new ones learnt, moved ones moved. */
    void look(MinecraftServer server, long tick) {
        int saves = SAVES_PER_LOOK;
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().identifier().toString();
            for (Entity entity : level.getAllEntities()) {
                if (entity.isRemoved() || !MobKinds.shared(entity)) continue;
                Known mob = see(entity, dimension);
                if (mob.nbt == null || (tick - mob.savedAt >= RESAVE_TICKS && saves > 0)) {
                    saves--;
                    save(mob, entity, level, tick);
                }
            }
        }
        while (known.size() > MAX_KNOWN) known.remove(known.keySet().iterator().next());
    }

    /** An entity leaves a world: kept where it was if it was only unloaded, dropped if it is gone for good. */
    void left(Entity entity, ServerLevel level) {
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason != null && reason.shouldDestroy()) {
            if (known.remove(entity.getUUID()) != null) dirty = true;
        } else if (reason != Entity.RemovalReason.CHANGED_DIMENSION && known.containsKey(entity.getUUID())) {
            see(entity, level.dimension().identifier().toString());
        }
    }

    private Known see(Entity entity, String dimension) {
        Known mob = known.get(entity.getUUID());
        if (mob == null) {
            mob = new Known();
            mob.id = entity.getUUID();
            mob.type = EntityType.getKey(entity.getType()).toString();
            mob.moved = ++version;
            known.put(mob.id, mob);
        }
        double dx = entity.getX() - mob.x, dy = entity.getY() - mob.y, dz = entity.getZ() - mob.z;
        if (!dimension.equals(mob.dimension) || dx * dx + dy * dy + dz * dz > STILL * STILL) {
            mob.dimension = dimension;
            mob.x = entity.getX();
            mob.y = entity.getY();
            mob.z = entity.getZ();
            mob.yaw = entity.getYRot();
            mob.moved = ++version;
            dirty = true;
        }
        if (mob.named != entity.hasCustomName()) {
            mob.named = entity.hasCustomName();
            mob.moved = ++version;
            dirty = true;
        }
        return mob;
    }

    private void save(Known mob, Entity entity, ServerLevel level, long tick) {
        mob.savedAt = Math.max(1, tick);
        String nbt;
        try {
            CompoundTag tag = EntityNbt.save(entity, level);
            for (String key : HIDDEN) tag.remove(key);
            nbt = tag.toString();
        } catch (RuntimeException e) {
            return;
        }
        if (!nbt.equals(mob.nbt)) {
            mob.nbt = nbt;
            mob.changed = ++version;
            dirty = true;
        }
    }

    /** Written off the server's thread, from a copy taken on it. */
    void write(boolean now) {
        if (!dirty) return;
        dirty = false;
        List<Known> copy = new ArrayList<>(known.size());
        for (Known mob : known.values()) copy.add(mob.copy());
        Runnable task = () -> {
            synchronized (writing) {
                write(copy);
            }
        };
        if (now) task.run();
        else java.util.concurrent.CompletableFuture.runAsync(task);
    }

    private void write(List<Known> copy) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp)) {
                GSON.toJson(copy, writer);
            }
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            LivingHorizon.LOGGER.warn("Could not write {}", file, e);
        }
    }
}
