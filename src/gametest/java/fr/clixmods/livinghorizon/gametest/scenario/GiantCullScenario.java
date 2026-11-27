package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.Stats;
import fr.clixmods.livinghorizon.compat.OcclusionQueries;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
//? if >=1.21.11 {
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.CowRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.phys.AABB;
//?}

/**
 * A giant whose model reaches far past its hitbox, as a mod's boss does (issue #351): a cow drawn
 * sixty times its size (84 blocks tall), whose renderer says so in its culling box, its hitbox left as it is. Its
 * copy 200 blocks away must be drawn while its middle is out of view and part of its body in it,
 * and while a wall hides its hitbox but not its body. Only from 1.21.11, where its renderer is written.
 */
public final class GiantCullScenario implements Scenario {
    /** How many times its size the giant cow is drawn. */
    private static final float GIANT = 60;

    @Override
    public String name() {
        return "giantcull";
    }

    @Override
    public void run(ClientGameTestContext context) {
        //? if >=1.21.11 {
        EntityRendererRegistry.register(EntityType.COW, GiantCow::new);
        GiantCow.on = true;
        try {
            context.runOnClient(minecraft -> minecraft.reloadResourcePacks());
            context.waitFor(minecraft -> minecraft.getOverlay() == null);
            giant(context);
        } finally {
            GiantCow.on = false;
        }
        //?} else {
        /*Scene.log("giantcull runs from 1.21.11 only");
        *///?}
    }

    //? if >=1.21.11 {
    private void giant(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> {
            config.impostors = false;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.summon(world, "cow", 0, 40, 180);
            context.waitTicks(100);
            // Walk away: the copy takes over once the real cow is no longer sent, 200 blocks off.
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);
            int shown = context.computeOnClient(minecraft -> FarPlayerTracker.get().mobs().shown().size());
            Scene.log("giantcull remembered shown=" + shown);
            if (shown < 1) throw new AssertionError("The cow is not remembered");

            // Straight at it: drawn either way.
            context.waitTicks(20);
            boolean ahead = drawn(context);
            Scene.log("giantcull ahead: " + counts(context));
            Scene.screenshot(context, this, "ahead");

            // Turned away and up: its middle just out of the mod's view cone (half the screen's
            // diagonal and a margin, 64 degrees), its body in the bottom corner of the screen.
            Scene.command(world, "tp @a 0 ~ -160 51 -46");
            context.waitTicks(20);
            boolean up = drawn(context);
            Scene.log("giantcull turned away: " + counts(context));
            Scene.screenshot(context, this, "up");

            // A wall halfway, over the hitbox from the eye, far under the body's top.
            Scene.configure(context, config -> {
                config.hideOccludedMobs = true;
                config.optOcclusionQueries = true;
            });
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            Scene.command(world, "execute at @p run fill ~-20 ~-1 ~100 ~20 ~2 ~100 stone");
            context.waitTicks(40);
            int hidden = context.computeOnClient(minecraft -> OcclusionQueries.culled());
            boolean behind = drawn(context) && hidden == 0;
            Scene.log("giantcull behind the wall: " + hidden + " hidden, " + counts(context));
            Scene.screenshot(context, this, "wall");

            Scene.log("giantcull result: ahead " + ahead + ", turned away " + up + ", behind the wall " + behind);
            if (!ahead) throw new AssertionError("The giant cow straight ahead is not drawn");
            if (!up) throw new AssertionError("The giant cow is left out with its middle out of view and its body in it");
            if (!behind) throw new AssertionError("The giant cow is hidden by a wall over its hitbox only");
        }
    }

    private static boolean drawn(ClientGameTestContext context) {
        return context.computeOnClient(minecraft -> Stats.drawn()) > 0;
    }

    private static String counts(ClientGameTestContext context) {
        return context.computeOnClient(minecraft -> "drawn " + Stats.drawn() + ", skipped " + Stats.skipped());
    }

    /** A cow drawn {@link #GIANT} times its size while {@link #on}, which its culling box says; a plain cow otherwise. */
    static final class GiantCow extends CowRenderer {
        static volatile boolean on;

        GiantCow(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void extractRenderState(Cow cow, CowRenderState state, float partialTick) {
            super.extractRenderState(cow, state, partialTick);
            if (on) state.scale *= GIANT;
        }

        @Override
        public AABB getBoundingBoxForCulling(Cow cow) {
            if (!on) return super.getBoundingBoxForCulling(cow);
            double half = Math.max(cow.getBbWidth(), 1.4) * GIANT / 2, height = cow.getBbHeight() * GIANT;
            return new AABB(cow.getX() - half, cow.getY(), cow.getZ() - half,
                    cow.getX() + half, cow.getY() + height, cow.getZ() + half);
        }
    }
    //?}
}
