package fr.clixmods.livinghorizon.render;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.ambient.Ambience;
import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.OcclusionQueries;
import fr.clixmods.livinghorizon.debug.DebugMarks;
import fr.clixmods.livinghorizon.debug.DebugMarks.Mark;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.render.impostor.ImpostorRenderer;
import fr.clixmods.livinghorizon.render.impostor.ImpostorViews;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayer;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
//? if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import net.minecraft.client.renderer.MultiBufferSource;
import java.util.ArrayList;
*///?}
import net.minecraft.core.BlockPos;
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
        if (distance > drawnRange) FarDepth.needed();
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
        OcclusionQueries.beginFrame();
        EXTRACTED.clear();
        TRANSFORMS.clear();
        glowing = false;
        //? if <1.21.9 {
        /*LIGHTS.clear();
        CLASSIC.clear();
        *///?}
        //? if >=1.21.2 && <1.21.9
        /*RENDERERS.clear();*/
        ImpostorRenderer.beginFrame();
        PolygonStats.beginFrame();
        forgetShown();
        neededFar = farthest > 0 ? (float) Math.min(MAX_FAR, farthest * 1.1 + 64) : 0f;
        farthest = 0;
    }

    /**
     * The far plane this frame needs: the projection is built before the entities are
     * extracted, so it covers the players of the frame before, with room to spare.
     */
    public static float farPlane(float vanilla) {
        FarConfig config = FarConfig.get();
        if (!config.anyDistant() || !config.extendFarPlane) return vanilla;
        return Math.max(vanilla, neededFar);
    }

    /** The game extracted this entity itself this frame. */
    public static void extracted(Entity entity) {
        EXTRACTED.add(entity);
    }

    /** Whether a figure added this frame wants the glowing outline. */
    private static boolean glowing;

    public static boolean glowing() {
        return glowing;
    }

    /** The state of an entity as its renderer sees it this frame. */
    private static EntityRenderState extract(EntityRenderDispatcher dispatcher, Entity entity, float partialTick) {
        //? if >=1.21.9 {
        return dispatcher.extractEntity(entity, partialTick);
        //?} elif >=1.21.2 {
        /*return state(renderer(dispatcher, entity), entity, partialTick);
        *///?} else {
        /*return EntityRenderState.of(entity, partialTick);
        *///?}
    }

    //? if >=1.21.2 && <1.21.9 {
    /*@SuppressWarnings("unchecked")
    private static <E extends Entity> net.minecraft.client.renderer.entity.EntityRenderer<? super E, EntityRenderState> renderer(
            EntityRenderDispatcher dispatcher, E entity) {
        return (net.minecraft.client.renderer.entity.EntityRenderer<? super E, EntityRenderState>) dispatcher.getRenderer(entity);
    }

    /^* A state of its own: the renderer's createRenderState(entity, tick) hands back one it reuses. ^/
    private static <E extends Entity, S extends EntityRenderState> EntityRenderState state(
            net.minecraft.client.renderer.entity.EntityRenderer<? super E, S> renderer, E entity, float partialTick) {
        S state = renderer.createRenderState();
        renderer.extractRenderState(entity, state, partialTick);
        RENDERERS.put(state, renderer);
        return state;
    }

    /^*
     * Which renderer made each state, the one that draws it: the game can only draw an
     * entity, not a state, before 1.21.9.
     ^/
    private static final Map<EntityRenderState, net.minecraft.client.renderer.entity.EntityRenderer<?, ?>> RENDERERS =
            new IdentityHashMap<>();

    @SuppressWarnings("unchecked")
    private static <S extends EntityRenderState> void drawState(S state, double x, double y, double z, PoseStack pose,
                                                               MultiBufferSource buffers, int light) {
        var renderer = (net.minecraft.client.renderer.entity.EntityRenderer<?, S>) RENDERERS.get(state);
        if (renderer == null) return;
        Vec3 offset = renderer.getRenderOffset(state);
        pose.pushPose();
        pose.translate(x + offset.x, y + offset.y, z + offset.z);
        renderer.render(state, pose, buffers, light);
        pose.popPose();
    }
    *///?} elif <1.21.2 {
    /*/^*
     * Draws a figure through its entity's renderer, the only way before 1.21.2, with what
     * the mod changed in its state given to the entity for that time.
     ^/
    @SuppressWarnings("unchecked")
    private static void drawState(EntityRenderState state, double x, double y, double z, PoseStack pose,
                                  MultiBufferSource buffers, int light) {
        Entity entity = state.entity;
        var renderer = (net.minecraft.client.renderer.entity.EntityRenderer<Entity>)
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        float tick = state.partialTick;
        Vec3 offset = renderer.getRenderOffset(entity, tick);
        pose.pushPose();
        pose.translate(x + offset.x, y + offset.y, z + offset.z);
        state.apply();
        try {
            renderer.render(entity, Mth.lerp(tick, entity.yRotO, entity.getYRot()), tick, pose, buffers, light);
        } finally {
            state.restore();
            pose.popPose();
        }
    }
    *///?}

    //? if <1.21.9 {
    /*/^* The light each figure is drawn in, which the states do not carry before 1.21.9. ^/
    private static final Map<EntityRenderState, Integer> LIGHTS = new IdentityHashMap<>();
    /^* This frame's figures, drawn by {@link #draw} rather than by the game. ^/
    private static final List<EntityRenderState> CLASSIC = new ArrayList<>();

    /^* The figures of this frame, gathered once the game has chosen the entities it draws. ^/
    public static void extract(Camera camera, float partialTick) {
        extract(camera, partialTick, CLASSIC);
    }

    /^*
     * Draws this frame's figures into the world's buffers, right after the game's own
     * entities: moved and scaled as {@link #transform} says, in the light worked out for them.
     ^/
    public static void draw(PoseStack pose, MultiBufferSource buffers, Vec3 camera) {
        for (EntityRenderState state : CLASSIC) {
            double x = state.x - camera.x, y = state.y - camera.y, z = state.z - camera.z;
            int light = LIGHTS.getOrDefault(state, LightTexture.pack(0, 15));
            Transform t = TRANSFORMS.get(state);
            PolygonStats.enter(state);
            try {
                if (t == null) {
                    drawState(state, x, y, z, pose, buffers, light);
                    continue;
                }
                pose.pushPose();
                pose.translate(
                        (float) (t.anchorX() * t.pull() + (x - t.anchorX()) * t.scale()),
                        (float) (t.anchorY() * t.pull() + (y - t.anchorY()) * t.scale()),
                        (float) (t.anchorZ() * t.pull() + (z - t.anchorZ()) * t.scale()));
                pose.scale((float) t.scale(), (float) t.scale(), (float) t.scale());
                drawState(state, 0.0, 0.0, 0.0, pose, buffers, light);
                pose.popPose();
            } finally {
                PolygonStats.leave();
            }
        }
    }
    *///?}

    public static @Nullable Transform transform(EntityRenderState state) {
        return TRANSFORMS.isEmpty() ? null : TRANSFORMS.get(state);
    }

    public static void extract(Camera camera, float partialTick, List<EntityRenderState> frame) {
        FarConfig config = FarConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        Vec3 eye = camera.position();
        view(camera, minecraft);
        prewarm(config);
        long started = System.nanoTime();
        drawn = 0;
        skipped = 0;

        for (FarPlayer player : config.enabled ? FarPlayerTracker.get().players() : List.<FarPlayer>of()) {
            AbstractClientPlayer live = player.live();
            if (live != null) {
                // Sent by the server, yet not drawn: outside the sections the client
                // draws, when its render distance is shorter than the server's.
                if (EXTRACTED.contains(live) || live.isInvisible()) continue;
                Entity mount = live.getRootVehicle();
                if (debug) DebugMarks.mark(live, Mark.PLAYER);
                if (mount == live && impostor(minecraft, eye, live, partialTick, config)) continue;
                EntityRenderState body = extract(dispatcher, live, partialTick);
                EntityRenderState mountState = mount != live && !EXTRACTED.contains(mount)
                        ? extract(dispatcher, mount, partialTick) : null;
                add(frame, minecraft, eye, body, mountState, false, config, live.getUUID());
            } else if (player.showsPuppet(config)) {
                Entity puppet = player.puppet();
                if (tooFar(puppet, eye, config.playerMaxDistance)) continue;
                if (skipped(puppet, false)) continue;
                if (debug) DebugMarks.mark(puppet, Mark.PLAYER);
                Entity mount = config.showVehicles ? player.mountPuppet() : null;
                if (mount == null && impostor(minecraft, eye, puppet, partialTick, config)) continue;
                EntityRenderState body = extract(dispatcher, puppet, partialTick);
                EntityRenderState mountState = mount == null ? null : extract(dispatcher, mount, partialTick);
                if (mountState != null && body instanceof HumanoidRenderState humanoid) {
                    humanoid.isPassenger = true;
                }
                add(frame, minecraft, eye, body, mountState, true, config, player.id());
            }
        }

        if (config.enabled) rest(frame, minecraft, dispatcher, eye, partialTick, config);
        mobs(frame, minecraft, dispatcher, eye, partialTick, config);
        unseen(frame, minecraft, dispatcher, eye, partialTick, config);
        LivingEntity cow = config.ufo ? FarPlayerTracker.get().ambience().ufo().cow() : null;
        if (cow != null) add(frame, minecraft, eye, extract(dispatcher, cow, partialTick), null, true, config, null);
        if (config.skyBirds) {
            // Parrots and bats; the silhouettes are drawn by AmbientRenderer.
            for (Ambience.Flyer flyer : FarPlayerTracker.get().ambience().flyers()) {
                if (flyer.puppet == null) continue;
                if (skip(flyer.puppet, true) != null) continue;
                add(frame, minecraft, eye, extract(dispatcher, flyer.puppet, partialTick), null, true, config, null,
                        (flyer.kind == Ambience.Kind.PARROT ? flyer.span : 1.0) * flyer.scale);
            }
        }
        Stats.extract(System.nanoTime() - started, drawn, skipped);
        if (debug) DebugMarks.end();
    }

    /** Past the distance set in the options; 0 is no limit. */
    private static boolean tooFar(Entity entity, Vec3 eye, int limit) {
        return limit > 0 && entity.distanceToSqr(eye) > (double) limit * limit;
    }

    /** Mobs met on the way, where they were, playing their animation. */
    private static void mobs(List<EntityRenderState> frame, Minecraft minecraft, EntityRenderDispatcher dispatcher,
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
            if (tooFar(puppet, eye, config.mobMaxDistance)) continue;
            // A copy walking up to its real mob stands for it, however close.
            if (!memory.handingOver(mob) && tooClose(puppet, eye, memory.nearRange(mob))) {
                if (debug) DebugMarks.mark(puppet, Mark.NEAR);
                continue;
            }
            if (skipped(puppet, true)) continue;
            if (occluded(mob.id(), puppet, config)) continue;
            if (debug) DebugMarks.mark(puppet, Mark.FAKE);
            mob.drawn();
            if (impostor(minecraft, eye, puppet, partialTick, config)) continue;
            EntityRenderState body = extract(dispatcher, puppet, partialTick);
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

    /**
     * Behind terrain, so not drawn this frame ({@link FarConfig#hideOccludedMobs}). A hidden
     * mob still counts for the far plane: past it, the depth there is the sky's, and the mob
     * could never be seen again to be drawn again.
     */
    private static boolean occluded(Object key, Entity entity, FarConfig config) {
        if (!config.hideOccludedMobs) return false;
        double dx = entity.getX() - eyeX, dy = entity.getY() - eyeY, dz = entity.getZ() - eyeZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // Drawn bigger than it is with a minimum size: the bigger box is what must be hidden.
        double boost = 1.0;
        if (config.minApparentPixels > 0 && distance > 1e-3) {
            double extent = Math.max(0.5, Math.max(entity.getBbHeight(), entity.getBbWidth()));
            boost = Math.clamp(config.minApparentPixels / (extent / distance * pixelsPerRadian), 1.0, 400.0);
        }
        if (!Occlusion.hidden(key, entity, dx, dy, dz, boost)) {
            return false;
        }
        noteDistance(distance);
        // Counted as drawn when it passed the view tests: it is not drawn after all.
        drawn--;
        skipped++;
        if (debug) DebugMarks.mark(entity, Mark.HIDDEN);
        return true;
    }

    /**
     * Within where the real mob would be sent, a copy is not where the mob is, and could be
     * walked up to: it is not drawn. See {@link MobMemory#nearRange}.
     */
    private static boolean tooClose(Entity puppet, Vec3 eye, double range) {
        double dx = puppet.getX() - eye.x, dz = puppet.getZ() - eye.z;
        return dx * dx + dz * dz < range * range;
    }

    private static void markRemembered(MobMemory.Remembered mob, Mark mark) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(mob.type())).orElse(null);
        if (type == null) return;
        DebugMarks.mark(mob.x(), mob.y(), mob.z(), type.getWidth(), type.getHeight(), type, mark);
    }

    /**
     * Mobs the server sends, of the kinds shown far away, that the game leaves out of the
     * frame: past its entity distance (the "Entity Distance" slider), or in a part of the
     * world it does not draw. Their copy only takes over once the server stops sending them;
     * until then they are drawn here, so that a mob never blinks out between the two.
     */
    private static void unseen(List<EntityRenderState> frame, Minecraft minecraft, EntityRenderDispatcher dispatcher,
                               Vec3 eye, float partialTick, FarConfig config) {
        if (!config.distantMobs) return;
        boolean game = debug && DebugMarks.gameMobs();
        Map<EntityType<?>, Boolean> wanted = new IdentityHashMap<>();
        MobMemory memory = FarPlayerTracker.get().mobs();
        for (Entity entity : memory.live()) {
            if (EXTRACTED.contains(entity)) {
                if (game) DebugMarks.mark(entity, Mark.GAME);
                continue;
            }
            if (entity.isRemoved() || entity.isInvisible() || entity.isPassenger() || entity.isVehicle()) continue;
            // Its copy is still walking up to it, and drawn in its place.
            if (memory.hides(entity)) continue;
            boolean shown = wanted.computeIfAbsent(entity.getType(),
                    type -> config.mobTypes.contains(EntityType.getKey(type).toString()));
            if (!shown && !(config.rememberNamedMobs && entity.hasCustomName())) continue;
            // Close by, the game leaves a mob out only when the terrain hides it.
            if (entity.distanceToSqr(eye) < 24 * 24) continue;
            if (tooFar(entity, eye, config.mobMaxDistance)) continue;
            if (skipped(entity, true)) continue;
            if (occluded(entity.getUUID(), entity, config)) continue;
            if (debug) DebugMarks.mark(entity, Mark.LIVE);
            if (impostor(minecraft, eye, entity, partialTick, config)) continue;
            add(frame, minecraft, eye, extract(dispatcher, entity, partialTick), null, false, config, entity.getUUID());
        }
    }

    public static boolean livesInWater(Entity entity) {
        MobCategory category = entity.getType().getCategory();
        return category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
                || category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS;
    }

    /** Players who logged off, lying or sitting where they were last. */
    private static void rest(List<EntityRenderState> frame, Minecraft minecraft, EntityRenderDispatcher dispatcher,
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
            if (skipped(puppet, true)) continue;
            if (debug) DebugMarks.mark(puppet, Mark.PLAYER);
            EntityRenderState body = extract(dispatcher, puppet, partialTick);
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

    private static void add(List<EntityRenderState> frame, Minecraft minecraft, Vec3 eye, EntityRenderState body,
                            @Nullable EntityRenderState mount, boolean puppet, FarConfig config, @Nullable Object who) {
        add(frame, minecraft, eye, body, mount, puppet, config, who, 1.0);
    }

    /** @param size drawn this many times bigger, around the feet */
    private static void add(List<EntityRenderState> frame, Minecraft minecraft, Vec3 eye, EntityRenderState body,
                            @Nullable EntityRenderState mount, boolean puppet, FarConfig config, @Nullable Object who,
                            double size) {
        double ax = body.x - eye.x, ay = body.y - eye.y, az = body.z - eye.z;
        double distance = Math.sqrt(ax * ax + ay * ay + az * az);
        if (distance < 1e-3) return;
        if (distance > drawnRange) FarDepth.needed();

        // Drawn where they really are, so that terrain in front hides them through the depth
        // buffer - Voxy's included, see FarDepth - unless that is past the far plane; then
        // closer and smaller in proportion, which looks the same but shows through whatever
        // stands between.
        farthest = Math.max(farthest, distance);
        double reach = DepthFar.of(minecraft) * 0.95;
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
            state.nameTag = null;
            //? if >=1.21.9 {
            state.shadowPieces.clear();
            state.shadowRadius = 0;
            if (puppet) state.lightCoords = light(level, state);
            int outline = self == null ? 0 : DebugMarks.outline(config, puppet);
            if (outline != 0) {
                state.outlineColor = outline;
                glowing = true;
            }
            //?} else {
            /*// Before 1.21.9 the light is handed over when the state is drawn, and outlines
            // need the entity: neither is in the state.
            LIGHTS.put(state, light(level, state));
            *///?}
            frame.add(state);
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
        return light(level, state.x, state.y, state.z);
    }

    private static int light(@Nullable ClientLevel level, double x, double y, double z) {
        BlockPos feet = BlockPos.containing(x, y + 0.1, z);
        if (level == null || !level.hasChunkAt(feet)) return LightTexture.pack(0, 15);
        int block = 0, sky = 0;
        for (int up = 0; up < 3; up++) {
            BlockPos at = feet.above(up);
            block = Math.max(block, level.getBrightness(LightLayer.BLOCK, at));
            sky = Math.max(sky, level.getBrightness(LightLayer.SKY, at));
        }
        return LightTexture.pack(block, sky);
    }

    // --- Impostors ------------------------------------------------------------------------

    private static long lastPrewarm;

    /**
     * Once a second, asks for the pictures of every figure on hand to be baked, near or far,
     * so that they are ready when the figure walks past the impostor distance: every skin,
     * every kind of mob, in the resource packs as they are.
     */
    private static void prewarm(FarConfig config) {
        if (!ImpostorAtlas.usable()) return;
        long now = System.currentTimeMillis();
        if (now - lastPrewarm < 1000) return;
        lastPrewarm = now;
        for (FarPlayer player : FarPlayerTracker.get().players()) {
            if (player.puppet() != null) ImpostorAtlas.request(ImpostorKey.of(player.puppet()), player.puppet());
        }
        if (!config.distantMobs) return;
        for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().shown()) {
            Entity puppet = mob.puppet();
            if (puppet instanceof LivingEntity) ImpostorAtlas.request(ImpostorKey.of(puppet), puppet);
        }
    }

    /** What a figure showed last as an impostor, so that neither its sheet nor its picture flickers. */
    private record Shown(ImpostorKey key, int view, long frame) {
    }

    private static final Map<Entity, Shown> SHOWN = new IdentityHashMap<>();
    private static long frame;

    /** A figure must come this much inside the impostor distance to get its model back. */
    private static final double IMPOSTOR_RETURN = 0.95;

    /**
     * Draws a flat picture of a figure in place of its model, when it is past the impostor
     * distance and its pictures are baked (they are asked for otherwise, and the model is
     * drawn meanwhile). Nothing is extracted: that is what is saved.
     *
     * <p>Once a figure is an impostor it stays one until it is a little closer than the
     * distance, it keeps its picture until it has clearly turned to the next, and when its
     * look changes (a sheep sheared, a player's armour) it keeps its old picture until the
     * new one is baked: none of it blinks between a model and a picture.
     */
    private static boolean impostor(Minecraft minecraft, Vec3 eye, Entity entity, float partialTick, FarConfig config) {
        if (!ImpostorAtlas.usable() || !(entity instanceof LivingEntity living)
                || entity.isPassenger() || entity.isVehicle()) {
            return false;
        }
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        double ax = x - eye.x, ay = y - eye.y, az = z - eye.z;
        double distance = Math.sqrt(ax * ax + ay * ay + az * az);
        Shown shown = SHOWN.get(entity);
        double from = shown == null ? config.impostorDistance : config.impostorDistance * IMPOSTOR_RETURN;
        if (distance <= from || distance < 1e-3) {
            if (shown != null) SHOWN.remove(entity);
            return false;
        }
        ImpostorKey key = ImpostorKey.of(entity);
        ImpostorAtlas.Sheet sheet = ImpostorAtlas.sheet(key);
        if (sheet == null) {
            ImpostorAtlas.request(key, entity);
            sheet = shown == null ? null : ImpostorAtlas.sheet(shown.key);
            if (sheet == null) return false;
            key = shown.key;
        }
        if (distance > drawnRange) FarDepth.needed();
        farthest = Math.max(farthest, distance);
        double reach = DepthFar.of(minecraft) * 0.95;
        double pull = distance > reach ? reach / distance : 1.0;
        double boost = 1.0;
        if (config.minApparentPixels > 0) {
            double extent = Math.max(entity.getBbHeight(), entity.getBbWidth());
            boost = Math.clamp(config.minApparentPixels / (extent / distance * pixelsPerRadian), 1.0, 400.0);
        }
        float yaw = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
        int view = ImpostorViews.view(yaw, -ax, -az, shown == null ? -1 : shown.view);
        SHOWN.put(entity, new Shown(key, view, frame));
        int light = light(minecraft.level, x, y, z);
        int outline = minecraft.player == null ? 0 : DebugMarks.impostorOutline(config);
        if (outline != 0 && ImpostorRenderer.OUTLINES) glowing = true;
        ImpostorRenderer.add(new ImpostorRenderer.Billboard(ax * pull, ay * pull, az * pull,
                sheet.worldSize() * pull * boost, sheet, view, light, outline));
        return true;
    }

    /** Forgets the figures not drawn as impostors for a while: puppets come and go. */
    private static void forgetShown() {
        frame++;
        if (frame % 600 == 0) SHOWN.values().removeIf(shown -> frame - shown.frame > 600);
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
