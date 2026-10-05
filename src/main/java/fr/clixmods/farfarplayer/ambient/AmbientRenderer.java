package fr.clixmods.farfarplayer.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.clixmods.farfarplayer.FarConfig;
import fr.clixmods.farfarplayer.FarFarPlayerClient;
import fr.clixmods.farfarplayer.render.GhostRenderer;
import fr.clixmods.farfarplayer.track.FarPlayerTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the pixel birds, from {@code textures/misc/birds.png}: a 64 by 32 sheet.
 *
 * <ul>
 *   <li>Flying, top row: a 16 by 8 bird seen from above per species, cut in two planes
 *       hinged along the body, one per wing, which tilt to beat.</li>
 *   <li>Perched, bottom row: an 8 by 8 bird seen from the side, on one upright plane that
 *       always faces the camera, mirrored to look the way the bird faces.</li>
 * </ul>
 *
 * <p>The plain sprite is white and tinted (dark for most birds, white for gulls); the
 * others are in their own colours. Submitted with the entities, so shader packs and Voxy's
 * depth treat them like any entity.
 */
public final class AmbientRenderer {
    private static final RenderType TYPE = RenderTypes.entityCutout(
            Identifier.fromNamespaceAndPath(FarFarPlayerClient.MOD_ID, "textures/misc/birds.png"));
    private static final int LIGHT = LightTexture.pack(0, 15);

    private AmbientRenderer() {
    }

    public static void submit(PoseStack pose, Vec3 camera, SubmitNodeCollector collector) {
        FarConfig config = FarConfig.get();
        if (!config.enabled) return;
        Minecraft minecraft = Minecraft.getInstance();
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double reach = minecraft.gameRenderer.getDepthFar() * 0.95;

        if (config.ufo) FarPlayerTracker.get().ambience().ufo().submit(pose, camera, collector, partial);
        for (Ambience.Flyer bird : FarPlayerTracker.get().ambience().flyers()) {
            if (!config.skyBirds || bird.kind != Ambience.Kind.SILHOUETTE) continue;
            double x = Mth.lerp(partial, bird.xo, bird.x) - camera.x;
            double y = Mth.lerp(partial, bird.yo, bird.y) - camera.y;
            double z = Mth.lerp(partial, bird.zo, bird.z) - camera.z;
            double distance = Math.sqrt(x * x + y * y + z * z);
            GhostRenderer.noteDistance(distance);
            // Past the far plane: closer and smaller in proportion, the same on screen.
            double pull = distance > reach ? reach / distance : 1.0;
            float yaw = Mth.rotLerp(partial, bird.yawO, bird.yaw);
            int tint = bird.shade;
            int species = bird.species.ordinal();

            pose.pushPose();
            pose.translate((float) (x * pull), (float) (y * pull), (float) (z * pull));
            if (bird.perched) {
                // Upright, turned to the camera; which way it looks decides the mirror.
                float toCamera = (float) Math.toDegrees(Math.atan2(-x, -z));
                pose.mulPose(Axis.YP.rotationDegrees(toCamera));
                double facing = Math.toRadians(yaw), across = Math.toRadians(toCamera);
                boolean mirrored = -Math.sin(facing) * Math.cos(across) + Math.cos(facing) * -Math.sin(across) < 0;
                float height = (float) (bird.span * bird.scale * 0.55 * pull);
                pose.scale(height, height, height);
                collector.submitCustomGeometry(pose, TYPE, (at, out) -> perched(at, out, species, mirrored, tint));
            } else {
                float bank = Mth.lerp(partial, bird.bankO, bird.bank);
                float wing = Mth.lerp(partial, bird.wingO, bird.wing);
                float scale = (float) (bird.span * bird.scale * pull);
                pose.mulPose(Axis.YP.rotationDegrees(-yaw));
                pose.mulPose(Axis.ZP.rotationDegrees(bank));
                pose.scale(scale, scale, scale);
                collector.submitCustomGeometry(pose, TYPE, (at, out) -> flying(at, out, species, wing, tint));
            }
            pose.popPose();
        }
    }

    /** Which flying sprite: the gull flies on the plain one, tinted white. */
    private static int flyingColumn(int species) {
        return switch (Ambience.Species.values()[species]) {
            case PIGEON -> 1;
            case ROBIN -> 2;
            case TIT -> 3;
            default -> 0;
        };
    }

