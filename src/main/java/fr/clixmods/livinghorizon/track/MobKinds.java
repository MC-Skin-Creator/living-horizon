package fr.clixmods.livinghorizon.track;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
//? if >=1.21.9
import net.minecraft.world.entity.decoration.Mannequin;
//? if >=1.21.2
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Which mobs are shared, and the mobs the data pack publishes, numbered the way it numbers them: kind 1 is the
 * first of {@link #TYPES}. The pack has the same list twice, in
 * {@code tags/entity_type/remembered.json} and {@code function/mob_kind.mcfunction};
 * {@code DatapackTest} fails when the three disagree.
 */
public final class MobKinds {
    public static final List<String> TYPES = List.of(
            "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:llama", "minecraft:camel",
            "minecraft:cow", "minecraft:mooshroom", "minecraft:pig", "minecraft:sheep", "minecraft:chicken",
            "minecraft:rabbit", "minecraft:goat", "minecraft:cat", "minecraft:wolf", "minecraft:fox",
            "minecraft:parrot", "minecraft:panda", "minecraft:polar_bear", "minecraft:turtle", "minecraft:axolotl",
            "minecraft:frog", "minecraft:armadillo", "minecraft:sniffer", "minecraft:bee", "minecraft:ocelot",
            "minecraft:happy_ghast", "minecraft:strider", "minecraft:villager", "minecraft:iron_golem",
            "minecraft:snow_golem", "minecraft:allay",
            // Boats are not mobs, but a parked boat vanishes from far away just the same.
            "minecraft:oak_boat", "minecraft:spruce_boat", "minecraft:birch_boat", "minecraft:jungle_boat",
            "minecraft:acacia_boat", "minecraft:cherry_boat", "minecraft:dark_oak_boat", "minecraft:pale_oak_boat",
            "minecraft:mangrove_boat", "minecraft:bamboo_raft",
            "minecraft:oak_chest_boat", "minecraft:spruce_chest_boat", "minecraft:birch_chest_boat",
            "minecraft:jungle_chest_boat", "minecraft:acacia_chest_boat", "minecraft:cherry_chest_boat",
            "minecraft:dark_oak_chest_boat", "minecraft:pale_oak_chest_boat", "minecraft:mangrove_chest_boat",
            "minecraft:bamboo_chest_raft");

    /** Every boat and raft, chest or not; before 1.21.2, the one boat of every wood. */
    public static boolean isBoat(String type) {
        return type.endsWith("_boat") || type.endsWith("_raft") || type.equals("minecraft:boat");
    }

    private MobKinds() {
    }

    /**
     * Mobs, boats - a parked boat vanishes from far away just like an animal - and
     * mannequins, the skinned figures that Distant Friends stands far away as fake players.
     */
    public static boolean figure(Entity entity) {
        // Mannequins came in 1.21.9; every boat was one class before 1.21.2.
        //? if >=1.21.9 {
        return entity instanceof Mob || entity instanceof AbstractBoat || entity instanceof Mannequin;
        //?} elif >=1.21.2 {
        /*return entity instanceof Mob || entity instanceof AbstractBoat;
        *///?} else
        /*return entity instanceof Mob || entity instanceof net.minecraft.world.entity.vehicle.Boat;*/
    }

    /**
     * What a server running the mod shares: the kinds of the data pack, every boat, mannequins,
     * every mob with a name tag, and the mobs of other mods that are not monsters - what a
     * client shows out of the box. A client that shows more remembers the rest itself.
     */
    public static boolean shared(Entity entity) {
        if (!figure(entity)) return false;
        if (entity.hasCustomName()) return true;
        Identifier key = EntityType.getKey(entity.getType());
        String type = key.toString();
        if (TYPES.contains(type) || isBoat(type) || type.equals("minecraft:mannequin")) return true;
        return !Identifier.DEFAULT_NAMESPACE.equals(key.getNamespace())
                && entity.getType().getCategory() != MobCategory.MONSTER;
    }

    public static @Nullable String type(int kind) {
        return kind >= 1 && kind <= TYPES.size() ? TYPES.get(kind - 1) : null;
    }

    /**
     * The saved field the pack reads the variant from, written back the same way so
     * the game itself turns the number into a coat, a fleece, a colour.
     */
    static @Nullable String variantField(String type) {
        return switch (type) {
            case "minecraft:horse", "minecraft:llama", "minecraft:axolotl", "minecraft:parrot" -> "Variant";
            case "minecraft:sheep" -> "Color";
            case "minecraft:rabbit" -> "RabbitType";
            default -> null;
        };
    }

    /** The saved data of a mob known only from the pack: its variant, and its age. */
    static String nbt(String type, int variant, boolean baby) {
        StringBuilder nbt = new StringBuilder("{");
        String field = variantField(type);
        if (field != null) {
            nbt.append(field).append(':').append(variant);
            if (field.equals("Color")) nbt.append('b');
        }
        if (baby) {
            if (nbt.length() > 1) nbt.append(',');
            nbt.append("Age:-24000");
        }
        return nbt.append('}').toString();
    }
}
