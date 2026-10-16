package fr.clixmods.livinghorizon.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.ambient.Ufo;
import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.compat.LodWorld;
import fr.clixmods.livinghorizon.debug.DebugHud;
import fr.clixmods.livinghorizon.track.FarPlayer;
import fr.clixmods.livinghorizon.track.LastSeen;
import fr.clixmods.livinghorizon.track.Profiles;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobScan;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.SharedPositions;
import fr.clixmods.livinghorizon.ui.FarConfigScreen;
import fr.clixmods.livinghorizon.ui.ImpostorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.time.ZoneId;
import java.util.Collection;
import java.util.Locale;
import java.util.function.BiPredicate;
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
                        })
                                .then(offlineTests(source)))
                        .then(LiteralArgumentBuilder.<S>literal("scan")
                                .executes(c -> scan(source.apply(c.getSource()), FarConfig.get().scanRadius))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("radius",
                                                IntegerArgumentType.integer(MobScan.MIN_RADIUS))
                                        .executes(c -> scan(source.apply(c.getSource()), IntegerArgumentType.getInteger(c, "radius")))))
                        // The easter eggs, numbered, and only for whoever works on them (EGGS).
                        .then(LiteralArgumentBuilder.<S>literal("ee1").requires(c -> EGGS).executes(c -> {
                            Minecraft client = source.apply(c.getSource()).client();
                            if (client.level == null || client.player == null) return 0;
                            Ufo.Call call = FarPlayerTracker.get().ambience().ufo().summon(client.level, client.player, true);
                            source.apply(c.getSource()).feedback(Component.translatable(call == Ufo.Call.NOTHING
                                    ? NOTHING : "livinghorizon.ee1." + call.name().toLowerCase(Locale.ROOT)));
                            return call == Ufo.Call.NOTHING ? 0 : 1;
                        }))
                        .then(LiteralArgumentBuilder.<S>literal("ee2").requires(c -> EGGS).executes(c -> egg(source.apply(c.getSource()), "ee2",
                                (level, player) -> FarPlayerTracker.get().ambience().pass().summon(level, player, true))))
                        .then(LiteralArgumentBuilder.<S>literal("ee3").requires(c -> EGGS).executes(c -> egg(source.apply(c.getSource()), "ee3",
                                (level, player) -> FarPlayerTracker.get().ambience().ghosts().summon(level, player, true))))
                        .then(LiteralArgumentBuilder.<S>literal("ee4").requires(c -> EGGS).executes(c -> egg(source.apply(c.getSource()), "ee4",
                                (level, player) -> FarPlayerTracker.get().ambience().herobrine().summon(level, player, true))))
                        .then(LiteralArgumentBuilder.<S>literal("ee5").requires(c -> EGGS).executes(c -> egg(source.apply(c.getSource()), "ee5",
                                (level, player) -> FarPlayerTracker.get().ambience().waver().summon(level, player, true))))
                        .then(LiteralArgumentBuilder.<S>literal("ee6").requires(c -> EGGS).executes(c -> egg(source.apply(c.getSource()), "ee6",
                                (level, player) -> FarPlayerTracker.get().ambience().bigfoot().summon(level, player, true))))
                        .then(LiteralArgumentBuilder.<S>literal("ee7").requires(c -> EGGS).executes(c -> egg(source.apply(c.getSource()), "ee7",
                                (level, player) -> FarPlayerTracker.get().ambience().ghostBoat().summon(level, player, true))))
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

    /**
     * {@code /livinghorizon debug offline test ...}: test players who logged off, to try the
     * feature alone. {@code spawn [name] [minutes]} places one where you stand, wearing your
     * skin, logged off that many minutes ago; {@code clear} removes them; {@code list} shows
     * everyone resting on this server. Test players are never written to the file.
     */
    private static <S> LiteralArgumentBuilder<S> offlineTests(Function<S, Source> source) {
        return LiteralArgumentBuilder.<S>literal("offline").then(LiteralArgumentBuilder.<S>literal("test")
                .then(LiteralArgumentBuilder.<S>literal("spawn")
                        .executes(c -> spawnOffline(source.apply(c.getSource()), null, 0))
                        .then(RequiredArgumentBuilder.<S, String>argument("name", StringArgumentType.word())
                                .executes(c -> spawnOffline(source.apply(c.getSource()),
                                        StringArgumentType.getString(c, "name"), 0))
                                .then(RequiredArgumentBuilder.<S, Integer>argument("minutes", IntegerArgumentType.integer(0))
                                        .executes(c -> spawnOffline(source.apply(c.getSource()),
                                                StringArgumentType.getString(c, "name"),
                                                IntegerArgumentType.getInteger(c, "minutes"))))))
                .then(LiteralArgumentBuilder.<S>literal("clear").executes(c -> {
                    int removed = FarPlayerTracker.get().resting().clearFakes();
                    source.apply(c.getSource()).feedback(Component.translatable("livinghorizon.offline.test.cleared", removed));
                    return removed;
                }))
                .then(LiteralArgumentBuilder.<S>literal("list").executes(c -> listOffline(source.apply(c.getSource())))));
    }

    private static int spawnOffline(Source source, @Nullable String name, int minutesAgo) {
        Player self = source.player();
        ClientPacketListener connection = source.client().getConnection();
        if (self == null || connection == null) return 0;
        RestingPlayers resting = FarPlayerTracker.get().resting();
        if (name == null) {
            int n = 1;
            while (taken(resting, "Sleeper" + n)) n++;
            name = "Sleeper" + n;
        }
        if (name.length() > 16) {
            source.feedback(Component.translatable("livinghorizon.offline.test.long", name));
            return 0;
        }
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            if (Profiles.name(info.getProfile()).equalsIgnoreCase(name)) {
                // Online: they would be woken up on the next tick.
                source.feedback(Component.translatable("livinghorizon.offline.test.online", name));
                return 0;
            }
        }
        resting.fake(name, self.getGameProfile(), SharedPositions.dimensionCode(self.level().dimension()),
                self.getX(), self.getY(), self.getZ(), self.getYRot(), minutesAgo);
        source.feedback(Component.translatable("livinghorizon.offline.test.spawned", name));
        if (!FarConfig.get().offlinePlayers) {
            source.feedback(Component.translatable("livinghorizon.offline.test.switchedOff", name));
        }
        return 1;
    }

    private static boolean taken(RestingPlayers resting, String name) {
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (spot.name().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Everyone resting on this server: where, and since when. */
    private static int listOffline(Source source) {
        RestingPlayers resting = FarPlayerTracker.get().resting();
        Collection<RestingPlayers.Spot> spots = resting.spots();
        if (spots.isEmpty()) {
            source.feedback(Component.translatable("livinghorizon.offline.test.none"));
            return 0;
        }
        source.feedback(Component.translatable("livinghorizon.offline.test.header", spots.size()));
        String language = source.client().getLanguageManager().getSelected();
        for (RestingPlayers.Spot spot : spots) {
            String date = LastSeen.format(spot.lastSeen(), language, ZoneId.systemDefault());
            source.feedback(Component.translatable("livinghorizon.offline.test.line",
                    spot.name(),
                    Component.translatable("livinghorizon.offline.test.dimension." + Math.clamp(spot.dimension(), 0, 3)),
                    Math.round(spot.x()), Math.round(spot.y()), Math.round(spot.z()),
                    date == null ? Component.translatable("livinghorizon.offline.test.unknown") : Component.literal(date),
                    resting.isFake(spot) ? Component.translatable("livinghorizon.offline.test.mark") : Component.empty()));
        }
        return spots.size();
    }

    /**
     * The easter eggs' commands, for whoever works on them: the game started with
     * {@code -Dlivinghorizon.ee=true}. Without it they are not there at all, not even among
     * the suggestions: nothing in the game tells a player which easter eggs there are.
     */
    private static final boolean EGGS = Boolean.getBoolean("livinghorizon.ee");

    /** What an easter egg answers when it cannot come: the off switch, the wrong place. */
    private static final String NOTHING = "livinghorizon.ee.nothing";

    /** Calls an easter egg now; it answers {@code livinghorizon.<egg>.on}, or {@link #NOTHING}. */
    private static int egg(Source source, String egg, BiPredicate<ClientLevel, LocalPlayer> call) {
        Minecraft client = source.client();
        if (client.level == null || client.player == null) return 0;
        boolean on = call.test(client.level, client.player);
        source.feedback(Component.translatable(on ? "livinghorizon." + egg + ".on" : NOTHING));
        return on ? 1 : 0;
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
        source.feedback(Component.translatable(FarPlayerTracker.get().sharing()
                ? "livinghorizon.status.pack.on" : "livinghorizon.status.pack.off"));
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
