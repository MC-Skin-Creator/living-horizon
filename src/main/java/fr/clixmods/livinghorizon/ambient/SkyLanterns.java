package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 13: some night, from a village far off, a few dozen paper lanterns rise into the
 * sky one after the other, warm and flickering, drifting together on a light wind and swaying a
 * little, and fade out high above it. Only on this screen.
 *
 * <p>The village is one {@link GhostVillager#villages} knows, 80 blocks off at least and in
 * sight above its roofs ({@link Sight}); where each lantern leaves from is read around it, in
 * the loaded chunks or the far world ({@link Columns}). Each lantern is a glowing box round a
 * brighter flame, in a dim halo, drawn larger the farther it is so that it stays a few pixels
 * across.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class SkyLanterns implements Egg {
    /** How far off the village may be, in blocks across; blocks across under which they go. */
    private static final double MIN = 80, MAX = 400, NEAR = 48;
    /** Blocks from the village's middle they leave from, at most; marks closer than this are one village. */
    private static final double SPREAD = 12.5, SAME_VILLAGE = 30;
    /** Blocks above the ground they rise to, at least and at most. */
    private static final double CEILING_MIN = 120, CEILING_MAX = 200;
    /** Blocks above the village it must be seen at: the lanterns are seen once they are up. */
    private static final double SEEN_ABOVE = 30;
    /** Lanterns, at least and at most. */
    private static final int FEWEST = 30, MOST = 60;
    /** Ticks over which they leave, one after the other; ticks one takes to reach its height, at least and at most. */
    private static final int RELEASE = 400, CLIMB_MIN = 1000, CLIMB_MAX = 1500;
    /** Ticks a lantern takes to pick up speed, to light up as it leaves; ticks they all fade at dawn. */
    private static final int LIFT = 60, KINDLE = 40, FADE = 60;
    /** The part of its climb over which a lantern fades out, at the top. */
    private static final double FADE_PART = 0.2;
    /** Blocks per tick the wind carries them, at least and at most, up high; blocks they sway. */
    private static final double WIND_MIN = 0.025, WIND_MAX = 0.045, SWAY = 0.8;
    /** Villages tried at most; ticks a search may take. */
    private static final int VILLAGES = 6, SEARCH = 400;
    /** Blocks across a lantern and its height; pixels across it stays at least, however far. */
    private static final float WIDTH = 0.55f, HEIGHT = 0.7f, PIXELS = 3f;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, active, called;
    /** The village's middle, on its ground; the wind, in blocks per tick. */
    private double x, y, z, windX, windZ;
    /** Each lantern: where it leaves from, how high it goes, its sway and its flicker. */
    private double[] fromX = new double[0], fromY = new double[0], fromZ = new double[0], ceiling = new double[0];
    private float[] phase = new float[0], green = new float[0];
    /** Each lantern: the tick it leaves, the ticks it climbs. */
    private int[] delay = new int[0], climb = new int[0];
    /** Ticks since they began; the tick the last is gone; ticks they have been fading at dawn, -1 until then. */
    private int age, end, leaving = -1, idle, searchAge;

    SkyLanterns(EggCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public int number() {
        return 13;
    }

    @Override
    public boolean busy() {
        return searching || active;
    }

    /** The village's middle they leave from, while they are there. */
    @Override
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /** For the game tests: how many lanterns are in the air now. */
    public int aloft() {
        int count = 0;
        for (int i = 0; active && i < delay.length; i++) {
            double t = age - delay[i];
            if (t >= 0 && height(i, t) < ceiling[i]) count++;
        }
        return count;
    }

    /** For the game tests: how high the lanterns in the air are above the ground, on average; 0 if none is. */
    public double rise() {
        double sum = 0;
        int count = 0;
        for (int i = 0; active && i < delay.length; i++) {
            double t = age - delay[i];
            if (t < 0) continue;
            double h = height(i, t);
            if (h >= ceiling[i]) continue;
            sum += fromY[i] + h - y;
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    /**
     * Looks for a village far off, in sight, and lets the lanterns go there once its ground is
     * read: by chance, or because the player asked ({@code called}: the nearest village first;
     * called again, it starts over). False if the easter eggs are off or no village is around.
     */
    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach());
        List<Vec3> villages = new ArrayList<>();
        if (far > MIN) {
            for (Vec3 mark : GhostVillager.villages(current, self, far)) {
                double across = Math.hypot(mark.x - self.getX(), mark.z - self.getZ());
                if (across < MIN || across > far) continue;
                // A bell and the villagers round it are one village.
                boolean known = false;
                for (Vec3 other : villages) known |= Math.hypot(other.x - mark.x, other.z - mark.z) < SAME_VILLAGE;
                if (!known) villages.add(mark);
            }
        }
        if (villages.isEmpty()) {
            if (called) EggLog.nowhere(13);
            return false;
        }
        // Called, the nearest first; by chance, any of them.
        for (int i = villages.size() - 1; i > 0 && !called; i--) Collections.swap(villages, i, random.nextInt(i + 1));
        if (villages.size() > VILLAGES) villages = new ArrayList<>(villages.subList(0, VILLAGES));
        int count = FEWEST + random.nextInt(MOST - FEWEST + 1), per = 1 + count;
        // Each village's middle, then where each lantern leaves from around it.
        double[] xs = new double[villages.size() * per], zs = new double[xs.length];
        for (int v = 0; v < villages.size(); v++) {
            Vec3 village = villages.get(v);
            xs[v * per] = village.x;
            zs[v * per] = village.z;
            for (int i = 1; i < per; i++) {
                double angle = random.nextDouble() * Math.PI * 2, off = Math.sqrt(random.nextDouble()) * SPREAD;
                xs[v * per + i] = village.x + Math.cos(angle) * off;
                zs[v * per + i] = village.z + Math.sin(angle) * off;
            }
        }
        this.called = called;
        level = current;
        searching = true;
        searchAge = 0;
        int mine = generation.get();
        Executor game = Minecraft.getInstance();
        Vec3 eye = self.getEyePosition();
        int tried = villages.size();
        Columns.read(current, xs, zs).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            List<Integer> known = new ArrayList<>();
            for (int v = 0; v < tried; v++) if (columns[v * per].known()) known.add(v);
            double[] hx = new double[known.size()], hy = new double[known.size()], hz = new double[known.size()];
            for (int k = 0; k < known.size(); k++) {
                Columns.Column middle = columns[known.get(k) * per];
                hx[k] = middle.x();
                hy[k] = middle.y() + SEEN_ABOVE;
                hz[k] = middle.z();
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                searching = false;
                for (int k = 0; k < seen.length; k++) {
                    if (!seen[k]) continue;
                    int v = known.get(k);
                    Columns.Column[] around = new Columns.Column[per];
                    System.arraycopy(columns, v * per, around, 0, per);
                    begin(around);
                    EggLog.came(13, x, y, z, self, called);
                    return;
                }
                if (called) EggLog.nowhere(13);
            }, game);
        }, game);
        return true;
    }

    /** {@code around}: the village's middle, then where each lantern leaves from. */
    private void begin(Columns.Column[] around) {
        if (!called) calendar.shown(13);
        Columns.Column middle = around[0];
        x = middle.x();
        y = middle.y();
        z = middle.z();
        int count = around.length - 1;
        fromX = new double[count];
        fromY = new double[count];
        fromZ = new double[count];
        ceiling = new double[count];
        phase = new float[count];
        green = new float[count];
        delay = new int[count];
        climb = new int[count];
        double wind = random.nextDouble() * Math.PI * 2, speed = WIND_MIN + random.nextDouble() * (WIND_MAX - WIND_MIN);
        windX = Math.cos(wind) * speed;
        windZ = Math.sin(wind) * speed;
        end = 0;
        for (int i = 0; i < count; i++) {
            Columns.Column spot = around[i + 1];
            // Over the roofs where the ground is known, from the village's ground where it is not.
            double ground = spot.known() && Math.abs(spot.y() - y) < 12 ? spot.y() : y;
            fromX[i] = spot.x();
            fromY[i] = ground + 1.5 + random.nextDouble() * 2;
            fromZ[i] = spot.z();
            ceiling[i] = CEILING_MIN + random.nextDouble() * (CEILING_MAX - CEILING_MIN);
            phase[i] = random.nextFloat() * 6.28f;
            green[i] = 150 + random.nextFloat() * 55;
            // One after the other, never all at once.
            delay[i] = (int) ((i + random.nextDouble()) * RELEASE / count);
            climb[i] = CLIMB_MIN + random.nextInt(CLIMB_MAX - CLIMB_MIN + 1);
            end = Math.max(end, delay[i] + climb[i] + LIFT / 2 + 1);
        }
        age = 0;
        leaving = -1;
        active = true;
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // Rare, and only at night: about once in an hour of it, where a village is around.
            if (++idle > 6000 && quiet && !current.isBrightOutside() && random.nextInt(72000) == 0) {
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
        if (age > end || Math.hypot(self.getX() - x, self.getZ() - z) < NEAR || !Columns.drawn(current, self, x, z)) {
            clear();
            return;
        }
        // Dawn: they fade, all together.
        if (leaving < 0 && !called && current.isBrightOutside()) leaving = 0;
        if (leaving >= 0 && ++leaving > FADE) clear();
    }

    /** Blocks lantern {@code i} has risen, {@code t} ticks after it left: slowly at first, then steadily. */
    private double height(int i, double t) {
        double going = t < LIFT ? t * t / (2.0 * LIFT) : t - LIFT / 2.0;
        return ceiling[i] * going / climb[i];
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = false;
        level = null;
    }

    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        Minecraft minecraft = Minecraft.getInstance();
        double reach = DepthFar.of(minecraft) * 0.95, perPixel = 1.0 / DepthFar.pixelsPerRadian(minecraft);
        float shown = leaving < 0 ? 1f : Mth.clamp(1f - (leaving + partial) / FADE, 0f, 1f);
        double time = age + partial, farthest = 0;
        // The flames first, then the lanterns round them, then their halos: a halo drawn first
        // would hide what is inside it.
        Glows flames = new Glows(), bodies = new Glows(), halos = new Glows();
        for (int i = 0; i < delay.length; i++) {
            double t = time - delay[i];
            if (t < 0) continue;
            double h = height(i, t);
            if (h >= ceiling[i]) continue;
            float alpha = shown * (float) Math.min(1.0, t / KINDLE)
                    * (float) Mth.clamp((1 - h / ceiling[i]) / FADE_PART, 0, 1);
            if (alpha <= 0.01f) continue;
            // The wind blows harder up high; each sways on its own.
            double carried = (t < LIFT ? t * t / (2.0 * LIFT) : t - LIFT / 2.0) * (0.4 + 0.6 * Math.min(1, h / 60));
            double lx = fromX[i] + windX * carried + SWAY * Math.sin(t * 0.03 + phase[i]);
            double lz = fromZ[i] + windZ * carried + SWAY * Math.cos(t * 0.025 + phase[i] * 1.7);
            double px = lx - camera.x, py = fromY[i] + h - camera.y, pz = lz - camera.z;
            double distance = Math.sqrt(px * px + py * py + pz * pz);
            farthest = Math.max(farthest, distance);
            float pull = distance > reach ? (float) (reach / distance) : 1f;
            // Never smaller than a few pixels, so that it reads from afar.
            float grow = (float) Math.max(1.0, distance * PIXELS * perPixel / WIDTH) * pull;
            float cx = (float) (px * pull), cy = (float) (py * pull), cz = (float) (pz * pull);
            float half = WIDTH / 2 * grow, tall = HEIGHT / 2 * grow;
            // A candle's flicker: two slow waves out of step.
            float flicker = 0.8f + 0.2f * (float) (0.5 + 0.5 * Math.sin(t * 0.7 + phase[i]) * Math.sin(t * 0.23 + phase[i] * 2));
            int r = Math.round(255 * flicker), g = Math.round(green[i] * flicker), b = Math.round(70 * flicker);
            flames.add(cx, cy - tall * 0.45f, cz, half * 0.45f, tall * 0.3f, 255, 240, 170, Math.round(255 * alpha));
            bodies.add(cx, cy, cz, half, tall, r, g, b, Math.round(200 * alpha));
            halos.add(cx, cy, cz, half * 2.4f, tall * 2.0f, 255, Math.round(green[i] * 0.85f), 60, Math.round(45 * alpha * flicker));
        }
        GhostRenderer.noteDistance(farthest + 5);
        if (flames.count > 0) {
            collector.draw(pose, Ufo.GLOW, (at, out) -> {
                flames.draw(at, out);
                bodies.draw(at, out);
                halos.draw(at, out);
            });
        }
        if (called) CalledBeam.submit(pose, camera, collector, x, y, z);
    }

    /** Glowing boxes gathered for one frame, drawn in the order they came: their middle, half their size, colour. */
    private static final class Glows {
        private float[] at = new float[5 * 64];
        private int[] colour = new int[2 * 64];
        private int count;

        void add(float x, float y, float z, float half, float tall, int r, int g, int b, int alpha) {
            if (alpha <= 0) return;
            if (count * 5 == at.length) {
                at = java.util.Arrays.copyOf(at, at.length * 2);
                colour = java.util.Arrays.copyOf(colour, colour.length * 2);
            }
            int k = count * 5;
            at[k] = x;
            at[k + 1] = y;
            at[k + 2] = z;
            at[k + 3] = half;
            at[k + 4] = tall;
            colour[count * 2] = Mth.clamp(r, 0, 255) << 16 | Mth.clamp(g, 0, 255) << 8 | Mth.clamp(b, 0, 255);
            colour[count * 2 + 1] = Math.min(255, alpha);
            count++;
        }

        void draw(PoseStack.Pose pose, VertexConsumer out) {
            for (int i = 0; i < count; i++) {
                int k = i * 5, rgb = colour[i * 2];
                float x = at[k], y = at[k + 1], z = at[k + 2], half = at[k + 3], tall = at[k + 4];
                Ufo.box(pose, out, x - half, y - tall, z - half, x + half, y + tall, z + half,
                        rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, colour[i * 2 + 1], Ufo.BRIGHT);
            }
        }
    }
}
