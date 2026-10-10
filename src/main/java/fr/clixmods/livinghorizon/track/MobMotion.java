package fr.clixmods.livinghorizon.track;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import org.jspecify.annotations.Nullable;

/**
 * Plays a remembered mob's animation in a loop: the one {@link MobPaths} chose from the
 * blocks around it, out of {@link MobAnimations}. Nothing is decided here; where the mob
 * is, which way it faces, whether it walks or grazes all come from the animation and the
 * clock. Every mob starts its loop at its own moment, drawn from its UUID, so a herd
 * does not move as one.
 *
 * <p>The one thing added to the animation: the mob never jumps. When where it should be
 * changes at once - the data pack moved it, or the real mob is sent again and the copy
 * hands over to it - it walks there in a straight line instead. And as the player comes
 * near enough for the real mob to be sent, the copy is called back to the spot where the
 * real one was last seen, so that the hand-over has little left to walk.
 */
final class MobMotion {
    /** Degrees a body turns in a tick, at most. */
    private static final float TURN = 18f;
    /** No room to walk: a slow turn on the spot every so many ticks. */
    private static final int TURN_ROUND = 300;

    private final long seed;
    private double x, y, z;
    private float body, head, pitch;
    private boolean placed;
    private MobPaths.@Nullable Choice playing;
    private int started;
    private int following;

    MobMotion(long seed) {
        this.seed = seed;
    }

    /** Where the puppet is drawn: where the animation took it, not where it was remembered. */
    boolean placed() {
        return placed;
    }

    double x() {
        return x;
    }

    double y() {
        return y;
    }

    double z() {
        return z;
    }

    private void place(MobMemory.Remembered mob) {
        x = mob.x;
        y = mob.y;
        z = mob.z;
        body = mob.yaw;
        head = Float.isNaN(mob.head) ? mob.yaw : mob.head;
        placed = true;
    }

    /**
     * How much a mob is called back to its spot, from 0 (far: it plays its animation) to 1
     * (about to be handed over to the real one: it stands where the real one was last seen).
     * Full from a few blocks inside the distance the server starts sending it, nil forty beyond.
     */
    static float home(double distance, double handover) {
        double t = Mth.clamp((handover + 48 - distance) / 40, 0, 1);
        return (float) (t * t * (3 - 2 * t));
    }

    /** How far the head is turned from the body at a tick: a glance, a pause, another glance. */
    static float glance(long seed, int tick) {
        int k = Math.floorDiv(tick, 70);
        float u = Mth.clamp((tick - k * 70) / 20f, 0f, 1f);
        u = u * u * (3 - 2 * u);
        return Mth.lerp(u, look(seed, k - 1), look(seed, k));
    }

    private static float look(long seed, int k) {
        return (Math.floorMod(mix(seed, k), 101L) - 50) * 0.9f;
    }

