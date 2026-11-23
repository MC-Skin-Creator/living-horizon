package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * The daily life of players who logged off, on a small homestead: one crouched over the wheat,
 * one by a cow with wheat in hand, one rummaging in a chest, one reading by a lectern inside a
 * hut whose door opens as they walk in and shuts behind them, one eating; three in a circle round
 * the campfire in the evening; one waving at you the first time you come by, and one looking at
 * a block placed beside them. A screenshot of each.
 */
public final class DailyLifeScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;
    private static final int G = GROUND, S = GROUND - 1;

    @Override
    public String name() {
        return "dailylife";
    }

    @Override
    public void run(ClientGameTestContext context) {
        Scene.configure(context, config -> {
            config.offlineLife = true;
            config.offlineRoam = 16;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // The field, the pen, the chest, the hut with its lectern and door, the campfire.
            Scene.command(world, "fill 4 " + S + " 6 9 " + S + " 7 farmland");
            Scene.command(world, "fill 4 " + G + " 6 9 " + G + " 7 wheat[age=7]");
            Scene.command(world, "summon cow 0.5 " + G + " -6.5 {NoAI:1b}");
            Scene.command(world, "setblock -5 " + G + " 3 chest");
            // Its floor the ground, two blocks of room inside, the roof above.
            Scene.command(world, "fill -14 " + S + " -4 -10 " + (G + 2) + " 0 oak_planks hollow");
            Scene.command(world, "setblock -10 " + G + " -2 oak_door[facing=east,half=lower]");
            Scene.command(world, "setblock -10 " + (G + 1) + " -2 oak_door[facing=east,half=upper]");
            Scene.command(world, "setblock -13 " + G + " -3 lectern");
            Scene.command(world, "setblock 12 " + G + " -8 campfire");
            context.waitTicks(20);

            spawn(context, world, "Gardener", 6.5, 9.5);
            spawn(context, world, "Shepherd", 0.5, -2.5);
            spawn(context, world, "Hoarder", -5.5, 6.5);
            spawn(context, world, "Reader", -6.5, -2.5);
            spawn(context, world, "Eater", 2.5, 2.5);
            Scene.command(world, "tp @a 0.5 " + G + " 30.5 180 10");
            context.waitTicks(100);

            check(context, world, "Gardener", "garden", 1, 1.5, p -> "garden".equals(p.chore()));
            check(context, world, "Shepherd", "tend", 1.5, 1, p -> "tend".equals(p.chore()));
            check(context, world, "Hoarder", "chest", 1.5, 1, p -> "chest".equals(p.chore()));
            check(context, world, "Eater", "eat", 1, 1, RestingPuppet::eating);

            // The reader walks into the hut: the door opens as they go through, and shuts after them.
            act(context, "Reader", "read");
            Scene.command(world, "tp @a -4.5 " + G + " -12.5 0 10");
            BlockPos door = new BlockPos(-10, G, -2);
            boolean opened = false;
            for (int i = 0; i < 400 && !opened; i++) {
                context.waitTick();
                opened = context.computeOnClient(minecraft -> minecraft.level.getBlockState(door).getValue(DoorBlock.OPEN));
            }
            Scene.log("dailylife: the hut's door " + (opened ? "opened" : "never opened"));
            if (!opened) throw new AssertionError("dailylife: the hut's door never opened");
            Scene.command(world, "tp @a -4.5 " + (G + 1) + " -6.5 facing -10 " + (G + 1) + " -2");
            context.waitTicks(2);
            Scene.screenshot(context, this, "door");
            waitFor(context, "Reader", p -> "read".equals(p.chore()), 400);
            boolean shut = false;
            for (int i = 0; i < 100 && !shut; i++) {
                context.waitTick();
                shut = !context.computeOnClient(minecraft -> minecraft.level.getBlockState(door).getValue(DoorBlock.OPEN));
            }
            Scene.log("dailylife: the hut's door " + (shut ? "shut again" : "left open"));
            if (!shut) throw new AssertionError("dailylife: the hut's door was left open");
            Scene.command(world, "setblock -10 " + (G + 1) + " -1 air");
            Scene.command(world, "setblock -10 " + (G + 1) + " -3 air");
            Scene.command(world, "tp @a -7.5 " + (G + 1) + " -2.5 facing -13 " + G + " -2.5");
            context.waitTicks(10);
            Scene.screenshot(context, this, "read");

            // You come by: the gardener, who never saw you this session, waves.
            Scene.command(world, "tp @a 6.5 " + G + " 18.5 facing 6.5 " + (G + 1) + " 8.5");
            boolean waved = false;
            for (int i = 0; i < 100 && !waved; i++) {
                context.waitTick();
                waved = puppet(context, "Gardener", RestingPuppet::greeting) == Boolean.TRUE;
                if (!waved) waved = anyone(context, RestingPuppet::greeting);
            }
            Scene.log("dailylife: " + (waved ? "a wave" : "no wave"));
            context.waitTicks(4);
            Scene.screenshot(context, this, "wave");
            if (!waved) throw new AssertionError("dailylife: nobody waved");

            // A block placed beside the shepherd: they look at it.
            Scene.command(world, "setblock 2 " + G + " -2 gold_block");
            context.waitTicks(3);
            boolean looked = anyone(context, RestingPuppet::noticing);
            Scene.log("dailylife: a block placed, " + (looked ? "looked at" : "not looked at"));
            if (!looked) throw new AssertionError("dailylife: nobody looked at the block placed");

            // The evening: three by the campfire, in a circle round it.
            command(context, "livinghorizon debug offline test clear");
            context.waitTicks(10);
            spawn(context, world, "Camper0", 9.5, -6.5);
            spawn(context, world, "Camper1", 15.5, -6.5);
            spawn(context, world, "Camper2", 12.5, -3.5);
            Scene.command(world, "tp @a 12.5 " + G + " 4.5 180 15");
            Scene.command(world, "time set 11500");
            context.waitTicks(100);
            String circle = null;
            for (int i = 0; i < 300 && circle == null; i++) {
                context.waitTicks(10);
                circle = context.computeOnClient(minecraft -> {
                    for (RestingPuppet puppet : puppets(minecraft)) {
                        int round = 0;
                        for (RestingPuppet mate : puppet.chatMates()) if (mate.chatting() && mate.warming()) round++;
                        if (round >= 3) return round + " round the fire";
                    }
                    return null;
                });
            }
            Scene.log("dailylife: evening, " + circle);
            Scene.command(world, "tp @a 12.5 " + (G + 2) + " -0.5 facing 12.5 " + G + " -8.5");
            context.waitTicks(5);
            Scene.screenshot(context, this, "fire");
            if (circle == null) throw new AssertionError("dailylife: never three round the fire");
            command(context, "livinghorizon debug offline test clear");
        }
    }

    /** A test player who logged off, there. */
    private static void spawn(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double z) {
        Scene.command(world, "tp @a " + x + " " + G + " " + z);
        context.waitTicks(3);
        command(context, "livinghorizon debug offline test spawn " + name);
        context.waitTicks(2);
    }

    /** Sent to a chore, then looked at from 3 blocks off along {@code dx, dz} once at it. */
    private void check(ClientGameTestContext context, TestSingleplayerContext world, String name, String chore,
                       double dx, double dz, Function<RestingPuppet, Boolean> doing) {
        act(context, name, chore);
        waitFor(context, name, doing, 400);
        double[] at = context.computeOnClient(minecraft -> {
            RestingPuppet puppet = find(minecraft, name);
            return puppet == null ? new double[]{0, G, 0} : new double[]{puppet.getX(), puppet.getY(), puppet.getZ()};
        });
        double length = Math.hypot(dx, dz);
        double fromX = at[0] + dx / length * 3, fromZ = at[2] + dz / length * 3;
        Scene.command(world, "tp @a " + fromX + " " + (G + 0.4) + " " + fromZ + " facing " + at[0] + " " + (at[1] + 1) + " " + at[2]);
        context.waitTicks(16);
        Scene.screenshot(context, this, chore);
    }

    private static void act(ClientGameTestContext context, String name, String chore) {
        boolean sent = false;
        for (int i = 0; i < 600 && !sent; i++) {
            context.waitTick();
            sent = puppet(context, name, p -> p.act(chore)) == Boolean.TRUE;
        }
        String state = puppet(context, name, RestingPuppet::state);
        Scene.log("dailylife: " + name + (sent ? " goes to " : " could not go to ") + chore + ": " + state);
        if (!sent) throw new AssertionError("dailylife: " + name + " could not go to " + chore + ": " + state);
    }

    private static void waitFor(ClientGameTestContext context, String name, Function<RestingPuppet, Boolean> doing, int ticks) {
        boolean there = false;
        for (int i = 0; i < ticks && !there; i++) {
            context.waitTick();
            there = puppet(context, name, doing) == Boolean.TRUE;
        }
        String state = puppet(context, name, RestingPuppet::state);
        Scene.log("dailylife: " + name + (there ? " at it: " : " never at it: ") + state);
        if (!there) throw new AssertionError("dailylife: " + name + " never at it: " + state);
    }

    private static <T> @Nullable T puppet(ClientGameTestContext context, String name, Function<RestingPuppet, T> read) {
        return context.computeOnClient(minecraft -> {
            RestingPuppet puppet = find(minecraft, name);
            return puppet == null ? null : read.apply(puppet);
        });
    }

    private static boolean anyone(ClientGameTestContext context, Function<RestingPuppet, Boolean> read) {
        return context.computeOnClient(minecraft -> {
            for (RestingPuppet puppet : puppets(minecraft)) if (read.apply(puppet)) return true;
            return false;
        });
    }

    private static @Nullable RestingPuppet find(Minecraft minecraft, String name) {
        for (RestingPuppet puppet : puppets(minecraft)) {
            if (puppet.getName().getString().equalsIgnoreCase(name)) return puppet;
        }
        return null;
    }

    private static java.util.List<RestingPuppet> puppets(Minecraft minecraft) {
        java.util.List<RestingPuppet> all = new java.util.ArrayList<>();
        RestingPlayers resting = FarPlayerTracker.get().resting();
        for (RestingPlayers.Spot spot : resting.spots()) {
            if (minecraft.level != null) all.add(resting.puppet(minecraft.level, spot));
        }
        return all;
    }

    private static void command(ClientGameTestContext context, String command) {
        context.runOnClient(minecraft -> minecraft.player.connection.sendCommand(command));
    }
}
