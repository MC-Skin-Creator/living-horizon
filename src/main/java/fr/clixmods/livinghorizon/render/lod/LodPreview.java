package fr.clixmods.livinghorizon.render.lod;

import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pictures of a model at each of its levels of detail, drawn on the CPU from its cubes, its
 * simplified triangles and its textures: what the preview screen shows, the same on every game
 * version. Each picture is the model turned to {@code yaw}, seen from a little above, lit from
 * the front, its layers drawn in the order the game draws them.
 */
public final class LodPreview {
    /** One textured quad, in the model's space (pixels, y down); a triangle repeats its last corner. */
    private record Face(double[] corners, float[] uv, LodBake.Pixels texture, boolean oneSided) {
    }

    private final List<List<Face>> levels = new ArrayList<>();
    private final int[] triangles;
    /** Half the size of the full model, round its middle: every level is framed on it. */
    private final double radius;
    private final Vector3d middle;

    /**
     * @param textures the textures the model is drawn with, in the order of its layers
     */
    public LodPreview(LodRenderer.Preview preview, List<LodBake.Pixels> textures) {
        LodSet.Plan plan = preview.plan;
        LodModel model = plan.model();
        LodLevels.Levels made = plan.levels();
        int count = made.count();
        triangles = new int[count];
        double[] low = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] high = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        // The full model: every cube with every layer.
        List<Face> full = new ArrayList<>();
        for (LodModel.Cube cube : model.cubes()) {
            Matrix4d rest = model.parts().get(cube.part()).rest();
            for (LodBake.Pixels texture : textures) {
                for (float[] quad : cube.quads()) full.add(cubeFace(rest, quad, texture));
            }
        }
        for (Face face : full) {
            for (int k = 0; k < 4; k++) {
                for (int axis = 0; axis < 3; axis++) {
                    low[axis] = Math.min(low[axis], face.corners[k * 3 + axis]);
                    high[axis] = Math.max(high[axis], face.corners[k * 3 + axis]);
                }
            }
        }
        levels.add(full);
        triangles[0] = made.triangles(0);
        // Each group's views, once per layer: solid for the first, holes kept for those over it.
        List<LodLevels.Group> groups = made.groups();
        List<List<List<LodBake.Face>>> views = new ArrayList<>();
        for (LodLevels.Group group : groups) {
            List<List<LodBake.Face>> byLayer = new ArrayList<>();
            for (int t = 0; t < textures.size(); t++) byLayer.add(LodBake.bake(model, group, textures.get(t), t > 0));
            views.add(byLayer);
        }
        for (int level = 1; level < count; level++) {
            List<Face> faces = new ArrayList<>();
            triangles[level] = made.triangles(level);
            for (int g = 0; g < groups.size(); g++) {
                Matrix4d rest = model.parts().get(groups.get(g).anchor()).rest();
                for (double[] quad : plan.quads()[level][g]) {
                    for (List<LodBake.Face> layer : views.get(g)) {
                        LodBake.Face view = view(layer, (int) quad[12]);
                        if (view != null) faces.add(simplified(rest, quad, view));
                    }
                }
            }
            levels.add(faces);
        }
        middle = new Vector3d((low[0] + high[0]) / 2, (low[1] + high[1]) / 2, (low[2] + high[2]) / 2);
        radius = Math.max(1, new Vector3d(high[0] - low[0], high[1] - low[1], high[2] - low[2]).length() / 2);
    }

    private static LodBake.Face view(List<LodBake.Face> views, int side) {
        for (LodBake.Face face : views) if (face.side() == side) return face;
        return views.isEmpty() ? null : views.get(0);
    }

    private static Face cubeFace(Matrix4d rest, float[] quad, LodBake.Pixels texture) {
        double[] corners = new double[12];
        float[] uv = new float[8];
        Vector3d point = new Vector3d();
        for (int k = 0; k < 4; k++) {
            rest.transformPosition(point.set(quad[k * 5], quad[k * 5 + 1], quad[k * 5 + 2]));
            corners[k * 3] = point.x;
            corners[k * 3 + 1] = point.y;
            corners[k * 3 + 2] = point.z;
            uv[k * 2] = quad[k * 5 + 3];
            uv[k * 2 + 1] = quad[k * 5 + 4];
        }
        // The game draws its cubes from both sides.
        return new Face(corners, uv, texture, false);
    }

    /** A simplified quad, textured from its view as the game draws it: projected onto it, from both sides. */
    private static Face simplified(Matrix4d rest, double[] quad, LodBake.Face view) {
        double[] corners = new double[12];
        float[] uv = new float[8];
        Vector3d point = new Vector3d(), local = new Vector3d();
        for (int k = 0; k < 4; k++) {
            local.set(quad[k * 3], quad[k * 3 + 1], quad[k * 3 + 2]);
            rest.transformPosition(point.set(local));
            corners[k * 3] = point.x;
            corners[k * 3 + 1] = point.y;
            corners[k * 3 + 2] = point.z;
            local.sub(view.center());
            double s = Math.min(1, Math.max(0, local.dot(view.right()) / view.width() + 0.5));
            double t = Math.min(1, Math.max(0, local.dot(view.up()) / view.height() + 0.5));
            uv[k * 2] = (float) s;
            uv[k * 2 + 1] = (float) (1 - t);
        }
        return new Face(corners, uv, view.pixels(), false);
    }

    /** How many levels there are, the full model included. */
    public int levels() {
        return levels.size();
    }

    /** The triangles of a level. */
    public int triangles(int level) {
        return triangles[level];
    }

    /**
     * The model at {@code level}, turned {@code yaw} radians round its middle and seen from
     * {@code pitch} radians above, in a picture {@code size} pixels square, ARGB, top row
     * first, transparent round it. {@code out} is filled when given, else a new one.
     */
    public int[] draw(int level, double yaw, double pitch, int size, int[] out) {
        int[] argb = out != null && out.length == size * size ? out : new int[size * size];
        Arrays.fill(argb, 0);
        double[] depth = new double[size * size];
        Arrays.fill(depth, Double.NEGATIVE_INFINITY);
        // The model's y points down: turned up as the game turns it, x and y together (a half
        // turn, which keeps which side of a face is its front), then round, then tipped towards the viewer.
        Matrix4d view = new Matrix4d().rotateX(pitch).rotateY(yaw).scale(-1, -1, 1).translate(-middle.x, -middle.y, -middle.z);
        double scale = size / (2.2 * radius);
        double[] x = new double[4], y = new double[4], z = new double[4];
        Vector3d point = new Vector3d(), normal = new Vector3d(), a = new Vector3d(), b = new Vector3d();
        for (Face face : levels.get(level)) {
            for (int k = 0; k < 4; k++) {
                view.transformPosition(point.set(face.corners[k * 3], face.corners[k * 3 + 1], face.corners[k * 3 + 2]));
                x[k] = size / 2.0 + point.x * scale;
                y[k] = size / 2.0 - point.y * scale;
                z[k] = point.z;
            }
            // Facing the viewer (+z) or away, from its corners as they now stand.
            a.set(x[1] - x[0], -(y[1] - y[0]), z[1] - z[0]);
            b.set(x[3] - x[0], -(y[3] - y[0]), z[3] - z[0]);
            a.cross(b, normal);
            double length = normal.length();
            if (length < 1e-9) continue;
            normal.div(length);
            if (face.oneSided && normal.z <= 0) continue;
            // Lit from the front and above, as a mob in daylight.
            double light = 0.55 + 0.45 * Math.abs(normal.z * 0.8 + normal.y * 0.6);
            triangle(argb, depth, size, face, x, y, z, 0, 1, 2, light);
            triangle(argb, depth, size, face, x, y, z, 0, 2, 3, light);
        }
        return argb;
    }

    private static void triangle(int[] argb, double[] depth, int size, Face face, double[] x, double[] y, double[] z,
                                 int i, int j, int k, double light) {
        double area = (x[j] - x[i]) * (y[k] - y[i]) - (x[k] - x[i]) * (y[j] - y[i]);
        if (Math.abs(area) < 1e-9) return;
        int x0 = Math.max(0, (int) Math.floor(Math.min(x[i], Math.min(x[j], x[k]))));
        int x1 = Math.min(size - 1, (int) Math.ceil(Math.max(x[i], Math.max(x[j], x[k]))));
        int y0 = Math.max(0, (int) Math.floor(Math.min(y[i], Math.min(y[j], y[k]))));
        int y1 = Math.min(size - 1, (int) Math.ceil(Math.max(y[i], Math.max(y[j], y[k]))));
        LodBake.Pixels texture = face.texture;
        for (int row = y0; row <= y1; row++) {
            double py = row + 0.5;
            for (int column = x0; column <= x1; column++) {
                double px = column + 0.5;
                double wi = ((x[j] - px) * (y[k] - py) - (x[k] - px) * (y[j] - py)) / area;
                double wj = ((x[k] - px) * (y[i] - py) - (x[i] - px) * (y[k] - py)) / area;
                double wk = 1 - wi - wj;
                if (wi < -1e-7 || wj < -1e-7 || wk < -1e-7) continue;
                double d = wi * z[i] + wj * z[j] + wk * z[k];
                int at = row * size + column;
                // A layer drawn over another at the same depth wins, as the game's do.
                if (d < depth[at] - 1e-4) continue;
                double u = wi * face.uv[i * 2] + wj * face.uv[j * 2] + wk * face.uv[k * 2];
                double v = wi * face.uv[i * 2 + 1] + wj * face.uv[j * 2 + 1] + wk * face.uv[k * 2 + 1];
                int tx = Math.min(texture.width() - 1, Math.max(0, (int) Math.floor(u * texture.width())));
                int ty = Math.min(texture.height() - 1, Math.max(0, (int) Math.floor(v * texture.height())));
                int texel = texture.argb(tx, ty);
                if ((texel >>> 24) < 128) continue;
                depth[at] = d;
                int r = (int) (((texel >> 16) & 0xFF) * light), g = (int) (((texel >> 8) & 0xFF) * light);
                int bl = (int) ((texel & 0xFF) * light);
                argb[at] = 0xFF000000 | r << 16 | g << 8 | bl;
            }
        }
    }
}
