package fr.clixmods.farfarplayer.compat;

import fr.clixmods.farfarplayer.FarConfig;
import fr.clixmods.farfarplayer.FarFarPlayerClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reads Voxy's world: the ground and biome of a column - for the birds to find coasts,
 * fields and lakes, and for the mobs to find where they may walk - and the first block
 * along a line, for {@code /farfarplayer voxy}. Voxy keeps every block it has seen, in
 * sections of 32 blocks a side at level 0, one block per voxel.
 *
 * <p>Reads run on a few threads of their own, never on the game's. Each keeps the last
 * sections it read; columns already read are remembered for a while, since herds and
 * flocks keep asking about the same ground; and many columns go in one task.
 *
 * <p>Voxy has no API for this. Its classes are reached by reflection so that the mod still
 * loads without it, and any failure turns this off for the session.
 */
public final class VoxyWorld {
    private static final double STEP = 0.75;
    /** Sections each reader keeps acquired between reads: 256 KiB each in Voxy's memory. */
    private static final int CACHED_SECTIONS = 96;
    /** A column read is trusted this long: the terrain far away rarely changes. */
    private static final long COLUMN_NANOS = 120_000_000_000L;
    private static final int COLUMNS_KEPT = 60_000;
    private static final int THREADS = Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 3);

    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final ExecutorService READERS = Executors.newFixedThreadPool(THREADS, runnable -> {
        Thread thread = new Thread(runnable, "Far Far Player Voxy reader " + NAMES.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    /** The same work on a single thread, when {@link FarConfig#optParallelVoxy} is off, to compare. */
    private static final ExecutorService READER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Far Far Player Voxy reader");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    /** Tasks given to the readers and not finished yet, for the debug panel. */
    private static final AtomicInteger PENDING = new AtomicInteger();

    private static void run(Runnable task) {
        PENDING.incrementAndGet();
        (FarConfig.get().optParallelVoxy ? READERS : READER).execute(() -> {
            try {
                task.run();
            } finally {
                PENDING.decrementAndGet();
            }
        });
    }

    /** For the debug panel: reads waiting or running, columns remembered, reader threads. */
    public static int pending() {
        return PENDING.get();
    }

    public static int columnsKnown() {
        return COLUMNS.size();
    }

    public static int threads() {
        return FarConfig.get().optParallelVoxy ? THREADS : 1;
    }

    /** One reader's sections, for one world. */
    private static final class Cache {
        final LinkedHashMap<Long, Object> sections = new LinkedHashMap<>(128, 0.75f, true);
        @Nullable Object world;
        int generation;
    }

    private static final ThreadLocal<Cache> CACHES = ThreadLocal.withInitial(Cache::new);
    /** Bumped when the world changes: every reader lets go of what it holds at its next read. */
    private static volatile int generation;

    /** Columns already read: the surface, or {@link #NOTHING}, and when. */
    private record Known(@Nullable Surface surface, long at) {
    }

    private static final Surface NOTHING = new Surface(Integer.MIN_VALUE, "", false, null);
    private static final ConcurrentHashMap<Long, Known> COLUMNS = new ConcurrentHashMap<>();

    private static boolean ready;
    private static volatile boolean broken;
    private static volatile @Nullable String failure;
    private static MethodHandle renderSystem, engine, acquire, data, release, mapper, opacity, isAir, isLive;
    private static MethodHandle biomeId, biomeEntries, biomeName, blockState, blockId;

    private VoxyWorld() {
    }

    /** Another world: every section held is let go, every column read forgotten. */
    public static void clear() {
        generation++;
        COLUMNS.clear();
    }

    /** For {@code /farfarplayer voxy}. */
    public record Status(boolean voxyLoaded, @Nullable String failure) {
    }

    public static Status status() {
        return new Status(FabricLoader.getInstance().isModLoaded("voxy"), failure);
    }

    /**
     * Walks Voxy's world along a direction and says how far the first opaque block is:
     * NaN when there is none within {@code max}, or when Voxy has no world loaded.
     */
    public static CompletableFuture<Double> probe(Vec3 eye, Vec3 direction, double max) {
        CompletableFuture<Double> result = new CompletableFuture<>();
        if (!available()) {
            result.completeExceptionally(new IllegalStateException(failure != null ? failure : "Voxy is not loaded"));
            return result;
        }
        run(() -> {
            try {
                Object world = world();
                if (world == null) {
                    result.completeExceptionally(new IllegalStateException("Voxy has no world loaded"));
                    return;
                }
                Vec3 d = direction.normalize();
                result.complete(firstHit(world, eye.x, eye.y, eye.z, d.x, d.y, d.z, 0.5, max));
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    /** The top of one column of Voxy's world: its height, its biome, whether it is water, its block. */
    public record Surface(int y, String biome, boolean water, @Nullable BlockState block) {
    }

    /**
     * The highest block Voxy knows at a column, and its biome, read off the thread;
     * empty where Voxy has nothing, or without Voxy.
     */
    public static CompletableFuture<Optional<Surface>> surface(int x, int z) {
        return surfaces(new int[]{x}, new int[]{z}).thenApply(found -> Optional.ofNullable(found[0]));
    }

    /**
     * Many columns at once, in one task: entry {@code i} is the surface of
     * {@code (xs[i], zs[i])}, or null where Voxy has nothing. Columns read recently come
     * from memory without waiting.
     */
    public static CompletableFuture<@Nullable Surface[]> surfaces(int[] xs, int[] zs) {
        Surface[] found = new Surface[xs.length];
        if (!available()) return CompletableFuture.completedFuture(found);
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
                Object world = world();
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
                        Surface surface = column(world, xs[i], zs[i]);
                        if (cache) COLUMNS.put(key, new Known(surface == null ? NOTHING : surface, at));
                        found[i] = surface;
                    }
                }
            } catch (Throwable e) {
                fail(e);
            }
            result.complete(found);
        });
        return result;
    }

    /**
     * For each point, whether Voxy's terrain stands between the eye and it: a block on the
     * line, short of the point by {@code margin}. Null without Voxy or its world.
     */
    public static CompletableFuture<boolean @Nullable []> blocked(Vec3 eye, double[] xs, double[] ys, double[] zs,
                                                                  double margin) {
        if (!available()) return CompletableFuture.completedFuture(null);
        CompletableFuture<boolean @Nullable []> result = new CompletableFuture<>();
        run(() -> {
            boolean[] blocked = null;
            try {
                Object world = world();
                if (world != null) {
                    blocked = new boolean[xs.length];
                    for (int i = 0; i < xs.length; i++) {
                        double dx = xs[i] - eye.x, dy = ys[i] - eye.y, dz = zs[i] - eye.z;
                        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (length <= margin + 2) continue;
                        blocked[i] = !Double.isNaN(firstHit(world, eye.x, eye.y, eye.z,
                                dx / length, dy / length, dz / length, 2, length - margin));
                    }
                }
            } catch (Throwable e) {
                fail(e);
                blocked = null;
            }
            result.complete(blocked);
        });
        return result;
    }

    private static long key(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }

    private static @Nullable Surface column(Object world, int x, int z) throws Throwable {
        Object types = (Object) mapper.invokeExact(world);
        for (int y = 319; y >= -64; y--) {
            long[] voxels = section(world, x >> 5, y >> 5, z >> 5,
                    ((long) (x >> 5) & 0xFFFFFFL) | ((long) (z >> 5) & 0xFFFFFFL) << 24 | ((long) (y >> 5) & 0xFFL) << 48);
            if (voxels == null) {
                y -= y & 31; // a whole empty section: on to the one below
                continue;
            }
            long id = voxels[(y & 31) << 10 | (z & 31) << 5 | (x & 31)];
            if ((boolean) isAir.invokeExact(id)) continue;
            Object[] biomes = (Object[]) biomeEntries.invokeExact(types);
            int b = (int) biomeId.invokeExact(id);
            String biome = b >= 0 && b < biomes.length ? (String) biomeName.invokeExact(biomes[b]) : "";
            BlockState state = (BlockState) blockState.invokeExact(types, (int) blockId.invokeExact(id));
            return new Surface(y, biome, state != null && state.getFluidState().is(FluidTags.WATER), state);
        }
        return null;
    }

    // --- Voxy -----------------------------------------------------------------------------

    private static synchronized boolean available() {
        if (broken) return false;
        if (!ready) {
            ready = true;
            if (!FabricLoader.getInstance().isModLoaded("voxy")) {
                broken = true;
                return false;
            }
            try {
                link();
            } catch (Throwable e) {
                broken = true;
                failure = "Voxy found, but not the version this mod knows: " + e;
                FarFarPlayerClient.LOGGER.warn("Voxy found, but not the version this mod knows: its world cannot be read", e);
                return false;
            }
        }
        return true;
    }

    private static void fail(Throwable e) {
        broken = true;
        failure = "Voxy did not answer as expected: " + e;
        FarFarPlayerClient.LOGGER.warn("Reading Voxy's world turned off: Voxy did not answer as expected", e);
    }

    private static void link() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> getter = Class.forName("me.cortex.voxy.client.core.IGetVoxyRenderSystem");
        Class<?> system = Class.forName("me.cortex.voxy.client.core.VoxyRenderSystem");
        Class<?> worldEngine = Class.forName("me.cortex.voxy.common.world.WorldEngine");
        Class<?> section = Class.forName("me.cortex.voxy.common.world.WorldSection");
        Class<?> mapperClass = Class.forName("me.cortex.voxy.common.world.other.Mapper");
        // Erased to Object so that invokeExact works without Voxy's classes in sight:
        // an exact call costs what a plain call does, a generic one boxes every argument.
        renderSystem = lookup.findStatic(getter, "getNullable", MethodType.methodType(system))
                .asType(MethodType.methodType(Object.class));
        engine = lookup.findVirtual(system, "getEngine", MethodType.methodType(worldEngine))
                .asType(MethodType.methodType(Object.class, Object.class));
        isLive = lookup.findVirtual(worldEngine, "isLive", MethodType.methodType(boolean.class))
                .asType(MethodType.methodType(boolean.class, Object.class));
        acquire = lookup.findVirtual(worldEngine, "acquireIfExists",
                        MethodType.methodType(section, int.class, int.class, int.class, int.class))
                .asType(MethodType.methodType(Object.class, Object.class, int.class, int.class, int.class, int.class));
        data = lookup.findVirtual(section, "_unsafeGetRawDataArray", MethodType.methodType(long[].class))
                .asType(MethodType.methodType(long[].class, Object.class));
        release = lookup.findVirtual(section, "release", MethodType.methodType(int.class))
                .asType(MethodType.methodType(int.class, Object.class));
        mapper = lookup.findVirtual(worldEngine, "getMapper", MethodType.methodType(mapperClass))
                .asType(MethodType.methodType(Object.class, Object.class));
        opacity = lookup.findVirtual(mapperClass, "getBlockStateOpacity", MethodType.methodType(int.class, long.class))
                .asType(MethodType.methodType(int.class, Object.class, long.class));
        isAir = lookup.findStatic(mapperClass, "isAir", MethodType.methodType(boolean.class, long.class));
        biomeId = lookup.findStatic(mapperClass, "getBiomeId", MethodType.methodType(int.class, long.class));
        blockId = lookup.findStatic(mapperClass, "getBlockId", MethodType.methodType(int.class, long.class));
        Class<?> biomeEntry = Class.forName("me.cortex.voxy.common.world.other.Mapper$BiomeEntry");
        biomeEntries = lookup.findVirtual(mapperClass, "getBiomeEntries", MethodType.methodType(biomeEntry.arrayType()))
                .asType(MethodType.methodType(Object[].class, Object.class));
        biomeName = lookup.findGetter(biomeEntry, "biome", String.class)
                .asType(MethodType.methodType(String.class, Object.class));
        blockState = lookup.findVirtual(mapperClass, "getBlockStateFromBlockId",
                        MethodType.methodType(BlockState.class, int.class))
                .asType(MethodType.methodType(BlockState.class, Object.class, int.class));
    }

    /** Voxy's world for this reader, after letting go of what it held for another one. */
    private static @Nullable Object world() throws Throwable {
        Object system = (Object) renderSystem.invokeExact();
        if (system == null) return null;
        Object world = (Object) engine.invokeExact(system);
        if (world == null || !(boolean) isLive.invokeExact(world)) return null;
        Cache cache = CACHES.get();
        if (world != cache.world || cache.generation != generation) {
            releaseAll(cache);
            cache.world = world;
            cache.generation = generation;
        }
        return world;
    }

    /** Distance to the first opaque voxel along a unit direction, between two distances; NaN if none. */
    private static double firstHit(Object world, double ax, double ay, double az, double dx, double dy, double dz,
                                   double from, double to) throws Throwable {
        Object types = (Object) mapper.invokeExact(world);
        long key = Long.MIN_VALUE;
        long[] voxels = null;
        for (double t = from; t < to; t += STEP) {
            int x = (int) Math.floor(ax + dx * t);
            int y = (int) Math.floor(ay + dy * t);
            int z = (int) Math.floor(az + dz * t);
            long k = ((long) (x >> 5) & 0xFFFFFFL) | ((long) (z >> 5) & 0xFFFFFFL) << 24 | ((long) (y >> 5) & 0xFFL) << 48;
            if (k != key) {
                key = k;
                voxels = section(world, x >> 5, y >> 5, z >> 5, k);
            }
            if (voxels == null) continue;
            long id = voxels[(y & 31) << 10 | (z & 31) << 5 | (x & 31)];
            if (!(boolean) isAir.invokeExact(id) && (int) opacity.invokeExact(types, id) > 0) return t;
        }
        return Double.NaN;
    }

    /** A level 0 section's voxels, from this reader's cache or from Voxy; null where Voxy has nothing. */
    private static long @Nullable [] section(Object world, int sx, int sy, int sz, long key) throws Throwable {
        LinkedHashMap<Long, Object> sections = CACHES.get().sections;
        Object section = sections.get(key);
        if (section == null && !sections.containsKey(key)) {
            section = (Object) acquire.invokeExact(world, 0, sx, sy, sz);
            sections.put(key, section);
            if (sections.size() > CACHED_SECTIONS) {
                Iterator<Map.Entry<Long, Object>> oldest = sections.entrySet().iterator();
                Object evicted = oldest.next().getValue();
                oldest.remove();
                if (evicted != null) {
                    int ignored = (int) release.invokeExact(evicted);
                }
            }
        }
        return section == null ? null : (long[]) data.invokeExact(section);
    }

    private static void releaseAll(Cache cache) {
        try {
            for (Object section : cache.sections.values()) {
                if (section != null) {
                    int ignored = (int) release.invokeExact(section);
                }
            }
        } catch (Throwable e) {
            fail(e);
        }
        cache.sections.clear();
        cache.world = null;
    }
}
