package fr.clixmods.livinghorizon.debug;

// The F3 screen lists entries a mod can add from 1.21.9; before, the panel alone shows these lines.
//? if >=1.21.9 {
import fr.clixmods.livinghorizon.LivingHorizonClient;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The mod's line in the game's own F3 debug options (F3 + F6): off until the player turns it on,
 * then the debug panel's lines are added to the F3 screen.
 */
public final class DebugHudEntry implements DebugScreenEntry {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(LivingHorizonClient.MOD_ID, "living_horizon");
    public static final DebugHudEntry INSTANCE = new DebugHudEntry();

    private DebugHudEntry() {
    }

    @Override
    public void display(DebugScreenDisplayer displayer, Level level, LevelChunk clientChunk, LevelChunk serverChunk) {
        displayer.addToGroup(ID, DebugHud.f3Lines());
    }
}
//?}
