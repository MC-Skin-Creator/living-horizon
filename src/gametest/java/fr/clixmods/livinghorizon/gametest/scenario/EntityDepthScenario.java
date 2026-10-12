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
 * depth was given back, it showed through Voxy's terrain under shaders. A glass block is drawn
 * with the translucent parts of the entities, a step of its own from 26.1, like a slime's outer
 * layer. The view after the entities must hold both, where the view before them does not.
 */
public final class EntityDepthScenario implements Scenario {
    /** Pixels of each half of the view that must differ: a block covers thousands. */
    private static final int MIN_CHANGED = 500;

    @Override
    public String name() {
        return "entitydepth";
    }

    @Override
    public void run(ClientGameTestContext context) {
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Facing south, stone on the right half of the screen, glass on the left.
            display(world, "stone", -1.5);
            display(world, "white_stained_glass", 1.5);
            context.runOnClient(minecraft -> minecraft.player.setXRot(-5));
            Scene.configure(context, config -> config.debugDepthView = 1);
            Path before = Scene.screenshotFrame(context, this, "before-entities");
            Scene.configure(context, config -> config.debugDepthView = 3);
            Path after = Scene.screenshotFrame(context, this, "after-entities");
            int glass = changed(before, after, true), stone = changed(before, after, false);
            Scene.log("depth view pixels changed by the entities: stone " + stone + ", glass " + glass);
            if (stone < MIN_CHANGED || glass < MIN_CHANGED) {
                throw new AssertionError("The depth after the entities lacks what they drew: stone " + stone
                        + ", glass " + glass + " pixels changed");
            }
        }
    }

    /** A block two blocks wide held by an item display, at eye height, 5 blocks ahead. */
    private static void display(TestSingleplayerContext world, String block, double x) {
        Scene.command(world, "execute at @p run summon item_display ~" + x + " ~1.6 ~5 {item:{id:\"minecraft:" + block
                + "\",count:1},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                + "translation:[0f,0f,0f],scale:[2f,2f,2f]}}");
    }

    /** Pixels of one half of the depth view, bottom right, that differ between the two screenshots. */
    private static int changed(Path before, Path after, boolean leftHalf) {
        try {
            BufferedImage a = ImageIO.read(before.toFile()), b = ImageIO.read(after.toFile());
            // Where FarDepth.drawDebugView puts it, from the top left corner here.
            int w = a.getWidth() * 2 / 5, h = a.getHeight() * 2 / 5;
            int left = a.getWidth() - w - 8, top = a.getHeight() - 8 - h;
            int from = leftHalf ? left : left + w / 2, to = leftHalf ? left + w / 2 : left + w;
            int changed = 0;
            for (int y = top; y < top + h; y++) {
                for (int x = from; x < to; x++) {
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
