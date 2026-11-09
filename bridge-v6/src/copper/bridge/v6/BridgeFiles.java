package copper.bridge.v6;

import arc.*;
import arc.files.*;
import arc.util.*;
import copper.bridge.*;

import java.io.*;

/**
 * The JVM side of arc's {@link Files} abstraction: {@code local} and {@code external} are the game data folder,
 * {@code absolute} is never rebased, and {@code internal}/{@code classpath} are entries of the game jars - which is
 * all of it, since a classpath cannot be enumerated and a directory that lives only inside a jar has no listing,
 * exactly as on the desktop.
 */
public class BridgeFiles implements Files {
    private final File dataFolder;

    public BridgeFiles() {
        this.dataFolder = require(Bridge.options.gameDataFolder, "game data folder");
    }

    @Override
    public Fi get(String path, FileType type) {
        return new BridgeFi(this, path, type);
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
