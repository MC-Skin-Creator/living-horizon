package fr.clixmods.farfarplayer.track;

import java.util.Random;

/**
 * Where a player probably is on the horizontal plane, as a cloud of guesses.
 *
 * <p>Past 332 blocks the locator bar only says in which direction a player is, never
 * how far. One direction is a line, not a point; but the line is seen again from
 * wherever the local player walks next, and two lines from two places cross. A
 * particle filter is the plain way to let that happen without deciding in advance
 * which observations are good: each guess carries a position and a velocity, every
 * tick moves them all a little, and every observation keeps the guesses that agree
 * with it. A guess that walks off the bearing line dies; one that agrees from two
 * viewpoints survives. Standing still, the cloud stays spread along the line - which is
 * the honest answer when there is no parallax.
 *
 * <p>Pure Java on purpose: nothing here touches the game, so it can be reasoned about
 * and tested alone.
 */
public final class PositionFilter {
    static final int COUNT = 400;

    /** Blocks per second. A rocket-boosted elytra flight tops out a little above 33. */
    private static final double MAX_SPEED = 40.0;
    /** How long a guess keeps its velocity, in seconds, before it fades. */
    private static final double VELOCITY_MEMORY = 20.0;
    /** Random change of velocity, in blocks per second, per square root of a second. */
    private static final double ACCEL_NOISE = 0.7;
    /** Random drift of position, in blocks per square root of a second. */
    private static final double POSITION_NOISE = 0.6;
    /** Drift of a guess that believes the player is standing still. */
    private static final double STILL_NOISE = 0.05;
    /** How often, per second, a guess changes its mind between standing and moving. */
    private static final double SWITCH_RATE = 0.01;

    private final double[] x = new double[COUNT];
    private final double[] z = new double[COUNT];
    private final double[] vx = new double[COUNT];
    private final double[] vz = new double[COUNT];
    /**
     * Guesses come in two kinds. A player who moves a little in any direction can
     * explain away the parallax the observer's own walk produces, and the distance is
     * never found; a player who stands still cannot. Keeping both kinds lets the
     * observations pick: a still player is pinned by the still guesses, which agree
     * with every line at once, and a moving one is followed by the others.
     */
    private final boolean[] still = new boolean[COUNT];
    private final double[] w = new double[COUNT];
    private final double[] scratch = new double[COUNT * 4];
    private final boolean[] stillScratch = new boolean[COUNT];
    private final Random random;

    private boolean ready;
    private double meanX, meanZ, meanVx, meanVz, spread;

    public PositionFilter() {
        this(new Random());
    }

    PositionFilter(Random random) {
        this.random = random;
    }

    public boolean ready() {
        return ready;
    }

    public void clear() {
        ready = false;
    }

    /** Every guess near one point, moving at roughly one velocity (blocks per second). */
    public void resetAt(double cx, double cz, double velX, double velZ, double radius) {
        double stillShare = Math.hypot(velX, velZ) > 1.0 ? 0.2 : 0.7;
        for (int i = 0; i < COUNT; i++) {
            x[i] = cx + random.nextGaussian() * radius;
            z[i] = cz + random.nextGaussian() * radius;
            still[i] = random.nextDouble() < stillShare;
            vx[i] = still[i] ? 0 : velX + random.nextGaussian() * 0.5;
            vz[i] = still[i] ? 0 : velZ + random.nextGaussian() * 0.5;
            w[i] = 1.0 / COUNT;
        }
        ready = true;
        summarize();
    }

    /**
     * Every guess on one bearing from an observer, between two distances. The distance
     * is drawn evenly on a log scale: nothing is known about it, and "twice as far" is
     * the kind of doubt there is.
     */
    public void resetAlongBearing(double ox, double oz, double angle, double near, double far) {
        double logNear = Math.log(near);
        double logFar = Math.log(Math.max(far, near + 1.0));
        for (int i = 0; i < COUNT; i++) {
            double r = Math.exp(logNear + random.nextDouble() * (logFar - logNear));
            double a = angle + random.nextGaussian() * Math.toRadians(0.4);
            x[i] = ox - Math.sin(a) * r;
            z[i] = oz + Math.cos(a) * r;
            still[i] = random.nextBoolean();
            vx[i] = still[i] ? 0 : random.nextGaussian() * 3.0;
            vz[i] = still[i] ? 0 : random.nextGaussian() * 3.0;
            w[i] = 1.0 / COUNT;
        }
        ready = true;
        summarize();
    }

    /** Moves every guess forward by {@code dt} seconds. */
    public void predict(double dt) {
        if (!ready) return;
        double decay = Math.exp(-dt / VELOCITY_MEMORY);
        double accel = ACCEL_NOISE * Math.sqrt(dt);
        double drift = POSITION_NOISE * Math.sqrt(dt);
        double stillDrift = STILL_NOISE * Math.sqrt(dt);
        double flip = SWITCH_RATE * dt;
        for (int i = 0; i < COUNT; i++) {
            if (random.nextDouble() < flip) {
                still[i] = !still[i];
                vx[i] = still[i] ? 0 : random.nextGaussian() * 3.0;
                vz[i] = still[i] ? 0 : random.nextGaussian() * 3.0;
            }
            if (still[i]) {
                x[i] += random.nextGaussian() * stillDrift;
                z[i] += random.nextGaussian() * stillDrift;
                continue;
            }
            double nvx = vx[i] * decay + random.nextGaussian() * accel;
            double nvz = vz[i] * decay + random.nextGaussian() * accel;
            double speed = Math.hypot(nvx, nvz);
            if (speed > MAX_SPEED) {
                nvx *= MAX_SPEED / speed;
                nvz *= MAX_SPEED / speed;
            }
            vx[i] = nvx;
            vz[i] = nvz;
            x[i] += nvx * dt + random.nextGaussian() * drift;
            z[i] += nvz * dt + random.nextGaussian() * drift;
        }
        summarize();
    }

