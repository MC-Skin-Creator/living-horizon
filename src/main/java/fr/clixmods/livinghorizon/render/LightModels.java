package fr.clixmods.livinghorizon.render;

import fr.clixmods.livinghorizon.FarConfig;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.LlamaRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.9 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?} else {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?}
//? if >=1.21.5 {
import net.minecraft.client.renderer.entity.state.EquineRenderState;
//?} else {
/*import net.minecraft.client.renderer.entity.state.HorseRenderState;
*///?}
//?}

/**
 * The level between a whole model and its flat picture: a figure drawn as a model, yet smaller
 * on screen than {@link FarConfig#lightModelPixels}, loses what cannot be seen at that size
 * before it is submitted - the items in its hands and on its head, its armour, cape, elytra,
 * parrots, a horse's saddle and armour, a llama's carpet and chest, a wolf's armour, and a
 * player's outer skin layer. Each of those is a model or a layer of its own, often as big as
 * the figure itself: without them a player is a third of the geometry. The state is the
 * mod's own copy, made for this frame: nothing of the game's is changed.
 *
 * <p>Before 1.21.2 the figures are drawn through their entity's renderer, with what the
 * entity holds: nothing can be left out there, and the setting does nothing.
 */
public final class LightModels {
    private static int lightened, lastLightened;

    private LightModels() {
    }

    /** A new frame: the count of the last one is kept for the debug tools. */
    public static void beginFrame() {
        lastLightened = lightened;
        lightened = 0;
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

    /** Takes out of {@code state} what a light model leaves out. */
    public static void lighten(EntityRenderState state) {
        lightened++;
        //? if >=1.21.2 {
        if (state instanceof LivingEntityRenderState living) {
            //? if >=1.21.4 {
            living.headItem.clear();
            //?} else {
            /*living.headItem = ItemStack.EMPTY;
            *///?}
            living.wornHeadType = null;
            living.wornHeadProfile = null;
        }
        if (state instanceof ArmedEntityRenderState armed) {
            //? if >=1.21.9 {
            armed.rightHandItemState.clear();
            armed.leftHandItemState.clear();
            armed.rightHandItemStack = ItemStack.EMPTY;
            armed.leftHandItemStack = ItemStack.EMPTY;
            //?} elif >=1.21.4 {
            /*armed.rightHandItem.clear();
            armed.leftHandItem.clear();
            *///?} else {
            /*armed.rightHandItem = ItemStack.EMPTY;
            armed.leftHandItem = ItemStack.EMPTY;
            *///?}
        }
        if (state instanceof HumanoidRenderState humanoid) {
            humanoid.headEquipment = ItemStack.EMPTY;
            humanoid.chestEquipment = ItemStack.EMPTY;
            humanoid.legsEquipment = ItemStack.EMPTY;
            humanoid.feetEquipment = ItemStack.EMPTY;
        }
        //? if >=1.21.9 {
        if (state instanceof AvatarRenderState player) {
        //?} else {
        /*if (state instanceof PlayerRenderState player) {
        *///?}
            player.showHat = false;
            player.showJacket = false;
            player.showLeftPants = false;
            player.showRightPants = false;
            player.showLeftSleeve = false;
            player.showRightSleeve = false;
            player.showCape = false;
            player.parrotOnLeftShoulder = null;
            player.parrotOnRightShoulder = null;
            player.arrowCount = 0;
            player.stingerCount = 0;
            //? if >=1.21.4 {
            player.heldOnHead.clear();
            //?} else {
            /*player.heldOnHead = ItemStack.EMPTY;
            *///?}
        }
        //? if >=1.21.5 {
        if (state instanceof EquineRenderState horse) {
            horse.saddle = ItemStack.EMPTY;
            horse.bodyArmorItem = ItemStack.EMPTY;
        }
        if (state instanceof LlamaRenderState llama) {
            llama.bodyItem = ItemStack.EMPTY;
            llama.hasChest = false;
        }
        //?} else {
        /*if (state instanceof HorseRenderState horse) horse.bodyArmorItem = ItemStack.EMPTY;
        if (state instanceof LlamaRenderState llama) {
            llama.swag = null;
            llama.hasChest = false;
        }
        *///?}
        if (state instanceof WolfRenderState wolf) wolf.bodyArmorItem = ItemStack.EMPTY;
        //?}
    }
}
