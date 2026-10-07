package fr.clixmods.livinghorizon.platform;

import java.nio.file.Path;

//? if fabric {
import net.fabricmc.loader.api.FabricLoader;
//?} elif quilt {
/*import net.fabricmc.loader.api.FabricLoader;
*///?} elif neoforge {
/*import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
*///?} else {
/*import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
*///?}

/**
 * What the mod asks of the loader it runs on, outside of hooking into the game.
 *
 * <p>Fabric, Quilt, NeoForge and Forge answer the same questions under different names, so
 * the answers are read here and nowhere else: the rest of the mod never names a loader.
 * The events live in {@link Events}, and the entry point is the only other loader-facing
 * code, because it is where each loader hands the mod control. Quilt runs the Fabric
 * API, which brings the Fabric Loader API with it, so it reads the Fabric answers.
 */
public final class Platform {
    private Platform() {
    }

    /** The game's config folder, where the mod keeps its settings and what it remembers. */
    public static Path configDir() {
        //? if fabric || quilt {
        return FabricLoader.getInstance().getConfigDir();
        //?} else {
        /*return FMLPaths.CONFIGDIR.get();
        *///?}
    }

    /** Whether a mod is loaded: Voxy and Distant Horizons are only ever looked at when it is. */
    public static boolean isModLoaded(String id) {
        //? if fabric || quilt {
        return FabricLoader.getInstance().isModLoaded(id);
        //?} elif neoforge {
        /*ModList mods = ModList.get();
        return mods != null && mods.isLoaded(id);
        *///?} elif >=26.1 {
        /*// Forge made the mod list static in 26.1.
        return ModList.isLoaded(id);
        *///?} else {
        /*return ModList.get().isLoaded(id);
        *///?}
    }
}