    /**
     * Keeps the guesses seen from {@code (ox, oz)} at the locator bar's angle.
     *
     * <p>The angle is the one the server computes: the yaw, in radians, of the direction
     * from the observer to the target, so the target lies along {@code (-sin a, cos a)}.
     * The server only sends it again once it has moved by half a degree, so the angle
     * in hand is right to within that much for where the observer stands now - which is
     * why the likelihood is flat inside {@code tolerance} rather than peaked: applying
     * it every tick then sharpens nothing it should not.
     *
     * @return false when no guess agrees at all; the cloud is left as it was
     */
    public boolean observeBearing(double ox, double oz, double angle, double tolerance, double sigma) {
        for (int i = 0; i < COUNT; i++) {
            double a = Math.atan2(-(x[i] - ox), z[i] - oz);
            double off = Math.abs(wrap(a - angle)) - tolerance;
            scratch[i] = off <= 0 ? 1.0 : gauss(off / sigma);
        }
        return weigh();
    }

    /** Keeps the guesses whose distance to {@code (ox, oz)} is within a range. */
    public boolean observeDistance(double ox, double oz, double min, double max, double softness) {
        for (int i = 0; i < COUNT; i++) {
            double r = Math.hypot(x[i] - ox, z[i] - oz);
            double off = r < min ? min - r : r > max ? r - max : 0.0;
            scratch[i] = off == 0 ? 1.0 : gauss(off / softness);
        }
        return weigh();
    }

    /** Keeps the guesses inside a box: a chunk, for the locator bar's middle range. */
    public boolean observeBox(double minX, double minZ, double maxX, double maxZ, double softness) {
        for (int i = 0; i < COUNT; i++) {
            double dx = x[i] < minX ? minX - x[i] : x[i] > maxX ? x[i] - maxX : 0.0;
            double dz = z[i] < minZ ? minZ - z[i] : z[i] > maxZ ? z[i] - maxZ : 0.0;
            double off = Math.hypot(dx, dz);
            scratch[i] = off == 0 ? 1.0 : gauss(off / softness);
        }
        return weigh();
    }

    public double x() { return meanX; }
    public double z() { return meanZ; }
    /** Mean velocity, blocks per second. */
    public double velocityX() { return meanVx; }
    public double velocityZ() { return meanVz; }
    /** Standard deviation of the guesses around their mean, in blocks. */
    public double spread() { return spread; }

    private boolean weigh() {
        if (!ready) return false;
        double total = 0;
        for (int i = 0; i < COUNT; i++) total += w[i] * scratch[i];
        if (!(total > 1e-12)) return false;
        double squares = 0;
        for (int i = 0; i < COUNT; i++) {
            w[i] = w[i] * scratch[i] / total;
            squares += w[i] * w[i];
        }
        if (1.0 / squares < COUNT * 0.5) resample();
        summarize();
        return true;
    }

    /** Systematic resampling, then a little jitter so that copies do not stay identical. */
    private void resample() {
        double step = 1.0 / COUNT;
        double u = random.nextDouble() * step;
        double cumulative = w[0];
        int j = 0;
        for (int i = 0; i < COUNT; i++) {
            double target = u + i * step;
            while (target > cumulative && j < COUNT - 1) cumulative += w[++j];
            scratch[i * 4] = x[j];
            scratch[i * 4 + 1] = z[j];
            scratch[i * 4 + 2] = vx[j];
            scratch[i * 4 + 3] = vz[j];
            stillScratch[i] = still[j];
        }
        for (int i = 0; i < COUNT; i++) {
            still[i] = stillScratch[i];
            double jitter = still[i] ? 0.05 : 0.3;
            x[i] = scratch[i * 4] + random.nextGaussian() * jitter;
            z[i] = scratch[i * 4 + 1] + random.nextGaussian() * jitter;
            vx[i] = still[i] ? 0 : scratch[i * 4 + 2] + random.nextGaussian() * 0.1;
            vz[i] = still[i] ? 0 : scratch[i * 4 + 3] + random.nextGaussian() * 0.1;
            w[i] = step;
        }
    }

    private void summarize() {
        double mx = 0, mz = 0, mvx = 0, mvz = 0;
        for (int i = 0; i < COUNT; i++) {
            mx += w[i] * x[i];
            mz += w[i] * z[i];
            mvx += w[i] * vx[i];
            mvz += w[i] * vz[i];
        }
        double variance = 0;
        for (int i = 0; i < COUNT; i++) {
            double dx = x[i] - mx, dz = z[i] - mz;
            variance += w[i] * (dx * dx + dz * dz);
        }
        meanX = mx;
        meanZ = mz;
        meanVx = mvx;
        meanVz = mvz;
        spread = Math.sqrt(variance);
    }

    private static double gauss(double t) {
        return Math.exp(-0.5 * t * t);
    }

    private static double wrap(double angle) {
        angle %= 2 * Math.PI;
        if (angle > Math.PI) angle -= 2 * Math.PI;
        if (angle < -Math.PI) angle += 2 * Math.PI;
        return angle;
    }
}
