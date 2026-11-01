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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Easter egg 19: now and then, at night, far off on a hilltop or on open dry ground, a small
 * campfire burns: an orange glow, flames that flicker, a thin column of smoke rising and
 * drifting. No one is ever seen by it. As the player comes near it is out, and for a minute
 * only a dark patch of ash, still smoking a little, shows where it burned.
 *
 * <p>Its place is read around the player, in the loaded chunks or the far world
 * ({@link Columns}): dry, flat enough, under the open sky, in sight ({@link Sight}), and the
 * highest of those found, so that it stands on a hill where there is one. Its smoke is boxes
 * of its own, never particles, which could not be brought inside the far plane. The fire is
 * drawn by {@link #fire}, which easter egg 9 ({@link Sect}) shares.
 *
 * <p>Secret: nothing a player reads (settings, README, changelog) says what it is.
 */
public final class Campfire implements Egg {
    /** See-through, lit as an entity: the smoke, and figures fading away. */
    static final RenderType FADING = RenderTypes.entityTranslucent(Ufo.SHEET);
    /** Logs, embers, the outer and inner flames, the glow; smoke; ash. */
    private static final int LOGS = 0x4A3423, EMBERS = 0xFF5A1E, FLAME = 0xFF7A1C, CORE = 0xFFD25A, GLOW = 0xFF8C32;
    private static final int SMOKE = 0x77777C, ASH = 0x2A2826;
    /** Puffs of smoke on its way up at once; blocks a puff rises per tick. */
    private static final int PUFFS = 14;
    private static final float RISE = 0.06f;
    /** Blocks its smoke rises before it is gone; how far the wind takes it for each block up. */
    private static final float SMOKE_HEIGHT = 14f, DRIFT = 0.35f;
    /** How far off it may burn, in blocks across; under which it is out. */
    private static final double MIN = 100, MAX = 350, NEAR = 48;
    /** Highest spots within this many blocks of the highest are all as good. */
    private static final double TOP = 0.5;
    /** Spots tried; of them, ahead of the player when called. */
    private static final int SPOTS = 160, AHEAD = 120;
    /** Half the angle ahead the spots are tried in when called, in radians. */
    private static final double SECTOR = Math.toRadians(30);
    /** Ticks it burns at most; the ash stays; it smokes as ash; a search may take. */
    private static final int LIFE = 9600, ASH_LIFE = 1200, ASH_SMOKE = 600, SEARCH = 400;

    private final EggCalendar calendar;
    private final RandomSource random = RandomSource.create();
    /** Counts the searches: a read that comes back for one given up or replaced is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private @Nullable ClientLevel level;
    private boolean searching, burning, ash, called;
    private double x, y, z;
    /** Where the wind takes the smoke, in radians; its light where it stands. */
    private float wind;
    private int light;
    private int age, ashAge, idle, searchAge;

    Campfire(EggCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public int number() {
        return 19;
    }

    @Override
    public boolean busy() {
        return searching || burning || ash;
    }

    @Override
    public @Nullable Vec3 position() {
        return burning || ash ? new Vec3(x, y, z) : null;
    }

    /** The fire is burning. For the game tests. */
    public boolean burning() {
        return burning;
    }

    /** Only its ash is left. For the game tests. */
    public boolean ash() {
        return ash;
    }

    @Override
    public boolean summon(ClientLevel current, LocalPlayer self, boolean called) {
        if (!FarConfig.get().easterEggs || current.dimension() != Level.OVERWORLD) return false;
        if (busy() && !called) return true;
        clear();
        // No farther than the terrain is drawn and clear of fog.
        double far = Math.min(MAX, Sight.reach());
        if (far <= MIN) {
            if (called) EggLog.nowhere(19);
            return false;
        }
        double look = Math.toRadians(self.getYRot());
        // Each spot, then two blocks off it on two sides: flat enough for a fire.
        double[] xs = new double[SPOTS * 3], zs = new double[xs.length];
        for (int s = 0; s < SPOTS; s++) {
            double bearing = called && s < AHEAD ? look + (random.nextDouble() * 2 - 1) * SECTOR
                    : random.nextDouble() * Math.PI * 2;
            double distance = MIN + random.nextDouble() * (far - MIN);
            double cx = Mth.floor(self.getX() - Math.sin(bearing) * distance) + 0.5;
            double cz = Mth.floor(self.getZ() + Math.cos(bearing) * distance) + 0.5;
            xs[3 * s] = cx;
            zs[3 * s] = cz;
            xs[3 * s + 1] = cx + 2;
            zs[3 * s + 1] = cz;
            xs[3 * s + 2] = cx;
            zs[3 * s + 2] = cz + 2;
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
            List<Columns.Column> open = new ArrayList<>();
            for (int s = 0; s < SPOTS; s++) {
                Columns.Column spot = columns[3 * s];
                if (!ground(current, spot)) continue;
                boolean flat = true;
                for (int k = 1; k < 3 && flat; k++) {
                    Columns.Column side = columns[3 * s + k];
                    flat = side.dry() && Math.abs(side.y() - spot.y()) <= 1;
                }
                if (flat) open.add(spot);
            }
            double[] hx = new double[open.size()], hy = new double[open.size()], hz = new double[open.size()];
            for (int i = 0; i < open.size(); i++) {
                hx[i] = open.get(i).x();
                hy[i] = open.get(i).y() + 1.2;
                hz[i] = open.get(i).z();
            }
            Sight.of(current, self, eye, hx, hy, hz).thenAcceptAsync(seen -> {
                if (generation.get() != mine) return;
                searching = false;
                // The highest in sight: a hilltop where there is one.
                double highest = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < seen.length; i++) if (seen[i]) highest = Math.max(highest, open.get(i).y());
                List<Columns.Column> tops = new ArrayList<>();
                for (int i = 0; i < seen.length; i++) {
                    if (seen[i] && open.get(i).y() >= highest - TOP) tops.add(open.get(i));
                }
                Columns.Column chosen = Sight.any(tops, random);
                if (chosen == null) {
                    if (called) EggLog.nowhere(19);
                    return;
                }
                begin(current, chosen);
                EggLog.came(19, x, y, z, self, called);
            }, game);
        }, game);
        return true;
    }

    /**
     * Open dry ground: out of the water, under no leaves, not on them nor on ice; under the
     * open sky where it is loaded (the loaded column gives the ground under the trees).
     */
    private static boolean ground(ClientLevel level, Columns.Column column) {
        if (!column.dry()) return false;
        BlockState block = column.block();
        if (block != null && (block.is(BlockTags.LEAVES) || block.is(BlockTags.ICE))) return false;
        return !Columns.loaded(level, column.x(), column.z())
                || level.canSeeSky(BlockPos.containing(column.x(), column.y(), column.z()));
    }

    private void begin(ClientLevel current, Columns.Column spot) {
        if (!called) calendar.shown(19);
        x = spot.x();
        y = spot.y();
        z = spot.z();
        wind = random.nextFloat() * Mth.TWO_PI;
        light = light(current, x, y, z, 14);
        age = ashAge = 0;
        ash = false;
        burning = true;
    }

    @Override
    public void tick(ClientLevel current, LocalPlayer self, boolean enabled, boolean quiet) {
        if (busy() && (!enabled || current != level)) clear();
        if (!enabled) return;
        if (searching) {
            if (++searchAge > SEARCH) clear();
            return;
        }
        if (!burning && !ash) {
            // Rare, and only at night: about once in forty minutes of it.
            if (++idle > 6000 && quiet && !current.isBrightOutside() && random.nextInt(48000) == 0) {
                idle = 0;
                summon(current, self, false);
            }
            return;
        }
        if (!Columns.drawn(current, self, x, z)) {
            clear();
            return;
        }
        if (burning) {
            age++;
            light = light(current, x, y, z, 14);
            // Out as the player comes, at the end of its time, and at dawn if it came by chance.
            boolean dawn = !called && current.isBrightOutside();
            if (Math.hypot(self.getX() - x, self.getZ() - z) < NEAR || age > LIFE || dawn) {
                burning = false;
                ash = true;
                ashAge = 0;
            }
        } else if (++ashAge > ASH_LIFE) {
            clear();
        } else {
            light = light(current, x, y, z, 0);
        }
    }

    private void clear() {
        generation.incrementAndGet();
        searching = burning = ash = false;
        level = null;
    }

    @Override
    public void submit(PoseStack pose, Vec3 camera, Sink collector, float partial) {
        if (!burning && !ash) return;
        double px = x - camera.x, py = y - camera.y, pz = z - camera.z;
        double distance = Math.sqrt(px * px + py * py + pz * pz);
        GhostRenderer.noteDistance(distance + SMOKE_HEIGHT + 5);
        double reach = DepthFar.of(Minecraft.getInstance()) * 0.95;
        float pull = distance > reach ? (float) (reach / distance) : 1f;
        pose.pushPose();
        pose.translate((float) (px * pull), (float) (py * pull), (float) (pz * pull));
        pose.scale(pull, pull, pull);
        if (burning) {
            fire(pose, collector, age + partial, wind, SMOKE_HEIGHT, 1f, light);
        } else {
            float left = 1f - Mth.clamp((ashAge + partial) / ASH_SMOKE, 0f, 1f);
            collector.draw(pose, Ufo.SOLID, (at, out) -> box(at, out, -0.7f, 0f, -0.7f, 0.7f, 0.04f, 0.7f, ASH, 255, light));
            if (left > 0f) {
                float time = ashAge + partial;
                // A few embers dying out, and a thin wisp of smoke.
                collector.draw(pose, Ufo.GLOW, (at, out) -> {
                    int glow = Math.round(200 * left);
                    box(at, out, -0.2f, 0.04f, -0.1f, -0.05f, 0.09f, 0.05f, EMBERS, glow, Ufo.BRIGHT);
                    box(at, out, 0.1f, 0.04f, 0.05f, 0.24f, 0.08f, 0.18f, EMBERS, glow, Ufo.BRIGHT);
                });
                smoke(pose, collector, time, wind, SMOKE_HEIGHT * 0.4f, left * 0.6f, light);
            }
        }
        pose.popPose();
        if (called) CalledBeam.submit(pose, camera, collector, x, y, z);
    }

    /**
     * A campfire at the pose's origin, on the ground: crossed logs, embers, flames flickering
     * with {@code time} (ticks), a soft glow over it and on the ground round it, and smoke
     * rising {@code smokeHeight} blocks, drifting towards {@code wind} (radians), as thick as
     * {@code smokeStrength} (0 to 1) says. {@code light}: the light it stands in, for the logs
     * and the smoke. Shared with easter egg 9's fire.
     */
    static void fire(PoseStack pose, Sink collector, float time, float wind, float smokeHeight, float smokeStrength,
                     int light) {
        int lit = brighter(light, 13);
        collector.draw(pose, Ufo.SOLID, (at, out) -> {
            box(at, out, -0.6f, 0f, -0.1f, 0.6f, 0.18f, 0.1f, LOGS, 255, lit);
            box(at, out, -0.1f, 0.06f, -0.6f, 0.1f, 0.24f, 0.6f, LOGS, 255, lit);
        });
        collector.draw(pose, Ufo.GLOW, (at, out) -> flames(at, out, time));
        if (smokeStrength > 0f) smoke(pose, collector, time, wind, smokeHeight, smokeStrength, light);
    }

    /** Embers, three flames each of its own height and flicker, the glow round them and on the ground. */
    private static void flames(PoseStack.Pose at, VertexConsumer out, float time) {
        box(at, out, -0.35f, 0.02f, -0.35f, 0.35f, 0.12f, 0.35f, EMBERS, 230, Ufo.BRIGHT);
        float[][] tongues = {{0f, 0f, 0.9f, 0.28f}, {0.14f, -0.08f, 0.6f, 0.18f}, {-0.12f, 0.1f, 0.65f, 0.18f}};
        for (int k = 0; k < tongues.length; k++) {
            float[] t = tongues[k];
            float flicker = flicker(time, k);
            float h = t[2] * (0.7f + 0.3f * flicker), w = t[3] * (0.85f + 0.15f * flicker);
            box(at, out, t[0] - w, 0.1f, t[1] - w, t[0] + w, 0.1f + h, t[1] + w, FLAME, 205, Ufo.BRIGHT);
            float c = w * 0.55f;
            box(at, out, t[0] - c, 0.1f, t[1] - c, t[0] + c, 0.1f + h * 0.6f, t[1] + c, CORE, 230, Ufo.BRIGHT);
        }
        float pulse = 0.8f + 0.2f * flicker(time * 0.5f, 7);
        int glow = Math.round(34 * pulse);
        box(at, out, -0.9f, 0f, -0.9f, 0.9f, 1.6f, 0.9f, GLOW, glow, Ufo.BRIGHT);
        box(at, out, -2.6f, 0.01f, -2.6f, 2.6f, 0.04f, 2.6f, GLOW, Math.round(26 * pulse), Ufo.BRIGHT);
    }

    /** 0 to 1, flickering as a flame does: a few waves of their own for each tongue {@code k}. */
    private static float flicker(float time, int k) {
        double v = Math.sin(time * 0.9 + k * 1.7) * 0.5 + Math.sin(time * 2.3 + k * 3.1) * 0.3
                + Math.sin(time * 5.1 + k) * 0.2;
        return (float) (v * 0.5 + 0.5);
    }

    /**
     * Puffs of smoke rising from the pose's origin, wider and fainter as they go up, the wind
     * taking them more and more aside, until they are gone at {@code height} blocks.
     */
    private static void smoke(PoseStack pose, Sink collector, float time, float wind, float height, float strength,
                              int light) {
        float wx = -Mth.sin(wind), wz = Mth.cos(wind);
        collector.draw(pose, FADING, (at, out) -> {
            for (int i = 0; i < PUFFS; i++) {
                float u = (time * RISE / height + (float) i / PUFFS) % 1f;
                float up = 0.6f + u * height, size = 0.15f + u * 0.9f;
                float aside = (float) Math.pow(u, 1.5) * height * DRIFT;
                float wobble = Mth.sin(time * 0.05f + i * 2.1f) * 0.3f * u;
                float cx = wx * aside + wz * wobble, cz = wz * aside - wx * wobble;
                int alpha = Math.round(110 * strength * (1f - u) * Math.min(1f, u * 8f));
                if (alpha <= 0) continue;
                // Lit by the fire down low, by the night up high.
                int lit = brighter(light, Math.round(12 * (1f - u)));
                box(at, out, cx - size, up - size, cz - size, cx + size, up + size, cz + size, SMOKE, alpha, lit);
            }
        });
    }

    /**
     * The light a figure stands in by a fire: the brightest block and sky light of its feet
     * and the two blocks above, the block light at least {@code fire}. Open sky where the
     * client has no light.
     */
    static int light(ClientLevel level, double x, double y, double z, int fire) {
        BlockPos feet = BlockPos.containing(x, y + 0.1, z);
        if (!level.hasChunkAt(feet)) return LightTexture.pack(fire, 15);
        int block = fire, sky = 0;
        for (int up = 0; up < 3; up++) {
            BlockPos at = feet.above(up);
            block = Math.max(block, level.getBrightness(LightLayer.BLOCK, at));
            sky = Math.max(sky, level.getBrightness(LightLayer.SKY, at));
        }
        return LightTexture.pack(block, sky);
    }

    /** {@code light} with its block light raised to {@code block} at least. */
    private static int brighter(int light, int block) {
        // Packed as the game packs it: block light in bits 4 to 7, sky light in bits 20 to 23.
        return LightTexture.pack(Math.max(light >> 4 & 0xF, block), light >> 20 & 0xF);
    }

    private static void box(PoseStack.Pose at, VertexConsumer out, float x0, float y0, float z0,
                            float x1, float y1, float z1, int colour, int alpha, int light) {
        Ufo.box(at, out, x0, y0, z0, x1, y1, z1, colour >> 16 & 0xFF, colour >> 8 & 0xFF, colour & 0xFF, alpha, light);
    }
}
