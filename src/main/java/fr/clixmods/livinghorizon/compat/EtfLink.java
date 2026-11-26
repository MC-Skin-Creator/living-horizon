package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
//? if <1.21.9
/*import net.minecraft.client.renderer.entity.state.EntityRenderState;*/
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Entity Texture Features (Traben) dresses each mob in one of a resource pack's textures for
 * its kind, picked from its id and what the pack's rules read on it: two cows the game draws
 * alike may wear two textures, which an impostor's key must tell apart (issue #96). Entity
 * Model Features, and the Fresh Animations pack with it, animate the models through ETF.
 * Reached by reflection, so that the mod loads without it; any failure turns it off, said once
 * in the log.
 */
public final class EtfLink {
    private static final String ROOT = "traben.entity_texture_features.";
    /** ETF's own copy of a texture, its emissive pixels taken out, is in a namespace of this prefix. */
    private static final String PATCHED = "etf_patched_";

    private static @Nullable MethodHandle variantOf;
    //? if <1.21.9 {
    /*private static @Nullable MethodHandle stateOf, mount, unMount;
    *///?}
    private static boolean looked;

    private EtfLink() {
    }

    /** Whether ETF is there, in a version this mod knows. */
    public static boolean present() {
        if (!looked) find();
        return variantOf != null;
    }

    /**
     * The texture ETF dresses this entity in, in place of {@code texture}, the one its renderer
     * names: {@code texture} itself without ETF, or when no pack has another for it. Never a
     * blink, nor ETF's copy patched for an emissive layer: the texture worn, whatever the frame.
     */
    public static Identifier worn(Entity entity, Identifier texture) {
        if (!looked) find();
        MethodHandle handle = variantOf;
        if (handle == null) return texture;
        try {
            Identifier worn = (Identifier) handle.invoke(entity, texture);
            return worn == null ? texture : steady(worn);
        } catch (Throwable error) {
            broken(error);
            variantOf = null;
            return texture;
        }
    }

    /** A texture as worn, its blinks and patched copy brought back to it. */
    static Identifier steady(Identifier texture) {
        String namespace = texture.getNamespace(), path = texture.getPath();
        if (namespace.startsWith(PATCHED)) namespace = namespace.substring(PATCHED.length());
        if (path.endsWith("_blink.png")) path = path.substring(0, path.length() - "_blink.png".length()) + ".png";
        else if (path.endsWith("_blink2.png")) path = path.substring(0, path.length() - "_blink2.png".length()) + ".png";
        return namespace.equals(texture.getNamespace()) && path.equals(texture.getPath())
                ? texture : Identifier.fromNamespaceAndPath(namespace, path);
    }

    //? if <1.21.9 {
    /*/^*
     * Tells ETF which entity the mod draws, as the game's own draw of an entity does: ETF picks
     * its textures, and Entity Model Features animates its model, only for the entity it was
     * told of. The mod draws its figures, far away and in the impostor pictures, through their
     * renderer, without that draw. Gives back what undoes it.
     ^/
    public static Runnable drawing(EntityRenderState state) {
        if (!looked) find();
        MethodHandle of = stateOf, on = mount, off = unMount;
        if (of == null || on == null || off == null) return NOTHING;
        try {
            //? if >=1.21.2 {
            Object told = of.invoke((Object) state);
            //?} else
            /^Object told = of.invoke((Object) state.entity);^/
            if (told == null) return NOTHING;
            on.invoke(told);
        } catch (Throwable error) {
            broken(error);
            stateOf = null;
            return NOTHING;
        }
        return () -> {
            try {
                off.invoke();
            } catch (Throwable error) {
                broken(error);
                stateOf = null;
            }
        };
    }

    private static final Runnable NOTHING = () -> {
    };
    *///?}

    private static void find() {
        looked = true;
        try {
            Class<?> api = Class.forName(ROOT + "ETFApi");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            variantOf = lookup.findStatic(api, "getCurrentETFVariantTextureOfEntity",
                    MethodType.methodType(Identifier.class, Entity.class, Identifier.class));
            //? if <1.21.9 {
            /*Class<?> state = Class.forName(ROOT + "features.state.ETFEntityRenderState");
            Class<?> current = Class.forName(ROOT + "features.state.ETFState");
            //? if >=1.21.2 {
            stateOf = lookup.findVirtual(Class.forName(ROOT + "features.state.HoldsETFRenderState"), "etf$getState",
                    MethodType.methodType(state));
            //?} else
            /^stateOf = lookup.findStatic(state, "forEntity", MethodType.methodType(state, Class.forName(ROOT + "utils.ETFEntity")));^/
            mount = lookup.findStatic(current, "mount", MethodType.methodType(void.class, state));
            unMount = lookup.findStatic(current, "unMount", MethodType.methodType(void.class));
            *///?}
        } catch (ClassNotFoundException absent) {
            // No ETF: nothing to do.
        } catch (ReflectiveOperationException | LinkageError error) {
            broken(error);
        }
    }

    private static void broken(Throwable error) {
        LivingHorizonClient.LOGGER.warn("Entity Texture Features found, but not the version this mod knows", error);
    }
}
