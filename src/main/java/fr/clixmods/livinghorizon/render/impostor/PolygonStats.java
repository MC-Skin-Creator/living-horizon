package fr.clixmods.livinghorizon.render.impostor;

import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.PartTree;
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
import java.util.WeakHashMap;

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
    private static int models, modelPolygons, impostors, fullPolygons;
    private static int lastModels, lastModelPolygons, lastImpostors, lastFullPolygons;
    private static final int[] LEVEL_FIGURES = new int[LEVELS], LAST_LEVEL_FIGURES = new int[LEVELS];

    /** The entity each of this frame's states was extracted from. */
    private static final Map<Object, Entity> OWNERS = new IdentityHashMap<>();
    /** Quads drawn this frame for each of the mod's states, all its layers together. */
    private static final Map<Object, int[]> FIGURES = new IdentityHashMap<>();
    /** What a figure cost the last time it was drawn as a full model. */
    private static final Map<Entity, Integer> BY_ENTITY = new WeakHashMap<>();
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
            int quads = figure.getValue()[0];
            fullPolygons += quads;
            LEVEL_FIGURES[0]++;
            Entity owner = OWNERS.get(figure.getKey());
            if (owner == null || quads == 0) continue;
            BY_ENTITY.put(owner, quads);
            BY_KIND.computeIfAbsent(owner.getType(), type -> new int[2])[age(owner)] = quads;
        }
        FIGURES.clear();
        OWNERS.clear();
        lastModels = models;
        lastModelPolygons = modelPolygons;
        lastImpostors = impostors;
        lastFullPolygons = fullPolygons;
        System.arraycopy(LEVEL_FIGURES, 0, LAST_LEVEL_FIGURES, 0, LEVELS);
        Arrays.fill(LEVEL_FIGURES, 0);
        models = 0;
        modelPolygons = 0;
        impostors = 0;
        fullPolygons = 0;
    }

    /** The state of a figure is made from this entity: what it costs is remembered for it. */
    public static void owner(Object state, Entity entity) {
        OWNERS.put(state, entity);
    }

    /** The renderer starts drawing the model of this state (any object the game gives it). */
    public static void enter(Object state) {
        counting = state instanceof EntityRenderState entity && GhostRenderer.transform(entity) != null;
        if (!counting) return;
        current = state;
        if (!FIGURES.containsKey(state)) {
            FIGURES.put(state, new int[1]);
            models++;
        }
    }

    public static void leave() {
        counting = false;
        current = null;
    }

    /** Whether the model being drawn is one of the mod's. */
    public static boolean counting() {
        return counting;
    }

    /** One part of a model is drawn, with this many faces. */
    public static void model(int polygons) {
        modelPolygons += polygons;
        int[] figure = current == null ? null : FIGURES.get(current);
        if (figure != null) figure[0] += polygons;
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
        Integer known = BY_ENTITY.get(entity);
        if (known != null) return known;
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
        return lastFullPolygons == 0 ? 0 : (int) Math.round(100.0 * (lastFullPolygons - polygons()) / lastFullPolygons);
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
