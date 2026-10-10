package fr.clixmods.livinghorizon.ambient;

import fr.clixmods.livinghorizon.track.Puppets;
import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.compat.LodWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import fr.clixmods.livinghorizon.track.EntityNbt;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * Life in the sky and around, purely a picture, on this client only; nothing is spawned
 * anywhere and no AI runs. Flocks cross the sky, birds of prey circle, gulls hang about
 * the coast, pigeons sit on high buildings, robins hop in the fields, tits flit in the
 * trees, bats come out at night. Perched birds fly off when you come close.
 *
 * <p>Where each kind goes is read off the terrain: a few columns at a time, from the
 * chunks loaded here or, farther, from Voxy's world - the top block and the biome say
 * field, tree, coast or high building. Birds are pixel sprites drawn by
 * {@code AmbientRenderer}; parrots and bats are the game's own, drawn like distant mobs.
 */
public final class Ambience {
    public enum Kind { SILHOUETTE, PARROT, BAT }

    /**
     * Which sprite or model a silhouette uses, flying and perched; and how it is tinted.
     * Geese and birds of prey share the plain sprite with the flocks, but have models of their own.
     */
    public enum Species { GENERIC, GULL, PIGEON, ROBIN, TIT, DUCK, GOOSE, RAPTOR }

    /** The kinds of birds that can be switched off one by one; the id is what {@code hiddenBirds} holds. */
    public enum Type {
        FLOCKS("flocks"), GEESE("geese"), RAPTORS("raptors"), GULLS("gulls"), PIGEONS("pigeons"),
        ROBINS("robins"), TITS("tits"), DUCKS("ducks"), BATS("bats"), PARROTS("parrots");

        public final String id;

        Type(String id) {
            this.id = id;
        }
    }

    /** One thing flying, or perched. Positions in blocks; angles in degrees; last tick kept to interpolate. */
    public static final class Flyer {
        public final Kind kind;
        public final Species species;
        /** Wingspan in blocks, size setting included. */
        public final float span;
        /** Tint of a silhouette, 0-255: grey levels for the plain sprite, 255 for coloured ones. */
        public final int shade;
        public final @Nullable LivingEntity puppet;
        public double x, y, z, xo, yo, zo;
        public float yaw, yawO, bank, bankO, wing, wingO;
        /** Sitting: drawn side on, wings folded. */
        public boolean perched;
        /** 1 normally; shrinks to 0 as the bird leaves for good. */
        public float scale = 1f;
        float beat, beatRate;
        boolean gliding;

        Flyer(Kind kind, Species species, float span, int shade, @Nullable LivingEntity puppet, float beatRate) {
            this.kind = kind;
            this.species = species;
            this.span = span;
            this.shade = shade;
            this.puppet = puppet;
            this.beatRate = beatRate;
        }

        void moveTo(double nx, double ny, double nz, float nyaw, float nbank) {
            xo = x;
            yo = y;
            zo = z;
            yawO = yaw;
            bankO = bank;
            wingO = wing;
            x = nx;
            y = ny;
            z = nz;
            yaw = nyaw;
            bank = nbank;
            beat += beatRate;
            // Wings: a beat when flapping, a slight lift when gliding.
            wing = gliding ? 8f : 5f + 38f * Mth.sin(beat);
            if (puppet != null) placePuppet();
        }

        private void placePuppet() {
            LivingEntity entity = puppet;
            entity.setOldPosAndRot();
            entity.yBodyRotO = entity.yBodyRot;
            entity.yHeadRotO = entity.yHeadRot;
            entity.setPos(x, y, z);
            entity.setYRot(yaw);
            entity.yBodyRot = yaw;
            entity.yHeadRot = yaw;
            if (entity instanceof Parrot parrot) {
                // The parrot renderer turns these two into the wing beat.
                parrot.oFlap = parrot.flap;
                parrot.oFlapSpeed = parrot.flapSpeed;
                parrot.flap += 0.9f;
                parrot.flapSpeed = 1f;
            }
            // Before 1.20.3 the bat's model read whether it rests by itself.
            //? if >=1.20.3 {
            if (entity instanceof Bat bat) {
                // Hanging upside down, or flying: the renderer plays the one that is running.
                if (bat.isResting()) {
                    bat.flyAnimationState.stop();
                    bat.restAnimationState.startIfStopped(bat.tickCount);
                } else {
                    bat.restAnimationState.stop();
                    bat.flyAnimationState.startIfStopped(bat.tickCount);
                }
            }
            //?}
            entity.tickCount++;
        }

        void place(double nx, double ny, double nz) {
            x = xo = nx;
            y = yo = ny;
            z = zo = nz;
        }
    }

    /** Some flyers that move together, and know when they are done. */
    private abstract static class Group {
        final Type type;
        final List<Flyer> flyers = new ArrayList<>();
        int age;

        Group(Type type) {
            this.type = type;
        }

        /** One tick. False once the group has done its part and should leave. */
        abstract boolean tick(Ambience ambience, LocalPlayer self, double reach);

        /** Ticks since it started leaving; -1 while it has not. */
        int departing = -1;
        private double[] dx = new double[0], dy = new double[0], dz = new double[0];
        /** When set, they leave away from this point - someone came too close - not from each other. */
        private boolean flee;
        private double fleeX, fleeZ;

        void fleeFrom(double x, double z) {
            flee = true;
            fleeX = x;
            fleeZ = z;
        }

        /** Scatter: every bird takes off away from the others, in its own direction. */
        void leave(RandomSource random) {
            departing = 0;
            double cx = 0, cz = 0;
            for (Flyer flyer : flyers) {
                cx += flyer.x / flyers.size();
                cz += flyer.z / flyers.size();
            }
            if (flee) {
                cx = fleeX;
                cz = fleeZ;
            }
            dx = new double[flyers.size()];
            dy = new double[flyers.size()];
            dz = new double[flyers.size()];
            for (int i = 0; i < flyers.size(); i++) {
                Flyer flyer = flyers.get(i);
                double angle = Math.atan2(flyer.x - cx, flyer.z - cz) + random.nextGaussian() * (flee ? 0.35 : 0.8);
                if (Math.hypot(flyer.x - cx, flyer.z - cz) < 0.5) angle = random.nextDouble() * Math.PI * 2;
                double speed = 0.18 + random.nextDouble() * 0.12;
                dx[i] = Math.sin(angle) * speed;
                dz[i] = Math.cos(angle) * speed;
                dy[i] = 0.06 + random.nextDouble() * 0.08;
                flyer.perched = false;
                flyer.gliding = false;
                if (flyer.puppet instanceof Bat bat) bat.setResting(false);
            }
        }

