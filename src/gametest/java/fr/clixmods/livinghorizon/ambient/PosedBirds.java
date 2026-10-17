package fr.clixmods.livinghorizon.ambient;

import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Birds posed by hand for the game tests, still, where a scenario puts them: added to the
 * birds drawn after every tick, until cleared. In the mod's own package for the
 * constructor of {@link Ambience.Flyer}, which nothing outside it may call.
 */
public final class PosedBirds {
    private static final List<Ambience.Flyer> POSED = new CopyOnWriteArrayList<>();
    private static boolean listening;

    private PosedBirds() {
    }

    public static void clear() {
        POSED.clear();
    }

    /**
     * One bird at a place in the world, turned to {@code yaw} (0 faces south), perched or
     * flying with its wings at {@code wing} degrees (up is positive) and banked by {@code bank}.
     */
    public static void add(Ambience.Species species, float span, double x, double y, double z, float yaw,
                           boolean perched, float wing, float bank) {
        if (!listening) {
            listening = true;
            // Registered after the mod's own tick, so it runs after the birds are rebuilt.
            ClientTickEvents.END_CLIENT_TICK.register(client -> FarPlayerTracker.get().ambience().flyers().addAll(POSED));
        }
        int shade = switch (species) {
            case GENERIC, GOOSE, RAPTOR -> 40;
            case GULL -> 230;
            default -> 255;
        };
        Ambience.Flyer flyer = new Ambience.Flyer(Ambience.Kind.SILHOUETTE, species, span, shade, null, 0f);
        flyer.place(x, y, z);
        flyer.yaw = flyer.yawO = yaw;
        flyer.perched = perched;
        flyer.wing = flyer.wingO = wing;
        flyer.bank = flyer.bankO = bank;
        POSED.add(flyer);
    }
}
