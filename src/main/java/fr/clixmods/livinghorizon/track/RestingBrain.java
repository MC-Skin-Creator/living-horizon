package fr.clixmods.livinghorizon.track;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The life of the players who logged off, lived once, on the server, so that every game with the
 * mod draws the same thing ({@link PositionsMessage#RESTING}). Each one is a virtual actor: no
 * entity, no chunk loaded for it. It stands, sits, strolls where a player could walk
 * ({@link Walkway}) and lies in a free bed at night, only where the blocks are loaded; anywhere
 * else it stays where it is. A game that is not sent this simulates them itself ({@link RestingPuppet}).
 *
 * <p>No type of the client's: runs on a dedicated server, and in a unit test over a fake {@link Footing.Source}.
 */
final class RestingBrain {
    static final int IDLE = 0, SIT = 1, WALK = 2, BED = 3;
    /** Players farther than this from every viewer are not simulated, in blocks. */
    static final double RANGE = 160;
    /** How far from where it stood when first simulated an actor strolls, in blocks. */
    static final int RADIUS = 16;
    private static final int BUDGET = 1200;
    /** Surveys started or grown per step, all actors together: keeps the cost of a step bounded. */
    static final int SEARCHES = 2;
    private static final int GROW = 600;
    /** Ticks between two reads of the ground an actor walks on, once it has read it. */
    private static final int RESURVEY = 2400;
    private static final double STROLL = 0.11;
    private static final double BED_HEIGHT = 0.6875;

    /** The world, by dimension id: its blocks, never loading one, and whether it is night there. */
    interface World {
        Footing.@Nullable Source blocks(String dimension);

        boolean night(String dimension);
    }

    /** Where a viewer is: only the actors near one are simulated. */
    record Eye(String dimension, double x, double z) {
    }

    private static final class Actor {
        final String name;
        final String dimension;
        final Random random;
        double x, y, z;
        float yaw;
        int activity = IDLE;
        int timer = 40;
        Walkway.@Nullable Survey survey;
        int surveyAge;
        @Nullable List<Walkway.Step> path;
        int index;
        @Nullable BlockPos bed;
        @Nullable double[] exit;
        final double homeX, homeZ;

        Actor(PositionsMessage.Player player, Random random) {
            this.name = player.name();
            this.dimension = player.dimension();
            this.random = random;
            this.x = homeX = player.x();
            this.y = player.y();
            this.z = homeZ = player.z();
            this.yaw = player.yaw();
            this.timer = 20 + random.nextInt(60);
        }
    }

    private final World world;
    private final long seed;
    private final int maxActors;
    private final Map<String, Actor> actors = new LinkedHashMap<>();
    /** Beds taken, as {@code dimension|position}: never two in one. */
    private final Set<String> taken = new HashSet<>();
    private int turn;

    RestingBrain(World world, long seed, int maxActors) {
        this.world = world;
        this.seed = seed;
        this.maxActors = maxActors;
    }

    /**
     * One step of {@code ticks} ticks. {@code offline} are the players who logged off the server
     * shares (those riding something are left to the games); the actors are chosen among those
     * within {@link #RANGE} of an eye, the nearest first, at most the limit.
     * Returns what each actor does now, by name.
     */
    Map<String, PositionsMessage.Actor> step(int ticks, List<PositionsMessage.Player> offline, List<Eye> eyes) {
        Map<String, PositionsMessage.Player> wanted = choose(offline, eyes);
        for (String name : new ArrayList<>(actors.keySet())) {
            if (!wanted.containsKey(name)) release(actors.remove(name));
        }
        for (PositionsMessage.Player player : wanted.values()) {
            Actor known = actors.get(player.name());
            // Logged off again somewhere else: a new life from there.
            if (known != null && !known.dimension.equals(player.dimension())) {
                release(actors.remove(player.name()));
                known = null;
            }
            if (known == null) {
                actors.put(player.name(), new Actor(player, new Random(seed ^ player.name().hashCode() * 0x9E3779B97F4A7C15L)));
            }
        }
        int[] searches = {SEARCHES};
        List<Actor> order = new ArrayList<>(actors.values());
        int first = order.isEmpty() ? 0 : Math.floorMod(turn++, order.size());
        for (int i = 0; i < order.size(); i++) live(order.get((first + i) % order.size()), ticks, searches);
        Map<String, PositionsMessage.Actor> now = new HashMap<>();
        for (Actor actor : actors.values()) {
            BlockPos head = actor.bed;
            boolean bed = actor.activity == BED && head != null;
            now.put(actor.name, new PositionsMessage.Actor(actor.name, actor.x, actor.y, actor.z, actor.yaw,
                    actor.dimension, actor.activity, bed ? head.getX() : 0, bed ? head.getY() : 0, bed ? head.getZ() : 0));
        }
        return now;
    }

    private Map<String, PositionsMessage.Player> choose(List<PositionsMessage.Player> offline, List<Eye> eyes) {
        record Near(PositionsMessage.Player player, double distance) {
        }
        List<Near> near = new ArrayList<>();
        for (PositionsMessage.Player player : offline) {
            if (player.online() || player.riding()) continue;
            double best = Double.MAX_VALUE;
            for (Eye eye : eyes) {
                if (!eye.dimension().equals(player.dimension())) continue;
                best = Math.min(best, Math.hypot(player.x() - eye.x(), player.z() - eye.z()));
            }
            if (best <= RANGE) near.add(new Near(player, best));
        }
        near.sort((a, b) -> Double.compare(a.distance(), b.distance()));
        Map<String, PositionsMessage.Player> wanted = new LinkedHashMap<>();
        for (Near each : near) {
            if (wanted.size() >= maxActors) break;
            wanted.put(each.player().name(), each.player());
        }
        // Those already living stay while they are in range, before newcomers take their place.
        return wanted;
    }

    private void release(@Nullable Actor actor) {
        if (actor != null && actor.bed != null) taken.remove(key(actor.dimension, actor.bed));
    }

    private static String key(String dimension, BlockPos at) {
        return dimension + "|" + at.asLong();
    }

    private void live(Actor a, int ticks, int[] searches) {
        Footing.Source blocks = world.blocks(a.dimension);
        // Blocks not loaded: it stays as it is.
        if (blocks == null || !blocks.loaded(BlockPos.containing(a.x, a.y, a.z))) {
            a.survey = null;
            return;
        }
        boolean night = world.night(a.dimension);
        if (a.activity == BED) {
            BlockPos head = a.bed;
            if (!night || head == null || !(blocks.state(head).getBlock() instanceof BedBlock b0)
                    || blocks.state(head).getValue(BedBlock.OCCUPIED)) leaveBed(a);
            return;
        }
        Walkway.Survey map = a.survey;
        a.surveyAge += ticks;
        if (map == null || a.surveyAge > RESURVEY && a.activity != WALK) {
            if (searches[0] <= 0) return;
            searches[0]--;
            map = new Walkway((x, y, z) -> Footing.stand(blocks, x, y, z))
                    .survey((int) Math.floor(a.x), (int) Math.floor(a.y), (int) Math.floor(a.z),
                            (int) Math.floor(a.homeX), (int) Math.floor(a.homeZ), RADIUS, BUDGET);
            a.survey = map;
            a.surveyAge = 0;
            // Nowhere to stand: it stays, and looks again a while later.
            if (map == null) {
                a.surveyAge = RESURVEY - 600;
                return;
            }
        }
        if (!map.done()) {
            if (searches[0] <= 0) return;
            searches[0]--;
            map.grow(GROW);
            if (!map.done()) return;
        }
        if (a.activity == WALK) {
            if (walk(a, blocks, ticks)) arrive(a, blocks);
            return;
        }
        a.timer -= ticks;
        if (a.timer > 0) return;
        decide(a, blocks, map, night);
    }

    private void decide(Actor a, Footing.Source blocks, Walkway.Survey map, boolean night) {
        if (night) {
            if (!towardsBed(a, blocks, map)) begin(a, IDLE, 200 + a.random.nextInt(200));
            return;
        }
        int roll = a.random.nextInt(100);
        if (roll < 35) {
            begin(a, IDLE, 60 + a.random.nextInt(100));
        } else if (roll < 60) {
            begin(a, SIT, 200 + a.random.nextInt(300));
        } else if (!stroll(a, map)) {
            begin(a, IDLE, 60 + a.random.nextInt(60));
        }
    }

    private static void begin(Actor a, int activity, int ticks) {
        a.activity = activity;
        a.timer = ticks;
        a.path = null;
    }

    private boolean stroll(Actor a, Walkway.Survey map) {
        List<Walkway.Step> spots = map.spots();
        for (int tries = 0; tries < 12 && !spots.isEmpty(); tries++) {
            Walkway.Step step = spots.get(a.random.nextInt(spots.size()));
            if (step.distanceSqr((int) Math.floor(a.x), (int) Math.floor(a.z)) < 9) continue;
            return route(a, map, step);
        }
        return false;
    }

    private boolean route(Actor a, Walkway.Survey map, Walkway.Step target) {
        List<Walkway.Step> way = map.pathTo(target);
        if (way == null || way.isEmpty()) return false;
        a.path = way;
        a.index = 0;
        a.activity = WALK;
        a.timer = 0;
        return true;
    }

    private boolean towardsBed(Actor a, Footing.Source blocks, Walkway.Survey map) {
        Walkway.Step best = null;
        BlockPos bestHead = null;
        double bestDistance = Double.MAX_VALUE;
        for (Walkway.Step spot : map.spots()) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = new BlockPos(spot.x() + side.getStepX(), spot.y(), spot.z() + side.getStepZ());
                BlockState state = blocks.state(next);
                if (!(state.getBlock() instanceof BedBlock)) continue;
                BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? next
                        : next.relative(state.getValue(BedBlock.FACING));
                if (taken.contains(key(a.dimension, head)) || !free(blocks.state(head))) continue;
                double distance = spot.distanceSqr((int) Math.floor(a.x), (int) Math.floor(a.z));
                if (distance < bestDistance) {
                    best = spot;
                    bestHead = head;
                    bestDistance = distance;
                }
            }
        }
        if (best == null) return false;
        taken.add(key(a.dimension, bestHead));
        a.bed = bestHead;
        if (bestDistance < 1e-6) {
            arrive(a, blocks);
            return true;
        }
        if (!route(a, map, best)) {
            taken.remove(key(a.dimension, bestHead));
            a.bed = null;
            return false;
        }
        return true;
    }

    private static boolean free(BlockState state) {
        return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD
                && !state.getValue(BedBlock.OCCUPIED);
    }

    /** One step along the way, {@code ticks} ticks of a stroll; true once there. */
    private boolean walk(Actor a, Footing.Source blocks, int ticks) {
        List<Walkway.Step> way = a.path;
        if (way == null || a.index >= way.size()) return true;
        double reach = STROLL * ticks;
        while (reach > 1e-6 && a.index < way.size()) {
            Walkway.Step next = way.get(a.index);
            double tx = next.x() + 0.5, tz = next.z() + 0.5;
            double dx = tx - a.x, dz = tz - a.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance <= reach) {
                if (Double.isNaN(Footing.stand(blocks, next.x(), next.y(), next.z()))) {
                    // Built on or dug out since it was read: read again.
                    a.path = null;
                    a.survey = null;
                    a.activity = IDLE;
                    a.timer = 20;
                    if (a.bed != null) leaveBed(a);
                    return false;
                }
                a.x = tx;
                a.y = next.height();
                a.z = tz;
                reach -= distance;
                a.index++;
            } else {
                a.x += dx / distance * reach;
                a.z += dz / distance * reach;
                // Up or down along the step, evenly: its end is the next spot's height.
                a.y += (next.height() - a.y) * Math.min(1.0, reach / distance);
                reach = 0;
            }
            if (distance > 1e-3) a.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        }
        return a.index >= way.size();
    }

    private void arrive(Actor a, Footing.Source blocks) {
        a.path = null;
        BlockPos head = a.bed;
        if (head != null) {
            if (free(blocks.state(head))) {
                a.exit = new double[] {a.x, a.y, a.z};
                a.x = head.getX() + 0.5;
                a.y = head.getY() + BED_HEIGHT;
                a.z = head.getZ() + 0.5;
                a.activity = BED;
                return;
            }
            leaveBed(a);
        }
        begin(a, IDLE, 40 + a.random.nextInt(80));
    }

    private void leaveBed(Actor a) {
        double[] exit = a.exit;
        if (exit != null) {
            a.x = exit[0];
            a.y = exit[1];
            a.z = exit[2];
        }
        a.exit = null;
        release(a);
        a.bed = null;
        a.survey = null;
        begin(a, IDLE, 40);
    }

    /** What each actor is doing now, for the tests. */
    int count() {
        return actors.size();
    }
}
