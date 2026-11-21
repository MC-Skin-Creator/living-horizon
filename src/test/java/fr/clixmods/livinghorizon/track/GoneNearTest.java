package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A mob the server stops sending close by is forgotten, unless the server sends that kind no farther. */
class GoneNearTest {
    @Test
    void aServerThatSendsFarForgetsWhatGoesWithin32Blocks() {
        assertEquals(32, MobMemory.goneNear(160));
        assertEquals(32, MobMemory.goneNear(48));
    }

    @Test
    void aServerThatSendsMobsTo24BlocksKeepsThoseItDrops() {
        // The mobs of mc.peacefulvanilla.club stop being sent at 24 blocks, as the player walks away.
        double near = MobMemory.goneNear(24);
        assertTrue(near < 23, "a mob dropped at the edge of 24 blocks is out of range, not gone: " + near);
        assertTrue(near > 0, "one gone right next to the player is still forgotten: " + near);
    }

    @Test
    void nothingIsForgottenForItsDistanceBeforeTheReachIsKnown() {
        assertTrue(MobMemory.goneNear(0) <= 0);
    }
}
