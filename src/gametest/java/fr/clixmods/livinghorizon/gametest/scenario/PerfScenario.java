package fr.clixmods.livinghorizon.gametest.scenario;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobScan;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * What the mod costs, to compare before and after a change: a flat world with a crowd far
 * away (a few hundred remembered mobs, test players who logged off, birds), the same camera
 * path every time, and the mod's switches turned off one at a time. Prints a table.
 *
 * <p>The game renders on the CPU here (llvmpipe): GPU work shows as CPU time, so the frame
 * times compare two runs on this machine, not what a graphics card does. What it compares
 * is the mod's own time per tick and per frame, and what it allocates.
 */
public final class PerfScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "pig", "chicken"};
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    /** Where the player watches from: the field of animals lies 124 to 476 blocks south. */
    private static final int SIDE = -300;
    private static final int CROWD = 40;
    /** Ticks each phase is left to settle, then measured: one frame per tick in a game test. */
    private static final int SETTLE = 40, FRAMES = 200;

    @Override
    public String name() {
        return "perf";
    }

    @Override
    public void run(ClientGameTestContext context) {
        // Birds on; the easter eggs stay off, they come by chance.
        Scene.configure(context, config -> config.skyBirds = true);
        FarConfig base = context.computeOnClient(minecraft -> copy(FarConfig.get(), new FarConfig()));
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // A field of animals around the start, 16 blocks apart, as far as the loaded chunks go.
            int i = 0;
            for (int x = -176; x <= 176; x += 16) {
                for (int z = -176; z <= 176; z += 16) {
                    Scene.command(world, "summon " + MOBS[i % MOBS.length] + " " + x + " " + GROUND + " " + z
                            + " {NoAI:1b,Rotation:[" + (i * 45 % 360) + "f,0f]}");
                    i++;
                }
            }
            context.waitTicks(100);
            // Away from it: most of the field is remembered copies now, the server no longer sends them.
            Scene.command(world, "tp @a 0 ~ " + SIDE + " 0 0");
            context.waitTicks(300);
            context.runOnClient(minecraft -> MobScan.start(minecraft, 32));
            context.waitTicks(100);
            context.runOnClient(PerfScenario::crowd);

            int shown = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().shown().size());
            int live = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().live().size());
            int resting = context.computeOnClient(minecraft -> FarPlayerTracker.get().resting().spots().size());
            Scene.log(String.format(Locale.ROOT, "perf scene: %d copies shown, %d mobs still sent, %d players logged off",
                    shown, live, resting));
            if (shown < 200) throw new AssertionError("perf: only " + shown + " remembered copies, the scene is wrong");
            if (resting < CROWD) throw new AssertionError("perf: only " + resting + " of " + CROWD + " players logged off");

            // Once round unmeasured, with the defaults: impostors baked from every side, the JIT warm.
            measure(context, base, config -> {
            });

            List<Row> rows = new ArrayList<>();
            rows.add(phase(context, base, "mod off", PerfScenario::off));
            rows.add(phase(context, base, "defaults", config -> {
            }));
            for (Field field : FarConfig.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != boolean.class
                        || !field.getName().startsWith("opt")) {
                    continue;
                }
                boolean value = get(field, base);
                rows.add(phase(context, base, field.getName() + (value ? " off" : " on"), config -> set(field, config, !value)));
            }
            rows.add(phase(context, base, "impostors off", config -> config.impostors = false));
            rows.add(phase(context, base, "mod off again", PerfScenario::off));
            table(rows);

            context.runOnClient(minecraft -> FarPlayerTracker.get().resting().clearFakes());
        }
    }

    /** The mod off, as its toggle key leaves it, with what it draws on its own off as well. */
    private static void off(FarConfig config) {
        config.enabled = false;
        config.distantMobs = false;
        config.offlinePlayers = false;
        config.skyBirds = false;
    }

    /**
     * Test players who logged off on a ring 120 to 260 blocks round the player, inside and
     * past the render distance, each at the same place every run. Their skins are looked up
     * once, during the unmeasured lap.
     */
    private static void crowd(Minecraft minecraft) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        int dimension = SharedPositions.dimensionCode(minecraft.level.dimension());
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int n = 0; n < CROWD; n++) {
            String name = "Perf" + n;
            double angle = n * golden, distance = 120 + (n * 37 % 141);
            resting.fake(name, new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name), dimension,
                    Math.cos(angle) * distance, GROUND, SIDE + Math.sin(angle) * distance, n * 47 % 360 - 180f,
                    10, null, null);
        }
    }

    private static Row phase(ClientGameTestContext context, FarConfig base, String name, Consumer<FarConfig> change) {
        Row row = measure(context, base, change);
        row.name = name;
        Scene.log(String.format(Locale.ROOT, "perf phase %-24s %7.2f ms per frame", name, row.frame.average()));
        return row;
    }

    /**
     * Settles with the settings changed, then turns the camera once round, one step a frame,
     * timing each frame and reading the mod's own times for it.
     */
    private static Row measure(ClientGameTestContext context, FarConfig base, Consumer<FarConfig> change) {
        context.runOnClient(minecraft -> {
            change.accept(copy(base, FarConfig.get()));
            look(minecraft, 0);
        });
        System.gc();
        context.waitTicks(SETTLE);
        long thread = context.computeOnClient(minecraft -> Thread.currentThread().threadId());
        Row row = new Row();
        long allocatedBefore = allocated(thread);
        for (int frame = 0; frame < FRAMES; frame++) {
            long started = System.nanoTime();
            context.waitTick();
            row.frame.add(System.nanoTime() - started);
            int next = frame + 1;
            long[] mod = context.computeOnClient(minecraft -> {
                long[] times = {Stats.lastTickNanos(), Stats.lastExtractNanos()};
                look(minecraft, next);
                return times;
            });
            row.tick.add(mod[0]);
            row.extract.add(mod[1]);
        }
        row.allocated = (allocated(thread) - allocatedBefore) / FRAMES;
        row.drawn = context.computeOnClient(minecraft -> Stats.drawn());
        return row;
    }

    /** The camera path: once round, from facing the field (south), level. */
    private static void look(Minecraft minecraft, int frame) {
        if (minecraft.player == null) return;
        float yaw = 360f * frame / FRAMES;
        minecraft.player.setYRot(yaw);
        minecraft.player.yRotO = yaw;
        minecraft.player.setYHeadRot(yaw);
        minecraft.player.setXRot(0);
        minecraft.player.xRotO = 0;
    }

    /** Bytes the render thread has allocated so far; -1 where the JVM does not tell. */
    private static long allocated(long thread) {
        if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean) {
            return bean.getThreadAllocatedBytes(thread);
        }
        return -1;
    }

    /** One row per phase, its cost against the mean of the two "mod off" phases. */
    private static void table(List<Row> rows) {
        double first = rows.get(0).frame.average(), last = rows.get(rows.size() - 1).frame.average();
        double baseline = (first + last) / 2;
        Scene.log("perf table: ms per frame (whole game), the mod's tick and extraction in ms, KB allocated per frame on the render thread");
        Scene.log(String.format(Locale.ROOT, "perf | %-24s | %7s %7s | %6s | %6s %6s | %6s %6s | %8s | %5s |",
                "phase", "frame", "max", "cost", "tick", "max", "extr.", "max", "alloc KB", "drawn"));
        for (Row row : rows) {
            Scene.log(String.format(Locale.ROOT, "perf | %-24s | %7.2f %7.2f | %+6.2f | %6.3f %6.2f | %6.3f %6.2f | %8.1f | %5d |",
                    row.name, row.frame.average(), row.frame.max(), row.frame.average() - baseline,
                    row.tick.average(), row.tick.max(), row.extract.average(), row.extract.max(),
                    row.allocated / 1024.0, row.drawn));
        }
        // Two runs of the same build differ by about this much: a smaller cost is noise.
        Scene.log(String.format(Locale.ROOT, "perf drift between the two \"mod off\" phases: %.2f ms per frame",
                Math.abs(first - last)));
    }

    /** Copies every setting of {@code from} into {@code to}, field by field; {@code to}. */
    private static FarConfig copy(FarConfig from, FarConfig to) {
        for (Field field : FarConfig.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                Object value = field.get(from);
                // The lists are the mod's own: never shared between two configs.
                if (value instanceof List<?> list) value = new ArrayList<>(list);
                field.set(to, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return to;
    }

    private static boolean get(Field field, FarConfig config) {
        try {
            return field.getBoolean(config);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Field field, FarConfig config, boolean value) {
        try {
            field.setBoolean(config, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class Row {
        String name;
        final Times frame = new Times(), tick = new Times(), extract = new Times();
        long allocated;
        int drawn;
    }

    /** Nanoseconds, given in; read back in milliseconds. */
    private static final class Times {
        private long total, max;
        private int count;

        void add(long nanos) {
            total += nanos;
            max = Math.max(max, nanos);
            count++;
        }

        double average() {
            return count == 0 ? 0 : total / 1e6 / count;
        }

        double max() {
            return max / 1e6;
        }
    }
}
