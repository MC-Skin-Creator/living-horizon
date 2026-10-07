package fr.clixmods.livinghorizon.track;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
//? if >=1.21.6
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * One other player, followed whether or not the server still sends them.
 *
 * <p>While the server sends the entity, it is the truth and the game draws it. Once it
 * stops, what remains is a memory - how they looked, what they rode - and whatever else
 * still arrives: the companion data pack's exact position when the server runs it, the
 * locator bar otherwise. The memory becomes two puppets (the player and their mount),
 * copies that were never added to the world, which this class moves and animates every
 * tick from the best estimate of where the real one is.
 */
public final class FarPlayer {
    public enum Source {
        /** The server sends the entity: its position is exact. */
        LIVE,
        /** The companion data pack publishes the position: exact, at any distance. */
        SHARED,
        /** The locator bar gives the block. */
        EXACT,
        /** The locator bar gives the chunk: 332 blocks or less, past the view distance. */
        CHUNK,
        /** The locator bar gives a direction only; the distance is estimated. */
        BEARING,
        /** Nothing is known any more. */
        LOST
    }

    private static final double DT = 0.05;

    final UUID id;
    Source source = Source.LOST;

    /** The live entity, only while {@link Source#LIVE}. */
    @Nullable AbstractClientPlayer live;

    // The memory of the last time the entity was sent.
    private @Nullable AbstractClientPlayer lastSeen;
    private @Nullable Entity lastVehicle;
    /** The player's position relative to their mount, in the mount's own frame. */
    private Vec3 riderOffset = Vec3.ZERO;
    private float riderYaw;
    private double knownY = Double.NaN;
    /** Where they look, when the data pack says it. */
    private float knownYaw = Float.NaN;
    /** Whether they ride something, when the data pack says it. */
    private boolean riding = true;

    /**
     * Not to be drawn until something new is heard: disappeared too close to have walked
     * out of range (died, left through a portal), or in another dimension.
     */
    boolean vanished;
    int ticksSinceInfo;

    final PositionFilter filter = new PositionFilter();

    // What is drawn, smoothed out of the estimate.
    private boolean placed;
    private double x, y, z;
    private double velX, velZ;
    private float yaw;

    @Nullable RemotePlayer puppet;
    @Nullable Entity mountPuppet;

    /** Their profile as the tab list last gave it: name and skin, kept for when they log off. */
    @Nullable GameProfile profile;
    /** The data pack code of the dimension x, y, z belong to; -1 when they belong to none. */
    private int knownDimension = -1;

    FarPlayer(UUID id) {
        this.id = id;
    }

