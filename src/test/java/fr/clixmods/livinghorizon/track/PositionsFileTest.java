package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The server's own file ({@link PositionPublisher#FILE}) keeps a version apart from the messages'. */
class PositionsFileTest {
    private static final UUID HORSE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

    @Test
    void roundTrips() {
        List<PositionsMessage.Player> players = List.of(new PositionsMessage.Player("Alex", 10, 70, -20, 90f, 2, false,
                true, null, new PositionsMessage.Mount("minecraft:horse", "{Tame:1b}", 0, 0.85, -0.1, 90f, -15f),
                new PositionsMessage.Look("dGV4dHVyZXM=", null, List.of("", "minecraft:iron_helmet")), 1760100000000L,
                false));
        List<PositionsMessage.Mob> mobs = List.of(new PositionsMessage.Mob(HORSE, 1.5, 70, -2.5, 1, 0, true, 513,
                "{SaddleItem:{id:\"minecraft:saddle\"}}"));
        byte[] saved = PositionsMessage.saved(players, mobs);
        assertEquals(PositionsMessage.FILE_VERSION, PositionsMessage.savedVersion(saved));
        PositionsMessage.Saved read = PositionsMessage.readSaved(saved);
        assertNotNull(read);
        assertEquals(players, read.players());
        assertEquals(mobs, read.mobs());
    }

    /** Files saved before the file had a version of its own: 2, the messages' version then. */
    @Test
    void readsTheFilesOfVersion2() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(2);
            // One player: Steve, online when it was saved, nothing else known; no mobs.
            out.writeInt(1);
            out.writeUTF("Steve");
            out.writeDouble(1);
            out.writeDouble(64);
            out.writeDouble(-3);
            out.writeFloat(180f);
            out.writeByte(0);
            out.writeByte(1);
            out.writeInt(0);
        }
        PositionsMessage.Saved read = PositionsMessage.readSaved(bytes.toByteArray());
        assertNotNull(read);
        assertEquals(List.of(new PositionsMessage.Player("Steve", 1, 64, -3, 180f, 0, true, false, null)), read.players());
        assertEquals(List.of(), read.mobs());
    }

    @Test
    void refusesAnotherVersionOrADamagedFile() {
        byte[] saved = PositionsMessage.saved(List.of(), List.of());
        saved[3]++;
        assertNull(PositionsMessage.readSaved(saved));
        assertEquals(PositionsMessage.FILE_VERSION + 1, PositionsMessage.savedVersion(saved));
        assertNull(PositionsMessage.readSaved(new byte[] {0, 0, 0, 2, 0}));
        assertEquals(-1, PositionsMessage.savedVersion(new byte[] {0, 0}));
    }
}
