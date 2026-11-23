package fr.clixmods.livinghorizon.track;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.compat.EpicFight;
import fr.clixmods.livinghorizon.compat.LodWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
//? if >=1.20.5
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
//? if >=26.1 {
/*import net.minecraft.world.level.block.FarmlandBlock;
*///?} else
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A player who logged off, drawn where they are. Their skin comes from a remembered profile,
 * not from the tab list. A picture on this client only: nothing is sent to the server, it
 * blocks nobody and nothing can hurt it.
 *
 * <p>With {@link FarConfig#offlineLife} on, it lives its life within
 * {@link FarConfig#offlineRoam} blocks of the spot where the player logged off - a village -
 * as long as the blocks there are loaded: it strolls, looks around, sits down, stands in
 * front of a chest, a crafting table or a villager's workstation; at night it walks to a bed
 * it can reach, or stays on its feet: never lying on the ground. Doors and fence gates it
 * walks through. Monsters in sight, it draws a sword and raises its shield, watches them and
 * keeps out of their way, never fights; safe indoors, behind walls and shut doors, it does
 * not mind them. A creeper blowing up in sight throws it back, shield up, and it walks back.
 * Hit, it raises its shield. At the water's edge it may fish for a while, a line cast, a
 * bobber bobbing and fish biting now and then; never two who are close by at the same time.
 * In lava or fire it shows fire resistance, under water water breathing. Where the blocks
 * are not loaded it only stands or sits where it was, standing at night.
 *
 * <p>When it rains, some of them - six in ten, always the same ones - walk to stand under a
 * roof or a tree they can reach, and stay there while it rains; the others go on as before.
 *
 * <p>In the evening it goes to stand in front of a campfire or a furnace it can reach, each
 * on a spot of its own.
 *
 * <p>Two of them close by may walk up to each other and chat a while, and others join them, up
 * to five in a circle ({@link Chat}): one speaks at a time, each in a way of their own (nodding,
 * explaining with a hand, looking round at the others, laughing), the others turned to them,
 * nodding now and then, laughing with them; now and then nobody speaks. Each leaves when they
 * have had enough; never another group close by.
 *
 * <p>By day, where the place has them, it sees to its crops crouched with seeds in hand, goes up
 * to an animal with the food it likes, opens a chest and rummages in it, reads a book sitting by
 * a lectern or a bookshelf, and eats now and then, by the fire in the evening above all, or as it
 * gets up. Two close by may go to the same thing together, one after the other. In the evening
 * those by the same campfire chat in a circle round it. Doors and gates open as it walks through,
 * on this client only and never with you near them; it greets you with a wave the first time it
 * sees you in a session, steps out of your way when you run at it, and looks at a block placed or
 * broken next to it.
 *
 * <p>Look at it and it looks back at you; facing you, it crouches when you crouch. Walk into
 * it and it is shoved aside, as players push each other; it never pushes back, being a
 * picture on this client only.
 *
 * <p>Off, it sits on its spot. Either way it keeps to the ground: break what it rests on and
 * it falls; bury it and it reappears on top. Sitting, its own spot wins again once it has
 * room and something under it, since that is where the player will come back.
 *
 * <p>All of this only near the camera, within {@link #NEAR_IN} blocks: farther, no life of
 * its own any more, nothing read nor decided, it plays one of the animations distant mobs
 * play, in a loop around where its life left it, never farther from its spot than it roams
 * ({@link RestingLoop}): a walk with pauses, seated in some of them by day, in the bed it
 * already lies in at night.
 *
 * <p>A player who logged off riding something stays on it ({@link RestingMount}): with
 * {@link FarConfig#offlineLife} on, the horse walks, the boat rows and the happy ghast drifts
 * about the spot, the rider looking around; off, both wait on the spot. No bed, no chest
 * then, and no sword: hit, the rider raises a shield.
 *
 * <p>Never left in water: a player who logged off swimming or wading is on the nearest shore
 * within {@link #SHORE} blocks of their spot, living their life or playing their loop from
 * there, or, with no shore that near, in a boat on the surface, rowed as a mount is.
 */
public final class RestingPuppet extends RemotePlayer {
    /** How it is drawn. */
    public enum Look { STAND, SIT, BED, RIDE }

    private enum Activity { IDLE, SIT, WALK, FACE, WARM, FISH, TALK, BED, GUARD, FLY, GARDEN, TEND, READ }

    /**
     * What all of them share this tick: the beds kept, where they fish, every one of them (for
     * a chat, a place by the fire), whether it is night, and whether the evening has come.
     */
    record Around(Set<BlockPos> beds, List<Vec3> fishers, Collection<RestingPuppet> crowd, boolean night,
                  boolean evening, Set<String> greeted) {
    }

    /** Blocks per tick², the drag and the most it falls in one tick: the game's own. */
    private static final double GRAVITY = 0.08, DRAG = 0.98, TERMINAL = 3.92;
    /** Blocks per tick when it climbs back through open air. */
    private static final double RISE = 0.3;
    /** Ticks it stays on its feet after falling, before sitting back down. */
    private static final int STAND_TICKS = 15;
    /** Ticks it stands at night with no bed, looking around, before looking for one again. */
    private static final int AWAKE = 200;
    /** Half the width under the middle of its feet that must stand on something once shoved: less, it goes over the edge. */
    private static final double RIM = 0.15;
    /** Blocks it is shoved aside in a tick at most, out of the way of whoever walks into it. */
    private static final double SHOVE = 0.25;
    /** Ticks a walk may keep running into you before it gives up and goes another way. */
    private static final int BLOCKED = 40;
    /** Blocks between two who fish: one on a stretch of shore, not the whole crowd. */
    static final double FISHERS_APART = 16;
    /** Blocks of open water out from the edge, the bobber at the last one. */
    private static final int CAST = 3;
    /** Ticks it keeps looking at you once you look away. */
    private static final int GAZE = 30;
    /** Degrees: how far it turns its head from its body, and how near facing you counts as facing you. */
    private static final float NECK = 75, FACING = 30;
    /**
     * Blocks: how far it looks for someone to chat with, how far apart two groups keep, and how
     * far from each other two stand; a group of more stands round in a circle as wide.
     */
    private static final double TALK_REACH = 24, TALKERS_APART = 16, TALK_GAP = 2;
    /** Ticks the one called waits for the other to come, and a chat lasts at least. */
    private static final int TALK_WAIT = 600, CHAT = 400;
    /** In a hundred, the chance by day that it goes to something the place has: crops, an animal, a book. */
    private static final int CHORE_CHANCE = 14;
    /**
     * In ten thousand, the chance each tick that it takes a bite, wherever it is, once it has
     * digested its last meal; and ticks a meal lasts it, at least.
     */
    private static final int SNACK_CHANCE = 3, FED = 6000;
    /** Ticks a bite lasts, as the game eats; and ticks between two bites. */
    private static final int BITE = 32, BETWEEN_BITES = 12;
    /** Blocks from you within which it greets you, and within which a door is never opened. */
    private static final double GREET = 9, DOOR_CLEAR = 4;
    /** Blocks: a block placed or broken this near draws its eyes; you running at it this near, it steps aside. */
    private static final double NOTICE = 7, RUN_AT = 6;
    /** Most in one chat. */
    private static final int CHAT_MOST = 5;
    /** In a hundred, the chance it joins a group chatting near rather than calls someone over. */
    private static final int JOIN_CHANCE = 70;
    /** In a hundred, the chance it goes to join a group chatting near, whatever it would do otherwise. */
    private static final int GROUP_PULL = 40;
    /** Ticks a fish takes to come to the bobber, and the bobber stays under once it bites. */
    private static final int WAKE = 40, DIP = 12;
    /** The game lays a sleeper this high above the block of its bed. */
    private static final double BED_HEIGHT = 0.6875;
    /** Blocks per tick: a stroll, and getting out of a monster's way. */
    private static final double STROLL = 0.11, HURRY = 0.2;
    /** Blocks around it, and spots read at most, when it finds its way back after a blast. */
    private static final int REACH = 10, BUDGET = 700;
    /** Blocks below and above its spot copied when it looks around: a village on a hillside. */
    private static final int COPY_BELOW = 32, COPY_ABOVE = 48;
    /** Ticks before it looks around again when it could not. */
    private static final int RETRY = 40;
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
    /**
     * Blocks across from the camera within which it lives its life, and past which it plays
     * its loop again: apart, so that it does not switch to and fro on the edge.
     */
    static final double NEAR_IN = 48, NEAR_OUT = 64;
    /** Blocks across: how far from the water it looks for a shore before taking a boat. */
    private static final int SHORE = 10;
    /** Ticks between two looks for a shore, and between two looks at the water under its boat. */
    private static final int SHORE_WAIT = 20;
    /** The surface of still water open to the sky, above the bottom of its block. */
    private static final double WATER_TOP = 8 / 9.0;
    /** Colours of the fire resistance and water breathing potions' swirls. */
    private static final int FIRE_RESISTANCE = 0xFF9900, WATER_BREATHING = 0x98DAC0;

    /** What a player stops to look at: a chest, a crafting table, a villager's workstation. */
    private static final Set<Block> INTERESTING = Set.of(
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST, Blocks.CRAFTING_TABLE,
            Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.ANVIL, Blocks.ENCHANTING_TABLE,
            Blocks.COMPOSTER, Blocks.LECTERN, Blocks.CARTOGRAPHY_TABLE, Blocks.BREWING_STAND, Blocks.CAULDRON,
            Blocks.FLETCHING_TABLE, Blocks.GRINDSTONE, Blocks.LOOM, Blocks.SMITHING_TABLE, Blocks.STONECUTTER,
            Blocks.JUKEBOX, Blocks.BELL);

    /** What it reads beside: a lectern, a shelf of books. */
    private static final Set<Block> BOOKS = Set.of(Blocks.LECTERN, Blocks.BOOKSHELF, Blocks.CHISELED_BOOKSHELF);
    /** The food an animal likes, the first of these it takes; what a player eats. */
    private static final List<ItemStack> FODDER = List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.CARROT),
            new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.BEETROOT), new ItemStack(Items.POTATO));
    private static final List<ItemStack> MEALS = List.of(new ItemStack(Items.BREAD), new ItemStack(Items.APPLE),
            new ItemStack(Items.COOKED_BEEF));

    /** Where it warms itself in the evening: a fire, a furnace. */
    private static final Set<Block> HEARTHS = Set.of(
            Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER);
    /** Campfires, stood round two blocks out; and where a spot two blocks from one lies. */
    private static final Set<Block> CAMPFIRES = Set.of(Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE);
    private static final int[][] AROUND_FIRE = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 1}, {2, -1}, {-2, 1}, {-2, -1},
            {1, 2}, {-1, 2}, {1, -2}, {-1, -2}};
    /** In the evening, the chance in a hundred that it goes to a fire rather than its usual day. */
    private static final int WARM_CHANCE = 60;
    /** In ten players, how many take shelter from the rain; and blocks above a spot looked at for a roof. */
    private static final int SHELTERING = 6, ROOF = 20;

    private RestingPlayers.Spot spot;
    private PlayerInfo info;
    private final Random random;
    private final Footing.Source blocks;
    private final @Nullable RestingMount mount;
    /** The boat it was given, its spot being in water far from any shore. */
    private @Nullable RestingMount boat;
    /** What it rides now: its own mount, or that boat. */
    private @Nullable RestingMount carried;
    /** Its spot being in water: the nearest shore, where it rests instead. */
    private @Nullable Vec3 shore;
    private int shoreWait, dryWait;
    /** Where it was found to rest, from the blocks: kept with its spot ({@link #restFound}). */
    private RestingPlayers.@Nullable Rest rest;
    private boolean restFound;
    /** Its shore or boat guessed from the far world, to be found again from the blocks once loaded. */
    private boolean guessed;
    /** The far world was asked what is under its spot; its answer, once there. */
    private boolean askedFar;
    private LodWorld.@Nullable Surface farSurface;

    private boolean placed;
    private boolean lively;
    private boolean riding;
    private boolean near = true;
    /** Playing its loop, away from the camera. */
    private boolean far;
    private @Nullable RestingLoop loop;
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
    /** Where it can walk around its spot, as last read, and the copy of the blocks it was read from. */
    private Walkway.@Nullable Survey known;
    private @Nullable BlockCopy area;
    /** A new look around: the blocks being copied, a few sections a tick, then read off the game's thread. */
    private BlockCopy.@Nullable Builder copying;
    private @Nullable CompletableFuture<Area> reading;
    private int surveyAge, retry;
    private List<Sight> sights = List.of(), sleeps = List.of(), hearths = List.of();
    /** Spots it can walk to with a roof or leaves above. */
    private List<Walkway.Step> covers = List.of();
    /** One of those who take shelter from the rain, by its name: always the same ones. */
    private final boolean sheltering;
    /** The spot in front of a fire it stands on, or walks to: one each. */
    private @Nullable BlockPos warmAt;
    private List<Shore> shores = List.of();
    /** The water's edge it fishes from, or walks to for that. */
    private @Nullable Shore fishingFrom;
    /** Its bobber while the line is out, drawn with it; ticks to the next bite, or before casting again. */
    private @Nullable FishingHook hook;
    private int bite;
    /** Crops it can tend, books it can read: the spot beside each. */
    private List<Sight> crops = List.of(), reads = List.of();
    /** The animal it sees to, or walks up to. */
    private @Nullable Animal tended;
    /** The chest whose lid it opened, to close again. */
    private @Nullable BlockPos openChest;
    /** Bites left to take, the ticks into this one, and what it eats. */
    private int bites, biting;
    /** Ticks before it is hungry again: a meal lasts it a while. Drawn when made, so that they never all eat at once. */
    private int fed = -1;
    private ItemStack food = ItemStack.EMPTY;
    /** The campfire it stands by, for a circle round it. */
    private @Nullable BlockPos fire;
    /** Ticks left of its wave, and of looking at a block that changed. */
    private int waving, noticing;
    private @Nullable Vec3 noticed;
    /** Ticks before it may step out of your way again. */
    private int dodgeWait;
    /** Everyone resting near the camera, as of this tick: for a circle round a fire. */
    private Collection<RestingPuppet> crowd = List.of();
    /** Doors and gates it opened, to close again once through, as they were. */
    private final List<BlockPos> opened = new ArrayList<>();
    /** One that goes to the same thing as another, a little after: ticks before it sets off. */
    private int followIn;
    private @Nullable Runnable following;
    /** The chat it is in, or walks up to; null when none. */
    private @Nullable Chat chat;
    /** Ticks before it nods while listening, and before it looks at another of the group. */
    private int nodIn, glanceIn;
    private @Nullable RestingPuppet glancedAt;
    /** Your eyes while you look at it, and for {@link #GAZE} ticks after; whether you crouch. */
    private @Nullable Vec3 gazer;
    private int gazed;
    /** You looked at it since its last tick: it crouches with you only then, never from memory. */
    private boolean gazerCrouching, watched, crouched;
    /** Ticks its walk has run into you in a row. */
    private int blocked;
    /** The way to a spot, searched off the game's thread in {@link #area}; empty when there is none. */
    private @Nullable CompletableFuture<Optional<List<Walkway.Step>>> route;
    private float headYaw, headPitch, headYawGoal, headPitchGoal;
    private int glance;
    /** What it held in each hand when its player logged off, given back once the sword and shield are put away. */
    private ItemStack held = ItemStack.EMPTY, heldOff = ItemStack.EMPTY;

    RestingPuppet(ClientLevel level, RestingPlayers.Spot spot, GameProfile profile) {
        super(level, profile);
        this.spot = spot;
        this.info = new PlayerInfo(profile, false);
        this.random = new Random(spot.name().hashCode() * 31L + System.nanoTime());
        this.sheltering = Math.floorMod(MobMotion.mix(spot.name().toLowerCase(java.util.Locale.ROOT).hashCode(), 17), 10L) < SHELTERING;
        this.blocks = Footing.live(level);
        // Each looks for monsters on a tick of its own, not all of them on the same one.
        this.scan = Math.floorMod(spot.name().hashCode(), 5);
        setSilent(true);
        getEntityData().set(Avatar.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
        this.mount = RestingMount.of(level, spot, random);
        // Where it was found to rest before: there at once, near or far, loaded or not.
        rest = spot.rest();
        if (rest != null && rest.boat()) boat = RestingMount.boat(level, spot, rest.y(), random);
        else if (rest != null) shore = rest.at();
        Vec3 at = place();
        snap(at.x, at.y, at.z);
        setYRot(spot.yaw());
        yBodyRot = yBodyRotO = spot.yaw();
        yHeadRot = yHeadRotO = spot.yaw();
        timer = 40 + random.nextInt(80);
        // Dressed as they were when they logged off; the sword and shield it draws are its own.
        Gear.apply(this, spot.gear(), level.registryAccess());
        held = getItemBySlot(EquipmentSlot.MAINHAND).copy();
        heldOff = getItemBySlot(EquipmentSlot.OFFHAND).copy();
    }

    /**
     * The same player, resting in the same place, in a new skin or new gear (fetched from
     * Mojang, or what the server says): dressed again where it stands, never made again,
     * which would put it back where its player logged off.
     */
    void dress(RestingPlayers.Spot now) {
        spot = now;
        info = new PlayerInfo(now.profile(), false);
        boolean sword = armed, shield = raised;
        Gear.apply(this, now.gear(), level().registryAccess());
        held = getItemBySlot(EquipmentSlot.MAINHAND).copy();
        heldOff = getItemBySlot(EquipmentSlot.OFFHAND).copy();
        // The sword and shield it holds now are its own, given back in hand over the new gear.
        armed = false;
        raised = false;
        arm(sword, shield);
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

    /**
     * Seated or riding counts as a passenger for whatever reads the entity rather than its render state
     * (Entity Model Features' Fresh Animations players): otherwise they stand up on the cushion and sink.
     */
    @Override
    public boolean isPassenger() {
        if (super.isPassenger()) return true;
        if (activity == null) return false;
        Look look = look();
        return look == Look.SIT || look == Look.RIDE;
    }

    /** Vanilla walks getVehicle() while isPassenger() is true, which is null for a seated puppet. */
    @Override
    public Entity getRootVehicle() {
        Entity vehicle = getVehicle();
        return vehicle == null ? this : vehicle.getRootVehicle();
    }

    /** How it is drawn this tick. */
    public Look look() {
        if (riding) return Look.RIDE;
        if (!lively) return standing > 0 ? Look.STAND : Look.SIT;
        if (raised && activity != Activity.BED) return Look.STAND;
        return switch (activity) {
            case BED -> Look.BED;
            case SIT, READ -> Look.SIT;
            default -> Look.STAND;
        };
    }

    /** In a bed. The bed is its sleeping position, so the game's renderer lays it along the bed. */
    public boolean inBed() {
        return activity == Activity.BED && bed != null;
    }

    /** What it rides, to draw under it; null when on foot. */
    public @Nullable Entity mount() {
        return riding && carried != null ? carried.entity : null;
    }

    /** Standing in front of a fire or a furnace, warming itself. */
    public boolean warming() {
        return activity == Activity.WARM && warmAt != null;
    }

    /** Shield up. */
    public boolean blocking() {
        return raised;
    }

    /** What it does and what it knows of around it, for the debug tools. */
    public String state() {
        if (riding && carried != null) return carried.state();
        if (gazed > 0 && gazer != null) return "looking at you at " + gazer + (crouched ? ", crouched" : "");
        if (far && loop != null) return "away: " + loop.state();
        Walkway.Survey map = known;
        return activity.name().toLowerCase(java.util.Locale.ROOT)
                + (route != null ? " (finding its way)" : "")
                + ", area " + (map == null ? "unread" : map.spots().size() + " spots")
                + (copying != null || reading != null ? " (reading)" : "")
                + ", " + sleeps.size() + " beds, " + sights.size() + " things to look at, " + hearths.size()
                + " places by a fire, " + shores.size() + " places to fish, " + covers.size() + " under a roof";
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
        if (riding) {
            // In the saddle: the shield up, the head turned to them.
            shieldTicks = SHIELD_TICKS;
            faceAt(from, 0);
            return;
        }
        if (activity == Activity.BED || activity == Activity.SIT || activity == Activity.FISH || activity == Activity.TALK
                || activity == Activity.GARDEN || activity == Activity.TEND || activity == Activity.READ) {
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
     * @param work  sections of blocks all puppets may still copy this tick, shared: looking around costs
     */
    /**
     * Whether it lives its life here, so close to the camera at {@code eye}; past
     * {@link #NEAR_OUT}, it plays its loop until the camera comes within {@link #NEAR_IN}.
     */
    boolean near(Vec3 eye) {
        double across = Math.hypot(getX() - eye.x, getZ() - eye.z);
        near = near ? across < NEAR_OUT : across < NEAR_IN;
        return near;
    }

    /** Playing its loop, away from the camera, rather than living its life. */
    public boolean away() {
        return riding && carried != null ? carried.away() : far;
    }

    void tick(ClientLevel level, FarConfig config, Around around, int[] work, @Nullable Entity you) {
        // Never in the level, so never ticked by it: Epic Fight's hooks around its own tick.
        EpicFight.beforeTick(this);
        try {
            tickOwn(level, config, around, work, you);
        } finally {
            // It never moves through the game, which says whether it stands; animations ask
            // (Epic Fight, Entity Model Features packs). On its feet unless thrown or carried.
            setOnGround(!riding && activity != Activity.FLY);
            EpicFight.afterTick(this);
        }
    }

    private void tickOwn(ClientLevel level, FarConfig config, Around around, int[] work, @Nullable Entity you) {
        crowd = around.crowd();
        Set<BlockPos> beds = around.beds();
        boolean night = around.night();
        setOldPosAndRot();
        yBodyRotO = yBodyRot;
        yHeadRotO = yHeadRot;
        tickCount++;
        if (standing > 0) standing--;
        if (gazed > 0) gazed--;
        // Crouched with you only while it lives its life on foot.
        if (crouched && (gazed <= 0 || riding || !lively || !near)) crouch(false);

        roam = config.offlineRoam;
        if (lively != config.offlineLife) {
            lively = config.offlineLife;
            calmDown(beds);
            if (!lively) snap(place().x, place().y, place().z);
            placed = false;
            if (mount != null) mount.reset();
            if (boat != null) boat.reset();
        }
        if (carried == null || carried == boat) keepDry(level);
        RestingMount carrier = mount != null && config.showVehicles ? mount : boat;
        if (carrier != carried) {
            // On or off its mount, by the switch that shows mounts, or into or out of its
            // boat: back on its spot either way.
            carried = carrier;
            riding = carrier != null;
            calmDown(beds);
            placed = false;
            if (carrier != null) carrier.reset();
            Vec3 at = place();
            snap(at.x, at.y, at.z);
        }
        if (riding && carried != null) {
            ride(level, carried, config, work);
            return;
        }
        if (!lively) {
            Puppets.walk(this, 0f, 1f, 1f);
            Puppets.ground(this, false);
            sit(level);
            return;
        }

        if (!near) {
            away(level, beds, night);
            return;
        }
        if (far) {
            // Back near the camera: its life again, from where its loop left it.
            far = false;
            placed = false;
            start(Activity.IDLE, 20 + random.nextInt(40));
        }

        if (!Footing.loaded(level, blockPosition())) {
            // Nothing known of the blocks here: it only stands or sits where it is.
            placed = false;
            afar(night);
            return;
        }
        boolean first = !placed;
        placed = true;
        if (first) settle(level, true);

        if (shieldTicks > 0) shieldTicks--;
        if (tickCount % 5 == 0) swirls(level);
        survey(level, work);
        double before = getX(), beforeZ = getZ();
        if (activity != Activity.FLY && shove(level, you) && activity == Activity.WALK) {
            // Its way runs through you: pushed back every tick, it would never get there.
            if (++blocked > BLOCKED) start(Activity.IDLE, 40 + random.nextInt(40));
        } else {
            blocked = 0;
        }

        if (activity == Activity.FLY) {
            fly(level);
        } else {
            watch(level, beds);
            // Thrown just now by a blast: nothing else this tick.
            if (activity == Activity.GUARD) guard(level);
            else if (activity != Activity.FLY) live(level, around);
            if (activity != Activity.WALK && activity != Activity.BED && activity != Activity.FLY && path == null) {
                settle(level, false);
            }
        }
        // The arm swings it starts (a gesture, a wave), played out as the game plays a player's.
        tickSwing();
        Runnable go = following;
        if (go != null && --followIn <= 0) {
            following = null;
            if (activity == Activity.IDLE && route == null) go.run();
        }
        keepDoors(level, you);
        if (activity != Activity.FLY && activity != Activity.GUARD && activity != Activity.BED) {
            greet(level, you, around.greeted());
            dodge(you);
            eat(level);
            notice();
        }
        if (activity != Activity.FLY && activity != Activity.GUARD) respond();
        boolean wary = activity == Activity.GUARD || activity == Activity.FLY;
        arm(wary, wary || shieldTicks > 0);
        double moved = Math.hypot(getX() - before, getZ() - beforeZ);
        Puppets.walk(this, (float) Math.min(moved * 4.0, 1.0), 0.4f, 1f);
        Puppets.ground(this, activity == Activity.FLY);
        setYRot(yBodyRot);
    }

    /**
     * Walked into by you, or by what you ride: it is shoved out of your way, getting up if it
     * sat, never into a wall. Never out of its bed. Whether you were in its way.
     */
    private boolean shove(ClientLevel level, @Nullable Entity you) {
        if (you == null || you.isSpectator() || activity == Activity.BED) return false;
        Entity body = you.getRootVehicle();
        if (!getBoundingBox().intersects(body.getBoundingBox())) return false;
        double dx = getX() - body.getX(), dz = getZ() - body.getZ();
        double d = Math.hypot(dx, dz);
        if (d < 1e-4) {
            // Right on it: straight ahead of whoever walks in.
            double yaw = Math.toRadians(body.getYRot());
            dx = -Math.sin(yaw);
            dz = Math.cos(yaw);
            d = 1;
        }
        double step = Math.min(SHOVE, Math.max(0.05, (getBbWidth() + body.getBbWidth()) / 2 - d));
        double x = getX() + dx / d * step, z = getZ() + dz / d * step;
        if (!Footing.fits(blocks, x, getY(), z, Footing.STANDING)) return true;
        setPos(x, getY(), z);
        if (!Footing.supported(blocks, x, getY(), z, RIM)) {
            tipOver(dx / d, dz / d);
            return true;
        }
        if (activity == Activity.SIT) start(Activity.IDLE, 40 + random.nextInt(40));
        return true;
    }

    /**
     * Shoved over an edge, its feet on the rim alone: it goes over, along {@code nx},
     * {@code nz}, until nothing holds it, and falls ({@link #settle}) rather than stand in the air.
     */
    private void tipOver(double nx, double nz) {
        double y = getY();
        for (int i = 0; i < 12 && Footing.supported(blocks, getX(), y, getZ()); i++) {
            double x = getX() + nx * 0.05, z = getZ() + nz * 0.05;
            if (!Footing.fits(blocks, x, y, z, Footing.STANDING)) break;
            setPos(x, y, z);
        }
        // Whatever it was doing, or walking to, it falls first.
        start(Activity.IDLE, 40 + random.nextInt(40));
    }

    // --- In the saddle ----------------------------------------------------------------------

    /** Carried by its mount, looking around; the shield up a moment after a hit. */
    private void ride(ClientLevel level, RestingMount ridden, FarConfig config, int[] work) {
        ridden.tick(level, lively, near, config.offlineRoam, work);
        Vec3 seat = ridden.rider();
        setPos(seat.x, seat.y, seat.z);
        yBodyRot = ridden.riderYaw();
        if (lively) {
            if (shieldTicks > 0) shieldTicks--;
            arm(false, shieldTicks > 0);
            if (shieldTicks > 0) {
                yHeadRot = yBodyRot + headYaw;
            } else if (gazed > 0 && gazer != null) {
                // Looked at from the saddle: it looks back, its head only.
                lookAt(gazer, false);
            } else {
                glance(false);
            }
        } else {
            arm(false, false);
            headYaw = approach(headYaw, 0, 6);
            headPitch = approach(headPitch, 0, 4);
            yHeadRot = yBodyRot + headYaw;
            setXRot(headPitch);
        }
        Puppets.walk(this, 0f, 1f, 1f);
        Puppets.ground(this, false);
        setYRot(yBodyRot);
    }

    // --- Away from the camera -----------------------------------------------------------------

    /**
     * Its loop: a walk around where it stands with pauses, seated in some of them by day; at
     * night on its feet, or in the bed it already lies in until the morning. It never walks
     * back to its spot: the loop starts wherever its life left it.
     */
    private void away(ClientLevel level, Set<BlockPos> beds, boolean night) {
        if (!far) {
            far = true;
            if (!(night && activity == Activity.BED)) calmDown(beds);
            loop = null;
        }
        if (activity == Activity.BED) {
            if (night) {
                Puppets.walk(this, 0f, 1f, 1f);
                Puppets.ground(this, false);
                return;
            }
            // Up in the morning: its loop starts beside the bed.
            leaveBed(beds);
            loop = null;
        }
        RestingLoop playing = loop(level);
        double moved = playing.tick((int) level.getGameTime(), false);
        if (getX() != playing.x || getY() != playing.y || getZ() != playing.z) setPos(playing.x, playing.y, playing.z);
        yBodyRot = playing.body;
        activity = playing.pausing && !night ? Activity.SIT : Activity.IDLE;
        glance(false);
        Puppets.walk(this, (float) Math.min(moved * 4.0, 1.0), 0.4f, 1f);
        Puppets.ground(this, activity == Activity.FLY);
        setYRot(yBodyRot);
    }

    /**
     * Its loop around where it stands, the ground under it read once, never farther from its
     * spot than it roams ({@link FarConfig#offlineRoam}): its spot is the middle of where it may go.
     */
    private RestingLoop loop(ClientLevel level) {
        if (loop == null) {
            long seed = spot.name().toLowerCase(java.util.Locale.ROOT).hashCode() * 0x9E3779B97F4A7C15L;
            double x = getX(), y = getY(), z = getZ(), cx = spot.x(), cz = spot.z(), radius = roam;
            // Where no animation fits, a cramped one; where not even that, it turns on its spot.
            loop = new RestingLoop(seed, x, y, z, yBodyRot, MobAnimations.WALK, true,
                    () -> MobPaths.plan(level, x, y, z, 0.6, seed)
                            .thenApply(choice -> MobPaths.orCramped(MobPaths.within(choice, cx, cz, radius, 0.6, seed), 0.6, seed)));
        }
        return loop;
    }

    // --- Out of the water -------------------------------------------------------------------

    /**
     * Where it rests, decided from the blocks once those around its spot are all there, again
     * when that changes; looked at once a second. Its spot in the air: the ground under it.
     * In water, or over it (water is what it falls onto): the nearest shore within
     * {@link #SHORE} blocks, or a boat on the surface when there is none, left once the water
     * at its spot is gone. Before the blocks are there, a guess from the far world
     * ({@link #guess}). What it finds is kept with its spot ({@link #restFound}).
     */
    private void keepDry(ClientLevel level) {
        if (dryWait > 0) {
            dryWait--;
            return;
        }
        dryWait = SHORE_WAIT;
        if (!Footing.loaded(level, home())) {
            if (rest == null) guess(level);
            return;
        }
        double x = spot.x(), y = landing(level), z = spot.z();
        if (!Footing.wet(blocks, x, y, z)) {
            boat = null;
            guessed = false;
            // In the air: on the ground under it. On its spot: nowhere else.
            Vec3 ground = y < spot.y() - 1e-3 ? new Vec3(x, y, z) : null;
            if (!Objects.equals(shore, ground)) forgetShore(ground);
            found(ground == null ? null : new RestingPlayers.Rest(x, y, z, false));
            return;
        }
        if (boat != null && !guessed) return;
        Vec3 known = shore;
        if (known != null && !guessed && !Double.isNaN(Footing.stand(blocks, (int) Math.floor(known.x),
                (int) Math.floor(known.y + 0.01), (int) Math.floor(known.z)))) {
            return;
        }
        if (!Footing.loadedAround(blocks, x, z, SHORE)) return;
        guessed = false;
        Vec3 land = Footing.shore(blocks, x, y, z, SHORE);
        if (land != null) {
            boat = null;
            forgetShore(land);
            found(new RestingPlayers.Rest(land.x, land.y, land.z, false));
            return;
        }
        double surface = Footing.surface(blocks, x, y, z);
        forgetShore(null);
        boat = RestingMount.boat(level, spot, surface, random);
        found(boat == null ? null : new RestingPlayers.Rest(x, surface, z, true));
    }

    /** Where it was found to rest, to be kept with its spot. */
    private void found(RestingPlayers.@Nullable Rest now) {
        if (Objects.equals(rest, now)) return;
        rest = now;
        restFound = true;
    }

    /** Whether it found where it rests since last asked: {@link #rest()}, to keep with its spot. */
    boolean restFound() {
        boolean found = restFound;
        restFound = false;
        return found;
    }

    /** Where it rests, when it is not its spot. */
    RestingPlayers.@Nullable Rest rest() {
        return rest;
    }

    /**
     * Its spot far away, its blocks not loaded and where it rests not found yet: what the far
     * world (Voxy, Distant Horizons) holds under it, asked once. A spot high above the ground
     * - a player who logged off flying - rests on it; a spot on water or over it rests in a
     * boat. A guess only: found again from the blocks once they are loaded.
     */
    private void guess(ClientLevel level) {
        LodWorld.Surface under = farSurface;
        if (under != null) {
            farSurface = null;
            double top = under.y() + 1;
            if (under.water() && top < spot.y() + 4) {
                // On the water or above it, not deep under it: afloat, as it would be once the blocks are there.
                boat = RestingMount.boat(level, spot, under.y() + WATER_TOP, random);
                guessed = boat != null;
            } else if (!under.water() && top < spot.y() - 1) {
                forgetShore(new Vec3(spot.x(), top, spot.z()));
                guessed = true;
                if (!riding) {
                    snap(spot.x(), top, spot.z());
                    loop = null;
                }
            }
            return;
        }
        if (askedFar || boat != null || shore != null) return;
        askedFar = true;
        LodWorld.surface(Mth.floor(spot.x()), Mth.floor(spot.z()))
                .thenAcceptAsync(found -> farSurface = found.orElse(null), Minecraft.getInstance());
    }

    /** Where it rests while not riding: its spot, or the ground or shore it rests on instead. */
    private Vec3 place() {
        Vec3 land = shore;
        return land != null ? land : new Vec3(spot.x(), spot.y(), spot.z());
    }

    /** Where its spot comes to rest: the spot, or what is under it when it is in the air, water included. */
    private double landing(ClientLevel level) {
        double x = spot.x(), y = spot.y(), z = spot.z();
        if (Footing.wet(blocks, x, y, z) || Footing.supported(blocks, x, y, z)) return y;
        return Footing.floorBelow(blocks, x, y, z);
    }

    /**
     * A new place to rest. Away from the camera and left in water, it is moved there, its loop
     * drawn again around it; anywhere else its loop goes on where it is.
     */
    private void forgetShore(@Nullable Vec3 land) {
        shore = land;
        if (!far || riding || !Footing.wet(blocks, getX(), getY(), getZ())) return;
        Vec3 at = place();
        snap(at.x, at.y, at.z);
        loop = null;
    }

    /**
     * Living, near the camera, and in water (its spot was, or it was thrown in): out onto the
     * nearest shore within {@link #SHORE} blocks of where it is, or its spot's.
     */
    private void ashore(ClientLevel level) {
        if (!Footing.wet(blocks, getX(), getY(), getZ())) return;
        if (shoreWait > 0) {
            shoreWait--;
            return;
        }
        shoreWait = SHORE_WAIT;
        if (!Footing.loadedAround(blocks, getX(), getZ(), SHORE)) return;
        Vec3 land = Footing.shore(blocks, getX(), getY(), getZ(), SHORE);
        if (land == null) land = shore;
        if (land == null) return;
        bed = null;
        calmDownQuietly();
        fall = 0;
        snap(land.x, land.y, land.z);
    }

    // --- A life ---------------------------------------------------------------------------

    private void live(ClientLevel level, Around around) {
        Set<BlockPos> beds = around.beds();
        boolean night = around.night();
        if (route != null) routing(level, beds);
        switch (activity) {
            case WALK -> {
                if (step(level, STROLL, false)) arrive(level, beds);
                return;
            }
            case BED -> {
                BlockPos head = bed;
                if (!night || head == null || !(level.getBlockState(head).getBlock() instanceof BedBlock)) {
                    leaveBed(beds);
                    start(Activity.IDLE, 40 + random.nextInt(40));
                    // Up for the day: a look at the sky, then breakfast now and then.
                    headPitch = -35;
                    if (!night && random.nextInt(10) < 3) snack();
                }
                return;
            }
            case FACE -> {
                if (focus != null && gazed <= 0) faceAt(focus, 6);
                // Rummaging in the chest it opened.
                if (openChest != null && tickCount % (14 + Math.floorMod(getId(), 7)) == 0) gesture(InteractionHand.MAIN_HAND);
            }
            case WARM -> {
                if (chat != null) talk();
                else if (focus != null && gazed <= 0) faceAt(focus, 6);
            }
            case GARDEN -> garden();
            case TEND -> tend();
            case READ -> readBook();
            case FISH -> {
                if (night) start(Activity.IDLE, 20);
                else fish(level);
            }
            case TALK -> {
                if (night) start(Activity.IDLE, 20);
                else talk();
            }
            default -> {
            }
        }
        // Rain starting: those who mind it think again at once.
        if (tickCount % 40 == 0 && sheltering && !covers.isEmpty() && activity != Activity.WALK && route == null
                && wet(level)) {
            timer = Math.min(timer, 1);
        }
        if (gazed <= 0 && activity != Activity.TALK && chat == null && waving <= 0 && noticing <= 0) {
            glance(activity == Activity.FACE || activity == Activity.WARM || activity == Activity.FISH
                    || activity == Activity.GARDEN || activity == Activity.TEND || activity == Activity.READ);
        }
        if (--timer <= 0) decide(level, around);
    }

    /** What it does next. */
    private void decide(ClientLevel level, Around around) {
        Set<BlockPos> beds = around.beds();
        boolean night = around.night();
        if (route != null) return;
        Walkway.Survey map = known;
        if (night) {
            if (map == null) {
                // Still reading what is around: a moment more before giving up on a bed.
                start(Activity.IDLE, 20);
                return;
            }
            if (towardsBed(level, beds)) return;
            if (sheltering && wet(level) && towardsCover()) return;
            // No bed it can reach: on its feet, looking around, until one is free.
            start(Activity.IDLE, AWAKE + random.nextInt(AWAKE));
            return;
        }
        if (sheltering && level.isRaining()) {
            if (!wet(level)) {
                // Dry under a roof or a tree: it stays there while it rains.
                start(random.nextInt(3) == 0 ? Activity.SIT : Activity.IDLE, 200 + random.nextInt(200));
                return;
            }
            if (towardsCover()) return;
        }
        if (around.evening() && activity != Activity.WARM && random.nextInt(100) < WARM_CHANCE
                && towardsHearth(around.crowd())) {
            return;
        }
        // Something the place has to do: crops, an animal, a book.
        if (map != null && random.nextInt(100) < CHORE_CHANCE && towardsChore(level, map)) return;
        // A group chatting near draws people in: they come and join it, more often than not.
        if (map != null && random.nextInt(100) < GROUP_PULL && towardsGroup(map, new ArrayList<>(around.crowd()), null)) {
            return;
        }
        int roll = random.nextInt(100);
        if (roll < 30 || map == null) {
            start(Activity.IDLE, 60 + random.nextInt(100));
        } else if (roll < 45) {
            start(Activity.SIT, 200 + random.nextInt(300));
        } else if (roll < 55 && towardsWater(around.fishers())) {
            return;
        } else if (roll < 63 && towardsChat(map, around.crowd())) {
            return;
        } else if (!(roll < 72 && towardsSomething()) && !stroll(map)) {
            start(Activity.IDLE, 60 + random.nextInt(60));
        }
    }

    private void start(Activity next, int ticks) {
        reelIn();
        leaveChore(next);
        if (next != Activity.TALK) hangUp();
        if (next != Activity.WARM) warmAt = null;
        if (next != Activity.FISH) fishingFrom = null;
        activity = next;
        timer = ticks;
        path = null;
        route = null;
        if (next != Activity.FACE && next != Activity.WARM) focus = null;
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
        if (!routeTo(sight.front(), Activity.FACE)) return false;
        // Set once on its way: starting the way clears what it looked at before.
        focus = Vec3.atCenterOf(sight.block());
        return true;
    }

    /** Rained on where it stands. */
    private boolean wet(ClientLevel level) {
        return level.isRaining() && level.isRainingAt(BlockPos.containing(getX(), getEyeY(), getZ()));
    }

    /** One of those who take shelter from the rain. */
    public boolean sheltering() {
        return sheltering;
    }

    /** To a spot it can reach with a roof or leaves above, one of the nearest. */
    private boolean towardsCover() {
        if (covers.isEmpty()) return false;
        BlockPos at = feet();
        List<Walkway.Step> nearest = new ArrayList<>(covers);
        nearest.sort(java.util.Comparator.comparingDouble(step -> step.distanceSqr(at.getX(), at.getZ())));
        return routeTo(nearest.get(random.nextInt(Math.min(4, nearest.size()))), Activity.IDLE);
    }

    /**
     * To a fire or a furnace it can reach, one of the nearest, on a spot in front of it that
     * nobody else stands on or walks to.
     */
    private boolean towardsHearth(Collection<RestingPuppet> crowd) {
        if (hearths.isEmpty()) return false;
        BlockPos at = feet();
        List<Sight> nearest = new ArrayList<>(hearths);
        nearest.sort(java.util.Comparator.comparingDouble(sight -> sight.front().distanceSqr(at.getX(), at.getZ())));
        List<Sight> free = new ArrayList<>();
        for (Sight sight : nearest) {
            BlockPos front = new BlockPos(sight.front().x(), sight.front().y(), sight.front().z());
            boolean taken = false;
            for (RestingPuppet other : crowd) {
                if (other != this && front.equals(other.warmAt)) taken = true;
            }
            if (!taken) free.add(sight);
            if (free.size() == 3) break;
        }
        if (free.isEmpty()) return false;
        Sight sight = free.get(random.nextInt(free.size()));
        if (!routeTo(sight.front(), Activity.WARM)) return false;
        bringAlong(follower -> follower.towardsHearth(follower.crowd));
        focus = Vec3.atCenterOf(sight.block());
        warmAt = new BlockPos(sight.front().x(), sight.front().y(), sight.front().z());
        return true;
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
     * Starts looking for the way to a spot, off the game's thread, in the blocks it last read
     * around; it stays as it is meanwhile, then walks.
     */
    private boolean routeTo(Walkway.Step target, Activity after) {
        BlockCopy copy = area;
        if (copy == null) return false;
        BlockPos at = feet();
        BlockPos home = home();
        int radius = roam + 4, budget = budget(roam);
        start(activity == Activity.SIT || activity == Activity.FISH || activity == Activity.TALK
                || activity == Activity.WARM || activity == Activity.FACE ? Activity.IDLE : activity, 400);
        route = GroundWork.run(() -> Optional.ofNullable(Walkway.way((x, y, z) -> Footing.stand(copy, x, y, z),
                at.getX(), at.getY(), at.getZ(), target, home.getX(), home.getZ(), radius, budget)));
        then = after;
        return true;
    }

    /** Once the way is found, off it goes. */
    private void routing(ClientLevel level, Set<BlockPos> beds) {
        CompletableFuture<Optional<List<Walkway.Step>>> searching = route;
        if (searching == null || !searching.isDone()) return;
        route = null;
        List<Walkway.Step> way = searching.isCompletedExceptionally() ? null : searching.join().orElse(null);
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

    /** What it read around its spot: where it can walk, from the copy of the blocks it read, and what is there to go to. */
    private record Area(BlockCopy blocks, Walkway.@Nullable Survey map, List<Sight> sights, List<Sight> sleeps,
                        List<Sight> hearths, List<Shore> shores, List<Walkway.Step> covers, List<Sight> crops,
                        List<Sight> reads) {
    }

    /**
     * Reads where it can walk around its spot and what is there to go to: beds, chests,
     * workstations. The blocks around are copied a few sections a tick, then read off the
     * game's thread. Read again now and then, as blocks change.
     */
    private void survey(ClientLevel level, int[] work) {
        surveyAge++;
        boolean due = known == null ? surveyAge >= retry : surveyAge > RESURVEY || lost();
        if (copying == null && reading == null && route == null && due) {
            BlockPos at = feet();
            // Nowhere to stand here: nothing to read, until it stands again.
            if (Double.isNaN(Footing.stand(blocks, at.getX(), at.getY(), at.getZ()))
                    && Double.isNaN(Footing.stand(blocks, at.getX(), at.getY() + 1, at.getZ()))
                    && Double.isNaN(Footing.stand(blocks, at.getX(), at.getY() - 1, at.getZ()))) {
                return;
            }
            BlockPos home = home();
            int reach = roam + 4;
            copying = BlockCopy.of(level, home.getX() - reach, home.getY() - COPY_BELOW, home.getZ() - reach,
                    home.getX() + reach, home.getY() + COPY_ABOVE, home.getZ() + reach);
            surveyAge = 0;
        }
        BlockCopy.Builder builder = copying;
        if (builder != null) {
            work[0] -= builder.copy(level, Math.max(0, work[0]));
            if (!builder.done()) return;
            copying = null;
            BlockCopy copy = builder.build();
            BlockPos at = feet();
            BlockPos home = home();
            int radius = roam, budget = budget(roam);
            reading = GroundWork.run(() -> read(copy, at, home, radius, budget));
            return;
        }
        CompletableFuture<Area> pending = reading;
        if (pending == null || !pending.isDone()) return;
        reading = null;
        Area read = pending.isCompletedExceptionally() ? null : pending.join();
        if (read == null || read.map() == null) {
            // It could not stand where it was read from: again a little later.
            retry = RETRY;
            return;
        }
        known = read.map();
        area = read.blocks();
        sights = read.sights();
        sleeps = read.sleeps();
        shores = read.shores();
        hearths = read.hearths();
        covers = read.covers();
        crops = read.crops();
        reads = read.reads();
        // Idle for want of knowing better (no bed known at night): think again with what it now knows.
        if (activity == Activity.IDLE) timer = Math.min(timer, 20);
    }

    /** Off the game's thread: everywhere it can walk from {@code at}, and the beds, things and water beside. */
    private static Area read(BlockCopy copy, BlockPos at, BlockPos home, int radius, int budget) {
        Walkway.Survey map = Walkway.around((x, y, z) -> Footing.stand(copy, x, y, z), at.getX(), at.getY(), at.getZ(),
                home.getX(), home.getZ(), radius, budget);
        if (map == null) return new Area(copy, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        Map<BlockPos, Sight> sights = new LinkedHashMap<>(), sleeps = new LinkedHashMap<>(), hearths = new LinkedHashMap<>();
        Map<BlockPos, Shore> shores = new LinkedHashMap<>();
        Map<BlockPos, Sight> crops = new LinkedHashMap<>(), reads = new LinkedHashMap<>();
        List<Walkway.Step> covers = new ArrayList<>();
        for (Walkway.Step spot : map.spots()) {
            if (roofed(copy, spot)) covers.add(spot);
            // Round a campfire, two blocks out: close enough to warm up, never in the flames.
            for (int[] off : AROUND_FIRE) {
                BlockPos block = new BlockPos(spot.x() + off[0], spot.y(), spot.z() + off[1]);
                if (CAMPFIRES.contains(copy.state(block).getBlock())) {
                    hearths.putIfAbsent(new BlockPos(spot.x(), spot.y(), spot.z()), new Sight(block, spot));
                }
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = new BlockPos(spot.x() + side.getStepX(), spot.y(), spot.z() + side.getStepZ());
                BlockState state = copy.state(next);
                // Every spot beside a furnace: several of them may stand round it.
                if (HEARTHS.contains(state.getBlock()) && !CAMPFIRES.contains(state.getBlock())) {
                    hearths.putIfAbsent(new BlockPos(spot.x(), spot.y(), spot.z()), new Sight(next, spot));
                }
                // A crop on farmland, at the height of its feet; a lectern or a shelf of books.
                if ((state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock)
                        && farmland(copy.state(next.below()))) {
                    crops.putIfAbsent(new BlockPos(spot.x(), spot.y(), spot.z()), new Sight(next, spot));
                }
                if (BOOKS.contains(state.getBlock())) reads.putIfAbsent(new BlockPos(spot.x(), spot.y(), spot.z()), new Sight(next, spot));
                if (INTERESTING.contains(state.getBlock())) {
                    sights.putIfAbsent(next, new Sight(next, spot));
                } else if (state.getBlock() instanceof BedBlock) {
                    BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? next
                            : next.relative(state.getValue(BedBlock.FACING));
                    sleeps.putIfAbsent(head, new Sight(head, spot));
                }
                Vec3 water = openWater(copy, spot, side);
                if (water != null) shores.putIfAbsent(new BlockPos(spot.x(), spot.y(), spot.z()), new Shore(spot, water));
            }
        }
        return new Area(copy, map, new ArrayList<>(sights.values()), new ArrayList<>(sleeps.values()),
                new ArrayList<>(hearths.values()), new ArrayList<>(shores.values()), covers,
                new ArrayList<>(crops.values()), new ArrayList<>(reads.values()));
    }

    /** Something over a spot within {@link #ROOF} blocks that keeps the rain off: a roof, leaves. */
    private static boolean roofed(BlockCopy copy, Walkway.Step spot) {
        for (int up = 2; up <= ROOF; up++) {
            if (!MobPaths.passable(copy.state(spot.x(), spot.y() + up, spot.z()))) return true;
        }
        return false;
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
            case FACE -> {
                start(Activity.FACE, 100 + random.nextInt(140));
                if (focus != null) openChest(BlockPos.containing(focus));
            }
            case WARM -> {
                start(Activity.WARM, 300 + random.nextInt(300));
                if (focus != null) circleRound(BlockPos.containing(focus));
            }
            case GARDEN -> {
                start(Activity.GARDEN, 200 + random.nextInt(240));
                hold(new ItemStack(Items.WHEAT_SEEDS));
                setPose(Pose.CROUCHING);
            }
            case TEND -> {
                start(Activity.TEND, 140 + random.nextInt(160));
                if (!food.isEmpty()) hold(food.copy());
            }
            case READ -> {
                start(Activity.READ, 300 + random.nextInt(400));
                hold(new ItemStack(Items.BOOK));
            }
            case TALK -> {
                start(Activity.TALK, CHAT + random.nextInt(CHAT));
                // Someone new: those there, the one who waited above all, stay a while with them.
                Chat joined = chat;
                if (joined != null) {
                    for (RestingPuppet other : joined.members) {
                        if (other != this && other.activity == Activity.TALK && other.timer < CHAT / 2) {
                            other.timer = CHAT / 2 + other.random.nextInt(CHAT);
                        }
                    }
                }
            }
            case FISH -> {
                start(Activity.FISH, 900 + random.nextInt(900));
                bite = 10 + random.nextInt(30);
            }
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
        openDoor(level, new BlockPos(next.x(), next.y(), next.z()));
        double tx = next.x() + 0.5, tz = next.z() + 0.5, ty = next.height();
        double dx = tx - getX(), dz = tz - getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= speed) {
            if (Double.isNaN(Footing.stand(blocks, next.x(), next.y(), next.z()))) {
                // Built on or dug out since it was read: read again what is around.
                path = null;
                surveyAge = Math.max(surveyAge, RESURVEY);
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
        if (gazed <= 0) {
            // Looked at, its head is turned to you instead (respond).
            headYaw = approach(headYaw, 0, 10);
            headPitch = approach(headPitch, 5, 5);
        }
        yHeadRot = yBodyRot + headYaw;
        setXRot(headPitch);
        return pathIndex >= way.size();
    }

    // --- You -------------------------------------------------------------------------------

    /** You look at it, from {@code eye}, crouching or not: it looks back for a while. */
    void lookedAt(Vec3 eye, boolean crouching) {
        gazer = eye;
        gazed = GAZE;
        gazerCrouching = crouching;
        watched = true;
    }

    /** Looking back at you while you look at it, crouched when you crouch and it faces you. */
    public boolean crouched() {
        return crouched;
    }

    /**
     * Looked at: it looks back at you. Standing, it turns to you and, facing you, crouches when
     * you crouch; walking, seated or fishing, it turns its head only. Asleep, on guard or
     * thrown, never.
     */
    private void respond() {
        Vec3 eye = gazer;
        boolean crouch = false;
        if (gazed > 0 && eye != null) {
            boolean standing = activity == Activity.IDLE || activity == Activity.FACE || activity == Activity.WARM;
            if (standing || activity == Activity.WALK || activity == Activity.SIT || activity == Activity.FISH) {
                lookAt(eye, standing);
                crouch = watched && gazerCrouching && standing && facing(eye);
            }
        }
        watched = false;
        if (crouch != crouched) crouch(crouch);
    }

    private void crouch(boolean down) {
        crouched = down;
        setPose(down ? Pose.CROUCHING : Pose.STANDING);
    }

    /** Its head to {@code target}, as far as its neck turns; its body too when {@code body}. */
    private void lookAt(Vec3 target, boolean body) {
        double dx = target.x - getX(), dz = target.z - getZ();
        double dy = target.y - getEyeY();
        float yaw = yawTowards(dx, dz);
        if (body) turnTo(yaw, 10);
        headYaw = approach(headYaw, Math.max(-NECK, Math.min(NECK, wrap(yaw - yBodyRot))), 15);
        headPitch = approach(headPitch, (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), 10);
        yHeadRot = yBodyRot + headYaw;
        setXRot(headPitch);
    }

    /** Its body turned to {@code target}, near enough. */
    private boolean facing(Vec3 target) {
        return Math.abs(wrap(yawTowards(target.x - getX(), target.z - getZ()) - yBodyRot)) < FACING;
    }

    // --- Daily life ---------------------------------------------------------------------------

    /**
     * To something the place has to do, one of those it has: crops to see to, an animal to feed,
     * a book to read. Nobody else's: one to a crop, an animal, a book.
     */
    private boolean towardsChore(ClientLevel level, Walkway.Survey map) {
        List<Runnable> choices = new ArrayList<>();
        List<Boolean> done = new ArrayList<>();
        if (!crops.isEmpty()) choices.add(() -> done.add(towardsSight(crops, Activity.GARDEN)));
        if (!reads.isEmpty()) choices.add(() -> done.add(towardsSight(reads, Activity.READ)));
        choices.add(() -> done.add(towardsAnimal(level, map)));
        java.util.Collections.shuffle(choices, random);
        for (Runnable choice : choices) {
            choice.run();
            if (done.get(done.size() - 1)) return true;
        }
        return false;
    }

    /** For the game tests and the debug tools: it goes to this chore now, if the place has it. */
    public boolean act(String chore) {
        Walkway.Survey map = known;
        if (map == null || !lively || far || riding) return false;
        return switch (chore) {
            case "garden" -> towardsSight(crops, Activity.GARDEN);
            case "read" -> towardsSight(reads, Activity.READ);
            case "tend" -> towardsAnimal((ClientLevel) level(), map);
            case "chest" -> towardsChest();
            case "eat" -> {
                start(Activity.SIT, 200);
                fed = 0;
                snack();
                yield true;
            }
            default -> false;
        };
    }

    /** One of {@code sights} nobody else goes to, one of the nearest. */
    private boolean towardsSight(List<Sight> sights, Activity then) {
        if (sights.isEmpty()) return false;
        BlockPos at = feet();
        List<Sight> nearest = new ArrayList<>(sights);
        nearest.removeIf(sight -> taken(sight.block()));
        if (nearest.isEmpty()) return false;
        nearest.sort(Comparator.comparingDouble(sight -> sight.front().distanceSqr(at.getX(), at.getZ())));
        Sight sight = nearest.get(random.nextInt(Math.min(4, nearest.size())));
        if (!routeTo(sight.front(), then)) return false;
        focus = Vec3.atCenterOf(sight.block());
        aim = sight.block();
        return true;
    }

    /** The block another of them sees to, or walks to. */
    private boolean taken(BlockPos block) {
        for (RestingPuppet other : crowd) {
            if (other != this && block.equals(other.aim)) return true;
        }
        return false;
    }

    /** To a chest it can reach, to open it and rummage in it. */
    private boolean towardsChest() {
        List<Sight> chests = new ArrayList<>();
        for (Sight sight : sights) {
            BlockState state = level().getBlockState(sight.block());
            if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.ENDER_CHEST)) chests.add(sight);
        }
        if (chests.isEmpty()) return false;
        Sight sight = chests.get(random.nextInt(chests.size()));
        if (!routeTo(sight.front(), Activity.FACE)) return false;
        focus = Vec3.atCenterOf(sight.block());
        return true;
    }

    /** To an animal grown, loaded around its spot, with the food it likes in hand; one each. */
    private boolean towardsAnimal(ClientLevel level, Walkway.Survey map) {
        BlockPos home = home();
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, new net.minecraft.world.phys.AABB(home).inflate(roam),
                animal -> animal.isAlive() && !animal.isBaby());
        if (animals.isEmpty()) return false;
        BlockPos at = feet();
        for (int tries = 0; tries < 4; tries++) {
            Animal animal = animals.get(random.nextInt(animals.size()));
            boolean seen = false;
            for (RestingPuppet other : crowd) seen |= other != this && other.tended == animal;
            if (seen) continue;
            ItemStack likes = ItemStack.EMPTY;
            for (ItemStack each : FODDER) {
                if (animal.isFood(each)) {
                    likes = each;
                    break;
                }
            }
            if (likes.isEmpty()) continue;
            Walkway.Step best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Walkway.Step step : map.spots()) {
                if (Math.abs(step.y() - animal.getBlockY()) > 1) continue;
                double apart = Math.hypot(step.x() + 0.5 - animal.getX(), step.z() + 0.5 - animal.getZ());
                if (apart < 1.3 || apart > 2.6) continue;
                double distance = step.distanceSqr(at.getX(), at.getZ());
                if (distance < bestDistance) {
                    best = step;
                    bestDistance = distance;
                }
            }
            if (best == null || !routeTo(best, Activity.TEND)) continue;
            tended = animal;
            food = likes;
            return true;
        }
        return false;
    }

    /** Crouched by its crops, seeds in hand: its hand over one, then another. */
    private void garden() {
        Vec3 crop = focus;
        if (crop == null) return;
        if (gazed <= 0) {
            faceAt(crop.add(Math.sin(tickCount * 0.02) * 0.8, -0.3, Math.cos(tickCount * 0.023) * 0.8), 4);
            setXRot(Math.max(getXRot(), 35f));
        }
        if (tickCount % (24 + Math.floorMod(getId(), 17)) == 0) gesture(InteractionHand.MAIN_HAND);
    }

    /** By its animal, the food held out: it follows it with its eyes, offers it now and then. */
    private void tend() {
        Animal animal = tended;
        if (animal == null || !animal.isAlive() || animal.isRemoved() || animal.distanceToSqr(this) > 36) {
            start(Activity.IDLE, 30 + random.nextInt(40));
            return;
        }
        if (gazed <= 0) faceAt(animal.position().add(0, animal.getBbHeight() * 0.6, 0), 8);
        if (tickCount % (30 + Math.floorMod(getId(), 23)) == 0) gesture(InteractionHand.MAIN_HAND);
    }

    /** Seated by its book's shelf or lectern, the book in hand, head down; a page turned now and then. */
    private void readBook() {
        Vec3 shelf = focus;
        if (shelf != null) turnTo(yawTowards(shelf.x - getX(), shelf.z - getZ()) + 50, 4);
        if (gazed > 0) return;
        boolean turning = tickCount % 100 < 8;
        headYaw = approach(headYaw, turning ? 12f : (float) Math.sin(tickCount * 0.03) * 4f, 3);
        headPitch = approach(headPitch, 42f, 3);
        yHeadRot = yBodyRot + headYaw;
        setXRot(headPitch);
        if (tickCount % 100 == 0) gesture(InteractionHand.OFF_HAND);
    }

    /** Tilled soil, whose name changed in 26.1. */
    private static boolean farmland(BlockState state) {
        //? if >=26.1 {
        /*return state.getBlock() instanceof FarmlandBlock;
        *///?} else
        return state.getBlock() instanceof FarmBlock;
    }

    /** Crumbs of what it eats, the game's own; made of the item alone from 26.1. */
    private ItemParticleOption crumbs() {
        //? if >=26.1 {
        /*return new ItemParticleOption(ParticleTypes.ITEM, food.getItem());
        *///?} else
        return new ItemParticleOption(ParticleTypes.ITEM, food);
    }

    /** Leaving a chore: its own things back in hand, on its feet, the chest shut. */
    private void leaveChore(Activity next) {
        if (next != activity) {
            if (activity == Activity.GARDEN && !crouched) setPose(Pose.STANDING);
            if (activity == Activity.GARDEN || activity == Activity.TEND || activity == Activity.READ) holdOwn();
            if (next != Activity.TEND) tended = null;
            if (next != Activity.GARDEN && next != Activity.READ && next != Activity.WALK) aim = null;
        }
        if (next != Activity.FACE) closeChest();
        if (next != Activity.WARM) fire = null;
        if (next != Activity.IDLE && next != Activity.SIT && next != Activity.WARM) stopEating();
    }

    /** The block it sees to, or walks to: a crop, a book. */
    private @Nullable BlockPos aim;

    private void hold(ItemStack stack) {
        if (!armed) setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

    private void holdOwn() {
        if (!armed) setItemSlot(EquipmentSlot.MAINHAND, held.copy());
    }

    // --- Chests and doors, on this client only ------------------------------------------------

    /**
     * What the puppets opened, by block: shut again by whoever opened it, or by
     * {@link #shutForgotten} once its puppet is gone. A lid or a door left open on this client
     * would stay open.
     */
    private record Opened(Runnable shut, long[] touched, int[] users) {
        Opened(Runnable shut, long touched) {
            this(shut, new long[]{touched}, new int[]{1});
        }
    }

    private static final Map<BlockPos, Opened> OPENED = new java.util.HashMap<>();

    /** Shuts what no puppet holds open any more: one let go with a door or a chest open. */
    static void shutForgotten(ClientLevel level) {
        long now = level.getGameTime();
        OPENED.entrySet().removeIf(entry -> {
            if (now - entry.getValue().touched()[0] < 40) return false;
            entry.getValue().shut().run();
            return true;
        });
    }

    /** Every puppet's chests and doors forgotten, the level gone. */
    static void forgetOpened() {
        OPENED.clear();
    }

    /** Opens the lid of the chest there, if it is one: the game's own animation, nothing in it touched. */
    private void openChest(BlockPos at) {
        BlockEntity entity = level().getBlockEntity(at);
        if (!(entity instanceof ChestBlockEntity) && !(entity instanceof EnderChestBlockEntity)) return;
        openChest = at.immutable();
        // Open already, another of them at it: it shuts once the last one leaves.
        Opened already = OPENED.get(openChest);
        if (already != null) {
            already.users()[0]++;
            return;
        }
        entity.triggerEvent(1, 1);
        OPENED.put(openChest, new Opened(() -> {
            BlockEntity now = level().getBlockEntity(at);
            if (now != null) now.triggerEvent(1, 0);
        }, level().getGameTime()));
    }

    private void closeChest() {
        BlockPos at = openChest;
        openChest = null;
        if (at == null) return;
        Opened opened = OPENED.get(at);
        if (opened == null || --opened.users()[0] > 0) return;
        OPENED.remove(at);
        opened.shut().run();
    }

    /**
     * Opens the door or gate it is about to walk through, on this client, both halves of a door;
     * never with you within {@link #DOOR_CLEAR} blocks, who would walk through it here while the
     * server has it shut.
     */
    private void openDoor(ClientLevel level, BlockPos at) {
        for (BlockPos each : new BlockPos[]{at, at.above()}) {
            BlockState state = level.getBlockState(each);
            boolean door = state.getBlock() instanceof DoorBlock, gate = state.getBlock() instanceof FenceGateBlock;
            if (!door && !gate || state.getValue(DoorBlock.OPEN) || youNear(each)) continue;
            BlockPos lower = door && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? each.below() : each.immutable();
            if (OPENED.containsKey(lower)) continue;
            List<BlockPos> blocks = door ? List.of(lower, lower.above()) : List.of(lower);
            for (BlockPos block : blocks) setOpen(level, block, true);
            opened.add(lower);
            OPENED.put(lower, new Opened(() -> {
                for (BlockPos block : blocks) setOpen(level, block, false);
            }, level.getGameTime()));
            return;
        }
    }

    /** Its doors held open while it walks through, shut once it is through or you come near. */
    private void keepDoors(ClientLevel level, @Nullable Entity you) {
        for (int i = opened.size() - 1; i >= 0; i--) {
            BlockPos door = opened.get(i);
            Opened open = OPENED.get(door);
            if (open == null) {
                opened.remove(i);
                continue;
            }
            boolean through = Vec3.atBottomCenterOf(door).distanceToSqr(position()) > 2.2 * 2.2;
            if (through || youNear(door)) {
                OPENED.remove(door);
                open.shut().run();
                opened.remove(i);
            } else {
                open.touched()[0] = level.getGameTime();
            }
        }
        if (openChest != null) {
            Opened chest = OPENED.get(openChest);
            if (chest != null) chest.touched()[0] = level.getGameTime();
        }
    }

    private static boolean youNear(BlockPos at) {
        Entity you = Minecraft.getInstance().player;
        return you != null && you.distanceToSqr(Vec3.atCenterOf(at)) < DOOR_CLEAR * DOOR_CLEAR;
    }

    /** A door or gate open or shut on this client, as it was, if nothing else changed it meanwhile. */
    private static void setOpen(ClientLevel level, BlockPos at, boolean open) {
        BlockState state = level.getBlockState(at);
        if (!(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof FenceGateBlock)) return;
        if (state.getValue(DoorBlock.OPEN) == open) return;
        // Told to the renderer, no neighbour updated: the server's word on it stays the law.
        level.setBlock(at, state.setValue(DoorBlock.OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    // --- A bite ---------------------------------------------------------------------------------

    /** Two or three bites of something, the game's own animation and crumbs. */
    private void snack() {
        if (fed < 0) fed = random.nextInt(2 * FED);
        if (bites > 0 || fed > 0 || armed || raised) return;
        fed = FED + random.nextInt(FED);
        food = MEALS.get(random.nextInt(MEALS.size())).copy();
        bites = 2 + random.nextInt(2);
        biting = 0;
    }

    private void eat(ClientLevel level) {
        if (fed < 0) fed = random.nextInt(2 * FED);
        if (fed > 0) fed--;
        if (bites <= 0) {
            if ((activity == Activity.IDLE || activity == Activity.SIT || activity == Activity.WARM) && chat == null
                    && random.nextInt(10000) < SNACK_CHANCE) {
                snack();
            }
            return;
        }
        if (raised || armed) {
            stopEating();
            return;
        }
        if (biting == 0) {
            hold(food.copy());
            getEntityData().set(DATA_LIVING_ENTITY_FLAGS, (byte) 1);
        }
        biting++;
        if (biting < BITE) {
            useItemRemaining = BITE - biting;
            if (biting % 4 == 0) {
                Vec3 look = getViewVector(1f);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(crumbs(), getX() + look.x * 0.35,
                            getEyeY() - 0.15, getZ() + look.z * 0.35, (random.nextDouble() - 0.5) * 0.1, 0.05,
                            (random.nextDouble() - 0.5) * 0.1);
                }
            }
        } else if (biting == BITE) {
            getEntityData().set(DATA_LIVING_ENTITY_FLAGS, (byte) 0);
        } else if (biting >= BITE + BETWEEN_BITES) {
            biting = 0;
            if (--bites <= 0) stopEating();
        }
    }

    private void stopEating() {
        if (bites <= 0 && biting == 0) return;
        boolean wasEating = biting > 0 && biting < BITE;
        bites = 0;
        biting = 0;
        if (wasEating && !raised) getEntityData().set(DATA_LIVING_ENTITY_FLAGS, (byte) 0);
        if (activity != Activity.GARDEN && activity != Activity.TEND && activity != Activity.READ) holdOwn();
    }

    /** Waving at you now, for the game tests. */
    public boolean greeting() {
        return waving > 0;
    }

    /** Looking at a block that just changed, for the game tests. */
    public boolean noticing() {
        return noticing > 0;
    }

    /** Eating now, for the game tests. */
    public boolean eating() {
        return bites > 0;
    }

    /** At a chore now, for the game tests: {@code garden}, {@code tend}, {@code read}, {@code chest}; null for none. */
    public @Nullable String chore() {
        return switch (activity) {
            case GARDEN -> "garden";
            case TEND -> "tend";
            case READ -> "read";
            case FACE -> openChest != null ? "chest" : null;
            default -> null;
        };
    }

    // --- Round the fire ---------------------------------------------------------------------------

    /** By a campfire: a circle with those already there, a chat round it. */
    private void circleRound(BlockPos block) {
        BlockState state = level().getBlockState(block);
        if (!state.is(Blocks.CAMPFIRE) && !state.is(Blocks.SOUL_CAMPFIRE)) return;
        fire = block.immutable();
        for (RestingPuppet other : crowd) {
            if (other == this || other.activity != Activity.WARM || !block.equals(other.fire)) continue;
            Chat round = other.chat;
            if (round != null) {
                if (round.full()) return;
                round.members.add(this);
                chat = round;
                return;
            }
            Chat started = new Chat(new Random(random.nextLong()));
            started.centre = Vec3.atBottomCenterOf(block).add(0, 0.5, 0);
            started.members.add(other);
            started.members.add(this);
            other.chat = started;
            chat = started;
            return;
        }
    }

    /**
     * Someone free right beside it now and then comes along, a moment after it sets off, to the
     * same thing: the two walk there one after the other.
     */
    private void bringAlong(java.util.function.Predicate<RestingPuppet> goes) {
        if (following != null) return;
        for (RestingPuppet other : crowd) {
            if (other == this || !other.free() || other.following != null || other.distanceToSqr(this) > 5 * 5) continue;
            if (random.nextBoolean()) return;
            other.start(Activity.IDLE, 80);
            other.following = () -> goes.test(other);
            other.followIn = 12 + random.nextInt(16);
            return;
        }
    }

    // --- You ---------------------------------------------------------------------------------------

    /** The first time it sees you this session, near: turned to you, a wave of the hand. Only when free. */
    private void greet(ClientLevel level, @Nullable Entity you, Set<String> greeted) {
        if (waving > 0) {
            waving--;
            if (you != null) faceAt(you.getEyePosition(), 12);
            if (waving == 30 || waving == 20 || waving == 10) gesture(InteractionHand.MAIN_HAND);
            return;
        }
        if (you == null || chat != null || bites > 0
                || activity != Activity.IDLE && activity != Activity.SIT && activity != Activity.FACE && activity != Activity.WARM) {
            return;
        }
        double distance = you.distanceToSqr(this);
        if (distance > GREET * GREET || distance < 4 || tickCount % 10 != 0) return;
        String name = spot.name().toLowerCase(java.util.Locale.ROOT);
        if (greeted.contains(name) || !seen(level, you.getEyePosition())) return;
        greeted.add(name);
        if (activity == Activity.SIT) start(Activity.IDLE, 60 + random.nextInt(40));
        waving = 36;
    }

    /** You running at it: a step aside, out of your way, before you reach it. */
    private void dodge(@Nullable Entity you) {
        if (dodgeWait > 0) {
            dodgeWait--;
            return;
        }
        if (you == null || !you.isSprinting() || activity == Activity.WALK || activity == Activity.READ) return;
        Vec3 motion = you.getDeltaMovement().multiply(1, 0, 1);
        double speed = motion.length();
        if (speed < 0.1) return;
        Vec3 toMe = position().subtract(you.position()).multiply(1, 0, 1);
        double ahead = toMe.dot(motion) / speed;
        if (ahead < 0.5 || ahead > RUN_AT) return;
        // Across your way, to the side it already stands on.
        Vec3 dir = motion.scale(1 / speed);
        double side = dir.x * toMe.z - dir.z * toMe.x;
        double off = Math.abs(side);
        if (off > 1.4) return;
        Vec3 aside = new Vec3(-dir.z, 0, dir.x).scale(side >= 0 ? -1 : 1);
        for (double reach : new double[]{2.2, 1.6, -2.2}) {
            BlockPos to = BlockPos.containing(getX() + aside.x * reach, getY() + 0.01, getZ() + aside.z * reach);
            double height = Footing.stand(blocks, to.getX(), to.getY(), to.getZ());
            if (Double.isNaN(height)) continue;
            dodgeWait = 60;
            start(Activity.WALK, 0);
            then = Activity.IDLE;
            path = List.of(new Walkway.Step(to.getX(), to.getY(), to.getZ(), height));
            pathIndex = 0;
            return;
        }
    }

    /** A block placed or broken near it: it looks there a moment. */
    void noticeBlock(BlockPos at) {
        if (activity == Activity.BED || activity == Activity.FLY || activity == Activity.GUARD || !lively || far || riding) return;
        if (Vec3.atCenterOf(at).distanceToSqr(getEyePosition()) > NOTICE * NOTICE) return;
        noticed = Vec3.atCenterOf(at);
        noticing = 50 + random.nextInt(30);
    }

    private void notice() {
        if (noticing <= 0 || waving > 0 || gazed > 0) return;
        noticing--;
        Vec3 at = noticed;
        if (at != null) lookAt(at, false);
    }

    // --- A chat ------------------------------------------------------------------------------

    /**
     * People chatting together: who is in it (those on their way included), who speaks and how,
     * worked out once a tick by whichever of them ticks first. Two to start with, up to
     * {@link #CHAT_MOST}; over when fewer than two are left.
     */
    static final class Chat {
        /** How one speaks: each turn its own. */
        enum Way { NOD, EXPLAIN, LOOK_ROUND, LAUGH, CALM }

        final List<RestingPuppet> members = new ArrayList<>();
        private @Nullable RestingPuppet speaker;
        private Way way = Way.NOD;
        private int turnLeft;
        private long tickedAt = Long.MIN_VALUE;
        private final Random random;

        Chat(Random random) {
            this.random = random;
        }

        /** Those there, standing in the circle, not on their way. */
        List<RestingPuppet> present() {
            List<RestingPuppet> there = new ArrayList<>(members.size());
            for (RestingPuppet member : members) {
                if ((member.activity == Activity.TALK || member.activity == Activity.WARM)
                        && member.path == null && member.route == null) {
                    there.add(member);
                }
            }
            return there;
        }

        /** Round a campfire: its middle, whoever stands there. */
        @Nullable Vec3 centre;

        /** The middle of those there: what each turns its body to. */
        Vec3 middle() {
            if (centre != null) return centre;
            List<RestingPuppet> there = present();
            if (there.isEmpty()) return members.get(0).position();
            double x = 0, z = 0, y = 0;
            for (RestingPuppet member : there) {
                x += member.getX();
                y += member.getY();
                z += member.getZ();
            }
            return new Vec3(x / there.size(), y / there.size(), z / there.size());
        }

        /** Who speaks now, and how, once a tick: a new turn now and then, sometimes a pause. */
        void tick(long now) {
            if (tickedAt == now) return;
            tickedAt = now;
            List<RestingPuppet> there = present();
            if (there.size() < 2) {
                speaker = null;
                return;
            }
            if (speaker != null && !there.contains(speaker)) turnLeft = 0;
            if (--turnLeft > 0) return;
            RestingPuppet last = speaker;
            if (last != null && random.nextInt(5) == 0) {
                // A moment when nobody speaks.
                speaker = null;
                turnLeft = 20 + random.nextInt(40);
                return;
            }
            List<RestingPuppet> next = new ArrayList<>(there);
            if (next.size() > 1) next.remove(last);
            speaker = next.get(random.nextInt(next.size()));
            Way[] ways = Way.values();
            way = ways[random.nextInt(ways.length)];
            turnLeft = 40 + random.nextInt(way == Way.LAUGH ? 40 : 110);
        }

        boolean full() {
            return members.size() >= CHAT_MOST;
        }
    }

    /** Someone it chats with, or walks up to or waits for, for that; null when nobody. */
    public @Nullable RestingPuppet partner() {
        Chat in = chat;
        if (in == null) return null;
        for (RestingPuppet member : in.members) if (member != this) return member;
        return null;
    }

    /** Everyone in its chat, itself included, those on their way too; empty when none. */
    public List<RestingPuppet> chatMates() {
        Chat in = chat;
        return in == null ? List.of() : List.copyOf(in.members);
    }

    /** Chatting now: in the circle, with at least one other there. */
    public boolean chatting() {
        Chat in = chat;
        return (activity == Activity.TALK || activity == Activity.WARM) && in != null && in.present().size() >= 2
                && in.present().contains(this);
    }

    /** Who speaks in its chat now; null when nobody does, or it chats with no one. */
    public @Nullable RestingPuppet speaker() {
        Chat in = chat;
        return in == null ? null : in.speaker;
    }

    /** Free to be called over for a chat: living its life near the camera, on foot, doing nothing much. */
    private boolean free() {
        return lively && near && !far && !riding && chat == null && route == null && path == null
                && (activity == Activity.IDLE || activity == Activity.SIT || activity == Activity.FACE
                || activity == Activity.WARM);
    }

    /**
     * To a chat: mostly a group chatting within {@link #TALK_REACH} blocks that has room, to a
     * spot of the circle with the most room; otherwise someone free, to a spot
     * {@link #TALK_GAP} blocks from them, who waits, turned to it. Never when another group
     * chats within {@link #TALKERS_APART} blocks of them.
     */
    private boolean towardsChat(Walkway.Survey map, Collection<RestingPuppet> crowd) {
        List<RestingPuppet> others = new ArrayList<>(crowd);
        if (others.size() < 2) return false;
        if (random.nextInt(100) < JOIN_CHANCE && towardsGroup(map, others, null)) return true;
        int first = random.nextInt(others.size());
        BlockPos at = feet();
        for (int i = 0; i < others.size(); i++) {
            RestingPuppet other = others.get((first + i) % others.size());
            if (other == this || !other.free() || other.distanceToSqr(this) > TALK_REACH * TALK_REACH) continue;
            if (chatNear(crowd, other, null)) continue;
            BlockPos them = other.feet();
            Walkway.Step best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Walkway.Step step : map.spots()) {
                if (Math.abs(step.y() - them.getY()) > 1) continue;
                double apart = Math.hypot(step.x() - them.getX(), step.z() - them.getZ());
                if (apart < TALK_GAP - 0.5 || apart > TALK_GAP + 0.6) continue;
                double distance = step.distanceSqr(at.getX(), at.getZ());
                if (distance < bestDistance) {
                    best = step;
                    bestDistance = distance;
                }
            }
            if (best == null || !routeTo(best, Activity.TALK)) continue;
            Chat started = new Chat(new Random(random.nextLong()));
            started.members.add(other);
            started.members.add(this);
            chat = started;
            other.calledBy(started);
            return true;
        }
        return false;
    }

    /**
     * To a group chatting near ({@code only} that one when given), with room for one more, at the
     * spot of its circle with the most room.
     */
    private boolean towardsGroup(Walkway.Survey map, List<RestingPuppet> others, @Nullable Chat only) {
        BlockPos at = feet();
        for (RestingPuppet other : others) {
            Chat group = other.chat;
            if (other == this || group == null || group.full() || other.distanceToSqr(this) > TALK_REACH * TALK_REACH) continue;
            if (only != null && group != only || group.centre != null) continue;
            List<RestingPuppet> there = group.present();
            if (there.size() < 2) continue;
            Vec3 middle = group.middle();
            // Wider as they are more, and the spot farthest round from everyone in it.
            double radius = Math.max(TALK_GAP / 2 + 0.4, 0.55 * (group.members.size() + 1) * 0.6 + 0.3);
            Walkway.Step best = null;
            double bestScore = -Double.MAX_VALUE;
            for (Walkway.Step step : map.spots()) {
                if (Math.abs(step.y() - Mth.floor(middle.y)) > 1) continue;
                double sx = step.x() + 0.5, sz = step.z() + 0.5;
                double out = Math.hypot(sx - middle.x, sz - middle.z);
                if (Math.abs(out - radius) > 0.7) continue;
                double room = Double.MAX_VALUE;
                for (RestingPuppet member : group.members) {
                    room = Math.min(room, Math.hypot(sx - member.getX(), sz - member.getZ()));
                }
                if (room < 1.2) continue;
                double score = room - 0.05 * Math.sqrt(step.distanceSqr(at.getX(), at.getZ()));
                if (score > bestScore) {
                    best = step;
                    bestScore = score;
                }
            }
            if (best == null || !routeTo(best, Activity.TALK)) continue;
            group.members.add(this);
            chat = group;
            if (only == null) bringAlong(follower -> follower.known != null
                    && follower.towardsGroup(follower.known, new ArrayList<>(follower.crowd), group));
            return true;
        }
        return false;
    }

    /** Whether another group chats, or is about to, within {@link #TALKERS_APART} blocks of {@code them}. */
    private boolean chatNear(Collection<RestingPuppet> crowd, RestingPuppet them, @Nullable Chat but) {
        for (RestingPuppet other : crowd) {
            if (other == this || other == them || other.chat == null || other.chat == but) continue;
            if (other.distanceToSqr(them) < TALKERS_APART * TALKERS_APART) return true;
        }
        return false;
    }

    /** Called over for a chat: it stops what it does and waits, turned to the one coming. */
    private void calledBy(Chat started) {
        start(Activity.TALK, TALK_WAIT);
        chat = started;
    }

    /**
     * It leaves its chat, or never got there: the others go on while two of them are left;
     * the last one goes back to its life.
     */
    private void hangUp() {
        Chat in = chat;
        chat = null;
        glancedAt = null;
        if (in == null) return;
        in.members.remove(this);
        if (in.speaker == this) in.speaker = null;
        if (in.members.size() >= 2) return;
        for (RestingPuppet other : List.copyOf(in.members)) {
            other.chat = null;
            if (other.activity == Activity.TALK) other.start(Activity.IDLE, 40 + other.random.nextInt(60));
        }
        in.members.clear();
    }

    /**
     * A tick of a chat: turned to the middle of the circle, its head to whoever speaks. The one
     * who speaks does so its own way; the others listen, nodding now and then, sometimes looking
     * at another of them, laughing when the speaker laughs. Waiting for the others, it watches
     * them come.
     */
    private void talk() {
        Chat in = chat;
        if (in == null || !in.members.contains(this) || far) {
            start(Activity.IDLE, 20 + random.nextInt(40));
            return;
        }
        in.tick(level().getGameTime());
        List<RestingPuppet> there = in.present();
        if (there.size() < 2) {
            // Waiting: turned to the nearest on its way.
            RestingPuppet coming = null;
            for (RestingPuppet member : in.members) {
                if (member != this && (coming == null || member.distanceToSqr(this) < coming.distanceToSqr(this))) {
                    coming = member;
                }
            }
            if (coming != null) lookAt(coming.getEyePosition(), true);
            return;
        }
        Vec3 middle = in.middle();
        double reach = TALK_GAP + 0.6 * there.size() + 1.5;
        if (middle.distanceToSqr(position()) > reach * reach) {
            // Pushed out or carried off: this chat is over for it.
            start(Activity.IDLE, 20 + random.nextInt(40));
            return;
        }
        // Just come: it turns to them quickly; then only follows the circle as it shifts.
        float toMiddle = yawTowards(middle.x - getX(), middle.z - getZ());
        turnTo(toMiddle, Math.abs(wrap(toMiddle - yBodyRot)) > 45 ? 20 : 8);
        RestingPuppet speaker = in.speaker;
        if (speaker == this) {
            speak(in, there);
            return;
        }
        // Listening: at the speaker, or now and then at another; with nobody speaking, round them.
        if (--glanceIn <= 0) {
            glanceIn = 40 + random.nextInt(80);
            glancedAt = speaker == null || random.nextInt(4) == 0 ? there.get(random.nextInt(there.size())) : null;
            if (glancedAt == this) glancedAt = null;
        }
        RestingPuppet target = glancedAt != null && there.contains(glancedAt) ? glancedAt : speaker;
        if (target != null) lookAt(target.getEyePosition(), false);
        float nod = 0f;
        if (speaker != null && in.way == Chat.Way.LAUGH && (tickCount + getId()) % 3 != 0) {
            nod = (float) Math.sin(tickCount * 1.2) * 4f - 6f;
        } else if (--nodIn <= 0) {
            nodIn = 30 + random.nextInt(90);
        } else if (nodIn < 12) {
            nod = 6f * (float) Math.sin(nodIn * Math.PI / 6);
        }
        setXRot(headPitch + nod);
    }

    /** Its turn to speak, its own way this time. */
    private void speak(Chat in, List<RestingPuppet> there) {
        RestingPuppet first = there.get(0) == this ? there.get(1) : there.get(0);
        switch (in.way) {
            case NOD -> {
                lookAt(first.getEyePosition(), false);
                yHeadRot += (float) Math.sin(tickCount * 0.17) * 6f;
                setXRot(headPitch + (float) Math.sin(tickCount * 0.5) * 7f);
            }
            case EXPLAIN -> {
                lookAt(first.getEyePosition(), false);
                yHeadRot += (float) Math.sin(tickCount * 0.09) * 12f;
                setXRot(headPitch + (float) Math.sin(tickCount * 0.3) * 4f);
                if (tickCount % (18 + Math.floorMod(getId(), 9)) == 0) gesture(InteractionHand.MAIN_HAND);
            }
            case LOOK_ROUND -> {
                // To each in turn.
                List<RestingPuppet> others = new ArrayList<>(there);
                others.remove(this);
                RestingPuppet at = others.get((tickCount / 25) % others.size());
                lookAt(at.getEyePosition(), false);
                setXRot(headPitch + (float) Math.sin(tickCount * 0.45) * 5f);
            }
            case LAUGH -> {
                lookAt(first.getEyePosition(), false);
                setXRot(headPitch - 10f + (float) Math.sin(tickCount * 1.3) * 5f);
            }
            case CALM -> {
                lookAt(first.getEyePosition(), false);
                setXRot(headPitch + (tickCount % 30 < 8 ? 5f : 0f));
            }
        }
    }

    /** A swing of the arm: a gesture, the game's own, which 26.3 makes of a kind and a length. */
    private void gesture(InteractionHand hand) {
        //? if >=26.3 {
        /*swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
        *///?} else {
        swing(hand);
        //?}
    }

    /** The arm's swing played on by a tick, as the game's tick does for a player, which a puppet never runs. */
    private void tickSwing() {
        //? if >=26.3 {
        /*swingState.tick();
        *///?} else {
        oAttackAnim = attackAnim;
        updateSwingTime();
        //?}
    }

    // --- Fishing ---------------------------------------------------------------------------

    /** Where it stands at the water's edge, and the spot on the water its bobber floats on. */
    private record Shore(Walkway.Step front, Vec3 water) {
    }

    /** The edge it fishes from, or walks to for that: one who fishes on a stretch of shore. */
    @Nullable Vec3 fishing() {
        Shore at = fishingFrom;
        return at == null ? null : Vec3.atBottomCenterOf(new BlockPos(at.front().x(), at.front().y(), at.front().z()));
    }

    /** Its bobber, while its line is out; drawn with it. */
    public @Nullable FishingHook hook() {
        return hook;
    }

    /**
     * The water a player standing on {@code spot} could fish in towards {@code side}: open
     * water just below its feet for {@link #CAST} blocks out, nothing over it. Where the
     * bobber floats, or null.
     */
    private static @Nullable Vec3 openWater(BlockCopy copy, Walkway.Step spot, Direction side) {
        for (int k = 1; k <= CAST; k++) {
            BlockPos surface = new BlockPos(spot.x() + side.getStepX() * k, spot.y() - 1, spot.z() + side.getStepZ() * k);
            if (!copy.state(surface).getFluidState().is(FluidTags.WATER) || !copy.state(surface.above()).isAir()) return null;
        }
        return new Vec3(spot.x() + side.getStepX() * CAST + 0.5, spot.y() - 0.1, spot.z() + side.getStepZ() * CAST + 0.5);
    }

    /** To the water's edge to fish, where nobody else fishes within {@link #FISHERS_APART} blocks. */
    private boolean towardsWater(List<Vec3> fishers) {
        if (shores.isEmpty()) return false;
        Shore shore = shores.get(random.nextInt(shores.size()));
        Vec3 at = Vec3.atBottomCenterOf(new BlockPos(shore.front().x(), shore.front().y(), shore.front().z()));
        for (Vec3 other : fishers) {
            if (other.distanceToSqr(at) < FISHERS_APART * FISHERS_APART) return false;
        }
        if (!routeTo(shore.front(), Activity.FISH)) return false;
        fishingFrom = shore;
        fishers.add(at);
        return true;
    }

    /**
     * A tick of fishing, facing its bobber: a cast with a splash, the bobber bobbing, a fish
     * coming in a wake, a bite that pulls the bobber under, the line reeled in, cast again.
     * Nothing is caught: none of it reaches the world.
     */
    private void fish(ClientLevel level) {
        Shore at = fishingFrom;
        if (at == null) {
            start(Activity.IDLE, 40);
            return;
        }
        Vec3 water = at.water();
        if (gazed <= 0) faceAt(water, 6);
        FishingHook bobber = hook;
        if (bobber == null) {
            if (--bite > 0) return;
            // Cast: a rod in the hand, the bobber on the water with a splash.
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
            bobber = Puppets.numbered(new FishingHook(EntityType.FISHING_BOBBER, level));
            bobber.setOwner(this);
            bobber.setPos(water.x, water.y, water.z);
            bobber.setOldPosAndRot();
            hook = bobber;
            splash(level, water, 6);
            bite = 100 + random.nextInt(400);
            return;
        }
        bite--;
        double sink = bite <= 0 && bite > -DIP ? 0.25 : 0;
        bobber.setOldPosAndRot();
        bobber.setPos(water.x, water.y + Math.sin(tickCount * 0.15) * 0.03 - sink, water.z);
        if (bite > 0 && bite <= WAKE) {
            // A fish coming: its wake on the water, nearer every tick.
            double u = bite / (double) WAKE, angle = (spot.name().hashCode() & 7) * Math.PI / 4;
            level.addParticle(ParticleTypes.FISHING, water.x + Math.cos(angle) * u * 3, water.y + 0.1,
                    water.z + Math.sin(angle) * u * 3, 0, 0, 0);
        } else if (bite == 0) {
            splash(level, water, 10);
        } else if (bite <= -DIP) {
            // Reeled in: the line back, a moment before the next cast.
            reelIn();
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
            bite = 30 + random.nextInt(60);
        }
    }

    private void splash(ClientLevel level, Vec3 at, int count) {
        for (int i = 0; i < count; i++) {
            level.addParticle(ParticleTypes.SPLASH, at.x + (random.nextDouble() - 0.5) * 0.4, at.y + 0.1,
                    at.z + (random.nextDouble() - 0.5) * 0.4, 0, 0.05, 0);
        }
        level.addParticle(ParticleTypes.BUBBLE, at.x, at.y, at.z, 0, 0.05, 0);
    }

    /** The line out of the water, and the rod put away: what it held back in its hand. */
    private void reelIn() {
        if (hook != null) {
            hook = null;
            fishing = null;
        }
        if (activity == Activity.FISH && !armed) setItemSlot(EquipmentSlot.MAINHAND, held.copy());
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
        // Nearest first, and a look only where it matters: until it sees one, and at a creeper
        // about to blow up beside it. A look is a ray through the blocks; the distance is free.
        List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(DANGER),
                e -> e instanceof Enemy && e.isAlive() && !e.isSpectator() && e.distanceToSqr(this) < DANGER * DANGER);
        around.sort(Comparator.comparingDouble(e -> e.distanceToSqr(this)));
        Entity nearest = null;
        for (LivingEntity entity : around) {
            double distance = entity.distanceToSqr(this);
            if (nearest != null && distance >= BLAST * BLAST) break;
            boolean fuse = entity instanceof Creeper creeper && creeper.getSwelling(1f) > 0 && !fuses.contains(creeper)
                    && distance < BLAST * BLAST;
            if (nearest != null && !fuse || !seen(level, entity.getEyePosition())) continue;
            if (fuse) fuses.add((Creeper) entity);
            if (nearest == null) nearest = entity;
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
        Walkway.Survey map = Walkway.around((x, y, z) -> Footing.stand(blocks, x, y, z), at.getX(), at.getY(), at.getZ(),
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
        if (Footing.fits(blocks, x + vx, y, z, Footing.STANDING)) x += vx;
        else vx = 0;
        if (Footing.fits(blocks, x, y, z + vz, Footing.STANDING)) z += vz;
        else vz = 0;
        boolean landed = false;
        if (vy > 0) {
            if (Footing.fits(blocks, x, y + vy, z, Footing.STANDING)) y += vy;
            else vy = 0;
        } else {
            double floor = Footing.floorBelow(blocks, x, y, z);
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
        Walkway.Survey map = Walkway.around((bx, by, bz) -> Footing.stand(blocks, bx, by, bz), at.getX(), at.getY(),
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

    /** What it holds: a sword drawn, a shield up. Changed only when it changes. */
    private void arm(boolean sword, boolean shield) {
        if (sword != armed) {
            armed = sword;
            setItemSlot(EquipmentSlot.MAINHAND, sword ? new ItemStack(Items.IRON_SWORD) : held.copy());
        }
        if (shield != raised) {
            raised = shield;
            if (shield) setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            // Using the item in the off hand: the game draws the shield raised.
            getEntityData().set(DATA_LIVING_ENTITY_FLAGS, (byte) (shield ? 3 : 0));
            if (!shield) setItemSlot(EquipmentSlot.OFFHAND, heldOff.copy());
        }
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

    /** Where the blocks are not loaded: standing or sitting where it is, standing at night. */
    private void afar(boolean night) {
        Puppets.walk(this, 0f, 1f, 1f);
        Puppets.ground(this, false);
        if (activity == Activity.BED) return;
        if (activity == Activity.WALK || activity == Activity.GUARD || activity == Activity.FLY
                || activity == Activity.FACE || activity == Activity.WARM || activity == Activity.FISH
                || activity == Activity.TALK || activity == Activity.GARDEN || activity == Activity.TEND
                || activity == Activity.READ) {
            calmDownQuietly();
        }
        if (night) {
            if (activity != Activity.IDLE) start(Activity.IDLE, AWAKE);
            glance(false);
            return;
        }
        if (--timer <= 0) {
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
        if (Footing.inFire(blocks, getX(), getY(), getZ(), Footing.STANDING)) colour = FIRE_RESISTANCE;
        else if (Footing.underWater(blocks, getX(), getEyeY(), getZ())) colour = WATER_BREATHING;
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
            case BED -> 0.5;
            default -> Footing.STANDING;
        };
        double x = getX(), y = getY(), z = getZ();
        double target;
        if (!Footing.fits(blocks, x, y, z, height)) target = Footing.freeAbove(blocks, x, y, z, height);
        else if (!Footing.supported(blocks, x, y, z)) target = Footing.floorBelow(blocks, x, y, z);
        else target = y;
        move(level, x, z, y, target, height, now);
        if (getY() == target) ashore(level);
    }

    /**
     * Sitting on its spot, or the shore near it when the spot is in water, which it goes back
     * to once it has room and something under it again.
     */
    private void sit(ClientLevel level) {
        if (!Footing.loaded(level, spot.x(), spot.z())) {
            placed = false;
            return;
        }
        boolean first = !placed;
        placed = true;
        // Its spot in water: on the shore instead.
        Vec3 land = shore;
        double x = land != null ? land.x : spot.x(), home = land != null ? land.y : spot.y();
        double z = land != null ? land.z : spot.z(), height = 1.2;
        double y = getY();
        if (x != getX() || z != getZ()) {
            fall = 0;
            snap(x, home, z);
            return;
        }
        double target;
        if (Footing.fits(blocks, x, home, z, height) && Footing.supported(blocks, x, home, z)) {
            target = home;
        } else if (!Footing.fits(blocks, x, y, z, height)) {
            target = Footing.freeAbove(blocks, x, y, z, height);
        } else if (!Footing.supported(blocks, x, y, z)) {
            target = Footing.floorBelow(blocks, x, y, z);
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
        if (now || !Footing.clear(blocks, x, y, target, z, height)) {
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
