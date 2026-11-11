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
 * One moved while the player was away: once the server sends it again, the real mob shows at
 * once, never kept out of view while its copy walks up to it. One died while the player was
 * away: its copy goes within a second of the player coming where the server would send it.
 */
public final class ComebackScenario implements Scenario {
    /** Ticks the real mob may stay hidden, and the copy of a dead one stay drawn, once in range. */
    private static final int MOVED_MAX = 2, DEAD_MAX = 25;
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
        int hidden = 0, ghost = 0;
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
                int[] copies = context.computeOnClient(minecraft -> new int[]{copies(), 0});
                if (copies[0] != 2) throw new AssertionError("both cows should be copies far away: " + copies[0]);
                // Away: one walks off to the side, the other dies.
                server.runCommand("tp @e[tag=lh.moved] -12 -60 2");
                server.runCommand("kill @e[tag=lh.dead]");
                context.waitTicks(20);
                for (int z = -BACK + 1; z <= 0; z++) {
                    server.runCommand("tp @a 0 -60 " + z + " 0 0");
                    context.waitTicks(PACE);
                    int[] seen = context.computeOnClient(ComebackScenario::look);
                    hidden += seen[0];
                    ghost += seen[1];
                    if (seen[0] + seen[1] > 0) Scene.log("comeback z=" + z + " real hidden " + seen[0] + ", dead copy drawn " + seen[1]);
                }
                for (int i = 0; i < 40; i++) {
                    context.waitTicks(1);
                    int[] seen = context.computeOnClient(ComebackScenario::look);
                    hidden += seen[0];
                    ghost += seen[1];
                }
                Scene.screenshot(context, this, "back");
            }
        } finally {
            Network.receiver(data -> Minecraft.getInstance().execute(() -> FarPlayerTracker.get().receive(data)));
        }
        Scene.log("comeback real mob hidden for about " + hidden * PACE + " ticks, dead mob's copy drawn in range for about "
                + ghost * PACE + " ticks");
        if (hidden > MOVED_MAX) throw new AssertionError("the real cow was kept out of view while its copy walked up: " + hidden + " looks");
        if (ghost * PACE > DEAD_MAX) throw new AssertionError("the dead cow's copy stayed " + ghost * PACE + " ticks where the server sends cows");
    }

    /** The cows remembered, not sent. */
    private static int copies() {
        int count = 0;
        for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().remembered("minecraft:overworld")) {
            if (mob.type().equals("minecraft:cow")) count++;
        }
        return count;
    }

    /**
     * Whether the moved cow is sent but kept out of view, and whether the dead cow's copy is drawn
     * where the server would send it (the player within 20 blocks across of it).
     */
    private static int[] look(Minecraft minecraft) {
        MobMemory memory = FarPlayerTracker.get().mobs();
        int hidden = 0, ghost = 0;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity.getType() == EntityType.COW && memory.hides(entity)) hidden++;
        }
        for (MobMemory.Remembered mob : memory.shown()) {
            if (!mob.type().equals("minecraft:cow") || mob.x() < 0) continue;
            double across = Math.hypot(mob.x() - minecraft.player.getX(), mob.z() - minecraft.player.getZ());
            if (across < 20) ghost++;
        }
        return new int[]{hidden, ghost};
    }
}
