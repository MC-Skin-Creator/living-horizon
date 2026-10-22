package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether terrain is drawn under a distant mob, for {@link FarConfig#onlyOverTerrain}: the
 * game's own chunk, or the far world's terrain (Voxy or Distant Horizons). A mob remembered
 * where neither draws anything yet would stand in the void.
 *
 * <p>Chunks the game holds within its render distance are answered at once. The others are
 * asked of the far world a chunk at a time, in one read per frame, off the game's thread:
 * until the answer comes the mob is left out. A chunk found empty is asked again, later and
 * later, since the far world fills in as it loads; one found with terrain stays so until the
 * world changes.
 */
public final class DrawnGround {
    /** The first wait before a chunk found empty is asked again, and the longest. */
    private static final long RETRY_NANOS = 1_000_000_000L, RETRY_MAX_NANOS = 30_000_000_000L;
    private static final int CHUNKS_KEPT = 20_000;

    /** What is known of one chunk; written by the reader threads, read by the game's. */
    private static final class Chunk {
        volatile boolean ground;
        volatile boolean asking;
        volatile long next;
        volatile int misses;
    }

    private static final ConcurrentHashMap<Long, Chunk> CHUNKS = new ConcurrentHashMap<>();
    /** Asked this frame: the chunk and the column of the mob that asked. */
    private static final List<Chunk> ASKED = new ArrayList<>();
    private static int[] askedX = new int[64], askedZ = new int[64];

    private static double farDrawn;
    private static long farDrawnAt = Long.MIN_VALUE;

    private DrawnGround() {
    }

    /** Another world, or none: every chunk is asked again. */
    static void clear() {
        CHUNKS.clear();
    }

    /**
     * Whether terrain is drawn under {@code (x, z)}, seen from {@code eye}: false while the far
     * world has not answered yet. Asks it when needed; {@link #ask()} sends the questions.
     */
    public static boolean under(ClientLevel level, Vec3 eye, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z), cx = bx >> 4, cz = bz >> 4;
        if (gameDraws(level, eye, cx, cz)) return true;
        if (!LodWorld.present()) return false;
        double far = farDrawn();
        double dx = x - eye.x, dz = z - eye.z;
        if (far > 0 && dx * dx + dz * dz > far * far) return false;
        if (CHUNKS.size() > CHUNKS_KEPT) CHUNKS.clear();
        Chunk chunk = CHUNKS.computeIfAbsent((long) cx << 32 | (cz & 0xFFFFFFFFL), key -> new Chunk());
        if (!chunk.ground && !chunk.asking && System.nanoTime() >= chunk.next) {
            chunk.asking = true;
            int i = ASKED.size();
            if (i == askedX.length) {
                askedX = Arrays.copyOf(askedX, i * 2);
                askedZ = Arrays.copyOf(askedZ, i * 2);
            }
            askedX[i] = bx;
            askedZ[i] = bz;
            ASKED.add(chunk);
        }
        return chunk.ground;
    }

    /** Sends the far world this frame's questions, in one read. */
    public static void ask() {
        if (ASKED.isEmpty()) return;
        Chunk[] chunks = ASKED.toArray(new Chunk[0]);
        int[] xs = Arrays.copyOf(askedX, chunks.length), zs = Arrays.copyOf(askedZ, chunks.length);
        ASKED.clear();
        LodWorld.fresh(xs, zs).whenComplete((found, error) -> {
            long now = System.nanoTime();
            for (int i = 0; i < chunks.length; i++) {
                Chunk chunk = chunks[i];
                if (error == null && found != null && found[i] != null) {
                    chunk.ground = true;
                } else {
                    int misses = Math.min(chunk.misses, 5);
                    chunk.next = now + Math.min(RETRY_MAX_NANOS, RETRY_NANOS << misses);
                    chunk.misses = misses + 1;
                }
                chunk.asking = false;
            }
        });
    }

    /**
     * The game draws this chunk: it holds it, and it is within the render distance, measured
     * the way the game picks the sections it draws ({@code ChunkTrackingView.isInViewDistance}).
     */
    private static boolean gameDraws(ClientLevel level, Vec3 eye, int cx, int cz) {
        int distance = Minecraft.getInstance().options.getEffectiveRenderDistance();
        long dx = Math.max(0, Math.abs(cx - (Mth.floor(eye.x) >> 4)) - 1);
        long dz = Math.max(0, Math.abs(cz - (Mth.floor(eye.z) >> 4)) - 1);
        return dx * dx + dz * dz < (long) distance * distance && level.hasChunk(cx, cz);
    }

    /** {@link LodWorld#drawnDistance}, read again once a second: the settings it reads seldom change. */
    private static double farDrawn() {
        long now = System.nanoTime();
        if (farDrawnAt == Long.MIN_VALUE || now - farDrawnAt > 1_000_000_000L) {
            farDrawn = LodWorld.drawnDistance();
            farDrawnAt = now;
        }
        return farDrawn;
    }
}
