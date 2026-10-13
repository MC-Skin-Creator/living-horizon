package fr.clixmods.livinghorizon.render.impostor;

// Three ways to draw a picture into a texture and read it back: the game's GPU device with
// its output overrides from 1.21.6, the device without them on 1.21.5 (our target stands in
// for the main one), and plain OpenGL before.
//? if >=1.21.6 {
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
//? if >=26.2
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;*/
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
//? if >=26.3 {
/*import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.Optional;
import java.util.OptionalDouble;
*///?}
//? if >=26.2 {
/*import com.mojang.blaze3d.GpuFormat;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.joml.Vector4f;
*///?} else {
import com.mojang.blaze3d.textures.TextureFormat;
//?}
//? if >=26.1 {
/*import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
*///?} else {
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
//?}
//?} elif >=1.21.5 {
/*import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.renderer.FogParameters;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
*///?} else {
/*import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryUtil;
import java.nio.IntBuffer;
*///?}
//? if >=1.21.2 && <1.21.5 {
/*import com.mojang.blaze3d.ProjectionType;
import net.minecraft.client.renderer.FogParameters;
import org.joml.Matrix4fStack;
*///?}
//? if >=1.20.5 && <1.21.2
//import org.joml.Matrix4fStack;
//? if <1.21.2 {
/*import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.FogRenderer;
*///?}
//? if <1.21.4
//import net.minecraft.server.packs.resources.ResourceManager;
//? if >=1.21.9 {
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.CameraRenderState;
//?} else
//import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The pictures of the impostors, baked by the game's own renderers.
 *
 * <p>A <em>sheet</em> is one figure turned through {@link ImpostorViews#VIEWS} angles. Each
 * view is drawn the way the inventory draws a player (an orthographic picture of the
 * entity through its own renderer, so skins, armour, resource packs and other mods'
 * models are all followed), twice as large as a tile, into a small target.
 *
 * <p>The pictures are then read back and their mip levels worked out on the CPU
 * ({@link ImpostorPixels}), and written into an atlas page that has real mip levels: the
 * graphics card picks and blends them as it does for any texture, so a figure a few pixels
 * tall is an average of its picture instead of a few pixels picked out of it, which would
 * flicker as it moves. The game's texture-to-texture copy cannot be used: it only copies
 * right at the corner of a texture, and stretches anything written anywhere else.
 *
 * <p>Baking is queued and done a little each frame: from 1.21.6 in the GUI pass, where the
 * game itself renders entities to textures, and the read back lands a frame or two later;
 * before, at the start of the frame, into a target of our own that the game takes for its
 * main one while a figure is drawn, and before 1.21.5 the eight views are drawn side by side
 * and read back at once. Everything here runs on the render thread. Resource reloads throw
 * the sheets away; they are baked again from the figures at hand.
 */
public final class ImpostorAtlas {
    /**
     * Mip levels of a page: tiles of 64, 32, 16 and 8 pixels. A smaller level would blend
     * a figure with the views next to it in the page; smaller than that, a figure is a
     * couple of pixels anyway.
     */
    public static final int MIPS = 4;

    /**
     * The feet stand this far down a tile, as a fraction of its height: above the strip
     * {@link ImpostorRenderer} leaves out at the bottom of a tile, so they are not cut off.
     */
    public static final float FEET = 0.9f;

    /** Each view is drawn this many times larger than a tile, then averaged down. */
    private static final int SUPERSAMPLE = 2;
    private static final int RENDER = ImpostorViews.TILE * SUPERSAMPLE;

    /** Sheets across and down one page of tiles at scale 1, and pages of them at most. */
    private static final int COLUMNS = 4, ROWS = 32, PER_PAGE = COLUMNS * ROWS, MAX_PAGES = 2;
    /** Pixels along a side of a page. */
    public static final int PAGE_SIZE = ImpostorViews.TILE * COLUMNS * ImpostorViews.VIEWS;
    /**
     * Tiles at most this many times as large as {@link ImpostorViews#TILE}, for figures baked
     * larger than their box says (see {@link Request}): as many pixels to the block as the
     * others. On pages of their own, {@link #MAX_LARGE_PAGES} at most.
     */
    private static final int MAX_SCALE = 3, MAX_LARGE_PAGES = 1;

    /** The formats of the pages and of the target a view is drawn in, and the bytes of a pixel. */
    //? if >=26.2 {
    /*private static final GpuFormat COLOR = GpuFormat.RGBA8_UNORM, DEPTH = GpuFormat.D32_FLOAT;
    *///?} elif >=1.21.6 {
    private static final TextureFormat COLOR = TextureFormat.RGBA8, DEPTH = TextureFormat.DEPTH32;
    //?} elif >=1.21.5 {
    /*private static final TextureFormat COLOR = TextureFormat.RGBA8;
    *///?}
    private static final int PIXEL_BYTES = 4;

    /** Full block and sky light: the pictures carry their shading, the world adds its light. */
    private static final int FULL_BRIGHT = 15728880;

    /** Nanoseconds a frame may spend baking. One sheet is always started. */
    private static final long BUDGET = 3_000_000L;

    /**
     * The game's light in the world, as the picture is drawn: the drawing turns the figure
     * over (see {@link #pose}), and its normals with it, so the light is turned over too.
     * The pictures are then shaded like a model in the world, not like one in the inventory.
     */
    private static final Vector3f LIGHT_0 = new Vector3f(0.2f, 1f, -0.7f).normalize().negate();
    private static final Vector3f LIGHT_1 = new Vector3f(-0.2f, 1f, 0.7f).normalize().negate();

    /** Where a figure's pictures are: which cell of the pages, and how big a tile is in blocks. */
    public record Sheet(int index, double worldSize, int scale) {
        public int page() {
            return index / PER_PAGE;
        }

        /** Pixels along a side of one of its tiles. */
        public int tile() {
            return ImpostorViews.TILE * scale;
        }

        public int column() {
            return index % PER_PAGE % across(scale);
        }

        public int row() {
            return index % PER_PAGE / across(scale);
        }

        /** The left edge of a view's tile, as a fraction of the page's width. */
        public float u(int view) {
            return (column() * ImpostorViews.VIEWS + view) * tile() / (float) PAGE_SIZE;
        }

        /** The top edge of its tiles, as a fraction of the page's height. */
        public float v() {
            return row() * tile() / (float) PAGE_SIZE;
        }

        /** A side of one of its tiles, as a fraction of the page's. */
        public float span() {
            return tile() / (float) PAGE_SIZE;
        }
    }

    /** Sheets across a page of tiles {@code scale} times as large. */
    private static int across(int scale) {
        return COLUMNS / scale;
    }

    /** Sheets on a page of tiles {@code scale} times as large. */
    private static int capacity(int scale) {
        return across(scale) * (ROWS / scale);
    }

    /**
     * A figure to bake, its picture {@code grow} times larger than {@link #size} gives: a
     * figure longer than its box (a horse's muzzle, a sniffer) is baked again larger, into
     * tiles as many times larger, so that it keeps its sharpness.
     */
    private record Request(ImpostorKey key, Entity puppet, double grow) {
        int scale() {
            return Math.clamp((int) Math.ceil(grow - 1e-6), 1, MAX_SCALE);
        }

        /** Pixels along a side of the target a view is drawn in. */
        int render() {
            return RENDER * scale();
        }
    }

    /**
     * How much larger a figure cut off by the very edge of its picture is baked again at
     * least, and how much larger than {@link #size} a picture can get.
     */
    private static final double GROW = 1.3, MAX_GROW = MAX_SCALE;

    //? if >=1.21.6 {
    private static final class PageTexture extends AbstractTexture {
        PageTexture(String label) {
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    COLOR, PAGE_SIZE, PAGE_SIZE, 1, MIPS);
            textureView = device.createTextureView(texture);
            // Smooth from far, but the pixels of the model when a figure is larger than its tile.
            //? if >=1.21.11 {
            sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.NEAREST, true);
            //?} else {
            /*texture.setAddressMode(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE);
            texture.setTextureFilter(FilterMode.LINEAR, FilterMode.NEAREST, true);
            *///?}
            CommandEncoder encoder = device.createCommandEncoder();
            // Nothing in it yet, at every level: the filter reads a little past each tile.
            for (int level = 0; level < MIPS; level++) {
                int size = PAGE_SIZE >> level;
                try (NativeImage empty = new NativeImage(size, size, true)) {
                    //? if >=26.2 {
                    /*encoder.writeToTexture(texture, empty, level, 0, 0, 0);
                    *///?} else {
                    encoder.writeToTexture(texture, empty, level, 0, 0, 0, size, size, 0, 0);
                    //?}
                }
            }
        }
    }

    /** The target one view is drawn in before it is read back. */
    private static final class Target {
        GpuTexture color, depth;
        GpuTextureView colorView, depthView;
        //? if >=26.1 {
        /*ProjectionMatrixBuffer projection;
        *///?} else {
        CachedOrthoProjectionMatrixBuffer projection;
        //?}
        //? if >=26.2 {
        /*// The game's feature renderer no longer keeps a storage of its own to borrow.
        final SubmitNodeStorage storage = new SubmitNodeStorage();
        final Projection ortho = new Projection();
        *///?}
    }
    //?} elif >=1.21.5 {
    /*private static final class PageTexture extends AbstractTexture {
        PageTexture(String label) {
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(label, COLOR, PAGE_SIZE, PAGE_SIZE, MIPS);
            // Smooth from far, but the pixels of the model when a figure is larger than its tile.
            texture.setAddressMode(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE);
            texture.setTextureFilter(FilterMode.LINEAR, FilterMode.NEAREST, true);
            CommandEncoder encoder = device.createCommandEncoder();
            // Nothing in it yet, at every level: the filter reads a little past each tile.
            for (int level = 0; level < MIPS; level++) {
                int size = PAGE_SIZE >> level;
                try (NativeImage empty = new NativeImage(size, size, true)) {
                    encoder.writeToTexture(texture, empty, level, 0, 0, size, size, 0, 0);
                }
            }
        }
    }
    *///?} else {
    /*/^* A page as a plain OpenGL texture, its mip levels allocated and cleared up front. ^/
    private static final class PageTexture extends AbstractTexture {
        PageTexture(String label) {
            bind();
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, MIPS - 1);
            // Smooth from far, but the pixels of the model when a figure is larger than its tile.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            unpack();
            // Nothing in it yet, at every level: the filter reads a little past each tile.
            for (int level = 0; level < MIPS; level++) {
                int size = PAGE_SIZE >> level;
                ByteBuffer empty = MemoryUtil.memCalloc(size * size * PIXEL_BYTES);
                try {
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, size, size, 0, GL11.GL_RGBA,
                            GL11.GL_UNSIGNED_BYTE, empty);
                } finally {
                    MemoryUtil.memFree(empty);
                }
            }
        }

        //? if <1.21.4 {
        /^@Override
        public void load(ResourceManager manager) {
        }
        ^///?}
    }
    *///?}

    private static final Map<ImpostorKey, Sheet> SHEETS = new LinkedHashMap<>(64, 0.75f, true);
    private static final Deque<Request> QUEUE = new ArrayDeque<>();
    /** Queued, or drawn and waiting for the read back. */
    private static final Set<ImpostorKey> QUEUED = new HashSet<>();
    private static final Deque<Integer> FREE = new ArrayDeque<>();
    /** Cells being baked: not free, not yet a sheet. */
    private static final Set<Integer> BAKING = new HashSet<>();
    private static final List<PageTexture> PAGES = new ArrayList<>();
    /** The scale of the tiles of each page handed out so far, and how many of its cells were. */
    private static final List<Integer> SCALES = new ArrayList<>(), OPENED = new ArrayList<>();
    //? if >=1.21.6 {
    /** The targets views are drawn in, by scale. */
    private static final @Nullable Target[] TARGETS = new Target[MAX_SCALE + 1];
    private static @Nullable Lighting lighting;
    //?} else {
    /*private static final @Nullable RenderTarget[] TARGETS = new RenderTarget[MAX_SCALE + 1];
    /^* Our target while a figure is drawn, which the game then takes for its main one. ^/
    private static @Nullable RenderTarget drawing;
    *///?}
    /** Bumped by every reset, so that a read back for pages thrown away is dropped. */
    private static int generation;
    private static boolean dirty, failed;

    private ImpostorAtlas() {
    }

    /** The game's textures were reloaded: every picture may be out of date. */
    public static void invalidate() {
        dirty = true;
    }

    /**
     * Throws every picture away and tries again, even after a failed bake: the figures on
     * hand are baked anew within the next seconds. From the impostor screen.
     */
    public static void regenerate() {
        failed = false;
        dirty = true;
        settle();
    }

    /** One baked figure, for the impostor screen. */
    public record Baked(ImpostorKey key, Sheet sheet) {
    }

    /** Every figure baked, without counting as a use: a new list, the caller's to sort. */
    public static List<Baked> baked() {
        settle();
        List<Baked> baked = new ArrayList<>(SHEETS.size());
        SHEETS.forEach((key, sheet) -> baked.add(new Baked(key, sheet)));
        return baked;
    }

    /** Whether impostors can be used at all: switched on, and the bake has not failed. */
    public static boolean usable() {
        FarConfig config = FarConfig.get();
        return config.anyDistant() && config.impostors && !failed;
    }

    /** The pictures of a figure, if baked. Counts as a use, so the sheets in use are kept. */
    public static @Nullable Sheet sheet(ImpostorKey key) {
        settle();
        return SHEETS.get(key);
    }

    /** Asks for a figure's pictures to be baked, within the next few frames. */
    public static void request(ImpostorKey key, Entity puppet) {
        settle();
        if (failed || SHEETS.containsKey(key) || !QUEUED.add(key)) return;
        QUEUE.add(new Request(key, puppet, 1.0));
    }

    /** Pages made so far: the first holds tiles of scale 1, the others may be larger. */
    public static int pages() {
        return PAGES.size();
    }

    public static int sheets() {
        return SHEETS.size();
    }

    public static int waiting() {
        return QUEUED.size();
    }

    public static boolean failed() {
        return failed;
    }

    //? if <1.21.6 {
    /*/^*
     * The target the game must draw into in place of its main one, while a figure is being
     * baked; null the rest of the time. Read by MinecraftMixin and LevelRendererMixin.
     ^/
    public static @Nullable RenderTarget drawing() {
        return drawing;
    }
    *///?}

    public static RenderType type(int page) {
        //? if >=26.1 {
        /*return RenderTypes.entityCutout(id(page));
        *///?} else {
        return RenderTypes.entityCutoutNoCull(id(page));
        //?}
    }

    /** The texture of one atlas page. */
    public static Identifier id(int page) {
        return Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "impostors/" + page);
    }

    /** Drops what a reload or a switch-off made stale. */
    private static void settle() {
        if (!dirty) return;
        dirty = false;
        generation++;
        Minecraft minecraft = Minecraft.getInstance();
        for (int page = 0; page < PAGES.size(); page++) minecraft.getTextureManager().release(id(page));
        PAGES.clear();
        SHEETS.clear();
        QUEUE.clear();
        QUEUED.clear();
        FREE.clear();
        BAKING.clear();
        SCALES.clear();
        OPENED.clear();
        ImpostorKey.forgetAll();
    }

    /**
     * Bakes what is queued, within a small budget. Called once a frame: from the GUI pass
     * from 1.21.6, at the start of the frame before.
     */
    public static void bakePending() {
        settle();
        if (!usable()) {
            for (Request request : QUEUE) QUEUED.remove(request.key);
            QUEUE.clear();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getOverlay() != null) return;
        long end = System.nanoTime() + BUDGET;
        while (!QUEUE.isEmpty()) {
            Request request = QUEUE.poll();
            if (request.puppet.level() != minecraft.level || SHEETS.containsKey(request.key)) {
                QUEUED.remove(request.key);
                continue;
            }
            try {
                bake(minecraft, request);
            } catch (RuntimeException | LinkageError error) {
                fail(error);
                return;
            } finally {
                //? if >=1.21.6 && <26.3 {
                RenderSystem.outputColorTextureOverride = null;
                RenderSystem.outputDepthTextureOverride = null;
                //?}
            }
            if (System.nanoTime() > end) return;
        }
    }

    private static void fail(Throwable error) {
        failed = true;
        QUEUE.clear();
        QUEUED.clear();
        LivingHorizonClient.LOGGER.error("Impostors could not be baked, falling back to full models", error);
    }

    /** A cell for a sheet of tiles {@code scale} times as large, or -1. */
    private static int allocate(int scale) {
        for (Iterator<Integer> free = FREE.iterator(); free.hasNext(); ) {
            int index = free.next();
            if (SCALES.get(index / PER_PAGE) != scale) continue;
            free.remove();
            return index;
        }
        int pages = 0;
        for (int page = 0; page < SCALES.size(); page++) {
            if (SCALES.get(page) != scale) continue;
            pages++;
            int opened = OPENED.get(page);
            if (opened == capacity(scale)) continue;
            OPENED.set(page, opened + 1);
            return page * PER_PAGE + opened;
        }
        if (pages < (scale == 1 ? MAX_PAGES : MAX_LARGE_PAGES)) {
            SCALES.add(scale);
            OPENED.add(1);
            return (SCALES.size() - 1) * PER_PAGE;
        }
        // Full: the sheet of that scale unused for longest gives way.
        for (Iterator<Sheet> oldest = SHEETS.values().iterator(); oldest.hasNext(); ) {
            Sheet evicted = oldest.next();
            if (evicted.scale != scale) continue;
            oldest.remove();
            return evicted.index;
        }
        return -1;
    }

    private static PageTexture page(int number) {
        while (PAGES.size() <= number) {
            int page = PAGES.size();
            PageTexture created = new PageTexture("living horizon impostors " + page);
            Minecraft.getInstance().getTextureManager().register(id(page), created);
            PAGES.add(created);
        }
        return PAGES.get(number);
    }

    /** Blocks along a side of a figure's tiles. */
    private static double size(Entity puppet) {
        return ImpostorViews.worldSize(Math.max(0.3, puppet.getBbHeight()), Math.max(0.3, puppet.getBbWidth()));
    }

    /** A figure's state for one view, still and lit evenly. */
    private static EntityRenderState staged(Minecraft minecraft, Entity puppet, int view) {
        EntityRenderState state = GhostRenderer.extract(minecraft.getEntityRenderDispatcher(), puppet, 1.0f);
        stage(state, view);
        // Water mobs were in water when last seen; a puppet is in no world.
        if (state instanceof LivingEntityRenderState living && GhostRenderer.livesInWater(puppet)) {
            living.isInWater = true;
        }
        return state;
    }

    /** As the inventory does it: pixels for blocks, y down, so turned over to stand. */
    private static PoseStack pose(double size, int render) {
        PoseStack pose = new PoseStack();
        float scale = (float) (render / size);
        pose.translate(render / 2f, render * FEET, 0f);
        pose.scale(scale, scale, -scale);
        pose.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        return pose;
    }

    //? if <1.21.6 {
    /*/^* The projection a view is drawn with: pixels, y down, as the GUI's. ^/
    private static Matrix4f ortho(int render) {
        return new Matrix4f().setOrtho(0f, render, render, 0f, -1000f, 1000f);
    }
    *///?}

    //? if >=1.21.6 {
    private static Target target(int scale) {
        Target target = TARGETS[scale];
        if (target != null) return target;
        int render = RENDER * scale;
        GpuDevice device = RenderSystem.getDevice();
        Target created = new Target();
        created.color = device.createTexture("living horizon impostor",
                GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING
                        | GpuTexture.USAGE_RENDER_ATTACHMENT,
                COLOR, render, render, 1, 1);
        created.colorView = device.createTextureView(created.color);
        created.depth = device.createTexture("living horizon impostor depth",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT, DEPTH, render, render, 1, 1);
        created.depthView = device.createTextureView(created.depth);
        //? if >=26.1 {
        /*created.projection = new ProjectionMatrixBuffer("living horizon impostor");
        *///?} else {
        created.projection = new CachedOrthoProjectionMatrixBuffer("living horizon impostor", -1000f, 1000f, true);
        //?}
        TARGETS[scale] = created;
        return created;
    }

    private static Lighting lighting() {
        if (lighting == null) {
            lighting = new Lighting();
            lighting.updateBuffer(Lighting.Entry.LEVEL, LIGHT_0, LIGHT_1);
        }
        return lighting;
    }

    private static void bake(Minecraft minecraft, Request request) {
        Entity puppet = request.puppet;
        double size = size(puppet) * request.grow;
        int scale = request.scale(), render = request.render();
        int index = allocate(scale);
        if (index < 0) {
            QUEUED.remove(request.key);
            return;
        }
        BAKING.add(index);
        GpuDevice device = RenderSystem.getDevice();
        int bytes = render * render * PIXEL_BYTES;
        GpuBuffer buffer = device.createBuffer(() -> "living horizon impostor read back",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, /*? if >=1.21.11 {*/ (long) /*?} else {*/ /*(int) *//*?}*/ bytes * ImpostorViews.VIEWS);
        boolean sent = false;
        try {
            Target target = target(scale);
            //? if >=1.21.9 {
            EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
            FeatureRenderDispatcher features = minecraft.gameRenderer.getFeatureRenderDispatcher();
            //?} else
            /*MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();*/
            CommandEncoder encoder = device.createCommandEncoder();
            // From 26.3 the drawing goes through a render pass on the target, opened below.
            //? if <26.3 {
            RenderSystem.outputColorTextureOverride = target.colorView;
            RenderSystem.outputDepthTextureOverride = target.depthView;
            //?}
            int generation = ImpostorAtlas.generation;
            for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                //? if >=26.2 {
                /*// Depth runs from 1 near to 0 far from 26.2.
                encoder.clearColorAndDepthTextures(target.color, new Vector4f(0f), target.depth, 0.0);
                target.ortho.setupOrtho(-1000f, 1000f, render, render, true);
                RenderSystem.setProjectionMatrix(target.projection.getBuffer(target.ortho), ProjectionType.ORTHOGRAPHIC);
                *///?} elif >=26.1 {
                /*encoder.clearColorAndDepthTextures(target.color, 0, target.depth, 1.0);
                RenderSystem.setProjectionMatrix(
                        target.projection.getBuffer(new Matrix4f().setOrtho(0f, render, render, 0f, -1000f, 1000f)),
                        ProjectionType.ORTHOGRAPHIC);
                *///?} else {
                encoder.clearColorAndDepthTextures(target.color, 0, target.depth, 1.0);
                RenderSystem.setProjectionMatrix(target.projection.getBuffer(render, render), ProjectionType.ORTHOGRAPHIC);
                //?}
                EntityRenderState state = staged(minecraft, puppet, view);
                PoseStack pose = pose(size, render);
                lighting().setupFor(Lighting.Entry.LEVEL);
                //? if >=26.3 {
                /*dispatcher.submit(state, new CameraRenderState(), 0.0, 0.0, 0.0, pose, target.storage);
                try (FeatureRenderDispatcher.PreparedFrame frame = features.prepareFrame(target.storage);
                     RenderPass pass = encoder.createRenderPass(() -> "living horizon impostor", target.colorView,
                             Optional.empty(), target.depthView, OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    FeatureRenderDispatcher.renderAllFeatures(pass, frame);
                }
                *///?} elif >=26.2 {
                /*dispatcher.submit(state, new CameraRenderState(), 0.0, 0.0, 0.0, pose, target.storage);
                features.renderAllFeatures(target.storage);
                *///?} elif >=1.21.9 {
                dispatcher.submit(state, new CameraRenderState(), 0.0, 0.0, 0.0, pose, features.getSubmitNodeStorage());
                features.renderAllFeatures();
                minecraft.renderBuffers().bufferSource().endBatch();
                //?} else {
                /*GhostRenderer.drawState(state, 0.0, 0.0, 0.0, pose, buffers, FULL_BRIGHT);
                buffers.endBatch();
                *///?}
                boolean last = view == ImpostorViews.VIEWS - 1;
                encoder.copyTextureToBuffer(target.color, buffer, /*? if >=1.21.11 {*/ (long) /*?} else {*/ /*(int) *//*?}*/ bytes * view,
                        last ? () -> readBack(request, index, size, buffer, generation) : () -> {
                        }, 0);
            }
            sent = true;
        } finally {
            // What the GUI pass draws next expects the game's own light.
            minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
            if (!sent) {
                BAKING.remove(index);
                FREE.push(index);
                QUEUED.remove(request.key);
                buffer.close();
            }
        }
    }
    //?} elif >=1.21.5 {
    /*private static RenderTarget target(int scale) {
        if (TARGETS[scale] == null) {
            TARGETS[scale] = new TextureTarget("living horizon impostor", RENDER * scale, RENDER * scale, true);
        }
        return TARGETS[scale];
    }

    /^*
     * Draws the views one by one into our target, which the game takes for its main one
     * meanwhile: this version has no output override to point the drawing elsewhere.
     ^/
    private static void bake(Minecraft minecraft, Request request) {
        Entity puppet = request.puppet;
        double size = size(puppet) * request.grow;
        int scale = request.scale(), render = request.render();
        int index = allocate(scale);
        if (index < 0) {
            QUEUED.remove(request.key);
            return;
        }
        BAKING.add(index);
        GpuDevice device = RenderSystem.getDevice();
        int bytes = render * render * PIXEL_BYTES;
        GpuBuffer buffer = device.createBuffer(() -> "living horizon impostor read back", BufferType.PIXEL_PACK,
                BufferUsage.STATIC_READ, bytes * ImpostorViews.VIEWS);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        // Whatever is still pending goes where it belongs, before our target stands in.
        buffers.endBatch();
        RenderTarget target = target(scale);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        FogParameters fog = RenderSystem.getShaderFog();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix().identity();
        drawing = target;
        boolean sent = false;
        try {
            CommandEncoder encoder = device.createCommandEncoder();
            RenderSystem.setShaderFog(FogParameters.NO_FOG);
            RenderSystem.setShaderLights(LIGHT_0, LIGHT_1);
            RenderSystem.setProjectionMatrix(ortho(render), ProjectionType.ORTHOGRAPHIC);
            int generation = ImpostorAtlas.generation;
            for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                encoder.clearColorAndDepthTextures(target.getColorTexture(), 0, target.getDepthTexture(), 1.0);
                GhostRenderer.drawState(staged(minecraft, puppet, view), 0.0, 0.0, 0.0, pose(size, render), buffers, FULL_BRIGHT);
                buffers.endBatch();
                boolean last = view == ImpostorViews.VIEWS - 1;
                encoder.copyTextureToBuffer(target.getColorTexture(), buffer, bytes * view,
                        last ? () -> readBack(request, index, size, buffer, generation) : () -> {
                        }, 0);
            }
            sent = true;
        } finally {
            drawing = null;
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.setShaderFog(fog);
            Lighting.setupFor3DItems();
            if (!sent) {
                BAKING.remove(index);
                FREE.push(index);
                QUEUED.remove(request.key);
                buffer.close();
            }
        }
    }
    *///?} else {
    /*/^* The eight views side by side, each in a square of its own. ^/
    private static RenderTarget target(int scale) {
        if (TARGETS[scale] != null) return TARGETS[scale];
        int render = RENDER * scale;
        //? if >=1.21.2 {
        RenderTarget target = new TextureTarget(render * ImpostorViews.VIEWS, render, true);
        //?} else
        //RenderTarget target = new TextureTarget(render * ImpostorViews.VIEWS, render, true, Minecraft.ON_OSX);
        target.setClearColor(0f, 0f, 0f, 0f);
        TARGETS[scale] = target;
        return target;
    }

    /^*
     * Draws the eight views side by side into our target, which the game takes for its main
     * one meanwhile, and reads them back at once.
     ^/
    private static void bake(Minecraft minecraft, Request request) {
        Entity puppet = request.puppet;
        double size = size(puppet) * request.grow;
        int scale = request.scale(), render = request.render();
        int index = allocate(scale);
        if (index < 0) {
            QUEUED.remove(request.key);
            return;
        }
        BAKING.add(index);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        // Whatever is still pending goes where it belongs, before our target stands in.
        buffers.endBatch();
        RenderTarget target = target(scale);
        int[][] pictures = new int[ImpostorViews.VIEWS][];
        //? if >=1.20.5 {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix().identity();
        //?} else {
        /^PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        ^///?}
        //? if <1.21.2
        //RenderSystem.applyModelViewMatrix();
        //? if >=1.21.2 {
        FogParameters fog = RenderSystem.getShaderFog();
        RenderSystem.setShaderFog(FogParameters.NO_FOG);
        //?} else {
        /^float fogStart = RenderSystem.getShaderFogStart();
        FogRenderer.setupNoFog();
        ^///?}
        RenderSystem.backupProjectionMatrix();
        drawing = target;
        boolean drawn = false;
        try {
            RenderSystem.setShaderLights(LIGHT_0, LIGHT_1);
            RenderSystem.depthMask(true);
            //? if >=1.21.2 {
            target.clear();
            RenderSystem.setProjectionMatrix(ortho(render), ProjectionType.ORTHOGRAPHIC);
            //?} else {
            /^target.clear(Minecraft.ON_OSX);
            RenderSystem.setProjectionMatrix(ortho(render), VertexSorting.ORTHOGRAPHIC_Z);
            ^///?}
            target.bindWrite(true);
            for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                RenderSystem.viewport(view * render, 0, render, render);
                GhostRenderer.drawState(staged(minecraft, puppet, view), 0.0, 0.0, 0.0, pose(size, render), buffers, FULL_BRIGHT);
                buffers.endBatch();
            }
            download(target, pictures, render);
            drawn = true;
        } finally {
            drawing = null;
            RenderSystem.restoreProjectionMatrix();
            //? if >=1.20.5 {
            modelView.popMatrix();
            //?} else
            //modelView.popPose();
            //? if <1.21.2
            //RenderSystem.applyModelViewMatrix();
            //? if >=1.21.2 {
            RenderSystem.setShaderFog(fog);
            //?} else
            //RenderSystem.setShaderFogStart(fogStart);
            minecraft.getMainRenderTarget().bindWrite(true);
            Lighting.setupFor3DItems();
            if (!drawn) {
                BAKING.remove(index);
                FREE.push(index);
                QUEUED.remove(request.key);
            }
        }
        store(request, index, size, pictures, generation);
    }

    /^* Reads the views back from our target: bottom row first, as the GPU device does. ^/
    private static void download(RenderTarget target, int[][] pictures, int render) {
        int width = render * ImpostorViews.VIEWS;
        IntBuffer all = MemoryUtil.memAllocInt(width * render);
        try {
            GlStateManager._bindTexture(target.getColorTextureId());
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, all);
            for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                int[] pixels = new int[render * render];
                for (int y = 0; y < render; y++) all.get(y * width + view * render, pixels, y * render, render);
                pictures[view] = pixels;
            }
        } finally {
            MemoryUtil.memFree(all);
        }
    }

    /^* What the game's own uploads may have left in the unpack state, reset to plain rows. ^/
    private static void unpack() {
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
    }
    *///?}

    //? if >=1.21.5 {
    /** The views are read back: their pixels go on to {@link #store}. */
    private static void readBack(Request request, int index, double size, GpuBuffer buffer, int generation) {
        int[][] pictures = null;
        try {
            if (generation == ImpostorAtlas.generation) {
                int[][] read = new int[ImpostorViews.VIEWS][];
                //? if >=26.2 {
                /*try (GpuBufferSlice.MappedView mapped = buffer.map(true, false)) {
                *///?} elif >=1.21.6 {
                try (GpuBuffer.MappedView mapped = RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer, true, false)) {
                //?} else {
                /*try (GpuBuffer.ReadView mapped = RenderSystem.getDevice().createCommandEncoder().readBuffer(buffer)) {
                *///?}
                    ByteBuffer data = mapped.data().order(ByteOrder.LITTLE_ENDIAN);
                    for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                        read[view] = new int[request.render() * request.render()];
                        data.asIntBuffer().get(view * read[view].length, read[view]);
                    }
                }
                pictures = read;
            }
        } catch (RuntimeException | LinkageError error) {
            if (generation == ImpostorAtlas.generation) fail(error);
        } finally {
            buffer.close();
        }
        store(request, index, size, pictures, generation);
    }

    /** Writes a strip of pixels into one level of a page, top row first. */
    private static void write(PageTexture page, int level, int x, int y, int width, int height, int[] pixels) {
        try (NativeImage strip = new NativeImage(width, height, false)) {
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) strip.setPixelABGR(column, row, pixels[row * width + column]);
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            //? if >=26.2 {
            /*encoder.writeToTexture(page.getTexture(), strip, level, 0, x, y);
            *///?} elif >=1.21.6 {
            encoder.writeToTexture(page.getTexture(), strip, level, 0, x, y, width, height, 0, 0);
            //?} else {
            /*encoder.writeToTexture(page.getTexture(), strip, level, x, y, width, height, 0, 0);
            *///?}
        }
    }
    //?} else {
    /*/^* Writes a strip of pixels into one level of a page, top row first. ^/
    private static void write(PageTexture page, int level, int x, int y, int width, int height, int[] pixels) {
        IntBuffer strip = MemoryUtil.memAllocInt(pixels.length);
        try {
            strip.put(pixels).flip();
            page.bind();
            unpack();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, x, y, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, strip);
        } finally {
            MemoryUtil.memFree(strip);
        }
    }
    *///?}

    /**
     * The views are drawn and read back, bottom row first: their levels go into the page,
     * and the sheet can be used. No pictures when the read back failed. A figure cut off by
     * the edge of its tiles is queued again, larger.
     */
    private static void store(Request request, int index, double size, int @Nullable [][] pictures, int generation) {
        ImpostorKey key = request.key;
        boolean again = false;
        try {
            if (generation != ImpostorAtlas.generation || pictures == null) return;
            double growth = growth(pictures, request.render());
            if (growth > 1.01 && request.grow < MAX_GROW) {
                QUEUE.add(new Request(key, request.puppet, Math.min(MAX_GROW, request.grow * growth)));
                again = true;
                return;
            }
            int[][][] views = new int[ImpostorViews.VIEWS][][];
            for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                views[view] = ImpostorPixels.mips(pictures[view], request.render(), ImpostorViews.TILE * request.scale(), MIPS);
            }
            Sheet sheet = new Sheet(index, size, request.scale());
            PageTexture page = page(sheet.page());
            for (int level = 0; level < MIPS; level++) {
                int tile = sheet.tile() >> level;
                int width = tile * ImpostorViews.VIEWS;
                int[] strip = new int[width * tile];
                for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                    int[] pixels = views[view][level];
                    // Read back bottom row first: turned so that the top of the figure is at the top.
                    for (int y = 0; y < tile; y++) {
                        System.arraycopy(pixels, y * tile, strip, (tile - 1 - y) * width + view * tile, tile);
                    }
                }
                write(page, level, sheet.column() * ImpostorViews.VIEWS * tile, sheet.row() * tile, width, tile, strip);
            }
            BAKING.remove(index);
            SHEETS.put(key, sheet);
        } catch (RuntimeException | LinkageError error) {
            if (generation == ImpostorAtlas.generation) fail(error);
        } finally {
            if (generation == ImpostorAtlas.generation) {
                if (!again) QUEUED.remove(key);
                if (BAKING.remove(index)) FREE.push(index);
            }
        }
    }

    /** How much larger the views must be drawn to stay within what their tiles show. */
    private static double growth(int[][] pictures, int render) {
        int hidden = (int) Math.ceil(ImpostorRenderer.INSET * render);
        double growth = 1;
        for (int[] picture : pictures) {
            growth = Math.max(growth, ImpostorPixels.growth(picture, render, hidden, render * (1 - FEET), GROW));
        }
        return growth;
    }

    /**
     * Stands a figure still, lit evenly and turned for one view. The camera sits in front of
     * a figure whose body faces 180 degrees, as in the inventory; a camera that sees it
     * from {@code r} degrees round its front sees the figure turned by minus {@code r}.
     */
    private static void stage(EntityRenderState state, int view) {
        // Before 1.21.9 the light is handed over when the figure is drawn, and no shadow or
        // outline is drawn along with it.
        //? if >=1.21.9 {
        state.lightCoords = FULL_BRIGHT;
        state.shadowPieces.clear();
        state.shadowRadius = 0;
        state.outlineColor = 0;
        //?}
        state.nameTag = null;
        state.ageInTicks = 0;
        state.displayFireAnimation = false;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180f - view * (360f / ImpostorViews.VIEWS);
            living.yRot = 0;
            living.xRot = 0;
            living.walkAnimationPos = 0;
            living.walkAnimationSpeed = 0;
            living.deathTime = 0;
            living.hasRedOverlay = false;
            living.bedOrientation = null;
        }
    }
}
