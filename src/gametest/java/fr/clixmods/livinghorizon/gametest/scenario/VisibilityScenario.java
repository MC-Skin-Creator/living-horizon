package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.Profile;
import fr.clixmods.livinghorizon.compat.OcclusionQueries;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The two ways of telling which distant mobs the terrain hides, on the same frames: every mob
 * in one draw ({@code VisibilityPass}) and one occlusion query per mob. A row of remembered
 * mobs 200 blocks away, a wall in front of the half of them: both must hide that half and
 * agree on nearly every answer, and the one draw alone must hide as many as the queries alone.
 */
public final class VisibilityScenario implements Scenario {
    private static final String[] MOBS = {"cow", "sheep", "pig", "villager", "horse", "iron_golem"};
    /** Answers compared, at least, by mob and frame: about half the row is hidden. */
    private static final int MIN_COMPARED = 200, MIN_HIDDEN = 100;
    /** Share of the answers the two ways may differ on: a sample grid against exact pixels, at the wall's edge. */
    private static final double MAX_DIFFERENT = 0.03;
    /** Mobs one way may hide more than the other. */
    private static final int MAX_HIDDEN_GAP = 2;

    @Override
    public String name() {
        return "visibility";
    }

    @Override
    public void run(ClientGameTestContext context) {
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> {
            config.hideOccludedMobs = true;
            config.optOcclusionQueries = true;
            config.optBatchedDepth = true;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Past 32 blocks: a mob that vanishes closer is taken as killed or despawned.
            Scene.row(world, MOBS, 4, 40, 4.0);
            context.waitTicks(100);
            // Walk away: the copies take over once the real mobs are no longer sent.
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);
            // A wall 20 blocks ahead, in front of the mobs on the negative x side.
            Scene.command(world, "execute at @p run fill ~-40 ~ ~20 ~-1 ~20 ~20 stone");
            context.waitTicks(60);
            context.runOnClient(minecraft -> Scene.log("visibility shown=" + FarPlayerTracker.get().mobs().shown().size()));

            context.runOnClient(minecraft -> OcclusionQueries.compare(true));
            context.waitTicks(60);
            int[] counts = context.computeOnClient(minecraft -> OcclusionQueries.compared());
            context.runOnClient(minecraft -> OcclusionQueries.compare(false));
            int total = counts[0] + counts[1] + counts[2] + counts[3], different = counts[2] + counts[3];
            Scene.log("visibility compared=" + total + " both shown=" + counts[0] + " both hidden=" + counts[1]
                    + " hidden by the queries only=" + counts[2] + " hidden by the one draw only=" + counts[3]);

            int batched = hidden(context, true);
            Scene.screenshot(context, this, "one-draw");
            int queries = hidden(context, false);
            Scene.screenshot(context, this, "queries");
            Scene.log("visibility hidden: one draw " + batched + ", queries " + queries);

            if (total < MIN_COMPARED) throw new AssertionError("Only " + total + " mobs answered by both ways");
            if (counts[1] < MIN_HIDDEN) throw new AssertionError("The wall hides " + counts[1] + " mobs by both ways");
            if (different > MAX_DIFFERENT * total) {
                throw new AssertionError("The two ways disagree on " + different + " of " + total + " mobs");
            }
            if (Math.abs(batched - queries) > MAX_HIDDEN_GAP) {
                throw new AssertionError("The one draw hides " + batched + " mobs, the queries " + queries);
            }
        } finally {
            context.runOnClient(minecraft -> OcclusionQueries.compare(false));
            Scene.configure(context, config -> config.optBatchedDepth = true);
        }
    }

    /** How many mobs one way hides once it alone has answered for a while. */
    private static int hidden(ClientGameTestContext context, boolean batched) {
        Scene.configure(context, config -> config.optBatchedDepth = batched);
        context.runOnClient(minecraft -> OcclusionQueries.clear());
        context.waitTicks(40);
        int hidden = context.computeOnClient(minecraft -> OcclusionQueries.culled());
        context.runOnClient(minecraft -> Scene.log("visibility " + (batched ? "one draw" : "queries") + ": " + OcclusionQueries.state()
                + ", sent in " + Profile.millis(Profile.Section.OCCLUSION_RUN.cpu().average()) + " ms, read in "
                + Profile.millis(Profile.Section.OCCLUSION_COLLECT.cpu().average()) + " ms"));
        return hidden;
    }
}
