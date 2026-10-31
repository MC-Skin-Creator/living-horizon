package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Ecstatic, a mod that draws the overworld past the render distance from the world's seed
 * (NeoForge 1.21.1), in single player only. It has no API: it is reached by reflection, like
 * Voxy, and {@link EcstaticSource} reads its world.
 *
 * <p>Ecstatic draws its terrain after the entities, tested against the game's depth and written
 * into it, but with a projection of its own: the game's near plane and a far plane of at least
 * 16,800 blocks. The two depths agree only when the two far planes do. With the game's nearer
 * (four render distances, or the farthest figure), a distant mob had a greater depth than
 * Ecstatic's hill a hundred blocks behind it, and the hill was painted over the mob.
 * {@link #farPlane} gives Ecstatic's plane, which the game's projection takes while Ecstatic
 * draws ({@code GhostRenderer.farPlane}).
 */
public final class EcstaticLink {
    static final String PACKAGE = "com.angryalchemist.ecstatic.";
    private static final Links.Status STATUS = new Links.Status();
    private static boolean ready, linked;
    /** {@code LodRenderer.extendedFarPlaneBlocks(int)}: its far plane for a render distance; null once it failed. */
    private static volatile @Nullable MethodHandle farPlane;
    private static volatile float lastFarPlane;

    private EcstaticLink() {
    }

    /** Whether Ecstatic is there, in a version whose classes this mod knows. */
    static synchronized boolean linked() {
        if (ready) return linked;
        ready = true;
        if (!Platform.isModLoaded("ecstatic")) {
            STATUS.set("not installed");
            return false;
        }
        try {
            Class.forName(PACKAGE + "Ecstatic");
            Class<?> renderer = Class.forName(PACKAGE + "render.LodRenderer");
            linked = true;
            STATUS.set("linked");
            try {
                farPlane = MethodHandles.privateLookupIn(renderer, MethodHandles.lookup())
                        .findStatic(renderer, "extendedFarPlaneBlocks", MethodType.methodType(float.class, int.class));
            } catch (ReflectiveOperationException | RuntimeException e) {
                STATUS.fallback("its far plane unknown, its terrain may be drawn over distant mobs");
                LivingHorizonClient.LOGGER.warn("Ecstatic found, but not its far plane: its terrain may be drawn over distant mobs", e);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            STATUS.set("off: " + e);
            LivingHorizonClient.LOGGER.warn("Ecstatic found, but not the version this mod knows", e);
        }
        return linked;
    }

    /**
     * Whether Ecstatic draws its terrain here: it only does in single player, in the overworld
     * (and not with the camera below y 35, which does not matter to the far plane).
     */
    static boolean draws(Minecraft minecraft) {
        Level level = minecraft.level;
        return linked() && minecraft.getSingleplayerServer() != null
                && level != null && level.dimension().equals(Level.OVERWORLD);
    }

    /**
     * Ecstatic's far plane this frame, in blocks, for the game's projection to take; 0 when it
     * draws nothing here or its plane cannot be read. On the game's thread.
     */
    public static float farPlane() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!draws(minecraft)) return 0f;
        MethodHandle handle = farPlane;
        if (handle == null) return 0f;
        try {
            float far = (float) handle.invokeExact(minecraft.options.getEffectiveRenderDistance());
            lastFarPlane = far;
            return far > 0f ? far : 0f;
        } catch (Throwable e) {
            farPlane = null;
            STATUS.fallback("its far plane unknown, its terrain may be drawn over distant mobs");
            LivingHorizonClient.LOGGER.warn("Ecstatic's far plane could not be read: its terrain may be drawn over distant mobs", e);
            return 0f;
        }
    }

    /** Something {@link EcstaticSource} does without on this version. */
    static void fallback(String what) {
        STATUS.fallback(what);
    }

    /** For {@code /livinghorizon lod}. */
    static String status() {
        linked();
        if (!linked) return STATUS.describe();
        if (!draws(Minecraft.getInstance())) return STATUS.describe() + "; draws nothing here (single player, overworld only)";
        float far = lastFarPlane;
        return far > 0f ? STATUS.describe() + "; far plane matched at " + Math.round(far) + " blocks" : STATUS.describe();
    }
}