    /** Which perched sprite. */
    private static int perchedColumn(int species) {
        return switch (Ambience.Species.values()[species]) {
            case PIGEON -> 1;
            case ROBIN -> 2;
            case TIT -> 3;
            case GULL -> 4;
            case DUCK -> 5;
            default -> 0;
        };
    }

    private static void flying(PoseStack.Pose at, VertexConsumer out, int species, float wingDegrees, int tint) {
        float u0 = flyingColumn(species) * 16 / 64f, um = u0 + 8 / 64f, u1 = u0 + 16 / 64f;
        // Flying sprites: the top row, the duck alone on the third.
        float v0 = Ambience.Species.values()[species] == Ambience.Species.DUCK ? 16 / 32f : 0f, v1 = v0 + 8 / 32f;
        float tipX = 0.5f * Mth.cos(wingDegrees * Mth.DEG_TO_RAD);
        float tipY = 0.5f * Mth.sin(wingDegrees * Mth.DEG_TO_RAD);
        // Left half of the sprite on the +X wing, its tip at the sprite's left edge.
        quad(at, out, tint,
                0f, 0f, 0.25f, um, v0,
                tipX, tipY, 0.25f, u0, v0,
                tipX, tipY, -0.25f, u0, v1,
                0f, 0f, -0.25f, um, v1);
        // Right half on the -X wing.
        quad(at, out, tint,
                0f, 0f, 0.25f, um, v0,
                -tipX, tipY, 0.25f, u1, v0,
                -tipX, tipY, -0.25f, u1, v1,
                0f, 0f, -0.25f, um, v1);
    }

    private static void perched(PoseStack.Pose at, VertexConsumer out, int species, boolean mirrored, int tint) {
        float u0 = perchedColumn(species) * 8 / 64f, u1 = u0 + 8 / 64f;
        float left = mirrored ? u1 : u0, right = mirrored ? u0 : u1;
        // One unit tall, standing on its feet: the sprite's bottom row is empty, so sink it by one texel.
        float sink = -1 / 8f;
        quad(at, out, tint,
                -0.5f, sink, 0f, left, 16 / 32f,
                0.5f, sink, 0f, right, 16 / 32f,
                0.5f, 1f + sink, 0f, right, 8 / 32f,
                -0.5f, 1f + sink, 0f, left, 8 / 32f);
    }

    /**
     * A plane with two real sides: once in the order given, facing where its own normal
     * says, and once the other way round, facing the other way. The render type culls
     * back faces, so each pixel sees exactly one side, lit as the side it is - a single
     * double-sided plane would be lit from the wrong side half the time, which shader packs
     * show as one wing dark and the other pale.
     */
    private static void quad(PoseStack.Pose at, VertexConsumer out, int tint,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float x4, float y4, float z4, float u4, float v4) {
        // Normal of the face as given: (2 - 1) x (3 - 1).
        float ax = x2 - x1, ay = y2 - y1, az = z2 - z1, bx = x3 - x1, by = y3 - y1, bz = z3 - z1;
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-6f) return;
        nx /= length;
        ny /= length;
        nz /= length;
        vertex(at, out, tint, x1, y1, z1, u1, v1, nx, ny, nz);
        vertex(at, out, tint, x2, y2, z2, u2, v2, nx, ny, nz);
        vertex(at, out, tint, x3, y3, z3, u3, v3, nx, ny, nz);
        vertex(at, out, tint, x4, y4, z4, u4, v4, nx, ny, nz);
        vertex(at, out, tint, x1, y1, z1, u1, v1, -nx, -ny, -nz);
        vertex(at, out, tint, x4, y4, z4, u4, v4, -nx, -ny, -nz);
        vertex(at, out, tint, x3, y3, z3, u3, v3, -nx, -ny, -nz);
        vertex(at, out, tint, x2, y2, z2, u2, v2, -nx, -ny, -nz);
    }

    private static void vertex(PoseStack.Pose at, VertexConsumer out, int tint, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz) {
        out.addVertex(at, x, y, z)
                .setColor(tint, tint, tint, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LIGHT)
                .setNormal(at, nx, ny, nz);
    }
}
