package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A far player's mount is drawn under them, never again on its own: the remembered copy of
 * it leaves the frame, and a rider whose mount was never seen finds the one the server names.
 */
class CarriedMountTest {
    private static MobMemory.Remembered mob(String dimension, double x, double y, double z) {
        MobMemory.Remembered mob = new MobMemory.Remembered();
        mob.id = UUID.randomUUID();
        mob.type = "minecraft:happy_ghast";
        mob.dimension = dimension;
        // The world a fresh memory is for: seed 0.
        mob.world = 0L;
        mob.x = x;
        mob.y = y;
        mob.z = z;
        return mob;
    }

    @SuppressWarnings("unchecked")
    private static MobMemory memory(MobMemory.Remembered... remembered) throws ReflectiveOperationException {
        MobMemory memory = new MobMemory();
        Field mobs = MobMemory.class.getDeclaredField("mobs");
        mobs.setAccessible(true);
        for (MobMemory.Remembered mob : remembered) ((Map<UUID, MobMemory.Remembered>) mobs.get(memory)).put(mob.id, mob);
        Field shown = MobMemory.class.getDeclaredField("shown");
        shown.setAccessible(true);
        shown.set(memory, List.of(remembered));
        return memory;
    }

    @Test
    void theMountTheServerNamesIsFound() throws ReflectiveOperationException {
        MobMemory.Remembered ghast = mob("minecraft:overworld", 100.5, 120, 40.5);
        MobMemory.Remembered elsewhere = mob("minecraft:overworld", 103, 120, 40.5);
        elsewhere.world = 42L;
        MobMemory memory = memory(ghast, elsewhere);
        assertEquals(ghast, memory.known(ghast.id));
        assertNull(memory.known(elsewhere.id), "a mob of another world");
        assertNull(memory.known(UUID.randomUUID()));
    }

    @Test
    void aCarriedMountLeavesTheFrame() throws ReflectiveOperationException {
        MobMemory.Remembered ridden = mob("minecraft:overworld", 0, 64, 0);
        MobMemory.Remembered other = mob("minecraft:overworld", 50, 64, 0);
        MobMemory memory = memory(ridden, other);
        memory.carried(Set.of(ridden.id));
        assertEquals(List.of(other), memory.shown());
        memory.carried(Set.of());
        assertEquals(List.of(other), memory.shown(), "back on the next choice, not at once");
    }
}
