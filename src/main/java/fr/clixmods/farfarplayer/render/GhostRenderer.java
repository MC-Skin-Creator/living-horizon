package fr.clixmods.farfarplayer.render;

import fr.clixmods.farfarplayer.FarConfig;
import fr.clixmods.farfarplayer.ambient.Ambience;
import fr.clixmods.farfarplayer.track.FarPlayer;
import fr.clixmods.farfarplayer.track.FarPlayerTracker;
import fr.clixmods.farfarplayer.track.MobMemory;
import fr.clixmods.farfarplayer.track.RestingPlayers;
import fr.clixmods.farfarplayer.track.SharedPositions;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
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
                add(frame, minecraft, eye, body, mountState, false, config, live.getUUID());
            } else if (player.showsPuppet(config)) {
                Entity puppet = player.puppet();
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
        LivingEntity cow = config.ufo ? FarPlayerTracker.get().ambience().ufo().cow() : null;
        if (cow != null) add(frame, minecraft, eye, dispatcher.extractEntity(cow, partialTick), null, true, config, null);
        if (config.skyBirds) {
            // Parrots and bats; the silhouettes are drawn by AmbientRenderer.
            for (Ambience.Flyer flyer : FarPlayerTracker.get().ambience().flyers()) {
                if (flyer.puppet == null) continue;
                add(frame, minecraft, eye, dispatcher.extractEntity(flyer.puppet, partialTick), null, true, config, null,
                        (flyer.kind == Ambience.Kind.PARROT ? flyer.span : 1.0) * flyer.scale);
            }
        }
    }

    /** Mobs met on the way, where they were, doing their little loop. */
    private static void mobs(LevelRenderState frame, Minecraft minecraft, EntityRenderDispatcher dispatcher,
                             Vec3 eye, float partialTick, FarConfig config) {
        if (!config.distantMobs) return;
        for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
            Entity puppet = mob.puppet();
            if (puppet == null) continue;
            EntityRenderState body = dispatcher.extractEntity(puppet, partialTick);
            // A puppet is in no world, so never in water: fish would be drawn flopping on
            // their side, as on land. Water mobs were in water when last seen.
            if (body instanceof LivingEntityRenderState living && livesInWater(puppet)) living.isInWater = true;
            add(frame, minecraft, eye, body, null, true, config, mob.id());
        }
    }

    private static boolean livesInWater(Entity entity) {
        MobCategory category = entity.getType().getCategory();
        return category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS;
    }

    /** Players who logged off, lying or sitting where they were last. */
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
            Entity puppet = resting.puppet(level, spot);
            if (puppet == null) continue;
            EntityRenderState body = dispatcher.extractEntity(puppet, partialTick);
            if (body instanceof LivingEntityRenderState living) {
                living.yRot = 0;
                living.xRot = 0;
                living.walkAnimationSpeed = 0;
                if (sleep) {
                    // Lying on the ground: the game lays a sleeper out along its body
                    // rotation when there is no bed, a little lower than on a mattress.
                    living.pose = Pose.SLEEPING;
                    living.bedOrientation = null;
                    body.y += 0.13;
                }
            }
            if (!sleep && body instanceof HumanoidRenderState humanoid) {
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
            if (puppet && (level == null || !level.hasChunkAt(BlockPos.containing(state.x, state.y + 0.5, state.z)))) {
                // No chunk is loaded where this puppet stands: light it as open sky,
                // which the lightmap still darkens at night.
                state.lightCoords = LightTexture.pack(0, 15);
            }
            if (config.glowOutline && self != null) {
                state.outlineColor = 0xFFFFFF;
                frame.haveGlowingEntities = true;
            }
            frame.entityRenderStates.add(state);
        }
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
