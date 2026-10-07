package fr.clixmods.livinghorizon.track;

import net.minecraft.world.entity.Entity;
//? if >=1.21.2
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The entities the mod makes to draw what is not loaded (distant players, remembered mobs,
 * birds, the saucer's cow) are never added to a level. Up to 26.1 every entity numbered
 * itself when it was made; from 26.2 only the level numbers it, and drawing one without a
 * number fails. Each gets its own, counting down from far below the numbers the server
 * hands out, so that none is ever taken for a real entity.
 */
public final class Puppets {
    private static int next = -1_000_000;

    private Puppets() {
    }

    /** The entity, numbered if the game no longer does it. Render thread only. */
    public static <T extends Entity> @Nullable T numbered(@Nullable T entity) {
        //? if >=26.2 {
        /*if (entity != null) entity.setId(next--);
        *///?}
        return entity;
    }

    /** A new entity of this type, as if loaded from a save, numbered. Render thread only. */
    public static <T extends Entity> @Nullable T create(EntityType<T> type, Level level) {
        //? if >=1.21.2 {
        return numbered(type.create(level, EntitySpawnReason.LOAD));
        //?} else
        /*return numbered(type.create(level));*/
    }

    /**
     * Moves the legs as if the entity had walked this much; {@code scale} speeds a baby's
     * up, which the renderer itself did before 1.21.2.
     */
    public static void walk(LivingEntity entity, float speed, float multiplier, float scale) {
        //? if >=1.21.2 {
        entity.walkAnimation.update(speed, multiplier, scale);
        //?} else
        /*entity.walkAnimation.update(speed, multiplier);*/
    }
}
