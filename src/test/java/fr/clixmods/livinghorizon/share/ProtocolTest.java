package fr.clixmods.livinghorizon.share;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** What a server running the mod writes, read back the way a client reads it. */
class ProtocolTest {
    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID SHEEP = UUID.fromString("0f2b0b6e-6d43-4c7a-9f0c-2d5f6c1e7a10");

    @Test
    void playersComeBackAsSent() {
        Protocol.Players sent = new Protocol.Players(List.of(
                new Protocol.Player(STEVE, "Steve", "minecraft:the_nether", -1234.5, 64.2, 8001.9, 270f, true)));
        assertEquals(sent, Protocol.decode(Protocol.encode(sent)));
    }

    @Test
    void restingKeepsWhetherItIsTheWholeList() {
        Protocol.Resting sent = new Protocol.Resting(true, List.of(
                new Protocol.Player(STEVE, "Steve", "minecraft:overworld", 10, 70, -20, -45f, false)));
        assertEquals(sent, Protocol.decode(Protocol.encode(sent)));
        Protocol.Resting one = new Protocol.Resting(false, List.of());
        assertEquals(one, Protocol.decode(Protocol.encode(one)));
    }

    @Test
    void mobsCarryTheirSavedDataOnlyWhenItTravels() {
        String nbt = "{Color:14b,Sheared:1b,CustomName:'{\"text\":\"Dolly ✿\"}'}";
        Protocol.Mobs sent = new Protocol.Mobs(List.of(
                new Protocol.Mob(SHEEP, "minecraft:sheep", "minecraft:overworld", 1.5, 64, -3.25, 90f, true, nbt),
                new Protocol.Mob(STEVE, "othermod:yak", "othermod:highlands", 0, 0, 0, 0f, false, null)),
                List.of(UUID.randomUUID()));
        assertEquals(sent, Protocol.decode(Protocol.encode(sent)));
    }

    @Test
    void savedDataLongerThanWriteUtfAllows() {
        String nbt = "{Text:\"" + "x".repeat(100_000) + "\"}";
        Protocol.Mobs sent = new Protocol.Mobs(List.of(
                new Protocol.Mob(SHEEP, "minecraft:sheep", "minecraft:overworld", 0, 0, 0, 0f, false, nbt)), List.of());
        assertEquals(sent, Protocol.decode(Protocol.encode(sent)));
    }

    @Test
    void anotherVersionOrBrokenBytesReadAsNothing() {
        byte[] message = Protocol.encode(new Protocol.Players(List.of()));
        message[0] = (byte) (Protocol.VERSION + 1);
        assertNull(Protocol.decode(message));

        byte[] cut = Protocol.encode(new Protocol.Players(List.of(
                new Protocol.Player(STEVE, "Steve", "minecraft:overworld", 1, 2, 3, 4f, false))));
        assertNull(Protocol.decode(Arrays.copyOf(cut, cut.length - 5)));
        assertNull(Protocol.decode(new byte[0]));
        assertNull(Protocol.decode(new byte[] {(byte) Protocol.VERSION, 42}));
    }
}
