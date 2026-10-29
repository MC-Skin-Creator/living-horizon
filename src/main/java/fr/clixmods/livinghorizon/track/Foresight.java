package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

/**
 * The first animals of the land nobody has explored, foreseen by this client ({@link SeedDraws}),
 * on top of those the server foresees around its players and shares ({@link SeedMobs}).
 *
 * <p>Asked for three ways: by itself, everywhere within <i>Mob Render Distance</i>, so that they
 * stand on the far terrain as Voxy or Distant Horizons draws it; through a spyglass, where the
 * player looks, however far; and by hand, in a circle of up to {@link #MAX_RADIUS} chunks
 * ({@link #start}, <i>Guess Nearby Mobs</i>).
 *
 * <p>Who works them out: in a world played alone or hosted on LAN, the server in this game
 * ({@link SeedMobs#foresee}), which knows which chunks are generated; on another server, this
 * client, from the seed that server shares when its owner allows it ({@code shareSeed}) and the
 * game's own generator built again ({@link SeedDraws#vanilla}). This client then only knows the
 * chunks it has held itself: those are left out, their real mobs being there.
 *
 * <p>What is found is kept per chunk in a few numbers a mob ({@link #index}), and only the nearest
 * {@link #MAX_SHOWN} become remembered mobs ({@link #wanted}): a scan of a thousand chunks around
 * finds about a million animals. Kept for the world and dimension the player is in, never saved.
 *
 * <p>On the game's thread but the work, done on threads of its own.
 */
public final class Foresight {
    public static final int MIN_RADIUS = 4;
    /** The top of the settings slider and the command. */
    public static final int MAX_RADIUS = 1024;
    /** Foreseen mobs made remembered mobs at once, the nearest first. */
    static final int MAX_SHOWN = 1500;
    /** Ticks between two automatic requests, or two through the spyglass. */
    private static final int AUTO_EVERY = 20, SCOPE_EVERY = 10;
    /** Chunks in an automatic request; in one part of a scan. */
    private static final int BATCH = 4096, SCAN_BATCH = 65536;
    /** How far the spyglass looks, in chunks, and how wide: about what it shows. */
    private static final int SCOPE_REACH = 256;
    private static final double SCOPE_COS = Math.cos(Math.toRadians(8));
    /** Blocks: how far it foresees by itself when <i>Mob Render Distance</i> has no limit. */
    private static final int UNLIMITED_REACH = 4096;
    /** Longs a mob takes in the index: its id, x and z, then y, kind, variant and age. */
    private static final int LONGS = 4;
    private static final ExecutorService THREAD = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Living Horizon foresight (client)");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    /** Works out the chunks given; the map holds those with animals only. */
    private interface Seer {
        CompletableFuture<Long2ObjectMap<List<PositionsMessage.Mob>>> foresee(long[] chunks, @Nullable IntConsumer progress);
    }

    /** Where the spyglass looks: from the eye, along a line, a few degrees round it. */
    private record Scope(double x, double y, double z, double dx, double dy, double dz) {
        boolean contains(double px, double py, double pz) {
            double vx = px - x, vy = py - y, vz = pz - z;
            double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
            return length > 1 && (vx * dx + vy * dy + vz * dz) / length >= SCOPE_COS;
        }

        /** The same across the ground alone, for a chunk's middle. */
        boolean across(double px, double pz) {
            double vx = px - x, vz = pz - z;
            double flat = Math.sqrt(dx * dx + dz * dz), length = Math.sqrt(vx * vx + vz * vz);
            if (flat < 1e-3) return length < 64;
            return length < 24 || (vx * dx + vz * dz) / (length * flat) >= SCOPE_COS - 0.01;
        }
    }

    /** A scan by hand under way. */
    private static final class Scan {
        final int radius;
        int chunks, done, mobs, told;

        Scan(int radius) {
            this.radius = radius;
        }
    }

    private long world = Long.MIN_VALUE;
    private @Nullable String dimension;
    /** Chunk to its foreseen mobs, {@link #LONGS} longs each; only chunks with some. */
    private final Long2ObjectOpenHashMap<long[]> index = new Long2ObjectOpenHashMap<>();
    /** Chunks asked for already, whatever came of it. */
    private final Worked worked = new Worked();
    /** Bumped when the world or dimension changes: what was asked before is dropped as it comes. */
    private int generation;
    private int clock;
    private @Nullable CompletableFuture<?> asking;
    private @Nullable Scan scan;
    private @Nullable Scope scope;
    private @Nullable String sharedKey;
    private SeedDraws.@Nullable Source shared;
    private @Nullable CompletableFuture<SeedDraws.@Nullable Source> building;

