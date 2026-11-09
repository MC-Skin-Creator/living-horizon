package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.Mca;
import fr.clixmods.livinghorizon.compat.McaLook;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.track.EntityNbt;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.PositionsMessage;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Players who logged off, with Minecraft Comes Alive (MCA; {@code --with minecraft-comes-alive-reborn}):
 * one shared with a look of MCA, read from one of its villagers as the server reads a player's,
 * one without, side by side 6 blocks ahead. The first must be drawn as MCA draws that villager,
 * the second in their skin. Without MCA it says so and checks nothing.
 */
public final class McaScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    private static final String PLAIN = "LH_Plain", DRESSED = "LH_Mca";

    @Override
    public String name() {
        return "mca";
    }

    @Override
    public void run(ClientGameTestContext context) {
        if (!Platform.isModLoaded(McaLook.MOD)) {
            Scene.log("mca: Minecraft Comes Alive is not installed, nothing checked");
            return;
        }
        Scene.configure(context, config -> config.offlineLife = false);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // MCA may greet a new player with a screen of its own.
            context.setScreen(() -> null);
            Scene.command(world, "summon mca:female_villager 0.5 " + GROUND + " 40.5 {NoAI:1b}");
            context.waitTicks(20);
            String look = world.getServer().computeOnServer(server -> {
                for (Entity entity : server.overworld().getAllEntities()) {
                    if (EntityType.getKey(entity.getType()).toString().equals("mca:female_villager")) {
                        return McaLook.trim(EntityNbt.save(entity, entity.level()));
                    }
                }
                return null;
            });
            if (look == null) throw new AssertionError("mca: no look read from MCA's villager");
            Scene.log("mca: look of " + look.length() + " characters");
            Scene.screenshot(context, this, "villager");
            Scene.command(world, "kill @e[type=mca:female_villager]");

            List<PositionsMessage.Player> shared = List.of(
                    offline(PLAIN, -1.5, null),
                    offline(DRESSED, 1.5, look));
            for (int done = 0; done < 60; done += 10) {
                context.runOnClient(minecraft -> {
                    FarPlayerTracker.get().receive(PositionsMessage.players(List.of()));
                    PositionsMessage.offline(shared).forEach(FarPlayerTracker.get()::receive);
                });
                context.waitTicks(10);
            }
            int plain = context.computeOnClient(minecraft -> lookHash(minecraft, PLAIN));
            int dressed = context.computeOnClient(minecraft -> lookHash(minecraft, DRESSED));
            Scene.log("mca: handed to MCA, plain " + (plain != 0) + ", dressed " + (dressed != 0));
            Scene.screenshot(context, this, "offline");
            if (plain != 0 || dressed == 0) {
                throw new AssertionError("mca: the look went to the wrong player, plain " + plain + ", dressed " + dressed);
            }
        }
    }

    /** A player who logged off 6 blocks ahead, facing the camera, in this look of MCA. */
    private static PositionsMessage.Player offline(String name, double x, @Nullable String mca) {
        return new PositionsMessage.Player(name, x, GROUND, 6.5, 180f, "minecraft:overworld", false, false, null, null,
                new PositionsMessage.Look(null, null, List.of(), mca), 0, false);
    }

    /** What MCA was handed for the puppet of {@code name} ({@link Mca#lookHash}), 0 for nothing. */
    private static int lookHash(Minecraft minecraft, String name) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (spot.name().equals(name) && minecraft.level != null) return Mca.lookHash(resting.puppet(minecraft.level, spot));
        }
        throw new AssertionError("mca: no puppet for " + name);
    }
}
