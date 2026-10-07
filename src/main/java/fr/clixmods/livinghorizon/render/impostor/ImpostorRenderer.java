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
 * Draws the impostors: one upright picture per figure, always turned to the camera,
 * submitted with the entities so that shader packs and Voxy's depth treat it like one.
 */
public final class ImpostorRenderer {
    /**
     * One figure to draw this frame.
     *
     * @param x       where the feet are, from the camera, already pulled in when past the far plane
     * @param size    blocks along a side of its picture, scaled the same way
     * @param outline the colour of its outline, 0 for none
     */
    public record Billboard(double x, double y, double z, double size, ImpostorAtlas.Sheet sheet, int view, int light,
                            int outline) {
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
    private record Outlined(PoseStack.Pose at, Billboard board, float rightX, float rightZ) {
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
            pose.pushPose();
            pose.translate((float) board.x, (float) board.y, (float) board.z);
            collector.draw(pose, ImpostorAtlas.type(board.sheet.page()),
                    (at, out) -> quad(at, out, board, rightX, rightZ));
            //? if >=1.21.9 && <26.2
            if (board.outline != 0) OUTLINED.add(new Outlined(pose.last().copy(), board, rightX, rightZ));
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
                    outlined.rightZ);
        }
        OUTLINED.clear();
        //?}
    }

    private static void quad(PoseStack.Pose at, VertexConsumer out, Billboard board, float rightX, float rightZ) {
        float half = (float) board.size * (0.5f - INSET);
        float bottom = (float) -(1 - ImpostorAtlas.FEET) * (float) board.size;
        float top = ImpostorAtlas.FEET * (float) board.size;
        // A tile of the page, the top of the picture at the top.
        float u0 = (board.sheet.column() * ImpostorViews.VIEWS + board.view + INSET) / ImpostorAtlas.TILES_ACROSS;
        float u1 = u0 + (1f - 2 * INSET) / ImpostorAtlas.TILES_ACROSS;
        float vTop = board.sheet.row() / (float) ImpostorAtlas.TILES_DOWN;
        float vBottom = (board.sheet.row() + 1) / (float) ImpostorAtlas.TILES_DOWN;
        float lx = -rightX * half, lz = -rightZ * half, rx = rightX * half, rz = rightZ * half;
        vertex(at, out, board.light, lx, bottom, lz, u0, vBottom);
        vertex(at, out, board.light, rx, bottom, rz, u1, vBottom);
        vertex(at, out, board.light, rx, top, rz, u1, vTop);
        vertex(at, out, board.light, lx, top, lz, u0, vTop);
    }

    private static void vertex(PoseStack.Pose at, VertexConsumer out, int light, float x, float y, float z,
                               float u, float v) {
        // Lit as a top face, which the game's light leaves at full brightness: the pictures
        // already carry the shading a model gets in the world. Only the light where the
        // figure stands (sky, torches, night) is applied on top.
        out.addVertex(at, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(at, 0f, 1f, 0f);
    }
}
