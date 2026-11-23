package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The players who logged off, in the player list (Tab), after those online: their head, their
 * name in grey, and the "no connection" bars. Every one known on this server,
 * in any dimension, the last to leave first, when the server allows it
 * ({@code ServerConfig#offlineInTabList}; a server without the mod says nothing, and this
 * client lists those it saw itself).
 */
public final class OfflineTabList {
    /** Rows the game's list holds at most: 4 columns of 20. */
    public static final int MAX_ROWS = 80;

    /** An entry per player, kept while their skin stays the same: its skin is looked up once. */
    private static final Map<String, Entry> ENTRIES = new HashMap<>();

    private record Entry(@Nullable String textures, OfflineInfo info) {
    }

    private OfflineTabList() {
    }

    /** Whether there is anyone to list: the list then opens even in a world played alone. */
    public static boolean any() {
        if (!shown()) return false;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return false;
        for (RestingPlayers.Spot spot : FarPlayerTracker.get().resting().spots()) {
            if (connection.getPlayerInfo(spot.name()) == null) return true;
        }
        return false;
    }

    /** {@code online}, the game's list, followed by those who logged off, within the rows it holds. */
    public static List<PlayerInfo> after(List<PlayerInfo> online) {
        if (online.size() >= MAX_ROWS || !shown()) return online;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return online;
        List<RestingPlayers.Spot> spots = new ArrayList<>();
        for (RestingPlayers.Spot spot : FarPlayerTracker.get().resting().spots()) {
            if (connection.getPlayerInfo(spot.name()) == null) spots.add(spot);
        }
        if (spots.isEmpty()) return online;
        spots.sort(Comparator.comparingLong(RestingPlayers.Spot::lastSeen).reversed()
                .thenComparing(spot -> spot.name().toLowerCase(Locale.ROOT)));
        List<PlayerInfo> all = new ArrayList<>(online);
        for (RestingPlayers.Spot spot : spots) {
            if (all.size() >= MAX_ROWS) break;
            all.add(entry(spot));
        }
        if (ENTRIES.size() > 4 * MAX_ROWS) ENTRIES.clear();
        return all;
    }

    private static boolean shown() {
        FarConfig config = FarConfig.get();
        return config.offlinePlayers && config.offlineTabList && FarPlayerTracker.get().shared().offlineListed();
    }

    private static PlayerInfo entry(RestingPlayers.Spot spot) {
        String key = spot.name().toLowerCase(Locale.ROOT);
        Entry entry = ENTRIES.get(key);
        if (entry == null || !Objects.equals(entry.textures(), spot.textures())) {
            entry = new Entry(spot.textures(), new OfflineInfo(spot));
            ENTRIES.put(key, entry);
        }
        return entry.info();
    }

    /** A player who logged off, as the list shows one: no ping, the name in grey. */
    private static final class OfflineInfo extends PlayerInfo {
        private final Component name;

        OfflineInfo(RestingPlayers.Spot spot) {
            super(spot.profile(), false);
            name = Component.literal(spot.name()).withStyle(ChatFormatting.GRAY);
        }

        /** Below 0: the list draws the bars of a player with no connection. */
        @Override
        public int getLatency() {
            return -1;
        }

        @Override
        public Component getTabListDisplayName() {
            return name;
        }

        /** The outer layer of their head, as the list shows anyone's who wears it; unknown before 1.21.2. */
        public boolean showHat() {
            return true;
        }
    }
}
