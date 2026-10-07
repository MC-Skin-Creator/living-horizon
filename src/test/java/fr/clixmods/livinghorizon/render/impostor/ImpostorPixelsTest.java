package fr.clixmods.livinghorizon.render.impostor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpostorPixelsTest {
    private static final int RED = 0xFF0000FF, CLEAR = 0;

    private static int alpha(int pixel) {
        return pixel >>> 24;
    }

    private static int red(int pixel) {
        return pixel & 0xFF;
    }

    @Test
    void halvingWeighsColoursByHowOpaqueTheyAre() {
        // One red pixel and three see-through black ones: a quarter opaque, and red.
        int[] half = ImpostorPixels.halve(new int[]{RED, CLEAR, CLEAR, CLEAR}, 2);
        assertEquals(1, half.length);
        assertEquals(255, red(half[0]));
        assertEquals(64, alpha(half[0]));
    }

    @Test
    void seeThroughPixelsTakeTheColourOfTheFigureNextToThem() {
        int[] pixels = {RED, CLEAR, CLEAR, CLEAR};
        ImpostorPixels.bleed(pixels, 2);
        for (int pixel : pixels) assertEquals(255, red(pixel));
        assertEquals(0, alpha(pixels[1]));
    }

    @Test
    void everyLevelIsHalfTheOneBefore() {
        int[] picture = new int[128 * 128];
        int[][] levels = ImpostorPixels.mips(picture, 128, 64, 4);
        assertEquals(4, levels.length);
        for (int level = 0; level < 4; level++) assertEquals((64 >> level) * (64 >> level), levels[level].length);
    }

    @Test
    void theCutFallsAtHalfCoverage() {
        // The shader keeps what is at least CUT: exactly half covered is kept, a little less is not.
        assertEquals(ImpostorPixels.CUT, alpha(ImpostorPixels.cut(new int[]{128 << 24}, 1)[0]));
        assertTrue(alpha(ImpostorPixels.cut(new int[]{120 << 24}, 1)[0]) < ImpostorPixels.CUT);
        assertEquals(255, alpha(ImpostorPixels.cut(new int[]{0xFF000000}, 1)[0]));
        assertEquals(0, alpha(ImpostorPixels.cut(new int[]{0}, 1)[0]));
    }

    @Test
    void aSolidSquareStaysSolidAllTheWayDown() {
        int[] picture = new int[128 * 128];
        // A 64-pixel opaque square in the middle.
        for (int y = 32; y < 96; y++) for (int x = 32; x < 96; x++) picture[y * 128 + x] = RED;
        int[][] levels = ImpostorPixels.mips(picture, 128, 64, 4);
        for (int[] level : levels) {
            int side = (int) Math.sqrt(level.length);
            assertEquals(255, alpha(level[side / 2 * side + side / 2]));
            assertEquals(0, alpha(level[0]));
        }
    }
}
