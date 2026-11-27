package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.IdentityHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Entity Model Features (Traben), which the Fresh Animations pack draws its models through. A
 * pack may give a kind of mob several models (a ram with horns, a sheep without), picked for each
 * mob from its id and the pack's rules, as Entity Texture Features picks textures. Which one a
 * mob wears goes into its impostor's key ({@link #variant}): left out, two sheep of one look but
 * two models shared a picture, and a picture of a walk baked from the other one showed horns on a
 * sheep without as it walked. Reached by reflection, through the public parts of EMF and ETF, so
 * that the mod loads without them; any failure turns it off, said once in the log.
 */
public final class EmfLink {
    private static final String ROOT = "traben.entity_model_features.";

    private static @Nullable MethodHandle rootOf, tester, suffix;
    private static @Nullable Class<?> emfModel;
    /** Each renderer's model's picker of variants, or {@link #NONE} for one without. */
    private static final Map<Object, Object> TESTERS = new IdentityHashMap<>();
    private static final Object NONE = new Object();
    private static boolean looked, logged;

    private EmfLink() {
    }

    /**
     * Which of its pack's models {@code entity} wears: 0 without EMF, or for a kind its pack gives
     * one model; otherwise the number EMF picks for it, from its id and the pack's rules.
     */
    @SuppressWarnings("rawtypes")
    public static int variant(Entity entity) {
        if (!looked) find();
        MethodHandle root = rootOf, test = tester, pick = suffix;
        Class<?> model = emfModel;
        if (root == null || test == null || pick == null || model == null) return 0;
        try {
            Object renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (!(renderer instanceof LivingEntityRenderer living)) return 0;
            Object picker = TESTERS.get(renderer);
            if (picker == null) {
                Object shape = living.getModel();
                // A model the pack does not change has no root of EMF's: one model, no variants.
                Object top = model.isInstance(shape) ? root.invoke(shape) : null;
                Object found = top == null ? null : test.invoke(top);
                TESTERS.put(renderer, picker = found == null ? NONE : found);
            }
            return picker == NONE ? 0 : (int) pick.invoke(picker, entity);
        } catch (Throwable error) {
            broken(error);
            rootOf = null;
            return 0;
        }
    }

    /** Resource packs were reloaded: their models, and the pickers of their variants, are new. */
    public static void reloaded() {
        TESTERS.clear();
    }

    private static void find() {
        looked = true;
        try {
            emfModel = Class.forName(ROOT + "models.IEMFModel");
            Class<?> root = Class.forName(ROOT + "models.parts.EMFModelPartRoot");
            Class<?> picker = Class.forName("traben.entity_texture_features.ETFApi$ETFVariantSuffixProvider");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            rootOf = lookup.findVirtual(emfModel, "emf$getEMFRootModel", MethodType.methodType(root));
            tester = lookup.findGetter(root, "variantTester", picker);
            suffix = lookup.findVirtual(picker, "getSuffixForEntity", MethodType.methodType(int.class, Entity.class));
        } catch (ClassNotFoundException absent) {
            // No EMF: nothing to do.
        } catch (ReflectiveOperationException | LinkageError error) {
            broken(error);
        }
    }

    private static void broken(Throwable error) {
        if (logged) return;
        logged = true;
        LivingHorizonClient.LOGGER.warn("Entity Model Features found, but not the version this mod knows", error);
    }
}
