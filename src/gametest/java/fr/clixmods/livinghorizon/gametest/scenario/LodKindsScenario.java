package fr.clixmods.livinghorizon.gametest.scenario;

import com.mojang.authlib.GameProfile;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.render.lod.LodRenderer;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.SharedPositions;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.UUIDUtil;

/**
 * The figures the levels of detail first left whole, each at a simpler level: the game's own
 * entities a few blocks ahead (<i>Nearby Entities</i>), a slime (its see-through outside), a
 * charged creeper (its swirl), a zombie in enchanted, trimmed armour (a glint, and a layer from a
 * sheet of textures, left out), and a player who logged off, in a downloaded skin when Mojang
 * answers. Each should keep its shape and colours at level 2, against the full models.
 */
public final class LodKindsScenario implements Scenario {
    private static final String SKIN = "LodSkin";

    @Override
    public String name() {
        return "lodkinds";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.impostors = false;
            config.hideOccludedMobs = false;
            config.mobLods = true;
            config.lodNearby = true;
            config.debugLodColors = false;
            // The player who logged off stays where they are put, in view.
            config.offlineLife = false;
            config.offlineNames = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            Scene.command(world, "execute at @p run summon slime ~-3 ~ ~12 {NoAI:1b,Size:1,Rotation:[180f,0f]}");
            Scene.command(world, "execute at @p run summon creeper ~-1 ~ ~12 {NoAI:1b,powered:1b,Rotation:[180f,0f]}");
            Scene.command(world, "execute at @p run summon zombie ~1 ~ ~12 {NoAI:1b,Rotation:[180f,0f]}");
            Scene.command(world, "item replace entity @e[type=zombie,limit=1] armor.chest with diamond_chestplate"
                    + "[enchantments={protection:1},trim={material:\"gold\",pattern:\"coast\"}]");
            Scene.command(world, "item replace entity @e[type=zombie,limit=1] armor.head with iron_helmet[enchantments={protection:1}]");
            context.runOnClient(minecraft -> {
                var resting = FarPlayerTracker.get().resting();
                var player = minecraft.player;
                resting.fake(SKIN, new GameProfile(UUIDUtil.createOfflinePlayerUUID(SKIN), SKIN),
                        SharedPositions.dimensionId(minecraft.level.dimension()), player.getX() + 3, player.getY(),
                        player.getZ() + 12, 180f, 10, null, null);
                resting.dressFake(SKIN, "Notch");
            });
            context.waitTicks(100);
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            try {
                Scene.configure(context, config -> config.mobLods = false);
                context.waitTicks(10);
                Scene.screenshot(context, this, "models");

                Scene.configure(context, config -> config.mobLods = true);
                context.runOnClient(minecraft -> LodRenderer.forceLevelForTests(2));
                // The levels are made and baked away from the game, a few textures handed over a frame.
                context.waitTicks(100);
                int game = context.computeOnClient(minecraft -> LodRenderer.gameFigures());
                int mod = context.computeOnClient(minecraft -> PolygonStats.figures(2));
                Scene.log("lodkinds: " + game + " of the game's entities and " + mod + " of the mod's figures at level 2");
                Scene.screenshot(context, this, "level2");
                Scene.configure(context, config -> config.debugLodColors = true);
                context.waitTicks(5);
                Scene.screenshot(context, this, "level2-colours");
                if (game < 3) throw new AssertionError("Only " + game + " of the game's entities at a simpler level");
            } finally {
                context.runOnClient(minecraft -> {
                    LodRenderer.forceLevelForTests(-1);
                    minecraft.options.fov().set(70);
                    FarPlayerTracker.get().resting().clearFakes();
                });
            }
        }
    }
}
