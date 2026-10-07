package fr.clixmods.livinghorizon.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** One scripted check: builds a scene, looks at it, takes screenshots, may assert. */
public interface Scenario {
    /** Lower case, the value given to {@code -Plh.scenario}; screenshots are prefixed with it. */
    String name();

    void run(ClientGameTestContext context);
}
