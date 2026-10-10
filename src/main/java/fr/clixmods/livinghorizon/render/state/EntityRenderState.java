package fr.clixmods.livinghorizon.render.state;

//? if <1.21.2 {
/*import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/^*
 * What the mod reads and changes of a figure before it is drawn, before 1.21.2: the game had
 * no render states then, and drew an entity itself. The fields are named as in the game's
 * later states, so that the code is the same on every version; the changes are made to the
 * entity for as long as it is drawn ({@link #apply}), and undone after.
 ^/
public class EntityRenderState {
    public final Entity entity;
    public final float partialTick;
    public double x, y, z;
    public float boundingBoxWidth, boundingBoxHeight;
    public @Nullable Component nameTag;

    protected EntityRenderState(Entity entity, float partialTick) {
        this.entity = entity;
        this.partialTick = partialTick;
        x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        boundingBoxWidth = entity.getBbWidth();
        boundingBoxHeight = entity.getBbHeight();
        nameTag = entity.getDisplayName();
    }

    /^* The state of an entity, of the kind the game would have made for it. ^/
    public static EntityRenderState of(Entity entity, float partialTick) {
        if (entity instanceof AbstractClientPlayer player) return new HumanoidRenderState(player, partialTick);
        if (entity instanceof LivingEntity living) return new LivingEntityRenderState(living, partialTick);
        return new EntityRenderState(entity, partialTick);
    }

    /^*
     * The figure drawn without its name right now: the game shows a name by the entity's own
     * rules, which LivingEntityRendererMixin overrules for it. Its name is its display name
     * until {@link #nameTag} is set to null.
     ^/
    private static @Nullable Entity nameless;

    public static boolean nameless(Entity entity) {
        return entity == nameless;
    }

    /^* Gives the entity what was changed here, until {@link #restore}. ^/
    public void apply() {
        if (nameTag == null) nameless = entity;
    }

    public void restore() {
        nameless = null;
    }
}
*///?}
