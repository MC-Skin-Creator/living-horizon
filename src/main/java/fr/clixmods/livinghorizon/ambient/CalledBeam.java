package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.render.Sink;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * For whoever works on the easter eggs: a beam of light, as a beacon's, rising over one that
 * was called by its command, to find it on the horizon. Never over one that came by chance.
 */
final class CalledBeam {
    /** Blocks above the point it starts at, over the figure's head, and blocks it rises. */
    private static final float FROM = 4, HEIGHT = 256;
    /** Blocks across its core for each block away: about the same width on screen however far. */
    private static final double WIDTH = 1.0 / 150, NARROWEST = 0.3;

    private CalledBeam() {
    }

    /**
     * Rising over {@code (x, y, z)}, seen from {@code camera}: pulled in past the far plane,
     * smaller in proportion, as the easter eggs are.
     */
    static void submit(PoseStack pose, Vec3 camera, Sink collector, double x, double y, double z) {
        double px = x - camera.x, py = y - camera.y, pz = z - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        float core = (float) Math.max(NARROWEST, distance * WIDTH) / 2, glow = core * 2.2f;
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        collector.draw(pose, Ufo.GLOW, (at, out) -> {
            Ufo.box(at, out, -core, FROM, -core, core, FROM + HEIGHT, core, 255, 255, 255, 210, Ufo.BRIGHT);
            Ufo.box(at, out, -glow, FROM, -glow, glow, FROM + HEIGHT, glow, 200, 230, 255, 70, Ufo.BRIGHT);
        });
        pose.popPose();
    }
}
