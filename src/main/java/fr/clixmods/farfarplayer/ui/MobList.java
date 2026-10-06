package fr.clixmods.farfarplayer.ui;

import fr.clixmods.farfarplayer.track.MobKinds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import java.util.ArrayList;
import java.util.List;

/** Every kind of mob in the game, and every boat, for the list of what is shown far away. */
public final class MobList {
    private MobList() {
    }

    /**
     * Every living kind has default attributes, which is how they are told apart here:
     * {@code EntityType.getBaseClass()} answers {@code Entity} for all of them. Players and
     * armour stands are living but not mobs; mannequins are kept, for the skinned figures
     * builds and mods (Distant Friends) leave in the world.
     */
    public static List<EntityType<?>> all() {
        List<EntityType<?>> mobs = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (DefaultAttributes.hasSupplier(type) && type != EntityType.PLAYER
                    && type != EntityType.ARMOR_STAND) {
                mobs.add(type);
            } else if (MobKinds.isBoat(EntityType.getKey(type).toString())) {
                mobs.add(type);
            }
        }
        return mobs;
    }
}
