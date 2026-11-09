package copper.bridge.v2;

import io.anuke.arc.util.*;
import copper.bridge.*;
import copper.bridge.jvm.Launch;
import copper.bridge.util.Log;
import io.anuke.mindustry.*;
import io.anuke.mindustry.net.*;

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

        prepareArcNatives();
        prepareGameFolders();

        // the game wraps its logger while it sets up: the printed copy is what this bridge wants, the native side
        // turning the process's stdout and stderr into logcat and the log file. The flag moved inside the range.

        configureLog();

        BridgeApplication application = new BridgeApplication();

        // the pre-ClientLauncher line: the game is the ApplicationCore listener, and the platform hooks are a
        // static instance rather than a callback the launcher hands back.
        new copper.bridge.v2.BridgePlatform();
        installCrashHandler();
        installNetProviders();
        application.addListener(new Mindustry());

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

    /** The game's crash reporter, called after the host handler; its entry point moved inside the range. */
    private static void installCrashHandler() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            reportCrash(error);
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                Log.error(error);
                System.exit(1);
            }
        });
    }

    private static void reportCrash(Throwable error) {
        try {
            io.anuke.arc.function.Consumer<File> written = file -> {
            };
            CrashSender.send(error, written);
        } catch (Throwable ignored) {
            Log.error(error);
        }
    }

    private static void installNetProviders() {
        try {
            Net.setClientProvider(new ArcNetClient());
            Net.setServerProvider(new ArcNetServer());
        } catch (Throwable ignored) {
        }
    }

    private static void configureLog() {
        io.anuke.arc.util.Log.setUseColors(false);
        if (Bridge.options.debug || Bridge.options.verbose)
            io.anuke.arc.util.Log.setLogLevel(io.anuke.arc.util.Log.LogLevel.debug);
    }

    private static void prepareArcNatives() {
        if (Bridge.options.arcNativeFolder == null) {
            Log.warn("no arc natives were staged; arc cannot load its native code paths");
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

    /**
     * Marks the process as Android before anything reads OS: the class reads the desktop properties when it loads,
     * so the flags are overridden as soon as it exists. The 6 line has no isMobile/isDesktop, only these flags.
     */
    private static void setOsType() {
        // read the field first, so the class initialises while the properties still say Linux
        boolean t = OS.isAndroid;

        OS.isAndroid = true;
        OS.isWindows = false;
        OS.isLinux = false;
        OS.isMac = false;
    }
}