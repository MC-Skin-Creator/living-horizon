package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.opengl.GLCapabilities;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Whether a distant mob is hidden by what stands in front of it, asked of the GPU itself.
 *
 * <p>Just before the entities are drawn - the terrain in the depth buffer, Voxy's or Distant
 * Horizons' merged in, no entity yet - the box around each distant mob is drawn against that
 * depth, with nothing written, inside an occlusion query: the GPU answers whether a single
 * pixel of it would show. The answers are read a frame or two later, never waited for, and a
 * mob whose last answer is "no pixel" is not drawn.
 *
 * <p>The box is projected by the GPU with the matrices of the frame, exactly as the mob would
 * be, so the answer is the picture's: a mob with one pixel in view is drawn. Depth clamping
 * keeps a box past the far plane at the far plane, where only terrain in front can hide it.
 * A mob never asked about, or whose answer is too old, is drawn.
 *
 * <p>Any GL failure turns this off for the session, and the far terrain's world is read instead.
 */
public final class OcclusionQueries {
    /** An answer older than this many frames is not trusted: the mob is drawn. */
    private static final int MAX_AGE = 6;
    /** Batches of queries waiting for the GPU: past this, a frame asks nothing. */
    private static final int IN_FLIGHT_MAX = 4;
    /** Blocks added around a box: the model outgrows its box a little, and answers come late. */
    private static final double MARGIN = 0.25;

    private static boolean broken;
    private static String reason = "no frame yet";

    private static int program, vertexArray, framebuffer, framebufferDepth;
    private static int lowLocation, highLocation, matrixLocation;

    /** This frame's view-projection, noted when the level starts to render. */
    private static final float[] VIEW_PROJECTION = new float[16];
    private static boolean viewNoted;

    /** A box to ask about this frame, relative to the camera. */
    private record Box(Object key, float lowX, float lowY, float lowZ, float highX, float highY, float highZ) {
    }

    /** Queries sent in one frame, in the order of their keys. */
    private record Batch(Object[] keys, int[] queries, long frame) {
    }

    /** The last answer for a mob, and the frame it was asked in. */
    private record Answer(boolean visible, long frame) {
    }

    private static final List<Box> ASKED = new ArrayList<>();
    private static final ArrayDeque<Batch> IN_FLIGHT = new ArrayDeque<>();
    private static final ArrayDeque<Integer> POOL = new ArrayDeque<>();
    private static final Map<Object, Answer> ANSWERS = new HashMap<>();
    private static long frame;
    private static int culled, asked;

    private OcclusionQueries() {
    }

    // --- The frame ----------------------------------------------------------------------------

    /** The matrices of this frame, as the level starts to render. */
    static void noteView(Matrix4f modelView, Matrix4f projection) {
        new Matrix4f(projection).mul(modelView).get(VIEW_PROJECTION);
        viewNoted = true;
    }

    /** Before the distant mobs are looked at: what the GPU answered since comes in. */
    public static void beginFrame() {
        frame++;
        ASKED.clear();
        culled = 0;
        // With Vulkan (26.2 on), there is no OpenGL to ask.
        if (broken || !DepthFar.openGl()) return;
        try {
            collect();
        } catch (Throwable e) {
            fail(e);
        }
    }

    /** Not turned off by a GL failure. */
    public static boolean usable() {
        return !broken;
    }

    // --- Through the game's GPU device ---------------------------------------------------------

    /** The game's device answers instead of OpenGL's queries ({@code FarDepthGpu}, 26.3). */
    private static boolean gpuReady;
    private static long takeCursor;

    public static void gpuReady(boolean ready) {
        gpuReady = ready;
    }

    public static boolean gpuReady() {
        return gpuReady && !broken;
    }

    /**
     * This frame's boxes, at most {@code keys.length}, for the game's device: their keys, and
     * their two corners in {@code boxes}, six floats each. The others are asked again next frame.
     */
    public static int takeAsked(Object[] keys, float[] boxes) {
        int size = ASKED.size();
        asked = size;
        int n = Math.min(keys.length, size);
        // More than fit in one frame: each frame starts where the last one stopped.
        int start = size > n ? (int) (takeCursor % size) : 0;
        takeCursor += n;
        for (int i = 0; i < n; i++) {
            Box box = ASKED.get((start + i) % size);
            keys[i] = box.key;
            boxes[6 * i] = box.lowX;
            boxes[6 * i + 1] = box.lowY;
            boxes[6 * i + 2] = box.lowZ;
            boxes[6 * i + 3] = box.highX;
            boxes[6 * i + 4] = box.highY;
            boxes[6 * i + 5] = box.highZ;
        }
        ASKED.clear();
        return n;
    }

