package fr.clixmods.livinghorizon.track;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads scores laid out the way the data pack's {@code publish.mcfunction} writes them. */
class SharedPositionsTest {
    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Objective objective(Scoreboard scoreboard, String name) {
        return scoreboard.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(name),
                ObjectiveCriteria.RenderType.INTEGER, false, null);
    }

    private static void set(Scoreboard scoreboard, String holder, Objective objective, int value) {
        scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(value);
    }

    private static Scoreboard pack() {
        Scoreboard scoreboard = new Scoreboard();
        Objective x = objective(scoreboard, "lh.x");
        Objective y = objective(scoreboard, "lh.y");
        Objective z = objective(scoreboard, "lh.z");
        Objective m = objective(scoreboard, "lh.m");
        set(scoreboard, "#version", m, SharedPositions.VERSION);
        set(scoreboard, "#clock", m, 1);
        // Steve, in the nether at (-1234.5, 64.2, 8001.9), looking at 270 degrees, on a strider.
        set(scoreboard, "Steve", x, -12345);
        set(scoreboard, "Steve", y, 642);
        set(scoreboard, "Steve", z, 80019);
        set(scoreboard, "Steve", m, 270 + 360 + 1440);
        return scoreboard;
    }

    @Test
    void decodesAPlayer() {
        Scoreboard scoreboard = pack();
        SharedPositions shared = new SharedPositions();
        assertTrue(shared.update(scoreboard));

        SharedPositions.Report steve = shared.read(scoreboard, "Steve");
        assertNotNull(steve);
        assertEquals(-1234.5, steve.x(), 1e-9);
        assertEquals(64.2, steve.y(), 1e-9);
        assertEquals(8001.9, steve.z(), 1e-9);
        assertEquals(270f, steve.yaw());
        assertEquals(1, steve.dimension());
        assertTrue(steve.riding());
        assertNull(shared.read(scoreboard, "Alex"));
    }

    @Test
    void decodesAMobAndKeepsItApartFromPlayers() {
        Scoreboard scoreboard = pack();
        UUID id = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
        String holder = id.toString();
        // A baby horse (kind 1), variant 513, in the end.
        set(scoreboard, holder, scoreboard.getObjective("lh.x"), 15);
        set(scoreboard, holder, scoreboard.getObjective("lh.y"), 700);
        set(scoreboard, holder, scoreboard.getObjective("lh.z"), -25);
        set(scoreboard, holder, scoreboard.getObjective("lh.m"), 1 + 2 * 256 + 1024 + 513 * 2048);

        SharedPositions shared = new SharedPositions();
        shared.update(scoreboard);
        assertEquals(List.of("Steve"), shared.holders(scoreboard));
        assertEquals(List.of(id), shared.mobs(scoreboard));

        SharedPositions.MobReport horse = shared.readMob(scoreboard, id);
        assertNotNull(horse);
        assertEquals(1.5, horse.x(), 1e-9);
        assertEquals(70.0, horse.y(), 1e-9);
        assertEquals(-2.5, horse.z(), 1e-9);
        assertEquals("minecraft:horse", MobKinds.type(horse.kind()));
        assertEquals(2, horse.dimension());
        assertTrue(horse.baby());
        assertEquals(513, horse.variant());
    }

    @Test
    void mobsKnownOnlyFromThePackParse() throws Exception {
        for (String type : MobKinds.TYPES) {
            TagParser.parseCompoundFully(MobKinds.nbt(type, 3, true));
            TagParser.parseCompoundFully(MobKinds.nbt(type, 0, false));
        }
        assertEquals((byte) 14, TagParser.parseCompoundFully(MobKinds.nbt("minecraft:sheep", 14, false)).getByteOr("Color", (byte) 0));
        assertEquals(-24000, TagParser.parseCompoundFully(MobKinds.nbt("minecraft:cow", 0, true)).getIntOr("Age", 0));
    }

    @Test
    void stopsTrustingAStoppedPack() {
        Scoreboard scoreboard = pack();
        SharedPositions shared = new SharedPositions();
        for (int tick = 0; tick < 60; tick++) assertTrue(shared.update(scoreboard), "tick " + tick);
        assertFalse(shared.update(scoreboard));
        assertNull(shared.read(scoreboard, "Steve"));

        set(scoreboard, "#clock", scoreboard.getObjective("lh.m"), 2);
        assertTrue(shared.update(scoreboard));
    }

    @Test
    void ignoresAnotherLayout() {
        Scoreboard scoreboard = pack();
        set(scoreboard, "#version", scoreboard.getObjective("lh.m"), SharedPositions.VERSION + 1);
        assertFalse(new SharedPositions().update(scoreboard));
    }
}
