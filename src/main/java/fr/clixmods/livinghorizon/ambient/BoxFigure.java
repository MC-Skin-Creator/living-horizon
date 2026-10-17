package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.clixmods.livinghorizon.render.Sink;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

/**
 * A figure made of boxes, for the easter eggs that are no entity of the game: legs, body,
 * arms and head, each turned at its joint. Its origin is at its feet and it faces +z, its
 * right hand on -x, as the game's models have it.
 */
final class BoxFigure {
    /**
     * Proportions in blocks, colours as {@code 0xRRGGBB}: the head sits {@code headForward}
     * in front of the shoulders, the face is a patch of its own colour on the front of it.
     */
    record Shape(float leg, float legWidth, float body, float bodyWidth, float bodyDepth,
                 float arm, float armWidth, float head, float headForward, int fur, int face) {
    }

    private BoxFigure() {
    }

    /**
     * Draws the figure at the pose's origin. Angles in degrees: {@code swing} sends one leg
     * and the other arm forwards, {@code raise} lifts the right arm out to the side (negative
     * up), {@code headYaw} turns the head from the body.
     */
    static void draw(PoseStack pose, Sink collector, Shape shape, float swing, float raise, float headYaw, int light) {
        draw(pose, collector, shape, swing, raise, headYaw, light, Ufo.SOLID, 255, false);
    }

    /**
     * The same in a render type of its own, see-through as much as {@code alpha} says (for a
     * translucent one); {@code seated}, the legs straight out in front from the hips.
     */
    static void draw(PoseStack pose, Sink collector, Shape shape, float swing, float raise, float headYaw, int light,
                     RenderType type, int alpha, boolean seated) {
        float hip = shape.leg(), shoulder = shape.leg() + shape.body();
        float bw = shape.bodyWidth() / 2, bd = shape.bodyDepth() / 2;
        float lw = shape.legWidth() / 2;
        Paint fur = new Paint(type, shape.fur(), alpha, light);
        limb(pose, collector, -lw, hip, seated ? -90 : swing, 0, -lw, lw, shape.legWidth(), shape.leg(), fur);
        limb(pose, collector, lw, hip, seated ? -90 : -swing, 0, -lw, lw, shape.legWidth(), shape.leg(), fur);
        collector.draw(pose, type, (at, out) -> fur.box(at, out, -bw, hip, -bd, bw, shoulder, bd));
        // Each arm turns on its inner top corner, against the body, a little under the shoulder
        // line as a model's arm is: raised, it stays on the shoulder.
        float aw = shape.armWidth(), socket = shoulder - aw * 0.3f;
        limb(pose, collector, -bw, socket, -swing, raise, -aw, 0, aw, shape.arm(), fur);
        limb(pose, collector, bw, socket, swing, 0, 0, aw, aw, shape.arm(), fur);

        float h = shape.head() / 2;
        int face = shape.face();
        pose.pushPose();
        pose.translate(0, shoulder, shape.headForward());
        pose.mulPose(Axis.YP.rotationDegrees(headYaw));
        collector.draw(pose, type, (at, out) -> {
            fur.box(at, out, -h, 0, -h, h, 2 * h, h);
            if (face != shape.fur()) {
                Ufo.box(at, out, -h * 0.7f, h * 0.25f, h, h * 0.7f, h * 1.35f, h + 0.01f,
                        red(face), green(face), blue(face), light);
                // Eyes, dark, under the brow.
                for (int s = -1; s <= 1; s += 2) {
                    float ex = s * h * 0.35f;
                    Ufo.box(at, out, ex - h * 0.12f, h * 0.95f, h + 0.01f, ex + h * 0.12f, h * 1.1f, h + 0.02f,
                            20, 14, 10, light);
                }
            }
        });
        pose.popPose();
    }

    /**
     * A limb hanging from its joint at {@code (x, y)}, from {@code x0} to {@code x1} across
     * it: swung forwards or back, then lifted sideways.
     */
    private static void limb(PoseStack pose, Sink collector, float x, float y, float swing, float raise,
                             float x0, float x1, float depth, float length, Paint paint) {
        float d = depth / 2;
        pose.pushPose();
        pose.translate(x, y, 0);
        if (raise != 0) pose.mulPose(Axis.ZP.rotationDegrees(raise));
        pose.mulPose(Axis.XP.rotationDegrees(swing));
        collector.draw(pose, paint.type(), (at, out) -> paint.box(at, out, x0, -length, -d, x1, 0, d));
        pose.popPose();
    }

    /** How a box of the figure is drawn: its render type, colour, alpha and light. */
    private record Paint(RenderType type, int colour, int alpha, int light) {
        void box(PoseStack.Pose at, VertexConsumer out,
                 float x0, float y0, float z0, float x1, float y1, float z1) {
            Ufo.box(at, out, x0, y0, z0, x1, y1, z1, red(colour), green(colour), blue(colour), alpha, light);
        }
    }

    /**
     * The light a figure stands in, as the game would light an entity there: the brightest
     * of its feet and the two blocks above. Open sky where the client has no light.
     */
    static int light(ClientLevel level, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y + 0.1, z);
        if (!level.hasChunkAt(feet)) return Ufo.LIT;
        int block = 0, sky = 0;
        for (int up = 0; up < 3; up++) {
            BlockPos at = feet.above(up);
            block = Math.max(block, level.getBrightness(LightLayer.BLOCK, at));
            sky = Math.max(sky, level.getBrightness(LightLayer.SKY, at));
        }
        return LightTexture.pack(block, sky);
    }

    private static int red(int colour) {
        return colour >> 16 & 0xFF;
    }

    private static int green(int colour) {
        return colour >> 8 & 0xFF;
    }

    private static int blue(int colour) {
        return colour & 0xFF;
    }
}
