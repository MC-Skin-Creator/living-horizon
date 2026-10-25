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
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.CameraRenderState;
//?} else
//import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The pictures of the impostors, baked by the game's own renderers.
 *
 * <p>A <em>sheet</em> is one figure turned through {@link ImpostorViews#VIEWS} angles. Each
 * view is drawn the way the inventory draws a player (an orthographic picture of the
 * entity through its own renderer, so skins, armour, resource packs and other mods'
 * models are all followed), twice as large as a tile, into a small target.
 *
 * <p>The pictures are then read back and their mip levels worked out on the CPU
 * ({@link ImpostorPixels}), and written into a page that has real mip levels: the
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
 * the sheets away; they are read back from disk when the packs are the same, baked again
 * from the figures at hand otherwise.
 *
 * <p>Each kind of figure has pages of its own, one sheet per row, sized to the looks it
 * has: a cow with two looks takes a small page, villagers a few larger ones. The mobs keep
 * their sheets, from one world to the next, and on disk from one game to the next
 * ({@link ImpostorDisk}); the players' are thrown away once unused.
 *
 * <p>Besides entities, the mod's own figures are baked the same way ({@link ImpostorFigure}:
 * the 3D birds), drawn by the mod instead of an entity renderer, seen from the height the
 * figure asks for, and baked again each game rather than kept on disk.
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

    /**
     * A flying mob pictured from a height ({@link ImpostorKey#seenFrom}) is turned round its
     * middle, which stands in the middle of its tiles: half its height above its feet.
     */
    public static final float MIDDLE = 0.5f;

    /** Blocks from a flying mob's feet to the middle it is pictured round, from a height. */
    public static double middle(Entity entity) {
        return entity.getBbHeight() / 2;
    }

    /** Blocks from a mount's feet to the middle it and its rider are pictured round, from a height. */
    public static double middle(Entity mount, @Nullable Rider rider) {
        if (rider == null) return middle(mount);
        return Math.max(mount.getBbHeight(), rider.seat().y + rider.entity().getBbHeight()) / 2;
    }

    /** How far down a tile the origin of an entity's pictures is: its feet, or its middle when seen from a height. */
    private static float feet(ImpostorKey key) {
        return Float.isNaN(ImpostorKey.elevation(key)) ? FEET : MIDDLE;
    }

    /** Each view is drawn this many times larger than a tile, then averaged down. */
    private static final int SUPERSAMPLE = 2;
    private static final int RENDER = ImpostorViews.TILE * SUPERSAMPLE;

    /**
     * Sheets down a kind of figure's first page, and down its largest: each new page of a kind
     * holds twice as many as its last, up to a page about 2048 pixels tall.
     */
    private static final int FIRST_ROWS = 4, ROWS = 32;
    /**
     * Pixels across a page: one sheet, its views side by side. Every tile is
     * {@link ImpostorViews#TILE} pixels: a figure larger than its box (a horse's muzzle) is drawn
     * smaller in its tile instead (see {@link Request}). At a hundred blocks a figure is a dozen
     * pixels tall, and tiles twice as large took four times the memory for nothing to see.
     */
    public static final int PAGE_WIDTH = ImpostorViews.TILE * ImpostorViews.VIEWS;

    /** Bytes of video memory a row takes: a strip of 512 by 64 pixels and its mip levels. */
    private static final long ROW_BYTES = rowBytes();
    /**
     * Rows the pages of the mobs may hold, set by the game tests in place of what the settings
     * allow; 0 for the settings.
     */
    private static int mobRowsForTests;

    /**
     * Rows the pages of the mobs, or of the players, may hold in all: the video memory the
     * settings allow each
     * ({@link FarConfig#impostorMobMemory}, 1 GB, and {@link FarConfig#impostorPlayerMemory},
     * 512 MB), taken only as pages are needed. Each has its own: the players never take the mobs'
     * room, nor the mobs the players'. The mobs keep theirs: a world has only so many looks, met
     * again from one world to the next, and a server of endless variations stays bounded. The
     * players come and go: theirs are thrown away once unused for {@link #PLAYER_KEEP}, or when
     * their pages are full. Every player has a look of its own, and a rider one more with what it
     * rides: a crowded server, or hundreds of players who logged off, need them all.
     */
    private static int rows(boolean players) {
        if (!players && mobRowsForTests > 0) return mobRowsForTests;
        FarConfig config = FarConfig.get();
        long megabytes = players ? config.impostorPlayerMemory : config.impostorMobMemory;
        return (int) Math.max(1, (megabytes << 20) / ROW_BYTES);
    }

    /** Bytes of video memory the pages of the players, or of the mobs, take now. */
    public static long memory(boolean players) {
        return (long) taken(players) * ROW_BYTES;
    }

    /** Bytes of video memory the pages of the players, or of the mobs, may take at most. */
    public static long memoryLimit(boolean players) {
        return (long) rows(players) * ROW_BYTES;
    }

    /** Whether a sheet is filed with the players' (players, riders with what they ride), not the mobs'. */
    public static boolean player(ImpostorKey key) {
        return PLAYER.equals(key.type());
    }
    /** Milliseconds a player's sheet is kept once nobody draws or asks for it. */
    private static final long PLAYER_KEEP = 60_000L;
    private static final String PLAYER = ImpostorKey.PLAYER;

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
     * Milliseconds a sheet is kept after it was last drawn or asked for, however full the
     * pages: longer than the second between two rounds of asking for every figure on hand.
     * Giving way sooner, a crowd larger than the pages would bake its sheets over and over,
     * each new one throwing out another still in use.
     */
    private static final long IN_USE = 10_000L;

    /**
     * The game's light in the world, as the picture is drawn: the drawing turns the figure
     * over (see {@link #pose}), and its normals with it, so the light is turned over too.
     * The pictures are then shaded like a model in the world, not like one in the inventory.
     */
    private static final Vector3f LIGHT_0 = new Vector3f(0.2f, 1f, -0.7f).normalize().negate();
    private static final Vector3f LIGHT_1 = new Vector3f(-0.2f, 1f, 0.7f).normalize().negate();

    /**
     * Where a figure's pictures are: which page and row of it, how many rows the page has,
     * how big a tile is in blocks, and how far down a tile the figure's origin is ({@link #FEET}
     * for an entity's feet).
     */
    public record Sheet(int index, int rows, double worldSize, float feet) {
        public int page() {
            return index / ROWS;
        }

        public int row() {
            return index % ROWS;
        }

        /** Pixels along a side of one of its tiles. */
        public int tile() {
            return ImpostorViews.TILE;
        }

        /** The left edge of a view's tile, as a fraction of the page's width. */
        public float u(int view) {
            return view / (float) ImpostorViews.VIEWS;
        }

        /** The top edge of its tiles, as a fraction of the page's height. */
        public float v() {
            return row() / (float) rows;
        }

        /** A tile's width, as a fraction of the page's. */
        public float uSpan() {
            return 1f / ImpostorViews.VIEWS;
        }

        /** A tile's height, as a fraction of the page's. */
        public float vSpan() {
            return 1f / rows;
        }

        /** Pixels across its page. */
        public int pageWidth() {
            return tile() * ImpostorViews.VIEWS;
        }

        /** Pixels down its page. */
        public int pageHeight() {
            return rows * tile();
        }
    }

    /** The pages of one kind of figure, each with the rows not yet taken. */
    private static final class Page {
        final String type;
        final int rows;
        final PageTexture texture;
        final Deque<Integer> free = new ArrayDeque<>();

        Page(String type, int rows, PageTexture texture) {
            this.type = type;
            this.rows = rows;
            this.texture = texture;
            for (int row = 0; row < rows; row++) free.add(row);
        }
    }

    /**
     * A figure on a mount, baked on it in one picture: its feet {@code seat} blocks from the
     * mount's in the mount's own frame, turned {@code yaw} degrees from it.
     */
    public record Rider(Entity entity, Vec3 seat, float yaw) {
    }

    /** A sheet read from disk, to upload; no sheet when it could not be read, to bake instead. */
    private record Loaded(ImpostorKey key, Entity puppet, ImpostorDisk.@Nullable Stored stored, int generation) {
    }

    /**
     * A figure to bake, its picture covering {@code grow} times what {@link #size} gives: a
     * figure longer than its box (a horse's muzzle, a sniffer) is baked again with more round
     * it, drawn smaller in a tile of the same pixels.
     */
    private record Request(ImpostorKey key, @Nullable Entity puppet, @Nullable ImpostorFigure figure, double grow,
                           @Nullable Rider rider) {
        static Request of(ImpostorKey key, Entity puppet, double grow) {
            return of(key, puppet, grow, null);
        }

        static Request of(ImpostorKey key, Entity puppet, double grow, @Nullable Rider rider) {
            return new Request(key, puppet, null, grow, rider);
        }

        /** One of the mod's own figures: its tile is the size it asks for, never grown. */
        static Request of(ImpostorKey key, ImpostorFigure figure) {
            return new Request(key, null, figure, 1.0, null);
        }

        /** The same figure, baked again with its picture covering {@code grow} times its box. */
        Request grown(double grow) {
            return of(key, puppet, grow, rider);
        }

        /** Blocks along a side of its tiles: a mount's, with its rider on top. */
        double size() {
            if (figure != null) return figure.worldSize();
            if (rider == null) return ImpostorAtlas.size(puppet) * grow;
            return ImpostorViews.worldSize(Math.max(puppet.getBbHeight(), rider.seat.y + rider.entity.getBbHeight()),
                    Math.max(puppet.getBbWidth(), rider.entity.getBbWidth())) * grow;
        }

        /** How far down a tile its origin is. */
        float feet() {
            return figure != null ? figure.feet() : ImpostorAtlas.feet(key);
        }

        /** Degrees the camera looks down on it from: 0 for a figure pictured level with it. */
        float elevation() {
            if (figure != null) return figure.elevation();
            float elevation = ImpostorKey.elevation(key);
            return Float.isNaN(elevation) ? 0f : elevation;
        }

        /** Pixels along a side of the target a view is drawn in. */
        int render() {
            return RENDER;
        }
    }

    /** Milliseconds a figure the pages had no room for waits before it is asked for again. */
    private static final long REFUSED_FOR = 30_000L;

    /**
     * How much larger a figure cut off by the very edge of its picture is baked again at
     * least, and how much larger than {@link #size} a picture can get.
     */
    private static final double GROW = 1.3, MAX_GROW = 3;

    //? if >=1.21.6 {
    private static final class PageTexture extends AbstractTexture {
        PageTexture(String label, int width, int height) {
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(label, GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    COLOR, width, height, 1, MIPS);
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
                int wide = width >> level, tall = height >> level;
                try (NativeImage empty = new NativeImage(wide, tall, true)) {
                    //? if >=26.2 {
                    /*encoder.writeToTexture(texture, empty, level, 0, 0, 0);
                    *///?} else {
                    encoder.writeToTexture(texture, empty, level, 0, 0, 0, wide, tall, 0, 0);
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
        PageTexture(String label, int width, int height) {
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(label, COLOR, width, height, MIPS);
            // Smooth from far, but the pixels of the model when a figure is larger than its tile.
            texture.setAddressMode(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE);
            texture.setTextureFilter(FilterMode.LINEAR, FilterMode.NEAREST, true);
            CommandEncoder encoder = device.createCommandEncoder();
            // Nothing in it yet, at every level: the filter reads a little past each tile.
            for (int level = 0; level < MIPS; level++) {
                int wide = width >> level, tall = height >> level;
                try (NativeImage empty = new NativeImage(wide, tall, true)) {
                    encoder.writeToTexture(texture, empty, level, 0, 0, wide, tall, 0, 0);
                }
            }
        }
    }
    *///?} else {
    /*/^* A page as a plain OpenGL texture, its mip levels allocated and cleared up front. ^/
    private static final class PageTexture extends AbstractTexture {
        PageTexture(String label, int width, int height) {
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
                int wide = width >> level, tall = height >> level;
                ByteBuffer empty = MemoryUtil.memCalloc(wide * tall * PIXEL_BYTES);
                try {
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA8, wide, tall, 0, GL11.GL_RGBA,
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
    /** Where each sheet's pictures come from, for the impostor screen. */
    private static final Map<ImpostorKey, Origin> ORIGINS = new HashMap<>();
    /** When each sheet was last drawn or asked for, in milliseconds: written over in place. */
    private static final Map<ImpostorKey, long[]> USED = new HashMap<>();

    /** The sheet of {@code key} is drawn or asked for now. */
    private static void touch(ImpostorKey key) {
        long[] used = USED.get(key);
        if (used == null) USED.put(key, used = new long[1]);
        used[0] = System.currentTimeMillis();
    }
    private static final Deque<Request> QUEUE = new ArrayDeque<>();
    /** Queued, or drawn and waiting for the read back. */
    private static final Set<ImpostorKey> QUEUED = new HashSet<>();
    /** Sheets read from disk, handed over by the disk's thread. */
    private static final Queue<Loaded> LOADED = new ConcurrentLinkedQueue<>();
    /** Sheets read from disk rather than baked, and views drawn, since the game started. */
    private static int loads, bakes;
    /**
     * Figures whose pictures came out the same as another's, pixel for pixel: they share its
     * sheet. Two looks that differ only in something the picture does not show (a value
     * never drawn, one learnt to be restless after the first bake) take one row, one line of
     * the impostor screen.
     */
    private static final Map<ImpostorKey, ImpostorKey> ALIASES = new HashMap<>();
    /** The figure each picture was first baked for, by its kind and a hash of its pixels. */
    private static final Map<Look, ImpostorKey> LOOKS = new HashMap<>();

    private record Look(String type, long pixels) {
    }

    /** How much larger each figure was last baked: asked for again, it starts there. */
    private static final Map<ImpostorKey, Double> GROWN = new HashMap<>();
    /** Figures the pages had no room for, and when: not asked for again for a while. */
    private static final Map<ImpostorKey, Long> REFUSED = new HashMap<>();
    /** Figures whose renderer gives nothing to draw: never asked for again. */
    private static final Set<ImpostorKey> UNDRAWABLE = new HashSet<>();
    /** Rows being baked: not free, not yet a sheet. */
    private static final Set<Integer> BAKING = new HashSet<>();
    /** The pages by number, null where a player page was given back. */
    private static final List<@Nullable Page> PAGES = new ArrayList<>();
    //? if >=1.21.6 {
    /** The target views are drawn in. */
    private static @Nullable Target target;
    private static @Nullable Lighting lighting;
    //?} else {
    /*private static @Nullable RenderTarget target;
    /^* Our target while a figure is drawn, which the game then takes for its main one. ^/
    private static @Nullable RenderTarget drawing;
    *///?}
    private static long lastSweep;
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
        ImpostorDisk.clear();
    }

    /** One baked figure, for the impostor screen: where its pictures come from, if known. */
    public record Baked(ImpostorKey key, Sheet sheet, @Nullable Origin origin) {
    }

    /** Where a sheet's pictures come from. */
    public enum Source {
        /** Baked in an earlier game and read back from disk: shared between games and worlds. */
        DISK,
        /** Baked in this game and saved: the worlds opened later and the next games read it back. */
        SAVED,
        /** Baked in this game and not kept on disk: players, riders, the mod's own figures. */
        TEMPORARY
    }

    /**
     * Where a sheet's pictures come from, when they were baked (milliseconds since 1970, 0 when
     * not known: saved by an older version) and in which world or server (empty when not known).
     */
    public record Origin(Source source, long bakedAt, String world) {
    }

    /** Every figure baked, without counting as a use: a new list, the caller's to sort. */
    public static List<Baked> baked() {
        settle();
        List<Baked> baked = new ArrayList<>(SHEETS.size());
        SHEETS.forEach((key, sheet) -> baked.add(new Baked(key, sheet, ORIGINS.get(key))));
        return baked;
    }

    /** The world or server being played, as its list names it; empty in none. */
    private static String world() {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (minecraft.getSingleplayerServer() != null) return minecraft.getSingleplayerServer().getWorldData().getLevelName();
            if (minecraft.getCurrentServer() != null) return minecraft.getCurrentServer().name;
        } catch (RuntimeException ignored) {
            // Only a label for the impostor screen.
        }
        return "";
    }

    /** Whether impostors can be used at all: switched on, and the bake has not failed. */
    public static boolean usable() {
        FarConfig config = FarConfig.get();
        return config.anyDistant() && config.anyImpostors() && !failed;
    }

    /** The pictures of a figure, if baked. Counts as a use, so the sheets in use are kept. */
    public static @Nullable Sheet sheet(ImpostorKey key) {
        settle();
        ImpostorKey own = ALIASES.getOrDefault(key, key);
        Sheet sheet = SHEETS.get(own);
        if (sheet != null) touch(own);
        return sheet;
    }

    /**
     * Asks for a figure's pictures to be baked, within the next few frames. Already baked,
     * it counts as a use: the figures on hand keep their sheets.
     */
    public static void request(ImpostorKey key, Entity puppet) {
        if (failed || sheet(key) != null || UNDRAWABLE.contains(key) || refused(key) || !QUEUED.add(key)) return;
        // A mob met in an earlier game: read back rather than baked.
        if (!PLAYER.equals(key.type()) && ImpostorDisk.has(key)) {
            int asked = generation;
            ImpostorDisk.read(key).whenComplete((stored, error) ->
                    LOADED.add(new Loaded(key, puppet, error == null ? stored : null, asked)));
            return;
        }
        QUEUE.add(Request.of(key, puppet, GROWN.getOrDefault(key, 1.0)));
    }

    /**
     * Asks for a mount and its rider to be baked together, within the next few frames. Never
     * kept on disk: a rider is a player, whose skin may change.
     */
    public static void request(ImpostorKey key, Entity mount, Rider rider) {
        if (failed || sheet(key) != null || UNDRAWABLE.contains(key) || refused(key) || !QUEUED.add(key)) return;
        QUEUE.add(Request.of(key, mount, GROWN.getOrDefault(key, 1.0), rider));
    }

    /** Asks for one of the mod's own figures to be baked, within the next few frames. */
    public static void request(ImpostorKey key, ImpostorFigure figure) {
        if (failed || sheet(key) != null || refused(key) || !QUEUED.add(key)) return;
        QUEUE.add(Request.of(key, figure));
    }

    private static boolean refused(ImpostorKey key) {
        Long when = REFUSED.get(key);
        if (when == null) return false;
        if (System.currentTimeMillis() - when < REFUSED_FOR) return true;
        REFUSED.remove(key);
        return false;
    }

    /** Sheets read from disk rather than baked, since the game started. */
    public static int loads() {
        return loads;
    }

    /** Figures drawn to be baked since the game started, read back or not. */
    public static int bakes() {
        return bakes;
    }

    public static int sheets() {
        return SHEETS.size();
    }

    private static boolean baked(ImpostorKey key) {
        return SHEETS.containsKey(key) || ALIASES.containsKey(key);
    }

    /** A sheet thrown away: the figures sharing it, and its pixels, go with it. */
    private static void forget(ImpostorKey key) {
        USED.remove(key);
        ImpostorKey.forget(key);
        ORIGINS.remove(key);
        ALIASES.values().removeIf(key::equals);
        LOOKS.values().removeIf(key::equals);
    }

    /** Page numbers in use run below this. */
    public static int pages() {
        return PAGES.size();
    }

    /** Pixels across a page, 0 for a number not in use. */
    public static int pageWidth(int number) {
        Page page = number < PAGES.size() ? PAGES.get(number) : null;
        return page == null ? 0 : PAGE_WIDTH;
    }

    /** Pixels down a page, 0 for a number not in use. */
    public static int pageHeight(int number) {
        Page page = number < PAGES.size() ? PAGES.get(number) : null;
        return page == null ? 0 : page.rows * ImpostorViews.TILE;
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

    /** The render type of each page, made once: the game hands back the same one every time. */
    private static RenderType[] types = new RenderType[16];

    public static RenderType type(int page) {
        if (page >= types.length) types = Arrays.copyOf(types, Math.max(page + 1, types.length * 2));
        RenderType type = types[page];
        if (type != null) return type;
        //? if >=26.1 {
        /*type = RenderTypes.entityCutout(id(page));
        *///?} else {
        type = RenderTypes.entityCutoutNoCull(id(page));
        //?}
        return types[page] = type;
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
        for (int page = 0; page < PAGES.size(); page++) {
            if (PAGES.get(page) != null) minecraft.getTextureManager().release(id(page));
        }
        PAGES.clear();
        SHEETS.clear();
        ORIGINS.clear();
        ALIASES.clear();
        LOOKS.clear();
        USED.clear();
        QUEUE.clear();
        QUEUED.clear();
        BAKING.clear();
        LOADED.clear();
        GROWN.clear();
        REFUSED.clear();
        UNDRAWABLE.clear();
        ImpostorKey.forgetAll();
        // The packs or the mods may have changed: the sheets on disk are looked for again.
        ImpostorDisk.reset();
    }

    /**
     * Bakes what is queued, within a small budget. Called once a frame: from the GUI pass
     * from 1.21.6, at the start of the frame before.
     */
    public static void bakePending() {
        long started = Profile.begin(Profile.Section.BAKE);
        try {
            bakeQueued();
        } finally {
            Profile.end(Profile.Section.BAKE, started);
        }
    }

    private static void bakeQueued() {
        settle();
        if (!usable()) {
            for (Request request : QUEUE) QUEUED.remove(request.key);
            QUEUE.clear();
            for (Loaded loaded : LOADED) QUEUED.remove(loaded.key);
            LOADED.clear();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getOverlay() != null) return;
        forgetPlayers();
        long end = System.nanoTime() + BUDGET;
        Loaded loaded;
        while ((loaded = LOADED.poll()) != null) {
            if (loaded.generation != generation) continue;
            if (loaded.stored == null || !fits(loaded.stored.levels())) {
                ImpostorDisk.forget(loaded.key);
                QUEUE.add(Request.of(loaded.key, loaded.puppet, GROWN.getOrDefault(loaded.key, 1.0)));
                continue;
            }
            QUEUED.remove(loaded.key);
            if (baked(loaded.key)) continue;
            int index = allocate(loaded.key);
            if (index < 0) {
                REFUSED.put(loaded.key, System.currentTimeMillis());
                continue;
            }
            try {
                place(loaded.key, index, loaded.stored.worldSize(), feet(loaded.key), loaded.stored.levels());
                if (SHEETS.containsKey(loaded.key)) {
                    ORIGINS.put(loaded.key, new Origin(Source.DISK, loaded.stored.bakedAt(), loaded.stored.world()));
                }
                loads++;
            } catch (RuntimeException | LinkageError error) {
                free(index);
                fail(error);
                return;
            }
        }
        while (!QUEUE.isEmpty()) {
            Request request = QUEUE.poll();
            // Gliding since it was asked for: lying in the air, it is asked for again once it lands.
            if (request.puppet != null && request.puppet.level() != minecraft.level || baked(request.key)
                    || request.puppet instanceof LivingEntity glider && glider.isFallFlying()) {
                QUEUED.remove(request.key);
                continue;
            }
            Runnable back = request.puppet != null ? Puppets.atRest(request.puppet) : () -> {
            };
            Runnable turnedBack = request.puppet != null ? facingBack(request.puppet) : () -> {
            };
            try {
                bake(minecraft, request);
            } catch (LeftOut leftOut) {
                // That one figure only: its renderer gives the mod nothing to draw, now or later.
                UNDRAWABLE.add(request.key);
                continue;
            } catch (RuntimeException | LinkageError error) {
                fail(error);
                return;
            } finally {
                back.run();
                turnedBack.run();
                //? if >=1.21.6 && <26.3 {
                RenderSystem.outputColorTextureOverride = null;
                RenderSystem.outputDepthTextureOverride = null;
                //?}
            }
            if (System.nanoTime() > end) return;
        }
    }

    /** A figure whose renderer gives no state ({@link GhostRenderer#extract}): its bake is dropped, not the others. */
    private static final class LeftOut extends RuntimeException {
        LeftOut() {
            super(null, null, false, false);
        }
    }

    private static void fail(Throwable error) {
        failed = true;
        QUEUE.clear();
        QUEUED.clear();
        LivingHorizonClient.LOGGER.error("Impostors could not be baked, falling back to full models", error);
    }

    /**
     * A free row on a page of the figure's kind, as an index: a new page when its pages are
     * full, twice as tall as its last. When the pages of the mobs or of the players hold all
     * they may, the sheet of the same kind unused for longest gives way, unless it is still in
     * use: then the figure waits, drawn as a model. -1 for none.
     */
    private static int allocate(ImpostorKey key) {
        String type = key.type();
        int pagesOfType = 0;
        for (int number = 0; number < PAGES.size(); number++) {
            Page page = PAGES.get(number);
            if (page == null || !page.type.equals(type)) continue;
            pagesOfType++;
            if (!page.free.isEmpty()) return number * ROWS + page.free.pop();
        }
        int rows = Math.min(ROWS, FIRST_ROWS << Math.min(pagesOfType, 3));
        boolean player = PLAYER.equals(type);
        if (taken(player) + rows <= rows(player)) {
            int number = open(type, rows);
            return number * ROWS + PAGES.get(number).free.pop();
        }
        Iterator<Map.Entry<ImpostorKey, Sheet>> oldest = SHEETS.entrySet().iterator();
        while (oldest.hasNext()) {
            Map.Entry<ImpostorKey, Sheet> entry = oldest.next();
            if (!entry.getKey().type().equals(type)) continue;
            // Least recently used first: the ones after it are in use too.
            if (inUse(entry.getKey(), IN_USE)) return -1;
            oldest.remove();
            forget(entry.getKey());
            return entry.getValue().index;
        }
        return -1;
    }

    private static boolean inUse(ImpostorKey key, long within) {
        long[] used = USED.get(key);
        return used != null && System.currentTimeMillis() - used[0] < within;
    }

    /**
     * A row for a figure. -1 for none: the figure is drawn as a model, and not asked for again
     * for {@link #REFUSED_FOR}, rather than baked again every second for nothing.
     */
    private static int room(Request request) {
        int index = allocate(request.key);
        if (index >= 0) return index;
        QUEUED.remove(request.key);
        REFUSED.put(request.key, System.currentTimeMillis());
        return -1;
    }

    private static long rowBytes() {
        long bytes = 0;
        for (int level = 0; level < MIPS; level++) bytes += (long) (PAGE_WIDTH >> level) * (ImpostorViews.TILE >> level) * PIXEL_BYTES;
        return bytes;
    }

    /**
     * Rows the pages of the mobs may hold, in place of what the settings allow, 0 for that again:
     * for the game tests, which fill them with a few hundred figures.
     */
    public static void limitMobRows(int rows) {
        mobRowsForTests = Math.max(0, rows);
    }

    /** Rows of the pages of the players, or of the mobs. */
    private static int taken(boolean players) {
        int rows = 0;
        for (Page page : PAGES) {
            if (page != null && PLAYER.equals(page.type) == players) rows += page.rows;
        }
        return rows;
    }

    /** A new page for a kind of figure, in the first number given back or after the last. */
    private static int open(String type, int rows) {
        int number = PAGES.indexOf(null);
        if (number < 0) {
            number = PAGES.size();
            PAGES.add(null);
        }
        int tile = ImpostorViews.TILE;
        PageTexture texture = new PageTexture("living horizon impostors " + number, tile * ImpostorViews.VIEWS, rows * tile);
        Minecraft.getInstance().getTextureManager().register(id(number), texture);
        PAGES.set(number, new Page(type, rows, texture));
        return number;
    }

    /** A row back to its page, its sheet not baked or thrown away. */
    private static void free(int index) {
        Page page = PAGES.get(index / ROWS);
        if (page != null) page.free.push(index % ROWS);
    }

    /**
     * Every few seconds, throws away the players' sheets unused for {@link #PLAYER_KEEP}, and
     * gives back the players' pages left empty: they come and go, and wear other skins.
     */
    private static void forgetPlayers() {
        long now = System.currentTimeMillis();
        if (now - lastSweep < 5000) return;
        lastSweep = now;
        Iterator<Map.Entry<ImpostorKey, Sheet>> sheets = SHEETS.entrySet().iterator();
        while (sheets.hasNext()) {
            Map.Entry<ImpostorKey, Sheet> entry = sheets.next();
            if (!PLAYER.equals(entry.getKey().type()) || inUse(entry.getKey(), PLAYER_KEEP)) continue;
            sheets.remove();
            forget(entry.getKey());
            free(entry.getValue().index);
        }
        for (int number = 0; number < PAGES.size(); number++) {
            Page page = PAGES.get(number);
            if (page == null || !PLAYER.equals(page.type) || page.free.size() < page.rows) continue;
            Minecraft.getInstance().getTextureManager().release(id(number));
            PAGES.set(number, null);
        }
    }

    /** Blocks along a side of a figure's tiles. */
    private static double size(Entity puppet) {
        return ImpostorViews.worldSize(Math.max(0.3, puppet.getBbHeight()), Math.max(0.3, puppet.getBbWidth()));
    }

    /** A figure's state for one view, still and lit evenly; seated if its key says so. */
    private static EntityRenderState staged(Minecraft minecraft, Request request, int view) {
        Entity puppet = request.puppet;
        if (!(puppet instanceof LivingEntity)) {
            // A boat, a minecart: no body to turn in its state, the entity itself is turned
            // for each view, the way stage turns a mob's body. Put back after the bake.
            float facing = ImpostorViews.facing(view);
            puppet.setYRot(facing);
            puppet.yRotO = facing;
            puppet.setXRot(0f);
            puppet.xRotO = 0f;
        }
        EntityRenderState state = GhostRenderer.extract(minecraft.getEntityRenderDispatcher(), puppet, 1.0f, FULL_BRIGHT);
        if (state == null) throw new LeftOut();
        stage(state, view);
        posture(state, request.key.posture());
        // Water mobs were in water when last seen; a puppet is in no world.
        if (state instanceof LivingEntityRenderState living && GhostRenderer.livesInWater(puppet)) {
            living.isInWater = true;
        }
        return state;
    }

    /**
     * Blocks a figure lying down is raised by in its picture: the game lays a sleeper across
     * its feet, half of it below them, out of the tile. {@code GhostRenderer} lowers the
     * picture by as much.
     */
    public static final double LYING_LIFT = 0.3;

    private static double lift(Request request) {
        // A flyer seen from a height: its middle where the tile turns.
        if (request.puppet != null && !Float.isNaN(ImpostorKey.elevation(request.key))) {
            return -middle(request.puppet, request.rider);
        }
        return request.rider == null && request.key.posture() == ImpostorKey.LYING ? LYING_LIFT : 0;
    }

    /** What puts back the way an entity that is not a mob faces, turned view by view while baked. */
    private static Runnable facingBack(Entity puppet) {
        if (puppet instanceof LivingEntity) return () -> {
        };
        float yaw = puppet.getYRot(), yawO = puppet.yRotO, pitch = puppet.getXRot(), pitchO = puppet.xRotO;
        return () -> {
            puppet.setYRot(yaw);
            puppet.yRotO = yawO;
            puppet.setXRot(pitch);
            puppet.xRotO = pitchO;
        };
    }

    /** A rider's state for one view: on the mount, turned with it, in the riding pose. */
    private static EntityRenderState stagedRider(Minecraft minecraft, Rider rider, int view) {
        EntityRenderState state = GhostRenderer.extract(minecraft.getEntityRenderDispatcher(), rider.entity, 1.0f, FULL_BRIGHT);
        if (state == null) throw new LeftOut();
        stage(state, view);
        if (state instanceof LivingEntityRenderState living) living.bodyRot += rider.yaw;
        posture(state, ImpostorKey.SEATED);
        return state;
    }

    /** Where a rider's feet are drawn in a view: its seat, turned with the mount. */
    private static Vec3 seat(Rider rider, int view) {
        float mount = ImpostorViews.facing(view);
        return rider.seat.yRot((float) -Math.toRadians(mount));
    }

    /**
     * A figure seated (the riding pose, legs out in front) or lying down along its body, as
     * {@code GhostRenderer} draws players who logged off.
     */
    private static void posture(EntityRenderState state, int posture) {
        if (posture == ImpostorKey.SEATED && state instanceof HumanoidRenderState humanoid) humanoid.isPassenger = true;
        if (posture == ImpostorKey.LYING && state instanceof LivingEntityRenderState living) {
            living.pose = Pose.SLEEPING;
            living.bedOrientation = null;
        }
    }

    /**
     * As the inventory does it: pixels for blocks, y down, so turned over to stand. A figure
     * of the mod's seen from above has its top tipped towards the camera, which looks along +z.
     */
    private static PoseStack pose(Request request, double size, int render) {
        PoseStack pose = new PoseStack();
        float scale = (float) (render / size);
        pose.translate(render / 2f, render * request.feet(), 0f);
        pose.scale(scale, scale, -scale);
        pose.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        if (request.elevation() != 0f) {
            pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(-request.elevation())));
        }
        return pose;
    }

    /**
     * One view of a figure, handed to the game to draw: an entity through its own renderer,
     * a figure of the mod's drawn by itself, turned the same way an entity is by {@link #stage}.
     */
    //? if >=1.21.9 {
    private static void draw(Minecraft minecraft, Request request, int view, PoseStack pose, SubmitNodeStorage storage) {
        if (request.figure != null) {
            request.figure.draw(pose, new Sink(storage), ImpostorViews.facing(view));
        } else {
            minecraft.getEntityRenderDispatcher().submit(staged(minecraft, request, view), new CameraRenderState(),
                    0.0, lift(request), 0.0, pose, storage);
            if (request.rider != null) {
                Vec3 seat = seat(request.rider, view);
                minecraft.getEntityRenderDispatcher().submit(stagedRider(minecraft, request.rider, view),
                        new CameraRenderState(), seat.x, seat.y + lift(request), seat.z, pose, storage);
            }
        }
    }
    //?} else {
    /*private static void draw(Minecraft minecraft, Request request, int view, PoseStack pose, MultiBufferSource buffers) {
        if (request.figure != null) {
            request.figure.draw(pose, new Sink(buffers), ImpostorViews.facing(view));
        } else {
            GhostRenderer.drawState(staged(minecraft, request, view), 0.0, lift(request), 0.0, pose, buffers, FULL_BRIGHT);
            if (request.rider != null) {
                Vec3 seat = seat(request.rider, view);
                GhostRenderer.drawState(stagedRider(minecraft, request.rider, view), seat.x, seat.y + lift(request), seat.z,
                        pose, buffers, FULL_BRIGHT);
            }
        }
    }
    *///?}

    //? if <1.21.6 {
    /*/^* The projection a view is drawn with: pixels, y down, as the GUI's. ^/
    private static Matrix4f ortho(int render) {
        return new Matrix4f().setOrtho(0f, render, render, 0f, -1000f, 1000f);
    }
    *///?}

    //? if >=1.21.6 {
    private static Target target() {
        if (target != null) return target;
        int render = RENDER;
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
        target = created;
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
        int index = room(request);
        if (index < 0) return;
        double size = request.size();
        int render = request.render();
        BAKING.add(index);
        bakes++;
        GpuDevice device = RenderSystem.getDevice();
        int bytes = render * render * PIXEL_BYTES;
        GpuBuffer buffer = device.createBuffer(() -> "living horizon impostor read back",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, /*? if >=1.21.11 {*/ (long) /*?} else {*/ /*(int) *//*?}*/ bytes * ImpostorViews.VIEWS);
        boolean sent = false;
        try {
            Target target = target();
            //? if >=1.21.9 {
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
                PoseStack pose = pose(request, size, render);
                lighting().setupFor(Lighting.Entry.LEVEL);
                //? if >=26.3 {
                /*draw(minecraft, request, view, pose, target.storage);
                try (FeatureRenderDispatcher.PreparedFrame frame = features.prepareFrame(target.storage);
                     RenderPass pass = encoder.createRenderPass(() -> "living horizon impostor", target.colorView,
                             Optional.empty(), target.depthView, OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    FeatureRenderDispatcher.renderAllFeatures(pass, frame);
                }
                *///?} elif >=26.2 {
                /*draw(minecraft, request, view, pose, target.storage);
                features.renderAllFeatures(target.storage);
                *///?} elif >=1.21.9 {
                draw(minecraft, request, view, pose, features.getSubmitNodeStorage());
                features.renderAllFeatures();
                minecraft.renderBuffers().bufferSource().endBatch();
                //?} else {
                /*draw(minecraft, request, view, pose, buffers);
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
                free(index);
                QUEUED.remove(request.key);
                buffer.close();
            }
        }
    }
    //?} elif >=1.21.5 {
    /*private static RenderTarget target() {
        if (target == null) target = new TextureTarget("living horizon impostor", RENDER, RENDER, true);
        return target;
    }

    /^*
     * Draws the views one by one into our target, which the game takes for its main one
     * meanwhile: this version has no output override to point the drawing elsewhere.
     ^/
    private static void bake(Minecraft minecraft, Request request) {
        int index = room(request);
        if (index < 0) return;
        double size = request.size();
        int render = request.render();
        BAKING.add(index);
        bakes++;
        GpuDevice device = RenderSystem.getDevice();
        int bytes = render * render * PIXEL_BYTES;
        GpuBuffer buffer = device.createBuffer(() -> "living horizon impostor read back", BufferType.PIXEL_PACK,
                BufferUsage.STATIC_READ, bytes * ImpostorViews.VIEWS);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        // Whatever is still pending goes where it belongs, before our target stands in.
        buffers.endBatch();
        RenderTarget target = target();
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
                draw(minecraft, request, view, pose(request, size, render), buffers);
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
                free(index);
                QUEUED.remove(request.key);
                buffer.close();
            }
        }
    }
    *///?} else {
    /*/^* The eight views side by side, each in a square of its own. ^/
    private static RenderTarget target() {
        if (target != null) return target;
        int render = RENDER;
        //? if >=1.21.2 {
        RenderTarget created = new TextureTarget(render * ImpostorViews.VIEWS, render, true);
        //?} else
        //RenderTarget created = new TextureTarget(render * ImpostorViews.VIEWS, render, true, Minecraft.ON_OSX);
        created.setClearColor(0f, 0f, 0f, 0f);
        target = created;
        return created;
    }

    /^*
     * Draws the eight views side by side into our target, which the game takes for its main
     * one meanwhile, and reads them back at once.
     ^/
    private static void bake(Minecraft minecraft, Request request) {
        int index = room(request);
        if (index < 0) return;
        double size = request.size();
        int render = request.render();
        BAKING.add(index);
        bakes++;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        // Whatever is still pending goes where it belongs, before our target stands in.
        buffers.endBatch();
        RenderTarget target = target();
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
                draw(minecraft, request, view, pose(request, size, render), buffers);
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
                free(index);
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
        long started = Profile.begin(Profile.Section.BAKE_READ_BACK);
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
            Profile.end(Profile.Section.BAKE_READ_BACK, started);
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
        long started = Profile.begin(Profile.Section.BAKE_STORE);
        try {
            storeSheet(request, index, size, pictures, generation);
        } finally {
            Profile.end(Profile.Section.BAKE_STORE, started);
        }
    }

    private static void storeSheet(Request request, int index, double size, int @Nullable [][] pictures, int generation) {
        ImpostorKey key = request.key;
        boolean again = false;
        try {
            if (generation != ImpostorAtlas.generation || pictures == null) return;
            double growth = request.figure != null ? 1 : growth(pictures, request.render(), request.feet());
            if (growth > 1.01 && request.grow < MAX_GROW) {
                double grow = Math.min(MAX_GROW, request.grow * growth);
                GROWN.put(key, grow);
                QUEUE.add(request.grown(grow));
                again = true;
                return;
            }
            int[][][] views = new int[ImpostorViews.VIEWS][][];
            for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                views[view] = ImpostorPixels.mips(pictures[view], request.render(), ImpostorViews.TILE, MIPS);
            }
            int[][] levels = new int[MIPS][];
            for (int level = 0; level < MIPS; level++) {
                int tile = ImpostorViews.TILE >> level;
                int width = tile * ImpostorViews.VIEWS;
                int[] strip = new int[width * tile];
                for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                    int[] pixels = views[view][level];
                    // Read back bottom row first: turned so that the top of the figure is at the top.
                    for (int y = 0; y < tile; y++) {
                        System.arraycopy(pixels, y * tile, strip, (tile - 1 - y) * width + view * tile, tile);
                    }
                }
                levels[level] = strip;
            }
            if (!place(key, index, size, request.feet(), levels)) return;
            BAKING.remove(index);
            // The mod's own figures are not kept: their sheets have no entity to be read for.
            boolean kept = !PLAYER.equals(key.type()) && request.figure == null;
            long now = System.currentTimeMillis();
            String world = world();
            if (SHEETS.containsKey(key)) ORIGINS.put(key, new Origin(kept ? Source.SAVED : Source.TEMPORARY, now, world));
            if (kept) ImpostorDisk.write(key, size, levels, now, world);
        } catch (RuntimeException | LinkageError error) {
            if (generation == ImpostorAtlas.generation) fail(error);
        } finally {
            if (generation == ImpostorAtlas.generation) {
                if (!again) QUEUED.remove(key);
                if (BAKING.remove(index)) free(index);
            }
        }
    }

    /** Whether the levels read from disk have the size of a sheet's: written by another format otherwise. */
    private static boolean fits(int[][] levels) {
        if (levels.length != MIPS) return false;
        for (int level = 0; level < MIPS; level++) {
            int tile = ImpostorViews.TILE >> level;
            if (levels[level] == null || levels[level].length != tile * ImpostorViews.VIEWS * tile) return false;
        }
        return true;
    }

    /**
     * Writes a sheet's levels, its views side by side, into its row: it can be used. The same
     * pixels as a sheet of its kind already baked: it shares that one, and its row is freed.
     */
    private static boolean place(ImpostorKey key, int index, double size, float feet, int[][] levels) {
        Page owner = PAGES.get(index / ROWS);
        if (owner == null) return false;
        Look look = new Look(key.type(), pixels(levels[0]));
        ImpostorKey same = LOOKS.get(look);
        if (same != null && !same.equals(key) && SHEETS.containsKey(same)) {
            ALIASES.put(key, same);
            touch(same);
            free(index);
            return true;
        }
        LOOKS.put(look, key);
        Sheet sheet = new Sheet(index, owner.rows, size, feet);
        for (int level = 0; level < MIPS; level++) {
            int tile = sheet.tile() >> level;
            write(owner.texture, level, 0, sheet.row() * tile, tile * ImpostorViews.VIEWS, tile, levels[level]);
        }
        SHEETS.put(key, sheet);
        touch(key);
        return true;
    }

    /** A hash of a picture's pixels: two looks drawn the same have the same. */
    private static long pixels(int[] level) {
        long hash = 1125899906842597L;
        for (int pixel : level) hash = hash * 31 + pixel;
        return hash;
    }

    /** How much larger the views must be drawn to stay within what their tiles show. */
    private static double growth(int[][] pictures, int render, float feet) {
        int hidden = (int) Math.ceil(ImpostorRenderer.INSET * render);
        double growth = 1;
        for (int[] picture : pictures) {
            growth = Math.max(growth, ImpostorPixels.growth(picture, render, hidden, render * (1 - feet), GROW));
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
            living.bodyRot = ImpostorViews.facing(view);
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
