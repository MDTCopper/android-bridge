package copper.bridge.jvm;

import copper.bridge.*;
import copper.bridge.branch.*;

import copper.bridge.util.*;
import java.lang.reflect.*;

/**
 * The start of a branch: which one applies to the game that was found, and the call into it. The call is reflective,
 * a branch being compiled against one game version.
 */
public final class Launch {

    private static GameVersion gameVersion;

    private Launch() {
    }

    /** Not an option: no caller asked for it. {@code null} when no jar carried a version. */
    public static GameVersion gameVersion() {
        return gameVersion;
    }

    public static void start(String[] args) {
        invoke(resolve(), args);
    }

    private static String resolve() {
        gameVersion = GameVersion.fromJars(Bridge.options.gameJars);
        Log.info("game version: " + (gameVersion == null ? "unknown" : gameVersion.toString()));

        BranchResolver.Result result = BranchResolver.resolve(gameVersion,
                System.getProperty("copper.bridge.branch"));
        Log.info("selected branch: " + result.branch + (result.exact ? "" : " (closest match)"));
        return result.branch;
    }

    /** Loads and calls the branch's {@code Main} through its archived classes and the game jars. */
    private static void invoke(String branch, String[] args) {
        String name = "copper.bridge." + branch + ".Main";
        try {
            ClassLoader loader = new BranchClassLoader(branch, Bridge.options.gameJars,
                    Launch.class.getClassLoader());
            Thread.currentThread().setContextClassLoader(loader);
            Class<?> entry = Class.forName(name, true, loader);
            Method main = entry.getDeclaredMethod("main", String[].class);
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("game boot failed: " + name, e.getCause());
        } catch (Throwable e) {
            throw new RuntimeException("failed to start the branch entry point: " + name, e);
        }
    }
}
