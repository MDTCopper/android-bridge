package copper.bridge.art;

import copper.bridge.*;
import copper.bridge.annotation.*;

import copper.bridge.util.*;
import java.io.*;
import java.util.*;

/**
 * Starts the JVM from the ART side, on its own thread: {@code JLI_Launch} does not return until the game exits. The
 * order below is the one the Android linker forces.
 */
public class Bootstrap {

    public static void start() {
        File jre = Bridge.options.javaHome;

        ArcNatives.stage();

        // the host alone knows where the jar ended up: without --bridge-jar the JVM dies quietly in under a second
        File jar = Bridge.jar();
        if (jar == null)
            Log.warn("no bridge jar: pass --bridge-jar <path>, the JVM will not find its main class");
        List<String> classpath = Bridge.options.jvmClasspath();

        Log.info("starting the JVM: " + Bridge.options.javaExecutable);

        setEnvironment();

        // only some ROMs take the ld directory from this call, and it must run before the libraries are loaded
        for (File dir : searchDirs()) {
            if (dir.isDirectory())
                updateLdPath(dir.getAbsolutePath());
        }
        // the staged arc natives join for the same reason. W^X: they are read-only, so arc loads them by name.
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

    /** The arguments after the main class: the loader's classpath first, then the caller's positional arguments. */
    private static List<String> trailingArgs() {
        List<String> args = new ArrayList<>();
        if (Bridge.options.usesCustomLoader())
            args.add(BridgeOptions.CLASS_PATH_OPTION + "=" + String.join(File.pathSeparator,
                    Bridge.options.bridgeClasspath()));
        args.addAll(Bridge.options.positional);
        return args;
    }

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


    @Native("jre::Loader::SetEnv")
    private static native void setEnv(String key, String value);

    /** Appends a directory to the linker's search path; must run before {@link #loadJvmLibs}. */
    @Native("jre::Loader::UpdateLinkerPath")
    private static native void updateLdPath(String path);

    /** Loads every shared library a JRE needs to start, in dependency order: the linker cannot be pointed at the JRE
     *  directory, so doing it up front makes {@code JLI_Launch} work. One that cannot be loaded is skipped. */
    @Native("jre::Loader::LoadJreLibraries")
    private static native void loadJvmLibs(String jreDir);

    /** Starts the JVM, blocking until it exits; the caller must own a dedicated thread. Returns its exit code. */
    @Native("jre::Launcher::LaunchJvm")
    private static native int launchJVM(String[] argv);

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
        // W^X: the staged arc libraries are read-only, so arc loads them by name instead of extracting a copy.
        File nativeFolder = Bridge.options.arcNativeFolder;
        if (nativeFolder != null && nativeFolder.exists())
            builder.append(':').append(nativeFolder.getAbsolutePath());
        return builder.toString();
    }
}
