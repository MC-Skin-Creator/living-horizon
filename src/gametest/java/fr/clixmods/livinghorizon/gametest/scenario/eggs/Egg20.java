package fr.clixmods.livinghorizon.gametest.scenario.eggs;

import fr.clixmods.livinghorizon.ambient.Signal;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Easter egg 20 at midnight, from a stone tower 12 blocks high built 150 blocks ahead, the
 * highest point around: it shines from the tower's top; its light, read tick after tick, says
 * its message three times in Morse code with a rest between, and it ends by itself. Called
 * again, a screenshot while it is lit; the player at the tower's foot, it stops.
 */
public final class Egg20 {
    /** The Morse code of the letters A to Z, written here again so that the test does not read it from the class. */
    private static final String[] CODE = {".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---", "-.-",
            ".-..", "--", "-.", "---", ".--.", "--.-", ".-.", "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--.."};

    private Egg20() {
    }

    private static Signal signal() {
        return FarPlayerTracker.get().ambience().signal();
    }

    public static void run(ClientGameTestContext context, TestSingleplayerContext world, Scenario scenario) {
        Scene.command(world, "tp @a " + 20 * 1000 + " -60 0 0 0");
        context.waitTicks(100);
        Scene.command(world, "execute at @p run fill ~-2 ~ ~148 ~2 ~11 ~152 minecraft:stone");
        Scene.command(world, "time set midnight");
        context.waitTicks(20);
        int top = context.computeOnClient(minecraft -> minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(minecraft.player.getX()), Mth.floor(minecraft.player.getZ()) + 150));
        boolean on = context.computeOnClient(minecraft -> signal().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee20 did not start");
        Vec3 at = await(context);
        context.runOnClient(minecraft -> {
            double across = Math.hypot(at.x - minecraft.player.getX(), at.z - minecraft.player.getZ());
            Scene.log("eggs2 ee20 at " + at + ", " + (int) across + " blocks, the tower's top at " + top
                    + ", sending " + signal().message());
            if (Math.abs(at.y - top) > 0.5) throw new AssertionError("ee20 at height " + at.y + ", not on the tower's top at " + top);
            if (Math.abs(across - 150) > 6) throw new AssertionError("ee20 at " + (int) across + " blocks, not on the tower");
        });

        // Lit or dark, every tick until it ends by itself.
        String message = context.computeOnClient(minecraft -> signal().message());
        List<Boolean> ticks = new ArrayList<>();
        boolean ended = false;
        for (int i = 0; i < 900; i++) {
            int state = context.computeOnClient(minecraft -> !signal().busy() ? 0 : signal().lit() ? 2 : 1);
            if (state == 0) {
                ended = true;
                break;
            }
            ticks.add(state == 2);
            context.waitTick();
        }
        if (!ended) throw new AssertionError("ee20 still sending after 45 seconds");
        String read = decode(ticks);
        String expected = String.join("|", encode(message), encode(message), encode(message));
        Scene.log("eggs2 ee20 read " + read + " over " + ticks.size() + " ticks, expected " + expected);
        if (!read.equals(expected)) throw new AssertionError("ee20 sent " + read + ", not " + message + " three times: " + expected);

        // Called again: a picture while it is lit.
        on = context.computeOnClient(minecraft -> signal().summon(minecraft.level, minecraft.player, true));
        if (!on) throw new AssertionError("ee20 did not start again");
        Vec3 again = await(context);
        context.runOnClient(minecraft -> {
            lookAt(minecraft.player, again.x, again.y + 1, again.z);
            minecraft.options.fov().set(30);
        });
        boolean lit = false;
        for (int i = 0; i < 200 && !lit; i++) {
            context.waitTick();
            lit = context.computeOnClient(minecraft -> signal().lit());
        }
        if (!lit) throw new AssertionError("ee20 never lit");
        // At once: a dot is lit four ticks only, too short for the usual wait before a screenshot.
        Scene.log("screenshot " + context.takeScreenshot(scenario.name() + "-ee20-lit"));
        context.waitTicks(10);
        if (!context.computeOnClient(minecraft -> signal().busy())) throw new AssertionError("ee20 gone too soon");
        // The player at the tower's foot: it stops.
        Scene.command(world, "tp @a " + again.x + " -60 " + (again.z - 30));
        context.waitTicks(10);
        if (context.computeOnClient(minecraft -> signal().busy())) throw new AssertionError("ee20 still there with the player 30 blocks off");
        context.runOnClient(minecraft -> minecraft.options.fov().set(70));
    }

    /**
     * The light read as Morse code: dots and dashes, {@code /} between letters, a space between
     * words, {@code |} between two times the message is sent. What is dark before it starts is
     * left out; a length the code does not know is written {@code ?}.
     */
    private static String decode(List<Boolean> ticks) {
        StringBuilder read = new StringBuilder();
        int unit = Signal.UNIT, i = 0;
        while (i < ticks.size() && !ticks.get(i)) i++;
        while (i < ticks.size()) {
            boolean lit = ticks.get(i);
            int run = 0;
            while (i < ticks.size() && ticks.get(i) == lit) {
                run++;
                i++;
            }
            if (lit) {
                read.append(run <= 2 * unit ? "." : run <= 4 * unit ? "-" : "?");
            } else if (i < ticks.size()) {
                // One unit inside a letter, three between letters, seven between words, longer between repeats.
                if (run > 10 * unit) read.append('|');
                else if (run > 5 * unit) read.append(' ');
                else if (run > 2 * unit) read.append('/');
            }
        }
        return read.toString();
    }

    /** {@code message} in Morse code, written as {@link #decode} writes it. */
    private static String encode(String message) {
        List<String> words = new ArrayList<>();
        for (String word : message.trim().split("\\s+")) {
            List<String> letters = new ArrayList<>();
            for (char letter : word.toUpperCase(java.util.Locale.ROOT).toCharArray()) letters.add(CODE[letter - 'A']);
            words.add(String.join("/", letters));
        }
        return String.join(" ", words);
    }

    /** Where it came, once it has: a few seconds at most. */
    private static Vec3 await(ClientGameTestContext context) {
        for (int i = 0; i < 100; i++) {
            Vec3 at = context.computeOnClient(minecraft -> signal().position());
            if (at != null) return at;
            context.waitTick();
        }
        throw new AssertionError("ee20 found no place");
    }

    private static void lookAt(LocalPlayer player, double x, double y, double z) {
        double dx = x - player.getX(), dy = y - player.getEyeY(), dz = z - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }
}
