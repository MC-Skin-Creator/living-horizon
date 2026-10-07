package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Distant Horizons' world, through its public API ({@code com.seibel.distanthorizons.api}):
 * the column of blocks at a position, and a ray cast through its level-of-detail data.
 * The API is reached by reflection, like Voxy, so that the mod loads without it and any
 * change on its side turns this source off for the session instead of crashing.
 *
 * <p>Distant Horizons stores coarser data the farther it is, so a column is as precise as
 * the detail level it holds there: good enough for the ground a herd walks on and the
 * hills that hide it.
 */
final class DhSource implements LodSource {
    private boolean ready;
    private volatile boolean broken;
    private volatile @Nullable String failure;
    /** After a failed read, nothing is read until then: Distant Horizons may only have been busy. */
    private volatile long pausedUntil;

    private MethodHandle terrainRepo, worldProxy;
    private MethodHandle worldLoaded, levels, wrapped, columnAt, softCache;
    private MethodHandle success, payload, messageOf;
    private MethodHandle top, blockOf, biomeOf;
    private MethodHandle isAir, isLiquid, opacity, biomeName;

    /** One reader's cache of Distant Horizons' data, for the level it was made for. */
    private record Cached(Object level, Object cache) {
    }

    private final ThreadLocal<Cached> caches = new ThreadLocal<>();

    /** Columns asked for, columns that came back with blocks, and the last thing Distant Horizons said. */
    private final AtomicInteger reads = new AtomicInteger(), found = new AtomicInteger();
    private volatile String lastAnswer = "nothing asked yet";

    @Override
    public String details() {
        return reads.get() + " columns read, " + found.get() + " with blocks; last answer: " + lastAnswer;
    }

    /** The data points of a column, or null; counts the answers and keeps the last message. */
    private Object @Nullable [] read(Object repo, Object world, int x, int z, Object cache) throws Throwable {
        Object result = columnAt.invoke(repo, world, x, z, cache);
        reads.incrementAndGet();
        if (result == null) {
            lastAnswer = "null";
            return null;
        }
        Object message = messageOf.invoke(result);
        if (!(boolean) success.invoke(result)) {
            lastAnswer = "failed: " + message;
            return null;
        }
        Object[] points = (Object[]) payload.invoke(result);
        if (points == null || points.length == 0) {
            lastAnswer = "empty: " + message;
            return null;
        }
        found.incrementAndGet();
        lastAnswer = "ok: " + message;
        return points;
    }

    @Override
    public String name() {
        return "Distant Horizons";
    }

    @Override
    public boolean loaded() {
        return Platform.isModLoaded("distanthorizons");
    }

    @Override
    public @Nullable String failure() {
        return failure;
    }

    @Override
    public void clear() {
        tops.clear();
    }

    /** The height of the highest block of each column read, and until when it is trusted. */
    private final ConcurrentHashMap<Long, Long> tops = new ConcurrentHashMap<>();
    private static final long TOP_SECONDS = 90;
    private static final int TOPS_KEPT = 300_000;

    /**
     * The top (exclusive) of the highest solid block of a column, or {@link Integer#MIN_VALUE}
     * where there is none. A line of sight asks about thousands of columns, the same ones again
     * and again, and every read makes Distant Horizons build a block of objects: a column is
     * read once, then remembered for a while.
     */
    private int topOf(Object repo, Object world, Object cache, int x, int z) throws Throwable {
        long key = (long) x << 32 | (z & 0xFFFFFFFFL);
        long now = System.currentTimeMillis() / 1000;
        Long known = tops.get(key);
        if (known != null && (known >>> 32) > now) return (int) known.longValue();
        Object[] points = read(repo, world, x, z, cache);
        int best = Integer.MIN_VALUE;
        if (points != null) {
            for (Object point : points) {
                Object block = blockOf.invoke(point);
                if (block == null || (boolean) isAir.invoke(block) || (int) opacity.invoke(block) <= 0) continue;
                best = Math.max(best, (int) top.invoke(point));
            }
        }
        if (tops.size() > TOPS_KEPT) tops.clear();
        tops.put(key, (now + TOP_SECONDS) << 32 | (best & 0xFFFFFFFFL));
        return best;
    }

    @Override
    public synchronized boolean available() {
        if (broken || System.nanoTime() < pausedUntil) return false;
        if (!ready) {
            ready = true;
            if (!loaded()) {
                broken = true;
                return false;
            }
            try {
                link();
            } catch (Throwable e) {
                broken = true;
                failure = "Distant Horizons found, but not the version this mod knows: " + e;
                LivingHorizonClient.LOGGER.warn("Distant Horizons found, but not the version this mod knows: its world cannot be read", e);
                return false;
            }
        }
        return true;
    }

    @Override
    public void fail(Throwable e) {
        // Not for good: a level change or a section being rewritten can fail one read, and
        // nothing would then hide a mob behind a hill until the game restarted.
        pausedUntil = System.nanoTime() + 20_000_000_000L;
        failure = "Distant Horizons did not answer as expected: " + e;
        LivingHorizonClient.LOGGER.warn("Reading Distant Horizons' world paused for 20 seconds: it did not answer as expected", e);
    }

