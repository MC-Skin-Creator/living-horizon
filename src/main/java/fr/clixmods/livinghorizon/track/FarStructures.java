package fr.clixmods.livinghorizon.track;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.JsonStore;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.compat.SableLink;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Moving builds met on the way, kept where they were last seen: the airships and cars of
 * Create: Aeronautics, which Sable moves ({@link SableLink}). They are not entities, so the
 * memory of mobs does not know them: their blocks are copied while the client has them, and
 * drawn where the build was when Sable stopped sending it ({@code render/StructureRenderer}).
 *
 * <p>Sable only tells the client a build is gone, never why. As with mobs, the distance tells:
 * gone close by, it was taken apart or destroyed, and is forgotten; gone far away, it went out
 * of range and is kept. Coming back within range of a copy that Sable does not send again
 * forgets it: someone flew it away. Only this client remembers them, in
 * {@code config/livinghorizon/structures/<server>.json}.
 */
public final class FarStructures {
    /** Layout of the file: the list under {@code "structures"}, with its version. */
    static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().create();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** Blocks past its own size: a build gone this close to the player was taken apart, not left behind. */
    static final double GONE_NEAR = 48;
    /** Blocks inside the distance the client is sent chunks at: a copy there that Sable does not send is not there. */
    static final double NEAR_MARGIN = 32;
    /** Ticks a copy in range stays missing before it is forgotten: Sable sends a build a moment after its chunks. */
    static final int MISSING_TICKS = 200;
    /** Ticks between two copies of a build's blocks while Sable sends it. */
    static final int RECOPY_TICKS = 200;

    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Living Horizon structures");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    /** A build, as remembered. The pose is Sable's: {@code world = position + orientation * ((plot - rotationPoint) * scale)}. */
    public static final class Remembered {
        UUID id;
        String name = "";
        String dimension;
        double x, y, z;
        double qx, qy, qz, qw = 1;
        double rx, ry, rz;
        double sx = 1, sy = 1, sz = 1;
        /** The corner of its box in its plot, and the box's size. */
        int minX, minY, minZ, sizeX, sizeY, sizeZ;
        List<JsonElement> palette;
        int[] runs;
        /** When the client last had it, in milliseconds. */
        long seen;

        transient @Nullable StructureBlocks blocks;
        /** Sable sends it now: the game draws it, not the copy. */
        transient boolean live;
        /** Where Sable would send it: a copy there would stand where the build may no longer be. */
        transient boolean near;
        transient int missing;
        transient long copiedAt;
        transient @Nullable CompletableFuture<Copy> copying;

        /** For the file. */
        Remembered() {
            copiedAt = Long.MIN_VALUE / 2;
        }

        Remembered(UUID id) {
            this();
            this.id = id;
        }

        public UUID id() {
            return id;
        }

        public String dimension() {
            return dimension;
        }

        /** Its blocks, once copied: null before. */
        public @Nullable StructureBlocks blocks() {
            return blocks;
        }

        public Vector3d position() {
            return new Vector3d(x, y, z);
        }

        public Quaterniond orientation() {
            return new Quaterniond(qx, qy, qz, qw);
        }

        public Vector3d scale() {
            return new Vector3d(sx, sy, sz);
        }

        /** Where the corner of its box is, from the point it turns round, in its plot. */
        public Vector3d corner() {
            return new Vector3d(minX - rx, minY - ry, minZ - rz);
        }

        /** Where a point of its plot is in the world. */
        public Vector3d world(double px, double py, double pz) {
            Vector3d point = new Vector3d(px - rx, py - ry, pz - rz).mul(sx, sy, sz);
            new Quaterniond(qx, qy, qz, qw).transform(point);
            return point.add(x, y, z);
        }

        /** The middle of its box, in the world. */
        public Vector3d center() {
            return world(minX + sizeX / 2.0, minY + sizeY / 2.0, minZ + sizeZ / 2.0);
        }

        /** Half the diagonal of its box, scaled: a sphere round {@link #center()} holds it all. */
        public double radius() {
            double scale = Math.max(Math.abs(sx), Math.max(Math.abs(sy), Math.abs(sz)));
            return 0.5 * Math.sqrt((double) sizeX * sizeX + (double) sizeY * sizeY + (double) sizeZ * sizeZ) * scale;
        }

        /** Takes the pose and box Sable draws it at now. */
        void place(SableLink.Seen seen, String dimension) {
            this.name = seen.name();
            this.dimension = dimension;
            x = seen.position().x();
            y = seen.position().y();
            z = seen.position().z();
            qx = seen.orientation().x();
            qy = seen.orientation().y();
            qz = seen.orientation().z();
            qw = seen.orientation().w();
            rx = seen.rotationPoint().x();
            ry = seen.rotationPoint().y();
            rz = seen.rotationPoint().z();
            sx = seen.scale().x();
            sy = seen.scale().y();
            sz = seen.scale().z();
        }

        /** Whether its box moved or changed size in its plot since it was copied. */
        boolean boxIs(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
            return this.minX == minX && this.minY == minY && this.minZ == minZ
                    && this.sizeX == sizeX && this.sizeY == sizeY && this.sizeZ == sizeZ;
        }
    }

