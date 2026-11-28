package fr.clixmods.livinghorizon.compat;

import fr.clixmods.livinghorizon.LivingHorizonClient;
import fr.clixmods.livinghorizon.platform.Platform;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * Useless Reptile's dragons are animated with Biscuit Roll (from 1.21.11), whose controllers play
 * what the dragon's {@code tickAnimations} chose: walk, idle, blink. A copy never ticks, so it
 * played nothing and stood in its model's bind pose, a T-pose. It is given the choice here, every
 * tick its animation moves it ({@code MobMotion}):
 * <ul>
 * <li>0.12 and 0.14 (1.21.11, 26.2) choose on the client, from {@code isMoving()}, which reads the
 * motion the copy is given: its own {@code tickAnimations} is called;</li>
 * <li>0.15 (26.1, 26.3) chooses on the server only, and the client plays what it is sent: the copy
 * is told whether it moves, and its main controller plays walk or idle, its blink one blink.</li>
 * </ul>
 * Reached by reflection, so that the mod loads without it; any failure turns this off.
 */
public final class UselessReptile {
    private static @Nullable Class<?> dragon;
    private static @Nullable Method controller, setMoving, play, playing;
    private static @Nullable Object main, blink;
    private static boolean looked, broken;
    /** Each kind of dragon's own {@code tickAnimations}, looked up once. */
    private static final ClassValue<Method> TICK = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("tickAnimations");
            } catch (NoSuchMethodException error) {
                throw new IllegalStateException(type.getName() + " has no tickAnimations", error);
            }
        }
    };

    private UselessReptile() {
    }

    /** One tick of a copy's animation; {@code moving} as it walks. Does nothing to any other mob. */
    public static void animate(Entity copy, boolean moving) {
        if (!looked) find();
        Class<?> type = dragon;
        if (broken || type == null || !type.isInstance(copy)) return;
        try {
            if (controller == null) {
                // It chooses on the client: from the motion the copy has.
                TICK.get(copy.getClass()).invoke(copy);
                return;
            }
            setMoving.invoke(copy, moving);
            play.invoke(controller.invoke(copy, main), moving ? "walk" : "idle");
            Object blinking = controller.invoke(copy, blink);
            if (((Collection<?>) playing.invoke(blinking)).isEmpty()) play.invoke(blinking, "blink");
        } catch (ReflectiveOperationException | RuntimeException error) {
            LivingHorizonClient.LOGGER.warn("Useless Reptile found, but not the version this mod knows", error);
            broken = true;
        }
    }

    private static void find() {
        looked = true;
        if (!Platform.isModLoaded("uselessreptile")) return;
        try {
            // Biscuit Roll's dragons only; before 1.21.11 they are GeckoLib's, which needs nothing.
            Class<?> animated = Class.forName("nordmods.biscuit_roll.common.animation.BRAnimatedObject");
            Class<?> type = Class.forName("nordmods.uselessreptile.common.entity.base.URDragonEntity");
            if (!animated.isAssignableFrom(type)) return;
            Class<?> brController = Class.forName("nordmods.biscuit_roll.common.animation.controller.BRAnimationController");
            try {
                Class<?> controllers = Class.forName("nordmods.uselessreptile.common.entity.base.URDragonEntity$AnimationController");
                controller = type.getMethod("getAnimationController", controllers);
                setMoving = type.getMethod("setMoving", boolean.class);
                play = brController.getMethod("playAnimation", String.class);
                playing = brController.getMethod("getPlayingAnimations");
                main = enumConstant(controllers, "MAIN");
                blink = enumConstant(controllers, "BLINK");
            } catch (ClassNotFoundException | NoSuchMethodException clientChooses) {
                // 0.12 and 0.14: no controller by name; the copy's own tickAnimations chooses.
                controller = null;
            }
            dragon = type;
        } catch (ClassNotFoundException notBiscuitRoll) {
            // An older Useless Reptile, drawn with GeckoLib.
        } catch (RuntimeException | LinkageError error) {
            LivingHorizonClient.LOGGER.warn("Useless Reptile found, but not the version this mod knows", error);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }
}
