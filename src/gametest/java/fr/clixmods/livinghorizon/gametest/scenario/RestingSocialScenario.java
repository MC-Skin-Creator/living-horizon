package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.RestingPlayers;
import fr.clixmods.livinghorizon.track.RestingPuppet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Players who logged off, near the camera, among themselves: four close together, three or more
 * of them gather and chat in a circle, taking turns to speak. In the evening, by a campfire
 * and a furnace, they stand in front of them, each on a spot of its own. In the rain, those who
 * mind it walk under a roof, and stay there.
 */
public final class RestingSocialScenario implements Scenario {
    /** The flat world's grass is the block below this one. */
    private static final int GROUND = -60;

    @Override
    public String name() {
        return "restingsocial";
    }

    @Override
    public void run(ClientGameTestContext context) {
        // Close round their spots, as in a village: they meet.
        Scene.configure(context, config -> {
            config.offlineLife = true;
            config.offlineRoam = 12;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            chat(context, world);
            command(context, "livinghorizon debug offline test clear");
            evening(context, world);
            command(context, "livinghorizon debug offline test clear");
            rain(context, world);
            command(context, "livinghorizon debug offline test clear");
        }
    }

    /**
     * Four of them 4 blocks apart: in time three or more chat in one circle, each turned to its
     * middle, near the others; and more than one of them speaks.
     */
    private void chat(ClientGameTestContext context, TestSingleplayerContext world) {
        for (int i = 0; i < 4; i++) {
            Scene.command(world, "tp @a " + (i * 4 - 6.5) + " " + GROUND + " 0.5 " + (i * 90) + " 0");
            context.waitTicks(5);
            command(context, "livinghorizon debug offline test spawn Chatter" + i);
        }
        Scene.command(world, "tp @a 0.5 " + GROUND + " -12.5 0 20");
        String group = null;
        for (int i = 0; i < 600 && group == null; i++) {
            context.waitTicks(10);
            if (i % 50 == 0) {
                Scene.log("restingsocial " + i * 10 + ": " + context.computeOnClient(minecraft -> {
                    StringBuilder line = new StringBuilder();
                    for (RestingPuppet puppet : puppets(minecraft)) {
                        line.append(puppet.getName().getString()).append(' ').append(puppet.blockPosition().toShortString())
                                .append(' ').append(puppet.state()).append(", chat of ").append(puppet.chatMates().size())
                                .append("; ");
                    }
                    return line.toString();
                }));
            }
            group = context.computeOnClient(minecraft -> {
                for (RestingPuppet puppet : puppets(minecraft)) {
                    List<RestingPuppet> mates = new ArrayList<>();
                    for (RestingPuppet mate : puppet.chatMates()) if (mate.chatting()) mates.add(mate);
                    if (mates.size() < 3) continue;
                    StringBuilder line = new StringBuilder(mates.size() + " chatting:");
                    for (RestingPuppet mate : mates) line.append(' ').append(mate.getName().getString());
                    return line.toString();
                }
                return null;
            });
        }
        Scene.log("restingsocial chat: " + group);
        if (group == null) throw new AssertionError("restingsocial: never three or more chatting together");
        // A while of it: who speaks, and how they stand.
        java.util.Set<String> speakers = new java.util.HashSet<>();
        // Turned away twice in a row, not just come and still turning.
        java.util.Set<String> away = new java.util.HashSet<>();
        String wrong = null;
        for (int i = 0; i < 40 && wrong == null; i++) {
            context.waitTicks(10);
            wrong = context.computeOnClient(minecraft -> {
                java.util.Set<String> turned = new java.util.HashSet<>();
                String found = null;
                for (RestingPuppet puppet : puppets(minecraft)) {
                    if (!puppet.chatting()) continue;
                    RestingPuppet speaker = puppet.speaker();
                    if (speaker != null) speakers.add(speaker.getName().getString());
                    double x = 0, z = 0;
                    int there = 0;
                    for (RestingPuppet mate : puppet.chatMates()) {
                        if (!mate.chatting()) continue;
                        x += mate.getX();
                        z += mate.getZ();
                        there++;
                        if (puppet.distanceTo(mate) > 5) return "too far apart: " + puppet.distanceTo(mate);
                    }
                    x /= there;
                    z /= there;
                    float toward = (float) Math.toDegrees(Math.atan2(-(x - puppet.getX()), z - puppet.getZ()));
                    float off = Math.abs(Mth.wrapDegrees(toward - puppet.yBodyRot));
                    if (off > 50 && Math.hypot(x - puppet.getX(), z - puppet.getZ()) > 0.5) {
                        String name = puppet.getName().getString();
                        turned.add(name);
                        if (away.contains(name)) found = name + " turned " + off + " degrees from the middle";
                    }
                }
                away.clear();
                away.addAll(turned);
                return found;
            });
        }
        Scene.log("restingsocial speakers: " + speakers);
        if (wrong != null) throw new AssertionError("restingsocial: " + wrong);
        if (speakers.size() < 2) throw new AssertionError("restingsocial: only " + speakers + " spoke");
        Scene.screenshot(context, this, "chat");
    }

    /** A campfire and a furnace, three of them around, the evening: in time they stand in front of them. */
    private void evening(ClientGameTestContext context, TestSingleplayerContext world) {
        Scene.command(world, "setblock 40 " + GROUND + " 0 campfire");
        Scene.command(world, "setblock 44 " + GROUND + " 0 furnace[facing=west]");
        for (int i = 0; i < 3; i++) {
            Scene.command(world, "tp @a " + (36.5 + i * 4) + " " + GROUND + " -5.5 0 0");
            context.waitTicks(5);
            command(context, "livinghorizon debug offline test spawn Warm" + i);
        }
        Scene.command(world, "tp @a 42.5 " + GROUND + " -10.5 0 15");
        Scene.command(world, "time set 11500");
        String found = null;
        for (int i = 0; i < 300 && found == null; i++) {
            context.waitTicks(10);
            found = context.computeOnClient(minecraft -> {
                List<String> by = new ArrayList<>();
                List<net.minecraft.core.BlockPos> spots = new ArrayList<>();
                for (RestingPuppet puppet : puppets(minecraft)) {
                    if (!puppet.warming()) continue;
                    double fire = Math.min(Math.hypot(puppet.getX() - 40.5, puppet.getZ() - 0.5),
                            Math.hypot(puppet.getX() - 44.5, puppet.getZ() - 0.5));
                    // Two blocks out round the campfire, beside the furnace.
                    if (fire > 2.8) continue;
                    if (spots.contains(puppet.blockPosition())) throw new AssertionError("restingsocial: two on one spot by the fire");
                    spots.add(puppet.blockPosition());
                    double fx = Math.hypot(puppet.getX() - 40.5, puppet.getZ() - 0.5)
                            < Math.hypot(puppet.getX() - 44.5, puppet.getZ() - 0.5) ? 40.5 : 44.5;
                    float toward = (float) Math.toDegrees(Math.atan2(-(fx - puppet.getX()), 0.5 - puppet.getZ()));
                    by.add(puppet.getName().getString() + " at " + puppet.blockPosition().toShortString() + ", "
                            + Math.abs(Mth.wrapDegrees(toward - puppet.yBodyRot)) + " degrees off facing it, " + puppet.state());
                }
                return by.isEmpty() ? null : String.join(", ", by);
            });
        }
        Scene.log("restingsocial evening: " + found);
        if (found == null) throw new AssertionError("restingsocial: nobody by the fire in the evening");
        context.waitTicks(40);
        Scene.log("restingsocial evening, 2 s later: " + context.computeOnClient(minecraft -> {
            StringBuilder line = new StringBuilder();
            for (RestingPuppet puppet : puppets(minecraft)) {
                if (!puppet.warming()) continue;
                double fx = Math.hypot(puppet.getX() - 40.5, puppet.getZ() - 0.5)
                            < Math.hypot(puppet.getX() - 44.5, puppet.getZ() - 0.5) ? 40.5 : 44.5;
                float toward = (float) Math.toDegrees(Math.atan2(-(fx - puppet.getX()), 0.5 - puppet.getZ()));
                float off = Math.abs(Mth.wrapDegrees(toward - puppet.yBodyRot));
                if (off > 40) throw new AssertionError("restingsocial: " + puppet.getName().getString() + " turned " + off + " degrees from the fire");
                line.append(puppet.getName().getString()).append(' ').append(off).append("; ");
            }
            return line.toString();
        }));
        Scene.screenshot(context, this, "evening");
        Scene.command(world, "time set noon");
    }

    /** A roof in the open, six of them around it, rain: those who mind it walk under it. */
    private void rain(ClientGameTestContext context, TestSingleplayerContext world) {
        Scene.command(world, "fill 78 " + (GROUND + 3) + " -3 84 " + (GROUND + 3) + " 3 oak_planks");
        for (int i = 0; i < 6; i++) {
            Scene.command(world, "tp @a " + (72.5 + i * 3) + " " + GROUND + " -9.5 0 0");
            context.waitTicks(5);
            command(context, "livinghorizon debug offline test spawn Rain" + i);
        }
        Scene.command(world, "tp @a 81.5 " + GROUND + " -20.5 0 10");
        context.waitTicks(100);
        Scene.command(world, "weather rain");
        String dry = null;
        for (int i = 0; i < 300 && dry == null; i++) {
            context.waitTicks(10);
            dry = context.computeOnClient(minecraft -> {
                if (!minecraft.level.isRaining()) return null;
                List<String> under = new ArrayList<>();
                for (RestingPuppet puppet : puppets(minecraft)) {
                    if (!puppet.getName().getString().startsWith("Rain") || !puppet.sheltering()) continue;
                    boolean roofed = puppet.getX() > 78 && puppet.getX() < 85 && puppet.getZ() > -3 && puppet.getZ() < 4;
                    if (roofed && !minecraft.level.isRainingAt(net.minecraft.core.BlockPos.containing(puppet.getEyePosition()))) {
                        under.add(puppet.getName().getString());
                    }
                }
                return under.isEmpty() ? null : String.join(", ", under);
            });
        }
        String who = context.computeOnClient(minecraft -> {
            StringBuilder line = new StringBuilder();
            for (RestingPuppet puppet : puppets(minecraft)) {
                if (!puppet.getName().getString().startsWith("Rain")) continue;
                line.append(puppet.getName().getString()).append(puppet.sheltering() ? " (minds the rain) " : " ")
                        .append(puppet.blockPosition().toShortString()).append("; ");
            }
            return line.toString();
        });
        Scene.log("restingsocial rain: under the roof " + dry + "; " + who);
        if (dry == null) throw new AssertionError("restingsocial: nobody took shelter from the rain");
        context.waitTicks(60);
        Scene.screenshot(context, this, "rain");
        Scene.command(world, "weather clear");
    }

    /** Degrees between the way {@code a}'s body faces and the way to {@code b}. */
    private static float facing(RestingPuppet a, RestingPuppet b) {
        float toward = (float) Math.toDegrees(Math.atan2(-(b.getX() - a.getX()), b.getZ() - a.getZ()));
        return Math.abs(Mth.wrapDegrees(toward - a.yBodyRot));
    }

    private static List<RestingPuppet> puppets(Minecraft minecraft) {
        List<RestingPuppet> all = new ArrayList<>();
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
