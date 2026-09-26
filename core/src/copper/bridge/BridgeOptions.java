package copper.bridge;

import java.io.*;
import java.util.*;

/**
 * Everything the ART side has to hand over to the JVM side.
 *
 * <p>The two VMs share no object graph, so the resolved runtime facts (jars, folders, ABI, ANGLE
 * choice, allowed GL version, JVM arguments) cross as system properties:
 * {@link copper.bridge.art.JvmArgs} emits one {@code -Dcopper.bridge.<name>=<value>} per field, and
 * {@link #fromSystemProperties()} reads them back, so the sides cannot disagree about, for example,
 * which ABI was detected - and there is no file in the cache folder to go stale.</p>
 */
public class BridgeOptions {
    /**
     * Prefix of every option property, so an option cannot collide with a property the JVM or the JRE
     * already defines: a bare {@code arch} would sit beside the injected {@code os.arch}.
     */
    private static final String PREFIX = "copper.bridge.";

    /** Bridge version, read from {@code bridge.properties} inside the bridge jar. */
    public String bridgeVersion = "0.0.0";

    /** All classpath jars passed on the ART side, in the order they were given. */
    public final List<File> gameJars = new ArrayList<>();
    /** Custom loader jars, in the order they were given. Only used in the injected-loader case. */
    public final List<File> loaderJars = new ArrayList<>();

    /** Game data folder (saves, settings, mods). */
    public File gameDataFolder;
    /** Bridge runtime folder: extracted natives, dex cache, logs, tmp. */
    public File cacheFolder;

    /** The JVM executable, as given by the caller. */
    public File javaExecutable;
    /** The JRE root derived from {@link #javaExecutable} (the parent of {@code bin}). */
    public File javaHome;

    /** Custom loader main class; enables the injected-loader classpath rule when set. */
    public String customMainClass;

    /**
     * Where the caller's native libraries are: a folder they are all taken from, or a single file. One
     * parameter, because the bridge stages whatever list it is given rather than owning it.
     */
    public File arcLibPath;
    /** This bridge's own jar, as the host gave it: the JVM needs it on its classpath to find its main class. */
    public File bridgeJar;
    /**
     * Folder the caller's libraries were staged in, or {@code null} when none was provided. Both VMs need
     * it: the ART side adds the folder to the linker search path, and on the JVM side arc then loads the
     * libraries by name with {@code System.loadLibrary}. See {@link copper.bridge.art.ArcNatives}.
     */
    public File arcNativeFolder;
    /** The name arc asks for {@code libarc}, i.e. what staging wrote; {@code null} when it did not. */
    public String arcNativeName;

    /** Whether ANGLE is requested: the device's own libraries when {@link #anglePath} is null. */
    public boolean angle = false;
    /** Folder the caller's ANGLE libraries are in, or {@code null} for the device's own. */
    public File anglePath;

    /** Whether an OpenGL ES 3 context should be requested (default) or ES 2. */
    public boolean useGL30 = true;

    /** Device ABI, or {@code null} to detect it at runtime. */
    public String abi;
    /** Android API level, injected as {@code os.version}. */
    public int androidVersion = 0;
    /** CPU architecture string, injected as {@code os.arch}. */
    public String arch = "";

    /**
     * Display density, as {@code android.util.DisplayMetrics#density}: arc scales the whole mobile
     * UI by it, and the JVM side has no way to ask the system for it.
     */
    public float density = 1f;
    /** Physical pixels per inch on the x axis; 0 when the device does not report it. */
    public float xdpi = 0f;
    /** Physical pixels per inch on the y axis; 0 when the device does not report it. */
    public float ydpi = 0f;

    /** Whether the default JVM argument injection is disabled. */
    public boolean noJvmArgs = false;
    /** User supplied JVM arguments, one entry per {@code -J} occurrence, in order. */
    public final List<String> jvmArgs = new ArrayList<>();

    /**
     * The caller's positional arguments, in the order they were given: everything on the ART side's command
     * line that is not one of this bridge's own options. They belong to the game - or to an injected loader -
     * and the bridge only carries them, because it is the side that parses that command line.
     */
    public final List<String> positional = new ArrayList<>();

    /** Debug log output requested. */
    public boolean debug = false;
    /** Verbose log output requested. */
    public boolean verbose = false;
    /** Whether lines may also go to Android's log. Off by default: the log file holds everything. */
    public boolean logcat = false;

