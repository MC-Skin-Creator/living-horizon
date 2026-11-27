package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * The Graveyard's crows, ravens and magpies ({@code CorvidEntity}) play the animation of the
 * flight state the server sends them, which is not saved: a copy, made from saved data, kept the
 * first one, idle, and was drawn perched in the sky. The copy is put in its flying state, wings
 * flapping. Reached by reflection, so that the mod loads without it; any failure turns this off.
 */
public final class Graveyard {
    private static @Nullable Class<?> corvid;
    private static @Nullable EntityDataAccessor<Byte> flightState;
    private static byte fly;
    private static boolean looked;

    private Graveyard() {
    }

    /** Makes the copy of one of The Graveyard's birds fly. Does nothing to any other mob. */
    public static void flying(Entity copy) {
        if (!looked) find();
        Class<?> type = corvid;
        EntityDataAccessor<Byte> state = flightState;
        if (type == null || state == null || !type.isInstance(copy)) return;
        try {
            copy.getEntityData().set(state, fly);
        } catch (RuntimeException error) {
            LivingHorizonClient.LOGGER.warn("The Graveyard found, but not the version this mod knows", error);
            flightState = null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void find() {
        looked = true;
        if (!Platform.isModLoaded("graveyard")) return;
        try {
            Class<?> type = Class.forName("com.lion.graveyard.entities.CorvidEntity");
            Field accessor = type.getDeclaredField("FLIGHT_STATE");
            accessor.setAccessible(true);
            Class<?> states = Class.forName("com.lion.graveyard.entities.CorvidEntity$FlightState");
            Field id = states.getDeclaredField("id");
            id.setAccessible(true);
            fly = id.getByte(states.getField("FLY").get(null));
            flightState = (EntityDataAccessor<Byte>) accessor.get(null);
            corvid = type;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            LivingHorizonClient.LOGGER.warn("The Graveyard found, but not the version this mod knows", error);
        }
    }
}
