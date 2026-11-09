package copper.bridge.v2;

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
 *
 * <p>This branch's {@code Files} has no default path methods, so every entry point names its type here.
 */
public class BridgeFiles implements Files {
    private final File dataFolder;

    public BridgeFiles() {
        this.dataFolder = require(Bridge.options.gameDataFolder, "game data folder");
    }

    @Override
    public FileHandle getFileHandle(String path, FileType type) {
        return new BridgeFi(this, path, type);
    }

    @Override
    public FileHandle classpath(String path) {
        return getFileHandle(path, FileType.Classpath);
    }

    @Override
    public FileHandle internal(String path) {
        return getFileHandle(path, FileType.Internal);
    }

    @Override
    public FileHandle external(String path) {
        return getFileHandle(path, FileType.External);
    }

    @Override
    public FileHandle absolute(String path) {
        return getFileHandle(path, FileType.Absolute);
    }

    @Override
    public FileHandle local(String path) {
        return getFileHandle(path, FileType.Local);
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