    /**
     * Where the ART side extracted the bridge's own native library. Recorded because both VMs load
     * the very same file, and the path carries the build's identity: an older library left in the
     * cache would otherwise be loaded silently, and the kind ids the JVM side routes by come from
     * the jar.
     */
    public File bridgeLibrary;

    /**
     * These options as properties, keyed exactly as they cross to the JVM: {@link #PREFIX} is
     * already in the keys, so this set <em>is</em> the {@code -D} argument list, with no translation
     * on either side - adding a field here is the whole change.
     */
    public Properties toProperties() {
        Properties props = new Properties();
        put(props, "bridgeVersion", bridgeVersion);
        putList(props, "gameJars", gameJars);
        putList(props, "loaderJars", loaderJars);
        putFile(props, "gameDataFolder", gameDataFolder);
        putFile(props, "cacheFolder", cacheFolder);
        putFile(props, "javaExecutable", javaExecutable);
        putFile(props, "javaHome", javaHome);
        put(props, "customMainClass", customMainClass);
        putFile(props, "arcLibPath", arcLibPath);
        putFile(props, "arcNativeFolder", arcNativeFolder);
        put(props, "arcNativeName", arcNativeName);
        putFile(props, "bridgeLibrary", bridgeLibrary);
        put(props, "angle", Boolean.toString(angle));
        putFile(props, "anglePath", anglePath);
        put(props, "gl30", Boolean.toString(useGL30));
        put(props, "abi", abi);
        put(props, "androidVersion", Integer.toString(androidVersion));
        put(props, "arch", arch);
        put(props, "density", Float.toString(density));
        put(props, "xdpi", Float.toString(xdpi));
        put(props, "ydpi", Float.toString(ydpi));
        put(props, "noJvmArgs", Boolean.toString(noJvmArgs));
        putStrings(props, "jvmArgs", jvmArgs);
        putStrings(props, "positional", positional);
        put(props, "debug", Boolean.toString(debug));
        put(props, "verbose", Boolean.toString(verbose));
        put(props, "logcat", Boolean.toString(logcat));
        return props;
    }

    /**
     * Reads back the options the ART side passed as {@code -Dcopper.bridge.*} system properties.
     * Reads the live system properties directly: the names carry {@link #PREFIX}, so the JVM's own
     * properties are simply never looked up.
     */
    public static BridgeOptions fromSystemProperties() {
        return parse(System.getProperties());
    }

    /** Parses a property set keyed the way {@link #toProperties()} writes it. */
    private static BridgeOptions parse(Properties props) {
        BridgeOptions options = new BridgeOptions();
        options.bridgeVersion = get(props, "bridgeVersion", "0.0.0");
        options.gameJars.addAll(getFiles(props, "gameJars"));
        options.loaderJars.addAll(getFiles(props, "loaderJars"));
        options.gameDataFolder = getFile(props, "gameDataFolder");
        options.cacheFolder = getFile(props, "cacheFolder");
        options.javaExecutable = getFile(props, "javaExecutable");
        options.javaHome = getFile(props, "javaHome");
        options.customMainClass = emptyToNull(get(props, "customMainClass"));
        options.arcLibPath = getFile(props, "arcLibPath");
        options.arcNativeFolder = getFile(props, "arcNativeFolder");
        options.arcNativeName = emptyToNull(get(props, "arcNativeName"));
        options.bridgeLibrary = getFile(props, "bridgeLibrary");
        options.angle = Boolean.parseBoolean(get(props, "angle", "false"));
        options.anglePath = getFile(props, "anglePath");
        options.useGL30 = Boolean.parseBoolean(get(props, "gl30", "true"));
        options.abi = emptyToNull(get(props, "abi"));
        options.androidVersion = Integer.parseInt(get(props, "androidVersion", "0"));
        options.arch = get(props, "arch", "");
        options.density = Float.parseFloat(get(props, "density", "1"));
        options.xdpi = Float.parseFloat(get(props, "xdpi", "0"));
        options.ydpi = Float.parseFloat(get(props, "ydpi", "0"));
        options.noJvmArgs = Boolean.parseBoolean(get(props, "noJvmArgs", "false"));
        options.jvmArgs.addAll(getStrings(props, "jvmArgs"));
        options.positional.addAll(getStrings(props, "positional"));
        options.debug = Boolean.parseBoolean(get(props, "debug", "false"));
        options.verbose = Boolean.parseBoolean(get(props, "verbose", "false"));
        options.logcat = Boolean.parseBoolean(get(props, "logcat", "false"));
        return options;
    }

