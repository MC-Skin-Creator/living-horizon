import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The regression bench's hands inside the game. Loaded with
 * {@code -javaagent:bench-agent.jar=<folder>}, it reads the files the bench drops into that
 * folder, one line per action:
 *
 * <pre>
 * key &lt;scancode&gt; &lt;keycode&gt;   pressed, then released (games drawn through SDL)
 * text &lt;characters&gt;          typed (the same)
 * iris &lt;pack file&gt;           the shader pack Iris turns on at its next toggle (key K)
 * ready &lt;file&gt;               writes "1 n" once Sodium has built every section it holds (n of
 *                            them), "0 n" while it builds, "? why" when it cannot tell
 * fps &lt;seconds&gt; &lt;file&gt;       writes the game's frame rate once a second for that long
 * </pre>
 *
 * <p>Keys: SDL (Minecraft 26.3 on) hands a key to the game only while its window is the desktop's
 * foreground window, and the bench runs several games at once without ever taking the foreground
 * (it would take the user's mouse too). The keys go to the game's own keyboard handler on the
 * game's thread instead, as SDL would, by the game's own names: from 26.1 it is not obfuscated.
 *
 * <p>Shader packs: Iris reads its settings file again only on its reload key, which works with its
 * debug options alone, and they change how it draws. Its toggle key writes the settings it holds
 * in memory, so the pack is set there, shaders off, and the bench presses the toggle key. Iris's
 * classes keep their names on every version, Oculus's too.
 *
 * <p>Ready: a picture taken before the chunks round the camera are built misses them, and the far
 * terrain mods fill the hole with their own terrain, as still as a finished picture. Sodium (and
 * Embeddium) says whether its build queue is empty and how many sections it holds, under the same
 * names on every version: built, and no section more for a moment, the picture is complete.
 *
 * <p>Frame rate: the game counts its frames each second in a static field of its main class,
 * {@code Minecraft.fps}, which Fabric names {@code class_310.field_1738} on every version. Read
 * once a second, as its debug screen does.
 */
public final class BenchAgent {
    private BenchAgent() {
    }

    public static void premain(String folder, Instrumentation instrumentation) {
        Path inbox = Paths.get(folder);
        Thread thread = new Thread(() -> loop(inbox), "lh-bench-agent");
        thread.setDaemon(true);
        thread.start();
    }

    private static void loop(Path inbox) {
        Game game = null;
        while (true) {
            try {
                Thread.sleep(20);
                if (!Files.isDirectory(inbox)) continue;
                List<Path> files = new ArrayList<>();
                try (DirectoryStream<Path> found = Files.newDirectoryStream(inbox, "*.txt")) {
                    for (Path file : found) files.add(file);
                }
                if (files.isEmpty()) continue;
                if (game == null) game = Game.find();
                // Named by a counter of fixed width: in order by name.
                Collections.sort(files);
                for (Path file : files) {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    Files.delete(file);
                    for (String line : lines) {
                        if (line.startsWith("iris ")) iris(line.substring(5));
                        else if (line.startsWith("ready ")) ready(Paths.get(line.substring(6)));
                        else if (line.startsWith("fps ")) {
                            String[] parts = line.split(" ", 3);
                            fps(Integer.parseInt(parts[1]), Paths.get(parts[2]));
                        }
                        else if (game != null) game.run(line);
                        else System.out.println("lh-bench-agent: no keyboard handler for " + line);
                    }
                }
            } catch (Throwable e) {
                System.out.println("lh-bench-agent: " + e);
            }
        }
    }

    /** The loader the game's classes live in: its render thread's (Fabric's, Forge's, NeoForge's). */
    private static ClassLoader gameLoader() {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().equals("Render thread") && thread.getContextClassLoader() != null) {
                return thread.getContextClassLoader();
            }
        }
        throw new IllegalStateException("no render thread yet");
    }

    /** Sets the pack Iris (or Oculus) turns on at its next toggle, shaders off until then. */
    private static void iris(String pack) throws Exception {
        Class<?> iris = Class.forName("net.irisshaders.iris.Iris", true, gameLoader());
        Object config = iris.getMethod("getIrisConfig").invoke(null);
        config.getClass().getMethod("setShaderPackName", String.class).invoke(config, pack);
        config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, false);
        System.out.println("lh-bench-agent: next pack " + pack);
    }

    /** The game's frame rate, read once a second for {@code seconds}, one number a line. */
    private static void fps(int seconds, Path out) throws Exception {
        Field counter = null;
        for (String[] name : new String[][]{{"net.minecraft.client.Minecraft", "fps"},
                {"net.minecraft.class_310", "field_1738"}}) {
            try {
                counter = Class.forName(name[0], true, gameLoader()).getDeclaredField(name[1]);
                break;
            } catch (ClassNotFoundException | NoSuchFieldException e) {
                // the other names
            }
        }
        StringBuilder lines = new StringBuilder();
        if (counter == null) {
            lines.append("? no frame counter\n");
        } else {
            counter.setAccessible(true);
            for (int i = 0; i < seconds; i++) {
                Thread.sleep(1000);
                lines.append(counter.getInt(null)).append("\n");
            }
        }
        Path part = out.resolveSibling(out.getFileName() + ".part");
        Files.write(part, lines.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(part, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Whether Sodium has built the chunks it holds, and how many sections it holds. */
    private static void ready(Path out) throws Exception {
        String answer;
        Class<?> type = null;
        for (String name : new String[]{"net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer",
                "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer"}) {
            try {
                type = Class.forName(name, true, gameLoader());
                break;
            } catch (ClassNotFoundException e) {
                // the other name
            }
        }
        if (type == null) {
            answer = "? no Sodium";
        } else {
            Object renderer = type.getMethod("instanceNullable").invoke(null);
            if (renderer == null) {
                answer = "? no world drawn yet";
            } else {
                boolean built = (boolean) type.getMethod("isTerrainRenderComplete").invoke(renderer);
                Field field = type.getDeclaredField("renderSectionManager");
                field.setAccessible(true);
                Object manager = field.get(renderer);
                int sections = (int) manager.getClass().getMethod("getTotalSections").invoke(manager);
                answer = (built ? "1 " : "0 ") + sections;
            }
        }
        Path part = out.resolveSibling(out.getFileName() + ".part");
        Files.write(part, answer.getBytes(StandardCharsets.UTF_8));
        Files.move(part, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** The game's keyboard handler and window, found once the game has started. */
    private static final class Game {
        final Object minecraft;
        final Object keyboard;
        final long window;
        final Method execute;
        final Method keyPress;
        final Method textInput;
        final Constructor<?> keyEvent;

        private Game(ClassLoader loader) throws Exception {
            Class<?> type = Class.forName("net.minecraft.client.Minecraft", true, loader);
            minecraft = type.getMethod("getInstance").invoke(null);
            if (minecraft == null) throw new IllegalStateException("the game is not started yet");
            keyboard = type.getField("keyboardHandler").get(minecraft);
            Object windowObject = type.getMethod("getWindow").invoke(minecraft);
            window = (long) windowObject.getClass().getMethod("handle").invoke(windowObject);
            execute = type.getMethod("execute", Runnable.class);
            Class<?> event = Class.forName("net.minecraft.client.input.KeyEvent", true, loader);
            keyEvent = event.getConstructor(int.class, int.class, int.class);
            keyPress = keyboard.getClass().getMethod("keyPress", long.class, int.class, event);
            textInput = keyboard.getClass().getMethod("textInput", long.class, String.class);
        }

        /** Null before the game has started, and on a version whose names are obfuscated. */
        static Game find() {
            try {
                return new Game(gameLoader());
            } catch (Throwable e) {
                return null;
            }
        }

        void run(String line) throws Exception {
            if (line.startsWith("key ")) {
                String[] parts = line.split(" ");
                Object event = keyEvent.newInstance(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), 0);
                onGameThread(() -> keyPress.invoke(keyboard, window, 1, event));
                Thread.sleep(30);
                onGameThread(() -> keyPress.invoke(keyboard, window, 0, event));
            } else if (line.startsWith("text ")) {
                String text = line.substring(5);
                onGameThread(() -> textInput.invoke(keyboard, window, text));
            }
            Thread.sleep(30);
        }

        private interface Call {
            void call() throws Exception;
        }

        private void onGameThread(Call call) throws Exception {
            Runnable task = () -> {
                try {
                    call.call();
                } catch (Exception e) {
                    System.out.println("lh-bench-agent: " + e);
                }
            };
            execute.invoke(minecraft, task);
        }
    }
}
