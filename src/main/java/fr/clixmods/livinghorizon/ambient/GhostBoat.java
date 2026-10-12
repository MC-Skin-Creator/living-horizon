package fr.clixmods.livinghorizon.ambient;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Easter egg 7: some night, out on open water, an empty rowing boat drifts, pale and
 * see-through, a ghost sitting in it, see-through as well; both are gone when the player
 * comes near. Only on this screen.
 *
 * <p>The water is found in a fan of columns ahead, loaded or in the far world
 * ({@link Columns}), each with water all round. The boat is drawn of glowing translucent
 * boxes; the ghost is a player of no one, seated, drawn see-through as {@link Ghosts} are.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class GhostBoat {
    /** Blocks per tick it drifts; blocks across under which it goes. */
    private static final double DRIFT = 0.012, NEAR = 28;
    /** Ticks it drifts at most. */
    private static final int LIFE = 4800;
    /** Height of the bench above the water, where the ghost sits. */
    private static final double SEAT = 0.3;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    private final AtomicReference<Columns.Column @Nullable []> found = new AtomicReference<>();
    private @Nullable RemotePlayer rider;
    private @Nullable ClientLevel level;
    private boolean searching, active, called;
    private double x, y, z, xo, yo, zo, surface, dirX, dirZ;
    private float yaw, yawO, turn;
    private int age, idle;

    GhostBoat(EggCalendar calendar) {
        this.calendar = calendar;
    }

    /** On its way or under way: no other easter egg starts meanwhile. */
    public boolean busy() {
        return searching || active;
    }

    /** Where it floats, while it is there. */
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /** The ghost in the boat, to draw seated. */
    public @Nullable RemotePlayer rider() {
        return active ? rider : null;
    }

    /**
     * Looks for open water ahead, and sets the boat drifting there once it is read: by
     * chance, or because the player asked ({@code called}: nearer). False if the easter eggs
     * are off.
     */
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy()) return true;
        int bearings = 7, rings = 6;
        double look = Math.toRadians(self.getYRot());
        // Each spot and four around it, four blocks away: water all round is open water.
        double[] xs = new double[bearings * rings * 5], zs = new double[xs.length];
        int[][] around = {{0, 0}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}};
        for (int b = 0, i = 0; b < bearings; b++) {
            double bearing = look + (b - (bearings - 1) / 2.0) * 0.13;
            for (int r = 0; r < rings; r++) {
                double distance = called ? 30 + r * 15 : 50 + r * 18;
                double cx = Mth.floor(self.getX() - Math.sin(bearing) * distance) + 0.5;
                double cz = Mth.floor(self.getZ() + Math.cos(bearing) * distance) + 0.5;
                for (int[] d : around) {
                    xs[i] = cx + d[0];
                    zs[i++] = cz + d[1];
                }
            }
        }
        this.called = called;
        level = current;
        found.set(null);
        Columns.read(current, xs, zs).thenAccept(found::set);
        searching = true;
        return true;
    }

    /** {@code quiet}: no other easter egg under way, so this one may start by chance. */
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // Rare, and only at night: about once in an hour and twenty minutes of it, where there is water.
            if (++idle > 6000 && quiet && !current.isBrightOutside() && random.nextInt(96000) == 0) {
                idle = 0;
                summon(current, self, false);
            }
            return;
        }
        if (searching) {
            Columns.Column[] columns = found.getAndSet(null);
            if (columns == null) return;
            searching = false;
            List<Columns.Column> open = new ArrayList<>();
            for (int i = 0; i < columns.length; i += 5) {
                boolean water = true;
                for (int k = 0; k < 5; k++) water &= columns[i + k].known() && columns[i + k].water();
                if (water) open.add(columns[i]);
            }
            if (open.isEmpty()) return;
            begin(current, open.get(random.nextInt(open.size())));
            return;
        }
        age++;
        xo = x;
        yo = y;
        zo = z;
        yawO = yaw;
        // Drifting, turning slowly, rising and falling on the swell; stopped where the water ends.
        double nx = x + dirX * DRIFT, nz = z + dirZ * DRIFT;
        if (water(current, nx, nz)) {
            x = nx;
            z = nz;
        }
        yaw += turn;
        y = surface + 0.04 * Math.sin(age * 0.05);
        RemotePlayer ghost = rider;
        if (ghost != null) {
            ghost.setOldPosAndRot();
            ghost.yBodyRotO = ghost.yBodyRot;
            ghost.yHeadRotO = ghost.yHeadRot;
            ghost.setPos(x, y + SEAT, z);
            ghost.setYRot(yaw);
            ghost.yBodyRot = yaw;
            // Looking out over the water, this way and that.
            ghost.yHeadRot = yaw + 35f * (float) Math.sin(age * 0.01);
            Puppets.walk(ghost, 0f, 0.4f, 1f);
            ghost.tickCount++;
        }
        if (age > LIFE || Math.hypot(self.getX() - x, self.getZ() - z) < NEAR
                || (!called && current.isBrightOutside())) {
            clear();
        }
    }

    private void begin(ClientLevel current, Columns.Column spot) {
        if (!called) calendar.shown(7);
        x = xo = spot.x();
        z = zo = spot.z();
        // The top of the water block, a little under the column's top.
        surface = y = yo = spot.y() - 0.1;
        double drift = random.nextDouble() * Math.PI * 2;
        dirX = Math.sin(drift);
        dirZ = Math.cos(drift);
        yaw = yawO = random.nextFloat() * 360f;
        turn = (random.nextBoolean() ? 1 : -1) * (0.05f + random.nextFloat() * 0.1f);
        RemotePlayer ghost = Puppets.player(current, new GameProfile(UUID.randomUUID(), ""));
        ghost.setInvisible(true);
        ghost.setPos(x, y + SEAT, z);
        ghost.setYRot(yaw);
        ghost.yBodyRot = ghost.yBodyRotO = ghost.yHeadRot = ghost.yHeadRotO = yaw;
        ghost.setOldPosAndRot();
        Ghosts.seeThrough(ghost, true);
        rider = ghost;
        age = 0;
        active = true;
    }

    private void clear() {
        if (rider != null) Ghosts.seeThrough(rider, false);
        rider = null;
        active = searching = false;
        level = null;
    }

    /** Water on top of this column; true where the chunk is not loaded, as nothing says otherwise. */
    private static boolean water(ClientLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return true;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return !level.getFluidState(new BlockPos(bx, top - 1, bz)).isEmpty();
    }

    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        double px = Mth.lerp(partial, xo, x) - camera.x;
        double py = Mth.lerp(partial, yo, y) - camera.y;
        double pz = Mth.lerp(partial, zo, z) - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        GhostRenderer.noteDistance(distance + 5);
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        float rock = (float) Math.sin((age + partial) * 0.07) * 4f;
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, yawO, yaw)));
        pose.mulPose(Axis.ZP.rotationDegrees(rock));
        collector.draw(pose, Ufo.GLOW, GhostBoat::hull);
        pose.popPose();
    }

    /** A rowing boat, pale and see-through: its floor, its four sides, a bench across. */
    private static void hull(PoseStack.Pose at, VertexConsumer out) {
        int r = 205, g = 228, b = 255, a = 70, light = Ufo.BRIGHT;
        // Half its width and length, the height of its sides, the thickness of a plank.
        float w = 0.62f, l = 0.88f, h = 0.42f, t = 0.08f;
        Ufo.box(at, out, -w, -0.05f, -l, w, t - 0.05f, l, r, g, b, a, light);
        Ufo.box(at, out, -w, 0, -l, -w + t, h, l, r, g, b, a, light);
        Ufo.box(at, out, w - t, 0, -l, w, h, l, r, g, b, a, light);
        Ufo.box(at, out, -w + t, 0, -l, w - t, h, -l + t, r, g, b, a, light);
        Ufo.box(at, out, -w + t, 0, l - t, w - t, h, l, r, g, b, a, light);
        Ufo.box(at, out, -w + t, (float) SEAT - 0.08f, -0.12f, w - t, (float) SEAT, 0.12f, r, g, b, a, light);
    }
}
