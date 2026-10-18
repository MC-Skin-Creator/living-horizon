package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.GameBoot;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads the messages a server with the mod sends ({@link PositionsMessage}), as the client keeps them. */
class SharedPositionsTest {
    private static final UUID HORSE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final UUID GHAST = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

    @BeforeAll
    static void boot() {
        GameBoot.start();
    }

    private static SharedPositions received() {
        SharedPositions shared = new SharedPositions();
        // Steve, in the nether, looking at 270 degrees, on the ghast; Alex logged off in the end.
        shared.receive(PositionsMessage.players(List.of(
                new PositionsMessage.Player("Steve", -1234.5, 64.25, 8001.9, 270f, 1, true, true, GHAST))));
        shared.receive(PositionsMessage.offline(List.of(
                new PositionsMessage.Player("Alex", 10, 70, -20, 90f, 2, false, false, null))));
        // A baby horse (kind 1), variant 513, in the end, sent around a player at (0, 0).
        shared.receive(PositionsMessage.mobs(new PositionsMessage.Mobs(List.of(
                new PositionsMessage.Mob(HORSE, 1.5, 70, -2.5, 1, 2, true, 513)), 2, 0, 0, 2048)));
        return shared;
    }

    @Test
    void decodesThePlayers() {
        SharedPositions shared = received();
        assertTrue(shared.update());
        SharedPositions.Report steve = shared.read("Steve");
        assertNotNull(steve);
        assertEquals(-1234.5, steve.x(), 1e-9);
        assertEquals(64.25, steve.y(), 1e-9);
        assertEquals(8001.9, steve.z(), 1e-9);
        assertEquals(270f, steve.yaw());
        assertEquals(1, steve.dimension());
        assertTrue(steve.riding());
        assertEquals(GHAST, shared.vehicle("Steve"));

        SharedPositions.Report alex = shared.read("Alex");
        assertNotNull(alex);
        assertEquals(2, alex.dimension());
        assertNull(shared.vehicle("Alex"));
        assertNull(shared.read("Nobody"));
        assertEquals(List.of("Steve", "Alex"), shared.holders());
    }

    @Test
    void decodesAMob() {
        SharedPositions shared = received();
        shared.update();
        assertEquals(List.of(HORSE), shared.mobs());
        SharedPositions.MobReport horse = shared.readMob(HORSE);
        assertNotNull(horse);
        assertEquals(1.5, horse.x(), 1e-9);
        assertEquals(70.0, horse.y(), 1e-9);
        assertEquals(-2.5, horse.z(), 1e-9);
        assertEquals("minecraft:horse", MobKinds.type(horse.kind()));
        assertEquals(2, horse.dimension());
        assertTrue(horse.baby());
        assertEquals(513, horse.variant());
        // Gone from where they were sent around, only out of reach farther off.
        assertTrue(shared.covers(2, 100, 100));
        assertFalse(shared.covers(2, 3000, 0));
        assertFalse(shared.covers(0, 100, 100));
    }

    @Test
    void stopsTrustingASilentServer() {
        SharedPositions shared = received();
        for (int tick = 0; tick < 59; tick++) assertTrue(shared.update());
        assertFalse(shared.update());
        assertNull(shared.read("Steve"));
        // News again.
        shared.receive(PositionsMessage.players(List.of()));
        assertTrue(shared.update());
    }

    @Test
    void keepsTheMountOfAPlayerWhoLoggedOff() {
        SharedPositions shared = new SharedPositions();
        PositionsMessage.Mount horse = new PositionsMessage.Mount("minecraft:horse", "{Variant:513}", 0, 0.85, -0.2, 45f, -10f);
        shared.receive(PositionsMessage.players(List.of()));
        shared.receive(PositionsMessage.offline(List.of(
                new PositionsMessage.Player("Alex", 10, 70, -20, 90f, 0, false, true, null, horse))));
        shared.update();
        assertTrue(shared.read("Alex").riding());
        assertEquals(horse, shared.mount("Alex"));
        // Back online: what they ride is no longer kept for them.
        shared.receive(PositionsMessage.players(List.of(
                new PositionsMessage.Player("Alex", 10, 70, -20, 90f, 0, true, false, null))));
        assertNull(shared.mount("Alex"));
        PositionsMessage.Saved saved = PositionsMessage.readSaved(PositionsMessage.saved(
                List.of(new PositionsMessage.Player("Alex", 10, 70, -20, 90f, 0, false, true, null, horse)), List.of()));
        assertNotNull(saved);
        assertEquals(horse, saved.players().get(0).mount());
    }

    @Test
    void ignoresAnotherVersionOrGarbage() {
        SharedPositions shared = new SharedPositions();
        byte[] message = PositionsMessage.players(List.of(new PositionsMessage.Player("Steve", 0, 0, 0, 0, 0, true, false, null)));
        message[3] = 99;
        shared.receive(message);
        shared.receive(new byte[] {1, 2, 3});
        assertFalse(shared.update());
        assertNull(PositionsMessage.read(new byte[0]));
    }

    @Test
    void theServerFileReadsBack() {
        List<PositionsMessage.Player> players = List.of(new PositionsMessage.Player("Alex", 10, 70, -20, 90f, 2, false, false, null));
        List<PositionsMessage.Mob> mobs = List.of(new PositionsMessage.Mob(HORSE, 1.5, 70, -2.5, 1, 2, true, 513));
        PositionsMessage.Saved saved = PositionsMessage.readSaved(PositionsMessage.saved(players, mobs));
        assertNotNull(saved);
        assertEquals(players, saved.players());
        assertEquals(mobs, saved.mobs());
    }

    @Test
    void mobsKnownOnlyFromTheServerParse() throws Exception {
        for (String type : MobKinds.TYPES) {
            TagParser.parseCompoundFully(MobKinds.nbt(type, 3, true));
        }
    }
}