        /**
         * One tick of leaving: flying off, a little faster each tick, shrinking after a
         * while until nothing is left. False once gone.
         */
        boolean depart() {
            departing++;
            for (int i = 0; i < flyers.size(); i++) {
                Flyer flyer = flyers.get(i);
                dx[i] *= 1.02;
                dz[i] *= 1.02;
                float yaw = (float) Math.toDegrees(Math.atan2(-dx[i], dz[i]));
                flyer.moveTo(flyer.x + dx[i], flyer.y + dy[i], flyer.z + dz[i], yaw, 0f);
                flyer.scale = departing < 40 ? 1f : Math.max(0f, 1f - (departing - 40) / 80f);
            }
            return departing < 120;
        }
    }

    /** What a column of terrain is good for. */
    private enum Place { COAST, FIELD, TREE, PERCH, WATER }

    private record Spot(Place place, double x, double y, double z) {
    }

    /** Per type, before the density setting: {groups at once, ticks between two}. */
    private static final Map<Type, int[]> PACE = new EnumMap<>(Type.class);

    static {
        PACE.put(Type.FLOCKS, new int[]{2, 500});
        PACE.put(Type.GEESE, new int[]{1, 1400});
        PACE.put(Type.RAPTORS, new int[]{2, 900});
        PACE.put(Type.GULLS, new int[]{2, 500});
        PACE.put(Type.PIGEONS, new int[]{2, 300});
        PACE.put(Type.ROBINS, new int[]{3, 260});
        PACE.put(Type.TITS, new int[]{3, 260});
        PACE.put(Type.BATS, new int[]{3, 300});
        PACE.put(Type.DUCKS, new int[]{3, 300});
        PACE.put(Type.PARROTS, new int[]{1, 2400});
    }

    private final List<Group> groups = new ArrayList<>();
    private final List<Flyer> flyers = new ArrayList<>();
    private final RandomSource random = RandomSource.create();
    private final Map<Type, Integer> cooldowns = new EnumMap<>(Type.class);
    private final List<Spot> spots = new ArrayList<>();
    /** Spots the Voxy lookups found, handed over from their thread. */
    private final ConcurrentLinkedQueue<Spot> found = new ConcurrentLinkedQueue<>();
    private @Nullable ClientLevel level;
    private int probeCooldown;
    private final Ufo ufo = new Ufo();

    /** The easter egg. */
    public Ufo ufo() {
        return ufo;
    }

    /** Everything flying or perched this tick. */
    public List<Flyer> flyers() {
        return flyers;
    }

    public void tick(ClientLevel current, LocalPlayer self, FarConfig config) {
        if (current != level) {
            groups.clear();
            spots.clear();
            found.clear();
            level = current;
        }
        ufo.tick(current, self, config.ufo && current.dimension() == Level.OVERWORLD);
        flyers.clear();
        if (!config.skyBirds || current.dimension() != Level.OVERWORLD) {
            groups.clear();
            return;
        }
        double reach = Math.max(100, config.birdMaxDistance);
        double density = Math.max(0, config.birdDensity) / 100.0;
        boolean day = current.isBrightOutside();
        boolean rain = current.isRaining();

        findSpots(current, self, reach);

        Map<Type, Integer> counts = new EnumMap<>(Type.class);
        for (Group group : groups) if (group.departing < 0) counts.merge(group.type, 1, Integer::sum);
        for (Type type : Type.values()) {
            int[] pace = PACE.get(type);
            int left = cooldowns.getOrDefault(type, random.nextInt(pace[1])) - 1;
            if (left > 0) {
                cooldowns.put(type, left);
                continue;
            }
            int every = density <= 0 ? pace[1] : (int) Math.max(40, pace[1] / density);
            cooldowns.put(type, every / 2 + random.nextInt(every));
            boolean wanted = type == Type.BATS ? !day : day && !rain;
            if (!wanted || config.hiddenBirds.contains(type.id)) continue;
            if (counts.getOrDefault(type, 0) >= Math.round(pace[0] * density)) continue;
            Group group = create(type, current, self, reach, config);
            if (group != null && !group.flyers.isEmpty()) groups.add(group);
        }

        groups.removeIf(group -> {
            group.age++;
            if (group.departing >= 0) return !group.depart();
            boolean keep = group.tick(this, self, reach) && !config.hiddenBirds.contains(group.type.id);
            // Bats go home at dawn; birds take shelter when it rains.
            if (group.type == Type.BATS && day) keep = false;
            if (group.type != Type.BATS && rain && group.age > 200) keep = false;
            // Never gone at once: they scatter first, and fade as they go.
            if (!keep) group.leave(random);
            return false;
        });
        for (Group group : groups) flyers.addAll(group.flyers);
    }

    private @Nullable Group create(Type type, ClientLevel level, LocalPlayer self, double reach, FarConfig config) {
        return switch (type) {
            case FLOCKS -> new Flock(this, level, self, reach, config, false, false);
            case GEESE -> new Flock(this, level, self, reach, config, true, false);
            case PARROTS -> new Flock(this, level, self, reach, config, false, true);
            case RAPTORS -> new Circle(this, level, self, reach, config);
            case BATS -> new Bats(this, level, self, reach);
            case GULLS -> found(spot(Place.COAST, self, reach), s -> new Gulls(this, level, s, config));
            case PIGEONS -> found(spot(Place.PERCH, self, 160), s -> new Pigeons(this, level, s, config));
            case ROBINS -> found(spot(Place.FIELD, self, 160),
                    s -> new Perchers(this, level, s, config, Type.ROBINS, Species.ROBIN, 1, 3, 0.8f));
            case TITS -> found(spot(Place.TREE, self, 160),
                    s -> new Perchers(this, level, s, config, Type.TITS, Species.TIT, 2, 4, 0.6f));
            case DUCKS -> found(spot(Place.WATER, self, 200),
                    s -> new Perchers(this, level, s, config, Type.DUCKS, Species.DUCK, 2, 6, 0.9f));
        };
    }

    // --- Where birds go -------------------------------------------------------------------

