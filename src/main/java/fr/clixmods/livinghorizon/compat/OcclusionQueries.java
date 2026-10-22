package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Profile;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.opengl.GLCapabilities;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
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
 * depth, with nothing written: the GPU answers whether a single pixel of it would show. The
 * answers are read a frame or two later, never waited for, and a mob whose last answer is
 * "no pixel" is not drawn.
 *
 * <p>The box is projected by the GPU with the matrices of the frame, exactly as the mob would
 * be, so the answer is the picture's: a mob with one pixel in view is drawn. Depth clamping
 * keeps a box past the far plane at the far plane, where only terrain in front can hide it.
 * A mob never asked about, or whose answer is too old, is drawn.
 *
 * <p>How the GPU answers depends on what it offers. From OpenGL 4.3, every box of the frame
 * goes in one instanced draw: each fragment that passes the depth test writes a word for its
 * box into a buffer, which is copied and read back once a fence says the GPU is done - a
 * dozen calls a frame whatever the number of mobs ({@link #drawWords}). Without it, each box
 * is drawn inside an occlusion query of its own ({@link #drawQueries}), five calls per box.
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
        int inFlight = IN_FLIGHT.size() + WORDS_IN_FLIGHT.size();
        return asked + " asked, " + culled + " hidden, " + inFlight + " frames in flight, "
                + (wordsUsable() ? "one draw" : "one query each")
                + (reason.isEmpty() ? "" : " (" + reason + ")");
    }

    /** How many mobs the last frame hid. */
    public static int culled() {
        return culled;
    }

    // --- On the GPU ---------------------------------------------------------------------------

    /**
     * Draws this frame's boxes against a depth texture holding the terrain and no entity yet,
     * and arranges for the answers to come back. Called just before the entities are drawn.
     */
    static void run(int depthTexture) {
        long started = Profile.begin(Profile.Section.OCCLUSION_RUN);
        try {
            drawBoxes(depthTexture);
        } finally {
            Profile.end(Profile.Section.OCCLUSION_RUN, started);
        }
    }

    private static void drawBoxes(int depthTexture) {
        asked = ASKED.size();
        if (broken || ASKED.isEmpty()) return;
        if (!viewNoted) {
            reason = "no view this frame";
            return;
        }
        if (IN_FLIGHT.size() + WORDS_IN_FLIGHT.size() >= IN_FLIGHT_MAX) {
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
            if (wordsSupport == 0) wordsSupport = GL.getCapabilities().OpenGL43 ? 1 : -1;
            // Where the world is drawn: all of the texture, or its corner under a render scale.
            int pictureWidth = FarDepth.pictureWidth(), pictureHeight = FarDepth.pictureHeight();
            int viewWidth = pictureWidth > 0 ? Math.min(pictureWidth, width) : width;
            int viewHeight = pictureHeight > 0 ? Math.min(pictureHeight, height) : height;
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
                GL11C.glDepthFunc(DepthFar.reversed() ? GL11C.GL_GEQUAL : GL11C.GL_LEQUAL);
                GL11C.glDepthMask(false);
                GL11C.glColorMask(false, false, false, false);
                GL11C.glViewport(0, 0, viewWidth, viewHeight);
                if (wordsUsable()) {
                    try {
                        drawWords();
                    } catch (Throwable e) {
                        // The queries still work: one per box, as before OpenGL 4.3.
                        wordsBroken = true;
                        LivingHorizonClient.LOGGER.warn("Occlusion boxes cannot be drawn in one go: one query per box instead", e);
                    }
                }
                if (!wordsUsable()) drawQueries();
            } finally {
                if (!clamp) GL11C.glDisable(GL32C.GL_DEPTH_CLAMP);
                gl.restore();
            }
        } catch (Throwable e) {
            fail(e);
        } finally {
            ASKED.clear();
            viewNoted = false;
        }
    }

    /** Takes in every batch the GPU has finished, oldest first, without waiting for any. */
    private static void collect() {
        long started = Profile.begin(Profile.Section.OCCLUSION_COLLECT);
        try {
            takeWords();
            takeAnswers();
            if (ANSWERS.size() > 4096) {
                for (Iterator<Answer> it = ANSWERS.values().iterator(); it.hasNext(); ) {
                    if (frame - it.next().frame > MAX_AGE) it.remove();
                }
            }
        } finally {
            Profile.end(Profile.Section.OCCLUSION_COLLECT, started);
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
        if (vertexArray == 0) vertexArray = GL45C.glCreateVertexArrays();
        // Attached again every frame: the copy is made again, often under the same name, when
        // the window is resized (see FarDepth.attachDepth).
        boolean made = framebuffer == 0;
        framebuffer = FarDepth.attachDepth(framebuffer, depthTexture);
        if (made) {
            GL45C.glNamedFramebufferDrawBuffer(framebuffer, GL11C.GL_NONE);
            GL45C.glNamedFramebufferReadBuffer(framebuffer, GL11C.GL_NONE);
        }
        if (framebufferDepth != depthTexture
                && GL45C.glCheckNamedFramebufferStatus(framebuffer, GL30C.GL_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("the occlusion framebuffer is incomplete");
        }
        framebufferDepth = depthTexture;
    }

    // --- One query per box (OpenGL 3.3) --------------------------------------------------------

    /** Draws each box inside an occlusion query of its own: nothing is written but the query's count. */
    private static void drawQueries() {
        if (program == 0) {
            program = compile();
            lowLocation = GL20C.glGetUniformLocation(program, "low");
            highLocation = GL20C.glGetUniformLocation(program, "high");
            matrixLocation = GL20C.glGetUniformLocation(program, "viewProjection");
        }
        // The conservative test is OpenGL 4.3; the game may run on 3.3 (26.x), where it is not to be used.
        GLCapabilities caps = GL.getCapabilities();
        int target = caps.OpenGL43 || caps.GL_ARB_ES3_compatibility
                ? GL43C.GL_ANY_SAMPLES_PASSED_CONSERVATIVE : GL33C.GL_ANY_SAMPLES_PASSED;
        Object[] keys = new Object[ASKED.size()];
        int[] queries = new int[ASKED.size()];
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
        IN_FLIGHT.add(new Batch(keys, queries, frame));
    }

    private static void takeAnswers() {
        while (!IN_FLIGHT.isEmpty()) {
            Batch batch = IN_FLIGHT.peek();
            // Queries finish in the order they were sent: the last one done, the batch is done.
            int last = batch.queries[batch.queries.length - 1];
            if (GL15C.glGetQueryObjecti(last, GL15C.GL_QUERY_RESULT_AVAILABLE) == GL11C.GL_FALSE) return;
            IN_FLIGHT.poll();
            for (int i = 0; i < batch.queries.length; i++) {
                boolean visible = GL15C.glGetQueryObjecti(batch.queries[i], GL15C.GL_QUERY_RESULT) != 0;
                ANSWERS.put(batch.keys[i], new Answer(visible, batch.frame));
                POOL.push(batch.queries[i]);
            }
        }
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
        return link(vertex, fragment, "Occlusion query program");
    }

    // --- One draw for every box (OpenGL 4.3) ---------------------------------------------------

    /**
     * The boxes of one frame drawn at once: the words the fragments wrote, copied into
     * {@code buffer} (one unsigned int per box, 0 until a fragment of it passed), and the
     * fence placed after the copy, which says when the words may be read.
     */
    private static final class Words {
        int buffer;
        /** Bytes the buffer holds. */
        long capacity;
        long sync;
        Object[] keys;
        long frame;
    }

    /** The draw in one go failed, or OpenGL is older than 4.3: one query per box instead. */
    private static boolean wordsBroken;
    /** Whether the context offers OpenGL 4.3, asked once on the GL thread: 0 not yet, 1 yes, -1 no. */
    private static int wordsSupport;
    private static int wordsProgram, wordsMatrixLocation;
    /** The boxes of this frame (two vec4 each) and the words the fragments write (one uint each). */
    private static int boxBuffer, wordBuffer;
    private static long boxCapacity, wordCapacity;
    private static FloatBuffer corners = BufferUtils.createFloatBuffer(8 * 64);
    private static IntBuffer words = BufferUtils.createIntBuffer(64);
    private static final ArrayDeque<Words> WORDS_IN_FLIGHT = new ArrayDeque<>();
    private static final ArrayDeque<Words> WORDS_FREE = new ArrayDeque<>();

    /** Shader storage buffers and early fragment tests, both in OpenGL 4.3. */
    private static boolean wordsUsable() {
        return !wordsBroken && wordsSupport == 1;
    }

    /**
     * Every box in one instanced draw: a fragment that passes the depth test writes 1 for its
     * box, the words are copied to a buffer of this batch, and a fence is placed after the copy.
     * Every binding touched is put back: the game, Sodium and the shader packs keep buffers there.
     */
    private static void drawWords() {
        int n = ASKED.size();
        if (wordsProgram == 0) {
            wordsProgram = compileWords();
            wordsMatrixLocation = GL20C.glGetUniformLocation(wordsProgram, "viewProjection");
            boxBuffer = GL45C.glCreateBuffers();
            wordBuffer = GL45C.glCreateBuffers();
        }
        if (corners.capacity() < 8 * n) corners = BufferUtils.createFloatBuffer(8 * Math.max(n, 2 * corners.capacity() / 8));
        if (words.capacity() < n) words = BufferUtils.createIntBuffer(Math.max(n, 2 * words.capacity()));
        Object[] keys = new Object[n];
        corners.clear();
        for (int i = 0; i < n; i++) {
            Box box = ASKED.get(i);
            keys[i] = box.key;
            corners.put(box.lowX).put(box.lowY).put(box.lowZ).put(0f).put(box.highX).put(box.highY).put(box.highZ).put(0f);
        }
        corners.flip();
        // Streamed: a new store each frame, so the GPU never waits for the last frame's draw.
        GL45C.glNamedBufferData(boxBuffer, corners, GL15C.GL_STREAM_DRAW);
        words.clear();
        for (int i = 0; i < n; i++) words.put(0);
        words.flip();
        GL45C.glNamedBufferData(wordBuffer, words, GL15C.GL_STREAM_COPY);

        Words batch = WORDS_FREE.isEmpty() ? new Words() : WORDS_FREE.pop();
        if (batch.buffer == 0) batch.buffer = GL45C.glCreateBuffers();
        if (batch.capacity < 4L * n) {
            batch.capacity = 4L * Math.max(n, 64);
            GL45C.glNamedBufferData(batch.buffer, batch.capacity, GL15C.GL_STREAM_READ);
        }

        int storage = GL11C.glGetInteger(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING);
        int[] bound = new int[2];
        long[] starts = new long[2], sizes = new long[2];
        for (int i = 0; i < 2; i++) {
            bound[i] = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, i);
            starts[i] = GL32C.glGetInteger64i(GL43C.GL_SHADER_STORAGE_BUFFER_START, i);
            sizes[i] = GL32C.glGetInteger64i(GL43C.GL_SHADER_STORAGE_BUFFER_SIZE, i);
        }
        try {
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, boxBuffer);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 1, wordBuffer);
            GL20C.glUseProgram(wordsProgram);
            GL20C.glUniformMatrix4fv(wordsMatrixLocation, false, VIEW_PROJECTION);
            GL30C.glBindVertexArray(vertexArray);
            GL31C.glDrawArraysInstanced(GL11C.GL_TRIANGLES, 0, 36, n);
            // The fragments' writes are made visible to the copy that follows.
            GL42C.glMemoryBarrier(GL42C.GL_BUFFER_UPDATE_BARRIER_BIT);
            GL45C.glCopyNamedBufferSubData(wordBuffer, batch.buffer, 0, 0, 4L * n);
        } finally {
            for (int i = 0; i < 2; i++) {
                if (bound[i] != 0 && sizes[i] > 0) {
                    GL30C.glBindBufferRange(GL43C.GL_SHADER_STORAGE_BUFFER, i, bound[i], starts[i], sizes[i]);
                } else {
                    GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, i, bound[i]);
                }
            }
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, storage);
        }
        batch.sync = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        batch.keys = keys;
        batch.frame = frame;
        WORDS_IN_FLIGHT.add(batch);
    }

    /** Takes in the words of every batch whose fence has passed, oldest first, without waiting. */
    private static void takeWords() {
        while (!WORDS_IN_FLIGHT.isEmpty()) {
            Words batch = WORDS_IN_FLIGHT.peek();
            if (GL32C.glGetSynci(batch.sync, GL32C.GL_SYNC_STATUS, null) != GL32C.GL_SIGNALED) return;
            WORDS_IN_FLIGHT.poll();
            GL32C.glDeleteSync(batch.sync);
            batch.sync = 0;
            int n = batch.keys.length;
            if (words.capacity() < n) words = BufferUtils.createIntBuffer(n);
            words.clear();
            words.limit(n);
            GL45C.glGetNamedBufferSubData(batch.buffer, 0, words);
            for (int i = 0; i < n; i++) {
                ANSWERS.put(batch.keys[i], new Answer(words.get(i) != 0, batch.frame));
            }
            batch.keys = null;
            WORDS_FREE.push(batch);
        }
    }

    /**
     * The boxes from a buffer, one instance each; a fragment that passes the depth test - the
     * test runs first, so none runs for a hidden pixel - writes its box's word.
     */
    private static int compileWords() {
        String vertex = """
                #version 430 core
                uniform mat4 viewProjection;
                layout(std430, binding = 0) readonly buffer Boxes { vec4 corners[]; };
                const int CORNERS[36] = int[36](
                    0, 2, 6, 0, 6, 4,   1, 5, 7, 1, 7, 3,
                    0, 4, 5, 0, 5, 1,   2, 3, 7, 2, 7, 6,
                    0, 1, 3, 0, 3, 2,   4, 6, 7, 4, 7, 5);
                flat out int box;
                void main() {
                    box = gl_InstanceID;
                    vec3 low = corners[2 * gl_InstanceID].xyz;
                    vec3 high = corners[2 * gl_InstanceID + 1].xyz;
                    int corner = CORNERS[gl_VertexID];
                    vec3 pick = vec3(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1);
                    gl_Position = viewProjection * vec4(mix(low, high, pick), 1.0);
                }
                """;
        String fragment = """
                #version 430 core
                layout(early_fragment_tests) in;
                layout(std430, binding = 1) buffer Words { uint visible[]; };
                flat in int box;
                void main() {
                    visible[box] = 1u;
                }
                """;
        return link(vertex, fragment, "Occlusion words program");
    }

    private static int link(String vertex, String fragment, String what) {
        int vs = FarDepth.shader(GL20C.GL_VERTEX_SHADER, vertex), fs = FarDepth.shader(GL20C.GL_FRAGMENT_SHADER, fragment);
        int linked = GL20C.glCreateProgram();
        GL20C.glAttachShader(linked, vs);
        GL20C.glAttachShader(linked, fs);
        GL20C.glLinkProgram(linked);
        GL20C.glDeleteShader(vs);
        GL20C.glDeleteShader(fs);
        if (GL20C.glGetProgrami(linked, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            String log = GL20C.glGetProgramInfoLog(linked);
            GL20C.glDeleteProgram(linked);
            throw new IllegalStateException(what + ": " + log);
        }
        return linked;
    }
}
