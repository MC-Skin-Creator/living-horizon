package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Retry;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
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
    /** After a failed read, nothing is read for a while: Distant Horizons may only have been busy. */
    private final Retry readFailures = new Retry("Reading Distant Horizons' world", "distant mobs find no ground in it");

    private MethodHandle terrainRepo, worldProxy;
    private MethodHandle worldLoaded, levels, wrapped, columnAt;
    private MethodHandle top, blockOf, biomeOf, clearCache;
    private MethodHandle isAir, isLiquid, isSolid, biomeName;
    /** Before Distant Horizons 3.0 there is no opacity, and before 2.4 no cache: null then. */
    private @Nullable MethodHandle opacity, softCache;

    /**
     * One reader's cache of Distant Horizons' data, for the level it was made for. Distant
     * Horizons' cache keeps an area as it was first read for as long as memory allows: read
     * before Distant Horizons had filled it in (it builds its terrain over minutes in a new
     * world), the area stayed empty, and a mob standing there stayed hidden over terrain in
     * plain view. So the cache is emptied every {@link #RENEW_NANOS}, and soon after a column
     * comes back empty ({@link #MISSED_NANOS}).
     */
    private static final class Cached {
        final Object level;
        final @Nullable Object cache;
        final int generation;
        long emptied = System.nanoTime();
        boolean missed;

        Cached(Object level, @Nullable Object cache, int generation) {
            this.level = level;
            this.cache = cache;
            this.generation = generation;
        }
    }

    private static final long RENEW_NANOS = 30_000_000_000L, MISSED_NANOS = 2_000_000_000L;
    /** Raised by {@link #clear()}: every reader's cache is made again. */
    private volatile int generation;

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
            return missed();
        }
        Object message = DhLink.message(result);
        if (!DhLink.success(result)) {
            lastAnswer = "failed: " + message;
            return missed();
        }
        Object[] points = (Object[]) DhLink.payload(result);
        if (points == null || points.length == 0) {
            lastAnswer = "empty: " + message;
            return missed();
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

    /** A column without data: this reader's cache may hold its area as it was before Distant Horizons filled it in. */
    private Object @Nullable [] missed() {
        Cached cached = caches.get();
        if (cached != null) cached.missed = true;
        return null;
    }

    @Override
    public void clear() {
        tops.clear();
        generation++;
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
                if (block == null || (boolean) isAir.invoke(block) || !opaque(block)) continue;
                best = Math.max(best, (int) top.invoke(point));
            }
        }
        if (tops.size() > TOPS_KEPT) tops.clear();
        tops.put(key, (now + TOP_SECONDS) << 32 | (best & 0xFFFFFFFFL));
        return best;
    }

    @Override
    public synchronized boolean available() {
        if (broken || readFailures.blocked()) return false;
        if (!ready) {
            ready = true;
            if (!loaded()) {
                broken = true;
                return false;
            }
            try {
                if (!DhLink.linked()) throw new IllegalStateException("its API is not the one this mod knows");
                link();
            } catch (Throwable e) {
                broken = true;
                failure = "Distant Horizons found, but too old or unknown (its world needs 2.3 or newer): " + e;
                LivingHorizonClient.LOGGER.warn("Distant Horizons found, but not the version this mod knows: its world cannot be read", e);
                return false;
            }
        }
        return true;
    }

    /**
     * Distant Horizons' render distance, from its settings through its API, as far as its far
     * fog is still thin: its start and end are fractions of the render distance. 0 with its
     * rendering off or no settings to read.
     */
    @Override
    public double clearDistance() {
        return distance(true);
    }

    /** Distant Horizons' render distance, {@link #clearDistance} without its fog. */
    @Override
    public double drawnDistance() {
        return distance(false);
    }

    private static double distance(boolean clear) {
        String api = DhLink.API, config = api + "interfaces.config.";
        try {
            Object configs = Class.forName(api + "DhApi$Delayed").getField("configs").get(null);
            if (configs == null) return 0;
            Object graphics = call(config + "IDhApiConfig", configs, "graphics");
            String graphicsType = config + "client.IDhApiGraphicsConfig";
            if (!(Boolean) value(call(graphicsType, graphics, "renderingEnabled"))) return 0;
            double drawn = ((Number) value(call(graphicsType, graphics, "chunkRenderDistance"))).doubleValue() * 16;
            if (!clear) return drawn;
            try {
                Object fog = call(graphicsType, graphics, "fog");
                if (!(Boolean) value(call(config + "client.IDhApiFogConfig", fog, "enableDhFog"))) return drawn * 0.9;
                Object far = call(config + "client.IDhApiFogConfig", fog, "farFog");
                double start = ((Number) value(call(config + "client.IDhApiFarFogConfig", far, "farFogStartDistance"))).doubleValue();
                double end = ((Number) value(call(config + "client.IDhApiFarFogConfig", far, "farFogEndDistance"))).doubleValue();
                // A little way into the fog, while it is still thin.
                return drawn * Math.max(0.1, Math.min(0.9, start + (end - start) * 0.2));
            } catch (ReflectiveOperationException | RuntimeException e) {
                // A version whose fog settings this does not know: where its fog starts by default.
                return drawn * 0.4;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    /** A method of no arguments of one of Distant Horizons' API interfaces. */
    private static Object call(String type, Object on, String method) throws ReflectiveOperationException {
        return Class.forName(type).getMethod(method).invoke(on);
    }

    /** What one of its settings is set to. */
    private static Object value(Object setting) throws ReflectiveOperationException {
        return call(DhLink.API + "interfaces.config.IDhApiConfigValue", setting, "getValue");
    }

    @Override
    public void fail(Throwable e) {
        // Not for good: a level change or a section being rewritten can fail one read, and
        // nothing would then hide a mob behind a hill until the game restarted.
        readFailures.fail(e);
        failure = "Distant Horizons did not answer as expected: " + readFailures.failure();
    }

    private void link() throws ReflectiveOperationException {
        String api = DhLink.API;
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        Class<?> repo = Class.forName(api + "interfaces.data.IDhApiTerrainDataRepo");
        Class<?> proxy = Class.forName(api + "interfaces.world.IDhApiWorldProxy");
        Class<?> level = Class.forName(api + "interfaces.world.IDhApiLevelWrapper");
        Class<?> cache = Class.forName(api + "interfaces.data.IDhApiTerrainDataCache");
        Class<?> unsafe = Class.forName(api + "interfaces.IDhApiUnsafeWrapper");
        Class<?> result = DhLink.resultType();
        Class<?> point = Class.forName(api + "objects.data.DhApiTerrainDataPoint");
        Class<?> block = Class.forName(api + "interfaces.block.IDhApiBlockStateWrapper");
        Class<?> biome = Class.forName(api + "interfaces.block.IDhApiBiomeWrapper");
        // Filled in by Distant Horizons once it has started: read at every use.
        terrainRepo = DhLink.delayed("terrainRepo", repo);
        worldProxy = DhLink.delayed("worldProxy", proxy);
        worldLoaded = lookup.findVirtual(proxy, "worldLoaded", MethodType.methodType(boolean.class));
        levels = lookup.findVirtual(proxy, "getAllLoadedLevelWrappers", MethodType.methodType(Iterable.class));
        wrapped = lookup.findVirtual(unsafe, "getWrappedMcObject", MethodType.methodType(Object.class));
        columnAt = lookup.findVirtual(repo, "getColumnDataAtBlockPos",
                MethodType.methodType(result, level, int.class, int.class, cache));
        softCache = Links.optional(lookup, repo, "createSoftCache", MethodType.methodType(cache));
        clearCache = lookup.findVirtual(cache, "clear", MethodType.methodType(void.class));
        if (softCache == null) DhLink.fallback("no cache for its world (before 2.4)");
        top = lookup.findGetter(point, "topYBlockPos", int.class);
        blockOf = lookup.findGetter(point, "blockStateWrapper", block);
        biomeOf = lookup.findGetter(point, "biomeWrapper", biome);
        isAir = lookup.findVirtual(block, "isAir", MethodType.methodType(boolean.class));
        isLiquid = lookup.findVirtual(block, "isLiquid", MethodType.methodType(boolean.class));
        isSolid = lookup.findVirtual(block, "isSolid", MethodType.methodType(boolean.class));
        opacity = Links.optional(lookup, block, "getOpacity", MethodType.methodType(int.class));
        if (opacity == null) DhLink.fallback("no opacity, solid blocks and liquids stop the eye (before 3.0)");
        biomeName = lookup.findVirtual(biome, "getName", MethodType.methodType(String.class));
    }

    /** Whether a block stops the eye; without opacity (before 3.0), solid blocks and liquids do. */
    private boolean opaque(Object block) throws Throwable {
        if (opacity != null) return (int) opacity.invoke(block) > 0;
        return (boolean) isSolid.invoke(block) || (boolean) isLiquid.invoke(block);
    }

    /** The level Distant Horizons holds for the one the player is in; null while it has none. */
    @Override
    public @Nullable Object open() throws Throwable {
        // Both are filled in by Distant Horizons once it has started: until then, nothing to read.
        Object proxy = worldProxy.invoke();
        if (proxy == null || terrainRepo.invoke() == null || !(boolean) worldLoaded.invoke(proxy)) return null;
        Level current = Minecraft.getInstance().level;
        if (current == null) return null;
        // In single player Distant Horizons' levels wrap the server's, one per dimension, in no
        // order: never the client's own level. The one of the player's dimension, then; the first
        // one, as before, read another dimension's terrain (the End's void, the Nether's roof).
        Object sameDimension = null;
        for (Object level : (Iterable<?>) levels.invoke(proxy)) {
            Object game = wrapped.invoke(level);
            if (game == current) return level;
            if (sameDimension == null && game instanceof Level other && other.dimension().equals(current.dimension())) {
                sameDimension = level;
            }
        }
        return sameDimension;
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
        if (cached == null || cached.level != world || cached.generation != generation) {
            if (cached != null && cached.cache != null) clearCache.invoke(cached.cache);
            // Without a cache (before 2.4), Distant Horizons reads without one.
            cached = new Cached(world, softCache == null ? null : softCache.invoke(terrainRepo.invoke()), generation);
            caches.set(cached);
        } else if (cached.cache != null) {
            long now = System.nanoTime();
            if (now - cached.emptied > RENEW_NANOS || cached.missed && now - cached.emptied > MISSED_NANOS) {
                clearCache.invoke(cached.cache);
                cached.emptied = now;
                cached.missed = false;
            }
        }
        return cached.cache;
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
