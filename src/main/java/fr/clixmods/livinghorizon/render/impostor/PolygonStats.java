package fr.clixmods.livinghorizon.render.impostor;

import fr.clixmods.livinghorizon.render.GhostRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * How many polygons the mod's own entities (the distant players and mobs, never the ones
 * the game draws itself) put on the screen, for the debug screen.
 *
 * <p>A model is only turned into polygons late, when the frame's features are drawn: the
 * renderer announces which entity it is drawing ({@link #enter}) and every part of the
 * model adds its faces ({@link #model}). A polygon is one quad of a model, two triangles
 * to the graphics card. An impostor is one quad.
 */
public final class PolygonStats {
    private static boolean counting;
    private static int models, modelPolygons, impostors;
    private static int lastModels, lastModelPolygons, lastImpostors;

    private PolygonStats() {
    }

    /** A frame begins: what the one before counted is what is shown. */
    public static void beginFrame() {
        lastModels = models;
        lastModelPolygons = modelPolygons;
        lastImpostors = impostors;
        models = 0;
        modelPolygons = 0;
        impostors = 0;
    }

    /** The renderer starts drawing the model of this state (any object the game gives it). */
    public static void enter(Object state) {
        counting = state instanceof EntityRenderState entity && GhostRenderer.transform(entity) != null;
        if (counting) models++;
    }

    public static void leave() {
        counting = false;
    }

    /** Whether the model being drawn is one of the mod's. */
    public static boolean counting() {
        return counting;
    }

    /** One part of a model is drawn, with this many faces. */
    public static void model(int polygons) {
        modelPolygons += polygons;
    }

    /** An impostor is drawn: one quad. */
    public static void impostor() {
        impostors++;
    }

    /** Models drawn in full last frame. */
    public static int models() {
        return lastModels;
    }

    /** Polygons of those models. */
    public static int modelPolygons() {
        return lastModelPolygons;
    }

    /** Impostors drawn last frame. */
    public static int impostors() {
        return lastImpostors;
    }

    /** Every polygon of the mod's entities last frame. */
    public static int polygons() {
        return lastModelPolygons + lastImpostors;
    }

    /** The same, as the graphics card sees it. */
    public static int triangles() {
        return polygons() * 2;
    }
}
