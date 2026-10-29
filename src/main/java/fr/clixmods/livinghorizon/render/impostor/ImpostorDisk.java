package fr.clixmods.livinghorizon.render.impostor;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The mobs' sheets kept on disk, so that a mob met in an earlier game is not baked again:
 * {@code config/livinghorizon/impostors/<fingerprint>/<namespace+kind>/<appearance>.bin}, each the
 * mip levels of one sheet, compressed.
 *
 * <p>The fingerprint is everything that changes how a figure is drawn: the game version, the
 * resource packs chosen and their files, the mods' files, and how a sheet is baked. When any
 * of them changes, the folder of the old fingerprint is thrown away and the mobs are baked
 * again. The players' sheets are never kept: they come and go. Each folder holds the text its
 * fingerprint was made from, {@code fingerprint.txt}: when it is thrown away, the log says
 * which lines changed.
 *
 * <p>Each sheet carries when it was baked and in which world, for the impostor screen.
 *
 * <p>Files are read and written on a thread of their own; the render thread only asks and,
 * frames later, uploads what was read ({@link ImpostorAtlas}).
 */
public final class ImpostorDisk {
    /** Bumped whenever a sheet is baked differently: part of the fingerprint, every sheet is baked again. */
    private static final int BAKE = 2;
    /**
     * Bumped whenever the file holds something else. 3 added when and where a sheet was baked;
     * a file of 2 is still read, its date unknown.
     */
    private static final int FORMAT = 3;
    private static final int MAGIC = 0x4C484953;
    /** Sheet files kept in all: the ones read or written longest ago go first. */
    private static final int KEEP = 4096;

    /**
     * How many times larger than {@link ImpostorViews#TILE} the tiles of a sheet on disk are:
     * always 1 now, still written so that the format stays the same.
     */
    static final int SCALE = 1;

