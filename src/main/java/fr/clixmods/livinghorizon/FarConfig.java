package fr.clixmods.livinghorizon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.clixmods.livinghorizon.track.MobKinds;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code config/livinghorizon.json}. Read once at start, written back so that a player
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

    private static final int CURRENT = 5;

    /** Skinned figures, which Distant Friends uses as its fake players: shown, standing still. */
    private static final String MANNEQUIN = "minecraft:mannequin";

    private static List<String> defaultMobTypes() {
        List<String> types = new ArrayList<>(MobKinds.TYPES);
        types.add(MANNEQUIN);
        return types;
    }

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
    public List<String> mobTypes = defaultMobTypes();

    /**
     * Mobs of {@link #mobTypes} shown standing still, where they were, instead of walking
     * their little loop: a happy ghast drifting around on its own looks wrong.
     */
    public List<String> stillMobTypes = new ArrayList<>(List.of("minecraft:happy_ghast", MANNEQUIN));

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

    /**
     * Distant mobs hidden behind terrain - Voxy's included - are not drawn at all, instead
     * of drawn and covered pixel by pixel. Cheaper with many mobs, but one only half behind
     * a hill is not drawn either, and a mob appears a moment after the view clears.
     * Needs Voxy: its world is what the line of sight is tested against.
     */
    public boolean hideOccludedMobs = false;

    /**
     * Mob types of other mods already offered once. A new one is shown far away from the
     * start (unless it is a monster), then left as the player sets it.
     */
    public List<String> knownModdedMobs = new ArrayList<>();

    // --- Optimisations: each one can be switched off, to see what it saves -----------------

    /** Copies outside the view are not prepared at all. */
    public boolean optViewCulling = true;
    /** Copies smaller than about half a pixel are not prepared (only when no minimum size is set). */
    public boolean optTinyCulling = true;
    /** Voxy's depth is merged only on frames that draw something past the render distance. */
    public boolean optLazyVoxyDepth = true;
    /** Voxy's world is read on several threads at once rather than one. */
    public boolean optParallelVoxy = true;
    /** Ground already read in Voxy's world is kept for two minutes. */
    public boolean optColumnCache = true;
    /** Saved mobs are read off the game's thread and built within 2 ms per tick. */
    public boolean optBackgroundBuild = true;

    // --- Debug -----------------------------------------------------------------------------

    /** A coloured box around every distant mob and player, seen through terrain. */
    public boolean debugBoxes = false;
    /** Its state, kind, distance and size on screen above each box. */
    public boolean debugLabels = false;
    /** Boxes around the mobs the game draws itself, too. */
    public boolean debugGameMobs = false;
    /** A panel of what the mod does and costs, in the corner of the screen. */
    public boolean debugHud = false;
    /** 0: off; 1: the game's depth; 2: with Voxy's terrain merged in; 3: after the entities. */
    public int debugDepthView = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static FarConfig instance = new FarConfig();

    public static FarConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("livinghorizon.json");
    }

    public static void load() {
        Path path = path();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                FarConfig read = GSON.fromJson(reader, FarConfig.class);
                if (read != null) instance = read;
            } catch (IOException | RuntimeException e) {
                LivingHorizonClient.LOGGER.warn("Could not read {}, using defaults", path, e);
            }
            if (instance.version < 2) {
                // 0.1.0 kept far players at least 14 pixels tall by default, which made
                // them giants next to the terrain they stand on.
                instance.minApparentPixels = 0.0;
            }
        }
        if (instance.mobTypes == null) instance.mobTypes = defaultMobTypes();
        if (instance.stillMobTypes == null) instance.stillMobTypes = new ArrayList<>(List.of("minecraft:happy_ghast", MANNEQUIN));
        if (instance.version < 5) {
            // Mannequins came with Distant Friends' fake players: shown, and still, unless set otherwise.
            if (!instance.mobTypes.contains(MANNEQUIN)) instance.mobTypes.add(MANNEQUIN);
            if (!instance.stillMobTypes.contains(MANNEQUIN)) instance.stillMobTypes.add(MANNEQUIN);
        }
        if (instance.version < 4 && instance.birdSize == 2) {
            // Twice the size suited lone parrots; silhouettes come in their own sizes.
            instance.birdSize = 1;
        }
        if (instance.hiddenBirds == null) instance.hiddenBirds = new ArrayList<>();
        if (instance.knownModdedMobs == null) instance.knownModdedMobs = new ArrayList<>();
        if (instance.version < 3) {
            // Boats came after the list was first written: shown unless taken out by hand.
            for (String type : MobKinds.TYPES) {
                if (MobKinds.isBoat(type) && !instance.mobTypes.contains(type)) instance.mobTypes.add(type);
            }
        }
        instance.version = CURRENT;
        save();
    }

    /**
     * Mob types of other mods met for the first time: shown far away, unless monsters,
     * which despawn as soon as nobody is near. Called once the game has registered them.
     */
    public static void adoptModdedMobs(Iterable<EntityType<?>> types) {
        boolean changed = false;
        for (EntityType<?> type : types) {
            Identifier key = EntityType.getKey(type);
            if (Identifier.DEFAULT_NAMESPACE.equals(key.getNamespace())) continue;
            String id = key.toString();
            if (instance.knownModdedMobs.contains(id)) continue;
            instance.knownModdedMobs.add(id);
            if (type.getCategory() != MobCategory.MONSTER && !instance.mobTypes.contains(id)) instance.mobTypes.add(id);
            changed = true;
        }
        if (changed) save();
    }

    public static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not write {}", path, e);
        }
    }
}