    /** The frame being drawn, to date the answers that come back for it. */
    public static long frame() {
        return frame;
    }

    /** An answer from the game's device about a box asked in {@code askedIn}. */
    public static void answer(Object key, boolean visible, long askedIn) {
        Answer old = ANSWERS.get(key);
        if (old == null || old.frame <= askedIn) ANSWERS.put(key, new Answer(visible, askedIn));
    }

    /** This frame's view-projection, relative to the camera, as the queries use it. */
    public static float[] viewProjection() {
        return VIEW_PROJECTION;
    }

    /** Another world: what was seen is of no use. */
    public static void clear() {
        ANSWERS.clear();
    }

    // --- The test -----------------------------------------------------------------------------

    /**
     * Whether a mob is hidden, by the last answer about it; asks again this frame. The box
     * goes from its feet, at {@code (x, y, z)} relative to the camera, to {@code height}
     * above, {@code half} to each side.
     */
    public static boolean hidden(Object key, double x, double y, double z, double half, double height) {
        if (broken) return false;
        half += MARGIN;
        ASKED.add(new Box(key, (float) (x - half), (float) (y - MARGIN), (float) (z - half),
                (float) (x + half), (float) (y + height + MARGIN), (float) (z + half)));
        Answer answer = ANSWERS.get(key);
        if (answer == null || answer.visible || frame - answer.frame > MAX_AGE) return false;
        culled++;
        return true;
    }

    /** For the debug panel. */
    public static String state() {
        if (broken) return "off: " + reason;
        return asked + " asked, " + culled + " hidden, " + IN_FLIGHT.size() + " frames in flight" +
                (reason.isEmpty() ? "" : " (" + reason + ")");
    }

    /** How many mobs the last frame hid. */
    public static int culled() {
        return culled;
    }

    // --- On the GPU ---------------------------------------------------------------------------

