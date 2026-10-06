package fr.clixmods.farfarplayer.ui;

import fr.clixmods.farfarplayer.track.MobKinds;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The list of mobs is not empty, which it once was. */
class MobListTest {
    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void listsMobsAndOnlyMobs() {
        List<EntityType<?>> mobs = MobList.all();
        assertTrue(mobs.size() > 60, mobs.size() + " mobs");
        assertTrue(mobs.contains(EntityType.HORSE));
        assertTrue(mobs.contains(EntityType.ZOMBIE));
        assertTrue(mobs.contains(EntityType.HAPPY_GHAST));
        assertTrue(mobs.contains(EntityType.VILLAGER));
        assertFalse(mobs.contains(EntityType.PLAYER));
        assertFalse(mobs.contains(EntityType.ARMOR_STAND));
        assertTrue(mobs.contains(EntityType.MANNEQUIN));
        assertTrue(mobs.contains(EntityType.OAK_BOAT));
        assertFalse(mobs.contains(EntityType.MINECART));
        for (String type : MobKinds.TYPES) {
            assertTrue(mobs.contains(EntityType.byString(type).orElseThrow()), type);
        }
    }
}
