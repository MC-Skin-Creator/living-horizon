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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 21: on a few real dates - New Year's Eve and Day, the 14th of July, the 17th of
 * May (the game's birthday) - once in the night, fireworks go up over a village far off for
 * the best part of a minute, in the colours of the day, and their booms come late and faint,
 * as sound does from that far. Only on this screen.
 *
 * <p>Its dates are its own: the {@link EggCalendar} does not pace it ({@link #paced}). The
 * village is one {@link GhostVillager#villages} knows, its ground read in the loaded chunks or
 * the far world ({@link Columns}). The rockets and their sparks are drawn here, glowing boxes a
 * few pixels across, since the game's own firework particles end at the edge of its view.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Fireworks implements Egg {
    /** Ticks rockets go up for; the last of them, a quicker volley; ticks after it everything has fallen. */
    private static final int SHOW = 1000, FINALE = 140, AFTER = 200;
    /** How far off the village may be by chance, in blocks across; blocks in front of the player when called without one. */
    private static final double MIN = 80, MAX = 400, AHEAD = 150;
    /** Blocks from the village's middle rockets go up from. */
    private static final double SPREAD = 10;
    /** A rocket's speed as it leaves, in blocks per tick, and the ticks it climbs: at least and at most. */
    private static final double SPEED_MIN = 1.6, SPEED_MAX = 2.2;
    private static final int CLIMB_MIN = 30, CLIMB_MAX = 40;
    /** Sparks' speed kept from one tick to the next; blocks per tick they fall faster each tick. */
    private static final double DRAG = 0.92, GRAVITY = 0.006;
    /** Ticks a burst lasts, at least and at most; ticks of the flash it bursts with. */
    private static final int LIFE_MIN = 40, LIFE_MAX = 60, FLASH = 3;
    /** Points in a rocket's trail. */
    private static final int TRAIL = 5;
    /** Blocks across a spark at least; pixels across it stays, however far. */
    private static final float SMALLEST = 0.35f, PIXELS = 2.2f;
    /** Blocks per tick sound goes: 343 metres a second. */
    private static final double SOUND = 343.0 / 20;
    /** Blocks from the player the boom is played at, in the burst's direction; how loud it is at most. */
    private static final double HEARD = 6;
    private static final float LOUDEST = 0.5f;
    /** Ticks a search may take. */
    private static final int SEARCH = 200;

    /** The colours of each date, and of any other day for a show called. */
    private enum Palette {
        NEW_YEAR(0xFFC850, 0xE6E6FF, 0xFF5050, 0x50C8FF, 0xC878FF),
        JULY_14(0xED2939, 0xFFFFFF, 0x3C6EFF),
        BIRTHDAY(0x5AC83C, 0x46E6E6, 0xFFD24A, 0xFFFFFF),
        ANY();

        final int[] colours;

        Palette(int... colours) {
            this.colours = colours;
        }

        /** The palette of {@code date}: null on any day without fireworks. */
        static @Nullable Palette of(LocalDate date) {
            int month = date.getMonthValue(), day = date.getDayOfMonth();
            if ((month == 12 && day == 31) || (month == 1 && day == 1)) return NEW_YEAR;
            if (month == 7 && day == 14) return JULY_14;
            if (month == 5 && day == 17) return BIRTHDAY;
            return null;
        }
    }

    /** The shapes a burst takes. */
    private enum Kind { BALL, LARGE, RING }

    /** A rocket on its way up. */
    private record Rocket(int launched, double x, double y, double z, double driftX, double driftZ, double speed, int climb) {
        /** Where it is {@code t} ticks after it left: slower and slower. */
        Vec3 at(double t) {
            return new Vec3(x + driftX * t, y + speed * t * (1 - 0.35 * t / climb), z + driftZ * t);
        }
    }

    /** A burst of sparks: where and when, each spark's direction, how fast they leave, their two colours. */
    private record Burst(int burst, Vec3 at, float[] directions, double speed, int first, int second, int life, boolean twinkles) {
        int sparks() {
            return directions.length / 3;
        }
    }

    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private final List<Rocket> rockets = new ArrayList<>();
    private final List<Burst> bursts = new ArrayList<>();
    private @Nullable ClientLevel level;
    private boolean searching, active, called;
    private double x, y, z;
    private Palette palette = Palette.ANY;
    /** The night it came last, counted as the calendar counts days: once a night. */
    private int played = Integer.MIN_VALUE;
    private final EggCalendar calendar;
    private int age, nextLaunch, idle, searchAge;

    Fireworks(EggCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public int number() {
        return 21;
    }

    /** Its dates are its own. */
    @Override
    public boolean paced() {
        return false;
    }

    @Override
    public boolean busy() {
        return searching || active;
    }

    /** The middle of the village it goes up over, on the ground, while it is there. */
    @Override
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /** For the game tests: sparks and rockets in the sky now. */
    public int sparks() {
        int count = rockets.size();
        for (Burst burst : bursts) count += burst.sparks();
        return active ? count : 0;
    }

    /**
     * Looks for the village it goes up over and starts once its ground is read: by chance, on
     * its dates, one far off; or because the player asked ({@code called}: at once, whatever the
     * date and the hour, over the nearest village, or else in front of them; called again, it
     * starts over). False if the easter eggs are off or, by chance, no village is around.
     */
    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach());
        List<Vec3> villages = new ArrayList<>();
        for (Vec3 mark : GhostVillager.villages(current, self, far)) {
            double across = Math.hypot(mark.x - self.getX(), mark.z - self.getZ());
            if (across <= far && (called || across >= MIN)) villages.add(mark);
        }
        Vec3 spot;
        if (!villages.isEmpty()) {
            spot = called ? villages.get(0) : villages.get(random.nextInt(villages.size()));
        } else if (called) {
            double ahead = Math.max(16, Math.min(AHEAD, far - 16)), yaw = Math.toRadians(self.getYRot());
            spot = new Vec3(self.getX() - Math.sin(yaw) * ahead, self.getY(), self.getZ() + Math.cos(yaw) * ahead);
        } else {
            return false;
        }
        this.called = called;
        level = current;
        searching = true;
        searchAge = 0;
        int mine = generation.get();
        Executor game = Minecraft.getInstance();
        double feet = self.getY();
        Columns.read(current, new double[]{spot.x}, new double[]{spot.z}).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            searching = false;
            Columns.Column ground = columns[0];
            // Called, the ground of a spot nothing is known of is the player's.
            if (!ground.known() && !called) return;
            begin(ground.x(), ground.known() ? ground.y() : feet, ground.z());
            EggLog.came(21, x, y, z, self, called);
        }, game);
        return true;
    }

    private void begin(double gx, double gy, double gz) {
        Palette today = Palette.of(LocalDate.now());
        palette = today != null ? today : Palette.ANY;
        if (!called) played = calendar.days();
        x = gx;
        y = gy;
        z = gz;
        rockets.clear();
        bursts.clear();
        age = 0;
        nextLaunch = 10;
        active = true;
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // On its dates only, at night, soon after dusk most often: once that night.
            if (quiet && !current.isBrightOutside() && ++idle > 600 && random.nextInt(2400) == 0) {
                idle = 0;
                if (played != calendar.days() && Palette.of(LocalDate.now()) != null) summon(current, self, false);
            }
            return;
        }
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        age++;
        if (!Columns.drawn(current, self, x, z) || age > SHOW + AFTER) {
            clear();
            return;
        }
        // Rockets until the show ends, faster at its end; none once dawn has come, unless called.
        boolean going = age < SHOW && (called || !current.isBrightOutside());
        if (going && age >= nextLaunch) {
            int salvo = random.nextInt(6) == 0 ? 2 + random.nextInt(2) : 1;
            for (int i = 0; i < salvo; i++) launch(i * 3);
            nextLaunch = age + (age > SHOW - FINALE ? 3 + random.nextInt(4) : 10 + random.nextInt(22));
        }
        rockets.removeIf(rocket -> {
            if (age - rocket.launched() < rocket.climb()) return false;
            burst(rocket, self);
            return true;
        });
        bursts.removeIf(burst -> age - burst.burst() > burst.life());
        if (!going && rockets.isEmpty() && bursts.isEmpty()) clear();
    }

    /** A rocket from somewhere round the village, {@code later} ticks from now. */
    private void launch(int later) {
        double angle = random.nextDouble() * Math.PI * 2, off = Math.sqrt(random.nextDouble()) * SPREAD;
        rockets.add(new Rocket(age + later, x + Math.cos(angle) * off, y + 1, z + Math.sin(angle) * off,
                random.nextGaussian() * 0.03, random.nextGaussian() * 0.03,
                SPEED_MIN + random.nextDouble() * (SPEED_MAX - SPEED_MIN), CLIMB_MIN + random.nextInt(CLIMB_MAX - CLIMB_MIN + 1)));
    }

    /** {@code rocket} bursts where it got to, and its boom sets out towards the player. */
    private void burst(Rocket rocket, LocalPlayer self) {
        Kind kind = Kind.values()[random.nextInt(Kind.values().length)];
        int count = kind == Kind.LARGE ? 110 + random.nextInt(30) : kind == Kind.RING ? 60 + random.nextInt(20) : 70 + random.nextInt(30);
        float[] directions = new float[count * 3];
        // A ring lies in a plane of its own, tilted at random; a ball goes every way.
        Vec3 normal = new Vec3(random.nextGaussian(), random.nextGaussian() + 2, random.nextGaussian()).normalize();
        Vec3 u = normal.cross(new Vec3(1, 0, 0)).lengthSqr() < 1e-4 ? normal.cross(new Vec3(0, 0, 1)).normalize()
                : normal.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 v = normal.cross(u);
        for (int i = 0; i < count; i++) {
            Vec3 d;
            if (kind == Kind.RING) {
                double a = i * Math.PI * 2 / count;
                d = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
            } else {
                d = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            }
            directions[i * 3] = (float) d.x;
            directions[i * 3 + 1] = (float) d.y;
            directions[i * 3 + 2] = (float) d.z;
        }
        int[] colours = palette.colours;
        int first = colours.length == 0 ? hue() : colours[random.nextInt(colours.length)];
        int second = random.nextInt(3) == 0 ? (colours.length == 0 ? hue() : colours[random.nextInt(colours.length)]) : first;
        double speed = (kind == Kind.LARGE ? 0.9 : kind == Kind.RING ? 0.7 : 0.6) * (0.85 + random.nextDouble() * 0.3);
        Vec3 at = rocket.at(rocket.climb());
        bursts.add(new Burst(age, at, directions, speed, first, second,
                LIFE_MIN + random.nextInt(LIFE_MAX - LIFE_MIN + 1), random.nextBoolean()));
        boom(at, self, kind == Kind.LARGE);
    }

    /** A bright colour of any hue, for a show on another day. */
    private int hue() {
        return Mth.hsvToRgb(random.nextFloat(), 0.75f + random.nextFloat() * 0.25f, 1f) & 0xFFFFFF;
    }

    /**
     * The boom of a burst at {@code at}: faint, late by the time sound takes to come that far,
     * played a few blocks from the player in its direction, where they would hear it come from.
     */
    private void boom(Vec3 at, LocalPlayer self, boolean large) {
        Vec3 ear = self.getEyePosition(), towards = at.subtract(ear);
        double distance = towards.length();
        if (distance < 1) return;
        Vec3 heard = ear.add(towards.scale(HEARD / distance));
        int late = (int) Math.round(distance / SOUND);
        float volume = (float) Mth.clamp(LOUDEST - distance / 1000, 0.15, LOUDEST);
        SoundEvent sound = large ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR : SoundEvents.FIREWORK_ROCKET_BLAST_FAR;
        Minecraft.getInstance().getSoundManager().playDelayed(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume,
                0.9f + random.nextFloat() * 0.2f, random, heard.x, heard.y, heard.z), late);
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = false;
        rockets.clear();
        bursts.clear();
        level = null;
    }

    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        Minecraft minecraft = Minecraft.getInstance();
        View view = new View(camera, DepthFar.of(minecraft) * 0.95, 1.0 / DepthFar.pixelsPerRadian(minecraft));
        double time = age + partial;
        Glows glows = new Glows();
        for (Rocket rocket : rockets) {
            double t = time - rocket.launched();
            if (t < 0) continue;
            // Its head, white, and a trail of embers behind it.
            view.add(glows, rocket.at(t), 1.2f, 0xFFF4D8, 255);
            for (int k = 1; k <= TRAIL; k++) {
                if (t - k * 1.5 < 0) break;
                view.add(glows, rocket.at(t - k * 1.5), 1f - k * 0.12f, 0xFFB040, 220 - k * 38);
            }
        }
        for (Burst burst : bursts) {
            double t = time - burst.burst();
            // Out fast, slowing, then falling: the drag and the fall worked out for any moment.
            double out = burst.speed() * (1 - Math.pow(DRAG, t)) / (1 - DRAG), fall = GRAVITY * t * t / 2;
            float fade = (float) Mth.clamp(2 - 2 * t / burst.life(), 0, 1);
            if (t < FLASH) view.add(glows, burst.at(), 7f, 0xFFFFFF, Math.round(90 * (1 - (float) t / FLASH)));
            int tick = age - burst.burst();
            for (int i = 0; i < burst.sparks(); i++) {
                float[] d = burst.directions();
                Vec3 at = burst.at().add(d[i * 3] * out, d[i * 3 + 1] * out - fall, d[i * 3 + 2] * out);
                int colour = (i & 1) == 0 ? burst.first() : burst.second();
                // White as they leave, their colour after; twinkling as they die, for some.
                if (t < 2) colour = 0xFFFFFF;
                float shine = fade;
                if (burst.twinkles() && t > burst.life() * 0.4 && ((i * 31 + tick * 17) & 3) == 0) shine *= 0.2f;
                view.add(glows, at, 1f, colour, Math.round(235 * shine));
            }
        }
        GhostRenderer.noteDistance(view.farthest + 5);
        if (glows.count > 0) collector.draw(pose, Ufo.GLOW, glows::draw);
        if (called) CalledBeam.submit(pose, camera, collector, x, y, z);
    }

    /** Where a point is drawn this frame: pulled in past the far plane, never under a few pixels. */
    private static final class View {
        private final Vec3 camera;
        private final double reach, perPixel;
        private double farthest;

        View(Vec3 camera, double reach, double perPixel) {
            this.camera = camera;
            this.reach = reach;
            this.perPixel = perPixel;
        }

        /** A spark at {@code at}, {@code size} times the usual one. */
        void add(Glows glows, Vec3 at, float size, int colour, int alpha) {
            if (alpha <= 0) return;
            double px = at.x - camera.x, py = at.y - camera.y, pz = at.z - camera.z;
            double distance = Math.sqrt(px * px + py * py + pz * pz);
            farthest = Math.max(farthest, distance);
            float pull = distance > reach ? (float) (reach / distance) : 1f;
            float half = (float) Math.max(SMALLEST, distance * PIXELS * perPixel) / 2 * size * pull;
            glows.add((float) (px * pull), (float) (py * pull), (float) (pz * pull), half, colour, alpha);
        }
    }

    /** Glowing boxes gathered for one frame: their middle, half their size, colour and alpha. */
    private static final class Glows {
        private float[] at = new float[4 * 256];
        private int[] colour = new int[2 * 256];
        private int count;

        void add(float x, float y, float z, float half, int rgb, int alpha) {
            if (count * 4 == at.length) {
                at = Arrays.copyOf(at, at.length * 2);
                colour = Arrays.copyOf(colour, colour.length * 2);
            }
            at[count * 4] = x;
            at[count * 4 + 1] = y;
            at[count * 4 + 2] = z;
            at[count * 4 + 3] = half;
            colour[count * 2] = rgb;
            colour[count * 2 + 1] = Math.min(255, alpha);
            count++;
        }

        void draw(PoseStack.Pose pose, VertexConsumer out) {
            for (int i = 0; i < count; i++) {
                float x = at[i * 4], y = at[i * 4 + 1], z = at[i * 4 + 2], h = at[i * 4 + 3];
                int rgb = colour[i * 2];
                Ufo.box(pose, out, x - h, y - h, z - h, x + h, y + h, z + h,
                        rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, colour[i * 2 + 1], Ufo.BRIGHT);
            }
        }
    }
}
