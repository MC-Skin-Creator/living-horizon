package fr.clixmods.livinghorizon.compat;

//? if >=1.21.6 {
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
//?}
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Voxy's fog on the world's entities, without a shader pack. Voxy fogs its terrain the way the
 * game fogs the world (the environmental fog: none at its start, full at its end, 1024 blocks in
 * a clear overworld), but stops it growing past a distance of its own: the render distance, at
 * least 320 blocks, times the square root of 3 (1.5 render distances before 0.2.6). The game's
 * fog on entities keeps growing: a mob past that distance was paler than the hill it stood on,
 * down to a flat patch of fog colour near the fog's end.
 *
 * <p>The game's shaders mix an entity's colour with the fog colour by the fog times that
 * colour's alpha, the fog going linearly from its start to its end. Ending it at Voxy's
 * distance and scaling the alpha by the fog Voxy reaches there gives Voxy's curve exactly: the
 * same fog up to that distance, all the way from the camera, and Voxy's from there on. The
 * game writes that fog next to its own ({@code FogRendererMixin}), and it is bound while the
 * world's entities are drawn, nowhere else: the sky, the clouds and the terrain keep the game's.
 * Only the enchantment glint and lightning, which read the fog without its alpha, fade a little
 * sooner.
 */
public final class FarFog {
    /** Where our fog sits in the game's fog buffer, after its own: a multiple of any uniform offset alignment. */
    public static final int OFFSET = 256;
    /** A render distance fog starting past this is off: Voxy moves it to a billion blocks. */
    private static final float NO_RENDER_DISTANCE_FOG = 1e8f;

    private FarFog() {
    }

    /**
     * The fog the entities get, from the game's this frame, Voxy's changes in it: its end, then
     * the factor on its colour's alpha. Null where it is the game's: switched off
     * ({@code optVoxyFog}), no Voxy, Voxy's fog off, a shader pack, the game's render distance
     * fog on, or Voxy's limit past the fog's end.
     */
    public static float @Nullable [] capped(float environmentalStart, float environmentalEnd, float renderDistanceStart) {
        if (!FarConfig.get().optVoxyFog) return null;
        if (!Float.isNaN(limitForTests)) {
            return environmentalEnd - environmentalStart > 1 ? limited(environmentalStart, environmentalEnd, limitForTests) : null;
        }
        // A fog that ends close (water, lava, blindness) is left alone, as Voxy leaves it.
        if (renderDistanceStart < NO_RENDER_DISTANCE_FOG || environmentalEnd < 10
                || environmentalEnd - environmentalStart <= 1) return null;
        if (!FarConfig.get().anyDistant() || IrisLink.shaderPackOn()) return null;
        return limited(environmentalStart, environmentalEnd, voxyLimit());
    }

    /**
     * A fog from {@code start} to {@code end} that stops growing at {@code limit} blocks: its
     * end, then the factor on its colour's alpha. Null where the limit changes nothing.
     */
    static float @Nullable [] limited(float start, float end, float limit) {
        if (!(limit < end)) return null; // NaN too: no limit
        float reached = Math.max(0, (limit - start) / (end - start));
        return new float[]{Math.max(limit, start + 1), reached};
    }

    /** A limit in place of Voxy's, NaN for none. */
    private static float limitForTests = Float.NaN;

    /**
     * Makes the fog stop growing at {@code blocks}, as Voxy's does, with or without Voxy; NaN
     * for Voxy's again. For the client game tests, where Voxy cannot run.
     */
    public static void limitForTests(float blocks) {
        limitForTests = blocks;
    }

    // --- Voxy's settings ----------------------------------------------------------------------

    private static boolean linked, broken;
    private static @Nullable Field config, environmentalFog, hasFog;
    private static @Nullable Method renderingEnabled, fogMode;
    /** A Voxy before 0.2.6, which stops its fog at 1.5 render distances. */
    private static boolean early;

