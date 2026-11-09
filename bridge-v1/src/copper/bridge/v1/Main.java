package copper.bridge.v1;

import io.anuke.arc.util.*;
import copper.bridge.*;
import copper.bridge.jvm.Launch;
import copper.bridge.util.Log;
import io.anuke.mindustry.*;

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
        markLoggerLoaded();

        configureLog();

        BridgeApplication application = new BridgeApplication();

        // the pre-ClientLauncher line: the game is the ApplicationCore listener, and the platform hooks are a
        // static instance rather than a callback the launcher hands back.
        new BridgePlatform();
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
            Class<?> sender = Class.forName("io.anuke.mindustry.net.CrashSender");
            try {
                sender.getMethod("log", Throwable.class).invoke(null, error);
                return;
            } catch (Throwable ignored) {
            }
            io.anuke.arc.function.Consumer<File> written = file -> {
            };
            sender.getMethod("send", Throwable.class, io.anuke.arc.function.Consumer.class)
                    .invoke(null, error, written);
        } catch (Throwable ignored) {
            Log.error(error);
        }
    }

    /** The network implementation lives in the desktop jar's net module; absent trees simply keep singleplayer. */
    private static void installNetProviders() {
        try {
            Class<?> net = Class.forName("io.anuke.mindustry.net.Net");
            Class<?> clientProvider = Class.forName("io.anuke.mindustry.net.Net$ClientProvider");
            Class<?> serverProvider = Class.forName("io.anuke.mindustry.net.Net$ServerProvider");
            Object client = Class.forName("io.anuke.mindustry.net.ArcNetClient").getConstructor().newInstance();
            Object server = Class.forName("io.anuke.mindustry.net.ArcNetServer").getConstructor().newInstance();
            net.getMethod("setClientProvider", clientProvider).invoke(null, client);
            net.getMethod("setServerProvider", serverProvider).invoke(null, server);
        } catch (Throwable ignored) {
        }
    }

    /** Marks the game's logger as already installed; the field is {@code loadedFileLogger} on the newer end and
     *  {@code loadedLogger} on the older one. */
    private static void markLoggerLoaded() {
        try {
            Vars.class.getField("loadedFileLogger").setBoolean(null, true);
        } catch (Throwable ignored) {
            try {
                Vars.class.getField("loadedLogger").setBoolean(null, true);
            } catch (Throwable ignoredToo) {
            }
        }
    }

    /**
     * Turns the game's log colors off and, when asked, its level up. Both are fields on every version in the range;
     * they are private on the older ones, which is why they are set through arc's own reflector rather than
     * assigned.
     */
    private static void configureLog() {
        setStatic(io.anuke.arc.util.Log.class, "useColors", false);
        if (Bridge.options.debug || Bridge.options.verbose)
            setStatic(io.anuke.arc.util.Log.class, "level", io.anuke.arc.util.Log.LogLevel.debug);
    }

    /** Reflection without arc's helper, which does not exist at the range's floor. */
    private static void setStatic(Class<?> owner, String name, Object value) {
        try {
            java.lang.reflect.Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (Throwable ignored) {
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