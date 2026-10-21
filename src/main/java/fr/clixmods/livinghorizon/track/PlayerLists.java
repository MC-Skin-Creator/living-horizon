package fr.clixmods.livinghorizon.track;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * How the server chooses among the players it keeps ({@link PositionPublisher}): those it sends
 * a player first, and those it forgets when it keeps too many. Plain lists, no type of the
 * game's, so that a unit test reads them.
 *
 * <p>Loaded on a dedicated server: nothing here may reach a class of the client's.
 */
final class PlayerLists {
    private PlayerLists() {
    }

    /**
     * The {@code count} players nearest a spot, the nearest first: those in its dimension by
     * distance, then those elsewhere. It keeps {@code count} at a time, rather than sorting them
     * all, so that a long list costs little for each player it is chosen for.
     */
    static List<PositionsMessage.Player> nearest(Collection<PositionsMessage.Player> players, String dimension,
                                                 double x, double z, int count) {
        if (count <= 0) return List.of();
        Comparator<PositionsMessage.Player> nearer = Comparator.comparingDouble(player -> distance(player, dimension, x, z));
        // The farthest of those kept on top, to make way for a nearer one.
        PriorityQueue<PositionsMessage.Player> kept = new PriorityQueue<>(count, nearer.reversed());
        for (PositionsMessage.Player player : players) {
            if (kept.size() < count) {
                kept.add(player);
            } else if (nearer.compare(player, kept.peek()) < 0) {
                kept.poll();
                kept.add(player);
            }
        }
        List<PositionsMessage.Player> chosen = new ArrayList<>(kept);
        chosen.sort(nearer);
        return chosen;
    }

    /** Across the ground; any other dimension is farther than anywhere in this one. */
    private static double distance(PositionsMessage.Player player, String dimension, double x, double z) {
        return player.dimension().equals(dimension) ? Math.hypot(player.x() - x, player.z() - z) : Double.MAX_VALUE;
    }

    /**
     * The players who logged off to forget so that {@code keep} remain: those seen longest ago
     * first, and before them those whose time is not known.
     */
    static List<String> oldest(Collection<PositionsMessage.Player> offline, int keep) {
        int extra = offline.size() - keep;
        if (extra <= 0) return List.of();
        List<PositionsMessage.Player> byAge = new ArrayList<>(offline);
        byAge.sort(Comparator.comparingLong(PositionsMessage.Player::lastSeen));
        List<String> forgotten = new ArrayList<>(extra);
        for (int i = 0; i < extra; i++) forgotten.add(byAge.get(i).name());
        return forgotten;
    }
}
