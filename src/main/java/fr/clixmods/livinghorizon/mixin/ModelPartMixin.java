package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.render.PartTree;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
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
 * Counts the faces of every part of a model as it is drawn, for {@link PolygonStats}, and
 * lets the mod read a part's cubes and children ({@link PartTree}).
 */
@Mixin(ModelPart.class)
abstract class ModelPartMixin implements PartTree {
    @Shadow
    @Final
    private List<ModelPart.Cube> cubes;

    @Shadow
    @Final
    private Map<String, ModelPart> children;

    @Inject(method = "compile", at = @At("HEAD"))
    // The colour was four floats before 1.21: the arguments are left out, nothing reads them.
    private void livinghorizon$count(CallbackInfo ci) {
        if (!PolygonStats.counting()) return;
        int polygons = 0;
        for (ModelPart.Cube cube : cubes) polygons += cube.polygons.length;
        PolygonStats.model(polygons);
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
