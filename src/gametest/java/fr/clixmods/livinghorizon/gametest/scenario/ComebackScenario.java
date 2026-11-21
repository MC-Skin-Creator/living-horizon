package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Network;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Properties;

/**
 * Coming back to mobs left as copies, on a server without the mod that sends them to 24 blocks.
 * One moved while the player was away: once the server sends it again, its copy walks to it,
 * faster as the player comes close, and the real mob takes over where and as the copy stands,
 * with no tick with neither of them. One died while the player was
 * away: its copy goes within a second of the player coming where the server would send it.
 */
public final class ComebackScenario implements Scenario {
    /**
     * Ticks the real mob may stay hidden behind its copy (a copy that never gets there takes six
     * seconds), and the copy of a dead one stay drawn, once in range.
     */
    private static final int MOVED_MAX = 100, DEAD_MAX = 25;
    /**
     * The fastest a cow's copy may walk to it, in blocks a tick (a cow walks about 0.064, twice
     * that when hurried), and the ticks it may spend neither on the ground nor on the step.
     */
    private static final double PACE_MAX = 0.2;
    private static final int MIDAIR_MAX = 8;
    /** How far the real mob may stand from where its copy last stood, as it takes over. */
    private static final double JUMP_MAX = 0.5;
    /** How far back the player walks, and how slowly: one block every two ticks. */
    private static final int BACK = 50, PACE = 2;

    @Override
    public String name() {
        return "comeback";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Network.receiver(data -> {
        });
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(8));
        Scene.configure(context, config -> {
            config.hideOccludedMobs = false;
            config.onlyOverTerrain = false;
        });
        Properties properties = new Properties();
        properties.setProperty("entity-broadcast-range-percentage", "15");
        Watch watch = new Watch();
        try (TestDedicatedServerContext server = context.worldBuilder().setUseConsistentSettings(true).createServer(properties)) {
            server.runCommand("forceload add -32 " + (-BACK - 16) + " 32 16");
            server.runCommand("summon cow -3 -60 8 {NoAI:1b,Tags:[\"lh.moved\"]}");
            server.runCommand("summon cow 3 -60 8 {NoAI:1b,Tags:[\"lh.dead\"]}");
            try (var connection = server.connect()) {
                context.waitTicks(20);
                context.runOnClient(minecraft -> FarPlayerTracker.get().mobs().forgetAll());
                server.runCommand("tp @a 0 -60 0 0 0");
                context.waitTicks(200);
                for (int z = -1; z >= -BACK; z--) {
                    server.runCommand("tp @a 0 -60 " + z + " 0 0");
                    context.waitTicks(PACE);
                }
                context.waitTicks(40);
                int copies = context.computeOnClient(minecraft -> copies());
                if (copies != 2) throw new AssertionError("both cows should be copies far away: " + copies);
                // Away: one walks off to the side, the other dies.
                // On a step a block high: its copy walks up it, not up a ramp through the air.
                server.runCommand("fill -9 -60 1 -3 -60 4 stone");
                server.runCommand("tp @e[tag=lh.moved] -6 -59 2");
                server.runCommand("kill @e[tag=lh.dead]");
                context.waitTicks(20);
                // Looked at every tick: a single tick with neither the real mob nor its copy is a gap.
                for (int z = -BACK + 1; z <= 0; z++) {
                    server.runCommand("tp @a 0 -60 " + z + " 0 0");
                    for (int t = 0; t < PACE; t++) {
                        context.waitTicks(1);
                        context.runOnClient(minecraft -> watch.look(minecraft));
                    }
                }
                for (int i = 0; i < 40; i++) {
                    context.waitTicks(1);
                    context.runOnClient(minecraft -> watch.look(minecraft));
                }
                Scene.screenshot(context, this, "back");
            }
        } finally {
            Network.receiver(data -> Minecraft.getInstance().execute(() -> FarPlayerTracker.get().receive(data)));
        }
        Scene.log("comeback copy walked at up to " + String.format("%.3f", watch.fastest) + " blocks a tick, "
                + watch.midair + " ticks between the ground and the step");
        Scene.log("comeback real mob hidden behind its copy " + watch.hidden + " ticks, with neither drawn " + watch.gaps
                + " ticks, taking over " + String.format("%.2f", watch.jump) + " blocks from its copy; dead mob's copy drawn in range "
                + watch.ghost + " ticks");
        if (watch.handovers == 0) throw new AssertionError("the moved cow's copy never handed over to it");
        if (watch.gaps > 0) throw new AssertionError("ticks with neither the real cow nor its copy: " + watch.gaps);
        if (watch.hidden > MOVED_MAX) throw new AssertionError("the real cow was kept out of view " + watch.hidden + " ticks");
        if (watch.jump > JUMP_MAX) throw new AssertionError("the real cow appeared " + watch.jump + " blocks from its copy");
        if (watch.fastest > PACE_MAX) throw new AssertionError("the copy walked to its cow at up to " + watch.fastest + " blocks a tick");
        if (watch.midair > MIDAIR_MAX) throw new AssertionError("the copy was between the ground and the step for " + watch.midair + " ticks");
                if (watch.ghost > DEAD_MAX) throw new AssertionError("the dead cow's copy stayed " + watch.ghost + " ticks where the server sends cows");
    }

    /** The cows remembered, not sent. */
    private static int copies() {
        int count = 0;
        for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().remembered("minecraft:overworld")) {
            if (mob.type().equals("minecraft:cow")) count++;
        }
        return count;
    }

    /** What the player sees of the two cows, tick after tick. */
    private static final class Watch {
        int hidden, gaps, ghost, handovers, midair;
        double jump, fastest;
        private double walkX = Double.NaN, walkZ;
        private boolean wasHidden;
        private double lastX = Double.NaN, lastZ;

        void look(Minecraft minecraft) {
            MobMemory memory = FarPlayerTracker.get().mobs();
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity.getType() != EntityType.COW) continue;
                if (memory.hides(entity)) {
                    hidden++;
                    Entity puppet = null;
                    for (MobMemory.Remembered mob : memory.shown()) {
                        if (mob.id().equals(entity.getUUID())) puppet = mob.puppet();
                    }
                    if (puppet == null) {
                        gaps++;
                    } else {
                        if (!Double.isNaN(walkX)) fastest = Math.max(fastest, Math.hypot(puppet.getX() - walkX, puppet.getZ() - walkZ));
                        walkX = puppet.getX();
                        walkZ = puppet.getZ();
                        double y = puppet.getY();
                        if (y > -59.95 && y < -59.05) midair++;
                        lastX = puppet.getX();
                        lastZ = puppet.getZ();
                    }
                    wasHidden = true;
                } else if (wasHidden) {
                    // Just taken over: from where its copy last stood.
                    handovers++;
                    if (!Double.isNaN(lastX)) jump = Math.max(jump, Math.hypot(entity.getX() - lastX, entity.getZ() - lastZ));
                    Scene.log("comeback took over " + String.format("%.2f", Math.hypot(entity.getX() - lastX, entity.getZ() - lastZ))
                            + " blocks from its copy, after " + hidden + " ticks hidden");
                    wasHidden = false;
                }
            }
            // The dead cow's copy (east of the line) drawn where the server would send it.
            for (MobMemory.Remembered mob : memory.shown()) {
                if (!mob.type().equals("minecraft:cow") || mob.x() < 0) continue;
                double across = Math.hypot(mob.x() - minecraft.player.getX(), mob.z() - minecraft.player.getZ());
                if (across < 20) ghost++;
            }
        }
    }
}
