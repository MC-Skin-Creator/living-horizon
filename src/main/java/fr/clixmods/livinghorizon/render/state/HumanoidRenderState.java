package fr.clixmods.livinghorizon.render.state;

//? if <1.21.2 {
/*import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/^* A player's figure before 1.21.2, which may be drawn seated. ^/
public class HumanoidRenderState extends LivingEntityRenderState {
    /^* Drawn seated, legs out in front, whether or not it rides anything. ^/
    public boolean isPassenger;

    HumanoidRenderState(LivingEntity entity, float partialTick) {
        super(entity, partialTick);
    }

    /^* The figure drawn seated right now, which LivingEntityRendererMixin tells the model. ^/
    private static @Nullable LivingEntity seated;

    public static boolean seated(LivingEntity entity) {
        return entity == seated;
    }

    @Override
    public void apply() {
        super.apply();
        if (isPassenger) seated = (LivingEntity) entity;
    }

    @Override
    public void restore() {
        seated = null;
        super.restore();
    }
}
*///?}
