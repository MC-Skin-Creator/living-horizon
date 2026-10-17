package fr.clixmods.livinghorizon.track;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Items as the game saves them, written as text, and back: what a player who logged off was
 * wearing and holding, kept in the file of who rests where.
 */
public final class ItemNbt {
    private ItemNbt() {
    }

    /** What the entity wears and holds, by slot name ({@code head}, {@code mainhand}...); empty slots left out. */
    public static Map<String, String> equipment(@Nullable LivingEntity entity, Level level) {
        Map<String, String> worn = new LinkedHashMap<>();
        if (entity == null) return worn;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            String saved = save(stack, level);
            if (saved != null) worn.put(slot.getName(), saved);
        }
        return worn;
    }

    /** The item as text; null when the game cannot save it. */
    static @Nullable String save(ItemStack stack, Level level) {
        try {
            return ItemStack.CODEC.encodeStart(ops(level), stack).result().map(Tag::toString).orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The item the text describes; empty when it is unknown here (a mod removed) or broken. */
    static ItemStack load(String saved, Level level) {
        try {
            Tag tag = TagParser.parseCompoundFully(saved);
            return ItemStack.CODEC.parse(ops(level), tag).result().orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /** The slot a saved name stands for, or null. */
    static @Nullable EquipmentSlot slot(String name) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getName().equals(name)) return slot;
        }
        return null;
    }

    /** A sword, the game's or a mod's: what a player draws against monsters. */
    static boolean sword(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_sword");
    }

    private static RegistryOps<Tag> ops(Level level) {
        return RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
    }
}