    /** The blocks of a box, copied, and where the box was. */
    record Copy(int minX, int minY, int minZ, @Nullable StructureBlocks blocks) {
    }

    private final Map<UUID, Remembered> structures = new HashMap<>();
    private @Nullable Path file;
    private boolean dirty;
    private long clock;

    /** The thread that copies the builds' blocks and bakes their meshes, off the game's. */
    public static Executor work() {
        return WORK;
    }

    // --- Lifecycle ---------------------------------------------------------------------

    void open(String server) {
        close();
        file = Platform.configDir().resolve("livinghorizon").resolve("structures")
                .resolve(RestingPlayers.safe(server) + ".json");
        if (!Files.exists(file)) return;
        for (Remembered structure : read(file)) structures.put(structure.id, structure);
    }

    void close() {
        save();
        structures.clear();
        file = null;
        dirty = false;
    }

    /** Another dimension or world: none of them is sent any more. */
    void levelChanged() {
        for (Remembered structure : structures.values()) {
            structure.live = false;
            structure.near = false;
            structure.missing = 0;
        }
    }

    /** Forgets every build of this server. */
    public void forgetAll() {
        structures.clear();
        dirty = true;
        save();
    }

    /** The copies to draw in this dimension: not sent by Sable now, out of the range it sends them in, with their blocks. */
    public List<Remembered> shown(String dimension) {
        List<Remembered> shown = new ArrayList<>();
        for (Remembered structure : structures.values()) {
            if (!structure.live && !structure.near && structure.blocks != null && dimension.equals(structure.dimension)) {
                shown.add(structure);
            }
        }
        return shown;
    }

    /** Every build remembered, for the debug tools and the game tests. */
    public Collection<Remembered> all() {
        return structures.values();
    }

    // --- Each tick ---------------------------------------------------------------------

    void tick(ClientLevel level, Entity self, int renderBlocks, FarConfig config) {
        clock++;
        if (clock % 1200 == 0 && dirty) save();
        if (!config.distantStructures || !SableLink.linked()) return;
        String dimension = SharedPositions.dimensionId(level.dimension());
        Set<UUID> sent = new HashSet<>();
        boolean copiedOne = false;
        for (SableLink.Seen seen : SableLink.loaded(level)) {
            if (seen.empty()) continue;
            int sizeX = seen.maxX() - seen.minX() + 1, sizeY = seen.maxY() - seen.minY() + 1, sizeZ = seen.maxZ() - seen.minZ() + 1;
            if (!StructureBlocks.fits(sizeX, sizeY, sizeZ)) continue;
            sent.add(seen.id());
            Remembered structure = structures.computeIfAbsent(seen.id(), Remembered::new);
            structure.place(seen, dimension);
            structure.live = true;
            structure.missing = 0;
            structure.seen = System.currentTimeMillis();
            dirty = true;
            take(structure);
            boolean moved = !structure.boxIs(seen.minX(), seen.minY(), seen.minZ(), sizeX, sizeY, sizeZ);
            // One copy a tick, of a build never copied, changed in size, or not copied for a while.
            if (!copiedOne && structure.copying == null
                    && (structure.blocks == null || moved || clock - structure.copiedAt >= RECOPY_TICKS)) {
                structure.copying = copy(seen, sizeX, sizeY, sizeZ);
                structure.copiedAt = clock;
                copiedOne = true;
            }
        }
        for (Iterator<Remembered> it = structures.values().iterator(); it.hasNext(); ) {
            Remembered structure = it.next();
            if (sent.contains(structure.id)) continue;
            if (!dimension.equals(structure.dimension)) {
                structure.live = false;
                continue;
            }
            take(structure);
            double distance = structure.center().distance(self.getX(), self.getY(), self.getZ());
            if (structure.live) {
                structure.live = false;
                structure.near = false;
                if (gone(distance, structure.radius()) || structure.blocks == null) {
                    // Taken apart or destroyed close by, or never copied: nothing to show.
                    it.remove();
                    dirty = true;
                }
                continue;
            }
            structure.near = missing(distance, structure.radius(), renderBlocks);
            if (structure.near) {
                if (++structure.missing >= MISSING_TICKS) {
                    it.remove();
                    dirty = true;
                }
            } else {
                structure.missing = 0;
            }
        }
    }

    /** Whether a build Sable stopped sending this far from the player, its box this big, was taken apart there. */
    static boolean gone(double distance, double radius) {
        return distance < radius + GONE_NEAR;
    }

    /** Whether a copy this far from the player, its box this big, is where Sable would send it: then it is not there. */
    static boolean missing(double distance, double radius, int renderBlocks) {
        return distance + radius < renderBlocks - NEAR_MARGIN;
    }

