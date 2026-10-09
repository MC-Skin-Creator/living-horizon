package fr.clixmods.livinghorizon.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

//? if fabric || quilt {
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
//?} elif neoforge {
/*import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
*///?} else {
/*import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
*///?}

/**
 * The server's events the mod listens to, under one name on every loader: the counterpart
 * of {@link Events} on the server side. Common code, which a dedicated server loads: no
 * client class may appear here.
 */
public final class ServerEvents {
    private ServerEvents() {
    }

    /** Every server tick, after the server has done its own. */
    public static void tick(Consumer<MinecraftServer> listener) {
        //? if fabric || quilt {
        ServerTickEvents.END_SERVER_TICK.register(listener::accept);
        //?} elif neoforge {
        /*NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> listener.accept(event.getServer()));
        *///?} elif >=1.20.4 {
        /*TickEvent.ServerTickEvent.Post.BUS.addListener(event -> listener.accept(ServerLifecycleHooks.getCurrentServer()));
        *///?} else {
        /*// Before Forge 49 (1.20.4) one event stands for both ends of the tick.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) listener.accept(ServerLifecycleHooks.getCurrentServer());
        });
        *///?}
    }

    /** When the server is about to stop, its worlds still there to be saved into. */
    public static void stopping(Consumer<MinecraftServer> listener) {
        //? if fabric || quilt {
        ServerLifecycleEvents.SERVER_STOPPING.register(listener::accept);
        //?} elif neoforge {
        /*NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> listener.accept(event.getServer()));
        *///?} else {
        /*ServerStoppingEvent.BUS.addListener(event -> listener.accept(event.getServer()));
        *///?}
    }

    /** An entity leaves a server world: unloaded with its chunk, or removed for good. */
    public static void entityUnload(BiConsumer<Entity, ServerLevel> listener) {
        //? if fabric || quilt {
        ServerEntityEvents.ENTITY_UNLOAD.register(listener::accept);
        //?} elif neoforge {
        /*NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) listener.accept(event.getEntity(), level);
        });
        *///?} else {
        /*EntityLeaveLevelEvent.BUS.addListener(event -> {
            if (event.getLevel() instanceof ServerLevel level) listener.accept(event.getEntity(), level);
        });
        *///?}
    }
}
