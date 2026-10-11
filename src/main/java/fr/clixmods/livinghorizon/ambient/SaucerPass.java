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
 * Easter egg 2: now and then, high up, the saucer of {@link Ufo} crosses the sky in front
 * of the player, stops dead for a moment, and darts off another way. Only on this screen.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class SaucerPass {
    /** Blocks per tick while it cruises; how much faster it goes every tick of its dash. */
    private static final double CRUISE = 3.5, DASH_GAIN = 0.6;
    /** Ticks of the dash, after which it is too far and too small to be seen. */
    private static final int DASH = 60;
    /** Half the length of its way across, in blocks. */
    private static final double HALF = 650;

    private enum Phase { CRUISE, HOVER, DASH }

    private final RandomSource random = RandomSource.create();
    private boolean active;
    private Phase phase = Phase.CRUISE;
    private int age, timer, idle;
    private double x, y, z, xo, yo, zo, hoverY;
    /** Which way it cruises, flat; which way it dashes off, climbing. */
    private double dirX, dirZ, dashX, dashY, dashZ;
    private double stopAt, travelled, speed;
    private float heading, tilt, tiltO, spin, spinO;

    /** Under way: no other easter egg starts meanwhile. */
    public boolean busy() {
        return active;
    }

    /**
     * Starts a pass across the sky ahead of the player: by chance, or because they asked
     * ({@code called}: straighter in front of them). False if the easter eggs are off.
     */
    public boolean summon(ClientLevel level, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || level.dimension() != Level.OVERWORLD) return false;
        if (active) return true;
        // Its way runs square to where the player looks, some way ahead of them.
        double look = Math.toRadians(self.getYRot()) + random.nextGaussian() * (called ? 0.15 : 0.5);
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
            // Rarer than the first: about once in an hour of night, three of day.
            int chance = level.isBrightOutside() ? 216000 : 72000;
            if (++idle > 2400 && quiet && random.nextInt(chance) == 0) {
                idle = 0;
                summon(level, self, false);
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
                    // Off another way, climbing, faster every tick.
                    double turn = (random.nextBoolean() ? 1 : -1) * (0.4 + random.nextDouble() * 0.8);
                    double nx = dirX * Math.cos(turn) - dirZ * Math.sin(turn);
                    double nz = dirX * Math.sin(turn) + dirZ * Math.cos(turn);
                    double up = 0.25 + random.nextDouble() * 0.35, length = Math.sqrt(1 + up * up);
                    dashX = nx / length;
                    dashY = up / length;
                    dashZ = nz / length;
                    heading = (float) Math.toDegrees(Math.atan2(-nx, nz));
                    speed = 0.5;
                    phase = Phase.DASH;
                }
            }
            case DASH -> {
                speed += DASH_GAIN;
                x += dashX * speed;
                y += dashY * speed;
                z += dashZ * speed;
                tilt = Math.min(25f, tilt + 3f);
                if (++timer > DASH) active = false;
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
