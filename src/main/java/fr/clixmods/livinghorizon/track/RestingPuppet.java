package fr.clixmods.livinghorizon.track;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * A player who logged off, drawn where they rest. Their skin comes from a remembered
 * profile, not from the tab list.
 *
 * <p>Where the blocks around are loaded, the puppet keeps to them: break what it rests
 * on and it falls to the floor below; bury it and it reappears on top. Its own spot, where
 * the player logged off, always wins once it has room and something under it again - that
 * is where the player will come back. A free bed close to that spot is better still: it is
 * shown asleep in it. Nothing here can hurt it: with nothing below down to the bottom of
 * the world, it stays where it is.
 */
public final class RestingPuppet extends RemotePlayer {
    /** Blocks per tick², the drag and the most it falls in one tick: the game's own. */
    private static final double GRAVITY = 0.08, DRAG = 0.98, TERMINAL = 3.92;
    /** Blocks per tick when it climbs back through open air. */
    private static final double RISE = 0.3;
    /** Ticks it stays on its feet after moving, before lying or sitting back down. */
    private static final int STAND_TICKS = 15;
    /** Ticks between two looks for a bed. */
    private static final int BED_SCAN = 20;
    /** The game lays a sleeper this high above the block of its bed. */
    private static final double BED_HEIGHT = 0.6875;

    private final RestingPlayers.Spot spot;
    private final PlayerInfo info;

    private boolean placed;
    private double fall;
    private int standing;
    private int bedScan;
    private @Nullable BlockPos bed;

