package fr.clixmods.livinghorizon.render.state;

//? if <1.21.2 {
/*import net.minecraft.world.entity.vehicle.Boat;

/^*
 * A boat's figure before 1.21.2. Taken for under water, the game leaves out the patch that
 * hides the water inside its hull, which drawn with our figures hides the water around it.
 ^/
public class BoatRenderState extends EntityRenderState {
    public boolean isUnderWater;
    private boolean touching, eye;

    BoatRenderState(Boat boat, float partialTick) {
        super(boat, partialTick);
        isUnderWater = boat.isUnderWater();
    }

    @Override
    public void apply() {
        super.apply();
        touching = entity.wasTouchingWater;
        eye = entity.wasEyeInWater;
        if (isUnderWater) {
            entity.wasTouchingWater = true;
            entity.wasEyeInWater = true;
        }
    }

    @Override
    public void restore() {
        entity.wasTouchingWater = touching;
        entity.wasEyeInWater = eye;
        super.restore();
    }
}
*///?}
