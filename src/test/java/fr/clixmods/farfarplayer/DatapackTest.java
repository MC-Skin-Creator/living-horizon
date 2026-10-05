package fr.clixmods.farfarplayer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.JsonOps;
import fr.clixmods.farfarplayer.track.MobKinds;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compiles every function of the companion data pack with the game's own command
 * dispatcher, the way a server does on {@code /reload}: a typo there fails here instead
 * of failing silently in a server log.
 */
class DatapackTest {
    private static final Path PACK = Path.of("..", "..", "datapack");
    private static final Path FUNCTIONS = PACK.resolve("data/farfarplayer/function");

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyFunctionCompiles() throws IOException {
        CommandDispatcher<CommandSourceStack> dispatcher = new Commands(Commands.CommandSelection.DEDICATED,
                Commands.createValidationContext(VanillaRegistries.createLookup())).getDispatcher();
        CommandSourceStack source = Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER);

        List<Path> files;
        try (Stream<Path> listing = Files.list(FUNCTIONS)) {
            files = listing.filter(p -> p.toString().endsWith(".mcfunction")).toList();
        }
        assertFalse(files.isEmpty(), "no function found in " + FUNCTIONS.toAbsolutePath());
        for (Path file : files) {
            String name = file.getFileName().toString().replace(".mcfunction", "");
            // Throws with the line and the reason on the first command that does not parse.
            CommandFunction.fromLines(Identifier.fromNamespaceAndPath("farfarplayer", name),
                    dispatcher, source, Files.readAllLines(file));
        }
        assertEquals(7, files.size());
    }

    @Test
    void mobKindsAgreeWithTheMod() throws IOException {
        JsonObject tag = JsonParser.parseString(Files.readString(
                PACK.resolve("data/farfarplayer/tags/entity_type/remembered.json"))).getAsJsonObject();
        List<String> tagged = tag.getAsJsonArray("values").asList().stream().map(JsonElement::getAsString).toList();
        assertEquals(MobKinds.TYPES, tagged, "remembered.json");

        Pattern line = Pattern.compile("execute if entity @s\\[type=([a-z_:]+)\\] run scoreboard players set @s ffp\\.k (\\d+)");
        List<String> numbered = new ArrayList<>();
        for (String text : Files.readAllLines(FUNCTIONS.resolve("mob_kind.mcfunction"))) {
            Matcher match = line.matcher(text);
            if (!match.matches()) continue;
            assertEquals(numbered.size() + 1, Integer.parseInt(match.group(2)), text);
            numbered.add(match.group(1));
        }
        assertEquals(MobKinds.TYPES, numbered, "mob_kind.mcfunction");
        for (String type : MobKinds.TYPES) {
            assertTrue(EntityType.byString(type).isPresent(), type + " is not an entity type");
        }
    }

    @Test
    void packFormatMatchesTheGame() throws IOException {
        JsonObject mcmeta = JsonParser.parseString(Files.readString(PACK.resolve("pack.mcmeta"))).getAsJsonObject();
        PackMetadataSection pack = PackMetadataSection.SERVER_TYPE.codec()
                .parse(JsonOps.INSTANCE, mcmeta.get("pack")).getOrThrow();
        PackFormat game = PackFormat.of(SharedConstants.DATA_PACK_FORMAT_MAJOR, SharedConstants.DATA_PACK_FORMAT_MINOR);
        assertTrue(pack.supportedFormats().isValueInRange(game), pack.supportedFormats() + " does not cover " + game);
    }

    @Test
    void ridingPredicateParses() throws IOException {
        JsonElement json = JsonParser.parseString(Files.readString(
                PACK.resolve("data/farfarplayer/predicate/riding.json")));
        LootItemCondition.DIRECT_CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, VanillaRegistries.createLookup()), json)
                .getOrThrow();
    }
}
