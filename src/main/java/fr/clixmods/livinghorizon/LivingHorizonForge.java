// Forge's annotation has no side, unlike NeoForge's: this entry point loads on a dedicated server
// too, where the client's would reach classes the server has not.
//? if forge {
/*package fr.clixmods.livinghorizon;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/^*
 * Forge's entry point, on both sides. On a client it starts {@link LivingHorizonClient};
 * on a dedicated server there is nothing to start, {@code MinecraftServerMixin} shares the
 * positions by itself. {@link LivingHorizonClient} is only named inside the client branch,
 * so a server never loads it.
 ^/
@Mod(LivingHorizonClient.MOD_ID)
public final class LivingHorizonForge {
    //? if <1.21.6 {
    /^// Forge before 56 (1.21.6) builds the mod with no argument.
    public LivingHorizonForge() {
        if (FMLEnvironment.dist.isClient()) new LivingHorizonClient();
    }
    ^///?} else {
    public LivingHorizonForge(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist.isClient()) new LivingHorizonClient(context);
    }
    //?}
}
*///?}
