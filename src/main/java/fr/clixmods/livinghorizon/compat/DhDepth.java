package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * What {@link FarDepth} needs to know about Distant Horizons to use its depth: the GL
 * texture it draws its terrain's depth into, and the projection that depth is made with.
 * The texture and the depth range come from its public API; the projection from the
 * parameters of its last render, which only its internals keep. All reached by reflection:
 * anything missing turns this off for the session, and the mobs show through the terrain
 * as they did before.
 */
final class DhDepth {
    private static boolean ready, broken, started;
    private static String reason = "not started";

    private static Field renderProxy, projection, m22, m23, m32, success, payload;
    private static Method depthTexture, name;
    /**
     * From Distant Horizons 3.3.2: the layout of its depth. Before, null: OpenGL's usual one,
     * -1 to 1 with nothing at 1.
     */
    private static @Nullable Method depthRange, depthDirection;
    private static @Nullable Field farDepth;
    /** From 3.2: the parameters of its last render. Before, null: its projection is rebuilt from its planes. */
    private static @Nullable Field renderParams;
    /** Before 3.2: where its near and far planes come from, the override that changes the near one. */
    private static @Nullable Method nearPlane, farPlane, nearOverride;
    /** From API 7.1, Distant Horizons may draw through Blaze3D, without a shader pack: its depth is reached through a wrapper. */
    private static @Nullable Method blazeDepthTexture, wrappedObject;
    /** Iris, when installed: whether a shader pack is on, and the planes it draws Distant Horizons with. */
    private static @Nullable Method irisApi, packInUse, irisNear, irisFar;

    private DhDepth() {
    }

    static boolean started() {
        return started;
    }

    /** Why {@link #read()} had nothing, for {@code /livinghorizon lod}. */
    static String reason() {
        return reason;
    }

    static synchronized boolean available() {
        if (broken) return false;
        if (!ready) {
            ready = true;
            if (!Platform.isModLoaded("distanthorizons")) {
                broken = true;
                return false;
            }
            try {
                link();
            } catch (Throwable e) {
                broken = true;
                reason = "Distant Horizons found, but too old or unknown (its depth needs 2.3 or newer): " + e;
                LivingHorizonClient.LOGGER.warn("Distant Horizons found, but its depth cannot be used: distant mobs show through its terrain", e);
                return false;
            }
        }
        started = true;
        return true;
    }

    /**
     * {@code {texture, a, b, signed, nothing}}: the GL texture of Distant Horizons' depth,
     * the two numbers of its projection that matter to depth, 1 when its depth runs from -1
     * to 1 (0 from 0 to 1), and the depth of a pixel with no terrain; null with the reason
     * in {@link #reason()} when there is none this frame.
     */
    static float @Nullable [] read() {
        try {
            Object proxy = renderProxy.get(null);
            if (proxy == null) return none("Distant Horizons has not started");
            Object result = depthTexture.invoke(proxy);
            int texture = success.getBoolean(result) && payload.get(result) instanceof Integer id ? id : blazeTexture(proxy);
            if (texture <= 0) return none("Distant Horizons has no depth texture");
            float[] terms = terms(proxy);
            return terms == null ? null : new float[]{texture, terms[0], terms[1], terms[2], terms[3]};
        } catch (Throwable e) {
            broken = true;
            reason = "Distant Horizons did not answer as expected: " + e;
            LivingHorizonClient.LOGGER.warn("Using Distant Horizons' depth turned off: it did not answer as expected", e);
            return null;
        }
    }

    /**
     * {@code {view, a, b, signed, nothing}} as {@link #read()} gives, but with the game's own
     * texture view of Distant Horizons' depth instead of an OpenGL name: when it draws through
     * Blaze3D, which works with Vulkan as with OpenGL. Null with the reason when there is none.
     */
    static Object @Nullable [] readView() {
        try {
            Object proxy = renderProxy.get(null);
            if (proxy == null) return noneView("Distant Horizons has not started");
            if (blazeDepthTexture == null || wrappedObject == null) return noneView("Distant Horizons is too old to draw with Vulkan");
            Object result = blazeDepthTexture.invoke(proxy);
            if (!success.getBoolean(result)) return noneView("Distant Horizons has no depth texture");
            Object wrapper = payload.get(result);
            // The texture, its view and its sampler.
            if (wrapper == null || !(wrappedObject.invoke(wrapper) instanceof Object[] objects) || objects.length < 2
                    || objects[1] == null) {
                return noneView("Distant Horizons has no depth texture");
            }
            float[] terms = terms(proxy);
            return terms == null ? null : new Object[]{objects[1], terms[0], terms[1], terms[2], terms[3]};
        } catch (Throwable e) {
            broken = true;
            reason = "Distant Horizons did not answer as expected: " + e;
            LivingHorizonClient.LOGGER.warn("Using Distant Horizons' depth turned off: it did not answer as expected", e);
            return null;
        }
    }

