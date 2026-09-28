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
 * The v1 branch entry point, reached reflectively by the JVM side once the branch is chosen; both classpath rules
 * arrive at {@link #main(String[])}, so nothing here has to know which one applied.
 */
public class Main {
    /**
     * Starts the game with the bridge's own class loader. The parameter list is the contract {@code
     * copper.bridge.jvm.Launch} reflects on: {@code getDeclaredMethod("main", String[].class)}.
     */
    public static void main(String[] args) {
        Log.info("starting branch v1 for game " + Launch.gameVersion());
        Log.info("game arguments: " + (args.length == 0 ? "none" : String.join(" ", args)));

        // runs first, before anything touches arc: OS.isAndroid decides whether arc's SharedLibraryLoader loads the
        // staged libraries by name or extracts its own copy, and where arc puts its data folder.
        setOsType();

        prepareLwjglNatives();
        prepareArcNatives();
        prepareGameFolders();

        // the game wraps its logger while it sets up: the printed copy is what this bridge wants, the native side
        // turning the process's stdout and stderr into logcat and the log file. Vars.loadFileLogger checks this flag.
        Vars.loadedFileLogger = true;

        arc.util.Log.useColors = false;

        if (Bridge.options.debug || Bridge.options.verbose)
            arc.util.Log.level = arc.util.Log.LogLevel.debug;

        BridgeApplication application = new BridgeApplication();
        application.addListener(BridgeLaunchers.create());

        // only a thread that dies of its own exception consults the default handler, and this throw happens on the
        // calling thread. With none installed, the bridge reports it itself and rethrows.
        try {
            application.run();
        } catch (Throwable e) {
            Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
            if (handler != null) {
                handler.uncaughtException(Thread.currentThread(), e);
            } else {
                Log.error("the game crashed");
                Log.error(e);
                throw e;
            }
        }
    }

    /** Makes LWJGL's natives loadable: they sit inside the bridge jar, so they are unpacked and that folder becomes
     *  LWJGL's search path. The hash check is off because LWJGL did not ship them. */
    private static void prepareLwjglNatives() {
        File lwjgl = LwjglNatives.extract();
        if (lwjgl != null) {
            Configuration.DISABLE_HASH_CHECKS.set(true);
            Configuration.LIBRARY_PATH.set(
                    new File(lwjgl, "org" + File.separator + "lwjgl").getAbsolutePath()
                            + File.pathSeparator
                            + new File(lwjgl, "org" + File.separator + "lwjgl" + File.separator + "opengles").getAbsolutePath());
            Log.info("GL", "lwjgl natives: " + lwjgl);
        }
    }

    private static void prepareArcNatives() {
        if (!Bridge.options.foundArcNative || Bridge.options.arcNativeFolder == null) {
            ArcNativesLoader.disableNativesLoading = true;
            Log.warn("no arc natives were staged; arc will use its non-native code paths");
            return;
        }

        Log.info("loading arc natives");
        ArcNativesLoader.load();
    }

    private static void prepareGameFolders() {
        File data = Bridge.options.gameDataFolder;
        if (data == null)
            return;
        data.mkdirs();
        System.setProperty("mindustry.data.dir", data.getAbsolutePath());
        Log.info("game data: " + data);
    }

    private static void setOsType() {
        // read the field first, so the class initialises while the properties still say Linux
        boolean t = OS.isAndroid;

        OS.isAndroid = true;
        OS.isWindows = false;
        OS.isLinux = false;
        OS.isMac = false;
        try {
            Reflect.set(OS.class, "isMobile", true);
            Reflect.set(OS.class, "isDesktop", false);
        } catch (Throwable ignored) {}
    }
}
