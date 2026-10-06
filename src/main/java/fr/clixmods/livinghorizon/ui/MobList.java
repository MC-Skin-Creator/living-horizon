package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.track.MobKinds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import java.util.ArrayList;
import java.util.List;

/** Every kind of mob in the game, and every boat, for the list of what is shown far away. */
final class MobList {
    private MobList() {
    }

    /**
     * Every living kind has default attributes, which is how they are told apart here:
     * {@code EntityType.getBaseClass()} answers {@code Entity} for all of them. Players,
     * armour stands and mannequins are living but not mobs.
     */
    static List<EntityType<?>> all() {
        List<EntityType<?>> mobs = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (DefaultAttributes.hasSupplier(type) && type != EntityType.PLAYER
                    && type != EntityType.ARMOR_STAND && type != EntityType.MANNEQUIN) {
                mobs.add(type);
            } else if (MobKinds.isBoat(EntityType.getKey(type).toString())) {
                mobs.add(type);
            }
        }
        return mobs;
    }
}
