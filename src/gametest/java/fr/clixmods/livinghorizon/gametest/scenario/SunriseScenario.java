package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Undead remembered at night, then the sun rises: the ones out in the open go, the one
 * under a roof and the one wearing a helmet stay.
 *
 * <p>The mobs stand 100 blocks from the player, in chunks the client still holds - the
 * sky above them is read from those chunks; Voxy and Distant Horizons cannot run here -
 * but past where the server sends monsters, with the entity distance at half.
 */
public final class SunriseScenario implements Scenario {
    @Override
    public String name() {
        return "sunrise";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.entityDistanceScaling().set(0.5));
        Scene.configure(context, config -> {
            config.mobTypes.add("minecraft:zombie");
            config.mobTypes.add("minecraft:skeleton");
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "time set midnight");
            // Out in the open: a zombie and a skeleton. Under a stone roof: a zombie.
            // In the open with a helmet: a zombie.
            Scene.summon(world, "zombie", -6, 60, 180);
            Scene.summon(world, "skeleton", -2, 60, 180);
            Scene.command(world, "execute at @p run fill ~1 ~3 ~58 ~5 ~3 ~62 minecraft:stone");
            Scene.summon(world, "zombie", 3, 60, 180);
            Scene.command(world, "execute at @p run summon zombie ~8 ~ ~60 {NoAI:1b,Tags:[\"helmet\"]}");
            Scene.command(world, "item replace entity @e[tag=helmet] armor.head with minecraft:iron_helmet");
            context.waitTicks(100);
            Scene.command(world, "tp @a 0 ~ -40 0 0");
            context.waitTicks(300);
            List<String> night = remembered(context);
            Scene.log("sunrise at night: " + night);
            if (night.size() != 4) throw new AssertionError("expected 4 remembered undead at night, got " + night);
            Scene.screenshot(context, this, "night");

            Scene.command(world, "time set noon");
            // Looked at every 100 ticks.
            context.waitTicks(220);
            List<String> day = remembered(context);
            Scene.log("sunrise at noon: " + day);
            List<String> expected = List.of("minecraft:zombie@3", "minecraft:zombie@8");
            if (!day.equals(expected)) throw new AssertionError("expected " + expected + " at noon, got " + day);
            Scene.screenshot(context, this, "noon");
        }
    }

    /** The remembered copies being drawn, as type@x, from west to east. */
    private static List<String> remembered(ClientGameTestContext context) {
        return context.computeOnClient(minecraft -> {
            List<MobMemory.Remembered> shown = new ArrayList<>(FarPlayerTracker.get().mobs().shown());
            shown.removeIf(mob -> mob.puppet() == null);
            shown.sort((a, b) -> Double.compare(a.x(), b.x()));
            List<String> found = new ArrayList<>();
            for (MobMemory.Remembered mob : shown) found.add(mob.type() + "@" + (int) Math.floor(mob.x()));
            return found;
        });
    }
}