    /** A well-mixed number from a mob's seed and a counter: the same inputs, the same number. */
    static long mix(long seed, long counter) {
        long z = seed + counter * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * One tick of its animation. {@code choice} is null until the ground around it is
     * read; {@code roam} is off for the mobs shown standing still; {@code tiny} means too
     * small on screen for its legs and idle movements to show; {@code home} is how much it
     * is called back to its spot ({@link #home}).
     */
    void tick(LivingEntity puppet, MobMemory.Remembered mob, MobPaths.@Nullable Choice choice, int clock, boolean roam,
              boolean tiny, float home) {
        if (!placed) place(mob);
        following = 0;
        if (choice != playing) {
            playing = choice;
            started = clock;
        }
        int offset = (int) Math.floorMod(seed >> 17, 400L);

        // Where the animation has it now.
        double tx = mob.x, tz = mob.z;
        float facing = mob.yaw;
        boolean walking = false, grazing = false;
        MobAnimations.Animation animation = roam && choice != null ? choice.animation() : null;
        int t = clock - started - (int) Math.floorMod(seed, 200L);
        if (animation != null && t >= 0) {
            MobAnimations.Pose pose = animation.sample(t);
            tx += MobAnimations.worldX(pose.x(), pose.z(), choice.angle(), choice.mirror());
            tz += MobAnimations.worldZ(pose.x(), pose.z(), choice.angle(), choice.mirror());
            facing = MobAnimations.yaw(pose.heading(), choice.angle(), choice.mirror());
            walking = pose.walking();
            grazing = pose.grazing() && puppet instanceof Animal;
        } else if (roam && choice != null) {
            // Nowhere to walk: a slow turn on the spot now and then, a different one each time.
            int round = Math.floorDiv(clock + offset, TURN_ROUND);
            facing = mob.yaw + (Math.floorMod(mix(seed, round), 5L) - 2) * 35f;
        }
        if (home > 0) {
            // Close to the hand-over: back towards the spot where the real one was last seen.
            tx = Mth.lerp(home, tx, mob.x);
            tz = Mth.lerp(home, tz, mob.z);
            if (home >= 1) facing = mob.yaw;
            grazing &= home < 0.01f;
        }

        // On it, or on the way to it in a straight line when it is not one tick away.
        double dx = tx - x, dz = tz - z, d = Math.hypot(dx, dz);
        double reach = Math.max(0.2, Math.min(0.5, d / 40));
        double step = Math.min(d, reach);
        double fromX = x, fromZ = z;
        boolean catching = d > reach;
        if (d > 1e-6) {
            x += dx / d * step;
            z += dz / d * step;
        }
        double ground = choice == null ? Double.NaN : choice.ground().height(x, z);
        if (Double.isNaN(ground) || catching) ground = d > 1e-6 ? y + (mob.y - y) * Math.min(1, step / d) : mob.y;
        // Up a step in a few ticks, like a hop; down the same.
        y += (ground - y) * 0.4;
        if (Math.abs(ground - y) < 0.01) y = ground;

        // Walking back to its spot, or on its way somewhere: facing the way it goes.
        boolean going = catching || (home > 0 && Math.hypot(x - fromX, z - fromZ) > 0.02);
        float turnTo = going ? (float) (Mth.atan2(z - fromZ, x - fromX) * Mth.RAD_TO_DEG) - 90f : facing;
        body = Mth.approachDegrees(body, turnTo, walking || going ? TURN : 4f);
        float look = grazing ? 0 : glance(seed, clock + offset) * (walking || catching ? 0.3f : 1f) * (1 - home);
        head = Mth.approachDegrees(head, body + look, 8f);
        pitch = Mth.lerp(0.15f, pitch, grazing ? 40f : 0f);
        apply(puppet, Math.hypot(x - fromX, z - fromZ), tiny);
    }

    /**
     * One tick of walking up to the real mob, now that the server sends it again: faster
     * the farther it is, never slower than the real one walks. True once there, and then
     * the real one takes its place without a jump.
     */
    boolean follow(LivingEntity puppet, MobMemory.Remembered mob, Entity real) {
        if (!placed) place(mob);
        double tx = real.getX(), ty = real.getY(), tz = real.getZ();
        double dx = tx - x, dz = tz - z, d = Math.hypot(dx, dz);
        double pace = Math.hypot(real.getX() - real.xo, real.getZ() - real.zo);
        double step = Math.min(d, Math.min(0.8, pace + 0.06 + 0.05 * d));
        double fromX = x, fromZ = z;
        if (d > 1e-4) {
            x += dx / d * step;
            z += dz / d * step;
            y += (ty - y) * Math.min(1, step / d);
        } else {
            y += (ty - y) * 0.5;
        }
        float yaw = real instanceof LivingEntity living ? living.yBodyRot : real.getYRot();
        // Facing the way it walks, and the way the real one faces on the last stretch.
        float turnTo = d > 1.5 ? (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f : yaw;
        body = Mth.approachDegrees(body, turnTo, TURN);
        float headTo = real instanceof LivingEntity living ? living.yHeadRot : yaw;
        head = Mth.approachDegrees(head, d > 1.5 ? body : headTo, 10f);
        pitch = Mth.lerp(0.3f, pitch, real.getXRot());
        apply(puppet, Math.hypot(x - fromX, z - fromZ), false);
        boolean there = Math.hypot(tx - x, tz - z) < 0.1 && Math.abs(ty - y) < 0.15
                && Math.abs(Mth.wrapDegrees(body - yaw)) < 15;
        return there || ++following > 120;
    }

    private void apply(LivingEntity puppet, double moved, boolean tiny) {
        puppet.setOldPosAndRot();
        puppet.yBodyRotO = puppet.yBodyRot;
        puppet.yHeadRotO = puppet.yHeadRot;
        puppet.setPos(x, y, z);
        puppet.setYRot(body);
        puppet.setXRot(pitch);
        puppet.yBodyRot = body;
        puppet.yHeadRot = head;
        if (tiny) {
            // Legs at rest and idle movements frozen: nothing of them would show.
            Puppets.walk(puppet, 0f, 1f, 0f);
        } else {
            Puppets.walk(puppet, (float) Math.min(moved * 4.0, 1.0), 0.4f, puppet.isBaby() ? 3f : 1f);
            puppet.tickCount++;
        }
    }
}
