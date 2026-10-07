//? if >=26.3 {
/*package fr.clixmods.livinghorizon.compat;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.render.DepthFar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/^*
 * What {@link FarDepth} does with OpenGL, done through the game's own GPU device, so that it
 * works when the game draws with Vulkan (26.2 on; this class from 26.3): Distant Horizons'
 * depth merged before the entities, and the distant figures kept out of its fade.
 *
 * <p>Nothing may be copied in the middle of the world's render pass there, so neither step
 * takes anything back the way {@link FarDepth} does. The merge is a draw in the world's pass:
 * Distant Horizons' depth stays in the game's for the rest of the frame, which its own fade
 * handles the same way as the sky. The fade, outside any pass, works on a copy of the depth:
 * whatever lies outside the game's own chunks is emptied, which can only be the distant
 * figures or that merged depth, and the copy is put back once the fade is done.
 ^/
public final class FarDepthGpu {
    private static final String NAMESPACE = "livinghorizon";

    private static final RenderPipeline MERGE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/far_depth_merge"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/far_depth_merge"))
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("DhDepth", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("LhFarDepth", UniformType.UNIFORM_BUFFER)
                    .build())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            // Drawn in the world's pass, with its colour target: written to depth only.
            .withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.DEFAULT.format(),
                    ColorTargetState.WRITE_NONE))
            // Only where Distant Horizons' terrain is nearer than what the game drew.
            .withDepthStencilState(new DepthStencilState(
                    DepthFar.REVERSED ? CompareOp.GREATER_THAN : CompareOp.LESS_THAN, true))
            .build();

    private static final RenderPipeline FADE_MASK = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(NAMESPACE, "pipeline/far_fade_mask"))
            .withVertexShader("core/screenquad")
            .withFragmentShader(Identifier.fromNamespaceAndPath(NAMESPACE, "core/far_fade_mask"))
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("LhFadeMask", UniformType.UNIFORM_BUFFER)
                    .build())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
            .build();

    private static final int MERGE_SIZE = new Std140SizeCalculator().putVec4().putVec4().get();
    private static final int FADE_SIZE = new Std140SizeCalculator().putMat4f().putVec4().putVec4().get();
    /^* Frames a uniform buffer is kept before it is written again: the GPU may still be reading it. ^/
    private static final int RING = 8;

    private static @Nullable MappableRingBuffer mergeUniforms, fadeUniforms;
    /^* The game's depth as it was before the fade, to give back after it. ^/
    private static @Nullable GpuTexture saved;
    private static @Nullable GpuTextureView savedView;
    private static boolean fading, broken;

    private FarDepthGpu() {
    }

    /^* In the world's pass, before the entities are drawn: Distant Horizons' terrain into the depth. ^/
    public static void merge(RenderPass pass) {
        if (broken || !FarDepth.gpuMerge()) return;
        try {
            Object[] dh = DhDepth.readView();
            if (dh == null) {
                FarDepth.gpuResult(false, DhDepth.reason(), 0, 0);
                return;
            }
            CompiledRenderPipeline pipeline = RenderSystem.getCompiledPipelineNullable(MERGE);
            if (pipeline == null) {
                FarDepth.gpuResult(false, "the depth merge pipeline did not compile", 0, 0);
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            float[] game = DepthFar.terms(0.05f, DepthFar.of(minecraft));
            if (mergeUniforms == null) {
                mergeUniforms = new MappableRingBuffer(() -> "Living Horizon far depth", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, MERGE_SIZE);
            }
            GpuBuffer uniforms = next(mergeUniforms);
            try (GpuBufferSlice.MappedView view = uniforms.map(false, true)) {
                Std140Builder.intoBuffer(view.data())
                        .putVec4((float) dh[1], (float) dh[2], (float) dh[3], (float) dh[4])
                        .putVec4(game[0], game[1], DepthFar.REVERSED ? 1f : 0f, DepthFar.zeroToOne() ? 1f : 0f);
            }
            pass.setPipeline(pipeline);
            pass.setUniform("DhDepth", (GpuTextureView) dh[0], nearest());
            pass.setUniform("LhFarDepth", uniforms);
            pass.draw(3, 1, 0, 0);
            RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
            FarDepth.gpuResult(true, "", main.width, main.height);
        } catch (Throwable e) {
            fail(e);
        }
    }

    /^* Distant Horizons is about to fade the game's picture into its terrain. ^/
    public static void beforeFade() {
        if (broken || fading || !FarDepth.gpuFade()) return;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
            GpuTexture depth = main.getDepthTexture();
            GpuTextureView depthView = main.getDepthTextureView();
            if (depth == null || depthView == null) return;
            CompiledRenderPipeline mask = RenderSystem.getCompiledPipelineNullable(FADE_MASK);
            CompiledRenderPipeline blit = RenderSystem.getCompiledPipelineNullable(RenderPipelines.BLIT_DEPTH);
            if (mask == null || blit == null) return;
            GpuTextureView copy = savedView(depth);

            if (fadeUniforms == null) {
                fadeUniforms = new MappableRingBuffer(() -> "Living Horizon fade mask", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, FADE_SIZE);
            }
            GpuBuffer uniforms = next(fadeUniforms);
            Vec3 camera = minecraft.gameRenderer.mainCamera().position();
            int chunks = minecraft.options.getEffectiveRenderDistance();
            int chunkX = Math.floorDiv((int) Math.floor(camera.x), 16), chunkZ = Math.floorDiv((int) Math.floor(camera.z), 16);
            try (GpuBufferSlice.MappedView view = uniforms.map(false, true)) {
                Std140Builder.intoBuffer(view.data())
                        .putMat4f(new Matrix4f(FarDepth.viewProjection()).invert())
                        .putVec4((float) ((chunkX - chunks) * 16 - camera.x), (float) ((chunkZ - chunks) * 16 - camera.z),
                                (float) ((chunkX + chunks + 1) * 16 - camera.x), (float) ((chunkZ + chunks + 1) * 16 - camera.z))
                        .putVec4(DepthFar.zeroToOne() ? 1f : 0f, DepthFar.REVERSED ? 0f : 1f, 0f, 0f);
            }

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            blitDepth(encoder, blit, depthView, copy, "Living Horizon: depth before the fade");
            try (RenderPass pass = encoder.createRenderPass(RenderPassDescriptor.builder(() -> "Living Horizon: fade mask")
                    .withDepthAttachment(depthView).build())) {
                RenderSystem.bindDefaultUniforms(pass);
                pass.setPipeline(mask);
                pass.setUniform("InSampler", copy, nearest());
                pass.setUniform("LhFadeMask", uniforms);
                pass.draw(3, 1, 0, 0);
            }
            fading = true;
        } catch (Throwable e) {
            fail(e);
        }
    }

    /^* Distant Horizons' fade is done: the depth comes back as it was. ^/
    public static void afterFade() {
        if (!fading) return;
        fading = false;
        try {
            RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            CompiledRenderPipeline blit = RenderSystem.getCompiledPipelineNullable(RenderPipelines.BLIT_DEPTH);
            if (blit == null || savedView == null || main.getDepthTextureView() == null) return;
            blitDepth(RenderSystem.getDevice().createCommandEncoder(), blit, savedView, main.getDepthTextureView(),
                    "Living Horizon: depth after the fade");
        } catch (Throwable e) {
            fail(e);
        }
    }

    /^* One depth texture drawn into another, as the game does it. ^/
    private static void blitDepth(CommandEncoder encoder, CompiledRenderPipeline blit, GpuTextureView from,
                                  GpuTextureView to, String label) {
        try (RenderPass pass = encoder.createRenderPass(RenderPassDescriptor.builder(() -> label)
                .withDepthAttachment(to).build())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setPipeline(blit);
            pass.setUniform("InSampler", from, nearest());
            pass.draw(3, 1, 0, 0);
        }
    }

    /^* A depth texture like the game's, to keep its depth in while the fade runs. ^/
    private static GpuTextureView savedView(GpuTexture depth) {
        int w = depth.getWidth(0), h = depth.getHeight(0);
        if (saved == null || saved.isClosed() || saved.getWidth(0) != w || saved.getHeight(0) != h
                || saved.getFormat() != depth.getFormat()) {
            if (savedView != null) savedView.close();
            if (saved != null) saved.close();
            saved = RenderSystem.getDevice().createTexture(() -> "Living Horizon saved depth",
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING, depth.getFormat(), w, h, 1, 1);
            savedView = RenderSystem.getDevice().createTextureView(saved);
        }
        return savedView;
    }

    private static GpuBuffer next(MappableRingBuffer ring) {
        ring.rotate();
        return ring.currentBuffer();
    }

    private static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    private static void fail(Throwable e) {
        broken = true;
        fading = false;
        FarDepth.gpuResult(false, "off: " + e, 0, 0);
        LivingHorizonClient.LOGGER.warn("Using Distant Horizons' depth with the game's GPU device failed: distant mobs show through its terrain", e);
    }
}
*///?}
