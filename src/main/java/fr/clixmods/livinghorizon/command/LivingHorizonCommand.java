package fr.clixmods.livinghorizon.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.LodWorld;
import fr.clixmods.livinghorizon.debug.DebugHud;
import fr.clixmods.livinghorizon.track.FarPlayer;
import fr.clixmods.livinghorizon.track.Profiles;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobScan;
import fr.clixmods.livinghorizon.ui.FarConfigScreen;
import fr.clixmods.livinghorizon.ui.ImpostorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.Locale;
import java.util.function.Function;

/**
 * {@code /livinghorizon}, a client command: it never reaches the server. The tree is built
 * for whatever source type the loader's command dispatcher carries - Fabric's own, or the
 * game's {@code CommandSourceStack} - and turns it into a {@link Source} before anything
 * runs, so what the commands do is written once.
 */
public final class LivingHorizonCommand {
    private LivingHorizonCommand() {
    }

    /** What a command needs of the one who ran it, however the loader names it. */
    public interface Source {
        Minecraft client();

        Player player();

        void feedback(Component message);
    }

    /**
     * The tree for the game's own source type, the one NeoForge and Forge hand their client
     * commands: a client command has no server behind it, so the game's client and player
     * are the ones to use, and the answer goes to the chat.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> forGame() {
        return tree(source -> new Source() {
            @Override
            public Minecraft client() {
                return Minecraft.getInstance();
            }

            @Override
            public Player player() {
                return Minecraft.getInstance().player;
            }

            @Override
            public void feedback(Component message) {
                //? if >=26.1 {
                /*Minecraft.getInstance().gui.getChat().addClientSystemMessage(message);
                *///?} else {
                Minecraft.getInstance().gui.getChat().addMessage(message);
                //?}
            }
        });
    }

    /** The command tree, for a dispatcher whose sources {@code source} turns into a {@link Source}. */
    public static <S> LiteralArgumentBuilder<S> tree(Function<S, Source> source) {
        return LiteralArgumentBuilder.<S>literal(LivingHorizonClient.MOD_ID)
                        .executes(c -> status(source.apply(c.getSource())))
                        .then(LiteralArgumentBuilder.<S>literal("lod").executes(c -> lod(source.apply(c.getSource()))))
                        .then(LiteralArgumentBuilder.<S>literal("voxy").executes(c -> lod(source.apply(c.getSource()))))
                        .then(LiteralArgumentBuilder.<S>literal("debug").executes(c -> {
                            // Panel and boxes together, on or off.
                            FarConfig config = FarConfig.get();
                            boolean on = !(config.debugHud || config.debugBoxes);
                            config.debugHud = on;
                            config.debugBoxes = on;
                            DebugHud.setEntry(on);
                            FarConfig.save();
                            source.apply(c.getSource()).feedback(Component.translatable(
                                    on ? "livinghorizon.debug.on" : "livinghorizon.debug.off"));
                            return 1;
                        }))
                        .then(LiteralArgumentBuilder.<S>literal("scan")
                                .executes(c -> scan(source.apply(c.getSource()), FarConfig.get().scanRadius))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("radius",
                                                IntegerArgumentType.integer(MobScan.MIN_RADIUS))
                                        .executes(c -> scan(source.apply(c.getSource()), IntegerArgumentType.getInteger(c, "radius")))))
                        .then(LiteralArgumentBuilder.<S>literal("ufo").executes(c -> {
                            Minecraft client = source.apply(c.getSource()).client();
                            if (client.level == null || client.player == null) return 0;
                            FarPlayerTracker.get().ambience().ufo().summon(client.level, client.player, true);
                            source.apply(c.getSource()).feedback(Component.translatable("livinghorizon.ufo.summoned"));
                            return 1;
                        }))
                        .then(LiteralArgumentBuilder.<S>literal("config").executes(c -> {
                            // After the chat screen, which closes once the command has run.
                            Minecraft client = source.apply(c.getSource()).client();
                            client.execute(() -> client.setScreen(new FarConfigScreen(null)));
                            return 1;
                        }))
                        .then(LiteralArgumentBuilder.<S>literal("impostors").executes(c -> {
                            Minecraft client = source.apply(c.getSource()).client();
                            client.execute(() -> client.setScreen(new ImpostorScreen(null)));
                            return 1;
                        }));
    }

    /** {@code /livinghorizon scan [radius]}: the mobs of the chunks around, read from a single player world. */
    private static int scan(Source source, int radius) {
        Minecraft client = source.client();
        // After the command has run, outside the command dispatcher.
        client.execute(() -> MobScan.start(client, radius));
        return MobScan.available(client) ? 1 : 0;
    }

    /**
     * {@code /livinghorizon lod} ({@code voxy} too): whether the mod can see the far terrain
     * of Voxy or Distant Horizons. Aim at a far mountain: the answer is how far the first
     * block they know along that line is.
     */
    private static int lod(Source source) {
        LodWorld.Status status = LodWorld.status();
        if (!status.loaded()) {
            source.feedback(Component.translatable("livinghorizon.lod.missing"));
            return 0;
        }
        if (status.failure() != null) {
            source.feedback(Component.translatable("livinghorizon.lod.failed", status.name(), status.failure()));
            return 0;
        }
        source.feedback(Component.translatable("livinghorizon.lod.source", status.name()));
        source.feedback(Component.translatable("livinghorizon.lod.depth", FarDepth.state()));
        Player self = source.player();
        Minecraft client = source.client();
        LodWorld.probe(self.getEyePosition(), self.getLookAngle(), 4096).whenComplete((hit, error) ->
                client.execute(() -> {
                    String details = LodWorld.details();
                    if (!details.isEmpty()) source.feedback(Component.translatable("livinghorizon.lod.details", details));
                    source.feedback(error != null
                        ? Component.translatable("livinghorizon.lod.probe.error", status.name(), String.valueOf(error.getMessage()))
                        : Double.isNaN(hit)
                        ? Component.translatable("livinghorizon.lod.probe.none", status.name())
                        : Component.translatable("livinghorizon.lod.probe.hit", status.name(), Math.round(hit)));
                }));
        return 1;
    }

    /** {@code /livinghorizon}: who is followed, from what, and how sure the position is. */
    private static int status(Source source) {
        if (FarPlayerTracker.get().feed().active()) {
            source.feedback(Component.translatable("livinghorizon.status.server.on", FarPlayerTracker.get().feed().mobCount()));
        } else {
            source.feedback(Component.translatable(FarPlayerTracker.get().sharing()
                    ? "livinghorizon.status.pack.on" : "livinghorizon.status.pack.off"));
        }
        source.feedback(Component.translatable("livinghorizon.status.mobs",
                FarPlayerTracker.get().mobs().size(), FarPlayerTracker.get().mobs().shown().size()));
        source.feedback(Component.translatable("livinghorizon.status.cost", Stats.tickMillis(), Stats.extractMillis(),
                Stats.drawn(), Stats.skipped()));
        Collection<FarPlayer> players = FarPlayerTracker.get().players();
        if (players.isEmpty()) {
            source.feedback(Component.translatable("livinghorizon.status.none"));
            return 0;
        }
        source.feedback(Component.translatable("livinghorizon.status.header", players.size()));
        Player self = source.player();
        ClientPacketListener connection = source.client().getConnection();
        for (FarPlayer player : players) {
            PlayerInfo info = connection == null ? null : connection.getPlayerInfo(player.id());
            String name = info == null ? player.id().toString() : Profiles.name(info.getProfile());
            String key = player.vanished() ? "vanished" : player.source().name().toLowerCase(Locale.ROOT);
            double distance = player.placed() ? Math.hypot(player.x() - self.getX(), player.z() - self.getZ()) : Double.NaN;
            double uncertainty = player.uncertainty();
            source.feedback(Component.translatable("livinghorizon.status.line",
                    name,
                    Component.translatable("livinghorizon.source." + key),
                    Double.isNaN(distance) ? "?" : String.valueOf(Math.round(distance)),
                    Double.isNaN(uncertainty) ? "?" : String.valueOf(Math.round(uncertainty))));
        }
        return players.size();
    }
}
