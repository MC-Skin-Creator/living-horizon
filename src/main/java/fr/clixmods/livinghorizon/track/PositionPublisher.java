package fr.clixmods.livinghorizon.track;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The companion data pack's job, done by the mod itself: it writes the scoreboard
 * objectives the pack does ({@code lh.x}, {@code lh.y}, {@code lh.z}, {@code lh.m} and
 * {@code lh.r}, which links a rider to their mount, in the layout of {@code load.mcfunction}) for one server, and clients read them with
 * {@link SharedPositions}, exactly as they would from the pack.
 *
 * <p>Run by {@link LanShare} for a world opened to LAN, and by {@code MinecraftServerMixin}
 * on a dedicated server. It touches nothing of the client, so a server can load it. When the
 * pack is running in the world as well, it keeps the job and this class stands down.
 *
 * <p>The scores are written with the game's own {@code scoreboard} command: the same text
 * on every Minecraft version, unlike the scoreboard's API.
 *
 * <p>Everything here runs on the server's thread.
 */
public final class PositionPublisher {
    /** Ticks between two runs; the pack's update is the same. */
    public static final int PERIOD = 4;
    /** Runs between two publications of the mobs: five seconds, as the pack. */
    private static final int MOB_EVERY = 25;
    /** Runs to watch a pack that was found before taking over. */
    private static final int PROBE = 3;
    private static final String[] OBJECTIVES = {"lh.x", "lh.y", "lh.z", "lh.m"};
    /** A number of each player's own, which the mob they ride carries too. */
    private static final String RIDER = "lh.r";
    private static final Map<String, Integer> KINDS = new HashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");

    static {
        for (int i = 0; i < MobKinds.TYPES.size(); i++) KINDS.put(MobKinds.TYPES.get(i), i + 1);
    }

    private final boolean dedicated;
    private final MinecraftServer server;
    /** Who writes: found out on the first runs. */
    private boolean decided;
    private boolean ours;
    private boolean packSeen;
    private int probeClock;
    private int probes;
    private int clock;
    private int runs;
    /** What was last written for each holder, so that a still player sends nothing. */
    private final Map<String, int[]> written = new HashMap<>();
    /** Each player's number in {@link #RIDER}, by name, once known. */
    private final Map<String, Integer> riders = new HashMap<>();
    /** The rider's number last written for each published mob, 0 for none. */
    private final Map<String, Integer> carrying = new HashMap<>();

    public PositionPublisher(MinecraftServer server, boolean dedicated) {
        this.server = server;
        this.dedicated = dedicated;
    }

    /** One publication; call it every {@link #PERIOD} ticks. */
    public void run() {
        if (!server.isRunning()) return;
        if (!decided && !decide()) return;
        if (!ours) return;
        players();
        if (runs++ % MOB_EVERY == 0) mobs();
        // Last, so that a client which sees the clock move has the positions of this run.
        set("#clock", "lh.m", ++clock);
    }

    /**
     * Whether to write. A pack already running in this world keeps the job: told by its
     * clock, which moves. Objectives left by a game that crashed do not move, and are taken over.
     */
    private boolean decide() {
        Scoreboard scoreboard = server.getScoreboard();
        Objective meta = scoreboard.getObjective("lh.m");
        Integer layout = meta == null ? null : SharedPositions.score(scoreboard, "#version", meta);
        if (meta == null || layout == null) return start();
        Integer now = SharedPositions.score(scoreboard, "#clock", meta);
        if (now == null) return start();
        if (!packSeen) {
            packSeen = true;
            probeClock = now;
            return false;
        }
        if (now != probeClock) {
            decided = true;
            ours = false;
            LOGGER.info("Living Horizon: the data pack runs in this world, the mod leaves the job to it");
            return false;
        }
        return ++probes >= PROBE && start();
    }

    private boolean start() {
        decided = true;
        ours = true;
        Scoreboard scoreboard = server.getScoreboard();
        for (String name : OBJECTIVES) {
            if (scoreboard.getObjective(name) == null) command("scoreboard objectives add " + name + " dummy");
        }
        if (scoreboard.getObjective(RIDER) == null) command("scoreboard objectives add " + RIDER + " dummy");
        // The sidebars of five team colours nobody plays in: shown, so sent to every client.
        command("scoreboard objectives setdisplay sidebar.team.black lh.x");
        command("scoreboard objectives setdisplay sidebar.team.dark_blue lh.y");
        command("scoreboard objectives setdisplay sidebar.team.dark_green lh.z");
        command("scoreboard objectives setdisplay sidebar.team.dark_aqua lh.m");
        command("scoreboard objectives setdisplay sidebar.team.dark_red " + RIDER);
        set("#version", "lh.m", SharedPositions.VERSION);
        LOGGER.info(dedicated
                ? "Living Horizon: no data pack on this server, so the mod shares the positions by itself. Players with the mod see each other at any distance."
                : "Living Horizon: no data pack in this LAN world, so the mod installed its own: positions are now shared with the players who join");
        return true;
    }