    // --- Every tick ------------------------------------------------------------------------

    void tick(Minecraft minecraft, ClientLevel level, LocalPlayer self, long world, FarConfig config,
              @Nullable SharedPositions pack) {
        String here = SharedPositions.dimensionId(level.dimension());
        if (world != this.world || !here.equals(dimension)) reset(world, here);
        clock++;
        Vec3 eye = self.getEyePosition();
        Vec3 look = self.getViewVector(1f);
        scope = config.seedMobs && self.isScoping() ? new Scope(eye.x, eye.y, eye.z, look.x, look.y, look.z) : null;
        if (clock % 20 == 0) forgetHeld(level, self, minecraft.options.getEffectiveRenderDistance() + 2);
        if (!config.seedMobs || asking != null && !asking.isDone()) return;
        asking = null;
        long[] chunks;
        if (scope != null && clock % SCOPE_EVERY == 0) chunks = cone(scope);
        else if (clock % AUTO_EVERY == 0) chunks = around(self.getX(), self.getZ(), reach(config) / 16 + 1, BATCH);
        else return;
        if (chunks.length == 0) return;
        Seer seer = seer(minecraft, level, pack);
        if (seer == null) return;
        for (long chunk : chunks) worked.mark(chunk);
        int asked = generation;
        asking = seer.foresee(chunks, null).whenComplete((found, error) -> minecraft.execute(() -> {
            if (error != null) LivingHorizonClient.LOGGER.debug("Could not foresee the mobs of the chunks around", error);
            if (found != null && asked == generation) take(found);
        }));
    }

    /** Another world or dimension: nothing found applies there. */
    void reset(long world, @Nullable String dimension) {
        this.world = world;
        this.dimension = dimension;
        index.clear();
        worked.clear();
        generation++;
        asking = null;
        scan = null;
        scope = null;
    }

