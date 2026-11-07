package fr.clixmods.livinghorizon.render;

import net.minecraft.client.model.geom.ModelPart;

import java.util.List;
import java.util.Map;

/** What a model part holds, which the game keeps to itself: given to every part by ModelPartMixin. */
public interface PartTree {
    List<ModelPart.Cube> livinghorizon$cubes();

    Map<String, ModelPart> livinghorizon$children();
}
