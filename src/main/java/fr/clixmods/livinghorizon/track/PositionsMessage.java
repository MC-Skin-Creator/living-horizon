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
 * the players who logged off, and {@link #MOBS}, every few seconds, and all three at once to a
 * player who just joined. How a player looks ({@link Look}) and what they ride ({@link Mount})
 * come every few seconds only. The server keeps the same lists in the world
 * ({@link PositionPublisher}), written in this format too, under a version of its own
 * ({@link #FILE_VERSION}).
 */
public final class PositionsMessage {
    /** The layout below; a client that reads another one ignores the message. */
    public static final int VERSION = 2;
    /**
     * The layout of the server's own file ({@link #saved}), apart from the messages': a new
     * message layout must not make every server drop its file. 2 is the one files had when it
     * was still {@link #VERSION}; a new one is read here alongside the older ones.
     */
    static final int FILE_VERSION = 2;
    public static final byte PLAYERS = 0;
    public static final byte MOBS = 1;
    public static final byte OFFLINE = 2;

    /**
     * One player: online now, or where they were when they logged off. {@code dimension} is
     * {@link SharedPositions#dimensionCode}; {@code vehicle} the published mob they ride, if any;
     * {@code mount} what they ride, as the server saves it, and {@code look} how they look: both
     * sent every few seconds only, so null in most messages. {@code lastSeen} is when they logged
     * off, in milliseconds since 1970 (0 when not known), and {@code test} marks a test player the
     * host placed with the debug command.
     */
    public record Player(String name, double x, double y, double z, float yaw, int dimension, boolean online,
                         boolean riding, @Nullable UUID vehicle, @Nullable Mount mount, @Nullable Look look,
                         long lastSeen, boolean test) {
        public Player(String name, double x, double y, double z, float yaw, int dimension, boolean online,
                      boolean riding, @Nullable UUID vehicle) {
            this(name, x, y, z, yaw, dimension, online, riding, vehicle, null, null, 0, false);
        }

        public Player(String name, double x, double y, double z, float yaw, int dimension, boolean online,
                      boolean riding, @Nullable UUID vehicle, @Nullable Mount mount) {
            this(name, x, y, z, yaw, dimension, online, riding, vehicle, mount, null, 0, false);
        }

        /** The same player, without what is sent every few seconds only. */
        public Player plain() {
            return mount == null && look == null ? this
                    : new Player(name, x, y, z, yaw, dimension, online, riding, vehicle, null, null, lastSeen, test);
        }

        /** The same player, with how they look and what they ride. */
        public Player full(@Nullable Mount mount, @Nullable Look look) {
            return new Player(name, x, y, z, yaw, dimension, online, riding, vehicle, mount, look, lastSeen, test);
        }
    }

    /**
     * How a player looks: their skin, as the {@code textures} property of their profile (value
     * and signature; null when the server has none), and what they wear and hold ({@link Gear}).
     */
    public record Look(@Nullable String textures, @Nullable String signature, List<String> gear) {
    }

    /**
     * What a player rode when they logged off, the server's own copy: its type
     * ({@code minecraft:horse}), the mount as the game saves it, where the rider sits on it (the
     * rider's feet from the mount's, in the mount's frame), the way it faced, and the way the
     * rider faced from it.
     */
    public record Mount(String type, String nbt, double seatX, double seatY, double seatZ, float yaw, float riderYaw) {
        /** Past this, a saved mount is left out: a message's text is at most 64 KiB. */
        public static final int MAX_NBT = 32_000;
    }

    /**
     * One remembered mob: its kind ({@link MobKinds}), and what its look needs; {@code extra} is
     * what it wears (saddle, harness, armour, carpet, chest), as the game saves it, or null.
     */
    public record Mob(UUID id, double x, double y, double z, int kind, int dimension, boolean baby, int variant,
                      @Nullable String extra) {
        public Mob(UUID id, double x, double y, double z, int kind, int dimension, boolean baby, int variant) {
            this(id, x, y, z, kind, dimension, baby, variant, null);
        }
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
        return write(FILE_VERSION, out -> {
            writePlayers(out, players);
            writeMobs(out, mobs);
        });
    }

    /** The server's file read back: the players and the mobs, or null when it does not read. */
    static @Nullable Saved readSaved(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readInt() != FILE_VERSION) return null;
            return new Saved(readPlayers(in), readMobs(in));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    record Saved(List<Player> players, List<Mob> mobs) {
    }

    /** The version the server's file says it is in; -1 when it is too short to say. */
    static int savedVersion(byte[] data) {
        if (data.length < 4) return -1;
        return (data[0] & 0xFF) << 24 | (data[1] & 0xFF) << 16 | (data[2] & 0xFF) << 8 | data[3] & 0xFF;
    }

    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }

    private static byte[] write(Writer writer) {
        return write(VERSION, writer);
    }

    private static byte[] write(int version, Writer writer) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(version);
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
            Mount mount = player.mount();
            Look look = player.look();
            out.writeByte((player.online() ? 1 : 0) | (player.riding() ? 2 : 0) | (player.vehicle() != null ? 4 : 0)
                    | (mount != null ? 8 : 0) | (look != null ? 16 : 0) | (player.test() ? 32 : 0)
                    | (player.lastSeen() != 0 ? 64 : 0));
            if (player.vehicle() != null) writeId(out, player.vehicle());
            if (player.lastSeen() != 0) out.writeLong(player.lastSeen());
            if (look != null) {
                writeText(out, look.textures());
                writeText(out, look.signature());
                out.writeByte(look.gear().size());
                for (String item : look.gear()) out.writeUTF(item);
            }
            if (mount != null) {
                out.writeUTF(mount.type());
                out.writeUTF(mount.nbt());
                out.writeDouble(mount.seatX());
                out.writeDouble(mount.seatY());
                out.writeDouble(mount.seatZ());
                out.writeFloat(mount.yaw());
                out.writeFloat(mount.riderYaw());
            }
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
            long lastSeen = (flags & 64) != 0 ? in.readLong() : 0;
            Look look = null;
            if ((flags & 16) != 0) {
                String textures = readText(in), signature = readText(in);
                int slots = in.readByte();
                List<String> gear = new ArrayList<>(slots);
                for (int slot = 0; slot < slots; slot++) gear.add(in.readUTF());
                look = new Look(textures, signature, gear);
            }
            Mount mount = (flags & 8) != 0
                    ? new Mount(in.readUTF(), in.readUTF(), in.readDouble(), in.readDouble(), in.readDouble(),
                            in.readFloat(), in.readFloat())
                    : null;
            players.add(new Player(name, x, y, z, yaw, dimension, (flags & 1) != 0, (flags & 2) != 0, vehicle, mount,
                    look, lastSeen, (flags & 32) != 0));
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
            out.writeByte(mob.dimension() | (mob.baby() ? 0x10 : 0) | (mob.extra() != null ? 0x20 : 0));
            out.writeInt(mob.variant());
            if (mob.extra() != null) out.writeUTF(mob.extra());
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
            String extra = (flags & 0x20) != 0 ? in.readUTF() : null;
            mobs.add(new Mob(id, x, y, z, kind, flags & 0x0F, (flags & 0x10) != 0, variant, extra));
        }
        return mobs;
    }

    /** A text that may be missing; longer than {@code writeUTF} allows, it is left out. */
    private static void writeText(DataOutputStream out, @Nullable String text) throws IOException {
        boolean kept = text != null && text.length() < 16_000;
        out.writeBoolean(kept);
        if (kept) out.writeUTF(text);
    }

    private static @Nullable String readText(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private static void writeId(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readId(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }
}
