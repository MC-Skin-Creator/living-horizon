package fr.clixmods.livinghorizon.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.clixmods.livinghorizon.LivingHorizon;
import fr.clixmods.livinghorizon.platform.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * What a server running the mod shares, in {@code config/livinghorizon-server.json}. Read
 * when the server starts; written back with every setting, so that the file lists them.
 */
public final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Off, the server shares nothing and the clients fall back on the data pack or the locator bar. */
    public boolean enabled = true;
    /** Where every player online is, five times a second. */
    public boolean sharePlayers = true;
    /** Where players who logged off were last: they are shown resting there. */
    public boolean shareOfflinePlayers = true;
    /** The mobs remembered on the server, around each player. */
    public boolean shareMobs = true;
    /** How far from a player, in blocks, the mobs sent to them are. */
    public int mobRadius = 2048;
    /** How many mobs one player is sent at most, the nearest first. */
    public int maxMobsPerPlayer = 2000;

    static ServerConfig load() {
        Path path = Platform.configDir().resolve("livinghorizon-server.json");
        ServerConfig config = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                config = GSON.fromJson(reader, ServerConfig.class);
            } catch (IOException | RuntimeException e) {
                LivingHorizon.LOGGER.warn("Could not read {}, using the defaults", path, e);
            }
        }
        if (config == null) config = new ServerConfig();
        config.mobRadius = Math.max(64, Math.min(config.mobRadius, 1 << 16));
        config.maxMobsPerPlayer = Math.max(0, Math.min(config.maxMobsPerPlayer, 20_000));
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LivingHorizon.LOGGER.warn("Could not write {}", path, e);
        }
        return config;
    }
}
