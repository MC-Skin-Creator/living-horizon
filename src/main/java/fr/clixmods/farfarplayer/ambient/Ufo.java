package fr.clixmods.farfarplayer.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.clixmods.farfarplayer.FarFarPlayerClient;
import fr.clixmods.farfarplayer.compat.VoxyWorld;
import fr.clixmods.farfarplayer.render.GhostRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * An easter egg: now and then, far away, a flying saucer comes down over a cow, lifts it
 * up in a beam of light, and is gone. The saucer, the beam and the cow exist on this
 * screen only - no cow anywhere was harmed.
 *
 * <p>The saucer is a few boxes of colour, Minecraft style, with lights blinking round the
 * rim; the beam is a translucent glowing column; the cow is the game's own, a puppet like
 * the distant mobs.
 */
public final class Ufo {
    private static final Identifier SHEET = Identifier.fromNamespaceAndPath(FarFarPlayerClient.MOD_ID, "textures/misc/birds.png");
    private static final RenderType SOLID = RenderTypes.entityCutout(SHEET);
    private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(SHEET);
    /** A white texel of the bird sheet: every box is that texel, tinted. */
    private static final float U = 7.5f / 64f, V = 1.5f / 32f;
    private static final int BRIGHT = LightTexture.FULL_BRIGHT, LIT = LightTexture.pack(0, 15);

    /** Ticks of the show: arrive, beam on, lift, beam off, wait, leave. */
    private static final int ARRIVED = 240, BEAM = 300, LIFTED = 480, TAKEN = 500, GONE = 520, END = 720;
    /** Blocks from the ground to the saucer's middle while it hovers. */
    private static final double HOVER = 16;

    private final RandomSource random = RandomSource.create();
    private final AtomicReference<double @Nullable []> foundGround = new AtomicReference<>();
    private int tries;
    private boolean active, searching;
    private int age;
    private double groundX, groundY, groundZ;
    private double fromX, fromY, fromZ, awayX, awayZ;
    private double x, y, z, xo, yo, zo;
    private float spin, spinO, tilt, tiltO, beam, beamO, heading;
    private @Nullable LivingEntity cow;
    private boolean cowShown;
    private int idle;

    /** The cow while it is still to be seen. */
    public @Nullable LivingEntity cow() {
        return active && cowShown ? cow : null;
    }

    /** Starts the show somewhere ahead of the player: by chance, or because they asked. */
    public void summon(ClientLevel level, LocalPlayer self, boolean near) {
        if (active || searching) return;
        searching = true;
        tries = 0;
        look(level, self, near);
    }

    public void tick(ClientLevel level, LocalPlayer self, boolean enabled) {
        if (!enabled) {
            active = searching = false;
            return;
        }
        if (!active && !searching) {
            // Rare: about once in a quarter of an hour of night, three quarters of day.
            int chance = level.isBrightOutside() ? 54000 : 18000;
            if (++idle > 1200 && random.nextInt(chance) == 0) {
                idle = 0;
                summon(level, self, false);
            }
            return;
        }
        if (searching) {
            double[] ground = foundGround.getAndSet(null);
            if (ground == null) return;
            if (Double.isNaN(ground[1])) {
                // Water, or nothing known there: look elsewhere, a few times.
                if (++tries < 6) look(level, self, ground[3] > 0);
                else searching = false;
                return;
            }
            begin(level, self, ground[0], ground[1], ground[2]);
            return;
        }
        step();
    }