    private static Object @Nullable [] noneView(String why) {
        reason = why;
        return null;
    }

    /** {@code {a, b, signed, nothing}} of the projection Distant Horizons' depth is made with. */
    private static float @Nullable [] terms(Object proxy) throws ReflectiveOperationException {
        float a, b;
        if (renderParams != null) {
            Object params = renderParams.get(null);
            Object matrix = params == null ? null : projection.get(params);
            if (matrix == null) return none("Distant Horizons has not drawn yet");
            a = m22.getFloat(matrix);
            float c = m23.getFloat(matrix), d = m32.getFloat(matrix);
            // The matrix is stored one way or the other: the entry that is -1 is the one that
            // divides by depth, the other one is the offset.
            b = Math.abs(c + 1.0f) < 1.0e-3f ? d : c;
        } else {
            float[] planes = planes(proxy);
            if (planes == null) return none("Distant Horizons has no clip planes yet");
            float near = planes[0], far = planes[1];
            a = (far + near) / (near - far);
            b = 2.0f * far * near / (near - far);
        }
        boolean signed = depthRange == null
                || "NEG_ONE_TO_POS_ONE".equals(String.valueOf(name.invoke(depthRange.invoke(proxy))));
        float[] iris = irisPlanes();
        if (iris != null) {
            // A shader pack draws Distant Horizons' terrain with Iris's own projection, whose
            // near plane is not the one Distant Horizons clamps for itself: OpenGL's -1..1.
            float near = iris[0], far = iris[1];
            a = (far + near) / (near - far);
            b = 2.0f * far * near / (near - far);
            signed = true;
        }
        if (Math.abs(b) < 1.0e-6f) return none("Distant Horizons has no projection yet");
        float nothing = depthDirection == null || farDepth == null ? 1.0f : farDepth.getFloat(depthDirection.invoke(proxy));
        return new float[]{a, b, signed ? 1.0f : 0.0f, nothing};
    }

    /**
     * {@code {near, far}} as Distant Horizons sets them on its projection before 3.2, which
     * keeps no copy of it: its near plane, held to 7.5 blocks unless the height-based
     * override is on, and its far plane. Null when they are not known yet.
     */
    private static float @Nullable [] planes(Object proxy) throws ReflectiveOperationException {
        if (nearPlane == null || farPlane == null) return null;
        float partialTick = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float near = ((Number) nearPlane.invoke(proxy, partialTick)).floatValue();
        if (nearOverride == null || ((Number) nearOverride.invoke(null)).floatValue() == -1.0f) near = Math.min(near, 7.5f);
        float far = ((Number) farPlane.invoke(null)).floatValue();
        return near > 0.0f && far > near ? new float[]{near, far} : null;
    }

    /**
     * {@code {near, far}} of the projection Iris draws Distant Horizons' terrain with while a
     * shader pack is on; null without Iris, without a pack, or when its planes are not known yet.
     */
    private static float @Nullable [] irisPlanes() {
        if (irisApi == null || packInUse == null || irisNear == null || irisFar == null) return null;
        try {
            if (!(boolean) packInUse.invoke(irisApi.invoke(null))) return null;
            float near = (float) irisNear.invoke(null), far = (float) irisFar.invoke(null);
            return near > 0.0f && far > near ? new float[]{near, far} : null;
        } catch (Throwable e) {
            packInUse = null;
            LivingHorizonClient.LOGGER.warn("Iris did not answer as expected: Distant Horizons' depth is read as without a shader pack", e);
            return null;
        }
    }

    /**
     * The OpenGL name of the depth texture Distant Horizons draws into through Blaze3D, which
     * it does without a shader pack from 26.1; 0 when there is none, or not on OpenGL.
     */
    private static int blazeTexture(Object proxy) throws ReflectiveOperationException {
        if (blazeDepthTexture == null || wrappedObject == null) return 0;
        Object result = blazeDepthTexture.invoke(proxy);
        if (!success.getBoolean(result)) return 0;
        Object wrapper = payload.get(result);
        // The texture, its view and its sampler; the texture is the game's own GpuTexture.
        if (wrapper == null || !(wrappedObject.invoke(wrapper) instanceof Object[] objects) || objects.length == 0
                || objects[0] == null) {
            return 0;
        }
        try {
            return (int) objects[0].getClass().getMethod("glId").invoke(objects[0]);
        } catch (NoSuchMethodException e) {
            return 0; // not an OpenGL texture: Vulkan
        }
    }

