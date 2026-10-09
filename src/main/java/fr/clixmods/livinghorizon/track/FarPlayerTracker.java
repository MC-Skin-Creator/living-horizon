package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.ambient.Ambience;
import fr.clixmods.livinghorizon.compat.LodWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.Scoreboard;
//? if >=1.21.6
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
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
 * receives: the entities the server sends, what a server running the mod shares
 * ({@link ServerFeed}), the data pack scores, the locator bar.
 *
 * <p>Nothing is sent to the server. When it runs the mod or the companion data pack, every
 * position is exact, the mod's first; otherwise the locator bar is all there is, and a
 * server that turns it off ({@code /gamerule locatorBar false}) leaves only the memory of
 * where each player was last seen.
 */
public final class FarPlayerTracker {
    private static final FarPlayerTracker INSTANCE = new FarPlayerTracker();

    private final Map<UUID, FarPlayer> players = new HashMap<>();
    private final SharedPositions shared = new SharedPositions();
    private final ServerFeed feed = new ServerFeed();
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

    /** What a server running the mod shares; whether it does right now is {@link ServerFeed#active}. */
    public ServerFeed feed() {
        return feed;
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
            feed.clear();
            players.clear();
            level = null;
            if (server != null) {
                LodWorld.suspend();
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
            LodWorld.clear();
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
        String dimensionId = current.dimension().identifier().toString();
        feed.tick();
        boolean fromMod = feed.active();

        Set<UUID> live = new HashSet<>();
        for (AbstractClientPlayer player : current.players()) {
            if (player == self || player.isRemoved()) continue;
            players.computeIfAbsent(player.getUUID(), FarPlayer::new).observeLive(player, dimension);
            live.add(player.getUUID());
        }

        // The locator bar came in 1.21.6; before, players are placed by the data pack alone.
        //? if >=1.21.6 {
        Map<UUID, TrackedWaypoint> waypoints = new HashMap<>();
        connection.getWaypointManager().forEachWaypoint(self,
                waypoint -> waypoint.id().left().ifPresent(id -> waypoints.put(id, waypoint)));
        //?} else
        /*Map<UUID, Object> waypoints = Map.of();*/

        // The server stops sending a player at the edge of its view distance. Lost well
        // inside it, the player was removed rather than left behind.
        double vanishRadius = Math.max(16, minecraft.options.getEffectiveRenderDistance() * 16 - 40);

        Scoreboard scoreboard = current.getScoreboard();
        shared.update(scoreboard);

        Set<String> online = new HashSet<>();
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            UUID id = Profiles.id(info.getProfile());
            String name = Profiles.name(info.getProfile());
            online.add(name.toLowerCase(Locale.ROOT));
            resting.wake(name);
            if (id.equals(self.getUUID())) continue;

            FarPlayer player = players.get(id);
            if (player != null) player.profile = info.getProfile();
            if (live.contains(id)) continue;

            // The mod's word first; the pack's when the mod says nothing of this player.
            SharedPositions.Report report = fromMod ? feed.player(name, dimensionId, dimension) : null;
            if (report == null) report = shared.read(scoreboard, name);
            var waypoint = waypoints.get(id);
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

        // The mod and the data pack keep the last position of everyone who ever played here.
        if ((fromMod || shared.active()) && ++packScan >= 20) {
            packScan = 0;
            for (String name : fromMod ? feed.resting() : shared.holders(scoreboard)) {
                if (online.contains(name.toLowerCase(Locale.ROOT))) continue;
                SharedPositions.Report report = fromMod
                        ? feed.resting(name, dimensionId, dimension) : shared.read(scoreboard, name);
                if (report != null) resting.restFromPack(name, report);
            }
        }
        resting.tick();
        ambience.tick(current, self, config);
        mobs.tick(current, self, minecraft.options.getEffectiveRenderDistance() * 16, config,
                fromMod ? feed::mobs : shared.active() ? () -> shared.sharedMobs(scoreboard) : null);
    }

    /** Which server this is, as a name for the file of who rests where. */
    private static String serverKey(Minecraft minecraft) {
        ServerData data = minecraft.getCurrentServer();
        if (data != null) return data.ip;
        IntegratedServer local = minecraft.getSingleplayerServer();
        // The folder, not the name: two worlds may be called the same.
        if (local != null) {
            Path folder = local.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
            return "local-" + (folder != null ? folder : local.getWorldData().getLevelName());
        }
        return "unknown";
    }
}
