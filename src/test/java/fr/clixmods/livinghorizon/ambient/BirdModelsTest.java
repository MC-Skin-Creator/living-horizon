package fr.clixmods.livinghorizon.ambient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every species has a 3D model, and every model can fly and perch. */
class BirdModelsTest {
    @Test
    void everySpeciesHasAModel() {
        for (Ambience.Species species : Ambience.Species.values()) {
            BirdModels.Model model = BirdModels.of(species);
            assertNotNull(model, species.name());
            assertTrue(model.span() > 0, species.name());
            assertFalse(model.part(BirdModels.Part.BODY).isEmpty(), species + " has no body");
            assertFalse(model.part(BirdModels.Part.WING).isEmpty(), species + " has no wings");
            assertFalse(model.part(BirdModels.Part.TIP).isEmpty(), species + " has no wing tips");
        }
    }

    @Test
    void boxesAreWellFormed() {
        for (Ambience.Species species : Ambience.Species.values()) {
            BirdModels.Model model = BirdModels.of(species);
            for (BirdModels.Cube cube : model.cubes()) {
                String where = species + " " + cube;
                assertTrue(cube.x0() < cube.x1() && cube.y0() < cube.y1() && cube.z0() < cube.z1(), where);
                assertTrue((cube.rgb() & ~0xFFFFFF) == 0, where);
                // The face texture patch is as large as the face: it must fit the grain texture.
                assertTrue(Math.max(cube.x1() - cube.x0(), Math.max(cube.y1() - cube.y0(), cube.z1() - cube.z0())) < 32, where);
            }
        }
    }

    @Test
    void wingsGrowOutwardsFromTheirHinges() {
        for (Ambience.Species species : Ambience.Species.values()) {
            BirdModels.Model model = BirdModels.of(species);
            float reach = 0;
            for (BirdModels.Cube cube : model.part(BirdModels.Part.WING)) {
                assertTrue(cube.x0() >= model.hingeX() - 1e-4f, species + " wing inside the body: " + cube);
            }
            for (BirdModels.Cube cube : model.part(BirdModels.Part.TIP)) {
                assertTrue(cube.x0() >= model.tipX() - 1e-4f, species + " tip inside the wing: " + cube);
                reach = Math.max(reach, cube.x1());
            }
            // Tip to tip, the model is about as wide as the span it says.
            assertTrue(Math.abs(2 * reach - model.span()) <= model.span() * 0.1f, species + " spans " + 2 * reach);
        }
    }
}
