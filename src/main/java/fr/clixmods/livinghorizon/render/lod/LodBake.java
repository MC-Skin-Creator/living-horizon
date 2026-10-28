package fr.clixmods.livinghorizon.render.lod;

import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The faces of a merged box ({@link LodLevels.Group#merged()}), baked from its cubes: for each
 * of the box's six sides, the cubes seen from outside, straight on, framed exactly on that
 * side, at {@link #TEXELS} texels per pixel of the model. Drawn on the CPU from the model's
 * texture, so that it can be done away from the render thread.
 *
 * <p>A box is solid and closed: its empty texels (where it goes past its cubes, between two legs
 * welded together) take their neighbours' colour and stay opaque. The shape swells, but its
 * faces meet and nothing is hollow; a box cut out like its cubes was tried and refused, its
 * faces no longer touching, the head and body hollow. Only the layers drawn over a model's first
 * (a villager's clothes) keep their texture's holes, or they would hide the layer under them.
 * The faces are drawn from outside only.
 */
public final class LodBake {
    /** Texels per pixel of the model. */
    public static final double TEXELS = 3;
    /** The most texels along a side of one face. */
    public static final int MAX_SIDE = 256;
    /** How many rounds of spreading colours into empty texels. */
    private static final int FILL_ROUNDS = 64;

    private LodBake() {
    }

    /** The texture a model is drawn with, ARGB. */
    public interface Texture {
        int width();

        int height();

        int argb(int x, int y);
    }

    /** A texture held in an array, row by row, ARGB. */
    public record Pixels(int width, int height, int[] argb) implements Texture {
        @Override
        public int argb(int x, int y) {
            return argb[y * width + x];
        }
    }

    /**
     * One baked side of a box, in the space of the part the box is anchored to.
     *
     * @param center the middle of the side
     * @param right  the direction its texels go right, a unit vector
     * @param up     the direction its rows go up, a unit vector; right × up faces out
     * @param width  pixels of the model along {@code right}
     * @param height pixels of the model along {@code up}
     * @param pixels its texels, top row first
     */
    public record Face(Vector3d center, Vector3d right, Vector3d up, double width, double height, Pixels pixels) {
        /** A point of the side, {@code s} from left (0) to right (1), {@code t} from bottom (0) to top (1). */
        public Vector3d corner(double s, double t) {
            return new Vector3d(center).fma((s - 0.5) * width, right).fma((t - 0.5) * height, up);
        }
    }

    /** The six sides a box is seen from: out of it, and which way is up on each. */
    private static final double[][] SIDES = {
            {1, 0, 0, 0, 1, 0}, {-1, 0, 0, 0, 1, 0},
            {0, 0, 1, 0, 1, 0}, {0, 0, -1, 0, 1, 0},
            {0, 1, 0, 0, 0, 1}, {0, -1, 0, 0, 0, 1},
    };

    /** The faces of a merged group, solid, baked from its cubes with the model's texture. Flat sides are left out. */
    public static List<Face> bake(LodModel model, LodLevels.Group group, Texture texture) {
        return bake(model, group, texture, false);
    }

    /**
     * @param overlay a layer drawn over the model's first: the texels its cubes leave empty stay
     *                transparent, so that the layer under it shows there
     */
    public static List<Face> bake(LodModel model, LodLevels.Group group, Texture texture, boolean overlay) {
        List<float[]> triangles = triangles(model, group);
        double[] box = group.box();
        Vector3d middle = new Vector3d((box[0] + box[3]) / 2, (box[1] + box[4]) / 2, (box[2] + box[5]) / 2);
        Vector3d extent = new Vector3d(box[3] - box[0], box[4] - box[1], box[5] - box[2]);
        List<Face> faces = new ArrayList<>();
        for (double[] side : SIDES) {
            Vector3d out = new Vector3d(side[0], side[1], side[2]);
            Vector3d up = new Vector3d(side[3], side[4], side[5]);
            Vector3d right = new Vector3d(up).cross(out);
            double width = Math.abs(right.dot(extent)), height = Math.abs(up.dot(extent)), depth = Math.abs(out.dot(extent));
            if (width * height < 1e-6) continue;
            int pw = Math.min(MAX_SIDE, Math.max(1, (int) Math.ceil(width * TEXELS - 1e-6)));
            int ph = Math.min(MAX_SIDE, Math.max(1, (int) Math.ceil(height * TEXELS - 1e-6)));
            Vector3d center = new Vector3d(middle).fma(depth / 2, out);
            int[] argb = draw(triangles, texture, center, right, up, out, width, height, depth, pw, ph);
            fill(argb, pw, ph, overlay);
            faces.add(new Face(center, right, up, width, height, new Pixels(pw, ph, argb)));
        }
        return faces;
    }

    /**
     * The group's cubes as triangles in the anchor's space, 15 floats each: three corners of
     * x, y, z, u, v.
     */
    static List<float[]> triangles(LodModel model, LodLevels.Group group) {
        List<float[]> out = new ArrayList<>();
        Vector3d point = new Vector3d();
        for (int source : group.sources()) {
            LodModel.Cube cube = model.cubes().get(source);
            Matrix4d to = cube.part() == group.anchor() ? null : LodLevels.between(model, cube.part(), group.anchor());
            for (float[] quad : cube.quads()) {
                float[] corners = new float[20];
                for (int k = 0; k < 4; k++) {
                    point.set(quad[k * 5], quad[k * 5 + 1], quad[k * 5 + 2]);
                    if (to != null) to.transformPosition(point);
                    corners[k * 5] = (float) point.x;
                    corners[k * 5 + 1] = (float) point.y;
                    corners[k * 5 + 2] = (float) point.z;
                    corners[k * 5 + 3] = quad[k * 5 + 3];
                    corners[k * 5 + 4] = quad[k * 5 + 4];
                }
                out.add(corners(corners, 0, 1, 2));
                out.add(corners(corners, 0, 2, 3));
            }
        }
        return out;
    }

    private static float[] corners(float[] quad, int a, int b, int c) {
        float[] out = new float[15];
        System.arraycopy(quad, a * 5, out, 0, 5);
        System.arraycopy(quad, b * 5, out, 5, 5);
        System.arraycopy(quad, c * 5, out, 10, 5);
        return out;
    }

    /**
     * The triangles seen from outside the side at {@code center}, looking in along {@code -out},
     * straight on: the nearest texel of each pixel wins, the texels the texture leaves
     * transparent are not drawn. Seen from 1 pixel out to 1 pixel past the far side.
     */
    private static int[] draw(List<float[]> triangles, Texture texture, Vector3d center, Vector3d right, Vector3d up,
                              Vector3d out, double width, double height, double depth, int pw, int ph) {
        int[] argb = new int[pw * ph];
        double[] nearest = new double[pw * ph];
        Arrays.fill(nearest, Double.NEGATIVE_INFINITY);
        double[] x = new double[3], y = new double[3], z = new double[3];
        Vector3d offset = new Vector3d();
        for (float[] triangle : triangles) {
            for (int k = 0; k < 3; k++) {
                offset.set(triangle[k * 5], triangle[k * 5 + 1], triangle[k * 5 + 2]).sub(center);
                // Texel columns from the left, rows from the top, and how far out of the side.
                x[k] = (offset.dot(right) / width + 0.5) * pw;
                y[k] = (0.5 - offset.dot(up) / height) * ph;
                z[k] = offset.dot(out);
            }
            double area = (x[1] - x[0]) * (y[2] - y[0]) - (x[2] - x[0]) * (y[1] - y[0]);
            if (Math.abs(area) < 1e-9) continue;
            int x0 = Math.max(0, (int) Math.floor(Math.min(x[0], Math.min(x[1], x[2])) - 0.5));
            int x1 = Math.min(pw - 1, (int) Math.ceil(Math.max(x[0], Math.max(x[1], x[2])) - 0.5));
            int y0 = Math.max(0, (int) Math.floor(Math.min(y[0], Math.min(y[1], y[2])) - 0.5));
            int y1 = Math.min(ph - 1, (int) Math.ceil(Math.max(y[0], Math.max(y[1], y[2])) - 0.5));
            for (int row = y0; row <= y1; row++) {
                double py = row + 0.5;
                for (int column = x0; column <= x1; column++) {
                    double px = column + 0.5;
                    double w0 = ((x[1] - px) * (y[2] - py) - (x[2] - px) * (y[1] - py)) / area;
                    double w1 = ((x[2] - px) * (y[0] - py) - (x[0] - px) * (y[2] - py)) / area;
                    double w2 = 1 - w0 - w1;
                    if (w0 < -1e-7 || w1 < -1e-7 || w2 < -1e-7) continue;
                    double distance = w0 * z[0] + w1 * z[1] + w2 * z[2];
                    if (distance > 0.99 || distance < -depth - 1) continue;
                    int at = row * pw + column;
                    if (distance <= nearest[at]) continue;
                    double u = w0 * triangle[3] + w1 * triangle[8] + w2 * triangle[13];
                    double v = w0 * triangle[4] + w1 * triangle[9] + w2 * triangle[14];
                    int texel = sample(texture, u, v);
                    if ((texel >>> 24) < 128) continue;
                    nearest[at] = distance;
                    argb[at] = texel;
                }
            }
        }
        return argb;
    }

    private static int sample(Texture texture, double u, double v) {
        int tx = Math.min(texture.width() - 1, Math.max(0, (int) Math.floor(u * texture.width())));
        int ty = Math.min(texture.height() - 1, Math.max(0, (int) Math.floor(v * texture.height())));
        return texture.argb(tx, ty);
    }

    /**
     * Spreads the colours of the drawn texels into the empty ones, a round at a time, each
     * taking the average of its drawn neighbours; texels still empty after that are grey.
     * Every texel is opaque, but for the empty ones of an {@code overlay}, which stay transparent.
     */
    static void fill(int[] argb, int width, int height, boolean overlay) {
        int count = width * height;
        boolean[] solid = new boolean[count], drawn = new boolean[count];
        for (int i = 0; i < count; i++) solid[i] = drawn[i] = (argb[i] >>> 24) >= 128;
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        for (int round = 0; round < FILL_ROUNDS; round++) {
            boolean[] next = solid.clone();
            boolean changed = false;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = y * width + x;
                    if (solid[i]) continue;
                    int r = 0, g = 0, b = 0, n = 0;
                    for (int k = 0; k < 4; k++) {
                        int nx = x + dx[k], ny = y + dy[k];
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                        int j = ny * width + nx;
                        if (!solid[j]) continue;
                        r += (argb[j] >> 16) & 0xFF;
                        g += (argb[j] >> 8) & 0xFF;
                        b += argb[j] & 0xFF;
                        n++;
                    }
                    if (n == 0) continue;
                    argb[i] = (r / n) << 16 | (g / n) << 8 | (b / n);
                    next[i] = true;
                    changed = true;
                }
            }
            solid = next;
            if (!changed) break;
        }
        for (int i = 0; i < count; i++) {
            int rgb = solid[i] ? argb[i] & 0xFFFFFF : 0x808080;
            argb[i] = (drawn[i] || !overlay ? 0xFF000000 : 0) | rgb;
        }
    }

    /**
     * Texture coordinates of a point of a face placed at ({@code x}, {@code y}) in a page of
     * {@code pageWidth} by {@code pageHeight}: a quarter texel in from each edge, so that
     * nothing of the faces next to it on the page bleeds in.
     */
    public static float[] pageUv(Face face, int x, int y, int pageWidth, int pageHeight, double s, double t) {
        int pw = face.pixels().width(), ph = face.pixels().height();
        double iu = 0.25 / pw, iv = 0.25 / ph;
        return new float[]{(float) ((x + (iu + s * (1 - 2 * iu)) * pw) / pageWidth),
                (float) ((y + (iv + (1 - t) * (1 - 2 * iv)) * ph) / pageHeight)};
    }
}
