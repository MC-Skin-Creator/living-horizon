package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.render.impostor.ImpostorKey;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * Which figures share a sheet, and a crowd larger than the pages. Zombies with different
 * names look the same and share one sheet, those with a helmet another, however worn by
 * the sun it is. Then more zombies than the pages hold, each in a helmet of its own
 * colour, asked for again and again: once the pages are full, the sheets stay the same,
 * none is baked over and over.
 */
public final class ImpostorSharingScenario implements Scenario {
    /** More figures than the pages hold: two pages of 128. */
    private static final int CROWD = 300;

    @Override
    public String name() {
        return "sharing";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 32;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            for (int i = 0; i < 8; i++) {
                String helmet = i % 2 == 0 ? "" : "," + helmet("iron_helmet", "");
                Scene.command(world, "execute at @p run summon zombie ~" + (i * 2 - 7) + " ~ ~20"
                        + " {NoAI:1b,PersistenceRequired:1b,CustomName:'\"Zombie " + i + "\"'" + helmet + "}");
            }
            context.waitTicks(20);
            int keys = context.computeOnClient(minecraft -> {
                Set<ImpostorKey> seen = new HashSet<>();
                for (Entity entity : minecraft.level.entitiesForRendering()) {
                    if (entity.getType() == EntityType.ZOMBIE) seen.add(ImpostorKey.of(entity));
                }
                Scene.log("sharing zombie keys=" + seen.size());
                return seen.size();
            });
            if (keys != 2) throw new AssertionError("Eight zombies, half in a helmet, should share 2 sheets, not " + keys);

            for (int i = 0; i < CROWD; i++) {
                int colour = 0x100000 + i * 0x0F0F;
                Scene.command(world, "execute at @p run summon zombie ~" + (i % 30 * 2 - 30) + " ~ ~" + (30 + i / 30 * 2)
                        + " {NoAI:1b,PersistenceRequired:1b," + helmet("leather_helmet", dyed(colour)) + "}");
            }
            context.waitTicks(20);
            // Asked for once a second, as the figures on hand are, until the pages are full.
            for (int round = 0; round < 150; round++) {
                context.runOnClient(ImpostorSharingScenario::requestAll);
                context.waitTicks(20);
                boolean full = context.computeOnClient(minecraft ->
                        ImpostorAtlas.failed() || ImpostorAtlas.waiting() == 0 && ImpostorAtlas.sheets() >= 256);
                if (full) break;
            }
            Set<ImpostorKey> before = context.computeOnClient(minecraft -> baked());
            for (int round = 0; round < 5; round++) {
                context.runOnClient(ImpostorSharingScenario::requestAll);
                context.waitTicks(20);
            }
            Set<ImpostorKey> after = context.computeOnClient(minecraft -> baked());
            boolean failed = context.computeOnClient(minecraft -> ImpostorAtlas.failed());
            Scene.log("sharing crowd sheets before=" + before.size() + " after=" + after.size()
                    + " same=" + before.equals(after) + " failed=" + failed);
            if (failed) throw new AssertionError("The impostor bake failed, see the log");
            if (!before.equals(after)) throw new AssertionError("A full atlas kept baking sheets over");
        }
    }

    private static void requestAll(Minecraft minecraft) {
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity && entity != minecraft.player) ImpostorAtlas.request(ImpostorKey.of(entity), entity);
        }
    }

    private static Set<ImpostorKey> baked() {
        Set<ImpostorKey> keys = new HashSet<>();
        for (ImpostorAtlas.Baked baked : ImpostorAtlas.baked()) keys.add(baked.key());
        return keys;
    }

    /** A helmet in the summon command's data, as this version writes it. */
    private static String helmet(String item, String components) {
        //? if >=1.21.5 {
        return "equipment:{head:{id:\"minecraft:" + item + "\",count:1" + components + "}}";
        //?} else
        /*return "ArmorItems:[{},{},{},{id:\"minecraft:" + item + "\",count:1" + components + "}]";*/
    }

    private static String dyed(int colour) {
        //? if >=1.21.5 {
        return ",components:{\"minecraft:dyed_color\":" + colour + "}";
        //?} else
        /*return ",components:{\"minecraft:dyed_color\":{rgb:" + colour + "}}";*/
    }
}
