package fr.clixmods.livinghorizon.track;

import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a server with the mod tells the clients with the mod: where every player is, and the
 * remembered mobs around the one it is sent to. Written with plain Java streams, no type of
 * the game's, so that it reads the same on every loader and version, and in a unit test.
 *
 * <p>Three kinds of message: {@link #PLAYERS} online, a few times a second; {@link #OFFLINE},
 * the players who logged off, and {@link #MOBS}, every few seconds. The server keeps the same lists in the world ({@link PositionPublisher}),
 * written in this format too.
 */
public final class PositionsMessage {
    /** The layout below; a client that reads another one ignores the message. */
    public static final int VERSION = 1;
    public static final byte PLAYERS = 0;
    public static final byte MOBS = 1;
    public static final byte OFFLINE = 2;

    /**
     * One player: online now, or where they were when they logged off. {@code dimension} is
     * {@link SharedPositions#dimensionCode}; {@code vehicle} the published mob they ride, if any.
     */
    public record Player(String name, double x, double y, double z, float yaw, int dimension, boolean online,
                         boolean riding, @Nullable UUID vehicle) {
    }

    /** One remembered mob: its kind ({@link MobKinds}), and what its look needs. */
    public record Mob(UUID id, double x, double y, double z, int kind, int dimension, boolean baby, int variant) {
    }

    /**
     * The mobs of one message, and the circle they were chosen in: a mob remembered inside it
     * and not in the list is gone, one outside it is only out of reach.
     */
    public record Mobs(List<Mob> mobs, int dimension, double x, double z, double radius) {
        public boolean covers(int dimension, double x, double z) {
            return dimension == this.dimension && Math.hypot(x - this.x, z - this.z) <= radius;
        }
    }

    /** A message read: its kind, and the list it carries (the other one is null). */
    public record Decoded(byte kind, @Nullable List<Player> players, @Nullable Mobs mobs) {
    }

    private PositionsMessage() {
    }

    /** The players online now. */
    public static byte[] players(List<Player> players) {
        return write(out -> {
            out.writeByte(PLAYERS);
            writePlayers(out, players);
        });
    }

    /** The players who logged off, where they were last. */
    public static byte[] offline(List<Player> players) {
        return write(out -> {
            out.writeByte(OFFLINE);
            writePlayers(out, players);
        });
    }

    public static byte[] mobs(Mobs mobs) {
        return write(out -> {
            out.writeByte(MOBS);
            out.writeByte(mobs.dimension());
            out.writeDouble(mobs.x());
            out.writeDouble(mobs.z());
            out.writeDouble(mobs.radius());
            writeMobs(out, mobs.mobs());
        });
    }

    /** Null for a message of another version, or one that does not read whole. */
    public static @Nullable Decoded read(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readInt() != VERSION) return null;
            byte kind = in.readByte();
            if (kind == PLAYERS || kind == OFFLINE) return new Decoded(kind, readPlayers(in), null);
            if (kind == MOBS) {
                int dimension = in.readByte();
                double x = in.readDouble(), z = in.readDouble(), radius = in.readDouble();
                return new Decoded(kind, null, new Mobs(readMobs(in), dimension, x, z, radius));
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The server's own file: every player and every mob it knows, in the same layout. */
    static byte[] saved(List<Player> players, List<Mob> mobs) {
        return write(out -> {
            writePlayers(out, players);
            writeMobs(out, mobs);
        });
    }

    /** The server's file read back: the players and the mobs, or null when it does not read. */
    static @Nullable Saved readSaved(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readInt() != VERSION) return null;
            return new Saved(readPlayers(in), readMobs(in));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    record Saved(List<Player> players, List<Mob> mobs) {
    }

    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private static byte[] write(Writer writer) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            writer.write(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static void writePlayers(DataOutputStream out, List<Player> players) throws IOException {
        out.writeInt(players.size());
        for (Player player : players) {
            out.writeUTF(player.name());
            out.writeDouble(player.x());
            out.writeDouble(player.y());
            out.writeDouble(player.z());
            out.writeFloat(player.yaw());
            out.writeByte(player.dimension());
            out.writeByte((player.online() ? 1 : 0) | (player.riding() ? 2 : 0) | (player.vehicle() != null ? 4 : 0));
            if (player.vehicle() != null) writeId(out, player.vehicle());
        }
    }

    private static List<Player> readPlayers(DataInputStream in) throws IOException {
        int count = in.readInt();
        List<Player> players = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            String name = in.readUTF();
            double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
            float yaw = in.readFloat();
            int dimension = in.readByte();
            int flags = in.readByte();
            UUID vehicle = (flags & 4) != 0 ? readId(in) : null;
            players.add(new Player(name, x, y, z, yaw, dimension, (flags & 1) != 0, (flags & 2) != 0, vehicle));
        }
        return players;
    }

    private static void writeMobs(DataOutputStream out, List<Mob> mobs) throws IOException {
        out.writeInt(mobs.size());
        for (Mob mob : mobs) {
            writeId(out, mob.id());
            out.writeDouble(mob.x());
            out.writeDouble(mob.y());
            out.writeDouble(mob.z());
            out.writeShort(mob.kind());
            out.writeByte(mob.dimension() | (mob.baby() ? 0x10 : 0));
            out.writeInt(mob.variant());
        }
    }

    private static List<Mob> readMobs(DataInputStream in) throws IOException {
        int count = in.readInt();
        List<Mob> mobs = new ArrayList<>(Math.min(count, 4096));
        for (int i = 0; i < count; i++) {
            UUID id = readId(in);
            double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
            int kind = in.readShort();
            int flags = in.readByte();
            int variant = in.readInt();
            mobs.add(new Mob(id, x, y, z, kind, flags & 0x0F, (flags & 0x10) != 0, variant));
        }
        return mobs;
    }

    private static void writeId(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readId(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }
}
