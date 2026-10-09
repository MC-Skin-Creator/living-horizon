package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The server side of the mod, which a single player world runs too: mobs this client never
 * met, 220 blocks away where the server never sends them, are shared by the server with
 * their saved data - the red sheep is red - and forgotten once killed.
 */
public final class ServerShareScenario implements Scenario {
    private static final String[] MOBS = {"cow", "horse", "villager", "pig"};

    @Override
    public String name() {
        return "servershare";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(16));
        Scene.configure(context, config -> config.hideOccludedMobs = false);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "forceload add -64 192 64 256");
            context.waitTicks(20);
            // Past where the server sends animals (160 blocks): only the mod can tell of them.
            Scene.row(world, MOBS, 1, 220, 6.0);
            Scene.command(world, "execute at @p run summon sheep ~15 ~ ~220 {NoAI:1b,Color:14b}");
            // A look at the loaded mobs, then a round of mobs sent to this client: 100 ticks each.
            context.waitTicks(260);

            context.runOnClient(minecraft -> {
                FarPlayerTracker tracker = FarPlayerTracker.get();
                Scene.log("servershare active=" + tracker.feed().active() + " shared=" + tracker.feed().mobCount()
                        + " live=" + tracker.mobs().live().size() + " shown=" + tracker.mobs().shown().size());
                if (!tracker.feed().active()) throw new AssertionError("The server running the mod was not heard");
                if (tracker.feed().mobCount() < MOBS.length + 1) {
                    throw new AssertionError("Shared " + tracker.feed().mobCount() + " mobs, expected " + (MOBS.length + 1));
                }
                int shown = 0;
                for (MobMemory.Remembered mob : tracker.mobs().shown()) {
                    Scene.log("servershare " + mob.type() + " fromServer=" + mob.fromPack() + " at "
                            + Math.round(mob.x()) + " " + Math.round(mob.z())
                            + (mob.puppet() == null ? " (not built)" : ""));
                    if (mob.fromPack() && mob.z() > 200) shown++;
                }
                if (shown < MOBS.length + 1) throw new AssertionError("Only " + shown + " shared mobs are shown");
            });
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "shared");

            Scene.command(world, "kill @e[type=sheep]");
            context.waitTicks(140);
            context.runOnClient(minecraft -> {
                for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                    if (mob.type().equals("minecraft:sheep")) throw new AssertionError("The killed sheep is still shown");
                }
                Scene.log("servershare after the kill: shared=" + FarPlayerTracker.get().feed().mobCount());
            });
            Scene.screenshot(context, this, "killed");
        }
    }
}
