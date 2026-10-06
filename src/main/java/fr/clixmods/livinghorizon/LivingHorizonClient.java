package fr.clixmods.livinghorizon;

import com.mojang.blaze3d.platform.InputConstants;
import fr.clixmods.livinghorizon.compat.VoxyDepth;
import fr.clixmods.livinghorizon.compat.VoxyWorld;
import fr.clixmods.livinghorizon.track.FarPlayer;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.render.Occlusion;
import fr.clixmods.livinghorizon.ui.FarConfigScreen;
import fr.clixmods.livinghorizon.ui.MobList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Locale;

/**
 * Client entry point. There is no server side: everything the mod knows, a vanilla
 * client already receives.
 */
public final class LivingHorizonClient implements ClientModInitializer {
    public static final String MOD_ID = "livinghorizon";
    public static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");

    private KeyMapping toggle;
    private KeyMapping settings;

    @Override
    public void onInitializeClient() {
        FarConfig.load();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        toggle = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.livinghorizon.toggle", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
        settings = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.livinghorizon.settings", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        // Every mod has registered its mobs by now: new kinds are shown far away from the start.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> FarConfig.adoptModdedMobs(MobList.all()));
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> FarPlayerTracker.get().mobs().onLoad(entity));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> FarPlayerTracker.get().mobs().onUnload(entity, level));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommandManager.literal(MOD_ID)
                        .executes(c -> status(c.getSource()))
                        .then(ClientCommandManager.literal("voxy").executes(c -> voxy(c.getSource())))
                        .then(ClientCommandManager.literal("debug").executes(c -> {
                            // Panel and boxes together, on or off.
                            FarConfig config = FarConfig.get();
                            boolean on = !(config.debugHud || config.debugBoxes);
                            config.debugHud = on;
                            config.debugBoxes = on;
                            FarConfig.save();
                            c.getSource().sendFeedback(Component.translatable(
                                    on ? "livinghorizon.debug.on" : "livinghorizon.debug.off"));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("ufo").executes(c -> {
                            Minecraft client = c.getSource().getClient();
                            if (client.level == null || client.player == null) return 0;
                            FarPlayerTracker.get().ambience().ufo().summon(client.level, client.player, true);
                            c.getSource().sendFeedback(Component.translatable("livinghorizon.ufo.summoned"));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("config").executes(c -> {
                            // After the chat screen, which closes once the command has run.
                            Minecraft client = c.getSource().getClient();
                            client.execute(() -> client.setScreen(new FarConfigScreen(null)));
                            return 1;
                        }))));
        LOGGER.info("Living Horizon loaded");
    }

    private void tick(Minecraft minecraft) {
        while (toggle.consumeClick()) {
            FarConfig config = FarConfig.get();
            config.enabled = !config.enabled;
            FarConfig.save();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        config.enabled ? "livinghorizon.toggle.on" : "livinghorizon.toggle.off"), true);
            }
        }
        while (settings.consumeClick()) {
            minecraft.setScreen(new FarConfigScreen(minecraft.screen));
        }
        long started = System.nanoTime();
        FarPlayerTracker.get().tick(minecraft);
        if (minecraft.level != null) Occlusion.tick(minecraft.gameRenderer.getMainCamera().position());
        Stats.tick(System.nanoTime() - started);
    }

    /**
     * {@code /livinghorizon voxy}: whether the mod can see Voxy's terrain. Aim at a far
     * mountain: the answer is how far the first block Voxy knows along that line is.
     */
    private static int voxy(FabricClientCommandSource source) {
        VoxyWorld.Status status = VoxyWorld.status();
        if (!status.voxyLoaded()) {
            source.sendFeedback(Component.translatable("livinghorizon.voxy.missing"));
            return 0;
        }
        if (status.failure() != null) {
            source.sendFeedback(Component.translatable("livinghorizon.voxy.failed", status.failure()));
        }
        source.sendFeedback(Component.translatable("livinghorizon.voxy.depth", VoxyDepth.state()));
        Player self = source.getPlayer();
        Minecraft client = source.getClient();
        VoxyWorld.probe(self.getEyePosition(), self.getLookAngle(), 4096).whenComplete((hit, error) ->
                client.execute(() -> source.sendFeedback(error != null
                        ? Component.translatable("livinghorizon.voxy.probe.error", String.valueOf(error.getMessage()))
                        : Double.isNaN(hit)
                        ? Component.translatable("livinghorizon.voxy.probe.none")
                        : Component.translatable("livinghorizon.voxy.probe.hit", Math.round(hit)))));
        return 1;
    }

    /** {@code /livinghorizon}: who is followed, from what, and how sure the position is. */
    private static int status(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable(FarPlayerTracker.get().sharing()
                ? "livinghorizon.status.pack.on" : "livinghorizon.status.pack.off"));
        source.sendFeedback(Component.translatable("livinghorizon.status.mobs",
                FarPlayerTracker.get().mobs().size(), FarPlayerTracker.get().mobs().shown().size()));
        source.sendFeedback(Component.translatable("livinghorizon.status.cost", Stats.tickMillis(), Stats.extractMillis(),
                Stats.drawn(), Stats.skipped()));
        Collection<FarPlayer> players = FarPlayerTracker.get().players();
        if (players.isEmpty()) {
            source.sendFeedback(Component.translatable("livinghorizon.status.none"));
            return 0;
        }
        source.sendFeedback(Component.translatable("livinghorizon.status.header", players.size()));
        Player self = source.getPlayer();
        ClientPacketListener connection = source.getClient().getConnection();
        for (FarPlayer player : players) {
            PlayerInfo info = connection == null ? null : connection.getPlayerInfo(player.id());
            String name = info == null ? player.id().toString() : info.getProfile().name();
            String key = player.vanished() ? "vanished" : player.source().name().toLowerCase(Locale.ROOT);
            double distance = player.placed() ? Math.hypot(player.x() - self.getX(), player.z() - self.getZ()) : Double.NaN;
            double uncertainty = player.uncertainty();
            source.sendFeedback(Component.translatable("livinghorizon.status.line",
                    name,
                    Component.translatable("livinghorizon.source." + key),
                    Double.isNaN(distance) ? "?" : String.valueOf(Math.round(distance)),
                    Double.isNaN(uncertainty) ? "?" : String.valueOf(Math.round(uncertainty))));
        }
        return players.size();
    }
}
