package fr.clixmods.livinghorizon.track;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The last-seen date reads as the player's language writes dates. */
class LastSeenTest {
    /** 10 October 2026, 14:05 UTC. */
    private static final long TIME = 1791641100000L;

    @Test
    void writesTheDateInTheGamesLanguage() {
        String french = LastSeen.format(TIME, "fr_fr", ZoneId.of("UTC"));
        assertTrue(french.contains("oct.") && french.contains("2026") && french.contains("14:05"), french);
        String english = LastSeen.format(TIME, "en_us", ZoneId.of("UTC"));
        assertTrue(english.contains("Oct") && english.contains("2026") && english.contains("2:05"), english);
    }

    @Test
    void usesTheComputersTimeZone() {
        String paris = LastSeen.format(TIME, "fr_fr", ZoneId.of("Europe/Paris"));
        assertTrue(paris.contains("16:05"), paris);
    }

    @Test
    void unknownIsNull() {
        assertNull(LastSeen.format(0, "en_us", ZoneId.of("UTC")));
    }

    @Test
    void readsTheGamesLanguageCodes() {
        assertEquals(Locale.forLanguageTag("fr-FR"), LastSeen.locale("fr_fr"));
        assertEquals(Locale.ENGLISH, LastSeen.locale(""));
    }
}