    /**
     * Whether Distant Horizons draws its terrain with OpenGL itself rather than through the
     * game's GPU device: its rendering engine setting, which it turns to OpenGL whenever Iris
     * is installed. Its depth is then an OpenGL texture, and no texture of the game's.
     */
    static boolean drawsWithOpenGl() {
        if (broken || renderProxy == null || depthTexture == null) return false;
        try {
            Object proxy = renderProxy.get(null);
            if (proxy == null) return false;
            Object result = depthTexture.invoke(proxy);
            return success.getBoolean(result) && payload.get(result) instanceof Integer id && id > 0;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Whether Iris draws with a shader pack: Distant Horizons then does not fade the game's picture. */
    static boolean shaderPackOn() {
        if (irisApi == null || packInUse == null) return false;
        try {
            return (boolean) packInUse.invoke(irisApi.invoke(null));
        } catch (Throwable e) {
            return false;
        }
    }

    private static float @Nullable [] none(String why) {
        reason = why;
        return null;
    }

    private static void link() throws ReflectiveOperationException {
        String api = "com.seibel.distanthorizons.api.";
        Class<?> delayed = Class.forName(api + "DhApi$Delayed");
        Class<?> proxy = Class.forName(api + "interfaces.render.IDhApiRenderProxy");
        Class<?> result = Class.forName(api + "objects.DhApiResult");
        Class<?> param = Class.forName(api + "methods.events.sharedParameterObjects.DhApiRenderParam");
        Class<?> matrix = Class.forName(api + "objects.math.DhApiMat4f");
        renderProxy = delayed.getField("renderProxy");
        try {
            depthTexture = proxy.getMethod("getDhDepthTextureGlId");
        } catch (NoSuchMethodException e) {
            depthTexture = proxy.getMethod("getDhDepthTextureId"); // before 3.3
        }
        try {
            depthRange = proxy.getMethod("getDepthRange");
            depthDirection = proxy.getMethod("getDepthDirection");
            farDepth = Class.forName(api + "enums.config.EDhApiDepthDirection").getField("farDepth");
        } catch (ReflectiveOperationException e) {
            depthRange = depthDirection = null; // before 3.3.2: OpenGL's usual depth
            farDepth = null;
        }
        success = result.getField("success");
        name = Enum.class.getMethod("name");
        projection = param.getField("dhProjectionMatrix");
        m22 = matrix.getField("m22");
        m23 = matrix.getField("m23");
        m32 = matrix.getField("m32");
        // Not API: the last parameters Distant Horizons rendered with, from 3.2.
        try {
            renderParams = Class.forName("com.seibel.distanthorizons.core.api.internal.ClientApi").getDeclaredField("RENDER_PARAMS");
            renderParams.setAccessible(true);
        } catch (NoSuchFieldException e) {
            renderParams = null;
            // Before 3.2, its planes: the near one through the API, the far one and the override not.
            nearPlane = proxy.getMethod("getNearClipPlaneDistanceInBlocks", float.class);
            Class<?> util = Class.forName("com.seibel.distanthorizons.core.util.RenderUtil");
            farPlane = util.getMethod("getFarClipPlaneDistanceInBlocks");
            nearOverride = null;
            for (String override : new String[]{"getHeightBasedNearClipOverride", "getHeightBasedNearClipOverrideBlockDistance"}) {
                try {
                    nearOverride = util.getMethod(override);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // the other name
                }
            }
        }
        payload = result.getField("payload");
        try {
            blazeDepthTexture = proxy.getMethod("getDhDepthTextureBlazeWrapper");
            wrappedObject = Class.forName(api + "interfaces.IDhApiUnsafeWrapper").getMethod("getWrappedMcObject");
        } catch (ReflectiveOperationException e) {
            blazeDepthTexture = null; // before API 7.1: OpenGL only
        }
        if (Platform.isModLoaded("iris")) {
            try {
                Class<?> iris = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Class<?> compat = Class.forName("net.irisshaders.iris.compat.dh.DHCompat");
                irisApi = iris.getMethod("getInstance");
                packInUse = iris.getMethod("isShaderPackInUse");
                irisNear = compat.getMethod("getNearPlane");
                irisFar = compat.getMethod("getFarPlane");
            } catch (ReflectiveOperationException e) {
                packInUse = null;
                LivingHorizonClient.LOGGER.warn("Iris found, but not the version this mod knows: Distant Horizons' depth is read as without a shader pack", e);
            }
        }
    }
}
