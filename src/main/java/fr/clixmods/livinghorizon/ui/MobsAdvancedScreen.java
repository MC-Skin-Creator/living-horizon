package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.DistancePreview.Shape;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.Foresight;
import fr.clixmods.livinghorizon.track.MobScan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import static fr.clixmods.livinghorizon.ui.FarConfigScreen.bool;
import static fr.clixmods.livinghorizon.ui.FarConfigScreen.chunks;
import static fr.clixmods.livinghorizon.ui.FarConfigScreen.forgets;
import static fr.clixmods.livinghorizon.ui.FarConfigScreen.unit;

/**
 * The settings of distant mobs and entities that are rarely changed, behind <i>Advanced...</i>
 * under <i>Distant Mobs &amp; Entities</i>: what the mod remembers and how it learns it, which mobs
 * it hides or keeps, and how a copy walks back to its mob once the server sends it again.
 */
public final class MobsAdvancedScreen extends SideOptionsScreen {
    private static final String KEY = "livinghorizon.options.";

    public MobsAdvancedScreen(@Nullable Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "mobsAdvanced.title"));
    }

    @Override
    protected void addOptions() {
        if (list == null) return;
        FarConfig c = FarConfig.get();

        header(Component.translatable(KEY + "mobsAdvanced.memory"));
        list.addSmall(scanButton(), distanceButton(Shape.CHUNKS, chunks("scanRadius", MobScan.MIN_RADIUS,
                MobScan.MAX_RADIUS, c.scanRadius, v -> c.scanRadius = v), 150));
        list.addSmall(foreseeButton(), distanceButton(Shape.CHUNKS, chunks("foreseeRadius", Foresight.MIN_RADIUS,
                Foresight.MAX_RADIUS, c.foreseeRadius, v -> c.foreseeRadius = v), 150));
        list.addSmall(forgets(this, "forgetMobs", () -> FarPlayerTracker.get().mobs().forgetAll()), null);

        header(Component.translatable(KEY + "mobs.creatures"));
        list.addSmall(
                bool("hideUndergroundMobs", c.hideUndergroundMobs, v -> c.hideUndergroundMobs = v),
                bool("rememberNamedMobs", c.rememberNamedMobs, v -> c.rememberNamedMobs = v));

        header(Component.translatable(KEY + "mobsAdvanced.copies"));
        list.addSmall(
                unit("joinMaxDistance", "blocks", 0, 128, c.joinMaxDistance, v -> c.joinMaxDistance = v),
                unit("joinPace", "percent", 25, 400, c.joinPace, v -> c.joinPace = v),
                bool("joinFollowGround", c.joinFollowGround, v -> c.joinFollowGround = v),
                unit("joinGiveUpSeconds", "seconds", 1, 120, c.joinGiveUpSeconds, v -> c.joinGiveUpSeconds = v),
                unit("joinHurryDistance", "blocks", 0, 64, c.joinHurryDistance, v -> c.joinHurryDistance = v),
                unit("joinHurrySpeed", "percent", 100, 400, c.joinHurrySpeed, v -> c.joinHurrySpeed = v));
    }

    /**
     * Reads the mobs of the chunks around from the world itself: only in single player,
     * where the world is in this game. Back to the game, where the chat says what it found.
     */
    private Button scanButton() {
        boolean available = MobScan.available(minecraft);
        Button button = Button.builder(Component.translatable(KEY + "scanMobs"), b -> {
                    FarConfig.save();
                    minecraft.setScreen(null);
                    MobScan.start(minecraft, FarConfig.get().scanRadius);
                })
                .tooltip(Tooltip.create(Component.translatable(
                        KEY + (available ? "scanMobs.tooltip" : "scanMobs.unavailable"))))
                .build();
        button.active = available;
        return button;
    }

    /** <i>Guess Nearby Mobs</i>: in a world played here, or on a server that shares its seed. */
    private Button foreseeButton() {
        boolean available = FarPlayerTracker.get().mobs().foresight().available(minecraft);
        Button button = Button.builder(Component.translatable(KEY + "foreseeMobs"), b -> {
                    FarConfig.save();
                    minecraft.setScreen(null);
                    FarPlayerTracker.get().mobs().foresight().start(minecraft, FarConfig.get().foreseeRadius);
                })
                .tooltip(Tooltip.create(Component.translatable(
                        KEY + (available ? "foreseeMobs.tooltip" : "foreseeMobs.unavailable"))))
                .build();
        button.active = available;
        return button;
    }

    @Override
    public void removed() {
        FarConfig.save();
    }
}
