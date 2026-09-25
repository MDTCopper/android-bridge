package copper.bridge.jvm;

import copper.bridge.*;
import copper.bridge.branch.*;

import copper.bridge.util.*;
import java.lang.reflect.*;

/**
 * The start of a branch: which one applies to the game that was found, and the call into it.
 *
 * <p>Choosing happens only here: the ART side cannot load a branch class at all - it has neither arc nor the
 * game on its classpath - so it reports only the game version it read out of the jar. The call is reflective
 * because a branch is compiled against one game version, so naming it directly would make the shared half fail to
 * link against every other version.</p>
 */
public final class Launch {

    private static GameVersion gameVersion;

    private Launch() {
    }

    /**
     * The version of the game this side found, or {@code null} when no jar carried one. Kept here rather
     * than in the options because it is not something a caller asked for.
     */
    public static GameVersion gameVersion() {
        return gameVersion;
    }

    /**
     * Resolves the branch this game version needs and calls its entry point.
     *
     * @param args the positional arguments
     */
    public static void start(String[] args) {
        invoke(resolve(), args);
    }

    /**
     * Reads the branch this game version needs, and records the version it read.
     *
     * @return the branch name, which lives no longer than this call
     */
    private static String resolve() {
        gameVersion = GameVersion.fromJars(Bridge.options.gameJars);
        Log.info("game version: " + (gameVersion == null ? "unknown" : gameVersion.toString()));

        BranchResolver.Result result = BranchResolver.resolve(gameVersion,
                System.getProperty("copper.bridge.branch"));
        Log.info("selected branch: " + result.branch + (result.exact ? "" : " (closest match)"));
        return result.branch;
    }

    /**
     * Loads and calls {@code copper.bridge.<branch>.Main}. Loaded through this class's own loader, which by
     * construction can see the branch: the jar that carries {@code Launch} carries every branch beside it.
     */
    private static void invoke(String branch, String[] args) {
        String name = "copper.bridge." + branch + ".Main";
        try {
            Class<?> entry = Class.forName(name, true, Launch.class.getClassLoader());
            Method main = entry.getDeclaredMethod("main", String[].class);
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("game boot failed: " + name, e.getCause());
        } catch (Throwable e) {
            throw new RuntimeException("failed to start the branch entry point: " + name, e);
        }
    }
}
