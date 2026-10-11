package fr.clixmods.livinghorizon.ambient;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.Sink;
import fr.clixmods.livinghorizon.track.Puppets;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Easter egg 4: some time, off to the side and a long way off, a figure in Steve's skin
 * with white eyes stands still and watches the player. Three to five seconds after they
 * look at him he is gone; gone as well if they come near, or if they never look. Only on
 * this screen.
 *
 * <p>The figure is a player of no one, in the default skin the game picks from its id:
 * Steve's for {@link #STEVE}. The white eyes are two small boxes drawn over its face.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Herobrine {
    /**
     * An id the game gives Steve's default skin (wide arms): the skin is the one at
     * {@code floorMod(id.hashCode(), 18)} in its list of default skins, Steve's at 15.
     */
    static final UUID STEVE = new UUID(0L, 15L);
    /** Blocks: he goes if the player comes this close. */
    private static final double NEAR = 24;
    /** How close to where the player looks counts as looking at him: about 8 degrees. */
    private static final double LOOKED_AT = Math.cos(Math.toRadians(8));
    /** Ticks he waits to be seen before he goes anyway. */
    private static final int PATIENCE = 1800;
    /** Glowing and unshaded, the way the game draws a spider's eyes: the white texel of the bird sheet. */
    private static final RenderType EYES = RenderTypes.eyes(Ufo.SHEET);
    /** A pixel of the player model, drawn at 15/16 of its size, in blocks. */
    private static final float PIXEL = 0.9375f / 16f;
    /** Pixels from the feet to the neck, where the head turns. */
    private static final float NECK = 1.501f * 16f;

    private final RandomSource random = RandomSource.create();
    private @Nullable RemotePlayer figure;
    private int age, leaveIn, idle;

    /** Under way: no other easter egg starts meanwhile. */
    public boolean busy() {
        return figure != null;
    }

    /** The figure to draw, while he is there. */
    public @Nullable RemotePlayer figure() {
        return figure;
    }

    /**
     * Puts him somewhere the player can see him from the corner of the eye: by chance, or
     * because they asked ({@code called}: nearer the middle of the view). False if the
     * easter eggs are off or no ground in sight was found.
     */
    public boolean summon(ClientLevel level, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || level.dimension() != Level.OVERWORLD) return false;
        if (figure != null) return true;
        Vec3 eye = self.getEyePosition();
        double yaw = Math.toRadians(self.getYRot());
        for (int attempt = 0; attempt < 32; attempt++) {
            double off = called ? 0.15 + random.nextDouble() * 0.35 : 0.35 + random.nextDouble() * 0.5;
            double bearing = yaw + off * (random.nextBoolean() ? 1 : -1);
            double distance = 40 + random.nextDouble() * 40;
            double x = self.getX() - Math.sin(bearing) * distance, z = self.getZ() + Math.cos(bearing) * distance;
            double y = Ghosts.ground(level, x, z);
            if (Double.isNaN(y) || !level.getFluidState(BlockPos.containing(x, y - 1, z)).isEmpty()) continue;
            // Seen whole, or near enough: his head and his knees.
            if (!Ghosts.inSight(level, self, eye, x, y + 1.6, z) || !Ghosts.inSight(level, self, eye, x, y + 0.6, z)) continue;
            RemotePlayer made = Puppets.player(level, new GameProfile(STEVE, ""));
            made.setPos(x, y, z);
            face(made, self);
            made.yBodyRotO = made.yBodyRot;
            made.yHeadRotO = made.yHeadRot;
            made.setOldPosAndRot();
            figure = made;
            age = 0;
            leaveIn = -1;
            return true;
        }
        return false;
    }

    /** {@code quiet}: no other easter egg under way, so this one may start by chance. */
    public void tick(ClientLevel level, LocalPlayer self, boolean enabled, boolean quiet) {
        RemotePlayer him = figure;
        if (him != null && (!enabled || him.level() != level)) figure = him = null;
        if (!enabled) return;
        if (him == null) {
            // Rare: about once in an hour of night, three of day.
            int chance = level.isBrightOutside() ? 216000 : 72000;
            if (++idle > 6000 && quiet && random.nextInt(chance) == 0) {
                idle = 0;
                summon(level, self, false);
            }
            return;
        }
        age++;
        him.setOldPosAndRot();
        him.yBodyRotO = him.yBodyRot;
        him.yHeadRotO = him.yHeadRot;
        face(him, self);
        Puppets.walk(him, 0f, 0.4f, 1f);
        him.tickCount++;

        Vec3 eye = self.getEyePosition();
        Vec3 head = new Vec3(him.getX(), him.getY() + 1.6, him.getZ());
        Vec3 to = head.subtract(eye);
        if (to.lengthSqr() < NEAR * NEAR) {
            figure = null;
            return;
        }
        if (leaveIn < 0 && self.getViewVector(1f).dot(to.normalize()) > LOOKED_AT
                && Ghosts.inSight(level, self, eye, head.x, head.y, head.z)) {
            // Seen: three to five seconds more.
            leaveIn = 60 + random.nextInt(41);
        }
        if (leaveIn >= 0 ? --leaveIn <= 0 : age > PATIENCE) figure = null;
    }

    /** Turned to the player, his head level. */
    private static void face(RemotePlayer him, LocalPlayer self) {
        float yaw = (float) Math.toDegrees(Math.atan2(-(self.getX() - him.getX()), self.getZ() - him.getZ()));
        him.setYRot(yaw);
        him.setXRot(0f);
        him.yBodyRot = yaw;
        him.yHeadRot = yaw;
    }

    /** His white eyes, over the face the game draws. */
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        RemotePlayer him = figure;
        if (him == null) return;
        pose.pushPose();
        pose.translate((float) (Mth.lerp(partial, him.xo, him.getX()) - camera.x),
                (float) (Mth.lerp(partial, him.yo, him.getY()) - camera.y),
                (float) (Mth.lerp(partial, him.zo, him.getZ()) - camera.z));
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, him.yHeadRotO, him.yHeadRot)));
        collector.draw(pose, EYES, Herobrine::eyes);
        pose.popPose();
    }

    /**
     * Both eyes of Steve's face, two pixels each, made white: the fifth row of the face
     * from the top, three and four pixels above the neck, a pixel in from each side.
     */
    private static void eyes(PoseStack.Pose at, VertexConsumer out) {
        float bottom = (NECK + 3) * PIXEL, top = (NECK + 4) * PIXEL;
        // Just in front of the face, behind the hat layer half a pixel out.
        float near = 4.05f * PIXEL, far = 4.25f * PIXEL;
        for (int side = -1; side <= 1; side += 2) {
            float inner = side * PIXEL, outer = side * 3 * PIXEL;
            Ufo.box(at, out, Math.min(inner, outer), bottom, near, Math.max(inner, outer), top, far,
                    255, 255, 255, Ufo.BRIGHT);
        }
    }
}
