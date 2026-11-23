package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * The players past the render distance on JourneyMap, the minimap and the full map: a marker
 * with their head where they are, those online that a server with the mod shares and those who
 * logged off (paler, marked offline). Through JourneyMap's client API, version 2 (JourneyMap
 * 6, Minecraft 1.20.6 and later): its markers ({@code MarkerOverlay}) shown and taken back under
 * the mod's id once a second. Reached by reflection, so that the mod loads without it; an older
 * API, or one whose classes differ, turns this off, with a line in the log.
 */
public final class JourneyMapLink {
    private static final String MOD_ID = "livinghorizon";
    /** The head on a 64-pixel skin: 8 by 8 pixels from (8, 8). */
    private static final int FACE = 8;
    /** Pixels a head takes on the map. */
    private static final double SIZE = 12;

    private static boolean looked, linked, shown;
    private static @Nullable Object api;
    private static @Nullable Method show, removeAll, setDimension, setTitle;
    private static @Nullable Method setDisplayWidth, setDisplayHeight, centerAnchors;
    private static @Nullable Constructor<?> marker, image;

    private JourneyMapLink() {
    }

    /** Whether JourneyMap is installed, in a version this mod knows. */
    static boolean present() {
        if (!looked) find();
        return linked;
    }

    private static void find() {
        looked = true;
        if (!Platform.isModLoaded("journeymap")) return;
        try {
            Class<?> impl = Class.forName("journeymap.api.client.impl.ClientAPI");
            Class<?> client = Class.forName("journeymap.api.v2.client.IClientAPI");
            Class<?> displayable = Class.forName("journeymap.api.v2.client.display.Displayable");
            Class<?> overlay = Class.forName("journeymap.api.v2.client.display.Overlay");
            Class<?> markerType = Class.forName("journeymap.api.v2.client.display.MarkerOverlay");
            Class<?> imageType = Class.forName("journeymap.api.v2.client.model.MapImage");
            api = impl.getField("INSTANCE").get(null);
            show = client.getMethod("show", displayable);
            removeAll = client.getMethod("removeAll", String.class);
            setDimension = overlay.getMethod("setDimension", net.minecraft.resources.ResourceKey.class);
            setTitle = overlay.getMethod("setTitle", String.class);
            setDisplayWidth = imageType.getMethod("setDisplayWidth", double.class);
            setDisplayHeight = imageType.getMethod("setDisplayHeight", double.class);
            centerAnchors = imageType.getMethod("centerAnchors");
            marker = markerType.getConstructor(String.class, BlockPos.class, imageType);
            image = imageType.getConstructor(Identifier.class, int.class, int.class, int.class, int.class, int.class,
                    float.class);
            linked = api != null;
            if (linked) LivingHorizonClient.LOGGER.info("Distant players shown on JourneyMap");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            LivingHorizonClient.LOGGER.warn("JourneyMap found, but not the version this mod knows: distant players are not shown on it",
                    error);
        }
    }

    /** The markers of now, in place of those of a second ago. */
    static void update(List<MapMarkers.Marker> markers) {
        if (!linked) return;
        try {
            if (shown) removeAll.invoke(api, MOD_ID);
            shown = false;
            String offline = I18n.get("livinghorizon.offline.tag");
            for (MapMarkers.Marker each : markers) {
                Object picture = image.newInstance(skin(each.info()), FACE, FACE, FACE, FACE, 0xFFFFFF,
                        each.offline() ? 0.6f : 1f);
                setDisplayWidth.invoke(picture, SIZE);
                setDisplayHeight.invoke(picture, SIZE);
                centerAnchors.invoke(picture);
                Object made = marker.newInstance(MOD_ID, BlockPos.containing(each.x(), each.y(), each.z()), picture);
                setDimension.invoke(made, each.dimension());
                String name = each.offline() ? each.name() + " " + offline : each.name();
                // Their name when the marker is pointed at: written under each head, a crowd hid them all.
                setTitle.invoke(made, name);
                show.invoke(api, made);
                shown = true;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            linked = false;
            LivingHorizonClient.LOGGER.warn("JourneyMap found, but not the version this mod knows: distant players are not shown on it",
                    error);
        }
    }

    /** Their skin's texture, as the game's list of players draws their head from. */
    private static Identifier skin(PlayerInfo info) {
        //? if >=1.21.9 {
        return info.getSkin().body().texturePath();
        //?} elif >=1.20.2 {
        /*return info.getSkin().texture();
        *///?} else
        /*return info.getSkinLocation();*/
    }
}
