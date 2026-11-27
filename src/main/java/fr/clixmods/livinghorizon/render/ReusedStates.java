package fr.clixmods.livinghorizon.render;

//? if >=1.21.2 && <1.21.9 {
/*import fr.clixmods.livinghorizon.compat.GeckoLib;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.util.IdentityHashMap;
import java.util.Map;

/^*
 * Figures of a renderer that makes no state of its own, from 1.21.2 to 1.21.8. GeckoLib's (4 on
 * 1.21.2 to 1.21.4, 5 on 1.21.5 to 1.21.8) return null from {@code createRenderState()}: their
 * only state is the one {@code createRenderState(entity, tick)} fills again for every entity, and
 * GeckoLib 4 draws the entity it was last handed there, whatever state it is given.
 *
 * <p>Each figure gets a state of the same class, filled for it alone ({@link #own}), and its
 * entity is kept with it. Just before the state is drawn, the renderer is handed that entity
 * again, and what the mod changed in the state (body, head, legs: an impostor's view, a resting
 * player's pose) is given to the entity while it is drawn ({@link #hand}). Any renderer made
 * that way is drawn the same; only the light GeckoLib 5 wants in its state is its own
 * ({@link GeckoLib#light}).
 ^/
public final class ReusedStates {
    private ReusedStates() {
    }

    /^* This frame's figures of such renderers, by their own state. ^/
    private static final Map<EntityRenderState, Figure> FIGURES = new IdentityHashMap<>();
    /^* The class of state each such renderer fills, made empty for each figure. ^/
    private static final Map<EntityRenderer<?, ?>, Constructor<?>> MAKERS = new IdentityHashMap<>();

    public static void clear() {
        FIGURES.clear();
    }

    /^*
     * A state for this figure alone, of the class the renderer fills, filled for it. Throws when
     * that class cannot be made empty: the caller leaves the entity out.
     ^/
    @SuppressWarnings("unchecked")
    public static <E extends Entity, S extends EntityRenderState> S own(EntityRenderer<? super E, S> renderer,
                                                                         E entity, float partialTick) {
        Constructor<?> maker = MAKERS.get(renderer);
        if (maker == null) {
            S shared = renderer.createRenderState(entity, partialTick);
            try {
                maker = shared.getClass().getDeclaredConstructor();
                maker.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException error) {
                throw new IllegalStateException(shared.getClass().getName() + " cannot be made for a figure", error);
            }
            MAKERS.put(renderer, maker);
        }
        S state;
        try {
            state = (S) maker.newInstance();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(maker.getDeclaringClass().getName() + " cannot be made for a figure", error);
        }
        renderer.extractRenderState(entity, state, partialTick);
        FIGURES.put(state, new Figure(entity, partialTick, state));
        return state;
    }

    /^* The entity a state of {@link #own} was filled from, null for any other state. ^/
    public static @Nullable Entity entity(EntityRenderState state) {
        Figure figure = FIGURES.get(state);
        return figure == null ? null : figure.entity;
    }

    /^*
     * Hands the renderer this state's entity again, just before it draws the state, with what
     * the mod changed in the state given to the entity. Null for a state not made by {@link #own};
     * otherwise run what it returns once drawn, which puts the entity back as it was.
     ^/
    @SuppressWarnings("unchecked")
    public static @Nullable Runnable hand(EntityRenderer<?, ?> renderer, EntityRenderState state, int light) {
        Figure figure = FIGURES.get(state);
        if (figure == null) return null;
        // GeckoLib 5 reads the light from the state, where only the game's draw puts it.
        GeckoLib.light(state, light);
        Runnable back = figure.pose(state);
        try {
            EntityRenderState shared = ((EntityRenderer<Entity, EntityRenderState>) renderer)
                    .createRenderState(figure.entity, figure.partialTick);
            // GeckoLib 4 draws the name over the figure from the state it reuses.
            if (shared != null && shared != state) shared.nameTag = state.nameTag;
        } catch (RuntimeException | Error error) {
            back.run();
            throw error;
        }
        return back;
    }

    private static final Runnable NOTHING = () -> {
    };

    /^* A figure's entity, and how its state was extracted, to tell what the mod changed since. ^/
    private static final class Figure {
        final Entity entity;
        final float partialTick;
        final float bodyRot, yRot, xRot, walkPos, walkSpeed;

        Figure(Entity entity, float partialTick, EntityRenderState state) {
            this.entity = entity;
            this.partialTick = partialTick;
            if (state instanceof LivingEntityRenderState living) {
                bodyRot = living.bodyRot;
                yRot = living.yRot;
                xRot = living.xRot;
                walkPos = living.walkAnimationPos;
                walkSpeed = living.walkAnimationSpeed;
            } else {
                bodyRot = yRot = xRot = walkPos = walkSpeed = Float.NaN;
            }
        }

        /^*
         * Gives the entity what the mod changed in its state since it was extracted, for a
         * renderer that draws the entity rather than the state; what it returns puts it back.
         ^/
        Runnable pose(EntityRenderState state) {
            if (!(state instanceof LivingEntityRenderState s) || !(entity instanceof LivingEntity living)) return NOTHING;
            boolean turned = s.bodyRot != bodyRot || s.yRot != yRot || s.xRot != xRot;
            boolean walked = s.walkAnimationPos != walkPos || s.walkAnimationSpeed != walkSpeed;
            boolean calm = !s.hasRedOverlay && (living.hurtTime > 0 || living.deathTime > 0);
            boolean wet = s.isInWater && !living.wasTouchingWater;
            if (!turned && !walked && !calm && !wet) return NOTHING;
            float body = living.yBodyRot, bodyO = living.yBodyRotO, head = living.yHeadRot, headO = living.yHeadRotO;
            float pitch = living.getXRot(), pitchO = living.xRotO;
            float position = living.walkAnimation.position, speed = living.walkAnimation.speed;
            float speedO = living.walkAnimation.speedOld;
            int hurt = living.hurtTime, dying = living.deathTime;
            boolean water = living.wasTouchingWater;
            if (turned) {
                living.yBodyRot = living.yBodyRotO = s.bodyRot;
                living.yHeadRot = living.yHeadRotO = s.bodyRot + s.yRot;
                living.setXRot(s.xRot);
                living.xRotO = s.xRot;
            }
            if (walked) {
                // Read back as position - speed * (1 - tick): the legs are where the state has them.
                living.walkAnimation.speed = living.walkAnimation.speedOld = s.walkAnimationSpeed;
                living.walkAnimation.position = s.walkAnimationPos + s.walkAnimationSpeed * (1 - partialTick);
            }
            if (calm) {
                living.hurtTime = 0;
                living.deathTime = 0;
            }
            if (wet) living.wasTouchingWater = true;
            return () -> {
                living.yBodyRot = body;
                living.yBodyRotO = bodyO;
                living.yHeadRot = head;
                living.yHeadRotO = headO;
                living.setXRot(pitch);
                living.xRotO = pitchO;
                living.walkAnimation.position = position;
                living.walkAnimation.speed = speed;
                living.walkAnimation.speedOld = speedO;
                living.hurtTime = hurt;
                living.deathTime = dying;
                living.wasTouchingWater = water;
            };
        }
    }
}
*///?}
