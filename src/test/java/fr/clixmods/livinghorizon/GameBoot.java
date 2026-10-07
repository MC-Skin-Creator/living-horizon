package fr.clixmods.livinghorizon;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Starts the game's registries for a test, on whichever loader it runs. */
public final class GameBoot {
    private GameBoot() {
    }

    public static void start() {
        SharedConstants.tryDetectVersion();
        //? if forge && <1.21.10 {
        /*// Older Forge reaches for its list of mods while the game boots; a test has none, so
        // an empty one is handed to its loader, where the launcher would have put the real one.
        try {
            java.lang.reflect.Field list = net.minecraftforge.fml.loading.FMLLoader.class.getDeclaredField("loadingModList");
            list.setAccessible(true);
            net.minecraftforge.fml.loading.LoadingModList mods =
                    net.minecraftforge.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(), null);
            mods.setBrokenFiles(java.util.List.of());
            list.set(null, mods);
            // And the module layers it would have built: the test's own one stands for them all.
            Class<?> layers = Class.forName("cpw.mods.modlauncher.api.IModuleLayerManager");
            Object manager = java.lang.reflect.Proxy.newProxyInstance(layers.getClassLoader(), new Class<?>[]{layers},
                    (proxy, method, args) -> method.getName().equals("getLayer")
                            ? java.util.Optional.of(GameBoot.class.getModule().getLayer() != null
                            ? GameBoot.class.getModule().getLayer() : ModuleLayer.boot())
                            : null);
            java.lang.reflect.Field layerField = net.minecraftforge.fml.loading.FMLLoader.class.getDeclaredField("moduleLayerManager");
            layerField.setAccessible(true);
            layerField.set(null, manager);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        *///?}
        //? if forge && <1.20.2 {
        /*try {
            Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError networkOnly) {
            // Forge 47 loads its network classes last of all, whose events only its launcher
            // can build: the game is booted by then, and a test needs none of them.
        }
        *///?} else
        Bootstrap.bootStrap();
        //? if forge {
        /*// Forge bakes the block states when it freezes its registries, which a test never
        // does by itself: until then no block blocks motion, and no water holds a fluid.
        // Before 55 (1.21.5) it froze them while the game booted, and cannot do it twice,
        // without baking them, nor even listing them: they are baked here from the blocks
        // themselves, which costs little.
        *///?}
        //? if forge && >=1.21.5
        /*net.minecraftforge.registries.GameData.freezeData();*/
        //? if forge {
        /*net.minecraft.core.registries.BuiltInRegistries.BLOCK.forEach(block ->
                block.getStateDefinition().getPossibleStates().forEach(state -> state.initCache()));
        *///?}
        //? if forge && <1.21.2 {
        /*// Older Forge gives every living entity attributes of its own, which only its mod
        // registers: in a test, they are registered here from its own list, past the
        // registry's locks, and its handles to them pointed at what was registered.
        try {
            var attributes = (net.minecraft.core.MappedRegistry<net.minecraft.world.entity.ai.attributes.Attribute>)
                    net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE;
            attributes.unfreeze();
            java.lang.reflect.Field locked = attributes.getClass().getDeclaredField("locked");
            locked.setAccessible(true);
            locked.setBoolean(attributes, false);
            java.lang.reflect.Field deferred = net.minecraftforge.common.ForgeMod.class.getDeclaredField("ATTRIBUTES");
            deferred.setAccessible(true);
            java.lang.reflect.Field entries = net.minecraftforge.registries.DeferredRegister.class.getDeclaredField("entries");
            entries.setAccessible(true);
            java.lang.reflect.Method update = net.minecraftforge.registries.RegistryObject.class
                    .getDeclaredMethod("updateReference", net.minecraft.core.Registry.class);
            update.setAccessible(true);
            var own = (java.util.Map<net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute>,
                    java.util.function.Supplier<? extends net.minecraft.world.entity.ai.attributes.Attribute>>)
                    entries.get(deferred.get(null));
            for (var entry : own.entrySet()) {
                net.minecraft.core.Registry.register(attributes, entry.getKey().getId(), entry.getValue().get());
                update.invoke(entry.getKey(), attributes);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        *///?}
    }
}
