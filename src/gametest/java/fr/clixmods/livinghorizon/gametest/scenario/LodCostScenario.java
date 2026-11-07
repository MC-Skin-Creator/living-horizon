package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobScan;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Locale;

/**
 * What the levels of detail cost the game's thread: a field of remembered mobs drawn as models
 * (no impostors), measured with <i>Detail Levels</i> on and off, four times each in turn.
 *
 * <p>The client game tests draw one frame a tick, so a frame's time says nothing: this reads
 * the CPU time of the game's thread per frame, where Sodium's fast cubes and the levels'
 * own work show. Run it with Sodium ({@code run.sh lodcost --sodium}), as most players play.
 * {@code -Dlodcost.only=true} (or false) measures one side only, to profile it.
 */
public final class LodCostScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "pig", "chicken", "villager", "horse"};
    private static final int ROUNDS = 4, SETTLE = 120, FRAMES = 300;

    @Override
    public String name() {
        return "lodcost";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.impostorDistance = 2000;
            config.impostorPixels = 0;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            int i = 0;
            for (int x = -176; x <= 176; x += 12) {
                for (int z = -176; z <= 176; z += 12) {
                    Scene.command(world, "summon " + MOBS[i % MOBS.length] + " " + x + " -60 " + z
                            + " {NoAI:1b,Rotation:[" + (i * 45 % 360) + "f,0f]}");
                    i++;
                }
            }
            context.waitTicks(100);
            // Away from them: they are remembered copies now, drawn by the mod.
            Scene.command(world, "tp @a 0 ~ -300 0 0");
            context.waitTicks(300);
            context.runOnClient(minecraft -> MobScan.start(minecraft, 32));
            context.waitTicks(200);
            long thread = context.computeOnClient(minecraft -> Thread.currentThread().threadId());
            int shown = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().shown().size());
            if (shown < 200) throw new AssertionError("lodcost: only " + shown + " remembered copies, the scene is wrong");
            Scene.log("lodcost " + shown + " remembered mobs");
            String only = System.getProperty("lodcost.only");
            boolean[] sides = only != null ? new boolean[]{Boolean.parseBoolean(only)} : new boolean[]{true, false};
            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            double[] total = new double[2];
            for (int round = 0; round < ROUNDS; round++) {
                for (boolean lods : sides) {
                    context.runOnClient(minecraft -> FarConfig.get().mobLods = lods);
                    context.waitTicks(SETTLE);
                    long cpu = bean.getThreadCpuTime(thread);
                    context.waitTicks(FRAMES);
                    double millis = (bean.getThreadCpuTime(thread) - cpu) / 1e6 / FRAMES;
                    total[lods ? 0 : 1] += millis;
                    String counts = context.computeOnClient(minecraft -> "triangles " + PolygonStats.triangles()
                            + ", quads " + PolygonStats.polygons() + ", figures per level " + PolygonStats.figures(0)
                            + "/" + PolygonStats.figures(1) + "/" + PolygonStats.figures(2) + "/" + PolygonStats.figures(3));
                    Scene.log(String.format(Locale.ROOT, "lodcost round %d, Detail Levels %-3s %.2f ms per frame, %s",
                            round, lods ? "on" : "off", millis, counts));
                }
            }
            if (sides.length == 2) {
                Scene.log(String.format(Locale.ROOT, "lodcost on average: on %.2f ms, off %.2f ms per frame",
                        total[0] / ROUNDS, total[1] / ROUNDS));
            }
        }
    }
}
