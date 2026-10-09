package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.share.Protocol;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What a server running the mod tells this client ({@code server/ServerShare}): the same
 * as the data pack shares through the scoreboard, and more - every dimension told apart,
 * and each mob with its saved data, so that one never met is drawn as it is. Kept as the
 * server last said it, until the client leaves that server.
 *
 * <p>The server sends where every player is five times a second, even when it shares no
 * player: that is how this client knows the server is there. Silent for three seconds, it
 * is not trusted any more.
 */
public final class ServerFeed {
    private static final int STALE_TICKS = 60;

    /** Online players, by name in lower case, the way the scoreboard names them. */
    private final Map<String, Protocol.Player> online = new HashMap<>();
    private final Map<String, Protocol.Player> resting = new HashMap<>();
    private final Map<UUID, Protocol.Mob> mobs = new LinkedHashMap<>();
    private int ticksSinceHeard = STALE_TICKS;

    /** One message from the server, on the game's thread. */
    public void accept(byte[] data) {
        Protocol.Message message = Protocol.decode(data);
        if (message instanceof Protocol.Players players) {
            online.clear();
            for (Protocol.Player player : players.players()) online.put(key(player.name()), player);
            ticksSinceHeard = 0;
        } else if (message instanceof Protocol.Resting rest) {
            if (rest.full()) resting.clear();
            for (Protocol.Player player : rest.players()) resting.put(key(player.name()), player);
        } else if (message instanceof Protocol.Mobs update) {
            for (Protocol.Mob mob : update.mobs()) {
                Protocol.Mob known = mobs.get(mob.id());
                // A mob that only moved travels without its saved data: the one it had stays.
                if (mob.nbt() == null && known != null && known.nbt() != null) {
                    mob = new Protocol.Mob(mob.id(), mob.type(), mob.dimension(), mob.x(), mob.y(), mob.z(), mob.yaw(),
                            mob.named(), known.nbt());
                }
                mobs.put(mob.id(), mob);
            }
            for (UUID id : update.forgotten()) mobs.remove(id);
        }
    }

    /** Everything the server said: this client left it. */
    public void clear() {
        online.clear();
        resting.clear();
        mobs.clear();
        ticksSinceHeard = STALE_TICKS;
    }

    void tick() {
        if (ticksSinceHeard < STALE_TICKS) ticksSinceHeard++;
    }

    /** Whether a server running the mod is talking to this client right now. */
    public boolean active() {
        return ticksSinceHeard < STALE_TICKS;
    }

    /** Where the server says an online player is, in the codes of {@link SharedPositions}. Null when it does not say. */
    SharedPositions.@Nullable Report player(String name, String dimension, int dimensionCode) {
        Protocol.Player player = online.get(key(name));
        return player == null ? null : report(player, dimension, dimensionCode);
    }

    /** Players who logged off, by name. */
    Collection<String> resting() {
        List<String> names = new ArrayList<>();
        for (Protocol.Player player : resting.values()) names.add(player.name());
        return names;
    }

    SharedPositions.@Nullable Report resting(String name, String dimension, int dimensionCode) {
        Protocol.Player player = resting.get(key(name));
        return player == null ? null : report(player, dimension, dimensionCode);
    }

    /** Every mob the server sent, in the form the data pack's take. */
    List<SharedPositions.SharedMob> mobs() {
        List<SharedPositions.SharedMob> shared = new ArrayList<>(mobs.size());
        for (Protocol.Mob mob : mobs.values()) {
            // Saved data too large to travel: built from its kind alone, like a mob of the pack.
            String nbt = mob.nbt() != null ? mob.nbt() : MobKinds.nbt(mob.type(), 0, false);
            shared.add(new SharedPositions.SharedMob(mob.id(), mob.type(), mob.dimension(), mob.x(), mob.y(), mob.z(),
                    mob.yaw(), nbt, mob.named(), mob.nbt() != null));
        }
        return shared;
    }

    public int mobCount() {
        return mobs.size();
    }

    /**
     * A position, with the dimension as the code the rest of the tracking compares: the code
     * of this client's dimension when the player is in it, another one when not, even for
     * two dimensions of mods that the codes of the data pack would both call "other".
     */
    private static SharedPositions.Report report(Protocol.Player player, String dimension, int dimensionCode) {
        int code = SharedPositions.dimensionCode(player.dimension());
        if (player.dimension().equals(dimension)) code = dimensionCode;
        else if (code == dimensionCode) code = -1;
        float yaw = ((player.yaw() % 360) + 360) % 360;
        return new SharedPositions.Report(player.x(), player.y(), player.z(), yaw, code, player.riding());
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
