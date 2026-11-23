package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Iterator;

/**
 * The players online that a server with the mod shares, on Xaero's Minimap and Xaero's World
 * Map, as their own tracked players: their head past the radar, on the minimap's edge and on
 * the world map. Through the trackers Xaero lets other mods register (FTB Teams, Open Parties
 * and Claims do the same): {@code IRenderedPlayerTracker} for the minimap,
 * {@code IPlayerTrackerSystem} for the world map, both with a reader of the player's id,
 * position and dimension. Xaero draws a tracked player only from the game's list of players
 * online, so the players who logged off are not handed over.
 *
 * <p>Xaero's mods say "All Rights Reserved": reached by reflection only, nothing of theirs
 * copied. A version whose classes differ turns this off, with a line in the log.
 */
public final class XaeroMaps {
    /** The name the trackers are registered under. */
    private static final String SYSTEM = "livinghorizon";

    private static boolean minimap, worldMap;
    private static boolean minimapDone, worldMapDone;
    private static boolean looked;

    private XaeroMaps() {
    }

    /** Whether either of Xaero's maps is installed. */
    static boolean present() {
        if (!looked) {
            looked = true;
            minimap = Platform.isModLoaded("xaerominimap") || Platform.isModLoaded("xaerobetterpvp");
            worldMap = Platform.isModLoaded("xaeroworldmap");
            minimapDone = !minimap;
            worldMapDone = !worldMap;
        }
        return minimap || worldMap;
    }

    /** The trackers registered once the maps are up: they read {@link MapMarkers#markers()} as they draw. */
    static void update() {
        if (!minimapDone) minimapDone = register(true);
        if (!worldMapDone) worldMapDone = register(false);
    }

    /** Whether it is done with: registered, or given up on. */
    private static boolean register(boolean onMinimap) {
        String name = onMinimap ? "Xaero's Minimap" : "Xaero's World Map";
        try {
            Object manager;
            String system, reader;
            if (onMinimap) {
                Class<?> mod = Class.forName("xaero.common.HudMod");
                Object instance = mod.getField("INSTANCE").get(null);
                if (instance == null) return false;
                manager = mod.getMethod("getRenderedPlayerTrackerManager").invoke(instance);
                system = "xaero.hud.minimap.player.tracker.system.IRenderedPlayerTracker";
                reader = "xaero.hud.minimap.player.tracker.system.ITrackedPlayerReader";
            } else {
                manager = Class.forName("xaero.map.WorldMap").getField("playerTrackerSystemManager").get(null);
                system = "xaero.map.radar.tracker.system.IPlayerTrackerSystem";
                reader = "xaero.map.radar.tracker.system.ITrackedPlayerReader";
            }
            // Not made yet: asked again a second later.
            if (manager == null) return false;
            Object tracker = tracker(Class.forName(system), Class.forName(reader));
            Method register = manager.getClass().getMethod("register", String.class, Class.forName(system));
            register.invoke(manager, SYSTEM, tracker);
            LivingHorizonClient.LOGGER.info("Distant players shown on {}", name);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            LivingHorizonClient.LOGGER.warn("{} found, but not the version this mod knows: distant players are not shown on it",
                    name, error);
        }
        return true;
    }

    /** A tracker over the online players of {@link MapMarkers}, and its reader. */
    private static Object tracker(Class<?> system, Class<?> readerType) {
        Object reader = proxy(readerType, (method, arguments) -> {
            MapMarkers.Marker marker = (MapMarkers.Marker) arguments[0];
            return switch (method) {
                case "getId" -> marker.id();
                case "getX" -> marker.x();
                case "getY" -> marker.y();
                case "getZ" -> marker.z();
                case "getDimension" -> marker.dimension();
                default -> null;
            };
        });
        return proxy(system, (method, arguments) -> switch (method) {
            case "getReader" -> reader;
            case "getTrackedPlayerIterator" -> online();
            default -> null;
        });
    }

    private static Iterator<MapMarkers.Marker> online() {
        return MapMarkers.markers().stream().filter(marker -> !marker.offline()).iterator();
    }

    private interface Calls {
        @Nullable Object call(String method, Object[] arguments);
    }

    private static Object proxy(Class<?> type, Calls calls) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, arguments) ->
                switch (method.getName()) {
                    case "equals" -> self == arguments[0];
                    case "hashCode" -> System.identityHashCode(self);
                    case "toString" -> "Living Horizon " + type.getSimpleName();
                    default -> calls.call(method.getName(), arguments == null ? new Object[0] : arguments);
                });
    }
}
