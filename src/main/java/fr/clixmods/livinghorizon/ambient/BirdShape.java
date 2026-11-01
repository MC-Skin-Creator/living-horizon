package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 18: now and then, on a clear day, a flock flies in from the side far off, gathers
 * in the sky in front of the player and, for a few seconds, its birds hang still in an eight by
 * eight grid that faces them, a bird on each dark pixel of a Creeper's face and all round its
 * edge; then they scatter and fly away. The birds flap all along.
 *
 * <p>The grid stands upright, across the player's line of sight, its pixels some one degree of
 * the view apart, so that the face reads the same however far it is. Its place is a column of
 * known ground, read in the loaded chunks or the far world ({@link Columns}), with the face in
 * sight ({@link Sight}). Gathered, each bird hovers nose up, its belly and spread wings to the
 * player, a pixel of the face. The birds are the 3D models of the flocks
 * ({@link AmbientRenderer#model}, {@link BirdModels}), drawn by this class.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class BirdShape implements Egg {
    private static final int NUMBER = 18;
    /** The face, top row first: X a bird, . the sky. Its edge is drawn as well. */
    private static final String[] FACE = {
            "........",
            ".XX..XX.",
            ".XX..XX.",
            "...XX...",
            "..XXXX..",
            "..XXXX..",
            "..X..X..",
            "........",
    };
    private static final int GRID = 8;
    /** Blocks out the face hangs, at least and at most; under which it has nowhere to come. */
    private static final double MIN = 150, MAX = 300, LEAST = 60;
    /** Blocks between two pixels of the face per block out: about a degree of the view. */
    private static final double SPACING = 0.0175;
    /** A bird's wingspan, as a share of the spacing. */
    private static final double SPAN = 0.9;
    /** How far up the face hangs, as a share of how far out: some ten degrees over the eye. */
    private static final double RISE = 0.16;
    /** Blocks between the bottom of the face and the ground under it, at least. */
    private static final double CLEAR = 12;
    /** Blocks to the side the flock comes from, at least and more by chance; blocks it spreads over there. */
    private static final double FROM = 70, FROM_MORE = 30, SPREAD = 8;
    /** Ticks each bird takes to its place, at least and more by chance: they come in one after the other. */
    private static final int FLIGHT = 140, FLIGHT_MORE = 70;
    /** Ticks the face holds; ticks the scattered birds take to go. */
    private static final int HOLD = 70, LEAVE = 120;
    /** Degrees a gathered bird holds its nose up; share of the way after which it turns to the player. */
    private static final float HOVER = 60f;
    private static final double TURNING = 0.75;
    /** Blocks across under which they scatter at once; ticks a search may take. */
    private static final double NEAR = 40;
    private static final int SEARCH = 400;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private final List<Ambience.Flyer> birds = new ArrayList<>();
    private @Nullable ClientLevel level;
    private boolean searching, active, called;
    /** The face's middle; its right, seen from the player; the way to the player. */
    private double cx, cy, cz, rightX, rightZ, toX, toZ, spacing;
    /** Per bird: its place in the face, where it set off from, how its way bows, how it leaves. */
    private double[] slotX = new double[0], slotY = new double[0], slotZ = new double[0];
    private double[] fromX = new double[0], fromY = new double[0], fromZ = new double[0], bow = new double[0];
    private double[] awayX = new double[0], awayY = new double[0], awayZ = new double[0];
    private float[] pitch = new float[0], pitchO = new float[0];
    private int[] flight = new int[0];
    private float facing;
    /** Ticks since they came; the tick all are in place; the tick they scatter, -1 until set. */
    private int age, formedAt, scatterAt, idle, searchAge;

    BirdShape(EggCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public int number() {
        return NUMBER;
    }

    @Override
    public boolean busy() {
        return searching || active;
    }

    /** The face's middle, while the flock is there. */
    @Override
    public @Nullable Vec3 position() {
        return active ? new Vec3(cx, cy, cz) : null;
    }

    /** Every bird in its place, the face held. For the game tests. */
    public boolean formed() {
        return active && age >= formedAt && (scatterAt < 0 || age < scatterAt);
    }

    /** Blocks the farthest bird is from its place in the face; 0 while there is none. For the game tests. */
    public double offSlots() {
        double most = 0;
        if (!active) return most;
        for (int i = 0; i < birds.size(); i++) {
            Ambience.Flyer bird = birds.get(i);
            most = Math.max(most, Math.sqrt(Mth.square(bird.x - slotX[i]) + Mth.square(bird.y - slotY[i])
                    + Mth.square(bird.z - slotZ[i])));
        }
        return most;
    }

    /**
     * Picks a place out in front of the player, reads the ground under the face and whether
     * it is in sight, and comes once it is read: by chance, or because the player asked
     * ({@code called}: straight ahead, at any hour, whatever the weather).
     */
    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        double out = Math.min(MIN + random.nextDouble() * (MAX - MIN), Sight.reach() * 0.8);
        if (out < LEAST) {
            if (called) EggLog.nowhere(NUMBER);
            return false;
        }
        // Where the player looks; by chance, somewhere near it.
        double heading = Math.toRadians(self.getYRot()) + (called ? 0 : (random.nextDouble() - 0.5) * Math.toRadians(100));
        double lookX = -Math.sin(heading), lookZ = Math.cos(heading);
        double middleX = self.getX() + lookX * out, middleZ = self.getZ() + lookZ * out;
        // The player's right, looking out there.
        double sideX = -lookZ, sideZ = lookX, step = out * SPACING, half = step * GRID / 2;
        double[] xs = {middleX, middleX - sideX * half, middleX + sideX * half};
        double[] zs = {middleZ, middleZ - sideZ * half, middleZ + sideZ * half};
        this.called = called;
        level = current;
        searching = true;
        searchAge = 0;
        int mine = generation.get();
        Executor game = Minecraft.getInstance();
        Vec3 eye = self.getEyePosition();
        Columns.read(current, xs, zs).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            if (!columns[0].known()) {
                give(called);
                return;
            }
            double ground = Double.NEGATIVE_INFINITY;
            for (Columns.Column column : columns) if (column.known()) ground = Math.max(ground, column.y());
            double middleY = Math.max(eye.y + out * RISE, ground + half + CLEAR);
            // The middle and the four corners of the face.
            double[] hx = new double[5], hy = new double[5], hz = new double[5];
            for (int k = 0; k < 5; k++) {
                double across = k == 0 ? 0 : (k % 2 == 0 ? 1 : -1) * half * 0.9;
                double up = k == 0 ? 0 : (k < 3 ? 1 : -1) * half * 0.9;
                hx[k] = middleX + sideX * across;
                hy[k] = middleY + up;
                hz[k] = middleZ + sideZ * across;
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                int corners = 0;
                for (int k = 1; k < 5; k++) if (seen[k]) corners++;
                if (!seen[0] || corners < 3) {
                    give(called);
                    return;
                }
                searching = false;
                begin(self, middleX, middleY, middleZ, sideX, sideZ, step);
                EggLog.came(NUMBER, cx, cy, cz, self, called);
            }, game);
        }, game);
        return true;
    }

    /** Nowhere to come: the search ends. */
    private void give(boolean called) {
        searching = false;
        level = null;
        if (called) EggLog.nowhere(NUMBER);
    }

    private void begin(LocalPlayer self, double x, double y, double z, double sideX, double sideZ, double step) {
        if (!called) calendar.shown(NUMBER);
        cx = x;
        cy = y;
        cz = z;
        rightX = sideX;
        rightZ = sideZ;
        spacing = step;
        double across = Math.hypot(self.getX() - x, self.getZ() - z);
        toX = (self.getX() - x) / across;
        toZ = (self.getZ() - z) / across;
        facing = (float) Math.toDegrees(Math.atan2(-toX, toZ));
        // A bird on each dark pixel and each pixel of the edge.
        List<int[]> cells = new ArrayList<>();
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                boolean edge = row == 0 || col == 0 || row == GRID - 1 || col == GRID - 1;
                if (edge || FACE[row].charAt(col) == 'X') cells.add(new int[]{row, col});
            }
        }
        int count = cells.size();
        slotX = new double[count];
        slotY = new double[count];
        slotZ = new double[count];
        fromX = new double[count];
        fromY = new double[count];
        fromZ = new double[count];
        bow = new double[count];
        awayX = new double[count];
        awayY = new double[count];
        awayZ = new double[count];
        pitch = new float[count];
        pitchO = new float[count];
        flight = new int[count];
        birds.clear();
        // The flock comes from one side, a little lower or higher and from farther out.
        double side = random.nextBoolean() ? 1 : -1, off = FROM + random.nextDouble() * FROM_MORE;
        double startX = x + rightX * side * off - toX * random.nextDouble() * 20;
        double startY = y + (random.nextDouble() - 0.4) * 20;
        double startZ = z + rightZ * side * off - toZ * random.nextDouble() * 20;
        formedAt = 0;
        for (int i = 0; i < count; i++) {
            int[] cell = cells.get(i);
            double right = (cell[1] - (GRID - 1) / 2.0) * step, up = ((GRID - 1) / 2.0 - cell[0]) * step;
            slotX[i] = x + rightX * right;
            slotY[i] = y + up;
            slotZ[i] = z + rightZ * right;
            fromX[i] = startX + random.nextGaussian() * SPREAD;
            fromY[i] = startY + random.nextGaussian() * SPREAD * 0.5;
            fromZ[i] = startZ + random.nextGaussian() * SPREAD;
            bow[i] = (random.nextDouble() - 0.5) * 2 * Math.max(6, step * 4);
            flight[i] = FLIGHT + random.nextInt(FLIGHT_MORE + 1);
            formedAt = Math.max(formedAt, flight[i]);
            Ambience.Flyer bird = new Ambience.Flyer(Ambience.Kind.SILHOUETTE, Ambience.Species.GENERIC,
                    (float) (step * SPAN * (0.92 + random.nextDouble() * 0.16)), 40, null, 0.8f + random.nextFloat() * 0.3f);
            bird.beat = random.nextFloat() * 6.28f;
            bird.place(fromX[i], fromY[i], fromZ[i]);
            bird.yaw = bird.yawO = (float) Math.toDegrees(Math.atan2(-(slotX[i] - fromX[i]), slotZ[i] - fromZ[i]));
            birds.add(bird);
        }
        scatterAt = -1;
        age = 0;
        active = true;
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // Rare, and only on a clear day: about once in an hour of it.
            if (++idle > 6000 && quiet && current.isBrightOutside() && !current.isRaining() && random.nextInt(72000) == 0) {
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
        // Held its time; or the player came near, or it got dark or wet, uncalled: they scatter.
        if (scatterAt < 0 && (age >= formedAt + HOLD || Math.hypot(self.getX() - cx, self.getZ() - cz) < NEAR
                || !called && (!current.isBrightOutside() || current.isRaining()))) {
            scatter();
        }
        for (int i = 0; i < birds.size(); i++) {
            Ambience.Flyer bird = birds.get(i);
            pitchO[i] = pitch[i];
            if (scatterAt >= 0) leave(bird, i);
            else gather(bird, i);
        }
        if (scatterAt >= 0 && age - scatterAt > LEAVE) clear();
    }

    /** On its way to its place, along a bowed path, turning to the player near the end; then there, hovering. */
    private void gather(Ambience.Flyer bird, int i) {
        double u = Mth.clamp(age / (double) flight[i], 0, 1);
        double e = u * u * (3 - 2 * u), arc = Math.sin(Math.PI * e) * bow[i];
        double hover = u >= 1 ? 0.12 * Math.sin(age * 0.15 + i) : 0;
        // Bowing up and out of the face's plane, so that the flock swirls in rather than slides.
        double nx = Mth.lerp(e, fromX[i], slotX[i]) - toX * arc * 0.6;
        double ny = Mth.lerp(e, fromY[i], slotY[i]) + arc * 0.5 + hover;
        double nz = Mth.lerp(e, fromZ[i], slotZ[i]) - toZ * arc * 0.6;
        double dx = nx - bird.x, dz = nz - bird.z;
        float moving = Math.hypot(dx, dz) > 1e-3 ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : bird.yaw;
        double turn = Mth.clamp((u - TURNING) / (1 - TURNING), 0, 1);
        float yaw = Mth.approachDegrees(bird.yaw, Mth.rotLerp((float) turn, moving, facing), 12f);
        pitch[i] = (float) (HOVER * turn);
        bird.moveTo(nx, ny, nz, yaw, 0f);
    }

    /** Off, away from the middle of the face and from the player, faster each tick, then gone. */
    private void leave(Ambience.Flyer bird, int i) {
        int since = age - scatterAt;
        awayX[i] *= 1.03;
        awayY[i] *= 1.01;
        awayZ[i] *= 1.03;
        double nx = bird.x + awayX[i], ny = bird.y + awayY[i], nz = bird.z + awayZ[i];
        float yaw = Mth.approachDegrees(bird.yaw, (float) Math.toDegrees(Math.atan2(-awayX[i], awayZ[i])), 15f);
        pitch[i] *= 0.85f;
        bird.moveTo(nx, ny, nz, yaw, 0f);
        bird.scale = since < 40 ? 1f : Math.max(0f, 1f - (since - 40) / 80f);
    }

    /** Every bird takes off its own way: out of the face, away from the player, up. */
    private void scatter() {
        scatterAt = age;
        for (int i = 0; i < birds.size(); i++) {
            Ambience.Flyer bird = birds.get(i);
            double outX = bird.x - cx, outY = bird.y - cy, outZ = bird.z - cz;
            double length = Math.sqrt(outX * outX + outY * outY + outZ * outZ);
            if (length < 0.5) {
                double angle = random.nextDouble() * Math.PI * 2;
                outX = rightX * Math.cos(angle);
                outY = Math.sin(angle);
                outZ = rightZ * Math.cos(angle);
                length = 1;
            }
            double speed = 0.2 + random.nextDouble() * 0.12;
            awayX[i] = (outX / length - toX * 0.6 + random.nextGaussian() * 0.2) * speed;
            awayY[i] = (outY / length * 0.5 + 0.35 + random.nextDouble() * 0.2) * speed;
            awayZ[i] = (outZ / length - toZ * 0.6 + random.nextGaussian() * 0.2) * speed;
        }
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = false;
        birds.clear();
        level = null;
    }

    /** Each bird as the flocks' 3D model, pulled in past the far plane like the flocks, a little smaller in proportion. */
    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        for (int i = 0; i < birds.size(); i++) {
            Ambience.Flyer bird = birds.get(i);
            if (bird.scale <= 0f) continue;
            double px = Mth.lerp(partial, bird.xo, bird.x) - camera.x;
            double py = Mth.lerp(partial, bird.yo, bird.y) - camera.y;
            double pz = Mth.lerp(partial, bird.zo, bird.z) - camera.z;
            double distance = Math.sqrt(px * px + py * py + pz * pz);
            GhostRenderer.noteDistance(distance + bird.span);
            float pull = distance > reach ? (float) (reach / distance) : 1f;
            float wing = Mth.lerp(partial, bird.wingO, bird.wing) * Mth.DEG_TO_RAD;
            float tilt = Mth.lerp(partial, pitchO[i], pitch[i]);
            float tone = 0.9f + 0.2f * ((i * 37) & 15) / 15f;
            pose.pushPose();
            pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
            // Turned, then the nose up: the model's own yaw is left at nought.
            pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, bird.yawO, bird.yaw)));
            pose.mulPose(Axis.XP.rotationDegrees(-tilt));
            AmbientRenderer.model(pose, collector, BirdModels.of(Ambience.Species.GENERIC), bird.span * bird.scale * pull,
                    false, 0f, 0f, wing, tone, Ufo.LIT);
            pose.popPose();
        }
        if (called) CalledBeam.submit(pose, camera, collector, cx, cy + spacing * GRID / 2, cz);
    }
}
