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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Mobs drawn with GeckoLib (issue #67, and #98 to #100 for Naturalist, Mowzie's Mobs, Bosses of
 * Mass Destruction, Critters and Companions). Run with GeckoLib and a mod of its mobs:
 * {@code run.sh geckolib --with geckolib,untitled-duck-mod}. Without GeckoLib it says so and
 * checks nothing.
 *
 * <p>Every mob kind drawn by a GeckoLib renderer is summoned in a row 64 blocks away, past
 * where the game stops drawing it: each must be drawn by the mod (GeckoLib only moves an
 * animation as it draws the mob), with a state of its own, then pictured as a simplified mob,
 * the picture not empty. Then they are left 140 blocks behind and their copies walk about:
 * their animations must go on, and a copy must not play the same animations as it walks as when
 * it stands. GeckoLib 4 calls a mob moving only from its motion and its legs, and most GeckoLib
 * mobs walk only then: a walking copy must have both (it had no motion and slid in its idle).
 */
public final class GeckoLibScenario implements Scenario {
    private static final int MOST = 12;

    @Override
    public String name() {
        return "geckolib";
    }

    @Override
    public void run(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("geckolib")) {
            Scene.log("geckolib not loaded: nothing to check (run.sh geckolib --with geckolib,untitled-duck-mod)");
            return;
        }
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.simulationDistance().set(5);
            minecraft.options.fov().set(30);
        });
        List<String> failures = new ArrayList<>();
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            List<String> kinds = context.computeOnClient(GeckoLibScenario::kinds);
            Scene.log("geckolib kinds: " + kinds);
            if (kinds.isEmpty()) throw new AssertionError("GeckoLib is loaded, but no mob of a mod draws with it");
            Scene.configure(context, config -> {
                config.hideOccludedMobs = false;
                config.impostors = false;
                config.animationMinPixels = 0;
                config.animationMaxDistance = 0;
                // Monsters are not shown by default: the bosses and monsters of these mods too.
                for (String kind : kinds) if (!config.mobTypes.contains(kind)) config.mobTypes.add(kind);
            });
            live(context, world, kinds, failures);
            remembered(context, world, kinds, failures);
        }
        if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
    }

    /** Mob kinds whose renderer is GeckoLib's, at most {@link #MOST}, a passive one first. */
    private static List<String> kinds(Minecraft minecraft) {
        List<String> found = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Entity sample;
            try {
                sample = type.create(minecraft.level, EntitySpawnReason.COMMAND);
            } catch (RuntimeException notOnTheClient) {
                continue;
            }
            if (!(sample instanceof LivingEntity)) continue;
            EntityRenderer<?, ?> renderer = minecraft.getEntityRenderDispatcher().getRenderer(sample);
            if (geckoLib(renderer.getClass())) found.add(EntityType.getKey(type).toString());
        }
        found.sort((a, b) -> Boolean.compare(!a.endsWith(":duck"), !b.endsWith(":duck")));
        return found.size() > MOST ? found.subList(0, MOST) : found;
    }

    private static boolean geckoLib(Class<?> type) {
        for (Class<?> at = type; at != null; at = at.getSuperclass()) {
            // software.bernie.geckolib up to 1.21.11, com.geckolib from 26.1.
            String name = at.getName();
            if (name.startsWith("software.bernie.geckolib.renderer.") || name.startsWith("com.geckolib.renderer.")) return true;
        }
        return false;
    }

    /**
     * Sent by the server, past the game's entity distance: drawn by the mod, models then
     * pictures. GeckoLib picks a mob's animation as it draws it: one that plays one was drawn.
     */
    private void live(ClientGameTestContext context, TestSingleplayerContext world, List<String> kinds,
                      List<String> failures) {
        for (int i = 0; i < kinds.size(); i++) {
            double x = (i - (kinds.size() - 1) / 2.0) * 5;
            Scene.command(world, "summon " + kinds.get(i) + " " + x + " ~ 64 {NoAI:1b,PersistenceRequired:1b,Rotation:[90f,0f]}");
            // Two of the first: a renderer that reuses one state must still draw each its own.
            if (i == 0) {
                Scene.command(world, "summon " + kinds.get(i) + " " + (x - 2.5) + " ~ 64 {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
            }
        }
        context.waitTicks(60);
        context.runOnClient(minecraft -> {
            Map<String, List<Entity>> byKind = mobs(minecraft, kinds);
            for (String kind : kinds) {
                List<Entity> mobs = byKind.getOrDefault(kind, List.of());
                if (mobs.isEmpty()) {
                    // Its mod sends it to clients from closer than this: only its copies are checked.
                    Scene.log("geckolib live " + kind + " is not sent this far: its copies only");
                    continue;
                }
                Entity mob = mobs.get(0);
                double distance = Math.sqrt(mob.distanceToSqr(minecraft.player));
                Set<String> playing = playing(mob);
                Scene.log("geckolib live " + kind + " " + Math.round(distance) + " blocks away: drawn by the game="
                        + mob.shouldRenderAtSqrDistance(distance * distance) + " drawable="
                        + GhostRenderer.drawable(minecraft.getEntityRenderDispatcher(), mob) + " playing " + playing);
                if (!GhostRenderer.drawable(minecraft.getEntityRenderDispatcher(), mob)) {
                    failures.add(kind + " is left out far away");
                } else if (playing.isEmpty()) {
                    failures.add(kind + " was never drawn far away: no animation of it plays");
                }
            }
            List<Entity> twins = byKind.getOrDefault(kinds.get(0), List.of());
            if (twins.size() >= 2) {
                var dispatcher = minecraft.getEntityRenderDispatcher();
                var a = GhostRenderer.extract(dispatcher, twins.get(0), 1f);
                var b = GhostRenderer.extract(dispatcher, twins.get(1), 1f);
                boolean own = a != null && b != null && a != b && a.x != b.x;
                Scene.log("geckolib two " + kinds.get(0) + " have states of their own: " + own);
                if (!own) failures.add("Two " + kinds.get(0) + " share one state");
            }
        });
        Path models = Scene.screenshot(context, this, "live-models");

        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 32;
            config.impostorPixels = 0;
        });
        for (int wait = 0; wait < 60; wait++) {
            context.waitTicks(10);
            boolean pictured = context.computeOnClient(minecraft -> {
                if (ImpostorAtlas.waiting() > 0) return false;
                for (List<Entity> mobs : mobs(minecraft, kinds).values()) {
                    for (Entity mob : mobs) if (!GhostRenderer.pictured(mob)) return false;
                }
                return true;
            });
            if (pictured) break;
        }
        Path pictures = Scene.screenshot(context, this, "live-impostors");
        Path atlas = Scene.screenshotAtlas(context, this);
        context.runOnClient(minecraft -> {
            for (Map.Entry<String, List<Entity>> entry : mobs(minecraft, kinds).entrySet()) {
                Entity mob = entry.getValue().get(0);
                ImpostorKey key = mob instanceof LivingEntity living ? ImpostorKey.of(living) : null;
                int[] rect = key == null ? null : Scene.atlasRect(key);
                int filled = rect == null ? 0 : filled(atlas, rect);
                Scene.log("geckolib picture " + entry.getKey() + ": pictured=" + GhostRenderer.pictured(mob)
                        + " baked=" + (rect != null) + " pixels=" + filled + " bakes=" + ImpostorAtlas.bakes()
                        + " failed=" + ImpostorAtlas.failed());
                if (!GhostRenderer.pictured(mob)) failures.add(entry.getKey() + " is not drawn as a picture");
                else if (rect == null) failures.add(entry.getKey() + "'s picture is not baked");
                else if (filled < 20) failures.add(entry.getKey() + "'s picture is empty (" + filled + " pixels)");
            }
        });
        Scene.log("geckolib pixels changed between models and pictures: " + changed(models, pictures));
        Scene.configure(context, config -> config.impostors = false);
    }

    /**
     * Left 140 blocks behind, out of the chunks the server moves: their copies play their own
     * animation. Over 30 seconds, what each plays while it walks and while it stands.
     */
    private void remembered(ClientGameTestContext context, TestSingleplayerContext world, List<String> kinds,
                            List<String> failures) {
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(16));
        // Seen closer first, whatever distance their mod sends them from (yet far enough not to be
        // taken as killed when they vanish), then left behind.
        Scene.command(world, "tp @a 0 ~ 26 0 0");
        context.waitTicks(100);
        context.runOnClient(minecraft -> mobs(minecraft, kinds).forEach((kind, mobs) -> Scene.log("geckolib up close "
                + kind + ": " + mobs.size() + " on the client, at " + mobs.get(0).blockPosition())));
        Scene.command(world, "tp @a 0 ~ -80 0 0");
        context.waitTicks(300);
        // How many looks played each animation, walking and standing, by kind.
        Map<String, Map<String, Integer>> walking = new LinkedHashMap<>(), standing = new LinkedHashMap<>();
        Map<Entity, Double> clocks = new IdentityHashMap<>();
        Map<Entity, double[]> places = new IdentityHashMap<>();
        Map<String, Integer> advanced = new HashMap<>(), copies = new HashMap<>(), steps = new HashMap<>(),
                still = new HashMap<>(), moving = new HashMap<>();
        for (int sample = 0; sample < 60; sample++) {
            context.runOnClient(minecraft -> {
                MobMemory memory = FarPlayerTracker.get().mobs();
                for (MobMemory.Remembered mob : memory.shown()) {
                    if (!kinds.contains(mob.type()) || !(mob.puppet() instanceof LivingEntity puppet)) continue;
                    memory.catchUp(mob);
                    copies.merge(mob.type(), 1, Integer::sum);
                    double[] at = {puppet.getX(), puppet.getZ()};
                    double[] was = places.put(puppet, at);
                    if (was == null) continue;
                    boolean walks = Math.hypot(at[0] - was[0], at[1] - was[1]) > 0.2;
                    Set<String> playing = playing(puppet);
                    Map<String, Integer> counts = (walks ? walking : standing).computeIfAbsent(mob.type(), k -> new TreeMap<>());
                    for (String name : playing) counts.merge(name, 1, Integer::sum);
                    if (!walks) still.merge(mob.type(), 1, Integer::sum);
                    if (walks) {
                        steps.merge(mob.type(), 1, Integer::sum);
                        // GeckoLib 4 plays a walk only when this holds (GeoEntityRenderer.actuallyRender).
                        var motion = puppet.getDeltaMovement();
                        if ((Math.abs(motion.x) + Math.abs(motion.z)) / 2 >= 0.015 && puppet.walkAnimation.speed() > 0) {
                            moving.merge(mob.type(), 1, Integer::sum);
                        }
                    }
                    double clock = clock(puppet);
                    Double before = clocks.put(puppet, clock);
                    if (before != null && clock > before) advanced.merge(mob.type(), 1, Integer::sum);
                }
            });
            context.waitTicks(10);
        }
        Scene.screenshot(context, this, "remembered-models");
        for (String kind : kinds) {
            Scene.log("geckolib copy " + kind + ": seen " + copies.getOrDefault(kind, 0) + " times, clock went on "
                    + advanced.getOrDefault(kind, 0) + " times, walking plays " + walking.get(kind)
                    + ", standing plays " + standing.get(kind) + ", moving for GeckoLib 4 " + moving.getOrDefault(kind, 0)
                    + " of " + steps.getOrDefault(kind, 0) + " walking looks");
            if (copies.getOrDefault(kind, 0) == 0) {
                failures.add("No copy of " + kind + " was remembered");
                continue;
            }
            if (advanced.getOrDefault(kind, 0) == 0) failures.add("The copies of " + kind + " are frozen");
            int walked = steps.getOrDefault(kind, 0);
            if (walked == 0) failures.add("No copy of " + kind + " walked");
            else if (moving.getOrDefault(kind, 0) * 2 < walked) {
                failures.add("The copies of " + kind + " walk with no motion: GeckoLib 4 plays their idle");
            }
            // Some controllers play the same whatever the mob does (blinking), and a copy just
            // stopped still ends its walk: some animation must be played far more often walking.
            String walks = walkingOnly(walking.get(kind), walked, standing.get(kind), still.getOrDefault(kind, 0));
            Scene.log("geckolib copy " + kind + " walks with " + walks);
            if (walks == null && standing.containsKey(kind)) {
                failures.add("The copies of " + kind + " play the same animations walking as standing");
            }
        }
        still(context, kinds, failures);
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 100;
            config.impostorPixels = 0;
        });
        context.waitTicks(100);
        Scene.screenshot(context, this, "remembered-impostors");
        Scene.configure(context, config -> config.impostors = false);
    }

    /**
     * The default *Min. Animated Size*: copies this far are too small to be animated, and stand
     * still, drawn as models. GeckoLib must still pose them: each must play an animation.
     */
    private void still(ClientGameTestContext context, List<String> kinds, List<String> failures) {
        Scene.configure(context, config -> config.animationMinPixels = new fr.clixmods.livinghorizon.FarConfig().animationMinPixels);
        Map<String, Integer> looks = new HashMap<>(), posed = new HashMap<>();
        for (int sample = 0; sample < 20; sample++) {
            context.waitTicks(10);
            context.runOnClient(minecraft -> {
                MobMemory memory = FarPlayerTracker.get().mobs();
                for (MobMemory.Remembered mob : memory.shown()) {
                    if (!kinds.contains(mob.type()) || !(mob.puppet() instanceof LivingEntity puppet)) continue;
                    looks.merge(mob.type(), 1, Integer::sum);
                    if (!playing(puppet).isEmpty()) posed.merge(mob.type(), 1, Integer::sum);
                }
            });
        }
        Scene.screenshot(context, this, "remembered-still");
        for (String kind : kinds) {
            int seen = looks.getOrDefault(kind, 0);
            Scene.log("geckolib still " + kind + ": " + seen + " looks, " + posed.getOrDefault(kind, 0)
                    + " playing an animation");
            if (seen > 0 && posed.getOrDefault(kind, 0) < seen) {
                failures.add("Copies of " + kind + " drawn with no animation (in their model's base pose)");
            }
        }
    }

    /**
     * The animation played in a larger share of the walking looks than of the standing ones, by
     * at least a third; null when there is none.
     */
    private static @Nullable String walkingOnly(@Nullable Map<String, Integer> walking, int walkingLooks,
                                                @Nullable Map<String, Integer> standing, int standingLooks) {
        if (walking == null || walkingLooks == 0) return null;
        String best = null;
        double gap = 1 / 3.0;
        for (Map.Entry<String, Integer> entry : walking.entrySet()) {
            double standingShare = standingLooks == 0 || standing == null ? 0 : standing.getOrDefault(entry.getKey(), 0) / (double) standingLooks;
            double share = entry.getValue() / (double) walkingLooks - standingShare;
            if (share > gap) {
                gap = share;
                best = entry.getKey();
            }
        }
        return best;
    }

    private static Map<String, List<Entity>> mobs(Minecraft minecraft, List<String> kinds) {
        Map<String, List<Entity>> found = new LinkedHashMap<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            String kind = EntityType.getKey(entity.getType()).toString();
            if (kinds.contains(kind)) found.computeIfAbsent(kind, k -> new ArrayList<>()).add(entity);
        }
        return found;
    }

    /** The animations GeckoLib plays on this entity now, by controller; none until it was drawn. */
    static Set<String> playing(Entity entity) {
        Set<String> names = new LinkedHashSet<>();
        try {
            for (Object controller : controllers(entity)) {
                Object raw = controller.getClass().getMethod("getCurrentRawAnimation").invoke(controller);
                if (raw == null) continue;
                List<?> stages = (List<?>) raw.getClass().getMethod("getAnimationStages").invoke(raw);
                for (Object stage : stages) {
                    names.add((String) stage.getClass().getMethod("animationName").invoke(stage));
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("GeckoLib is loaded, but not the version this scenario knows", e);
        }
        return names;
    }

    /** How far GeckoLib's clock has gone for this entity: it only goes on as the entity is drawn. */
    private static double clock(Entity entity) {
        try {
            Object manager = manager(entity);
            try {
                return (double) manager.getClass().getMethod("getLastUpdateTime").invoke(manager);
            } catch (NoSuchMethodException five) {
                double most = Double.NEGATIVE_INFINITY;
                for (Object controller : controllers(entity)) {
                    most = Math.max(most, (double) controller.getClass().getMethod("getCurrentAnimationTime").invoke(controller));
                }
                return most;
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("GeckoLib is loaded, but not the version this scenario knows", e);
        }
    }

    private static Object manager(Entity entity) throws ReflectiveOperationException {
        Object cache = entity.getClass().getMethod("getAnimatableInstanceCache").invoke(entity);
        Method forId = cache.getClass().getMethod("getManagerForId", long.class);
        return forId.invoke(cache, (long) entity.getId());
    }

    private static Iterable<?> controllers(Entity entity) throws ReflectiveOperationException {
        Object manager = manager(entity);
        return ((Map<?, ?>) manager.getClass().getMethod("getAnimationControllers").invoke(manager)).values();
    }

    /** Pixels of a sheet in the atlas screenshot that are not its background. */
    private static int filled(Path atlas, int[] rect) {
        try {
            BufferedImage image = ImageIO.read(atlas.toFile());
            double scale = image.getWidth() / (double) Scene.minecraft().getWindow().getGuiScaledWidth();
            int count = 0;
            for (int y = (int) (rect[1] * scale); y < (rect[1] + rect[3]) * scale && y < image.getHeight(); y++) {
                for (int x = (int) (rect[0] * scale); x < (rect[0] + rect[2]) * scale && x < image.getWidth(); x++) {
                    int rgb = image.getRGB(x, y) & 0xFFFFFF;
                    int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
                    // The screen behind the pages is 0x7090B0; an empty tile shows it through.
                    if (Math.abs(r - 0x70) + Math.abs(g - 0x90) + Math.abs(b - 0xB0) > 30) count++;
                }
            }
            return count;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + atlas, e);
        }
    }

    /** Pixels that differ between two screenshots of the same size. */
    private static int changed(Path a, Path b) {
        try {
            BufferedImage one = ImageIO.read(a.toFile()), two = ImageIO.read(b.toFile());
            int count = 0;
            for (int y = 0; y < Math.min(one.getHeight(), two.getHeight()); y++) {
                for (int x = 0; x < Math.min(one.getWidth(), two.getWidth()); x++) {
                    if (one.getRGB(x, y) != two.getRGB(x, y)) count++;
                }
            }
            return count;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not compare " + a + " and " + b, e);
        }
    }
}
