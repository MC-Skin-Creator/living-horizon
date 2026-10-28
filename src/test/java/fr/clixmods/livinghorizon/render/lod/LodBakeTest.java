package fr.clixmods.livinghorizon.render.lod;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The faces baked for a merged box: framed on its sides, cut out where its cubes leave a gap. */
class LodBakeTest {
    /** One colour per side of a cube, in the order of {@link #cube}: +x, -x, +y, -y, +z, -z. */
    private static final int[] SIDE_COLOURS = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, 0xFFFF00FF, 0xFF00FFFF};
    private static final LodBake.Pixels TEXTURE = new LodBake.Pixels(6, 1, SIDE_COLOURS);

    /** The six faces of a box, each sampling its own texel of {@link #TEXTURE}. */
    private static float[][] cube(double[] b) {
        double[][][] sides = {
                {{b[3], b[1], b[2]}, {b[3], b[4], b[2]}, {b[3], b[4], b[5]}, {b[3], b[1], b[5]}},
                {{b[0], b[1], b[2]}, {b[0], b[1], b[5]}, {b[0], b[4], b[5]}, {b[0], b[4], b[2]}},
                {{b[0], b[4], b[2]}, {b[0], b[4], b[5]}, {b[3], b[4], b[5]}, {b[3], b[4], b[2]}},
                {{b[0], b[1], b[2]}, {b[3], b[1], b[2]}, {b[3], b[1], b[5]}, {b[0], b[1], b[5]}},
                {{b[0], b[1], b[5]}, {b[3], b[1], b[5]}, {b[3], b[4], b[5]}, {b[0], b[4], b[5]}},
                {{b[0], b[1], b[2]}, {b[0], b[4], b[2]}, {b[3], b[4], b[2]}, {b[3], b[1], b[2]}},
        };
        float[][] quads = new float[6][20];
        for (int side = 0; side < 6; side++) {
            for (int k = 0; k < 4; k++) {
                quads[side][k * 5] = (float) sides[side][k][0];
                quads[side][k * 5 + 1] = (float) sides[side][k][1];
                quads[side][k * 5 + 2] = (float) sides[side][k][2];
                quads[side][k * 5 + 3] = (side + 0.5f) / 6;
                quads[side][k * 5 + 4] = 0.5f;
            }
        }
        return quads;
    }

    /** Two cubes of 4 pixels on the root, a gap of 2 between them along x. */
    private static LodModel twoCubes() {
        LodModel model = new LodModel();
        int root = model.part("root", -1, 0, 0, 0, 0, 0, 0, 1, 1, 1);
        double[] a = {0, 0, 0, 4, 4, 4}, b = {6, 0, 0, 10, 4, 4};
        model.cube(root, a, cube(a));
        model.cube(root, b, cube(b));
        return model;
    }

    private static LodLevels.Group merged(LodModel model) {
        return new LodLevels.Group(0, new int[]{0, 1}, new double[]{0, 0, 0, 10, 4, 4}, 128, 6, true);
    }

    @Test
    void everySideIsFramedOnTheBoxAtThreeTexelsAPixel() {
        List<LodBake.Face> faces = LodBake.bake(twoCubes(), merged(twoCubes()), TEXTURE);
        assertEquals(6, faces.size());
        LodBake.Face east = faces.get(0);
        assertEquals(12, east.pixels().width());
        assertEquals(12, east.pixels().height());
        assertEquals(10, east.center().x, 1e-9);
        LodBake.Face top = faces.get(4);
        assertEquals(30, top.pixels().width());
        assertEquals(12, top.pixels().height());
        // right × up points out of the box.
        assertEquals(1, top.right().cross(top.up(), new org.joml.Vector3d()).y, 1e-9);
    }

    @Test
    void eachSideShowsTheCubesSideFacingIt() {
        List<LodBake.Face> faces = LodBake.bake(twoCubes(), merged(twoCubes()), TEXTURE);
        // +x, -x, +z, -z, +y, -y, in the order the sides are baked.
        int[] expected = {SIDE_COLOURS[0], SIDE_COLOURS[1], SIDE_COLOURS[4], SIDE_COLOURS[5], SIDE_COLOURS[2], SIDE_COLOURS[3]};
        for (int i = 0; i < faces.size(); i++) {
            LodBake.Pixels pixels = faces.get(i).pixels();
            assertEquals(expected[i], pixels.argb(0, 0), "side " + i);
        }
    }

    @Test
    void theGapBetweenTheCubesIsFilledSolidInTheirColour() {
        LodBake.Pixels pixels = LodBake.bake(twoCubes(), merged(twoCubes()), TEXTURE).get(4).pixels();
        for (int x = 0; x < pixels.width(); x++) {
            assertEquals(SIDE_COLOURS[2], pixels.argb(x, 5), "column " + x);
        }
    }

    @Test
    void aLayerDrawnOverAnotherKeepsTheGapClear() {
        LodBake.Face top = LodBake.bake(twoCubes(), merged(twoCubes()), TEXTURE, true).get(4);
        LodBake.Pixels pixels = top.pixels();
        int opaque = 0, clear = 0;
        for (int x = 0; x < pixels.width(); x++) {
            // Columns go along -x on the top: x = 10 - (column + 0.5) / 3; the gap is 4 to 6.
            double at = 10 - (x + 0.5) / LodBake.TEXELS;
            int texel = pixels.argb(x, 5);
            if (at > 4 && at < 6) {
                assertEquals(0, texel >>> 24, "column " + x);
                clear++;
            } else {
                assertEquals(0xFF, texel >>> 24, "column " + x);
                opaque++;
            }
            assertEquals(SIDE_COLOURS[2] & 0xFFFFFF, texel & 0xFFFFFF, "column " + x);
        }
        assertEquals(6, clear);
        assertEquals(24, opaque);
    }

    @Test
    void aFaceCornerIsOnTheBox() {
        LodBake.Face east = LodBake.bake(twoCubes(), merged(twoCubes()), TEXTURE).get(0);
        org.joml.Vector3d low = east.corner(0, 0), high = east.corner(1, 1);
        assertEquals(10, low.x, 1e-9);
        assertEquals(0, low.y, 1e-9);
        assertEquals(4, high.y, 1e-9);
        assertEquals(4, Math.abs(high.z - low.z), 1e-9);
    }

    @Test
    void emptyTexelsTakeTheirNeighboursColourAndStayOpaque() {
        int[] argb = {0xFF102030, 0, 0xFF304050};
        LodBake.fill(argb, 3, 1, false);
        assertEquals(0xFF102030, argb[0]);
        assertEquals(0xFF203040, argb[1]);
        assertEquals(0xFF304050, argb[2]);
    }

    @Test
    void anOverlaysEmptyTexelsStayClear() {
        int[] argb = {0xFF102030, 0, 0xFF304050};
        LodBake.fill(argb, 3, 1, true);
        assertEquals(0x00203040, argb[1]);
    }

    @Test
    void texelsFarFromAnyDrawnOneAreGrey() {
        int[] argb = new int[3];
        LodBake.fill(argb, 3, 1, false);
        assertEquals(0xFF808080, argb[1]);
    }

    @Test
    void uvsStayAQuarterTexelInside() {
        LodBake.Face east = LodBake.bake(twoCubes(), merged(twoCubes()), TEXTURE).get(0);
        float[] low = LodBake.pageUv(east, 0, 0, 12, 12, 0, 1);
        float[] high = LodBake.pageUv(east, 0, 0, 12, 12, 1, 0);
        assertEquals(0.25 / 12, low[0], 1e-6);
        assertEquals(0.25 / 12, low[1], 1e-6);
        assertEquals(1 - 0.25 / 12, high[0], 1e-6);
        assertEquals(1 - 0.25 / 12, high[1], 1e-6);
    }
}
