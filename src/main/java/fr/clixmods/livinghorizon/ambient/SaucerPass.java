package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
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

/**
 * Easter egg 2: now and then, high up, the saucer of {@link Ufo} crosses the sky somewhere
 * around the player, stops dead for a moment, and goes off another way, to the horizon,
 * until it is a speck. Only on this screen.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class SaucerPass {
    /** Blocks per tick while it cruises; blocks per tick it gains on its way off, up to its speed. */
    private static final double CRUISE = 3.5, AWAY_GAIN = 0.03, AWAY_SPEED = 4;
    /** Ticks it goes on its way off at most: before that, it is gone once a pixel or so on screen. */
    private static final int AWAY_LONGEST = 6000;
    /** Half the length of its way across, in blocks. */
    private static final double HALF = 650;

    private enum Phase { CRUISE, HOVER, AWAY }

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    private boolean active;
    private Phase phase = Phase.CRUISE;
    private int age, timer, idle;
    private double x, y, z, xo, yo, zo, hoverY;
    /** Which way it cruises, flat; which way it goes off, climbing a little. */
    private double dirX, dirZ, awayX, awayY, awayZ;
    private double stopAt, travelled, speed;
    private float heading, tilt, tiltO, spin, spinO;

    SaucerPass(EggCalendar calendar) {
        this.calendar = calendar;
    }

    /** Under way: no other easter egg starts meanwhile. */
    public boolean busy() {
        return active;
    }

    /**
     * Starts a pass across the sky around the player: by chance anywhere, or because they
     * asked ({@code called}: in front of them). False if the easter eggs are off.
     */
    public boolean summon(ClientLevel level, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || level.dimension() != Level.OVERWORLD) return false;
        if (active) return true;
        // Its way runs square to a line from the player, some way out along it.
        double look = called ? Math.toRadians(self.getYRot()) + random.nextGaussian() * 0.15 : random.nextDouble() * Math.PI * 2;
        double fx = -Math.sin(look), fz = Math.cos(look);
        double ahead = 220 + random.nextDouble() * 180, side = random.nextBoolean() ? 1 : -1;
        dirX = -fz * side;
        dirZ = fx * side;
        x = xo = self.getX() + fx * ahead - dirX * HALF;
        z = zo = self.getZ() + fz * ahead - dirZ * HALF;
        y = yo = Math.max(self.getY(), level.getSeaLevel()) + 110 + random.nextDouble() * 70;
        // It stops about in front of the player.
        stopAt = HALF + random.nextGaussian() * 60;
        travelled = 0;
        heading = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
        tilt = tiltO = 14f;
        phase = Phase.CRUISE;
        age = timer = 0;
        active = true;
        return true;
    }

    /** {@code quiet}: no other easter egg under way, so this one may start by chance. */
    public void tick(ClientLevel level, LocalPlayer self, boolean enabled, boolean quiet) {
        if (!enabled) {
            active = false;
            return;
        }
        if (!active) {
            // Rarer than the first: about once in two hours of night, six of day.
            int chance = level.isBrightOutside() ? 432000 : 144000;
            if (++idle > 2400 && quiet && random.nextInt(chance) == 0) {
                idle = 0;
                if (summon(level, self, false)) calendar.shown(2);
            }
            return;
        }
        age++;
        xo = x;
        yo = y;
        zo = z;
        tiltO = tilt;
        spinO = spin;
        spin += 9f;
        switch (phase) {
            case CRUISE -> {
                // Full speed, then braking hard over the last few dozen blocks.
                double step = Math.min(CRUISE, Math.max(0.1, (stopAt - travelled) * 0.12));
                travelled += step;
                x += dirX * step;
                z += dirZ * step;
                tilt = (float) (14 * step / CRUISE);
                if (stopAt - travelled < 0.5) {
                    phase = Phase.HOVER;
                    timer = 40 + random.nextInt(40);
                    hoverY = y;
                }
            }
            case HOVER -> {
                y = hoverY + 0.6 * Math.sin(age * 0.2);
                tilt = 0f;
                if (--timer <= 0) {
                    // Off another way, to the horizon, climbing a little, slowly faster.
                    double turn = (random.nextBoolean() ? 1 : -1) * (0.4 + random.nextDouble() * 0.8);
                    double nx = dirX * Math.cos(turn) - dirZ * Math.sin(turn);
                    double nz = dirX * Math.sin(turn) + dirZ * Math.cos(turn);
                    double up = 0.08 + random.nextDouble() * 0.12, length = Math.sqrt(1 + up * up);
                    awayX = nx / length;
                    awayY = up / length;
                    awayZ = nz / length;
                    heading = (float) Math.toDegrees(Math.atan2(-nx, nz));
                    speed = 0.2;
                    timer = 0;
                    phase = Phase.AWAY;
                }
            }
            case AWAY -> {
                speed = Math.min(AWAY_SPEED, speed + AWAY_GAIN);
                x += awayX * speed;
                y += awayY * speed;
                z += awayZ * speed;
                tilt = Math.min(20f, tilt + 0.5f);
                if (Ufo.tiny(self.getEyePosition().distanceTo(new Vec3(x, y, z))) || ++timer > AWAY_LONGEST) active = false;
            }
        }
    }

    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        double px = Mth.lerp(partial, xo, x) - camera.x;
        double py = Mth.lerp(partial, yo, y) - camera.y;
        double pz = Mth.lerp(partial, zo, z) - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        GhostRenderer.noteDistance(distance + 20);
        // Past the far plane: closer and smaller in proportion, the same on screen.
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        Ufo.saucer(pose, collector, heading, Mth.lerp(partial, tiltO, tilt), Mth.lerp(partial, spinO, spin), (age / 5) % 3);
        pose.popPose();
    }
}
