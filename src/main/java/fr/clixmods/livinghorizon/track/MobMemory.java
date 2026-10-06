package fr.clixmods.livinghorizon.track;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The mobs this client has met, left where it last saw them once the server stops
 * sending them, doing a little loop of their own.
 *
 * <p>Nothing runs on the server and no AI runs here: a remembered mob is a copy that was
 * never added to the world, saved as the game saves it (so the horse keeps its coat,
 * the sheep its colour, the wolf its collar), and moved along a small circle now and then
 * while its head looks around. It is a picture of what lives there, not a simulation.
 *
 * <p>Only mobs that stay put are worth remembering: animals do, hostile mobs despawn as
 * soon as nobody is around. Which ones is {@link FarConfig#mobTypes}, plus every mob with
 * a name tag ({@link FarConfig#rememberNamedMobs}). A memory is dropped
 * when the mob is seen dying, or when this client comes back within range of where it
 * was and it is not there any more.
 *
 * <p>Remembered per server across sessions, in {@code config/livinghorizon/mobs/<server>.json}.
 */
public final class MobMemory {
    /** One remembered mob. Gson writes every field that is not transient. */
    public static final class Remembered {
        UUID id;
        String type;
        String dimension;
        double x, y, z;
        float yaw;
        String nbt;
        long seenAt;
        /** Published by the data pack: dropped when the pack drops it, which means it died. */
        boolean fromPack;
        /** Wore a name tag when last seen. */
        boolean named;

        transient @Nullable Entity puppet;
        /** Where it may walk, read from the ground around it; null until read. */
        transient volatile MobPaths.@Nullable Plan plan;
        transient volatile boolean planning;
        transient boolean failed;
        /** Its saved data, being read off the game's thread. */
        transient @Nullable CompletableFuture<CompoundTag> parsed;
        transient @Nullable EntityType<?> entityType;
        transient int missingChecks;
        transient double distance;
        /** Forgotten while still on the list being drawn: not drawn again, nor rebuilt. */
        transient boolean gone;

        public UUID id() { return id; }
        public @Nullable Entity puppet() { return gone ? null : puppet; }
        public boolean waiting() { return !gone && puppet == null && !failed; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public String type() { return type; }
        public boolean fromPack() { return fromPack; }
    }

    private static final Gson GSON = new GsonBuilder().create();
    private static final List<String> DIMENSIONS =
            List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
    /** Bounds the file; the oldest sightings go first. */
    private static final int MAX_REMEMBERED = 2000;
    /** Nanoseconds per tick for building puppets, so that a crowd does not cost one long frame. */
    private static final long BUILD_BUDGET = 2_000_000L;

    private final Map<UUID, Remembered> mobs = new LinkedHashMap<>();
    /** Mobs the server sends right now: never drawn twice. */
    private final Map<UUID, Entity> live = new HashMap<>();
    /**
     * Per kind of mob, the farthest this server was seen to send one, in blocks across:
     * how near a remembered mob must be before its absence means something. Servers differ
     * a lot - vanilla sends animals as far as the view distance, Paper only to 48 blocks.
     */
    private final Map<MobCategory, Double> reach = new EnumMap<>(MobCategory.class);
    /** A mob just left or came back: choose what is drawn on the next tick, not in a second. */
    private boolean chooseSoon;
    /**
     * Pack mobs this client went to look at and did not find, with where the pack had
     * them: not taken back from the pack until it says they moved.
     */
    private final Map<UUID, Double> dismissed = new HashMap<>();
    private List<Remembered> shown = List.of();
    private @Nullable Path file;
    private boolean dirty;
    private int clock;
    private double selfX, selfY, selfZ;
    private boolean selfKnown;

    public List<Remembered> shown() {
        return shown;
    }

    public int size() {
        return mobs.size();
    }

    /** The mobs and boats the server sends right now. */
    public Collection<Entity> live() {
        return live.values();
    }

    /** How far this server was seen sending each kind of mob, for the debug panel. */
    public Map<MobCategory, Double> reach() {
        return reach;
    }

    /** Every remembered mob of a dimension, drawn or not, for the debug view. */
    public List<Remembered> remembered(String dimension) {
        List<Remembered> here = new ArrayList<>();
        for (Remembered mob : mobs.values()) {
            if (mob.dimension.equals(dimension) && !live.containsKey(mob.id)) here.add(mob);
        }
        return here;
    }

    // --- Lifecycle ---------------------------------------------------------------------

    void open(String server) {
        close();
        file = FabricLoader.getInstance().getConfigDir()
                .resolve("livinghorizon").resolve("mobs").resolve(RestingPlayers.safe(server) + ".json");
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            List<Remembered> read = GSON.fromJson(reader, new TypeToken<List<Remembered>>() { }.getType());
            if (read != null) {
                for (Remembered mob : read) {
                    if (mob != null && mob.id != null && mob.type != null && mob.dimension != null) mobs.put(mob.id, mob);
                }
            }
        } catch (IOException | RuntimeException e) {
            LivingHorizonClient.LOGGER.warn("Could not read {}", file, e);
        }
    }

    void close() {
        save();
        mobs.clear();
        live.clear();
        reach.clear();
        shown = List.of();
        file = null;
        selfKnown = false;
    }

    /** Puppets belong to one level. */
    void forgetPuppets() {
        for (Remembered mob : mobs.values()) {
            mob.puppet = null;
            mob.failed = false;
        }
        shown = List.of();
    }

    // --- What the server sends and stops sending ----------------------------------------

    /** Seen live: the memory is out of date, the real one is here. */
    public void onLoad(Entity entity) {
        if (rememberable(entity)) live.put(entity.getUUID(), entity);
        forget(entity.getUUID());
    }

    /** Dropped from memory, and from the frame at once rather than at the next choice. */
    private void forget(UUID id) {
        Remembered mob = mobs.remove(id);
        if (mob == null) return;
        mob.gone = true;
        mob.puppet = null;
        dirty = true;
        chooseSoon = true;
    }

    /** No longer sent: remembered if it walked out of range, forgotten if it died. */
    public void onUnload(Entity entity, ClientLevel level) {
        live.remove(entity.getUUID());
        if (file == null || !rememberable(entity)) return;
        Entity mob = entity;
        FarConfig config = FarConfig.get();
        if (!config.distantMobs || !worthRemembering(mob, config)) return;
        if ((mob instanceof LivingEntity living && living.isDeadOrDying()) || carriesPlayer(mob)) {
            forget(mob.getUUID());
            return;
        }
        // Gone right next to us: killed, despawned, picked up - not out of range.
        if (selfKnown && distance(mob.getX(), mob.getY(), mob.getZ()) < 32) {
            forget(mob.getUUID());
            return;
        }
        CompoundTag tag = snapshot(mob, level);
        if (tag == null) return;

        Remembered memory = new Remembered();
        memory.id = mob.getUUID();
        memory.type = EntityType.getKey(mob.getType()).toString();
        memory.dimension = level.dimension().identifier().toString();
        memory.x = mob.getX();
        memory.y = mob.getY();
        memory.z = mob.getZ();
        memory.yaw = mob.getYRot();
        memory.nbt = tag.toString();
        // Already read: the copy takes over on the next tick, where the real one was.
        memory.parsed = CompletableFuture.completedFuture(tag);
        memory.entityType = mob.getType();
        memory.seenAt = System.currentTimeMillis();
        memory.named = mob.hasCustomName();
        forget(memory.id);
        mobs.put(memory.id, memory);
        chooseSoon = true;
        while (mobs.size() > MAX_REMEMBERED) mobs.remove(mobs.keySet().iterator().next());
        dirty = true;
    }

    /**
     * Mobs, boats - a parked boat vanishes from far away just like an animal - and
     * mannequins, the skinned figures that Distant Friends stands far away as fake players.
     */
    private static boolean rememberable(Entity entity) {
        return entity instanceof Mob || entity instanceof AbstractBoat || entity instanceof Mannequin;
    }

    private static boolean worthRemembering(Entity mob, FarConfig config) {
        if (config.rememberNamedMobs && mob.hasCustomName()) return true;
        return config.mobTypes.contains(EntityType.getKey(mob.getType()).toString());
    }

    /** A mount under another player belongs to that player's copy. */
    private static boolean carriesPlayer(Entity entity) {
        for (Entity passenger : entity.getIndirectPassengers()) {
            if (passenger instanceof AbstractClientPlayer) return true;
        }
        return false;
    }

    /** The mob as the game saves it: variants, colours, equipment, name, age. */
    private static @Nullable CompoundTag snapshot(Entity mob, ClientLevel level) {
        try {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            mob.saveWithoutId(output);
            return output.buildResult();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // --- Every tick --------------------------------------------------------------------

    void tick(ClientLevel level, LocalPlayer self, int renderDistanceBlocks, FarConfig config,
              @Nullable SharedPositions pack, Scoreboard scoreboard) {
        selfX = self.getX();
        selfY = self.getY();
        selfZ = self.getZ();
        selfKnown = true;
        clock++;
        if (!config.distantMobs) {
            shown = List.of();
            return;
        }
        String dimension = level.dimension().identifier().toString();

        if (clock % 20 == 0) {
            if (pack != null) sync(pack, scoreboard);
            learnReach();
            reconcile(level, dimension, renderDistanceBlocks);
            choose(dimension, config);
        } else if (chooseSoon) {
            choose(dimension, config);
        }
        if (clock % 1200 == 0 && dirty) save();

        Set<String> still = new HashSet<>(config.stillMobTypes);
        boolean background = config.optBackgroundBuild;
        long budget = System.nanoTime() + BUILD_BUDGET;
        for (Remembered mob : shown) {
            if (mob.gone) continue;
            if (mob.puppet == null && !mob.failed) {
                // The saved data is read on a background thread; the puppet is made here,
                // where the world is, as long as this tick's budget lasts.
                if (mob.parsed == null) {
                    String nbt = mob.nbt;
                    mob.parsed = background
                            ? CompletableFuture.supplyAsync(() -> parse(nbt), Util.backgroundExecutor())
                            : CompletableFuture.completedFuture(parse(nbt));
                }
                if (mob.parsed.isDone() && (!background || System.nanoTime() < budget)) {
                    build(mob, level);
                }
            }
            if (mob.puppet == null) continue;
            if (mob.puppet instanceof LivingEntity living && !still.contains(mob.type)) {
                MobPaths.Plan plan = walker(living) ? mob.plan : LoopAnimation.free(mob);
                if (plan == null) {
                    // The ground around is read once, before it ever moves.
                    if (!mob.planning) {
                        mob.planning = true;
                        MobPaths.plan(level, mob.x, mob.y, mob.z, living.getBbWidth(), LoopAnimation.seed(mob))
                                .whenComplete((found, error) -> {
                                    mob.plan = found != null ? found : new MobPaths.Stand();
                                    mob.planning = false;
                                });
                    }
                    LoopAnimation.hold(living, mob);
                } else {
                    LoopAnimation.animate(living, mob, clock, plan);
                }
            } else {
                LoopAnimation.hold(mob.puppet, mob);
            }
        }
    }

    /**
     * Takes the data pack's word: where every mob it publishes is now, as last seen by
     * whoever was near it. A mob this client has seen keeps the look it saved; one it
     * never met is built from its kind, variant and age. One the pack stopped publishing
     * died: the server resets the scores of a dead mob.
     */
    private void sync(SharedPositions pack, Scoreboard scoreboard) {
        Set<UUID> published = new HashSet<>();
        for (UUID id : pack.mobs(scoreboard)) {
            SharedPositions.MobReport report = pack.readMob(scoreboard, id);
            String type = report == null ? null : MobKinds.type(report.kind());
            String dimension = report == null || report.dimension() >= DIMENSIONS.size()
                    ? null : DIMENSIONS.get(report.dimension());
            if (type == null || dimension == null) continue;
            published.add(id);
            if (live.containsKey(id)) continue;
            Double gone = dismissed.get(id);
            if (gone != null) {
                if (gone == report.x() + report.y() * 31 + report.z() * 961) continue;
                dismissed.remove(id);
            }

            Remembered mob = mobs.get(id);
            if (mob == null) {
                mob = new Remembered();
                mob.id = id;
                mob.type = type;
                mob.nbt = MobKinds.nbt(type, report.variant(), report.baby());
                mob.seenAt = System.currentTimeMillis();
                mobs.put(id, mob);
            } else if (mob.x == report.x() && mob.y == report.y() && mob.z == report.z()
                    && dimension.equals(mob.dimension) && mob.fromPack) {
                continue;
            }
            mob.plan = null;
            mob.fromPack = true;
            mob.dimension = dimension;
            mob.x = report.x();
            mob.y = report.y();
            mob.z = report.z();
            dirty = true;
        }
        List<UUID> dead = new ArrayList<>();
        for (Remembered mob : mobs.values()) {
            if (mob.fromPack && !published.contains(mob.id) && !live.containsKey(mob.id)) dead.add(mob.id);
        }
        dead.forEach(this::forget);
    }

    /**
     * How far this server sends each kind of mob: the farthest one of them it sends now,
     * if that is farther than any before. A mob carried or carrying is left out: the
     * server sends a whole stack as far as the farthest-seen of its riders.
     */
    private void learnReach() {
        for (Entity entity : live.values()) {
            if (entity.isPassenger() || entity.isVehicle()) continue;
            double across = Math.hypot(entity.getX() - selfX, entity.getZ() - selfZ);
            reach.merge(entity.getType().getCategory(), across, Math::max);
        }
    }

    /**
     * Back within range of a remembered mob and it is not there: it moved on, or died.
     * Within range means well inside where this server was seen sending that kind of mob,
     * not merely inside the loaded chunks: a server that sends animals to 48 blocks only
     * would otherwise wipe out, a few seconds after they appear, every copy between 48
     * blocks and the render distance - to bring them back when the pack next moves them.
     */
    private void reconcile(ClientLevel level, String dimension, int renderDistanceBlocks) {
        List<Remembered> missing = new ArrayList<>();
        for (Remembered mob : mobs.values()) {
            if (!mob.dimension.equals(dimension) || live.containsKey(mob.id)) continue;
            EntityType<?> type = type(mob);
            double range = Math.max(24, Math.min(sent(type), renderDistanceBlocks) - 16);
            boolean inRange = Math.hypot(mob.x - selfX, mob.z - selfZ) < range
                    && level.hasChunk((int) Math.floor(mob.x) >> 4, (int) Math.floor(mob.z) >> 4);
            mob.missingChecks = inRange ? mob.missingChecks + 1 : 0;
            if (mob.missingChecks >= 5) missing.add(mob);
        }
        for (Remembered mob : missing) {
            if (mob.fromPack) dismissed.put(mob.id, mob.x + mob.y * 31 + mob.z * 961);
            forget(mob.id);
        }
    }

    /**
     * How far, in blocks across, the server sends mobs of a type. In a single player world
     * the answer is known: the type's tracking range scaled by the "Entity Distance" slider,
     * which the integrated server applies to what it sends, not only to what is drawn. On
     * another server, the farthest one of that kind it was seen sending.
     */
    private static double sent(@Nullable EntityType<?> type, Map<MobCategory, Double> reach) {
        if (type == null) return 0;
        int range = type.clientTrackingRange() * 16;
        IntegratedServer local = Minecraft.getInstance().getSingleplayerServer();
        if (local != null) return local.getScaledTrackingDistance(range);
        return Math.min(reach.getOrDefault(type.getCategory(), 0.0), range);
    }

    private double sent(@Nullable EntityType<?> type) {
        return sent(type, reach);
    }

    /** The nearest few, in this dimension: the ones drawn until the next choice. */
    private void choose(String dimension, FarConfig config) {
        chooseSoon = false;
        Set<String> wanted = new HashSet<>(config.mobTypes);
        List<Remembered> here = new ArrayList<>();
        for (Remembered mob : mobs.values()) {
            if (!mob.dimension.equals(dimension) || live.containsKey(mob.id)) continue;
            if (!wanted.contains(mob.type) && !(config.rememberNamedMobs && mob.named)) continue;
            mob.distance = distance(mob.x, mob.y, mob.z);
            here.add(mob);
        }
        here.sort(Comparator.comparingDouble(mob -> mob.distance));
        List<Remembered> chosen = here.subList(0, Math.min(here.size(), Math.max(0, config.maxDistantMobs)));
        Set<Remembered> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        kept.addAll(chosen);
        for (Remembered mob : shown) {
            if (!kept.contains(mob)) mob.puppet = null;
        }
        shown = Collections.unmodifiableList(new ArrayList<>(chosen));
    }

    /** A mob that walks on the ground, and so can fall: not one that flies or swims. */
    private static boolean walker(LivingEntity entity) {
        if (entity instanceof FlyingAnimal || entity.isNoGravity()) return false;
        MobCategory category = entity.getType().getCategory();
        return category != MobCategory.WATER_CREATURE && category != MobCategory.WATER_AMBIENT
                && category != MobCategory.UNDERGROUND_WATER_CREATURE && category != MobCategory.AXOLOTLS;
    }

    private static @Nullable EntityType<?> type(Remembered mob) {
        if (mob.entityType == null) mob.entityType = EntityType.byString(mob.type).orElse(null);
        return mob.entityType;
    }

    private static @Nullable CompoundTag parse(String nbt) {
        try {
            return TagParser.parseCompoundFully(nbt);
        } catch (Exception e) {
            return null;
        }
    }

    private void build(Remembered mob, ClientLevel level) {
        try {
            EntityType<?> type = type(mob);
            Entity entity = type == null ? null : type.create(level, EntitySpawnReason.LOAD);
            CompoundTag tag = mob.parsed == null ? null : mob.parsed.join();
            if (entity == null || tag == null) {
                mob.failed = true;
                return;
            }
            entity.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            entity.setPos(mob.x, mob.y, mob.z);
            entity.setOldPosAndRot();
            mob.puppet = entity;
        } catch (Exception e) {
            mob.failed = true;
        }
    }

    private double distance(double x, double y, double z) {
        double dx = x - selfX, dy = y - selfY, dz = z - selfZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Forgets every mob, here and in the file. */
    public void forgetAll() {
        for (Remembered mob : mobs.values()) mob.gone = true;
        mobs.clear();
        dismissed.clear();
        shown = List.of();
        dirty = true;
        save();
    }

    // --- File --------------------------------------------------------------------------

    private void save() {
        if (file == null) return;
        dirty = false;
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(new ArrayList<>(mobs.values()), writer);
            }
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not write {}", file, e);
        }
    }

    /**
     * A loop that looks like a life from far away: most of the time standing and looking
     * around, now and then a slow walk around a small circle that ends where it started.
     * Every mob has its own rhythm, drawn from its UUID, so a herd does not move as one.
     */
    static final class LoopAnimation {
        private LoopAnimation() {
        }

        /** Where it was, facing where it faced, not moving: only its idle animations play. */
        static void hold(Entity puppet, Remembered mob) {
            puppet.setOldPosAndRot();
            puppet.setPos(mob.x, mob.y, mob.z);
            puppet.setYRot(mob.yaw);
            if (puppet instanceof LivingEntity living) {
                living.yBodyRotO = living.yBodyRot;
                living.yHeadRotO = living.yHeadRot;
                living.yBodyRot = mob.yaw;
                living.yHeadRot = mob.yaw;
                living.walkAnimation.update(0f, 0.4f, living.isBaby() ? 3f : 1f);
            }
            puppet.tickCount++;
        }

        static long seed(Remembered mob) {
            return mob.id.getMostSignificantBits() ^ mob.id.getLeastSignificantBits();
        }

        /** The circle a flying or swimming mob takes: nothing to fall from. */
        static MobPaths.Plan free(Remembered mob) {
            long seed = seed(mob);
            return new MobPaths.Circle(1.2 + Math.floorMod(seed >> 25, 100L) / 100.0, Math.toRadians(mob.yaw),
                    ((seed >> 33) & 1) == 0 ? 1 : -1);
        }

        /**
         * One tick of a mob's loop: a walk along its plan now and then - around a circle,
         * to and fro, or only turning on the spot where there is no room - and between
         * walks, standing, looking around, grazing for the animals that do.
         */
        static void animate(LivingEntity puppet, Remembered mob, int clock, MobPaths.Plan plan) {
            long seed = seed(mob);
            int period = 300 + (int) Math.floorMod(seed, 400L);            // 15 to 35 seconds
            int walk = 100 + (int) Math.floorMod(seed >> 9, 80L);          // 5 to 9 seconds of it
            int offset = (int) Math.floorMod(seed >> 17, (long) period);
            int t = Math.floorMod(clock + offset, period);
            int round = Math.floorDiv(clock + offset, period);

            double x = mob.x, z = mob.z;
            float body = mob.yaw;
            float walking = 0f;
            switch (plan) {
                case MobPaths.Circle c -> {
                    double angle = t < walk ? c.turn() * 2 * Math.PI * t / walk : 0;
                    double cx = mob.x - c.radius() * Math.cos(c.start()), cz = mob.z - c.radius() * Math.sin(c.start());
                    x = cx + c.radius() * Math.cos(c.start() + angle);
                    z = cz + c.radius() * Math.sin(c.start() + angle);
                    double vx = -Math.sin(c.start() + angle) * c.turn(), vz = Math.cos(c.start() + angle) * c.turn();
                    body = (float) Math.toDegrees(Math.atan2(-vx, vz));
                    if (t < walk) walking = (float) Math.min(2 * Math.PI * c.radius() / walk * 4.0, 1.0);
                }
                case MobPaths.Pace p -> {
                    double heading = p.angle();
                    if (t < walk) {
                        // Out, a pause at the far end hidden in the easing, and back.
                        double u = t / (double) walk;
                        boolean out = u < 0.5;
                        double s = out ? u * 2 : (1 - u) * 2;
                        double d = p.length() * s * s * (3 - 2 * s);
                        x = mob.x + Math.sin(p.angle()) * d;
                        z = mob.z + Math.cos(p.angle()) * d;
                        if (!out) heading += Math.PI;
                        walking = (float) Math.min(2 * p.length() / walk * 4.0 * 1.5, 1.0);
                    }
                    body = (float) Math.toDegrees(Math.atan2(-Math.sin(heading), Math.cos(heading)));
                }
                case MobPaths.Stand s -> {
                    // No room to walk: a slow turn on the spot now and then, a different one each time.
                    float target = (float) (Math.floorMod(seed >> 3 + round, 5L) - 2) * 35f;
                    float progress = (float) Mth.clamp(t / 40.0, 0.0, 1.0);
                    body = mob.yaw + target * progress;
                }
            }

            // Between walks, every other time, an animal lowers its head to graze.
            boolean grazing = puppet instanceof Animal && t > walk + 30 && t < period - 30 && round % 2 == 0;
            float pitch = Mth.lerp(0.15f, puppet.getXRot(), grazing ? 40f : 0f);
            float head = grazing ? body : body + (float) (35 * Math.sin((clock + offset) * 0.03) * (t < walk ? 0.3 : 1.0));

            puppet.setOldPosAndRot();
            puppet.yBodyRotO = puppet.yBodyRot;
            puppet.yHeadRotO = puppet.yHeadRot;
            puppet.setPos(x, mob.y, z);
            puppet.setYRot(body);
            puppet.setXRot(pitch);
            puppet.yBodyRot = body;
            puppet.yHeadRot = head;
            puppet.walkAnimation.update(walking, 0.4f, puppet.isBaby() ? 3f : 1f);
            puppet.tickCount++;
        }
    }
}
