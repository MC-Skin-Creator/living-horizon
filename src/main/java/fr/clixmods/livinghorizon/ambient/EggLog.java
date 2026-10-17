package fr.clixmods.livinghorizon.ambient;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * For whoever works on the easter eggs, the game started with {@code -Dlh.ee=true}: their
 * commands, and in the log where each one comes, or that it found nowhere to. Without it,
 * nothing: a player reading the log learns nothing of them either.
 */
public final class EggLog {
    /** The game was started for work on the easter eggs. */
    public static final boolean ON = Boolean.getBoolean("lh.ee");

    private EggLog() {
    }

    /** Easter egg {@code number} came here. */
    static void came(int number, double x, double y, double z, LocalPlayer self, boolean called) {
        if (!ON) return;
        LivingHorizonClient.LOGGER.info("ee{} at {} {} {}, {} blocks away, {}", number, Mth.floor(x), Mth.floor(y), Mth.floor(z),
                Math.round(Math.hypot(x - self.getX(), z - self.getZ())), called ? "called" : "by chance");
    }

    /** Easter egg {@code number}, called, found nowhere to come. */
    static void nowhere(int number) {
        if (ON) LivingHorizonClient.LOGGER.info("ee{} found no place", number);
    }
}
