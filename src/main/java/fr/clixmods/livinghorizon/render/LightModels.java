package fr.clixmods.livinghorizon.render;

import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
//? if >=1.21.2 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
//? if >=1.21.9 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?} else {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?}
import java.util.IdentityHashMap;
import java.util.Map;
//?}

/**
 * The level between a whole model and its flat picture: a figure drawn as a model, yet small
 * on screen, keeps its look and loses what costs without showing. Below
 * {@link FarConfig#lightModelPixels}, its gear stays but is drawn plain: no enchantment
 * shimmer (a second pass over every piece), no armour trim (another), no arrows or stingers
 * stuck in it, and no skin layer under a piece of armour that covers it. Below half that size
 * the items in its hands and on its head, its parrots and its outer skin layer go too: a sword
 * is a hundred faces for two pixels there. Armour, capes, elytra, saddles and carpets always
 * stay: they are what a figure looks like from afar.
 *
 * <p>The state is the mod's own copy, made for this frame: nothing of the game's, nor of the
 * puppet's own items, is changed. Before 1.21.2 the figures are drawn through their entity's
 * renderer, with what the entity holds: nothing can be left out there, and the setting does nothing.
 */
public final class LightModels {
    private static int lightened, lastLightened;

    // --- Crossed planes (a prototype, off unless a scenario turns it on) ---------------------

    /**
     * Whether the lightest figures' cubes are drawn as crossed planes: each cube's four side
     * faces moved to its middle, two planes crossing at right angles, the top and bottom left
     * out. Four faces instead of six, the same texture, the same animation; seen from a corner
     * the box shows as a cross. Set by the {@code crossed} scenario to look at it.
     */
    public static boolean crossedPlanes;
    /** The states of this frame drawn as crossed planes. */
    private static final java.util.Set<EntityRenderState> CROSSED =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static boolean crossing;

    /** The model of this state (any object the game hands the draw) is about to be turned into faces. */
    public static void drawing(Object state) {
        crossing = crossedPlanes && CROSSED.contains(state);
    }

    public static void drawn() {
        crossing = false;
    }

    /** Whether the cubes being drawn right now go as crossed planes. */
    public static boolean crossing() {
        return crossing;
    }

    /** How many faces a cube keeps as crossed planes: its sides, never its top or bottom. */
    public static int crossedFaces(Object[] polygons) {
        int faces = 0;
        //? if >=1.21.9 {
        for (Object polygon : polygons) {
            if (Math.abs(((net.minecraft.client.model.geom.ModelPart.Polygon) polygon).normal().y()) < 0.5f) faces++;
        }
        //?}
        return faces;
    }

