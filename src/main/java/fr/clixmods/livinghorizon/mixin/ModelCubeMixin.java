package fr.clixmods.livinghorizon.mixin;

//? if >=1.21.9 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.clixmods.livinghorizon.render.LightModels;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a cube of one of the mod's lightest figures as crossed planes instead of a box
 * ({@link LightModels#crossing()}): its four side faces moved to the middle of the cube, the
 * top and bottom left out.
 */
@Mixin(ModelPart.Cube.class)
abstract class ModelCubeMixin {
    @Shadow
    @Final
    public ModelPart.Polygon[] polygons;
    @Shadow
    @Final
    public float minX, minZ, maxX, maxZ;

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void livinghorizon$crossed(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color,
                                       CallbackInfo ci) {
        if (!LightModels.crossing()) return;
        LightModels.crossed(polygons, (minX + maxX) * 0.5f, (minZ + maxZ) * 0.5f, pose, consumer, light, overlay, color);
        ci.cancel();
    }
}
//?}
