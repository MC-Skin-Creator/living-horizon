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

import java.util.concurrent.atomic.AtomicReference;

/**
 * Easter egg 5: now and then, by day, a dark figure stands on the highest summit ahead,
 * against the sky, turns to the player, waves, and is gone. Only on this screen.
 *
 * <p>The summit is the highest of a fan of columns in front of the player, read in the
 * loaded chunks or the far world ({@link Columns}); by chance it must rise well above the
 * player and the sea. The figure is drawn of boxes ({@link BoxFigure}).
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Waver {
    private static final BoxFigure.Shape SHAPE = new BoxFigure.Shape(
            0.75f, 0.25f, 0.75f, 0.5f, 0.25f, 0.72f, 0.25f, 0.5f, 0f, 0x16161A, 0x16161A);
    /** Ticks it stands still, then waves; then it is gone. */
    private static final int STAND = 30, WAVE = 80;
    /** Blocks a summit found by chance rises above the player's feet, and above the sea. */
    private static final double ABOVE_PLAYER = 12, ABOVE_SEA = 20;
    /** Blocks across: it goes if the player comes this close. */
    private static final double NEAR = 24;

    private final RandomSource random = RandomSource.create();
    private final AtomicReference<Columns.Column @Nullable []> found = new AtomicReference<>();
    private boolean searching, active, called;
    private double x, y, z, feetY;
    private float yaw, yawO;
    private int age, idle;

    /** On its way or under way: no other easter egg starts meanwhile. */
    public boolean busy() {
        return searching || active;
    }

    /** Where it stands, while it is there. */
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /**
     * Looks for the summit ahead, and stands there once it is read: by chance, or because
     * the player asked ({@code called}: the highest point ahead, however low). False if the
     * easter eggs are off.
     */
    public boolean summon(ClientLevel level, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || level.dimension() != Level.OVERWORLD) return false;
        if (busy()) return true;
        int bearings = 9, rings = 7;
        double look = Math.toRadians(self.getYRot());
        double[] xs = new double[bearings * rings], zs = new double[bearings * rings];
        for (int b = 0, i = 0; b < bearings; b++) {
            double bearing = look + (b - (bearings - 1) / 2.0) * 0.1;
            for (int r = 0; r < rings; r++, i++) {
                double distance = 60 + r * 25;
                xs[i] = Mth.floor(self.getX() - Math.sin(bearing) * distance) + 0.5;
                zs[i] = Mth.floor(self.getZ() + Math.cos(bearing) * distance) + 0.5;
            }
        }
        this.called = called;
        feetY = self.getY();
        found.set(null);
        Columns.read(level, xs, zs).thenAccept(found::set);
        searching = true;
        return true;
    }

    /** {@code quiet}: no other easter egg under way, so this one may start by chance. */
    public void tick(ClientLevel level, LocalPlayer self, boolean enabled, boolean quiet) {
        if (!enabled) {
            active = searching = false;
            return;
        }
        if (!active && !searching) {
            // Rare, by day: about once in an hour of it, where there are mountains.
            if (++idle > 6000 && quiet && level.isBrightOutside() && random.nextInt(72000) == 0) {
                idle = 0;
                summon(level, self, false);
            }
            return;
        }
        if (searching) {
            Columns.Column[] columns = found.getAndSet(null);
            if (columns == null) return;
            searching = false;
            Columns.Column top = null;
            for (Columns.Column column : columns) {
                if (column.known() && !column.water() && (top == null || column.y() > top.y())) top = column;
            }
            if (top == null) return;
            if (!called && (top.y() < feetY + ABOVE_PLAYER || top.y() < level.getSeaLevel() + ABOVE_SEA)) return;
            x = top.x();
            y = top.y();
            z = top.z();
            yaw = yawO = facing(self);
            age = 0;
            active = true;
            return;
        }
        age++;
        yawO = yaw;
        yaw = facing(self);
        if (age > STAND + WAVE || Math.hypot(self.getX() - x, self.getZ() - z) < NEAR) active = false;
    }

    private float facing(LocalPlayer self) {
        return (float) Math.toDegrees(Math.atan2(-(self.getX() - x), self.getZ() - z));
    }

    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        double px = x - camera.x, py = y - camera.y, pz = z - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        GhostRenderer.noteDistance(distance + 5);
        // Past the far plane: closer and smaller in proportion, the same on screen.
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        // The right arm comes up and waves until the figure is gone.
        float t = age + partial, raise = 0f;
        if (t > STAND) {
            float up = Math.min(1f, (t - STAND) / 8f);
            raise = -(150f + 22f * (float) Math.sin((t - STAND) * 0.55)) * up;
        }
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, yawO, yaw)));
        BoxFigure.draw(pose, collector, SHAPE, 0f, raise, 0f, Ufo.LIT);
        pose.popPose();
    }
}
