package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.GameBoot;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a client and the server's thread take from {@link SeedMobs} without the worker. */
class SeedMobsTest {
    @BeforeAll
    static void boot() {
        GameBoot.start();
    }

    @Test
    void tellsAForeseenMobByItsIdAlone() {
        // The game gives its mobs random ids, version 4; those foreseen are name-based, version 3.
        for (int i = 0; i < 100; i++) assertFalse(SeedMobs.foreseen(UUID.randomUUID()));
        assertTrue(SeedMobs.foreseen(UUID.nameUUIDFromBytes("a cow".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void packsAChunkAsTheGameDoes() {
        int[][] chunks = {{0, 0}, {1, -1}, {-30000, 29999}, {123456, -654321}};
        for (int[] chunk : chunks) {
            long packed = SeedMobs.chunk(chunk[0], chunk[1]);
            //? if >=26.1 {
            /*assertEquals(new ChunkPos(chunk[0], chunk[1]).pack(), packed);
            *///?} else
            assertEquals(ChunkPos.asLong(chunk[0], chunk[1]), packed);
            assertEquals(chunk[0], SeedMobs.chunkX(packed));
            assertEquals(chunk[1], SeedMobs.chunkZ(packed));
        }
    }
}
