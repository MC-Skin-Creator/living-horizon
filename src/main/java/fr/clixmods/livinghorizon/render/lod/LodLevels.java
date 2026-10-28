package fr.clixmods.livinghorizon.render.lod;

import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The simpler models of a model: its levels of detail, generated from its cubes once per model.
 *
 * <p>Each cube starts as a group of its own, anchored to its part. Level {@code i} may keep
 * {@code round(Q0 × RATIO^i)} quads, Q0 being the model's; while there are more, the cheapest
 * operation is applied:
 * <ul>
 *   <li>a group is <b>removed</b>, at a cost of {@link #REMOVE_WEIGHT} times the square root
 *       of its box's largest face;</li>
 *   <li>two groups are <b>merged</b> into one box, made in the space of their nearest common
 *       part from their cubes' corners at rest, at the cost of the cube root of the empty
 *       volume it adds. Carrying a group up past an animated part (a leg, an arm, the head)
 *       freezes it, and costs half its size plus 2 more. A merge that saves no quad is never
 *       made; a flat box has two faces.</li>
 * </ul>
 * Of two operations that cost the same, the one that saves more quads is taken. A group never
 * merged keeps its cube's quads; a merged one becomes a box whose faces {@link LodBake} bakes.
 * The last level, the impostor, is not made here: it is the flat picture the mod already has.
 */
public final class LodLevels {
    /** Levels made of models: the full one, then three simpler ones. The impostor follows them. */
    public static final int MODEL_LEVELS = 4;
    /** The share of the full model's quads each level keeps more than the next. */
    public static final double RATIO = 0.5;
    /** How much more removing a group costs than the room a merge wastes. */
    static final double REMOVE_WEIGHT = 3;
    private static final double TIE = 1e-9;

    private LodLevels() {
    }

    /**
     * A group of cubes drawn as one at a level.
     *
     * @param anchor  the part it is drawn with, whose animation it follows
     * @param sources the model's cubes it stands for
     * @param box     its box in the anchor's space: min x, y, z, then max
     * @param filled  how much of that box its cubes fill
     * @param quads   what it costs to draw
     * @param merged  a box of its own (baked faces) rather than one cube's own quads
     */
    public record Group(int anchor, int[] sources, double[] box, double filled, int quads, boolean merged) {
    }

    /** One level: its groups and the quads they cost. */
    public record Level(List<Group> groups, int quads) {
    }

    /** The {@link #MODEL_LEVELS} levels of a model, with the defaults. */
    public static List<Level> generate(LodModel model) {
        return generate(model, MODEL_LEVELS, RATIO, true);
    }

    /**
     * @param levels      levels made, the full model included
     * @param ratio       share of the quads each level keeps more than the next
     * @param keepAnimated whether freezing an animated part costs more
     */
    public static List<Level> generate(LodModel model, int levels, double ratio, boolean keepAnimated) {
        List<LodModel.Cube> cubes = model.cubes();
        List<Group> groups = new ArrayList<>();
        for (int i = 0; i < cubes.size(); i++) {
            LodModel.Cube cube = cubes.get(i);
            groups.add(new Group(cube.part(), new int[]{i}, cube.box().clone(), volume(cube.box()), cube.quads().length,
                    false));
        }
        List<Level> out = new ArrayList<>();
        out.add(new Level(List.copyOf(groups), model.quads()));
        List<LodModel.Part> parts = model.parts();
        for (int level = 1; level < levels; level++) {
            int budget = Math.max(1, (int) Math.round(model.quads() * Math.pow(ratio, level)));
            while (quads(groups) > budget && !groups.isEmpty()) {
                Choice best = null;
                if (groups.size() > 1) {
                    for (int k = 0; k < groups.size(); k++) {
                        Group group = groups.get(k);
                        double cost = size(group.box()) * REMOVE_WEIGHT;
                        if (best == null || cost < best.cost) best = Choice.remove(cost, group.quads(), k);
                    }
                }
                for (int a = 0; a < groups.size(); a++) {
                    for (int b = a + 1; b < groups.size(); b++) {
                        Group first = groups.get(a), second = groups.get(b);
                        int anchor = common(parts, first.anchor(), second.anchor());
                        double[] boxA = first.anchor() == anchor ? first.box() : boxOf(model, first.sources(), anchor);
                        double[] boxB = second.anchor() == anchor ? second.box() : boxOf(model, second.sources(), anchor);
                        double[] box = union(boxA, boxB);
                        int faces = faces(box);
                        int saved = first.quads() + second.quads() - faces;
                        if (saved <= 0) continue;
                        double volume = volume(box);
                        double filled = Math.min(volume, Math.max(0, first.filled() + second.filled() - overlap(boxA, boxB)));
                        double cost = Math.cbrt(Math.max(0, volume - filled));
                        if (keepAnimated) {
                            if (first.anchor() != anchor && freezes(parts, first.anchor(), anchor)) {
                                cost += 0.5 * size(first.box()) + 2;
                            }
                            if (second.anchor() != anchor && freezes(parts, second.anchor(), anchor)) {
                                cost += 0.5 * size(second.box()) + 2;
                            }
                        }
                        if (best == null || cost < best.cost - TIE || (Math.abs(cost - best.cost) < TIE && saved > best.saved)) {
                            best = Choice.merge(cost, saved, a, b, new Group(anchor, concat(first.sources(), second.sources()),
                                    box, filled, faces, true));
                        }
                    }
                }
                if (best == null) break;
                if (best.merged == null) {
                    groups.remove(best.remove);
                } else {
                    // The later one first, so that the earlier keeps its index.
                    groups.remove(best.b);
                    groups.remove(best.a);
                    groups.add(best.merged);
                }
            }
            out.add(new Level(List.copyOf(groups), quads(groups)));
        }
        return out;
    }

    /**
     * The levels of a model drawn over another one of the same figure (a sheep's wool over its
     * body, a saddle, armour), grouped as the other's are, part by part by name: every group of
     * {@code template}'s level takes this model's cubes of the parts its cubes belong to, merged
     * into one box where the template's are, anchored to the part of the same name. A part the
     * template has at that level no group for is left out; one the template does not have at all
     * keeps its cubes. Its boxes then wrap the other's when its cubes do (wool is drawn larger).
     */
    public static List<Level> follow(LodModel model, LodModel template, List<Level> templateLevels) {
        List<LodModel.Cube> cubes = model.cubes();
        List<LodModel.Part> parts = model.parts();
        java.util.Map<String, Integer> partByName = new java.util.HashMap<>();
        for (int p = 0; p < parts.size(); p++) partByName.putIfAbsent(parts.get(p).name(), p);
        java.util.Set<String> known = new java.util.HashSet<>();
        for (LodModel.Part part : template.parts()) known.add(part.name());
        List<Level> out = new ArrayList<>();
        List<Group> all = new ArrayList<>();
        for (int i = 0; i < cubes.size(); i++) all.add(single(model, i));
        out.add(new Level(List.copyOf(all), model.quads()));
        for (int level = 1; level < templateLevels.size(); level++) {
            boolean[] used = new boolean[cubes.size()];
            List<Group> groups = new ArrayList<>();
            for (Group group : templateLevels.get(level).groups()) {
                java.util.Set<String> names = new java.util.HashSet<>();
                for (int source : group.sources()) names.add(template.parts().get(template.cubes().get(source).part()).name());
                List<Integer> mine = new ArrayList<>();
                for (int c = 0; c < cubes.size(); c++) {
                    if (!used[c] && names.contains(parts.get(cubes.get(c).part()).name())) mine.add(c);
                }
                if (mine.isEmpty()) continue;
                for (int c : mine) used[c] = true;
                if (!group.merged()) {
                    for (int c : mine) groups.add(single(model, c));
                    continue;
                }
                int[] sources = mine.stream().mapToInt(Integer::intValue).toArray();
                Integer named = partByName.get(template.parts().get(group.anchor()).name());
                int anchor = named != null ? named : cubes.get(sources[0]).part();
                if (named == null) {
                    for (int source : sources) anchor = common(parts, anchor, cubes.get(source).part());
                }
                double[] box = boxOf(model, sources, anchor);
                double filled = 0;
                for (int source : sources) filled += volume(cubes.get(source).box());
                groups.add(new Group(anchor, sources, box, Math.min(filled, volume(box)), faces(box), true));
            }
            for (int c = 0; c < cubes.size(); c++) {
                // A part of the template's that this level has no group for went: so does this model's.
                if (!used[c] && !known.contains(parts.get(cubes.get(c).part()).name())) groups.add(single(model, c));
            }
            out.add(new Level(List.copyOf(groups), quads(groups)));
        }
        return out;
    }

    private static Group single(LodModel model, int index) {
        LodModel.Cube cube = model.cubes().get(index);
        return new Group(cube.part(), new int[]{index}, cube.box().clone(), volume(cube.box()), cube.quads().length, false);
    }

    private record Choice(double cost, int saved, int remove, int a, int b, Group merged) {
        static Choice remove(double cost, int saved, int index) {
            return new Choice(cost, saved, index, -1, -1, null);
        }

        static Choice merge(double cost, int saved, int a, int b, Group merged) {
            return new Choice(cost, saved, -1, a, b, merged);
        }
    }

    private static int quads(List<Group> groups) {
        int quads = 0;
        for (Group group : groups) quads += group.quads();
        return quads;
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    static double volume(double[] box) {
        return extent(box, 0) * extent(box, 1) * extent(box, 2);
    }

    private static double extent(double[] box, int axis) {
        return Math.max(0, box[axis + 3] - box[axis]);
    }

    /** The square root of the box's largest face: how big it looks. */
    static double size(double[] box) {
        double x = extent(box, 0), y = extent(box, 1), z = extent(box, 2);
        return Math.sqrt(Math.max(x * y, Math.max(x * z, y * z)));
    }

    static double[] union(double[] a, double[] b) {
        return new double[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[3], b[3]), Math.max(a[4], b[4]), Math.max(a[5], b[5])};
    }

    static double overlap(double[] a, double[] b) {
        double x = Math.min(a[3], b[3]) - Math.max(a[0], b[0]);
        double y = Math.min(a[4], b[4]) - Math.max(a[1], b[1]);
        double z = Math.min(a[5], b[5]) - Math.max(a[2], b[2]);
        return x > 0 && y > 0 && z > 0 ? x * y * z : 0;
    }

    /** Faces a box is drawn with: two per pair of sides that is not flat. */
    static int faces(double[] box) {
        double x = extent(box, 0), y = extent(box, 1), z = extent(box, 2);
        int faces = 0;
        if (y * z > 1e-6) faces += 2;
        if (x * z > 1e-6) faces += 2;
        if (x * y > 1e-6) faces += 2;
        return faces;
    }

    /** The nearest part both are under, themselves included. */
    static int common(List<LodModel.Part> parts, int a, int b) {
        while (parts.get(a).depth() > parts.get(b).depth()) a = parts.get(a).parent();
        while (parts.get(b).depth() > parts.get(a).depth()) b = parts.get(b).parent();
        while (a != b) {
            a = parts.get(a).parent();
            b = parts.get(b).parent();
        }
        return a;
    }

    /** Whether carrying a group from part {@code from} up to {@code to} freezes an animated part. */
    static boolean freezes(List<LodModel.Part> parts, int from, int to) {
        for (int p = from; p != to && p >= 0; p = parts.get(p).parent()) {
            if (parts.get(p).animated()) return true;
        }
        return false;
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
        for (int i = 1; i < sources.length; i++) box = union(box, cubeIn(model, sources[i], anchor));
        return box;
    }
}
