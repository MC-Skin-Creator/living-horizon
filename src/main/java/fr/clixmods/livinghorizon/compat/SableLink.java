package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Quaterniondc;
import org.joml.Vector3dc;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the mod reaches of Sable, the physics of Create: Aeronautics: the moving builds it calls
 * sub-levels (an airship, a car), which are not entities. Each one's blocks are kept in a plot of
 * their own, far from the world (around 20 million blocks out), and drawn where its pose puts
 * them: {@code world = position + orientation * ((plot - rotationPoint) * scale)}.
 *
 * <p>Sable has no API for this: everything is found by name, once, and a renamed class turns the
 * link off for the session, said in the log and in {@code /livinghorizon lod}. Only Sable's own
 * classes are looked up by name; what they take and give of the game's is the game's own class,
 * so it reads the same under every loader.
 */
public final class SableLink {
    private static final Links.Status STATUS = new Links.Status();
    private static boolean ready, linked;

    private static Method getContainer, getAllSubLevels, getUniqueId, getName, renderPose, getPlot, isRemoved;
    private static Method position, orientation, rotationPoint, scale;
    private static Method getBoundingBox, toLocal, getChunk;
    private static Method minX, minY, minZ, maxX, maxY, maxZ;

    private SableLink() {
    }

    /** A sub-level the client has, as it is drawn now. Its bounds are in its plot, inclusive. */
    public record Seen(UUID id, String name, Vector3dc position, Quaterniondc orientation, Vector3dc rotationPoint,
                       Vector3dc scale, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Object plot) {
        /** Whether its plot holds a block at all. */
        public boolean empty() {
            return maxX < minX || maxY < minY || maxZ < minZ;
        }
    }

    /** Whether Sable is there and is the version this mod knows. */
    public static synchronized boolean linked() {
        if (ready) return linked;
        ready = true;
        if (!Platform.isModLoaded("sable")) {
            STATUS.set("not installed");
            return false;
        }
        try {
            link();
            linked = true;
            STATUS.set("linked");
        } catch (Throwable e) {
            STATUS.set("off: " + e);
            LivingHorizonClient.LOGGER.warn("Sable found, but not the version this mod knows: its airships are not kept far away", e);
        }
        return linked;
    }

    private static void link() throws ReflectiveOperationException {
        Class<?> container = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Class<?> subLevel = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
        Class<?> clientAccess = Class.forName("dev.ryanhcode.sable.companion.ClientSubLevelAccess");
        Class<?> pose = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
        Class<?> plot = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot");
        Class<?> box = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3ic");
        getContainer = container.getMethod("getContainer", Level.class);
        getAllSubLevels = container.getMethod("getAllSubLevels");
        getUniqueId = clientAccess.getMethod("getUniqueId");
        getName = clientAccess.getMethod("getName");
        renderPose = clientAccess.getMethod("renderPose");
        getPlot = subLevel.getMethod("getPlot");
        isRemoved = subLevel.getMethod("isRemoved");
        position = pose.getMethod("position");
        orientation = pose.getMethod("orientation");
        rotationPoint = pose.getMethod("rotationPoint");
        scale = pose.getMethod("scale");
        getBoundingBox = plot.getMethod("getBoundingBox");
        toLocal = plot.getMethod("toLocal", ChunkPos.class);
        getChunk = plot.getMethod("getChunk", ChunkPos.class);
        minX = box.getMethod("minX");
        minY = box.getMethod("minY");
        minZ = box.getMethod("minZ");
        maxX = box.getMethod("maxX");
        maxY = box.getMethod("maxY");
        maxZ = box.getMethod("maxZ");
    }

    /**
     * The sub-levels the client has in this level, as drawn this tick: none without Sable, and
     * none for the rest of the session once a read fails (said once in the log).
     */
    public static List<Seen> loaded(Level level) {
        if (!linked()) return List.of();
        try {
            Object container = getContainer.invoke(null, level);
            if (container == null) return List.of();
            List<?> all = (List<?>) getAllSubLevels.invoke(container);
            List<Seen> seen = new ArrayList<>(all.size());
            for (Object one : all) {
                if (one == null || (boolean) isRemoved.invoke(one)) continue;
                UUID id = (UUID) getUniqueId.invoke(one);
                if (id == null) continue;
                Object name = getName.invoke(one);
                Object pose = renderPose.invoke(one);
                Object plot = getPlot.invoke(one);
                if (pose == null || plot == null) continue;
                Object bounds = getBoundingBox.invoke(plot);
                seen.add(new Seen(id, name == null ? "" : name.toString(),
                        (Vector3dc) position.invoke(pose), (Quaterniondc) orientation.invoke(pose),
                        (Vector3dc) rotationPoint.invoke(pose), (Vector3dc) scale.invoke(pose),
                        (int) minX.invoke(bounds), (int) minY.invoke(bounds), (int) minZ.invoke(bounds),
                        (int) maxX.invoke(bounds), (int) maxY.invoke(bounds), (int) maxZ.invoke(bounds), plot));
            }
            return seen;
        } catch (Throwable e) {
            fail(e);
            return List.of();
        }
    }

    /** The chunk of a plot at these chunk coordinates of the plot grid: null where it has none. */
    public static @Nullable LevelChunk chunk(Object plot, int chunkX, int chunkZ) {
        if (!linked) return null;
        try {
            Object local = toLocal.invoke(plot, new ChunkPos(chunkX, chunkZ));
            return (LevelChunk) getChunk.invoke(plot, local);
        } catch (Throwable e) {
            fail(e);
            return null;
        }
    }

    private static synchronized void fail(Throwable e) {
        if (!linked) return;
        linked = false;
        STATUS.set("off: " + e);
        LivingHorizonClient.LOGGER.warn("Living Horizon could not read Sable's airships: they are not kept far away", e);
    }

    /** What the link found, for {@code /livinghorizon lod}. */
    static String status() {
        return STATUS.describe();
    }
}
