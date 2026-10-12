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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Easter egg 6: now and then, day or night, far off in a forest, a big hairy figure walks
 * across the player's view among the trees, turns its head to look at them once, walks on,
 * and is gone as soon as something stands between them - a trunk, a rock - or the player
 * comes near. Only on this screen.
 *
 * <p>The figure is drawn of boxes ({@link BoxFigure}), on the ground under the trees, in
 * the light where it walks. It starts out of sight where it can, and once it has been in
 * sight a while, the first obstacle that hides it is where it goes.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Bigfoot {
    private static final BoxFigure.Shape SHAPE = new BoxFigure.Shape(
            1.0f, 0.36f, 1.0f, 0.95f, 0.6f, 1.25f, 0.3f, 0.55f, 0.1f, 0x5E4329, 0x8C7058);
    /** Blocks per tick it walks; blocks across under which it goes. */
    private static final double PACE = 0.075, NEAR = 18;
    /** How far off it walks, in blocks across: in the loaded chunks, where the trees are. */
    private static final double MIN = 80, MAX = 180;
    /** Ticks it keeps its head turned to the player. */
    private static final int LOOK = 40;
    /** Ticks in sight before an obstacle may take it; ticks hidden that do; ticks it walks on, at most, to find one. */
    private static final int SEEN = 40, HIDDEN = 4, LONGER = 400;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    private boolean active;
    private double x, y, z, xo, yo, zo, dirX, dirZ, walked;
    private float yaw, headYaw, headYawO;
    private int age, steps, lookAt, light, idle, seen, hidden;

    Bigfoot(EggCalendar calendar) {
        this.calendar = calendar;
    }

    /** Under way: no other easter egg starts meanwhile. */
    public boolean busy() {
        return active;
    }

    /** Where it walks, while it is there. */
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /**
     * Starts it walking across the view somewhere ahead, far off in a forest: by chance, or
     * because the player asked ({@code called}: straighter in front of them). False if the
     * easter eggs are off or no forest ground was found.
     */
    public boolean summon(ClientLevel level, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || level.dimension() != Level.OVERWORLD) return false;
        if (active) return true;
        double look = Math.toRadians(self.getYRot());
        Vec3 eye = self.getEyePosition();
        for (int attempt = 0; attempt < 48; attempt++) {
            double bearing = look + random.nextGaussian() * (called ? 0.35 : 0.6);
            double distance = MIN + random.nextDouble() * (MAX - MIN);
            double sx = self.getX() - Math.sin(bearing) * distance, sz = self.getZ() + Math.cos(bearing) * distance;
            double sy = ground(level, sx, sz);
            if (Double.isNaN(sy) || !forest(level, sx, sy, sz)) continue;
            // Across the view, from a few blocks before the spot: out of sight there, if it can be.
            double side = random.nextBoolean() ? 1 : -1;
            double ax = Math.cos(bearing) * side, az = Math.sin(bearing) * side;
            double bx = sx - ax * 8, bz = sz - az * 8, start = ground(level, bx, bz);
            double by = Double.isNaN(start) ? sy : start;
            if (attempt < 24 && visible(level, self, eye, bx, by, bz)) continue;
            dirX = ax;
            dirZ = az;
            x = xo = bx;
            z = zo = bz;
            y = yo = by;
            yaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
            headYaw = headYawO = 0f;
            steps = 220 + random.nextInt(100);
            lookAt = steps * 2 / 5;
            walked = 0;
            age = 0;
            seen = hidden = 0;
            light = BoxFigure.light(level, x, y, z);
            active = true;
            return true;
        }
        return false;
    }

    /** {@code quiet}: no other easter egg under way, so this one may start by chance. */
    public void tick(ClientLevel level, LocalPlayer self, boolean enabled, boolean quiet) {
        if (!enabled) {
            active = false;
            return;
        }
        if (!active) {
            // Rare, day or night: about once in an hour and a half, where a forest stands ahead.
            if (++idle > 6000 && quiet && random.nextInt(108000) == 0) {
                idle = 0;
                if (summon(level, self, false)) calendar.shown(6);
            }
            return;
        }
        age++;
        xo = x;
        yo = y;
        zo = z;
        headYawO = headYaw;
        x += dirX * PACE;
        z += dirZ * PACE;
        walked += PACE;
        double ground = ground(level, x, z);
        if (!Double.isNaN(ground)) y += (ground - y) * 0.3;
        // Once on its way, it turns its head to the player, then looks ahead again.
        float toPlayer = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-(self.getX() - x), self.getZ() - z)) - yaw);
        float wanted = age > lookAt && age < lookAt + LOOK ? Mth.clamp(toPlayer, -100f, 100f) : 0f;
        headYaw += Mth.clamp(wanted - headYaw, -8f, 8f);
        light = BoxFigure.light(level, x, y, z);
        // Seen for a while, it goes behind the first thing that hides it; past its walk, as soon
        // as something does, or anyway a little later.
        if (visible(level, self, self.getEyePosition(), x, y, z)) {
            seen++;
            hidden = 0;
        } else {
            hidden++;
        }
        boolean gone = hidden >= HIDDEN && (seen >= SEEN || age > steps);
        if (gone || age > steps + LONGER || Math.hypot(self.getX() - x, self.getZ() - z) < NEAR) active = false;
    }

    /** Its head or its chest in sight of the player's eye. */
    private static boolean visible(ClientLevel level, LocalPlayer self, Vec3 eye, double x, double y, double z) {
        return Ghosts.inSight(level, self, eye, x, y + 2.3, z) || Ghosts.inSight(level, self, eye, x, y + 1.4, z);
    }

    /** Forest, taiga or jungle. */
    private static boolean forest(ClientLevel level, double x, double y, double z) {
        Holder<Biome> biome = level.getBiome(BlockPos.containing(x, y, z));
        return biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE);
    }

    /** The ground of a column, under its trees: below the trunks the heightmap stands on. NaN where not loaded. */
    private static double ground(ClientLevel level, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return Double.NaN;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(
                bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1, bz);
        for (int i = 0; i < 32 && level.getBlockState(at).is(BlockTags.LOGS); i++) at.move(0, -1, 0);
        return at.getY() + 1;
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
        // A long, slow stride.
        float swing = (float) Math.sin((walked + PACE * partial) * 2.2) * 28f;
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        // Yaw turns the other way round from the pose's rotations, the body's as the head's.
        BoxFigure.draw(pose, collector, SHAPE, swing, 0f, -Mth.lerp(partial, headYawO, headYaw), light);
        pose.popPose();
    }
}
