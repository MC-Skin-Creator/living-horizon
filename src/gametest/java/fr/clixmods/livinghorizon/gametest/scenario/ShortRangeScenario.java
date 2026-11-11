package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Network;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;

import java.util.Properties;

/**
 * A server without the mod (the client throws away what the test's server shares), that sends mobs only within 24 blocks,
 * as some servers do to save work (mc.peacefulvanilla.club). The player walks slowly backwards
 * from a few cows, looking at them: the server stops sending each one about 24 blocks away,
 * and every one of them is remembered and drawn as a copy, not taken for killed.
 */
public final class ShortRangeScenario implements Scenario {
    /** The cows, along the line of sight, this many blocks ahead of where the player starts. */
    private static final int FIRST = 8, STEP = 4, COWS = 4;
    /** How far back the player walks, and how slowly: one block every two ticks. */
    private static final int BACK = 60, PACE = 2;

    @Override
    public String name() {
        return "shortrange";
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
        // Cows are sent to 160 blocks at 100 %: at 15 %, to 24 blocks.
        Properties properties = new Properties();
        properties.setProperty("entity-broadcast-range-percentage", "15");
        int kept;
        try (TestDedicatedServerContext server = context.worldBuilder().setUseConsistentSettings(true).createServer(properties)) {
            server.runCommand("forceload add -16 " + (-BACK - 16) + " 16 " + (FIRST + STEP * COWS));
            for (int i = 0; i < COWS; i++) {
                server.runCommand("summon cow 0 -60 " + (FIRST + i * STEP) + " {NoAI:1b,Rotation:[90f,0f]}");
            }
            try (var connection = server.connect()) {
                server.runCommand("tp @a 0 -60 0 0 0");
                context.waitTicks(200);
                context.runOnClient(minecraft -> {
                    int sent = 0;
                    for (var entity : minecraft.level.entitiesForRendering()) {
                        if (entity.getType() == EntityType.COW) sent++;
                    }
                    if (sent < COWS) throw new AssertionError("only " + sent + " of the " + COWS + " cows are sent at the start");
                    Scene.log("shortrange start, reach " + FarPlayerTracker.get().mobs().reach() + ", cows sent " + sent);
                });
                for (int z = -1; z >= -BACK; z--) {
                    server.runCommand("tp @a 0 -60 " + z + " 0 0");
                    context.waitTicks(PACE);
                }
                context.waitTicks(40);
                kept = context.computeOnClient(minecraft -> {
                    int copies = 0;
                    for (var mob : FarPlayerTracker.get().mobs().remembered("minecraft:overworld")) {
                        if (mob.type().equals("minecraft:cow")) copies++;
                    }
                    Scene.log("shortrange far, reach " + FarPlayerTracker.get().mobs().reach() + ", cows remembered "
                            + copies + " of " + COWS);
                    return copies;
                });
                Scene.screenshot(context, this, "far");
            }
        } finally {
            // As the client's entry point hands it in.
            Network.receiver(data -> Minecraft.getInstance().execute(() -> FarPlayerTracker.get().receive(data)));
        }
        if (kept < COWS) throw new AssertionError("cows dropped by the server at 24 blocks and forgotten: " + (COWS - kept) + " of " + COWS);
    }
}
