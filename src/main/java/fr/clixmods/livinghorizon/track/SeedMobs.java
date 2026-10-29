package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.ServerConfig;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
//? if >=26.3 {
/*import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
*///?}
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The animals the world puts in a chunk as it generates it, worked out from the seed for the
 * chunks it has not generated yet: land nobody has explored is not empty from afar, and the
 * animals stand about where the game will put them once someone gets there.
 *
 * <p>The game places a chunk's first animals with a random of its own, seeded by the world's
 * seed and the chunk's position alone ({@code NoiseBasedChunkGenerator.spawnOriginalMobs}).
 * The same draws are made here, on the biome the generator gives and on the bare terrain of
 * its noise (no grass, trees or snow yet): a column topped with water is no place for an
 * animal, any other is. Where the terrain says otherwise the herd is off by a few blocks or
 * missing, as is the look (colour, coat, age): the game draws it with a random that nothing
 * can foresee, so it is drawn here from the mob's own id, with the game's odds.
 *
 * <p>A chunk is left out as soon as it has its animals: loaded, or saved past the step that puts
 * them there (a chunk the game only began, around one it generated, still gets them). Its own mobs
 * are there, the server remembers them ({@link PositionPublisher}) and they take the place of
 * those foreseen. Only the overworld's kind of terrain is foreseen: a dimension with a ceiling,
 * a flat or debug world, or {@code doMobSpawning} off, has nothing.
 *
 * <p>Each foreseen mob has an id of its own, the same every time (a name-based one, version 3,
 * that no mob the game makes has: {@link #foreseen}), so a client keeps them apart.
 *
 * <p>The work is done on a thread of its own, a pass every few seconds; the server's thread
 * only reads what the last pass found. Nothing here reaches a class of the client's.
 */
public final class SeedMobs {
    /** Chunks worked out in a pass at most, the nearest a player first: the others in the next passes. */
    private static final int CHUNKS_PER_PASS = 2048;
    /** Passes a region file is kept unread before it is forgotten. */
    private static final int REGION_PASSES = 12;
    /** Saved chunks asked for their status in a pass at most. */
    private static final int SCANS_PER_PASS = 512;
    /**
     * What a chunk saved before it got its first animals says it is at: those around a generated
     * one, begun by the game and left there. Any other status, a mod's or a newer one, is taken
     * as generated.
     */
    private static final Set<String> BEFORE_ANIMALS = Set.of("empty", "structure_starts", "structure_references",
            "biomes", "noise", "surface", "carvers", "liquid_carvers", "features", "initialize_light", "light",
            "heightmaps");
    private static final byte BEGUN = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Living Horizon seed mobs");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    /** What the last pass found, per dimension: the chunks not generated yet that have animals. */
    private volatile Map<String, Long2ObjectMap<List<PositionsMessage.Mob>>> found = Map.of();
    /** Chunks the server's thread saw loaded, per dimension: generated, whatever their region file says yet. */
    private final Map<String, LongOpenHashSet> loaded = new HashMap<>();
    private @Nullable Future<?> pass;
    private boolean failed;

    // The worker's own.
    private final Map<String, Dimension> dimensions = new HashMap<>();
    private int passes;

    /** One pass's work in a dimension: where its players are, and how to make its terrain. */
    private record Area(String dimension, Source source, List<double[]> centers, int range, long[] loaded) {
    }

    /** What the generator of a dimension needs, taken on the server's thread. */
    private record Source(ServerLevel level, ChunkGenerator generator, RandomState random, long seed, Path regions) {
        static @Nullable Source of(MinecraftServer server, ServerLevel level) {
            if (level.dimensionType().hasCeiling() || !spawnsMobs(level)) return null;
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            if (!(generator instanceof NoiseBasedChunkGenerator noise) || noise.generatorSettings().value().disableMobGeneration()) {
                return null;
            }
            Path regions = DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT))
                    .resolve("region");
            return new Source(level, generator, level.getChunkSource().randomState(), level.getSeed(), regions);
        }
    }

    /** The worker's memory of a dimension. */
    private static final class Dimension {
        /** Chunks worked out, an empty list for none, while a player is near. */
        final Long2ObjectOpenHashMap<List<PositionsMessage.Mob>> chunks = new Long2ObjectOpenHashMap<>();
        /** Chunks found generated, while a player is near. */
        final LongOpenHashSet generated = new LongOpenHashSet();
        final Long2ObjectOpenHashMap<Region> regions = new Long2ObjectOpenHashMap<>();
        /** Saved chunks found begun only ({@link #BEGUN}): they get their animals later, foreseen meanwhile. */
        final Long2ByteOpenHashMap status = new Long2ByteOpenHashMap();
        /** Saved chunks asked for their status, not answered yet: taken as generated meanwhile. */
        final LongOpenHashSet asking = new LongOpenHashSet();
        /** The answers, from the game's storage thread: whether the chunk got its animals. */
        final Map<Long, Boolean> answers = new ConcurrentHashMap<>();
        int scans;
    }

    /** Which chunks a region file holds, from its header, read again when the file changes. */
    private static final class Region {
        final BitSet chunks = new BitSet(1024);
        long modified = Long.MIN_VALUE;
        int pass = -1;

        /** True when the file changed since it was last read. */
        boolean refresh(Path file) {
            long now;
            try {
                now = Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : Long.MIN_VALUE;
            } catch (IOException e) {
                return false;
            }
            if (now == modified) return false;
            modified = now;
            if (now == Long.MIN_VALUE) {
                chunks.clear();
                return true;
            }
            // The first 4 KiB: where each of the 1024 chunks is in the file, 0 for one never saved.
            ByteBuffer header = ByteBuffer.allocate(4096);
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                while (header.hasRemaining() && channel.read(header) >= 0) {
                    if (channel.position() >= channel.size()) break;
                }
            } catch (IOException e) {
                // Read again at the next pass.
                modified = Long.MIN_VALUE;
                return false;
            }
            for (int i = 0; i < 1024; i++) {
                if (header.getInt(i * 4) != 0) chunks.set(i);
            }
            return true;
        }
    }

    /**
     * Starts a pass around the players given, unless one is still running. Called on the
     * server's thread every few seconds; the mobs found are there for {@link #addAround} once
     * the pass is done.
     */
    public void update(MinecraftServer server, List<ServerPlayer> viewers, int range) {
        if (pass != null && !pass.isDone()) return;
        pass = null;
        range = Math.min(Math.max(range, 0), ServerConfig.MAX_SEED_MOB_RANGE);
        Map<ServerLevel, List<double[]>> centers = new HashMap<>();
        for (ServerPlayer player : viewers) {
            centers.computeIfAbsent((ServerLevel) player.level(), level -> new ArrayList<>())
                    .add(new double[]{player.getX(), player.getZ()});
        }
        List<Area> areas = new ArrayList<>();
        Map<String, LongOpenHashSet> kept = new HashMap<>();
        for (Map.Entry<ServerLevel, List<double[]>> entry : centers.entrySet()) {
            if (range == 0) break;
            ServerLevel level = entry.getKey();
            Source source;
            try {
                source = Source.of(server, level);
            } catch (RuntimeException e) {
                warn("could not read the generator of " + level.dimension(), e);
                continue;
            }
            if (source == null) continue;
            String dimension = SharedPositions.dimensionId(level.dimension());
            // Only those near a player are worth keeping: the region file says the same of the others.
            LongOpenHashSet seen = loaded.getOrDefault(dimension, new LongOpenHashSet());
            LongOpenHashSet near = new LongOpenHashSet();
            for (LongIterator it = seen.iterator(); it.hasNext(); ) {
                long chunk = it.nextLong();
                if (nearest(entry.getValue(), chunkX(chunk), chunkZ(chunk)) <= range + 256) near.add(chunk);
            }
            kept.put(dimension, near);
            areas.add(new Area(dimension, source, entry.getValue(), range, near.toLongArray()));
        }
        loaded.clear();
        loaded.putAll(kept);
        if (areas.isEmpty()) {
            found = Map.of();
            return;
        }
        pass = WORKER.submit(() -> {
            try {
                run(areas);
            } catch (RuntimeException e) {
                warn("could not work out the mobs of the chunks around", e);
            }
        });
    }

    /**
     * Adds the mobs foreseen within {@code range} blocks of a point of {@code level}, but those of
     * a chunk loaded since: generated, with mobs of its own.
     */
    public void addAround(ServerLevel level, double x, double z, int range, List<PositionsMessage.Mob> into) {
        String dimension = SharedPositions.dimensionId(level.dimension());
        Long2ObjectMap<List<PositionsMessage.Mob>> chunks = found.get(dimension);
        if (chunks == null || range <= 0) return;
        LongOpenHashSet seen = loaded.computeIfAbsent(dimension, d -> new LongOpenHashSet());
        double reach = (double) range * range;
        for (Long2ObjectMap.Entry<List<PositionsMessage.Mob>> entry : chunks.long2ObjectEntrySet()) {
            long chunk = entry.getLongKey();
            int cx = chunkX(chunk), cz = chunkZ(chunk);
            double dx = (cx << 4) + 8 - x, dz = (cz << 4) + 8 - z;
            if (Math.sqrt(dx * dx + dz * dz) > range + 12) continue;
            if (seen.contains(chunk)) continue;
            if (level.getChunkSource().hasChunk(cx, cz) || level.areEntitiesLoaded(chunk)) {
                seen.add(chunk);
                continue;
            }
            for (PositionsMessage.Mob mob : entry.getValue()) {
                double mx = mob.x() - x, mz = mob.z() - z;
                if (mx * mx + mz * mz <= reach) into.add(mob);
            }
        }
    }

    /** Whether a mob is one foreseen here, not one the game made: by its id alone. */
    public static boolean foreseen(UUID id) {
        return id.version() == 3;
    }

    // --- The worker ----------------------------------------------------------------------

    private void run(List<Area> areas) {
        passes++;
        Map<String, Long2ObjectMap<List<PositionsMessage.Mob>>> next = new HashMap<>();
        for (Area area : areas) {
            Dimension dimension = dimensions.computeIfAbsent(area.dimension(), d -> new Dimension());
            for (long chunk : area.loaded()) dimension.generated.add(chunk);
            dimension.scans = 0;
            for (Map.Entry<Long, Boolean> answer : dimension.answers.entrySet()) {
                long chunk = answer.getKey();
                dimension.answers.remove(chunk);
                dimension.asking.remove(chunk);
                if (answer.getValue()) dimension.generated.add(chunk);
                else dimension.status.put(chunk, BEGUN);
            }
            Long2DoubleOpenHashMap wanted = wanted(area);
            Long2ObjectOpenHashMap<List<PositionsMessage.Mob>> shown = new Long2ObjectOpenHashMap<>();
            List<long[]> todo = new ArrayList<>();
            for (Long2DoubleOpenHashMap.Entry entry : wanted.long2DoubleEntrySet()) {
                long chunk = entry.getLongKey();
                if (generated(dimension, area.source(), chunk)) {
                    dimension.chunks.remove(chunk);
                    continue;
                }
                List<PositionsMessage.Mob> mobs = dimension.chunks.get(chunk);
                if (mobs == null) todo.add(new long[]{chunk, Double.doubleToLongBits(entry.getDoubleValue())});
                else if (!mobs.isEmpty()) shown.put(chunk, mobs);
            }
            // The nearest first: those far off come in the next passes.
            todo.sort((a, b) -> Double.compare(Double.longBitsToDouble(a[1]), Double.longBitsToDouble(b[1])));
            BiomeManager biomes = todo.isEmpty() ? null : biomes(area.source());
            for (int i = 0; i < Math.min(todo.size(), CHUNKS_PER_PASS); i++) {
                long chunk = todo.get(i)[0];
                List<PositionsMessage.Mob> mobs = spawns(area, biomes, chunkX(chunk), chunkZ(chunk));
                dimension.chunks.put(chunk, mobs);
                if (!mobs.isEmpty()) shown.put(chunk, mobs);
            }
            // What no player is near any more is forgotten, and worked out again if one comes back.
            dimension.chunks.keySet().removeIf((long chunk) -> !wanted.containsKey(chunk));
            dimension.generated.removeIf((long chunk) -> !wanted.containsKey(chunk));
            dimension.status.keySet().removeIf((long chunk) -> !wanted.containsKey(chunk));
            dimension.regions.values().removeIf(region -> passes - region.pass > REGION_PASSES);
            next.put(area.dimension(), shown);
        }
        dimensions.keySet().retainAll(next.keySet());
        found = next;
    }

    /** The chunks within range of a player, with how far the nearest player is. */
    private static Long2DoubleOpenHashMap wanted(Area area) {
        Long2DoubleOpenHashMap wanted = new Long2DoubleOpenHashMap();
        int reach = (area.range() >> 4) + 1;
        for (double[] center : area.centers()) {
            int cx = Mth.floor(center[0]) >> 4, cz = Mth.floor(center[1]) >> 4;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double across = distance(center, cx + dx, cz + dz);
                    if (across > area.range() + 12) continue;
                    long chunk = chunk(cx + dx, cz + dz);
                    if (!wanted.containsKey(chunk) || wanted.get(chunk) > across) wanted.put(chunk, across);
                }
            }
        }
        return wanted;
    }

    /**
     * Whether a chunk has its animals already: loaded, or saved past the step that puts them
     * there. A saved chunk is asked for its status once, in the background, and taken as
     * generated until the answer comes.
     */
    private boolean generated(Dimension dimension, Source source, long chunk) {
        if (dimension.generated.contains(chunk)) return true;
        int cx = chunkX(chunk), cz = chunkZ(chunk);
        long key = chunk(cx >> 5, cz >> 5);
        Region region = dimension.regions.get(key);
        if (region == null) {
            region = new Region();
            dimension.regions.put(key, region);
        }
        // Each file is looked at once a pass at most, and read again only once it changed.
        if (region.pass != passes) {
            region.pass = passes;
            if (region.refresh(source.regions().resolve("r." + (cx >> 5) + "." + (cz >> 5) + ".mca"))) {
                // Saved again: a chunk begun then may be finished now.
                dimension.status.keySet().removeIf((long begun) -> chunkX(begun) >> 5 == cx >> 5 && chunkZ(begun) >> 5 == cz >> 5);
            }
        }
        if (!region.chunks.get((cx & 31) + (cz & 31) * 32)) return false;
        if (dimension.status.get(chunk) == BEGUN) return false;
        if (!dimension.asking.contains(chunk) && dimension.scans < SCANS_PER_PASS) {
            dimension.scans++;
            dimension.asking.add(chunk);
            ask(dimension, source.level(), chunk);
        }
        return true;
    }

    /** The status a chunk was saved at, read by the game's storage thread as far as that field only. */
    private static void ask(Dimension dimension, ServerLevel level, long chunk) {
        CollectFields fields = new CollectFields(new FieldSelector(StringTag.TYPE, "Status"));
        try {
            level.getChunkSource().chunkScanner().scanChunk(new ChunkPos(chunkX(chunk), chunkZ(chunk)), fields)
                    .whenComplete((done, error) -> dimension.answers.put(chunk, error != null || animals(fields.getResult())));
        } catch (RuntimeException e) {
            dimension.answers.put(chunk, true);
        }
    }

    /** Whether a chunk saved with this status got its animals; one that cannot be told is taken as so. */
    private static boolean animals(@Nullable Tag saved) {
        if (!(saved instanceof CompoundTag tag) || !(tag.get("Status") instanceof StringTag status)) return true;
        //? if >=1.21.5 {
        String name = status.value();
        //?} else
        /*String name = status.getAsString();*/
        return !BEFORE_ANIMALS.contains(name.startsWith("minecraft:") ? name.substring(10) : name);
    }

    /**
     * The game's draws for one chunk ({@code NaturalSpawner.spawnMobsForChunkGeneration}), made
     * on the generator's biome and bare terrain. Only the mobs a server shares are kept, but every
     * one is drawn: each takes its share of the random, as in the game.
     */
    private List<PositionsMessage.Mob> spawns(Area area, BiomeManager biomes, int cx, int cz) {
        Source source = area.source();
        ServerLevel level = source.level();
        int minX = cx << 4, minZ = cz << 4;
        Holder<Biome> biome;
        try {
            biome = biome(source, biomes, minX, minZ);
        } catch (RuntimeException e) {
            warn("could not read the biome of a chunk", e);
            return List.of();
        }
        // From 26.3 the animals and their odds are environment attributes: the dimension's, then the biome's.
        //? if >=26.3 {
        /*MobSpawnSettings settings = attribute(level, biome, EnvironmentAttributes.NATURAL_MOB_SPAWNS);
        float probability = attribute(level, noiseBiome(source, biomes, minX, minZ),
                EnvironmentAttributes.CREATURE_WORLD_GEN_SPAWN_PROBABILITY);
        var creatures = settings.getMobsToSpawn(MobCategory.CREATURE);
        *///?} else {
        MobSpawnSettings settings = biome.value().getMobSettings();
        float probability = settings.getCreatureProbability();
        var creatures = settings.getMobs(MobCategory.CREATURE);
        //?}
        if (creatures.isEmpty()) return List.of();

        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setDecorationSeed(source.seed(), minX, minZ);
        Map<Long, Integer> tops = new HashMap<>();
        List<PositionsMessage.Mob> mobs = new ArrayList<>();
        int index = 0;
        while (random.nextFloat() < probability) {
            var picked = creatures.getRandom(random);
            if (picked.isEmpty()) continue;
            MobSpawnSettings.SpawnerData data = picked.get();
            EntityType<?> type = type(data);
            int count = count(data, random);
            int x = minX + random.nextInt(16), z = minZ + random.nextInt(16);
            int startX = x, startZ = z;
            String id = EntityType.getKey(type).toString();
            int kind = MobKinds.kind(id);
            Random group = new Random(mix(source.seed(), cx, cz, index * 31L + 7));
            Look look = new Look(id, biome, group);
            for (int member = 0; member < count; member++) {
                boolean placed = false;
                for (int attempt = 0; !placed && attempt < 4; attempt++) {
                    int y = type.canSummon() ? top(source, tops, x, z) : Integer.MIN_VALUE;
                    if (y != Integer.MIN_VALUE) {
                        // Turned down by its own rules: the game tries the same spot again, with no draw.
                        if (!allowed(id, y, level)) continue;
                        float width = type.getWidth();
                        double px = Mth.clamp(x, minX + width, minX + 16.0 - width);
                        double pz = Mth.clamp(z, minZ + width, minZ + 16.0 - width);
                        random.nextFloat(); // Which way it faces.
                        placed = true;
                        if (kind > 0) {
                            UUID uuid = id(source.seed(), area.dimension(), cx, cz, index);
                            Random own = new Random(uuid.getLeastSignificantBits());
                            mobs.add(new PositionsMessage.Mob(uuid, px, y, pz, kind, area.dimension(),
                                    look.baby(own), look.variant(own), null));
                        }
                        index++;
                    }
                    x += random.nextInt(5) - random.nextInt(5);
                    z += random.nextInt(5) - random.nextInt(5);
                    while (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16) {
                        x = startX + random.nextInt(5) - random.nextInt(5);
                        z = startZ + random.nextInt(5) - random.nextInt(5);
                    }
                }
            }
            index++;
        }
        return mobs.isEmpty() ? List.of() : List.copyOf(mobs);
    }

    /**
     * The biome the game reads for a chunk's first animals: at its corner, at the top of the
     * world, through the same zoom as the game's ({@link BiomeManager}), on the biomes the
     * generator gives, kept to the height of the world as a chunk keeps them.
     */
    private static Holder<Biome> biome(Source source, BiomeManager biomes, int minX, int minZ) {
        return biomes.getBiome(new BlockPos(minX, topY(source.level()), minZ));
    }

    private static BiomeManager biomes(Source source) {
        ServerLevel level = source.level();
        int lowest = QuartPos.fromBlock(level.getMinY());
        int highest = lowest + QuartPos.fromBlock(level.getHeight()) - 1;
        // 26.3 hands out a resolver of its own where a sampler was.
        //? if >=26.3 {
        /*var resolver = source.generator().getBiomeSource().createUncachedResolver(source.random());
        return new BiomeManager((qx, qy, qz) -> resolver.getNoiseBiome(qx, Mth.clamp(qy, lowest, highest), qz),
                BiomeManager.obfuscateSeed(source.seed()));
        *///?} else {
        var biomes = source.generator().getBiomeSource();
        var sampler = source.random().sampler();
        return new BiomeManager((qx, qy, qz) -> biomes.getNoiseBiome(qx, Mth.clamp(qy, lowest, highest), qz, sampler),
                BiomeManager.obfuscateSeed(source.seed()));
        //?}
    }

    //? if >=26.3 {
    /*/^* The biome of the same corner without the zoom: what an attribute that is not read at full resolution takes. ^/
    private static Holder<Biome> noiseBiome(Source source, BiomeManager biomes, int minX, int minZ) {
        return biomes.getNoiseBiomeAtPosition(minX + 0.5, topY(source.level()) + 0.5, minZ + 0.5);
    }

    /^* An attribute where nothing but the dimension and the biome sets it, as in a chunk being generated. ^/
    private static <T> T attribute(ServerLevel level, Holder<Biome> biome, EnvironmentAttribute<T> attribute) {
        T value = attribute.defaultValue();
        value = level.dimensionType().attributes().applyModifier(attribute, value);
        return biome.value().getAttributes().applyModifier(attribute, value);
    }
    *///?}

    /**
     * The height an animal stands at on a column of bare terrain, or {@code MIN_VALUE} where it
     * cannot: water or lava on top, or nothing at all.
     */
    private static int top(Source source, Map<Long, Integer> tops, int x, int z) {
        return tops.computeIfAbsent(chunk(x, z), key -> {
            ServerLevel level = source.level();
            NoiseColumn column = source.generator().getBaseColumn(x, z, level, source.random());
            for (int y = level.getMinY() + level.getHeight() - 1; y >= level.getMinY(); y--) {
                BlockState state = column.getBlock(y);
                if (state.isAir()) continue;
                return state.getFluidState().isEmpty() ? y + 1 : Integer.MIN_VALUE;
            }
            return Integer.MIN_VALUE;
        });
    }

    /**
     * The game's own rules for a kind, where the bare terrain can tell them: a turtle only on a
     * beach low enough. The others want grass, sand or snow, which the bare terrain does not have.
     */
    private static boolean allowed(String type, int y, ServerLevel level) {
        return !type.equals("minecraft:turtle") || y < level.getSeaLevel() + 4;
    }

    /**
     * How a group looks: what its members share (a herd of horses one coat, a family of rabbits
     * one fur), then each one's own, with the game's odds.
     */
    private static final class Look {
        final String type;
        final Holder<Biome> biome;
        final int shared;
        final float babies;
        int members;

        Look(String type, Holder<Biome> biome, Random group) {
            this.type = type;
            this.biome = biome;
            this.shared = switch (type) {
                case "minecraft:horse" -> group.nextInt(7);
                case "minecraft:llama" -> group.nextInt(4);
                case "minecraft:rabbit" -> rabbit(biome, group.nextInt(100));
                default -> 0;
            };
            // The first of a group is never a baby; the game's odds for each one after it.
            this.babies = switch (type) {
                case "minecraft:rabbit", "minecraft:polar_bear", "minecraft:ocelot" -> 1.0F;
                case "minecraft:donkey", "minecraft:mule", "minecraft:panda" -> 0.2F;
                case "minecraft:fox", "minecraft:parrot" -> 0.0F;
                default -> 0.05F;
            };
        }

        boolean baby(Random own) {
            return members++ > 0 && own.nextFloat() <= babies;
        }

        int variant(Random own) {
            return switch (type) {
                case "minecraft:horse" -> shared | own.nextInt(5) << 8;
                case "minecraft:llama", "minecraft:rabbit" -> shared;
                case "minecraft:parrot" -> own.nextInt(5);
                case "minecraft:sheep" -> sheep(biome, own);
                default -> 0;
            };
        }

        /** {@code Rabbit.getRandomRabbitVariant}: brown 0, white 1, black 2, splotched 3, gold 4, salt 5. */
        private static int rabbit(Holder<Biome> biome, int roll) {
            if (biome.is(BiomeTags.SPAWNS_WHITE_RABBITS)) return roll < 80 ? 1 : 3;
            if (biome.is(BiomeTags.SPAWNS_GOLD_RABBITS)) return 4;
            return roll < 50 ? 0 : roll < 90 ? 5 : 2;
        }

        /** {@code SheepColorSpawnRules}, as wool colour ids: most white, brown where warm, black where cold. */
        private static int sheep(Holder<Biome> biome, Random own) {
            int common = 0;
            int[] rare = {15, 7, 8, 12};
            //? if >=1.21.5 {
            if (biome.is(BiomeTags.SPAWNS_WARM_VARIANT_FARM_ANIMALS)) {
                common = 12;
                rare = new int[]{7, 8, 0, 15};
            } else if (biome.is(BiomeTags.SPAWNS_COLD_VARIANT_FARM_ANIMALS)) {
                common = 15;
                rare = new int[]{8, 7, 0, 12};
            }
            //?}
            int roll = own.nextInt(100);
            if (roll < 5) return rare[0];
            if (roll < 10) return rare[1];
            if (roll < 15) return rare[2];
            if (roll < 18) return rare[3];
            return own.nextInt(500) == 0 ? 6 : common;
        }
    }

    /** The id of a foreseen mob: always the same for the same mob of the same world, version 3. */
    private static UUID id(long seed, String dimension, int cx, int cz, int index) {
        ByteBuffer bytes = ByteBuffer.allocate(20 + dimension.length() * 3);
        bytes.putLong(seed).putInt(cx).putInt(cz).putInt(index).put(dimension.getBytes(StandardCharsets.UTF_8));
        return UUID.nameUUIDFromBytes(java.util.Arrays.copyOf(bytes.array(), bytes.position()));
    }

    private static long mix(long seed, int cx, int cz, long salt) {
        long h = seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static double nearest(List<double[]> centers, int cx, int cz) {
        double best = Double.MAX_VALUE;
        for (double[] center : centers) best = Math.min(best, distance(center, cx, cz));
        return best;
    }

    /** From a point to the middle of a chunk, across the ground. */
    private static double distance(double[] center, int cx, int cz) {
        double dx = (cx << 4) + 8 - center[0], dz = (cz << 4) + 8 - center[1];
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void warn(String what, Throwable error) {
        if (failed) return;
        failed = true;
        LOGGER.warn("Living Horizon: {}, the mobs of unexplored land are left out where it fails", what, error);
    }

    // --- What changed between versions ---------------------------------------------------

    /** The game rule that keeps every mob from spawning, a chunk's first animals included. */
    private static boolean spawnsMobs(ServerLevel level) {
        // Before 1.21.11 the game puts them there whatever the rule says.
        //? if >=1.21.11 {
        return level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS);
        //?} else
        /*return true;*/
    }

    /** Where the game reads the biome of a chunk's first animals: the top block of the world. */
    private static int topY(ServerLevel level) {
        //? if >=1.21.2 {
        return level.getMaxY();
        //?} else
        /*return level.getMaxBuildHeight() - 1;*/
    }

    // SpawnerData became a record in 1.21.5.
    private static EntityType<?> type(MobSpawnSettings.SpawnerData data) {
        //? if >=1.21.5 {
        return data.type();
        //?} else
        /*return data.type;*/
    }

    /** How many in a group: one draw, between the two counts; a value provider from 26.3, which draws the same. */
    private static int count(MobSpawnSettings.SpawnerData data, WorldgenRandom random) {
        //? if >=26.3 {
        /*return data.count().sample(random);
        *///?} elif >=1.21.5 {
        return data.minCount() + random.nextInt(1 + data.maxCount() - data.minCount());
        //?} else
        /*return data.minCount + random.nextInt(1 + data.maxCount - data.minCount);*/
    }

    /** A chunk's two coordinates in one number, as the game packs them. */
    static long chunk(int x, int z) {
        return (x & 0xFFFFFFFFL) | (z & 0xFFFFFFFFL) << 32;
    }

    static int chunkX(long chunk) {
        return (int) chunk;
    }

    static int chunkZ(long chunk) {
        return (int) (chunk >>> 32);
    }
}