    /** Takes a copy of the blocks once it is made, if the box is still where it was copied from. */
    private void take(Remembered structure) {
        CompletableFuture<Copy> copying = structure.copying;
        if (copying == null || !copying.isDone()) return;
        structure.copying = null;
        Copy copy = copying.getNow(null);
        if (copy == null || copy.blocks() == null) return;
        StructureBlocks blocks = copy.blocks();
        if (blocks.solid() == 0) return;
        if (structure.blocks != null && structure.blocks.hash() == blocks.hash()
                && structure.boxIs(copy.minX(), copy.minY(), copy.minZ(), blocks.sizeX(), blocks.sizeY(), blocks.sizeZ())) {
            return;
        }
        structure.minX = copy.minX();
        structure.minY = copy.minY();
        structure.minZ = copy.minZ();
        structure.sizeX = blocks.sizeX();
        structure.sizeY = blocks.sizeY();
        structure.sizeZ = blocks.sizeZ();
        structure.blocks = blocks;
        structure.palette = blocks.savedPalette();
        structure.runs = blocks.savedRuns();
        dirty = true;
    }

    /**
     * Copies the sections of the build's plot that its box touches, on the game's thread, which
     * costs little; its blocks are read from them on {@link #work()}.
     */
    private static CompletableFuture<Copy> copy(SableLink.Seen seen, int sizeX, int sizeY, int sizeZ) {
        int minCx = seen.minX() >> 4, minSy = seen.minY() >> 4, minCz = seen.minZ() >> 4;
        int countX = (seen.maxX() >> 4) - minCx + 1, countY = (seen.maxY() >> 4) - minSy + 1, countZ = (seen.maxZ() >> 4) - minCz + 1;
        @SuppressWarnings("unchecked")
        PalettedContainer<BlockState>[] sections = new PalettedContainer[countX * countY * countZ];
        for (int i = 0; i < countX; i++) {
            for (int k = 0; k < countZ; k++) {
                LevelChunk chunk = SableLink.chunk(seen.plot(), minCx + i, minCz + k);
                if (chunk == null) continue;
                LevelChunkSection[] all = chunk.getSections();
                for (int j = 0; j < countY; j++) {
                    int index = chunk.getSectionIndex((minSy + j) << 4);
                    if (index < 0 || index >= all.length) continue;
                    LevelChunkSection section = all[index];
                    if (section == null || section.hasOnlyAir()) continue;
                    sections[(i * countZ + k) * countY + j] = section.getStates().copy();
                }
            }
        }
        int ox = seen.minX(), oy = seen.minY(), oz = seen.minZ();
        return CompletableFuture.supplyAsync(() -> new Copy(ox, oy, oz, StructureBlocks.read(sizeX, sizeY, sizeZ, (x, y, z) -> {
            int wx = ox + x, wy = oy + y, wz = oz + z;
            PalettedContainer<BlockState> states =
                    sections[(((wx >> 4) - minCx) * countZ + ((wz >> 4) - minCz)) * countY + ((wy >> 4) - minSy)];
            return states == null ? AIR : states.get(wx & 15, wy & 15, wz & 15);
        })), WORK).exceptionally(e -> {
            LivingHorizonClient.LOGGER.warn("Could not copy the blocks of a moving build", e);
            return null;
        });
    }

    // --- File --------------------------------------------------------------------------

    /** The builds saved in a file: none when it does not read, then kept aside ({@link JsonStore}). */
    static List<Remembered> read(Path file) {
        JsonElement root = JsonStore.read(file);
        if (root == null) return List.of();
        try {
            JsonStore.Versioned saved = JsonStore.unwrap(root, "structures");
            if (saved == null) throw new IllegalStateException("not a list of structures");
            if (saved.version() > VERSION) JsonStore.keepNewer(file, saved.version());
            return read(saved.data());
        } catch (RuntimeException e) {
            JsonStore.keepBad(file, e);
            return List.of();
        }
    }

    /** The builds of a list as saved, their blocks read back; those missing what they need are left out. */
    static List<Remembered> read(JsonElement list) {
        List<Remembered> read = GSON.fromJson(list, new TypeToken<List<Remembered>>() { }.getType());
        if (read == null) return List.of();
        List<Remembered> kept = new ArrayList<>(read.size());
        for (Remembered structure : read) {
            if (structure == null || structure.id == null || structure.dimension == null) continue;
            structure.blocks = StructureBlocks.load(structure.sizeX, structure.sizeY, structure.sizeZ,
                    structure.palette, structure.runs);
            if (structure.blocks != null && structure.blocks.solid() > 0) kept.add(structure);
        }
        return kept;
    }

    /** Writes the builds as {@link #read(Path)} reads them back. */
    static void write(Collection<Remembered> structures, Writer writer) throws IOException {
        JsonStore.writeVersioned(writer, GSON, VERSION, "structures", new ArrayList<>(structures));
    }

    private void save() {
        if (file == null || !dirty) return;
        dirty = false;
        try {
            List<Remembered> kept = new ArrayList<>();
            for (Remembered structure : structures.values()) if (structure.blocks != null) kept.add(structure);
            Files.createDirectories(file.getParent());
            JsonStore.write(file, writer -> write(kept, writer));
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not write {}", file, e);
        }
    }
}
