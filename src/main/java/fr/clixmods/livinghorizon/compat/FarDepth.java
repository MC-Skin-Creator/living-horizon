package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
//? if >=1.21.5
import com.mojang.blaze3d.opengl.GlTexture;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
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
 * behind any block - and not by Voxy's water, which the eye sees through.
 *
 * <p>Voxy writes its terrain's depth into the game's after drawing it - unless a shader
 * pack says not to, which Photon does ({@code excludeLodsFromVanillaDepth}): the pack
 * composites Voxy's terrain itself later, and finds it by where the game's depth is empty.
 * Leaving Voxy's depth there for the rest of the frame would break that. So, around the
 * one call that draws the entities:
 *
 * <ol>
 *   <li>before: the depth is saved, then Voxy's own depth blit - the one it runs when a
 *       pack allows it - writes its opaque terrain into it, and that state is saved too;</li>
 *   <li>the entities are drawn, tested against Voxy's terrain like against any block;</li>
 *   <li>after: wherever the depth is still what Voxy wrote, the saved one comes back;
 *       wherever an entity was drawn, its depth stays.</li>
 * </ol>
 *
 * <p>When Voxy writes its depth for good - without a pack, or when the pack allows it - that
 * depth holds its water surfaces too: the game draws its own water after the entities, Voxy
 * draws its own before them. A mob swimming in a far river would lose all that is under the
 * surface, where the game shows it through the water. So the game's depth is copied just
 * before Voxy draws, and Voxy's opaque depth just before it draws its water; around the
 * entities, the first is put back with the second blitted over it, and once they are drawn,
 * what of them is under Voxy's water is tinted with the colour that was there - the water.
 *
 * <p>Voxy has no API for this: its classes are reached by reflection, every GL state touched
 * is put back, and any failure turns this off for the session.
 */
public final class FarDepth {
    private static boolean ready, broken;
    /** Voxy is absent or not usable: Distant Horizons is tried instead. */
    private static boolean voxyOff;
    private static Method getNullable, getViewport, getDepthTex, transformBlitDepth;
    private static Field pipelineField, dataField, toVanillaField, depthBlitField, textureIdField;
    private static Field projectionField, modelViewField, fbField;
    private static Class<?> irisPipeline;
    /** Voxy's pipeline without a pack; null if this Voxy has none we know. */
    private static @Nullable Class<?> normalPipeline;
    private static Field finalBlitField, colourField;

    /** Armed between the entities being submitted and drawn, in the world's main pass. */
    private static boolean armed;
    /** Whether {@link #before()} wrote Voxy's depth, so that {@link #after()} must undo it. */
    private static boolean merged;

    private static int target, depthTexture, width, height, format;
    private static int saved, written, drawn, faded;
    private static int program, vertexArray;
    /** How much of what is under Voxy's water the water covers, as the game's water does. */
    private static final float WATER = 0.6f;
    private static int tintProgram;
    private static int state;
    /** Why the last frame did nothing, for {@code /livinghorizon voxy}. */
    private static String reason = "no frame yet";

    private FarDepth() {
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
        if (!DepthFar.openGl()) return;
        voxyTarget = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
        voxyWidth = viewport[2];
        voxyHeight = viewport[3];
    }

    /** Whether the next frame copies the depth around Voxy's drawing: the last one needed it. */
    private static boolean copyNext;
    /** Whether Voxy kept its depth in the game's, water included, the last time it was asked. */
    private static boolean voxyKeeps = true;
    /** The game's depth just before Voxy drew this frame, and the texture it was copied from. */
    private static int below, belowFrom;
    /** Voxy's depth before its water this frame, without a pack, at Voxy's own size and format. */
    private static int opaque, opaqueWidth, opaqueHeight, opaqueFormat;
    private static boolean opaqueCopied;
    /** The colour just before the entities, when there is water to draw over them. */
    private static int colour, colourFormat, colourTarget;

