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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Useless Reptile's dragons, animated with Biscuit Roll from 1.21.11, whose animations are chosen
 * in the dragon's tick (on the server only from 0.15): their copies never tick, played nothing
 * and stood in a T-pose far away. Run with it: {@code run.sh uselessreptile --with
 * useless-reptile,yacl} (26.x also needs Fabric Loader 0.19.5 for 26.3). Without it, or with a
 * version drawn by GeckoLib (the {@code geckolib} scenario's), it checks nothing.
 */
public final class UselessReptileScenario implements Scenario {
    private static final String[] DRAGONS = {"wyvern", "moleclaw", "river_pikehorn", "lightning_chaser"};

    @Override
    public String name() {
        return "uselessreptile";
    }

    @Override
    public void run(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("uselessreptile") || !FabricLoader.getInstance().isModLoaded("biscuit_roll")) {
            Scene.log("uselessreptile: Useless Reptile with Biscuit Roll not loaded, nothing to check");
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
            // Past the 8 chunks the client holds, without Voxy: drawn over the void all the same.
            config.onlyOverTerrain = false;
            config.impostors = false;
            for (String dragon : DRAGONS) {
                if (!config.mobTypes.contains("uselessreptile:" + dragon)) config.mobTypes.add("uselessreptile:" + dragon);
            }
        });
        List<String> failures = new ArrayList<>();
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            for (int i = 0; i < DRAGONS.length; i++) {
                Scene.command(world, "summon uselessreptile:" + DRAGONS[i] + " " + (i - 1.5) * 8
                        + " ~ 64 {NoAI:1b,PersistenceRequired:1b,Rotation:[90f,0f]}");
            }
            // Seen from 38 blocks (some are sent only closer than 64), then left 214 blocks behind.
            Scene.command(world, "tp @a 0 ~ 26 0 0");
            context.waitTicks(100);
            context.runOnClient(minecraft -> {
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    String kind = net.minecraft.world.entity.EntityType.getKey(entity.getType()).toString();
                    if (kind.startsWith("uselessreptile:")) {
                        Scene.log("uselessreptile seen " + kind + " at " + entity.blockPosition() + ", "
                                + Math.round(Math.sqrt(entity.distanceToSqr(minecraft.player))) + " blocks away");
                    }
                }
            });
            // The rideable ones are sent as far as the server's view distance, which follows the
            // render distance in single player: lowered, they become copies past 128 blocks.
            context.runOnClient(minecraft -> minecraft.options.renderDistance().set(8));
            Scene.command(world, "tp @a 0 ~ -150 0 0");
            context.waitTicks(300);
            context.runOnClient(minecraft -> {
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    String kind = net.minecraft.world.entity.EntityType.getKey(entity.getType()).toString();
                    if (kind.startsWith("uselessreptile:")) Scene.log("uselessreptile still sent " + kind + " "
                            + Math.round(Math.sqrt(entity.distanceToSqr(minecraft.player))) + " blocks away");
                }
            });
            Map<String, Integer> looks = new HashMap<>(), posed = new HashMap<>();
            for (int sample = 0; sample < 30; sample++) {
                context.waitTicks(10);
                context.runOnClient(minecraft -> {
                    MobMemory memory = FarPlayerTracker.get().mobs();
                    for (MobMemory.Remembered mob : memory.shown()) {
                        if (!mob.type().startsWith("uselessreptile:") || mob.puppet() == null) continue;
                        looks.merge(mob.type(), 1, Integer::sum);
                        if (playing(mob.puppet()) > 0) posed.merge(mob.type(), 1, Integer::sum);
                    }
                });
            }
            Scene.screenshot(context, this, "copies");
            for (String dragon : DRAGONS) {
                String kind = "uselessreptile:" + dragon;
                int seen = looks.getOrDefault(kind, 0), animated = posed.getOrDefault(kind, 0);
                Scene.log("uselessreptile " + kind + ": " + seen + " looks at its copy, " + animated + " playing an animation");
                // A dragon summoned without AI is sometimes never sent at all (the wyvern on 1.21.11).
                if (seen > 0 && animated * 2 < seen) failures.add("The copies of " + kind + " play no animation: a T-pose");
            }
            if (looks.isEmpty()) failures.add("No dragon was remembered");
        }
        if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
    }

    /** How many animations Biscuit Roll's controllers play on this entity. */
    private static int playing(Entity entity) {
        try {
            int count = 0;
            for (Object controller : (Collection<?>) entity.getClass().getMethod("getAnimationControllers").invoke(entity)) {
                count += ((Collection<?>) controller.getClass().getMethod("getPlayingAnimations").invoke(controller)).size();
            }
            return count;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Biscuit Roll is loaded, but not the version this scenario knows", e);
        }
    }
}
