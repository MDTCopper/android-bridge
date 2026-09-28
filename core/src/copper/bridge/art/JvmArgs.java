package copper.bridge.art;

import copper.bridge.*;
import copper.bridge.util.*;
import java.io.*;
import java.util.*;

/**
 * Builds the final JVM command line: environment properties, the injected tuning arguments, the user's {@code -J}
 * arguments, the classpath and the main class. The user may override anything injected, comparison happening on a
 * per-argument key. GC selection is special: the injected set tunes G1, so another collector drops that tuning too.
 */
public class JvmArgs {
    /** Collectors recognized by the mutual exclusion rule. */
    private static final String[] GC_FLAGS = {
            "UseG1GC", "UseZGC", "UseSerialGC", "UseParallelGC", "UseShenandoahGC", "UseEpsilonGC"
    };

    private final List<String> injected = new ArrayList<>();
    private final List<String> user = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final Map<String, String> properties = new LinkedHashMap<>();

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

        // The keys already carry their prefix, so the option set is literally the -D argument list.
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

    public List<String> properties() {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : properties.entrySet())
            list.add("-D" + entry.getKey() + "=" + entry.getValue());
        return list;
    }

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

    public List<String> user() {
        return user;
    }

    public List<String> notes() {
        return notes;
    }

    /** Assembles the argument vector handed to {@code JLI_Launch}; {@code java} is {@code argv[0]}. */
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

    /** Logs the classpath and the override notes; the whole argument vector is a debug line, not an info one. */
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

    public String mainClass() {
        return Bridge.options.usesCustomLoader() ? Bridge.options.customMainClass : "copper.bridge.jvm.Main";
    }

    private void property(String key, String value) {
        if (value != null && !value.isEmpty())
            properties.put(key, value);
    }

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
