package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * A mob the server stops sending is drawn as its copy from the very next frame. The game
 * reads the server's packets every frame but ticks every 50 ms: a copy made on the next
 * tick left the frames in between with no mob at all.
 *
 * <p>The cow is removed on the client the way the server's packet removes it, and its copy
 * must be built and listed before any tick runs. It stands 150 blocks away, where the
 * server still sends it and the copy is drawn.
 *
 * <p>The copy faces the way the real mob was drawn: its body and head, which a mob standing
 * still turns away from its yaw, the direction it last walked.
 */
public final class UnloadScenario implements Scenario {
    private static final float BODY = 20f, HEAD = 45f;

    @Override
    public String name() {
        return "unload";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.renderDistance().set(16);
            minecraft.options.fov().set(30);
        });
        Scene.configure(context, config -> config.hideOccludedMobs = false);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.summon(world, "cow", 0, 150, 90);
            context.waitTicks(60);
            Scene.screenshot(context, this, "real");
            UUID id = context.computeOnClient(minecraft -> {
                Entity cow = null;
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity.getType() == EntityType.COW) cow = entity;
                }
                if (cow == null) throw new AssertionError("The server does not send the cow");
                // Summoned facing 90: body and head turned elsewhere, as a mob looking around has them.
                if (cow instanceof LivingEntity living) {
                    living.yBodyRot = living.yBodyRotO = BODY;
                    living.yHeadRot = living.yHeadRotO = HEAD;
                }
                // What the server's packet does when it stops sending a mob.
                minecraft.level.removeEntity(cow.getId(), Entity.RemovalReason.DISCARDED);
                for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                    if (mob.id().equals(cow.getUUID()) && mob.puppet() instanceof LivingEntity copy) {
                        Scene.log("unload copy shown before the next tick at " + copy.position()
                                + ", body " + copy.yBodyRot + ", head " + copy.yHeadRot);
                        if (Math.abs(Mth.wrapDegrees(copy.yBodyRot - BODY)) > 1
                                || Math.abs(Mth.wrapDegrees(copy.yHeadRot - HEAD)) > 1) {
                            throw new AssertionError("The cow's copy turned: body " + copy.yBodyRot + " for " + BODY
                                    + ", head " + copy.yHeadRot + " for " + HEAD);
                        }
                        return cow.getUUID();
                    }
                }
                throw new AssertionError("The cow's copy waits for the next tick: frames without the cow");
            });
            Scene.screenshot(context, this, "copy");
            context.runOnClient(minecraft -> {
                for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                    if (mob.id().equals(id) && mob.puppet() instanceof LivingEntity copy) {
                        Scene.log("unload copy after the next tick: body " + copy.yBodyRot + ", head " + copy.yHeadRot);
                        if (Math.abs(Mth.wrapDegrees(copy.yBodyRot - BODY)) > 5) {
                            throw new AssertionError("The cow's copy turned on its first tick: body " + copy.yBodyRot);
                        }
                    }
                }
                boolean shown = FarPlayerTracker.get().mobs().shown().stream()
                        .anyMatch(mob -> mob.id().equals(id) && mob.puppet() != null);
                if (!shown) throw new AssertionError("The cow's copy is gone after the next tick");
            });
        }
    }
}
