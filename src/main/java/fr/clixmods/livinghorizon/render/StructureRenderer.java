package fr.clixmods.livinghorizon.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
//? if >=1.21 && <1.21.2 {
/*import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.FarStructures;
import fr.clixmods.livinghorizon.track.SharedPositions;
import fr.clixmods.livinghorizon.track.StructureBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
*///?}

/**
 * Draws the moving builds of Sable met on the way ({@code track/FarStructures}): Create:
 * Aeronautics' airships and cars, where they were when Sable stopped sending them. Their
 * blocks are baked once into quads, off the game's thread, and drawn with the birds, among the
 * world's entities, in the light of a falling block.
 *
 * <p>Sable is only made for 1.21.1: on every other version there is nothing to draw.
 */
public final class StructureRenderer {
    private StructureRenderer() {
    }

    //? if >=1.21 && <1.21.2 {
    /*/^* Vertices drawn at most each frame, the nearest builds first: a larger fleet waits, farthest first. ^/
    private static final int MAX_VERTICES = 600_000;
    /^* Frames a build's mesh is kept once it is no longer drawn. ^/
    private static final long KEEP_FRAMES = 1200;
    /^* Ints a vertex takes in a mesh: position, colour, texture, light and normal. ^/
    private static final int STRIDE = 8;
    /^* Each copy of a build's blocks, baked once: a build copied again gets a new one. ^/
    private static final Map<StructureBlocks, Mesh> MESHES = new IdentityHashMap<>();
    private static long frame;

    private static final class Mesh {
        @Nullable CompletableFuture<List<Layer>> baking;
        List<Layer> layers = List.of();
        int vertices;
        long drawn;
    }

    /^* The quads of one kind of block, drawn as the game draws a falling block of that kind. ^/
    private record Layer(RenderType type, int[] data, int vertices) implements Sink.Geometry {
        @Override
        public void draw(PoseStack.Pose at, VertexConsumer out) {
            int[] d = data;
            for (int i = 0, end = vertices * STRIDE; i < end; i += STRIDE) {
                int normal = d[i + 7];
                out.addVertex(at, Float.intBitsToFloat(d[i]), Float.intBitsToFloat(d[i + 1]), Float.intBitsToFloat(d[i + 2]))
                        .setColor(d[i + 3])
                        .setUv(Float.intBitsToFloat(d[i + 4]), Float.intBitsToFloat(d[i + 5]))
                        .setLight(d[i + 6])
                        .setNormal(at, (byte) normal / 127f, (byte) (normal >> 8) / 127f, (byte) (normal >> 16) / 127f);
            }
        }
    }

    /^*
     * The copies of moving builds, where they were last seen: each mesh is placed by the build's
     * pose, and drawn closer and smaller past the far plane, the same on screen, as the birds are.
     ^/
    public static void submit(PoseStack pose, Vec3 camera, Sink sink) {
        frame++;
        if (frame % 200 == 0) evict();
        FarConfig config = FarConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !config.distantStructures) return;
        List<FarStructures.Remembered> shown = FarPlayerTracker.get().structures().shown(SharedPositions.dimensionId(level.dimension()));
        if (shown.isEmpty()) return;
        double reach = DepthFar.of(minecraft) * 0.95;
        shown.sort(Comparator.comparingDouble(structure -> structure.center().distanceSquared(camera.x, camera.y, camera.z)));
        int budget = MAX_VERTICES;
        boolean bakedOne = false;
        for (FarStructures.Remembered structure : shown) {
            StructureBlocks blocks = structure.blocks();
            if (blocks == null) continue;
            Vector3d center = structure.center();
            double dx = center.x - camera.x, dy = center.y - camera.y, dz = center.z - camera.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (config.entityMaxDistance > 0 && distance > config.entityMaxDistance) continue;
            double radius = structure.radius();
            if (GhostRenderer.outOfSight(dx, dy, dz, radius)) continue;
            Mesh mesh = MESHES.computeIfAbsent(blocks, any -> new Mesh());
            mesh.drawn = frame;
            if (mesh.baking == null && mesh.layers.isEmpty()) {
                // One build baked at a time, the nearest first.
                if (!bakedOne) mesh.baking = bake(blocks, level, center);
                bakedOne = true;
                continue;
            }
            if (mesh.baking != null) {
                if (!mesh.baking.isDone()) continue;
                List<Layer> baked = mesh.baking.getNow(null);
                mesh.baking = null;
                mesh.layers = baked != null ? baked : List.of();
                mesh.vertices = 0;
                for (Layer layer : mesh.layers) mesh.vertices += layer.vertices();
                // A build of nothing drawable is baked again only once copied again.
                if (mesh.layers.isEmpty()) mesh.layers = List.of(new Layer(null, new int[0], 0));
            }
            if (mesh.vertices == 0 || mesh.vertices > budget) continue;
            budget -= mesh.vertices;
            GhostRenderer.noteDistance(distance + radius);
            double pull = distance > reach ? reach / distance : 1.0;
            Vector3d position = structure.position();
            Vector3d scale = structure.scale();
            Vector3d corner = structure.corner();
            pose.pushPose();
            pose.translate((position.x - camera.x) * pull, (position.y - camera.y) * pull, (position.z - camera.z) * pull);
            pose.scale((float) pull, (float) pull, (float) pull);
            pose.mulPose(new Quaternionf(structure.orientation()));
            pose.scale((float) scale.x, (float) scale.y, (float) scale.z);
            pose.translate(corner.x, corner.y, corner.z);
            for (Layer layer : mesh.layers) {
                if (layer.vertices() > 0) sink.draw(pose, layer.type(), layer);
            }
            pose.popPose();
        }
    }

    /^* Drops the meshes of builds not drawn for a while, or copied again since. ^/
    private static void evict() {
        MESHES.values().removeIf(mesh -> mesh.baking == null && frame - mesh.drawn > KEEP_FRAMES);
    }

    /^*
     * Bakes a build's blocks into quads on {@link FarStructures#work()}, as the game bakes a
     * chunk's: each block's model, its faces against another block of the build left out,
     * tinted as the land under it, in full daylight and the light the blocks give themselves.
     ^/
    private static CompletableFuture<List<Layer>> bake(StructureBlocks blocks, ClientLevel level, Vector3d center) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BlockPos tintAt = BlockPos.containing(center.x, center.y, center.z);
        return CompletableFuture.supplyAsync(() -> {
            Getter getter = new Getter(blocks, level, tintAt);
            Map<RenderType, Capture> captures = new LinkedHashMap<>();
            PoseStack at = new PoseStack();
            RandomSource random = RandomSource.create();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = 0; y < blocks.sizeY(); y++) {
                for (int z = 0; z < blocks.sizeZ(); z++) {
                    for (int x = 0; x < blocks.sizeX(); x++) {
                        BlockState state = blocks.state(x, y, z);
                        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) continue;
                        Capture capture = captures.computeIfAbsent(ItemBlockRenderTypes.getMovingBlockRenderType(state), any -> new Capture());
                        pos.set(x, y, z);
                        at.pushPose();
                        at.translate(x, y, z);
                        int before = capture.size;
                        try {
                            dispatcher.renderBatched(state, pos, getter, at, capture, true, random);
                        } catch (RuntimeException e) {
                            // A model that wants more of the world than a copy has: that block is left out.
                            capture.size = before;
                        }
                        at.popPose();
                    }
                }
            }
            List<Layer> layers = new ArrayList<>();
            for (Map.Entry<RenderType, Capture> entry : captures.entrySet()) {
                Capture capture = entry.getValue();
                int vertices = capture.size / STRIDE;
                // Whole quads only: a block that failed halfway leaves none of its own behind.
                vertices -= vertices % 4;
                if (vertices > 0) layers.add(new Layer(entry.getKey(), Arrays.copyOf(capture.data, vertices * STRIDE), vertices));
            }
            return layers;
        }, FarStructures.work()).exceptionally(e -> {
            LivingHorizonClient.LOGGER.warn("Could not bake the blocks of a moving build", e);
            return List.of();
        });
    }

    /^* Keeps the vertices a block's model writes, as {@link #STRIDE} ints each. ^/
    private static final class Capture implements VertexConsumer {
        int[] data = new int[STRIDE * 1024];
        int size;
        private int at = -STRIDE;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (size + STRIDE > data.length) data = Arrays.copyOf(data, data.length * 2);
            at = size;
            size += STRIDE;
            data[at] = Float.floatToRawIntBits(x);
            data[at + 1] = Float.floatToRawIntBits(y);
            data[at + 2] = Float.floatToRawIntBits(z);
            data[at + 3] = -1;
            data[at + 4] = 0;
            data[at + 5] = 0;
            data[at + 6] = 0xF000F0;
            data[at + 7] = 127 << 8;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            data[at + 3] = (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | (blue & 255);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            data[at + 4] = Float.floatToRawIntBits(u);
            data[at + 5] = Float.floatToRawIntBits(v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            data[at + 6] = (u & 0xFFFF) | (v & 0xFFFF) << 16;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            data[at + 7] = ((int) (x * 127) & 255) | ((int) (y * 127) & 255) << 8 | ((int) (z * 127) & 255) << 16;
            return this;
        }
    }

    /^*
     * A build's blocks as the block models read their neighbours: from the corner of its box,
     * nothing outside it, in full daylight. Tints come from the land at its middle.
     ^/
    private record Getter(StructureBlocks blocks, ClientLevel level, BlockPos tintAt) implements BlockAndTintGetter {
        @Override
        public float getShade(Direction direction, boolean shade) {
            return level.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return layer == LightLayer.SKY ? 15 : 0;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int darkening) {
            return 15 - darkening;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return level.getBlockTint(tintAt, resolver);
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.state(pos.getX(), pos.getY(), pos.getZ());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return blocks.sizeY();
        }

        @Override
        public int getMinBuildHeight() {
            return 0;
        }
    }
    *///?} else {
    /** Nothing: Sable is not made for this version of the game. */
    public static void submit(PoseStack pose, Vec3 camera, Sink sink) {
    }
    //?}
}
