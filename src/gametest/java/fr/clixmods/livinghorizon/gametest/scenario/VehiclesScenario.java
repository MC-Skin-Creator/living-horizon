package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.ui.MobList;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Vehicles the list of mobs cannot name, met in the world. A minecart, which only seats its
 * rider, is listed once met but not shown until chosen; that part needs nothing else.
 *
 * <p>With Immersive Aircraft ({@code run.sh vehicles --with immersive-aircraft}), its biplane
 * and airship, which their pilot steers, are listed and shown from the start: left behind
 * until the server no longer sends them, their copies stand where they were. The check is on
 * the copies being built, and on the picture: the same view with the aircraft hidden in the
 * list must differ where they stand. Without it, that part is skipped.
 */
public final class VehiclesScenario implements Scenario {
    private static final String MINECART = "minecraft:minecart";
    private static final String[] AIRCRAFT = {"immersive_aircraft:biplane", "immersive_aircraft:airship"};

    @Override
    public String name() {
        return "vehicles";
    }

    @Override
    public void run(ClientGameTestContext context) {
        boolean aircraft = FabricLoader.getInstance().isModLoaded("immersive_aircraft");
        context.runOnClient(minecraft -> {
            minecraft.options.entityDistanceScaling().set(0.5);
            minecraft.options.renderDistance().set(16);
        });
        Scene.configure(context, config -> {
            config.impostors = false;
            config.hideOccludedMobs = false;
        });
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            // Past 32 blocks: a mob that vanishes closer is taken as gone for good.
            Scene.summon(world, "minecart", 0, 36, 0);
            if (aircraft) {
                Scene.summon(world, AIRCRAFT[0], -8, 40, 135);
                Scene.summon(world, AIRCRAFT[1], 10, 44, 90);
            }
            context.waitTicks(100);
            String listed = context.computeOnClient(minecraft -> {
                FarConfig config = FarConfig.get();
                Scene.log("known vehicles: " + config.knownVehicles);
                StringBuilder wrong = new StringBuilder();
                if (!config.knownVehicles.contains(MINECART)) wrong.append(" the minecart was not found a vehicle;");
                if (config.mobTypes.contains(MINECART)) wrong.append(" the minecart is shown without being chosen;");
                if (config.knownVehicles.stream().anyMatch(id -> id.endsWith("_boat") || id.endsWith("_raft"))) {
                    wrong.append(" a boat was taken for a new vehicle;");
                }
                List<String> list = MobList.all().stream().map(type -> EntityType.getKey(type).toString()).toList();
                if (!list.contains(MINECART)) wrong.append(" the minecart is not in the list of mobs;");
                if (aircraft) {
                    for (String id : AIRCRAFT) {
                        if (!config.knownVehicles.contains(id)) wrong.append(' ').append(id).append(" was not found a vehicle;");
                        if (!config.mobTypes.contains(id)) wrong.append(' ').append(id).append(" is not shown from the start;");
                        if (!list.contains(id)) wrong.append(' ').append(id).append(" is not in the list of mobs;");
                    }
                }
                return wrong.toString();
            });
            if (!listed.isEmpty()) throw new AssertionError("vehicles:" + listed);
            if (!aircraft) {
                Scene.log("vehicles: Immersive Aircraft not loaded, only the minecart was checked (run with --with immersive-aircraft)");
                return;
            }
            context.runOnClient(minecraft -> minecraft.options.fov().set(30));
            Scene.screenshot(context, this, "near");

            // Walk away: the copies take over once the server no longer sends the aircraft.
            Scene.command(world, "tp @a 0 ~ -160 0 0");
            context.waitTicks(300);
            String built = context.computeOnClient(minecraft -> {
                MobMemory memory = FarPlayerTracker.get().mobs();
                StringBuilder missing = new StringBuilder();
                for (String id : AIRCRAFT) {
                    boolean live = memory.live().stream().anyMatch(entity -> EntityType.getKey(entity.getType()).toString().equals(id));
                    MobMemory.Remembered copy = memory.shown().stream().filter(mob -> mob.type().equals(id)).findFirst().orElse(null);
                    Entity puppet = copy == null ? null : copy.puppet();
                    Scene.log(id + ": sent by the server " + live + ", copy "
                            + (puppet == null ? "none" : "at " + puppet.position() + " facing " + puppet.getYRot()));
                    if (puppet == null) missing.append(' ').append(id);
                }
                return missing.toString();
            });
            if (!built.isEmpty()) throw new AssertionError("vehicles: no copy of" + built);
            Path shown = Scene.screenshot(context, this, "remembered");
            Scene.configure(context, config -> config.mobTypes.removeAll(List.of(AIRCRAFT)));
            // The copies drawn are chosen again once a second.
            context.waitTicks(30);
            Path hidden = Scene.screenshot(context, this, "hidden");
            int drawn = differing(shown, hidden);
            Scene.log("vehicles: " + drawn + " pixels drawn by the copies");
            if (drawn < 50) throw new AssertionError("vehicles: the copies of the aircraft were not drawn (" + drawn + " pixels)");
        }
    }

    /** Pixels that tell two screenshots apart. */
    private static int differing(Path a, Path b) {
        try {
            BufferedImage one = ImageIO.read(a.toFile()), two = ImageIO.read(b.toFile());
            int count = 0;
            for (int y = 0; y < Math.min(one.getHeight(), two.getHeight()); y++) {
                for (int x = 0; x < Math.min(one.getWidth(), two.getWidth()); x++) {
                    int p = one.getRGB(x, y), q = two.getRGB(x, y);
                    int d = Math.abs((p >> 16 & 255) - (q >> 16 & 255)) + Math.abs((p >> 8 & 255) - (q >> 8 & 255))
                            + Math.abs((p & 255) - (q & 255));
                    if (d > 40) count++;
                }
            }
            return count;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
