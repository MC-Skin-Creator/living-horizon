package fr.clixmods.farfarplayer.compat;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads Voxy's world: the ground and biome of a column, for the gulls to find the coast,
 * and the first block along a line, for {@code /farfarplayer voxy}. Voxy keeps every block
 * it has seen, in sections of 32 blocks a side at level 0, one block per voxel; this walks
 * them on a thread of its own, keeping the last sections it read.
 *
 * <p>Voxy has no API for this. Its classes are reached by reflection so that the mod still
 * loads without it, and any failure turns this off for the session.
 */
public final class VoxyWorld {
    private static final double STEP = 0.75;
    /** Sections kept acquired between reads: 256 KiB each in Voxy's memory. */
    private static final int CACHED_SECTIONS = 160;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Far Far Player Voxy reader");
        thread.setDaemon(true);
        return thread;
    });

    /** Worker thread only: Voxy sections held between reads, oldest first. */
    private static final LinkedHashMap<Long, Object> SECTIONS = new LinkedHashMap<>(256, 0.75f, true);
    private static @Nullable Object cachedWorld;

    private static boolean ready;
    private static volatile boolean broken;
    private static volatile @Nullable String failure;
    private static MethodHandle renderSystem, engine, acquire, data, release, mapper, opacity, isAir, isLive;
    private static MethodHandle biomeId, biomeEntries, biomeName, blockState, blockId;

    private VoxyWorld() {
    }

    /** Lets go of every cached section: another world. */
    public static void clear() {
        if (ready && !broken) WORKER.execute(VoxyWorld::releaseAll);
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
        WORKER.execute(() -> {
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
        CompletableFuture<Optional<Surface>> result = new CompletableFuture<>();
        if (!available()) {
            result.complete(Optional.empty());
            return result;
        }
        WORKER.execute(() -> {
            try {
                Object world = world();
                result.complete(world == null ? Optional.empty() : Optional.ofNullable(column(world, x, z)));
            } catch (Throwable e) {
                fail(e);
                result.complete(Optional.empty());
            }
        });
        return result;
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

    private static boolean available() {
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

    private static @Nullable Object world() throws Throwable {
        Object system = (Object) renderSystem.invokeExact();
        if (system == null) return null;
        Object world = (Object) engine.invokeExact(system);
        if (world == null || !(boolean) isLive.invokeExact(world)) return null;
        if (world != cachedWorld) {
            releaseAll();
            cachedWorld = world;
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

    /** A level 0 section's voxels, from the cache or from Voxy; null where Voxy has nothing. */
    private static long @Nullable [] section(Object world, int sx, int sy, int sz, long key) throws Throwable {
        Object section = SECTIONS.get(key);
        if (section == null && !SECTIONS.containsKey(key)) {
            section = (Object) acquire.invokeExact(world, 0, sx, sy, sz);
            SECTIONS.put(key, section);
            if (SECTIONS.size() > CACHED_SECTIONS) {
                Iterator<Map.Entry<Long, Object>> oldest = SECTIONS.entrySet().iterator();
                Object evicted = oldest.next().getValue();
                oldest.remove();
                if (evicted != null) {
                    int ignored = (int) release.invokeExact(evicted);
                }
            }
        }
        return section == null ? null : (long[]) data.invokeExact(section);
    }

    private static void releaseAll() {
        try {
            for (Object section : SECTIONS.values()) {
                if (section != null) {
                    int ignored = (int) release.invokeExact(section);
                }
            }
        } catch (Throwable e) {
            fail(e);
        }
        SECTIONS.clear();
        cachedWorld = null;
    }

}
