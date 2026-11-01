package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 8: now and then, at night, far off in a village around the player, a pale,
 * see-through villager fades in on a street, walks a few steps, stops and turns to look at
 * them, and fades away; at once if the player comes near. Only on this screen.
 *
 * <p>A village is where a bell stands in the loaded chunks, or where villagers stand together,
 * there or among the remembered mobs farther off. Its way is read around it, in the loaded
 * chunks or the far world ({@link Columns}): on the ground of a street or a garden, never on a
 * roof, flat all along, and in sight where it starts ({@link Sight}). The figure is drawn of
 * glowing, see-through boxes, as the ghosts of {@link Ghosts} are.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class GhostVillager {
    /** Robe and head, pale; eyes, dark. */
    private static final int ROBE = 0xC4D6F2, HEAD = 0xD4E2F8, EYES = 0x26324A;
    /** How much of what is behind it hides, out of 255, at most. */
    private static final int ALPHA = 70;
    /** Blocks per tick it walks; blocks across under which it goes. */
    private static final double PACE = 0.03, NEAR = 32;
    /** How far off it may come, in blocks across; how far from what marks the village. */
    private static final double MIN = 56, MAX = 400, AROUND = 18;
    /** Blocks between two villagers that make a village; villagers that do, at least. */
    private static final double TOGETHER = 40;
    /** Villages looked at, at most; spots tried; stretches its way is read in. */
    private static final int VILLAGES = 24, SPOTS = 48, ALONG = 3;
    /** Ticks it takes to show or to fade; it stands looking at the player; a search may take. */
    private static final int FADE = 50, STARE = 70, SEARCH = 400;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, active, called, noticed;
    private double x, y, z, xo, yo, zo, dirX, dirZ, floor, walked, length;
    /** The ground along its way, where it is not loaded: {@link #ALONG} stretches. */
    private double[] heights = new double[0];
    private float yaw, yawO, phase;
    /** Ticks since it came; the tick it stopped walking; ticks it has been fading away, -1 until then. */
    private int age, stop, leaving = -1, idle, searchAge;

    GhostVillager(EggCalendar calendar) {
        this.calendar = calendar;
    }

    /** On its way or under way: no other easter egg starts meanwhile. */
    public boolean busy() {
        return searching || active;
    }

    /** Where it stands, while it is there. */
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /**
     * Looks for a village around the player and a street in sight in it, and comes there once
     * it is read: by chance, or because the player asked ({@code called}: the nearest villages
     * first; called again, it starts over). False if the easter eggs are off or no village is
     * known around.
     */
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach());
        List<Vec3> villages = far <= MIN ? List.of() : villages(current, self, far);
        if (villages.isEmpty()) {
            if (called) EggLog.nowhere(8);
            return false;
        }
        int steps = 140 + random.nextInt(80);
        double way = steps * PACE;
        // Each spot, two blocks off it on four sides (flat ground, no roof's edge), then its way.
        int per = 5 + ALONG;
        double[] xs = new double[SPOTS * per], zs = new double[xs.length], dirs = new double[SPOTS * 2];
        int[][] around = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        for (int s = 0; s < SPOTS; s++) {
            Vec3 village = villages.get(random.nextInt(villages.size()));
            double angle = random.nextDouble() * Math.PI * 2, off = random.nextDouble() * AROUND;
            double cx = Mth.floor(village.x + Math.cos(angle) * off) + 0.5;
            double cz = Mth.floor(village.z + Math.sin(angle) * off) + 0.5;
            for (int k = 0; k < 5; k++) {
                xs[s * per + k] = cx + around[k][0];
                zs[s * per + k] = cz + around[k][1];
            }
            double heading = random.nextDouble() * Math.PI * 2;
            dirs[2 * s] = -Math.sin(heading);
            dirs[2 * s + 1] = Math.cos(heading);
            for (int k = 1; k <= ALONG; k++) {
                xs[s * per + 4 + k] = cx + dirs[2 * s] * way * k / ALONG;
                zs[s * per + 4 + k] = cz + dirs[2 * s + 1] * way * k / ALONG;
            }
        }
        this.called = called;
        level = current;
        searching = true;
        searchAge = 0;
        int mine = generation.get();
        Executor game = Minecraft.getInstance();
        Vec3 eye = self.getEyePosition();
        Columns.read(current, xs, zs).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            // The ground of a street or a garden all round and all along, near enough the player
            // to be seen, far enough to be far.
            List<Integer> streets = new ArrayList<>();
            for (int s = 0; s < SPOTS; s++) {
                Columns.Column spot = columns[s * per];
                double across = Math.hypot(spot.x() - self.getX(), spot.z() - self.getZ());
                boolean ok = across >= MIN && across <= far;
                for (int k = 0; k < per && ok; k++) {
                    Columns.Column column = columns[s * per + k];
                    Columns.Column before = k <= 5 ? spot : columns[s * per + k - 1];
                    ok = street(column) && Math.abs(column.y() - before.y()) <= 1;
                }
                if (ok) streets.add(s);
            }
            double[] hx = new double[streets.size()], hy = new double[streets.size()], hz = new double[streets.size()];
            for (int i = 0; i < streets.size(); i++) {
                Columns.Column spot = columns[streets.get(i) * per];
                hx[i] = spot.x();
                hy[i] = spot.y() + 1.8;
                hz[i] = spot.z();
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                searching = false;
                List<Integer> clear = new ArrayList<>();
                for (int i = 0; i < seen.length; i++) if (seen[i]) clear.add(streets.get(i));
                Integer s = Sight.any(clear, random);
                if (s == null) {
                    if (called) EggLog.nowhere(8);
                    return;
                }
                double[] ys = new double[ALONG + 1];
                ys[0] = columns[s * per].y();
                for (int k = 1; k <= ALONG; k++) ys[k] = columns[s * per + 4 + k].y();
                begin(columns[s * per], dirs[2 * s], dirs[2 * s + 1], ys, way, steps);
                EggLog.came(8, x, floor, z, self, called);
            }, game);
        }, game);
        return true;
    }

    /**
     * Where villages stand around the player, within {@code far} blocks across: each bell in the
     * loaded chunks, and each villager with another one near, loaded or remembered. The nearest
     * first, {@link #VILLAGES} at most.
     */
    static List<Vec3> villages(ClientLevel level, LocalPlayer self, double far) {
        List<Vec3> found = new ArrayList<>();
        double reach = far + AROUND;
        int radius = Mth.ceil(Math.min(reach, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16) / 16);
        int pcx = Mth.floor(self.getX()) >> 4, pcz = Mth.floor(self.getZ()) >> 4;
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity entity : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(entity instanceof BellBlockEntity)) continue;
                    BlockPos at = entity.getBlockPos();
                    found.add(new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5));
                }
            }
        }
        List<Vec3> villagers = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.getType() == EntityType.VILLAGER) villagers.add(entity.position());
        }
        String dimension = level.dimension().identifier().toString();
        String villager = EntityType.getKey(EntityType.VILLAGER).toString();
        for (MobMemory.Remembered mob : FarPlayerTracker.get().mobs().remembered(dimension)) {
            if (villager.equals(mob.type())) villagers.add(new Vec3(mob.x(), mob.y(), mob.z()));
        }
        for (Vec3 one : villagers) {
            for (Vec3 other : villagers) {
                if (one != other && Math.hypot(one.x - other.x, one.z - other.z) < TOGETHER) {
                    found.add(one);
                    break;
                }
            }
        }
        found.removeIf(at -> Math.hypot(at.x - self.getX(), at.z - self.getZ()) > reach);
        found.sort((a, b) -> Double.compare(a.distanceToSqr(self.position()), b.distanceToSqr(self.position())));
        return found.size() > VILLAGES ? new ArrayList<>(found.subList(0, VILLAGES)) : found;
    }

    /** Known ground to walk on, out of the water: a street, a garden, a field, never a roof. */
    private static boolean street(Columns.Column column) {
        if (!column.dry()) return false;
        BlockState block = column.block();
        // A far world that does not say: what the flat ground around tells is enough.
        return block == null || block.is(BlockTags.DIRT) || block.is(Blocks.DIRT_PATH) || block.is(BlockTags.SAND)
                || block.is(Blocks.GRAVEL) || block.is(BlockTags.SNOW) || block.is(Blocks.FARMLAND);
    }

    private void begin(Columns.Column start, double dx, double dz, double[] ys, double way, int steps) {
        if (!called) calendar.shown(8);
        x = xo = start.x();
        z = zo = start.z();
        heights = ys;
        floor = ys[0];
        y = yo = floor + 0.1;
        dirX = dx;
        dirZ = dz;
        yaw = yawO = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
        length = way;
        stop = steps;
        walked = 0;
        age = 0;
        leaving = -1;
        noticed = false;
        phase = random.nextFloat() * 6.28f;
        active = true;
    }

    /** {@code quiet}: no other easter egg under way, so this one may start by chance. */
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // Rare, and only at night: about once in forty minutes of it, where a village is around.
            if (++idle > 6000 && quiet && !current.isBrightOutside() && random.nextInt(48000) == 0) {
                idle = 0;
                summon(current, self, false);
            }
            return;
        }
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        age++;
        xo = x;
        yo = y;
        zo = z;
        yawO = yaw;
        if (Math.hypot(self.getX() - x, self.getZ() - z) < NEAR || !Columns.drawn(current, self, x, z)) {
            clear();
            return;
        }
        // Looked at once it shows: it stops where it is.
        if (!noticed && age > FADE && age < stop
                && Herobrine.lookedAt(current, self, new Vec3(x, y + 1.6, z))) {
            noticed = true;
            stop = age;
        }
        if (age < stop) {
            double nx = x + dirX * PACE, nz = z + dirZ * PACE, ground = ground(current, nx, nz, walked + PACE);
            if (Double.isNaN(ground)) {
                // Something in the way: it stops there.
                stop = age;
            } else {
                x = nx;
                z = nz;
                walked += PACE;
                floor += (ground - floor) * 0.15;
            }
        } else {
            // Turning, slowly, to the player, then looking at them a while.
            float toPlayer = (float) Math.toDegrees(Math.atan2(-(self.getX() - x), self.getZ() - z));
            yaw = Mth.approachDegrees(yaw, toPlayer, 6f);
            if (leaving < 0 && age > stop + STARE) leaving = 0;
        }
        // Dawn sends it away too.
        if (leaving < 0 && !called && current.isBrightOutside()) leaving = 0;
        if (leaving >= 0 && ++leaving > FADE) {
            clear();
            return;
        }
        // Not quite touching the ground.
        y = floor + 0.1 + 0.05 * Math.sin(age * 0.06 + phase);
    }

    /**
     * The ground to walk on, {@code along} blocks into its way: the loaded column's, NaN where
     * it is water or a step up; where it is not loaded, the one read before it started.
     */
    private double ground(ClientLevel level, double x, double z, double along) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (Columns.loaded(level, x, z)) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            boolean dry = level.getFluidState(new BlockPos(bx, top - 1, bz)).isEmpty();
            return dry && Math.abs(top - floor) <= 1.2 ? top : Double.NaN;
        }
        double f = Mth.clamp(along / length, 0, 1) * ALONG;
        int i = Math.min(ALONG - 1, (int) f);
        return Mth.lerp(f - i, heights[i], heights[i + 1]);
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = false;
        level = null;
    }

    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        // Fading in as it comes, out as it goes.
        float shown = Mth.clamp((age + partial) / FADE, 0f, 1f);
        if (leaving >= 0) shown = Math.min(shown, Mth.clamp(1f - (leaving + partial) / FADE, 0f, 1f));
        int alpha = Math.round(ALPHA * shown);
        if (alpha <= 0) return;
        double px = Mth.lerp(partial, xo, x) - camera.x;
        double py = Mth.lerp(partial, yo, y) - camera.y;
        double pz = Mth.lerp(partial, zo, z) - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        GhostRenderer.noteDistance(distance + 5);
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        // A slow shuffle while it walks, the feet under the robe.
        float stride = age < stop ? (float) Math.sin((walked + PACE * partial) * 5.0) * 0.09f : 0f;
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, yawO, yaw)));
        collector.draw(pose, Ufo.GLOW, (at, out) -> body(at, out, alpha, stride));
        // The arms folded in front, as a villager holds them.
        pose.translate(0f, 1.3125f, 0.11f);
        pose.mulPose(Axis.XP.rotationDegrees(-43f));
        collector.draw(pose, Ufo.GLOW, (at, out) -> arms(at, out, alpha));
        pose.popPose();
        if (called) CalledBeam.submit(pose, camera, collector, px + camera.x, py + camera.y, pz + camera.z);
    }

    /** Feet, the long robe, the head and its big nose, two dark eyes; facing +z, origin at its feet. */
    private static void body(PoseStack.Pose at, VertexConsumer out, int alpha, float stride) {
        int light = Ufo.BRIGHT;
        box(at, out, -0.24f, 0f, -0.1f + stride, -0.01f, 0.36f, 0.12f + stride, ROBE, alpha, light);
        box(at, out, 0.01f, 0f, -0.1f - stride, 0.24f, 0.36f, 0.12f - stride, ROBE, alpha, light);
        box(at, out, -0.25f, 0.36f, -0.1875f, 0.25f, 1.5f, 0.1875f, ROBE, alpha, light);
        box(at, out, -0.25f, 1.5f, -0.25f, 0.25f, 2.125f, 0.25f, HEAD, alpha, light);
        box(at, out, -0.0625f, 1.5f, 0.25f, 0.0625f, 1.75f, 0.375f, HEAD, alpha, light);
        int eyes = Math.min(255, alpha * 3);
        box(at, out, -0.1875f, 1.78f, 0.25f, -0.0625f, 1.85f, 0.26f, EYES, eyes, light);
        box(at, out, 0.0625f, 1.78f, 0.25f, 0.1875f, 1.85f, 0.26f, EYES, eyes, light);
    }

    /** The folded arms, around their joint at the shoulders: hands across, an arm down each side. */
    private static void arms(PoseStack.Pose at, VertexConsumer out, int alpha) {
        int light = Ufo.BRIGHT;
        box(at, out, -0.25f, -0.375f, -0.125f, 0.25f, -0.125f, 0.125f, ROBE, alpha, light);
        box(at, out, -0.5f, -0.375f, -0.125f, -0.25f, 0.125f, 0.125f, ROBE, alpha, light);
        box(at, out, 0.25f, -0.375f, -0.125f, 0.5f, 0.125f, 0.125f, ROBE, alpha, light);
    }

    private static void box(PoseStack.Pose at, VertexConsumer out, float x0, float y0, float z0,
                            float x1, float y1, float z1, int colour, int alpha, int light) {
        Ufo.box(at, out, x0, y0, z0, x1, y1, z1, colour >> 16 & 0xFF, colour >> 8 & 0xFF, colour & 0xFF, alpha, light);
    }
}
