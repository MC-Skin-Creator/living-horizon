package fr.clixmods.livinghorizon.ambient;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.LivingHorizonClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
//? if >=1.21.9 {
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
//?} elif >=1.20.2 {
/*import net.minecraft.client.resources.PlayerSkin;
*///?}

import java.util.UUID;

/**
 * A player of no one in one of the mod's statue skins (stone, marble, mossy stone, all made
 * for it), for easter egg 10 ({@link Statue}). The game draws it as it draws any player; only
 * the skin is its own.
 */
final class StatueFigure extends RemotePlayer {
    /** The statue skins, in {@code textures/entity/statue/}: shown one after the other. */
    static final String[] LOOKS = {"stone", "marble", "mossy"};

    private final Identifier texture;

    StatueFigure(ClientLevel level, String look) {
        super(level, new GameProfile(UUID.randomUUID(), ""));
        texture = Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "textures/entity/statue/" + look + ".png");
        getEntityData().set(Avatar.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
    }

    //? if >=1.21.9 {
    @Override
    public PlayerSkin getSkin() {
        return PlayerSkin.insecure(new ClientAsset.ResourceTexture(texture, texture), null, null, PlayerModelType.WIDE);
    }
    //?} elif >=1.20.2 {
    /*@Override
    public PlayerSkin getSkin() {
        return new PlayerSkin(texture, null, null, null, PlayerSkin.Model.WIDE, false);
    }
    *///?} else {
    /*@Override
    public Identifier getSkinTextureLocation() {
        return texture;
    }

    @Override
    public String getModelName() {
        return "default";
    }
    *///?}
}
