package fr.clixmods.livinghorizon.track;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Where a player who logged off can walk, around the place where it stands: every spot a
 * player could stand on and reach on foot, as a player would - a step up of one block, a
 * drop of two at most, no corner cut, nothing walked through. Worked out on the client from
 * the blocks it has, for the figure alone: nothing is sent anywhere.
 *
 * <p>The blocks are read through {@link Ground}, so that this is tested without a world.
 */
public final class Walkway {
    /** What a spot is like: the height a player stands at with its feet in that block, or NaN. */
    public interface Ground {
        double stand(int x, int y, int z);
    }

    /** A spot to stand on: its block, and the height of the feet in it. */
    public record Step(int x, int y, int z, double height) {
        double distanceSqr(int ox, int oz) {
            double dx = x - ox, dz = z - oz;
            return dx * dx + dz * dz;
        }
    }

    /** Highest step up, in blocks: a jump. */
    static final double CLIMB = 1.25;
    /** Lowest drop, in blocks. */
    static final double DROP = 2.2;

    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private final Ground ground;
    private final Map<Long, Double> heights = new HashMap<>();
    private final Map<Long, Step> steps = new HashMap<>();
    private final Map<Long, Long> previous = new HashMap<>();

    private Walkway(Ground ground) {
        this.ground = ground;
    }

    /**
     * Every spot reachable from the one with its feet in block ({@code x}, {@code y}, {@code z}),
     * at most {@code radius} blocks across from ({@code cx}, {@code cz}) and {@code budget} spots
     * in all, nearest first. Null when no one can stand there.
     */
    public static Walkway around(Ground ground, int x, int y, int z, int cx, int cz, int radius, int budget) {
        Walkway walkway = new Walkway(ground);
        Step start = walkway.step(x, y, z);
        if (start == null) {
            // Standing a little inside a block or above it: the spot next to its feet.
            start = walkway.step(x, y + 1, z);
            if (start == null) start = walkway.step(x, y - 1, z);
        }
        if (start == null) return null;
        walkway.spread(start, cx, cz, radius, budget);
        return walkway;
    }

    /** The spots reached, nearest first. */
    public List<Step> spots() {
        return new ArrayList<>(steps.values());
    }

    /** Whether a player can stand with its feet in this block, and reach it from the start. */
    public Step reached(int x, int y, int z) {
        return steps.get(key(x, y, z));
    }

    /** The way from the start to a spot reached, the start left out; empty if it is the start. */
    public List<Step> pathTo(Step target) {
        Deque<Step> path = new ArrayDeque<>();
        long at = key(target.x(), target.y(), target.z());
        while (previous.containsKey(at)) {
            path.addFirst(steps.get(at));
            at = previous.get(at);
        }
        return new ArrayList<>(path);
    }

    private void spread(Step start, int cx, int cz, int radius, int budget) {
        record Open(Step step, double cost) {
        }
        Map<Long, Double> costs = new HashMap<>();
        PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.cost(), b.cost()));
        long startKey = key(start.x(), start.y(), start.z());
        costs.put(startKey, 0.0);
        open.add(new Open(start, 0));
        double limit = (radius + 0.5) * (radius + 0.5);
        while (!open.isEmpty() && steps.size() < budget) {
            Open next = open.poll();
            Step at = next.step();
            long atKey = key(at.x(), at.y(), at.z());
            if (steps.containsKey(atKey)) continue;
            steps.put(atKey, at);
            for (int[] d : DIRECTIONS) {
                Step to = neighbour(at, d[0], d[1]);
                if (to == null || to.distanceSqr(cx, cz) > limit) continue;
                long toKey = key(to.x(), to.y(), to.z());
                if (steps.containsKey(toKey)) continue;
                double cost = next.cost() + (d[0] != 0 && d[1] != 0 ? 1.414 : 1.0)
                        + Math.max(0, to.height() - at.height()) * 0.5;
                Double known = costs.get(toKey);
                if (known != null && known <= cost) continue;
                costs.put(toKey, cost);
                previous.put(toKey, atKey);
                open.add(new Open(to, cost));
            }
        }
        // A spot reached last may have been given a parent it was never reached through.
        previous.keySet().retainAll(steps.keySet());
    }

    /** The spot one block over, a step up, level or down; for a diagonal, both sides must be open. */
    private Step neighbour(Step from, int dx, int dz) {
        Step to = over(from, from.x() + dx, from.z() + dz);
        if (to == null || dx == 0 || dz == 0) return to;
        Step side = over(from, from.x() + dx, from.z());
        Step other = over(from, from.x(), from.z() + dz);
        if (side == null || other == null || side.y() != to.y() || other.y() != to.y()) return null;
        return to;
    }

    private Step over(Step from, int x, int z) {
        for (int dy : new int[]{0, 1, -1, -2}) {
            Step to = step(x, from.y() + dy, z);
            if (to == null) continue;
            double rise = to.height() - from.height();
            if (rise > CLIMB || -rise > DROP) continue;
            // Going up, the head must clear the block above where it stands now.
            if (rise > 0.6 && step(from.x(), to.y(), from.z()) == null && Double.isNaN(clear(from, to))) continue;
            return to;
        }
        return null;
    }

    /** Room above the feet to jump: the spot over its own block, if any; NaN when there is none. */
    private double clear(Step from, Step to) {
        double h = height(from.x(), from.y() + 1, from.z());
        return Double.isNaN(h) && height(from.x(), from.y(), from.z()) == from.height() ? 0 : h;
    }

    private Step step(int x, int y, int z) {
        double h = height(x, y, z);
        return Double.isNaN(h) ? null : new Step(x, y, z, h);
    }

    private double height(int x, int y, int z) {
        return heights.computeIfAbsent(key(x, y, z), k -> ground.stand(x, y, z));
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** The spots in a random order, for picking one. */
    static List<Step> shuffled(List<Step> spots, java.util.Random random) {
        List<Step> copy = new ArrayList<>(spots);
        Collections.shuffle(copy, random);
        return copy;
    }
}
