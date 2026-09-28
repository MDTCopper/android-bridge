package copper.bridge.art;

import copper.bridge.*;
import copper.bridge.util.*;
import java.io.*;
import java.util.*;

/**
 * The entry point the launcher calls on the ART side. The version branch is decided on the JVM side, from its own
 * classpath; this side only reports the game version it found.
 */
public class Main {

    public static void main(String[] args) {
        parse(args);

        Log.setSide(Log.Side.ART);
        Log.setLogcat(Bridge.options.logcat);
        if (Bridge.options.verbose)
            Log.setLevel(Log.Level.VERBOSE);
        else if (Bridge.options.debug)
            Log.setLevel(Log.Level.DEBUG);

        // a run's log starts empty: a stale one would read as if it were this run's
        File logFile = new File(Bridge.options.gameDataFolder, "last_log.txt");
        logFile.getParentFile().mkdirs();
        logFile.delete();
        Log.setOutputFile(logFile);
        Log.setBackend(new AndroidLogBackend());

        Log.info("CopperBridge " + Bridge.options.versionLabel());
        Log.info("CopperBridge (ART side)");
        Log.info("cache = " + Bridge.options.cacheFolder);
        Log.info("game jars:");
        for (File jar : Bridge.options.gameJars)
            Log.info("  " + jar);

        // reported, not used: the ART log is the only record of the version when a launch never reaches the JVM
        GameVersion version = GameVersion.fromJars(Bridge.options.gameJars);
        Log.info("game version: " + (version == null ? "unknown (no version.properties)" : version.toString()));

        // the JVM has to reopen this very file: a native method binds to the VM whose JNI_OnLoad registered it
        Bridge.prepare();

        // writer given up before the load: the native side owns the file from its first instruction
        Log.closeOutputFile();
        try {
            Bridge.load();
        } catch (Throwable e) {
            Log.setOutputFile(logFile);
            Log.error("the native library failed to load: " + e);
            throw e;
        }

        Log.setBackend(new NativeLogBackend());
    }

    /**
     * Creates the game activity on the ART main thread, the JVM being started by the activity itself. Reaching
     * {@link BridgeActivity} directly is safe because nothing on the JVM side ever loads this class.
     */
    public static Object launch() {
        requireOptions();
        return new BridgeActivity();
    }

    //region argument parsing

    private static ArgParser buildParser() {
        ArgParser parser = new ArgParser("CopperBridge", "An Android JVM bridge to launch the vanilla game.");

        // the bare words are the game's, or an injected loader's, which forwards them
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

        parser.addOption("J", "jvm-args", "One JVM argument, repeatable", "arg",
                arg -> Bridge.options.jvmArgs.add(arg), true);

        return parser;
    }

    private static void parse(String[] args) {
        Bridge.options = new BridgeOptions();
        readBridgeProperties();
        ArgParser parser = buildParser();
        parser.parse(args);

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

        // the JVM side cannot ask Android for the display density, so it is recorded here
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

    /** Reads the build stamp {@code :pack} writes; a missing resource leaves the placeholders standing. */
    private static void readBridgeProperties() {
        try (InputStream in = Main.class.getClassLoader().getResourceAsStream("bridge.properties")) {
            if (in == null)
                return;
            Properties props = new Properties();
            props.load(new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            Bridge.options.bridgeVersion = props.getProperty("version", "0.0.0");
            Bridge.options.bridgeCommit = props.getProperty("commit", "").trim();
            Bridge.options.customBuild = Boolean.parseBoolean(props.getProperty("custom", "false"));
        } catch (Throwable ignored) {
            // the placeholders stand
        }
    }

    private static File deriveJavaHome(File javaExecutable) {
        File bin = javaExecutable.getAbsoluteFile().getParentFile();
        if (bin != null && "bin".equals(bin.getName()) && bin.getParentFile() != null)
            return bin.getParentFile();
        return bin == null ? javaExecutable.getAbsoluteFile() : bin;
    }

    private static void requireOptions() {
        if (Bridge.options == null)
            throw new RuntimeException("copper.bridge.art.Main.main has not run yet");
    }

    private static void require(boolean empty, String message) {
        if (empty)
            throw new RuntimeException(message);
    }
    //endregion
}
