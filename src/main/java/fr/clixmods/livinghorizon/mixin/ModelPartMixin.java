package fr.clixmods.livinghorizon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.clixmods.livinghorizon.render.PartTree;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.render.lod.LodRenderer;
import fr.clixmods.livinghorizon.render.lod.LodSet;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/**
 * The parts of the mod's figures: their faces counted for {@link PolygonStats}, and, for a
 * figure drawn at a simpler level of detail, only the cubes that level keeps, with the merged
 * boxes anchored to the part queued for {@link LodRenderer}. The game's own entities are left
 * as they are. Also lets the mod read a part's cubes and children ({@link PartTree}).
 */
@Mixin(ModelPart.class)
abstract class ModelPartMixin implements PartTree {
    @Shadow
    @Final
    private List<ModelPart.Cube> cubes;

    @Shadow
    @Final
    private Map<String, ModelPart> children;

    // Every way of drawing a part ends in this one: the first call of a model is its root.
    //? if >=1.21 {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("HEAD"))
    //?} else
    /*@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V", at = @At("HEAD"))*/
    private void livinghorizon$enter(CallbackInfo ci) {
        if (PolygonStats.counting()) LodRenderer.enterPart((ModelPart) (Object) this);
    }

    //? if >=1.21 {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("RETURN"))
    //?} else
    /*@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V", at = @At("RETURN"))*/
    private void livinghorizon$leave(CallbackInfo ci) {
        if (PolygonStats.counting()) LodRenderer.leavePart();
    }

    //? if >=1.21 {
    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void livinghorizon$compile(PoseStack.Pose pose, VertexConsumer out, int light, int overlay, int color,
                                       CallbackInfo ci) {
        if (!PolygonStats.counting()) return;
        LodSet.PartDraw draw = level(ci);
        if (draw == null) return;
        int tinted = LodRenderer.tint(color);
        for (int k : draw.kept()) cubes.get(k).compile(pose, out, light, overlay, tinted);
        LodRenderer.queue(draw, pose, light, overlay, tinted);
    }
    //?} else {
    /*@Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void livinghorizon$compile(PoseStack.Pose pose, VertexConsumer out, int light, int overlay, float red,
                                       float green, float blue, float alpha, CallbackInfo ci) {
        if (!PolygonStats.counting()) return;
        LodSet.PartDraw draw = level(ci);
        if (draw == null) return;
        int tinted = LodRenderer.tint((int) (alpha * 255) << 24 | (int) (red * 255) << 16 | (int) (green * 255) << 8
                | (int) (blue * 255));
        float r = ((tinted >> 16) & 0xFF) / 255f, g = ((tinted >> 8) & 0xFF) / 255f, b = (tinted & 0xFF) / 255f;
        for (int k : draw.kept()) cubes.get(k).compile(pose, out, light, overlay, r, g, b, alpha);
        LodRenderer.queue(draw, pose, light, overlay, tinted);
    }
    *///?}

    /**
     * Counts the part's faces; what it draws at its figure's level, the game's own drawing
     * cancelled, or null for its cubes as the game draws them.
     */
    private LodSet.PartDraw level(CallbackInfo ci) {
        int polygons = 0;
        for (ModelPart.Cube cube : cubes) polygons += cube.polygons.length;
        PolygonStats.full(polygons);
        LodSet.PartDraw draw = LodRenderer.part((ModelPart) (Object) this);
        if (draw == null) {
            PolygonStats.model(polygons);
            return null;
        }
        ci.cancel();
        PolygonStats.model(draw.keptQuads());
        return draw;
    }

    @Override
    public List<ModelPart.Cube> livinghorizon$cubes() {
        return cubes;
    }

    @Override
    public Map<String, ModelPart> livinghorizon$children() {
        return children;
    }
}
