package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import fr.clixmods.livinghorizon.ui.ImpostorScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The impostor screen with two figures of one name: villagers in two outfits. The atlas hands
 * its figures out in order of last use, which the world changes every frame; the screen must
 * keep its rows in place whatever that order. The villagers stand close, drawn by the game
 * itself, so that only this scenario uses their pictures: once in one order, once in the other,
 * a screenshot after each, which must be the same.
 */
public final class ImpostorScreenScenario implements Scenario {
    private static final String[] PROFESSIONS = {"farmer", "librarian"};

    @Override
    public String name() {
        return "impostorscreen";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.impostors = true);
        // The "Saving world" notice in a corner, after a new world is made, would tell the two
        // screenshots apart.
        context.runOnClient(minecraft -> minecraft.options.showAutosaveIndicator().set(false));
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            for (int i = 0; i < PROFESSIONS.length; i++) {
                Scene.command(world, "execute at @p run summon villager ~" + (i * 3 - 1.5) + " ~ ~6 {NoAI:1b,"
                        + "VillagerData:{profession:\"minecraft:" + PROFESSIONS[i] + "\",type:\"minecraft:plains\",level:1}}");
            }
            context.waitTicks(40);
            List<ImpostorKey> keys = context.computeOnClient(minecraft -> {
                List<ImpostorKey> found = new ArrayList<>();
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (!(entity instanceof LivingEntity) || entity == minecraft.player) continue;
                    ImpostorKey key = ImpostorKey.of(entity);
                    ImpostorAtlas.request(key, entity);
                    if (!found.contains(key)) found.add(key);
                }
                return found;
            });
            context.waitTicks(80);
            // The sheets of earlier scenarios stay: the mobs keep theirs from one world to the next.
            int baked = context.computeOnClient(minecraft ->
                    (int) keys.stream().filter(key -> ImpostorAtlas.sheet(key) != null).count());
            Scene.log("impostorscreen keys=" + keys.size() + " baked=" + baked);
            if (keys.size() != 2 || baked != 2) {
                throw new AssertionError("Expected two villager pictures, got " + keys.size() + " keys, " + baked + " baked");
            }

            context.setScreen(() -> new ImpostorScreen(null));
            context.runOnClient(minecraft -> keys.forEach(ImpostorAtlas::sheet));
            Path first = Scene.screenshot(context, this, "first");
            context.runOnClient(minecraft -> {
                for (int i = keys.size() - 1; i >= 0; i--) ImpostorAtlas.sheet(keys.get(i));
            });
            Path second = Scene.screenshot(context, this, "second");
            context.setScreen(() -> null);

            context.runOnClient(minecraft -> minecraft.options.showAutosaveIndicator().set(true));
            long changed = changedPixels(first, second);
            Scene.log("impostorscreen changed=" + changed);
            if (changed > 0) throw new AssertionError("The impostor screen's rows moved: " + changed + " pixels changed");
        }
    }

    private static long changedPixels(Path first, Path second) {
        try {
            BufferedImage a = ImageIO.read(first.toFile()), b = ImageIO.read(second.toFile());
            long changed = 0;
            for (int y = 0; y < Math.min(a.getHeight(), b.getHeight()); y++) {
                for (int x = 0; x < Math.min(a.getWidth(), b.getWidth()); x++) {
                    if (a.getRGB(x, y) != b.getRGB(x, y)) changed++;
                }
            }
            return changed;
        } catch (IOException e) {
            throw new AssertionError("Could not read the screenshots", e);
        }
    }
}
