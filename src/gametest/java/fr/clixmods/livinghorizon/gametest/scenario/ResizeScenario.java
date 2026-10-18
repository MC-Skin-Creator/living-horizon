package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.compat.FarDepth;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The depth view across window sizes: the game makes its textures again when the window is
 * resized, often under the same names. The view must follow, at every size and back.
 */
public final class ResizeScenario implements Scenario {
    @Override
    public String name() {
        return "resize";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.debugDepthView = 1);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.summon(world, "cow", 0, 8, 0);
            context.runOnClient(minecraft -> minecraft.player.setXRot(-5));
            check(Scene.screenshotFrame(context, this, "854x480"));
            log(context);
            resize(context, 1200, 640);
            check(Scene.screenshotFrame(context, this, "1200x640"));
            log(context);
            resize(context, 640, 400);
            check(Scene.screenshotFrame(context, this, "640x400"));
            log(context);
            resize(context, 854, 480);
            check(Scene.screenshotFrame(context, this, "854x480-again"));
            log(context);
        }
    }

    private static void resize(ClientGameTestContext context, int width, int height) {
        context.getInput().resizeWindow(width, height);
        context.waitTicks(20);
    }

    /**
     * The depth view, bottom right, shows the sky in dark blue at the top: gone, the frame has
     * the game's sky there instead. Drawn into a deleted texture, it never reaches the screen.
     */
    private static void check(java.nio.file.Path frame) {
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(frame.toFile());
            int w = image.getWidth(), h = image.getHeight();
            int rgb = image.getRGB(w - 8 - (int) (w * 0.2), h - 8 - (int) (h * 0.37));
            int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
            if (!(r < 50 && g < 60 && b > 50 && b < 120)) {
                throw new AssertionError("No depth view at " + w + "x" + h + ": " + r + "," + g + "," + b + " where its sky is");
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void log(ClientGameTestContext context) {
        context.runOnClient(minecraft -> Scene.log("window " + minecraft.getMainRenderTarget().width + "x"
                + minecraft.getMainRenderTarget().height + ", depth " + FarDepth.state()));
    }
}
