package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Reads the world past the render distance, from whichever mod keeps it - Voxy or Distant
 * Horizons, neither required: the ground and biome of a column - for the birds to find
 * coasts, fields and lakes, and for the mobs to find where they may walk - and the first
 * block along a line, for the line of sight and {@code /livinghorizon lod}.
 *
 * <p>Reads run on a few threads of their own, never on the game's. Columns already read
 * are remembered for a while, since herds and flocks keep asking about the same ground;
 * and many columns go in one task. The mod to read is picked at each read: the first of
 * the {@link LodSource}s installed that this mod knows how to talk to.
 */
public final class LodWorld {
    /** A column read is trusted this long: the terrain far away rarely changes. */
    private static final long COLUMN_NANOS = 120_000_000_000L;
    private static final int COLUMNS_KEPT = 60_000;
    private static final int THREADS = Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 3);

    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final ExecutorService READERS = Executors.newFixedThreadPool(THREADS, runnable -> {
        Thread thread = new Thread(runnable, "Living Horizon LOD reader " + NAMES.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    /** The same work on a single thread, when {@link FarConfig#optParallelRead} is off, to compare. */
    private static final ExecutorService READER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Living Horizon LOD reader");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    /** Tasks given to the readers and not finished yet, for the debug panel. */
    private static final AtomicInteger PENDING = new AtomicInteger();

    /** Reads hold the read side; letting go of what they hold waits for them on the write side. */
    private static final ReadWriteLock READS = new ReentrantReadWriteLock();
    /** Set while there is no world: reads find nothing and hold nothing. */
    private static volatile boolean suspended;

    private static void run(Runnable task) {
        PENDING.incrementAndGet();
        (FarConfig.get().optParallelRead ? READERS : READER).execute(() -> {
            READS.readLock().lock();
            try {
                task.run();
            } finally {
                READS.readLock().unlock();
                PENDING.decrementAndGet();
            }
        });
    }

    /** The source's world for this read; null while suspended. */
    private static @Nullable Object open(LodSource source) throws Throwable {
        return suspended ? null : source.open();
    }

    /** For the debug panel: reads waiting or running, columns remembered, reader threads. */
    public static int pending() {
        return PENDING.get();
    }

    public static int columnsKnown() {
        return COLUMNS.size();
    }

    public static int threads() {
        return FarConfig.get().optParallelRead ? THREADS : 1;
    }

    /** Columns already read: the surface, or {@link #NOTHING}, and when. */
    private record Known(@Nullable Surface surface, long at) {
    }

    private static final Surface NOTHING = new Surface(Integer.MIN_VALUE, "", false, null);
    private static final ConcurrentHashMap<Long, Known> COLUMNS = new ConcurrentHashMap<>();

    private static final LodSource[] SOURCES = {new VoxySource(), new DhSource()};

    private LodWorld() {
    }

    /** Another world: every section held is let go, every column read forgotten. */
    public static void clear() {
        OcclusionQueries.clear();
        release();
        suspended = false;
    }

    /**
     * No world any more, or the game is closing: everything held is let go at once, so that
     * the mods can close their worlds. Reads find nothing until {@link #clear()}.
     */
    public static void suspend() {
        suspended = true;
        release();
    }

    private static void release() {
        READS.writeLock().lock();
        try {
            for (LodSource source : SOURCES) source.clear();
            COLUMNS.clear();
        } finally {
            READS.writeLock().unlock();
        }
    }

    /** The mod this read goes to: the first installed one that is not turned off; null if none. */
    private static @Nullable LodSource source() {
        for (LodSource source : SOURCES) {
            if (source.loaded() && source.available()) return source;
        }
        return null;
    }

    /** For {@code /livinghorizon lod}. */
    public record Status(boolean loaded, String name, @Nullable String failure) {
    }

    /** What the reads of the mod in use found so far, for {@code /livinghorizon lod}. */
    public static String details() {
        LodSource source = source();
        return source == null ? "" : source.details();
    }

    /** The mod being read, or else why none is: nothing installed, or the one installed turned off. */
    public static Status status() {
        LodSource source = source();
        if (source != null) return new Status(true, source.name(), null);
        for (LodSource candidate : SOURCES) {
            if (candidate.loaded()) return new Status(true, candidate.name(), candidate.failure());
        }
        return new Status(false, "", null);
    }

    /**
     * Walks the far world along a direction and says how far the first opaque block is:
     * NaN when there is none within {@code max}, or when no mod has a world loaded.
     */
    public static CompletableFuture<Double> probe(Vec3 eye, Vec3 direction, double max) {
        CompletableFuture<Double> result = new CompletableFuture<>();
        LodSource source = source();
        if (source == null) {
            String failure = status().failure();
            result.completeExceptionally(new IllegalStateException(failure != null ? failure
                    : "neither Voxy nor Distant Horizons is loaded"));
            return result;
        }
        run(() -> {
            try {
                Object world = open(source);
                if (world == null) {
                    result.completeExceptionally(new IllegalStateException(source.name() + " has no world loaded"));
                    return;
                }
                Vec3 d = direction.normalize();
                result.complete(source.firstHit(world, eye.x, eye.y, eye.z, d.x, d.y, d.z, 0.5, max));
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    /** The top of one column of the far world: its height, its biome, whether it is water, its block. */
    public record Surface(int y, String biome, boolean water, @Nullable BlockState block) {
    }

    /**
     * The highest block the far world holds at a column, and its biome, read off the thread;
     * empty where it has nothing, or without Voxy or Distant Horizons.
     */
    public static CompletableFuture<Optional<Surface>> surface(int x, int z) {
        return surfaces(new int[]{x}, new int[]{z}).thenApply(found -> Optional.ofNullable(found[0]));
    }

    /**
     * Many columns at once, in one task: entry {@code i} is the surface of
     * {@code (xs[i], zs[i])}, or null where it has nothing. Columns read recently come
     * from memory without waiting.
     */
    public static CompletableFuture<@Nullable Surface[]> surfaces(int[] xs, int[] zs) {
        Surface[] found = new Surface[xs.length];
        LodSource source = source();
        if (source == null) return CompletableFuture.completedFuture(found);
        long now = System.nanoTime();
        boolean cache = FarConfig.get().optColumnCache;
        boolean missing = false;
        for (int i = 0; i < xs.length; i++) {
            Known known = cache ? COLUMNS.get(key(xs[i], zs[i])) : null;
            if (known != null && now - known.at < COLUMN_NANOS) {
                found[i] = known.surface == NOTHING ? null : known.surface;
            } else {
                missing = true;
            }
        }
        if (!missing) return CompletableFuture.completedFuture(found);
        CompletableFuture<@Nullable Surface[]> result = new CompletableFuture<>();
        run(() -> {
            try {
                Object world = open(source);
                if (world != null) {
                    long at = System.nanoTime();
                    if (COLUMNS.size() > COLUMNS_KEPT) COLUMNS.clear();
                    for (int i = 0; i < xs.length; i++) {
                        long key = key(xs[i], zs[i]);
                        Known known = cache ? COLUMNS.get(key) : null;
                        if (known != null && at - known.at < COLUMN_NANOS) {
                            found[i] = known.surface == NOTHING ? null : known.surface;
                            continue;
                        }
                        Surface surface = source.column(world, xs[i], zs[i]);
                        if (cache) COLUMNS.put(key, new Known(surface == null ? NOTHING : surface, at));
                        found[i] = surface;
                    }
                }
            } catch (Throwable e) {
                source.fail(e);
            }
            result.complete(found);
        });
        return result;
    }

    /**
     * A slice of many columns at once, in one task: entry {@code i} holds the blocks of
     * {@code (xs[i], zs[i])} from {@code bottom} up, {@code height} of them, air where the
     * far world has nothing in that part of the column; null where it has nothing in the
     * column at all. Voxy gives every block; Distant Horizons only the top one.
     */
    public static CompletableFuture<BlockState @Nullable [][]> columns(int[] xs, int[] zs, int bottom, int height) {
        BlockState[][] found = new BlockState[xs.length][];
        LodSource source = source();
        if (source == null) return CompletableFuture.completedFuture(found);
        CompletableFuture<BlockState @Nullable [][]> result = new CompletableFuture<>();
        run(() -> {
            try {
                Object world = open(source);
                if (world != null) {
                    for (int i = 0; i < xs.length; i++) found[i] = source.slice(world, xs[i], zs[i], bottom, height);
                }
            } catch (Throwable e) {
                source.fail(e);
            }
            result.complete(found);
        });
        return result;
    }

    /**
     * For each point, whether the far terrain stands between the eye and it: a block on the
     * line, short of the point by {@code margin}. Null without Voxy, Distant Horizons or their world.
     */
    public static CompletableFuture<boolean @Nullable []> blocked(Vec3 eye, double[] xs, double[] ys, double[] zs,
                                                                  double margin) {
        LodSource source = source();
        if (source == null) return CompletableFuture.completedFuture(null);
        CompletableFuture<boolean @Nullable []> result = new CompletableFuture<>();
        run(() -> {
            boolean[] blocked = null;
            try {
                Object world = open(source);
                if (world != null) {
                    blocked = new boolean[xs.length];
                    for (int i = 0; i < xs.length; i++) {
                        double dx = xs[i] - eye.x, dy = ys[i] - eye.y, dz = zs[i] - eye.z;
                        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (length <= margin + 2) continue;
                        blocked[i] = !Double.isNaN(source.firstHit(world, eye.x, eye.y, eye.z,
                                dx / length, dy / length, dz / length, 2, length - margin));
                    }
                }
            } catch (Throwable e) {
                source.fail(e);
                blocked = null;
            }
            result.complete(blocked);
        });
        return result;
    }

    /**
     * For each point, whether it stands out in the open in the far world: no block that
     * stops light above {@code eyes[i]}, and no water at {@code feet[i]}. Null where the
     * far world knows nothing of the column - a cave is never taken for open air for want
     * of knowing what is over it - and the whole answer null without Voxy or Distant Horizons.
     */
    public static CompletableFuture<@Nullable Boolean @Nullable []> openAir(double[] xs, double[] feet, double[] eyes,
                                                                            double[] zs) {
        LodSource source = source();
        if (source == null) return CompletableFuture.completedFuture(null);
        CompletableFuture<@Nullable Boolean @Nullable []> result = new CompletableFuture<>();
        run(() -> {
            Boolean[] open = null;
            try {
                Object world = open(source);
                if (world != null) {
                    open = new Boolean[xs.length];
                    for (int i = 0; i < xs.length; i++) {
                        int x = (int) Math.floor(xs[i]), z = (int) Math.floor(zs[i]);
                        Surface top = source.column(world, x, z);
                        if (top == null) continue;
                        // Nothing in the column over its top block: no need to look up past it.
                        double above = top.y() + 1 - eyes[i];
                        BlockState[] standing = source.slice(world, x, z, (int) Math.floor(feet[i]), 1);
                        boolean wet = standing != null && standing[0] != null && !standing[0].getFluidState().isEmpty();
                        open[i] = !wet && (above <= 0 || Double.isNaN(source.firstHit(world, xs[i], eyes[i], zs[i],
                                0, 1, 0, 0, above)));
                    }
                }
            } catch (Throwable e) {
                source.fail(e);
                open = null;
            }
            result.complete(open);
        });
        return result;
    }

    private static long key(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }

}
