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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * One other player, followed whether or not the server still sends them.
 *
 * <p>While the server sends the entity, it is the truth and the game draws it. Once it
 * stops, what remains is a memory - how they looked, what they rode - and the position
 * the companion data pack (or a LAN host's mod) publishes, when there is one. The memory
 * becomes two puppets (the player and their mount), copies that were never added to the world, which this class moves and animates every
 * tick towards where the real one is.
 */
public final class FarPlayer {
    public enum Source {
        /** The server sends the entity: its position is exact. */
        LIVE,
        /** The data pack, or a LAN host's mod, publishes the position: exact, at any distance. */
        SHARED,
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

    /** Where the shared position says they are; only meaningful while {@link #targeted}. */
    private double targetX, targetZ;
    private boolean targeted;

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
        return source == Source.LIVE || source == Source.SHARED ? 0.1 : Double.NaN;
    }

    /** Whether the puppets should be drawn this frame. */
    public boolean showsPuppet(FarConfig config) {
        return source != Source.LIVE && source != Source.LOST && placed && !vanished && puppet != null;
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
                       int dimension, double vanishRadius, FarConfig config) {
        if (source == Source.LIVE) leaveLive(level, self, vanishRadius);
        if (puppet == null) freshPuppet(level, info);

        Source heard = shared == null ? null : share(shared, dimension);

        if (heard != null) {
            source = heard;
            vanished = false;
            ticksSinceInfo = 0;
        } else {
            if (source != Source.LOST) ticksSinceInfo = 0;
            source = Source.LOST;
            ticksSinceInfo++;
        }

        if (source != Source.LOST && targeted) {
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
            targeted = false;
            placed = false;
        } else {
            targetX = x;
            targetZ = z;
            targeted = true;
        }
        if (lastSeen != null) {
            puppet = copyPlayer(level, lastSeen);
            mountPuppet = lastVehicle == null ? null : copyEntity(level, lastVehicle);
            if (mountPuppet != null) {
                mountPuppet.setYRot(lastVehicle.getYRot());
            }
        }
    }

    /** Takes the shared position's word. Null when they are in another dimension. */
    private @Nullable Source share(SharedPositions.Report report, int dimension) {
        if (report.dimension() != dimension) {
            knownDimension = -1;
            vanished = true;
            placed = false;
            targeted = false;
            return null;
        }
        targetX = report.x();
        targetZ = report.z();
        targeted = true;
        knownY = report.y();
        knownYaw = report.yaw();
        riding = report.riding();
        return Source.SHARED;
    }

    /** Moves what is drawn towards the estimate, smoothly unless the jump is a teleport. */
    private void follow(Player self) {
        double tx = targetX, tz = targetZ;
        double ty = Double.isNaN(knownY) ? self.getY() : knownY;
        double px = x, pz = z;
        if (!placed || Math.hypot(tx - x, tz - z) > 64) {
            x = tx;
            y = ty;
            z = tz;
            placed = true;
            velX = velZ = 0;
            return;
        }
        // The positions come five times a second.
        double k = 1 - Math.exp(-DT / 0.2);
        x += (tx - x) * k;
        y += (ty - y) * k;
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
