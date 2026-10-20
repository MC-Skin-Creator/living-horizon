package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.ImpostorAtlas;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
//? if >=1.21.9
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.world.entity.Entity;

/**
 * Mannequins with skins, left behind until the mod draws its copies of them: each copy must
 * wear its mannequin's skin, never the default one. Two take a texture of the game's through
 * their profile, as a server's resource pack gives one; a third a player's name, whose skin
 * comes from Mojang when the network allows (logged, not checked).
 *
 * <p>The server sends mannequins as far as its view distance, which follows the render
 * distance here: their copies stand past the fog, out of sight without Voxy. The check is on
 * the copies' skins and the impostor page; the screenshot up close shows the skins expected.
 * Mannequins came in 1.21.9: nothing to do before.
 */
public final class MannequinsScenario implements Scenario {
    /** A texture of the game's for each skinned mannequin, by its place across the row. */
    private static final String[] TEXTURES = {"minecraft:entity/player/wide/kai", "minecraft:entity/player/slim/efe"};

    @Override
    public String name() {
        return "mannequins";
    }

    @Override
    public void run(ClientGameTestContext context) {
        //? if >=1.21.9 {
        context.runOnClient(minecraft -> minecraft.options.renderDistance().set(12));
        Scene.configure(context, config -> {
            config.impostors = true;
            config.impostorDistance = 100;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Past 32 blocks: a figure that vanishes closer is taken as gone for good.
            summon(world, -4, "{texture:\"" + TEXTURES[0] + "\"}");
            summon(world, 0, "{texture:\"" + TEXTURES[1] + "\",model:\"slim\"}");
            summon(world, 4, "\"Notch\"");
            context.waitTicks(100);
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "near");
            context.runOnClient(minecraft -> minecraft.options.fov().set(70));

            // Past the 192 blocks the server sends them at: the copies take over.
            Scene.command(world, "tp @a 0 ~ -220 0 0");
            context.waitTicks(300);
            String failure = context.computeOnClient(minecraft -> {
                MobMemory memory = FarPlayerTracker.get().mobs();
                Scene.log("mobs still sent by the server: " + memory.live().size());
                int checked = 0;
                StringBuilder wrong = new StringBuilder();
                for (MobMemory.Remembered mob : memory.shown()) {
                    if (!mob.type().equals("minecraft:mannequin")) continue;
                    Entity puppet = mob.puppet();
                    if (!(puppet instanceof ClientAvatarEntity avatar)) {
                        Scene.log("mannequin copy at " + mob.x() + " not built yet");
                        continue;
                    }
                    String skin = String.valueOf(avatar.getSkin().body().texturePath());
                    Scene.log("mannequin copy at x=" + Math.round(puppet.getX()) + " wears " + skin);
                    int place = (int) Math.round(puppet.getX() / 4) + 1;
                    if (place < 0 || place >= TEXTURES.length) continue;
                    checked++;
                    String expected = TEXTURES[place].substring("minecraft:".length());
                    if (!skin.contains(expected)) wrong.append(" x=").append(Math.round(puppet.getX())).append(" wears ").append(skin);
                }
                if (checked < TEXTURES.length) return "only " + checked + " textured mannequin copies were shown";
                return wrong.isEmpty() ? null : "mannequin copies without their skin:" + wrong;
            });
            context.runOnClient(minecraft -> Scene.log("mannequins impostor sheets=" + ImpostorAtlas.sheets()));
            Scene.screenshotAtlas(context, this);
            if (failure != null) throw new AssertionError(failure);
        }
        //?} else
        /*Scene.log("mannequins: none before 1.21.9");*/
    }

    //? if >=1.21.9 {
    /** A mannequin 40 blocks ahead of the player and {@code x} across, facing them, with this profile. */
    private static void summon(TestSingleplayerContext world, double x, String profile) {
        Scene.command(world, "execute at @p run summon minecraft:mannequin ~" + x + " ~ ~40 {Rotation:[180f,0f],profile:"
                + profile + "}");
    }
    //?}
}
