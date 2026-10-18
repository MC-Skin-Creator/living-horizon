package fr.clixmods.livinghorizon.track;

import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A player who logged off, or what they ride, away from the camera: no life of its own any
 * more, one of the {@link MobAnimations} played in a loop around the spot where they logged
 * off, as remembered mobs play theirs. The ground around the spot is read once
 * ({@link MobPaths}), again later if some of it was missing; then nothing is decided, where
 * it is and which way it faces come from the animation and the clock. Each starts its loop
 * at its own moment, drawn from its seed.
 *
 * <p>Coming from wherever its life near the camera left it, it walks back to its loop in a
 * straight line, never jumping.
 */
final class RestingLoop {
    /** Ticks before a ground read with columns missing is read again. */
    private static final int READ_AGAIN = 600;

    private final long seed;
    /** The spot it loops around, and the way it faced there. */
    private final double homeX, homeY, homeZ;
    private final float homeYaw;
    /** Reads the ground around the spot and chooses the animation. */
    private final Supplier<CompletableFuture<MobPaths.Choice>> planner;
    /** Blocks a tick it walks at, at most. */
    private final double pace;

    private MobPaths.@Nullable Choice choice;
    private @Nullable CompletableFuture<MobPaths.Choice> pending;
    private int started, readAgain;

    double x, y, z;
    float body;
    /** Walking this tick, or stopped in a pause the animation grazes in (a seat, for a player). */
    boolean walking, pausing;

    RestingLoop(long seed, double homeX, double homeY, double homeZ, float homeYaw, double pace,
                Supplier<CompletableFuture<MobPaths.Choice>> planner) {
        this.seed = seed;
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        this.homeYaw = homeYaw;
        this.pace = pace;
        this.planner = planner;
        x = homeX;
        y = homeY;
        z = homeZ;
        body = homeYaw;
    }

    /** Takes over from wherever the figure is now. */
    void from(double x, double y, double z, float body) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.body = body;
    }

    /**
     * One tick of the loop; how far it moved. {@code free} is for a figure that flies or
     * floats: it keeps the spot's height instead of following the ground.
     */
    double tick(int clock, boolean free) {
        if (pending == null && (choice == null || !choice.complete() && clock >= readAgain)) pending = planner.get();
        if (pending != null && pending.isDone()) {
            MobPaths.Choice read = pending.getNow(null);
            pending = null;
            readAgain = clock + READ_AGAIN;
            if (read != null) {
                if (choice == null || !read.same(choice)) started = clock;
                choice = read;
            }
        }

        // Where the animation has it now.
        double tx = homeX, tz = homeZ;
        float facing = homeYaw;
        boolean moving = false, grazing = false;
        MobAnimations.Animation animation = choice == null ? null : choice.animation();
        int t = clock - started - (int) Math.floorMod(seed, 200L);
        if (animation != null && t >= 0) {
            MobAnimations.Pose pose = animation.sample(t);
            tx += MobAnimations.worldX(pose.x(), pose.z(), choice.angle(), choice.mirror());
            tz += MobAnimations.worldZ(pose.x(), pose.z(), choice.angle(), choice.mirror());
            facing = MobAnimations.yaw(pose.heading(), choice.angle(), choice.mirror());
            moving = pose.walking();
            grazing = pose.grazing();
        }

        // On it, or on its way back to it in a straight line.
        double dx = tx - x, dz = tz - z, d = Math.hypot(dx, dz);
        double reach = Math.max(pace * 1.8, Math.min(0.5, d / 40));
        double step = Math.min(d, reach);
        double fromX = x, fromZ = z;
        if (d > 1e-6) {
            x += dx / d * step;
            z += dz / d * step;
        }
        double ground = free || choice == null ? Double.NaN : choice.ground().height(x, z);
        if (Double.isNaN(ground)) ground = homeY;
        y += (ground - y) * 0.4;
        if (Math.abs(ground - y) < 0.01) y = ground;
        double moved = Math.hypot(x - fromX, z - fromZ);
        boolean catching = d > reach;
        float turnTo = catching ? (float) (Mth.atan2(z - fromZ, x - fromX) * Mth.RAD_TO_DEG) - 90f : facing;
        body = Mth.approachDegrees(body, turnTo, moving || catching ? 18f : 4f);
        walking = moving || moved > 0.02;
        pausing = grazing && !walking;
        return moved;
    }

    /** What it plays, for the debug tools. */
    String state() {
        MobAnimations.Animation animation = choice == null ? null : choice.animation();
        return pending != null ? "reading the ground" : animation == null ? "standing" : animation.toString();
    }
}
