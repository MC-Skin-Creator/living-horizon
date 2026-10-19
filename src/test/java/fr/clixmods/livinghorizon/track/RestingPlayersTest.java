package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The file of who rests where survives a game restart unchanged. */
class RestingPlayersTest {
    @Test
    void roundTrips() {
        List<RestingPlayers.Spot> spots = List.of(
                new RestingPlayers.Spot("Steve", UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7"),
                        "ewogICJ0aW1lc3RhbXAiIDogMQp9", "c2lnbmF0dXJl", 1, -1234.5, 64.0, 8001.9, 270f, 1760100000000L, null,
                        List.of("", "", "", "", "{count:1,id:\"minecraft:iron_chestplate\"}", "")),
                new RestingPlayers.Spot("Alex", null, null, null, 0, 10, 70, -20, 0f, 0,
                        new RestingPlayers.Mount("minecraft:horse", "{Variant:3,Tame:1b}", "17:b:4", 0, 0.85, -0.1,
                                90f, -15f), null),
                new RestingPlayers.Spot("Ghast", null, null, null, 0, 0, 120, 0, 0f, 0,
                        new RestingPlayers.Mount("minecraft:happy_ghast", "{}", null, 0, 4, 0, 0f, 0f), null));
        StringWriter out = new StringWriter();
        RestingPlayers.write(spots, out);
        assertEquals(spots, RestingPlayers.read(new StringReader(out.toString())));
    }

    @Test
    void readsFilesWithoutTheLastSeenTimeOrMount() {
        String older = "[{\"name\": \"Alex\", \"dimension\": 0, \"x\": 10, \"y\": 70, \"z\": -20, \"yaw\": 0}]";
        assertEquals(List.of(new RestingPlayers.Spot("Alex", null, null, null, 0, 10, 70, -20, 0f, 0, null, null)),
                RestingPlayers.read(new StringReader(older)));
    }

    @Test
    void skipsBrokenEntries() {
        assertEquals(List.of(), RestingPlayers.read(new StringReader("[null, {\"x\": 1}]")));
        assertEquals(List.of(), RestingPlayers.read(new StringReader("")));
    }
}
