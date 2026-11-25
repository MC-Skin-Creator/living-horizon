package fr.clixmods.livinghorizon.debug;

// The F3 screen lists entries a mod can add from 1.21.9; before, this one is not shown.
//? if >=1.21.9 {
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.ambient.AmbientRenderer;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import net.minecraft.client.gui.components.debug.DebugEntryCategory;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
//? if >=26.4 {
/*import net.minecraft.client.core.registries.ClientRegistries;
import net.minecraft.client.gui.components.debug.DebugGroup;
import net.minecraft.resources.ResourceKey;
*///?}
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * The F3 screen's line for the mod's own entities: how many polygons the distant players
 * and mobs cost, and how many of them are impostors; and how many birds are drawn, as models
 * or as pictures. The game's own entities are not in it.
 */
public final class FakeEntitiesDebugEntry implements DebugScreenEntry {
    //? if >=26.4 {
    /*public static final ResourceKey<DebugScreenEntry> ID = ResourceKey.create(ClientRegistries.DEBUG_SCREEN_ENTRY,
            Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "fake_entities"));
    // 26.4 has no loose lines: they go into a group without a title.
    private static final DebugGroup GROUP = DebugGroup.Builder.titleless().build();
    *///?} else {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "fake_entities");
    //?}

    @Override
    public void display(DebugScreenDisplayer displayer, @Nullable Level level, @Nullable LevelChunk clientChunk,
                        @Nullable LevelChunk serverChunk) {
        FarConfig config = FarConfig.get();
        if (!config.anyDistant() && !config.skyBirds) return;
        line(displayer, I18n.get("livinghorizon.debug.f3.polygons", PolygonStats.polygons(), PolygonStats.triangles(),
                PolygonStats.models(), PolygonStats.modelPolygons(), PolygonStats.impostors()));
        if (config.skyBirds) {
            line(displayer, I18n.get("livinghorizon.debug.f3.birds", AmbientRenderer.drawn(), AmbientRenderer.asModels(),
                    AmbientRenderer.asPictures()));
        }
        if (config.anyDistant() && config.anyImpostors() || config.birdImpostors()) {
            line(displayer, I18n.get(ImpostorAtlas.failed() ? "livinghorizon.debug.f3.impostorsFailed"
                    : "livinghorizon.debug.f3.impostors", ImpostorAtlas.sheets(), ImpostorAtlas.waiting()));
        }
    }

    private static void line(DebugScreenDisplayer displayer, String line) {
        //? if >=26.4 {
        /*displayer.addToGroup(GROUP, line);
        *///?} else {
        displayer.addLine(line);
        //?}
    }

    @Override
    public DebugEntryCategory category() {
        return DebugEntryCategory.RENDERER;
    }
}
//?}
