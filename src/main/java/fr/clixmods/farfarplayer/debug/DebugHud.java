package fr.clixmods.farfarplayer.debug;

import fr.clixmods.farfarplayer.FarConfig;
import fr.clixmods.farfarplayer.Stats;
import fr.clixmods.farfarplayer.compat.VoxyDepth;
import fr.clixmods.farfarplayer.compat.VoxyWorld;
import fr.clixmods.farfarplayer.debug.DebugMarks.Mark;
import fr.clixmods.farfarplayer.render.Occlusion;
import fr.clixmods.farfarplayer.track.FarPlayerTracker;
import fr.clixmods.farfarplayer.track.MobMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The debug panel, top left: what the mod costs, what became of every distant mob in
 * the last frame (in the colours of their boxes), how Voxy is read and merged, how far
 * this server sends mobs, and which optimisations are on. Also draws the depth view.
 */
public final class DebugHud {
    private static final int WHITE = 0xFFFFFFFF, GREY = 0xFFAAAAAA, ON = 0xFF55FF55, OFF = 0xFFFF5555;

    private DebugHud() {
    }

    public static void render(GuiGraphics graphics) {
        FarConfig config = FarConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (config.debugDepthView != 0) VoxyDepth.drawDebugView(config.debugDepthView);
        if (!config.debugHud || minecraft.player == null || minecraft.getDebugOverlay().showDebugScreen()) return;

        List<Line> lines = new ArrayList<>();
        lines.add(new Line(I18n.get("farfarplayer.debug.hud.title"), WHITE));
        lines.add(new Line(I18n.get("farfarplayer.debug.hud.cost", Stats.tickMillis(), Stats.extractMillis(),
                Stats.drawn(), Stats.skipped()), GREY));
        MobMemory mobs = FarPlayerTracker.get().mobs();
        int waiting = 0, built = 0;
        for (MobMemory.Remembered mob : mobs.shown()) {
            if (mob.puppet() != null) built++;
            else if (mob.waiting()) waiting++;
        }
        lines.add(new Line(I18n.get("farfarplayer.debug.hud.mobs", mobs.size(), mobs.shown().size(), built, waiting,
                mobs.live().size()), GREY));
        for (Mark mark : Mark.values()) {
            lines.add(new Line("■ " + mark.label() + " : " + DebugMarks.count(mark), mark.color));
        }
        lines.add(new Line(I18n.get("farfarplayer.debug.hud.depth", VoxyDepth.state(),
                I18n.get(VoxyDepth.mergedLastFrame() ? "farfarplayer.debug.yes" : "farfarplayer.debug.no")), GREY));
        lines.add(new Line(I18n.get("farfarplayer.debug.hud.voxy", VoxyWorld.threads(), VoxyWorld.pending(),
                VoxyWorld.columnsKnown()), GREY));
        if (config.hideOccludedMobs) {
            lines.add(new Line(I18n.get("farfarplayer.debug.hud.hidden", Occlusion.hiddenCount()), GREY));
        }
        StringBuilder reach = new StringBuilder();
        for (Map.Entry<MobCategory, Double> entry : mobs.reach().entrySet()) {
            if (!reach.isEmpty()) reach.append(", ");
            reach.append(entry.getKey().getName()).append(' ').append(Math.round(entry.getValue())).append(" m");
        }
        lines.add(new Line(I18n.get("farfarplayer.debug.hud.reach", reach.isEmpty() ? "?" : reach), GREY));
        lines.add(new Line(I18n.get("farfarplayer.debug.hud.opts"), GREY));
        String[] names = {"view", "tiny", "lazy", "parallel", "cache", "background"};
        boolean[] values = {config.optViewCulling, config.optTinyCulling, config.optLazyVoxyDepth,
                config.optParallelVoxy, config.optColumnCache, config.optBackgroundBuild};
        for (int i = 0; i < names.length; i++) {
            lines.add(new Line("  " + (values[i] ? "✔ " : "✘ ") + I18n.get("farfarplayer.debug.opt." + names[i]),
                    values[i] ? ON : OFF));
        }
        if (config.debugDepthView != 0) {
            lines.add(new Line(I18n.get("farfarplayer.debug.hud.view",
                    I18n.get("farfarplayer.debug.depth." + config.debugDepthView)), GREY));
        }

        Font font = minecraft.font;
        int width = 0;
        for (Line line : lines) width = Math.max(width, font.width(line.text));
        int x = 4, y = 4, height = lines.size() * (font.lineHeight + 1);
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 1, 0x90000000);
        for (Line line : lines) {
            graphics.drawString(font, line.text, x, y, line.color, true);
            y += font.lineHeight + 1;
        }
    }

    private record Line(String text, int color) {
    }
}
