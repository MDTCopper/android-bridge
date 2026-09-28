package copper.bridge.v1;

import arc.*;
import arc.files.*;
import arc.util.*;
import copper.bridge.*;

import java.io.*;

/**
 * The JVM side of arc's {@link Files} abstraction. {@code local}/{@code external} are the game data folder,
 * {@code absolute} is never rebased, and {@code internal}/{@code classpath} are entries of the game jars
 * (see {@link BridgeFi}) - which is all of it: a classpath cannot be enumerated, so a directory that lives only
 * inside a jar has no listing, exactly as on the desktop. Cache files live under the bridge's own folder rather
 * than arc's default {@code local("cache")}, which would scatter scratch files through the user's data folder.
 */
public class BridgeFiles implements Files {
    private final File dataFolder;
    private final File cacheFolder;

    public BridgeFiles() {
        this.dataFolder = require(Bridge.options.gameDataFolder, "game data folder");
        this.cacheFolder = new File(require(Bridge.options.cacheFolder, "cache folder"), "files");
    }

    /** The handle for a path of any type. See {@link BridgeFi}. */
    @Override
    public Fi get(String path, FileType type) {
        return new BridgeFi(this, path, type);
    }

    /** @return an absolute path, so arc's default {@code cache(...)} resolves without a rebase. */
    @Override
    public String getCachePath() {
        if (!cacheFolder.isDirectory()) cacheFolder.mkdirs();
        return cacheFolder.getAbsolutePath();
    }

    @Override
    public String getExternalStoragePath() {
        return dataFolder.getAbsolutePath() + File.separator;
    }

    @Override
    public boolean isExternalStorageAvailable() {
        return usable(dataFolder);
    }

    @Override
    public String getLocalStoragePath() {
        return dataFolder.getAbsolutePath() + File.separator;
    }

    @Override
    public boolean isLocalStorageAvailable() {
        return usable(dataFolder);
    }

    /** Whether a folder can be written to, creating it when the ART side has not done so yet. */
    private static boolean usable(File folder) {
        return (folder.isDirectory() || folder.mkdirs()) && folder.canWrite();
    }

    private static File require(File folder, String what) {
        if (folder == null) throw new ArcRuntimeException("BridgeFiles: the ART side handed over no " + what);
        return folder;
    }
}
