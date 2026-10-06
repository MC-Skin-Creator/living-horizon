package fr.clixmods.farfarplayer.compat;

import fr.clixmods.farfarplayer.FarConfig;
import fr.clixmods.farfarplayer.FarFarPlayerClient;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.opengl.GlTexture;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Voxy's terrain in the depth buffer while the entities are drawn, and only then: so that
 * a distant mob behind a Voxy tree is hidden by the tree, pixel by pixel, like any mob
 * behind any block.
 *
 * <p>Voxy writes its terrain's depth into the game's after drawing it - unless a shader
 * pack says not to, which Photon does ({@code excludeLodsFromVanillaDepth}): the pack
 * composites Voxy's terrain itself later, and finds it by where the game's depth is empty.
 * Leaving Voxy's depth there for the rest of the frame would break that. So, around the
 * one call that draws the entities:
 *
 * <ol>
 *   <li>before: the depth is saved, then Voxy's own depth blit - the one it runs when a
 *       pack allows it - writes its terrain into it, and that state is saved too;</li>
 *   <li>the entities are drawn, tested against Voxy's terrain like against any block;</li>
 *   <li>after: wherever the depth is still what Voxy wrote, the saved one comes back;
 *       wherever an entity was drawn, its depth stays.</li>
 * </ol>
 *
 * <p>Without a pack, Voxy already writes its depth for good and this does nothing. Voxy
 * has no API for this: its classes are reached by reflection, every GL state touched is
 * put back, and any failure turns this off for the session.
 */
public final class VoxyDepth {
    private static boolean ready, broken;
    private static Method getNullable, getViewport, getDepthTex, transformBlitDepth;
    private static Field pipelineField, dataField, toVanillaField, depthBlitField, fbTranslucentField, textureIdField;
    private static Field projectionField, modelViewField;
    private static Class<?> irisPipeline;

    /** Armed between the entities being submitted and drawn, in the world's main pass. */
    private static boolean armed;
    /** Whether {@link #before()} wrote Voxy's depth, so that {@link #after()} must undo it. */
    private static boolean merged;

    private static int target, depthTexture, width, height, format;
    private static int saved, written, drawn;
    private static int program, vertexArray;
    private static int state;
    /** Why the last frame did nothing, for {@code /farfarplayer voxy}. */
    private static String reason = "no frame yet";

    private VoxyDepth() {
    }

    /** Where Voxy drew its terrain this frame, and over how much of it. */
    private static int voxyTarget, voxyWidth, voxyHeight;
    /** Our own framebuffer around the game's depth, when Voxy's could not be noted. */
    private static int ownTarget, ownTargetDepth;

    /**
     * Voxy is about to draw its terrain into whatever is bound: the framebuffer the
     * entities' depth goes to as well. Nothing is bound between Minecraft's render passes,
     * so this is the moment to note it.
     */
    public static void noteTarget() {
        voxyTarget = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
        voxyWidth = viewport[2];
        voxyHeight = viewport[3];
    }

    /** Something is drawn past the render distance this frame, where Voxy's terrain could hide it. */
    private static boolean needed;

    /**
     * Called for anything drawn past the render distance. Without it the frame is left
     * alone: three copies of a full-screen depth texture are no small cost for nothing.
     */
    public static void needed() {
        needed = true;
    }

    /** The world's entities are submitted: the next drawing of features is theirs. */
    public static void arm() {
        armed = true;
    }

    /** Just before the entities are drawn. */
    public static void before() {
        if (!armed) return;
        merged = false;
        captured = false;
        FarConfig config = FarConfig.get();
        boolean wanted = needed || !config.optLazyVoxyDepth;
        needed = false;
        try {
            if (!wanted) {
                reason = "nothing far to hide this frame";
            } else if (config.enabled && config.voxyOcclusion && available()) {
                merged = merge();
            }
            // The depth view shows the game's depth even when there was nothing to merge.
            if (!merged && config.debugDepthView != 0) captured = capture();
        } catch (Throwable e) {
            fail(e);
        }
        mergedLastFrame = merged;
    }

    /** Just after the entities are drawn. */
    public static void after() {
        if (!armed) return;
        armed = false;
        try {
            if (merged) restore();
            else if (captured) copy(depthTexture, drawn);
        } catch (Throwable e) {
            fail(e);
        }
        merged = false;
        captured = false;
    }

    /** The depth was copied for the debug view alone, without Voxy's terrain. */
    private static boolean captured;
    private static boolean mergedLastFrame;

