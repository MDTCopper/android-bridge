package copper.bridge.jvm;

import copper.bridge.*;
import copper.bridge.util.*;
import java.util.*;

/**
 * The entry point the JVM runs.
 *
 * <p>{@code JLI_Launch} starts this class as its main class with the game classpath already set, so this is
 * the "start the game" entry the bridge was built around. It is a separate class from the ART side's
 * {@link copper.bridge.art.Main} because the two run in different virtual machines, are reached through
 * different mechanisms, and share no state.</p>
 */
public class Main {

    /**
     * Runs on the JVM side, launched by {@code JLI_Launch} as the main class. The ART side passed everything
     * it resolved as {@code -Dcopper.bridge.*} system properties, so this does not re-parse the command line:
     * one parse means the two sides cannot disagree about what was requested. What is left of the command
     * line - the caller's positional arguments - is the game's and goes to the branch untouched.
     */
    public static void main(String[] args) {
        Bridge.options = BridgeOptions.fromSystemProperties();
        if (Bridge.options.verbose)
            Log.setLevel(Log.Level.VERBOSE);
        else if (Bridge.options.debug)
            Log.setLevel(Log.Level.DEBUG);

        // The library is loaded first: registering this VM's tables is what binds `logLine`, and from
        // that point this side's lines are handed over like ART's, under this side's own logcat tag.
        Bridge.load();
        Log.setBackend(new NativeLogBackend());
        Log.setSide(Log.Side.JVM);

        Log.info("CopperBridge " + Bridge.options.versionLabel() + " (JVM side)");
        Log.info("java.home = " + Bridge.options.javaHome);
        Log.info("classpath = " + System.getProperty("java.class.path"));
        Log.info("class loader = " + Main.class.getClassLoader());

        Launch.start(gameArgs(args));
    }

    /**
     * The game's arguments: this command line without the bridge's own option. An injected loader is told its
     * classpath with {@code --bridge-class-path=<paths>}, and a loader that hands its whole command line on
     * would otherwise have that option arrive as a game argument. Everything else is the caller's, in order.
     */
    private static String[] gameArgs(String[] args) {
        List<String> game = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith(BridgeOptions.CLASS_PATH_OPTION + "="))
                continue;
            if (args[i].equals(BridgeOptions.CLASS_PATH_OPTION)) {
                i++;
                continue;
            }
            game.add(args[i]);
        }
        return game.toArray(new String[0]);
    }
}
