package copper.bridge.art;

import copper.bridge.*;
import copper.bridge.util.*;
import java.io.*;
import java.util.*;

/**
 * Builds the final JVM command line: environment properties, the injected tuning arguments, the user's
 * {@code -J} arguments, the classpath and the main class.
 *
 * <p>The user may override anything injected. Comparison happens on a per-argument key, so
 * {@code -Xmx3072m} replaces the injected {@code -Xmx...} and leaves the rest alone. GC selection is
 * special: the injected set tunes G1 specifically, so a user who picks a different collector gets the G1
 * tuning dropped too, rather than a command line the JVM refuses to start.</p>
 */
public class JvmArgs {
    /** Collectors recognized by the mutual exclusion rule. */
    private static final String[] GC_FLAGS = {
            "UseG1GC", "UseZGC", "UseSerialGC", "UseParallelGC", "UseShenandoahGC", "UseEpsilonGC"
    };

    /** The injected argument set, in emission order. */
    private final List<String> injected = new ArrayList<>();
    /** Everything the user passed through {@code -J}, in order. */
    private final List<String> user = new ArrayList<>();
    /** Override diagnostics, one line each. */
    private final List<String> notes = new ArrayList<>();
    /** Properties handed to the JVM through {@code -D}, in insertion order. */
    private final Map<String, String> properties = new LinkedHashMap<>();

    /**
     * Builds the argument set for this process. Everything it needs is the process's: the one options
     * instance and the bridge jar both live in {@link Bridge}.
     */
    public JvmArgs() {
        this.user.addAll(Bridge.options.jvmArgs);

        // locale and platform facts the game reads straight from system properties
        property("user.language", Locale.getDefault().getLanguage());
        property("user.country", Locale.getDefault().getCountry());
        property("user.timezone", TimeZone.getDefault().getID());
        property("user.home", Bridge.options.gameDataFolder.getAbsolutePath());
        property("os.name", "Linux");
        property("os.version", "Android-" + Bridge.options.androidVersion);
        property("os.arch", Bridge.options.arch);
        property("java.home", path(Bridge.options.javaHome));
        property("java.io.tmpdir", path(new File(Bridge.options.cacheFolder, "tmp")));

        // Everything else the JVM side needs. The keys already carry their prefix, so the option set is
        // literally the -D argument list. Values go into the map directly rather than through
        // property(...), which drops empty ones: the emission must match what the JVM reads back.
        for (Map.Entry<Object, Object> entry : Bridge.options.toProperties().entrySet())
            properties.put((String) entry.getKey(), (String) entry.getValue());

        // The surface is deliberately not here: it arrives through the request queue.

        if (Bridge.options.noJvmArgs)
            return;

        boolean g1 = true;
        for (String arg : user) {
            if (isGcSelection(arg)) {
                g1 = false;
                notes.add("skip injected G1 tuning (user selected " + arg + ")");
                break;
            }
        }

        injected.add("--enable-native-access=ALL-UNNAMED");
        injected.add("-XX:+AlwaysPreTouch");
        injected.add("-XX:+UseStringDeduplication");
        injected.add("-XX:+DisableExplicitGC");
        if (g1) {
            injected.add("-XX:+UseG1GC");
            injected.add("-XX:MaxGCPauseMillis=130");
            injected.add("-XX:+ParallelRefProcEnabled");
            injected.add("-XX:+UnlockExperimentalVMOptions");
            injected.add("-XX:G1MixedGCLiveThresholdPercent=75");
            injected.add("-XX:G1HeapWastePercent=5");
            injected.add("-XX:+PerfDisableSharedMem");
        }
    }

    /** The properties handed to the JVM, as {@code -D} arguments. */
    public List<String> properties() {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : properties.entrySet())
            list.add("-D" + entry.getKey() + "=" + entry.getValue());
        return list;
    }

    /** The injected arguments that survived the override rules, in order. */
    public List<String> injected() {
        Set<String> userKeys = new HashSet<>();
        for (String arg : user)
            userKeys.add(key(arg));

        List<String> result = new ArrayList<>();
        for (String arg : injected) {
            if (userKeys.contains(key(arg))) {
                notes.add("skip injected " + arg + " (overridden)");
                continue;
            }
            result.add(arg);
        }
        return result;
    }

    /** The user arguments, unchanged and in order. */
    public List<String> user() {
        return user;
    }

    /** Override diagnostics, one line each. */
    public List<String> notes() {
        return notes;
    }

    /**
     * Assembles the argument vector handed to {@code JLI_Launch}.
     *
     * @param java       the JVM executable path, used as {@code argv[0]}
     * @param loaderArgs the arguments after the main class: an injected loader's classpath, and nothing
     *                   without one
     */
    public List<String> build(String java, String mainClass, List<String> classpath, List<String> loaderArgs) {
        List<String> args = new ArrayList<>();
        args.add(java);
        args.addAll(properties());
        args.addAll(injected());
        args.addAll(user);
        args.add("-cp");
        args.add(String.join(File.pathSeparator, classpath));
        args.add(mainClass);
        args.addAll(loaderArgs);
        return args;
    }

    /**
     * Logs the classpath and the override notes. The whole argument vector is one debug line: it says the
     * same thing as the lines above, one argument at a time, and is only wanted when a launch misbehaves.
     */
    public void print(List<String> args, String jre, List<String> classpath) {
        Log.info("jre  = " + jre);

        Log.info("classpath:");
        for (String entry : classpath)
            Log.info("  " + entry);

        for (String note : notes)
            Log.info("override: " + note);

        Log.debug("full argv:");
        for (String arg : args)
            Log.debug("  " + arg);
    }

    /** The main class the JVM should run: the custom loader, or the bridge's JVM entry point. */
    public String mainClass() {
        return Bridge.options.usesCustomLoader() ? Bridge.options.customMainClass : "copper.bridge.jvm.Main";
    }

    private void property(String key, String value) {
        if (value != null && !value.isEmpty())
            properties.put(key, value);
    }

    /** Derives the comparison key of an argument. */
    private String key(String arg) {
        if (arg.startsWith("-D") || arg.startsWith("-XX:")) {
            int eq = arg.indexOf('=');
            return eq < 0 ? arg : arg.substring(0, eq);
        }
        for (String prefix : new String[] {"-Xmx", "-Xms", "-Xss", "-Xmn"}) {
            if (arg.startsWith(prefix))
                return prefix;
        }
        return arg;
    }

    /** Whether the argument selects a garbage collector. */
    private boolean isGcSelection(String arg) {
        for (String flag : GC_FLAGS) {
            if (arg.equals("-XX:+" + flag) || arg.equals("-XX:-" + flag))
                return true;
        }
        return false;
    }

    private String path(File file) {
        return file == null ? null : file.getAbsolutePath();
    }
}
