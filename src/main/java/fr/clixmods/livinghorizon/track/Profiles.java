package fr.clixmods.livinghorizon.track;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.google.common.collect.ImmutableMultimap;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A player's profile, read and made the same way whatever the authentication library: it
 * became a record in 1.21.9, with {@code name()} and {@code id()}, and a property map
 * that is built whole; before, getters and a map filled after the fact.
 */
public final class Profiles {
    private Profiles() {
    }

    public static String name(GameProfile profile) {
        //? if >=1.21.9 {
        return profile.name();
        //?} else {
        /*return profile.getName();
        *///?}
    }

    public static UUID id(GameProfile profile) {
        //? if >=1.21.9 {
        return profile.id();
        //?} else {
        /*return profile.getId();
        *///?}
    }

    public static PropertyMap properties(GameProfile profile) {
        //? if >=1.21.9 {
        return profile.properties();
        //?} else {
        /*return profile.getProperties();
        *///?}
    }

    // A property became a record in 1.20.2.
    public static String value(Property property) {
        //? if >=1.20.2 {
        return property.value();
        //?} else
        /*return property.getValue();*/
    }

    public static @Nullable String signature(Property property) {
        //? if >=1.20.2 {
        return property.signature();
        //?} else
        /*return property.getSignature();*/
    }

    /** A profile with one skin. */
    public static GameProfile withSkin(UUID id, String name, Property textures) {
        //? if >=1.21.9 {
        return new GameProfile(id, name, new PropertyMap(ImmutableMultimap.of("textures", textures)));
        //?} else {
        /*GameProfile profile = new GameProfile(id, name);
        profile.getProperties().put("textures", textures);
        return profile;
        *///?}
    }
}
