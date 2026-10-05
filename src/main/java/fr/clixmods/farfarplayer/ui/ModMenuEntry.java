package fr.clixmods.farfarplayer.ui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** The "Configure" button on this mod's page in Mod Menu. Loaded by Mod Menu only. */
public final class ModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return FarConfigScreen::new;
    }
}
