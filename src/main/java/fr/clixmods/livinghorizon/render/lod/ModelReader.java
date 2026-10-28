package fr.clixmods.livinghorizon.render.lod;

import fr.clixmods.livinghorizon.render.PartTree;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

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
        LodModel model = new LodModel();
        read(model, tree, root, "root", -1);
        return model;
    }

    private static void read(LodModel model, Tree tree, ModelPart part, String name, int parent) {
        PartPose pose = part.getInitialPose();
        //? if >=1.21.2 {
        int index = model.part(name, parent, pose.x(), pose.y(), pose.z(), pose.xRot(), pose.yRot(), pose.zRot(),
                pose.xScale(), pose.yScale(), pose.zScale());
        //?} else {
        /*int index = model.part(name, parent, pose.x, pose.y, pose.z, pose.xRot, pose.yRot, pose.zRot, 1, 1, 1);
        *///?}
        for (ModelPart.Cube cube : tree.cubes(part)) {
            float[][] quads = new float[cube.polygons.length][];
            for (int q = 0; q < quads.length; q++) quads[q] = quad(cube.polygons[q]);
            model.cube(index, new double[]{cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ}, quads);
        }
        for (Map.Entry<String, ModelPart> child : tree.children(part).entrySet()) {
            read(model, tree, child.getValue(), child.getKey(), index);
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
