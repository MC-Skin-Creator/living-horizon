package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 9: now and then, at night, far off on open ground, six to eight figures in white
 * hooded robes sit in a circle round a fire, one of them standing. Looked at through a
 * spyglass, they all turn their heads, then their bodies, slowly to the player, and keep
 * facing them. Once they have been seen so, as soon as the player lowers the spyglass, they
 * stand up and come, each on a heading of its own, walking towards the player, and fade away
 * before they reach them. Never seen through a spyglass, they stay until dawn, and are gone,
 * fire and all, if the player comes near. Only on this screen.
 *
 * <p>Their place is read around the player, in the loaded chunks or the far world
 * ({@link Columns}): dry, flat, open ground in sight ({@link Sight}), and the ground on the
 * way to the player, for where they walk past the loaded chunks. The figures are drawn of
 * boxes ({@link BoxFigure}), lit by their fire, which is {@link Campfire}'s.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Sect implements Egg {
    /** The robe, white; the face, dark under the hood. */
    private static final int ROBE = 0xE6E3DA, FACE = 0x18140F;
    /**
     * A person in a long robe, white all over: the face is the hood's to draw, see-through
     * with the rest as the figure fades, so the shape has none of its own.
     */
    private static final BoxFigure.Shape SHAPE = new BoxFigure.Shape(
            0.75f, 0.25f, 0.75f, 0.5f, 0.28f, 0.72f, 0.22f, 0.5f, 0f, ROBE, ROBE);
    /** Blocks the hips sit above the ground when seated. */
    private static final float SEAT = 0.2f;
    /** Blocks from the fire to each figure round it. */
    private static final double RING = 2.6;
    /** Figures round the fire, at least; more, at most. */
    private static final int FEW = 6, MORE = 2;
    /** Blocks per tick they walk. */
    private static final double PACE = 0.13;
    /** How far off they may be, in blocks across; nearer than which they go, fire and all; a walker goes. */
    private static final double MIN = 80, MAX = 300, NEAR = 60, REACHED = 40;
    /** Spots tried; of them, ahead of the player when called; points read on the way to the player. */
    private static final int SPOTS = 48, AHEAD = 32, ALONG = 6;
    /** Ticks looked at through a spyglass before they notice; ticks of a fade. */
    private static final int NOTICE = 6, FADE = 40;
    /** Ticks they sit at most; walk at most; the fire burns once they are all gone; a search may take. */
    private static final int LIFE = 14000, WALK = 1200, EMBERS = 1200, SEARCH = 400;
    /** Degrees per tick a head turns, a body turns to look, a walker turns to its way. */
    private static final float HEAD_TURN = 5f, BODY_TURN = 3f, WALK_TURN = 6f;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, active, called, noticed, scattering, fireOn;
    /** For the game tests: stands in for the player's spyglass while set. */
    private @Nullable Boolean scopingStandIn;
    /** The fire; the player's feet when they came, which the ground on the way was read towards. */
    private double fx, fy, fz, toX, toZ;
    private float wind;
    private int fireLight;
    /** The ground on the way to the player, {@link #ALONG} stretches, from the fire. */
    private double[] heights = new double[0];
    private int count;
    private double[] x = new double[0], y = x, z = x, xo = x, yo = x, zo = x, walked = x;
    /** World yaws of each body and head, in degrees; the heading each takes off the player's. */
    private float[] body = new float[0], bodyO = body, head = body, headO = body, off = body, rise = body;
    /** Ticks before each turns to the player once noticed, stands up once they scatter; ticks of its fade, -1 until then. */
    private int[] turnAt = new int[0], standAt = turnAt, fade = turnAt, light = turnAt;
    private boolean[] gone = new boolean[0];
    private int age, lookedAt, sinceNoticed, sinceScatter, emptyFor, idle, searchAge;

    Sect(EggCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public int number() {
        return 9;
    }

    @Override
    public boolean busy() {
        return searching || active;
    }

    /** The fire they sit round, while they are there. */
    @Override
    public @Nullable Vec3 position() {
        return active ? new Vec3(fx, fy, fz) : null;
    }

    /** They have been seen through a spyglass. For the game tests. */
    public boolean noticed() {
        return active && noticed;
    }

    /** Figures still there. For the game tests. */
    public int left() {
        int n = 0;
        for (int i = 0; active && i < count; i++) if (!gone[i]) n++;
        return n;
    }

    /**
     * The widest angle, in degrees, between where a figure still there faces, head or body,
     * and the way to {@code from}. For the game tests.
     */
    public float turnedAway(Vec3 from) {
        float widest = 0f;
        for (int i = 0; active && i < count; i++) {
            if (gone[i]) continue;
            float to = yawTo(x[i], z[i], from.x, from.z);
            widest = Math.max(widest, Math.max(Math.abs(Mth.wrapDegrees(head[i] - to)), Math.abs(Mth.wrapDegrees(body[i] - to))));
        }
        return widest;
    }

    /** Blocks across from {@code from} to the nearest figure still there; infinite when none. For the game tests. */
    public double nearest(Vec3 from) {
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = 0; active && i < count; i++) {
            if (!gone[i]) nearest = Math.min(nearest, Math.hypot(x[i] - from.x, z[i] - from.z));
        }
        return nearest;
    }

    /**
     * For the game tests, which cannot always hold a spyglass up: true or false stands in for
     * whether the player looks through one; null reads the player again.
     */
    public void standIn(@Nullable Boolean scoping) {
        scopingStandIn = scoping;
    }

    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach());
        if (far <= MIN) {
            if (called) EggLog.nowhere(9);
            return false;
        }
        double look = Math.toRadians(self.getYRot());
        // Each spot, four points round it where they sit, then the way to the player.
        int per = 5 + ALONG - 1;
        double[] xs = new double[SPOTS * per], zs = new double[xs.length];
        double px = self.getX(), pz = self.getZ();
        for (int s = 0; s < SPOTS; s++) {
            double bearing = called && s < AHEAD ? look + random.nextGaussian() * 0.5 : random.nextDouble() * Math.PI * 2;
            double distance = MIN + random.nextDouble() * (far - MIN);
            double cx = Mth.floor(px - Math.sin(bearing) * distance) + 0.5;
            double cz = Mth.floor(pz + Math.cos(bearing) * distance) + 0.5;
            xs[s * per] = cx;
            zs[s * per] = cz;
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2;
                xs[s * per + 1 + k] = cx + Math.cos(a) * (RING + 0.5);
                zs[s * per + 1 + k] = cz + Math.sin(a) * (RING + 0.5);
            }
            for (int k = 1; k < ALONG; k++) {
                xs[s * per + 4 + k] = Mth.lerp((double) k / ALONG, cx, px);
                zs[s * per + 4 + k] = Mth.lerp((double) k / ALONG, cz, pz);
            }
        }
        this.called = called;
        level = current;
        searching = true;
        searchAge = 0;
        int mine = generation.get();
        Executor game = Minecraft.getInstance();
        Vec3 eye = self.getEyePosition();
        double feet = self.getY();
        Columns.read(current, xs, zs).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            // Dry, open and flat where they sit.
            List<Integer> open = new ArrayList<>();
            for (int s = 0; s < SPOTS; s++) {
                Columns.Column spot = columns[s * per];
                boolean ok = ground(current, spot);
                for (int k = 1; k <= 4 && ok; k++) {
                    Columns.Column round = columns[s * per + k];
                    ok = round.dry() && Math.abs(round.y() - spot.y()) <= 1;
                }
                if (ok) open.add(s);
            }
            double[] hx = new double[open.size()], hy = new double[open.size()], hz = new double[open.size()];
            for (int i = 0; i < open.size(); i++) {
                Columns.Column spot = columns[open.get(i) * per];
                hx[i] = spot.x();
                hy[i] = spot.y() + 1.6;
                hz[i] = spot.z();
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                searching = false;
                List<Integer> clear = new ArrayList<>();
                for (int i = 0; i < seen.length; i++) if (seen[i]) clear.add(open.get(i));
                Integer s = Sight.any(clear, random);
                if (s == null) {
                    if (called) EggLog.nowhere(9);
                    return;
                }
                // The ground on the way, each point unknown taking the one before it.
                double[] ys = new double[ALONG + 1];
                ys[0] = columns[s * per].y();
                for (int k = 1; k < ALONG; k++) {
                    double at = columns[s * per + 4 + k].y();
                    ys[k] = Double.isNaN(at) ? ys[k - 1] : at;
                }
                ys[ALONG] = feet;
                begin(current, columns[s * per], ys, px, pz);
                EggLog.came(9, fx, fy, fz, self, called);
            }, game);
        }, game);
        return true;
    }

    /** Open dry ground: out of the water, not on leaves nor ice, under the sky where it is loaded. */
    private static boolean ground(ClientLevel level, Columns.Column column) {
        if (!column.dry()) return false;
        BlockState block = column.block();
        if (block != null && (block.is(BlockTags.LEAVES) || block.is(BlockTags.ICE))) return false;
        return !Columns.loaded(level, column.x(), column.z())
                || level.canSeeSky(BlockPos.containing(column.x(), column.y(), column.z()));
    }

    private void begin(ClientLevel current, Columns.Column spot, double[] ys, double px, double pz) {
        if (!called) calendar.shown(9);
        fx = spot.x();
        fy = spot.y();
        fz = spot.z();
        toX = px;
        toZ = pz;
        heights = ys;
        wind = random.nextFloat() * Mth.TWO_PI;
        fireLight = Campfire.light(current, fx, fy, fz, 14);
        count = FEW + random.nextInt(MORE + 1);
        x = new double[count];
        y = new double[count];
        z = new double[count];
        walked = new double[count];
        body = new float[count];
        head = new float[count];
        off = new float[count];
        rise = new float[count];
        turnAt = new int[count];
        standAt = new int[count];
        fade = new int[count];
        light = new int[count];
        gone = new boolean[count];
        double start = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double a = start + i * Math.PI * 2 / count + (random.nextDouble() - 0.5) * 0.25;
            x[i] = fx + Math.cos(a) * RING;
            z[i] = fz + Math.sin(a) * RING;
            double ground = loadedGround(current, x[i], z[i]);
            y[i] = Double.isNaN(ground) ? fy : ground;
            body[i] = head[i] = yawTo(x[i], z[i], fx, fz);
            // The one standing turns first and stands at once; the others one after the other.
            turnAt[i] = i == 0 ? 0 : 10 + random.nextInt(30);
            standAt[i] = i == 0 ? 0 : 4 + random.nextInt(30);
            off[i] = (random.nextFloat() * 2 - 1) * 20f;
            rise[i] = i == 0 ? 1f : 0f;
            fade[i] = -1;
            light[i] = Campfire.light(current, x[i], y[i], z[i], 12);
        }
        xo = x.clone();
        yo = y.clone();
        zo = z.clone();
        bodyO = body.clone();
        headO = head.clone();
        age = lookedAt = sinceNoticed = sinceScatter = emptyFor = 0;
        noticed = scattering = false;
        fireOn = true;
        active = true;
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        if (!active) {
            // Rare, and only at night: about once in an hour of it.
            if (++idle > 6000 && quiet && !current.isBrightOutside() && random.nextInt(72000) == 0) {
                idle = 0;
                summon(current, self, false);
            }
            return;
        }
        age++;
        System.arraycopy(x, 0, xo, 0, count);
        System.arraycopy(y, 0, yo, 0, count);
        System.arraycopy(z, 0, zo, 0, count);
        System.arraycopy(body, 0, bodyO, 0, count);
        System.arraycopy(head, 0, headO, 0, count);
        double fromFire = Math.hypot(self.getX() - fx, self.getZ() - fz);
        boolean fireDrawn = Columns.drawn(current, self, fx, fz);
        if (!scattering && (fromFire < NEAR || !fireDrawn)) {
            clear();
            return;
        }
        if (fireOn && (fromFire < NEAR || !fireDrawn)) fireOn = false;
        boolean scoping = scopingStandIn != null ? scopingStandIn : self.isScoping();
        if (!noticed && scoping && seenThroughSpyglass(current, self)) {
            noticed = ++lookedAt >= NOTICE;
        }
        // Seen, then the spyglass lowered: they come.
        if (noticed && !scattering && !scoping) scattering = true;
        // Dawn, or too long a night, sends them all away.
        boolean over = (!called && current.isBrightOutside()) || (!scattering && age > LIFE)
                || (scattering && sinceScatter > WALK);
        if (over) fireOn = fireOn && scattering && !current.isBrightOutside();
        if (noticed) sinceNoticed++;
        if (scattering) sinceScatter++;
        int left = 0;
        for (int i = 0; i < count; i++) {
            if (gone[i]) continue;
            if (scattering) walk(current, self, i);
            else if (noticed) turn(self, i);
            if (fade[i] < 0 && (over || (scattering && Math.hypot(self.getX() - x[i], self.getZ() - z[i]) < REACHED))) {
                fade[i] = 0;
            }
            if (fade[i] >= 0 && ++fade[i] > FADE || !Columns.drawn(current, self, x[i], z[i])) {
                gone[i] = true;
                continue;
            }
            light[i] = Campfire.light(current, x[i], y[i], z[i], fireOn ? fireGlow(i) : 0);
            left++;
        }
        if (left == 0 && (!fireOn || ++emptyFor > EMBERS)) clear();
    }

    /** The player looks at the fire through a spyglass: the middle of their view on it, and nothing loaded in between. */
    private boolean seenThroughSpyglass(ClientLevel current, LocalPlayer self) {
        Vec3 eye = self.getEyePosition(), to = new Vec3(fx, fy + 1.2, fz).subtract(eye);
        // The spyglass shows a tenth of the view: a little of its middle, or a few blocks round them.
        double cone = Math.max(Math.toRadians(2.5), Math.atan2(4, to.length()));
        if (self.getViewVector(1f).dot(to.normalize()) <= Math.cos(cone)) return false;
        return !Columns.loaded(current, fx, fz) || Ghosts.inSight(current, self, eye, fx, fy + 1.2, fz);
    }

    /** Noticed: the head turns to the player first, then the body after it. */
    private void turn(LocalPlayer self, int i) {
        float to = yawTo(x[i], z[i], self.getX(), self.getZ());
        if (sinceNoticed > turnAt[i]) body[i] = Mth.approachDegrees(body[i], to, BODY_TURN);
        // A head turns no farther than a neck lets it from the body.
        float wanted = body[i] + Mth.clamp(Mth.wrapDegrees(to - body[i]), -80f, 80f);
        head[i] = Mth.approachDegrees(head[i], wanted, HEAD_TURN);
    }

    /** Scattering: stood up in turn, then each walks towards the player on a heading of its own, along the ground. */
    private void walk(ClientLevel current, LocalPlayer self, int i) {
        if (sinceScatter < standAt[i]) return;
        if (rise[i] < 1f) {
            rise[i] = Math.min(1f, rise[i] + 0.1f);
            return;
        }
        float to = yawTo(x[i], z[i], self.getX(), self.getZ()) + off[i];
        body[i] = Mth.approachDegrees(body[i], to, WALK_TURN);
        head[i] = body[i];
        double yaw = Math.toRadians(body[i]);
        x[i] -= Math.sin(yaw) * PACE;
        z[i] += Math.cos(yaw) * PACE;
        walked[i] += PACE;
        double ground = loadedGround(current, x[i], z[i]);
        if (Double.isNaN(ground)) ground = onTheWay(x[i], z[i]);
        y[i] += (ground - y[i]) * 0.3;
    }

    /** The block light the fire gives a figure, less the farther it walks from it. */
    private int fireGlow(int i) {
        return Mth.clamp(13 - (int) Math.hypot(x[i] - fx, z[i] - fz) + 2, 0, 13);
    }

    /** The top of a loaded column; NaN where it is not loaded. */
    private static double loadedGround(ClientLevel level, double x, double z) {
        if (!Columns.loaded(level, x, z)) return Double.NaN;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    /** The ground read on the way from the fire to where the player was, at the point nearest to this one. */
    private double onTheWay(double px, double pz) {
        double wx = toX - fx, wz = toZ - fz, length = wx * wx + wz * wz;
        double t = length <= 0 ? 0 : Mth.clamp(((px - fx) * wx + (pz - fz) * wz) / length, 0, 1);
        double f = t * ALONG;
        int k = Math.min(ALONG - 1, (int) f);
        return Mth.lerp(f - k, heights[k], heights[k + 1]);
    }

    /** The yaw, as the game counts it, that faces from one point to another. */
    private static float yawTo(double fromX, double fromZ, double toX, double toZ) {
        return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = false;
        level = null;
    }

    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        Minecraft minecraft = Minecraft.getInstance();
        double reach = DepthFar.of(minecraft) * 0.95;
        if (fireOn) {
            double px = fx - camera.x, py = fy - camera.y, pz = fz - camera.z;
            double distance = Math.sqrt(px * px + py * py + pz * pz);
            GhostRenderer.noteDistance(distance + 20);
            float pull = distance > reach ? (float) (reach / distance) : 1f;
            pose.pushPose();
            pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
            pose.scale(pull, pull, pull);
            Campfire.fire(pose, collector, age + partial, wind, 6f, 0.7f, fireLight);
            pose.popPose();
        }
        for (int i = 0; i < count; i++) {
            if (gone[i]) continue;
            int alpha = fade[i] < 0 ? 255 : Math.round(255 * Mth.clamp(1f - (fade[i] + partial) / FADE, 0f, 1f));
            if (alpha <= 0) continue;
            double px = Mth.lerp(partial, xo[i], x[i]) - camera.x;
            double py = Mth.lerp(partial, yo[i], y[i]) - camera.y;
            double pz = Mth.lerp(partial, zo[i], z[i]) - camera.z;
            double distance = Math.sqrt(px * px + py * py + pz * pz);
            GhostRenderer.noteDistance(distance + 5);
            float pull = distance > reach ? (float) (reach / distance) : 1f;
            boolean seated = rise[i] <= 0f;
            // Seated, the hips low; standing up, rising from there.
            float lower = (SHAPE.leg() - SEAT) * (1f - rise[i]);
            float bodyYaw = Mth.rotLerp(partial, bodyO[i], body[i]);
            float headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, headO[i], head[i]) - bodyYaw);
            boolean walking = scattering && rise[i] >= 1f && sinceScatter >= standAt[i];
            float swing = walking ? (float) Math.sin((walked[i] + PACE * partial) * 2.5) * 28f : 0f;
            // The one standing holds an arm out over the fire, slowly, until they are seen.
            float raise = i == 0 && !noticed ? -18f - 10f * Mth.sin((age + partial) * 0.03f) : 0f;
            pose.pushPose();
            pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
            pose.scale(pull, pull, pull);
            pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
            pose.translate(0f, -lower, 0f);
            RenderType type = alpha < 255 ? Campfire.FADING : Ufo.SOLID;
            int lit = light[i];
            // Yaw turns the other way round from the pose's rotations, the body's as the head's.
            BoxFigure.draw(pose, collector, SHAPE, swing, raise, -headYaw, lit, type, alpha, seated);
            if (!seated) {
                // The robe down to the ankles, over the legs.
                collector.draw(pose, type, (at, out) -> box(at, out, -0.27f, 0.12f, -0.16f, 0.27f, SHAPE.leg() + 0.02f, 0.16f,
                        ROBE, alpha, lit));
            }
            hood(pose, collector, -headYaw, type, alpha, lit);
            pose.popPose();
        }
        if (called) CalledBeam.submit(pose, camera, collector, fx, fy, fz);
    }

    /** The hood round the head, turned with it: on top, at the back and on both sides, the face dark in its opening. */
    private static void hood(PoseStack pose, Sink collector, float headYaw, RenderType type, int alpha, int light) {
        float h = SHAPE.head() / 2, t = 0.06f;
        pose.pushPose();
        pose.translate(0f, SHAPE.leg() + SHAPE.body(), SHAPE.headForward());
        pose.mulPose(Axis.YP.rotationDegrees(headYaw));
        collector.draw(pose, type, (at, out) -> {
            box(at, out, -h - t, 2 * h, -h - t, h + t, 2 * h + t + 0.04f, h + t * 1.5f, ROBE, alpha, light);
            box(at, out, -h - t, -0.02f, -h - t, h + t, 2 * h, -h, ROBE, alpha, light);
            box(at, out, -h - t, -0.02f, -h, -h, 2 * h, h + t * 1.5f, ROBE, alpha, light);
            box(at, out, h, -0.02f, -h, h + t, 2 * h, h + t * 1.5f, ROBE, alpha, light);
            box(at, out, -h * 0.75f, h * 0.15f, h, h * 0.75f, h * 1.7f, h + 0.01f, FACE, alpha, light);
        });
        pose.popPose();
    }

    private static void box(PoseStack.Pose at, VertexConsumer out, float x0, float y0, float z0,
                            float x1, float y1, float z1, int colour, int alpha, int light) {
        Ufo.box(at, out, x0, y0, z0, x1, y1, z1, colour >> 16 & 0xFF, colour >> 8 & 0xFF, colour & 0xFF, alpha, light);
    }
}
