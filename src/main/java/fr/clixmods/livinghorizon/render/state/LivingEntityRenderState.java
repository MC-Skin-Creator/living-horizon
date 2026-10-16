package fr.clixmods.livinghorizon.render.state;

//? if <1.21.2 {
/*import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.jspecify.annotations.Nullable;

/^*
 * A living figure's head, pose and water, before 1.21.2. A field left as it is made means
 * "as the entity is": NaN for an angle, null for the pose.
 ^/
public class LivingEntityRenderState extends EntityRenderState {
    /^* The way the body faces, in the game's yaw degrees. ^/
    public float bodyRot = Float.NaN;
    /^* The head turned from the body; 0 looks straight ahead. ^/
    public float yRot = Float.NaN;
    public float xRot = Float.NaN;
    /^* How far through its stride the figure is, and how fast it walks; 0 stands still. ^/
    public float walkAnimationPos = Float.NaN;
    public float walkAnimationSpeed = Float.NaN;
    /^* Ticks into its death, which tips a mob over. ^/
    public float deathTime = Float.NaN;
    /^* The red of a mob just hurt or dying; false takes it off, and the fall of its death with it. ^/
    public boolean hasRedOverlay;
    public @Nullable Pose pose;
    /^* Read by nothing: a figure the mod lays down is never in a bed. ^/
    public @Nullable Direction bedOrientation;
    public boolean isInWater;

    private float bodyYaw, bodyYawO, headRot, headRotO, pitch, pitchO, walkPos, walkSpeed, walkSpeedO;
    private int dying, hurt;
    private @Nullable Pose oldPose;
    private boolean wasInWater;

    LivingEntityRenderState(LivingEntity entity, float partialTick) {
        super(entity, partialTick);
        hasRedOverlay = entity.hurtTime > 0 || entity.deathTime > 0;
    }

    @Override
    public void apply() {
        super.apply();
        LivingEntity living = (LivingEntity) entity;
        bodyYaw = living.yBodyRot;
        bodyYawO = living.yBodyRotO;
        headRot = living.yHeadRot;
        headRotO = living.yHeadRotO;
        pitch = living.getXRot();
        pitchO = living.xRotO;
        walkPos = living.walkAnimation.position;
        walkSpeed = living.walkAnimation.speed;
        walkSpeedO = living.walkAnimation.speedOld;
        dying = living.deathTime;
        hurt = living.hurtTime;
        oldPose = living.getPose();
        wasInWater = living.wasTouchingWater;
        if (!Float.isNaN(bodyRot)) {
            living.yBodyRot = bodyRot;
            living.yBodyRotO = bodyRot;
        }
        if (!Float.isNaN(walkAnimationPos)) living.walkAnimation.position = walkAnimationPos;
        if (!Float.isNaN(walkAnimationSpeed)) {
            living.walkAnimation.speed = walkAnimationSpeed;
            living.walkAnimation.speedOld = walkAnimationSpeed;
        }
        if (!Float.isNaN(deathTime)) living.deathTime = (int) deathTime;
        if (!hasRedOverlay) {
            living.hurtTime = 0;
            living.deathTime = 0;
        }
        if (!Float.isNaN(yRot)) {
            living.yHeadRot = living.yBodyRot + yRot;
            living.yHeadRotO = living.yBodyRotO + yRot;
        }
        if (!Float.isNaN(xRot)) {
            living.setXRot(xRot);
            living.xRotO = xRot;
        }
        if (pose != null) living.setPose(pose);
        if (isInWater) living.wasTouchingWater = true;
    }

    @Override
    public void restore() {
        LivingEntity living = (LivingEntity) entity;
        living.yBodyRot = bodyYaw;
        living.yBodyRotO = bodyYawO;
        living.walkAnimation.position = walkPos;
        living.walkAnimation.speed = walkSpeed;
        living.walkAnimation.speedOld = walkSpeedO;
        living.deathTime = dying;
        living.hurtTime = hurt;
        living.yHeadRot = headRot;
        living.yHeadRotO = headRotO;
        living.setXRot(pitch);
        living.xRotO = pitchO;
        if (oldPose != null && living.getPose() != oldPose) living.setPose(oldPose);
        living.wasTouchingWater = wasInWater;
        super.restore();
    }
}
*///?}
