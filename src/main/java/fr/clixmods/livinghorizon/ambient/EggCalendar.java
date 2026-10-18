package fr.clixmods.livinghorizon.ambient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * When the easter eggs may come by chance, in days of the game this player has lived in a
 * world: each from a day of its own on, never two within a few days of each other; the
 * watcher ({@link Herobrine}) on nights of his own. A day is a dawn seen in the overworld,
 * a night slept through included.
 *
 * <p>Kept per server, in {@code config/livinghorizon/calendar/<server>.json}. Calling an
 * easter egg by its command is none of its business.
 */
public final class EggCalendar {
    /** The first day each easter egg may come by chance, by number; the watcher's is drawn per world. */
    private static final int[] FIRST = {0, 3, 5, 10, 0, 4, 6, 8};

    /** Gson writes every field that is not transient, under names that say nothing of what they count. */
    private static final class State {
        /** Dawns seen. */
        int days;
        /** No easter egg comes by chance before this day: the last one was not long ago. */
        @SerializedName("next")
        int quietUntil;
        /** The watcher comes every night from this day on, until he is looked in the eyes; -1 until drawn. */
        @SerializedName("next4")
        int watcherFrom = -1;
        /** The night he last came, by the day it belongs to; -1 if never. */
        @SerializedName("last4")
        int watcherNight = -1;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final RandomSource random = RandomSource.create();
    private State state = new State();
    private @Nullable Path file;
    private boolean bright, known;

    /** Days lived in this world so far. */
    public int days() {
        return state.days;
    }

    public void open(String server) {
        close();
        file = Platform.configDir().resolve("livinghorizon").resolve("calendar")
                .resolve(RestingPlayers.safe(server) + ".json");
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                State read = GSON.fromJson(reader, State.class);
                if (read != null) state = read;
            } catch (IOException | RuntimeException e) {
                LivingHorizonClient.LOGGER.warn("Could not read {}", file, e);
            }
        }
        if (state.watcherFrom < 0) {
            state.watcherFrom = 2 + random.nextInt(2);
            save();
        }
    }

    public void close() {
        save();
        state = new State();
        file = null;
        known = false;
    }

    /** Counts a dawn when the overworld's sky turns bright. */
    void tick(ClientLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            known = false;
            return;
        }
        boolean now = level.isBrightOutside();
        if (known && now && !bright) {
            state.days++;
            save();
        }
        bright = now;
        known = true;
    }

    /** Whether easter egg {@code number} may come by chance today. */
    boolean allows(int number) {
        return state.days >= FIRST[number] && state.days >= state.quietUntil;
    }

    /** Easter egg {@code number} came by chance: none other for two to four days. */
    void shown(int number) {
        state.quietUntil = state.days + 2 + random.nextInt(3);
        save();
    }

    /** Whether the watcher is due tonight: his day has come and he has not come yet this night. */
    boolean watcherTonight() {
        return state.watcherFrom >= 0 && state.days >= state.watcherFrom && state.watcherNight != state.days;
    }

    /** He came tonight. */
    void watcherCame() {
        state.watcherNight = state.days;
        save();
    }

    /** He was looked in the eyes: back in two to four days. */
    void watcherSeen() {
        state.watcherFrom = state.days + 2 + random.nextInt(3);
        save();
    }

    private void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(state, writer);
            }
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not write {}", file, e);
        }
    }
}
