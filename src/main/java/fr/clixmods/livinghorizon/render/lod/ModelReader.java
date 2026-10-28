package fr.clixmods.livinghorizon.render.lod;

import fr.clixmods.livinghorizon.render.PartTree;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a model from the game's {@link ModelPart} tree, as it was made, at rest: what a mob of
 * any mod is drawn with, so that its levels of detail are made at run time.
 */
public final class ModelReader {
    /** What a part holds, which the game keeps to itself: {@link PartTree} in the game. */
    public interface Tree {
        List<ModelPart.Cube> cubes(ModelPart part);

        Map<String, ModelPart> children(ModelPart part);
    }

    /** The game's parts, through ModelPartMixin. */
    public static final Tree GAME = new Tree() {
        @Override
        public List<ModelPart.Cube> cubes(ModelPart part) {
            return ((PartTree) (Object) part).livinghorizon$cubes();
        }

        @Override
        public Map<String, ModelPart> children(ModelPart part) {
            return ((PartTree) (Object) part).livinghorizon$children();
        }
    };

    private ModelReader() {
    }

    /** The model under {@code root}, every part and cube of it, each part at its initial pose. */
    public static LodModel read(ModelPart root, Tree tree) {
        return read(root, tree, false).model();
    }

    /**
     * A model read with the game's parts it came from.
     *
     * @param parts     the game's part of each of the model's parts, by index
     * @param cubeIndex for each of the model's cubes, its index in its part's own list
     */
    public record Read(LodModel model, List<ModelPart> parts, int[] cubeIndex) {
    }

    /**
     * The model under {@code root}; with {@code drawnOnly}, only the cubes the game draws as the
     * parts stand now: none under a hidden part, none of a part that skips its own.
     */
    public static Read read(ModelPart root, Tree tree, boolean drawnOnly) {
        LodModel model = new LodModel();
        List<ModelPart> parts = new ArrayList<>();
        List<Integer> cubeIndex = new ArrayList<>();
        read(model, tree, root, "root", -1, drawnOnly, parts, cubeIndex);
        int[] indices = new int[cubeIndex.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = cubeIndex.get(i);
        return new Read(model, parts, indices);
    }

    /**
     * A model's parts in the order they are read, and for each the index past its own
     * children: walked every frame without asking the game's maps for an iterator each time.
     */
    private record Flat(ModelPart[] parts, int[] after) {
    }

    private static final Map<ModelPart, Flat> FLAT = new IdentityHashMap<>();

    /**
     * A number that changes when which parts the game draws changes: two models read with
     * {@code drawnOnly} from the same root are the same when it is. On the render thread.
     */
    public static long drawn(ModelPart root, Tree tree) {
        Flat flat = FLAT.get(root);
        if (flat == null) {
            List<ModelPart> parts = new ArrayList<>();
            List<Integer> after = new ArrayList<>();
            flatten(root, tree, parts, after);
            int[] ends = new int[after.size()];
            for (int i = 0; i < ends.length; i++) ends[i] = after.get(i);
            FLAT.put(root, flat = new Flat(parts.toArray(new ModelPart[0]), ends));
        }
        long hash = 0xcbf29ce484222325L;
        ModelPart[] parts = flat.parts();
        for (int i = 0; i < parts.length; ) {
            ModelPart part = parts[i];
            hash = (hash ^ (part.visible ? 1 : 0) ^ (part.skipDraw ? 2 : 0)) * 0x100000001b3L;
            // The game draws nothing under a hidden part.
            i = part.visible ? i + 1 : flat.after()[i];
        }
        return hash;
    }

    private static void flatten(ModelPart part, Tree tree, List<ModelPart> parts, List<Integer> after) {
        int at = parts.size();
        parts.add(part);
        after.add(0);
        for (ModelPart child : tree.children(part).values()) flatten(child, tree, parts, after);
        after.set(at, parts.size());
    }

    /** Forgets the models walked so far: the game's are made again on a reload. */
    public static void forget() {
        FLAT.clear();
    }

    private static void read(LodModel model, Tree tree, ModelPart part, String name, int parent, boolean drawnOnly,
                             List<ModelPart> parts, List<Integer> cubeIndex) {
        PartPose pose = part.getInitialPose();
        //? if >=1.21.2 {
        int index = model.part(name, parent, pose.x(), pose.y(), pose.z(), pose.xRot(), pose.yRot(), pose.zRot(),
                pose.xScale(), pose.yScale(), pose.zScale());
        //?} else {
        /*int index = model.part(name, parent, pose.x, pose.y, pose.z, pose.xRot, pose.yRot, pose.zRot, 1, 1, 1);
        *///?}
        parts.add(part);
        if (drawnOnly && !part.visible) return;
        if (!drawnOnly || !part.skipDraw) {
            List<ModelPart.Cube> cubes = tree.cubes(part);
            for (int c = 0; c < cubes.size(); c++) {
                ModelPart.Cube cube = cubes.get(c);
                float[][] quads = new float[cube.polygons.length][];
                for (int q = 0; q < quads.length; q++) quads[q] = quad(cube.polygons[q]);
                model.cube(index, new double[]{cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ}, quads);
                cubeIndex.add(c);
            }
        }
        for (Map.Entry<String, ModelPart> child : tree.children(part).entrySet()) {
            read(model, tree, child.getValue(), child.getKey(), index, drawnOnly, parts, cubeIndex);
        }
    }

    /** One face: its four corners, x, y, z in pixels and u, v from 0 to 1. */
    private static float[] quad(ModelPart.Polygon polygon) {
        //? if >=1.21.2 {
        ModelPart.Vertex[] vertices = polygon.vertices();
        //?} else
        /*ModelPart.Vertex[] vertices = polygon.vertices;*/
        float[] out = new float[20];
        for (int k = 0; k < 4 && k < vertices.length; k++) {
            ModelPart.Vertex vertex = vertices[k];
            //? if >=1.21.9 {
            out[k * 5] = vertex.x();
            out[k * 5 + 1] = vertex.y();
            out[k * 5 + 2] = vertex.z();
            out[k * 5 + 3] = vertex.u();
            out[k * 5 + 4] = vertex.v();
            //?} elif >=1.21.2 {
            /*out[k * 5] = vertex.pos().x();
            out[k * 5 + 1] = vertex.pos().y();
            out[k * 5 + 2] = vertex.pos().z();
            out[k * 5 + 3] = vertex.u();
            out[k * 5 + 4] = vertex.v();
            *///?} else {
            /*out[k * 5] = vertex.pos.x();
            out[k * 5 + 1] = vertex.pos.y();
            out[k * 5 + 2] = vertex.pos.z();
            out[k * 5 + 3] = vertex.u;
            out[k * 5 + 4] = vertex.v;
            *///?}
        }
        return out;
    }
}
