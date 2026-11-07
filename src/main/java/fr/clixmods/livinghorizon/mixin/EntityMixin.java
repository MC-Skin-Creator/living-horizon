package fr.clixmods.livinghorizon.mixin;

import fr.clixmods.livinghorizon.render.GhostRenderer;
import fr.clixmods.livinghorizon.render.lod.LodHolder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws a mount carrying another player as far as the player on it.
 *
 * <p>The game culls every entity past a distance that grows with its size, and makes
 * an exception for other players only ({@code RemotePlayer} draws to ten times the
 * usual distance). A boat is small, so at 100 blocks a friend in one sits on nothing.
 *
 * <p>Also holds what the levels of detail remember of the figure drawn from an entity.
 */
@Mixin(Entity.class)
abstract class EntityMixin implements LodHolder {
    @Unique
    private @Nullable Object livinghorizon$lodRecord;
    @Unique
    private int livinghorizon$fullQuads;

    @Override
    public @Nullable Object livinghorizon$lod() {
        return livinghorizon$lodRecord;
    }

    @Override
    public void livinghorizon$lod(@Nullable Object record) {
        livinghorizon$lodRecord = record;
    }

    @Override
    public int livinghorizon$fullQuads() {
        return livinghorizon$fullQuads;
    }

    @Override
    public void livinghorizon$fullQuads(int quads) {
        livinghorizon$fullQuads = quads;
    }

    @Inject(method = "shouldRenderAtSqrDistance", at = @At("HEAD"), cancellable = true)
    private void livinghorizon$mountsOfPlayers(double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (GhostRenderer.alwaysRender((Entity) (Object) this)) cir.setReturnValue(true);
    }
}