    /** What is made at a spot, or nothing where none was found. */
    private static <T> @Nullable T found(@Nullable Spot spot, Function<Spot, T> make) {
        return spot == null ? null : make.apply(spot);
    }

    private @Nullable Spot spot(Place place, LocalPlayer self, double within) {
        List<Spot> candidates = new ArrayList<>();
        for (Spot spot : spots) {
            if (spot.place == place && Math.hypot(spot.x - self.getX(), spot.z - self.getZ()) < within) candidates.add(spot);
        }
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * Reads a few columns of terrain: near ones from the chunks loaded here, far ones from
     * Voxy's world, off the thread.
     */
    private void findSpots(ClientLevel level, LocalPlayer self, double reach) {
        Spot spot;
        while ((spot = found.poll()) != null) keep(spot);
        spots.removeIf(s -> Math.hypot(s.x - self.getX(), s.z - self.getZ()) > reach * 1.2);
        if (--probeCooldown > 0) return;
        probeCooldown = 30;
        double loaded = Math.max(16, Math.min(96, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 - 8));
        for (int i = 0; i < 5; i++) {
            boolean near = i < 3;
            double bearing = random.nextDouble() * Math.PI * 2;
            double distance = near ? 6 + random.nextDouble() * (loaded - 6)
                    : loaded + random.nextDouble() * Math.max(1, reach * 0.8 - loaded);
            int x = Mth.floor(self.getX() - Math.sin(bearing) * distance);
            int z = Mth.floor(self.getZ() + Math.cos(bearing) * distance);
            if (level.hasChunk(x >> 4, z >> 4)) {
                Spot read = read(level, x, z);
                if (read != null) keep(read);
            } else {
                LodWorld.surface(x, z).thenAccept(surface -> surface.ifPresent(s -> {
                    Place place = classify(s.block(), s.biome(), false);
                    if (place != null) found.add(new Spot(place, x + 0.5, s.y() + 1, z + 0.5));
                }));
            }
        }
    }

    private void keep(Spot spot) {
        int same = 0;
        for (Spot s : spots) if (s.place == spot.place) same++;
        if (same >= 16) {
            for (int i = 0; i < spots.size(); i++) {
                if (spots.get(i).place == spot.place) {
                    spots.remove(i);
                    break;
                }
            }
        }
        spots.add(spot);
    }

    /** One loaded column: its top block, and whether it stands well above its neighbours. */
    private static @Nullable Spot read(ClientLevel level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockState block = level.getBlockState(new BlockPos(x, top - 1, z));
        String biome = level.getBiome(new BlockPos(x, top, z)).unwrapKey()
                .map(key -> key.identifier().toString()).orElse("");
        boolean high = false;
        if (!natural(block)) {
            int around = Integer.MAX_VALUE;
            for (int[] d : new int[][]{{6, 0}, {-6, 0}, {0, 6}, {0, -6}}) {
                around = Math.min(around, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + d[0], z + d[1]));
            }
            high = top - around >= 5;
        }
        Place place = classify(block, biome, high);
        return place == null ? null : new Spot(place, x + 0.5, top, z + 0.5);
    }

    private static @Nullable Place classify(@Nullable BlockState block, String biome, boolean high) {
        if (block != null) {
            if (block.is(BlockTags.CROPS) || block.is(Blocks.FARMLAND)) return Place.FIELD;
            if (block.is(BlockTags.LEAVES)) return Place.TREE;
            // Lakes, rivers, ponds: not the sea, which is for gulls.
            if (block.getFluidState().is(FluidTags.WATER) && !coastal(biome)) return Place.WATER;
            if (high) return Place.PERCH;
        }
        if (coastal(biome)) return Place.COAST;
        return null;
    }

    private static boolean coastal(String biome) {
        return biome.contains("beach") || biome.contains("ocean") || biome.contains("shore");
    }

    /** Blocks the world grows by itself: a tall one is a tree or a hill, not a building. */
    private static boolean natural(BlockState block) {
        return block.is(BlockTags.LEAVES) || block.is(BlockTags.LOGS) || block.is(BlockTags.DIRT)
                || block.is(BlockTags.SAND) || block.is(BlockTags.BASE_STONE_OVERWORLD) || block.is(BlockTags.SNOW)
                || block.is(BlockTags.ICE) || block.is(BlockTags.REPLACEABLE) || block.is(Blocks.GRAVEL)
                || block.is(Blocks.WATER) || block.is(Blocks.GRASS_BLOCK) || block.is(BlockTags.FLOWERS);
    }

