package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.ambient.Ambience;
import fr.clixmods.livinghorizon.compat.VoxyWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every other player on the server, followed once per client tick from what a client
 * receives: the entities the server sends, the data pack scores, the locator bar.
 *
 * <p>Nothing is sent to the server. When it runs the companion data pack, every
 * position is exact; otherwise the locator bar is all there is, and a server that turns
 * it off ({@code /gamerule locatorBar false}) leaves only the memory of where each player
 * was last seen.
 */
public final class FarPlayerTracker {
    private static final FarPlayerTracker INSTANCE = new FarPlayerTracker();

    private final Map<UUID, FarPlayer> players = new HashMap<>();
    private final SharedPositions shared = new SharedPositions();
    private final RestingPlayers resting = new RestingPlayers();
    private final MobMemory mobs = new MobMemory();
    private final Ambience ambience = new Ambience();
    private @Nullable ClientLevel level;
    private @Nullable String server;
    private int packScan;

    public static FarPlayerTracker get() {
        return INSTANCE;
    }

    /** Whether the companion data pack is running on this server. */
    public boolean sharing() {
        return shared.active();
    }

    public Collection<FarPlayer> players() {
        return Collections.unmodifiableCollection(players.values());
    }

    /** Birds and bats in the sky. */
    public Ambience ambience() {
        return ambience;
    }

    /** Mobs met on the way, kept where they were. */
    public MobMemory mobs() {
        return mobs;
    }

    /** Players who logged off and rest where they were. */
    public RestingPlayers resting() {
        return resting;
    }

    public void tick(Minecraft minecraft) {
        ClientLevel current = minecraft.level;
        LocalPlayer self = minecraft.player;
        ClientPacketListener connection = minecraft.getConnection();
        if (current == null || self == null || connection == null) {
            players.clear();
            level = null;
            if (server != null) {
                resting.close();
                mobs.close();
                server = null;
            }
            return;
        }
        if (current != level) {
            // Another dimension or another server: every position known is meaningless.
            players.clear();
            resting.forgetPuppets();
            mobs.forgetPuppets();
            VoxyWorld.clear();
            level = current;
            String joined = serverKey(minecraft);
            if (!joined.equals(server)) {
                resting.open(joined);
                mobs.open(joined);
                server = joined;
            }
        }
        FarConfig config = FarConfig.get();
        int dimension = SharedPositions.dimensionCode(current.dimension());

        Set<UUID> live = new HashSet<>();
        for (AbstractClientPlayer player : current.players()) {
            if (player == self || player.isRemoved()) continue;
            players.computeIfAbsent(player.getUUID(), FarPlayer::new).observeLive(player, dimension);
            live.add(player.getUUID());
        }

        Map<UUID, TrackedWaypoint> waypoints = new HashMap<>();
        connection.getWaypointManager().forEachWaypoint(self,
                waypoint -> waypoint.id().left().ifPresent(id -> waypoints.put(id, waypoint)));

        // The server stops sending a player at the edge of its view distance. Lost well
        // inside it, the player was removed rather than left behind.
        double vanishRadius = Math.max(16, minecraft.options.getEffectiveRenderDistance() * 16 - 40);

        Scoreboard scoreboard = current.getScoreboard();
        shared.update(scoreboard);

        Set<String> online = new HashSet<>();
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            UUID id = info.getProfile().id();
            String name = info.getProfile().name();
            online.add(name.toLowerCase(Locale.ROOT));
            resting.wake(name);
            if (id.equals(self.getUUID())) continue;

            FarPlayer player = players.get(id);
            if (player != null) player.profile = info.getProfile();
            if (live.contains(id)) continue;

            SharedPositions.Report report = shared.read(scoreboard, name);
            TrackedWaypoint waypoint = waypoints.get(id);
            if (player == null) {
                if (waypoint == null && report == null) continue;
                player = new FarPlayer(id);
                player.profile = info.getProfile();
                players.put(id, player);
            }
            player.observeRemote(current, self, info, report, dimension, waypoint, vanishRadius, config);
        }

        // Gone from the tab list: logged off. They rest where they were last known.
        players.values().removeIf(player -> {
            if (connection.getPlayerInfo(player.id()) != null) return false;
            if (player.profile != null && player.knownDimension() >= 0) {
                resting.rest(player.profile, player.knownDimension(), player.x(), player.y(), player.z(), player.yaw());
            }
            return true;
        });

        // The data pack keeps the last position of everyone who ever played here.
        if (shared.active() && ++packScan >= 20) {
            packScan = 0;
            for (String name : shared.holders(scoreboard)) {
                if (online.contains(name.toLowerCase(Locale.ROOT))) continue;
                SharedPositions.Report report = shared.read(scoreboard, name);
                if (report != null) resting.restFromPack(name, report);
            }
        }
        resting.tick(current, config);
        ambience.tick(current, self, config);
        mobs.tick(current, self, minecraft.options.getEffectiveRenderDistance() * 16, config,
                shared.active() ? shared : null, scoreboard);
    }

    /** Which server this is, as a name for the file of who rests where. */
    private static String serverKey(Minecraft minecraft) {
        ServerData data = minecraft.getCurrentServer();
        if (data != null) return data.ip;
        IntegratedServer local = minecraft.getSingleplayerServer();
        if (local != null) return "local-" + local.getWorldData().getLevelName();
        return "unknown";
    }
}
