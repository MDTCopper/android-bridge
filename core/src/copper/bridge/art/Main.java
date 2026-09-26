package copper.bridge.art;

import copper.bridge.*;
import copper.bridge.util.*;
import java.io.*;
import java.util.*;

/**
 * The entry point the launcher calls on the ART side; a separate class from the JVM side's
 * {@link copper.bridge.jvm.Main} because the two run in different virtual machines. {@link #main(String[])}
 * parses the arguments, reports the game version and loads the native library; {@link #launch()} builds the
 * activity and returns it. The version branch is not decided here - the JVM resolves it from its own classpath -
 * and this side reports only the game version it found, the one fact knowable before a JVM exists.
 */
public class Main {

    /**
     * Parses the launcher arguments. Called once before {@link #launch()}; calling it again is harmless.
     */
    public static void main(String[] args) {
        parse(args);

        // Android's log is reached by this side's backend alone - the JVM has no android.util.Log -
        // and the same backend writes the file until the native library is loaded.
        Log.setSide(Log.Side.ART);
        Log.setLogcat(Bridge.options.logcat);
        if (Bridge.options.verbose)
            Log.setLevel(Log.Level.VERBOSE);
        else if (Bridge.options.debug)
            Log.setLevel(Log.Level.DEBUG);

        // This side owns the log file until the native library is loaded, and takes it back if that never
        // happens. A run's log starts empty: a stale one would read as if it were this run's.
        File logFile = new File(Bridge.options.gameDataFolder, "last_log.txt");
        logFile.getParentFile().mkdirs();
        logFile.delete();
        Log.setOutputFile(logFile);
        Log.setBackend(new AndroidLogBackend());

        Log.info("CopperBridge v" + Bridge.options.bridgeVersion + " (ART side)");
        Log.info("cache = " + Bridge.options.cacheFolder);
        Log.info("game jars:");
        for (File jar : Bridge.options.gameJars)
            Log.info("  " + jar);

        // Reported, not used: the branch is resolved on the JVM side. Keeping the line here is what
        // makes a launch that never reaches the JVM diagnosable at all - the ART log exists before
        // the JVM does, so it is the only record of which jar the game version came out of.
        GameVersion version = GameVersion.fromJars(Bridge.options.gameJars);
        Log.info("game version: " + (version == null ? "unknown (no version.properties)" : version.toString()));

        // The library is extracted and loaded here, and the path it landed on is recorded: the JVM has to
        // reopen this very file, because a native method binds to the VM whose JNI_OnLoad registered it.
        // Extraction happens while this side still has the file.
        Bridge.prepare();

        // The writer is given up before the load: from its first instruction the native side owns the
        // file, and this side's lines still reach logcat through this backend.
        Log.closeOutputFile();
        try {
            Bridge.load();
        } catch (Throwable e) {
            // The library's first act is to take the file over; if it never got that far, the file is
            // this side's again and this is where the reason for the failed load ends up.
            Log.setOutputFile(logFile);
            Log.error("the native library failed to load: " + e);
            throw e;
        }

        // Loaded: the native side is the writer now, and this side hands its lines over.
        Log.setBackend(new NativeLogBackend());
    }

    /**
     * Creates the game activity on the ART main thread and returns it immediately: the JVM is started by the
     * activity itself, on its own thread. Reaching {@link BridgeActivity} directly puts
     * {@code android.app.Activity} into this class's reference graph, which is safe because nothing on the JVM
     * side ever loads this class.
     *
     * @return the activity, ready to be returned from {@code instantiateActivity}
     */
    public static Object launch() {
        requireOptions();
        return new BridgeActivity();
    }

    // argument parsing