    /**
     * The JVM classpath for the current mode: the bridge jar followed by every jar the caller
     * passed, or, with an injected loader, the loader jars only - the game and the bridge itself go
     * over as {@code --bridge-class-path} so the loader owns the class loading order.
     *
     * <p>{@link #arcNativeFolder} is not a classpath entry in either mode. arc loads its libraries by name,
     * so the staged folder belongs on the library search path the JVM builds from {@code LD_LIBRARY_PATH}
     * ({@link copper.bridge.art.Bootstrap}), not on the classpath.</p>
     */
    public List<String> jvmClasspath() {
        List<String> path = new ArrayList<>();
        if (usesCustomLoader()) {
            for (File jar : loaderJars)
                path.add(jar.getAbsolutePath());
        } else {
            addBridgeEntries(path);
        }
        return path;
    }

    /**
     * The argument that carries {@link #bridgeClasspath()} to an injected loader, as
     * {@code --bridge-class-path=<paths>}. Written by the ART side and consumed by the loader, so both read
     * the name from here rather than each spelling it out.
     */
    public static final String CLASS_PATH_OPTION = "--bridge-class-path";

    /**
     * The game-side classpath handed to an injected loader: the bridge jar first, then every jar
     * the caller passed, from which the loader adds its own jar and builds its class loader.
     */
    public List<String> bridgeClasspath() {
        List<String> path = new ArrayList<>();
        addBridgeEntries(path);
        return path;
    }

    /**
     * The entries both classpaths share, in this order: the bridge jar, then the game jars. The staged arc
     * natives are not here, because arc loads them by name. So a game jar that carries its own desktop build
     * of them is never found by a class loader.
     */
    private void addBridgeEntries(List<String> path) {
        File bridgeJar = Bridge.jar();
        if (bridgeJar != null)
            path.add(bridgeJar.getAbsolutePath());
        for (File jar : gameJars)
            path.add(jar.getAbsolutePath());
    }

    /** Whether the injected-loader classpath rule applies. */
    public boolean usesCustomLoader() {
        return customMainClass != null && !customMainClass.isEmpty() && !loaderJars.isEmpty();
    }

    /** Reads one option, or {@code null} when it was not passed. */
    private static String get(Properties props, String key) {
        return props.getProperty(PREFIX + key);
    }

    /** Reads one option, falling back when it was not passed. */
    private static String get(Properties props, String key, String fallback) {
        return props.getProperty(PREFIX + key, fallback);
    }

    private static void put(Properties props, String key, String value) {
        if (value != null)
            props.setProperty(PREFIX + key, value);
    }

    private static void putFile(Properties props, String key, File file) {
        if (file != null)
            props.setProperty(PREFIX + key, file.getAbsolutePath());
    }

    private static void putList(Properties props, String key, List<File> files) {
        props.setProperty(PREFIX + key + ".size", Integer.toString(files.size()));
        for (int i = 0; i < files.size(); i++)
            props.setProperty(PREFIX + key + "." + i, files.get(i).getAbsolutePath());
    }

    private static void putStrings(Properties props, String key, List<String> values) {
        props.setProperty(PREFIX + key + ".size", Integer.toString(values.size()));
        for (int i = 0; i < values.size(); i++)
            props.setProperty(PREFIX + key + "." + i, values.get(i));
    }

    private static File getFile(Properties props, String key) {
        String value = emptyToNull(get(props, key));
        return value == null ? null : new File(value);
    }

    private static List<File> getFiles(Properties props, String key) {
        List<File> files = new ArrayList<>();
        int size = Integer.parseInt(get(props, key + ".size", "0"));
        for (int i = 0; i < size; i++) {
            String value = emptyToNull(get(props, key + "." + i));
            if (value != null)
                files.add(new File(value));
        }
        return files;
    }

    private static List<String> getStrings(Properties props, String key) {
        List<String> values = new ArrayList<>();
        int size = Integer.parseInt(get(props, key + ".size", "0"));
        for (int i = 0; i < size; i++) {
            String value = get(props, key + "." + i);
            if (value != null)
                values.add(value);
        }
        return values;
    }

    private static String emptyToNull(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }
}