    //? if >=1.21.9 {
    /**
     * Writes a cube's side faces as two planes crossing at its middle ({@code cx}, {@code cz}
     * in model units): the faces along Z keep their X and Y and move to {@code cz}, those
     * along X move to {@code cx}. Each keeps its texture, its normal and its winding, so the
     * one facing the camera is drawn and the one behind is culled, as on the box.
     */
    public static void crossed(net.minecraft.client.model.geom.ModelPart.Polygon[] polygons, float cx, float cz,
                               com.mojang.blaze3d.vertex.PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer out,
                               int light, int overlay, int color) {
        for (net.minecraft.client.model.geom.ModelPart.Polygon polygon : polygons) {
            org.joml.Vector3fc normal = polygon.normal();
            if (Math.abs(normal.y()) > 0.5f) continue;
            boolean alongZ = Math.abs(normal.z()) > 0.5f;
            for (net.minecraft.client.model.geom.ModelPart.Vertex vertex : polygon.vertices()) {
                float x = alongZ ? vertex.x() : cx, z = alongZ ? cz : vertex.z();
                out.addVertex(pose, x / 16f, vertex.y() / 16f, z / 16f)
                        .setColor(color)
                        .setUv(vertex.u(), vertex.v())
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, normal.x(), normal.y(), normal.z());
            }
        }
    }
    //?}

    private LightModels() {
    }

    /** A new frame: the count of the last one is kept for the debug tools. */
    public static void beginFrame() {
        lastLightened = lightened;
        lightened = 0;
        CROSSED.clear();
        //? if >=1.21.2
        if (++frame % 600 == 0 || PLAIN.size() > 1024) PLAIN.clear();
    }

    /** Figures drawn as light models last frame. */
    public static int count() {
        return lastLightened;
    }

    /** Whether a figure drawn {@code pixels} tall on screen is drawn light. */
    public static boolean wanted(FarConfig config, double pixels) {
        return SUPPORTED && config.lightModelPixels > 0 && pixels < config.lightModelPixels;
    }

    /** Whether this version of the game lets the figures be lightened at all. */
    //? if >=1.21.2 {
    public static final boolean SUPPORTED = true;
    //?} else {
    /*public static final boolean SUPPORTED = false;
    *///?}

    /**
     * Lightens {@code state}, the copy of {@code entity} for this frame, drawn {@code pixels}
     * tall under the setting's size; {@code entity} may be null, and the hand items then keep
     * their shimmer.
     */
    public static void lighten(EntityRenderState state, @Nullable Entity entity, FarConfig config, double pixels) {
        lightened++;
        //? if >=1.21.2 {
        boolean tiny = pixels < config.lightModelPixels * 0.5;
        if (tiny && crossedPlanes) CROSSED.add(state);
        LivingEntity living = entity instanceof LivingEntity alive ? alive : null;
        if (state instanceof LivingEntityRenderState livingState) {
            //? if >=1.21.4 {
            if (tiny) livingState.headItem.clear();
            else if (living != null && !livingState.headItem.isEmpty()) {
                resolve(livingState.headItem, living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD),
                        ItemDisplayContext.HEAD, false, living);
            }
            //?} else {
            /*livingState.headItem = tiny ? ItemStack.EMPTY : plain(livingState.headItem);
            *///?}
        }
        if (state instanceof ArmedEntityRenderState armed) {
            //? if >=1.21.9 {
            if (tiny) {
                armed.rightHandItemState.clear();
                armed.leftHandItemState.clear();
                armed.rightHandItemStack = ItemStack.EMPTY;
                armed.leftHandItemStack = ItemStack.EMPTY;
            } else if (living != null) {
                resolve(armed.rightHandItemState, armed.rightHandItemStack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, living);
                resolve(armed.leftHandItemState, armed.leftHandItemStack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, living);
            }
            //?} elif >=1.21.4 {
            /*if (tiny) {
                armed.rightHandItem.clear();
                armed.leftHandItem.clear();
            } else if (living != null) {
                boolean rightMain = armed.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT;
                resolve(armed.rightHandItem, rightMain ? living.getMainHandItem() : living.getOffhandItem(),
                        ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, living);
                resolve(armed.leftHandItem, rightMain ? living.getOffhandItem() : living.getMainHandItem(),
                        ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, living);
            }
            *///?} else {
            /*armed.rightHandItem = tiny ? ItemStack.EMPTY : plain(armed.rightHandItem);
            armed.leftHandItem = tiny ? ItemStack.EMPTY : plain(armed.leftHandItem);
            *///?}
        }
        if (state instanceof HumanoidRenderState humanoid) {
            humanoid.headEquipment = plain(humanoid.headEquipment);
            humanoid.chestEquipment = plain(humanoid.chestEquipment);
            humanoid.legsEquipment = plain(humanoid.legsEquipment);
            humanoid.feetEquipment = plain(humanoid.feetEquipment);
        }
        //? if >=1.21.9 {
        if (state instanceof AvatarRenderState player) {
        //?} else {
        /*if (state instanceof PlayerRenderState player) {
        *///?}
            player.arrowCount = 0;
            player.stingerCount = 0;
            // The skin layer under a piece of armour never shows: the armour is drawn over it, bigger.
            if (tiny || covers(player.headEquipment)) player.showHat = false;
            if (tiny || covers(player.chestEquipment)) {
                player.showJacket = false;
                player.showLeftSleeve = false;
                player.showRightSleeve = false;
            }
            if (tiny || covers(player.legsEquipment)) {
                player.showLeftPants = false;
                player.showRightPants = false;
            }
            if (tiny) {
                player.parrotOnLeftShoulder = null;
                player.parrotOnRightShoulder = null;
                //? if >=1.21.4 {
                player.heldOnHead.clear();
                //?} else {
                /*player.heldOnHead = ItemStack.EMPTY;
                *///?}
            }
        }
        //?}
    }

    //? if >=1.21.2 {
    /** The plain copies of the items seen lately, by the item itself: a puppet keeps its own. */
    private static final Map<ItemStack, ItemStack> PLAIN = new IdentityHashMap<>();
    private static long frame;

    /** The same item without its enchantment shimmer and its trim; the item itself when it has neither. */
    private static ItemStack plain(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasFoil() && !stack.has(DataComponents.TRIM)) return stack;
        ItemStack kept = PLAIN.get(stack);
        if (kept == null) {
            kept = stack.copy();
            kept.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            kept.remove(DataComponents.TRIM);
            PLAIN.put(stack, kept);
        }
        return kept;
    }

    /** Resolves {@code into} again from the plain {@code stack}, where it had a shimmer; left as it is otherwise. */
    private static void resolve(net.minecraft.client.renderer.item.ItemStackRenderState into, ItemStack stack,
                                ItemDisplayContext context, boolean leftHand, LivingEntity living) {
        ItemStack kept = plain(stack);
        if (kept == stack) return;
        //? if >=1.21.9 {
        Minecraft.getInstance().getItemModelResolver().updateForLiving(into, kept, context, living);
        //?} else {
        /*Minecraft.getInstance().getItemModelResolver().updateForLiving(into, kept, context, leftHand, living);
        *///?}
    }

    /**
     * Whether a piece of armour hides the skin layer under it: a worn item with an equipment
     * model of its own, not an elytra (wings) nor a block (a pumpkin, a head).
     */
    private static boolean covers(ItemStack stack) {
        if (stack.isEmpty() || stack.is(Items.ELYTRA) || stack.getItem() instanceof BlockItem) return false;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.assetId().isPresent();
    }
    //?}
}
