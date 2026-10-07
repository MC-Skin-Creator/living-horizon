package fr.clixmods.livinghorizon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.clixmods.livinghorizon.ambient.Ambience;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every string the mod shows exists in both languages: a missing one shows as its raw key. */
class LangTest {
    private static final Path LANG = Path.of("..", "..", "src", "main", "resources", "assets", "livinghorizon", "lang");
    private static final Path SOURCES = Path.of("..", "..", "src", "main", "java");

    private static Set<String> keys(String file) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(LANG.resolve(file), StandardCharsets.UTF_8)).getAsJsonObject();
        return new TreeSet<>(json.keySet());
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws IOException {
        assertEquals(keys("en_us.json"), keys("fr_fr.json"));
    }

    @Test
    void everySettingHasItsTexts() throws IOException {
        Set<String> keys = keys("en_us.json");
        // bool("name", ...), integer("name", ...), choice("name", ...) in the settings screen.
        Pattern option = Pattern.compile("(?:bool|integer|choice)\\(\"(\\w+)\"");
        String screen = Files.readString(SOURCES.resolve("fr/clixmods/livinghorizon/ui/FarConfigScreen.java"));
        Matcher match = option.matcher(screen);
        int found = 0;
        while (match.find()) {
            found++;
            String key = "livinghorizon.options." + match.group(1);
            assertTrue(keys.contains(key), key);
            assertTrue(keys.contains(key + ".tooltip"), key + ".tooltip");
        }
        assertTrue(found >= 12, "only " + found + " settings found");

        // Every literal translation key written out in the sources.
        Pattern literal = Pattern.compile("\"(livinghorizon\\.[a-zA-Z.]+[a-zA-Z])\"");
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = literal.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.endsWith(".json") || key.endsWith(".accesswidener")) continue; // file names
                    assertTrue(keys.contains(key), key + " in " + file.getFileName());
                }
            }
        }
        for (Ambience.Type type : Ambience.Type.values()) {
            assertTrue(keys.contains("livinghorizon.options.birdTypes." + type.id), type.id);
            assertTrue(keys.contains("livinghorizon.options.birdTypes." + type.id + ".tooltip"), type.id + ".tooltip");
        }
        for (String suffix : List.of("title", "search", "shared", "shared.tooltip", "local.tooltip", "all", "none", "defaults")) {
            assertTrue(keys.contains("livinghorizon.options.mobTypes." + suffix), suffix);
        }
    }
}
