package fr.clixmods.livinghorizon.render.impostor;

/**
 * Turns one rendered picture of a figure into the mip levels of its tile, on the CPU.
 *
 * <p>The graphics card could build mip levels for a whole page, but a figure's tile has to
 * be filtered on its own, without its neighbours bleeding in, and its see-through pixels
 * need a colour: a smaller level averages them in, and so does the texture filter at the
 * edge of a figure. Left black, every figure would get a dark outline that thickens with
 * the distance.
 *
 * <p>The pictures are drawn cut out, as models are: the game's shader keeps a pixel whose
 * alpha, once filtered, is at least 0.1 ({@link #CUT}), and draws it opaque. Drawn smoothly
 * instead, a figure a few pixels wide would show the sky through it, a ghost of itself.
 * So each level's alpha is stored as a steep ramp that crosses 0.1 where half of what a
 * pixel covers is the figure: the filter then cuts the outline where it really is, and a
 * figure is neither fatter nor thinner than its model.
 *
 * <p>Pixels are packed as the game packs them in memory: {@code 0xAABBGGRR}.
 */
public final class ImpostorPixels {
    /** The alpha below which the game's cut-out shader drops a pixel, out of 255. */
    static final int CUT = 26;
    /** How steep the stored alpha ramp is round half coverage. */
    private static final int STEEP = 4;

    /** Passes of {@link #bleed} on each level: enough for the filter's reach at the next one. */
    private static final int BLEED_PASSES = 3;

    private ImpostorPixels() {
    }

    /**
     * The mip levels of one square picture.
     *
     * @param picture the picture, {@code size * size} pixels, row by row
     * @param size    its side, a power of two times {@code tile}
     * @param tile    the side of level 0
     * @param levels  how many levels, each half the side of the one before
     */
    public static int[][] mips(int[] picture, int size, int tile, int levels) {
        int[][] out = new int[levels][];
        int[] level = picture;
        int side = size;
        while (side > tile) {
            level = halve(level, side);
            side /= 2;
        }
        out[0] = cut(level, side);
        for (int i = 1; i < levels; i++) {
            level = halve(level, side);
            side /= 2;
            out[i] = cut(level, side);
        }
        return out;
    }

    /** A level as it is stored: coloured round the figure, its alpha a ramp through the cut. */
    static int[] cut(int[] level, int side) {
        int[] out = level.clone();
        bleed(out, side);
        for (int i = 0; i < out.length; i++) {
            int alpha = (int) Math.clamp((long) ((out[i] >>> 24) - 128) * STEEP + CUT, 0, 255);
            out[i] = alpha << 24 | (out[i] & 0x00FFFFFF);
        }
        return out;
    }

    /**
     * Half the side: each pixel the average of four, the colours weighted by how opaque they
     * are, so that a see-through pixel does not darken an opaque one next to it.
     */
    static int[] halve(int[] pixels, int side) {
        int half = side / 2;
        int[] out = new int[half * half];
        for (int y = 0; y < half; y++) {
            for (int x = 0; x < half; x++) {
                int a = pixels[2 * y * side + 2 * x], b = pixels[2 * y * side + 2 * x + 1];
                int c = pixels[(2 * y + 1) * side + 2 * x], d = pixels[(2 * y + 1) * side + 2 * x + 1];
                out[y * half + x] = average(a, b, c, d);
            }
        }
        return out;
    }

    private static int average(int... pixels) {
        long alpha = 0, red = 0, green = 0, blue = 0, plainRed = 0, plainGreen = 0, plainBlue = 0;
        for (int pixel : pixels) {
            int a = pixel >>> 24;
            alpha += a;
            red += (long) (pixel & 0xFF) * a;
            green += (long) (pixel >> 8 & 0xFF) * a;
            blue += (long) (pixel >> 16 & 0xFF) * a;
            plainRed += pixel & 0xFF;
            plainGreen += pixel >> 8 & 0xFF;
            plainBlue += pixel >> 16 & 0xFF;
        }
        int n = pixels.length;
        int r, g, b;
        if (alpha > 0) {
            r = (int) ((red + alpha / 2) / alpha);
            g = (int) ((green + alpha / 2) / alpha);
            b = (int) ((blue + alpha / 2) / alpha);
        } else {
            r = (int) (plainRed / n);
            g = (int) (plainGreen / n);
            b = (int) (plainBlue / n);
        }
        int a = (int) ((alpha + n / 2) / n);
        return a << 24 | b << 16 | g << 8 | r;
    }

    /**
     * Gives the see-through pixels around a figure the colour of the figure next to them,
     * a few pixels out, leaving them see-through.
     */
    static void bleed(int[] pixels, int side) {
        boolean[] known = new boolean[pixels.length];
        for (int i = 0; i < pixels.length; i++) known[i] = pixels[i] >>> 24 != 0;
        for (int pass = 0; pass < BLEED_PASSES; pass++) {
            boolean[] next = known.clone();
            boolean changed = false;
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) {
                    int i = y * side + x;
                    if (known[i]) continue;
                    int count = 0, r = 0, g = 0, b = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= side || ny >= side || !known[ny * side + nx]) continue;
                            int pixel = pixels[ny * side + nx];
                            r += pixel & 0xFF;
                            g += pixel >> 8 & 0xFF;
                            b += pixel >> 16 & 0xFF;
                            count++;
                        }
                    }
                    if (count == 0) continue;
                    pixels[i] = (pixels[i] & 0xFF000000) | (b / count) << 16 | (g / count) << 8 | r / count;
                    next[i] = true;
                    changed = true;
                }
            }
            known = next;
            if (!changed) return;
        }
    }
}
