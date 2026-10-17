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
 * it leaves the frame, and a rider whose mount was never seen finds it right under them.
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
    void theMountUnderARiderIsFound() throws ReflectiveOperationException {
        MobMemory.Remembered under = mob("minecraft:overworld", 100.5, 120, 40.5);
        MobMemory.Remembered beside = mob("minecraft:overworld", 103, 120, 40.5);
        MobMemory.Remembered farBelow = mob("minecraft:overworld", 100.5, 100, 40.5);
        MobMemory.Remembered nether = mob("minecraft:the_nether", 100.5, 120, 40.5);
        MobMemory memory = memory(beside, farBelow, nether, under);
        // A rider on a happy ghast sits about four blocks above it.
        assertEquals(under, memory.mountUnder(0, 100.6, 124, 40.4));
        assertEquals(nether, memory.mountUnder(1, 100.6, 124, 40.4));
        assertNull(memory.mountUnder(0, 200, 124, 40));
        assertNull(memory.mountUnder(0, 100.6, 130, 40.4), "too far above anything");
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
