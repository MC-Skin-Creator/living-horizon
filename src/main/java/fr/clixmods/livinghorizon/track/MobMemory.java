package fr.clixmods.livinghorizon.track;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
//? if >=26.2 {
/*import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.parrot.Parrot;
*///?} else {
import net.minecraft.world.entity.animal.FlyingAnimal;
//?}
//? if >=1.21.9
import net.minecraft.world.entity.decoration.Mannequin;
//? if >=1.21.2
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
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
 * the sheep its colour, the wolf its collar), that plays where it was an animation
 * chosen from the blocks around it ({@link MobPaths}, {@link MobMotion}): a stroll, a pause
 * to graze or look around, and back. It is a picture of what lives there, not a simulation.
 * When the server sends the real mob again, the copy walks up to it first: the real one
 * is only drawn once the copy stands where it is.
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
        /** The world it lives in, told apart by its seed: null in a file from before worlds were told apart. */
        @Nullable Long world;
        double x, y, z;
        float yaw;
        String nbt;
        long seenAt;
        /** Published by the data pack: dropped when the pack drops it, which means it died. */
        boolean fromPack;
        /** Wore a name tag when last seen. */
        boolean named;

        transient @Nullable Entity puppet;
        /** The animation it plays, chosen from the blocks around where it is remembered; null until read. */
        transient volatile MobPaths.@Nullable Choice choice;
        transient volatile boolean planning;
        /** Where its animation took it, where it looks. */
        transient @Nullable MobMotion motion;
        /** The real mob, sent again by the server, that the copy walks up to before it takes over. */
        transient @Nullable Entity handover;
        transient boolean failed;
        /** Its saved data, being read off the game's thread. */
        transient @Nullable CompletableFuture<CompoundTag> parsed;
        transient @Nullable EntityType<?> entityType;
        transient int missingChecks;
        transient double distance;
        /** Forgotten while still on the list being drawn: not drawn again, nor rebuilt. */
        transient boolean gone;
        /** Too small or too far for its animation to show; kept between ticks for the hysteresis. */
        transient boolean still;
        /** When it was last drawn, in nanoseconds; 0 if never. */
        transient long drawnAt;

        /** Said by the renderer each frame it draws this copy. */
        public void drawn() { drawnAt = System.nanoTime(); }

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
    /** A copy not drawn for this long (nanoseconds) is frozen, with {@link FarConfig#optFreezeHidden}. */
    private static final long FREEZE_AFTER = 500_000_000L;
    /** Grounds read per tick, at most: a few thousand blocks each, when the chunks are loaded. */
    private static final int READS_PER_TICK = 6;

    private final Map<UUID, Remembered> mobs = new LinkedHashMap<>();
    /** The render distance, in blocks, as of the last tick. */
    private int renderBlocks;
    /** Mobs the server sends right now: never drawn twice. */
    private final Map<UUID, Entity> live = new HashMap<>();
    /** Real mobs kept out of the frame while their copy walks up to them. */
    private final Map<Entity, Remembered> joining = new IdentityHashMap<>();
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
    private boolean firstVisit;
    /** The level the memory is read for: what unloads from any other one is not remembered. */
    private @Nullable ClientLevel bound;
    /** The seed of the world of {@link #bound}: what a memory must carry to be drawn. */
    private long world;
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

    /** A real mob its copy is still walking up to: not drawn yet, the copy stands for it. */
    public boolean hides(Entity entity) {
        return !joining.isEmpty() && joining.containsKey(entity);
    }

    /** A copy walking up to its real mob: drawn however close it is. */
    public boolean handingOver(Remembered mob) {
        return mob.handover != null;
    }

    /** How far this server was seen sending each kind of mob, for the debug panel. */
    public Map<MobCategory, Double> reach() {
        return reach;
    }

    /** Every remembered mob of a dimension, drawn or not, for the debug view. */
    public List<Remembered> remembered(String dimension) {
        List<Remembered> here = new ArrayList<>();
        for (Remembered mob : mobs.values()) {
            if (mob.dimension.equals(dimension) && isHere(mob) && !live.containsKey(mob.id)) here.add(mob);
        }
        return here;
    }

    // --- Lifecycle ---------------------------------------------------------------------

    void open(String server) {
        close();
        file = Platform.configDir()
                .resolve("livinghorizon").resolve("mobs").resolve(RestingPlayers.safe(server) + ".json");
        // No file yet: the mod has never been in this world.
        firstVisit = !Files.exists(file);
        if (firstVisit) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            List<Remembered> read = GSON.fromJson(reader, new TypeToken<List<Remembered>>() { }.getType());
            if (read != null) {
                for (Remembered mob : read) {
                    // A memory with no world cannot be told from another world's: dropped.
                    if (mob != null && mob.id != null && mob.type != null && mob.dimension != null
                            && mob.world != null) mobs.put(mob.id, mob);
                }
            }
        } catch (IOException | RuntimeException e) {
            LivingHorizonClient.LOGGER.warn("Could not read {}", file, e);
        }
    }

    /** True once per world the mod has never been in before: the cue for a first scan. */
    boolean takeFirstVisit() {
        boolean first = firstVisit;
        firstVisit = false;
        return first;
    }

    void close() {
        save();
        firstVisit = false;
        mobs.clear();
        live.clear();
        joining.clear();
        reach.clear();
        dismissed.clear();
        shown = List.of();
        chooseSoon = false;
        file = null;
        bound = null;
        selfKnown = false;
    }

    /** Puppets belong to one level. */
    void forgetPuppets() {
        for (Remembered mob : mobs.values()) {
            mob.puppet = null;
            mob.motion = null;
            mob.handover = null;
            mob.failed = false;
        }
        joining.clear();
        shown = List.of();
    }

    // --- What the server sends and stops sending ----------------------------------------

    /**
     * Seen live: the memory is out of date, the real one is here. A copy on screen walks
     * up to it first, so that the mob does not jump from where the copy was.
     */
    public void onLoad(Entity entity) {
        if (rememberable(entity)) live.put(entity.getUUID(), entity);
        Remembered mob = mobs.get(entity.getUUID());
        if (mob != null && joins(mob, entity)) {
            mob.handover = entity;
            joining.put(entity, mob);
            return;
        }
        forget(entity.getUUID());
    }

    /** A copy drawn now, on its own feet, not so far from the real one that walking up to it would take long. */
    private boolean joins(Remembered mob, Entity real) {
        return !mob.gone && mob.handover == null && mob.puppet instanceof LivingEntity puppet
                && real instanceof LivingEntity && !real.isPassenger() && !real.isVehicle()
                && shown.contains(mob) && puppet.distanceToSqr(real) < 48 * 48;
    }

    /** Its copy reached the real mob, or cannot any more: the real one takes over. */
    private void handOver(Remembered mob) {
        if (mob.handover != null) joining.remove(mob.handover);
        mob.handover = null;
        if (mobs.get(mob.id) == mob) forget(mob.id);
    }

    /**
     * Which world a level is: its seed, as the server sends it (hashed). Dimensions of one
     * world share it, and so do two names or two addresses that lead to different worlds
     * only by it - a proxy, a multiworld plugin, two saves called the same.
     */
    private static long worldOf(ClientLevel level) {
        return level.getBiomeManager().biomeZoomSeed;
    }

    private boolean isHere(Remembered mob) {
        return mob.world != null && mob.world == world;
    }

    /** Dropped from memory, and from the frame at once rather than at the next choice. */
    private void forget(UUID id) {
        Remembered mob = mobs.remove(id);
        if (mob == null) return;
        if (mob.handover != null) joining.remove(mob.handover);
        mob.handover = null;
        mob.gone = true;
        mob.puppet = null;
        dirty = true;
        chooseSoon = true;
    }

    /** No longer sent: remembered if it walked out of range, forgotten if it died. */
    public void onUnload(Entity entity, ClientLevel level) {
        live.remove(entity.getUUID());
        // Gone again before its copy reached it: that copy carries on from where it got to.
        Remembered joined = joining.remove(entity);
        if (joined != null) joined.handover = null;
        Entity joinedPuppet = joined != null && mobs.get(joined.id) == joined ? joined.puppet : null;
        // The old level's mobs unload while the new one is already being read: they would
        // be filed under the new world.
        if (file == null || (bound != null && level != bound) || !rememberable(entity)) return;
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
        remember(new MobScan.Found(mob.getUUID(), EntityType.getKey(mob.getType()).toString(),
                mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), tag, mob.hasCustomName()),
                level.dimension().identifier().toString(), worldOf(level));
        Remembered fresh = mobs.get(entity.getUUID());
        if (joinedPuppet != null && fresh != null) {
            fresh.puppet = joinedPuppet;
            fresh.motion = joined.motion;
        }
        trim();
    }

    /**
     * What a scan of the world found ({@link MobScan}): every mob of the chunks it read,
     * remembered as if met there. A mob remembered in those chunks and not found any more
     * is gone. Returns how many were found.
     */
    public int adopt(MobScan.Result scan) {
        if (file == null || bound == null) return 0;
        Set<UUID> found = new HashSet<>();
        for (MobScan.Found mob : scan.mobs()) found.add(mob.id());
        List<UUID> gone = new ArrayList<>();
        for (Remembered mob : mobs.values()) {
            if (mob.dimension.equals(scan.dimension()) && isHere(mob) && !found.contains(mob.id) && !live.containsKey(mob.id)
                    && scan.covers(mob.x, mob.z)) {
                gone.add(mob.id);
            }
        }
        gone.forEach(this::forget);
        // Farthest first: past the limit, the oldest go first, so the nearest stay.
        List<MobScan.Found> sorted = new ArrayList<>(scan.mobs());
        sorted.sort(Comparator.comparingDouble((MobScan.Found mob) -> distance(mob.x(), mob.y(), mob.z())).reversed());
        int count = 0;
        for (MobScan.Found mob : sorted) {
            if (live.containsKey(mob.id())) continue;
            dismissed.remove(mob.id());
            remember(mob, scan.dimension(), world);
            count++;
        }
        trim();
        return count;
    }

    private void remember(MobScan.Found mob, String dimension, long world) {
        Remembered memory = new Remembered();
        memory.id = mob.id();
        memory.type = mob.type();
        memory.dimension = dimension;
        memory.world = world;
        memory.x = mob.x();
        memory.y = mob.y();
        memory.z = mob.z();
        memory.yaw = mob.yaw();
        memory.nbt = mob.nbt().toString();
        // Already read: the copy takes over on the next tick, where the real one was.
        memory.parsed = CompletableFuture.completedFuture(mob.nbt());
        memory.seenAt = System.currentTimeMillis();
        memory.named = mob.named();
        forget(memory.id);
        mobs.put(memory.id, memory);
        chooseSoon = true;
        dirty = true;
    }

    private void trim() {
        while (mobs.size() > MAX_REMEMBERED) mobs.remove(mobs.keySet().iterator().next());
    }

    /**
     * Mobs, boats - a parked boat vanishes from far away just like an animal - and
     * mannequins, the skinned figures that Distant Friends stands far away as fake players.
     */
    static boolean rememberable(Entity entity) {
        // Mannequins came in 1.21.9; every boat was one class before 1.21.2.
        //? if >=1.21.9 {
        return entity instanceof Mob || entity instanceof AbstractBoat || entity instanceof Mannequin;
        //?} elif >=1.21.2 {
        /*return entity instanceof Mob || entity instanceof AbstractBoat;
        *///?} else
        /*return entity instanceof Mob || entity instanceof net.minecraft.world.entity.vehicle.Boat;*/
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
            return EntityNbt.save(mob, level);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // --- Every tick --------------------------------------------------------------------

    void tick(ClientLevel level, LocalPlayer self, int renderDistanceBlocks, FarConfig config,
              @Nullable SharedPositions pack, Scoreboard scoreboard) {
        bound = level;
        world = worldOf(level);
        selfX = self.getX();
        selfY = self.getY();
        selfZ = self.getZ();
        selfKnown = true;
        renderBlocks = renderDistanceBlocks;
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
        double pixelsPerRadian = config.optStillTiny ? pixelsPerRadian() : 0;

        // Copies walking up to their real mob, before anything else.
        if (!joining.isEmpty()) {
            List<Remembered> arrived = new ArrayList<>();
            for (Map.Entry<Entity, Remembered> joined : joining.entrySet()) {
                Remembered mob = joined.getValue();
                if (joined.getKey().isRemoved() || !(mob.puppet() instanceof LivingEntity puppet)
                        || motion(mob).follow(puppet, mob, joined.getKey())) {
                    arrived.add(mob);
                }
            }
            arrived.forEach(this::handOver);
        }

        boolean background = config.optBackgroundBuild;
        long budget = System.nanoTime() + BUILD_BUDGET;
        int reads = 0;
        for (Remembered mob : shown) {
            if (mob.gone || mob.handover != null) continue;
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
            if (mob.puppet instanceof LivingEntity living) {
                // Not drawn for a moment: it keeps its place, as a position and nothing more.
                if (config.optFreezeHidden && mob.motion != null && mob.motion.placed()
                        && System.nanoTime() - mob.drawnAt > FREEZE_AFTER) {
                    continue;
                }
                boolean roam = !still.contains(mob.type);
                // Moved by the data pack: the blocks are read again around where it is now.
                MobPaths.Choice choice = mob.choice;
                if (choice != null && !choice.ground().around(mob.x, mob.z)) mob.choice = choice = null;
                if (choice == null && roam && !mob.planning && reads < READS_PER_TICK) {
                    reads++;
                    mob.planning = true;
                    double x = mob.x, y = mob.y, z = mob.z;
                    long seed = seed(mob);
                    CompletableFuture<MobPaths.Choice> read = walker(living)
                            ? MobPaths.plan(level, x, y, z, living.getBbWidth(), seed)
                            : CompletableFuture.completedFuture(MobPaths.free(x, y, z, seed));
                    read.whenComplete((found, error) -> {
                        mob.choice = found != null ? found : MobPaths.stand(x, y, z);
                        mob.planning = false;
                    });
                }
                mob.still = isStill(living, mob, config, pixelsPerRadian);
                boolean tiny = mob.still;
                double handover = Math.max(24, Math.min(sent(type(mob)), renderDistanceBlocks));
                float home = MobMotion.home(Math.hypot(mob.x - selfX, mob.z - selfZ), handover);
                motion(mob).tick(living, mob, choice, clock, roam, tiny, home);
            } else {
                hold(mob.puppet, mob);
            }
        }
    }

    private static long seed(Remembered mob) {
        return mob.id.getMostSignificantBits() ^ mob.id.getLeastSignificantBits();
    }

    private static MobMotion motion(Remembered mob) {
        if (mob.motion == null) mob.motion = new MobMotion(seed(mob));
        return mob.motion;
    }

    /** Where it was, facing where it faced, not moving: a boat, which has no animation. */
    private static void hold(Entity puppet, Remembered mob) {
        puppet.setOldPosAndRot();
        puppet.setPos(mob.x, mob.y, mob.z);
        puppet.setYRot(mob.yaw);
        puppet.tickCount++;
    }

    /** How many pixels a radian is across the screen, as the renderer counts them. */
    private static double pixelsPerRadian() {
        Minecraft minecraft = Minecraft.getInstance();
        double fov = Math.toRadians(minecraft.options.fov().get());
        return Math.max(1, minecraft.getWindow().getHeight()) / (2.0 * Math.tan(fov / 2));
    }

    /**
     * Whether a mob's animation is switched off: past the distance cap, or smaller on screen than
     * the minimum. Once still it resumes only above the minimum plus a quarter and a pixel.
     */
    private static boolean isStill(LivingEntity puppet, Remembered mob, FarConfig config, double pixelsPerRadian) {
        if (config.animationMaxDistance > 0 && mob.distance > config.animationMaxDistance) return true;
        if (pixelsPerRadian <= 0 || config.animationMinPixels <= 0) return false;
        double min = config.animationMinPixels;
        return apparentPixels(puppet, mob, config, pixelsPerRadian) < (mob.still ? min * 1.25 + 1 : min);
    }

    /** Roughly how tall a mob looks on screen, at the distance it was last sorted at. */
    private static double apparentPixels(LivingEntity puppet, Remembered mob, FarConfig config, double pixelsPerRadian) {
        double size = Math.max(0.5, Math.max(puppet.getBbHeight(), puppet.getBbWidth()));
        double apparent = size / Math.max(1, mob.distance) * pixelsPerRadian;
        return Math.max(apparent, config.minApparentPixels);
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
                    && dimension.equals(mob.dimension) && isHere(mob) && mob.fromPack) {
                continue;
            }
            // A mob already drawn is not snapped to its new spot: it walks there (MobMotion).
            mob.fromPack = true;
            mob.world = world;
            mob.dimension = dimension;
            mob.x = report.x();
            mob.y = report.y();
            mob.z = report.z();
            dirty = true;
        }
        List<UUID> dead = new ArrayList<>();
        for (Remembered mob : mobs.values()) {
            if (mob.fromPack && isHere(mob) && !published.contains(mob.id) && !live.containsKey(mob.id)) dead.add(mob.id);
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
    /**
     * How close to the player a copy of this kind of mob cannot be: where the real mob would be
     * sent, and so drawn by the game or by the mod, were it still there. That is how far the
     * server sends this kind (which follows the "Entity Distance" slider in a single player
     * world, and the view distance), within the render distance, less a margin. The simulation
     * distance does not shrink it: mobs past it still stand in the loaded chunks and are sent.
     */
    public double nearRange(Remembered mob) {
        return nearRange(type(mob));
    }

    private double nearRange(@Nullable EntityType<?> type) {
        return Math.max(24, Math.min(sent(type), renderBlocks) - 16);
    }

    private void reconcile(ClientLevel level, String dimension, int renderDistanceBlocks) {
        List<Remembered> missing = new ArrayList<>();
        for (Remembered mob : mobs.values()) {
            if (!mob.dimension.equals(dimension) || !isHere(mob) || live.containsKey(mob.id)) continue;
            EntityType<?> type = type(mob);
            double range = nearRange(type);
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
            if (!mob.dimension.equals(dimension) || !isHere(mob)
                    || (live.containsKey(mob.id) && mob.handover == null)) continue;
            if (!wanted.contains(mob.type) && !(config.rememberNamedMobs && mob.named)) continue;
            mob.distance = distance(mob.x, mob.y, mob.z);
            here.add(mob);
        }
        here.sort(Comparator.comparingDouble(mob -> mob.distance));
        int most = config.maxDistantMobs >= FarConfig.UNLIMITED_MOBS ? here.size() : Math.max(0, config.maxDistantMobs);
        List<Remembered> chosen = here.subList(0, Math.min(here.size(), most));
        Set<Remembered> kept = Collections.newSetFromMap(new IdentityHashMap<>());
        kept.addAll(chosen);
        for (Remembered mob : shown) {
            if (!kept.contains(mob)) mob.puppet = null;
        }
        shown = Collections.unmodifiableList(new ArrayList<>(chosen));
    }

    /** A mob that walks on the ground, and so can fall: not one that flies or swims. */
    private static boolean walker(LivingEntity entity) {
        // 26.2 dropped the interface bees and parrots shared, the only two that had it.
        //? if >=26.2 {
        /*if (entity instanceof Bee || entity instanceof Parrot || entity.isNoGravity()) return false;
        *///?} else {
        if (entity instanceof FlyingAnimal || entity.isNoGravity()) return false;
        //?}
        MobCategory category = entity.getType().getCategory();
        return category != MobCategory.WATER_CREATURE && category != MobCategory.WATER_AMBIENT
                && category != MobCategory.UNDERGROUND_WATER_CREATURE && category != MobCategory.AXOLOTLS;
    }

    private static @Nullable EntityType<?> type(Remembered mob) {
        if (mob.entityType == null) mob.entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(mob.type)).orElse(null);
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
            Entity entity = type == null ? null : Puppets.create(type, level);
            CompoundTag tag = mob.parsed == null ? null : mob.parsed.join();
            if (entity == null || tag == null) {
                mob.failed = true;
                return;
            }
            EntityNbt.load(entity, level, tag);
            if (entity instanceof LivingEntity living) {
                // A puppet never ticks, so a hit taken just before it was remembered would
                // leave it flashing red for good.
                living.hurtTime = 0;
                living.hurtDuration = 0;
                living.deathTime = 0;
            }
            MobMotion motion = mob.motion;
            if (motion != null && motion.placed()) {
                entity.setPos(motion.x(), motion.y(), motion.z());
            } else {
                entity.setPos(mob.x, mob.y, mob.z);
            }
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
        joining.clear();
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
}
