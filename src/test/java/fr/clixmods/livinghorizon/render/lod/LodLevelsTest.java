package fr.clixmods.livinghorizon.render.lod;

import fr.clixmods.livinghorizon.GameBoot;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The levels of detail the generator makes: the game's own cow gives the triangles the Mob LOD
 * Lab gave for it, every level keeps to its budget, the voxel surface is closed, and the
 * triangles pair into quads for the game.
 */
class LodLevelsTest {
    private static Map<ModelLayerLocation, LayerDefinition> roots;

    @BeforeAll
    static void boot() {
        GameBoot.start();
        roots = LayerDefinitions.createRoots();
    }

    /** The game's parts, read the way ModelPartMixin gives them in the game. */
    private static final ModelReader.Tree REFLECTED = new ModelReader.Tree() {
        @Override
        @SuppressWarnings("unchecked")
        public List<ModelPart.Cube> cubes(ModelPart part) {
            return (List<ModelPart.Cube>) field(part, "cubes");
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, ModelPart> children(ModelPart part) {
            return (Map<String, ModelPart>) field(part, "children");
        }

        private Object field(ModelPart part, String name) {
            try {
                Field field = ModelPart.class.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(part);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    };

    private static LodModel model(ModelLayerLocation layer) {
        return ModelReader.read(roots.get(layer).bakeRoot(), REFLECTED);
    }

    private static List<Integer> triangles(ModelLayerLocation layer) {
        LodLevels.Levels levels = LodLevels.generate(model(layer));
        List<Integer> out = new ArrayList<>();
        for (int level = 0; level < levels.count(); level++) out.add(levels.triangles(level));
        return out;
    }

    //? if >=1.21.5 {
    // The cow's model as the lab read it from 1.21.11: the one of the cow variants.
    @Test
    void cowAsInTheLab() {
        assertEquals(List.of(120, 60, 30, 14), triangles(ModelLayers.COW));
    }
    //?}

    private static final List<ModelLayerLocation> MOBS = List.of(ModelLayers.COW, ModelLayers.PIG, ModelLayers.SHEEP,
            ModelLayers.CHICKEN, ModelLayers.HORSE, ModelLayers.WOLF, ModelLayers.IRON_GOLEM, ModelLayers.ZOMBIE,
            ModelLayers.VILLAGER);

    @Test
    void everyLevelKeepsToItsBudget() {
        for (ModelLayerLocation layer : MOBS) {
            LodModel model = model(layer);
            LodLevels.Levels levels = LodLevels.generate(model);
            assertEquals(LodLevels.MODEL_LEVELS, levels.count(), layer.toString());
            assertEquals(model.quads() * 2, levels.triangles(0), layer.toString());
            for (int level = 1; level < levels.count(); level++) {
                int budget = Math.max(4, (int) Math.round(model.quads() * 2 * Math.pow(LodLevels.RATIO, level)));
                assertTrue(levels.triangles(level) <= budget, layer + " level " + level + ": " + levels.triangles(level));
                assertTrue(levels.triangles(level) <= levels.triangles(level - 1), layer + " level " + level);
                assertFalse(levels.level(level).isEmpty(), layer + " level " + level);
            }
        }
    }

    @Test
    void theVoxelSurfaceIsClosed() {
        for (ModelLayerLocation layer : MOBS) {
            LodModel model = model(layer);
            LodLevels.Mesh mesh = LodLevels.surface(model, LodLevels.groups(model));
            Map<Long, Integer> edges = new HashMap<>();
            for (int[] tri : mesh.triangles) {
                for (int k = 0; k < 3; k++) {
                    long a = tri[k], b = tri[(k + 1) % 3];
                    edges.merge(Math.min(a, b) << 32 | Math.max(a, b), 1, Integer::sum);
                }
            }
            assertFalse(edges.isEmpty(), layer.toString());
            // Closed: every edge between two triangles, or four where two solid cells touch by an edge only.
            for (int shared : edges.values()) assertEquals(0, shared % 2, layer.toString());
        }
    }

    @Test
    void noTriangleIsFlat() {
        for (ModelLayerLocation layer : MOBS) {
            LodLevels.Levels levels = LodLevels.generate(model(layer));
            for (int level = 1; level < levels.count(); level++) {
                for (LodLevels.Triangle triangle : levels.level(level)) {
                    double[] n = triangle.normal();
                    assertTrue(n[0] * n[0] + n[1] * n[1] + n[2] * n[2] > 1e-12, layer + " level " + level);
                }
            }
        }
    }

    @Test
    void trianglesPairIntoQuadsForTheGame() {
        LodLevels.Levels levels = LodLevels.generate(model(ModelLayers.COW));
        for (int level = 1; level < levels.count(); level++) {
            Map<Integer, List<LodLevels.Triangle>> byGroup = new HashMap<>();
            for (LodLevels.Triangle t : levels.level(level)) byGroup.computeIfAbsent(t.group(), g -> new ArrayList<>()).add(t);
            int triangles = 0, quads = 0;
            for (List<LodLevels.Triangle> group : byGroup.values()) {
                triangles += group.size();
                quads += LodLevels.pair(group).size();
            }
            assertTrue(quads >= (triangles + 1) / 2 && quads <= triangles, "level " + level + ": " + quads + " for " + triangles);
            // Most triangles find a partner: the game is handed far fewer quads than triangles.
            assertTrue(quads < triangles * 0.8, "level " + level + ": " + quads + " quads for " + triangles + " triangles");
        }
    }

    @Test
    void aQuadIsItsTwoTriangles() {
        double[] a = {0, 0, 0, 1, 0, 0, 1, 1, 0}, b = {0, 0, 0, 1, 1, 0, 0, 1, 0};
        List<double[]> quads = LodLevels.pair(List.of(new LodLevels.Triangle(0, a), new LodLevels.Triangle(0, b)));
        assertEquals(1, quads.size());
        double[] q = quads.get(0);
        // The game splits a quad into corners 0, 1, 2 and 0, 2, 3: those must be the two triangles.
        Set<Set<String>> drawn = Set.of(points(q, 0, 1, 2), points(q, 0, 2, 3));
        assertEquals(Set.of(points(a, 0, 1, 2), points(b, 0, 1, 2)), drawn);
    }

    /** A triangle's corners as a set of points, whatever their order. */
    private static Set<String> points(double[] corners, int i, int j, int k) {
        Set<String> out = new HashSet<>();
        for (int c : new int[]{i, j, k}) out.add(corners[c * 3] + "," + corners[c * 3 + 1] + "," + corners[c * 3 + 2]);
        return out;
    }

    @Test
    void oneGroupPerAnimatedPart() {
        // The cow: its body and the root's other parts, its head, four legs.
        assertEquals(6, LodLevels.groups(model(ModelLayers.COW)).size());
    }

    @Test
    void everyGroupOfTheCowBakesSolid() {
        LodModel model = model(ModelLayers.COW);
        int[] argb = new int[64 * 64];
        for (int i = 0; i < argb.length; i++) argb[i] = 0xFF000000 | i * 2654435 & 0xFFFFFF;
        LodBake.Pixels texture = new LodBake.Pixels(64, 64, argb);
        for (LodLevels.Group group : LodLevels.groups(model)) {
            List<LodBake.Face> faces = LodBake.bake(model, group, texture);
            assertFalse(faces.isEmpty());
            for (LodBake.Face face : faces) {
                assertTrue(face.pixels().width() <= LodBake.MAX_SIDE && face.pixels().height() <= LodBake.MAX_SIDE);
                for (int texel : face.pixels().argb()) assertEquals(0xFF, texel >>> 24, "a solid view has no hole");
            }
        }
    }

    @Test
    void partsAreAnimatedByTheirNames() {
        for (String name : List.of("head", "right_arm", "left_hind_leg", "right_front_leg", "left_haunch")) {
            assertTrue(LodModel.animated(name), name);
        }
        for (String name : List.of("body", "nose", "tail", "leg", "hat", "jacket")) {
            assertFalse(LodModel.animated(name), name);
        }
    }

    @Test
    void aBoxSeenFromATurnedPart() {
        LodModel model = new LodModel();
        int root = model.part("root", -1, 0, 0, 0, 0, 0, 0, 1, 1, 1);
        int turned = model.part("turned", root, 10, 0, 0, 0, Math.PI / 2, 0, 1, 1, 1);
        model.cube(turned, new double[]{0, 0, 0, 2, 3, 4}, new float[6][20]);
        double[] box = LodLevels.cubeIn(model, 0, root);
        // Turned a quarter round y: x follows z, z follows -x.
        assertEquals(List.of(10.0, 0.0, -2.0, 14.0, 3.0, 0.0), boxList(box));
    }

    private static List<Double> boxList(double[] box) {
        List<Double> out = new ArrayList<>();
        for (double v : box) out.add(Math.round(v * 1e6) / 1e6 + 0.0);
        return out;
    }
}
