package fr.clixmods.livinghorizon.render.impostor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.clixmods.livinghorizon.render.Sink;
import net.minecraft.client.renderer.texture.OverlayTexture;
//? if >=1.21.9 && <26.2 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OutlineBufferSource;
//?}

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the impostors: one picture per figure, always turned to the camera, submitted with
 * the entities so that shader packs and Voxy's depth treat it like one. A figure standing
 * on the ground is upright and only turns round; a bird, baked as seen from below or above,
 * faces the camera entirely.
 */
public final class ImpostorRenderer {
    /**
     * One figure to draw this frame.
     *
     * @param x       where the figure's origin is (the feet of an entity), from the camera,
     *                already pulled in when past the far plane
     * @param size    blocks along a side of its picture, scaled the same way
     * @param outline the colour of its outline, 0 for none
     * @param facing  turned to the camera up and down too, not only round: for a picture baked
     *                as seen from that height
     */
    public record Billboard(double x, double y, double z, double size, ImpostorAtlas.Sheet sheet, int view, int light,
                            int outline, boolean facing) {
    }

    /**
     * Whether impostors can have an outline in this version: drawn into the game's outline
     * buffer next to the models', which only this range of versions lets in by the side.
     */
    //? if >=1.21.9 && <26.2 {
    public static final boolean OUTLINES = true;
    //?} else {
    /*public static final boolean OUTLINES = false;
    *///?}

    /** An outlined impostor as it was submitted, drawn again into the outline buffer. */
    private record Outlined(PoseStack.Pose at, Billboard board, float rightX, float rightZ, float[] up) {
    }

    /**
     * Fraction of a tile left out on each side: half a pixel of the smallest level, which
     * the filter would otherwise blend with the view next to it, a speck beside the figure.
     * The quad is narrowed to match, so the picture keeps its size.
     */
    private static final float INSET = 0.5f / (ImpostorViews.TILE >> (ImpostorAtlas.MIPS - 1));

    private static final List<Billboard> FRAME = new ArrayList<>();
    private static final List<Outlined> OUTLINED = new ArrayList<>();

    private ImpostorRenderer() {
    }

    public static void beginFrame() {
        FRAME.clear();
        OUTLINED.clear();
    }

    public static void add(Billboard billboard) {
        FRAME.add(billboard);
    }

    /** From the entity submission, with the pose at the camera. */
    public static void submit(PoseStack pose, Sink collector) {
        for (Billboard board : FRAME) {
            double across = Math.sqrt(board.x * board.x + board.z * board.z);
            if (across < 1e-3) continue;
            // The camera's right along the ground: forward is (x, z), and right is forward x up.
            float rightX = (float) (-board.z / across), rightZ = (float) (board.x / across);
            float[] up = up(board);
            pose.pushPose();
            pose.translate((float) board.x, (float) board.y, (float) board.z);
            collector.draw(pose, ImpostorAtlas.type(board.sheet.page()),
                    (at, out) -> quad(at, out, board, rightX, rightZ, up));
            //? if >=1.21.9 && <26.2
            if (board.outline != 0) OUTLINED.add(new Outlined(pose.last().copy(), board, rightX, rightZ, up));
            pose.popPose();
            PolygonStats.impostor();
        }
    }

    /**
     * The outlines of this frame's impostors, once the world's models have written theirs:
     * the same quads, through the picture's cutout, into the outline buffer. Once a frame.
     */
    public static void drawOutlines() {
        //? if >=1.21.9 && <26.2 {
        if (OUTLINED.isEmpty()) return;
        OutlineBufferSource buffers = Minecraft.getInstance().renderBuffers().outlineBufferSource();
        for (Outlined outlined : OUTLINED) {
            Billboard board = outlined.board;
            buffers.setColor(board.outline);
            quad(outlined.at, buffers.getBuffer(ImpostorAtlas.type(board.sheet.page())), board, outlined.rightX,
                    outlined.rightZ, outlined.up);
        }
        OUTLINED.clear();
        //?}
    }

    /**
     * The picture's up: straight up for an upright one; for one facing the camera, straight
     * up as the camera sees it, square to its line of sight, the way the picture was baked.
     */
    private static float[] up(Billboard board) {
        if (!board.facing) return new float[]{0f, 1f, 0f};
        double length = Math.sqrt(board.x * board.x + board.y * board.y + board.z * board.z);
        double fx = board.x / length, fy = board.y / length, fz = board.z / length;
        double ux = -fx * fy, uy = 1 - fy * fy, uz = -fz * fy;
        double u = Math.sqrt(ux * ux + uy * uy + uz * uz);
        // Straight above or below: the picture is seen edge-on whatever is up; keep it upright.
        if (u < 1e-6) return new float[]{0f, 1f, 0f};
        return new float[]{(float) (ux / u), (float) (uy / u), (float) (uz / u)};
    }

    private static void quad(PoseStack.Pose at, VertexConsumer out, Billboard board, float rightX, float rightZ,
                             float[] up) {
        float half = (float) board.size * (0.5f - INSET);
        float bottom = -(1 - board.sheet.feet()) * (float) board.size;
        float top = board.sheet.feet() * (float) board.size;
        // A tile of the page, the top of the picture at the top.
        float u0 = (board.sheet.column() * ImpostorViews.VIEWS + board.view + INSET) / ImpostorAtlas.TILES_ACROSS;
        float u1 = u0 + (1f - 2 * INSET) / ImpostorAtlas.TILES_ACROSS;
        float vTop = board.sheet.row() / (float) ImpostorAtlas.TILES_DOWN;
        float vBottom = (board.sheet.row() + 1) / (float) ImpostorAtlas.TILES_DOWN;
        float lx = -rightX * half, lz = -rightZ * half, rx = rightX * half, rz = rightZ * half;
        float bx = up[0] * bottom, by = up[1] * bottom, bz = up[2] * bottom;
        float tx = up[0] * top, ty = up[1] * top, tz = up[2] * top;
        float[] corners = {lx + bx, by, lz + bz, rx + bx, by, rz + bz, rx + tx, ty, rz + tz, lx + tx, ty, lz + tz};
        // Leant back round the middle of the figure, so that shader packs light it from above too:
        // half way up from the feet, or the middle of the picture for one facing the camera.
        float middle = board.facing ? (top + bottom) / 2 : top / 2;
        ImpostorLean.lean(board.x, board.y, board.z, board.x + up[0] * middle, board.y + up[1] * middle,
                board.z + up[2] * middle, corners);
        vertex(at, out, board.light, corners[0], corners[1], corners[2], u0, vBottom);
        vertex(at, out, board.light, corners[3], corners[4], corners[5], u1, vBottom);
        vertex(at, out, board.light, corners[6], corners[7], corners[8], u1, vTop);
        vertex(at, out, board.light, corners[9], corners[10], corners[11], u0, vTop);
    }

    private static void vertex(PoseStack.Pose at, VertexConsumer out, int light, float x, float y, float z,
                               float u, float v) {
        // Lit as a top face, which the game's light leaves at full brightness: the pictures
        // already carry the shading a model gets in the world. Only the light where the
        // figure stands (sky, torches, night) is applied on top. Shader packs never see this
        // normal: Iris takes the quad's own, which ImpostorLean turns up.
        out.addVertex(at, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(at, 0f, 1f, 0f);
    }
}
