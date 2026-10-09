package fr.clixmods.livinghorizon;

import fr.clixmods.livinghorizon.platform.Network;
import fr.clixmods.livinghorizon.server.ServerShare;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if fabric || quilt {
import net.fabricmc.api.ModInitializer;
//?} elif neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
*///?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
*///?}
//? if forge && >=1.21.6
/*import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;*/

/**
 * Common entry point, run on a client and on a dedicated server alike: the mod's channel,
 * and the server side ({@link ServerShare}), which a single player world runs too. What only
 * the client does starts in {@link LivingHorizonClient}. Nothing reached from here may touch
 * a class that only exists on the client.
 *
 * <p>Fabric and Quilt call {@code onInitialize} ({@code main} in {@code fabric.mod.json});
 * NeoForge builds this class on both sides and {@link LivingHorizonClient} on the client
 * only. Forge builds one class per mod, this one, which starts the client itself when it
 * runs on one.
 */
//? if fabric || quilt {
public final class LivingHorizon implements ModInitializer {
//?} else {
/*@Mod(LivingHorizon.MOD_ID)
public final class LivingHorizon {
*///?}
    public static final String MOD_ID = "livinghorizon";
    public static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");

    //? if fabric || quilt {
    @Override
    public void onInitialize() {
        Network.register();
        ServerShare.install();
    }
    //?} elif neoforge {
    /*public LivingHorizon(IEventBus modBus) {
        Network.register(modBus);
        ServerShare.install();
    }
    *///?} elif >=1.21.6 {
    /*public LivingHorizon(FMLJavaModLoadingContext context) {
        Network.register();
        ServerShare.install();
        if (FMLEnvironment.dist == Dist.CLIENT) LivingHorizonClient.forge(context);
    }
    *///?} else {
    /*public LivingHorizon() {
        Network.register();
        ServerShare.install();
        if (FMLEnvironment.dist == Dist.CLIENT) LivingHorizonClient.forge();
    }
    *///?}
}
