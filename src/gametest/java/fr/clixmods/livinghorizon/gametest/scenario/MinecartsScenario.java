package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Minecarts the server no longer sends: parked ones stay where they were, and one sent
 * off along a line of powered rails rolls on as far as the server keeps moving it.
 *
 * <p>The server sends a minecart 64 blocks away here (entity distance 0.5) but moves it
 * within its simulation distance (12 chunks): the copy must roll on from where the client
 * lost it, and stop about where the server's cart stops, at the end of the line.
 */
public final class MinecartsScenario implements Scenario {
    private static final String[] CARTS = {"minecart", "chest_minecart", "furnace_minecart", "hopper_minecart", "tnt_minecart"};
    /** {@code UUID:[I;0,0,0,42]}, as the game writes it. */
    private static final UUID MOVING = new UUID(0L, 42L);

    @Override
    public String name() {
        return "minecarts";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(16);
            minecraft.options.simulationDistance().set(12);
        });
        Scene.configure(context, config -> config.hideOccludedMobs = false);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Past 32 blocks: a cart that vanishes closer is taken as broken.
            Scene.row(world, CARTS, 1, 40, 3.0);
            // A powered line to the south, along x = 12: the cart keeps going at 8 blocks a second.
            // A piece at a time: a fill reaching past the chunks the server has loaded does
            // nothing, and the line ends where they do.
            context.waitTicks(100);
            for (int from = 2; from < 230; from += 32) {
                int to = Math.min(230, from + 31);
                Scene.command(world, "execute at @p run fill ~12 ~-1 ~" + from + " ~12 ~-1 ~" + to + " minecraft:redstone_block");
                Scene.command(world, "execute at @p run fill ~12 ~ ~" + from + " ~12 ~ ~" + to
                        + " minecraft:powered_rail[shape=north_south,powered=true]");
            }
            context.waitTicks(40);
            Scene.command(world, "execute at @p run summon minecraft:minecart ~12 ~ ~4 {UUID:[I;0,0,0,42],Motion:[0.0,0.0,0.4]}");

            context.waitTicks(3);
            Scene.log("minecarts just sent off: " + world.getServer().computeOnServer(server -> {
                Entity cart = server.overworld().getEntity(MOVING);
                return cart == null ? "none" : cart.position() + " moving " + cart.getDeltaMovement();
            }));
            for (int second = 5; second <= 40; second += 5) {
                context.waitTicks(100);
                double real = serverZ(world);
                int at = second;
                context.runOnClient(minecraft -> Scene.log("minecarts t=" + at + "s server z=" + Math.round(real)
                        + " copy z=" + copyZ() + " sent=" + FarPlayerTracker.get().mobs().live().stream().anyMatch(e -> e.getUUID().equals(MOVING))
                        + " simulation=" + (minecraft.level == null ? -1 : minecraft.level.getServerSimulationDistance())));
            }
            double real = serverZ(world);
            double copy = context.computeOnClient(minecraft -> copyZ());
            Scene.log("minecarts stopped: server z=" + real + " copy z=" + copy);
            if (Double.isNaN(copy) || Math.abs(copy - real) > 4) {
                throw new AssertionError("the copy stopped at z=" + copy + ", the server's cart at z=" + real);
            }

            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.command(world, "tp @a 12.5 ~2 0 0 10");
            Scene.screenshot(context, this, "line");
            // The parked ones, left 200 blocks behind.
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);
            context.runOnClient(minecraft -> {
                for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                    Scene.log("minecarts remembered " + mob.type() + " at " + Math.round(mob.x()) + " " + Math.round(mob.z()));
                }
            });
            Scene.screenshot(context, this, "parked");
        }
    }

    /** Where the server's cart is along the line. */
    private static double serverZ(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> {
            Entity cart = server.overworld().getEntity(MOVING);
            return cart == null ? Double.NaN : cart.getZ();
        });
    }

    private static double copyZ() {
        for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
            if (mob.id().equals(MOVING)) return mob.z();
        }
        return Double.NaN;
    }
}
