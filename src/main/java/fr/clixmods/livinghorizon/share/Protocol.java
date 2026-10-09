package fr.clixmods.livinghorizon.share;

import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a server running the mod tells the clients running it, as bytes. Plain Java, so that
 * it reads the same on every game version and loader: {@code platform/Network} only
 * carries the bytes.
 *
 * <p>Every message starts with the protocol version and its kind. A client drops a message
 * of another version: a server and a client of different releases simply do not talk, and
 * the client falls back on the data pack or the locator bar.
 *
 * <ul>
 *   <li>{@link Players}: every player online, five times a second.</li>
 *   <li>{@link Resting}: where players who logged off were last, all of them when a client
 *       arrives, then each one as they leave.</li>
 *   <li>{@link Mobs}: the mobs around a client, every five seconds, as a change to what it
 *       was sent before: new and moved mobs, and those it should forget (dead, or now too
 *       far). A mob's saved data only travels when it is new to that client or changed.</li>
 * </ul>
 */
public final class Protocol {
    public static final int VERSION = 1;

    private static final int PLAYERS = 1;
    private static final int RESTING = 2;
    private static final int MOBS = 3;

    /** Where a player is, online or last seen. */
    public record Player(UUID id, String name, String dimension, double x, double y, double z, float yaw, boolean riding) {
    }

    /** A mob, and its saved data (as the game writes it, in text) when it travels. */
    public record Mob(UUID id, String type, String dimension, double x, double y, double z, float yaw, boolean named,
                      @Nullable String nbt) {
    }

    public sealed interface Message permits Players, Resting, Mobs {
    }

    public record Players(List<Player> players) implements Message {
    }

    /** {@code full}: every player who logged off, replacing whatever the client had. */
    public record Resting(boolean full, List<Player> players) implements Message {
    }

    public record Mobs(List<Mob> mobs, List<UUID> forgotten) implements Message {
    }

    private Protocol() {
    }

    public static byte[] encode(Message message) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            if (message instanceof Players players) {
                out.writeByte(PLAYERS);
                writePlayers(out, players.players());
            } else if (message instanceof Resting resting) {
                out.writeByte(RESTING);
                out.writeBoolean(resting.full());
                writePlayers(out, resting.players());
            } else if (message instanceof Mobs mobs) {
                out.writeByte(MOBS);
                out.writeInt(mobs.mobs().size());
                for (Mob mob : mobs.mobs()) {
                    writeId(out, mob.id());
                    writeString(out, mob.type());
                    writeString(out, mob.dimension());
                    out.writeDouble(mob.x());
                    out.writeDouble(mob.y());
                    out.writeDouble(mob.z());
                    out.writeFloat(mob.yaw());
                    out.writeBoolean(mob.named());
                    out.writeBoolean(mob.nbt() != null);
                    if (mob.nbt() != null) writeString(out, mob.nbt());
                }
                out.writeInt(mobs.forgotten().size());
                for (UUID id : mobs.forgotten()) writeId(out, id);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** The message, or null when it is of another version or cannot be read. */
    public static @Nullable Message decode(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readUnsignedByte() != VERSION) return null;
            int kind = in.readUnsignedByte();
            switch (kind) {
                case PLAYERS:
                    return new Players(readPlayers(in));
                case RESTING: {
                    boolean full = in.readBoolean();
                    return new Resting(full, readPlayers(in));
                }
                case MOBS: {
                    int count = count(in);
                    List<Mob> mobs = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        UUID id = readId(in);
                        String type = readString(in);
                        String dimension = readString(in);
                        double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
                        float yaw = in.readFloat();
                        boolean named = in.readBoolean();
                        String nbt = in.readBoolean() ? readString(in) : null;
                        mobs.add(new Mob(id, type, dimension, x, y, z, yaw, named, nbt));
                    }
                    int gone = count(in);
                    List<UUID> forgotten = new ArrayList<>(gone);
                    for (int i = 0; i < gone; i++) forgotten.add(readId(in));
                    return new Mobs(mobs, forgotten);
                }
                default:
                    return null;
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void writePlayers(DataOutputStream out, List<Player> players) throws IOException {
        out.writeInt(players.size());
        for (Player player : players) {
            writeId(out, player.id());
            writeString(out, player.name());
            writeString(out, player.dimension());
            out.writeDouble(player.x());
            out.writeDouble(player.y());
            out.writeDouble(player.z());
            out.writeFloat(player.yaw());
            out.writeBoolean(player.riding());
        }
    }

    private static List<Player> readPlayers(DataInputStream in) throws IOException {
        int count = count(in);
        List<Player> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = readId(in);
            String name = readString(in);
            String dimension = readString(in);
            double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
            players.add(new Player(id, name, dimension, x, y, z, in.readFloat(), in.readBoolean()));
        }
        return players;
    }

    /** A count read back, never trusted further than the bytes left could hold. */
    private static int count(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > in.available()) throw new IOException("Bad count " + count);
        return count;
    }

    private static void writeId(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readId(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    /** A string as a length and its UTF-8 bytes: a mob's saved data can pass the 64 KB of writeUTF. */
    private static void writeString(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = count(in);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
