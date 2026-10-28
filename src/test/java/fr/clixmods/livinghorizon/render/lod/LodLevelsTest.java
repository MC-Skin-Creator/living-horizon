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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The levels of detail the generator makes: the game's own cow and villager give the quads the
 * Mob LOD Lab gave for them, and a few models of two cubes show each rule on its own.
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

    private static List<Integer> quads(ModelLayerLocation layer) {
        LodModel model = ModelReader.read(roots.get(layer).bakeRoot(), REFLECTED);
        List<Integer> quads = new ArrayList<>();
        for (LodLevels.Level level : LodLevels.generate(model)) quads.add(level.quads());
        return quads;
    }

    //? if >=1.21.5 {
    // The cow's model as the lab read it from 1.21.11: the one of the cow variants.
    @Test
    void cowAsInTheLab() {
        assertEquals(List.of(60, 30, 12, 6), quads(ModelLayers.COW));
    }
    //?}

    @Test
    void villagerAsInTheLab() {
        assertEquals(List.of(66, 30, 12, 6), quads(ModelLayers.VILLAGER));
    }

    @Test
    void everyLevelKeepsToItsBudgetOrIsOneBox() {
        for (ModelLayerLocation layer : List.of(ModelLayers.COW, ModelLayers.PIG, ModelLayers.SHEEP, ModelLayers.CHICKEN,
                ModelLayers.HORSE, ModelLayers.WOLF, ModelLayers.IRON_GOLEM, ModelLayers.ZOMBIE)) {
            LodModel model = ModelReader.read(roots.get(layer).bakeRoot(), REFLECTED);
            List<LodLevels.Level> levels = LodLevels.generate(model);
            assertEquals(LodLevels.MODEL_LEVELS, levels.size(), layer.toString());
            assertEquals(model.quads(), levels.get(0).quads(), layer.toString());
            for (int i = 1; i < levels.size(); i++) {
                int budget = Math.max(1, (int) Math.round(model.quads() * Math.pow(LodLevels.RATIO, i)));
                // One box left is as simple as a model gets, even over the budget.
                assertTrue(levels.get(i).quads() <= budget || levels.get(i).groups().size() == 1,
                        layer + " level " + i + ": " + levels.get(i).quads());
                assertTrue(levels.get(i).quads() <= levels.get(i - 1).quads(), layer + " level " + i);
                assertFalse(levels.get(i).groups().isEmpty(), layer + " level " + i);
            }
        }
    }

    /** A cube of six faces in a part of its own, under the root. */
    private static int cube(LodModel model, String name, int parent, double x, double y, double z, double[] box) {
        int part = model.part(name, parent, x, y, z, 0, 0, 0, 1, 1, 1);
        model.cube(part, box, new float[6][20]);
        return part;
    }

    @Test
    void twoCubesSideBySideMergeIntoOneBox() {
        LodModel model = new LodModel();
        int root = model.part("root", -1, 0, 0, 0, 0, 0, 0, 1, 1, 1);
        cube(model, "a", root, 0, 0, 0, new double[]{0, 0, 0, 4, 4, 4});
        cube(model, "b", root, 4, 0, 0, new double[]{0, 0, 0, 4, 4, 4});
        LodLevels.Level level = LodLevels.generate(model, 2, 0.5, true).get(1);
        assertEquals(6, level.quads());
        LodLevels.Group group = level.groups().get(0);
        assertTrue(group.merged());
        assertEquals(root, group.anchor());
        assertEquals(List.of(0.0, 0.0, 0.0, 8.0, 4.0, 4.0), boxList(group.box()));
    }

    @Test
    void aSmallCubeFarAwayIsRemovedRatherThanMerged() {
        LodModel model = new LodModel();
        int root = model.part("root", -1, 0, 0, 0, 0, 0, 0, 1, 1, 1);
        cube(model, "body", root, 0, 0, 0, new double[]{0, 0, 0, 8, 8, 8});
        cube(model, "speck", root, 40, 40, 40, new double[]{0, 0, 0, 1, 1, 1});
        LodLevels.Level level = LodLevels.generate(model, 2, 0.5, true).get(1);
        assertEquals(1, level.groups().size());
        assertEquals(0, level.groups().get(0).sources()[0]);
        assertFalse(level.groups().get(0).merged());
    }

    @Test
    void flatBoxesHaveTwoFaces() {
        assertEquals(2, LodLevels.faces(new double[]{0, 0, 0, 4, 0, 4}));
        assertEquals(6, LodLevels.faces(new double[]{0, 0, 0, 4, 1, 4}));
    }

    @Test
    void freezingALegCostsMore() {
        LodModel model = new LodModel();
        int root = model.part("root", -1, 0, 0, 0, 0, 0, 0, 1, 1, 1);
        int body = cube(model, "body", root, 0, 0, 0, new double[]{0, 0, 0, 8, 4, 4});
        int leg = cube(model, "right_hind_leg", root, 0, 4, 0, new double[]{0, 0, 0, 2, 6, 2});
        assertTrue(model.parts().get(leg).animated());
        assertFalse(model.parts().get(body).animated());
        assertTrue(LodLevels.freezes(model.parts(), leg, root));
        assertFalse(LodLevels.freezes(model.parts(), body, root));
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