    /** Blocks past which Voxy's fog stops growing, or NaN where Voxy draws no fog. */
    private static float voxyLimit() {
        if (!linked) link();
        if (broken || config == null) return Float.NaN;
        try {
            Object settings = config.get(null);
            if (settings == null) return Float.NaN;
            if (renderingEnabled != null && !(boolean) renderingEnabled.invoke(settings)) return Float.NaN;
            boolean fog = fogMode != null && hasFog != null
                    ? hasFog.getBoolean(fogMode.invoke(settings))
                    : environmentalFog != null && environmentalFog.getBoolean(settings);
            if (!fog) return Float.NaN;
            float view = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16f;
            return early ? view * 1.5f : Math.max(view, 320f) * (float) Math.sqrt(3);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            LivingHorizonClient.LOGGER.warn("Voxy's fog settings could not be read: distant mobs keep the game's fog", e);
            return Float.NaN;
        }
    }

    private static void link() {
        linked = true;
        if (!Platform.isModLoaded("voxy")) return;
        try {
            Class<?> type = Class.forName("me.cortex.voxy.client.config.VoxyConfig");
            config = type.getField("CONFIG");
            try {
                renderingEnabled = type.getMethod("isRenderingEnabled");
            } catch (NoSuchMethodException e) {
                renderingEnabled = null;
            }
            try {
                // From 0.2.19: fog, fade, both or neither.
                fogMode = type.getMethod("getFogMode");
                hasFog = Class.forName("me.cortex.voxy.client.core.NormalRenderPipeline$FogMode").getField("hasFog");
            } catch (NoSuchMethodException | ClassNotFoundException | NoSuchFieldException e) {
                fogMode = null;
                hasFog = null;
                environmentalFog = type.getField("useEnvironmentalFog");
            }
            try {
                type.getField("renderVanillaFog");
                early = true;
            } catch (NoSuchFieldException e) {
                early = false;
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            broken = true;
            LivingHorizonClient.LOGGER.warn("Voxy found, but not its fog settings: distant mobs keep the game's fog", e);
        }
    }

    // --- While the entities are drawn ---------------------------------------------------------

    /** The world's entities are submitted: the next drawing of features is theirs, not the hand's. */
    private static boolean armed;
    /** What drew the solid part of the world's entities: its translucent step follows, from 26.1. */
    private static @Nullable Object owner;

    /** Just before the world's entities are drawn; see {@code FarDepth.arm()}. */
    public static void arm() {
        armed = true;
    }

    //? if >=1.21.6 {
    /** The game's fog this frame, and ours next to it, or null where the game's stays. */
    private static @Nullable GpuBufferSlice world, far;
    /** The fog put back after the entities, while ours is bound. */
    private static @Nullable GpuBufferSlice saved;

    /** Where the game wrote this frame's fog, and ours if it wrote it. */
    public static void written(GpuBufferSlice game, @Nullable GpuBufferSlice ours) {
        world = game;
        far = ours;
    }

    /**
     * Just before the world's entities are drawn, by {@code by}: ours is bound in place of the
     * game's, and returned. Null where the game's stays.
     */
    public static @Nullable GpuBufferSlice begin(@Nullable Object by) {
        if (!armed) return null;
        armed = false;
        owner = by;
        return swap();
    }

    /** Just before the translucent parts of the world's entities are drawn, from 26.1: see {@link #begin}. */
    public static @Nullable GpuBufferSlice beginTranslucent(Object by) {
        return by == owner ? swap() : null;
    }

    private static @Nullable GpuBufferSlice swap() {
        if (far == null || saved != null) return null;
        GpuBufferSlice current = RenderSystem.getShaderFog();
        // The fog turned off (F3 + F), or this frame's fog not written yet.
        if (current == null || !current.equals(world)) return null;
        saved = current;
        RenderSystem.setShaderFog(far);
        return far;
    }

    /** Whether ours is bound: what is left of the entities in the buffers is drawn before {@link #end}. */
    public static boolean active() {
        return saved != null;
    }

    /** Just after the world's entities (or their translucent parts) are drawn: the game's fog is bound again, and returned. */
    public static @Nullable GpuBufferSlice end() {
        if (saved == null) return null;
        GpuBufferSlice back = saved;
        saved = null;
        RenderSystem.setShaderFog(back);
        return back;
    }

    /** Just after the translucent parts of the world's entities are drawn. */
    public static @Nullable GpuBufferSlice endTranslucent() {
        owner = null;
        return end();
    }
    //?} else {
    /*// Before 1.21.6 the overworld has no fog of its own past the render distance, and Voxy is
    // not there: nothing to match.
    public static void begin(@Nullable Object by) {
        armed = false;
        owner = by;
    }

    public static boolean active() {
        return false;
    }

    public static void end() {
    }
    *///?}
}