    /**
     * A sheet as read from disk: its size in blocks, the rows of each mip level, when it was baked
     * (milliseconds since 1970, 0 when not known) and in which world (empty when not known).
     */
    public record Stored(double worldSize, int[][] levels, long bakedAt, String world) {
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Living Horizon impostors");
        thread.setDaemon(true);
        return thread;
    });

    /** The folder of the current fingerprint; null until worked out again after a reload. */
    private static @Nullable Path folder;
    /** The sheets in {@link #folder}, listed when it is worked out. */
    private static final Set<ImpostorKey> SAVED = new HashSet<>();
    /** What {@link ImpostorKey#learnt()} was when last saved. */
    private static int savedLearnt;

    private ImpostorDisk() {
    }

    /** The packs or the mods may have changed: the fingerprint is worked out again. */
    public static void reset() {
        folder = null;
        SAVED.clear();
    }

    /** Throws every saved sheet away: they are all baked again. */
    public static void clear() {
        Path old = folder();
        SAVED.clear();
        savedLearnt = ImpostorKey.learnt() - 1;
        IO.execute(() -> delete(old));
    }

    /** Whether a sheet is on disk, never for a player. */
    public static boolean has(ImpostorKey key) {
        folder();
        return SAVED.contains(key);
    }

    /** Reads a sheet, on the disk's thread: null when it cannot be read, and then forgotten. */
    public static CompletableFuture<@Nullable Stored> read(ImpostorKey key) {
        Path file = file(folder(), key);
        return CompletableFuture.supplyAsync(() -> {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
                Stored stored = decode(in);
                // Read again: kept over the ones not met for longer.
                if (stored != null) file.toFile().setLastModified(System.currentTimeMillis());
                return stored;
            } catch (IOException | RuntimeException e) {
                LivingHorizonClient.LOGGER.warn("Could not read the impostor {}, baking it again", file, e);
                return null;
            }
        }, IO);
    }

    /** A sheet that could not be read: dropped, and baked again. */
    public static void forget(ImpostorKey key) {
        SAVED.remove(key);
        Path file = file(folder(), key);
        IO.execute(() -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // Written over by the next bake.
            }
        });
    }

    /** Saves a mob's sheet just baked, when and where, and what has been learnt to leave out of keys. */
    public static void write(ImpostorKey key, double worldSize, int[][] levels, long bakedAt, String world) {
        Path root = folder();
        Path file = file(root, key);
        SAVED.add(key);
        Map<String, Map<Integer, Long>> restless = null;
        if (ImpostorKey.learnt() != savedLearnt) {
            savedLearnt = ImpostorKey.learnt();
            restless = ImpostorKey.restless();
        }
        Map<String, Map<Integer, Long>> learnt = restless;
        IO.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                Path part = file.resolveSibling(file.getFileName() + ".part");
                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(part))))) {
                    encode(out, worldSize, SCALE, levels, bakedAt, world);
                }
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
                if (learnt != null) writeRestless(root, learnt);
            } catch (IOException e) {
                LivingHorizonClient.LOGGER.warn("Could not save the impostor {}", file, e);
            }
        });
    }

    /** A sheet as {@link #decode} reads it back, its tiles {@code scale} times larger: {@link #SCALE}. */
    static void encode(DataOutputStream out, double worldSize, int scale, int[][] levels, long bakedAt, String world)
            throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(FORMAT);
        out.writeLong(bakedAt);
        out.writeUTF(world);
        out.writeDouble(worldSize);
        out.writeInt(scale);
        out.writeInt(levels.length);
        for (int[] level : levels) {
            out.writeInt(level.length);
            for (int pixel : level) out.writeInt(pixel);
        }
    }

    /**
     * A sheet written by {@link #encode}: null when it is of another format. Every length is
     * checked against the size of a sheet before anything is made, so that a damaged file never
     * asks for more memory than one sheet takes; it throws then.
     */
    static @Nullable Stored decode(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) return null;
        int format = in.readInt();
        if (format != FORMAT && format != 2) return null;
        long bakedAt = format >= 3 ? in.readLong() : 0;
        String world = format >= 3 ? in.readUTF() : "";
        double worldSize = in.readDouble();
        int scale = in.readInt();
        if (scale != SCALE) throw new IOException("tiles " + scale + " times larger");
        int count = in.readInt();
        if (count != ImpostorAtlas.MIPS) throw new IOException(count + " levels, not " + ImpostorAtlas.MIPS);
        int[][] levels = new int[count][];
        for (int level = 0; level < count; level++) {
            int tile = ImpostorViews.TILE >> level;
            int expected = tile * ImpostorViews.VIEWS * tile;
            int length = in.readInt();
            if (length != expected) throw new IOException("level " + level + " of " + length + " pixels, not " + expected);
            levels[level] = new int[length];
            for (int i = 0; i < length; i++) levels[level][i] = in.readInt();
        }
        return new Stored(worldSize, levels, bakedAt, world);
    }

    /**
     * The folder of the current fingerprint, worked out once after each reload: the folders
     * of other fingerprints are thrown away, the sheets of this one listed, and what an
     * earlier game learnt to leave out of keys taken back.
     */
    private static Path folder() {
        if (folder != null) return folder;
        Path base = Platform.configDir().resolve("livinghorizon").resolve("impostors");
        String text = fingerprintText();
        Path current = base.resolve(fingerprint(text));
        folder = current;
        SAVED.clear();
        try (Stream<Path> kinds = Files.isDirectory(current) ? Files.list(current) : Stream.empty()) {
            for (Path kind : kinds.filter(Files::isDirectory).toList()) {
                String type = kind.getFileName().toString().replace('+', ':').replace('~', '/');
                try (Stream<Path> sheets = Files.list(kind)) {
                    for (Path sheet : sheets.toList()) {
                        String name = sheet.getFileName().toString();
                        if (!name.endsWith(".bin")) continue;
                        try {
                            SAVED.add(new ImpostorKey(type, Long.parseUnsignedLong(name.substring(0, name.length() - 4), 16)));
                        } catch (NumberFormatException ignored) {
                            // Not one of ours.
                        }
                    }
                }
            }
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not list the saved impostors in {}", current, e);
        }
        ImpostorKey.restore(readRestless(current));
        savedLearnt = ImpostorKey.learnt();
        LivingHorizonClient.LOGGER.info("Impostors: {} mob pictures saved by earlier games found in {}", SAVED.size(), current);
        IO.execute(() -> tidy(base, current, text));
        return current;
    }

    private static Path file(Path root, ImpostorKey key) {
        // Neither character is allowed in an id: the kind is read back from its folder.
        return root.resolve(key.type().replace(':', '+').replace('/', '~')).resolve(Long.toHexString(key.appearance()) + ".bin");
    }

    /** The fingerprint's text as a short name. */
    private static String fingerprint(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    /**
     * Everything that changes how a figure is drawn, one line each: the game, the resource
     * packs chosen and their files, the mods' files, and how a sheet is baked.
     */
    private static String fingerprintText() {
        Minecraft minecraft = Minecraft.getInstance();
        StringBuilder text = new StringBuilder();
        // Still named "format": a new name would change every fingerprint, and bake every sheet again.
        text.append("format ").append(BAKE).append(" tile ").append(ImpostorViews.TILE).append(" views ")
                .append(ImpostorViews.VIEWS).append(" mips ").append(ImpostorAtlas.MIPS).append('\n');
        text.append("game ").append(minecraft.getLaunchedVersion()).append('\n');
        File packs = new File(minecraft.gameDirectory, "resourcepacks");
        for (String pack : minecraft.getResourcePackRepository().getSelectedIds()) {
            text.append("pack ").append(pack);
            if (pack.startsWith("file/")) stamp(text, new File(packs, pack.substring("file/".length())));
            text.append('\n');
        }
        File[] mods = new File(minecraft.gameDirectory, "mods").listFiles();
        if (mods != null) {
            List<File> sorted = new ArrayList<>(List.of(mods));
            sorted.sort(Comparator.comparing(File::getName));
            for (File mod : sorted) {
                if (!mod.getName().endsWith(".jar")) continue;
                text.append("mod ").append(mod.getName());
                stamp(text, mod);
                text.append('\n');
            }
        }
        return text.toString();
    }

    /** A file's size and when it was last changed: an edited pack or a new jar shows here. */
    private static void stamp(StringBuilder text, File file) {
        text.append(' ').append(file.length()).append(' ').append(file.lastModified());
    }

    /**
     * Throws away the folders of other fingerprints, saying in the log what changed since, and
     * the sheets met longest ago past {@link #KEEP}; writes down the current fingerprint's text.
     */
    private static void tidy(Path base, Path current, String text) {
        try (Stream<Path> folders = Files.isDirectory(base) ? Files.list(base) : Stream.empty()) {
            for (Path other : folders.toList()) {
                if (other.equals(current) || !Files.isDirectory(other)) continue;
                LivingHorizonClient.LOGGER.info("Impostors: the saved mob pictures of {} are thrown away and baked again, changed since: {}",
                        other.getFileName(), changes(other.resolve(FINGERPRINT), text));
                delete(other);
            }
            Files.createDirectories(current);
            Files.writeString(current.resolve(FINGERPRINT), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not tidy the saved impostors in {}", base, e);
        }
        if (!Files.isDirectory(current)) return;
        try (Stream<Path> files = Files.walk(current)) {
            List<Path> sheets = new ArrayList<>(files.filter(path -> path.toString().endsWith(".bin")).toList());
            if (sheets.size() <= KEEP) return;
            sheets.sort(Comparator.comparingLong(path -> path.toFile().lastModified()));
            for (Path old : sheets.subList(0, sheets.size() - KEEP)) Files.deleteIfExists(old);
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not tidy the saved impostors in {}", current, e);
        }
    }

    private static final String FINGERPRINT = "fingerprint.txt";

    /** The lines of a fingerprint's text that are not in the other, both ways: "- old line", "+ new line". */
    private static String changes(Path old, String text) {
        if (!Files.exists(old)) return "not known, saved by an older version";
        try {
            List<String> before = Files.readAllLines(old, StandardCharsets.UTF_8);
            List<String> after = List.of(text.split("\n"));
            List<String> lines = new ArrayList<>();
            for (String line : before) if (!after.contains(line)) lines.add("- " + line);
            for (String line : after) if (!before.contains(line)) lines.add("+ " + line);
            return lines.isEmpty() ? "nothing" : String.join(", ", lines);
        } catch (IOException e) {
            return "not known, " + e.getMessage();
        }
    }

    private static void delete(Path root) {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException e) {
            LivingHorizonClient.LOGGER.warn("Could not delete the saved impostors in {}", root, e);
        }
    }

    /** One line per value: kind of mob, synched value id, the bits left out (hex). */
    private static Map<String, Map<Integer, Long>> readRestless(Path root) {
        Map<String, Map<Integer, Long>> read = new HashMap<>();
        Path file = root.resolve("restless.txt");
        if (!Files.exists(file)) return read;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] parts = line.trim().split(" ");
                if (parts.length != 3) continue;
                read.computeIfAbsent(parts[0], type -> new HashMap<>())
                        .put(Integer.parseInt(parts[1]), Long.parseUnsignedLong(parts[2], 16));
            }
        } catch (IOException | NumberFormatException e) {
            LivingHorizonClient.LOGGER.warn("Could not read {}", file, e);
        }
        return read;
    }

    private static void writeRestless(Path root, Map<String, Map<Integer, Long>> restless) throws IOException {
        List<String> lines = new ArrayList<>();
        restless.forEach((type, masks) -> masks.forEach((id, mask) ->
                lines.add(type + " " + id + " " + Long.toHexString(mask))));
        Files.createDirectories(root);
        Files.write(root.resolve("restless.txt"), lines, StandardCharsets.UTF_8);
    }
}
