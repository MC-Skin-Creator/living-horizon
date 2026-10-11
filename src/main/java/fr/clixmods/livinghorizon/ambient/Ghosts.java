package fr.clixmods.livinghorizon.ambient;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Easter egg 3, very rare: some night, a few pale figures walk in single file not far
 * off, see-through, then stop, turn to look at the player, and are gone - at once if the
 * player comes near. Only on this screen.
 *
 * <p>They are players of no one, in the default skins the game picks from their ids, drawn
 * see-through the way the game draws an invisible entity its viewer may see
 * ({@link #seeThrough}), and lit by nothing but themselves.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Ghosts {
    /** Blocks per tick they walk; how close the player may come before they vanish. */
    private static final double PACE = 0.045, NEAR = 10;
    /** Blocks between one and the next, in file. */
    private static final double SPACING = 2.4;
    /** Ticks they stand looking at the player before they vanish. */
    private static final int STARE = 50;
    /** The figures drawn see-through, of this easter egg and others. Render thread only. */
    private static final Set<Entity> SEE_THROUGH = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final class Figure {
        final RemotePlayer body;
        /** The height it floats above, eased towards the ground under it. */
        double floor;
        final float phase;

        Figure(RemotePlayer body, double floor, float phase) {
            this.body = body;
            this.floor = floor;
            this.phase = phase;
        }
    }

    private final RandomSource random = RandomSource.create();
    private final List<Figure> walkers = new ArrayList<>();
    private final List<RemotePlayer> figures = new ArrayList<>();
    private @Nullable ClientLevel level;
    private double dirX, dirZ;
    private int age, walk, idle;

    /** Drawn see-through by the game, as an invisible entity its viewer may see. */
    public static boolean seeThrough(Entity entity) {
        return !SEE_THROUGH.isEmpty() && SEE_THROUGH.contains(entity);
    }

    /** An invisible figure of another easter egg drawn see-through from now on, or no longer. */
    static void seeThrough(Entity entity, boolean on) {
        if (on) SEE_THROUGH.add(entity);
        else SEE_THROUGH.remove(entity);
    }

    /** Under way: no other easter egg starts meanwhile. */
    public boolean busy() {
        return !walkers.isEmpty();
    }

    /** The figures to draw this frame. */
    public List<RemotePlayer> figures() {
        return figures;
    }

    /**
     * Starts the show somewhere ahead of the player, in sight: by chance, or because they
     * asked ({@code called}: straighter in front of them). False if the easter eggs are off
     * or no ground ahead is in sight.
     */
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (!walkers.isEmpty()) return true;
        Vec3 eye = self.getEyePosition();
        double yaw = Math.toRadians(self.getYRot());
        for (int attempt = 0; attempt < 24; attempt++) {
            double bearing = yaw + random.nextGaussian() * (called ? 0.25 : 0.45);
            double distance = 22 + random.nextDouble() * 26;
            double gx = self.getX() - Math.sin(bearing) * distance, gz = self.getZ() + Math.cos(bearing) * distance;
            double gy = ground(current, gx, gz);
            if (Double.isNaN(gy) || !inSight(current, self, eye, gx, gy + 1.5, gz)) continue;
            // Across the player's view, one way or the other.
            double side = random.nextBoolean() ? 1 : -1;
            dirX = Math.cos(bearing) * side;
            dirZ = Math.sin(bearing) * side;
            float facing = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
            int count = 1 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                double x = gx - dirX * SPACING * i, z = gz - dirZ * SPACING * i, y = ground(current, x, z);
                RemotePlayer body = Puppets.player(current, new GameProfile(UUID.randomUUID(), ""));
                body.setInvisible(true);
                body.setPos(x, Double.isNaN(y) ? gy : y, z);
                body.setYRot(facing);
                body.yBodyRot = body.yBodyRotO = body.yHeadRot = body.yHeadRotO = facing;
                body.setOldPosAndRot();
                walkers.add(new Figure(body, body.getY(), random.nextFloat() * 6.28f));
                figures.add(body);
                SEE_THROUGH.add(body);
            }
            level = current;
            walk = 160 + random.nextInt(100);
            age = 0;
            return true;
        }
        return false;
    }

    /** {@code quiet}: no other easter egg under way, so this one may start by chance. */
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (!walkers.isEmpty() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (walkers.isEmpty()) {
            // Very rare, and only at night: about once in four hours of night.
            if (++idle > 6000 && quiet && !current.isBrightOutside() && random.nextInt(288000) == 0) {
                idle = 0;
                summon(current, self, false);
            }
            return;
        }
        age++;
        boolean staring = age > walk;
        for (Figure figure : walkers) {
            RemotePlayer body = figure.body;
            if (body.distanceToSqr(self) < NEAR * NEAR) {
                clear();
                return;
            }
            body.setOldPosAndRot();
            body.yBodyRotO = body.yBodyRot;
            body.yHeadRotO = body.yHeadRot;
            double x = body.getX(), z = body.getZ();
            if (!staring) {
                x += dirX * PACE;
                z += dirZ * PACE;
            } else {
                // Turning, slowly, to the player.
                float toPlayer = (float) Math.toDegrees(Math.atan2(-(self.getX() - x), self.getZ() - z));
                body.yBodyRot = Mth.approachDegrees(body.yBodyRot, toPlayer, 9f);
            }
            body.yHeadRot = body.yBodyRot;
            body.setYRot(body.yBodyRot);
            double ground = ground(current, x, z);
            if (!Double.isNaN(ground)) figure.floor += (ground - figure.floor) * 0.15;
            // Not quite touching the ground.
            body.setPos(x, figure.floor + 0.2 + 0.08 * Math.sin(age * 0.08 + figure.phase), z);
            Puppets.walk(body, staring ? 0f : (float) (PACE * 4), 0.4f, 1f);
            body.tickCount++;
        }
        if (age > walk + STARE) clear();
    }

    private void clear() {
        figures.forEach(SEE_THROUGH::remove);
        walkers.clear();
        figures.clear();
        level = null;
    }

    /** The height to stand at on this column, or NaN where its chunk is not here. */
    static double ground(ClientLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return Double.NaN;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }

    /** Nothing the player cannot see through between their eye and this point. */
    static boolean inSight(ClientLevel level, LocalPlayer self, Vec3 eye, double x, double y, double z) {
        return level.clip(new ClipContext(eye, new Vec3(x, y, z), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, self))
                .getType() == HitResult.Type.MISS;
    }
}
