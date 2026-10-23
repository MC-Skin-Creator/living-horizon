package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The depth view, bottom right: the game's depth copied and shaded, near white to far black,
 * the sky in blue. A view all white is a copy the driver never gave a size to. Every OpenGL
 * program of the far terrain's depth is compiled too, and the shaders of 26.3's GPU path read
 * as GLSL: Voxy and Distant Horizons cannot run here, and their programs would go untried.
 */
public final class DepthViewScenario implements Scenario {
    @Override
    public String name() {
        return "depthview";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.debugDepthView = 1);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.summon(world, "cow", 0, 8, 0);
            context.runOnClient(minecraft -> minecraft.player.setXRot(-5));
            Scene.screenshotFrame(context, this, "game");
            context.runOnClient(minecraft -> {
                FarDepth.compileAll();
                // 26.3's own, through the game's shader loader and its #include.
                //? if >=26.3
                /*fr.clixmods.livinghorizon.compat.FarDepthGpu.compileAll();*/
                Scene.log("depth " + FarDepth.state());
                // A step out of order is a hook gone missing: the far terrain's depth goes wrong.
                if (FarDepth.outOfOrder() != null) throw new AssertionError("steps out of order: " + FarDepth.outOfOrder());
            });
        }
    }
}