    private void link() throws ReflectiveOperationException {
        String api = "com.seibel.distanthorizons.api.";
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> delayed = Class.forName(api + "DhApi$Delayed");
        Class<?> repo = Class.forName(api + "interfaces.data.IDhApiTerrainDataRepo");
        Class<?> proxy = Class.forName(api + "interfaces.world.IDhApiWorldProxy");
        Class<?> level = Class.forName(api + "interfaces.world.IDhApiLevelWrapper");
        Class<?> cache = Class.forName(api + "interfaces.data.IDhApiTerrainDataCache");
        Class<?> unsafe = Class.forName(api + "interfaces.IDhApiUnsafeWrapper");
        Class<?> result = Class.forName(api + "objects.DhApiResult");
        Class<?> point = Class.forName(api + "objects.data.DhApiTerrainDataPoint");
        Class<?> block = Class.forName(api + "interfaces.block.IDhApiBlockStateWrapper");
        Class<?> biome = Class.forName(api + "interfaces.block.IDhApiBiomeWrapper");
        // Filled in by Distant Horizons once it has started: read at every use.
        terrainRepo = lookup.findStaticGetter(delayed, "terrainRepo", repo);
        worldProxy = lookup.findStaticGetter(delayed, "worldProxy", proxy);
        worldLoaded = lookup.findVirtual(proxy, "worldLoaded", MethodType.methodType(boolean.class));
        levels = lookup.findVirtual(proxy, "getAllLoadedLevelWrappers", MethodType.methodType(Iterable.class));
        wrapped = lookup.findVirtual(unsafe, "getWrappedMcObject", MethodType.methodType(Object.class));
        columnAt = lookup.findVirtual(repo, "getColumnDataAtBlockPos",
                MethodType.methodType(result, level, int.class, int.class, cache));
        softCache = lookup.findVirtual(repo, "createSoftCache", MethodType.methodType(cache));
        success = lookup.findGetter(result, "success", boolean.class);
        payload = lookup.findGetter(result, "payload", Object.class);
        messageOf = lookup.findGetter(result, "message", String.class);
        top = lookup.findGetter(point, "topYBlockPos", int.class);
        blockOf = lookup.findGetter(point, "blockStateWrapper", block);
        biomeOf = lookup.findGetter(point, "biomeWrapper", biome);
        isAir = lookup.findVirtual(block, "isAir", MethodType.methodType(boolean.class));
        isLiquid = lookup.findVirtual(block, "isLiquid", MethodType.methodType(boolean.class));
        opacity = lookup.findVirtual(block, "getOpacity", MethodType.methodType(int.class));
        biomeName = lookup.findVirtual(biome, "getName", MethodType.methodType(String.class));
    }

    /** The level Distant Horizons holds for the one the player is in; null while it has none. */
    @Override
    public @Nullable Object open() throws Throwable {
        // Both are filled in by Distant Horizons once it has started: until then, nothing to read.
        Object proxy = worldProxy.invoke();
        if (proxy == null || terrainRepo.invoke() == null || !(boolean) worldLoaded.invoke(proxy)) return null;
        Object current = Minecraft.getInstance().level;
        Object first = null;
        for (Object level : (Iterable<?>) levels.invoke(proxy)) {
            if (first == null) first = level;
            if (current != null && wrapped.invoke(level) == current) return level;
        }
        return first;
    }

    @Override
    public LodWorld.@Nullable Surface column(Object world, int x, int z) throws Throwable {
        Object[] points = read(terrainRepo.invoke(), world, x, z, cache(world));
        if (points == null) return null;
        Object best = null;
        int bestTop = Integer.MIN_VALUE;
        for (Object point : points) {
            Object block = blockOf.invoke(point);
            if (block == null || (boolean) isAir.invoke(block)) continue;
            int y = (int) top.invoke(point);
            if (y > bestTop) {
                bestTop = y;
                best = point;
            }
        }
        if (best == null) return null;
        Object block = blockOf.invoke(best);
        Object biome = biomeOf.invoke(best);
        String name = biome == null ? "" : String.valueOf(biomeName.invoke(biome));
        BlockState state = wrapped.invoke(block) instanceof BlockState found ? found : null;
        boolean water = state != null ? state.getFluidState().is(FluidTags.WATER) : (boolean) isLiquid.invoke(block);
        // The top of a data point is exclusive: the block itself is the one below.
        return new LodWorld.Surface(bestTop - 1, name, water, state);
    }

    /**
     * A cache of Distant Horizons' data for this thread and level: without one its reads are
     * far slower. A new one when the level is another.
     */
    private Object cache(Object world) throws Throwable {
        Cached cached = caches.get();
        if (cached == null || cached.level() != world) {
            cached = new Cached(world, softCache.invoke(terrainRepo.invoke()));
            caches.set(cached);
        }
        return cached.cache();
    }

    /**
     * Walks the ray a block at a time near the eye and in longer steps farther away, where
     * Distant Horizons' data is coarser, and stops where it goes under the highest block of a
     * column. Caves under a hill do not count: from far away that is all the data tells.
     * Its own {@code raycast} looks at nine columns per block and gives up at the first column
     * without data, which is far too slow and too fragile for rays of hundreds of blocks.
     * A column without data is walked through.
     */
    @Override
    public double firstHit(Object world, double ax, double ay, double az, double dx, double dy, double dz,
                           double from, double to) throws Throwable {
        Object repo = terrainRepo.invoke();
        Object cache = cache(world);
        long column = Long.MIN_VALUE;
        int height = Integer.MIN_VALUE;
        for (double t = from; t < to; t += Math.clamp(t / 48.0, 1.0, 8.0)) {
            int x = (int) Math.floor(ax + dx * t);
            int y = (int) Math.floor(ay + dy * t);
            int z = (int) Math.floor(az + dz * t);
            long key = (long) x << 32 | (z & 0xFFFFFFFFL);
            if (key != column) {
                column = key;
                height = topOf(repo, world, cache, x, z);
            }
            // Under the highest block of a column: inside the ground, or a tree on it.
            if (y < height) return t;
        }
        return Double.NaN;
    }
}
