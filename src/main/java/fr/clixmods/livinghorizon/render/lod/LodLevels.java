package fr.clixmods.livinghorizon.render.lod;

import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The simpler models of a model, its levels of detail, generated from its cubes once per model:
 * the Mob LOD Lab's "voxel remesh and decimation".
 *
 * <ol>
 *   <li><b>Rigid groups.</b> One group per animated part (a leg, an arm, the head, told by the
 *       part's name), with the cubes of the parts under it that do not move on their own (ears,
 *       horns, nose); the rest of the model is the root's group. A group is made in its part's
 *       space at rest and drawn with that part's pose, so it follows its animation.</li>
 *   <li><b>Voxels.</b> Each group fills a grid aligned on its part, centred on its cubes' box, one
 *       model pixel a cell or more so that the longest side has at most {@link #CELLS} cells. A
 *       cell is solid when at least 3 of its 8 samples fall in a cube; a flat cube is one pixel thick.</li>
 *   <li><b>Closed surface.</b> Every side of a solid cell against an empty one gives two triangles,
 *       facing out, their corners shared: one welded surface per group, nothing hollow or loose.</li>
 *   <li><b>Decimation</b> (Garland-Heckbert quadrics): edges are collapsed cheapest first, the new
 *       vertex at the quadric's minimum when it stays near the edge, else the best of its two ends
 *       and its middle; a collapse that would turn a triangle over waits with a penalty. Level
 *       {@code i} keeps {@code max(4, round(T0 × ratio^i))} triangles, T0 those of the full model,
 *       each level going on from the one before. Groups are never merged into one another.</li>
 * </ol>
 * The triangles of a level are then paired into quads across shared edges ({@link #pair}): the
 * game draws entities in quads, and two triangles that share an edge are exactly one quad.
 */
public final class LodLevels {
    /** Levels made of models: the full one, then three simpler ones. The impostor follows them. */
    public static final int MODEL_LEVELS = 4;
    /** The share of the full model's triangles each level keeps more than the next. */
    public static final double RATIO = 0.5;
    /** Most cells along a group's longest side. */
    static final int CELLS = 32;
    /** A collapse that turns a triangle over waits behind every other, with this much more cost. */
    private static final double FLIP_PENALTY = 1e4;
    /** Below this cosine between a triangle's normal before and after a collapse, it is turned over. */
    private static final double FLIP_COSINE = 0.2;

    private LodLevels() {
    }

    /**
     * A rigid group of the model's cubes.
     *
     * @param anchor  the part it is made in and drawn with
     * @param sources the model's cubes in it
     * @param box     their box in the anchor's space: min x, y, z, then max
     */
    public record Group(int anchor, int[] sources, double[] box) {
    }

    /** One triangle of a level, in its group's anchor space, with its outward normal. */
    public record Triangle(int group, double[] corners) {
        public double[] normal() {
            double ux = corners[3] - corners[0], uy = corners[4] - corners[1], uz = corners[5] - corners[2];
            double vx = corners[6] - corners[0], vy = corners[7] - corners[1], vz = corners[8] - corners[2];
            return new double[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
        }

        /** Which of a group's six views its normal faces most: +x, -x, +z, -z, +y, -y, as {@link LodBake} bakes them. */
        public int side() {
            double[] n = normal();
            double ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
            if (ax >= ay && ax >= az) return n[0] >= 0 ? 0 : 1;
            if (ay >= az) return n[1] >= 0 ? 4 : 5;
            return n[2] >= 0 ? 2 : 3;
        }
    }

    /** A model's levels: its groups, and for each simpler level its triangles. Level 0 is the model itself. */
    public record Levels(List<Group> groups, int fullTriangles, List<List<Triangle>> simpler) {
        /** Triangles drawn at a level, 0 being the full model's. */
        public int triangles(int level) {
            return level == 0 ? fullTriangles : simpler.get(level - 1).size();
        }

        public int count() {
            return simpler.size() + 1;
        }

        public List<Triangle> level(int level) {
            return simpler.get(level - 1);
        }
    }

    /** The {@link #MODEL_LEVELS} levels of a model, with the defaults. */
    public static Levels generate(LodModel model) {
        return generate(model, RATIO);
    }

    public static Levels generate(LodModel model, double ratio) {
        List<Group> groups = groups(model);
        int full = model.quads() * 2;
        int[] budgets = new int[MODEL_LEVELS - 1];
        for (int i = 1; i < MODEL_LEVELS; i++) budgets[i - 1] = Math.max(4, (int) Math.round(full * Math.pow(ratio, i)));
        Mesh mesh = surface(model, groups);
        return new Levels(groups, full, decimate(mesh, budgets));
    }

    // ---------------------------------------------------------------- groups

    /** One group per animated part, the root's for the rest. */
    static List<Group> groups(LodModel model) {
        Map<Integer, List<Integer>> byAnchor = new LinkedHashMap<>();
        for (int i = 0; i < model.cubes().size(); i++) {
            int anchor = anchor(model.parts(), model.cubes().get(i).part());
            byAnchor.computeIfAbsent(anchor, k -> new ArrayList<>()).add(i);
        }
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : byAnchor.entrySet()) {
            int[] sources = entry.getValue().stream().mapToInt(Integer::intValue).toArray();
            groups.add(new Group(entry.getKey(), sources, boxOf(model, sources, entry.getKey())));
        }
        return groups;
    }

    /** The nearest animated part a part is, or is under; the root (0) for none. */
    private static int anchor(List<LodModel.Part> parts, int part) {
        for (int p = part; p > 0; p = parts.get(p).parent()) {
            if (parts.get(p).animated()) return p;
        }
        return 0;
    }

    /** From the space of part {@code from} to that of part {@code to}, at rest. */
    static Matrix4d between(LodModel model, int from, int to) {
        return new Matrix4d(model.parts().get(to).restInverse()).mul(model.parts().get(from).rest());
    }

    /** The box of one cube, seen in the space of another part. */
    static double[] cubeIn(LodModel model, int index, int anchor) {
        LodModel.Cube cube = model.cubes().get(index);
        if (cube.part() == anchor) return cube.box().clone();
        Matrix4d to = between(model, cube.part(), anchor);
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
                -Double.MAX_VALUE};
        double[] b = cube.box();
        Vector3d corner = new Vector3d();
        for (int i = 0; i < 8; i++) {
            to.transformPosition(corner.set((i & 1) != 0 ? b[3] : b[0], (i & 2) != 0 ? b[4] : b[1], (i & 4) != 0 ? b[5] : b[2]));
            box[0] = Math.min(box[0], corner.x);
            box[1] = Math.min(box[1], corner.y);
            box[2] = Math.min(box[2], corner.z);
            box[3] = Math.max(box[3], corner.x);
            box[4] = Math.max(box[4], corner.y);
            box[5] = Math.max(box[5], corner.z);
        }
        return box;
    }

    static double[] boxOf(LodModel model, int[] sources, int anchor) {
        double[] box = cubeIn(model, sources[0], anchor);
        for (int i = 1; i < sources.length; i++) {
            double[] b = cubeIn(model, sources[i], anchor);
            for (int k = 0; k < 3; k++) {
                box[k] = Math.min(box[k], b[k]);
                box[k + 3] = Math.max(box[k + 3], b[k + 3]);
            }
        }
        return box;
    }

    // ---------------------------------------------------------------- voxels and surface

    /** Welded triangles: three coordinates a vertex, the group of each vertex, three vertices a triangle. */
    static final class Mesh {
        final List<double[]> positions = new ArrayList<>();
        final List<Integer> groups = new ArrayList<>();
        final List<int[]> triangles = new ArrayList<>();
        private final Map<Long, Integer> keys = new HashMap<>();

        /** The vertex at grid corner (x, y, z) of a group, made once. */
        int corner(int group, int x, int y, int z, double ox, double oy, double oz, double step) {
            long key = (long) group << 48 | (long) (x & 0xFFFF) << 32 | (long) (y & 0xFFFF) << 16 | (z & 0xFFFF);
            Integer at = keys.get(key);
            if (at != null) return at;
            positions.add(new double[]{ox + x * step, oy + y * step, oz + z * step});
            groups.add(group);
            keys.put(key, positions.size() - 1);
            return positions.size() - 1;
        }
    }

    /** One closed surface per group, from its solid cells. */
    static Mesh surface(LodModel model, List<Group> groups) {
        Mesh mesh = new Mesh();
        for (int g = 0; g < groups.size(); g++) {
            Group group = groups.get(g);
            double[] b = group.box();
            double extent = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
            double step = Math.max(1, extent / CELLS);
            int[] n = new int[3];
            double[] origin = new double[3];
            for (int k = 0; k < 3; k++) {
                n[k] = Math.max(1, (int) Math.ceil((b[k + 3] - b[k]) / step - 1e-6));
                origin[k] = (b[k] + b[k + 3]) / 2 - n[k] * step / 2;
            }
            boolean[] solid = voxelize(model, group, n, origin, step);
            for (int z = 0; z < n[2]; z++) {
                for (int y = 0; y < n[1]; y++) {
                    for (int x = 0; x < n[0]; x++) {
                        if (!solid[(z * n[1] + y) * n[0] + x]) continue;
                        int[] cell = {x, y, z};
                        for (int a = 0; a < 3; a++) {
                            int u = (a + 1) % 3, w = (a + 2) % 3;
                            for (int sign = 1; sign >= -1; sign -= 2) {
                                int[] next = cell.clone();
                                next[a] += sign;
                                if (inside(solid, n, next)) continue;
                                int[] ring = new int[4];
                                int[][] steps = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
                                for (int k = 0; k < 4; k++) {
                                    int[] p = cell.clone();
                                    p[a] += sign > 0 ? 1 : 0;
                                    p[u] += steps[k][0];
                                    p[w] += steps[k][1];
                                    ring[k] = mesh.corner(g, p[0], p[1], p[2], origin[0], origin[1], origin[2], step);
                                }
                                // Turned round to face out, as the lab does it: the diagonal changes too.
                                if (sign < 0) ring = new int[]{ring[3], ring[2], ring[1], ring[0]};
                                mesh.triangles.add(new int[]{ring[0], ring[1], ring[2]});
                                mesh.triangles.add(new int[]{ring[0], ring[2], ring[3]});
                            }
                        }
                    }
                }
            }
        }
        return mesh;
    }

    private static boolean inside(boolean[] solid, int[] n, int[] cell) {
        return cell[0] >= 0 && cell[1] >= 0 && cell[2] >= 0 && cell[0] < n[0] && cell[1] < n[1] && cell[2] < n[2]
                && solid[(cell[2] * n[1] + cell[1]) * n[0] + cell[0]];
    }

    /** Which cells of a group's grid are solid: 3 of their 8 samples in one of its cubes. */
    static boolean[] voxelize(LodModel model, Group group, int[] n, double[] origin, double step) {
        int count = group.sources().length;
        Matrix4d[] into = new Matrix4d[count];
        double[][] boxes = new double[count][], near = new double[count][];
        for (int i = 0; i < count; i++) {
            int source = group.sources()[i];
            LodModel.Cube cube = model.cubes().get(source);
            into[i] = between(model, group.anchor(), cube.part());
            double[] box = cube.box().clone();
            for (int k = 0; k < 3; k++) {
                if (box[k + 3] - box[k] < 1) {
                    double middle = (box[k] + box[k + 3]) / 2;
                    box[k] = middle - 0.5;
                    box[k + 3] = middle + 0.5;
                }
            }
            boxes[i] = box;
            double[] around = cubeIn(model, source, group.anchor());
            for (int k = 0; k < 3; k++) {
                around[k] -= 0.5;
                around[k + 3] += 0.5;
            }
            near[i] = around;
        }
        boolean[] solid = new boolean[n[0] * n[1] * n[2]];
        double[] samples = {0.25, 0.75};
        Vector3d point = new Vector3d();
        int[] close = new int[count];
        for (int z = 0; z < n[2]; z++) {
            for (int y = 0; y < n[1]; y++) {
                for (int x = 0; x < n[0]; x++) {
                    double x0 = origin[0] + x * step, y0 = origin[1] + y * step, z0 = origin[2] + z * step;
                    int found = 0;
                    for (int i = 0; i < count; i++) {
                        double[] b = near[i];
                        if (b[0] < x0 + step && b[3] > x0 && b[1] < y0 + step && b[4] > y0 && b[2] < z0 + step && b[5] > z0) {
                            close[found++] = i;
                        }
                    }
                    if (found == 0) continue;
                    int hits = 0;
                    for (double sx : samples) {
                        for (double sy : samples) {
                            for (double sz : samples) {
                                for (int c = 0; c < found; c++) {
                                    int i = close[c];
                                    into[i].transformPosition(point.set(x0 + sx * step, y0 + sy * step, z0 + sz * step));
                                    double[] b = boxes[i];
                                    if (point.x >= b[0] && point.x <= b[3] && point.y >= b[1] && point.y <= b[4]
                                            && point.z >= b[2] && point.z <= b[5]) {
                                        hits++;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (hits >= 3) solid[(z * n[1] + y) * n[0] + x] = true;
                }
            }
        }
        return solid;
    }

    // ---------------------------------------------------------------- decimation

    /** One edge to collapse, as it stood when it was costed. */
    private record Collapse(double cost, int a, int b, int versionA, int versionB, double[] at, boolean forced) {
    }

    /** Collapses edges down to each budget in turn; the triangles left at each. */
    static List<List<Triangle>> decimate(Mesh mesh, int[] budgets) {
        int vertices = mesh.positions.size();
        double[][] positions = new double[vertices][];
        for (int v = 0; v < vertices; v++) positions[v] = mesh.positions.get(v).clone();
        int[][] triangles = new int[mesh.triangles.size()][];
        for (int t = 0; t < triangles.length; t++) triangles[t] = mesh.triangles.get(t).clone();
        boolean[] alive = new boolean[triangles.length];
        Arrays.fill(alive, true);
        int live = triangles.length;
        List<Set<Integer>> around = new ArrayList<>(vertices);
        for (int v = 0; v < vertices; v++) around.add(new HashSet<>());
        double[][] quadrics = new double[vertices][10];
        for (int t = 0; t < triangles.length; t++) {
            int[] tri = triangles[t];
            double[] q = new double[10];
            planeQuadric(positions, tri[0], tri[1], tri[2], q);
            for (int v : tri) {
                around.get(v).add(t);
                for (int k = 0; k < 10; k++) quadrics[v][k] += q[k];
            }
        }
        boolean[] vertexAlive = new boolean[vertices];
        Arrays.fill(vertexAlive, true);
        int[] version = new int[vertices];
        PriorityQueue<Collapse> heap = new PriorityQueue<>((x, y) -> Double.compare(x.cost, y.cost));
        for (int v = 0; v < vertices; v++) {
            for (int w : partners(v, around, triangles, vertexAlive)) {
                if (w > v) push(heap, quadrics, positions, version, vertexAlive, v, w);
            }
        }
        int[] group = new int[vertices];
        for (int v = 0; v < vertices; v++) group[v] = mesh.groups.get(v);

        List<List<Triangle>> out = new ArrayList<>();
        for (int budget : budgets) {
            while (live > budget && !heap.isEmpty()) {
                Collapse e = heap.poll();
                int a = e.a, b = e.b;
                if (!vertexAlive[a] || !vertexAlive[b] || version[a] != e.versionA || version[b] != e.versionB) continue;
                if (!e.forced && flips(a, b, e.at, around, triangles, positions)) {
                    heap.add(new Collapse(e.cost + FLIP_PENALTY, a, b, e.versionA, e.versionB, e.at, true));
                    continue;
                }
                // b goes into a.
                positions[a] = e.at.clone();
                for (int k = 0; k < 10; k++) quadrics[a][k] += quadrics[b][k];
                vertexAlive[b] = false;
                for (int t : around.get(b)) {
                    if (!alive[t]) continue;
                    int[] tri = triangles[t];
                    for (int k = 0; k < 3; k++) if (tri[k] == b) tri[k] = a;
                    if (tri[0] == tri[1] || tri[1] == tri[2] || tri[0] == tri[2]) {
                        alive[t] = false;
                        live--;
                        for (int w : tri) if (w != b) around.get(w).remove(t);
                    } else {
                        around.get(a).add(t);
                    }
                }
                around.get(b).clear();
                version[a]++;
                for (int w : partners(a, around, triangles, vertexAlive)) push(heap, quadrics, positions, version, vertexAlive, a, w);
            }
            List<Triangle> level = new ArrayList<>();
            for (int t = 0; t < triangles.length; t++) {
                if (!alive[t]) continue;
                int[] tri = triangles[t];
                double[] corners = new double[9];
                for (int k = 0; k < 3; k++) System.arraycopy(positions[tri[k]], 0, corners, k * 3, 3);
                level.add(new Triangle(group[tri[0]], corners));
            }
            out.add(level);
        }
        return out;
    }

    /** The live vertices sharing a triangle with {@code v}. */
    private static Set<Integer> partners(int v, List<Set<Integer>> around, int[][] triangles, boolean[] vertexAlive) {
        Set<Integer> out = new HashSet<>();
        for (int t : around.get(v)) {
            for (int w : triangles[t]) if (w != v && vertexAlive[w]) out.add(w);
        }
        return out;
    }

    private static void push(PriorityQueue<Collapse> heap, double[][] quadrics, double[][] positions, int[] version,
                             boolean[] vertexAlive, int a, int b) {
        if (a == b || !vertexAlive[a] || !vertexAlive[b]) return;
        double[] q = new double[10];
        for (int k = 0; k < 10; k++) q[k] = quadrics[a][k] + quadrics[b][k];
        double[] best = null;
        double error = Double.MAX_VALUE;
        for (double[] candidate : candidates(q, positions[a], positions[b])) {
            double e = error(q, candidate[0], candidate[1], candidate[2]);
            if (e < error - 1e-12) {
                error = e;
                best = candidate;
            }
        }
        heap.add(new Collapse(Math.max(0, error), a, b, version[a], version[b], best, false));
    }

    /** Where a merged vertex may go: the quadric's minimum when it stays near the edge, the two ends, the middle. */
    private static List<double[]> candidates(double[] q, double[] pa, double[] pb) {
        List<double[]> out = new ArrayList<>(4);
        double A = q[0], B = q[1], C = q[2], D = q[4], E = q[5], F = q[7];
        double det = A * (D * F - E * E) - B * (B * F - C * E) + C * (B * E - C * D);
        double[] middle = {(pa[0] + pb[0]) / 2, (pa[1] + pb[1]) / 2, (pa[2] + pb[2]) / 2};
        if (Math.abs(det) > 1e-9) {
            double r0 = -q[3], r1 = -q[6], r2 = -q[8];
            double x = (r0 * (D * F - E * E) - B * (r1 * F - E * r2) + C * (r1 * E - D * r2)) / det;
            double y = (A * (r1 * F - E * r2) - r0 * (B * F - C * E) + C * (B * r2 - r1 * C)) / det;
            double z = (A * (D * r2 - r1 * E) - B * (B * r2 - r1 * C) + r0 * (B * E - C * D)) / det;
            // Only trusted near the edge: on flat ground the minimum is anywhere.
            double reach = Math.sqrt(sq(pa[0] - pb[0]) + sq(pa[1] - pb[1]) + sq(pa[2] - pb[2])) * 2 + 0.5;
            if (Math.sqrt(sq(x - middle[0]) + sq(y - middle[1]) + sq(z - middle[2])) < reach) out.add(new double[]{x, y, z});
        }
        out.add(pa.clone());
        out.add(pb.clone());
        out.add(middle);
        return out;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Whether collapsing a and b to {@code at} would turn over, or flatten, a triangle round them. */
    private static boolean flips(int a, int b, double[] at, List<Set<Integer>> around, int[][] triangles,
                                 double[][] positions) {
        for (int v : new int[]{a, b}) {
            for (int t : around.get(v)) {
                int[] tri = triangles[t];
                boolean hasA = tri[0] == a || tri[1] == a || tri[2] == a, hasB = tri[0] == b || tri[1] == b || tri[2] == b;
                if (hasA && hasB) continue;
                double[][] before = new double[3][], after = new double[3][];
                for (int k = 0; k < 3; k++) {
                    before[k] = positions[tri[k]];
                    after[k] = tri[k] == a || tri[k] == b ? at : positions[tri[k]];
                }
                double[] n0 = normal(before), n1 = normal(after);
                double l0 = Math.sqrt(dot(n0, n0)), l1 = Math.sqrt(dot(n1, n1));
                if (l0 < 1e-9) continue;
                if (l1 < 1e-9) return true;
                if (dot(n0, n1) / (l0 * l1) < FLIP_COSINE) return true;
            }
        }
        return false;
    }

    private static double[] normal(double[][] p) {
        double ux = p[1][0] - p[0][0], uy = p[1][1] - p[0][1], uz = p[1][2] - p[0][2];
        double vx = p[2][0] - p[0][0], vy = p[2][1] - p[0][1], vz = p[2][2] - p[0][2];
        return new double[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** Adds a triangle's plane, weighted by its area, to {@code out}. */
    private static void planeQuadric(double[][] p, int a, int b, int c, double[] out) {
        double[] n = normal(new double[][]{p[a], p[b], p[c]});
        double length = Math.sqrt(dot(n, n));
        if (length < 1e-12) return;
        double area = length / 2;
        double nx = n[0] / length, ny = n[1] / length, nz = n[2] / length;
        double d = -(nx * p[a][0] + ny * p[a][1] + nz * p[a][2]);
        double[] q = {nx * nx, nx * ny, nx * nz, nx * d, ny * ny, ny * nz, ny * d, nz * nz, nz * d, d * d};
        for (int i = 0; i < 10; i++) out[i] += q[i] * area;
    }

    private static double error(double[] q, double x, double y, double z) {
        return q[0] * x * x + 2 * q[1] * x * y + 2 * q[2] * x * z + 2 * q[3] * x + q[4] * y * y + 2 * q[5] * y * z
                + 2 * q[6] * y + q[7] * z * z + 2 * q[8] * z + q[9];
    }

    // ---------------------------------------------------------------- quads

    /**
     * A level's triangles of one group as quads for the game: two triangles sharing an edge and
     * the same view make one quad (the game splits a quad along its first and third corners, so
     * {@code a, b, c} and {@code a, c, d} are exactly those two); a triangle left alone is a quad
     * whose last corner repeats its third. Each quad is 4 corners of x, y, z, then which view
     * ({@link Triangle#side}) it is textured from.
     */
    public static List<double[]> pair(List<Triangle> triangles) {
        int count = triangles.size();
        int[] side = new int[count];
        for (int t = 0; t < count; t++) side[t] = triangles.get(t).side();
        Map<String, List<Integer>> byEdge = new HashMap<>();
        for (int t = 0; t < count; t++) {
            double[] c = triangles.get(t).corners();
            for (int k = 0; k < 3; k++) byEdge.computeIfAbsent(edge(c, k, (k + 1) % 3), e -> new ArrayList<>()).add(t);
        }
        boolean[] used = new boolean[count];
        List<double[]> quads = new ArrayList<>();
        for (int t = 0; t < count; t++) {
            if (used[t]) continue;
            used[t] = true;
            double[] c = triangles.get(t).corners();
            double[] quad = null;
            for (int k = 0; k < 3 && quad == null; k++) {
                for (int other : byEdge.get(edge(c, k, (k + 1) % 3))) {
                    if (used[other] || side[other] != side[t]) continue;
                    double[] o = triangles.get(other).corners();
                    int far = farCorner(o, c, k, (k + 1) % 3);
                    if (far < 0) continue;
                    used[other] = true;
                    // The shared edge k..k+1 becomes the quad's diagonal: corners k+1, k+2, k, far.
                    int first = (k + 1) % 3, second = (k + 2) % 3, third = k;
                    quad = new double[13];
                    put(quad, 0, c, first);
                    put(quad, 1, c, second);
                    put(quad, 2, c, third);
                    put(quad, 3, o, far);
                    break;
                }
            }
            if (quad == null) {
                quad = new double[13];
                for (int k = 0; k < 3; k++) put(quad, k, c, k);
                put(quad, 3, c, 2);
            }
            quad[12] = side[t];
            quads.add(quad);
        }
        return quads;
    }

    private static void put(double[] quad, int at, double[] corners, int corner) {
        System.arraycopy(corners, corner * 3, quad, at * 3, 3);
    }

    /** The corner of {@code other} not on the edge i..j of {@code corners}, -1 when they do not share it. */
    private static int farCorner(double[] other, double[] corners, int i, int j) {
        int shared = 0, far = -1;
        for (int k = 0; k < 3; k++) {
            if (same(other, k, corners, i) || same(other, k, corners, j)) shared++;
            else far = k;
        }
        return shared == 2 ? far : -1;
    }

    private static boolean same(double[] a, int i, double[] b, int j) {
        return a[i * 3] == b[j * 3] && a[i * 3 + 1] == b[j * 3 + 1] && a[i * 3 + 2] == b[j * 3 + 2];
    }

    /** The same key for an edge whichever way round. */
    private static String edge(double[] c, int i, int j) {
        String a = c[i * 3] + "," + c[i * 3 + 1] + "," + c[i * 3 + 2];
        String b = c[j * 3] + "," + c[j * 3 + 1] + "," + c[j * 3 + 2];
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }
}
