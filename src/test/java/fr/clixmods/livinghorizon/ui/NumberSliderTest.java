package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.QualityPreset;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumberSliderTest {
    private static NumberSlider.Range range(int min, int max, String unlimited) {
        return new NumberSlider.Range(Component.literal("Max Mobs"), min, max, (caption, v) -> Component.literal("" + v),
                unlimited == null ? null : Component.literal(unlimited), true);
    }

    @Test
    void readsTypedNumbers() {
        NumberSlider.Range range = range(0, 4001, "Illimité");
        assertEquals(1234, range.parse("1234"));
        assertEquals(1234, range.parse(" 1 234 "));
        assertEquals(1234, range.parse("1 234"));
        assertEquals(4000, range.parse("4000"));
        // Past the last number, the end with no limit.
        assertEquals(4001, range.parse("4001"));
        assertEquals(4001, range.parse("999999999999999999999"));
        assertEquals(0, range.parse("0"));
    }

    @Test
    void readsTheWordForNoLimit() {
        NumberSlider.Range range = range(0, 4001, "Illimité");
        assertEquals(4001, range.parse("illimité"));
        assertEquals(4001, range.parse("ILLIMITE"));
        assertEquals(4001, range.parse("ill"));
        assertEquals(4001, range.parse("∞"));
        assertEquals(4001, range(0, 4001, "Sin límite").parse("sin limite"));
        assertNull(range.parse("i"));
        assertNull(range.parse("abc"));
        assertNull(range.parse(""));
        assertNull(range.parse("-5"));
    }

    @Test
    void bringsNumbersIntoARangeWithoutNoLimit() {
        NumberSlider.Range range = range(32, 2000, null);
        assertEquals(2000, range.parse("5000"));
        assertEquals(32, range.parse("1"));
        assertNull(range.parse("illimité"));
    }

    @Test
    void slidesBackToTheSameValue() {
        NumberSlider.Range range = range(1, 4001, "Unlimited");
        for (int v : new int[]{1, 2, 512, 2048, 4000, 4001}) assertEquals(v, range.fromSlider(range.toSlider(v)));
        assertEquals(1, range.fromSlider(0));
        assertEquals(4001, range.fromSlider(1));
    }

    @Test
    void asksForEverySettingWithNoLimit() {
        for (String setting : new String[]{"maxDistantMobs", "maxDistantEntities", "maxOfflinePlayers",
                "playerMaxDistance", "mobMaxDistance", "animationMaxDistance", "entityMaxDistance"}) {
            assertTrue(NumberSlider.asksBeforeUnlimited(setting), setting);
        }
        assertFalse(NumberSlider.asksBeforeUnlimited("impostorDistance"));
        assertFalse(NumberSlider.asksBeforeUnlimited("foreseeRadius"));
    }

    @Test
    void noDefaultGoesWithoutALimit() {
        FarConfig defaults = new FarConfig();
        assertTrue(defaults.maxDistantMobs < FarConfig.UNLIMITED_MOBS);
        assertTrue(defaults.maxDistantEntities < FarConfig.UNLIMITED_MOBS);
        assertTrue(defaults.maxOfflinePlayers < FarConfig.UNLIMITED_OFFLINE);
        for (int distance : new int[]{defaults.playerMaxDistance, defaults.mobMaxDistance,
                defaults.animationMaxDistance, defaults.entityMaxDistance}) {
            assertTrue(distance > 0, "a distance with no limit by default");
        }
    }

    @Test
    void noPresetGoesWithoutALimit() {
        for (QualityPreset preset : QualityPreset.values()) {
            FarConfig config = new FarConfig();
            preset.apply(config);
            assertTrue(config.maxDistantMobs < FarConfig.UNLIMITED_MOBS, preset + " has no limit on mobs");
            assertTrue(config.mobMaxDistance > 0, preset + " has no limit on distance");
        }
    }
}
