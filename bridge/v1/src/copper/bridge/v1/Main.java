package copper.bridge.v1;

import copper.bridge.*;
import copper.bridge.jvm.Launch;
import copper.bridge.util.Log;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.SharedLibraryLoader;
import io.anuke.kryonet.KryoClient;
import io.anuke.kryonet.KryoServer;
import io.anuke.mindustry.Mindustry;
import io.anuke.mindustry.net.Net;
import io.anuke.ucore.util.OS;

import java.io.*;

/**
 * The v1 branch entry point, reached reflectively by the JVM side once the branch is chosen. It boots the
 * libGDX/uCore game the way the desktop launcher does, with the bridge's Android-backed application in place of
 * the LWJGL window.
 */
public class Main {
    /**
     * Starts the game with the bridge's own class loader. The parameter list is the contract
     * {@code copper.bridge.jvm.Launch} reflects on: {@code getDeclaredMethod("main", String[].class)}.
     */
    public static void main(String[] args) {
        Log.info("starting branch " + Main.class.getPackage().getName().substring("copper.bridge.".length())
                + " for game " + Launch.gameVersion());
        Log.info("game arguments: " + (args.length == 0 ? "none" : String.join(" ", args)));

        setOsType();
        prepareGdxNatives();
        prepareGameFolders();

        installPlatform();
        installNetProviders();

        BridgeApplication application = new BridgeApplication(new Mindustry());
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

    /**
     * libGDX and uCore both read the platform from static flags initialised from the JVM's system properties.
     * The game must see Android: the mobile UI and the data directory are chosen from it.
     */
    private static void setOsType() {
        OS.isAndroid = true;
        OS.isLinux = false;
        OS.isWindows = false;
        OS.isMac = false;
        OS.isIos = false;

        SharedLibraryLoader.isAndroid = true;
        SharedLibraryLoader.isLinux = false;
        SharedLibraryLoader.isWindows = false;
        SharedLibraryLoader.isMac = false;
        SharedLibraryLoader.isIos = false;
        SharedLibraryLoader.is64Bit = false;
    }

    /**
     * The caller stages libGDX's Android native library through {@code --arc-lib}, next to the game's other
     * native code. libGDX resolves it by bare name on Android, so only the search path has to point there.
     */
    private static void prepareGdxNatives() {
        File folder = Bridge.options.arcNativeFolder;
        if (folder == null) {
            Log.warn("no arc native folder was staged; libGDX cannot load its native library");
            return;
        }

        System.setProperty("java.library.path",
                folder.getAbsolutePath() + File.pathSeparator + System.getProperty("java.library.path", ""));
        try {
            GdxNativesLoader.load();
        } catch (Throwable e) {
            Log.error("failed to load libGDX's native library from " + folder);
            Log.error(e);
        }
    }

    /** The game's own data directory; uCore reads it back through {@code Gdx.files.getLocalStoragePath()}. */
    private static void prepareGameFolders() {
        File data = Bridge.options.gameDataFolder;
        if (data == null)
            return;
        data.mkdirs();
        System.setProperty("mindustry.data.dir", data.getAbsolutePath());
        Log.info("game data: " + data);
    }

    /**
     * 40 moved the platform class from io.anuke.mindustry.io to io.anuke.mindustry.core and changed getUUID's
     * type. The legacy subclass is compiled against 33, so both shapes can be created without naming either
     * one in this file's signatures.
     */
    private static void installPlatform() {
        try {
            Object instance = Class.forName("copper.bridge.v1.BridgePlatform").getConstructor().newInstance();
            Class.forName("io.anuke.mindustry.core.Platform").getField("instance").set(null, instance);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Object instance = Class.forName("copper.bridge.v1.BridgePlatformLegacy").getConstructor().newInstance();
            Class.forName("io.anuke.mindustry.io.Platform").getField("instance").set(null, instance);
        } catch (Throwable e) {
            Log.error("no game platform class could be installed");
            Log.error(e);
        }
    }

    private static void installNetProviders() {
        Net.setClientProvider(new KryoClient());
        Net.setServerProvider(new KryoServer());
    }
}
