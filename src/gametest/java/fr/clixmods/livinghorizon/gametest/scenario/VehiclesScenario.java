package fr.clixmods.livinghorizon.gametest.scenario;

import fr.clixmods.livinghorizon.FarConfig;
import fr.clixmods.livinghorizon.gametest.Scenario;
import fr.clixmods.livinghorizon.gametest.Scene;
import fr.clixmods.livinghorizon.render.impostor.PolygonStats;
import fr.clixmods.livinghorizon.track.FarPlayerTracker;
import fr.clixmods.livinghorizon.track.MobMemory;
import fr.clixmods.livinghorizon.ui.MobList;
import fr.clixmods.livinghorizon.ui.MobTypesScreen;
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
 * rider, is listed but not shown until chosen; that part, and the list's two tabs, need
 * nothing else.
 *
 * <p>With Immersive Aircraft ({@code run.sh vehicles --with immersive-aircraft}), its biplane
 * and airship, which their pilot steers, are listed and shown before one is met. Left behind
 * until the server no longer sends them, their copies stand where they were, past the mobs'
 * distance (the entities' own reaches farther), as pictures with Simplified Entities. With the
 * list open over the paused game, switching the airship off and on shows at once. Without the
 * mod, that part is skipped.
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
        // From the title screen, before a world: from 26.1 the items have no components yet there.
        boolean bare = context.computeOnClient(minecraft -> {
            try {
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PIG_SPAWN_EGG);
                return false;
            } catch (RuntimeException unbound) {
                return true;
            }
        });
        Scene.log("vehicles: items without components on the title screen " + bare);
        context.setScreen(() -> new MobTypesScreen(null));
        context.waitTicks(2);
        boolean titleList = context.computeOnClient(minecraft -> minecraft.screen instanceof MobTypesScreen);
        context.setScreen(() -> null);
        if (!titleList) throw new AssertionError("vehicles: the list of mobs did not open from the title screen");
        try (TestSingleplayerContext world = Scene.flatWorld(context)) {
            if (aircraft) {
                // Listed, and shown, before one is ever met: their renderers say what they draw.
                String unmet = context.computeOnClient(minecraft -> {
                    List<String> list = MobList.all().stream().map(type -> EntityType.getKey(type).toString()).toList();
                    FarConfig.adoptNewMobs(MobList.all());
                    StringBuilder wrong = new StringBuilder();
                    for (String id : AIRCRAFT) {
                        if (!list.contains(id)) wrong.append(' ').append(id).append(" is not listed before it is met;");
                        if (!FarConfig.get().mobTypes.contains(id)) wrong.append(' ').append(id).append(" is not shown before it is met;");
                    }
                    return wrong.toString();
                });
                if (!unmet.isEmpty()) throw new AssertionError("vehicles:" + unmet);
            }
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
                // As the game starts, and the Defaults button: the minecart stays off all the same.
                FarConfig.adoptNewMobs(MobList.all());
                if (config.mobTypes.contains(MINECART)) wrong.append(" the minecart is shown once the game starts again;");
                config.resetMobs(MobList.all());
                if (config.mobTypes.contains(MINECART)) wrong.append(" the minecart is shown after Defaults;");
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
            tabs(context, aircraft);
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
            // The mobs' distance stops short of them: the entities' own, unlimited, still reaches.
            Scene.configure(context, config -> config.mobMaxDistance = 100);
            Path pictures = Scene.screenshot(context, this, "remembered");
            int impostors = context.computeOnClient(minecraft -> PolygonStats.impostors());
            // From 80 blocks up: pictures baked from above, as a ghast's are.
            // On a barrier, invisible: a fall from there kills.
            Scene.command(world, "fill -1 19 -161 1 19 -159 minecraft:barrier");
            Scene.command(world, "tp @a 0 20 -160 0 22");
            context.waitTicks(60);
            Scene.screenshot(context, this, "from-above");
            int above = context.computeOnClient(minecraft -> PolygonStats.impostors());
            Scene.screenshotAtlas(context, this);
            Scene.command(world, "tp @a 0 -60 -160 0 0");
            context.waitTicks(20);
            Scene.log("vehicles: " + above + " drawn as pictures from above");
            if (above < AIRCRAFT.length) throw new AssertionError("vehicles: the aircraft are not pictures from above");
            Scene.configure(context, config -> config.impostorEntities = false);
            Scene.screenshot(context, this, "remembered-models");
            Scene.log("vehicles: " + impostors + " drawn as pictures with Simplified Entities");
            if (impostors < AIRCRAFT.length) throw new AssertionError("vehicles: the aircraft are not pictures past the simplification distance");
            menu(context);
            // Show Entities off: the aircraft go, whatever the mobs do.
            Path on = Scene.screenshot(context, this, "entities-on");
            Scene.configure(context, config -> config.distantEntities = false);
            Path off = Scene.screenshot(context, this, "entities-off");
            int gone = differing(on, off, 0);
            Scene.log("vehicles: Show Entities off changes " + gone + " pixels");
            if (gone < 50) throw new AssertionError("vehicles: Show Entities off left the aircraft drawn (" + gone + " pixels)");
        }
    }

    /**
     * The list of mobs open over the game, paused under it: switching the airship off and on
     * shows at once behind the list, on the right of the screen where the list leaves the view.
     */
    private void menu(ClientGameTestContext context) {
        context.setScreen(() -> new MobTypesScreen(null));
        context.clickScreenButton("livinghorizon.options.mobTypes.tab.entities");
        context.getInput().typeChars("airship");
        context.getInput().setCursorPos(4, 4);
        context.waitTicks(3);
        Path before = Scene.screenshot(context, this, "menu-shown");
        double[] at = context.computeOnClient(minecraft -> {
            int[] middle = minecraft.screen instanceof MobTypesScreen types ? types.switchAt(AIRCRAFT[1]) : null;
            double scale = minecraft.getWindow().getGuiScale();
            int column = minecraft.screen instanceof MobTypesScreen types ? types.columnWidth() : 0;
            return middle == null ? null : new double[]{middle[0] * scale, middle[1] * scale, column * scale};
        });
        if (at == null) throw new AssertionError("vehicles: the airship's switch is not on screen");
        context.getInput().setCursorPos(at[0], at[1]);
        context.getInput().pressMouse(0);
        context.getInput().setCursorPos(4, 4);
        context.waitTicks(2);
        Path off = Scene.screenshot(context, this, "menu-hidden");
        context.getInput().setCursorPos(at[0], at[1]);
        context.getInput().pressMouse(0);
        context.getInput().setCursorPos(4, 4);
        context.waitTicks(2);
        Path on = Scene.screenshot(context, this, "menu-shown-again");
        context.setScreen(() -> null);
        int gone = differing(before, off, (int) at[2]), back = differing(before, on, (int) at[2]);
        Scene.log("vehicles: switching the airship off changes " + gone + " pixels behind the list, back on " + back);
        if (gone < 50) throw new AssertionError("vehicles: the airship did not leave at once (" + gone + " pixels)");
        if (back > gone / 4) throw new AssertionError("vehicles: the airship did not come back at once (" + back + " pixels off)");
    }

    /**
     * The list of mobs, on each of its tabs: the vehicles met under the entities', with the boats,
     * never under the mobs'. Larger, to be read on the screenshots.
     */
    private void tabs(ClientGameTestContext context, boolean aircraft) {
        context.runOnClient(minecraft -> minecraft.options.guiScale().set(2));
        context.setScreen(() -> new MobTypesScreen(null));
        // Off the list, where no tooltip covers it.
        context.getInput().setCursorPos(4, 4);
        context.waitTicks(5);
        java.util.Set<String> mobs = context.computeOnClient(minecraft -> listed(minecraft.screen));
        String cow = context.computeOnClient(minecraft -> switchText(minecraft.screen, "minecraft:cow"));
        Scene.screenshot(context, this, "tab-mobs");
        context.clickScreenButton("livinghorizon.options.mobTypes.tab.entities");
        // Over the entities' tab, which says what it holds.
        double[] tab = context.computeOnClient(minecraft -> {
            double scale = minecraft.getWindow().getGuiScale();
            int middle = minecraft.screen instanceof MobTypesScreen types ? types.columnWidth() / 2 : 180;
            return new double[]{(middle + 52) * scale, 36 * scale};
        });
        context.getInput().setCursorPos(tab[0], tab[1]);
        context.waitTicks(5);
        java.util.Set<String> entities = context.computeOnClient(minecraft -> listed(minecraft.screen));
        String boat = context.computeOnClient(minecraft -> switchText(minecraft.screen, "minecraft:oak_boat"));
        Scene.screenshot(context, this, "tab-entities");
        // The search on this tab: the minecart, off since nobody steers it.
        context.getInput().setCursorPos(4, 4);
        context.getInput().typeChars("cart");
        context.waitTicks(5);
        String minecart = context.computeOnClient(minecraft -> switchText(minecraft.screen, MINECART));
        Scene.screenshot(context, this, "tab-entities-search");
        context.setScreen(() -> new MobTypesScreen(null));
        context.clickScreenButton("livinghorizon.options.mobTypes.tab.mobs");
        // The search, on the tab back in front: each letter opens the screen again.
        context.getInput().typeChars("cow");
        context.waitTicks(5);
        java.util.Set<String> searched = context.computeOnClient(minecraft -> listed(minecraft.screen));
        context.setScreen(() -> null);
        context.runOnClient(minecraft -> minecraft.options.guiScale().set(1));
        Scene.log("vehicles: mobs tab " + mobs.size() + " kinds, entities tab " + entities);
        StringBuilder wrong = new StringBuilder();
        List<String> expected = new java.util.ArrayList<>(List.of(MINECART, "minecraft:oak_boat"));
        if (aircraft) expected.addAll(List.of(AIRCRAFT));
        for (String id : expected) {
            if (!entities.contains(id)) wrong.append(' ').append(id).append(" is not under the entities' tab;");
            if (mobs.contains(id)) wrong.append(' ').append(id).append(" is under the mobs' tab;");
        }
        if (!mobs.contains("minecraft:cow")) wrong.append(" the cow is not under the mobs' tab;");
        // A vehicle is shown or hidden; a mob roams or stands still.
        String shown = net.minecraft.client.resources.language.I18n.get("livinghorizon.options.mobTypes.mode.shown");
        String hidden = net.minecraft.client.resources.language.I18n.get("livinghorizon.options.mobTypes.mode.hidden");
        String roaming = net.minecraft.client.resources.language.I18n.get("livinghorizon.options.mobTypes.mode.animated");
        Scene.log("vehicles: switches '" + cow + "', '" + boat + "', '" + minecart + "'");
        if (!cow.endsWith(roaming)) wrong.append(" the cow's switch reads '").append(cow).append("';");
        if (!boat.endsWith(shown)) wrong.append(" the boat's switch reads '").append(boat).append("';");
        if (!minecart.endsWith(hidden)) wrong.append(" the minecart's switch reads '").append(minecart).append("';");
        if (entities.contains("minecraft:cow")) wrong.append(" the cow is under the entities' tab;");
        Scene.log("vehicles: searching cow lists " + searched);
        if (!searched.contains("minecraft:cow") || searched.size() > 4) wrong.append(" the search for cow lists ").append(searched).append(';');
        if (!wrong.isEmpty()) throw new AssertionError("vehicles:" + wrong);
    }

    private static String switchText(net.minecraft.client.gui.screens.Screen screen, String id) {
        return screen instanceof MobTypesScreen types ? types.switchText(id) : "";
    }

    private static java.util.Set<String> listed(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof MobTypesScreen types ? new java.util.TreeSet<>(types.listedKinds()) : java.util.Set.of();
    }

    /** Pixels that tell two screenshots apart, right of {@code fromX}. */
    private static int differing(Path a, Path b, int fromX) {
        try {
            BufferedImage one = ImageIO.read(a.toFile()), two = ImageIO.read(b.toFile());
            int count = 0;
            for (int y = 0; y < Math.min(one.getHeight(), two.getHeight()); y++) {
                for (int x = fromX; x < Math.min(one.getWidth(), two.getWidth()); x++) {
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
