package fr.clixmods.livinghorizon.gametest;

import fr.clixmods.livinghorizon.gametest.scenario.ImpostorsScenario;
import fr.clixmods.livinghorizon.gametest.scenario.OptionsScreenScenario;
import fr.clixmods.livinghorizon.gametest.scenario.RememberedMobsScenario;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The client game tests: the real game, with the mod, driven by a script that takes
 * screenshots. Run with {@code ./gradlew runClientGameTest} (under {@code xvfb-run} without
 * a display); see {@code .claude/skills/game-test/SKILL.md}.
 *
 * <p>Each scenario is one {@link Scenario} in {@link #SCENARIOS}. {@code -Plh.scenario=a,b}
 * runs only those; the default runs them all. A new check is a new scenario added here.
 */
public final class LivingHorizonGameTests implements FabricClientGameTest {
    private static final List<Scenario> SCENARIOS = List.of(
            new ImpostorsScenario(),
            new RememberedMobsScenario(),
            new OptionsScreenScenario());

    @Override
    public void runTest(ClientGameTestContext context) {
        String wanted = System.getProperty("livinghorizon.scenario", "all").trim().toLowerCase(Locale.ROOT);
        Set<String> names = Arrays.stream(wanted.split(",")).map(String::trim).collect(Collectors.toSet());
        boolean all = names.isEmpty() || names.contains("all") || names.contains("");
        Set<String> known = SCENARIOS.stream().map(Scenario::name).collect(Collectors.toSet());
        for (String name : names) {
            if (!all && !known.contains(name)) {
                throw new AssertionError("Unknown scenario '" + name + "', known: " + known);
            }
        }
        for (Scenario scenario : SCENARIOS) {
            if (!all && !names.contains(scenario.name())) continue;
            Scene.log("scenario " + scenario.name() + " starts");
            Scene.reset(context);
            scenario.run(context);
            Scene.log("scenario " + scenario.name() + " done");
        }
    }
}