    private void players() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int yaw = Math.floorMod((int) Math.floor(player.getYRot()), 360);
            int meta = yaw + 360 * SharedPositions.dimensionCode(player.level().dimension())
                    + (player.isPassenger() ? 1440 : 0);
            publish(player.getScoreboardName(), player.getX(), player.getY(), player.getZ(), meta);
            // The mob under them carries their number at once, not at the next round of mobs.
            Entity vehicle = player.getVehicle();
            if (vehicle != null && KINDS.containsKey(EntityType.getKey(vehicle.getType()).toString())) {
                carry(vehicle.getUUID().toString(), rider(player));
            }
        }
    }

    /** The player's number, given once, as the data pack gives it: the last one given plus one. */
    private int rider(ServerPlayer player) {
        String name = player.getScoreboardName();
        Integer known = riders.get(name);
        if (known != null) return known;
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(RIDER);
        Integer score = objective == null ? null : SharedPositions.score(scoreboard, name, objective);
        int number;
        if (score != null && score > 0) {
            number = score;
        } else {
            Integer last = objective == null ? null : SharedPositions.score(scoreboard, "#next", objective);
            number = (last == null ? 0 : last) + 1;
            set("#next", RIDER, number);
            set(name, RIDER, number);
        }
        riders.put(name, number);
        return number;
    }

    /** Writes which player rides a published mob: 0 for none, which resets it. */
    private void carry(String holder, int rider) {
        Integer before = carrying.put(holder, rider);
        if (before != null && before == rider) return;
        command(rider > 0 ? "scoreboard players set " + holder + " " + RIDER + " " + rider
                : "scoreboard players reset " + holder + " " + RIDER);
    }

    private void mobs() {
        Set<String> seen = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            int dimension = SharedPositions.dimensionCode(level.dimension());
            for (Entity entity : level.getAllEntities()) {
                if (entity.isRemoved()) continue;
                String type = EntityType.getKey(entity.getType()).toString();
                Integer kind = KINDS.get(type);
                if (kind == null) continue;
                int variant = 0;
                String field = MobKinds.variantField(type);
                if (field != null) variant = number(EntityNbt.save(entity, level), field);
                boolean baby = entity instanceof LivingEntity living && living.isBaby();
                int meta = kind + 256 * dimension + (baby ? 1024 : 0) + 2048 * (variant & 2047);
                String holder = entity.getUUID().toString();
                seen.add(holder);
                publish(holder, entity.getX(), entity.getY(), entity.getZ(), meta);
                int rider = 0;
                for (Entity passenger : entity.getPassengers()) {
                    if (passenger instanceof ServerPlayer player) rider = rider(player);
                }
                carry(holder, rider);
            }
        }
        // An unloaded mob keeps its scores in the world; only the memory of them goes.
        written.keySet().removeIf(holder -> holder.length() == 36 && !seen.contains(holder));
        carrying.keySet().removeIf(holder -> !seen.contains(holder));
    }

    /** Position in tenths of a block, then the meta score last: a client reads nothing before it. */
    private void publish(String holder, double x, double y, double z, int meta) {
        int[] now = {(int) Math.floor(x * 10), (int) Math.floor(y * 10), (int) Math.floor(z * 10), meta};
        int[] before = written.put(holder, now);
        for (int i = 0; i < OBJECTIVES.length; i++) {
            if (before == null || before[i] != now[i]) {
                command("scoreboard players set " + holder + " " + OBJECTIVES[i] + " " + now[i]);
            }
        }
    }

    private void set(String holder, String objective, int value) {
        command("scoreboard players set " + holder + " " + objective + " " + value);
    }

    /**
     * Takes back what was written, for a world that closes. A dedicated server keeps it: the
     * positions of the players who logged off are what the pack keeps too.
     */
    public void remove() {
        if (!ours || dedicated) return;
        for (String name : OBJECTIVES) command("scoreboard objectives remove " + name);
        command("scoreboard objectives remove " + RIDER);
    }

    private void command(String command) {
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }

    private static int number(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getIntOr(key, 0);
        //?} else {
        /*return tag.getInt(key);
        *///?}
    }
}
