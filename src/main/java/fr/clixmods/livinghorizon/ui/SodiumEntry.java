//? if (fabric || quilt) && >=1.21.11 {
package fr.clixmods.livinghorizon.ui;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * A "Living Horizon" page in Sodium's video settings, which also shows in the screens built
 * on it (Reese's and Enchanted's Sodium Options). Sodium reads it from the
 * {@code sodium:config_api_user} entry point, from its 0.8; the page opens the mod's own
 * settings screen rather than copying them. Sodium alone loads this class.
 */
public final class SodiumEntry implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        builder.registerOwnModOptions().addPage(builder.createExternalPage()
                .setName(Component.translatable("livinghorizon.options.open"))
                .setScreenConsumer(screen -> Minecraft.getInstance().setScreen(new FarConfigScreen(screen))));
    }
}
//?}
