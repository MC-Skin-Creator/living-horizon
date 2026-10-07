package fr.clixmods.livinghorizon.render.impostor;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * What decides which picture a figure gets: its type, and everything about it that
 * changes how it looks - skin, variant, colour, size, profession, what it wears. Two
 * figures with the same key share one sheet.
 *
 * @param type       the entity type's id
 * @param appearance a hash of the rest
 */
public record ImpostorKey(String type, long appearance) {
    /** Milliseconds a figure's key is kept before it is worked out again. */
    private static final long KEEP = 5000;

    private record Known(ImpostorKey key, long when) {
    }

    private static final Map<Entity, Known> KNOWN = new IdentityHashMap<>();

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
        ImpostorKey key = compute(entity);
        KNOWN.put(entity, new Known(key, now));
        return key;
    }

    public static void forgetAll() {
        KNOWN.clear();
    }

    private static ImpostorKey compute(Entity entity) {
        long hash = 17;
        if (entity instanceof AbstractClientPlayer player) {
            // A skin was its texture alone before 1.20.2.
            //? if >=1.20.2 {
            hash = hash * 31 + player.getSkin().hashCode();
            //?} else
            /*hash = hash * 31 + player.getSkinTextureLocation().hashCode();*/
        }
        List<SynchedEntityData.DataValue<?>> values = entity.getEntityData().getNonDefaultValues();
        if (values != null) {
            for (SynchedEntityData.DataValue<?> value : values) {
                if (ignored(entity, value.id())) continue;
                Object data = value.value();
                if (value.id() == Entity.DATA_SHARED_FLAGS_ID.id() && data instanceof Byte flags) {
                    data = (byte) (flags & ~UNSEEN_FLAGS);
                }
                hash = hash * 31 + value.id();
                hash = hash * 31 + (data == null ? 0 : data.hashCode());
            }
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
