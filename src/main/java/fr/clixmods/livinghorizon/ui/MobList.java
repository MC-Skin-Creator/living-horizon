package fr.clixmods.livinghorizon.ui;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.track.MobKinds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Every kind of mob in the game, every boat, and the other vehicles met so far, for the list
 * of what is shown far away.
 */
public final class MobList {
    private MobList() {
    }

    /**
     * Every living kind has default attributes, which is how they are told apart here:
     * {@code EntityType.getBaseClass()} answers {@code Entity} for all of them. Players and
     * armour stands are living but not mobs; mannequins are kept, for the skinned figures
     * builds and mods (Distant Friends) leave in the world. A vehicle is only known as one once
     * met ({@link FarConfig#knownVehicles}).
     */
    public static List<EntityType<?>> all() {
        List<String> vehicles = FarConfig.get().knownVehicles;
        List<EntityType<?>> mobs = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (DefaultAttributes.hasSupplier(type) && type != EntityType.PLAYER
                    && type != EntityType.ARMOR_STAND) {
                mobs.add(type);
                continue;
            }
            String id = EntityType.getKey(type).toString();
            if (MobKinds.isBoat(id) || vehicles.contains(id)) mobs.add(type);
        }
        return mobs;
    }

    /**
     * Whether a kind of {@link #all} goes under the mobs' tab: a living kind, mannequins
     * excepted. Boats, vehicles and mannequins go under the entities' tab.
     */
    public static boolean creature(EntityType<?> type) {
        return DefaultAttributes.hasSupplier(type) && !EntityType.getKey(type).toString().equals("minecraft:mannequin");
    }
}
