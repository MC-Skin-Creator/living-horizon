import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
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
