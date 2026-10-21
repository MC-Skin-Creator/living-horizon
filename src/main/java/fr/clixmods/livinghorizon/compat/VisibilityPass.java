package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.render.DepthFar;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL45C;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Which distant mobs the depth hides, every mob in one draw, with OpenGL: what
 * {@code FarDepthGpu} does through the game's device on 26.3 ({@code far_visibility.fsh}), in
 * place of one occlusion query per mob.
 *
 * <p>The boxes go into a buffer texture. One draw into a row of pixels, one pixel per mob:
 * the box is projected on the picture, and the depth under it - every pixel of a small box, a
 * grid of 16 by 16 of a large one - is tested against the box's nearest point. The row is read
 * into a pixel buffer behind a fence, and taken a frame or two later, never waited for.
 *
 * <p>Any GL failure turns this off for the session: the occlusion queries answer instead.
 */
final class VisibilityPass {
    /** Mobs tested per frame, one pixel each. The others wait a frame. */
    static final int MAX = 512;
    /** Read-backs waiting for the GPU: past this, a frame asks nothing. */
    private static final int SLOTS = 4;
    /** Frames after which a read-back the GPU never finished is given up. */
    private static final int GIVE_UP = 60;

    private static boolean broken;
    private static String reason = "";

    private static int program, vertexArray, target, framebuffer, boxBuffer, boxTexture;
    private static int matrixLocation, pictureLocation, reversedLocation, zeroToOneLocation;

    /** One read-back per frame in flight: the pixel buffer, its fence, the keys it answers for and their frame. */
    private static final int[] PIXELS = new int[SLOTS];
    private static final long[] FENCES = new long[SLOTS];
    private static final Object[][] SLOT_KEYS = new Object[SLOTS][];
    private static final long[] SLOT_FRAMES = new long[SLOTS];

    private static final Object[] KEYS = new Object[MAX];
    private static final float[] BOXES = new float[MAX * 6];
    private static final FloatBuffer UPLOAD = BufferUtils.createFloatBuffer(MAX * 8);
    private static final ByteBuffer READ = BufferUtils.createByteBuffer(MAX * 4);

    private VisibilityPass() {
    }

    /** Not turned off by a GL failure. */
    static boolean usable() {
        return !broken;
    }

    /** Read-backs the GPU has not finished yet. */
    static int inFlight() {
        int n = 0;
        for (long fence : FENCES) {
            if (fence != 0) n++;
        }
        return n;
    }

    /** For the debug panel: why the last frame asked nothing, or why this is off. */
    static String reason() {
        return reason;
    }

    /**
     * Tests this frame's boxes against a depth texture holding the terrain and no entity yet.
     * The world is drawn in its bottom left {@code pictureWidth} by {@code pictureHeight} pixels.
     */
    static void run(int depthTexture, int pictureWidth, int pictureHeight, float[] viewProjection, long frame) {
        if (broken) return;
        try {
            int slot = -1;
            for (int i = 0; i < SLOTS; i++) {
                if (FENCES[i] == 0) {
                    slot = i;
                    break;
                }
            }
            if (slot < 0) {
                reason = "the GPU is behind";
                return;
            }
            reason = "";
            int n = OcclusionQueries.takeAsked(KEYS, BOXES);
            if (n == 0) return;
            prepare();
            UPLOAD.clear();
            for (int i = 0; i < n; i++) {
                UPLOAD.put(BOXES, 6 * i, 3).put(0f).put(BOXES, 6 * i + 3, 3).put(0f);
            }
            UPLOAD.flip();
            // A new store each frame: the GPU may still read last frame's.
            GL45C.glNamedBufferData(boxBuffer, (long) MAX * 32, GL15C.GL_STREAM_DRAW);
            GL45C.glNamedBufferSubData(boxBuffer, 0, UPLOAD);
            draw(slot, n, depthTexture, pictureWidth, pictureHeight, viewProjection);
            Object[] keys = new Object[n];
            System.arraycopy(KEYS, 0, keys, 0, n);
            SLOT_KEYS[slot] = keys;
            SLOT_FRAMES[slot] = frame;
        } catch (Throwable e) {
            fail(e);
        }
    }

