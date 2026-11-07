package fr.clixmods.livinghorizon.render.lod;

import org.jspecify.annotations.Nullable;

/**
 * Slots on every entity for what the levels of detail remember of the figure drawn from it,
 * given by EntityMixin: read for each figure every frame, so no map is looked up for it.
 */
public interface LodHolder {
    @Nullable Object livinghorizon$lod();

    void livinghorizon$lod(@Nullable Object record);

    /** The quads its figure cost the last time it was drawn as a full model, 0 while unknown. */
    int livinghorizon$fullQuads();

    void livinghorizon$fullQuads(int quads);
}
