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
 * Easter egg 6: now and then, by day in a forest, a big hairy figure walks across the
 * player's view among the trees, turns its head to look at them once, walks on, and is
 * gone - at once if the player comes near. Only on this screen.
 *
 * <p>The figure is drawn of boxes ({@link BoxFigure}), on the ground under the trees, in
 * the light where it walks.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Bigfoot {
    private static final BoxFigure.Shape SHAPE = new BoxFigure.Shape(
            1.0f, 0.36f, 1.0f, 0.95f, 0.6f, 1.25f, 0.3f, 0.55f, 0.1f, 0x5E4329, 0x8C7058);
    /** Blocks per tick it walks; blocks across under which it goes. */
    private static final double PACE = 0.075, NEAR = 18;
    /** Ticks it keeps its head turned to the player. */
    private static final int LOOK = 40;

    private final RandomSource random = RandomSource.create();
    private boolean active;
    private double x, y, z, xo, yo, zo, dirX, dirZ, walked;
    private float yaw, headYaw, headYawO;
    private int age, steps, lookAt, light, idle;

    /** Under way: no other easter egg starts meanwhile. */
    public boolean busy() {
        return active;
    }

    /** Where it walks, while it is there. */
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /**
     * Starts it walking across the view somewhere ahead, in a forest: by chance, or because
     * the player asked ({@code called}: anywhere ahead if no forest is). False if the easter
     * eggs are off or no ground was found.
     */
    public boolean summon(ClientLevel level, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || level.dimension() != Level.OVERWORLD) return false;
        if (active) return true;
        double look = Math.toRadians(self.getYRot());
        for (int attempt = 0; attempt < 32; attempt++) {
            double bearing = look + random.nextGaussian() * (called ? 0.3 : 0.6);
            double distance = 30 + random.nextDouble() * 30;
            double sx = self.getX() - Math.sin(bearing) * distance, sz = self.getZ() + Math.cos(bearing) * distance;
            double sy = ground(level, sx, sz);
            if (Double.isNaN(sy)) continue;
            if (!forest(level, sx, sy, sz) && !(called && attempt >= 16)) continue;
            // Across the view, from a few blocks before the spot.
            double side = random.nextBoolean() ? 1 : -1;
            dirX = Math.cos(bearing) * side;
            dirZ = Math.sin(bearing) * side;
            x = xo = sx - dirX * 8;
            z = zo = sz - dirZ * 8;
            double start = ground(level, x, z);
            y = yo = Double.isNaN(start) ? sy : start;
            yaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
            headYaw = headYawO = 0f;
            steps = 220 + random.nextInt(100);
            lookAt = steps * 2 / 5;
            walked = 0;
            age = 0;
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
            // Rare, by day, and only for a player who stands in a forest.
            if (++idle > 6000 && quiet && level.isBrightOutside() && random.nextInt(54000) == 0
                    && forest(level, self.getX(), self.getY(), self.getZ())) {
                idle = 0;
                summon(level, self, false);
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
        if (age > steps || Math.hypot(self.getX() - x, self.getZ() - z) < NEAR) active = false;
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
