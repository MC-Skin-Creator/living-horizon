package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Entity Model Features, Entity Texture Features and the Fresh Animations pack (issue #96).
 * Run with them: {@code run.sh entityfeatures --with entitytexturefeatures,entity-model-features
 * --pack fresh-animations}. Without them it checks the same of the game's own models.
 *
 * <p>ETF gives each mob one of a pack's textures for its kind, drawn from its id. The scenario
 * writes a pack with two plain variants of the cow, magenta and yellow, and checks that far
 * cows keep theirs: their impostor keys tell the variants apart, and the pictures show as
 * many magenta and yellow cows as the models.
 *
 * <p>Then squid and axolotls met in water and left behind. Their copies are in no world, but
 * EMF's animations ask the entity whether it is in water, and Fresh Animations lays a swimmer
 * on its side, flapping, when it is not: each copy must say it is. On 1.21.8 and older, the
 * cows check that ETF dresses the figures the mod draws at all.
 */
public final class EntityFeaturesScenario implements Scenario {
    private static final String PACK = "lh-random-cows";
    private static final int COWS = 12;
    // Fish summoned in the test pool never reach the client: squid and axolotls do.
    private static final String[] SWIMMERS = {"squid", "glow_squid", "axolotl"};
    private static final String[] WALKERS = {"cow", "sheep", "pig", "chicken", "villager", "wolf", "horse", "fox"};

    @Override
    public String name() {
        return "entityfeatures";
    }

    @Override
    public void run(ClientGameTestContext context) {
        boolean etf = FabricLoader.getInstance().isModLoaded("entity_texture_features");
        boolean emf = FabricLoader.getInstance().isModLoaded("entity_model_features");
        Scene.log("entityfeatures etf=" + etf + " emf=" + emf);
        List<String> failures = new ArrayList<>();
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.fov().set(30);
        });
        Scene.configure(context, config -> {
            config.hideOccludedMobs = false;
            config.impostors = false;
            for (String swimmer : SWIMMERS) {
                if (!config.mobTypes.contains("minecraft:" + swimmer)) config.mobTypes.add("minecraft:" + swimmer);
            }
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.summon(world, "cow", 0, 6, 0);
            context.waitTicks(20);
            Identifier texture = context.computeOnClient(minecraft -> texture(minecraft, first(minecraft, EntityType.COW)));
            context.runOnClient(minecraft -> writePack(minecraft, texture));
            Scene.packs(context, List.of("file/" + PACK), true);
            forget(context, world);
            try {
                cows(context, world, etf, failures);
                remembered(context, world, failures);
            } finally {
                Scene.packs(context, List.of("file/" + PACK), false);
            }
        }
        if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
    }

    /**
     * A row of cows 64 blocks away, side on: models, then pictures, the same colours. The game
     * draws a cow up to about 51 blocks at this render distance and entity distance: past it, the mod.
     */
    private void cows(ClientGameTestContext context, TestSingleplayerContext world, boolean etf, List<String> failures) {
        for (int i = 0; i < COWS; i++) Scene.summon(world, "cow", (i - (COWS - 1) / 2.0) * 3, 64, 90);
        context.waitTicks(40);
        Map<ImpostorKey, Set<String>> variants = context.computeOnClient(minecraft -> {
            Map<ImpostorKey, Set<String>> found = new HashMap<>();
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity.getType() != EntityType.COW || !(entity instanceof LivingEntity cow)) continue;
                found.computeIfAbsent(ImpostorKey.of(cow), key -> new HashSet<>()).add(variant(minecraft, cow, etf));
            }
            return found;
        });
        Set<String> all = new HashSet<>();
        variants.values().forEach(all::addAll);
        Scene.log("entityfeatures cows: " + all.size() + " textures " + all + ", " + variants.size() + " impostor keys");
        variants.forEach((key, textures) -> {
            if (textures.size() > 1) failures.add("Cows of " + textures.size() + " textures share one impostor key: " + textures);
        });

        Path models = Scene.screenshot(context, this, "cows-models");
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 32;
            config.impostorPixels = 0;
        });
        for (int wait = 0; wait < 60; wait++) {
            context.waitTicks(10);
            boolean pictured = context.computeOnClient(minecraft -> {
                if (ImpostorAtlas.waiting() > 0) return false;
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity.getType() == EntityType.COW && !GhostRenderer.pictured(entity)) return false;
                }
                return true;
            });
            if (pictured) break;
        }
        context.runOnClient(minecraft -> {
            LivingEntity cow = first(minecraft, EntityType.COW);
            double distance = Math.sqrt(cow.distanceToSqr(minecraft.player));
            Scene.log("entityfeatures cow " + Math.round(distance) + " blocks away: drawn by the game="
                    + cow.shouldRenderAtSqrDistance(distance * distance) + " pictured=" + GhostRenderer.pictured(cow)
                    + " baked=" + (ImpostorAtlas.sheet(ImpostorKey.of(cow)) != null) + " waiting=" + ImpostorAtlas.waiting()
                    + " bakes=" + ImpostorAtlas.bakes() + " failed=" + ImpostorAtlas.failed());
        });
        Path pictures = Scene.screenshot(context, this, "cows-impostors");
        Scene.screenshotAtlas(context, this);
        int[] seen = colours(models), shown = colours(pictures);
        Scene.log("entityfeatures pixels: models magenta=" + seen[0] + " yellow=" + seen[1]
                + ", impostors magenta=" + shown[0] + " yellow=" + shown[1]);
        // ETF dresses the mod's figures only when told which entity they are.
        if (all.size() > 1 && seen[0] + seen[1] < 40) failures.add("The far cows' models show none of ETF's textures");
        String[] names = {"magenta", "yellow"};
        for (int colour = 0; colour < 2; colour++) {
            if (seen[colour] < 40) continue;
            double ratio = shown[colour] / (double) seen[colour];
            if (ratio < 0.4 || ratio > 2.5) {
                failures.add("The impostors show " + shown[colour] + " " + names[colour] + " pixels, the models " + seen[colour]);
            }
        }
        forget(context, world);
        Scene.configure(context, config -> config.impostors = false);
    }

    /**
     * Gone, and forgotten: a mob that vanishes this close to the player is taken as killed.
     * Behind the player, where the smoke of their deaths stays out of the screenshots.
     */
    private static void forget(ClientGameTestContext context, TestSingleplayerContext world) {
        Scene.command(world, "execute at @p run tp @e[type=cow] ~ ~ ~-5");
        context.waitTicks(10);
        Scene.command(world, "kill @e[type=cow]");
        context.waitTicks(20);
    }

    /** Swimmers in a pool and walkers beside it, seen, then left 200 blocks behind. */
    private void remembered(ClientGameTestContext context, TestSingleplayerContext world, List<String> failures) {
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(16));
        Scene.command(world, "execute at @p run fill ~-14 ~-3 ~50 ~14 ~-1 ~58 water");
        for (int i = 0; i < SWIMMERS.length; i++) {
            Scene.command(world, "execute at @p run summon " + SWIMMERS[i] + " ~" + (i * 6 - 6) + " ~-2 ~54"
                    + " {NoAI:1b,PersistenceRequired:1b,Rotation:[90f,0f]}");
        }
        // Past 32 blocks: a mob that vanishes closer is taken as killed or despawned.
        Scene.row(world, WALKERS, 1, 42, 4.0);
        context.waitTicks(100);
        Scene.command(world, "tp @a 0 ~ -160 0 0");
        context.waitTicks(300);
        context.runOnClient(minecraft -> {
            Set<String> swimmers = new HashSet<>();
            for (String swimmer : SWIMMERS) swimmers.add("minecraft:" + swimmer);
            int checked = 0;
            for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
                Entity puppet = mob.puppet();
                if (puppet == null) {
                    Scene.log("entityfeatures remembered " + mob.type() + " with no copy yet");
                    continue;
                }
                Scene.log("entityfeatures remembered " + mob.type() + " in water=" + puppet.isInWater());
                if (!swimmers.contains(mob.type())) continue;
                checked++;
                if (!puppet.isInWater()) failures.add("The copy of a " + mob.type() + " is not in water");
            }
            if (checked == 0) failures.add("No swimmer was remembered");
        });
        Scene.screenshot(context, this, "remembered-models");
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 100;
            config.impostorPixels = 0;
        });
        context.waitTicks(100);
        Scene.screenshot(context, this, "remembered-impostors");
        Scene.screenshotAtlas(context, this);
    }

    private static LivingEntity first(Minecraft minecraft, EntityType<?> type) {
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity.getType() == type && entity instanceof LivingEntity living) return living;
        }
        throw new AssertionError("The client has no " + EntityType.getKey(type));
    }

    /** The texture the game draws this mob with. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Identifier texture(Minecraft minecraft, LivingEntity entity) {
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        EntityRenderState state = GhostRenderer.extract(dispatcher, entity, 1f);
        LivingEntityRenderer renderer = (LivingEntityRenderer) dispatcher.getRenderer(entity);
        return renderer.getTextureLocation((LivingEntityRenderState) state);
    }

    /** The texture ETF gives this mob, through its API; the game's own without ETF. */
    private static String variant(Minecraft minecraft, LivingEntity entity, boolean etf) {
        Identifier texture = texture(minecraft, entity);
        if (!etf) return texture.toString();
        try {
            Class<?> api = Class.forName("traben.entity_texture_features.ETFApi");
            return api.getMethod("getCurrentETFVariantTextureOfEntity", Entity.class, Identifier.class)
                    .invoke(null, entity, texture).toString();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ETF is loaded, but not its API", e);
        }
    }

    /**
     * A resource pack beside the game's cow texture: the same shape in plain magenta (2) and
     * plain yellow (3), where ETF looks for random variants.
     */
    private static void writePack(Minecraft minecraft, Identifier texture) {
        Path root = minecraft.getResourcePackDirectory().resolve(PACK);
        String file = texture.getPath().substring(texture.getPath().lastIndexOf('/') + 1, texture.getPath().length() - 4);
        Path folder = root.resolve("assets").resolve(texture.getNamespace()).resolve("optifine/random/entity/cow");
        try (InputStream in = minecraft.getResourceManager().getResourceOrThrow(texture).open()) {
            BufferedImage base = ImageIO.read(in);
            Files.createDirectories(folder);
            ImageIO.write(plain(base, 0xEB28EB), "png", folder.resolve(file + "2.png").toFile());
            ImageIO.write(plain(base, 0xF0D21E), "png", folder.resolve(file + "3.png").toFile());
            Files.writeString(root.resolve("pack.mcmeta"), mcmeta());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the test pack", e);
        }
        Scene.log("entityfeatures pack " + PACK + " varies " + texture);
    }

    private static BufferedImage plain(BufferedImage base, int rgb) {
        BufferedImage out = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                int alpha = base.getRGB(x, y) >>> 24;
                out.setRGB(x, y, alpha << 24 | rgb);
            }
        }
        return out;
    }

    /** The pack's description, made for this game version. */
    private static String mcmeta() {
        //? if >=1.21.9 {
        var format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        String range = "[" + format.major() + "," + format.minor() + "]";
        return "{\"pack\":{\"description\":\"Living Horizon test: random cows\",\"min_format\":" + range
                + ",\"max_format\":" + range + "}}";
        //?} elif >=1.21.6 {
        /*int format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        return "{\"pack\":{\"description\":\"Living Horizon test: random cows\",\"pack_format\":" + format + "}}";
        *///?} else {
        /*int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        return "{\"pack\":{\"description\":\"Living Horizon test: random cows\",\"pack_format\":" + format + "}}";
        *///?}
    }

    /** Pixels of the two variants on a screenshot: magenta, then yellow. */
    private static int[] colours(Path screenshot) {
        try {
            BufferedImage image = ImageIO.read(screenshot.toFile());
            int magenta = 0, yellow = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgb = image.getRGB(x, y), r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
                    // Plain colours, shaded: never the brown and white of the game's cow, nor grass or sky.
                    if (r > 60 && b > 60 && g < 0.4 * Math.min(r, b)) magenta++;
                    else if (r > 60 && g > 0.75 * r && b < 0.3 * g) yellow++;
                }
            }
            return new int[]{magenta, yellow};
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + screenshot, e);
        }
    }
}
