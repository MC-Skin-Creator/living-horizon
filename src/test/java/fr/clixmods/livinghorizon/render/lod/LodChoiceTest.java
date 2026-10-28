package fr.clixmods.livinghorizon.render.lod;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.GameBoot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The level a mob takes for its distance and its size on screen, with the Levels of Detail settings. */
class LodChoiceTest {
    @BeforeAll
    static void boot() {
        GameBoot.start();
    }

    private static final double BIG = 1000;

    @Test
    void theDefaultsBeginAt18Then36Then72Blocks() {
        FarConfig config = new FarConfig();
        assertEquals(0, LodRenderer.levelFor(17, BIG, config));
        assertEquals(1, LodRenderer.levelFor(18, BIG, config));
        assertEquals(1, LodRenderer.levelFor(35, BIG, config));
        assertEquals(2, LodRenderer.levelFor(36, BIG, config));
        assertEquals(3, LodRenderer.levelFor(72, BIG, config));
        assertEquals(3, LodRenderer.levelFor(5000, BIG, config));
    }

    @Test
    void distanceAndSpacingMoveTheLevels() {
        FarConfig config = new FarConfig();
        config.lodDistance = 30;
        config.lodSpacing = 300;
        assertEquals(0, LodRenderer.levelFor(29, BIG, config));
        assertEquals(1, LodRenderer.levelFor(89, BIG, config));
        assertEquals(2, LodRenderer.levelFor(90, BIG, config));
        assertEquals(3, LodRenderer.levelFor(270, BIG, config));
    }

    @Test
    void neverPastTheLastSimplerModelAllowed() {
        FarConfig config = new FarConfig();
        config.lodLevels = 1;
        assertEquals(1, LodRenderer.levelFor(500, BIG, config));
    }

    @Test
    void aSmallMobOnScreenIsSimplerSooner() {
        FarConfig config = new FarConfig();
        config.lodPixels = 40;
        // Close, but small on screen: 30 pixels is under 40, 15 under 20, 8 under 10.
        assertEquals(1, LodRenderer.levelFor(5, 30, config));
        assertEquals(2, LodRenderer.levelFor(5, 15, config));
        assertEquals(3, LodRenderer.levelFor(5, 8, config));
        // Far, the distance gives the larger level.
        assertEquals(3, LodRenderer.levelFor(100, 30, config));
        config.lodPixels = 0;
        assertEquals(0, LodRenderer.levelFor(5, 1, config));
    }
}
