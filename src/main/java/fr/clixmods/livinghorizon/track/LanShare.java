package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
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
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The companion data pack's job, done by the mod itself for a world opened to LAN.
 *
 * <p>The pack makes every position exact, but it has to be installed in the world, and a
 * player who opens their single player world to friends never does. The host runs the
 * server inside their own game, so their mod writes the same scoreboard objectives the
 * pack does ({@code lh.x}, {@code lh.y}, {@code lh.z}, {@code lh.m}, in the layout of
 * {@code load.mcfunction}). The friends who join only need the mod: they read them with
 * {@link SharedPositions}, exactly as they would from the pack.
 *
 * <p>Nothing runs in an unshared single player world, nor on another server. When the pack is
 * running in the world as well, it keeps the job and this class stands down. The objectives
 * are removed when the world closes, so the world is left as it was found.
 *
 * <p>The scores are written with the game's own {@code scoreboard} command: the same text
 * on every Minecraft version, unlike the scoreboard's API.
 */
public final class LanShare {
    /** Ticks between two publications of the players; the pack's is the same. */
    private static final int PERIOD = 4;
    /** Publications between two of the mobs: five seconds, as the pack. */
    private static final int MOB_EVERY = 25;
    /** Publications to watch a pack that was found before taking over. */
    private static final int PROBE = 3;
    private static final String[] OBJECTIVES = {"lh.x", "lh.y", "lh.z", "lh.m"};
    private static final Map<String, Integer> KINDS = new HashMap<>();

    static {
        for (int i = 0; i < MobKinds.TYPES.size(); i++) KINDS.put(MobKinds.TYPES.get(i), i + 1);
    }

    private static @Nullable Session session;
    private static int ticks;

    private LanShare() {
    }

    /** Every client tick: publishes while the world the player hosts is open to LAN. */
    public static void tick(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || !server.isPublished()) {
            close();
            return;
        }
        if (session == null || session.server != server) {
            close();
            session = new Session(server);
            ticks = 0;
        }
        if (++ticks < PERIOD) return;
        ticks = 0;
        Session current = session;
        server.execute(() -> {
            try {
                current.run();
            } catch (RuntimeException e) {
                LivingHorizonClient.LOGGER.warn("Could not share the positions of the LAN world", e);
            }
        });
    }

    /** Removes what was written, when the world closes or stops being shared. */
    public static void close() {
        Session closing = session;
        session = null;
        if (closing == null || !closing.server.isRunning()) return;
        try {
            // On the server's thread, where the scoreboard belongs; the game is leaving the world.
            closing.server.submit(closing::remove).get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            LivingHorizonClient.LOGGER.debug("Could not remove the shared positions", e);
        }
    }

    /** One shared world. Everything here runs on the server's thread. */
    private static final class Session {
        final MinecraftServer server;
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

        Session(MinecraftServer server) {
            this.server = server;
        }

        void run() {
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
                LivingHorizonClient.LOGGER.info("The data pack runs in this world, the LAN share stays out of it");
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
            // The sidebars of four team colours nobody plays in: shown, so sent to every client.
            command("scoreboard objectives setdisplay sidebar.team.black lh.x");
            command("scoreboard objectives setdisplay sidebar.team.dark_blue lh.y");
            command("scoreboard objectives setdisplay sidebar.team.dark_green lh.z");
            command("scoreboard objectives setdisplay sidebar.team.dark_aqua lh.m");
            set("#version", "lh.m", SharedPositions.VERSION);
            LivingHorizonClient.LOGGER.info("Living Horizon: no data pack in this LAN world, so the mod installed its own: positions are now shared with the players who join");
            return true;
        }

        private void players() {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                int yaw = Math.floorMod((int) Math.floor(player.getYRot()), 360);
                int meta = yaw + 360 * SharedPositions.dimensionCode(player.level().dimension())
                        + (player.isPassenger() ? 1440 : 0);
                publish(player.getScoreboardName(), player.getX(), player.getY(), player.getZ(), meta);
            }
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
                }
            }
            // An unloaded mob keeps its scores in the world; only the memory of them goes.
            written.keySet().removeIf(holder -> holder.length() == 36 && !seen.contains(holder));
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

        void remove() {
            if (!ours) return;
            for (String name : OBJECTIVES) command("scoreboard objectives remove " + name);
        }

        private void command(String command) {
            CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, command);
        }
    }

    private static int number(CompoundTag tag, String key) {
        //? if >=1.21.5 {
        return tag.getIntOr(key, 0);
        //?} else {
        /*return tag.getInt(key);
        *///?}
    }
}