    /** Picks a column ahead of the player and finds its ground, here or in Voxy's world. */
    private void look(ClientLevel level, LocalPlayer self, boolean near) {
        double bearing = Math.toRadians(self.getYRot()) + random.nextGaussian() * (near ? 0.3 : 0.7);
        double distance = near ? 70 + random.nextDouble() * 80 : 140 + random.nextDouble() * 200;
        int gx = Mth.floor(self.getX() - Math.sin(bearing) * distance);
        int gz = Mth.floor(self.getZ() + Math.cos(bearing) * distance);
        double flag = near ? 1 : 0;
        if (level.hasChunk(gx >> 4, gz >> 4)) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
            boolean wet = !level.getFluidState(new BlockPos(gx, top - 1, gz)).isEmpty();
            foundGround.set(new double[]{gx + 0.5, wet ? Double.NaN : top, gz + 0.5, flag});
        } else {
            VoxyWorld.surface(gx, gz).thenAccept(surface -> foundGround.set(surface.isPresent() && !surface.get().water()
                    ? new double[]{gx + 0.5, surface.get().y() + 1, gz + 0.5, flag}
                    : new double[]{gx + 0.5, Double.NaN, gz + 0.5, flag}));
        }
    }

    private void begin(ClientLevel level, LocalPlayer self, double gx, double gy, double gz) {
        LivingEntity created = EntityType.COW.create(level, EntitySpawnReason.LOAD);
        searching = false;
        if (created == null) return;
        cow = created;
        groundX = gx;
        groundY = gy;
        groundZ = gz;
        float yaw = random.nextFloat() * 360f;
        cow.setPos(gx, gy, gz);
        cow.setOldPosAndRot();
        cow.setYRot(yaw);
        cow.yBodyRot = cow.yBodyRotO = cow.yHeadRot = cow.yHeadRotO = yaw;
        cowShown = true;
        // In from the far side, high up; out the way it came, onwards.
        double away = Math.atan2(gx - self.getX(), gz - self.getZ()) + random.nextGaussian() * 0.5;
        fromX = gx + Math.sin(away) * 420;
        fromZ = gz + Math.cos(away) * 420;
        fromY = gy + 90;
        double leave = away + Math.PI * 0.5 * (random.nextBoolean() ? 1 : -1);
        awayX = Math.sin(leave);
        awayZ = Math.cos(leave);
        x = xo = fromX;
        y = yo = fromY;
        z = zo = fromZ;
        heading = (float) Math.toDegrees(Math.atan2(-(gx - fromX), gz - fromZ));
        age = 0;
        beam = beamO = tilt = tiltO = 0f;
        active = true;
    }

    private void step() {
        age++;
        xo = x;
        yo = y;
        zo = z;
        spinO = spin;
        tiltO = tilt;
        beamO = beam;
        spin += 7f;
        double hoverY = groundY + HOVER + 0.4 * Math.sin(age * 0.15);
        if (age <= ARRIVED) {
            // Gliding in, slowing down over the cow.
            double t = age / (double) ARRIVED, ease = 1 - Math.pow(1 - t, 3);
            x = Mth.lerp(ease, fromX, groundX);
            y = Mth.lerp(ease, fromY, hoverY);
            z = Mth.lerp(ease, fromZ, groundZ);
            tilt = (float) (14 * (1 - ease));
        } else if (age <= GONE) {
            x = groundX;
            y = hoverY;
            z = groundZ;
            tilt = 0f;
            if (age > ARRIVED && age <= BEAM) beam = (age - ARRIVED) / (float) (BEAM - ARRIVED);
            if (age > LIFTED) beam = Math.max(0f, 1f - (age - LIFTED) / (float) (TAKEN - LIFTED));
        } else {
            // Off, faster and faster, nose down into the direction.
            int k = age - GONE;
            heading = (float) Math.toDegrees(Math.atan2(-awayX, awayZ));
            x = groundX + awayX * 0.012 * k * k;
            z = groundZ + awayZ * 0.012 * k * k;
            y = hoverY + 0.004 * k * k + 0.15 * k;
            tilt = Math.min(25f, k * 0.6f);
        }

        if (cow != null) {
            cow.setOldPosAndRot();
            cow.yBodyRotO = cow.yBodyRot;
            cow.yHeadRotO = cow.yHeadRot;
            double cowY = groundY;
            if (age > BEAM && age <= LIFTED) {
                double t = (age - BEAM) / (double) (LIFTED - BEAM);
                double ease = t * t * (3 - 2 * t);
                cowY = Mth.lerp(ease, groundY, groundY + HOVER - 2.8);
                // Turning slowly as it goes up.
                cow.yBodyRot += 2.5f;
                cow.yHeadRot = cow.yBodyRot;
                cow.setYRot(cow.yBodyRot);
            } else if (age > LIFTED) {
                cowY = groundY + HOVER - 2.8;
            }
            cow.setPos(groundX, cowY, groundZ);
            cow.walkAnimation.update(0f, 0.4f, 1f);
            cow.tickCount++;
            cowShown = age <= LIFTED + 4;
        }
        if (age >= END) active = false;
    }

    // --- Drawing --------------------------------------------------------------------------

    public void submit(PoseStack pose, Vec3 camera, SubmitNodeCollector collector, float partial) {
        if (!active) return;
        double px = Mth.lerp(partial, xo, x) - camera.x;
        double py = Mth.lerp(partial, yo, y) - camera.y;
        double pz = Mth.lerp(partial, zo, z) - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        GhostRenderer.noteDistance(distance + 20);
        double reach = Minecraft.getInstance().gameRenderer.getDepthFar() * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        float spinNow = Mth.lerp(partial, spinO, spin), tiltNow = Mth.lerp(partial, tiltO, tilt);
        float beamNow = Mth.lerp(partial, beamO, beam);
        int blink = (age / 5) % 3;

        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);

        if (beamNow > 0.01f) {
            float drop = (float) (Mth.lerp(partial, yo, y) - groundY);
            int alpha = (int) (110 * beamNow);
            collector.submitCustomGeometry(pose, GLOW, (at, out) -> beam(at, out, drop, alpha));
        }

        pose.mulPose(Axis.YP.rotationDegrees(-heading));
        pose.mulPose(Axis.XP.rotationDegrees(tiltNow));
        pose.mulPose(Axis.YP.rotationDegrees(spinNow));
        collector.submitCustomGeometry(pose, SOLID, (at, out) -> saucer(at, out, blink));
        pose.popPose();
    }

    /** The saucer: a wide rim, a hull above and below, a glass dome, lights around. */
    private static void saucer(PoseStack.Pose at, VertexConsumer out, int blink) {
        box(at, out, -4, -1.6f, -4, 4, -0.6f, 4, 105, 108, 118, LIT);       // underside
        box(at, out, -7, -0.6f, -7, 7, 0.4f, 7, 196, 200, 208, LIT);        // rim
        box(at, out, -5, 0.4f, -5, 5, 1.4f, 5, 168, 172, 182, LIT);         // upper hull
        box(at, out, -2.5f, 1.4f, -2.5f, 2.5f, 3.6f, 2.5f, 130, 215, 235, LIT); // dome
        box(at, out, -2, -2.1f, -2, 2, -1.6f, 2, 150, 255, 180, BRIGHT);   // the hatch, glowing
        int[][] colours = {{255, 70, 70}, {255, 220, 70}, {90, 255, 120}};
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float cx = (float) (Math.cos(a) * 7.2), cz = (float) (Math.sin(a) * 7.2);
            int[] c = colours[(i + blink) % 3];
            box(at, out, cx - 0.45f, -0.45f, cz - 0.45f, cx + 0.45f, 0.35f, cz + 0.45f, c[0], c[1], c[2], BRIGHT);
        }
    }

    /** The beam: a glowing column widening from the hatch to the ground. */
    private static void beam(PoseStack.Pose at, VertexConsumer out, float drop, int alpha) {
        float top = 2f, bottom = 3.6f, y0 = -2.1f, y1 = -drop;
        float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i], b = corners[(i + 1) % 4];
            vertex(at, out, a[0] * top, y0, a[1] * top, 170, 255, 190, alpha, BRIGHT, 0, 1, 0);
            vertex(at, out, b[0] * top, y0, b[1] * top, 170, 255, 190, alpha, BRIGHT, 0, 1, 0);
            vertex(at, out, b[0] * bottom, y1, b[1] * bottom, 170, 255, 190, alpha / 3, BRIGHT, 0, 1, 0);
            vertex(at, out, a[0] * bottom, y1, a[1] * bottom, 170, 255, 190, alpha / 3, BRIGHT, 0, 1, 0);
        }
    }

    /** An axis-aligned box, every face wound to face outwards. */
    private static void box(PoseStack.Pose at, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1,
                            int r, int g, int b, int light) {
        face(at, out, r, g, b, light, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        face(at, out, r, g, b, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        face(at, out, r, g, b, light, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        face(at, out, r, g, b, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        face(at, out, r, g, b, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        face(at, out, r, g, b, light, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    /** One face, its corners put in the order that makes it face {@code (nx, ny, nz)}. */
    private static void face(PoseStack.Pose at, VertexConsumer out, int r, int g, int b, int light,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        float ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
        float wx = uy * vz - uz * vy, wy = uz * vx - ux * vz, wz = ux * vy - uy * vx;
        if (wx * nx + wy * ny + wz * nz >= 0) {
            vertex(at, out, ax, ay, az, r, g, b, 255, light, nx, ny, nz);
            vertex(at, out, bx, by, bz, r, g, b, 255, light, nx, ny, nz);
            vertex(at, out, cx, cy, cz, r, g, b, 255, light, nx, ny, nz);
            vertex(at, out, dx, dy, dz, r, g, b, 255, light, nx, ny, nz);
        } else {
            vertex(at, out, ax, ay, az, r, g, b, 255, light, nx, ny, nz);
            vertex(at, out, dx, dy, dz, r, g, b, 255, light, nx, ny, nz);
            vertex(at, out, cx, cy, cz, r, g, b, 255, light, nx, ny, nz);
            vertex(at, out, bx, by, bz, r, g, b, 255, light, nx, ny, nz);
        }
    }

    private static void vertex(PoseStack.Pose at, VertexConsumer out, float x, float y, float z,
                               int r, int g, int b, int a, int light, float nx, float ny, float nz) {
        out.addVertex(at, x, y, z)
                .setColor(r, g, b, a)
                .setUv(U, V)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(at, nx, ny, nz);
    }
}
