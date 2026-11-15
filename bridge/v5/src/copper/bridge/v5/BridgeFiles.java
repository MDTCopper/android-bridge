package copper.bridge.v5;

import copper.bridge.annotation.BridgeOverride;

import io.anuke.arc.*;
import io.anuke.arc.files.*;
import io.anuke.arc.util.*;
import copper.bridge.*;

import java.io.*;

/**
 * The JVM side of arc's {@link Files} abstraction: {@code local} and {@code external} are the game data folder,
 * {@code absolute} is never rebased, and {@code internal}/{@code classpath} are entries of the game jars - which is
 * all of it, since a classpath cannot be enumerated and a directory that lives only inside a jar has no listing,
 * exactly as on the desktop.
 */
public class BridgeFiles implements Files {
    /** The constant names changed case inside the range; the order did not. 0 is classpath, 3 absolute, 4 local. */
    private static final FileType[] FILE_TYPES = FileType.values();
    private final File dataFolder;

    public BridgeFiles() {
        this.dataFolder = require(Bridge.options.gameDataFolder, "game data folder");
    }

    @BridgeOverride
    public Fi get(String path, FileType type) {
        return new BridgeFi(this, path, type);
    }

    /** The floor's name for {@link #get}; the interface renamed it inside this branch's range. */
    @BridgeOverride
    public Fi getFileHandle(String path, FileType type) {
        return new BridgeFi(this, path, type);
    }

    // Default methods at this branch's ceiling, abstract at its floor: declaring them keeps the older end working.
    @BridgeOverride
    public Fi classpath(String path) {
        return get(path, FILE_TYPES[0]);
    }

    @BridgeOverride
    public Fi internal(String path) {
        return get(path, FILE_TYPES[1]);
    }

    @BridgeOverride
    public Fi external(String path) {
        return get(path, FILE_TYPES[2]);
    }

    @BridgeOverride
    public Fi absolute(String path) {
        return get(path, FILE_TYPES[3]);
    }

    @BridgeOverride
    public Fi local(String path) {
        return get(path, FILE_TYPES[4]);
    }

    @BridgeOverride
    public String getExternalStoragePath() {
        return dataFolder.getAbsolutePath() + File.separator;
    }

    @BridgeOverride
    public boolean isExternalStorageAvailable() {
        return usable(dataFolder);
    }

    @BridgeOverride
    public String getLocalStoragePath() {
        return dataFolder.getAbsolutePath() + File.separator;
    }

    @BridgeOverride
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