    /** The chunks this client holds are generated: their real mobs are there, never those foreseen. */
    private void forgetHeld(ClientLevel level, LocalPlayer self, int reach) {
        int cx = self.getBlockX() >> 4, cz = self.getBlockZ() >> 4;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (!level.getChunkSource().hasChunk(cx + dx, cz + dz)) continue;
                long chunk = SeedDraws.chunk(cx + dx, cz + dz);
                index.remove(chunk);
                worked.mark(chunk);
            }
        }
    }

    private void take(Long2ObjectMap<List<PositionsMessage.Mob>> found) {
        for (Long2ObjectMap.Entry<List<PositionsMessage.Mob>> entry : found.long2ObjectEntrySet()) {
            index.put(entry.getLongKey(), pack(entry.getValue()));
        }
    }

    /** Blocks around the player it foresees by itself: as far as mobs are drawn, and a little more. */
    private static int reach(FarConfig config) {
        return config.mobMaxDistance > 0 ? config.mobMaxDistance + 64 : UNLIMITED_REACH;
    }

    // --- Who works them out ----------------------------------------------------------------

    /** Whether anything can be foreseen here: a world played here, or a server sharing its seed for this dimension. */
    public boolean available(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) return false;
        if (minecraft.getSingleplayerServer() != null) return true;
        return ground(FarPlayerTracker.get().shared().seed(), SharedPositions.dimensionId(level.dimension())) != null;
    }

    private @Nullable Seer seer(Minecraft minecraft, ClientLevel level, @Nullable SharedPositions pack) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            ResourceKey<Level> key = level.dimension();
            return (chunks, progress) -> SeedMobs.foresee(server, key, chunks, progress);
        }
        PositionsMessage.Seed seed = pack == null ? null : pack.seed();
        PositionsMessage.SeedGround ground = ground(seed, SharedPositions.dimensionId(level.dimension()));
        if (seed == null || ground == null) return null;
        String key = seed.seed() + "|" + ground;
        if (!key.equals(sharedKey)) {
            sharedKey = key;
            shared = null;
            long number = seed.seed();
            building = CompletableFuture.supplyAsync(() -> {
                try {
                    return SeedDraws.vanilla(ground.dimension(), number, ground.settings(), ground.biomes(), level,
                            level.dimensionType(), level.registryAccess());
                } catch (RuntimeException e) {
                    LivingHorizonClient.LOGGER.warn("Living Horizon: could not build the generator of {} from the shared seed",
                            ground.dimension(), e);
                    return null;
                }
            }, THREAD);
        }
        if (shared == null && building != null && building.isDone()) shared = building.getNow(null);
        SeedDraws.Source source = shared;
        if (source == null) return null;
        return (chunks, progress) -> CompletableFuture.supplyAsync(() -> SeedMobs.draw(source, chunks, progress), THREAD);
    }

    private static PositionsMessage.@Nullable SeedGround ground(PositionsMessage.@Nullable Seed seed, String dimension) {
        if (seed == null) return null;
        for (PositionsMessage.SeedGround ground : seed.dimensions()) {
            if (ground.dimension().equals(dimension)) return ground;
        }
        return null;
    }

    // --- Where to ask ------------------------------------------------------------------------

    /** What to keep of a chunk while going round the rings. */
    private interface Filter {
        boolean keep(int cx, int cz);
    }

    /**
     * The chunks within {@code reach} chunks of a chunk that {@code filter} keeps, ring after ring:
     * the nearest first, with no sorting, and only each ring's edge gone round. {@code most} at most.
     */
    private static long[] rings(int cx, int cz, int reach, int most, Filter filter) {
        LongArrayList chunks = new LongArrayList();
        long reachSquared = (long) reach * reach;
        for (int ring = 0; ring <= reach && chunks.size() < most; ring++) {
            int side = ring == 0 ? 1 : 8 * ring;
            for (int step = 0; step < side && chunks.size() < most; step++) {
                int dx, dz;
                if (ring == 0) {
                    dx = 0;
                    dz = 0;
                } else if (step < 2 * ring) {
                    dx = -ring + step;
                    dz = -ring;
                } else if (step < 4 * ring) {
                    dx = ring;
                    dz = -ring + (step - 2 * ring);
                } else if (step < 6 * ring) {
                    dx = ring - (step - 4 * ring);
                    dz = ring;
                } else {
                    dx = -ring;
                    dz = ring - (step - 6 * ring);
                }
                if ((long) dx * dx + (long) dz * dz > reachSquared) continue;
                if (filter.keep(cx + dx, cz + dz)) chunks.add(SeedDraws.chunk(cx + dx, cz + dz));
            }
        }
        return chunks.toLongArray();
    }

    /** Chunks within {@code reach} chunks of a point not asked for yet, the nearest first, {@code most} at most. */
    private long[] around(double x, double z, int reach, int most) {
        return rings(Mth.floor(x) >> 4, Mth.floor(z) >> 4, reach, most,
                (cx, cz) -> !worked.contains(SeedDraws.chunk(cx, cz)));
    }

    /** Chunks along where the spyglass looks not asked for yet, the nearest first. */
    private long[] cone(Scope scope) {
        return rings(Mth.floor(scope.x()) >> 4, Mth.floor(scope.z()) >> 4, SCOPE_REACH, BATCH,
                (cx, cz) -> scope.across((cx << 4) + 8, (cz << 4) + 8) && !worked.contains(SeedDraws.chunk(cx, cz)));
    }

    // --- By hand ---------------------------------------------------------------------------

    /**
     * Foresees every chunk within {@code radius} chunks, saying so in the chat: the settings
     * button and {@code /livinghorizon foresee}. One at a time.
     */
    public void start(Minecraft minecraft, int radius) {
        ClientLevel level = minecraft.level;
        LocalPlayer self = minecraft.player;
        if (scan != null) {
            tell(minecraft, Component.translatable("livinghorizon.foresee.running"));
            return;
        }
        Seer seer = level == null || self == null ? null : seer(minecraft, level, FarPlayerTracker.get().shared());
        if (seer == null || !FarConfig.get().seedMobs) {
            tell(minecraft, Component.translatable(level != null && available(minecraft) && !FarConfig.get().seedMobs
                    ? "livinghorizon.foresee.off" : "livinghorizon.foresee.unavailable"));
            return;
        }
        int r = Mth.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        Scan running = new Scan(r);
        scan = running;
        int asked = generation;
        double x = self.getX(), z = self.getZ();
        tell(minecraft, Component.translatable("livinghorizon.foresee.started", r));
        int cx = Mth.floor(x) >> 4, cz = Mth.floor(z) >> 4;
        CompletableFuture.runAsync(() -> {
            // Listed here, all of them: those asked for already are asked again, in case.
            long[] all = rings(cx, cz, r, Integer.MAX_VALUE, (any, other) -> true);
            running.chunks = all.length;
            for (int from = 0; from < all.length; from += SCAN_BATCH) {
                if (asked != generation) return;
                long[] part = java.util.Arrays.copyOfRange(all, from, Math.min(all.length, from + SCAN_BATCH));
                int before = from;
                Long2ObjectMap<List<PositionsMessage.Mob>> found = seer.foresee(part,
                        done -> minecraft.execute(() -> progress(minecraft, running, before + done))).join();
                int mobs = 0;
                for (List<PositionsMessage.Mob> list : found.values()) mobs += list.size();
                int counted = mobs, reached = before + part.length;
                minecraft.execute(() -> {
                    if (asked != generation) return;
                    for (long chunk : part) worked.mark(chunk);
                    take(found);
                    running.mobs += counted;
                    progress(minecraft, running, reached);
                });
            }
        }, THREAD).whenComplete((done, error) -> minecraft.execute(() -> {
            if (scan == running) scan = null;
            if (asked != generation) return;
            if (error != null) {
                LivingHorizonClient.LOGGER.warn("Living Horizon: could not foresee the mobs of the chunks around", error);
                tell(minecraft, Component.translatable("livinghorizon.foresee.failed"));
                return;
            }
            tell(minecraft, Component.translatable("livinghorizon.foresee.done", running.mobs, running.radius));
        }));
    }

    /** Every quarter of the way, in the chat. */
    private void progress(Minecraft minecraft, Scan running, int done) {
        running.done = Math.max(running.done, done);
        if (running.chunks < SCAN_BATCH) return;
        int quarter = running.done * 4 / Math.max(1, running.chunks);
        if (quarter <= running.told || quarter >= 4) return;
        running.told = quarter;
        tell(minecraft, Component.translatable("livinghorizon.foresee.progress", quarter * 25));
    }

    private static void tell(Minecraft minecraft, Component message) {
        //? if >=26.1 {
        /*if (minecraft.player != null) minecraft.gui.getChat().addClientSystemMessage(message);
        *///?} else {
        if (minecraft.player != null) minecraft.player.displayClientMessage(message, false);
        //?}
    }

    // --- What becomes a remembered mob -------------------------------------------------------

    /**
     * The foreseen mobs worth remembering now, nearest first, {@link #MAX_SHOWN} at most: those within
     * <i>Mob Render Distance</i>, and before them those where the spyglass looks, however far.
     */
    List<PositionsMessage.Mob> wanted(double x, double y, double z, FarConfig config) {
        if (!config.seedMobs || dimension == null || index.isEmpty()) return List.of();
        List<PositionsMessage.Mob> near = new ArrayList<>();
        List<PositionsMessage.Mob> looked = new ArrayList<>();
        int reach = reach(config);
        double reachSquared = (double) reach * reach;
        int cx = Mth.floor(x) >> 4, cz = Mth.floor(z) >> 4, r = reach / 16 + 1;
        Scope seen = scope;
        if (index.size() < (2 * r + 1) * (2 * r + 1)) {
            for (Long2ObjectMap.Entry<long[]> entry : index.long2ObjectEntrySet()) {
                pick(entry.getValue(), x, y, z, reachSquared, seen, near, looked);
            }
        } else {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    long[] mobs = index.get(SeedDraws.chunk(cx + dx, cz + dz));
                    if (mobs != null) pick(mobs, x, y, z, reachSquared, null, near, looked);
                }
            }
            if (seen != null) {
                for (long chunk : coneChunks(seen)) {
                    long[] mobs = index.get(chunk);
                    if (mobs != null) pick(mobs, x, y, z, 0, seen, near, looked);
                }
            }
        }
        Comparator<PositionsMessage.Mob> nearest = Comparator.comparingDouble(mob -> Math.hypot(mob.x() - x, mob.z() - z));
        looked.sort(nearest);
        near.sort(nearest);
        List<PositionsMessage.Mob> wanted = new ArrayList<>(Math.min(MAX_SHOWN, looked.size() + near.size()));
        for (PositionsMessage.Mob mob : looked) if (wanted.size() < MAX_SHOWN) wanted.add(mob);
        for (PositionsMessage.Mob mob : near) if (wanted.size() < MAX_SHOWN) wanted.add(mob);
        return wanted;
    }

    private long[] coneChunks(Scope scope) {
        return rings(Mth.floor(scope.x()) >> 4, Mth.floor(scope.z()) >> 4, SCOPE_REACH, Integer.MAX_VALUE,
                (cx, cz) -> index.containsKey(SeedDraws.chunk(cx, cz)) && scope.across((cx << 4) + 8, (cz << 4) + 8));
    }

    private void pick(long[] mobs, double x, double y, double z, double reachSquared, @Nullable Scope seen,
                      List<PositionsMessage.Mob> near, List<PositionsMessage.Mob> looked) {
        for (int i = 0; i < mobs.length; i += LONGS) {
            PositionsMessage.Mob mob = unpack(mobs, i);
            double dx = mob.x() - x, dz = mob.z() - z;
            if (seen != null && seen.contains(mob.x(), mob.y() + 0.5, mob.z())) looked.add(mob);
            else if (dx * dx + dz * dz <= reachSquared) near.add(mob);
        }
    }

    /** Whether a point is where the spyglass looks now: drawn there whatever <i>Mob Render Distance</i> says. */
    public boolean inScope(double x, double y, double z) {
        Scope seen = scope;
        return seen != null && seen.contains(x, y, z);
    }

    /** Foreseen mobs kept, for the debug panel and the game tests. */
    public int size() {
        int mobs = 0;
        for (long[] list : index.values()) mobs += list.length / LONGS;
        return mobs;
    }

    /** Whether a scan by hand is under way. */
    public boolean scanning() {
        return scan != null;
    }

    // --- The index's numbers -----------------------------------------------------------------

    private long[] pack(List<PositionsMessage.Mob> mobs) {
        long[] packed = new long[mobs.size() * LONGS];
        for (int i = 0; i < mobs.size(); i++) {
            PositionsMessage.Mob mob = mobs.get(i);
            int at = i * LONGS;
            packed[at] = mob.id().getMostSignificantBits();
            packed[at + 1] = mob.id().getLeastSignificantBits();
            packed[at + 2] = (long) Float.floatToIntBits((float) mob.x()) << 32 | Float.floatToIntBits((float) mob.z()) & 0xFFFFFFFFL;
            packed[at + 3] = ((long) Mth.floor(mob.y()) & 0xFFFF) << 48 | ((long) mob.kind() & 0xFFFF) << 32
                    | ((long) mob.variant() & 0x7FFFFFFFL) << 1 | (mob.baby() ? 1 : 0);
        }
        return packed;
    }

    private PositionsMessage.Mob unpack(long[] packed, int at) {
        long place = packed[at + 2], rest = packed[at + 3];
        double x = Float.intBitsToFloat((int) (place >>> 32)), z = Float.intBitsToFloat((int) place);
        int y = (short) (rest >>> 48);
        int kind = (int) (rest >>> 32) & 0xFFFF;
        int variant = (int) ((rest >>> 1) & 0x7FFFFFFFL);
        return new PositionsMessage.Mob(new UUID(packed[at], packed[at + 1]), x, y, z, kind, dimension,
                (rest & 1) != 0, variant, null);
    }

    /** Chunks asked for, a bit each, in blocks of 32 by 32: a scan of a thousand chunks around takes half a megabyte. */
    private static final class Worked {
        private final Long2ObjectOpenHashMap<long[]> regions = new Long2ObjectOpenHashMap<>();

        void mark(long chunk) {
            int cx = SeedDraws.chunkX(chunk), cz = SeedDraws.chunkZ(chunk);
            long[] bits = regions.computeIfAbsent(SeedDraws.chunk(cx >> 5, cz >> 5), key -> new long[16]);
            int i = (cx & 31) + (cz & 31) * 32;
            bits[i >> 6] |= 1L << (i & 63);
        }

        boolean contains(long chunk) {
            int cx = SeedDraws.chunkX(chunk), cz = SeedDraws.chunkZ(chunk);
            long[] bits = regions.get(SeedDraws.chunk(cx >> 5, cz >> 5));
            if (bits == null) return false;
            int i = (cx & 31) + (cz & 31) * 32;
            return (bits[i >> 6] & 1L << (i & 63)) != 0;
        }

        void clear() {
            regions.clear();
        }
    }
}
