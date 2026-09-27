package copper.bridge.v1;

import arc.util.*;
import copper.bridge.*;
import copper.bridge.jvm.Launch;
import copper.bridge.jvm.LwjglNatives;
import copper.bridge.util.Log;
import mindustry.*;
import org.lwjgl.system.*;

import java.io.*;

/**
 * The v1 branch entry point. Named like the two bridge entry points because it is one; only the caller
 * differs, this one being reached reflectively by the JVM side once the branch is chosen.
 *
 * <p>It is the branch's only entry: both classpath rules reach it through {@link #main(String[])}, so
 * nothing here has to know which rule applied.</p>
 */
public class Main {
    /**
     * Starts the game with the bridge's own class loader. The parameter list is the entry contract
     * {@code copper.bridge.jvm.Launch} reflects on: {@code getDeclaredMethod("main", String[].class)}.
     *
     * <p>The array is the caller's positional arguments: the ART side collected them, they travelled on the
     * JVM command line, and {@code Launch} handed them here. Nothing in the game's own core reads a command
     * line - the desktop launcher is the only entry that does, and this branch replaces it - so they are
     * reported here, where a reader can see they arrived.</p>
     */
    public static void main(String[] args) {
        Log.info("starting branch v1 for game " + Launch.gameVersion());
        Log.info("game arguments: " + (args.length == 0 ? "none" : String.join(" ", args)));

        // Runs first, before anything touches arc. OS.isAndroid decides two things. First, whether arc's
        // SharedLibraryLoader loads the staged libraries by name, or extracts its own copy from the classpath
        // with no executable bit and no read-only bit. Second, where arc puts its data folder. Neither desktop
        // answer may happen, so this comes before the preparations below.
        setOsType();

        prepareLwjglNatives();
        prepareArcNatives();
        prepareGameFolders();

        // The game wraps its logger while it sets itself up so that everything it prints also goes to
        // last_log.txt. The printed copy is the one this bridge wants - the native side turns the process's
        // stdout and stderr into logcat lines and into this VM's log file, so a second copy in a file of
        // the game's own would only go stale. This is the flag Vars.loadFileLogger checks before it opens
        // anything.
        Vars.loadedFileLogger = true;

        // The game colourises what it prints and its stdout is the log file, so the escape sequences would
        // end up in the file and in logcat. This is the switch arc's formatter consults, set before the
        // launcher installs that logger.
        arc.util.Log.useColors = false;

        // The flags reach the game's own logger here: arc's level is what decides which of the
        // game's lines survive, and its ladder stops at debug, so both --debug and --verbose ask for
        // that one level. The bridge's own logger is set separately on each entry point.
        if (Bridge.options.debug || Bridge.options.verbose)
            arc.util.Log.level = arc.util.Log.LogLevel.debug;

        BridgeApplication application = new BridgeApplication();
        application.addListener(BridgeLaunchers.create());
        application.run();
    }

    /**
     * Makes LWJGL's native libraries loadable. LWJGL only searches the directories it is told about, and
     * its libraries sit inside the bridge jar, so they are unpacked first and that folder becomes the
     * search path. The hash check is off because the libraries were not shipped by LWJGL, so their hashes
     * are not in its manifest.
     */
    private static void prepareLwjglNatives() {
        File lwjgl = LwjglNatives.extract();
        if(lwjgl != null){
            Configuration.DISABLE_HASH_CHECKS.set(true);
            Configuration.LIBRARY_PATH.set(
                    new File(lwjgl, "org" + File.separator + "lwjgl").getAbsolutePath()
                            + File.pathSeparator
                            + new File(lwjgl, "org" + File.separator + "lwjgl" + File.separator + "opengles").getAbsolutePath());
            Log.info("GL", "lwjgl natives: " + lwjgl);
        }
    }

    /**
     * Starts arc's own native load. arc's own backends would do it during start-up and this branch replaces
     * them, so the call is made here. When arc's own library was not among the ones staged, the native paths
     * are switched off instead of failing at the first use.
     */
    private static void prepareArcNatives() {
        if(!Bridge.options.foundArcNative || Bridge.options.arcNativeFolder == null) {
            ArcNativesLoader.disableNativesLoading = true;
            Log.warn("no arc natives were staged; arc will use its non-native code paths");
            return;
        }

        Log.info("loading arc natives");
        ArcNativesLoader.load();
    }

    /**
     * Points the game at the data folder the caller asked for. The game looks at {@code mindustry.data.dir}
     * first, and without it arc would pick the user's home directory: a real JVM does not report itself as
     * Android, and the bridge injects {@code os.name=Linux} so the JRE behaves normally.
     */
    private static void prepareGameFolders() {
        File data = Bridge.options.gameDataFolder;
        if(data == null)
            return;
        data.mkdirs();
        System.setProperty("mindustry.data.dir", data.getAbsolutePath());
        Log.info("game data: " + data);
    }

    private static void setOsType() {
        // Read the field first, so the class initialises now. Its initialiser takes the platform from the
        // system properties, which say Linux here. The lines below then override that.
        boolean t = OS.isAndroid;

        OS.isAndroid = true;
        OS.isWindows = false;
        OS.isLinux = false;
        OS.isMac = false;
        // Added in arc d825843
        try {
            Reflect.set(OS.class, "isMobile", true);
            Reflect.set(OS.class, "isDesktop", false);
        } catch (Throwable ignored) {}
    }
}
