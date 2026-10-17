package fr.clixmods.livinghorizon.track;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
//? if >=1.20.5
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A player who logged off, drawn where they are. Their skin comes from a remembered profile,
 * not from the tab list. A picture on this client only: nothing is sent to the server, it
 * blocks nobody and nothing can hurt it.
 *
 * <p>With {@link FarConfig#offlineLife} on, it lives its life within
 * {@link FarConfig#offlineRoam} blocks of the spot where the player logged off - a village -
 * as long as the blocks there are loaded: it strolls, looks around, sits down, stands in
 * front of a chest, a crafting table or a villager's workstation; at night it walks to a bed
 * it can reach, or lies down. Doors and fence gates it walks through. Monsters in sight, it
 * draws a sword and raises its shield (its own when it held them, the tools in its hands put
 * away meanwhile), watches them and keeps out of their way, never fights; safe indoors,
 * behind walls and shut doors, it does not mind them. A creeper blowing up in sight throws
 * it back, shield up, and it walks back. Hit, it raises its shield. In lava or fire it shows
 * fire resistance, under water water breathing. Where the blocks are not loaded it only
 * stands, sits or sleeps where it was.
 *
 * <p>It wears and holds what the player did when last seen up close: armour, weapons, tools.
 *
 * <p>Off, it sits on its spot. Either way it keeps to the ground: break what it rests on and
 * it falls; bury it and it reappears on top. Sitting, its own spot wins again once it has
 * room and something under it, since that is where the player will come back.
 */
public final class RestingPuppet extends RemotePlayer {
    /** How it is drawn. */
    public enum Look { STAND, SIT, LIE, BED }

    private enum Activity { IDLE, SIT, WALK, FACE, SLEEP, BED, GUARD, FLY }

    /** Blocks per tick², the drag and the most it falls in one tick: the game's own. */
    private static final double GRAVITY = 0.08, DRAG = 0.98, TERMINAL = 3.92;
    /** Blocks per tick when it climbs back through open air. */
    private static final double RISE = 0.3;
    /** Ticks it stays on its feet after falling, before sitting back down. */
    private static final int STAND_TICKS = 15;
    /** The game lays a sleeper this high above the block of its bed. */
    private static final double BED_HEIGHT = 0.6875;
    /** Blocks per tick: a stroll, and getting out of a monster's way. */
    private static final double STROLL = 0.11, HURRY = 0.2;
    /** Blocks around it, and spots read at most, when it finds its way back after a blast. */
    private static final int REACH = 10, BUDGET = 700;
    /** Spots read per tick when it looks around or for its way; spots looked beside for beds and chests. */
    private static final int GROW = 250, SCAN = 1500;
    /** Ticks before it reads again what is around: blocks change. */
    private static final int RESURVEY = 600;
    /** Ticks the shield stays up after a hit. */
    private static final int SHIELD_TICKS = 30;
    /** Blocks: monsters this close put it on guard; closer than {@link #TOO_CLOSE}, it steps away. */
    private static final double DANGER = 10, TOO_CLOSE = 3.5;
    /** Ticks without a monster before it puts its sword away. */
    private static final int CALM_TICKS = 80;
    /** Blocks: a creeper blowing up this close throws it. */
    private static final double BLAST = 7;
    /** Colours of the fire resistance and water breathing potions' swirls. */
    private static final int FIRE_RESISTANCE = 0xFF9900, WATER_BREATHING = 0x98DAC0;

    /** What a player stops to look at: a chest, a crafting table, a villager's workstation. */
    private static final Set<Block> INTERESTING = Set.of(
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST, Blocks.CRAFTING_TABLE,
            Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.ANVIL, Blocks.ENCHANTING_TABLE,
            Blocks.COMPOSTER, Blocks.LECTERN, Blocks.CARTOGRAPHY_TABLE, Blocks.BREWING_STAND, Blocks.CAULDRON,
            Blocks.FLETCHING_TABLE, Blocks.GRINDSTONE, Blocks.LOOM, Blocks.SMITHING_TABLE, Blocks.STONECUTTER,
            Blocks.JUKEBOX, Blocks.BELL);

    private final RestingPlayers.Spot spot;
    private final PlayerInfo info;
    private final Random random;
    private final Walkway walkway;

    private boolean placed;
    private boolean lively;
    private double fall;
    private int standing;

    private Activity activity = Activity.IDLE;
    private int timer;
    private @Nullable List<Walkway.Step> path;
    private int pathIndex;
    private Activity then = Activity.IDLE;
    private @Nullable Vec3 focus;
    private @Nullable BlockPos bed;
    private @Nullable Vec3 bedExit;
    private double vx, vy, vz;
    private @Nullable Vec3 anchor;
    private int shieldTicks;
    private boolean armed, raised;
    private int calm, scan, dodge;
    private @Nullable Entity threat;
    private final List<Creeper> fuses = new ArrayList<>();
    private int roam = 50;
    private Walkway.@Nullable Survey survey, known;
    private int scanned, surveyAge;
    private final Map<BlockPos, Sight> nextSights = new LinkedHashMap<>(), nextSleeps = new LinkedHashMap<>();
    private List<Sight> sights = List.of(), sleeps = List.of();
    private Walkway.@Nullable Route route;
    private float headYaw, headPitch, headYawGoal, headPitchGoal;
    private int glance;
    /** What the player held when last seen, given back once the sword and shield are put away. */
    private ItemStack mainHand = ItemStack.EMPTY, offHand = ItemStack.EMPTY;

    RestingPuppet(ClientLevel level, RestingPlayers.Spot spot, GameProfile profile) {
        super(level, profile);
        this.spot = spot;
        this.info = new PlayerInfo(profile, false);
        this.random = new Random(spot.name().hashCode() * 31L + System.nanoTime());
        this.walkway = new Walkway((x, y, z) -> Footing.stand(level, x, y, z));
        setSilent(true);
        getEntityData().set(Avatar.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
        snap(spot.x(), spot.y(), spot.z());
        setYRot(spot.yaw());
        yBodyRot = yBodyRotO = spot.yaw();
        yHeadRot = yHeadRotO = spot.yaw();
        timer = 40 + random.nextInt(80);
        dress(level, spot);
    }

    /** What the player wore and held when last seen up close. */
    private void dress(ClientLevel level, RestingPlayers.Spot spot) {
        if (spot.equipment() == null) return;
        spot.equipment().forEach((name, saved) -> {
            EquipmentSlot slot = ItemNbt.slot(name);
            if (slot != null && saved != null) setItemSlot(slot, ItemNbt.load(saved, level));
        });
        mainHand = getItemBySlot(EquipmentSlot.MAINHAND).copy();
        offHand = getItemBySlot(EquipmentSlot.OFFHAND).copy();
    }

    @Override
    protected @Nullable PlayerInfo getPlayerInfo() {
        return info;
    }

    /** Its name, marked offline: what its name tag says. */
    @Override
    public Component getDisplayName() {
        return Component.empty().append(getName()).append(" ")
                .append(Component.translatable("livinghorizon.offline.tag").withStyle(ChatFormatting.GRAY));
    }

    /** How it is drawn this tick. */
    public Look look() {
        if (!lively) return standing > 0 ? Look.STAND : Look.SIT;
        if (raised && activity != Activity.BED) return Look.STAND;
        return switch (activity) {
            case BED -> Look.BED;
            case SIT -> Look.SIT;
            case SLEEP -> Look.LIE;
            default -> Look.STAND;
        };
    }

    /** In a bed. The bed is its sleeping position, so the game's renderer lays it along the bed. */
    public boolean inBed() {
        return activity == Activity.BED && bed != null;
    }

    /** Shield up. */
    public boolean blocking() {
        return raised;
    }

    /** What it does and what it knows of around it, for the debug tools. */
    public String state() {
        Walkway.Survey reading = survey, map = known;
        return activity.name().toLowerCase(java.util.Locale.ROOT)
                + (route != null ? " (finding its way)" : "")
                + ", area " + (map == null ? "unread" : map.spots().size() + " spots")
                + (reading != null ? " (reading " + reading.spots().size() + ")" : "")
                + ", " + sleeps.size() + " beds, " + sights.size() + " things to look at";
    }

    /** Thrown through the air by a blast. */
    public boolean thrown() {
        return activity == Activity.FLY;
    }

    /** Sword drawn, watching monsters. */
    public boolean guarding() {
        return activity == Activity.GUARD;
    }

    /** The bed it lies in or walks to: one sleeper per bed. */
    @Nullable BlockPos bed() {
        return bed;
    }

    /** Hit by the player: it raises its shield towards them, standing. */
    void strike(Vec3 from) {
        if (!lively || activity == Activity.FLY) return;
        if (activity == Activity.BED || activity == Activity.SIT || activity == Activity.SLEEP) {
            // Out of bed: the bed is free again, the next look for one finds it.
            leaveBedIfIn();
            bed = null;
            start(Activity.IDLE, SHIELD_TICKS + 20);
        }
        shieldTicks = SHIELD_TICKS;
        faceAt(from, 180);
    }

    // --- Once per tick -------------------------------------------------------------------

    /**
     * Lives one tick.
     *
     * @param beds  the beds puppets lie in or walk to: one sleeper per bed
     * @param night whether it is time to sleep
     * @param work  spots all puppets may still read this tick, shared: looking around costs
     */
    void tick(ClientLevel level, FarConfig config, Set<BlockPos> beds, boolean night, int[] work) {
        setOldPosAndRot();
        yBodyRotO = yBodyRot;
        yHeadRotO = yHeadRot;
        tickCount++;
        if (standing > 0) standing--;

        if (lively != config.offlineLife) {
            lively = config.offlineLife;
            calmDown(beds);
            if (!lively) snap(spot.x(), spot.y(), spot.z());
            placed = false;
        }
        if (!lively) {
            Puppets.walk(this, 0f, 1f, 1f);
            sit(level);
            return;
        }

        if (!level.hasChunkAt(blockPosition())) {
            // Nothing known of the blocks here: it only stands, sits or sleeps where it is.
            placed = false;
            afar(night);
            return;
        }
        boolean first = !placed;
        placed = true;
        if (first) settle(level, true);

        if (shieldTicks > 0) shieldTicks--;
        if (tickCount % 5 == 0) swirls(level);
        roam = config.offlineRoam;
        survey(level, work);
        double before = getX(), beforeZ = getZ();

        if (activity == Activity.FLY) {
            fly(level);
        } else {
            watch(level, beds);
            // Thrown just now by a blast: nothing else this tick.
            if (activity == Activity.GUARD) guard(level);
            else if (activity != Activity.FLY) live(level, beds, night, work);
            if (activity != Activity.WALK && activity != Activity.BED && activity != Activity.FLY && path == null) {
                settle(level, false);
            }
        }
        boolean wary = activity == Activity.GUARD || activity == Activity.FLY;
        arm(wary, wary || shieldTicks > 0);
        double moved = Math.hypot(getX() - before, getZ() - beforeZ);
        Puppets.walk(this, (float) Math.min(moved * 4.0, 1.0), 0.4f, 1f);
        setYRot(yBodyRot);
    }

    // --- A life ---------------------------------------------------------------------------

    private void live(ClientLevel level, Set<BlockPos> beds, boolean night, int[] work) {
        if (route != null) routing(level, beds, work);
        switch (activity) {
            case WALK -> {
                if (step(level, STROLL, false)) arrive(level, beds);
                return;
            }
            case BED -> {
                BlockPos head = bed;
                if (!night || head == null || !(level.getBlockState(head).getBlock() instanceof BedBlock)) {
                    leaveBed(beds);
                    start(Activity.IDLE, 40);
                }
                return;
            }
            case SLEEP -> {
                if (!night) start(Activity.IDLE, 60);
            }
            case FACE -> {
                if (focus != null) faceAt(focus, 6);
            }
            default -> {
            }
        }
        if (activity != Activity.SLEEP) glance(activity == Activity.FACE);
        if (--timer <= 0) decide(level, beds, night);
    }

    /** What it does next. */
    private void decide(ClientLevel level, Set<BlockPos> beds, boolean night) {
        if (route != null) return;
        Walkway.Survey map = known;
        if (night) {
            if (map == null) {
                // Still reading what is around: a moment more before giving up on a bed.
                start(Activity.IDLE, 20);
                return;
            }
            if (towardsBed(level, beds)) return;
            start(Activity.SLEEP, 400);
            return;
        }
        int roll = random.nextInt(100);
        if (roll < 30 || map == null) {
            start(Activity.IDLE, 60 + random.nextInt(100));
        } else if (roll < 50) {
            start(Activity.SIT, 200 + random.nextInt(300));
        } else if (!(roll < 72 && towardsSomething()) && !stroll(map)) {
            start(Activity.IDLE, 60 + random.nextInt(60));
        }
    }

    private void start(Activity next, int ticks) {
        activity = next;
        timer = ticks;
        path = null;
        route = null;
        if (next != Activity.FACE) focus = null;
    }

    /** A stroll to somewhere it can reach around its spot. */
    private boolean stroll(Walkway.Survey map) {
        BlockPos at = feet();
        List<Walkway.Step> spots = map.spots();
        for (int tries = 0; tries < 12 && !spots.isEmpty(); tries++) {
            Walkway.Step step = spots.get(random.nextInt(spots.size()));
            if (step.distanceSqr(at.getX(), at.getZ()) < 9) continue;
            return routeTo(step, Activity.IDLE);
        }
        return false;
    }

    /** To stand in front of a chest, a crafting table or a workstation it can reach. */
    private boolean towardsSomething() {
        if (sights.isEmpty()) return false;
        Sight sight = sights.get(random.nextInt(sights.size()));
        focus = Vec3.atCenterOf(sight.block());
        return routeTo(sight.front(), Activity.FACE);
    }

    /** To the nearest free bed it can reach, which it keeps for itself on the way. */
    private boolean towardsBed(ClientLevel level, Set<BlockPos> beds) {
        BlockPos at = feet();
        Sight best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Sight sight : sleeps) {
            BlockPos head = sight.block();
            if (beds.contains(head) || !freeBed(level.getBlockState(head))) continue;
            double distance = head.distSqr(at);
            if (distance < bestDistance) {
                best = sight;
                bestDistance = distance;
            }
        }
        if (best == null) return false;
        bed = best.block();
        beds.add(best.block());
        return routeTo(best.front(), Activity.BED);
    }

    /**
     * Starts looking for the way to a spot, which takes a few ticks across a village; it
     * stays as it is meanwhile, then walks.
     */
    private boolean routeTo(Walkway.Step target, Activity after) {
        BlockPos at = feet();
        BlockPos home = home();
        Walkway.Route found = walkway.route(at.getX(), at.getY(), at.getZ(), target, home.getX(), home.getZ(),
                roam + 4, budget(roam));
        if (found == null) return false;
        start(activity == Activity.SIT ? Activity.IDLE : activity, 400);
        route = found;
        then = after;
        return true;
    }

    /** One tick of looking for the way; once found, off it goes. */
    private void routing(ClientLevel level, Set<BlockPos> beds, int[] work) {
        Walkway.Route searching = route;
        if (searching == null) return;
        int spots = Math.min(work[0], GROW);
        work[0] -= spots;
        if (spots <= 0 || !searching.grow(spots)) return;
        route = null;
        List<Walkway.Step> way = searching.path();
        if (way == null) {
            // No way there after all: the bed it kept is free again.
            if (then == Activity.BED) leaveBed(beds);
            start(Activity.IDLE, 40 + random.nextInt(60));
        } else if (way.isEmpty()) {
            arrive(level, beds);
        } else {
            path = way;
            pathIndex = 0;
            activity = Activity.WALK;
            timer = 0;
        }
    }

    // --- What is around -----------------------------------------------------------------------

    /** A block worth going to, and the spot to stand on in front of it. */
    private record Sight(BlockPos block, Walkway.Step front) {
    }

    /**
     * Reads, a little every tick, where it can walk around its spot and what is there to go
     * to: beds, chests, workstations. Read again now and then, as blocks change.
     */
    private void survey(ClientLevel level, int[] work) {
        surveyAge++;
        if (survey == null && route == null && (known == null || surveyAge > RESURVEY || lost())) {
            walkway.forget();
            BlockPos at = feet();
            BlockPos home = home();
            survey = walkway.survey(at.getX(), at.getY(), at.getZ(), home.getX(), home.getZ(), roam, budget(roam));
            surveyAge = 0;
            scanned = 0;
            nextSights.clear();
            nextSleeps.clear();
            if (survey == null) return;
        }
        Walkway.Survey reading = survey;
        if (reading == null) return;
        if (!reading.done()) {
            int spots = Math.min(work[0], GROW);
            work[0] -= spots;
            if (spots > 0) reading.grow(spots);
            return;
        }
        List<Walkway.Step> spots = reading.spots();
        int end = Math.min(spots.size(), scanned + SCAN);
        for (int i = scanned; i < end; i++) {
            Walkway.Step spot = spots.get(i);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = new BlockPos(spot.x() + side.getStepX(), spot.y(), spot.z() + side.getStepZ());
                BlockState state = level.getBlockState(next);
                if (INTERESTING.contains(state.getBlock())) {
                    nextSights.putIfAbsent(next, new Sight(next, spot));
                } else if (state.getBlock() instanceof BedBlock) {
                    BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? next
                            : next.relative(state.getValue(BedBlock.FACING));
                    nextSleeps.putIfAbsent(head, new Sight(head, spot));
                }
            }
        }
        scanned = end;
        if (end < spots.size()) return;
        known = reading;
        survey = null;
        sights = new ArrayList<>(nextSights.values());
        sleeps = new ArrayList<>(nextSleeps.values());
        // Asleep on the ground or idle for want of knowing better: think again with what it now knows.
        if (activity == Activity.SLEEP || activity == Activity.IDLE) timer = Math.min(timer, 20);
    }

    /**
     * Off the area it knows - fallen, thrown, or its floor broken - while standing still: what
     * it read no longer says where it can go from here. Not more often than every two seconds.
     */
    private boolean lost() {
        Walkway.Survey map = known;
        if (map == null || surveyAge < 40) return false;
        if (activity == Activity.WALK || activity == Activity.BED || activity == Activity.FLY) return false;
        BlockPos at = feet();
        return map.reached(at.getX(), at.getY(), at.getZ()) == null && map.reached(at.getX(), at.getY() + 1, at.getZ()) == null
                && map.reached(at.getX(), at.getY() - 1, at.getZ()) == null;
    }

    /** Spots read at most around its spot: the area of the circle, and as much again for floors. */
    private static int budget(int radius) {
        return (int) Math.min(60_000, Math.PI * radius * radius * 1.6 + 200);
    }

    private BlockPos home() {
        return BlockPos.containing(spot.x(), spot.y(), spot.z());
    }

    /** Where a walk was going, and what it was for. */
    private void arrive(ClientLevel level, Set<BlockPos> beds) {
        path = null;
        switch (then) {
            case FACE -> start(Activity.FACE, 100 + random.nextInt(140));
            case BED -> {
                BlockPos head = bed;
                if (head != null && freeBed(level.getBlockState(head))) {
                    bedExit = position();
                    setSleepingPos(head);
                    snap(head.getX() + 0.5, head.getY() + BED_HEIGHT, head.getZ() + 0.5);
                    activity = Activity.BED;
                } else {
                    leaveBed(beds);
                    start(Activity.IDLE, 40);
                }
            }
            case GUARD -> activity = Activity.GUARD;
            default -> start(Activity.IDLE, 40 + random.nextInt(80));
        }
    }

    /**
     * One tick along the way: a stroll, or backing off from a monster while facing it.
     * True once there; a spot that can no longer be stood on ends the walk where it is.
     */
    private boolean step(ClientLevel level, double speed, boolean backing) {
        List<Walkway.Step> way = path;
        if (way == null || pathIndex >= way.size()) return true;
        Walkway.Step next = way.get(pathIndex);
        double tx = next.x() + 0.5, tz = next.z() + 0.5, ty = next.height();
        double dx = tx - getX(), dz = tz - getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= speed) {
            if (Double.isNaN(Footing.stand(level, next.x(), next.y(), next.z()))) {
                path = null;
                return true;
            }
            setPos(tx, ty, tz);
            pathIndex++;
        } else {
            double y = getY();
            // Up a step at once, as with a jump; down only once over the edge.
            if (ty > y) y = Math.min(ty, y + 0.3);
            else if (ty < y && distance < 0.45) y = Math.max(ty, y - 0.4);
            setPos(getX() + dx / distance * speed, y, getZ() + dz / distance * speed);
        }
        if (!backing && distance > 1e-3) turnTo(yawTowards(dx, dz), 20);
        headYaw = approach(headYaw, 0, 10);
        headPitch = approach(headPitch, 5, 5);
        yHeadRot = yBodyRot + headYaw;
        setXRot(headPitch);
        return pathIndex >= way.size();
    }

    // --- Monsters ---------------------------------------------------------------------------

    /** Monsters around, and creepers about to blow up. */
    private void watch(ClientLevel level, Set<BlockPos> beds) {
        for (int i = fuses.size() - 1; i >= 0; i--) {
            Creeper creeper = fuses.get(i);
            if (creeper.isRemoved()) {
                // Gone while about to blow up: it blew up (the client sees it die of its own blast).
                if (creeper.getSwelling(1f) > 0.6f && seen(level, creeper.position().add(0, 1, 0))) {
                    thrown(creeper.position(), beds);
                }
                fuses.remove(i);
            } else if (creeper.distanceToSqr(this) > BLAST * BLAST * 4) {
                fuses.remove(i);
            }
        }
        if (activity == Activity.FLY || ++scan % 5 != 0) return;
        Entity nearest = null;
        double best = DANGER * DANGER;
        AABB around = getBoundingBox().inflate(DANGER);
        for (Entity entity : level.getEntitiesOfClass(LivingEntity.class, around,
                e -> e instanceof Enemy && e.isAlive() && !e.isSpectator() && seen(level, e.getEyePosition()))) {
            double distance = entity.distanceToSqr(this);
            if (entity instanceof Creeper creeper && creeper.getSwelling(1f) > 0 && !fuses.contains(creeper)
                    && distance < BLAST * BLAST) {
                fuses.add(creeper);
            }
            if (distance < best) {
                best = distance;
                nearest = entity;
            }
        }
        threat = nearest;
        if (nearest != null) {
            calm = CALM_TICKS;
            if (activity != Activity.GUARD) {
                leaveBed(beds);
                start(Activity.GUARD, 0);
            }
        } else if (activity == Activity.GUARD && (calm -= 5) <= 0) {
            start(Activity.IDLE, 60);
        }
    }

    /**
     * In plain sight: nothing solid between its eyes and there - no wall, shut door or window.
     * A monster it cannot see cannot reach it either: safe indoors, it stays calm.
     */
    private boolean seen(ClientLevel level, Vec3 target) {
        return level.clip(new ClipContext(getEyePosition(), target, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** Sword drawn and shield up, it watches the nearest monster and keeps out of its way. */
    private void guard(ClientLevel level) {
        Entity watched = threat;
        if (path != null) {
            boolean there = step(level, HURRY, watched != null);
            if (watched != null) faceAt(watched.getEyePosition(), 25);
            if (there) path = null;
            return;
        }
        if (watched == null) return;
        faceAt(watched.getEyePosition(), 25);
        double distance = Math.sqrt(watched.distanceToSqr(this));
        if (--dodge > 0) return;
        if (distance < TOO_CLOSE) {
            away(level, watched, 4, TOO_CLOSE + 1.5);
            dodge = 20;
        } else if (random.nextInt(60) == 0) {
            // A step aside now and then, round the monster rather than towards it.
            away(level, watched, 2, distance - 0.5);
            dodge = 40;
        }
    }

    /** A spot within {@code within} blocks, at least {@code keep} from the monster; the farthest. */
    private void away(ClientLevel level, Entity from, int within, double keep) {
        BlockPos at = feet();
        Walkway.Survey map = Walkway.around((x, y, z) -> Footing.stand(level, x, y, z), at.getX(), at.getY(), at.getZ(),
                at.getX(), at.getZ(), within, 120);
        if (map == null) return;
        Walkway.Step best = null;
        double bestDistance = keep * keep;
        for (Walkway.Step step : Walkway.shuffled(map.spots(), random)) {
            double dx = step.x() + 0.5 - from.getX(), dz = step.z() + 0.5 - from.getZ();
            double distance = dx * dx + dz * dz;
            if (distance >= bestDistance) {
                best = step;
                bestDistance = distance;
            }
        }
        if (best == null) return;
        path = map.pathTo(best);
        pathIndex = 0;
        if (path.isEmpty()) path = null;
    }

    /** Thrown back by a creeper blowing up, shield up, before walking back. */
    private void thrown(Vec3 blast, Set<BlockPos> beds) {
        double dx = getX() - blast.x, dz = getZ() - blast.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > BLAST) return;
        double strength = 0.5 + (1 - distance / BLAST) * 1.2;
        double nx = distance < 1e-3 ? 1 : dx / distance, nz = distance < 1e-3 ? 0 : dz / distance;
        leaveBed(beds);
        anchor = position();
        vx = nx * strength;
        vz = nz * strength;
        vy = 0.35 + strength * 0.3;
        faceAt(blast, 180);
        start(Activity.FLY, 0);
    }

    /** In the air, after a blast: it lands, then walks back to where it stood. */
    private void fly(ClientLevel level) {
        vy = (vy - GRAVITY) * DRAG;
        vx *= 0.91;
        vz *= 0.91;
        double x = getX(), y = getY(), z = getZ();
        if (Footing.fits(level, x + vx, y, z, Footing.STANDING)) x += vx;
        else vx = 0;
        if (Footing.fits(level, x, y, z + vz, Footing.STANDING)) z += vz;
        else vz = 0;
        boolean landed = false;
        if (vy > 0) {
            if (Footing.fits(level, x, y + vy, z, Footing.STANDING)) y += vy;
            else vy = 0;
        } else {
            double floor = Footing.floorBelow(level, x, y, z);
            if (y + vy <= floor) {
                y = floor;
                landed = true;
            } else {
                y += vy;
            }
        }
        setPos(x, y, z);
        if (!landed) return;
        vx = vy = vz = 0;
        Vec3 back = anchor;
        anchor = null;
        start(Activity.GUARD, 0);
        // On guard long enough to walk back, monsters or not.
        calm = CALM_TICKS;
        if (back == null) return;
        BlockPos at = feet();
        Walkway.Survey map = Walkway.around((bx, by, bz) -> Footing.stand(level, bx, by, bz), at.getX(), at.getY(),
                at.getZ(), at.getX(), at.getZ(), REACH, BUDGET);
        BlockPos target = BlockPos.containing(back.x, back.y + 0.01, back.z);
        Walkway.Step home = map == null ? null : map.reached(target.getX(), target.getY(), target.getZ());
        if (home != null) {
            path = map.pathTo(home);
            pathIndex = 0;
            if (path.isEmpty()) path = null;
        }
    }

    /** Back to its quiet self: sword away, shield down, out of any bed. */
    private void calmDown(Set<BlockPos> beds) {
        leaveBed(beds);
        start(Activity.IDLE, 60);
        shieldTicks = 0;
        threat = null;
        fuses.clear();
        vx = vy = vz = 0;
        anchor = null;
        arm(false, false);
    }

    /**
     * What it holds: a sword drawn, a shield up - the player's own when they held one, an iron
     * sword and a plain shield otherwise. The tools in its hands are put away while either is
     * out, and given back once both are. Changed only when it changes.
     */
    private void arm(boolean sword, boolean shield) {
        if (sword == armed && shield == raised) return;
        boolean wasOut = armed || raised;
        armed = sword;
        raised = shield;
        boolean out = sword || shield;
        if (out) {
            setItemSlot(EquipmentSlot.MAINHAND, sword ? drawnSword() : ItemStack.EMPTY);
            setItemSlot(EquipmentSlot.OFFHAND, shield ? raisedShield() : ItemStack.EMPTY);
        } else if (wasOut) {
            setItemSlot(EquipmentSlot.MAINHAND, mainHand.copy());
            setItemSlot(EquipmentSlot.OFFHAND, offHand.copy());
        }
        // Using the item in the off hand: the game draws the shield raised.
        getEntityData().set(DATA_LIVING_ENTITY_FLAGS, (byte) (shield ? 3 : 0));
    }

    private ItemStack drawnSword() {
        if (ItemNbt.sword(mainHand)) return mainHand.copy();
        if (ItemNbt.sword(offHand)) return offHand.copy();
        return new ItemStack(Items.IRON_SWORD);
    }

    private ItemStack raisedShield() {
        if (offHand.is(Items.SHIELD)) return offHand.copy();
        if (mainHand.is(Items.SHIELD)) return mainHand.copy();
        return new ItemStack(Items.SHIELD);
    }

    // --- Beds -----------------------------------------------------------------------------

    private void leaveBedIfIn() {
        if (activity != Activity.BED) return;
        clearSleepingPos();
        Vec3 exit = bedExit;
        if (exit != null) snap(exit.x, exit.y, exit.z);
        bedExit = null;
    }

    private void leaveBed(Set<BlockPos> beds) {
        leaveBedIfIn();
        if (bed != null) beds.remove(bed);
        bed = null;
        clearSleepingPos();
    }

    /** The head of a bed nobody really sleeps in. */
    private static boolean freeBed(BlockState state) {
        return state.getBlock() instanceof BedBlock
                && state.getValue(BedBlock.PART) == BedPart.HEAD
                && !state.getValue(BedBlock.OCCUPIED);
    }

    // --- Afar -------------------------------------------------------------------------------

    /** Where the blocks are not loaded: standing, sitting or asleep where it is. */
    private void afar(boolean night) {
        Puppets.walk(this, 0f, 1f, 1f);
        if (activity == Activity.BED) return;
        if (activity == Activity.WALK || activity == Activity.GUARD || activity == Activity.FLY
                || activity == Activity.FACE) {
            calmDownQuietly();
        }
        if (night) {
            if (activity != Activity.SLEEP) start(Activity.SLEEP, 400);
            return;
        }
        if (activity == Activity.SLEEP || --timer <= 0) {
            start(activity == Activity.SIT || random.nextBoolean() ? Activity.IDLE : Activity.SIT, 200 + random.nextInt(400));
        }
        glance(false);
    }

    private void calmDownQuietly() {
        start(Activity.IDLE, 60);
        shieldTicks = 0;
        threat = null;
        fuses.clear();
        arm(false, false);
    }

    // --- Looking around ---------------------------------------------------------------------

    /** Its head turning now and then: around, or at what it stands in front of. */
    private void glance(boolean focused) {
        if (!focused && --glance <= 0) {
            headYawGoal = (random.nextFloat() - 0.5f) * 140f;
            headPitchGoal = (random.nextFloat() - 0.3f) * 40f;
            glance = 30 + random.nextInt(70);
        }
        if (!focused) {
            headYaw = approach(headYaw, headYawGoal, 6);
            headPitch = approach(headPitch, headPitchGoal, 4);
        }
        yHeadRot = yBodyRot + headYaw;
        setXRot(headPitch);
    }

    /** Turns towards a point: the body as far as {@code turn} degrees, the head the rest. */
    private void faceAt(Vec3 target, float turn) {
        double dx = target.x - getX(), dz = target.z - getZ();
        double dy = target.y - getEyeY();
        turnTo(yawTowards(dx, dz), turn);
        headYaw = approach(headYaw, wrap(yawTowards(dx, dz) - yBodyRot), 15);
        headPitch = approach(headPitch, (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), 10);
        yHeadRot = yBodyRot + headYaw;
        setXRot(headPitch);
    }

    private void turnTo(float yaw, float max) {
        yBodyRot = yBodyRot + Math.max(-max, Math.min(max, wrap(yaw - yBodyRot)));
    }

    private static float yawTowards(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private static float approach(float from, float to, float max) {
        return from + Math.max(-max, Math.min(max, to - from));
    }

    // --- Fire and water ---------------------------------------------------------------------

    /** The swirls of a potion keeping it safe: fire resistance in lava or fire, water breathing under water. */
    private void swirls(ClientLevel level) {
        int colour;
        if (Footing.inFire(level, getX(), getY(), getZ(), Footing.STANDING)) colour = FIRE_RESISTANCE;
        else if (Footing.underWater(level, getX(), getEyeY(), getZ())) colour = WATER_BREATHING;
        else return;
        for (int i = 0; i < 2; i++) {
            double x = getX() + (random.nextDouble() - 0.5) * 0.6;
            double y = getY() + random.nextDouble() * 1.8;
            double z = getZ() + (random.nextDouble() - 0.5) * 0.6;
            //? if >=1.20.5 {
            level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | colour), x, y, z, 0, 0, 0);
            //?} else
            /*level.addParticle(ParticleTypes.ENTITY_EFFECT, x, y, z, (colour >> 16 & 255) / 255.0, (colour >> 8 & 255) / 255.0, (colour & 255) / 255.0);*/
        }
    }

    // --- Ground -----------------------------------------------------------------------------

    /** The block its feet are in. */
    private BlockPos feet() {
        return BlockPos.containing(getX(), getY() + 0.01, getZ());
    }

    /**
     * Keeps it on the ground where it is: it falls when nothing is under it any more, and
     * reappears on top when buried. Placed at once the first time.
     */
    private void settle(ClientLevel level, boolean now) {
        double height = switch (look()) {
            case SIT -> 1.2;
            case LIE, BED -> 0.5;
            default -> Footing.STANDING;
        };
        double x = getX(), y = getY(), z = getZ();
        double target;
        if (!Footing.fits(level, x, y, z, height)) target = Footing.freeAbove(level, x, y, z, height);
        else if (!Footing.supported(level, x, y, z)) target = Footing.floorBelow(level, x, y, z);
        else target = y;
        move(level, x, z, y, target, height, now);
    }

    /** Sitting on its spot, which it goes back to once it has room and something under it again. */
    private void sit(ClientLevel level) {
        if (!level.hasChunkAt(BlockPos.containing(spot.x(), spot.y(), spot.z()))) {
            placed = false;
            return;
        }
        boolean first = !placed;
        placed = true;
        double x = spot.x(), z = spot.z(), height = 1.2;
        double y = getY();
        double target;
        if (Footing.fits(level, x, spot.y(), z, height) && Footing.supported(level, x, spot.y(), z)) {
            target = spot.y();
        } else if (!Footing.fits(level, x, y, z, height)) {
            target = Footing.freeAbove(level, x, y, z, height);
        } else if (!Footing.supported(level, x, y, z)) {
            target = Footing.floorBelow(level, x, y, z);
        } else {
            target = y;
        }
        move(level, x, z, y, target, height, first);
    }

    private void move(ClientLevel level, double x, double z, double y, double target, double height, boolean now) {
        if (target == y) {
            fall = 0;
            return;
        }
        if (now || !Footing.clear(level, x, y, target, z, height)) {
            // Nothing to see on the way: through blocks, it simply reappears.
            fall = 0;
            snap(x, target, z);
            return;
        }
        Motion step = Motion.step(y, fall, target);
        fall = step.fall();
        if (step.y() != y) {
            setPos(x, step.y(), z);
            standing = STAND_TICKS;
        }
    }

    /** One tick of movement towards where it rests: it falls like anyone, and climbs steadily. */
    record Motion(double y, double fall) {
        static Motion step(double y, double fall, double target) {
            if (Math.abs(target - y) < 1e-4) return new Motion(target, 0);
            if (target > y) return new Motion(Math.min(target, y + RISE), 0);
            double speed = Math.min(TERMINAL, (fall + GRAVITY) * DRAG);
            double next = y - speed;
            return next <= target ? new Motion(target, 0) : new Motion(next, speed);
        }
    }

    private void snap(double x, double y, double z) {
        setPos(x, y, z);
        setOldPosAndRot();
    }
}
