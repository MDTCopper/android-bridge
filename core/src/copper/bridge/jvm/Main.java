package copper.bridge.jvm;

import copper.bridge.*;
import copper.bridge.util.*;
import java.util.*;

/**
 * The entry point the JVM runs: {@code JLI_Launch} starts this class as its main class with the classpath set.
 */
public class Main {

    /**
     * Runs on the JVM side, launched by {@code JLI_Launch}. The ART side passed everything as {@code -Dcopper.bridge.*}
     * properties, so this does not re-parse; what is left of the command line is the game's and goes to the branch.
     */
    public static void main(String[] args) {
        Bridge.options = BridgeOptions.fromSystemProperties();
        if (Bridge.options.verbose)
            Log.setLevel(Log.Level.VERBOSE);
        else if (Bridge.options.debug)
            Log.setLevel(Log.Level.DEBUG);

        // The library is loaded first: registering this VM's tables binds `logLine`, which hands the lines over.
        Bridge.load();
        Log.setBackend(new NativeLogBackend());
        Log.setSide(Log.Side.JVM);

        Log.info("CopperBridge (JVM side)");
        Log.debug("java.home = " + Bridge.options.javaHome);
        Log.debug("classpath = " + System.getProperty("java.class.path"));
        Log.debug("class loader = " + Main.class.getClassLoader());

        Launch.start(gameArgs(args));
    }

    /**
     * The game's arguments: this command line without the bridge's own option, which a loader forwarding its whole
     * command line would otherwise hand to the game. Everything else is the caller's, in order.
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
