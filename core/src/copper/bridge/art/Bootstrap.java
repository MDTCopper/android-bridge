package copper.bridge.art;

import copper.bridge.*;
import copper.bridge.annotation.*;

import copper.bridge.util.*;
import java.io.*;
import java.util.*;

/**
 * Starts the JVM from the ART side. Runs on its own thread because {@code JLI_Launch} does not return until
 * the game exits, so the activity's main thread stays free for the Android message loop. The order below is
 * the one the Android linker forces: the search path is widened before anything is opened, and the
 * dependency closure is loaded before the launch.
 */
public class Bootstrap {

    /** Starts the JVM and blocks this thread until the game process ends. */
    public static void start() {
        File jre = Bridge.options.javaHome;

        // Runs before the JVM starts: the folder set here has to be on the library search path below, and the
        // JVM side is told about it too. See ArcNatives.stage.
        ArcNatives.stage();

        // The host is the only one that knows where this jar ended up, so it says so with
        // --bridge-jar. Without it the JVM dies quietly in well under a second: "could not find main
        // class" goes to a stdout that is /dev/null in an app process.
        File jar = Bridge.jar();
        if (jar == null)
            Log.warn("no bridge jar: pass --bridge-jar <path>, the JVM will not find its main class");
        List<String> classpath = Bridge.options.jvmClasspath();

        Log.info("starting the JVM: " + Bridge.options.javaExecutable);

        setEnvironment();

        // Only some ROMs take the ld directory from this call. It has to run before the libraries are
        // loaded. On the other ROMs, loading the JRE libraries by absolute path below is what works.
        for (File dir : searchDirs()) {
            if (dir.isDirectory())
                updateLdPath(dir.getAbsolutePath());
        }
        // The staged arc natives are added for the same reason, and once instead of once per JRE directory:
        // the folder is not one of the JRE's. W^X: the staged libraries are read-only, so arc can only load
        // them by name.
        File nativeFolder = Bridge.options.arcNativeFolder;
        if (nativeFolder != null && nativeFolder.isDirectory())
            updateLdPath(nativeFolder.getAbsolutePath());

        Log.info("JRE", "loading JVM libraries from " + jre);
        loadJvmLibs(jre.getAbsolutePath());

        JvmArgs args = new JvmArgs();
        List<String> argv = args.build(Bridge.options.javaExecutable.getAbsolutePath(), args.mainClass(),
                classpath, trailingArgs());
        args.print(argv, jre.getAbsolutePath(), classpath);
        int code = launchJVM(argv.toArray(new String[0]));
        Log.info("the JVM exited with code " + code);
    }

    /**
     * Builds the arguments that follow the main class on the JVM command line: the classpath an injected loader
     * owns first, then the caller's positional arguments. Those go last and bare, because the loader's own
     * parser collects bare words as its positional list and hands them to the game.
     */
    private static List<String> trailingArgs() {
        List<String> args = new ArrayList<>();
        if (Bridge.options.usesCustomLoader())
            args.add(BridgeOptions.CLASS_PATH_OPTION + "=" + String.join(File.pathSeparator,
                    Bridge.options.bridgeClasspath()));
        args.addAll(Bridge.options.positional);
        return args;
    }

    /** Sets the process environment the JVM and the game rely on. */
    private static void setEnvironment() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("JAVA_HOME", Bridge.options.javaHome.getAbsolutePath());
        env.put("HOME", Bridge.options.gameDataFolder.getAbsolutePath());
        env.put("MINDUSTRY_DATA_DIR", Bridge.options.gameDataFolder.getAbsolutePath());
        env.put("TMPDIR", new File(Bridge.options.cacheFolder, "tmp").getAbsolutePath());
        env.put("PATH", new File(Bridge.options.javaHome, "bin").getAbsolutePath() + ":" + System.getenv("PATH"));
        env.put("LD_LIBRARY_PATH", joinLdPaths());

        for (Map.Entry<String, String> entry : env.entrySet()) {
            if (entry.getValue() == null)
                continue;
            setEnv(entry.getKey(), entry.getValue());
        }
    }

    // Declared where they are used: the table binding them is generated from the @Native names
    // below, so the two cannot drift apart.

    /** Sets a process environment variable. */
    @Native("jre::Loader::SetEnv")
    private static native void setEnv(String key, String value);

    /**
     * Appends a directory to the linker's search path with {@code android_update_LD_LIBRARY_PATH}, only
     * effective on some ROMs, so it is a best effort companion to loading the JVM libraries by absolute
     * path. Must run before {@link #loadJvmLibs}: the linker reads the path when it resolves a library.
     */
    @Native("jre::Loader::UpdateLinkerPath")
    private static native void updateLdPath(String path);

    /**
     * Loads every shared library a JRE needs to be started, in dependency order. Doing this up front is what
     * makes the later {@code JLI_Launch} work at all: the Android linker cannot be pointed at the JRE directory
     * (it ignores {@code LD_LIBRARY_PATH}), and a JRE's libraries usually carry no usable {@code DT_RUNPATH}, so
     * loading one by name fails even when the file sits next to its caller. A library that cannot be loaded is
     * reported in logcat and skipped.
     *
     * @param jreDir the JRE root, the directory that contains {@code bin} and {@code lib}
     */
    @Native("jre::Loader::LoadJreLibraries")
    private static native void loadJvmLibs(String jreDir);

    /**
     * Starts the JVM. Blocks until the VM exits, so the caller must own a dedicated thread.
     *
     * @return the JVM exit code
     */
    @Native("jre::Launcher::LaunchJvm")
    private static native int launchJVM(String[] argv);

    /** The JRE directories the linker may have to look into. */
    private static List<File> searchDirs() {
        File lib = new File(Bridge.options.javaHome, "lib");
        List<File> dirs = new ArrayList<>();
        dirs.add(new File(lib, "jli"));
        for (String arch : Device.archCandidates()) {
            dirs.add(new File(new File(lib, arch), "server"));
            dirs.add(new File(new File(lib, arch), "client"));
            dirs.add(new File(lib, arch));
        }
        dirs.add(lib);
        return dirs;
    }

    private static String joinLdPaths() {
        StringBuilder builder = new StringBuilder();
        for (File dir : searchDirs()) {
            if (builder.length() > 0)
                builder.append(':');
            builder.append(dir.getAbsolutePath());
        }
        // W^X: the staged arc libraries are read-only, so arc loads them by name instead of extracting a
        // writable copy. That is why the branch entry sets OS.isAndroid to true. The JVM builds its library
        // search path from LD_LIBRARY_PATH while it starts, so this entry has to be here.
        File nativeFolder = Bridge.options.arcNativeFolder;
        if (nativeFolder != null && nativeFolder.exists())
            builder.append(':').append(nativeFolder.getAbsolutePath());
        return builder.toString();
    }
}
