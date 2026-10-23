package fr.clixmods.livinghorizon;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * What a part of the mod does when it fails, the same everywhere: it says so in the log once,
 * stops for a while and tries again. A world closing as the player joins, a level change, a GL
 * error on one frame: one failure is often gone by the next try, and a part turned off for the
 * session on the first one stayed off until the game restarted. Only a failure that cannot go
 * away - a class, method or field of the game or of another mod missing or changed
 * ({@link #permanent}) - turns the part off for good.
 *
 * <p>The log gets the first failure with its stack trace, and after that only a failure of
 * another kind (its class and message), so that one failing every try does not fill it. Safe
 * to share between threads. Loaded by a dedicated server too: no client class here.
 */
public final class Retry {
    private static final Logger LOGGER = LoggerFactory.getLogger("Living Horizon");
    /** How long a part waits after a failure before it tries again. */
    public static final long PAUSE_SECONDS = 20;
    /** Kinds of failure logged at most, per part: past that, a part failing in new ways says nothing more. */
    private static final int KINDS_LOGGED = 8;

    private final String what, without;
    private final LongSupplier clock;
    private volatile long pausedUntil;
    private volatile boolean off;
    private volatile @Nullable String failure;
    private final Set<String> logged = new HashSet<>();

    /**
     * {@code what} names the part as the log says it failed ("Reading Voxy's world"), and
     * {@code without} what the player sees while it is stopped ("distant mobs show through it").
     */
    public Retry(String what, String without) {
        this(what, without, System::nanoTime);
    }

    /** With a clock of its own, in nanoseconds: for the tests. */
    Retry(String what, String without, LongSupplier clock) {
        this.what = what;
        this.without = without;
        this.clock = clock;
    }

    /** Whether the part is stopped right now: paused after a failure, or off for good. */
    public boolean blocked() {
        return off || clock.getAsLong() < pausedUntil;
    }

    /** Whether the part is off for good, until {@link #reset}. */
    public boolean off() {
        return off;
    }

    /** The last failure, for the debug panel and {@code /livinghorizon lod}; null while there was none. */
    public @Nullable String failure() {
        return failure;
    }

    /**
     * A failure: the part is paused for {@link #PAUSE_SECONDS}, or turned off for good when
     * it cannot go away. Returns whether it is off for good.
     */
    public boolean fail(Throwable e) {
        boolean permanent = permanent(e);
        Throwable cause = cause(e);
        synchronized (this) {
            failure = String.valueOf(cause);
            if (permanent) off = true;
            else pausedUntil = clock.getAsLong() + PAUSE_SECONDS * 1_000_000_000L;
            if (!firstOfItsKind(cause)) return permanent;
        }
        if (permanent) LOGGER.warn("{} failed and is turned off: {}", what, without, e);
        else LOGGER.warn("{} failed, trying again in {} seconds: {} until then", what, PAUSE_SECONDS, without, e);
        return permanent;
    }

    /** Turns the part off for good: what it needs is not there ({@code why}). Logged once. */
    public void turnOff(String why, @Nullable Throwable e) {
        synchronized (this) {
            if (off) return;
            off = true;
            failure = why;
        }
        if (e != null) LOGGER.warn("{} is turned off, {}: {}", what, why, without, e);
        else LOGGER.warn("{} is turned off, {}: {}", what, why, without);
    }

    /** Everything forgotten: the part is tried again at once. */
    public synchronized void reset() {
        off = false;
        pausedUntil = 0;
        failure = null;
        logged.clear();
    }

    /** {@code "paused: why (12 s)"}, {@code "off: why"}, or null while the part runs. */
    public @Nullable String state() {
        if (off) return "off: " + failure;
        long left = pausedUntil - clock.getAsLong();
        if (left <= 0) return null;
        return "paused: " + failure + " (" + ((left + 999_999_999L) / 1_000_000_000L) + " s)";
    }

    private boolean firstOfItsKind(Throwable e) {
        if (logged.size() >= KINDS_LOGGED) return false;
        return logged.add(e.getClass().getName() + ": " + e.getMessage());
    }

    /**
     * Whether a failure cannot go away by trying again: what was called is missing or not what
     * this mod was built for. Looked for in what a reflective call threw too.
     */
    public static boolean permanent(Throwable e) {
        Throwable cause = cause(e);
        // Not found, not allowed: a reflective lookup that will never find it.
        return cause instanceof LinkageError || cause instanceof WrongMethodTypeException
                || cause instanceof ReflectiveOperationException && !(cause instanceof InvocationTargetException);
    }

    /** What a reflective call threw, rather than the wrapper it came in. */
    private static Throwable cause(Throwable e) {
        for (int depth = 0; depth < 8; depth++) {
            if (!(e instanceof InvocationTargetException || e instanceof UndeclaredThrowableException) || e.getCause() == null) {
                return e;
            }
            e = e.getCause();
        }
        return e;
    }
}
