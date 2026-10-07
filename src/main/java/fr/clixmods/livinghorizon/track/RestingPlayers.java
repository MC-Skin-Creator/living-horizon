package fr.clixmods.livinghorizon.track;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Avatar;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Players who logged off, left where they were last, asleep or sitting.
 *
 * <p>Remembered per server, across sessions, in
 * {@code config/livinghorizon/resting/<server>.json}: a friend who logged off yesterday
 * is still napping where they stopped when you come back. When the server runs the
 * companion data pack, its scores keep the position of every player who ever played
 * there, so even someone who left before you joined gets a spot; their skin is then
 * fetched from Mojang by name, once.
 */
public final class RestingPlayers {
    /** Where one player rests. Keyed by name: the data pack only knows names. */
    public record Spot(String name, @Nullable UUID id, @Nullable String textures, @Nullable String signature,
                       int dimension, double x, double y, double z, float yaw) {

        Spot moved(int dimension, double x, double y, double z, float yaw) {
            return new Spot(name, id, textures, signature, dimension, x, y, z, yaw);
        }

        Spot dressed(GameProfile profile) {
            Property skin = skinOf(profile);
            return new Spot(name, Profiles.id(profile), skin == null ? textures : Profiles.value(skin),
                    skin == null ? signature : Profiles.signature(skin), dimension, x, y, z, yaw);
        }

        GameProfile profile() {
            UUID uuid = id != null ? id : UUIDUtil.createOfflinePlayerUUID(name);
            if (textures == null) return new GameProfile(uuid, name);
            return Profiles.withSkin(uuid, name, new Property("textures", textures, signature));
        }
    }

    /** A player that never was: their skin comes from a remembered profile, not from the tab list. */
    static final class Puppet extends RemotePlayer {
        private final PlayerInfo info;

        Puppet(ClientLevel level, GameProfile profile) {
            super(level, profile);
            this.info = new PlayerInfo(profile, false);
        }

        @Override
        protected @Nullable PlayerInfo getPlayerInfo() {
            return info;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Spot> spots = new LinkedHashMap<>();
    private final Map<String, Puppet> puppets = new HashMap<>();
    private final Set<String> lookups = new HashSet<>();
    private @Nullable Path file;

    public Collection<Spot> spots() {
        return Collections.unmodifiableCollection(spots.values());
    }

    // --- Lifecycle ---------------------------------------------------------------------

    void open(String server) {
        close();
        file = Platform.configDir()
                .resolve("livinghorizon").resolve("resting").resolve(safe(server) + ".json");
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            for (Spot spot : read(reader)) spots.put(key(spot.name()), spot);
        } catch (IOException | RuntimeException e) {
            LivingHorizonClient.LOGGER.warn("Could not read {}", file, e);
        }
    }

    void close() {
        save();
        spots.clear();
        puppets.clear();
        file = null;
    }

    /** Puppets belong to one level; a new dimension needs new ones. */
    void forgetPuppets() {
        puppets.clear();
    }

    // --- Who rests where ---------------------------------------------------------------

    /** A player who just logged off, at the last place this client knew. */
    void rest(GameProfile profile, int dimension, double x, double y, double z, float yaw) {
        Spot spot = new Spot(Profiles.name(profile), Profiles.id(profile), null, null, dimension, x, y, z, yaw).dressed(profile);
        put(spot);
    }

    /** The data pack's last word on an offline player. */
    void restFromPack(String name, SharedPositions.Report report) {
        Spot known = spots.get(key(name));
        if (known != null && known.dimension() == report.dimension()
                && Math.abs(known.x() - report.x()) < 0.05 && Math.abs(known.y() - report.y()) < 0.05
                && Math.abs(known.z() - report.z()) < 0.05) {
            return;
        }
        Spot spot = known != null
                ? known.moved(report.dimension(), report.x(), report.y(), report.z(), report.yaw())
                : new Spot(name, null, null, null, report.dimension(), report.x(), report.y(), report.z(), report.yaw());
        put(spot);
        if (spot.textures() == null) lookUp(name);
    }

    /** Back online. */
    void wake(String name) {
        if (spots.remove(key(name)) != null) {
            puppets.remove(key(name));
            save();
        }
    }

    private void put(Spot spot) {
        spots.put(key(spot.name()), spot);
        puppets.remove(key(spot.name()));
        save();
    }

    /** Fetches the skin of a player this client never saw, from Mojang, off the game thread. */
    private void lookUp(String name) {
        if (!lookups.add(key(name))) return;
        Minecraft minecraft = Minecraft.getInstance();
        // The game asks Mojang for a profile by name from 1.21.9; before, skins of players
        // never seen stay the default ones.
        //? if >=1.21.9 {
        CompletableFuture
                .supplyAsync(() -> minecraft.services().profileResolver().fetchByName(name), Util.ioPool())
                .exceptionally(e -> Optional.empty())
                .thenAcceptAsync(found -> found.ifPresent(profile -> {
                    Spot spot = spots.get(key(name));
                    if (spot != null && spot.textures() == null) put(spot.dressed(profile));
                }), minecraft);
        //?}
    }

    /** Wakes everyone up: nobody rests anywhere any more, here and in the file. */
    public void forgetAll() {
        spots.clear();
        puppets.clear();
        save();
    }

    // --- Puppets -----------------------------------------------------------------------

    public @Nullable RemotePlayer puppet(ClientLevel level, Spot spot) {
        return puppets.computeIfAbsent(key(spot.name()), k -> {
            Puppet puppet = Puppets.numbered(new Puppet(level, spot.profile()));
            puppet.getEntityData().set(Avatar.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
            puppet.setPos(spot.x(), spot.y(), spot.z());
            puppet.setOldPosAndRot();
            puppet.setYRot(spot.yaw());
            puppet.yBodyRot = puppet.yBodyRotO = spot.yaw();
            puppet.yHeadRot = puppet.yHeadRotO = spot.yaw();
            return puppet;
        });
    }

    /** Keeps the idle animations (breathing, arms) going. */
    void tick() {
        for (Puppet puppet : puppets.values()) {
            puppet.setOldPosAndRot();
            puppet.tickCount++;
        }
    }

    // --- File --------------------------------------------------------------------------

    private void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                write(spots.values(), writer);
            }
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not write {}", file, e);
        }
    }

    static List<Spot> read(Reader reader) {
        List<Spot> read = GSON.fromJson(reader, new TypeToken<List<Spot>>() { }.getType());
        if (read == null) return List.of();
        return read.stream().filter(spot -> spot != null && spot.name() != null).toList();
    }

    static void write(Collection<Spot> spots, Writer writer) {
        GSON.toJson(new ArrayList<>(spots), writer);
    }

    private static @Nullable Property skinOf(GameProfile profile) {
        for (Property property : Profiles.properties(profile).get("textures")) return property;
        return null;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** A server address as a file name. */
    static String safe(String server) {
        String cleaned = server.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }
}