    /**
     * Voxy is about to draw its terrain over the game's depth: the moment to copy that depth,
     * for the water Voxy will write into it to be taken back out around the entities.
     */
    public static void noteBelow(int sourceDepth) {
        belowFrom = 0;
        opaqueCopied = false;
        if (!copyNext || broken || sourceDepth == 0 || !DepthFar.openGl()) return;
        try {
            int w = GL45C.glGetTextureLevelParameteri(sourceDepth, 0, GL11C.GL_TEXTURE_WIDTH);
            int h = GL45C.glGetTextureLevelParameteri(sourceDepth, 0, GL11C.GL_TEXTURE_HEIGHT);
            int f = GL45C.glGetTextureLevelParameteri(sourceDepth, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
            if (w <= 0 || h <= 0) return;
            prepare(w, h, f);
            copy(sourceDepth, below);
            belowFrom = sourceDepth;
        } catch (Throwable e) {
            fail(e);
        }
    }

    /**
     * Voxy, without a pack, is about to draw its water into the depth its opaque terrain is
     * in: the moment to copy that depth, the one the entities are tested against.
     */
    public static void noteOpaque(Object pipeline) {
        if (!copyNext || broken || opaqueCopied || belowFrom == 0 || !available()) return;
        try {
            int depth = textureIdField.getInt(getDepthTex.invoke(fbField.get(pipeline)));
            int w = GL45C.glGetTextureLevelParameteri(depth, 0, GL11C.GL_TEXTURE_WIDTH);
            int h = GL45C.glGetTextureLevelParameteri(depth, 0, GL11C.GL_TEXTURE_HEIGHT);
            int f = GL45C.glGetTextureLevelParameteri(depth, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
            if (w <= 0 || h <= 0) return;
            if (opaque == 0 || w != opaqueWidth || h != opaqueHeight || f != opaqueFormat) {
                if (opaque != 0) GL11C.glDeleteTextures(opaque);
                opaqueWidth = w;
                opaqueHeight = h;
                opaqueFormat = f;
                opaque = texture(f, w, h);
            }
            GL43C.glCopyImageSubData(depth, GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, opaque, GL11C.GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
            opaqueCopied = true;
        } catch (Throwable e) {
            fail(e);
        }
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
        fadeMask = false;
        gpuMerge = false;
        gpuFade = false;
    }

    // --- Without OpenGL ---------------------------------------------------------------------

    /**
     * What {@code FarDepthGpu} is to do this frame when the game draws with Vulkan, where
     * nothing here may talk to OpenGL: merge Distant Horizons' depth before the entities, keep
     * the figures out of its fade. It draws through the game's own GPU device instead.
     */
    private static boolean gpuMerge, gpuFade;
    /** This frame's view-projection, relative to the camera. */
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    /** {@code FarDepthGpu} exists (26.3) and has run. */
    private static boolean gpuAvailable;

    public static void gpuAvailable() {
        gpuAvailable = true;
    }

    /**
     * Whether the far terrain's depth goes through the game's own GPU device this frame rather
     * than straight OpenGL: always with Vulkan; with OpenGL too over Distant Horizons without a
     * shader pack, where OpenGL calls in the middle of the world's pass left smears at the edge
     * of the game's chunks.
     */
    public static boolean gpuPath() {
        if (!DepthFar.openGl()) return true;
        return gpuAvailable && !available() && DhDepth.available() && !DhDepth.shaderPackOn();
    }

    public static boolean gpuMerge() {
        return gpuMerge;
    }

    public static boolean gpuFade() {
        return gpuFade;
    }

    public static Matrix4f viewProjection() {
        return VIEW_PROJECTION;
    }

    /** What the GPU path did, for {@code /livinghorizon lod} and the debug panel. */
    public static void gpuResult(boolean done, String why, int w, int h) {
        mergedLastFrame = done;
        if (done) {
            state = 1;
            width = w;
            height = h;
        } else {
            state = 0;
            reason = why;
        }
    }

    /** Just before the entities are drawn. */
    public static void before() {
        if (!armed) return;
        merged = false;
        captured = false;
        FarConfig config = FarConfig.get();
        boolean wanted = needed || !config.optLazyDepth;
        needed = false;
        copyNext = wanted && config.anyDistant() && config.depthOcclusion && voxyKeeps;
        try {
            if (gpuPath()) {
                // Distant Horizons alone: Voxy draws with OpenGL only. No shader pack either.
                boolean dh = wanted && config.anyDistant() && DhDepth.available() && !DhDepth.shaderPackOn();
                gpuMerge = dh && config.depthOcclusion;
                gpuFade = dh && config.optDhFade;
                if (!gpuMerge) {
                    reason = wanted ? "the far terrain's depth is not usable" : "nothing far to hide this frame";
                    mergedLastFrame = false;
                }
                return;
            } else if (!wanted) {
                reason = "nothing far to hide this frame";
            } else if (config.anyDistant() && config.depthOcclusion) {
                if (available()) merged = merge();
                else if (!broken && DhDepth.available()) merged = mergeDh();
                // Why Distant Horizons is of no use (too old, most often), not what an earlier frame said.
                else reason = Platform.isModLoaded("distanthorizons") ? DhDepth.reason() : "neither Voxy nor Distant Horizons is usable";
            }
            // Distant Horizons' fade, without a shader pack, needs to know where the entities are.
            boolean dhFade = wanted && config.anyDistant() && config.optDhFade && DhDepth.available()
                    && !DhDepth.shaderPackOn();
            boolean queries = wanted && config.anyDistant() && config.hideOccludedMobs && config.optOcclusionQueries;
            // The depth view shows the game's depth even when there was nothing to merge.
            if (!merged && (config.debugDepthView != 0 || dhFade || queries)) captured = capture();
            fadeMask = dhFade && (merged || captured) && target == ownTarget;
            // The depth as it is now - far terrain in it, entities not yet - is what hides distant mobs.
            // Tested on its copy: the game's own texture, which Distant Horizons reads later in the
            // frame, is never attached anywhere else in the middle of the frame.
            if (queries && (merged || captured)) OcclusionQueries.run(written);
        } catch (Throwable e) {
            fail(e);
        }
        mergedLastFrame = merged;
    }

    /** The matrices of this frame, for {@link OcclusionQueries}. */
    public static void noteView(Matrix4f modelView, Matrix4f projection) {
        VIEW_PROJECTION.set(projection).mul(modelView);
        OcclusionQueries.noteView(modelView, projection);
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

    /** The depth was copied without the far terrain's: for the debug view, or for Distant Horizons' fade. */
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
        if (mode < 1 || mode > 3 || saved == 0 || !DepthFar.openGl() || gpuPath()) return;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            int colorId = mainTexture(false);
            if (colorId == 0) return;
            if (viewProgram == 0) viewProgram = compileView();
            if (viewTarget == 0 || viewColor != colorId) {
                if (viewTarget != 0) GL30C.glDeleteFramebuffers(viewTarget);
                viewTarget = GL45C.glCreateFramebuffers();
                GL45C.glNamedFramebufferTexture(viewTarget, GL30C.GL_COLOR_ATTACHMENT0, colorId, 0);
                viewColor = colorId;
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
                float far = DepthFar.of(minecraft);
                float[] terms = DepthFar.terms(0.05f, far);
                GL20C.glUniform2f(GL20C.glGetUniformLocation(viewProgram, "planes"), 0.05f, far);
                GL20C.glUniform2f(GL20C.glGetUniformLocation(viewProgram, "terms"), terms[0], terms[1]);
                GL20C.glUniform2f(GL20C.glGetUniformLocation(viewProgram, "depthLayout"),
                        DepthFar.REVERSED ? 1f : 0f, DepthFar.zeroToOne() ? 1f : 0f);
                GL30C.glBindVertexArray(vertexArray);
                GL33C.glBindSampler(0, 0);
                GL45C.glBindTextureUnit(0, mode == 1 ? saved : mode == 2 ? written : drawn);
                GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            } finally {
                gl.restore();
            }
        } catch (Throwable e) {
            LivingHorizonClient.LOGGER.warn("Drawing the depth view failed", e);
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
                uniform vec2 terms;  // the projection's two terms, see DepthFar.terms
                uniform vec2 depthLayout; // 1 when the depth is reversed, 1 when it is 0..1
                out vec4 color;
                void main() {
                    vec2 uv = (gl_FragCoord.xy - area.xy) / area.zw;
                    float d = texelFetch(depth, ivec2(uv * size), 0).r;
                    if (depthLayout.x > 0.5 ? d <= 0.0 : d >= 1.0) {
                        color = vec4(0.08, 0.12, 0.32, 1.0);
                        return;
                    }
                    float near = planes.x, far = planes.y;
                    float z = depthLayout.y > 0.5 ? d : d * 2.0 - 1.0;
                    float linear = terms.y / (z + terms.x);
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

    /** For {@code /livinghorizon voxy}. */
    public static String state() {
        if (!ready && !DhDepth.started()) return "not started";
        if (broken) return "off";
        return switch (state) {
            case 1 -> "on, " + width + "x" + height;
            default -> "waiting: " + reason;
        };
    }

    // --- Before -------------------------------------------------------------------------

    /**
     * The OpenGL name of the main render target's depth or colour texture, 0 when it has
     * none OpenGL knows (Vulkan from 26.2). Before 1.21.5 the target held the names itself.
     */
    private static int mainTexture(boolean depth) {
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=1.21.5 {
        var texture = depth ? minecraft.getMainRenderTarget().getDepthTexture() : minecraft.getMainRenderTarget().getColorTexture();
        return texture instanceof GlTexture gl ? gl.glId() : 0;
        //?} else {
        /*return depth ? minecraft.getMainRenderTarget().getDepthTextureId() : minecraft.getMainRenderTarget().getColorTextureId();
        *///?}
    }

    /** A framebuffer of our own around the main render target's depth texture. */
    private static int ownTarget() {
        int depth = mainTexture(true);
        if (depth == 0) return 0;
        if (ownTarget != 0 && ownTargetDepth == depth) return ownTarget;
        if (ownTarget != 0) GL30C.glDeleteFramebuffers(ownTarget);
        int format = GL45C.glGetTextureLevelParameteri(depth, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
        boolean stencil = format == GL30C.GL_DEPTH24_STENCIL8 || format == GL30C.GL_DEPTH32F_STENCIL8
                || format == GL30C.GL_DEPTH_STENCIL;
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
        boolean iris = irisPipeline.isInstance(pipeline);
        if (!iris && (normalPipeline == null || !normalPipeline.isInstance(pipeline))) {
            return skip("Voxy renders in a way this mod does not know");
        }
        // Whether Voxy wrote its depth for good, water surfaces included.
        boolean kept = !iris || toVanillaField.getBoolean(dataField.get(pipeline));
        voxyKeeps = kept;
        Object viewport = getViewport.invoke(system);
        if (viewport == null) return skip("Voxy has no viewport");

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
        // Voxy's depth is in the game's for good: the game's own, from before, is needed to
        // take its water back out. Copied from the frame after the one that first needs it.
        if (kept && (belowFrom != depthTexture || w != width || h != height || f != format)) {
            return skip("the depth from before Voxy is not copied yet");
        }
        prepare(w, h, f);

        // Voxy's terrain without its water: its opaque depth, copied before the water was
        // drawn into it without a pack; with one, the water goes to a depth of its own.
        int voxyTexture = !iris && opaqueCopied ? opaque
                : textureIdField.getInt(getDepthTex.invoke(fbField.get(pipeline)));
        Object blit = iris ? depthBlitField.get(pipeline) : finalBlitField.get(pipeline);

        copy(depthTexture, saved);
        if (kept) copy(below, depthTexture);
        GlState gl = GlState.save();
        try {
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glColorMask(false, false, false, false);
            GL11C.glDepthMask(true);
            GL33C.glBindSampler(0, 0);
            if (!iris) {
                // The blit Voxy draws its picture with leaves out where its colour is empty.
                GL33C.glBindSampler(3, 0);
                GL45C.glBindTextureUnit(3, textureIdField.getInt(colourField.get(pipeline)));
            }
            // Over the part of the picture Voxy drew to: all of it, or less under a render scale.
            GL11C.glViewport(0, 0, voxyWidth > 0 ? voxyWidth : w, voxyHeight > 0 ? voxyHeight : h);
            Matrix4f transform = new Matrix4f((Matrix4f) projectionField.get(viewport)).mul((Matrix4f) modelViewField.get(viewport));
            transformBlitDepth.invoke(null, blit, voxyTexture, target, viewport, transform);
        } finally {
            gl.restore();
        }
        copy(depthTexture, written);
        // Without a pack, Voxy's water is already in the picture: it goes back over the entities.
        colourTarget = !iris ? copyColour() : 0;
        state = 1;
        return true;
    }

    /** The colour of the picture as it is, for the water over the entities; 0 if it cannot be read. */
    private static int copyColour() {
        if (GL45C.glGetNamedFramebufferAttachmentParameteri(target, GL30C.GL_COLOR_ATTACHMENT0,
                GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11C.GL_TEXTURE) return 0;
        int texture = GL45C.glGetNamedFramebufferAttachmentParameteri(target, GL30C.GL_COLOR_ATTACHMENT0,
                GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if (GL45C.glGetTextureLevelParameteri(texture, 0, GL11C.GL_TEXTURE_WIDTH) != width
                || GL45C.glGetTextureLevelParameteri(texture, 0, GL11C.GL_TEXTURE_HEIGHT) != height) return 0;
        int f = GL45C.glGetTextureLevelParameteri(texture, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
        if (colour == 0 || f != colourFormat) {
            if (colour != 0) GL11C.glDeleteTextures(colour);
            colourFormat = f;
            colour = texture(f, width, height);
        }
        copy(texture, colour);
        return target;
    }

    /**
     * Distant Horizons draws its terrain into a framebuffer of its own and never writes its
     * depth into the game's, so the entities would show through it. Its depth texture is
     * converted from its projection to the game's, and written wherever it is nearer than
     * what is there: the same merge as Voxy's, undone by {@link #restore()} the same way.
     */
    private static boolean mergeDh() {
        float[] dh = DhDepth.read();
        if (dh == null) return skip(DhDepth.reason());
        target = ownTarget();
        if (target == 0) return skip("no framebuffer to draw into");
        if (GL45C.glGetNamedFramebufferAttachmentParameteri(target, GL30C.GL_DEPTH_ATTACHMENT,
                GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11C.GL_TEXTURE
                && GL45C.glGetNamedFramebufferAttachmentParameteri(target, GL30C.GL_DEPTH_STENCIL_ATTACHMENT,
                GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11C.GL_TEXTURE) {
            return skip("the depth is not a texture");
        }
        depthTexture = ownTargetDepth;
        int w = GL45C.glGetTextureLevelParameteri(depthTexture, 0, GL11C.GL_TEXTURE_WIDTH);
        int h = GL45C.glGetTextureLevelParameteri(depthTexture, 0, GL11C.GL_TEXTURE_HEIGHT);
        int f = GL45C.glGetTextureLevelParameteri(depthTexture, 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
        if (w <= 0 || h <= 0) return skip("the depth texture has no size");
        int dhWidth = GL45C.glGetTextureLevelParameteri((int) dh[0], 0, GL11C.GL_TEXTURE_WIDTH);
        int dhHeight = GL45C.glGetTextureLevelParameteri((int) dh[0], 0, GL11C.GL_TEXTURE_HEIGHT);
        if (dhWidth <= 0 || dhHeight <= 0) return skip("Distant Horizons has no depth yet");
        prepare(w, h, f);
        if (dhProgram == 0) {
            dhProgram = compileDh();
            dhSampler = GL33C.glGenSamplers();
            GL33C.glSamplerParameteri(dhSampler, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL33C.glSamplerParameteri(dhSampler, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL33C.glSamplerParameteri(dhSampler, GL14C.GL_TEXTURE_COMPARE_MODE, GL11C.GL_NONE);
        }

        copy(depthTexture, saved);
        GlState gl = GlState.save();
        try {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, target);
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glDisable(GL11C.GL_CULL_FACE);
            GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            GL11C.glDisable(GL11C.GL_BLEND);
            GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            GL11C.glDepthFunc(DepthFar.REVERSED ? GL11C.GL_GREATER : GL11C.GL_LESS);
            GL11C.glDepthMask(true);
            GL11C.glColorMask(false, false, false, false);
            GL11C.glViewport(0, 0, w, h);
            GL20C.glUseProgram(dhProgram);
            GL20C.glUniform2f(GL20C.glGetUniformLocation(dhProgram, "screen"), w, h);
            GL20C.glUniform2f(GL20C.glGetUniformLocation(dhProgram, "dhSize"), dhWidth, dhHeight);
            GL20C.glUniform4f(GL20C.glGetUniformLocation(dhProgram, "dh"), dh[1], dh[2], dh[3], dh[4]);
            Minecraft minecraft = Minecraft.getInstance();
            float near = 0.05f, far = DepthFar.of(minecraft);
            float[] terms = DepthFar.terms(near, far);
            GL20C.glUniform2f(GL20C.glGetUniformLocation(dhProgram, "game"), terms[0], terms[1]);
            GL20C.glUniform2f(GL20C.glGetUniformLocation(dhProgram, "depthLayout"),
                    DepthFar.REVERSED ? 1f : 0f, DepthFar.zeroToOne() ? 1f : 0f);
            GL30C.glBindVertexArray(vertexArray);
            GL33C.glBindSampler(0, dhSampler);
            GL45C.glBindTextureUnit(0, (int) dh[0]);
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
        } finally {
            gl.restore();
        }
        copy(depthTexture, written);
        state = 1;
        return true;
    }

    private static int dhProgram, dhSampler;

    /**
     * Distant Horizons' depth to the game's: back to a distance in front of the camera
     * through Distant Horizons' own projection, then forward through the game's.
     */
    private static int compileDh() {
        String vertex = """
                #version 330 core
                void main() {
                    vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
                }
                """;
        String fragment = """
                #version 330 core
                uniform sampler2D dhDepth;
                uniform vec2 screen;
                uniform vec2 dhSize;
                uniform vec4 dh;   // projection a, projection b, 1 when the depth is -1..1, depth of nothing
                uniform vec2 game; // the game's projection a and b, see DepthFar.terms
                uniform vec2 depthLayout; // 1 when the game's depth is reversed, 1 when it is 0..1
                void main() {
                    float d = texelFetch(dhDepth, ivec2(gl_FragCoord.xy / screen * dhSize), 0).r;
                    if (d == dh.w) discard;
                    float ndc = dh.z > 0.5 ? d * 2.0 - 1.0 : d;
                    float z = -dh.y / (ndc + dh.x);
                    if (!(z < 0.0)) discard;
                    float depth = (game.x * z + game.y) / -z;
                    if (depthLayout.y < 0.5) depth = depth * 0.5 + 0.5;
                    if (depthLayout.x > 0.5 ? !(depth > 0.0) : !(depth < 1.0)) discard;
                    gl_FragDepth = clamp(depth, 0.0, 1.0);
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
            throw new IllegalStateException("Distant Horizons depth program: " + GL20C.glGetProgramInfoLog(linked));
        }
        int previous = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        GL20C.glUseProgram(linked);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(linked, "dhDepth"), 0);
        GL20C.glUseProgram(previous);
        return linked;
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
            GL11C.glViewport(0, 0, width, height);
            GL30C.glBindVertexArray(vertexArray);
            for (int unit = 0; unit < 4; unit++) {
                GL33C.glBindSampler(unit, 0);
                GL45C.glBindTextureUnit(unit, unit == 0 ? saved : unit == 1 ? written : unit == 2 ? drawn : colour);
            }
            if (colourTarget == target) {
                // Voxy's water over what of the entities is under it, as the game's water is.
                if (tintProgram == 0) tintProgram = compileTint();
                GL11C.glDisable(GL11C.GL_DEPTH_TEST);
                GL11C.glDepthMask(false);
                GL11C.glColorMask(true, true, true, false);
                GL11C.glEnable(GL11C.GL_BLEND);
                GL14C.glBlendFuncSeparate(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA, GL11C.GL_ZERO, GL11C.GL_ONE);
                GL20C.glUseProgram(tintProgram);
                GL20C.glUniform1f(GL20C.glGetUniformLocation(tintProgram, "water"), WATER);
                GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            }
            GL11C.glDisable(GL11C.GL_BLEND);
            GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            GL11C.glDepthFunc(GL11C.GL_ALWAYS);
            GL11C.glDepthMask(true);
            GL11C.glColorMask(false, false, false, false);
            GL20C.glUseProgram(program);
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
        } finally {
            gl.restore();
            colourTarget = 0;
        }
    }

    // --- Distant Horizons' fade -------------------------------------------------------------

    /** The entities of this frame are known: {@code written} before them, {@code drawn} after. */
    private static boolean fadeMask;
    /** Between {@link #beforeFade()} and {@link #afterFade()}: the depth to give back is in {@code faded}. */
    private static boolean fading;
    private static int unmaskProgram;

    /**
     * Distant Horizons is about to fade the game's picture into its terrain. Without a
     * shader pack, it takes anything the game drew past the render distance for terrain
     * left over, and paints its own terrain over it - the distant mobs included. For the
     * fade, the pixels of the entities read as sky, which it leaves alone.
     */
    public static void beforeFade() {
        if (!fadeMask || fading || !DepthFar.openGl()) return;
        try {
            copy(depthTexture, faded);
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
                if (unmaskProgram == 0) unmaskProgram = compileUnmask();
                GL20C.glUseProgram(unmaskProgram);
                GL20C.glUniform1f(GL20C.glGetUniformLocation(unmaskProgram, "empty"), DepthFar.REVERSED ? 0f : 1f);
                GL30C.glBindVertexArray(vertexArray);
                for (int unit = 0; unit < 3; unit++) {
                    GL33C.glBindSampler(unit, 0);
                    GL45C.glBindTextureUnit(unit, unit == 0 ? faded : unit == 1 ? written : drawn);
                }
                GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);
            } finally {
                gl.restore();
            }
            fading = true;
        } catch (Throwable e) {
            fadeMask = false;
            fail(e);
        }
    }

    /** Distant Horizons' fade is done: the entities' depth comes back. */
    public static void afterFade() {
        if (!fading) return;
        fading = false;
        try {
            copy(faded, depthTexture);
        } catch (Throwable e) {
            fadeMask = false;
            fail(e);
        }
    }

    /**
     * A screen-filling triangle that empties the depth wherever an entity was drawn and
     * nothing was drawn over it since (translucent terrain, which the fade must still see).
     */
    private static int compileUnmask() {
        String vertex = """
                #version 330 core
                void main() {
                    vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                    gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
                }
                """;
        String fragment = """
                #version 330 core
                uniform sampler2D current;
                uniform sampler2D written;
                uniform sampler2D drawn;
                uniform float empty;
                void main() {
                    ivec2 at = ivec2(gl_FragCoord.xy);
                    float entity = texelFetch(drawn, at, 0).r;
                    if (entity == texelFetch(written, at, 0).r || texelFetch(current, at, 0).r != entity) discard;
                    gl_FragDepth = empty;
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
            throw new IllegalStateException("Fade mask program: " + GL20C.glGetProgramInfoLog(linked));
        }
        int previous = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        GL20C.glUseProgram(linked);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(linked, "current"), 0);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(linked, "written"), 1);
        GL20C.glUniform1i(GL20C.glGetUniformLocation(linked, "drawn"), 2);
        GL20C.glUseProgram(previous);
        return linked;
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
        for (int texture : new int[]{saved, written, drawn, faded, below}) if (texture != 0) GL11C.glDeleteTextures(texture);
        width = w;
        height = h;
        format = f;
        saved = texture(f, w, h);
        written = texture(f, w, h);
        drawn = texture(f, w, h);
        faded = texture(f, w, h);
        below = texture(f, w, h);
        belowFrom = 0;
    }

    private static int texture(int format, int width, int height) {
        int texture = GL45C.glCreateTextures(GL11C.GL_TEXTURE_2D);
        if (format == GL11C.GL_DEPTH_COMPONENT || format == GL30C.GL_DEPTH_STENCIL) {
            // Before 1.21.5 the game's depth has an unsized format, which storage refuses and
            // leaves the copy without a size: it is made the way the game makes its own, so
            // that copying between the two stays allowed.
            boolean stencil = format == GL30C.GL_DEPTH_STENCIL;
            int previous = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, format, width, height, 0, format,
                    stencil ? GL30C.GL_UNSIGNED_INT_24_8 : GL11C.GL_FLOAT, (java.nio.ByteBuffer) null);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previous);
            GL45C.glTextureParameteri(texture, GL12C.GL_TEXTURE_MAX_LEVEL, 0);
        } else {
            GL45C.glTextureStorage2D(texture, 1, format, width, height);
        }
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
                    float before = texelFetch(saved, at, 0).r;
                    // Still what Voxy wrote: no entity here, give the depth back as it was.
                    // Else the entity, unless it is under Voxy's water: then the water.
                    gl_FragDepth = now == texelFetch(written, at, 0).r ? before : min(now, before);
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

    /** Over what of the entities is behind the depth Voxy keeps - its water - the colour that was there. */
    private static int compileTint() {
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
                uniform sampler2D before;
                uniform float water;
                out vec4 color;
                void main() {
                    ivec2 at = ivec2(gl_FragCoord.xy);
                    float now = texelFetch(drawn, at, 0).r;
                    if (now == texelFetch(written, at, 0).r || now <= texelFetch(saved, at, 0).r) discard;
                    color = vec4(texelFetch(before, at, 0).rgb, water);
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
            throw new IllegalStateException("Water tint program: " + GL20C.glGetProgramInfoLog(linked));
        }
        int previous = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        GL20C.glUseProgram(linked);
        String[] names = {"saved", "written", "drawn", "before"};
        for (int unit = 0; unit < names.length; unit++) {
            GL20C.glUniform1i(GL20C.glGetUniformLocation(linked, names[unit]), unit);
        }
        GL20C.glUseProgram(previous);
        return linked;
    }

    static int shader(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            throw new IllegalStateException("Depth restore shader: " + GL20C.glGetShaderInfoLog(shader));
        }
        return shader;
    }

    /** Every piece of GL state touched here, as it was, so that the game, Iris and Voxy find it again. */
    record GlState(int program, int vertexArray, int drawFramebuffer, int readFramebuffer, int activeTexture, int[] textures,
                           int[] samplers, boolean depthTest, boolean stencilTest, boolean scissor, boolean cull,
                           boolean blend, int[] blendFunc, int depthFunc, boolean depthMask, boolean[] colorMask, int[] viewport) {
        /** Texture units used here: three depths and a colour. */
        private static final int UNITS = 4;

        static GlState save() {
            int active = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
            int[] textures = new int[UNITS], samplers = new int[UNITS];
            for (int unit = 0; unit < UNITS; unit++) {
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
                    GL11C.glIsEnabled(GL11C.GL_BLEND),
                    new int[]{GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB), GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB),
                            GL11C.glGetInteger(GL14C.GL_BLEND_SRC_ALPHA), GL11C.glGetInteger(GL14C.GL_BLEND_DST_ALPHA)},
                    GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC),
                    GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK),
                    new boolean[]{mask[0] != 0, mask[1] != 0, mask[2] != 0, mask[3] != 0}, viewport);
        }

        void restore() {
            GL20C.glUseProgram(program);
            GL30C.glBindVertexArray(vertexArray);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFramebuffer);
            for (int unit = 0; unit < UNITS; unit++) {
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
            GL14C.glBlendFuncSeparate(blendFunc[0], blendFunc[1], blendFunc[2], blendFunc[3]);
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
        if (broken || voxyOff) return false;
        if (!ready) {
            ready = true;
            if (!Platform.isModLoaded("voxy")) {
                voxyOff = true;
                return false;
            }
            try {
                link();
            } catch (Throwable e) {
                voxyOff = true;
                LivingHorizonClient.LOGGER.warn("Voxy found, but its depth cannot be used: distant mobs show through its terrain", e);
                return false;
            }
        }
        return true;
    }

    private static void fail(Throwable e) {
        broken = true;
        merged = false;
        LivingHorizonClient.LOGGER.warn("Using the far terrain's depth failed: distant mobs show through it", e);
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
        fbField = pipeline.getField("fb");
        getDepthTex = depthBuffer.getMethod("getDepthTex");
        textureIdField = glTexture.getField("id");
        projectionField = viewport.getField("vanillaProjection");
        modelViewField = viewport.getField("modelView");
        transformBlitDepth = pipeline.getDeclaredMethod("transformBlitDepth", blit, int.class, int.class, viewport, Matrix4f.class);
        transformBlitDepth.setAccessible(true);
        try {
            Class<?> normal = Class.forName("me.cortex.voxy.client.core.NormalRenderPipeline");
            finalBlitField = normal.getDeclaredField("finalBlit");
            finalBlitField.setAccessible(true);
            colourField = normal.getDeclaredField("colourSSAOTex");
            colourField.setAccessible(true);
            normalPipeline = normal;
        } catch (ReflectiveOperationException e) {
            LivingHorizonClient.LOGGER.warn("Voxy's pipeline without a shader pack is not the one this mod knows: its water hides distant mobs", e);
        }
    }
}
