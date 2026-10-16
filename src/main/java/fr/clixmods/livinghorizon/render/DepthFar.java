package fr.clixmods.livinghorizon.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL;

/**
 * The far clipping plane the game uses this frame, mixin included, and how its depth buffer
 * is laid out: what the code that reads that buffer directly with OpenGL needs to know.
 */
public final class DepthFar {
    /**
     * Whether the game's own depth runs from 1 at the near plane to 0 at the far one, the sky
     * being 0: its layout from 26.2. Before, 0 near and 1 far. What Blaze3D draws in, whose
     * comparisons Iris turns around itself; OpenGL calls follow {@link #reversed()}.
     */
    //? if >=26.2 {
    /*public static final boolean REVERSED = true;

    private static volatile boolean reversed = true;
    *///?} else {
    public static final boolean REVERSED = false;
    //?}

    private DepthFar() {
    }

    public static float of(Minecraft minecraft) {
        //? if >=26.1 {
        /*return minecraft.gameRenderer.getMainCamera().depthFar;
        *///?} else {
        return minecraft.gameRenderer.getDepthFar();
        //?}
    }

    /**
     * Whether the world's depth is reversed this frame. Iris 1.11.2 and older undo 26.2's
     * reversed depth while a shader pack is on, newer ones keep it: the projection says.
     */
    public static boolean reversed() {
        //? if >=26.2 {
        /*return reversed;
        *///?} else {
        return false;
        //?}
    }

    /** This frame's projection: reversed when a nearer point gets a greater depth. */
    public static void noteProjection(Matrix4fc projection) {
        //? if >=26.2 {
        /*if (projection.m23() != 0f) reversed = projection.m32() > 0f;
        *///?}
    }

    /** Whether the projection maps depth to 0..1 rather than OpenGL's -1..1. */
    public static boolean zeroToOne() {
        //? if >=26.2 {
        /*return RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        *///?} elif >=26.1 {
        /*return RenderSystem.getDevice().isZZeroToOne();
        *///?} else {
        return false;
        //?}
    }

    /**
     * Whether the game draws with OpenGL on this thread. From 26.2 it can draw with Vulkan
     * instead, and then nothing that talks to OpenGL directly may run.
     */
    public static boolean openGl() {
        try {
            GL.getCapabilities();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * The two terms of the game's projection that turn a distance in front of the camera
     * into a depth, and back: depth {@code = (a * z + b) / -z} for a view-space {@code z},
     * then halved and moved to 0..1 when the projection is OpenGL's -1..1; and the distance
     * {@code = b / (ndc + a)}. Swapped near and far when the depth is reversed.
     */
    public static float[] terms(float near, float far) {
        float n = reversed() ? far : near, f = reversed() ? near : far;
        return zeroToOne()
                ? new float[]{f / (n - f), n * f / (n - f)}
                : new float[]{(f + n) / (n - f), 2.0f * f * n / (n - f)};
    }
}
