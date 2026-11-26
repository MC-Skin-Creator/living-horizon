package fr.clixmods.livinghorizon.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The box the game culls an entity by, which may be far larger than its hitbox: a modded giant
 * whose model reaches past it says so through its renderer's {@code getBoundingBoxForCulling}
 * (the entity's before 1.21.2), and one never to be culled through {@code affectedByCulling}
 * (the entity's {@code noCulling} before 1.21.2). Taken by the view, size and terrain tests of
 * the figures drawn far away, which read the hitbox alone and left such giants out (issue #351).
 */
public final class CullBox {
    private CullBox() {
    }

    /**
     * Its culling box this frame; null when the game never culls it, so that its true size is
     * unknown and it is never left out for its place or size. The hitbox when its renderer fails.
     */
    public static @Nullable AABB of(Entity entity, float partialTick) {
        AABB box;
        try {
            //? if >=26.3 {
            /*var renderer = renderer(entity);
            if (!renderer.affectedByCulling(entity)) return null;
            box = renderer.getBoundingBoxForCulling(entity, partialTick);
            *///?} elif >=1.21.2 {
            var renderer = renderer(entity);
            if (!renderer.affectedByCulling(entity)) return null;
            box = renderer.getBoundingBoxForCulling(entity);
            //?} else {
            /*if (entity.noCulling) return null;
            box = entity.getBoundingBoxForCulling();
            *///?}
        } catch (RuntimeException | LinkageError error) {
            // Another mod's renderer, asked about an entity it never draws itself: the hitbox.
            return entity.getBoundingBox();
        }
        // As the game does with a box it cannot use: two blocks round the entity.
        if (box == null || box.hasNaN() || box.getSize() == 0) {
            double x = entity.getX(), y = entity.getY(), z = entity.getZ();
            return new AABB(x - 2, y - 2, z - 2, x + 2, y + 2, z + 2);
        }
        return box;
    }

    //? if >=1.21.2 {
    @SuppressWarnings("unchecked")
    private static net.minecraft.client.renderer.entity.EntityRenderer<Entity, ?> renderer(Entity entity) {
        return (net.minecraft.client.renderer.entity.EntityRenderer<Entity, ?>)
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
    }
    //?}
}
