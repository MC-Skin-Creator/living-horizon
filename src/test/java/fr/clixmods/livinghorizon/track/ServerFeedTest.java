package fr.clixmods.livinghorizon.track;

import fr.clixmods.livinghorizon.share.Protocol;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a client keeps of what a server running the mod tells it. */
class ServerFeedTest {
    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID SHEEP = UUID.fromString("0f2b0b6e-6d43-4c7a-9f0c-2d5f6c1e7a10");

    private static void send(ServerFeed feed, Protocol.Message message) {
        feed.accept(Protocol.encode(message));
    }

    @Test
    void activeWhileTheServerKeepsTalking() {
        ServerFeed feed = new ServerFeed();
        assertFalse(feed.active());
        send(feed, new Protocol.Players(List.of()));
        assertTrue(feed.active());
        for (int i = 0; i < 60; i++) feed.tick();
        assertFalse(feed.active());
        send(feed, new Protocol.Players(List.of()));
        feed.clear();
        assertFalse(feed.active());
    }

    @Test
    void playersInTheCodesOfThePack() {
        ServerFeed feed = new ServerFeed();
        send(feed, new Protocol.Players(List.of(
                new Protocol.Player(STEVE, "Steve", "minecraft:the_nether", -1234.5, 64.2, 8001.9, -90f, true))));
        SharedPositions.Report report = feed.player("steve", "minecraft:the_nether", 1);
        assertNotNull(report);
        assertEquals(1, report.dimension());
        assertEquals(-1234.5, report.x(), 1e-9);
        assertEquals(270f, report.yaw(), 1e-4);
        assertTrue(report.riding());
        assertEquals(1, feed.player("Steve", "minecraft:overworld", 0).dimension());
        assertNull(feed.player("Alex", "minecraft:overworld", 0));
    }

    @Test
    void twoDimensionsOfModsAreNeverTheSame() {
        ServerFeed feed = new ServerFeed();
        send(feed, new Protocol.Players(List.of(
                new Protocol.Player(STEVE, "Steve", "othermod:moon", 0, 0, 0, 0f, false))));
        assertEquals(3, feed.player("Steve", "othermod:moon", 3).dimension());
        assertEquals(-1, feed.player("Steve", "othermod:mars", 3).dimension());
    }

    @Test
    void restingListReplacedOrAddedTo() {
        ServerFeed feed = new ServerFeed();
        send(feed, new Protocol.Resting(true, List.of(
                new Protocol.Player(STEVE, "Steve", "minecraft:overworld", 1, 2, 3, 0f, false))));
        send(feed, new Protocol.Resting(false, List.of(
                new Protocol.Player(UUID.randomUUID(), "Alex", "minecraft:overworld", 4, 5, 6, 0f, false))));
        assertEquals(2, feed.resting().size());
        send(feed, new Protocol.Resting(true, List.of()));
        assertTrue(feed.resting().isEmpty());
    }

    @Test
    void aMobThatMovedKeepsItsSavedData() {
        ServerFeed feed = new ServerFeed();
        send(feed, new Protocol.Mobs(List.of(new Protocol.Mob(SHEEP, "minecraft:sheep", "minecraft:overworld",
                1, 64, 1, 0f, false, "{Color:14b}")), List.of()));
        send(feed, new Protocol.Mobs(List.of(new Protocol.Mob(SHEEP, "minecraft:sheep", "minecraft:overworld",
                9, 64, 9, 0f, true, null)), List.of()));
        List<SharedPositions.SharedMob> mobs = feed.mobs();
        assertEquals(1, mobs.size());
        SharedPositions.SharedMob sheep = mobs.get(0);
        assertEquals("{Color:14b}", sheep.nbt());
        assertEquals(9, sheep.x(), 1e-9);
        assertTrue(sheep.named());
        assertTrue(sheep.exact());

        send(feed, new Protocol.Mobs(List.of(), List.of(SHEEP)));
        assertTrue(feed.mobs().isEmpty());
    }

    @Test
    void aMobWithoutSavedDataIsBuiltFromItsKind() {
        ServerFeed feed = new ServerFeed();
        send(feed, new Protocol.Mobs(List.of(new Protocol.Mob(SHEEP, "minecraft:sheep", "minecraft:overworld",
                1, 64, 1, 0f, false, null)), List.of()));
        SharedPositions.SharedMob sheep = feed.mobs().get(0);
        assertFalse(sheep.exact());
        assertEquals(MobKinds.nbt("minecraft:sheep", 0, false), sheep.nbt());
    }
}