    RestingPuppet(ClientLevel level, RestingPlayers.Spot spot, GameProfile profile) {
        super(level, profile);
        this.spot = spot;
        this.info = new PlayerInfo(profile, false);
        getEntityData().set(Avatar.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
        snap(spot.x(), spot.y(), spot.z());
        setYRot(spot.yaw());
        yBodyRot = yBodyRotO = spot.yaw();
        yHeadRot = yHeadRotO = spot.yaw();
    }

    @Override
    protected @Nullable PlayerInfo getPlayerInfo() {
        return info;
    }

    /** Its name, marked offline: what its name tag says. */
    @Override
    public Component getDisplayName() {
        return Component.empty().append(getName()).append(" ")
                .append(Component.translatable("livinghorizon.offline.tag").withStyle(ChatFormatting.GRAY));
    }

    /**
     * In a bed. The bed is its sleeping position, as for a player asleep in one, so the
     * game's renderer lays it along the bed once its pose is sleeping.
     */
    public boolean inBed() {
        return bed != null;
    }

    /** On its feet, as if awake: it has just moved. */
    public boolean standing() {
        return standing > 0;
    }

    @Nullable BlockPos bed() {
        return bed;
    }

    // --- Once per tick -------------------------------------------------------------------

    /**
     * Keeps the idle animations going, and the puppet in a bed or on the ground.
     *
     * @param beds the beds puppets lie in: one sleeper per bed
     */
    void tick(ClientLevel level, FarConfig config, Set<BlockPos> beds) {
        setOldPosAndRot();
        tickCount++;
        if (standing > 0) standing--;

        BlockPos home = BlockPos.containing(spot.x(), spot.y(), spot.z());
        if (!level.hasChunkAt(home)) {
            // Nothing known of the blocks here: left as it is, and placed at once next time.
            placed = false;
            return;
        }
        boolean first = !placed;
        placed = true;

        if (config.offlineBedRadius > 0) {
            if (bed != null && !freeBed(level.getBlockState(bed))) leaveBed(beds);
            if (bed == null && (first || --bedScan <= 0)) {
                bedScan = BED_SCAN;
                findBed(level, home, config.offlineBedRadius, beds);
            }
        } else if (bed != null) {
            leaveBed(beds);
        }
        if (bed != null) {
            snap(bed.getX() + 0.5, bed.getY() + BED_HEIGHT, bed.getZ() + 0.5);
            fall = 0;
            return;
        }

        double x = spot.x(), z = spot.z();
        if (getX() != x || getZ() != z) {
            // Out of a bed: back on its own spot, and from there to the ground.
            snap(x, spot.y(), z);
            first = true;
        }
        if (!config.offlineOnGround) {
            if (getY() != spot.y()) snap(x, spot.y(), z);
            return;
        }

        double height = "sit".equals(config.offlinePose) ? 1.2 : 0.5;
        double y = getY();
        double target;
        if (fits(level, x, spot.y(), z, height) && supported(level, x, spot.y(), z)) {
            target = spot.y();
        } else if (!fits(level, x, y, z, height)) {
            target = freeAbove(level, x, y, z, height);
        } else if (!supported(level, x, y, z)) {
            target = floorBelow(level, x, y, z);
        } else {
            target = y;
        }

        if (target == y) {
            fall = 0;
            return;
        }
        if (first || !clear(level, x, y, target, z, height)) {
            // Nothing to see on the way: through blocks, it simply reappears.
            fall = 0;
            snap(x, target, z);
            return;
        }
        Motion step = Motion.step(y, fall, target);
        fall = step.fall();
        if (step.y() != y) {
            setPos(x, step.y(), z);
            standing = STAND_TICKS;
        }
    }

    /** One tick of movement towards where it rests: it falls like anyone, and climbs steadily. */
    record Motion(double y, double fall) {
        static Motion step(double y, double fall, double target) {
            if (Math.abs(target - y) < 1e-4) return new Motion(target, 0);
            if (target > y) return new Motion(Math.min(target, y + RISE), 0);
            double speed = Math.min(TERMINAL, (fall + GRAVITY) * DRAG);
            double next = y - speed;
            return next <= target ? new Motion(target, 0) : new Motion(next, speed);
        }
    }

    private void snap(double x, double y, double z) {
        setPos(x, y, z);
        setOldPosAndRot();
    }

    // --- Beds ----------------------------------------------------------------------------

    /** The nearest free bed around its spot, by its head, which is where the game lays a sleeper. */
    private void findBed(ClientLevel level, BlockPos home, int radius, Set<BlockPos> beds) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos at : BlockPos.betweenClosed(home.offset(-radius, -radius, -radius), home.offset(radius, radius, radius))) {
            if (beds.contains(at) || !freeBed(level.getBlockState(at))) continue;
            double dx = at.getX() + 0.5 - spot.x(), dy = at.getY() + 0.5 - spot.y(), dz = at.getZ() + 0.5 - spot.z();
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                best = at.immutable();
                bestDistance = distance;
            }
        }
        if (best == null) return;
        bed = best;
        beds.add(best);
        setSleepingPos(best);
    }

    private void leaveBed(Set<BlockPos> beds) {
        if (bed != null) beds.remove(bed);
        bed = null;
        clearSleepingPos();
    }

    /** The head of a bed nobody really sleeps in. */
    private static boolean freeBed(BlockState state) {
        return state.getBlock() instanceof BedBlock
                && state.getValue(BedBlock.PART) == BedPart.HEAD
                && !state.getValue(BedBlock.OCCUPIED);
    }

    // --- Ground --------------------------------------------------------------------------

    private static AABB body(double x, double y, double z, double height) {
        return new AABB(x - 0.3, y + 0.05, z - 0.3, x + 0.3, y + height, z + 0.3);
    }

    /** Room for it there: no block in its body. */
    private static boolean fits(ClientLevel level, double x, double y, double z, double height) {
        return level.noCollision(body(x, y, z, height));
    }

    /** Something to rest on: a block right under it, or water or lava to float on. */
    private static boolean supported(ClientLevel level, double x, double y, double z) {
        if (!level.noCollision(new AABB(x - 0.3, y - 0.1, z - 0.3, x + 0.3, y + 0.01, z + 0.3))) return true;
        return !level.getFluidState(BlockPos.containing(x, y + 0.05, z)).isEmpty()
                || !level.getFluidState(BlockPos.containing(x, y - 0.1, z)).isEmpty();
    }

    /** Nothing in the way between where it is and where it goes. */
    private static boolean clear(ClientLevel level, double x, double y, double target, double z, double height) {
        return level.noCollision(body(x, Math.min(y, target), z, height + Math.abs(target - y)));
    }

    /**
     * The top of the highest block under its feet, between {@code bottom} and {@code y};
     * NaN if there is none.
     */
    private static double ground(ClientLevel level, double x, double y, double z, double bottom) {
        double top = Double.NaN;
        for (VoxelShape shape : level.getBlockCollisions(null, new AABB(x - 0.3, bottom, z - 0.3, x + 0.3, y, z + 0.3))) {
            double max = shape.max(Direction.Axis.Y);
            if (max <= y + 1e-4 && !(max <= top)) top = max;
        }
        return top;
    }

    /** The first thing under it to land on, a block or the surface of water; where it is if there is none. */
    private static double floorBelow(ClientLevel level, double x, double y, double z) {
        double floor = ground(level, x, y, z, level.getMinY());
        double lowest = Double.isNaN(floor) ? level.getMinY() : floor;
        for (int by = BlockPos.containing(x, y, z).getY(); by >= Math.floor(lowest); by--) {
            BlockPos at = BlockPos.containing(x, by, z);
            FluidState fluid = level.getFluidState(at);
            if (fluid.isEmpty()) continue;
            double surface = by + fluid.getHeight(level, at);
            if (surface <= y + 1e-4) return Double.isNaN(floor) ? surface : Math.max(floor, surface);
        }
        return Double.isNaN(floor) ? y : floor;
    }

    /** The first place above where it fits, standing on what is there; where it is if there is none. */
    private static double freeAbove(ClientLevel level, double x, double y, double z, double height) {
        int top = level.getMinY() + level.getHeight();
        for (int by = (int) Math.floor(y) + 1; by < top; by++) {
            if (!fits(level, x, by, z, height)) continue;
            double landed = ground(level, x, by, z, by - 1);
            return !Double.isNaN(landed) && fits(level, x, landed, z, height) ? landed : by;
        }
        return y;
    }
}
