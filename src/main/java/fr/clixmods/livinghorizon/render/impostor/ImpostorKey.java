package fr.clixmods.livinghorizon.render.impostor;

import net.minecraft.ChatFormatting;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * What decides which picture a figure gets: its type, and everything about it that
 * changes how it looks - skin, variant, colour, size, profession, what it wears. Two
 * figures with the same key share one sheet: every pig of one skin, every zombie with
 * the same helmet and sword.
 *
 * <p>What a figure carries and never shows is left out: where its bed is, who owns it,
 * when its anger ends, its name. So is what keeps changing on it, a counter or a flag
 * that comes and goes (a dolphin drying, a spider climbing, a zombie raising its arms):
 * it is learnt per kind of mob, from the figures seen changing it again and again.
 * Counted in, it gave the same figure a new sheet every few seconds, and a crowd of them
 * more sheets than the pages hold, baked over and over.
 *
 * @param type       the entity type's id
 * @param appearance a hash of the rest
 */
public record ImpostorKey(String type, long appearance) {
    /** Milliseconds a figure's key is kept before it is worked out again. */
    private static final long KEEP = 5000;

    /** Milliseconds within which a value changing twice on one figure is taken as restless. */
    private static final long RESTLESS_WITHIN = 60_000;

    /** When a value of a figure last changed, and which of its bits. */
    private record Change(long when, long bits) {
    }

    /**
     * A figure's key, the values it was worked out from, and when each of them last changed.
     */
    private record Known(ImpostorKey key, long when, Map<Integer, Object> values, Map<Integer, Change> changes) {
    }

    private static final Map<Entity, Known> KNOWN = new IdentityHashMap<>();

    /**
     * Per kind of mob, the bits of each synched value seen changing on one figure again and
     * again, -1 for the whole value: left out of its key. Kept until the game closes.
     */
    private static final Map<EntityType<?>, Map<Integer, Long>> RESTLESS = new HashMap<>();

    /** Names the game draws differently: upside down, in rainbow colours, as Toast. */
    private static final Set<String> SPECIAL_NAMES = Set.of("Dinnerbone", "Grumm", "jeb_", "Toast");

    /**
     * Synched data that changes all the time and never the picture: health, air, score, the
     * arrows stuck in it... Counted in, a hurt player or a mob holding its breath would ask
     * for a new sheet every few seconds.
     */
    private static final List<EntityDataAccessor<?>> IGNORED = List.of(
            Entity.DATA_AIR_SUPPLY_ID, Entity.DATA_CUSTOM_NAME_VISIBLE, Entity.DATA_SILENT, Entity.DATA_NO_GRAVITY,
            Entity.DATA_TICKS_FROZEN);
    private static final List<EntityDataAccessor<?>> IGNORED_LIVING = List.of(
            LivingEntity.DATA_LIVING_ENTITY_FLAGS, LivingEntity.DATA_HEALTH_ID, LivingEntity.DATA_EFFECT_PARTICLES,
            LivingEntity.DATA_EFFECT_AMBIENCE_ID, LivingEntity.DATA_ARROW_COUNT_ID, LivingEntity.DATA_STINGER_COUNT_ID);
    private static final List<EntityDataAccessor<?>> IGNORED_PLAYER = List.of(
            Player.DATA_PLAYER_ABSORPTION_ID, Player.DATA_SCORE_ID);

    /** The shared flags that change nothing on the picture: on fire, sprinting, glowing. */
    private static final int UNSEEN_FLAGS = 1 | 1 << 3 | 1 << 6;

    /** The key of a figure, worked out at most every few seconds: figures hardly ever change. */
    public static ImpostorKey of(Entity entity) {
        long now = System.currentTimeMillis();
        Known known = KNOWN.get(entity);
        if (known != null && now - known.when < KEEP) return known.key;
        if (KNOWN.size() > 4096) KNOWN.clear();
        Map<Integer, Object> values = values(entity);
        Map<Integer, Change> changes = known == null ? new HashMap<>() : known.changes;
        if (known != null) learn(entity.getType(), known.values, values, changes, now);
        ImpostorKey key = compute(entity, values);
        KNOWN.put(entity, new Known(key, now, values, changes));
        return key;
    }

    public static void forgetAll() {
        KNOWN.clear();
    }

    /** The synched values of a figure that may show on its picture, by id. */
    private static Map<Integer, Object> values(Entity entity) {
        Map<Integer, Object> values = new HashMap<>();
        List<SynchedEntityData.DataValue<?>> all = entity.getEntityData().getNonDefaultValues();
        if (all == null) return values;
        for (SynchedEntityData.DataValue<?> value : all) {
            if (ignored(entity, value.id())) continue;
            Object data = seen(entity, value.id(), value.value());
            if (data != null) values.put(value.id(), data);
        }
        return values;
    }

