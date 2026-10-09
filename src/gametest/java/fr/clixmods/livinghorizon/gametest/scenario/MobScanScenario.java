package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.MobScan;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.concurrent.CompletableFuture;

/**
 * The scan of the chunks around: mobs left behind in chunks the server has since unloaded
 * (so they are only in the save) must all be found.
 */
public final class MobScanScenario implements Scenario {
    private static final int MOBS = 40;

    @Override
    public String name() {
        return "scan";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.mobTypes.add("minecraft:cow"));
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Cows spread over the chunks 300 blocks out, then abandoned.
            for (int i = 0; i < MOBS; i++) {
                Scene.command(world, "execute in minecraft:overworld run summon cow " + (i * 7 - 140) + " 5 300 {NoAI:1b,PersistenceRequired:1b}");
            }
            context.waitTicks(40);
            Scene.command(world, "tp @a 0 ~ 0 0 0");
            context.waitTicks(400);
            Scene.command(world, "save-all");
            context.waitTicks(40);
            CompletableFuture<MobScan.Result> scan = context.computeOnClient(minecraft -> MobScan.scan(minecraft, 40));
            // The server only advances while the test lets the game tick: never block on the future.
            for (int waited = 0; !scan.isDone() && waited < 600; waited++) context.waitTicks(1);
            if (!scan.isDone()) throw new AssertionError("The scan did not finish");
            MobScan.Result result = scan.join();
            Scene.log("scan summoned=" + MOBS + " found=" + result.mobs().size() + " chunksRead=" + result.chunksRead());
            if (result.mobs().size() < MOBS) {
                throw new AssertionError("The scan found " + result.mobs().size() + " of " + MOBS + " mobs");
            }
        }
    }
}
