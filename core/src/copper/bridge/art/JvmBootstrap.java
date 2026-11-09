package copper.bridge.art;

import copper.bridge.*;
import copper.bridge.annotation.*;
import copper.bridge.util.*;

import java.io.*;
import java.util.*;

/**
 * Starts the JVM from the ART side, on its own thread: {@code JLI_Launch} does not return until the game exits. The
 * order below is the one the Android linker forces; everything about the command line and its environment is built
 * by {@link JvmArgs}.
 */
public class JvmBootstrap {

    public static void start() {
        File jre = Bridge.options.javaHome;
        ArcNatives.stage();
        JvmArgs arguments = new JvmArgs();

        // the host alone knows where the jar ended up: without --bridge-jar the JVM dies quietly in under a second
        File jar = Bridge.jar();
        if (jar == null)
            Log.warn("no bridge jar: pass --bridge-jar <path>, the JVM will not find its main class");

        Log.info("starting the JVM: " + Bridge.options.javaExecutable);

        setEnvironment(arguments);

        // only some ROMs take the ld directory from this call, and it must run before the libraries are loaded
        for (File dir : arguments.libraryDirs()) {
            if (dir.isDirectory())
                updateLdPath(dir.getAbsolutePath());
        }

        Log.info("JRE", "loading JVM libraries from " + jre);
        loadJvmLibs(jre.getAbsolutePath());

        List<String> argv = arguments.build();
        arguments.print(argv, jre.getAbsolutePath());
        int code = launchJVM(argv.toArray(new String[0]));
        Log.info("the JVM exited with code " + code);
    }

    private static void setEnvironment(JvmArgs arguments) {
        for (Map.Entry<String, String> entry : arguments.environment().entrySet()) {
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
}