    /** Builds the argument parser with every option documented in the project spec. */
    private static ArgParser buildParser() {
        ArgParser parser = new ArgParser("CopperBridge", "An Android JVM bridge to launch the vanilla game.");

        // The bare words are the caller's arguments for the game: the bridge collects them and passes them
        // on, and with an injected loader that loader is the one that forwards them.
        parser.setPositionalDescription("passed on to the game, or to an injected loader, which forwards them");

        parser.addOption("G", "game-jar", "Game jar / arc jar / extra classpath jar, repeatable", "path",
                path -> Bridge.options.gameJars.add(new File(path)));
        parser.addOption("D", "game-data", "Game data folder", "path",
                path -> Bridge.options.gameDataFolder = new File(path));
        parser.addOption("C", "cache-path", "Bridge runtime folder", "path",
                path -> Bridge.options.cacheFolder = new File(path));
        parser.addOption(null, "java", "JVM executable", "path",
                path -> Bridge.options.javaExecutable = new File(path));
        parser.addOption("L", "loader-jar", "Custom loader jar, repeatable", "path",
                path -> Bridge.options.loaderJars.add(new File(path)));
        parser.addOption(null, "main", "Custom loader main class", "class name",
                name -> Bridge.options.customMainClass = name);
        parser.addOption(null, "bridge-jar", "This bridge's own jar, as the host loaded it", "path",
                path -> Bridge.options.bridgeJar = new File(path));
        parser.addOption(null, "arc-lib", "Folder of arc native libraries, or one library file", "path",
                path -> Bridge.options.arcLibPath = new File(path));
        parser.addOption(null, "angle-path", "Folder of the caller's ANGLE libraries", "path",
                path -> {
                    Bridge.options.angle = true;
                    Bridge.options.anglePath = new File(path);
                });
        parser.addOption(null, "abi", "Override the detected ABI", "abi",
                abi -> Bridge.options.abi = abi);

        parser.addFlag(null, "angle", "Use the device's own ANGLE libraries for EGL and GLES", () -> Bridge.options.angle = true);
        parser.addFlag(null, "gl3", "Request an OpenGL ES 3 context", () -> Bridge.options.useGL30 = true);
        parser.addFlag(null, "gl2", "Request an OpenGL ES 2 context", () -> Bridge.options.useGL30 = false);
        parser.addFlag(null, "no-jvm-args", "Disable the injected JVM arguments",
                () -> Bridge.options.noJvmArgs = true);
        parser.addFlag("d", "debug", "Debug logging: this bridge's, and the game's own",
                () -> Bridge.options.debug = true);
        parser.addFlag(null, "verbose", "Verbose logging for this bridge; the game gets its debug level too",
                () -> Bridge.options.verbose = true);
        parser.addFlag(null, "logcat", "Also write to Android's log", () -> Bridge.options.logcat = true);

        // one JVM argument per occurrence, handed over untouched: the value may start with a dash
        parser.addOption("J", "jvm-args", "One JVM argument, repeatable", "arg",
                arg -> Bridge.options.jvmArgs.add(arg), true);

        return parser;
    }

    /**
     * Parses the given arguments into the one options instance, filling in the derived values. Nothing is
     * returned: the instance is the process's, and every other class reads it from {@link Bridge}.
     */
    private static void parse(String[] args) {
        Bridge.options = new BridgeOptions();
        Bridge.options.bridgeVersion = readBridgeVersion();
        ArgParser parser = buildParser();
        parser.parse(args);

        // Everything that is not one of this bridge's own options is the game's: those words are carried
        // over, so whoever starts the game - the JVM entry, or an injected loader - can hand them on.
        Bridge.options.positional.addAll(parser.getPositionalArgs());

        require(Bridge.options.gameJars.isEmpty(), "no game jar provided, pass at least one -G");
        require(Bridge.options.gameDataFolder == null, "no game data folder provided, pass -D");
        require(Bridge.options.cacheFolder == null, "no cache folder provided, pass -C");
        require(Bridge.options.javaExecutable == null, "no JVM executable provided, pass --java");

        if (Bridge.options.abi == null || Bridge.options.abi.isEmpty())
            Bridge.options.abi = Device.abi();
        if (Bridge.options.arch == null || Bridge.options.arch.isEmpty())
            Bridge.options.arch = Device.arch();
        if (Bridge.options.androidVersion == 0)
            Bridge.options.androidVersion = Device.apiLevel();

        // arc derives its mobile UI scale from the display density and the JVM side has no way to
        // ask Android for it, so it is recorded here with the rest of the device facts
        if (Bridge.options.density <= 0f)
            Bridge.options.density = Device.density();
        if (Bridge.options.xdpi <= 0f)
            Bridge.options.xdpi = Device.xdpi();
        if (Bridge.options.ydpi <= 0f)
            Bridge.options.ydpi = Device.ydpi();

        Bridge.options.gameDataFolder.mkdirs();
        Bridge.options.cacheFolder.mkdirs();
        new File(Bridge.options.cacheFolder, "tmp").mkdirs();

        Bridge.options.javaHome = deriveJavaHome(Bridge.options.javaExecutable);
    }

    /** Reads {@code bridge.properties}, falling back to a placeholder when it is missing. */
    private static String readBridgeVersion() {
        try (InputStream in = Main.class.getClassLoader().getResourceAsStream("bridge.properties")) {
            if (in == null)
                return "0.0.0";
            Properties props = new Properties();
            props.load(new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            return props.getProperty("version", "0.0.0");
        } catch (Throwable e) {
            return "0.0.0";
        }
    }

    /** The JRE root implied by a JVM executable path. */
    private static File deriveJavaHome(File javaExecutable) {
        File bin = javaExecutable.getAbsoluteFile().getParentFile();
        if (bin != null && "bin".equals(bin.getName()) && bin.getParentFile() != null)
            return bin.getParentFile();
        return bin == null ? javaExecutable.getAbsoluteFile() : bin;
    }

    /** Fails unless {@link #main} has parsed the arguments, i.e. unless the one options instance exists. */
    private static void requireOptions() {
        if (Bridge.options == null)
            throw new RuntimeException("copper.bridge.art.Main.main has not run yet");
    }

    private static void require(boolean empty, String message) {
        if (empty)
            throw new RuntimeException(message);
    }
}
