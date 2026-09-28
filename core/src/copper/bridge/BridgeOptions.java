package copper.bridge;

import java.io.*;
import java.util.*;

/**
 * Everything the ART side hands over to the JVM side: the two VMs share no object graph, so the resolved runtime
 * facts cross as system properties. {@link #fromSystemProperties()} is the other end of
 * {@link copper.bridge.art.JvmArgs}.
 */
public class BridgeOptions {
    /** Prefix of every option property: a bare {@code arch} would sit beside the JVM's own {@code os.arch}. */
    private static final String PREFIX = "copper.bridge.";

    public String bridgeVersion = "0.0.0";
    public String bridgeCommit = "";
    public boolean customBuild = false;

    public final List<File> gameJars = new ArrayList<>();
    public final List<File> loaderJars = new ArrayList<>();

    public File gameDataFolder;
    public File cacheFolder;

    public File javaExecutable;
    public File javaHome;

    public String customMainClass;

    public File arcLibPath;
    public File bridgeJar;
    public File arcNativeFolder;
    /** Whether staging found arc's own {@code libarc.so}, without which arc's native code paths are unusable. */
    public boolean foundArcNative;

    /** Whether ANGLE is requested: the device's own libraries when {@link #anglePath} is {@code null}. */
    public boolean angle = false;
    public File anglePath;

    public boolean useGL30 = true;

    public String abi;
    public int androidVersion = 0;
    public String arch = "";

    /** Display density: arc scales the whole mobile UI by it, and the JVM side cannot ask the system. */
    public float density = 1f;
    public float xdpi = 0f;
    public float ydpi = 0f;

    public boolean noJvmArgs = false;
    public final List<String> jvmArgs = new ArrayList<>();

    public final List<String> positional = new ArrayList<>();

    public boolean debug = false;
    public boolean verbose = false;
    public boolean logcat = false;

    /** Where the ART side extracted the bridge's own native library; both VMs load that very file. */
    public File bridgeLibrary;

    /** The version the startup lines print: {@code v0.1.3}, {@code snapshot}, {@code snapshot+<commit|custom>}. */
    public String versionLabel() {
        String label = bridgeVersion;
        if (customBuild)
            label += "+custom";
        else if (bridgeCommit != null && !bridgeCommit.isEmpty())
            label += "+" + bridgeCommit;
        return "snapshot".equals(bridgeVersion) ? label : "v" + label;
    }

    /** These options as properties, keyed exactly as they cross to the JVM: adding a field here is the whole change. */
    public Properties toProperties() {
        Properties props = new Properties();
        put(props, "bridgeVersion", bridgeVersion);
        put(props, "bridgeCommit", bridgeCommit);
        put(props, "bridgeCustom", Boolean.toString(customBuild));
        putList(props, "gameJars", gameJars);
        putList(props, "loaderJars", loaderJars);
        putFile(props, "gameDataFolder", gameDataFolder);
        putFile(props, "cacheFolder", cacheFolder);
        putFile(props, "javaExecutable", javaExecutable);
        putFile(props, "javaHome", javaHome);
        put(props, "customMainClass", customMainClass);
        putFile(props, "arcLibPath", arcLibPath);
        putFile(props, "arcNativeFolder", arcNativeFolder);
        put(props, "foundArcNative", Boolean.toString(foundArcNative));
        putFile(props, "bridgeJar", bridgeJar);
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

    public static BridgeOptions fromSystemProperties() {
        return parse(System.getProperties());
    }

    private static BridgeOptions parse(Properties props) {
        BridgeOptions options = new BridgeOptions();
        options.bridgeVersion = get(props, "bridgeVersion", "0.0.0");
        options.bridgeCommit = get(props, "bridgeCommit", "");
        options.customBuild = Boolean.parseBoolean(get(props, "bridgeCustom", "false"));
        options.gameJars.addAll(getFiles(props, "gameJars"));
        options.loaderJars.addAll(getFiles(props, "loaderJars"));
        options.gameDataFolder = getFile(props, "gameDataFolder");
        options.cacheFolder = getFile(props, "cacheFolder");
        options.javaExecutable = getFile(props, "javaExecutable");
        options.javaHome = getFile(props, "javaHome");
        options.customMainClass = emptyToNull(get(props, "customMainClass"));
        options.arcLibPath = getFile(props, "arcLibPath");
        options.arcNativeFolder = getFile(props, "arcNativeFolder");
        options.foundArcNative = Boolean.parseBoolean(get(props, "foundArcNative"));
        options.bridgeJar = getFile(props, "bridgeJar");
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

    /** The JVM classpath for the current mode: with an injected loader, the loader jars only. */
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

    public static final String CLASS_PATH_OPTION = "--bridge-class-path";

    public List<String> bridgeClasspath() {
        List<String> path = new ArrayList<>();
        addBridgeEntries(path);
        return path;
    }

    /** The entries both classpaths share: the bridge jar, then the game jars - never the staged arc natives. */
    private void addBridgeEntries(List<String> path) {
        File bridgeJar = Bridge.jar();
        if (bridgeJar != null)
            path.add(bridgeJar.getAbsolutePath());
        for (File jar : gameJars)
            path.add(jar.getAbsolutePath());
    }

    public boolean usesCustomLoader() {
        return customMainClass != null && !customMainClass.isEmpty() && !loaderJars.isEmpty();
    }

    private static String get(Properties props, String key) {
        return props.getProperty(PREFIX + key);
    }

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
