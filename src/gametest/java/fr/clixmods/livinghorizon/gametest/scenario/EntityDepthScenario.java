package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Everything the entities drew is in the depth when the far terrain's depth is given back:
 * the depth view after the entities is copied at that moment. A block held by an item display
 * is drawn from a buffer the game empties only after the entities' call; drawn after the
 * depth was given back, it showed through Voxy's terrain under shaders. The view after the
 * entities must hold it, where the view before them does not.
 */
public final class EntityDepthScenario implements Scenario {
    /** Pixels of the view that must differ: the block covers thousands. */
    private static final int MIN_CHANGED = 500;

    @Override
    public String name() {
        return "entitydepth";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "execute at @p run summon item_display ~ ~1.6 ~4 {item:{id:\"minecraft:stone\",count:1},"
                    + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                    + "translation:[0f,0f,0f],scale:[2f,2f,2f]}}");
            context.runOnClient(minecraft -> minecraft.player.setXRot(-5));
            Scene.configure(context, config -> config.debugDepthView = 1);
            Path before = Scene.screenshotFrame(context, this, "before-entities");
            Scene.configure(context, config -> config.debugDepthView = 3);
            Path after = Scene.screenshotFrame(context, this, "after-entities");
            int changed = changed(before, after);
            Scene.log("depth view pixels changed by the entities: " + changed);
            if (changed < MIN_CHANGED) {
                throw new AssertionError("The depth after the entities lacks what they drew: " + changed + " pixels changed");
            }
        }
    }

    /** Pixels of the depth view, bottom right, that differ between the two screenshots. */
    private static int changed(Path before, Path after) {
        try {
            BufferedImage a = ImageIO.read(before.toFile()), b = ImageIO.read(after.toFile());
            // Where FarDepth.drawDebugView puts it, from the top left corner here.
            int w = a.getWidth() * 2 / 5, h = a.getHeight() * 2 / 5;
            int left = a.getWidth() - w - 8, top = a.getHeight() - 8 - h;
            int changed = 0;
            for (int y = top; y < top + h; y++) {
                for (int x = left; x < left + w; x++) {
                    int p = a.getRGB(x, y), q = b.getRGB(x, y);
                    if (Math.abs((p & 0xFF) - (q & 0xFF)) > 16 || Math.abs((p >> 8 & 0xFF) - (q >> 8 & 0xFF)) > 16) changed++;
                }
            }
            return changed;
        } catch (IOException e) {
            throw new AssertionError("Reading the screenshots back failed", e);
        }
    }
}