    /** Where to stand at a column: the loaded ground when there is one, otherwise a guess. */
    private static double ground(ClientLevel level, double x, double z, Heightmap.Types surface, double guess) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        return level.hasChunk(bx >> 4, bz >> 4) ? level.getHeight(surface, bx, bz) : guess;
    }

    private static float silhouetteSize(FarConfig config, double base) {
        return (float) (base * Math.max(1, config.birdSize));
    }

    /** The lowest height sky birds fly at: above the player or the sea, plus the setting. */
    private static double skyBase(ClientLevel level, LocalPlayer self, FarConfig config) {
        return Math.max(self.getY(), level.getSeaLevel()) + Math.max(0, config.birdMinHeight);
    }

    // --- Flocks crossing ------------------------------------------------------------------

    /** A flock passing by in a straight line, in a V or in a loose cloud, and gone. */
    private static final class Flock extends Group {
        final double vx, vz;
        final float yaw;
        final int life;
        double x, y, z;
        final double[][] offsets;

        Flock(Ambience a, ClientLevel level, LocalPlayer self, double reach, FarConfig config, boolean geese,
              boolean parrots) {
            super(parrots ? Type.PARROTS : geese ? Type.GEESE : Type.FLOCKS);
            RandomSource r = a.random;
            double heading = r.nextDouble() * Math.PI * 2;
            double dx = -Math.sin(heading), dz = Math.cos(heading);
            double near = 40 + r.nextDouble() * (reach * 0.8 - 40);
            double side = r.nextBoolean() ? near : -near;
            double nearX = self.getX() + dz * side, nearZ = self.getZ() - dx * side;
            double speed = (geese ? 11 + r.nextDouble() * 4 : 8 + r.nextDouble() * 5) / 20.0;
            double half = reach * 1.15;
            x = nearX - dx * half;
            z = nearZ - dz * half;
            y = skyBase(level, self, config) + r.nextDouble() * (geese ? 110 : 70);
            vx = dx * speed;
            vz = dz * speed;
            yaw = (float) Math.toDegrees(heading);
            life = (int) (2 * half / speed);

            int count = geese ? 5 + r.nextInt(9) : 3 + r.nextInt(12);
            float baseSpan = silhouetteSize(config, geese ? 1.6 + r.nextDouble() * 0.5 : 0.5 + r.nextDouble() * 0.6);
            int variant = r.nextInt(4) == 0 ? r.nextInt(5) : 4;
            offsets = new double[count][];
            for (int i = 0; i < count; i++) {
                double back, sideways, rise;
                if (geese) {
                    int rank = (i + 1) / 2;
                    back = rank * baseSpan * 1.3;
                    sideways = (i % 2 == 0 ? 1 : -1) * rank * baseSpan * 1.1;
                    rise = r.nextDouble() * 0.6;
                } else {
                    back = r.nextGaussian() * baseSpan * 4;
                    sideways = r.nextGaussian() * baseSpan * 4;
                    rise = r.nextGaussian() * baseSpan * 2;
                }
                offsets[i] = new double[]{back, i == 0 && geese ? 0 : sideways, rise, r.nextDouble() * Math.PI * 2};
                Flyer flyer;
                if (parrots) {
                    Parrot parrot = parrot(level, variant);
                    if (parrot == null) break;
                    flyer = new Flyer(Kind.PARROT, Species.GENERIC, (float) Math.max(1, config.birdSize), 0, parrot, 0.9f);
                } else {
                    flyer = new Flyer(Kind.SILHOUETTE, geese ? Species.GOOSE : Species.GENERIC,
                            baseSpan * (float) (0.9 + r.nextDouble() * 0.2),
                            25 + r.nextInt(30), null, geese ? 0.32f : 0.7f + r.nextFloat() * 0.3f);
                    flyer.beat = r.nextFloat() * 6.28f;
                }
                flyers.add(flyer);
            }
            for (Flyer flyer : flyers) flyer.place(x, y, z);
        }

        @Override
        boolean tick(Ambience a, LocalPlayer self, double reach) {
            x += vx;
            z += vz;
            double s = Math.sin(Math.toRadians(yaw)), c = Math.cos(Math.toRadians(yaw));
            for (int i = 0; i < flyers.size(); i++) {
                double[] o = offsets[i];
                double px = x + s * o[0] + c * o[1];
                double pz = z - c * o[0] + s * o[1];
                double py = y + o[2] + 0.4 * Math.sin(age * 0.07 + o[3]);
                Flyer flyer = flyers.get(i);
                // Small birds glide between bursts of beats.
                if (flyer.beatRate > 0.5f) flyer.gliding = ((age + (int) (o[3] * 40)) / 30) % 3 == 2;
                flyer.moveTo(px, py, pz, yaw, 0f);
            }
            return age < life;
        }
    }

    // --- Birds of prey circling a spot -------------------------------------------------------

    /**
     * One to four large birds riding a thermal: wide circles, wings still, a beat now and
     * then, the circle drifting with the wind. After a few minutes they glide away.
     */
    private static final class Circle extends Group {
        double cx, cy, cz;
        final double windX, windZ;
        final int stay;
        final double[] radius, speed, angle, height, phase;
        final int[] turn;

        Circle(Ambience a, ClientLevel level, LocalPlayer self, double reach, FarConfig config) {
            super(Type.RAPTORS);
            RandomSource r = a.random;
            double bearing = r.nextDouble() * Math.PI * 2;
            double distance = 60 + r.nextDouble() * (reach * 0.85 - 60);
            cx = self.getX() - Math.sin(bearing) * distance;
            cz = self.getZ() + Math.cos(bearing) * distance;
            cy = skyBase(level, self, config) + 5 + r.nextDouble() * 80;
            double wind = r.nextDouble() * Math.PI * 2;
            windX = -Math.sin(wind) * 0.015;
            windZ = Math.cos(wind) * 0.015;
            stay = 2400 + r.nextInt(3600);
            int count = 1 + r.nextInt(4);
            radius = new double[count];
            speed = new double[count];
            angle = new double[count];
            height = new double[count];
            phase = new double[count];
            turn = new int[count];
            int direction = r.nextBoolean() ? 1 : -1;
            for (int i = 0; i < count; i++) {
                radius[i] = 12 + r.nextDouble() * 28;
                speed[i] = (6 + r.nextDouble() * 3) / 20.0;
                angle[i] = r.nextDouble() * Math.PI * 2;
                height[i] = r.nextGaussian() * 6;
                phase[i] = r.nextDouble() * 100;
                turn[i] = r.nextInt(5) == 0 ? -direction : direction;
                Flyer flyer = new Flyer(Kind.SILHOUETTE, Species.RAPTOR,
                        silhouetteSize(config, 1.8 + r.nextDouble() * 0.8), 30 + r.nextInt(25), null, 0.28f);
                flyer.place(cx, cy, cz);
                flyers.add(flyer);
            }
        }

        @Override
        boolean tick(Ambience a, LocalPlayer self, double reach) {
            cx += windX;
            cz += windZ;
            boolean leaving = age > stay;
            boolean anyNear = false;
            for (int i = 0; i < flyers.size(); i++) {
                Flyer flyer = flyers.get(i);
                double x, y, z;
                float yaw, bank;
                if (!leaving) {
                    angle[i] += turn[i] * speed[i] / radius[i];
                    x = cx + Math.cos(angle[i]) * radius[i];
                    z = cz + Math.sin(angle[i]) * radius[i];
                    y = cy + height[i] + 4 * Math.sin((age + phase[i]) * 0.01);
                    // Tangent to the circle, leaning into the turn.
                    double tx = -Math.sin(angle[i]) * turn[i], tz = Math.cos(angle[i]) * turn[i];
                    yaw = (float) Math.toDegrees(Math.atan2(-tx, tz));
                    bank = turn[i] * 24f;
                } else {
                    double h = Math.toRadians(flyer.yaw);
                    x = flyer.x - Math.sin(h) * speed[i];
                    z = flyer.z + Math.cos(h) * speed[i];
                    y = flyer.y + 0.02;
                    yaw = flyer.yaw;
                    bank = 0f;
                }
                flyer.gliding = ((age + (int) phase[i] * 7) % 220) > 35;
                flyer.moveTo(x, y, z, yaw, bank);
                if (Math.hypot(x - self.getX(), z - self.getZ()) < reach * 1.3) anyNear = true;
            }
            return !leaving || anyNear;
        }
    }

    // --- Gulls over the coast -----------------------------------------------------------------

    /**
     * Gulls hanging about a stretch of coast: low over the water, wandering in long loose
     * curves, mostly gliding, drawn back whenever they stray. Now and then one glides down
     * onto the beach, stands there a while, and takes off again - at once if someone comes
     * close.
     */
    private static final class Gulls extends Group {
        private static final int FLYING = 0, LANDING = 1, LANDED = 2, RISING = 3;

        final ClientLevel level;
        final double sx, sy, sz;
        final int stay;
        final double[] heading, speed, altitude, phase;
        /** Per gull: what it is doing, for how long, and from where to where. */
        final int[] mode, timer, length;
        final double[] fromX, fromY, fromZ, toX, toY, toZ;

        Gulls(Ambience a, ClientLevel level, Spot spot, FarConfig config) {
            super(Type.GULLS);
            RandomSource r = a.random;
            this.level = level;
            sx = spot.x;
            sy = spot.y;
            sz = spot.z;
            stay = 3600 + r.nextInt(3600);
            int count = 3 + r.nextInt(7);
            heading = new double[count];
            speed = new double[count];
            altitude = new double[count];
            phase = new double[count];
            mode = new int[count];
            timer = new int[count];
            length = new int[count];
            fromX = new double[count];
            fromY = new double[count];
            fromZ = new double[count];
            toX = new double[count];
            toY = new double[count];
            toZ = new double[count];
            for (int i = 0; i < count; i++) {
                heading[i] = r.nextDouble() * Math.PI * 2;
                speed[i] = (4 + r.nextDouble() * 3) / 20.0;
                altitude[i] = 5 + r.nextDouble() * 18;
                phase[i] = r.nextDouble() * 1000;
                Flyer flyer = new Flyer(Kind.SILHOUETTE, Species.GULL, silhouetteSize(config, 1.0 + r.nextDouble() * 0.45),
                        215 + r.nextInt(30), null, 0.42f);
                flyer.place(sx + r.nextGaussian() * 12, sy + altitude[i], sz + r.nextGaussian() * 12);
                flyer.yaw = (float) Math.toDegrees(heading[i]);
                flyers.add(flyer);
            }
        }

        /** A dry spot of beach near the coast, in loaded chunks; null when none was found. */
        @Nullable double[] beach(RandomSource r) {
            for (int tries = 0; tries < 6; tries++) {
                double x = sx + r.nextGaussian() * 10, z = sz + r.nextGaussian() * 10;
                int bx = Mth.floor(x), bz = Mth.floor(z);
                if (!level.hasChunk(bx >> 4, bz >> 4)) continue;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
                if (level.getFluidState(new BlockPos(bx, top - 1, bz)).isEmpty()) return new double[]{x, top, z};
            }
            return null;
        }

        /** Landing, standing, taking off: true while gull i is busy with that rather than flying. */
        boolean ground(int i, Flyer flyer, LocalPlayer self, RandomSource r, boolean leaving) {
            boolean near = Math.hypot(flyer.x - self.getX(), flyer.z - self.getZ()) < 9;
            if (mode[i] == FLYING) {
                if (leaving || near || r.nextInt(900) != 0) return false;
                double[] beach = beach(r);
                if (beach == null) return false;
                start(i, flyer, LANDING, beach[0], beach[1], beach[2], 90);
            }
            timer[i]++;
            double t = Math.min(1.0, timer[i] / (double) length[i]);
            switch (mode[i]) {
                case LANDING -> {
                    double ease = 1 - Math.pow(1 - t, 2);
                    flyer.gliding = t < 0.8;
                    flyer.perched = false;
                    float yaw = (float) Math.toDegrees(Math.atan2(-(toX[i] - fromX[i]), toZ[i] - fromZ[i]));
                    flyer.moveTo(Mth.lerp(ease, fromX[i], toX[i]), Mth.lerp(ease, fromY[i], toY[i]),
                            Mth.lerp(ease, fromZ[i], toZ[i]), yaw, 0f);
                    if (t >= 1.0) start(i, flyer, LANDED, toX[i], toY[i], toZ[i], 300 + r.nextInt(900));
                }
                case LANDED -> {
                    flyer.perched = true;
                    float yaw = r.nextInt(80) == 0 ? flyer.yaw + (r.nextBoolean() ? 60f : -60f) : flyer.yaw;
                    flyer.moveTo(toX[i], toY[i], toZ[i], yaw, 0f);
                    if (t >= 1.0 || near || leaving) {
                        // Up and away from whoever came close, back to its altitude over the coast.
                        double away = near ? Math.atan2(flyer.x - self.getX(), flyer.z - self.getZ())
                                : r.nextDouble() * Math.PI * 2;
                        heading[i] = Math.atan2(-Math.sin(away), Math.cos(away));
                        start(i, flyer, RISING, flyer.x + Math.sin(away) * 16, sy + altitude[i],
                                flyer.z + Math.cos(away) * 16, 70);
                    }
                }
                default -> {
                    double ease = t * t;
                    flyer.perched = false;
                    flyer.gliding = false;
                    float yaw = (float) Math.toDegrees(Math.atan2(-(toX[i] - fromX[i]), toZ[i] - fromZ[i]));
                    flyer.moveTo(Mth.lerp(t, fromX[i], toX[i]), Mth.lerp(ease, fromY[i], toY[i]),
                            Mth.lerp(t, fromZ[i], toZ[i]), yaw, 0f);
                    if (t >= 1.0) {
                        heading[i] = Math.toRadians(yaw);
                        mode[i] = FLYING;
                    }
                }
            }
            return true;
        }

        void start(int i, Flyer flyer, int next, double x, double y, double z, int ticks) {
            mode[i] = next;
            timer[i] = 0;
            length[i] = ticks;
            fromX[i] = flyer.x;
            fromY[i] = flyer.y;
            fromZ[i] = flyer.z;
            toX[i] = x;
            toY[i] = y;
            toZ[i] = z;
        }

        @Override
        boolean tick(Ambience a, LocalPlayer self, double reach) {
            boolean leaving = age > stay;
            boolean anyNear = false;
            for (int i = 0; i < flyers.size(); i++) {
                Flyer flyer = flyers.get(i);
                if (ground(i, flyer, self, a.random, leaving)) {
                    anyNear = true;
                    continue;
                }
                double before = heading[i];
                heading[i] += 0.035 * Math.sin((age + phase[i]) * 0.011) + 0.02 * Math.sin((age + phase[i]) * 0.037);
                double toSpot = Math.atan2(-(sx - flyer.x), sz - flyer.z) + (leaving ? Math.PI : 0);
                if (leaving || Math.hypot(sx - flyer.x, sz - flyer.z) > 30) heading[i] += 0.04 * Math.sin(wrap(toSpot - heading[i]));
                double x = flyer.x - Math.sin(heading[i]) * speed[i];
                double z = flyer.z + Math.cos(heading[i]) * speed[i];
                double y = sy + altitude[i] + 3 * Math.sin((age + phase[i]) * 0.02) + (leaving ? (age - stay) * 0.03 : 0);
                float bank = (float) Mth.clamp(wrap(heading[i] - before) * 900, -35, 35);
                flyer.gliding = ((age + (int) phase[i]) % 160) > 28;
                flyer.moveTo(x, y, z, (float) Math.toDegrees(heading[i]), bank);
                if (Math.hypot(x - self.getX(), z - self.getZ()) < reach * 1.3) anyNear = true;
            }
            return !leaving || (anyNear && age < stay + 2400);
        }
    }

    // --- Robins and tits: small birds about a field or a tree --------------------------------

    /**
     * A few small birds around one spot: on the ground of a field (robins) or on the top of
     * a tree (tits). They hop, peck, flit to another place nearby, and fly off when someone
     * comes close - landing again a little farther, or leaving.
     */
    private static final class Perchers extends Group {
        final ClientLevel level;
        final double sx, sy, sz;
        final boolean onTrees, onWater;
        final int stay;
        /** Per bird: where it comes from and goes to, how far along, and whether it flies there. */
        final double[] fromX, fromY, fromZ, toX, toY, toZ;
        final int[] timer, length;
        final boolean[] flying;

        Perchers(Ambience a, ClientLevel level, Spot spot, FarConfig config, Type type, Species species,
                 int min, int max, float span) {
            super(type);
            RandomSource r = a.random;
            this.level = level;
            sx = spot.x;
            sy = spot.y;
            sz = spot.z;
            onTrees = species == Species.TIT;
            onWater = species == Species.DUCK;
            stay = 2400 + r.nextInt(3600);
            int count = min + r.nextInt(max - min + 1);
            fromX = new double[count];
            fromY = new double[count];
            fromZ = new double[count];
            toX = new double[count];
            toY = new double[count];
            toZ = new double[count];
            timer = new int[count];
            length = new int[count];
            flying = new boolean[count];
            for (int i = 0; i < count; i++) {
                Flyer flyer = new Flyer(Kind.SILHOUETTE, species, silhouetteSize(config, span * (0.9 + r.nextDouble() * 0.2)),
                        255, null, 0.9f);
                double x = sx + r.nextGaussian() * 2, z = sz + r.nextGaussian() * 2;
                double y = groundAt(x, z);
                flyer.place(x, y, z);
                flyer.yaw = flyer.yawO = r.nextFloat() * 360f;
                flyer.perched = true;
                toX[i] = fromX[i] = x;
                toY[i] = fromY[i] = y;
                toZ[i] = fromZ[i] = z;
                timer[i] = length[i] = 20 + r.nextInt(60);
                flyers.add(flyer);
            }
        }

        double groundAt(double x, double z) {
            return ground(level, x, z, onTrees || onWater ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sy);
        }

        /** Whether a duck may go there: water, or a column not loaded, which it trusts. */
        boolean swimmable(double x, double z) {
            int bx = Mth.floor(x), bz = Mth.floor(z);
            if (!level.hasChunk(bx >> 4, bz >> 4)) return true;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
            return level.getFluidState(new BlockPos(bx, top - 1, bz)).is(FluidTags.WATER);
        }

        @Override
        boolean tick(Ambience a, LocalPlayer self, double reach) {
            RandomSource r = a.random;
            boolean leaving = age > stay;
            boolean anyNear = false;
            if (onWater) {
                // Ducks never let anyone near: the whole lot takes off, away, and is gone.
                for (Flyer flyer : flyers) {
                    if (Math.hypot(flyer.x - self.getX(), flyer.z - self.getZ()) < 14) {
                        fleeFrom(self.getX(), self.getZ());
                        return false;
                    }
                }
            }
            for (int i = 0; i < flyers.size(); i++) {
                Flyer flyer = flyers.get(i);
                double close = Math.hypot(flyer.x - self.getX(), flyer.z - self.getZ());
                boolean scared = !onWater && close < 7 && Math.abs(flyer.y - self.getY()) < 6;
                if (timer[i] >= length[i] || (scared && !flying[i])) {
                    // Next move: a hop, a short flight, or away from whoever came close.
                    fromX[i] = flyer.x;
                    fromY[i] = flyer.y;
                    fromZ[i] = flyer.z;
                    double angle, distance;
                    if (scared || leaving) {
                        angle = Math.atan2(flyer.x - self.getX(), flyer.z - self.getZ()) + r.nextGaussian() * 0.4;
                        distance = leaving ? 60 : 12 + r.nextDouble() * 10;
                        flying[i] = true;
                    } else if (r.nextInt(onTrees ? 3 : onWater ? 25 : 6) == 0) {
                        angle = r.nextDouble() * Math.PI * 2;
                        distance = onWater ? 8 + r.nextDouble() * 12 : 3 + r.nextDouble() * 5;
                        flying[i] = true;
                    } else {
                        angle = r.nextDouble() * Math.PI * 2;
                        distance = onWater ? 0.8 + r.nextDouble() * 1.2 : onTrees ? 0.6 : 0.5 + r.nextDouble() * 0.4;
                        flying[i] = false;
                    }
                    double tx = flyer.x + Math.sin(angle) * distance, tz = flyer.z + Math.cos(angle) * distance;
                    // Never wander far from the spot, unless leaving or scared off.
                    if (!leaving && !scared && Math.hypot(tx - sx, tz - sz) > 10) {
                        tx = sx + r.nextGaussian() * 2;
                        tz = sz + r.nextGaussian() * 2;
                    }
                    // Ducks stay on the water.
                    if (onWater && !swimmable(tx, tz)) {
                        tx = flyer.x;
                        tz = flyer.z;
                    }
                    toX[i] = tx;
                    toZ[i] = tz;
                    toY[i] = groundAt(tx, tz);
                    timer[i] = 0;
                    // About three blocks a second: slow enough to see them take off.
                    length[i] = flying[i] ? (int) (20 + distance * 5) : onWater ? (int) (distance * 25) + 1 : 6;
                    flyer.yaw = (float) Math.toDegrees(Math.atan2(-(tx - flyer.x), tz - flyer.z));
                }
                timer[i]++;
                double t = Math.min(1.0, timer[i] / (double) length[i]);
                double x = Mth.lerp(t, fromX[i], toX[i]);
                double z = Mth.lerp(t, fromZ[i], toZ[i]);
                double arc = flying[i] ? 1.5 + Math.hypot(toX[i] - fromX[i], toZ[i] - fromZ[i]) * 0.15 : onWater ? 0 : 0.35;
                double y = Mth.lerp(t, fromY[i], toY[i]) + Math.sin(t * Math.PI) * arc;
                // Afloat: rocking gently on the water, the body a little below its surface.
                if (onWater && !flying[i]) y += Math.sin(age * 0.12 + i) * 0.03 - 0.1;
                flyer.perched = !flying[i] || t >= 1.0;
                flyer.gliding = false;
                if (t >= 1.0) {
                    flying[i] = false;
                    // Waiting: a peck now and then, then the next move.
                    if (timer[i] == length[i]) length[i] += 20 + r.nextInt(80);
                    if ((timer[i] / 7) % 9 == 0) y -= 0.05;
                }
                flyer.moveTo(x, y, z, flyer.yaw, 0f);
                if (Math.hypot(x - self.getX(), z - self.getZ()) < reach) anyNear = true;
            }
            return !leaving || (anyNear && age < stay + 400);
        }
    }

    // --- Pigeons on high buildings -------------------------------------------------------------

    /**
     * Pigeons sitting along the top of something tall. Someone comes close: the whole lot
     * takes off, circles overhead a while, and lands back once the coast is clear - or leaves.
     */
    private static final class Pigeons extends Group {
        final double sx, sy, sz;
        final double[] perchX, perchY, perchZ, angle;
        final int stay;
        int airborne = -1;

        Pigeons(Ambience a, ClientLevel level, Spot spot, FarConfig config) {
            super(Type.PIGEONS);
            RandomSource r = a.random;
            sx = spot.x;
            sy = spot.y;
            sz = spot.z;
            stay = 3600 + r.nextInt(4800);
            List<double[]> seats = new ArrayList<>();
            int wanted = 3 + r.nextInt(6);
            for (int tries = 0; tries < 24 && seats.size() < wanted; tries++) {
                double x = Math.floor(sx) + r.nextInt(5) - 2 + 0.2 + r.nextDouble() * 0.6;
                double z = Math.floor(sz) + r.nextInt(5) - 2 + 0.2 + r.nextDouble() * 0.6;
                double y = ground(level, x, z, Heightmap.Types.MOTION_BLOCKING, sy);
                // Only along the same height: on the building, not in the air beside it.
                if (Math.abs(y - sy) <= 1.01) seats.add(new double[]{x, y, z});
            }
            int count = seats.size();
            perchX = new double[count];
            perchY = new double[count];
            perchZ = new double[count];
            angle = new double[count];
            for (int i = 0; i < count; i++) {
                perchX[i] = seats.get(i)[0];
                perchY[i] = seats.get(i)[1];
                perchZ[i] = seats.get(i)[2];
                angle[i] = r.nextDouble() * Math.PI * 2;
                Flyer flyer = new Flyer(Kind.SILHOUETTE, Species.PIGEON, silhouetteSize(config, 0.85 + r.nextDouble() * 0.15),
                        255, null, 0.75f);
                flyer.place(perchX[i], perchY[i], perchZ[i]);
                flyer.yaw = flyer.yawO = r.nextFloat() * 360f;
                flyer.perched = true;
                flyers.add(flyer);
            }
        }

        @Override
        boolean tick(Ambience a, LocalPlayer self, double reach) {
            RandomSource r = a.random;
            double close = Math.hypot(sx - self.getX(), sz - self.getZ());
            boolean leaving = age > stay;
            if (airborne < 0 && (close < 10 || leaving)) airborne = 0;
            if (airborne < 0) {
                for (int i = 0; i < flyers.size(); i++) {
                    Flyer flyer = flyers.get(i);
                    // Sitting: a turn now and then.
                    float yaw = r.nextInt(120) == 0 ? flyer.yaw + (r.nextBoolean() ? 90f : -90f) : flyer.yaw;
                    flyer.perched = true;
                    flyer.moveTo(perchX[i], perchY[i], perchZ[i], yaw, 0f);
                }
                return true;
            }
            airborne++;
            // Circling overhead; back down once nobody is near and they have flown a while.
            boolean land = airborne > 300 && close > 16 && !leaving;
            boolean settled = true;
            for (int i = 0; i < flyers.size(); i++) {
                Flyer flyer = flyers.get(i);
                // About four or five blocks a second around the circle.
                angle[i] += 0.022 + i * 0.001;
                double radius = leaving ? 6 + airborne * 0.08 : 9 + i * 0.7;
                double tx = sx + Math.cos(angle[i]) * radius, tz = sz + Math.sin(angle[i]) * radius;
                double ty = sy + Math.min(12, airborne * 0.06) + (leaving ? airborne * 0.02 : 0);
                if (land) {
                    tx = perchX[i];
                    ty = perchY[i];
                    tz = perchZ[i];
                }
                double pull = land ? 0.035 : 0.06;
                double x = flyer.x + (tx - flyer.x) * pull;
                double y = flyer.y + (ty - flyer.y) * pull;
                double z = flyer.z + (tz - flyer.z) * pull;
                boolean moving = Math.abs(x - flyer.x) + Math.abs(z - flyer.z) > 0.01;
                float yaw = moving ? (float) Math.toDegrees(Math.atan2(-(x - flyer.x), z - flyer.z)) : flyer.yaw;
                flyer.perched = false;
                flyer.gliding = false;
                flyer.moveTo(x, y, z, yaw, 0f);
                if (Math.abs(flyer.y - perchY[i]) > 0.05 || Math.hypot(flyer.x - perchX[i], flyer.z - perchZ[i]) > 0.1) {
                    settled = false;
                }
            }
            if (land && settled) {
                airborne = -1;
                for (int i = 0; i < flyers.size(); i++) {
                    flyers.get(i).place(perchX[i], perchY[i], perchZ[i]);
                    flyers.get(i).perched = true;
                }
            }
            return !leaving || airborne < 1200;
        }
    }

    // --- Bats at night -------------------------------------------------------------------------

    /**
     * A few bats around a spot: flitting about, erratic and quick - or, near you, hanging
     * upside down under leaves, a roof, an overhang, until you come close and they drop and
     * fly off.
     */
    private static final class Bats extends Group {
        final double cx, cy, cz;
        final double[] vx, vy, vz;
        final boolean[] hanging;
        final int life;

        Bats(Ambience a, ClientLevel level, LocalPlayer self, double reach) {
            super(Type.BATS);
            RandomSource r = a.random;
            double bearing = r.nextDouble() * Math.PI * 2;
            // Half of the groups roost close by, where the chunks are loaded and ceilings known.
            boolean roost = r.nextBoolean();
            double distance = roost ? 10 + r.nextDouble() * 30 : 18 + r.nextDouble() * (Math.min(140, reach) - 18);
            cx = self.getX() - Math.sin(bearing) * distance;
            cz = self.getZ() + Math.cos(bearing) * distance;
            cy = self.getY() + 4 + r.nextDouble() * 22;
            life = 1200 + r.nextInt(2400);
            int count = 2 + r.nextInt(6);
            vx = new double[count];
            vy = new double[count];
            vz = new double[count];
            hanging = new boolean[count];
            for (int i = 0; i < count; i++) {
                Bat bat = Puppets.create(EntityType.BAT, level);
                if (bat == null) break;
                bat.setOnGround(false);
                Flyer flyer = new Flyer(Kind.BAT, Species.GENERIC, 1f, 0, bat, 0f);
                double[] ceiling = roost ? ceiling(level, cx + r.nextGaussian() * 4, cz + r.nextGaussian() * 4, self.getY()) : null;
                if (ceiling != null) {
                    bat.setResting(true);
                    hanging[i] = true;
                    flyer.place(ceiling[0], ceiling[1], ceiling[2]);
                    flyer.yaw = flyer.yawO = r.nextFloat() * 360f;
                } else {
                    bat.setResting(false);
                    flyer.place(cx + r.nextGaussian() * 4, cy + r.nextGaussian() * 2, cz + r.nextGaussian() * 4);
                }
                flyers.add(flyer);
            }
        }

        /**
         * Somewhere to hang at a column: the underside of the highest block with air below
         * it, under the top of the column - leaves, a roof, an overhang. Loaded chunks only.
         */
        static @Nullable double[] ceiling(ClientLevel level, double x, double z, double near) {
            int bx = Mth.floor(x), bz = Mth.floor(z);
            if (!level.hasChunk(bx >> 4, bz >> 4)) return null;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
            for (int y = top - 2; y > near - 16 && y > level.getMinY(); y--) {
                BlockPos at = new BlockPos(bx, y, bz);
                if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).isAir()
                        && level.getBlockState(at.above()).isFaceSturdy(level, at.above(), Direction.DOWN)) {
                    // A bat is 0.9 tall: hanging, its top touches the block above.
                    return new double[]{bx + 0.5, y + 1 - 0.9, bz + 0.5};
                }
            }
            return null;
        }

        @Override
        boolean tick(Ambience a, LocalPlayer self, double reach) {
            RandomSource r = a.random;
            for (int i = 0; i < flyers.size(); i++) {
                Flyer flyer = flyers.get(i);
                if (hanging[i]) {
                    if (Math.hypot(flyer.x - self.getX(), flyer.z - self.getZ()) < 6
                            && Math.abs(flyer.y - self.getY()) < 6) {
                        // Someone came close: drop, and away.
                        hanging[i] = false;
                        if (flyer.puppet instanceof Bat bat) bat.setResting(false);
                        double away = Math.atan2(flyer.x - self.getX(), flyer.z - self.getZ());
                        vx[i] = Math.sin(away) * 0.3;
                        vz[i] = Math.cos(away) * 0.3;
                        vy[i] = -0.1;
                    } else {
                        flyer.moveTo(flyer.x, flyer.y, flyer.z, flyer.yaw, 0f);
                        continue;
                    }
                }
                // Jinking about, pulled back towards the spot.
                vx[i] += r.nextGaussian() * 0.06 + (cx - flyer.x) * 0.004;
                vy[i] += r.nextGaussian() * 0.04 + (cy - flyer.y) * 0.006;
                vz[i] += r.nextGaussian() * 0.06 + (cz - flyer.z) * 0.004;
                double speed = Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]);
                if (speed > 0.35) {
                    vx[i] *= 0.35 / speed;
                    vy[i] *= 0.35 / speed;
                    vz[i] *= 0.35 / speed;
                }
                float yaw = (float) Math.toDegrees(Math.atan2(-vx[i], vz[i]));
                flyer.moveTo(flyer.x + vx[i], flyer.y + vy[i], flyer.z + vz[i], yaw, 0f);
            }
            return age < life;
        }
    }

    private static double wrap(double angle) {
        return Mth.wrapDegrees(Math.toDegrees(angle)) * Mth.DEG_TO_RAD;
    }

    private static @Nullable Parrot parrot(ClientLevel level, int variant) {
        Parrot parrot = Puppets.create(EntityType.PARROT, level);
        if (parrot == null) return null;
        CompoundTag tag = new CompoundTag();
        tag.putInt("Variant", variant);
        EntityNbt.load(parrot, level, tag);
        // Not on the ground: the renderer draws it flying.
        parrot.setOnGround(false);
        return parrot;
    }

    /** The puppets among the flyers: parrots and bats, drawn like other distant mobs. */
    public List<Entity> puppets() {
        List<Entity> puppets = new ArrayList<>();
        for (Flyer flyer : flyers) if (flyer.puppet != null) puppets.add(flyer.puppet);
        return puppets;
    }
}
