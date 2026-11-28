package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * GeckoLib 5 on 1.21.5 to 1.21.8 adds the light an entity is drawn in to its state only in its
 * hook on the game's own draw of an entity ({@code EntityRenderDispatcher.render}), and throws
 * without it when the state is drawn. The figures the mod draws through the renderer never go
 * there: the light is given to their state here, as that hook does. Reached by reflection, so
 * that the mod loads without GeckoLib; any failure turns this off. Does nothing to a state that
 * is not GeckoLib's (GeckoLib 4 has none).
 */
public final class GeckoLib {
    private static @Nullable Class<?> geoState;
    private static @Nullable Method add;
    private static @Nullable Object packedLight;
    private static boolean looked;

    private GeckoLib() {
    }

    /** Tells a GeckoLib state the light it is drawn in. */
    public static void light(Object state, int light) {
        if (!looked) find();
        Class<?> type = geoState;
        Method method = add;
        if (type == null || method == null || !type.isInstance(state)) return;
        try {
            method.invoke(state, packedLight, light);
        } catch (ReflectiveOperationException | RuntimeException error) {
            LivingHorizonClient.LOGGER.warn("GeckoLib found, but not the version this mod knows", error);
            add = null;
        }
    }

    private static void find() {
        looked = true;
        if (!Platform.isModLoaded("geckolib")) return;
        try {
            Class<?> state = Class.forName("software.bernie.geckolib.renderer.base.GeoRenderState");
            Object ticket = Class.forName("software.bernie.geckolib.constant.DataTickets").getField("PACKED_LIGHT").get(null);
            for (Method method : state.getMethods()) {
                if (method.getName().equals("addGeckolibData") && method.getParameterCount() == 2) {
                    packedLight = ticket;
                    add = method;
                    geoState = state;
                    return;
                }
            }
        } catch (ClassNotFoundException fourOrLater) {
            // GeckoLib 4 has no state of its own; from 26.1 it is com.geckolib, past 1.21.8.
        } catch (ReflectiveOperationException | LinkageError error) {
            LivingHorizonClient.LOGGER.warn("GeckoLib found, but not the version this mod knows", error);
        }
    }
}
