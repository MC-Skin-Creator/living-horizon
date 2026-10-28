package fr.clixmods.livinghorizon.render.lod;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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
 * drawn after the models, one batch per shared page of baked textures, the way the impostors are
 * ({@link #submit}). Only the mod's own figures, and no player yet: the game's entities and other
 * mods' rendering are never touched.
 */
public final class LodRenderer {
    /** The most texels down a baked texture. */
    private static final int MAX_HEIGHT = 4096;
    /** The side of a page the baked models share, in texels. */
    private static final int PAGE = 1024;
    /** The texels handed to the graphics card a frame at most: a few baked models. */
    private static final int UPLOAD_TEXELS = 512 * 512;
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
    /** The pages this frame's figures were chosen with: one batch each, each once ({@link Page#active}). */
    private static final List<Page> ACTIVE = new ArrayList<>();
    /** The pages given boxes this frame. */
    private static final List<Page> DRAWING = new ArrayList<>();
    /** The pages given boxes since the last {@link #flush}. */
    private static final List<Page> PENDING = new ArrayList<>();
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
        for (Page page : DRAWING) {
            page.count = 0;
            page.drawing = false;
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
        Layers layers = layers(owner, false);
        if (layers == null) return;
        // Chosen before the figure is drawn this frame: what it was drawn with last.
        boolean rolled = layers.frame == frame;
        Seen[] seen = rolled ? layers.last : layers.now;
        int count = rolled ? layers.lastCount : layers.nowCount;
        boolean failed = rolled ? layers.lastFailed : layers.failed;
        if (failed || count == 0) return;
        Bake[] bakes = CHOSEN;
        int chosen = 0;
        for (int m = 0; m < count; m++) {
            Seen model = seen[m];
            LodSet set = set(model.root, model);
            if (set == null || !set.ready(wanted)) return;
            for (int i = 0; i < model.layers.size(); i++) {
                Bake bake = bake(set, model.layers.get(i));
                if (bake.state != READY) return;
                if (chosen == bakes.length) CHOSEN = bakes = java.util.Arrays.copyOf(bakes, chosen * 2);
                bakes[chosen++] = bake;
            }
        }
        LEVELS.put(state, wanted);
        for (int i = 0; i < chosen; i++) {
            Page page = bakes[i].page;
            if (page != null && page.active != frame) {
                page.active = frame;
                ACTIVE.add(page);
            }
        }
    }

    // ---------------------------------------------------------------- what a figure is drawn with

    /** How one of a figure's models was drawn in a frame: which of its parts, and with which layers. */
    private static final class Seen {
        @Nullable ModelPart root;
        long shown;
        /** Its layers, each once; a list kept from frame to frame, so that a frame makes none. */
        final List<Layer> layers = new ArrayList<>();
        /** What its levels are made for, the parts drawn and the textures in any order, as a number. */
        long key;

        Seen reset(ModelPart root, long shown) {
            this.root = root;
            this.shown = shown;
            layers.clear();
            key = mix(shown);
            return this;
        }

        void add(Layer layer) {
            if (layers.contains(layer)) return;
            layers.add(layer);
            // A sum: the same textures in any order give the same key.
            key += mix(layer.texture().hashCode() * 0x9E3779B97F4A7C15L);
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
     * last, and a model's levels are made for the textures it was drawn with then. A figure has
     * a model or three: arrays searched in order, swapped as a frame starts, never a map.
     */
    private static final class Layers {
        int frame, generation = LodRenderer.generation;
        boolean failed, lastFailed;
        Seen[] now = new Seen[2], last = new Seen[2];
        int nowCount, lastCount;

        /** Its first model this frame: this frame's record starts. */
        void roll() {
            if (frame == LodRenderer.frame) return;
            frame = LodRenderer.frame;
            Seen[] old = last;
            last = now;
            now = old;
            lastCount = nowCount;
            nowCount = 0;
            lastFailed = failed;
            failed = false;
        }

        Seen seen(ModelPart root, long shown) {
            for (int i = 0; i < nowCount; i++) if (now[i].root == root) return now[i];
            if (nowCount == now.length) now = java.util.Arrays.copyOf(now, nowCount * 2);
            Seen seen = now[nowCount];
            if (seen == null) now[nowCount] = seen = new Seen();
            nowCount++;
            return seen.reset(root, shown);
        }

        @Nullable Seen last(ModelPart root) {
            for (int i = 0; i < lastCount; i++) if (last[i].root == root) return last[i];
            return null;
        }
    }

    /** A figure's record, kept on its entity ({@link LodHolder}); a new one after the levels were thrown away. */
    private static @Nullable Layers layers(Entity owner, boolean make) {
        LodHolder holder = (LodHolder) owner;
        Layers layers = holder.livinghorizon$lod() instanceof Layers kept ? kept : null;
        if (layers != null && layers.generation == generation) return layers;
        if (!make) return null;
        layers = new Layers();
        holder.livinghorizon$lod(layers);
        return layers;
    }

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

    /** The levels of a model drawn as {@code seen} says, made already or being made; null for none yet. */
    private static @Nullable LodSet set(ModelPart root, Seen seen) {
        Long2ObjectOpenHashMap<LodSet> sets = SETS.get(root);
        return sets == null ? null : sets.get(seen.key);
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

    /**
     * The game starts drawing the root of one of the mod's figures, at {@code pose}: true when
     * it was drawn whole here, standing ({@link #STANDING}), and the game must draw none of it.
     */
    public static boolean enterRoot(ModelPart root, PoseStack.Pose pose, int light, int overlay, int argb) {
        if (depth != 0) {
            enterPart(root);
            return false;
        }
        enterPart(root);
        LodSet set = drawing;
        if (set == null || drawingLevel < STANDING || !root.visible) return false;
        // Its parts at rest, each group in one go: none of the game's parts is walked.
        LodSet.Plan plan = set.plan();
        if (plan == null) return false;
        PolygonStats.full(set.fullQuads());
        int tinted = tint(argb);
        List<LodLevels.Group> groups = plan.levels().groups();
        for (int g = 0; g < groups.size(); g++) {
            int anchor = groups.get(g).anchor();
            Matrix4f place = PLACE.set(pose.pose()).mul(set.rest(anchor));
            Matrix3f turn = TURN.set(pose.normal()).mul(set.restNormal(anchor));
            queue(g, place, turn, light, overlay, tinted);
        }
        depth = 0;
        drawing = null;
        return true;
    }

    /** From this level a figure stands still, at rest, as its impostor does: too far for its legs to show. */
    private static final int STANDING = LodLevels.MODEL_LEVELS - 1;
    private static final Matrix4f PLACE = new Matrix4f();
    private static final Matrix3f TURN = new Matrix3f();

    public static void leavePart() {
        if (depth > 0 && --depth == 0) drawing = null;
    }

    private static void beginModel(ModelPart root) {
        drawing = null;
        Object state = PolygonStats.current();
        Entity owner = state == null ? null : PolygonStats.ownerOf(state);
        if (owner == null || !WANTED.containsKey(state)) return;
        Layers layers = layers(owner, true);
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
        Seen before = layers.last(root);
        if (before == null || before.shown != shown) return;
        LodSet set = set(root, before);
        if (set == null) {
            // Made for the textures it was drawn with last frame, the parts as the game draws them now.
            LodSet made = new LodSet(ModelReader.read(root, ModelReader.GAME, true), owner.getType().getDescription(),
                    before.drawOrder());
            SETS.computeIfAbsent(root, part -> new Long2ObjectOpenHashMap<>()).put(before.key, made);
            List<Identifier> textures = before.textures();
            double ratio = FarConfig.get().lodKeep / 100.0;
            WORKER.execute(() -> generate(made, textures, ratio));
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
    private static void generate(LodSet set, List<Identifier> textures, double ratio) {
        List<LodBake.Pixels> pixels = new ArrayList<>();
        for (Identifier texture : textures) {
            LodBake.Pixels read = pixels(texture);
            if (read == null) {
                set.fail();
                return;
            }
            pixels.add(read);
        }
        set.generate(pixels, ratio);
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

    /** The groups a part anchors at its level, queued with its pose, light and colour. */
    public static void queue(LodSet.PartDraw draw, PoseStack.Pose pose, int light, int overlay, int argb) {
        for (int group : draw.groups()) queue(group, pose.pose(), pose.normal(), light, overlay, argb);
    }

    /** One group of the model being drawn, where its anchor stands. */
    private static void queue(int group, Matrix4f pose, Matrix3f normal, int light, int overlay, int argb) {
        Bake bake = drawingBake;
        LodSet set = drawing;
        LodSet.Plan plan = set == null ? null : set.plan();
        Page page = bake == null ? null : bake.page;
        if (bake == null || bake.faces == null || plan == null || page == null) return;
        float[] faces = bake.faces[drawingLevel][group];
        if (faces == null || faces.length == 0) return;
        if (!page.drawing) {
            page.drawing = true;
            DRAWING.add(page);
        }
        if (page.count == 0) PENDING.add(page);
        if (page.count == page.entries.size()) page.entries.add(new Entry());
        Entry entry = page.entries.get(page.count++);
        entry.pose.set(pose);
        entry.normal.set(normal);
        entry.faces = faces;
        entry.light = light;
        entry.overlay = overlay;
        entry.argb = argb;
        PolygonStats.lod(plan.triangles()[drawingLevel][group], faces.length / FACE);
    }

    // ---------------------------------------------------------------- the boxes

    /** A merged box to draw: its faces, where its part was, its light and colour. */
    private static final class Entry {
        final Matrix4f pose = new Matrix4f();
        final Matrix3f normal = new Matrix3f();
        float[] faces;
        int light, overlay, argb;
    }

    /** The corner and normal being written: one at a time, on the render thread. */
    private static final Vector3f POINT = new Vector3f(), NORMAL = new Vector3f();

    /** The boxes of one model baked with one texture: their faces, and where their texels are. */
    private static final class Bake {
        final LodSet set;
        final Layer layer;
        /** The {@link #generation} it was asked for in: one from before a reload is dropped. */
        final int generation;
        volatile int state = WORKING;
        int width, height;
        int @Nullable [] argb;
        /** For each level and group, its faces ({@link #FACE} floats each); null where not merged. */
        float @Nullable [][][] faces;
        /** The page its texels were put on, once ready. */
        @Nullable Page page;

        Bake(LodSet set, Layer layer, int generation) {
            this.set = set;
            this.layer = layer;
            this.generation = generation;
        }
    }

    /**
     * A texture the baked models share, and their batch this frame: one draw for every box
     * drawn from it, rather than one per model and layer.
     */
    private static final class Page {
        final Identifier id;
        final DynamicTexture texture;
        final RenderType type;
        final boolean eyes;
        final int width, height;
        /** Where the next baked model goes: shelves, left to right, top to bottom. */
        private int x, y, row;
        final List<Entry> entries = new ArrayList<>();
        int count;
        /** Given boxes this frame, so in {@link #DRAWING}. */
        boolean drawing;
        /** The frames it was put in {@link #ACTIVE} and handed to the game in. */
        int active = -1, submitted = -1;
        final Sink.Geometry geometry = (at, out) -> draw(out);

        Page(boolean eyes, int width, int height) {
            this.eyes = eyes;
            this.width = width;
            this.height = height;
            Identifier page = Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "lod/" + pages++);
            id = page;
            //? if >=1.21.5 {
            texture = new DynamicTexture(() -> "living horizon level of detail " + page, width, height, true);
            //?} else
            /*texture = new DynamicTexture(width, height, true);*/
            // Cleared once: the rest is written a baked model at a time.
            texture.upload();
            Minecraft.getInstance().getTextureManager().register(page, texture);
            // Drawn from both sides: a simplified surface may fold.
            //? if >=26.1 {
            /*type = eyes ? RenderTypes.eyes(page) : RenderTypes.entityCutout(page);
            *///?} else {
            type = eyes ? RenderTypes.eyes(page) : RenderTypes.entityCutoutNoCull(page);
            //?}
        }

        /** Room for a baked model, its corner; null when the page is full. */
        int @Nullable [] place(int wide, int tall) {
            int atX = x, atY = y, shelf = row;
            if (atX + wide > width) {
                atX = 0;
                atY += shelf + 1;
                shelf = 0;
            }
            if (wide > width || atY + tall > height) return null;
            x = atX + wide + 1;
            y = atY;
            row = Math.max(shelf, tall);
            return new int[]{atX, atY};
        }

        /** This frame's boxes, from where their parts were drawn: the pose they are handed is not theirs. */
        void draw(VertexConsumer out) {
            Vector3f point = POINT, normal = NORMAL;
            for (int i = 0; i < count; i++) {
                Entry entry = entries.get(i);
                float[] faces = entry.faces;
                int argb = entry.argb;
                //? if <1.21 {
                /*float a = (argb >>> 24) / 255f, r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f,
                        b = (argb & 0xFF) / 255f;
                *///?}
                for (int f = 0; f + FACE <= faces.length; f += FACE) {
                    entry.normal.transform(normal.set(faces[f + 20], faces[f + 21], faces[f + 22])).normalize();
                    for (int k = 0; k < 4; k++) {
                        int v = f + k * 5;
                        entry.pose.transformPosition(point.set(faces[v], faces[v + 1], faces[v + 2]));
                        // The whole vertex in one call, as the game writes its cubes: its buffers write it at once.
                        //? if >=1.21 {
                        out.addVertex(point.x, point.y, point.z, argb, faces[v + 3], faces[v + 4], entry.overlay,
                                entry.light, normal.x, normal.y, normal.z);
                        //?} else {
                        /*out.vertex(point.x, point.y, point.z, r, g, b, a, faces[v + 3], faces[v + 4], entry.overlay,
                                entry.light, normal.x, normal.y, normal.z);
                        *///?}
                    }
                }
            }
            count = 0;
        }
    }

    /** The boxes of a model baked with a layer's texture, asked for the first time it is needed. */
    private static Bake bake(LodSet set, Layer layer) {
        List<Object> bakes = set.bakes;
        Bake bake = null;
        for (int i = 0; i < bakes.size() && bake == null; i++) {
            Bake kept = (Bake) bakes.get(i);
            if (kept.layer.texture().equals(layer.texture())) bake = kept;
        }
        if (bake == null) {
            Bake made = new Bake(set, layer, generation);
            bakes.add(made);
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
            record Placed(int group, LodBake.Face face) {
            }
            // Solid views for the model's first layer; those drawn over it keep their holes.
            boolean overlay = !bake.set.layers.isEmpty() && !bake.set.layers.get(0).equals(bake.layer.texture());
            List<LodLevels.Group> groups = plan.levels().groups();
            List<Placed> placed = new ArrayList<>();
            List<List<LodBake.Face>> views = new ArrayList<>();
            for (int g = 0; g < groups.size(); g++) {
                List<LodBake.Face> sides = LodBake.bake(plan.model(), groups.get(g), texture, overlay);
                views.add(sides);
                for (LodBake.Face face : sides) placed.add(new Placed(g, face));
            }
            // Shelves, the tallest views first; a texel of room round each.
            int width = 256;
            for (Placed p : placed) width = Math.max(width, p.face().pixels().width() + 1);
            List<Placed> sorted = new ArrayList<>(placed);
            sorted.sort(Comparator.comparingInt((Placed p) -> -p.face().pixels().height()));
            Map<LodBake.Face, int[]> at = new IdentityHashMap<>();
            int x = 0, y = 0, row = 0;
            for (Placed p : sorted) {
                LodBake.Pixels pixels = p.face().pixels();
                if (x + pixels.width() > width) {
                    x = 0;
                    y += row + 1;
                    row = 0;
                }
                at.put(p.face(), new int[]{x, y});
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
                int[] corner = at.get(p.face());
                for (int line = 0; line < pixels.height(); line++) {
                    System.arraycopy(pixels.argb(), line * pixels.width(), argb, (corner[1] + line) * width + corner[0],
                            pixels.width());
                }
            }
            // Each quad's corners, textured from the view of its side: projected onto it.
            int levels = plan.levels().count();
            float[][][] faces = new float[levels][][];
            Vector3d point = new Vector3d();
            for (int level = 1; level < levels; level++) {
                faces[level] = new float[groups.size()][];
                for (int g = 0; g < groups.size(); g++) {
                    List<double[]> quads = plan.quads()[level][g];
                    float[] out = new float[quads.size() * FACE];
                    int o = 0;
                    for (double[] quad : quads) {
                        LodBake.Face view = view(views.get(g), (int) quad[12]);
                        if (view == null) continue;
                        int[] corner = at.get(view);
                        for (int k = 0; k < 4; k++) {
                            point.set(quad[k * 3], quad[k * 3 + 1], quad[k * 3 + 2]).sub(view.center());
                            double s = Math.clamp(point.dot(view.right()) / view.width() + 0.5, 0, 1);
                            double t = Math.clamp(point.dot(view.up()) / view.height() + 0.5, 0, 1);
                            float[] uv = LodBake.pageUv(view, corner[0], corner[1], width, height, s, t);
                            // Model pixels to blocks, as the game divides its cubes' corners as it draws them.
                            out[o++] = (float) quad[k * 3] / 16;
                            out[o++] = (float) quad[k * 3 + 1] / 16;
                            out[o++] = (float) quad[k * 3 + 2] / 16;
                            out[o++] = uv[0];
                            out[o++] = uv[1];
                        }
                        double[] n = normal(quad);
                        out[o++] = (float) n[0];
                        out[o++] = (float) n[1];
                        out[o++] = (float) n[2];
                    }
                    faces[level][g] = o == out.length ? out : java.util.Arrays.copyOf(out, o);
                }
            }
            bake.width = width;
            bake.height = height;
            bake.argb = argb;
            bake.faces = faces;
            bake.state = PIXELS;
            BAKED.add(bake);
        } catch (RuntimeException | LinkageError e) {
            LivingHorizonClient.LOGGER.warn("Levels of detail: could not bake {}", bake.layer.texture(), e);
            bake.state = FAILED;
        }
    }

    /** The view of a group for a side, the first one when that side is flat. */
    private static LodBake.@Nullable Face view(List<LodBake.Face> views, int side) {
        for (LodBake.Face face : views) if (face.side() == side) return face;
        return views.isEmpty() ? null : views.get(0);
    }

    /** A quad's normal, from its first triangle. */
    private static double[] normal(double[] q) {
        double ux = q[3] - q[0], uy = q[4] - q[1], uz = q[5] - q[2];
        double vx = q[6] - q[0], vy = q[7] - q[1], vz = q[8] - q[2];
        return new double[]{uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }

    /** A model whose levels are made, for the preview screen. */
    public static final class Preview {
        private final net.minecraft.network.chat.Component name;
        final LodSet.Plan plan;
        private final List<Identifier> textures;

        Preview(net.minecraft.network.chat.Component name, LodSet.Plan plan, List<Identifier> textures) {
            this.name = name;
            this.plan = plan;
            this.textures = textures;
        }

        public net.minecraft.network.chat.Component name() {
            return name;
        }

        /** Its textures, in the order its layers are drawn. */
        public List<Identifier> textures() {
            return textures;
        }

        /** Whether it shows the same model as {@code other}. */
        public boolean same(Preview other) {
            return plan == other.plan;
        }
    }

    /** The models whose levels are made so far, by name: those of the mobs drawn far away since the game started. */
    public static List<Preview> previews() {
        List<Preview> out = new ArrayList<>();
        for (Map<Long, LodSet> sets : SETS.values()) {
            for (LodSet set : sets.values()) {
                LodSet.Plan plan = set.plan();
                if (plan != null && !set.failed()) out.add(new Preview(set.name, plan, set.layers));
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

    /** Baked models whose texels wait for a page, from the worker. */
    private static final Queue<Bake> BAKED = new ConcurrentLinkedQueue<>();
    private static final List<Page> PAGES = new ArrayList<>();
    private static int generation;

    /** A few baked models a frame are put on pages and handed to the graphics card, on the render thread. */
    private static void upload() {
        int budget = UPLOAD_TEXELS;
        while (budget > 0) {
            Bake bake = BAKED.poll();
            if (bake == null) return;
            if (bake.generation != generation || bake.state != PIXELS) continue;
            int[] argb = bake.argb;
            bake.argb = null;
            try {
                if (argb == null) throw new IllegalStateException("no texels");
                place(bake, argb);
                bake.state = READY;
            } catch (RuntimeException | LinkageError e) {
                LivingHorizonClient.LOGGER.warn("Levels of detail: could not upload a baked texture", e);
                bake.state = FAILED;
            }
            budget -= bake.width * bake.height;
        }
    }

    /** Puts a baked model's texels on a page with room, and its texture coordinates onto that spot. */
    private static void place(Bake bake, int[] argb) {
        Page page = null;
        int[] at = null;
        for (Page open : PAGES) {
            if (open.eyes != bake.layer.eyes()) continue;
            at = open.place(bake.width, bake.height);
            if (at != null) {
                page = open;
                break;
            }
        }
        if (page == null || at == null) {
            page = new Page(bake.layer.eyes(), Math.max(PAGE, bake.width), Math.max(PAGE, bake.height));
            PAGES.add(page);
            at = page.place(bake.width, bake.height);
            if (at == null) throw new IllegalStateException("a baked model larger than its page");
        }
        write(page, at[0], at[1], bake.width, bake.height, argb);
        // Made for a texture of its own: scaled and moved onto its spot.
        float scaleU = (float) bake.width / page.width, scaleV = (float) bake.height / page.height;
        float fromU = (float) at[0] / page.width, fromV = (float) at[1] / page.height;
        float[][][] levels = bake.faces;
        if (levels != null) {
            for (float[][] level : levels) {
                if (level == null) continue;
                for (float[] faces : level) {
                    if (faces == null) continue;
                    for (int f = 0; f + FACE <= faces.length; f += FACE) {
                        for (int k = 0; k < 4; k++) {
                            faces[f + k * 5 + 3] = fromU + faces[f + k * 5 + 3] * scaleU;
                            faces[f + k * 5 + 4] = fromV + faces[f + k * 5 + 4] * scaleV;
                        }
                    }
                }
            }
        }
        bake.page = page;
    }

    /** Writes a baked model's texels into its spot on a page. */
    private static void write(Page page, int x, int y, int width, int height, int[] argb) {
        try (NativeImage strip = new NativeImage(width, height, false)) {
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    int pixel = argb[row * width + column];
                    //? if >=1.21.2 {
                    strip.setPixel(column, row, pixel);
                    //?} else
                    /*strip.setPixelRGBA(column, row, (pixel & 0xFF00FF00) | ((pixel >> 16) & 0xFF) | ((pixel & 0xFF) << 16));*/
                }
            }
            //? if >=26.2 {
            /*RenderSystem.getDevice().createCommandEncoder().writeToTexture(page.texture.getTexture(), strip, 0, 0, x, y);
            *///?} elif >=1.21.6 {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(page.texture.getTexture(), strip, 0, 0, x, y,
                    width, height, 0, 0);
            //?} elif >=1.21.5 {
            /*RenderSystem.getDevice().createCommandEncoder().writeToTexture(page.texture.getTexture(), strip, 0, x, y,
                    width, height, 0, 0);
            *///?} elif >=1.21.4 {
            /*page.texture.bind();
            strip.upload(0, x, y, 0, 0, width, height, false);
            *///?} else {
            /*page.texture.bind();
            strip.upload(0, x, y, 0, 0, width, height, false, false);
            *///?}
        }
    }

    private static int pages;
    /** Kept from frame to frame, so that a frame makes no new list. */
    private static Bake[] CHOSEN = new Bake[8];

    /** The boxes queued this frame, one batch per page, with the impostors. */
    public static void submit(PoseStack pose, Sink sink) {
        // From 26.2 they go with their models instead (flush): the frame may be prepared in another order.
        //? if >=26.2
        /*if (true) return;*/
        for (int i = 0; i < ACTIVE.size(); i++) batch(ACTIVE.get(i), pose, sink);
        for (int i = 0; i < DRAWING.size(); i++) batch(DRAWING.get(i), pose, sink);
    }

    /** A page's batch, once a frame however many lists it is in. */
    private static void batch(Page page, PoseStack pose, Sink sink) {
        if (page.submitted == frame) return;
        page.submitted = frame;
        sink.draw(pose, page.type, page.geometry);
    }

    /**
     * The boxes queued since the last call, written at once with {@code builder}'s vertices for
     * their textures: from 26.2, the group of models they belong to has just been prepared, and
     * they go into it.
     */
    public static void flush(java.util.function.Function<RenderType, VertexConsumer> builder) {
        for (Page page : PENDING) {
            if (page.count > 0) page.draw(builder.apply(page.type));
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
        // A bake still being made away from here is dropped with its generation.
        generation++;
        BAKED.clear();
        for (Page page : PAGES) Minecraft.getInstance().getTextureManager().release(page.id);
        PAGES.clear();
        ACTIVE.clear();
        DRAWING.clear();
        PENDING.clear();
        SETS.clear();
        ModelReader.forget();
        LAYER_OF.clear();
    }
}
