package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 20: some night, on top of the highest point far off, a bright white light blinks
 * in Morse code, its message three times over with a pause between: SOS most nights, now and
 * then LH. It stops when the player comes near, or once it has said it. Only on this screen.
 *
 * <p>The summit is the highest of many columns read 120 to 400 blocks off, in the loaded chunks
 * or the far world ({@link Columns}), looked for again around the highest ones to find their
 * top, and in sight ({@link Sight}). The light is a small glowing box in two dimmer halos,
 * larger the farther it is, so that it stays a few pixels across; its timing is the standard
 * one: a dot one unit, a dash three, one unit dark inside a letter, three between letters,
 * seven between words.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Signal implements Egg {
    /** Ticks in a unit of Morse code. */
    public static final int UNIT = 4;
    /** Units dark: inside a letter, between letters, between words, between two times the message is sent. */
    private static final int SYMBOL_GAP = 1, LETTER_GAP = 3, WORD_GAP = 7, REST = 14;
    /** Times the message is sent; ticks dark before the first. */
    private static final int REPEATS = 3, LEAD = 40;
    /** The usual message, and the rarer one; one night in five, the rarer. */
    private static final String SOS = "SOS", RARE = "LH";
    private static final int RARE_CHANCE = 5;
    /** The Morse code of the letters A to Z. */
    private static final String[] CODE = {".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---", "-.-",
            ".-..", "--", "-.", "---", ".--.", "--.-", ".-.", "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--.."};
    /** How far off the summit may be, in blocks across: by chance, when called, at most; under which it stops. */
    private static final double MIN = 120, CALLED_MIN = 80, MAX = 400, NEAR = 64;
    /** Blocks above the summit's ground the light shines at. */
    private static final double ABOVE = 1.0;
    /** Bearings and rings of columns read, by chance all around and when called in front. */
    private static final int BEARINGS = 40, RINGS = 14, CALLED_BEARINGS = 21;
    /** Radians between two bearings when called; blocks between two rings when called. */
    private static final double CALLED_STEP = 0.03, CALLED_RING = 4;
    /** Highest columns looked at again round about, and the columns read round each. */
    private static final int TOPS = 12, ROUND = 16;
    /** Highest columns tried for sight at last; ticks a search may take. */
    private static final int SEEN = 24, SEARCH = 400;
    /** Blocks across the light at least, and pixels across it stays, however far. */
    private static final float SMALLEST = 0.3f, PIXELS = 3f;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, active, called;
    private double x, y, z;
    private String message = SOS;
    /** Lit or dark, tick after tick, from the start. */
    private boolean[] timeline = new boolean[0];
    private int age, idle, searchAge;

    Signal(EggCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public int number() {
        return 20;
    }

    @Override
    public boolean busy() {
        return searching || active;
    }

    /** The summit it shines from, on its ground, while it is there. */
    @Override
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /** Whether the light is on this tick. */
    public boolean lit() {
        return active && age < timeline.length && timeline[age];
    }

    /** The message it sends, in letters. */
    public String message() {
        return message;
    }

    /**
     * Looks for the highest point far off, in sight, and starts sending from it once it is read:
     * by chance anywhere around, or because the player asked ({@code called}: in front of them,
     * a little nearer; called again, it starts over). False if the easter eggs are off.
     */
    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach()), near = called ? CALLED_MIN : MIN;
        if (far <= near) {
            if (called) EggLog.nowhere(20);
            return false;
        }
        double look = Math.toRadians(self.getYRot());
        int bearings = called ? CALLED_BEARINGS : BEARINGS;
        int rings = called ? Math.max(1, (int) ((far - near) / CALLED_RING)) : RINGS;
        double[] xs = new double[bearings * rings], zs = new double[xs.length];
        for (int b = 0, i = 0; b < bearings; b++) {
            // Called, a close fan in front, so that a lone tower is not missed between two lines.
            double bearing = called ? look + (b - (bearings - 1) / 2.0) * CALLED_STEP
                    : (b + random.nextDouble()) * Math.PI * 2 / bearings;
            for (int r = 0; r < rings; r++, i++) {
                double distance = near + (r + (called ? 0.5 : random.nextDouble())) * (far - near) / rings;
                xs[i] = Mth.floor(self.getX() - Math.sin(bearing) * distance) + 0.5;
                zs[i] = Mth.floor(self.getZ() + Math.cos(bearing) * distance) + 0.5;
            }
        }
        this.called = called;
        level = current;
        searching = true;
        searchAge = 0;
        int mine = generation.get();
        Executor game = Minecraft.getInstance();
        Vec3 eye = self.getEyePosition();
        double fromX = self.getX(), fromZ = self.getZ();
        Columns.read(current, xs, zs).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            List<Columns.Column> tops = highest(columns, fromX, fromZ, near, far, TOPS);
            // Round each of the highest, two and four blocks off on eight sides: the very top of a hill.
            double[] rx = new double[tops.size() * ROUND], rz = new double[rx.length];
            for (int t = 0; t < tops.size(); t++) {
                for (int k = 0; k < ROUND; k++) {
                    double angle = (k % 8) * Math.PI / 4, off = k < 8 ? 2 : 4;
                    rx[t * ROUND + k] = Mth.floor(tops.get(t).x() + Math.cos(angle) * off) + 0.5;
                    rz[t * ROUND + k] = Mth.floor(tops.get(t).z() + Math.sin(angle) * off) + 0.5;
                }
            }
            Columns.read(current, rx, rz).thenAcceptAsync(round -> {
                if (generation.get() != mine) return;
                Columns.Column[] all = new Columns.Column[tops.size() + round.length];
                for (int t = 0; t < tops.size(); t++) all[t] = tops.get(t);
                System.arraycopy(round, 0, all, tops.size(), round.length);
                List<Columns.Column> candidates = highest(all, fromX, fromZ, near, far, SEEN);
                double[] hx = new double[candidates.size()], hy = new double[candidates.size()], hz = new double[candidates.size()];
                for (int i = 0; i < candidates.size(); i++) {
                    hx[i] = candidates.get(i).x();
                    hy[i] = candidates.get(i).y() + ABOVE;
                    hz[i] = candidates.get(i).z();
                }
                Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                    if (generation.get() != mine) return;
                    searching = false;
                    // The highest in sight: they are in that order.
                    for (int i = 0; i < seen.length; i++) {
                        if (!seen[i]) continue;
                        begin(candidates.get(i));
                        EggLog.came(20, x, y, z, self, called);
                        return;
                    }
                    if (called) EggLog.nowhere(20);
                }, game);
            }, game);
        }, game);
        return true;
    }

    /** The {@code most} highest dry columns of {@code columns} within reach, the highest first. */
    private static List<Columns.Column> highest(Columns.Column[] columns, double fromX, double fromZ,
                                                double near, double far, int most) {
        List<Columns.Column> dry = new ArrayList<>();
        for (Columns.Column column : columns) {
            if (column == null || !column.dry()) continue;
            double across = Math.hypot(column.x() - fromX, column.z() - fromZ);
            if (across >= near && across <= far) dry.add(column);
        }
        dry.sort((a, b) -> Double.compare(b.y(), a.y()));
        return dry.size() > most ? new ArrayList<>(dry.subList(0, most)) : dry;
    }

    private void begin(Columns.Column top) {
        if (!called) calendar.shown(20);
        x = top.x();
        y = top.y();
        z = top.z();
        message = random.nextInt(RARE_CHANCE) == 0 ? RARE : SOS;
        timeline = timeline(message);
        age = 0;
        active = true;
    }

    /** Lit or dark tick after tick: a while dark, then the message {@link #REPEATS} times, a rest between. */
    private static boolean[] timeline(String message) {
        List<Boolean> ticks = new ArrayList<>();
        dark(ticks, LEAD);
        for (int repeat = 0; repeat < REPEATS; repeat++) {
            if (repeat > 0) dark(ticks, REST * UNIT);
            String[] words = message.trim().split("\\s+");
            for (int w = 0; w < words.length; w++) {
                if (w > 0) dark(ticks, WORD_GAP * UNIT);
                for (int l = 0; l < words[w].length(); l++) {
                    if (l > 0) dark(ticks, LETTER_GAP * UNIT);
                    String code = CODE[Character.toUpperCase(words[w].charAt(l)) - 'A'];
                    for (int s = 0; s < code.length(); s++) {
                        if (s > 0) dark(ticks, SYMBOL_GAP * UNIT);
                        int on = (code.charAt(s) == '-' ? 3 : 1) * UNIT;
                        for (int t = 0; t < on; t++) ticks.add(true);
                    }
                }
            }
        }
        boolean[] timeline = new boolean[ticks.size()];
        for (int i = 0; i < timeline.length; i++) timeline[i] = ticks.get(i);
        return timeline;
    }

    private static void dark(List<Boolean> ticks, int length) {
        for (int t = 0; t < length; t++) ticks.add(false);
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // Rare, and only at night: about once in forty minutes of it.
            if (++idle > 6000 && quiet && !current.isBrightOutside() && random.nextInt(48000) == 0) {
                idle = 0;
                summon(current, self, false);
            }
            return;
        }
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        age++;
        // Said three times; or the player came to see; or it is day.
        if (age >= timeline.length || Math.hypot(self.getX() - x, self.getZ() - z) < NEAR
                || !Columns.drawn(current, self, x, z) || (!called && current.isBrightOutside())) {
            clear();
        }
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = false;
        level = null;
    }

    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        double px = x - camera.x, py = y + ABOVE - camera.y, pz = z - camera.z;
        if (called) CalledBeam.submit(pose, camera, collector, x, y, z);
        if (!lit()) return;
        Minecraft minecraft = Minecraft.getInstance();
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        GhostRenderer.noteDistance(distance + 5);
        double reach = DepthFar.of(minecraft) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        // A few pixels across however far, and its halos round it, larger and dimmer.
        float core = (float) Math.max(SMALLEST, distance * PIXELS / DepthFar.pixelsPerRadian(minecraft)) / 2;
        float halo = core * 3f, glow = core * 6f;
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        // The core first: a halo drawn before it would hide it.
        collector.draw(pose, Ufo.GLOW, (at, out) -> {
            Ufo.box(at, out, -core, -core, -core, core, core, core, 255, 255, 255, 255, Ufo.BRIGHT);
            Ufo.box(at, out, -halo, -halo, -halo, halo, halo, halo, 225, 238, 255, 70, Ufo.BRIGHT);
            Ufo.box(at, out, -glow, -glow, -glow, glow, glow, glow, 200, 220, 255, 22, Ufo.BRIGHT);
        });
        pose.popPose();
    }
}
