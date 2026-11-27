package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Flying back to a herd left as copies: every cow is followed tick by tick, as the player sees
 * it (its copy while the copy is drawn, the real one otherwise). A figure that moves more than a
 * couple of blocks in a tick, or shows up off the ground, jumped; one gone and back within a
 * second blinked.
 */
public final class PopInScenario implements Scenario {
    private static final int HERD = 24;
    private static final double JUMP = 2.0;

    @Override
    public String name() {
        return "popin";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(8));
        Scene.configure(context, config -> {
            config.hideOccludedMobs = false;
            config.onlyOverTerrain = false;
        });
        Watch watch = new Watch();
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "forceload add -64 0 64 128");
            for (int i = 0; i < HERD; i++) {
                int x = (i % 6) * 6 - 15, z = 60 + (i / 6) * 6;
                Scene.command(world, "summon " + (i % 3 == 0 ? "sheep" : i % 3 == 1 ? "cow" : "pig") + " " + x + " -60 " + z);
            }
            context.waitTicks(40);
            // Away until the server stops sending them, and long enough for them to wander.
            Scene.command(world, "tp @a 0 -50 -300 0 0");
            context.waitTicks(400);
            Scene.log("popin remembered " + context.computeOnClient(m -> FarPlayerTracker.get().mobs().shown().size()));
            // Back at about flying speed, half a block a tick, a little above the ground.
            for (int step = 0; step < 700; step++) {
                double z = -300 + step * 0.5;
                Scene.command(world, "tp @a 0 -50 " + z + " 0 20");
                context.waitTicks(1);
                context.runOnClient(watch::look);
            }
            for (int i = 0; i < 100; i++) {
                context.waitTicks(1);
                context.runOnClient(watch::look);
            }
            Scene.screenshot(context, this, "back");
        }
        Scene.log("popin jumps " + watch.jumps + ", off the ground " + watch.aloft + ", blinks " + watch.blinks
                + ", appearances " + watch.appearances);
    }

    private static final class Watch {
        private final Map<UUID, Vec3> last = new HashMap<>();
        private final Map<UUID, String> lastBy = new HashMap<>();
        private final Map<UUID, Integer> goneAt = new HashMap<>();
        private final Map<UUID, Long> drawn = new HashMap<>();
        int tick, jumps, aloft, blinks, appearances;

        void look(Minecraft minecraft) {
            tick++;
            MobMemory memory = FarPlayerTracker.get().mobs();
            Map<UUID, Vec3> now = new HashMap<>();
            Map<UUID, String> by = new HashMap<>();
            for (MobMemory.Remembered mob : memory.shown(minecraft.level)) {
                Entity puppet = mob.puppet();
                if (puppet == null) continue;
                long at = drawnAt(mob);
                Long before = drawn.put(mob.id(), at);
                if (before == null || before == at) continue;
                now.put(mob.id(), puppet.position());
                by.put(mob.id(), memory.handingOver(mob) ? "copy-joining" : "copy");
            }
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                EntityType<?> type = entity.getType();
                if (type != EntityType.COW && type != EntityType.SHEEP && type != EntityType.PIG) continue;
                if (memory.hides(entity) || now.containsKey(entity.getUUID())) continue;
                now.put(entity.getUUID(), entity.position());
                by.put(entity.getUUID(), "real");
            }
            double px = minecraft.player.getX(), pz = minecraft.player.getZ();
            for (Map.Entry<UUID, Vec3> e : now.entrySet()) {
                UUID id = e.getKey();
                Vec3 at = e.getValue();
                String source = by.get(id);
                double far = Math.hypot(at.x - px, at.z - pz);
                Vec3 was = last.get(id);
                Integer gone = goneAt.remove(id);
                if (was == null) {
                    appearances++;
                    if (at.y > -59.5 || at.y < -60.5) {
                        aloft++;
                        Scene.log(String.format("popin tick %d: %s appears at y %.2f, %.0f blocks away", tick, source, at.y, far));
                    }
                } else {
                    if (gone != null && tick - gone < 20) {
                        blinks++;
                        Scene.log(String.format("popin tick %d: %s back after %d ticks gone, %.0f blocks away (was %s)",
                                tick, source, tick - gone, far, lastBy.get(id)));
                    }
                    double moved = at.distanceTo(was);
                    if (moved > JUMP) {
                        jumps++;
                        Scene.log(String.format("popin tick %d: %s -> %s jumped %.2f blocks (dy %.2f), %.0f blocks away",
                                tick, lastBy.get(id), source, moved, at.y - was.y, far));
                    }
                }
                last.put(id, at);
                lastBy.put(id, source);
            }
            for (UUID id : last.keySet()) {
                if (!now.containsKey(id) && !goneAt.containsKey(id)) {
                    goneAt.put(id, tick);
                }
            }
            // Gone for good from view: forget the last spot only after a second.
            last.keySet().removeIf(id -> goneAt.containsKey(id) && tick - goneAt.get(id) >= 20 && !now.containsKey(id));
            goneAt.keySet().removeIf(id -> !last.containsKey(id));
        }

        private static long drawnAt(MobMemory.Remembered mob) {
            try {
                Field field = MobMemory.Remembered.class.getDeclaredField("drawnAt");
                field.setAccessible(true);
                return field.getLong(mob);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
