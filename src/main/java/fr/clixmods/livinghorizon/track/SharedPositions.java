package fr.clixmods.livinghorizon.track;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The positions the companion data pack ({@code datapack/} at the root of this
 * repository) publishes through the scoreboard.
 *
 * <p>An objective shown in a display slot is sent to every client at any distance; the
 * pack shows its four in the sidebars of unused team colours, so nobody sees them and
 * every client receives them. The layout is documented in the pack's
 * {@code load.mcfunction}, and {@link #VERSION} is the one this class reads.
 */
public final class SharedPositions {
    static final int VERSION = 1;
    /** Ticks without the pack's clock moving before its scores are no longer trusted. */
    private static final int STALE_TICKS = 60;

    public record Report(double x, double y, double z, float yaw, int dimension, boolean riding) {
    }

    private static final ScoreHolder CLOCK = ScoreHolder.forNameOnly("#clock");
    private static final ScoreHolder LAYOUT = ScoreHolder.forNameOnly("#version");

    private int lastClock;
    private int ticksSinceClock = STALE_TICKS;
    private boolean active;

    /** Once per tick: whether the pack is running on this server right now. */
    boolean update(Scoreboard scoreboard) {
        Objective meta = scoreboard.getObjective("lh.m");
        ReadOnlyScoreInfo clock = meta == null ? null : scoreboard.getPlayerScoreInfo(CLOCK, meta);
        ReadOnlyScoreInfo layout = meta == null ? null : scoreboard.getPlayerScoreInfo(LAYOUT, meta);
        if (clock == null || layout == null || layout.value() != VERSION) {
            ticksSinceClock = STALE_TICKS;
            active = false;
            return false;
        }
        if (clock.value() != lastClock) {
            lastClock = clock.value();
            ticksSinceClock = 0;
        } else if (ticksSinceClock < STALE_TICKS) {
            ticksSinceClock++;
        }
        active = ticksSinceClock < STALE_TICKS;
        return active;
    }

    public boolean active() {
        return active;
    }

    /** What the pack last said about one player, by scoreboard name. Null when nothing. */
    @Nullable Report read(Scoreboard scoreboard, String name) {
        if (!active) return null;
        Objective ox = scoreboard.getObjective("lh.x");
        Objective oy = scoreboard.getObjective("lh.y");
        Objective oz = scoreboard.getObjective("lh.z");
        Objective om = scoreboard.getObjective("lh.m");
        if (ox == null || oy == null || oz == null || om == null) return null;
        ScoreHolder holder = ScoreHolder.forNameOnly(name);
        ReadOnlyScoreInfo x = scoreboard.getPlayerScoreInfo(holder, ox);
        ReadOnlyScoreInfo y = scoreboard.getPlayerScoreInfo(holder, oy);
        ReadOnlyScoreInfo z = scoreboard.getPlayerScoreInfo(holder, oz);
        ReadOnlyScoreInfo m = scoreboard.getPlayerScoreInfo(holder, om);
        if (x == null || y == null || z == null || m == null) return null;
        int meta = m.value();
        return new Report(
                x.value() / 10.0, y.value() / 10.0, z.value() / 10.0,
                meta % 360,
                (meta / 360) % 4,
                meta >= 1440);
    }

    /** A mob the pack publishes, under its UUID. */
    public record MobReport(UUID id, double x, double y, double z, int kind, int dimension, boolean baby, int variant) {
    }

    /** Every player the pack has a position for, online or not. */
    List<String> holders(Scoreboard scoreboard) {
        List<String> names = new ArrayList<>();
        for (String owner : owners(scoreboard)) {
            if (!owner.startsWith("#") && mobId(owner) == null) names.add(owner);
        }
        return names;
    }

    /** Every mob the pack has a position for. Their score holder is their UUID. */
    List<UUID> mobs(Scoreboard scoreboard) {
        List<UUID> ids = new ArrayList<>();
        for (String owner : owners(scoreboard)) {
            UUID id = mobId(owner);
            if (id != null) ids.add(id);
        }
        return ids;
    }

    @Nullable MobReport readMob(Scoreboard scoreboard, UUID id) {
        if (!active) return null;
        Objective ox = scoreboard.getObjective("lh.x");
        Objective oy = scoreboard.getObjective("lh.y");
        Objective oz = scoreboard.getObjective("lh.z");
        Objective om = scoreboard.getObjective("lh.m");
        if (ox == null || oy == null || oz == null || om == null) return null;
        ScoreHolder holder = ScoreHolder.forNameOnly(id.toString());
        ReadOnlyScoreInfo x = scoreboard.getPlayerScoreInfo(holder, ox);
        ReadOnlyScoreInfo y = scoreboard.getPlayerScoreInfo(holder, oy);
        ReadOnlyScoreInfo z = scoreboard.getPlayerScoreInfo(holder, oz);
        ReadOnlyScoreInfo m = scoreboard.getPlayerScoreInfo(holder, om);
        if (x == null || y == null || z == null || m == null) return null;
        int meta = m.value();
        return new MobReport(id, x.value() / 10.0, y.value() / 10.0, z.value() / 10.0,
                meta & 255, (meta >> 8) & 3, ((meta >> 10) & 1) == 1, meta >>> 11);
    }

    private static List<String> owners(Scoreboard scoreboard) {
        Objective ox = scoreboard.getObjective("lh.x");
        if (ox == null) return List.of();
        List<String> owners = new ArrayList<>();
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(ox)) owners.add(entry.owner());
        return owners;
    }

    /** A player name is at most 16 characters; a UUID written out is 36. */
    private static @Nullable UUID mobId(String owner) {
        if (owner.length() != 36) return null;
        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The pack's code for a dimension: 0 overworld, 1 nether, 2 end, 3 anything else. */
    public static int dimensionCode(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) return 0;
        if (dimension == Level.NETHER) return 1;
        if (dimension == Level.END) return 2;
        return 3;
    }
}
