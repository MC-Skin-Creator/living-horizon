package fr.clixmods.livinghorizon.render.lod;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Draws the mod's distant mobs at their level of detail: the simpler models {@link LodLevels}
 * makes, chosen by distance, before the impostor.
 *
 * <p>A figure's level is chosen when the mod picks its figures ({@link #choose}); a level is
 * only used once every model and layer the figure was last drawn with has it ready (its levels
 * made, its boxes baked for that layer's texture), otherwise the figure keeps its full model.
 * As a model is drawn, each of its parts draws the cubes its level keeps ({@code ModelPartMixin}),
 * and the merged boxes anchored to it are queued with the part's pose, light and colour; they are
 * drawn after the models, one batch per baked texture, the way the impostors are
 * ({@link #submit}). Only the mod's own figures, and no player yet: the game's entities and other
 * mods' rendering are never touched.
 */
public final class LodRenderer {
    /** The most texels down a baked texture. */
    private static final int MAX_HEIGHT = 4096;
    /** Share of a texture's visible texels that may be half transparent: more, and it is not cut out. */
    private static final double TRANSLUCENT = 0.02;
    private static final int WORKING = 0, PIXELS = 1, READY = 2, FAILED = -1;
    /** Floats per baked face: four corners of x, y, z (in blocks), u, v, then its normal. */
    private static final int FACE = 23;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Living Horizon levels of detail");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private LodRenderer() {
    }

    /**
     * The level a figure takes, before its impostor: one more each time it is {@code lodSpacing}
     * farther, from {@code lodDistance}; with {@code lodPixels}, one more each time it is that
     * much smaller on screen too, the larger of the two; never past {@code lodLevels}.
     *
     * @param pixels how tall it is on screen
     */
    public static int levelFor(double distance, double pixels, FarConfig config) {
        int most = Math.max(1, Math.min(config.lodLevels, LodLevels.MODEL_LEVELS - 1));
        double step = Math.max(1.05, config.lodSpacing / 100.0);
        int level = 0;
        for (double from = config.lodDistance; level < most && distance >= from; from *= step) level++;
        if (config.lodPixels > 0) {
            int small = 0;
            for (double below = config.lodPixels; small < most && pixels < below; below /= step) small++;
            level = Math.max(level, small);
        }
        return level;
    }

    /** A level every figure takes whatever its distance, -1 for none: for the game tests' close-ups. */
    private static int forced = -1;

    public static void forceLevelForTests(int level) {
        forced = level;
    }

    // ---------------------------------------------------------------- one frame

    /** The level each of this frame's figures is drawn at, and the one it would be at once ready. */
    private static final Map<Object, Integer> LEVELS = new IdentityHashMap<>(), WANTED = new IdentityHashMap<>();
    /** The baked textures this frame's figures were chosen with: one batch each. */
    private static final Set<Bake> ACTIVE = new LinkedHashSet<>();
    /** The baked textures given boxes this frame. */
    private static final List<Bake> DRAWING = new ArrayList<>();
    /** The baked textures given boxes since the last {@link #flush}. */
    private static final List<Bake> PENDING = new ArrayList<>();
    private static int frame;
    private static boolean dirty;
    /** The share of polygons the levels made so far keep, {@code lodKeep}. */
    private static int keptFor = -1;

    /** The level a state of this frame is drawn at, 0 for its full model. */
    public static int level(Object state) {
        return LEVELS.getOrDefault(state, 0);
    }

    public static void beginFrame() {
        frame++;
        LEVELS.clear();
        WANTED.clear();
        ACTIVE.clear();
        for (Bake bake : DRAWING) {
            bake.count = 0;
            bake.drawing = false;
        }
        DRAWING.clear();
        PENDING.clear();
        // The polygons each level keeps changed: every level is made again.
        if (FarConfig.get().lodKeep != keptFor) {
            keptFor = FarConfig.get().lodKeep;
            dirty = true;
        }
        if (dirty) clear();
        upload();
    }

    /**
     * A figure is in this frame, {@code distance} blocks away: the level it is drawn at, if it
     * is one of the mod's mobs and every model it was last drawn with has that level ready.
     */
    public static void choose(Object state, double distance, double pixels) {
        Entity owner = PolygonStats.ownerOf(state);
        FarConfig config = FarConfig.get();
        if (owner == null || owner instanceof Player || !config.mobLods) return;
        int wanted = forced >= 0 ? forced : levelFor(distance, pixels, config);
        if (wanted == 0) return;
        WANTED.put(state, wanted);
        Layers layers = LAYERS.get(owner);
        if (layers == null) return;
        // Chosen before the figure is drawn this frame: what it was drawn with last.
        Map<ModelPart, Seen> seen = layers.frame == frame ? layers.last : layers.now;
        ModelPart first = layers.frame == frame ? layers.firstLast : layers.firstNow;
        boolean failed = layers.frame == frame ? layers.lastFailed : layers.failed;
        if (failed || seen.isEmpty()) return;
        List<Bake> bakes = CHOSEN;
        bakes.clear();
        boolean ready = true;
        // Its keys rather than its entries: an identity map makes an entry each step.
        for (ModelPart root : seen.keySet()) {
            Seen model = seen.get(root);
            long follows = follows(root, seen, first);
            LodSet set = follows == -1 ? null : set(root, model, follows);
            if (set == null || !set.ready(wanted)) {
                ready = false;
                continue;
            }
            for (int i = 0; i < model.layers.size(); i++) {
                Bake bake = bake(set, model.layers.get(i));
                if (bake.state != READY) ready = false;
                bakes.add(bake);
            }
        }
        if (!ready) return;
        LEVELS.put(state, wanted);
        ACTIVE.addAll(bakes);
    }

    // ---------------------------------------------------------------- what a figure is drawn with

    /** How one of a figure's models was drawn in a frame: which of its parts, and with which layers. */
    private static final class Seen {
        long shown;
        /** Its layers, each once; a list kept from frame to frame, so that a frame makes none. */
        final List<Layer> layers = new ArrayList<>();
        /** What its levels are made for, the parts drawn and the textures in any order, as a number. */
        long key;

        Seen reset(long shown) {
            this.shown = shown;
            layers.clear();
            key = mix(shown);
            return this;
        }

        void add(Layer layer) {
            if (layers.contains(layer)) return;
            layers.add(layer);
            // A sum: the same textures in any order give the same key.
            key += mix(layer.texture().toString().hashCode() * 0x9E3779B97F4A7C15L);
        }

        /** Its textures in the order its layers are drawn, each once. */
        List<Identifier> drawOrder() {
            List<Identifier> textures = new ArrayList<>();
            for (Layer layer : layers) {
                if (!textures.contains(layer.texture())) textures.add(layer.texture());
            }
            return textures;
        }

        /** Its textures, in a set order: when its levels are made. */
        List<Identifier> textures() {
            List<Identifier> textures = new ArrayList<>();
            for (Layer layer : layers) {
                if (!textures.contains(layer.texture())) textures.add(layer.texture());
            }
            textures.sort(Comparator.comparing(Identifier::toString));
            return textures;
        }

        static long mix(long value) {
            value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdL;
            value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53L;
            return value ^ (value >>> 33);
        }
    }

    /**
     * The models a figure was drawn with in its last two frames: its levels are chosen from the
     * last, and a model's levels are made for the textures it was drawn with then.
     */
    private static final class Layers {
        int frame;
        boolean failed, lastFailed;
        Map<ModelPart, Seen> now = new IdentityHashMap<>(), last = new IdentityHashMap<>();
        /** The first model drawn in each record: the figure's own, which the others follow. */
        @Nullable ModelPart firstNow, firstLast;
        /** Records of frames gone, kept for the next ones: a frame makes none. */
        private final List<Seen> spare = new ArrayList<>();

        /** Its first model this frame: this frame's record starts. */
        void roll() {
            if (frame == LodRenderer.frame) return;
            frame = LodRenderer.frame;
            Map<ModelPart, Seen> old = last;
            for (Seen seen : old.values()) spare.add(seen);
            old.clear();
            last = now;
            now = old;
            firstLast = firstNow;
            firstNow = null;
            lastFailed = failed;
            failed = false;
        }

        Seen seen(ModelPart root, long shown) {
            Seen seen = now.get(root);
            if (seen == null) {
                seen = (spare.isEmpty() ? new Seen() : spare.remove(spare.size() - 1)).reset(shown);
                if (now.isEmpty()) firstNow = root;
                now.put(root, seen);
            }
            return seen;
        }
    }

    private static final Map<Entity, Layers> LAYERS = new WeakHashMap<>();

    /** What the layer being drawn is textured with, and whether it glows. */
    private record Layer(Identifier texture, boolean eyes) {
    }

    private static @Nullable Layer layer;
    private static final Map<RenderType, Optional<Layer>> LAYER_OF = new IdentityHashMap<>();
    private static final Pattern TEXTURE = Pattern.compile("([a-z0-9_.-]+):(textures/[a-z0-9_./-]+\\.png)");

    /** A figure of the mod starts being drawn: what its layers are is not known yet. */
    public static void figure() {
        layer = null;
        depth = 0;
        drawing = null;
    }

    /**
     * The next model is drawn with this render type, through an atlas sprite or not. A layer
     * whose texture cannot be read from its type (an atlas, a glint) keeps its figure whole.
     */
    public static void layer(RenderType type, boolean sprite) {
        layer = sprite ? null : LAYER_OF.computeIfAbsent(type, LodRenderer::read).orElse(null);
    }

    /** The texture and the kind of layer a render type draws, as its description names them. */
    private static Optional<Layer> read(RenderType type) {
        String text = type.toString();
        String name = text.toLowerCase(Locale.ROOT);
        boolean eyes = name.contains("eyes");
        boolean plain = name.contains("entity_cutout") || name.contains("entity_solid") || name.contains("armor_cutout")
                || name.contains("entity_smooth_cutout") || name.contains("entity_translucent") && !name.contains("emissive");
        Matcher texture = TEXTURE.matcher(text);
        if ((eyes || plain) && texture.find()) {
            Identifier id = Identifier.tryParse(texture.group(1) + ":" + texture.group(2));
            if (id != null) return Optional.of(new Layer(id, eyes));
        }
        LivingHorizonClient.LOGGER.debug("Levels of detail: a layer drawn as {} keeps its figure whole", text);
        return Optional.empty();
    }

    // ---------------------------------------------------------------- the models

    private static final Map<ModelPart, Long2ObjectOpenHashMap<LodSet>> SETS = new IdentityHashMap<>();
    private static final Map<LodSet, Map<Identifier, Bake>> BAKES = new IdentityHashMap<>();

    /** The levels of a model drawn as {@code seen} says, made already or being made; null for none yet. */
    private static @Nullable LodSet set(ModelPart root, Seen seen, long follows) {
        Long2ObjectOpenHashMap<LodSet> sets = SETS.get(root);
        return sets == null ? null : sets.get(seen.key ^ follows);
    }

    /**
     * The levels a model of a figure follows, from one of its records: none (0) for the figure's
     * first model, else the first model's ({@link LodLevels#follow}), whose number goes into the
     * key; -1 while those are not made yet.
     */
    private static long follows(ModelPart root, Map<ModelPart, Seen> record, @Nullable ModelPart first) {
        if (first == null || root == first) return 0;
        Seen own = record.get(first);
        LodSet template = own == null ? null : set(first, own, 0);
        return template == null || template.plan() == null ? -1 : Seen.mix(template.id) | 1;
    }

    /** The first model's levels, which {@code root}'s follow; null for the first model itself. */
    private static @Nullable LodSet template(ModelPart root, Map<ModelPart, Seen> record, @Nullable ModelPart first) {
        if (first == null || root == first) return null;
        Seen own = record.get(first);
        return own == null ? null : set(first, own, 0);
    }

    /** How deep in a model's parts the drawing is: 1 at the part it was asked to draw. */
    private static int depth;
    /** What the model being drawn draws at its level; null while it draws its full model. */
    private static @Nullable LodSet drawing;
    private static @Nullable Bake drawingBake;
    private static int drawingLevel;

    /** The game starts drawing a part of one of the mod's figures, and the parts under it. */
    public static void enterPart(ModelPart part) {
        if (depth++ == 0) beginModel(part);
    }

    public static void leavePart() {
        if (depth > 0 && --depth == 0) drawing = null;
    }

    private static void beginModel(ModelPart root) {
        drawing = null;
        Object state = PolygonStats.current();
        Entity owner = state == null ? null : PolygonStats.ownerOf(state);
        if (owner == null || !WANTED.containsKey(state)) return;
        Layers layers = LAYERS.computeIfAbsent(owner, entity -> new Layers());
        layers.roll();
        Layer drawn = layer;
        if (drawn == null) {
            layers.failed = true;
            return;
        }
        long shown = ModelReader.drawn(root, ModelReader.GAME);
        Seen seen = layers.seen(root, shown);
        // A layer that hides other parts of the same model than the first: not one set of boxes for both.
        if (seen.shown != shown) layers.failed = true;
        seen.add(drawn);
        Seen before = layers.last.get(root);
        if (before == null || before.shown != shown) return;
        // A model drawn over the figure's first (wool, a saddle) is grouped as the first is.
        long follows = follows(root, layers.last, layers.firstLast);
        if (follows == -1) return;
        LodSet set = set(root, before, follows);
        if (set == null) {
            LodSet template = template(root, layers.last, layers.firstLast);
            LodSet.Plan follow = template == null ? null : template.plan();
            // Made for the textures it was drawn with last frame, the parts as the game draws them now.
            LodSet made = new LodSet(ModelReader.read(root, ModelReader.GAME, true), owner.getType().getDescription(),
                    before.drawOrder());
            SETS.computeIfAbsent(root, part -> new Long2ObjectOpenHashMap<>()).put(before.key ^ follows, made);
            List<Identifier> textures = before.textures();
            double ratio = FarConfig.get().lodKeep / 100.0;
            WORKER.execute(() -> generate(made, textures, ratio, follow));
            set = made;
        }
        if (set.failed()) {
            layers.failed = true;
            return;
        }
        int level = level(state);
        if (level == 0 || !set.ready(level)) return;
        Bake bake = bake(set, drawn);
        if (bake.state != READY) return;
        drawing = set;
        drawingBake = bake;
        drawingLevel = level;
    }

    /** Makes a model's levels from the textures it is drawn with; away from the render thread. */
    private static void generate(LodSet set, List<Identifier> textures, double ratio, LodSet.@Nullable Plan follow) {
        List<LodBake.Pixels> pixels = new ArrayList<>();
        for (Identifier texture : textures) {
            LodBake.Pixels read = pixels(texture);
            if (read == null) {
                set.fail();
                return;
            }
            pixels.add(read);
        }
        set.generate(pixels, ratio, follow);
    }

    /**
     * What a part of the model being drawn draws at its figure's level, null for its own cubes
     * as the game draws them.
     */
    public static LodSet.@Nullable PartDraw part(ModelPart part) {
        LodSet set = drawing;
        return set == null ? null : set.draw(part, drawingLevel);
    }

    /** The colour a figure drawn at a simpler level is drawn in: tinted with LOD Colours. */
    public static int tint(int argb) {
        if (!FarConfig.get().debugLodColors || drawing == null) return argb;
        int tint = PolygonStats.tint(drawingLevel);
        int r = ((argb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((argb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (argb & 0xFF) * (tint & 0xFF) / 255;
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** The merged boxes a part anchors at its level, queued with its pose, light and colour. */
    public static void queue(LodSet.PartDraw draw, PoseStack.Pose pose, int light, int overlay, int argb) {
        Bake bake = drawingBake;
        if (draw.merged().length == 0 || bake == null || bake.faces == null) return;
        float[][] groups = bake.faces[drawingLevel];
        for (int group : draw.merged()) {
            float[] faces = groups[group];
            if (faces == null || faces.length == 0) continue;
            if (!bake.drawing) {
                bake.drawing = true;
                DRAWING.add(bake);
            }
            if (bake.count == 0) PENDING.add(bake);
            if (bake.count == bake.entries.size()) bake.entries.add(new Entry());
            Entry entry = bake.entries.get(bake.count++);
            entry.pose.set(pose.pose());
            entry.normal.set(pose.normal());
            entry.faces = faces;
            entry.light = light;
            entry.overlay = overlay;
            entry.argb = argb;
            PolygonStats.model(faces.length / FACE);
        }
    }

    // ---------------------------------------------------------------- the boxes

    /** A merged box to draw: its faces, where its part was, its light and colour. */
    private static final class Entry {
        final Matrix4f pose = new Matrix4f();
        final Matrix3f normal = new Matrix3f();
        float[] faces;
        int light, overlay, argb;
    }

    //? if <1.20.5
    /*private static final Matrix4f IDENTITY = new Matrix4f();*/

    /** The corner and normal being written: one at a time, on the render thread. */
    private static final Vector3f POINT = new Vector3f(), NORMAL = new Vector3f();

    /** The boxes of one model baked with one texture, and their batch this frame. */
    private static final class Bake {
        final LodSet set;
        final Layer layer;
        volatile int state = WORKING;
        int width, height;
        int @Nullable [] argb;
        /** For each level and group, its faces ({@link #FACE} floats each); null where not merged. */
        float @Nullable [][][] faces;
        @Nullable Identifier page;
        @Nullable RenderType type;
        final List<Entry> entries = new ArrayList<>();
        int count;
        /** Given boxes this frame, so in {@link #DRAWING}. */
        boolean drawing;
        final Sink.Geometry geometry = (at, out) -> draw(out);

        Bake(LodSet set, Layer layer) {
            this.set = set;
            this.layer = layer;
        }

        /** This frame's boxes, from where their parts were drawn: the pose they are handed is not theirs. */
        void draw(VertexConsumer out) {
            Vector3f point = POINT, normal = NORMAL;
            for (int i = 0; i < count; i++) {
                Entry entry = entries.get(i);
                float[] faces = entry.faces;
                int a = entry.argb >>> 24, r = (entry.argb >> 16) & 0xFF, g = (entry.argb >> 8) & 0xFF, b = entry.argb & 0xFF;
                for (int f = 0; f + FACE <= faces.length; f += FACE) {
                    entry.normal.transform(normal.set(faces[f + 20], faces[f + 21], faces[f + 22])).normalize();
                    for (int k = 0; k < 4; k++) {
                        int v = f + k * 5;
                        entry.pose.transformPosition(point.set(faces[v], faces[v + 1], faces[v + 2]));
                        //? if >=1.20.5 {
                        out.addVertex(point.x, point.y, point.z)
                                .setColor(r, g, b, a)
                                .setUv(faces[v + 3], faces[v + 4])
                                .setOverlay(entry.overlay)
                                .setLight(entry.light)
                                .setNormal(normal.x, normal.y, normal.z);
                        //?} else {
                        /*// Before 1.20.5 a vertex is written through a matrix: the identity, it is placed already.
                        out.vertex(IDENTITY, point.x, point.y, point.z).color(r, g, b, a).uv(faces[v + 3], faces[v + 4])
                                .overlayCoords(entry.overlay).uv2(entry.light).normal(normal.x, normal.y, normal.z).endVertex();
                        *///?}
                    }
                }
            }
            count = 0;
        }
    }

    /** The boxes of a model baked with a layer's texture, asked for the first time it is needed. */
    private static Bake bake(LodSet set, Layer layer) {
        Map<Identifier, Bake> byTexture = BAKES.computeIfAbsent(set, s -> new HashMap<>());
        Bake bake = byTexture.get(layer.texture());
        if (bake == null) {
            Bake made = new Bake(set, layer);
            byTexture.put(layer.texture(), made);
            WORKER.execute(() -> bakeAway(made));
            bake = made;
        }
        return bake;
    }

    /** Bakes every merged box of every level from the texture, and packs them on one; away from the render thread. */
    private static void bakeAway(Bake bake) {
        try {
            LodSet.Plan plan = bake.set.plan();
            LodBake.Pixels texture = pixels(bake.layer.texture());
            if (plan == null || texture == null) {
                bake.state = FAILED;
                return;
            }
            record Placed(int level, int group, LodBake.Face face) {
            }
            // Solid boxes for the model's first layer; those drawn over it keep their holes.
            boolean overlay = !bake.set.layers.isEmpty() && !bake.set.layers.get(0).equals(bake.layer.texture());
            List<Placed> placed = new ArrayList<>();
            List<LodLevels.Level> levels = plan.levels();
            for (int level = 1; level < levels.size(); level++) {
                List<LodLevels.Group> groups = levels.get(level).groups();
                for (int g = 0; g < groups.size(); g++) {
                    if (!groups.get(g).merged()) continue;
                    for (LodBake.Face face : LodBake.bake(plan.model(), groups.get(g), texture, overlay)) {
                        placed.add(new Placed(level, g, face));
                    }
                }
            }
            // Shelves, the tallest faces first; a texel of room round each.
            int width = 256;
            for (Placed p : placed) width = Math.max(width, p.face().pixels().width() + 1);
            List<Placed> sorted = new ArrayList<>(placed);
            sorted.sort(Comparator.comparingInt((Placed p) -> -p.face().pixels().height()));
            Map<Placed, int[]> at = new IdentityHashMap<>();
            int x = 0, y = 0, row = 0;
            for (Placed p : sorted) {
                LodBake.Pixels pixels = p.face().pixels();
                if (x + pixels.width() > width) {
                    x = 0;
                    y += row + 1;
                    row = 0;
                }
                at.put(p, new int[]{x, y});
                x += pixels.width() + 1;
                row = Math.max(row, pixels.height());
            }
            int height = Math.max(1, y + row);
            if (height > MAX_HEIGHT) {
                bake.state = FAILED;
                return;
            }
            int[] argb = new int[width * height];
            for (Placed p : placed) {
                LodBake.Pixels pixels = p.face().pixels();
                int[] corner = at.get(p);
                for (int row2 = 0; row2 < pixels.height(); row2++) {
                    System.arraycopy(pixels.argb(), row2 * pixels.width(), argb, (corner[1] + row2) * width + corner[0],
                            pixels.width());
                }
            }
            float[][][] faces = new float[levels.size()][][];
            for (int level = 1; level < levels.size(); level++) faces[level] = new float[levels.get(level).groups().size()][];
            Map<Long, List<Placed>> byGroup = new HashMap<>();
            for (Placed p : placed) byGroup.computeIfAbsent((long) p.level() << 32 | p.group(), k -> new ArrayList<>()).add(p);
            // Counter-clockwise seen from outside, the side drawn: the game turns its models
            // upside down by mirroring two axes at once, which keeps the winding as it is.
            double[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
            for (List<Placed> group : byGroup.values()) {
                float[] out = new float[group.size() * FACE];
                int o = 0;
                for (Placed p : group) {
                    LodBake.Face face = p.face();
                    int[] corner = at.get(p);
                    for (double[] st : corners) {
                        Vector3d point = face.corner(st[0], st[1]);
                        float[] uv = LodBake.pageUv(face, corner[0], corner[1], width, height, st[0], st[1]);
                        // Model pixels to blocks, as the game divides its cubes' corners as it draws them.
                        out[o++] = (float) point.x / 16;
                        out[o++] = (float) point.y / 16;
                        out[o++] = (float) point.z / 16;
                        out[o++] = uv[0];
                        out[o++] = uv[1];
                    }
                    Vector3d facing = new Vector3d(face.right()).cross(face.up());
                    out[o++] = (float) facing.x;
                    out[o++] = (float) facing.y;
                    out[o++] = (float) facing.z;
                }
                Placed first = group.get(0);
                faces[first.level()][first.group()] = out;
            }
            bake.width = width;
            bake.height = height;
            bake.argb = argb;
            bake.faces = faces;
            bake.state = PIXELS;
        } catch (RuntimeException | LinkageError e) {
            LivingHorizonClient.LOGGER.warn("Levels of detail: could not bake {}", bake.layer.texture(), e);
            bake.state = FAILED;
        }
    }

    /** A model whose levels are made, for the preview screen. */
    public record Preview(net.minecraft.network.chat.Component name, LodModel model, List<LodLevels.Level> levels,
                          List<Identifier> textures) {
    }

    /** The models whose levels are made so far, by name: those of the mobs drawn far away since the game started. */
    public static List<Preview> previews() {
        List<Preview> out = new ArrayList<>();
        for (Map<Long, LodSet> sets : SETS.values()) {
            for (LodSet set : sets.values()) {
                LodSet.Plan plan = set.plan();
                if (plan != null && !set.failed()) out.add(new Preview(set.name, plan.model(), plan.levels(), set.layers));
            }
        }
        out.sort(Comparator.comparing((Preview p) -> p.name().getString()).thenComparing(p -> p.textures().toString()));
        return out;
    }

    /** A texture's pixels, ARGB, from the resource packs; null when it cannot be read or is not cut out. */
    public static LodBake.@Nullable Pixels pixels(Identifier id) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) return null;
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            int width = image.getWidth(), height = image.getHeight();
            int[] argb = new int[width * height];
            int seen = 0, half = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    //? if >=1.21.2 {
                    int pixel = image.getPixel(x, y);
                    //?} else {
                    /*int abgr = image.getPixelRGBA(x, y);
                    int pixel = (abgr & 0xFF00FF00) | ((abgr >> 16) & 0xFF) | ((abgr & 0xFF) << 16);
                    *///?}
                    argb[y * width + x] = pixel;
                    int alpha = pixel >>> 24;
                    if (alpha > 8) seen++;
                    if (alpha > 8 && alpha < 247) half++;
                }
            }
            // Glass-like layers (a slime's outside) would lose what makes them see-through.
            if (seen == 0 || half > seen * TRANSLUCENT) return null;
            return new LodBake.Pixels(width, height, argb);
        } catch (Exception e) {
            return null;
        }
    }

    /** One baked texture a frame is handed to the graphics card, on the render thread. */
    private static void upload() {
        for (Map<Identifier, Bake> byTexture : BAKES.values()) {
            for (Bake bake : byTexture.values()) {
                if (bake.state != PIXELS) continue;
                int[] argb = bake.argb;
                bake.argb = null;
                if (argb == null) {
                    bake.state = FAILED;
                    return;
                }
                try {
                    NativeImage image = new NativeImage(bake.width, bake.height, false);
                    for (int y = 0; y < bake.height; y++) {
                        for (int x = 0; x < bake.width; x++) {
                            int pixel = argb[y * bake.width + x];
                            //? if >=1.21.2 {
                            image.setPixel(x, y, pixel);
                            //?} else
                            /*image.setPixelRGBA(x, y, (pixel & 0xFF00FF00) | ((pixel >> 16) & 0xFF) | ((pixel & 0xFF) << 16));*/
                        }
                    }
                    Identifier page = Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "lod/" + pages++);
                    //? if >=1.21.5 {
                    DynamicTexture texture = new DynamicTexture(() -> "living horizon level of detail " + page, image);
                    //?} else
                    /*DynamicTexture texture = new DynamicTexture(image);*/
                    Minecraft.getInstance().getTextureManager().register(page, texture);
                    bake.page = page;
                    //? if >=26.1 {
                    /*bake.type = bake.layer.eyes() ? RenderTypes.eyes(page) : RenderTypes.entityCutoutCull(page);
                    *///?} else {
                    bake.type = bake.layer.eyes() ? RenderTypes.eyes(page) : RenderTypes.entityCutout(page);
                    //?}
                    bake.state = READY;
                } catch (RuntimeException | LinkageError e) {
                    LivingHorizonClient.LOGGER.warn("Levels of detail: could not upload a baked texture", e);
                    bake.state = FAILED;
                }
                return;
            }
        }
    }

    private static int pages;
    /** Kept from frame to frame, so that a frame makes no new list. */
    private static final List<Bake> CHOSEN = new ArrayList<>();
    private static final Set<Bake> BATCHES = new LinkedHashSet<>();

    /** The boxes queued this frame, one batch per baked texture, with the impostors. */
    public static void submit(PoseStack pose, Sink sink) {
        // From 26.2 they go with their models instead (flush): the frame may be prepared in another order.
        //? if >=26.2
        /*if (true) return;*/
        Set<Bake> batches = BATCHES;
        batches.clear();
        batches.addAll(ACTIVE);
        batches.addAll(DRAWING);
        for (Bake bake : batches) {
            if (bake.state == READY && bake.type != null) sink.draw(pose, bake.type, bake.geometry);
        }
    }

    /**
     * The boxes queued since the last call, written at once with {@code builder}'s vertices for
     * their textures: from 26.2, the group of models they belong to has just been prepared, and
     * they go into it.
     */
    public static void flush(java.util.function.Function<RenderType, VertexConsumer> builder) {
        for (Bake bake : PENDING) {
            if (bake.count > 0 && bake.type != null) bake.draw(builder.apply(bake.type));
        }
        PENDING.clear();
    }

    /** The game's textures were reloaded: the models may be new, the textures too. */
    public static void invalidate() {
        dirty = true;
    }

    /** Throws away every level and baked texture; on the render thread. */
    private static void clear() {
        dirty = false;
        for (Map<Identifier, Bake> byTexture : BAKES.values()) {
            for (Bake bake : byTexture.values()) {
                if (bake.page != null) Minecraft.getInstance().getTextureManager().release(bake.page);
                // A bake still being made away from here is dropped with its map.
                bake.state = FAILED;
            }
        }
        BAKES.clear();
        SETS.clear();
        ModelReader.forget();
        LAYERS.clear();
        LAYER_OF.clear();
    }
}
