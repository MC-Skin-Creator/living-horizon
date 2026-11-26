package fr.clixmods.livinghorizon.compat;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.track.FarPlayer;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.Profiles;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The players the mod knows past the render distance, on the maps of other mods: those online
 * that a server with the mod shares ({@link FarPlayer.Source#SHARED}), and those who logged off
 * ({@link RestingPlayers}). Worked out once a second and handed to each map that is there:
 * Xaero's Minimap and World Map ({@link XaeroMaps}, online players only: they draw a tracked
 * player from the game's list of players online) and JourneyMap ({@link JourneyMapLink}).
 * Players the game itself loaded are left to the maps' own radar.
 */
public final class MapMarkers {
    /** Ticks between two lists. */
    private static final int EVERY = 20;

    /**
     * A player on a map: who, where, in which dimension, and the list entry their head is
     * drawn from (the game's for a player online, one of this mod's for a player who logged off).
     */
    public record Marker(UUID id, String name, double x, double y, double z, ResourceKey<Level> dimension,
                         PlayerInfo info, boolean offline) {
    }

    private static List<Marker> markers = List.of();
    /** An entry per player who logged off, kept while their skin stays the same. */
    private static final Map<String, Offline> OFFLINE = new HashMap<>();
    private static int clock;

    private record Offline(@Nullable String textures, PlayerInfo info) {
    }

    private MapMarkers() {
    }

    /** The players on the maps now. */
    public static List<Marker> markers() {
        return markers;
    }

    /** Once a tick, on the game's thread. */
    public static void tick(Minecraft minecraft) {
        if (++clock % EVERY != 0) return;
        boolean xaero = XaeroMaps.present(), journeyMap = JourneyMapLink.present();
        if (!xaero && !journeyMap) return;
        markers = collect(minecraft);
        if (xaero) XaeroMaps.update();
        if (journeyMap) JourneyMapLink.update(markers);
    }

    private static List<Marker> collect(Minecraft minecraft) {
        FarConfig config = FarConfig.get();
        ClientPacketListener connection = minecraft.getConnection();
        if (!config.enabled || minecraft.level == null || connection == null) return List.of();
        ResourceKey<Level> dimension = minecraft.level.dimension();
        List<Marker> found = new ArrayList<>();
        FarPlayerTracker tracker = FarPlayerTracker.get();
        if (config.mapPlayers) {
            for (FarPlayer player : tracker.players()) {
                // Loaded by the game: the map's own radar shows them.
                if (player.source() != FarPlayer.Source.SHARED || !player.placed() || player.vanished()) continue;
                PlayerInfo info = connection.getPlayerInfo(player.id());
                if (info == null) continue;
                found.add(new Marker(player.id(), Profiles.name(info.getProfile()), player.x(), player.y(), player.z(),
                        dimension, info, false));
            }
        }
        if (config.mapOfflinePlayers && config.offlinePlayers) {
            String here = SharedPositions.dimensionId(dimension);
            for (RestingPlayers.Spot spot : tracker.resting().spots()) {
                if (!spot.dimension().equals(here) || connection.getPlayerInfo(spot.name()) != null) continue;
                PlayerInfo info = offline(spot);
                found.add(new Marker(Profiles.id(info.getProfile()), spot.name(), spot.x(), spot.y(), spot.z(),
                        dimension, info, true));
            }
            if (OFFLINE.size() > 1024) OFFLINE.clear();
        }
        return List.copyOf(found);
    }

    private static PlayerInfo offline(RestingPlayers.Spot spot) {
        String key = spot.name().toLowerCase(Locale.ROOT);
        Offline known = OFFLINE.get(key);
        if (known == null || !Objects.equals(known.textures(), spot.textures())) {
            GameProfile profile = spot.profile();
            known = new Offline(spot.textures(), new PlayerInfo(profile, false));
            OFFLINE.put(key, known);
        }
        return known.info();
    }
}
