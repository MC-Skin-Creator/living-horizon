package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Graveyard's crows, ravens and magpies: far away their copies fly, wings flapping, as the
 * game's own flyers and Ambient Birds' birds do. They used to walk on the ground, perched. Run
 * with it: {@code run.sh graveyardbirds --with the-graveyard-fabric,villagerstradingplus,geckolib}.
 */
public final class GraveyardBirdsScenario implements Scenario {
    private static final String[] BIRDS = {"crow", "raven", "magpie"};

    @Override
    public String name() {
        return "graveyardbirds";
    }

    @Override
    public void run(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("graveyard")) {
            Scene.log("graveyardbirds: The Graveyard not loaded, nothing to check");
            return;
        }
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.simulationDistance().set(5);
            minecraft.options.renderDistance().set(16);
            minecraft.options.fov().set(30);
        });
        Scene.configure(context, config -> {
            config.hideOccludedMobs = false;
            config.impostors = false;
            config.animationMinPixels = 0;
            for (String bird : BIRDS) {
                if (!config.mobTypes.contains("graveyard:" + bird)) config.mobTypes.add("graveyard:" + bird);
            }
        });
        List<String> failures = new ArrayList<>();
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            for (int i = 0; i < BIRDS.length; i++) {
                for (int j = 0; j < 2; j++) {
                    Scene.command(world, "summon graveyard:" + BIRDS[i] + " " + ((i - 1) * 8 + j * 3)
                            + " ~ 64 {NoAI:1b,PersistenceRequired:1b}");
                }
            }
            Scene.command(world, "tp @a 0 ~ 26 0 0");
            context.waitTicks(100);
            Scene.command(world, "tp @a 0 ~ -80 0 0");
            context.waitTicks(300);
            Map<String, Integer> looks = new HashMap<>(), flying = new HashMap<>(), flapping = new HashMap<>();
            double ground = context.computeOnClient(minecraft -> minecraft.player.getY());
            for (int sample = 0; sample < 30; sample++) {
                context.waitTicks(10);
                context.runOnClient(minecraft -> {
                    MobMemory memory = FarPlayerTracker.get().mobs();
                    for (MobMemory.Remembered mob : memory.shown()) {
                        if (!mob.type().startsWith("graveyard:") || mob.puppet() == null) continue;
                        Entity copy = mob.puppet();
                        looks.merge(mob.type(), 1, Integer::sum);
                        if (copy.getY() > ground + 2) flying.merge(mob.type(), 1, Integer::sum);
                        if (GeckoLibScenario.playing(copy).stream().anyMatch(name -> name.endsWith(".fly"))) {
                            flapping.merge(mob.type(), 1, Integer::sum);
                        }
                    }
                });
            }
            Scene.screenshot(context, this, "copies");
            for (String bird : BIRDS) {
                String kind = "graveyard:" + bird;
                int seen = looks.getOrDefault(kind, 0);
                Scene.log("graveyardbirds " + kind + ": " + seen + " looks, " + flying.getOrDefault(kind, 0)
                        + " in the air, " + flapping.getOrDefault(kind, 0) + " flapping");
                if (seen == 0) {
                    failures.add("No copy of " + kind + " was remembered");
                    continue;
                }
                if (flying.getOrDefault(kind, 0) * 2 < seen) failures.add("The copies of " + kind + " do not fly");
                if (flapping.getOrDefault(kind, 0) * 2 < seen) failures.add("The copies of " + kind + " fly without flapping");
            }
        }
        if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
    }
}
