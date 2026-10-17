package fr.clixmods.livinghorizon;

import fr.clixmods.livinghorizon.platform.Network;
//? if fabric || quilt {
import net.fabricmc.api.ModInitializer;
//?} elif neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
*///?} else {
/*import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
*///?}

/**
 * The entry point on both sides, a dedicated server's included: it opens the mod's network
 * channel ({@link Network}), which the server shares the positions through. The client starts
 * from {@link LivingHorizonClient} on top of it.
 *
 * <p>Fabric and Quilt call it as the {@code main} entry point; NeoForge builds it as the mod's
 * common entry point, beside the client's; Forge's annotation has no side, so on Forge this is
 * the mod's only entry point, and it builds the client's on a client only. Nothing here may
 * reach a class of the client's: {@link LivingHorizonClient} is only named inside that branch.
 */
//? if fabric || quilt {
public final class LivingHorizonCommon implements ModInitializer {
    @Override
    public void onInitialize() {
        Network.register();
    }
}
//?} elif neoforge {
/*@Mod("livinghorizon")
public final class LivingHorizonCommon {
    public LivingHorizonCommon(IEventBus modBus) {
        Network.register(modBus);
    }
}
*///?} else {
/*@Mod("livinghorizon")
public final class LivingHorizonCommon {
    //? if <1.21.6 {
    /^// Forge before 56 (1.21.6) builds the mod with no argument.
    public LivingHorizonCommon() {
        Network.register();
        if (FMLEnvironment.dist.isClient()) new LivingHorizonClient();
    }
    ^///?} else {
    public LivingHorizonCommon(FMLJavaModLoadingContext context) {
        Network.register();
        if (FMLEnvironment.dist.isClient()) new LivingHorizonClient(context);
    }
    //?}
}
*///?}
