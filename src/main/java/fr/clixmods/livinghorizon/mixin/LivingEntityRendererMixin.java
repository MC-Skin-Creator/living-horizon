package fr.clixmods.livinghorizon.mixin;

//? if <1.21.2 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.clixmods.livinghorizon.render.state.EntityRenderState;
import fr.clixmods.livinghorizon.render.state.HumanoidRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * Draws a figure seated, before 1.21.2: the model only sits when the entity rides
 * something, where later states carry the pose themselves. A distant player sitting where
 * it logged off, or drawn on a mount it does not really ride, is taken for a passenger.
 * Also leaves out the name of a figure the mod draws without one, which the game would
 * show by its own rules.
 ^/
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @ModifyExpressionValue(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isPassenger()Z"))
    private boolean livinghorizon$seated(boolean passenger, LivingEntity entity, float yaw, float partialTick,
                                         PoseStack pose, MultiBufferSource buffers, int light) {
        return passenger || HumanoidRenderState.seated(entity);
    }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void livinghorizon$nameless(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (EntityRenderState.nameless(entity)) cir.setReturnValue(false);
    }
}
*///?}
