package fr.clixmods.livinghorizon;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryTest {
    private static final long SECOND = 1_000_000_000L;

    private long now;

    private Retry retry() {
        return new Retry("Testing", "nothing works", () -> now);
    }

    @Test
    void aFailurePausesThePartThenItIsTriedAgain() {
        Retry retry = retry();
        assertFalse(retry.blocked());
        assertNull(retry.state());
        assertFalse(retry.fail(new IllegalStateException("World is not live")));
        assertTrue(retry.blocked());
        assertFalse(retry.off());
        assertEquals("paused: java.lang.IllegalStateException: World is not live (20 s)", retry.state());
        now += (Retry.PAUSE_SECONDS - 1) * SECOND;
        assertTrue(retry.blocked());
        now += SECOND;
        assertFalse(retry.blocked());
        assertNull(retry.state());
    }

    @Test
    void onlyWhatCannotGoAwayTurnsThePartOff() {
        Retry retry = retry();
        assertTrue(retry.fail(new NoSuchMethodError("renderSystem")));
        assertTrue(retry.off());
        now += 3600 * SECOND;
        assertTrue(retry.blocked());
        assertEquals("off: java.lang.NoSuchMethodError: renderSystem", retry.state());
        retry.reset();
        assertFalse(retry.blocked());
    }

    @Test
    void tellsAMissingClassFromAFailureInsideTheCall() {
        assertTrue(Retry.permanent(new NoClassDefFoundError("me/cortex/voxy/Thing")));
        assertTrue(Retry.permanent(new NoSuchFieldException("CONFIG")));
        assertTrue(Retry.permanent(new ClassNotFoundException("me.cortex.voxy.Thing")));
        assertTrue(Retry.permanent(new InvocationTargetException(new AbstractMethodError())));
        // What the called method threw itself: it may answer next time.
        assertFalse(Retry.permanent(new InvocationTargetException(new IllegalStateException("busy"))));
        assertFalse(Retry.permanent(new InvocationTargetException(null)));
        assertFalse(Retry.permanent(new RuntimeException("GL error")));
    }

    @Test
    void saysWhatACallThrewRatherThanItsWrapper() {
        Retry retry = retry();
        retry.fail(new InvocationTargetException(new IllegalStateException("busy")));
        assertEquals("java.lang.IllegalStateException: busy", retry.failure());
    }

    @Test
    void turnedOffStaysOffAndKeepsTheFirstReason() {
        Retry retry = retry();
        retry.turnOff("not installed", null);
        retry.turnOff("something else", null);
        assertTrue(retry.blocked());
        assertEquals("off: not installed", retry.state());
    }
}