    /** Whether Voxy's depth was merged in the last frame, for the debug panel. */
    public static boolean mergedLastFrame() {
        return mergedLastFrame;
    }

    /** The game's depth as it is, for the debug view: no Voxy, or nothing to merge. */
    private static boolean capture() {
        target = ownTarget();
        if (target == 0) return false;
        depthTexture = ownTargetDepth;
        int w = GL45C.glGetTextureLevelParameteri(depthTexture, 0, GL11C.GL_TEXTURE_WIDTH);
        int h = GL45C.glGetTextureLevelParameteri(depthTexture, 0, GL11C.GL_TEXTURE_HEIGHT);
        int f = GL45C.glGetTextureLevelParameteri(depthTexture, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
        if (w <= 0 || h <= 0) return false;
        prepare(w, h, f);
        copy(depthTexture, saved);
        copy(depthTexture, written);
        return true;
    }

    // --- Debug view ---------------------------------------------------------------------

    private static int viewProgram, viewTarget, viewColor;

    /**
     * Draws one of the depth copies of the last frame in the bottom right corner of the
     * screen, near white to far black, the sky in dark blue: {@code 1} the game's depth,
     * {@code 2} with Voxy's terrain merged in, {@code 3} after the entities were drawn.
     * Called with the HUD, after the world, before the HUD itself is drawn over it.
     */
    public static void drawDebugView(int mode) {
        if (mode < 1 || mode > 3 || saved == 0 || broken) return;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (!(minecraft.getMainRenderTarget().getColorTexture() instanceof GlTexture color)) return;
            if (viewProgram == 0) viewProgram = compileView();
            if (viewTarget == 0 || viewColor != color.glId()) {
                if (viewTarget != 0) GL30C.glDeleteFramebuffers(viewTarget);
                viewTarget = GL45C.glCreateFramebuffers();
                GL45C.glNamedFramebufferTexture(viewTarget, GL30C.GL_COLOR_ATTACHMENT0, color.glId(), 0);
                viewColor = color.glId();
            }
            int screenW = minecraft.getMainRenderTarget().width, screenH = minecraft.getMainRenderTarget().height;
            int w = screenW * 2 / 5, h = screenH * 2 / 5, x = screenW - w - 8, y = 8;
            GlState gl = GlState.save();
            try {
                GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, viewTarget);
                GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
                GL11C.glDisable(GL11C.GL_CULL_FACE);
                GL11C.glDisable(GL11C.GL_STENCIL_TEST);
                GL11C.glDisable(GL11C.GL_BLEND);
                GL11C.glDisable(GL11C.GL_DEPTH_TEST);
                GL11C.glDepthMask(false);
                GL11C.glColorMask(true, true, true, true);
                GL11C.glViewport(x, y, w, h);
                GL20C.glUseProgram(viewProgram);
                GL20C.glUniform4f(GL20C.glGetUniformLocation(viewProgram, "area"), x, y, w, h);
                GL20C.glUniform2f(GL20C.glGetUniformLocation(viewProgram, "size"), width, height);
                GL20C.glUniform2f(GL20C.glGetUniformLocation(viewProgram, "planes"), 0.05f,
                        minecraft.gameRenderer.getDepthFar());
                GL30C.glBindVertexArray(vertexArray);
                GL33C.glBindSampler(0, 0);
                GL45C.glBindTextureUnit(0, mode == 1 ? saved : mode == 2 ? written : drawn);
                GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            } finally {
                gl.restore();
            }
        } catch (Throwable e) {
            FarFarPlayerClient.LOGGER.warn("Drawing the depth view failed", e);
            FarConfig.get().debugDepthView = 0;
        }
    }

    private static int compileView() {
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
                uniform vec4 area;
                uniform vec2 size;
                uniform vec2 planes;
                out vec4 color;
                void main() {
                    vec2 uv = (gl_FragCoord.xy - area.xy) / area.zw;
                    float d = texelFetch(depth, ivec2(uv * size), 0).r;
                    if (d >= 1.0) {
                        color = vec4(0.08, 0.12, 0.32, 1.0);
                        return;
                    }
                    float near = planes.x, far = planes.y;
                    float z = d * 2.0 - 1.0;
                    float linear = 2.0 * near * far / (far + near - z * (far - near));
                    // Logarithmic: a block away and the horizon both readable.
                    float shade = 1.0 - log(max(linear, near) / near) / log(far / near);
                    color = vec4(vec3(shade), 1.0);
                }
                """;
        int vs = shader(GL20C.GL_VERTEX_SHADER, vertex), fs = shader(GL20C.GL_FRAGMENT_SHADER, fragment);
        int linked = GL20C.glCreateProgram();
        GL20C.glAttachShader(linked, vs);
        GL20C.glAttachShader(linked, fs);
        GL20C.glLinkProgram(linked);
        GL20C.glDeleteShader(vs);
        GL20C.glDeleteShader(fs);
        if (GL20C.glGetProgrami(linked, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            throw new IllegalStateException("Depth view program: " + GL20C.glGetProgramInfoLog(linked));
        }
        int previous = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        GL20C.glUseProgram(linked);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(linked, "depth"), 0);
        GL20C.glUseProgram(previous);
        return linked;
    }

    /** For {@code /farfarplayer voxy}. */
    public static String state() {
        if (!ready) return "not started";
        if (broken) return "off";
        return switch (state) {
            case 1 -> "on, " + width + "x" + height;
            case 2 -> "not needed (Voxy writes its own depth)";
            default -> "waiting: " + reason;
        };
    }

    // --- Before -------------------------------------------------------------------------

    /** A framebuffer of our own around the main render target's depth texture. */
    private static int ownTarget() {
        if (!(Minecraft.getInstance().getMainRenderTarget().getDepthTexture() instanceof GlTexture texture)) return 0;
        int depth = texture.glId();
        if (ownTarget != 0 && ownTargetDepth == depth) return ownTarget;
        if (ownTarget != 0) GL30C.glDeleteFramebuffers(ownTarget);
        int format = GL45C.glGetTextureLevelParameteri(depth, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
        boolean stencil = format == GL30C.GL_DEPTH24_STENCIL8 || format == GL30C.GL_DEPTH32F_STENCIL8;
        ownTarget = GL45C.glCreateFramebuffers();
        GL45C.glNamedFramebufferTexture(ownTarget,
                stencil ? GL30C.GL_DEPTH_STENCIL_ATTACHMENT : GL30C.GL_DEPTH_ATTACHMENT, depth, 0);
        ownTargetDepth = depth;
        voxyWidth = voxyHeight = 0;
        return ownTarget;
    }

    private static boolean skip(String why) {
        reason = why;
        return false;
    }

    private static boolean merge() throws ReflectiveOperationException {
        Object system = getNullable.invoke(null);
        if (system == null) return skip("Voxy is not rendering");
        Object pipeline = pipelineField.get(system);
        if (!irisPipeline.isInstance(pipeline)) {
            state = 2;
            return false;
        }
        if (toVanillaField.getBoolean(dataField.get(pipeline))) {
            state = 2;
            return false;
        }
        Object viewport = getViewport.invoke(system);
        if (viewport == null) return skip("Voxy has no viewport");
        Object voxyDepth = getDepthTex.invoke(fbTranslucentField.get(pipeline));
        int voxyTexture = textureIdField.getInt(voxyDepth);

        // The game's depth texture: the one Voxy drew into, or else the main one.
        target = voxyTarget != 0 ? voxyTarget : ownTarget();
        voxyTarget = 0;
        if (target == 0) return skip("no framebuffer to draw into");
        if (GL45C.glGetNamedFramebufferAttachmentParameteri(target, GL30C.GL_DEPTH_ATTACHMENT,
                GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11C.GL_TEXTURE) {
            return skip("the depth is not a texture");
        }
        depthTexture = GL45C.glGetNamedFramebufferAttachmentParameteri(target, GL30C.GL_DEPTH_ATTACHMENT,
                GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        int w = GL45C.glGetTextureLevelParameteri(depthTexture, 0, GL11C.GL_TEXTURE_WIDTH);
        int h = GL45C.glGetTextureLevelParameteri(depthTexture, 0, GL11C.GL_TEXTURE_HEIGHT);
        int f = GL45C.glGetTextureLevelParameteri(depthTexture, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
        // Voxy's blit reads its picture in screen fractions, so a picture rendered smaller
        // (a pack's render scale) lands where it belongs all the same.
        if (w <= 0 || h <= 0) return skip("the depth texture has no size");
        prepare(w, h, f);

        copy(depthTexture, saved);
        GlState gl = GlState.save();
        try {
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glColorMask(false, false, false, false);
            GL11C.glDepthMask(true);
            // Over the part of the picture Voxy drew to: all of it, or less under a render scale.
            GL11C.glViewport(0, 0, voxyWidth > 0 ? voxyWidth : w, voxyHeight > 0 ? voxyHeight : h);
            Matrix4f transform = new Matrix4f((Matrix4f) projectionField.get(viewport)).mul((Matrix4f) modelViewField.get(viewport));
            transformBlitDepth.invoke(null, depthBlitField.get(pipeline), voxyTexture, target, viewport, transform);
        } finally {
            gl.restore();
        }
        copy(depthTexture, written);
        state = 1;
        return true;
    }

    // --- After --------------------------------------------------------------------------

    private static void restore() {
        copy(depthTexture, drawn);
        GlState gl = GlState.save();
        try {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target);
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            GL11C.glDisable(GL11C.GL_BLEND);
            GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            GL11C.glDepthFunc(GL11C.GL_ALWAYS);
            GL11C.glDepthMask(true);
            GL11C.glColorMask(false, false, false, false);
            GL11C.glViewport(0, 0, width, height);
            GL20C.glUseProgram(program);
            GL30C.glBindVertexArray(vertexArray);
            for (int unit = 0; unit < 3; unit++) {
                GL33C.glBindSampler(unit, 0);
                GL45C.glBindTextureUnit(unit, unit == 0 ? saved : unit == 1 ? written : drawn);
            }
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
        } finally {
            gl.restore();
        }
    }

    // --- GPU objects ----------------------------------------------------------------------

    private static void copy(int from, int to) {
        GL43C.glCopyImageSubData(from, GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, to, GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, width, height, 1);
    }

    private static void prepare(int w, int h, int f) {
        if (program == 0) {
            program = compile();
            vertexArray = GL45C.glCreateVertexArrays();
        }
        if (saved != 0 && w == width && h == height && f == format) return;
        for (int texture : new int[]{saved, written, drawn}) if (texture != 0) GL11C.glDeleteTextures(texture);
        width = w;
        height = h;
        format = f;
        saved = texture();
        written = texture();
        drawn = texture();
    }

    private static int texture() {
        int texture = GL45C.glCreateTextures(GL11C.GL_TEXTURE_2D);
        GL45C.glTextureStorage2D(texture, 1, format, width, height);
        GL45C.glTextureParameteri(texture, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL45C.glTextureParameteri(texture, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL45C.glTextureParameteri(texture, GL14C.GL_TEXTURE_COMPARE_MODE, GL11C.GL_NONE);
        GL45C.glTextureParameteri(texture, GL43C.GL_DEPTH_STENCIL_TEXTURE_MODE, GL11C.GL_DEPTH_COMPONENT);
        return texture;
    }

    /** A screen-filling triangle that puts back the saved depth wherever no entity was drawn. */
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
                uniform sampler2D saved;
                uniform sampler2D written;
                uniform sampler2D drawn;
                void main() {
                    ivec2 at = ivec2(gl_FragCoord.xy);
                    float now = texelFetch(drawn, at, 0).r;
                    // Still what Voxy wrote: no entity here, give the depth back as it was.
                    gl_FragDepth = now == texelFetch(written, at, 0).r ? texelFetch(saved, at, 0).r : now;
                }
                """;
        int vs = shader(GL20C.GL_VERTEX_SHADER, vertex), fs = shader(GL20C.GL_FRAGMENT_SHADER, fragment);
        int linked = GL20C.glCreateProgram();
        GL20C.glAttachShader(linked, vs);
        GL20C.glAttachShader(linked, fs);
        GL20C.glLinkProgram(linked);
        GL20C.glDeleteShader(vs);
        GL20C.glDeleteShader(fs);
        if (GL20C.glGetProgrami(linked, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
            throw new IllegalStateException("Depth restore program: " + GL20C.glGetProgramInfoLog(linked));
        }
        int previous = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        GL20C.glUseProgram(linked);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(linked, "saved"), 0);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(linked, "written"), 1);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(linked, "drawn"), 2);
        GL20C.glUseProgram(previous);
        return linked;
    }

    private static int shader(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            throw new IllegalStateException("Depth restore shader: " + GL20C.glGetShaderInfoLog(shader));
        }
        return shader;
    }

    /** Every piece of GL state touched here, as it was, so that the game, Iris and Voxy find it again. */
    private record GlState(int program, int vertexArray, int drawFramebuffer, int readFramebuffer, int activeTexture, int[] textures,
                           int[] samplers, boolean depthTest, boolean stencilTest, boolean scissor, boolean cull,
                           boolean blend, int depthFunc, boolean depthMask, boolean[] colorMask, int[] viewport) {
        static GlState save() {
            int active = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
            int[] textures = new int[3], samplers = new int[3];
            for (int unit = 0; unit < 3; unit++) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
                textures[unit] = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
                samplers[unit] = GL11C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
            }
            GL13C.glActiveTexture(active);
            byte[] mask = new byte[4];
            java.nio.ByteBuffer maskBuffer = org.lwjgl.BufferUtils.createByteBuffer(4);
            GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, maskBuffer);
            maskBuffer.get(mask);
            int[] viewport = new int[4];
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
            return new GlState(GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM),
                    GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING),
                    GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING), active, textures, samplers,
                    GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST), GL11C.glIsEnabled(GL11C.GL_STENCIL_TEST),
                    GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST), GL11C.glIsEnabled(GL11C.GL_CULL_FACE),
                    GL11C.glIsEnabled(GL11C.GL_BLEND), GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC),
                    GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK),
                    new boolean[]{mask[0] != 0, mask[1] != 0, mask[2] != 0, mask[3] != 0}, viewport);
        }

        void restore() {
            GL20C.glUseProgram(program);
            GL30C.glBindVertexArray(vertexArray);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFramebuffer);
            for (int unit = 0; unit < 3; unit++) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0 + unit);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textures[unit]);
                GL33C.glBindSampler(unit, samplers[unit]);
            }
            GL13C.glActiveTexture(activeTexture);
            toggle(GL11C.GL_DEPTH_TEST, depthTest);
            toggle(GL11C.GL_STENCIL_TEST, stencilTest);
            toggle(GL11C.GL_SCISSOR_TEST, scissor);
            toggle(GL11C.GL_CULL_FACE, cull);
            toggle(GL11C.GL_BLEND, blend);
            GL11C.glDepthFunc(depthFunc);
            GL11C.glDepthMask(depthMask);
            GL11C.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            GL11C.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }

        private static void toggle(int capability, boolean on) {
            if (on) GL11C.glEnable(capability);
            else GL11C.glDisable(capability);
        }
    }

    // --- Voxy ---------------------------------------------------------------------------

    private static boolean available() {
        if (broken) return false;
        if (!ready) {
            ready = true;
            if (!FabricLoader.getInstance().isModLoaded("voxy")) {
                broken = true;
                return false;
            }
            try {
                link();
            } catch (Throwable e) {
                broken = true;
                FarFarPlayerClient.LOGGER.warn("Voxy found, but its depth cannot be used: distant mobs show through its terrain", e);
                return false;
            }
        }
        return true;
    }

    private static void fail(Throwable e) {
        broken = true;
        merged = false;
        FarFarPlayerClient.LOGGER.warn("Using Voxy's depth failed: distant mobs show through its terrain", e);
    }

    private static void link() throws ReflectiveOperationException {
        Class<?> getter = Class.forName("me.cortex.voxy.client.core.IGetVoxyRenderSystem");
        Class<?> system = Class.forName("me.cortex.voxy.client.core.VoxyRenderSystem");
        Class<?> pipeline = Class.forName("me.cortex.voxy.client.core.AbstractRenderPipeline");
        Class<?> depthBuffer = Class.forName("me.cortex.voxy.client.core.rendering.util.DepthFramebuffer");
        Class<?> glTexture = Class.forName("me.cortex.voxy.client.core.gl.GlTexture");
        Class<?> viewport = Class.forName("me.cortex.voxy.client.core.rendering.Viewport");
        Class<?> blit = Class.forName("me.cortex.voxy.client.core.rendering.post.FullscreenBlit");
        Class<?> data = Class.forName("me.cortex.voxy.client.iris.IrisVoxyRenderPipelineData");
        irisPipeline = Class.forName("me.cortex.voxy.client.core.IrisVoxyRenderPipeline");
        getNullable = getter.getMethod("getNullable");
        getViewport = system.getMethod("getViewport");
        pipelineField = system.getDeclaredField("pipeline");
        pipelineField.setAccessible(true);
        dataField = irisPipeline.getDeclaredField("data");
        dataField.setAccessible(true);
        toVanillaField = data.getField("renderToVanillaDepth");
        depthBlitField = irisPipeline.getDeclaredField("depthBlit");
        depthBlitField.setAccessible(true);
        fbTranslucentField = irisPipeline.getField("fbTranslucent");
        getDepthTex = depthBuffer.getMethod("getDepthTex");
        textureIdField = glTexture.getField("id");
        projectionField = viewport.getField("vanillaProjection");
        modelViewField = viewport.getField("modelView");
        transformBlitDepth = pipeline.getDeclaredMethod("transformBlitDepth", blit, int.class, int.class, viewport, Matrix4f.class);
        transformBlitDepth.setAccessible(true);
    }
}
