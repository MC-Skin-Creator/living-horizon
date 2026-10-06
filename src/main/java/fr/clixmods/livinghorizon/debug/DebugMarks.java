package fr.clixmods.livinghorizon.debug;

import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * What became of every distant mob and player this frame, for the debug view: a box of
 * its colour around it, seen through terrain, a label over it, and a count for the panel.
 * The boxes are the game's own debug gizmos, drawn at the end of the world.
 */
public final class DebugMarks {
    /** Why a mob is drawn, or not. */
    public enum Mark {
        /** A copy, drawn by the mod. */
        FAKE(0xFF55FF55),
        /** A real mob the server sends but the game does not draw, drawn by the mod. */
        LIVE(0xFF55FFFF),
        /** A distant player, drawn by the mod. */
        PLAYER(0xFF5599FF),
        /** A real mob the game draws itself. */
        GAME(0xFFFFFFFF),
        /** Not drawn: smaller than about half a pixel. */
        TINY(0xFFFFFF55),
        /** Not drawn: behind terrain. */
        HIDDEN(0xFFFFAA00),
        /** Not drawn: outside the view. */
        OUTSIDE(0xFFFF5555),
        /** Not drawn yet: its copy is being built. */
        WAITING(0xFFAA55FF),
        /** Not drawn: remembered, but past the most mobs shown at once. */
        SPARE(0xFF888888);

        public final int color;

        Mark(int color) {
            this.color = color;
        }

        public String label() {
            return I18n.get("livinghorizon.debug.mark." + name().toLowerCase(Locale.ROOT));
        }
    }

    private static final int[] COUNTS = new int[Mark.values().length];
    private static final int[] LAST = new int[Mark.values().length];
    private static double eyeX, eyeY, eyeZ, pixelsPerRadian;
    private static boolean boxes, labels;

    private DebugMarks() {
    }

    /** Whether anything of the debug view is on: marks cost nothing otherwise. */
    public static boolean active() {
        FarConfig config = FarConfig.get();
        return config.debugBoxes || config.debugLabels || config.debugHud;
    }

    public static boolean gameMobs() {
        FarConfig config = FarConfig.get();
        return config.debugGameMobs && (config.debugBoxes || config.debugLabels);
    }

    /** Wants the mobs left out of the frame altogether, like the spare ones: only for boxes and labels. */
    public static boolean drawing() {
        return boxes || labels;
    }

    public static void begin(Vec3 eye, double pixels) {
        FarConfig config = FarConfig.get();
        eyeX = eye.x;
        eyeY = eye.y;
        eyeZ = eye.z;
        pixelsPerRadian = pixels;
        boxes = config.debugBoxes;
        labels = config.debugLabels;
        java.util.Arrays.fill(COUNTS, 0);
    }

    public static void end() {
        System.arraycopy(COUNTS, 0, LAST, 0, COUNTS.length);
    }

    /** Last frame's count of one mark, for the panel. */
    public static int count(Mark mark) {
        return LAST[mark.ordinal()];
    }

    public static void mark(Entity entity, Mark mark) {
        mark(entity.getX(), entity.getY(), entity.getZ(), entity.getBbWidth(), entity.getBbHeight(), entity.getType(), mark);
    }

    public static void mark(double x, double y, double z, double width, double height, EntityType<?> type, Mark mark) {
        COUNTS[mark.ordinal()]++;
        if (!boxes && !labels) return;
        double dx = x - eyeX, dy = y + height / 2 - eyeY, dz = z - eyeZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        try {
            if (boxes) {
                double half = width / 2;
                Gizmos.cuboid(new AABB(x - half, y, z - half, x + half, y + height, z + half),
                        GizmoStyle.stroke(mark.color, 2f)).setAlwaysOnTop();
                // A dot as well: past a few hundred blocks the box is smaller than its lines.
                Gizmos.point(new Vec3(x, y + height / 2, z), mark.color, 5f).setAlwaysOnTop();
            }
            if (labels) {
                double pixels = Math.max(height, width) / Math.max(distance, 1e-3) * pixelsPerRadian;
                String text = String.format(Locale.ROOT, "%s · %s · %d m · %.1f px", mark.label(),
                        I18n.get(type.getDescriptionId()), Math.round(distance), pixels);
                // A size in the world that grows with the distance: the same few pixels on screen.
                float scale = (float) (16.0 * distance / pixelsPerRadian * 1.4);
                Gizmos.billboardText(text, new Vec3(x, y + height + distance / pixelsPerRadian * 12, z),
                        TextGizmo.Style.forColorAndCentered(mark.color).withScale(scale)).setAlwaysOnTop();
            }
        } catch (IllegalStateException ignored) {
            // No gizmo collector outside the frame: nothing to draw into.
        }
    }
}
