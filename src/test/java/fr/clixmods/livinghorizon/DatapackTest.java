package fr.clixmods.livinghorizon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.JsonOps;
import fr.clixmods.livinghorizon.track.MobKinds;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
//? if >=1.21.9 {
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
//?}
//? if >=1.21.11 {
import net.minecraft.server.permissions.LevelBasedPermissionSet;
//?} else {
/*import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
*///?}
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
    private static final Path FUNCTIONS = PACK.resolve("data/livinghorizon/function");

    @BeforeAll
    static void boot() {
        GameBoot.start();
    }

    /** The game's built-in registries. */
    private static HolderLookup.Provider lookup() {
        //? if >=26.3 {
        /*// 26.3 builds the world's registries apart from the reloadable ones, which the
        // functions must not see: they name the pack's own predicate, unknown to them.
        return VanillaRegistries.createWorldLookup();
        *///?} else {
        return VanillaRegistries.createLookup();
        //?}
    }

    @Test
    void everyFunctionCompiles() throws IOException {
        CommandDispatcher<CommandSourceStack> dispatcher = new Commands(Commands.CommandSelection.DEDICATED,
                Commands.createValidationContext(lookup())).getDispatcher();
        //? if >=1.21.11 {
        CommandSourceStack source = Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER);
        //?} else {
        /*CommandSourceStack source = new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null,
                Commands.LEVEL_GAMEMASTERS, "livinghorizon", Component.empty(), null, null);
        *///?}

        List<Path> files;
        try (Stream<Path> listing = Files.list(FUNCTIONS)) {
            files = listing.filter(p -> p.toString().endsWith(".mcfunction")).toList();
        }
        assertFalse(files.isEmpty(), "no function found in " + FUNCTIONS.toAbsolutePath());
        for (Path file : files) {
            String name = file.getFileName().toString().replace(".mcfunction", "");
            // Throws with the line and the reason on the first command that does not parse.
            CommandFunction.fromLines(Identifier.fromNamespaceAndPath("livinghorizon", name),
                    dispatcher, source, Files.readAllLines(packFile("data/livinghorizon/function/" + file.getFileName())));
        }
        assertEquals(7, files.size());
    }

    /** A file of the pack as this version's zip has it: the build's copy when it wrote one. */
    private static Path packFile(String path) {
        Path own = Path.of(System.getProperty("livinghorizon.datapackOverrides")).resolve(path);
        return Files.exists(own) ? own : PACK.resolve(path);
    }

    /** The mob kinds of the mod this game version has: the newest are left out on older ones. */
    private static List<String> kinds() {
        List<String> missing = List.of(System.getProperty("livinghorizon.missingTypes", "").split(","));
        return MobKinds.TYPES.stream().filter(type -> !missing.contains(type)).toList();
    }

    @Test
    void mobKindsAgreeWithTheMod() throws IOException {
        JsonObject tag = JsonParser.parseString(Files.readString(
                packFile("data/livinghorizon/tags/entity_type/remembered.json"))).getAsJsonObject();
        List<String> tagged = tag.getAsJsonArray("values").asList().stream().map(JsonElement::getAsString).toList();
        assertEquals(kinds(), tagged, "remembered.json");

        Pattern line = Pattern.compile("execute if entity @s\\[type=([a-z_:]+)\\] run scoreboard players set @s lh\\.k (\\d+)");
        List<String> numbered = new ArrayList<>();
        for (String text : Files.readAllLines(packFile("data/livinghorizon/function/mob_kind.mcfunction"))) {
            Matcher match = line.matcher(text);
            if (!match.matches()) continue;
            assertEquals(MobKinds.TYPES.indexOf(match.group(1)) + 1, Integer.parseInt(match.group(2)), text);
            numbered.add(match.group(1));
        }
        assertEquals(kinds(), numbered, "mob_kind.mcfunction");
        for (String type : kinds()) {
            assertTrue(BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(type)).isPresent(), type + " is not an entity type");
        }
    }

    @Test
    void packFormatMatchesTheGame() throws IOException {
        JsonObject mcmeta = JsonParser.parseString(Files.readString(Path.of(System.getProperty("livinghorizon.mcmeta")))).getAsJsonObject();
        //? if >=1.21.9 {
        PackMetadataSection pack = PackMetadataSection.SERVER_TYPE.codec()
                .parse(JsonOps.INSTANCE, mcmeta.get("pack")).getOrThrow();
        PackFormat game = PackFormat.of(SharedConstants.DATA_PACK_FORMAT_MAJOR, SharedConstants.DATA_PACK_FORMAT_MINOR);
        assertTrue(pack.supportedFormats().isValueInRange(game), pack.supportedFormats() + " does not cover " + game);
        //?} else {
        /*// Before 1.21.9 a pack names a single format.
        int format = mcmeta.getAsJsonObject("pack").get("pack_format").getAsInt();
        assertTrue(format == SharedConstants.DATA_PACK_FORMAT, format + " is not " + SharedConstants.DATA_PACK_FORMAT);
        *///?}
    }

    @Test
    void ridingPredicateParses() throws IOException {
        JsonElement json = JsonParser.parseString(Files.readString(packFile("data/livinghorizon/predicate/riding.json")));
        // The codec was LootItemConditions' before 1.21, and a result threw differently before 1.20.5.
        //? if >=1.21 {
        LootItemCondition.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, lookup()), json).getOrThrow();
        //?} elif >=1.20.5 {
        /*net.minecraft.world.level.storage.loot.predicates.LootItemConditions.DIRECT_CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, lookup()), json).getOrThrow();
        *///?} elif >=1.20.2 {
        /*net.minecraft.world.level.storage.loot.predicates.LootItemConditions.CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, lookup()), json).getOrThrow(false, error -> { });
        *///?} else {
        /*// Gson read the conditions before 1.20.2; it throws on one it cannot read.
        net.minecraft.world.level.storage.loot.Deserializers.createConditionSerializer().create()
                .fromJson(json, net.minecraft.world.level.storage.loot.predicates.LootItemCondition.class);
        *///?}
    }
}
