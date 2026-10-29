package fr.clixmods.livinghorizon.render.impostor;

import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.PartTree;
import fr.clixmods.livinghorizon.render.lod.LodHolder;
import fr.clixmods.livinghorizon.render.lod.LodRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * How many polygons the mod's own entities (the distant players and mobs, never the ones
 * the game draws itself) put on the screen, for the debug screen.
 *
 * <p>A model is only turned into polygons late, when the frame's features are drawn: the
 * renderer announces which entity it is drawing ({@link #enter}) and every part of the
 * model adds its faces ({@link #model}). A polygon is one quad of a model, two triangles
 * to the graphics card. An impostor is one quad.
 *
 * <p>Each figure is drawn at a level of detail: {@code 0} is its full model, the last level
 * ({@link #IMPOSTOR}) its impostor. Next to what was drawn, the stats keep what the same
 * figures would have cost as full models ({@link #fullPolygons}): an impostor counts as the
 * model it stands for, measured when that figure, or one of its kind, was last drawn as a
 * model, and read from its renderer's model before that.
 */
public final class PolygonStats {
    /** Levels of detail: the full model, three simpler ones, the impostor. */
    public static final int LEVELS = 5;
    /** The impostor's level, the last. */
    public static final int IMPOSTOR = LEVELS - 1;
    /** The tint of each level with {@code debugLodColors} on: none, green, yellow, orange, red. */
    private static final int[] TINTS = {0xFFFFFF, 0x7DFF6A, 0xFFE14A, 0xFF9C3A, 0xFF4A4A};

    private static boolean counting;
    private static @Nullable Object current;
    /** The counts of the figure being drawn, kept at hand: added to for every part. */
    private static int @Nullable [] counts;
    private static int models, modelPolygons, modelTriangles, impostors, fullPolygons;
    private static int lastModels, lastModelPolygons, lastModelTriangles, lastImpostors, lastFullPolygons;
    private static final int[] LEVEL_FIGURES = new int[LEVELS], LAST_LEVEL_FIGURES = new int[LEVELS];

    /** The entity each of this frame's states was extracted from. */
    private static final Map<Object, Entity> OWNERS = new IdentityHashMap<>();
    /**
     * For each of the mod's states this frame, all its layers together: the quads drawn, then
     * the quads its full models would have cost.
     */
    private static final Map<Object, int[]> FIGURES = new IdentityHashMap<>();
    /**
     * The same for its kind of figure, for one never drawn as a model itself: the adult's,
     * then the baby's, 0 while unknown.
     */
    private static final Map<EntityType<?>, int[]> BY_KIND = new IdentityHashMap<>();
    /** Read from the renderer's model, for a kind never drawn as a model yet, -1 while not read. */
    private static final Map<EntityType<?>, int[]> ESTIMATED = new IdentityHashMap<>();

    private static int age(Entity entity) {
        return entity instanceof LivingEntity living && living.isBaby() ? 1 : 0;
    }

    private PolygonStats() {
    }

    /** A frame begins: what the one before counted is what is shown. */
    public static void beginFrame() {
        for (Map.Entry<Object, int[]> figure : FIGURES.entrySet()) {
            int quads = figure.getValue()[1];
            fullPolygons += quads;
            LEVEL_FIGURES[LodRenderer.level(figure.getKey())]++;
            Entity owner = OWNERS.get(figure.getKey());
            if (owner == null || quads == 0) continue;
            // What it costs as a full model, kept on the entity; its kind's only when it changed.
            LodHolder holder = (LodHolder) owner;
            if (holder.livinghorizon$fullQuads() == quads) continue;
            holder.livinghorizon$fullQuads(quads);
            BY_KIND.computeIfAbsent(owner.getType(), type -> new int[2])[age(owner)] = quads;
        }
        FIGURES.clear();
        OWNERS.clear();
        lastModels = models;
        lastModelPolygons = modelPolygons;
        lastModelTriangles = modelTriangles;
        lastImpostors = impostors;
        lastFullPolygons = fullPolygons;
        System.arraycopy(LEVEL_FIGURES, 0, LAST_LEVEL_FIGURES, 0, LEVELS);
        Arrays.fill(LEVEL_FIGURES, 0);
        models = 0;
        modelPolygons = 0;
        modelTriangles = 0;
        impostors = 0;
        fullPolygons = 0;
    }

    /** The state of a figure is made from this entity: what it costs is remembered for it. */
    public static void owner(Object state, Entity entity) {
        OWNERS.put(state, entity);
    }

    /** The renderer starts drawing the model of this state (any object the game gives it). */
    public static void enter(Object state) {
        LodRenderer.enter(state);
        counting = state instanceof EntityRenderState entity && GhostRenderer.transform(entity) != null;
        if (!counting) return;
        current = state;
        int[] figure = FIGURES.get(state);
        if (figure == null) {
            FIGURES.put(state, figure = new int[2]);
            models++;
        }
        counts = figure;
    }

    public static void leave() {
        counting = false;
        current = null;
        counts = null;
        LodRenderer.leave();
    }

    /** Whether the model being drawn is one of the mod's. */
    public static boolean counting() {
        return counting;
    }

    /** The state of the mod's figure being drawn, null for none. */
    public static @Nullable Object current() {
        return current;
    }

    /** The entity this frame's state was made from, null for none. */
    public static @Nullable Entity ownerOf(Object state) {
        return OWNERS.get(state);
    }

    /** Faces of its model drawn for the figure being drawn: a quad, two triangles, each. */
    public static void model(int polygons) {
        modelPolygons += polygons;
        modelTriangles += polygons * 2;
        int[] figure = counts;
        if (figure != null) figure[0] += polygons;
    }

    /**
     * A simplified group drawn for the figure being drawn: {@code triangles} triangles, handed
     * to the game as {@code quads} quads (two triangles that share an edge make one).
     */
    public static void lod(int triangles, int quads) {
        modelPolygons += quads;
        modelTriangles += triangles;
        int[] figure = counts;
        if (figure != null) figure[0] += quads;
    }

    /** Faces one part of its full model has, drawn or not. */
    public static void full(int polygons) {
        int[] figure = counts;
        if (figure != null) figure[1] += polygons;
    }

    /** An impostor is drawn: one quad, standing for a model of {@code full} quads. */
    public static void impostor(int full) {
        impostors++;
        fullPolygons += Math.max(1, full);
        LEVEL_FIGURES[IMPOSTOR]++;
    }

    /**
     * Quads an entity costs as a full model: what it cost when last drawn so, or one of its
     * kind, or what its renderer's model holds; 1 when none of these is known.
     */
    public static int fullQuads(Entity entity) {
        int known = ((LodHolder) entity).livinghorizon$fullQuads();
        if (known > 0) return known;
        int age = age(entity);
        int[] kind = BY_KIND.get(entity.getType());
        if (kind != null && kind[age] > 0) return kind[age];
        int[] estimated = ESTIMATED.computeIfAbsent(entity.getType(), type -> new int[]{-1, -1});
        if (estimated[age] < 0) estimated[age] = estimate(entity);
        return Math.max(1, estimated[age]);
    }

    /** The colour a figure at {@code level} is tinted with, RGB, white for none. */
    public static int tint(int level) {
        return TINTS[Math.max(0, Math.min(IMPOSTOR, level))];
    }

    /** Models drawn in full last frame: figures, each counted once whatever its layers. */
    public static int models() {
        return lastModels;
    }

    /** Quads of those models handed to the game. */
    public static int modelPolygons() {
        return lastModelPolygons;
    }

    /** Impostors drawn last frame. */
    public static int impostors() {
        return lastImpostors;
    }

    /** Every quad of the mod's entities the game was handed last frame. */
    public static int polygons() {
        return lastModelPolygons + lastImpostors;
    }

    /** The triangles they make: two a quad, but for the simplified levels, whose quads may be lone triangles. */
    public static int triangles() {
        return lastModelTriangles + lastImpostors * 2;
    }

    /** Polygons the same figures would have cost last frame, each drawn as its full model. */
    public static int fullPolygons() {
        return lastFullPolygons;
    }

    /** The same, in triangles. */
    public static int fullTriangles() {
        return lastFullPolygons * 2;
    }

    /** Percent of the triangles the levels of detail saved last frame, 0 with nothing drawn. */
    public static int gain() {
        return lastFullPolygons == 0 ? 0 : (int) Math.round(100.0 * (fullTriangles() - triangles()) / fullTriangles());
    }

    /** Figures drawn at {@code level} last frame. */
    public static int figures(int level) {
        return LAST_LEVEL_FIGURES[level];
    }

    /**
     * Quads of the model the renderer of {@code entity} draws it with, its visible parts as
     * they stand: a guess, without layers (armour, saddle), until the figure is drawn as a model.
     */
    @SuppressWarnings("rawtypes")
    private static int estimate(Entity entity) {
        try {
            Object renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (!(renderer instanceof LivingEntityRenderer living)) return 0;
            Set<ModelPart> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            //? if >=1.21.2 {
            return quads(living.getModel().root(), seen);
            //?} else {
            /*Object model = living.getModel();
            int quads = 0;
            // No root to ask for before 1.21.2: every part the model holds.
            for (Class<?> type = model.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || !ModelPart.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    if (field.get(model) instanceof ModelPart part) quads += quads(part, seen);
                }
            }
            return quads;
            *///?}
        } catch (Exception | LinkageError e) {
            return 0;
        }
    }

    /** Quads of a part and its children, as they would be drawn now. */
    private static int quads(ModelPart part, Set<ModelPart> seen) {
        if (!part.visible || !seen.add(part)) return 0;
        PartTree tree = (PartTree) (Object) part;
        int quads = 0;
        if (!part.skipDraw) {
            for (ModelPart.Cube cube : tree.livinghorizon$cubes()) quads += cube.polygons.length;
        }
        for (ModelPart child : tree.livinghorizon$children().values()) quads += quads(child, seen);
        return quads;
    }
}