    /**
     * Draws this frame's boxes against a depth texture holding the terrain and no entity yet,
     * each in its own query. Called just before the entities are drawn.
     */
    static void run(int depthTexture) {
        asked = ASKED.size();
        if (broken || ASKED.isEmpty()) return;
        if (!viewNoted) {
            reason = "no view this frame";
            return;
        }
        if (IN_FLIGHT.size() >= IN_FLIGHT_MAX) {
            reason = "the GPU is behind";
            return;
        }
        reason = "";
        try {
            int width = GL45C.glGetTextureLevelParameteri(depthTexture, 0, GL11C.GL_TEXTURE_WIDTH);
            int height = GL45C.glGetTextureLevelParameteri(depthTexture, 0, GL11C.GL_TEXTURE_HEIGHT);
            if (width <= 0 || height <= 0) {
                reason = "the depth texture has no size";
                return;
            }
            prepare(depthTexture);
            // The conservative test is OpenGL 4.3; the game may run on 3.3 (26.x), where it is not to be used.
            GLCapabilities caps = GL.getCapabilities();
            int target = caps.OpenGL43 || caps.GL_ARB_ES3_compatibility
                    ? GL43C.GL_ANY_SAMPLES_PASSED_CONSERVATIVE : GL33C.GL_ANY_SAMPLES_PASSED;
            Object[] keys = new Object[ASKED.size()];
            int[] queries = new int[ASKED.size()];
            FarDepth.GlState gl = FarDepth.GlState.save();
            boolean clamp = GL11C.glIsEnabled(GL32C.GL_DEPTH_CLAMP);
            try {
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glDisable(GL11C.GL_STENCIL_TEST);
                GL11C.glDisable(GL11C.GL_BLEND);
                GL11C.glEnable(GL11C.GL_DEPTH_TEST);
                GL11C.glEnable(GL32C.GL_DEPTH_CLAMP);
                GL11C.glDepthFunc(DepthFar.REVERSED ? GL11C.GL_GEQUAL : GL11C.GL_LEQUAL);
                GL11C.glDepthMask(false);
                GL11C.glColorMask(false, false, false, false);
                GL11C.glViewport(0, 0, width, height);
                GL20C.glUseProgram(program);
                GL20C.glUniformMatrix4fv(matrixLocation, false, VIEW_PROJECTION);
                GL30C.glBindVertexArray(vertexArray);
                for (int i = 0; i < keys.length; i++) {
                    Box box = ASKED.get(i);
                    keys[i] = box.key;
                    queries[i] = POOL.isEmpty() ? GL15C.glGenQueries() : POOL.pop();
                    GL20C.glUniform3f(lowLocation, box.lowX, box.lowY, box.lowZ);
                    GL20C.glUniform3f(highLocation, box.highX, box.highY, box.highZ);
                    GL15C.glBeginQuery(target, queries[i]);
                    GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 36);
                    GL15C.glEndQuery(target);
                }
            } finally {
                if (!clamp) GL11C.glDisable(GL32C.GL_DEPTH_CLAMP);
                gl.restore();
            }
            IN_FLIGHT.add(new Batch(keys, queries, frame));
        } catch (Throwable e) {
            fail(e);
        } finally {
            ASKED.clear();
            viewNoted = false;
        }
    }

    /** Takes in every batch the GPU has finished, oldest first, without waiting for any. */
    private static void collect() {
        while (!IN_FLIGHT.isEmpty()) {
            Batch batch = IN_FLIGHT.peek();
            for (int query : batch.queries) {
                if (GL15C.glGetQueryObjecti(query, GL15C.GL_QUERY_RESULT_AVAILABLE) == GL11C.GL_FALSE) return;
            }
            IN_FLIGHT.poll();
            for (int i = 0; i < batch.queries.length; i++) {
                boolean visible = GL15C.glGetQueryObjecti(batch.queries[i], GL15C.GL_QUERY_RESULT) != 0;
                ANSWERS.put(batch.keys[i], new Answer(visible, batch.frame));
                POOL.push(batch.queries[i]);
            }
        }
        if (ANSWERS.size() > 4096) {
            for (Iterator<Answer> it = ANSWERS.values().iterator(); it.hasNext(); ) {
                if (frame - it.next().frame > MAX_AGE) it.remove();
            }
        }
    }

    private static void fail(Throwable e) {
        broken = true;
        reason = String.valueOf(e);
        ANSWERS.clear();
        LivingHorizonClient.LOGGER.warn("Occlusion queries turned off: distant mobs are tested in the far terrain's world instead", e);
    }

    /** The program, and a framebuffer of our own around the depth texture, with no colour. */
    private static void prepare(int depthTexture) {
        if (program == 0) {
            program = compile();
            vertexArray = GL45C.glCreateVertexArrays();
            lowLocation = GL20C.glGetUniformLocation(program, "low");
            highLocation = GL20C.glGetUniformLocation(program, "high");
            matrixLocation = GL20C.glGetUniformLocation(program, "viewProjection");
        }
        if (framebuffer != 0 && framebufferDepth == depthTexture) return;
        if (framebuffer != 0) GL30C.glDeleteFramebuffers(framebuffer);
        int format = GL45C.glGetTextureLevelParameteri(depthTexture, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
        boolean stencil = format == GL30C.GL_DEPTH24_STENCIL8 || format == GL30C.GL_DEPTH32F_STENCIL8;
        framebuffer = GL45C.glCreateFramebuffers();
        GL45C.glNamedFramebufferTexture(framebuffer,
                stencil ? GL30C.GL_DEPTH_STENCIL_ATTACHMENT : GL30C.GL_DEPTH_ATTACHMENT, depthTexture, 0);
        GL45C.glNamedFramebufferDrawBuffer(framebuffer, GL11C.GL_NONE);
        GL45C.glNamedFramebufferReadBuffer(framebuffer, GL11C.GL_NONE);
        if (GL45C.glCheckNamedFramebufferStatus(framebuffer, GL30C.GL_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("the occlusion framebuffer is incomplete");
        }
        framebufferDepth = depthTexture;
    }

    /** A box from two corners, as twelve triangles; nothing is written but the query's count. */
    private static int compile() {
        String vertex = """
                #version 330 core
                uniform mat4 viewProjection;
                uniform vec3 low;
                uniform vec3 high;
                const int CORNERS[36] = int[36](
                    0, 2, 6, 0, 6, 4,   1, 5, 7, 1, 7, 3,
                    0, 4, 5, 0, 5, 1,   2, 3, 7, 2, 7, 6,
                    0, 1, 3, 0, 3, 2,   4, 6, 7, 4, 7, 5);
                void main() {
                    int corner = CORNERS[gl_VertexID];
                    vec3 pick = vec3(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1);
                    gl_Position = viewProjection * vec4(mix(low, high, pick), 1.0);
                }
                """;
        String fragment = """
                #version 330 core
                void main() {
                }
                """;
        int vs = FarDepth.shader(GL20C.GL_VERTEX_SHADER, vertex), fs = FarDepth.shader(GL20C.GL_FRAGMENT_SHADER, fragment);
        int linked = GL20C.glCreateProgram();
        GL20C.glAttachShader(linked, vs);
        GL20C.glAttachShader(linked, fs);
        GL20C.glLinkProgram(linked);
        GL20C.glDeleteShader(vs);
        GL20C.glDeleteShader(fs);
        if (GL20C.glGetProgrami(linked, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            throw new IllegalStateException("Occlusion query program: " + GL20C.glGetProgramInfoLog(linked));
        }
        return linked;
    }
}
