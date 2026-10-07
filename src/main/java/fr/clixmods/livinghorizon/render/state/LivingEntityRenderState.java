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
    /^* The head turned from the body; 0 looks straight ahead. ^/
    public float yRot = Float.NaN;
    public float xRot = Float.NaN;
    /^* Read by nothing: a figure the mod changes this way never walks. ^/
    public float walkAnimationSpeed = Float.NaN;
    public @Nullable Pose pose;
    /^* Read by nothing: a figure the mod lays down is never in a bed. ^/
    public @Nullable Direction bedOrientation;
    public boolean isInWater;

    private float headRot, headRotO, pitch, pitchO;
    private @Nullable Pose oldPose;
    private boolean wasInWater;

    LivingEntityRenderState(LivingEntity entity, float partialTick) {
        super(entity, partialTick);
    }

    @Override
    public void apply() {
        LivingEntity living = (LivingEntity) entity;
        headRot = living.yHeadRot;
        headRotO = living.yHeadRotO;
        pitch = living.getXRot();
        pitchO = living.xRotO;
        oldPose = living.getPose();
        wasInWater = living.wasTouchingWater;
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
        living.yHeadRot = headRot;
        living.yHeadRotO = headRotO;
        living.setXRot(pitch);
        living.xRotO = pitchO;
        if (oldPose != null && living.getPose() != oldPose) living.setPose(oldPose);
        living.wasTouchingWater = wasInWater;
    }
}
*///?}
