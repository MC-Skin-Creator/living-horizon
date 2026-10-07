package fr.clixmods.livinghorizon.render.impostor;

//? if >=1.21.9 {
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
import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import net.minecraft.client.Minecraft;
//? if >=26.1 {
/*import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
*///?} else {
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
//?}
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
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
 * <p>Baking is queued and done a little each frame, from the GUI pass, where the game
 * itself renders entities to textures; the read back lands a frame or two later. Everything
 * here runs on the render thread. Resource reloads throw the sheets away; they are baked
 * again from the figures at hand.
 */
public final class ImpostorAtlas {
    /**
     * Mip levels of a page: tiles of 64, 32, 16 and 8 pixels. A smaller level would blend
     * a figure with the views next to it in the page; smaller than that, a figure is a
     * couple of pixels anyway.
     */
    public static final int MIPS = 4;

    /** The feet stand this far down a tile, as a fraction of its height. */
    public static final float FEET = 0.95f;

    /** Each view is drawn this many times larger than a tile, then averaged down. */
    private static final int SUPERSAMPLE = 2;
    private static final int RENDER = ImpostorViews.TILE * SUPERSAMPLE;

    /** Sheets across and down one page. */
    private static final int COLUMNS = 4, ROWS = 32, PER_PAGE = COLUMNS * ROWS, MAX_PAGES = 2;
    /** Tiles across and down one page. */
    public static final int TILES_ACROSS = COLUMNS * ImpostorViews.VIEWS, TILES_DOWN = ROWS;
    private static final int PAGE_SIZE = ImpostorViews.TILE * TILES_ACROSS;

    /** The formats of the pages and of the target a view is drawn in, and the bytes of a pixel. */
    //? if >=26.2 {
    /*private static final GpuFormat COLOR = GpuFormat.RGBA8_UNORM, DEPTH = GpuFormat.D32_FLOAT;
    *///?} else {
    private static final TextureFormat COLOR = TextureFormat.RGBA8, DEPTH = TextureFormat.DEPTH32;
    //?}
    private static final int PIXEL_BYTES = 4;

    /** Nanoseconds a frame may spend baking. One sheet is always started. */
    private static final long BUDGET = 3_000_000L;

    /**
     * The game's light in the world, as the picture is drawn: the drawing turns the figure
     * over (see {@link #bake}), and its normals with it, so the light is turned over too.
     * The pictures are then shaded like a model in the world, not like one in the inventory.
     */
    private static final Vector3f LIGHT_0 = new Vector3f(0.2f, 1f, -0.7f).normalize().negate();
    private static final Vector3f LIGHT_1 = new Vector3f(-0.2f, 1f, 0.7f).normalize().negate();

    /** Where a figure's pictures are: which cell of the pages, and how big a tile is in blocks. */
    public record Sheet(int index, double worldSize) {
        public int page() {
            return index / PER_PAGE;
        }

        public int column() {
            return index % PER_PAGE % COLUMNS;
        }

        public int row() {
            return index % PER_PAGE / COLUMNS;
        }
    }

    private record Request(ImpostorKey key, Entity puppet) {
    }

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

    private static final Map<ImpostorKey, Sheet> SHEETS = new LinkedHashMap<>(64, 0.75f, true);
    private static final Deque<Request> QUEUE = new ArrayDeque<>();
    /** Queued, or drawn and waiting for the read back. */
    private static final Set<ImpostorKey> QUEUED = new HashSet<>();
    private static final Deque<Integer> FREE = new ArrayDeque<>();
    /** Cells being baked: not free, not yet a sheet. */
    private static final Set<Integer> BAKING = new HashSet<>();
    private static final List<PageTexture> PAGES = new ArrayList<>();
    private static @Nullable Target target;
    private static @Nullable Lighting lighting;
    private static int next;
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

