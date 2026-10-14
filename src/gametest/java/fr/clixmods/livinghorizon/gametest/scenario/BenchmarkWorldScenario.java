package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.debug.BenchmarkWorld;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * {@code /livinghorizon benchmark world}, from the title screen to the end: the world made,
 * nothing in it but the mobs placed (none spawned by the game), the far herds remembered,
 * and a look at it facing south. The world is closed again afterwards.
 */
public final class BenchmarkWorldScenario implements Scenario {
    @Override
    public String name() {
        return "benchmarkworld";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(BenchmarkWorld::start);
        for (int i = 0; i < 20 * 60 * 2 && context.computeOnClient(minecraft -> BenchmarkWorld.running()); i++) {
            context.waitTicks(10);
        }
        if (context.computeOnClient(minecraft -> BenchmarkWorld.running())) throw new AssertionError("The benchmark world was never ready");
        String counts = context.computeOnClient(minecraft -> {
            var server = minecraft.getSingleplayerServer();
            if (server == null) return "no server";
            int placedCount = 0, other = 0;
            StringBuilder strays = new StringBuilder();
            for (Entity entity : server.overworld().getAllEntities()) {
                if (entity instanceof Player) continue;
                //? if >=26.1 {
                /*boolean placed = entity.entityTags().contains(BenchmarkWorld.TAG);
                *///?} else
                boolean placed = entity.getTags().contains(BenchmarkWorld.TAG);
                if (placed) {
                    placedCount++;
                } else {
                    other++;
                    if (strays.length() < 300) strays.append(entity.getType()).append(" at ").append(entity.blockPosition()).append("; ");
                }
            }
            return placedCount + " placed loaded, " + other + " others " + strays + "| remembered "
                    + FarPlayerTracker.get().mobs().size() + ", shown " + FarPlayerTracker.get().mobs().shown().size()
                    + " | player at " + minecraft.player.blockPosition() + " yaw " + minecraft.player.getYRot();
        });
        Scene.log("benchmark world: " + counts);
        if (!counts.contains(" 0 others")) throw new AssertionError("Mobs the benchmark did not place: " + counts);
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(16));
        context.waitTicks(100);
        Scene.screenshot(context, this, "south");
        context.runOnClient(BenchmarkWorld::leave);
        // Closed once the integrated server's thread has ended, as the test framework counts it:
        // this world is not one the framework made, so it is not closed for us.
        for (int i = 0; i < 600 && (net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl.isServerRunning
                || context.computeOnClient(minecraft -> minecraft.level != null)); i++) {
            context.waitTicks(5);
        }
    }
}
