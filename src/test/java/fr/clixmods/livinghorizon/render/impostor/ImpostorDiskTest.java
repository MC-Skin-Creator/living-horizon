package fr.clixmods.livinghorizon.render.impostor;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A mob's sheet saved on disk reads back the same, and a damaged one never asks for more than a sheet. */
class ImpostorDiskTest {
    /** The levels of a sheet of scale 1, each pixel its own number. */
    private static int[][] sheet() {
        int[][] levels = new int[ImpostorAtlas.MIPS][];
        for (int level = 0; level < levels.length; level++) {
            int tile = ImpostorViews.TILE >> level;
            levels[level] = new int[tile * ImpostorViews.VIEWS * tile];
            int shift = level;
            Arrays.setAll(levels[level], i -> i * 31 + shift);
        }
        return levels;
    }

    private static byte[] encoded(double worldSize, int scale, int[][] levels) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            ImpostorDisk.encode(out, worldSize, scale, levels);
        }
        return bytes.toByteArray();
    }

    private static ImpostorDisk.Stored decoded(byte[] data) throws IOException {
        return ImpostorDisk.decode(new DataInputStream(new ByteArrayInputStream(data)));
    }

    @Test
    void roundTrips() throws IOException {
        int[][] levels = sheet();
        ImpostorDisk.Stored stored = decoded(encoded(2.5, ImpostorDisk.SCALE, levels));
        assertNotNull(stored);
        assertEquals(2.5, stored.worldSize());
        assertEquals(levels.length, stored.levels().length);
        for (int level = 0; level < levels.length; level++) assertArrayEquals(levels[level], stored.levels()[level]);
    }

    @Test
    void ignoresAnotherFormat() throws IOException {
        byte[] data = encoded(2.5, ImpostorDisk.SCALE, sheet());
        data[7]++;
        assertNull(decoded(data));
    }

    @Test
    void refusesALengthItCannotHave() throws IOException {
        byte[] data = encoded(2.5, ImpostorDisk.SCALE, sheet());
        // The first level's length, after the magic, the format, the size, the scale and the count.
        int at = 4 + 4 + 8 + 4 + 4;
        data[at] = 0x7F;
        assertThrows(IOException.class, () -> decoded(data));
    }

    @Test
    void refusesAScaleOrCountItCannotHave() throws IOException {
        assertThrows(IOException.class, () -> decoded(encoded(2.5, ImpostorDisk.SCALE - 1, sheet())));
        assertThrows(IOException.class, () -> decoded(encoded(2.5, ImpostorDisk.SCALE + 1, sheet())));
        assertThrows(IOException.class, () -> decoded(encoded(2.5, ImpostorDisk.SCALE, Arrays.copyOf(sheet(), ImpostorAtlas.MIPS - 1))));
    }

    @Test
    void stopsAtTheEndOfACutFile() throws IOException {
        byte[] data = encoded(2.5, ImpostorDisk.SCALE, sheet());
        assertThrows(EOFException.class, () -> decoded(Arrays.copyOf(data, data.length / 2)));
    }
}