    /** Every figure baked, without counting as a use. */
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
        QUEUE.add(new Request(key, puppet));
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
        next = 0;
        ImpostorKey.forgetAll();
    }

    /** Bakes what is queued, within a small budget. Called once a frame, from the GUI pass. */
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
                //? if <26.3 {
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

    private static int allocate() {
        if (!FREE.isEmpty()) return FREE.pop();
        if (next < MAX_PAGES * PER_PAGE) return next++;
        // Full: the sheet unused for longest gives way.
        Iterator<Sheet> oldest = SHEETS.values().iterator();
        if (!oldest.hasNext()) return -1;
        Sheet evicted = oldest.next();
        oldest.remove();
        return evicted.index;
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

    private static Target target() {
        if (target != null) return target;
        GpuDevice device = RenderSystem.getDevice();
        Target created = new Target();
        created.color = device.createTexture("living horizon impostor",
                GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING
                        | GpuTexture.USAGE_RENDER_ATTACHMENT,
                COLOR, RENDER, RENDER, 1, 1);
        created.colorView = device.createTextureView(created.color);
        created.depth = device.createTexture("living horizon impostor depth",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT, DEPTH, RENDER, RENDER, 1, 1);
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
        Entity puppet = request.puppet;
        double size = ImpostorViews.worldSize(Math.max(0.3, puppet.getBbHeight()), Math.max(0.3, puppet.getBbWidth()));
        int index = allocate();
        if (index < 0) {
            QUEUED.remove(request.key);
            return;
        }
        BAKING.add(index);
        GpuDevice device = RenderSystem.getDevice();
        int bytes = RENDER * RENDER * PIXEL_BYTES;
        GpuBuffer buffer = device.createBuffer(() -> "living horizon impostor read back",
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, /*? if >=1.21.11 {*/ (long) /*?} else {*/ /*(int) *//*?}*/ bytes * ImpostorViews.VIEWS);
        boolean sent = false;
        try {
            Target target = target();
            EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
            FeatureRenderDispatcher features = minecraft.gameRenderer.getFeatureRenderDispatcher();
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
                target.ortho.setupOrtho(-1000f, 1000f, RENDER, RENDER, true);
                RenderSystem.setProjectionMatrix(target.projection.getBuffer(target.ortho), ProjectionType.ORTHOGRAPHIC);
                *///?} elif >=26.1 {
                /*encoder.clearColorAndDepthTextures(target.color, 0, target.depth, 1.0);
                RenderSystem.setProjectionMatrix(
                        target.projection.getBuffer(new Matrix4f().setOrtho(0f, RENDER, RENDER, 0f, -1000f, 1000f)),
                        ProjectionType.ORTHOGRAPHIC);
                *///?} else {
                encoder.clearColorAndDepthTextures(target.color, 0, target.depth, 1.0);
                RenderSystem.setProjectionMatrix(target.projection.getBuffer(RENDER, RENDER), ProjectionType.ORTHOGRAPHIC);
                //?}
                EntityRenderState state = dispatcher.extractEntity(puppet, 1.0f);
                stage(state, view);
                // Water mobs were in water when last seen; a puppet is in no world.
                if (state instanceof LivingEntityRenderState living && GhostRenderer.livesInWater(puppet)) {
                    living.isInWater = true;
                }
                // As the inventory does it: pixels for blocks, y down, so turned over to stand.
                PoseStack pose = new PoseStack();
                float scale = (float) (RENDER / size);
                pose.translate(RENDER / 2f, RENDER * FEET, 0f);
                pose.scale(scale, scale, -scale);
                pose.mulPose(new Quaternionf().rotateZ((float) Math.PI));
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
                *///?} else {
                dispatcher.submit(state, new CameraRenderState(), 0.0, 0.0, 0.0, pose, features.getSubmitNodeStorage());
                features.renderAllFeatures();
                minecraft.renderBuffers().bufferSource().endBatch();
                //?}
                boolean last = view == ImpostorViews.VIEWS - 1;
                encoder.copyTextureToBuffer(target.color, buffer, /*? if >=1.21.11 {*/ (long) /*?} else {*/ /*(int) *//*?}*/ bytes * view,
                        last ? () -> store(request.key, index, size, buffer, generation) : () -> {
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

    /** The views are read back: their levels go into the page, and the sheet can be used. */
    private static void store(ImpostorKey key, int index, double size, GpuBuffer buffer, int generation) {
        try {
            if (generation != ImpostorAtlas.generation) return;
            int[][][] views = new int[ImpostorViews.VIEWS][][];
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            //? if >=26.2 {
            /*try (GpuBufferSlice.MappedView mapped = buffer.map(true, false)) {
            *///?} else {
            try (GpuBuffer.MappedView mapped = encoder.mapBuffer(buffer, true, false)) {
            //?}
                ByteBuffer data = mapped.data().order(ByteOrder.LITTLE_ENDIAN);
                int[] pixels = new int[RENDER * RENDER];
                for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                    data.asIntBuffer().get(view * pixels.length, pixels);
                    views[view] = ImpostorPixels.mips(pixels, RENDER, ImpostorViews.TILE, MIPS);
                }
            }
            Sheet sheet = new Sheet(index, size);
            PageTexture page = page(sheet.page());
            for (int level = 0; level < MIPS; level++) {
                int tile = ImpostorViews.TILE >> level;
                try (NativeImage strip = new NativeImage(tile * ImpostorViews.VIEWS, tile, false)) {
                    for (int view = 0; view < ImpostorViews.VIEWS; view++) {
                        int[] pixels = views[view][level];
                        // Read back bottom row first: turned so that the top of the figure is at the top.
                        for (int y = 0; y < tile; y++) {
                            for (int x = 0; x < tile; x++) {
                                strip.setPixelABGR(view * tile + x, tile - 1 - y, pixels[y * tile + x]);
                            }
                        }
                    }
                    //? if >=26.2 {
                    /*encoder.writeToTexture(page.getTexture(), strip, level, 0,
                            sheet.column() * ImpostorViews.VIEWS * tile, sheet.row() * tile);
                    *///?} else {
                    encoder.writeToTexture(page.getTexture(), strip, level, 0,
                            sheet.column() * ImpostorViews.VIEWS * tile, sheet.row() * tile, strip.getWidth(), tile, 0, 0);
                    //?}
                }
            }
            BAKING.remove(index);
            SHEETS.put(key, sheet);
        } catch (RuntimeException | LinkageError error) {
            if (generation == ImpostorAtlas.generation) fail(error);
        } finally {
            if (generation == ImpostorAtlas.generation) {
                QUEUED.remove(key);
                if (BAKING.remove(index)) FREE.push(index);
            }
            buffer.close();
        }
    }

    /**
     * Stands a figure still, lit evenly and turned for one view. The camera sits in front of
     * a figure whose body faces 180 degrees, as in the inventory; a camera that sees it
     * from {@code r} degrees round its front sees the figure turned by minus {@code r}.
     */
    private static void stage(EntityRenderState state, int view) {
        state.lightCoords = 15728880;
        state.shadowPieces.clear();
        state.shadowRadius = 0;
        state.nameTag = null;
        state.outlineColor = 0;
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
//?} else {
/*import fr.clixmods.livinghorizon.LivingHorizonClient;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.List;

/^*
 * Before 1.21.9 the game has no way to draw an entity into a texture that the pictures can
 * be baked with, so impostors are off there: distant figures are always drawn as models.
 * The same names as on the newer versions, so that the rest of the mod reads them alike.
 ^/
public final class ImpostorAtlas {
    public static final int MIPS = 4;
    public static final float FEET = 0.95f;
    public static final int TILES_ACROSS = 4 * ImpostorViews.VIEWS, TILES_DOWN = 32;

    public record Sheet(int index, double worldSize) {
        public int page() {
            return 0;
        }

        public int column() {
            return 0;
        }

        public int row() {
            return 0;
        }
    }

    public record Baked(ImpostorKey key, Sheet sheet) {
    }

    private ImpostorAtlas() {
    }

    public static void invalidate() {
    }

    public static void regenerate() {
    }

    public static List<Baked> baked() {
        return List.of();
    }

    public static boolean usable() {
        return false;
    }

    public static @Nullable Sheet sheet(ImpostorKey key) {
        return null;
    }

    public static void request(ImpostorKey key, Entity puppet) {
    }

    public static int sheets() {
        return 0;
    }

    public static int waiting() {
        return 0;
    }

    /^* Reads as failed, which the impostor screen shows as the reason there are none. ^/
    public static boolean failed() {
        return true;
    }

    public static RenderType type(int page) {
        return RenderTypes.entityCutoutNoCull(id(page));
    }

    public static Identifier id(int page) {
        return Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "impostors/" + page);
    }

    public static void bakePending() {
    }
}
*///?}
