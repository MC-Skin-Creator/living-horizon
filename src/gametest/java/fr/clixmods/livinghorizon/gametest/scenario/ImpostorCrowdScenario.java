package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * More looks than the pages may hold: horses, each in horse armour of its own colour, baked
 * larger than their box (their muzzle), asked for once a second as the figures on hand are.
 * Once the pages are full, the ones left out wait as models: nothing is baked over and over.
 */
public final class ImpostorCrowdScenario implements Scenario {
    private static final int HORSES = 320;

    @Override
    public String name() {
        return "crowd";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> config.impostors = true);
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            for (int i = 0; i < HORSES; i++) {
                int colour = 0x100000 + i * 0x0F0F;
                Scene.command(world, "execute at @p run summon horse ~" + (i % 32 * 3 - 48) + " ~ ~" + (30 + i / 32 * 3)
                        + " {NoAI:1b,PersistenceRequired:1b," + armour(colour) + "}");
            }
            context.waitTicks(20);
            // Until nothing waits any more, a few seconds running. Going round and round instead,
            // it ends after a while and the bakes below tell.
            int idle = 0;
            for (int round = 0; round < 400 && idle < 3; round++) {
                context.runOnClient(ImpostorCrowdScenario::requestAll);
                context.waitTicks(20);
                idle = context.computeOnClient(minecraft -> ImpostorAtlas.waiting()) == 0 ? idle + 1 : 0;
            }
            int before = context.computeOnClient(minecraft -> ImpostorAtlas.bakes());
            for (int round = 0; round < 5; round++) {
                context.runOnClient(ImpostorCrowdScenario::requestAll);
                context.waitTicks(20);
            }
            int after = context.computeOnClient(minecraft -> ImpostorAtlas.bakes());
            int sheets = context.computeOnClient(minecraft -> ImpostorAtlas.sheets());
            int waiting = context.computeOnClient(minecraft -> ImpostorAtlas.waiting());
            Scene.log("crowd sheets=" + sheets + " waiting=" + waiting + " bakes in 5 s once full=" + (after - before));
            Scene.command(world, "kill @e[type=horse]");
            context.waitTicks(40);
            if (after > before) throw new AssertionError("A full atlas kept baking: " + (after - before) + " bakes in 5 s");
        }
    }

    private static void requestAll(Minecraft minecraft) {
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity && entity != minecraft.player) ImpostorAtlas.request(ImpostorKey.of(entity), entity);
        }
    }

    /** Horse armour in the summon command's data, as this version writes it. */
    private static String armour(int colour) {
        //? if >=1.21.5 {
        return "equipment:{body:{id:\"minecraft:leather_horse_armor\",count:1,components:{\"minecraft:dyed_color\":" + colour + "}}}";
        //?} else
        /*return "body_armor_item:{id:\"minecraft:leather_horse_armor\",count:1,components:{\"minecraft:dyed_color\":{rgb:" + colour + "}}}";*/
    }
}
