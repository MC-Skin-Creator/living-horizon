package fr.clixmods.farfarplayer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.clixmods.farfarplayer.track.MobKinds;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code config/farfarplayer.json}. Read once at start, written back so that a player
 * always finds every setting in the file, including the ones added by a newer version.
 */
public final class FarConfig {
    /** Distant players are drawn at all. The toggle key flips this. */
    public boolean enabled = true;

    /** Layout of this file; older files are brought up to date on load. */
    public int version = 0;

    /**
     * The smallest size, in screen pixels, a distant player is drawn at. 0, the default,
     * draws everyone at their true size, where they are: a player 1.8 blocks tall is
     * three pixels high at 500 blocks on a 1080p screen. Above 0 they stop shrinking
     * past some distance and look huge next to the terrain around them.
     */
    public double minApparentPixels = 0.0;

    /**
     * Pushes the game's far clipping plane (four times the render distance) out to the
     * farthest distant player, so that they are drawn where they really are and hidden
     * by the terrain in front of them - Voxy's included, which writes its depth into the
     * game's. Off, players past the plane are drawn closer and smaller instead, and then
     * show through the terrain between.
     */
    public boolean extendFarPlane = true;

    /**
     * Voxy's terrain in the depth buffer while the entities are drawn, so that it hides
     * distant mobs pixel by pixel. Needed with shader packs that keep Voxy out of the depth
     * buffer (Photon does); without one, Voxy does it itself.
     */
    public boolean voxyOcclusion = true;

    private static final int CURRENT = 4;

    /** Seconds a player stays drawn where they were last placed once the locator bar loses them. */
    public double lostTimeoutSeconds = 30.0;

    /**
     * How a player who logged off is shown where they were last: {@code "sleep"} lying
     * down, {@code "sit"} sitting on the ground, {@code "hidden"} not at all.
     */
    public String offlinePose = "sleep";

    /**
     * Mobs met on the way stay where they were once out of range, doing a little loop
     * of their own (no AI: a walk around a small circle, a look around).
     */
    public boolean distantMobs = true;

    /** How many remembered mobs are drawn at once: the nearest ones. */
    public int maxDistantMobs = 200;

    /**
     * The mobs shown far away, by type id ({@code "minecraft:horse"}). This client
     * remembers these when it sees them leave; the data pack publishes its own list
     * ({@link MobKinds#TYPES}), and only those of its mobs in this list are shown.
     * Hostile mobs despawn when nobody is near, so remembering them shows ghosts.
     * Chosen in game under "Choose mobs...".
     */
    public List<String> mobTypes = new ArrayList<>(MobKinds.TYPES);

    /**
     * Mobs of {@link #mobTypes} shown standing still, where they were, instead of walking
     * their little loop: a happy ghast drifting around on its own looks wrong.
     */
    public List<String> stillMobTypes = new ArrayList<>(List.of("minecraft:happy_ghast"));

    /** A mob with a name tag is remembered and shown whatever its type. */
    public boolean rememberNamedMobs = true;

    /**
     * Birds and bats: flocks, birds of prey, gulls, pigeons, robins, tits, bats at night.
     * Only a picture, on this client.
     */
    public boolean skyBirds = true;

    /** Percent: how many birds, all kinds together. 100 is the usual; 0 none; 500 five times as many. */
    public int birdDensity = 100;

    /** Blocks: how far away birds can be. */
    public int birdMaxDistance = 800;

    /** Blocks above you (or the sea, whichever is higher) below which flying flocks never go. */
    public int birdMinHeight = 30;

    /** How much bigger than life the birds are drawn. */
    public int birdSize = 1;

    /**
     * Kinds of birds switched off, by id: {@code flocks}, {@code geese}, {@code raptors},
     * {@code gulls}, {@code pigeons}, {@code robins}, {@code tits}, {@code bats}, {@code parrots}.
     */
    public List<String> hiddenBirds = new ArrayList<>();

    /** An easter egg, now and then, far away: a flying saucer taking a cow. Only a picture. */
    public boolean ufo = true;

    /** Draws what a player was riding when last seen: horse, boat, happy ghast... */
    public boolean showVehicles = true;

    /** A glowing outline around distant players, seen through terrain. */
    public boolean glowOutline = false;

    /** Lets vehicles carrying another player be drawn at any distance the server still sends them. */
    public boolean renderTrackedVehiclesFar = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static FarConfig instance = new FarConfig();

    public static FarConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("farfarplayer.json");
    }

    public static void load() {
        Path path = path();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                FarConfig read = GSON.fromJson(reader, FarConfig.class);
                if (read != null) instance = read;
            } catch (IOException | RuntimeException e) {
                FarFarPlayerClient.LOGGER.warn("Could not read {}, using defaults", path, e);
            }
            if (instance.version < 2) {
                // 0.1.0 kept far players at least 14 pixels tall by default, which made
                // them giants next to the terrain they stand on.
                instance.minApparentPixels = 0.0;
            }
        }
        if (instance.mobTypes == null) instance.mobTypes = new ArrayList<>(MobKinds.TYPES);
        if (instance.stillMobTypes == null) instance.stillMobTypes = new ArrayList<>(List.of("minecraft:happy_ghast"));
        if (instance.version < 4 && instance.birdSize == 2) {
            // Twice the size suited lone parrots; silhouettes come in their own sizes.
            instance.birdSize = 1;
        }
        if (instance.hiddenBirds == null) instance.hiddenBirds = new ArrayList<>();
        if (instance.version < 3) {
            // Boats came after the list was first written: shown unless taken out by hand.
            for (String type : MobKinds.TYPES) {
                if (MobKinds.isBoat(type) && !instance.mobTypes.contains(type)) instance.mobTypes.add(type);
            }
        }
        instance.version = CURRENT;
        save();
    }

    public static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            FarFarPlayerClient.LOGGER.warn("Could not write {}", path, e);
        }
    }
}
