package fr.clixmods.livinghorizon.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.render.DepthFar;
import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.Sink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 14: some day, in a desert, far off on the horizon over the dry sand, a small
 * oasis shows: a few flat-roofed houses of sand, two or three palm trees, a blue pool, all
 * see-through and wavering like hot air. The nearer the player walks, the paler it grows,
 * until it is gone, and it does not come back that day. Only on this screen.
 *
 * <p>Its spot is read in the loaded chunks or the far world ({@link Columns}): sand in a
 * desert or badlands biome, flat around, in sight ({@link Sight}). It is drawn of glowing,
 * see-through boxes, turned to face the player where it came.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Mirage implements Egg {
    private static final int NUMBER = 14;
    /** Walls, roof edge, doors and windows; trunks, leaves; the pool, the grass round it. */
    private static final int WALL = 0xD9C28E, ROOF = 0xE6D3A4, OPENING = 0x5E4630,
            TRUNK = 0x8C6B45, LEAF = 0x4F8C3C, POOL = 0x3F8FD8, GRASS = 0x86A64C;
    /** The pale sky it is mixed with, as hot air blurs what is seen through it; how much of it. */
    private static final int HAZE = 0xD2E4F2;
    private static final float HAZED = 0.25f;
    /** How much of what is behind it hides, out of 255, at most. */
    private static final int ALPHA = 110;
    /** How far off it may come, in blocks across. */
    private static final double MIN = 120, MAX = 350;
    /** Blocks across under which it is gone, whatever its fading says. */
    private static final double NEAR = 80;
    /** The share of its first distance at which it has faded away. */
    private static final double FADED = 0.6;
    /** Blocks off its middle where the ground is read flat and sandy, on four sides. */
    private static final int AROUND = 9;
    /** Spots tried; ticks a search may take; it shows or fades away; it stays at most. */
    private static final int SPOTS = 64, SEARCH = 400, FADE = 60, LIFE = 12000;
    /** About once in forty minutes of daylight in a desert. */
    private static final int CHANCE = 48000;

    /** A house of the oasis, in its own frame: middle, half its width and depth, its height. */
    private record House(float x, float z, float halfWidth, float halfDepth, float height) {
    }

    /** A palm tree: foot, trunk height in blocks, the way it bends, the turn of its crown. */
    private record Palm(float x, float z, int height, float bendX, float bendZ, float turn) {
    }

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, active, called;
    private double x, y, z, start;
    /** Its turn to face the player where it came, in degrees. */
    private float yaw;
    /** How much it shows of what the player's distance leaves it, 0 to 1, this tick and the last. */
    private float fade, fadeO;
    private int age, leaving = -1, idle, searchAge;
    /** The day it was last gone: by chance, not back that day. */
    private int goneDay = -1;
    private List<House> houses = List.of();
    private List<Palm> palms = List.of();

    Mirage(EggCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public int number() {
        return NUMBER;
    }

    @Override
    public boolean busy() {
        return searching || active;
    }

    @Override
    public @Nullable Vec3 position() {
        return active ? new Vec3(x, y, z) : null;
    }

    /**
     * How much it shows, 0 to 1, without its trembling: its coming and going, and how near the
     * player stands. For the game tests.
     */
    public float shown() {
        return active ? shown(0f) : 0f;
    }

    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach());
        if (far <= MIN) {
            if (called) EggLog.nowhere(NUMBER);
            return false;
        }
        double look = Math.toRadians(self.getYRot());
        // Each spot and four around it: the oasis stands on flat sand.
        double[] xs = new double[SPOTS * 5], zs = new double[xs.length];
        int[][] around = {{0, 0}, {AROUND, 0}, {-AROUND, 0}, {0, AROUND}, {0, -AROUND}};
        for (int s = 0, i = 0; s < SPOTS; s++) {
            double bearing = called ? look + random.nextGaussian() * 0.5 : random.nextDouble() * Math.PI * 2;
            double distance = MIN + random.nextDouble() * (far - MIN);
            double cx = Mth.floor(self.getX() - Math.sin(bearing) * distance) + 0.5;
            double cz = Mth.floor(self.getZ() + Math.cos(bearing) * distance) + 0.5;
            for (int[] d : around) {
                xs[i] = cx + d[0];
                zs[i++] = cz + d[1];
            }
        }
        this.called = called;
        level = current;
        searching = true;
        searchAge = 0;
        int mine = generation.get();
        Executor game = Minecraft.getInstance();
        Vec3 eye = self.getEyePosition();
        Columns.read(current, xs, zs).thenAcceptAsync(columns -> {
            if (generation.get() != mine) return;
            List<Columns.Column> sand = new ArrayList<>();
            for (int i = 0; i < columns.length; i += 5) {
                Columns.Column middle = columns[i];
                double across = Math.hypot(middle.x() - self.getX(), middle.z() - self.getZ());
                boolean ok = across >= MIN && across <= far && desert(middle.biome());
                for (int k = 0; k < 5 && ok; k++) {
                    Columns.Column column = columns[i + k];
                    ok = column.dry() && sandy(column.block()) && Math.abs(column.y() - middle.y()) <= 2;
                }
                if (ok) sand.add(middle);
            }
            double[] hx = new double[sand.size()], hy = new double[sand.size()], hz = new double[sand.size()];
            for (int i = 0; i < sand.size(); i++) {
                hx[i] = sand.get(i).x();
                hy[i] = sand.get(i).y() + 2.5;
                hz[i] = sand.get(i).z();
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                searching = false;
                List<Columns.Column> clear = new ArrayList<>();
                for (int i = 0; i < seen.length; i++) if (seen[i]) clear.add(sand.get(i));
                Columns.Column spot = Sight.any(clear, random);
                if (spot == null) {
                    if (called) EggLog.nowhere(NUMBER);
                    return;
                }
                begin(spot, self);
                EggLog.came(NUMBER, x, y, z, self, called);
            }, game);
        }, game);
        return true;
    }

    /** A desert, by the name of its biome: deserts and the badlands. */
    private static boolean desert(String biome) {
        String id = biome.toLowerCase(Locale.ROOT);
        return id.contains("desert") || id.contains("badlands");
    }

    /** Sand, red sand or terracotta on top; a far world that does not say is taken at its biome. */
    private static boolean sandy(@Nullable BlockState block) {
        return block == null || block.is(BlockTags.SAND) || block.is(BlockTags.TERRACOTTA);
    }

    private void begin(Columns.Column spot, LocalPlayer self) {
        if (!called) calendar.shown(NUMBER);
        x = spot.x();
        y = spot.y();
        z = spot.z();
        start = Math.hypot(self.getX() - x, self.getZ() - z);
        // Its front, where the doors are, to the player.
        double dx = self.getX() - x, dz = self.getZ() - z;
        yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        layout();
        age = 0;
        leaving = -1;
        fade = fadeO = 1f;
        active = true;
    }

    /**
     * The oasis in its own frame, +z to the player: the pool in the middle, the palms round
     * it, the houses behind and to the sides, never in front of the pool.
     */
    private void layout() {
        List<House> built = new ArrayList<>();
        int count = 3 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(70 + 220.0 * (i + 0.5) / count + (random.nextDouble() - 0.5) * 20);
            double radius = 10 + random.nextDouble() * 3;
            built.add(new House((float) (Math.sin(angle) * radius), (float) (Math.cos(angle) * radius),
                    2f + random.nextFloat(), 2f + random.nextFloat(), 3f + random.nextFloat() * 1.5f));
        }
        List<Palm> grown = new ArrayList<>();
        int trees = 2 + random.nextInt(2);
        double first = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < trees; i++) {
            double angle = first + Math.PI * 2 * i / trees + (random.nextDouble() - 0.5) * 0.6;
            double radius = 4.2 + random.nextDouble() * 1.3;
            // Leaning out from the water, as palms by a pool do.
            float lean = 0.8f + random.nextFloat() * 0.8f;
            grown.add(new Palm((float) (Math.sin(angle) * radius), (float) (Math.cos(angle) * radius),
                    5 + random.nextInt(3), (float) Math.sin(angle) * lean, (float) Math.cos(angle) * lean,
                    random.nextFloat() * 60f));
        }
        houses = built;
        palms = grown;
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (!busy()) {
            // Rare, by day, with the player in a desert; once gone, not back that day.
            if (++idle > 6000 && quiet && current.isBrightOutside() && random.nextInt(CHANCE) == 0
                    && calendar.days() != goneDay && desert(biome(current, self))) {
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
        fadeO = fade;
        double across = Math.hypot(self.getX() - x, self.getZ() - z);
        // Full from where it came, gone at FADED of that distance.
        fade = (float) Mth.clamp((across - FADED * start) / ((1 - FADED) * start), 0, 1);
        if (across < NEAR || fade <= 0 || !Columns.drawn(current, self, x, z)) {
            gone();
            return;
        }
        // Dusk takes one that came by chance; the end of its time takes any.
        if (leaving < 0 && (age > LIFE || (!called && !current.isBrightOutside()))) leaving = 0;
        if (leaving >= 0 && ++leaving > FADE) gone();
    }

    /** The biome the player stands in, by its id. */
    private static String biome(ClientLevel level, LocalPlayer self) {
        return level.getBiome(BlockPos.containing(self.getX(), self.getY(), self.getZ()))
                .unwrapKey().map(key -> key.identifier().toString()).orElse("");
    }

    /** Gone for good: not back by chance this day. */
    private void gone() {
        goneDay = calendar.days();
        clear();
    }

    private void clear() {
        generation.incrementAndGet();
        searching = active = false;
        level = null;
    }

    /** Its coming, its going and the player's distance, between two ticks. */
    private float shown(float partial) {
        float shown = Mth.clamp((age + partial) / FADE, 0f, 1f) * Mth.lerp(partial, fadeO, fade);
        if (leaving >= 0) shown *= Mth.clamp(1f - (leaving + partial) / FADE, 0f, 1f);
        return shown;
    }

    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!active) return;
        float t = age + partial;
        // Hot air: the light through it trembles.
        float tremble = 0.78f + 0.22f * Mth.sin(t * 0.33f) * Mth.sin(t * 0.091f + 1.3f);
        int alpha = Math.round(ALPHA * shown(partial) * tremble);
        if (alpha <= 0) return;
        double px = x - camera.x, py = y - camera.y, pz = z - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        GhostRenderer.noteDistance(distance + 20);
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        // A slow rise and fall a little over the sand, stretched and squeezed up and down.
        float lift = 0.6f + 0.35f * Mth.sin(t * 0.045f);
        float stretch = 1f + 0.12f * Mth.sin(t * 0.06f) + 0.05f * Mth.sin(t * 0.17f + 0.7f);
        float shimmer = 1f + 0.02f * Mth.sin(t * 0.11f);
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(0f, lift, 0f);
        pose.scale(shimmer, stretch, 1f);
        List<House> built = houses;
        List<Palm> grown = palms;
        collector.draw(pose, Ufo.GLOW, (at, out) -> ground(at, out, alpha));
        collector.draw(pose, Ufo.GLOW, (at, out) -> {
            for (House house : built) house(at, out, house, alpha);
        });
        for (Palm palm : grown) palm(pose, collector, palm, alpha);
        pose.popPose();
        if (called) CalledBeam.submit(pose, camera, collector, x, y, z);
    }

    /** The grass round the pool and the pool, flat on the sand. */
    private static void ground(PoseStack.Pose at, VertexConsumer out, int alpha) {
        box(at, out, -5.5f, 0f, -4.5f, 5.5f, 0.06f, 4.5f, GRASS, alpha);
        box(at, out, -3.5f, 0.06f, -2.6f, 3.5f, 0.16f, 2.6f, POOL, Math.min(255, alpha * 4 / 3));
    }

    /** A house of sand: walls, a flat roof a little wider, a door and windows on its front and sides. */
    private static void house(PoseStack.Pose at, VertexConsumer out, House house, int alpha) {
        float x0 = house.x() - house.halfWidth(), x1 = house.x() + house.halfWidth();
        float z0 = house.z() - house.halfDepth(), z1 = house.z() + house.halfDepth();
        float h = house.height();
        box(at, out, x0, 0f, z0, x1, h, z1, WALL, alpha);
        box(at, out, x0 - 0.2f, h, z0 - 0.2f, x1 + 0.2f, h + 0.3f, z1 + 0.2f, ROOF, alpha);
        int dark = Math.min(255, alpha * 3 / 2);
        float cx = house.x(), cz = house.z();
        // The door on the front, a window beside it where the wall is wide enough.
        box(at, out, cx - 0.5f, 0f, z1, cx + 0.5f, 2f, z1 + 0.06f, OPENING, dark);
        if (house.halfWidth() >= 2.2f) {
            float wx = cx + house.halfWidth() * 0.6f;
            box(at, out, wx - 0.4f, 1.3f, z1, wx + 0.4f, 2.1f, z1 + 0.06f, OPENING, dark);
        }
        // A window on each side.
        box(at, out, x0 - 0.06f, 1.3f, cz - 0.4f, x0, 2.1f, cz + 0.4f, OPENING, dark);
        box(at, out, x1, 1.3f, cz - 0.4f, x1 + 0.06f, 2.1f, cz + 0.4f, OPENING, dark);
    }

    /** A palm tree: a trunk of boxes bending more as it rises, a crown of drooping leaves. */
    private static void palm(PoseStack pose, Sink collector, Palm palm, int alpha) {
        int n = palm.height();
        float topX = palm.x() + palm.bendX(), topZ = palm.z() + palm.bendZ();
        collector.draw(pose, Ufo.GLOW, (at, out) -> {
            for (int k = 0; k < n; k++) {
                float bend = (float) (k * k) / (n * n);
                float sx = palm.x() + palm.bendX() * bend, sz = palm.z() + palm.bendZ() * bend;
                float half = 0.28f - 0.02f * k / n;
                box(at, out, sx - half, k, sz - half, sx + half, k + 1.02f, sz + half, TRUNK, alpha);
            }
            // The nuts under the crown.
            box(at, out, topX - 0.35f, n - 0.4f, topZ - 0.35f, topX + 0.35f, n + 0.1f, topZ + 0.35f, TRUNK, alpha);
        });
        for (int leaf = 0; leaf < 6; leaf++) {
            pose.pushPose();
            pose.translate(topX, n + 0.1f, topZ);
            pose.mulPose(Axis.YP.rotationDegrees(palm.turn() + leaf * 60f));
            pose.mulPose(Axis.XP.rotationDegrees(18f));
            collector.draw(pose, Ufo.GLOW, (at, out) -> box(at, out, -0.35f, -0.08f, 0f, 0.35f, 0.08f, 2.4f, LEAF, alpha));
            // Its end hanging lower.
            pose.translate(0f, 0f, 2.3f);
            pose.mulPose(Axis.XP.rotationDegrees(32f));
            collector.draw(pose, Ufo.GLOW, (at, out) -> box(at, out, -0.28f, -0.07f, 0f, 0.28f, 0.07f, 1.5f, LEAF, alpha));
            pose.popPose();
        }
    }

    /** A box of the oasis, its colour blurred towards the sky's as hot air blurs it. */
    private static void box(PoseStack.Pose at, VertexConsumer out, float x0, float y0, float z0,
                            float x1, float y1, float z1, int colour, int alpha) {
        int r = Math.round(Mth.lerp(HAZED, colour >> 16 & 0xFF, HAZE >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(HAZED, colour >> 8 & 0xFF, HAZE >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(HAZED, colour & 0xFF, HAZE & 0xFF));
        Ufo.box(at, out, x0, y0, z0, x1, y1, z1, r, g, b, alpha, Ufo.BRIGHT);
    }
}