    private static void draw(int slot, int n, int depthTexture, int pictureWidth, int pictureHeight, float[] viewProjection) {
        FarDepth.GlState gl = FarDepth.GlState.save();
        // Not in the saved state: the buffer texture of unit 1, the pixel buffer, how pixels are packed.
        GL13C.glActiveTexture(GL13C.GL_TEXTURE1);
        int bufferTexture = GL11C.glGetInteger(GL31C.GL_TEXTURE_BINDING_BUFFER);
        int pack = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        int rowLength = GL11C.glGetInteger(GL11C.GL_PACK_ROW_LENGTH);
        int skipPixels = GL11C.glGetInteger(GL11C.GL_PACK_SKIP_PIXELS);
        int skipRows = GL11C.glGetInteger(GL11C.GL_PACK_SKIP_ROWS);
        try {
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, framebuffer);
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            GL11C.glDisable(GL11C.GL_BLEND);
            GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            GL11C.glColorMask(true, true, true, true);
            GL11C.glViewport(0, 0, n, 1);
            GL20C.glUseProgram(program);
            GL20C.glUniformMatrix4fv(matrixLocation, false, viewProjection);
            GL20C.glUniform2i(pictureLocation, pictureWidth, pictureHeight);
            GL20C.glUniform1i(reversedLocation, DepthFar.reversed() ? 1 : 0);
            GL20C.glUniform1i(zeroToOneLocation, DepthFar.zeroToOne() ? 1 : 0);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, depthTexture);
            GL33C.glBindSampler(0, 0);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE1);
            GL11C.glBindTexture(GL31C.GL_TEXTURE_BUFFER, boxTexture);
            GL33C.glBindSampler(1, 0);
            GL30C.glBindVertexArray(vertexArray);
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, PIXELS[slot]);
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, 0);
            GL11C.glReadPixels(0, 0, n, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, 0L);
            FENCES[slot] = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        } finally {
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, rowLength);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, skipPixels);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, skipRows);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, pack);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE1);
            GL11C.glBindTexture(GL31C.GL_TEXTURE_BUFFER, bufferTexture);
            gl.restore();
        }
    }

    /** Takes in every read-back the GPU has finished, without waiting for any. */
    static void collect(long frame) {
        if (broken) return;
        try {
            for (int slot = 0; slot < SLOTS; slot++) {
                long fence = FENCES[slot];
                if (fence == 0) continue;
                boolean done = GL32C.glGetSynci(fence, GL32C.GL_SYNC_STATUS, null) == GL32C.GL_SIGNALED;
                if (!done && frame - SLOT_FRAMES[slot] <= GIVE_UP) continue;
                GL32C.glDeleteSync(fence);
                FENCES[slot] = 0;
                Object[] keys = SLOT_KEYS[slot];
                SLOT_KEYS[slot] = null;
                if (!done || keys == null) continue;
                READ.clear().limit(keys.length * 4);
                GL45C.glGetNamedBufferSubData(PIXELS[slot], 0, READ);
                for (int i = 0; i < keys.length; i++) {
                    OcclusionQueries.answer(keys[i], (READ.get(4 * i) & 0xFF) >= 128, SLOT_FRAMES[slot]);
                }
            }
        } catch (Throwable e) {
            fail(e);
        }
    }

    private static void fail(Throwable e) {
        broken = true;
        reason = String.valueOf(e);
        for (int slot = 0; slot < SLOTS; slot++) {
            SLOT_KEYS[slot] = null;
            try {
                if (FENCES[slot] != 0) GL32C.glDeleteSync(FENCES[slot]);
            } catch (Throwable ignored) {
                // Already failing: the fence is only memory.
            }
            FENCES[slot] = 0;
        }
        LivingHorizonClient.LOGGER.warn("Testing distant mobs in one draw failed: one occlusion query per mob instead", e);
    }

    /** The program, the boxes' buffer texture, the row of pixels and its read-back buffers. */
    private static void prepare() {
        if (program != 0) return;
        program = compile();
        matrixLocation = GL20C.glGetUniformLocation(program, "viewProjection");
        pictureLocation = GL20C.glGetUniformLocation(program, "picture");
        reversedLocation = GL20C.glGetUniformLocation(program, "reversed");
        zeroToOneLocation = GL20C.glGetUniformLocation(program, "zeroToOne");
        int previous = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        GL20C.glUseProgram(program);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(program, "depth"), 0);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(program, "boxes"), 1);
        GL20C.glUseProgram(previous);
        vertexArray = GL45C.glCreateVertexArrays();

        boxBuffer = GL45C.glCreateBuffers();
        GL45C.glNamedBufferData(boxBuffer, (long) MAX * 32, GL15C.GL_STREAM_DRAW);
        boxTexture = GL45C.glCreateTextures(GL31C.GL_TEXTURE_BUFFER);
        GL45C.glTextureBuffer(boxTexture, GL30C.GL_RGBA32F, boxBuffer);

        target = GL45C.glCreateTextures(GL11C.GL_TEXTURE_2D);
        GL45C.glTextureStorage2D(target, 1, GL11C.GL_RGBA8, MAX, 1);
        framebuffer = GL45C.glCreateFramebuffers();
        GL45C.glNamedFramebufferTexture(framebuffer, GL30C.GL_COLOR_ATTACHMENT0, target, 0);
        GL45C.glNamedFramebufferDrawBuffer(framebuffer, GL30C.GL_COLOR_ATTACHMENT0);
        GL45C.glNamedFramebufferReadBuffer(framebuffer, GL30C.GL_COLOR_ATTACHMENT0);
        if (GL45C.glCheckNamedFramebufferStatus(framebuffer, GL30C.GL_FRAMEBUFFER) != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("the visibility framebuffer is incomplete");
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            PIXELS[slot] = GL45C.glCreateBuffers();
            GL45C.glNamedBufferData(PIXELS[slot], (long) MAX * 4, GL15C.GL_STREAM_READ);
        }
    }

    /**
     * One pixel per mob: white if one pixel of the depth under its box is not in front of the
     * box's nearest point, black if all are. The same test as {@code far_visibility.fsh}, every
     * pixel of a box up to 16 pixels wide and the picture's render scale taken into account.
     */
    private static int compile() {
        String vertex = """
                #version 330 core
                void main() {
                    vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
                }
                """;
        String fragment = """
                #version 330 core
                uniform sampler2D depth;
                // Each box's lowest then highest corner, relative to the camera.
                uniform samplerBuffer boxes;
                // From the world, relative to the camera, to the screen.
                uniform mat4 viewProjection;
                // Where the world is drawn in the depth: all of it, or its corner under a render scale.
                uniform ivec2 picture;
                uniform int reversed;
                uniform int zeroToOne;
                layout(location = 0) out vec4 color;
                const int STEPS = 16;

                void main() {
                    int i = int(gl_FragCoord.x);
                    vec3 low = texelFetch(boxes, 2 * i).xyz, high = texelFetch(boxes, 2 * i + 1).xyz;
                    vec2 lowest = vec2(1.0e9), highest = vec2(-1.0e9);
                    float nearest = reversed != 0 ? 0.0 : 1.0;
                    for (int c = 0; c < 8; c++) {
                        vec3 corner = vec3((c & 1) != 0 ? high.x : low.x, (c & 2) != 0 ? high.y : low.y,
                                (c & 4) != 0 ? high.z : low.z);
                        vec4 clip = viewProjection * vec4(corner, 1.0);
                        // A corner behind the camera: the box reaches the eye, it is shown.
                        if (clip.w <= 0.0) {
                            color = vec4(1.0);
                            return;
                        }
                        vec3 ndc = clip.xyz / clip.w;
                        vec2 at = (ndc.xy * 0.5 + 0.5) * vec2(picture);
                        lowest = min(lowest, at);
                        highest = max(highest, at);
                        // Clamped as the depth clamp of the queries did: past the far plane is at the far plane.
                        float d = clamp(zeroToOne != 0 ? ndc.z : ndc.z * 0.5 + 0.5, 0.0, 1.0);
                        nearest = reversed != 0 ? max(nearest, d) : min(nearest, d);
                    }
                    lowest = clamp(lowest, vec2(0.0), vec2(picture));
                    highest = clamp(highest, vec2(0.0), vec2(picture));
                    // Off the picture: left to the view's own culling.
                    if (highest.x <= lowest.x || highest.y <= lowest.y) {
                        color = vec4(1.0);
                        return;
                    }
                    ivec2 first = min(ivec2(floor(lowest)), picture - 1);
                    ivec2 last = max(min(ivec2(ceil(highest)), picture) - 1, first);
                    ivec2 span = last - first + 1;
                    ivec2 steps = min(span, ivec2(STEPS));
                    for (int y = 0; y < steps.y; y++) {
                        for (int x = 0; x < steps.x; x++) {
                            // Every pixel when the box is small, else evenly spread over it.
                            ivec2 at = first + ((2 * ivec2(x, y) + 1) * span) / (2 * steps);
                            float scene = texelFetch(depth, at, 0).r;
                            if (reversed != 0 ? scene <= nearest : scene >= nearest) {
                                color = vec4(1.0);
                                return;
                            }
                        }
                    }
                    color = vec4(0.0, 0.0, 0.0, 1.0);
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
            throw new IllegalStateException("Visibility program: " + GL20C.glGetProgramInfoLog(linked));
        }
        return linked;
    }
}
