package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembered mobs move as their kind does: a slime, a magma cube and a rabbit hop, a cow
 * and a chicken walk. The mobs are left 140 blocks behind, past the distance the server
 * sends them at and outside the chunks it still moves, so that their copies play their own
 * animation; how high each one leaves the ground is measured over a few hundred ticks.
 */
public final class GaitsScenario implements Scenario {
    private static final String[] MOBS = {
            "slime{Size:1}", "magma_cube{Size:1}", "rabbit", "chicken", "cow"};

    @Override
    public String name() {
        return "gaits";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.simulationDistance().set(5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> {
            config.mobTypes.add("minecraft:slime");
            config.mobTypes.add("minecraft:magma_cube");
            config.impostors = false;
            config.hideOccludedMobs = false;
            config.animationMinPixels = 0;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "difficulty easy");
            for (int i = 0; i < MOBS.length; i++) {
                String type = MOBS[i].replaceAll("\\{.*", "");
                String nbt = MOBS[i].contains("{") ? MOBS[i].substring(MOBS[i].indexOf('{') + 1, MOBS[i].length() - 1) + "," : "";
                Scene.command(world, "execute at @p run summon " + type + " ~" + (i - 2) * 5 + " ~ ~40 {" + nbt
                        + "NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
            }
            context.waitTicks(100);
            // Past the 80 blocks these are sent at, and outside the five chunks the server moves.
            Scene.command(world, "tp @a 0 ~ -100 0 0");
            context.waitTicks(200);

            Map<String, Double> highest = new HashMap<>(), moved = new HashMap<>();
            Map<String, double[]> first = new HashMap<>();
            for (int tick = 0; tick < 400; tick++) {
                context.runOnClient(minecraft -> {
                    for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                        Entity puppet = mob.puppet();
                        if (puppet == null) continue;
                        double lift = puppet.getY() - Math.floor(mob.y() + 1e-3);
                        highest.merge(mob.type(), lift, Math::max);
                        double[] start = first.computeIfAbsent(mob.type(), t -> new double[]{puppet.getX(), puppet.getZ()});
                        moved.merge(mob.type(), Math.hypot(puppet.getX() - start[0], puppet.getZ() - start[1]), Math::max);
                    }
                });
                context.waitTicks(1);
            }
            for (String type : highest.keySet()) {
                Scene.log("gaits " + type + " highest " + String.format("%.2f", highest.get(type))
                        + " farthest " + String.format("%.2f", moved.get(type)));
            }
            check(highest, "minecraft:slime", 0.5, 2);
            check(highest, "minecraft:magma_cube", 1.0, 3);
            check(highest, "minecraft:rabbit", 0.3, 1);
            check(highest, "minecraft:cow", -1, 0.05);
            check(highest, "minecraft:chicken", -1, 0.05);

            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            for (int shot = 0; shot < 8; shot++) {
                context.takeScreenshot(name() + "-hop-" + shot);
                context.waitTicks(3);
            }
            Scene.configure(context, config -> config.impostors = true);
            context.waitTicks(40);
            Scene.screenshot(context, this, "impostors");
            Scene.screenshotAtlas(context, this);
        }
    }

    private static void check(Map<String, Double> highest, String type, double least, double most) {
        Double lift = highest.get(type);
        if (lift == null) throw new AssertionError("No copy of " + type + " was drawn");
        if (lift < least || lift > most) {
            throw new AssertionError(type + " left the ground by " + lift + " blocks, expected " + least + " to " + most);
        }
    }
}
