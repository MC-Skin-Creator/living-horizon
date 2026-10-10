package fr.clixmods.livinghorizon;

import com.google.gson.annotations.SerializedName;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.clixmods.livinghorizon.track.MobKinds;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.jspecify.annotations.Nullable;

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
     * by the terrain in front of them - the far terrain's included. Off, players past the plane are drawn closer and smaller instead, and then
     * show through the terrain between.
     */
    public boolean extendFarPlane = true;

    /**
     * The far terrain's depth (Voxy's or Distant Horizons') in the depth buffer while the
     * entities are drawn, so that it hides distant mobs pixel by pixel. Needed with Distant
     * Horizons, and with shader packs that keep Voxy out of the depth buffer (Photon does).
     * Named {@code voxyOcclusion} before Distant Horizons.
     */
    @SerializedName(value = "depthOcclusion", alternate = "voxyOcclusion")
    public boolean depthOcclusion = true;

    private static final int CURRENT = 9;

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
     * Players who logged off stay where they were last, until they come back. Off, they are
     * not followed at all: not drawn, not remembered, and the file of who rests where is
     * left as it is.
     */
    public boolean offlinePlayers = true;

    /**
     * How a player who logged off is shown where they were last: {@code "sleep"} lying
     * down, {@code "sit"} sitting on the ground.
     */
    public String offlinePose = "sit";

    /**
     * Their name above a player who logged off, marked offline, as close as the game shows
     * names; and when they were last seen, under the crosshair, when looked at.
     */
    public boolean offlineNames = true;

    /**
     * A player who logged off keeps to the blocks around them, where those are loaded:
     * they fall when what they rest on is broken, reappear on top when buried, and go
     * back to where they logged off once there is room and something under it again.
     */
    public boolean offlineOnGround = true;

    /** Blocks: a player who logged off this close to a free bed is shown asleep in it. 0: never. */
    public int offlineBedRadius = 4;

    /**
     * Mobs met on the way stay where they were once out of range, living there on their
     * own (no AI: a stroll around, a look around, grazing).
     */
    public boolean distantMobs = true;

    /** The value of {@link #maxDistantMobs} (and above) that means no limit. */
    public static final int UNLIMITED_MOBS = 1000;

    /** How many remembered mobs are drawn at once: the nearest ones. {@link #UNLIMITED_MOBS}: all. */
    public int maxDistantMobs = 512;

    /** Blocks: past this, distant mobs are not drawn. 0 is no limit. */
    public int mobMaxDistance = 512;

    /** Blocks: past this, distant players are not drawn. 0, the default, is no limit. */
    public int playerMaxDistance = 0;

    /**
     * The mobs shown far away, by type id ({@code "minecraft:horse"}). This client
     * remembers these when it sees them leave; the data pack publishes its own list
     * ({@link MobKinds#TYPES}), and only those of its mobs in this list are shown.
     * Hostile mobs despawn when nobody is near, so remembering them shows ghosts.
     * Chosen in game under "Choose mobs...".
     */
    public List<String> mobTypes = defaultMobTypes();

    /**
     * Mobs of {@link #mobTypes} shown standing still, where they were, instead of strolling
     * around: a happy ghast drifting around on its own looks wrong.
     */
    public List<String> stillMobTypes = new ArrayList<>(List.of("minecraft:happy_ghast", MANNEQUIN));

    /** A mob with a name tag is remembered and shown whatever its type. */
    public boolean rememberNamedMobs = true;

    /**
     * Chunks around the player read by a scan ("Scan the chunks around", or
     * {@code /livinghorizon scan}): in a single player world, the mobs of these chunks
     * are read from the world itself instead of met on the way.
     */
    public int scanRadius = 32;

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

    /** Lets vehicles carrying another player be drawn at any distance the server still sends them. */
    public boolean renderTrackedVehiclesFar = true;

    /**
     * Distant mobs the terrain hides entirely - the far terrain's included - are not drawn at
     * all, instead of drawn and covered pixel by pixel. Cheaper with many mobs. Asked of the
     * GPU against the depth of the picture ({@link #optOcclusionQueries}), so a mob with one
     * pixel in view is still drawn, and one appears a frame or two after the view clears.
     */
    public boolean hideOccludedMobs = true;

    /**
     * Distant players and mobs past {@link #impostorDistance} are drawn as a flat picture of
     * themselves, turned to the camera, instead of their whole model. The pictures are
     * baked from the game's own renderers when a world is joined and again whenever the
     * resource packs change, so skins, mobs and packs are all followed.
     */
    public boolean impostors = true;

    /** Blocks: past this distance an impostor stands in for the model. */
    public int impostorDistance = 128;

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
    /** The far terrain's depth is merged only on frames that draw something past the render distance. */
    @SerializedName(value = "optLazyDepth", alternate = "optLazyVoxyDepth")
    public boolean optLazyDepth = true;
    /** The far world (Voxy or Distant Horizons) is read on several threads at once rather than one. */
    @SerializedName(value = "optParallelRead", alternate = "optParallelVoxy")
    public boolean optParallelRead = true;
    /** Ground already read in the far world is kept for two minutes. */
    public boolean optColumnCache = true;
    /**
     * How {@link #hideOccludedMobs} decides: occlusion queries against the depth of the picture
     * (see {@code OcclusionQueries}), what the player sees, a frame or two late. Off, to
     * compare: lines of sight in the far terrain's world.
     */
    @SerializedName(value = "optOcclusionQueries", alternate = "optHiZ")
    public boolean optOcclusionQueries = true;
    /**
     * Distant Horizons' fade of the game's picture into its terrain, without a shader pack,
     * leaves the entities alone (see {@code FarDepth.beforeFade}). Off, to compare: it paints
     * its terrain over the distant mobs.
     */
    public boolean optDhFade = true;
    /** Saved mobs are read off the game's thread and built within 2 ms per tick. */
    public boolean optBackgroundBuild = true;
    /** A mob only a few pixels high on screen plays no walking or idle animation: it only slides and turns. */
    public boolean optStillTiny = true;
    /**
     * Pixels: a distant mob shorter than this on screen plays no walking or idle animation (it
     * resumes a little above, so it never flickers). 0 always animates. Needs {@link #optStillTiny}.
     */
    public int animationMinPixels = 10;
    /** Blocks: past this, no distant mob plays an animation, however big it looks. 0 is no limit. */
    public int animationMaxDistance = 0;
    /** A copy that was not drawn for a moment (out of view, hidden, too small) stays where it is: no animation, no path read. */
    public boolean optFreezeHidden = false;

    // --- Debug -----------------------------------------------------------------------------

    /** A coloured box around every distant mob and player, seen through terrain. */
    public boolean debugBoxes = false;
    /** Its state, kind, distance and size on screen above each box. */
    public boolean debugLabels = false;
    /** Boxes around the mobs the game draws itself, too. */
    public boolean debugGameMobs = false;
    // Outlines, seen through terrain, in the colours of the debug boxes: what draws each figure.
    /** Around the mobs the game draws itself: white. */
    public boolean outlineGameMobs = false;
    /** Around the real mobs the server sends that the mod draws, past the game's entity distance: cyan. */
    public boolean outlineLiveMobs = false;
    /** Around the 3D copies of mobs the server no longer sends: green. */
    public boolean outlineCopies = false;
    /** Around the distant players the mod draws: blue. */
    public boolean outlinePlayers = false;
    /** Around impostors, whatever they stand for: magenta. */
    public boolean outlineImpostors = false;
    /** A cross where a distant mob is left out of the frame, in the colour of why: where it is, not what. */
    public boolean outlineLeftOut = false;
    /** Before version 8, one white outline around every distant figure; read once, then dropped. */
    private @Nullable Boolean glowOutline;
    /** Before version 8, the outlines and crosses on the mod's figures together; read once, then dropped. */
    private @Nullable Boolean debugOutlines;
    /** A panel of what the mod does and costs, in the corner of the screen. */
    public boolean debugHud = false;
    /** 0: off; 1: the game's depth; 2: with the far terrain merged in; 3: after the entities. */
    public int debugDepthView = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static FarConfig instance = new FarConfig();

    /** Whether anything distant is drawn: players, mobs or both, each on its own switch. */
    public boolean anyDistant() {
        return enabled || distantMobs;
    }

    public static FarConfig get() {
        return instance;
    }

    private static Path path() {
        return Platform.configDir().resolve("livinghorizon.json");
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
        if (instance.version < 6) {
            // The far terrain's depth on mobs and skipping the mobs it hides left the main
            // settings for the debug screen, on: whoever had turned them off could not tell.
            instance.depthOcclusion = true;
            instance.hideOccludedMobs = true;
            instance.optOcclusionQueries = true;
        }
        if (instance.version < 7) {
            // New defaults: whoever never changed the old ones gets them.
            if (instance.maxDistantMobs == 200) instance.maxDistantMobs = 512;
            if (instance.mobMaxDistance == 0) instance.mobMaxDistance = 512;
            if (instance.offlinePose.equals("sleep")) instance.offlinePose = "sit";
            if (instance.impostorDistance == 200) instance.impostorDistance = 128;
            instance.impostors = true;
        }
        if (instance.version < 8) {
            // One switch per kind of figure now; the old ones turn on what they covered.
            if (Boolean.TRUE.equals(instance.glowOutline)) {
                instance.outlineLiveMobs = instance.outlineCopies = instance.outlinePlayers = true;
                instance.outlineImpostors = true;
            }
            if (Boolean.TRUE.equals(instance.debugOutlines)) {
                instance.outlineCopies = instance.outlineImpostors = instance.outlineLeftOut = true;
            }
            instance.glowOutline = null;
            instance.debugOutlines = null;
        }
        if (instance.version < 9 && "hidden".equals(instance.offlinePose)) {
            // Hiding them is a switch of its own now, which also stops remembering them.
            instance.offlinePlayers = false;
            instance.offlinePose = "sit";
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

    /** Every setting back to its default, mob and bird choices included, and written to the file. */
    public static void reset() {
        instance = new FarConfig();
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
            LivingHorizonClient.LOGGER.warn("Could not write {}", path, e);
        }
    }
}
