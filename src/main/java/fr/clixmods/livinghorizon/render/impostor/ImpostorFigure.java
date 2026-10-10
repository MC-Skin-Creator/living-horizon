package fr.clixmods.livinghorizon.render.impostor;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.render.Sink;

/**
 * A figure the mod draws itself, baked into impostor pictures like an entity: the 3D birds.
 * Its sheet holds the same {@link ImpostorViews#VIEWS} views round it, all seen from one
 * height, {@link #elevation()}: a bird in the sky is mostly seen from below, so each height
 * it is seen from has a sheet of its own.
 */
public interface ImpostorFigure {
    /** Blocks along one side of a tile. */
    double worldSize();

    /** How far down a tile the figure's origin is, as a fraction of its height. */
    float feet();

    /** Degrees the camera looks down on the figure from: 0 level with it, negative from below. */
    float elevation();

    /** Lit as everything else baked: by the light the bake sets up, at full brightness. */
    int FULL_BRIGHT = 15728880;

    /**
     * Draws the figure at the pose's origin, turned to {@code yaw} (the game's degrees, 0
     * facing south), at full brightness ({@link #FULL_BRIGHT}).
     */
    void draw(PoseStack pose, Sink sink, float yaw);
}