    /**
     * What of a synched value may show on the picture, or null for nothing. Judged by its
     * type, which every version and every mod's mobs share: a position, a time, an owner or
     * a name never changes how a figure is drawn, a carried block does.
     */
    private static @Nullable Object seen(Entity entity, int id, @Nullable Object data) {
        if (data instanceof Optional<?> optional) {
            Object inside = optional.orElse(null);
            if (inside instanceof Component name) return special(name);
            return inside instanceof BlockState ? inside : null;
        }
        if (data instanceof Component name) return special(name);
        if (data instanceof Long || data instanceof BlockPos || data instanceof UUID) return null;
        // The parrots on a player's shoulders; on a mob, the id of another entity.
        if (data instanceof OptionalInt) return entity instanceof Player ? data : null;
        if (id == Entity.DATA_SHARED_FLAGS_ID.id() && data instanceof Byte flags) return (byte) (flags & ~UNSEEN_FLAGS);
        return data;
    }

    private static @Nullable String special(Component name) {
        String plain = ChatFormatting.stripFormatting(name.getString());
        return plain != null && SPECIAL_NAMES.contains(plain) ? plain : null;
    }

    /**
     * Compares a figure's values with those of its last key: a value changing again within
     * {@link #RESTLESS_WITHIN} is restless for its whole kind of mob, the bits that changed
     * both times for a number or flags, all of it otherwise.
     */
    private static void learn(EntityType<?> type, Map<Integer, Object> before, Map<Integer, Object> after,
                              Map<Integer, Change> changes, long now) {
        Set<Integer> ids = new HashSet<>(before.keySet());
        ids.addAll(after.keySet());
        for (int id : ids) {
            Object was = before.get(id), is = after.get(id);
            if (Objects.equals(was, is)) continue;
            Long a = bits(was), b = bits(is);
            long changed = a != null && b != null ? a ^ b : -1L;
            Change last = changes.get(id);
            if (last != null && now - last.when < RESTLESS_WITHIN) {
                RESTLESS.computeIfAbsent(type, ignoredType -> new HashMap<>()).merge(id, changed | last.bits, (x, y) -> x | y);
            }
            changes.put(id, new Change(now, changed));
        }
    }

    /** A number or flags as bits, 0 for a value at its default; null for anything else. */
    private static @Nullable Long bits(@Nullable Object data) {
        if (data == null) return 0L;
        if (data instanceof Boolean flag) return flag ? 1L : 0L;
        if (data instanceof Byte number) return number & 0xFFL;
        if (data instanceof Integer number) return number & 0xFFFFFFFFL;
        return null;
    }

    private static ImpostorKey compute(Entity entity, Map<Integer, Object> values) {
        Map<Integer, Long> restless = RESTLESS.getOrDefault(entity.getType(), Map.of());
        long hash = 17;
        if (entity instanceof AbstractClientPlayer player) {
            // A skin was its texture alone before 1.20.2.
            //? if >=1.20.2 {
            hash = hash * 31 + player.getSkin().hashCode();
            //?} else
            /*hash = hash * 31 + player.getSkinTextureLocation().hashCode();*/
        }
        // In the order of the ids: the same values always give the same key.
        for (int id : values.keySet().stream().sorted().toList()) {
            Object data = values.get(id);
            long mask = restless.getOrDefault(id, 0L);
            Long bits = bits(data);
            long seen;
            if (bits != null) {
                // A restless bit is left out, and a value with nothing else set counts as unset.
                seen = bits & ~mask;
                if (seen == 0) continue;
            } else {
                if (mask != 0) continue;
                seen = data.hashCode();
            }
            hash = hash * 31 + id;
            hash = hash * 31 + seen;
        }
        if (entity instanceof LivingEntity living) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                //? if >=1.20.5 {
                hash = hash * 31 + ItemStack.hashItemAndComponents(living.getItemBySlot(slot));
                //?} else {
                /*ItemStack stack = living.getItemBySlot(slot);
                hash = hash * 31 + (stack.isEmpty() ? 0 : java.util.Objects.hash(stack.getItem(), stack.getTag()));
                *///?}
            }
        }
        return new ImpostorKey(EntityType.getKey(entity.getType()).toString(), hash);
    }

    /** Ids are only unique along one line of classes: each list is checked for its own class. */
    private static boolean ignored(Entity entity, int id) {
        return listed(IGNORED, id)
                || entity instanceof LivingEntity && listed(IGNORED_LIVING, id)
                || entity instanceof Player && listed(IGNORED_PLAYER, id)
                || entity instanceof AbstractVillager && AbstractVillager.DATA_UNHAPPY_COUNTER.id() == id;
    }

    private static boolean listed(List<EntityDataAccessor<?>> accessors, int id) {
        for (EntityDataAccessor<?> accessor : accessors) {
            if (accessor.id() == id) return true;
        }
        return false;
    }
}
