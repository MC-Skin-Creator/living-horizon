//? if fabric || quilt {
package fr.clixmods.livinghorizon.ui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * The "Configure" button on this mod's page in Mod Menu. Loaded by Mod Menu only. NeoForge
 * and Forge have their own button on the mod list, registered by the entry point.
 */
public final class ModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FarConfigScreen::new;
    }
}
//?}
