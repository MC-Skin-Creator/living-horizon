package fr.clixmods.livinghorizon.render;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.ambient.Ambience;
import fr.clixmods.livinghorizon.compat.VoxyDepth;
import fr.clixmods.livinghorizon.debug.DebugMarks;
import fr.clixmods.livinghorizon.debug.DebugMarks.Mark;
import fr.clixmods.livinghorizon.track.FarPlayer;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds the distant players to the frame, next to the entities the game draws itself.
 *
 * <p>The game draws the world in two passes: extraction copies what each entity looks
 * like into a render state, submission turns those states into geometry. A puppet's
 * state is extracted by the game's own renderer, exactly like a real player's, and
 * added to the same list - so skins, armour, capes, elytras, mounts and shader packs
 * all come for free.
 *
 * <p>They are drawn where they really are, so that the terrain in front of them hides
 * them: Voxy writes its depth into the game's, and removes the distance fog. The one
 * limit is the far clipping plane, which {@code GameRendererMixin} pushes out to the
 * farthest of them. Past it (or with {@link FarConfig#extendFarPlane} off), a player is
 * drawn closer along the same line of sight and smaller by the same ratio: the same
 * picture, but in front of the terrain between.
 */
public final class GhostRenderer {
    /** How one state is moved and scaled at submission. Positions relative to the camera. */
    public record Transform(double anchorX, double anchorY, double anchorZ, double pull, double scale) {
    }

    private static final Map<EntityRenderState, Transform> TRANSFORMS = new IdentityHashMap<>();
    private static final Set<Entity> EXTRACTED = Collections.newSetFromMap(new IdentityHashMap<>());

    private GhostRenderer() {
    }

    /** Blocks. Past this the depth buffer is too coarse to be worth extending to. */
    private static final float MAX_FAR = 65536f;

    /** The farthest distant player drawn last frame, which this frame's far plane covers. */
    private static double farthest;
    private static float neededFar;
    /** Something drawn this far away this frame: the next far plane covers it. */
    public static void noteDistance(double distance) {
        farthest = Math.max(farthest, distance);
        if (distance > drawnRange) VoxyDepth.needed();
    }

    // --- What is worth drawing ----------------------------------------------------------

    /** The view this frame: where the camera looks, how wide, how many pixels a radian is. */
    private static double eyeX, eyeY, eyeZ, lookX, lookY, lookZ, viewAngle, pixelsPerRadian, drawnRange;
    private static int drawn, skipped;

    private static void view(Camera camera, Minecraft minecraft) {
        Vec3 eye = camera.position();
        eyeX = eye.x;
        eyeY = eye.y;
        eyeZ = eye.z;
        Vector3fc look = camera.forwardVector();
        lookX = look.x();
        lookY = look.y();
        lookZ = look.z();
        double fov = Math.toRadians(minecraft.options.fov().get());
        int width = Math.max(1, minecraft.getWindow().getWidth()), height = Math.max(1, minecraft.getWindow().getHeight());
        // Half the screen's diagonal, as an angle, with a margin for a quick turn of the head.
        double aspect = width / (double) height;
        viewAngle = Math.atan(Math.tan(fov / 2) * Math.sqrt(1 + aspect * aspect)) + 0.15;
        pixelsPerRadian = height / (2.0 * Math.tan(fov / 2));
        drawnRange = minecraft.options.getEffectiveRenderDistance() * 16 - 16;
        debug = DebugMarks.active();
        if (debug) DebugMarks.begin(eye, pixelsPerRadian);
    }

    /** The debug view is on this frame: every puppet says what became of it. */
    private static boolean debug;

    /**
     * Why a puppet is not worth extracting at all, or null when it is: outside the view,
     * or - for {@code mayVanish} - smaller than about half a pixel at its true size.
     * Extraction is the costly part of drawing a puppet, and most remembered mobs are
     * behind the camera or specks. Each test can be switched off to measure it.
     */
    static @Nullable Mark skip(Entity entity, boolean mayVanish) {
        FarConfig config = FarConfig.get();
        double size = Math.max(0.5, Math.max(entity.getBbHeight(), entity.getBbWidth()));
        double dx = entity.getX() - eyeX, dy = entity.getY() + size * 0.5 - eyeY, dz = entity.getZ() - eyeZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < size * 2 + 4) return count(null);
        if (config.optViewCulling) {
            double angle = Math.acos(Mth.clamp((dx * lookX + dy * lookY + dz * lookZ) / distance, -1, 1));
            if (angle > viewAngle + Math.asin(Math.min(1, size / distance))) return count(Mark.OUTSIDE);
        }
        if (mayVanish && config.optTinyCulling && config.minApparentPixels <= 0
                && size / distance * pixelsPerRadian < 0.6) {
            return count(Mark.TINY);
        }
        return count(null);
    }

    private static @Nullable Mark count(@Nullable Mark why) {
        if (why == null) drawn++;
        else skipped++;
        return why;
    }

    /** Not drawn this frame; the debug view is told why. */
    private static boolean skipped(Entity entity, boolean mayVanish) {
        Mark why = skip(entity, mayVanish);
        if (why == null) return false;
        if (debug) DebugMarks.mark(entity, why);
        return true;
    }

    public static void beginFrame() {
        EXTRACTED.clear();
        TRANSFORMS.clear();
        neededFar = farthest > 0 ? (float) Math.min(MAX_FAR, farthest * 1.1 + 64) : 0f;
        farthest = 0;
    }

    /**
     * The far plane this frame needs: the projection is built before the entities are
     * extracted, so it covers the players of the frame before, with room to spare.
     */
    public static float farPlane(float vanilla) {
        FarConfig config = FarConfig.get();
        if (!config.enabled || !config.extendFarPlane) return vanilla;
        return Math.max(vanilla, neededFar);
    }

    /** The game extracted this entity itself this frame. */
    public static void extracted(Entity entity) {
        EXTRACTED.add(entity);
    }

    public static @Nullable Transform transform(EntityRenderState state) {
        return TRANSFORMS.isEmpty() ? null : TRANSFORMS.get(state);
    }

    public static void extract(Camera camera, float partialTick, LevelRenderState frame) {
        FarConfig config = FarConfig.get();
        if (!config.enabled) return;
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        Vec3 eye = camera.position();
        view(camera, minecraft);
        long started = System.nanoTime();
        drawn = 0;
        skipped = 0;

        for (FarPlayer player : FarPlayerTracker.get().players()) {
            AbstractClientPlayer live = player.live();
            if (live != null) {
                // Sent by the server, yet not drawn: outside the sections the client
                // draws, when its render distance is shorter than the server's.
                if (EXTRACTED.contains(live) || live.isInvisible()) continue;
                Entity mount = live.getRootVehicle();
                EntityRenderState body = dispatcher.extractEntity(live, partialTick);
                EntityRenderState mountState = mount != live && !EXTRACTED.contains(mount)
                        ? dispatcher.extractEntity(mount, partialTick) : null;
                if (debug) DebugMarks.mark(live, Mark.PLAYER);
                add(frame, minecraft, eye, body, mountState, false, config, live.getUUID());
            } else if (player.showsPuppet(config)) {
                Entity puppet = player.puppet();
                if (skipped(puppet, false)) continue;
                if (debug) DebugMarks.mark(puppet, Mark.PLAYER);
                Entity mount = config.showVehicles ? player.mountPuppet() : null;
                EntityRenderState body = dispatcher.extractEntity(puppet, partialTick);
                EntityRenderState mountState = mount == null ? null : dispatcher.extractEntity(mount, partialTick);
                if (mountState != null && body instanceof HumanoidRenderState humanoid) {
                    humanoid.isPassenger = true;
                }
                add(frame, minecraft, eye, body, mountState, true, config, player.id());
            }
        }

        rest(frame, minecraft, dispatcher, eye, partialTick, config);
        mobs(frame, minecraft, dispatcher, eye, partialTick, config);
        unseen(frame, minecraft, dispatcher, eye, partialTick, config);
        LivingEntity cow = config.ufo ? FarPlayerTracker.get().ambience().ufo().cow() : null;
        if (cow != null) add(frame, minecraft, eye, dispatcher.extractEntity(cow, partialTick), null, true, config, null);
        if (config.skyBirds) {
            // Parrots and bats; the silhouettes are drawn by AmbientRenderer.
            for (Ambience.Flyer flyer : FarPlayerTracker.get().ambience().flyers()) {
                if (flyer.puppet == null) continue;
                if (skip(flyer.puppet, true) != null) continue;
                add(frame, minecraft, eye, dispatcher.extractEntity(flyer.puppet, partialTick), null, true, config, null,
                        (flyer.kind == Ambience.Kind.PARROT ? flyer.span : 1.0) * flyer.scale);
            }
        }
        Stats.extract(System.nanoTime() - started, drawn, skipped);
        if (debug) DebugMarks.end();
    }

    /** Mobs met on the way, where they were, doing their little loop. */
    private static void mobs(LevelRenderState frame, Minecraft minecraft, EntityRenderDispatcher dispatcher,
                             Vec3 eye, float partialTick, FarConfig config) {
        if (!config.distantMobs) return;
        MobMemory memory = FarPlayerTracker.get().mobs();
        List<MobMemory.Remembered> shown = memory.shown();
        for (MobMemory.Remembered mob : shown) {
            Entity puppet = mob.puppet();
            if (puppet == null) {
                if (debug && mob.waiting()) markRemembered(mob, Mark.WAITING);
                continue;
            }
            if (skipped(puppet, true)) continue;
            if (config.hideOccludedMobs && Occlusion.hidden(mob.id(), puppet.getX(), puppet.getY(), puppet.getZ(),
                    puppet.getBbHeight())) {
                if (debug) DebugMarks.mark(puppet, Mark.HIDDEN);
                continue;
            }
            if (debug) DebugMarks.mark(puppet, Mark.FAKE);
            EntityRenderState body = dispatcher.extractEntity(puppet, partialTick);
            // A puppet is in no world, so never in water: fish would be drawn flopping on
            // their side, as on land. Water mobs were in water when last seen.
            if (body instanceof LivingEntityRenderState living && livesInWater(puppet)) living.isInWater = true;
            add(frame, minecraft, eye, body, null, true, config, mob.id());
        }
        if (debug && DebugMarks.drawing() && minecraft.level != null) {
            // The ones remembered but past the most shown at once.
            Set<MobMemory.Remembered> listed = Collections.newSetFromMap(new IdentityHashMap<>());
            listed.addAll(shown);
            for (MobMemory.Remembered mob : memory.remembered(minecraft.level.dimension().identifier().toString())) {
                if (!listed.contains(mob)) markRemembered(mob, Mark.SPARE);
            }
        }
    }

    private static void markRemembered(MobMemory.Remembered mob, Mark mark) {
        EntityType<?> type = EntityType.byString(mob.type()).orElse(null);
        if (type == null) return;
        DebugMarks.mark(mob.x(), mob.y(), mob.z(), type.getWidth(), type.getHeight(), type, mark);
    }

    /**
     * Mobs the server sends, of the kinds shown far away, that the game leaves out of the
     * frame: past its entity distance (the "Entity Distance" slider), or in a part of the
     * world it does not draw. Their copy only takes over once the server stops sending them;
     * until then they are drawn here, so that a mob never blinks out between the two.
     */
    private static void unseen(LevelRenderState frame, Minecraft minecraft, EntityRenderDispatcher dispatcher,
                               Vec3 eye, float partialTick, FarConfig config) {
        if (!config.distantMobs) return;
        boolean game = debug && DebugMarks.gameMobs();
        Map<EntityType<?>, Boolean> wanted = new IdentityHashMap<>();
        for (Entity entity : FarPlayerTracker.get().mobs().live()) {
            if (EXTRACTED.contains(entity)) {
                if (game) DebugMarks.mark(entity, Mark.GAME);
                continue;
            }
            if (entity.isRemoved() || entity.isInvisible() || entity.isPassenger() || entity.isVehicle()) continue;
            boolean shown = wanted.computeIfAbsent(entity.getType(),
                    type -> config.mobTypes.contains(EntityType.getKey(type).toString()));
            if (!shown && !(config.rememberNamedMobs && entity.hasCustomName())) continue;
            // Close by, the game leaves a mob out only when the terrain hides it.
            if (entity.distanceToSqr(eye) < 24 * 24) continue;
            if (skipped(entity, true)) continue;
            if (config.hideOccludedMobs && Occlusion.hidden(entity.getUUID(), entity.getX(), entity.getY(), entity.getZ(),
                    entity.getBbHeight())) {
                if (debug) DebugMarks.mark(entity, Mark.HIDDEN);
                continue;
            }
            if (debug) DebugMarks.mark(entity, Mark.LIVE);
            add(frame, minecraft, eye, dispatcher.extractEntity(entity, partialTick), null, false, config, entity.getUUID());
        }
    }

    private static boolean livesInWater(Entity entity) {
        MobCategory category = entity.getType().getCategory();
        return category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS;
    }

    /**
     * Players who logged off, lying or sitting where they were last - or asleep in a bed
     * nearby, or on their feet for a moment when what they rested on gave way.
     */
    private static void rest(LevelRenderState frame, Minecraft minecraft, EntityRenderDispatcher dispatcher,
                             Vec3 eye, float partialTick, FarConfig config) {
        boolean sleep = "sleep".equals(config.offlinePose);
        if (!sleep && !"sit".equals(config.offlinePose)) return;
        ClientLevel level = minecraft.level;
        if (level == null) return;
        RestingPlayers resting = FarPlayerTracker.get().resting();
        int dimension = SharedPositions.dimensionCode(level.dimension());
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (spot.dimension() != dimension) continue;
            RestingPuppet puppet = resting.puppet(level, spot);
            if (skipped(puppet, true)) continue;
            if (debug) DebugMarks.mark(puppet, Mark.PLAYER);
            EntityRenderState body = dispatcher.extractEntity(puppet, partialTick);
            Direction bed = puppet.bedFacing();
            boolean lying = bed == null && !puppet.standing();
            if (body instanceof LivingEntityRenderState living) {
                living.yRot = 0;
                living.xRot = 0;
                living.walkAnimationSpeed = 0;
                if (bed != null) {
                    // In the bed, the way the game lays a sleeper in one.
                    living.pose = Pose.SLEEPING;
                    living.bedOrientation = bed;
                } else if (lying && sleep) {
                    // Lying on the ground: the game lays a sleeper out along its body
                    // rotation when there is no bed, a little lower than on a mattress.
                    living.pose = Pose.SLEEPING;
                    living.bedOrientation = null;
                    body.y += 0.13;
                }
            }
            if (lying && !sleep && body instanceof HumanoidRenderState humanoid) {
                // The riding pose, without a mount: legs out in front, on the ground.
                humanoid.isPassenger = true;
                body.y -= 0.6;
            }
            add(frame, minecraft, eye, body, null, true, config, spot.name());
        }
    }

    private static void add(LevelRenderState frame, Minecraft minecraft, Vec3 eye, EntityRenderState body,
                            @Nullable EntityRenderState mount, boolean puppet, FarConfig config, @Nullable Object who) {
        add(frame, minecraft, eye, body, mount, puppet, config, who, 1.0);
    }

    /** @param size drawn this many times bigger, around the feet */
    private static void add(LevelRenderState frame, Minecraft minecraft, Vec3 eye, EntityRenderState body,
                            @Nullable EntityRenderState mount, boolean puppet, FarConfig config, @Nullable Object who,
                            double size) {
        double ax = body.x - eye.x, ay = body.y - eye.y, az = body.z - eye.z;
        double distance = Math.sqrt(ax * ax + ay * ay + az * az);
        if (distance < 1e-3) return;
        if (distance > drawnRange) VoxyDepth.needed();

        // Drawn where they really are, so that terrain in front hides them through the depth
        // buffer - Voxy's included, see VoxyDepth - unless that is past the far plane; then
        // closer and smaller in proportion, which looks the same but shows through whatever
        // stands between.
        farthest = Math.max(farthest, distance);
        double reach = minecraft.gameRenderer.getDepthFar() * 0.95;
        double pull = distance > reach ? reach / distance : 1.0;

        double boost = 1.0;
        if (config.minApparentPixels > 0) {
            double fov = Math.toRadians(minecraft.options.fov().get());
            double pixelsPerRadian = minecraft.getWindow().getHeight() / (2.0 * Math.tan(fov / 2.0));
            // The longest side: a sleeper is 1.8 blocks long and 0.2 high.
            double extent = Math.max(1.8, Math.max(body.boundingBoxHeight, body.boundingBoxWidth)) * size;
            double apparent = extent / distance * pixelsPerRadian;
            boost = Math.clamp(config.minApparentPixels / apparent, 1.0, 400.0);
        }

        Transform transform = new Transform(ax, ay, az, pull, pull * boost * size);
        LocalPlayer self = minecraft.player;
        ClientLevel level = minecraft.level;
        for (EntityRenderState state : mount == null ? new EntityRenderState[]{body} : new EntityRenderState[]{mount, body}) {
            TRANSFORMS.put(state, transform);
            state.shadowPieces.clear();
            state.shadowRadius = 0;
            state.nameTag = null;
            if (puppet) state.lightCoords = light(level, state);
            if (config.glowOutline && self != null) {
                state.outlineColor = 0xFFFFFF;
                frame.haveGlowingEntities = true;
            }
            frame.entityRenderStates.add(state);
        }
    }

    /**
     * The light a puppet stands in. Where no chunk is loaded, open sky, which the lightmap
     * still darkens at night. Where one is, the brightest of its feet, the block above and
     * the one above that: a puppet stands where its mob was last seen, which may be a little
     * inside a block, or in a pocket the light engine has not reached yet - read there alone,
     * it would be drawn black.
     */
    private static int light(@Nullable ClientLevel level, EntityRenderState state) {
        BlockPos feet = BlockPos.containing(state.x, state.y + 0.1, state.z);
        if (level == null || !level.hasChunkAt(feet)) return LightTexture.pack(0, 15);
        int block = 0, sky = 0;
        for (int up = 0; up < 3; up++) {
            BlockPos at = feet.above(up);
            block = Math.max(block, level.getBrightness(LightLayer.BLOCK, at));
            sky = Math.max(sky, level.getBrightness(LightLayer.SKY, at));
        }
        return LightTexture.pack(block, sky);
    }

    /** Whether an entity the server sends should be drawn whatever its distance. */
    public static boolean alwaysRender(Entity entity) {
        FarConfig config = FarConfig.get();
        return config.enabled && config.renderTrackedVehiclesFar && carriesOtherPlayer(entity, 0);
    }

    private static boolean carriesOtherPlayer(Entity entity, int depth) {
        if (depth > 4) return false;
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof AbstractClientPlayer && !(passenger instanceof LocalPlayer)) return true;
            if (carriesOtherPlayer(passenger, depth + 1)) return true;
        }
        return false;
    }
}