    public UUID id() { return id; }
    public Source source() { return source; }
    public boolean vanished() { return vanished; }
    public @Nullable AbstractClientPlayer live() { return live; }
    public @Nullable RemotePlayer puppet() { return puppet; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public boolean placed() { return placed; }
    public float yaw() { return yaw; }
    int knownDimension() { return knownDimension; }

    /** The mount to draw under the puppet, if any. */
    public @Nullable Entity mountPuppet() {
        return riding ? mountPuppet : null;
    }

    /** How sure the position is, in blocks. */
    public double uncertainty() {
        return switch (source) {
            case LIVE, SHARED -> 0.1;
            case EXACT -> 0.5;
            default -> filter.ready() ? filter.spread() : Double.NaN;
        };
    }

    /** Whether the puppets should be drawn this frame. */
    public boolean showsPuppet(FarConfig config) {
        if (source == Source.LIVE || !placed || vanished || puppet == null) return false;
        return source != Source.LOST || ticksSinceInfo < config.lostTimeoutSeconds * 20;
    }

    // --- While the server sends the entity ---------------------------------------

    void observeLive(AbstractClientPlayer player, int dimension) {
        knownDimension = dimension;
        double px = x, pz = z;
        boolean wasPlaced = placed;
        source = Source.LIVE;
        live = player;
        lastSeen = player;
        vanished = false;
        ticksSinceInfo = 0;
        knownY = player.getY();

        Entity root = player.getRootVehicle();
        if (root != player) {
            lastVehicle = root;
            riderOffset = player.position().subtract(root.position()).yRot((float) Math.toRadians(root.getYRot()));
            riderYaw = player.getYRot() - root.getYRot();
        } else {
            lastVehicle = null;
        }
        riding = root != player;

        x = player.getX();
        y = player.getY();
        z = player.getZ();
        placed = true;
        if (wasPlaced) {
            velX = lerp(0.3, velX, x - px);
            velZ = lerp(0.3, velZ, z - pz);
        }
        yaw = player.getYRot();
        puppet = null;
        mountPuppet = null;
    }

    // --- Once it stops ------------------------------------------------------------

    void observeRemote(ClientLevel level, Player self, PlayerInfo info, SharedPositions.@Nullable Report shared,
                       //? if >=1.21.6 {
                       int dimension, @Nullable TrackedWaypoint waypoint, double vanishRadius, FarConfig config) {
                       //?} else
                       /*int dimension, @Nullable Object waypoint, double vanishRadius, FarConfig config) {*/
        if (source == Source.LIVE) leaveLive(level, self, vanishRadius);
        if (puppet == null) freshPuppet(level, info);

        filter.predict(DT);
        Source heard;
        if (shared != null) {
            heard = share(shared, dimension);
        } else {
            heard = waypoint == null ? null : read(waypoint, self);
        }

        if (heard != null) {
            source = heard;
            vanished = false;
            ticksSinceInfo = 0;
        } else {
            if (source != Source.LOST) ticksSinceInfo = 0;
            source = Source.LOST;
            ticksSinceInfo++;
        }

        if (source != Source.LOST && filter.ready()) {
            follow(self);
            knownDimension = dimension;
        }
        animate(config);
    }

    private void leaveLive(ClientLevel level, Player self, double vanishRadius) {
        live = null;
        source = Source.LOST;
        // Players leave the server's range by walking out of it. One that disappears
        // well inside it was removed instead: dead, through a portal, logged off.
        vanished = Math.hypot(x - self.getX(), z - self.getZ()) < vanishRadius;
        if (vanished) {
            filter.clear();
            placed = false;
        } else {
            filter.resetAt(x, z, velX * 20, velZ * 20, 1.0);
        }
        if (lastSeen != null) {
            puppet = copyPlayer(level, lastSeen);
            mountPuppet = lastVehicle == null ? null : copyEntity(level, lastVehicle);
            if (mountPuppet != null) {
                mountPuppet.setYRot(lastVehicle.getYRot());
            }
        }
    }

    /** Takes the data pack's word. Null when they are in another dimension. */
    private @Nullable Source share(SharedPositions.Report report, int dimension) {
        if (report.dimension() != dimension) {
            knownDimension = -1;
            vanished = true;
            placed = false;
            filter.clear();
            return null;
        }
        filter.resetAt(report.x(), report.z(),
                filter.ready() ? filter.velocityX() : 0, filter.ready() ? filter.velocityZ() : 0, 0.1);
        knownY = report.y();
        knownYaw = report.yaw();
        riding = report.riding();
        return Source.SHARED;
    }

    //? if <1.21.6 {
    /*/^* No locator bar before 1.21.6: nothing to read. ^/
    private @Nullable Source read(Object waypoint, Player self) {
        return null;
    }
    *///?} else {
    /** Feeds one locator bar waypoint to the filter. Null when it carries nothing. */
    private @Nullable Source read(TrackedWaypoint waypoint, Player self) {
        knownYaw = Float.NaN;
        if (waypoint instanceof TrackedWaypoint.Vec3iWaypoint block) {
            double bx = block.vector.getX() + 0.5, bz = block.vector.getZ() + 0.5;
            filter.resetAt(bx, bz, filter.ready() ? filter.velocityX() : 0, filter.ready() ? filter.velocityZ() : 0, 0.4);
            knownY = block.vector.getY();
            return Source.EXACT;
        }
        if (waypoint instanceof TrackedWaypoint.ChunkWaypoint chunk) {
            ChunkPos pos = chunk.chunkPos;
            double minX = pos.getMinBlockX(), minZ = pos.getMinBlockZ();
            if (!filter.ready() || !filter.observeBox(minX, minZ, minX + 16, minZ + 16, 1.5)) {
                filter.resetAt(minX + 8, minZ + 8, 0, 0, 4.0);
            }
            return Source.CHUNK;
        }
        if (waypoint instanceof TrackedWaypoint.AzimuthWaypoint azimuth) {
            double angle = azimuth.angle;
            double ox = self.getX(), oz = self.getZ();
            if (!filter.ready()) {
                filter.resetAlongBearing(ox, oz, angle, 340, 3000);
            }
            if (!bearing(ox, oz, angle)) {
                // Nothing in the cloud agrees: a teleport, a respawn, or a turn the
                // cloud could not follow. Start again on the new line, around the
                // distance believed so far.
                double r = placed ? Math.max(340, Math.hypot(x - ox, z - oz)) : 1000;
                filter.resetAlongBearing(ox, oz, angle, Math.max(340, r * 0.5), Math.max(r * 2.0, 900));
                bearing(ox, oz, angle);
            }
            return Source.BEARING;
        }
        return null;
    }
    //?}

    private boolean bearing(double ox, double oz, double angle) {
        // Half a degree is the server's own threshold for sending a new angle.
        return filter.observeBearing(ox, oz, angle, Math.toRadians(0.6), Math.toRadians(0.6))
                // A direction is only sent past 332 blocks.
                && filter.observeDistance(ox, oz, 330, Double.POSITIVE_INFINITY, 6.0);
    }

    /** Moves what is drawn towards the estimate, smoothly unless the jump is a teleport. */
    private void follow(Player self) {
        double tx = filter.x(), tz = filter.z();
        double ty = Double.isNaN(knownY) ? self.getY() : knownY;
        double px = x, pz = z;
        boolean precise = source == Source.EXACT || source == Source.SHARED;
        if (!placed || (precise && Math.hypot(tx - x, tz - z) > 64)) {
            x = tx;
            y = ty;
            z = tz;
            placed = true;
            velX = velZ = 0;
            return;
        }
        double seconds = switch (source) {
            // The pack publishes five times a second.
            case SHARED -> 0.2;
            case EXACT -> 0.15;
            case CHUNK -> 0.6;
            default -> 1.0;
        };
        double k = 1 - Math.exp(-DT / seconds);
        x += (tx - x) * k;
        y += (ty - y) * (1 - Math.exp(-DT / (precise ? 0.2 : 0.3)));
        z += (tz - z) * k;
        velX = lerp(0.2, velX, x - px);
        velZ = lerp(0.2, velZ, z - pz);
    }

    // --- Puppets ---------------------------------------------------------------------

    private void animate(FarConfig config) {
        if (puppet == null) return;
        double speed = Math.hypot(velX, velZ); // blocks per tick
        boolean moving = speed > 0.03 && source != Source.LOST;
        if (moving) {
            yaw = (float) Math.toDegrees(Math.atan2(-velX, velZ));
        } else if (!Float.isNaN(knownYaw)) {
            yaw = knownYaw;
        }
        float head = Float.isNaN(knownYaw) ? yaw : knownYaw;
        float walk = moving ? (float) Math.min(speed * 4.0, 1.0) : 0f;

        Entity mount = config.showVehicles ? mountPuppet() : null;
        if (mount != null) {
            place(mount, x, y, z, yaw, yaw, walk);
            Vec3 seat = riderOffset.yRot((float) -Math.toRadians(yaw));
            mount.setPos(x - seat.x, y - seat.y, z - seat.z);
            place(puppet, x, y, z, yaw + riderYaw, head, 0f);
        } else {
            place(puppet, x, y, z, yaw, head, walk);
        }
    }

    private static void place(Entity entity, double px, double py, double pz, float body, float head, float walk) {
        entity.setOldPosAndRot();
        entity.setPos(px, py, pz);
        entity.setYRot(head);
        entity.setXRot(0f);
        if (entity instanceof LivingEntity living) {
            living.yBodyRotO = living.yBodyRot;
            living.yHeadRotO = living.yHeadRot;
            living.yBodyRot = body;
            living.yHeadRot = head;
            Puppets.walk(living, walk, 0.4f, living.isBaby() ? 3f : 1f);
        }
        entity.tickCount++;
    }

    /** A player never seen up close: their skin from the tab list, every skin layer on. */
    private void freshPuppet(ClientLevel level, PlayerInfo info) {
        puppet = Puppets.numbered(new RemotePlayer(level, info.getProfile()));
        puppet.getEntityData().set(Avatar.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
        mountPuppet = null;
    }

    private static RemotePlayer copyPlayer(ClientLevel level, AbstractClientPlayer source) {
        RemotePlayer copy = Puppets.numbered(new RemotePlayer(level, source.getGameProfile()));
        copyState(source, copy);
        return copy;
    }

    private static @Nullable Entity copyEntity(ClientLevel level, Entity source) {
        Entity copy = Puppets.create(source.getType(), level);
        if (copy == null) return null;
        copyState(source, copy);
        return copy;
    }

    /**
     * What the server told this client about the entity, given to the copy the same way
     * the network gives it: synched data (variants, saddles, skin layers, pose) and
     * equipment.
     */
    private static void copyState(Entity source, Entity copy) {
        try {
            List<SynchedEntityData.DataValue<?>> values = source.getEntityData().getNonDefaultValues();
            if (values != null) copy.getEntityData().assignValues(values);
        } catch (RuntimeException ignored) {
            // A modded entity with unusual data: the copy keeps its defaults.
        }
        if (source instanceof LivingEntity from && copy instanceof LivingEntity to) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                to.setItemSlot(slot, from.getItemBySlot(slot).copy());
            }
        }
        copy.setPos(source.getX(), source.getY(), source.getZ());
        copy.setOldPosAndRot();
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }
}
