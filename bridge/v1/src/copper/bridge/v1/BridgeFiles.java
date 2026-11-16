package copper.bridge.v1;

import copper.bridge.annotation.BridgeOverride;

import copper.bridge.*;
import copper.bridge.util.*;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.*;

import java.io.*;

/**
 * libGDX's files for the bridge. {@code local} and {@code external} are the game data folder, {@code absolute} is
 * never rebased, and {@code internal}/{@code classpath} are class-loader resources: the game jar is on the startup
 * loader's classpath, which is exactly where libGDX's desktop backend looks for an internal file.
 */
public class BridgeFiles implements Files {

    private final File dataFolder;

    public BridgeFiles(File data) {
        this.dataFolder = data;
    }

    /** FileHandle's (File, FileType) constructor is protected; this keeps the type the caller asked for. */
    private static final class Handle extends FileHandle {
        Handle(File file, FileType type) {
            super(file, type);
        }
    }

    @Override
    @BridgeOverride
    public FileHandle getFileHandle(String path, FileType type) {
        switch (type) {
            case Classpath:
            case Internal:
                // FileHandle.read/exists fall back to the class loader for these types, which is the game jar.
                return new Handle(new File(path), type);
            case Local:
            case External:
                return new Handle(new File(dataFolder, path), type);
            default:
                return new Handle(new File(path), type);
        }
    }

    @Override
    @BridgeOverride
    public FileHandle classpath(String path) {
        return getFileHandle(path, FileType.Classpath);
    }

    @Override
    @BridgeOverride
    public FileHandle internal(String path) {
        return getFileHandle(path, FileType.Internal);
    }

    @Override
    @BridgeOverride
    public FileHandle external(String path) {
        return getFileHandle(path, FileType.External);
    }

    @Override
    @BridgeOverride
    public FileHandle absolute(String path) {
        return getFileHandle(path, FileType.Absolute);
    }

    @Override
    @BridgeOverride
    public FileHandle local(String path) {
        return getFileHandle(path, FileType.Local);
    }

    @Override
    @BridgeOverride
    public String getExternalStoragePath() {
        return dataFolder.getAbsolutePath() + File.separator;
    }

    @Override
    @BridgeOverride
    public boolean isExternalStorageAvailable() {
        return usable(dataFolder);
    }

    @Override
    @BridgeOverride
    public String getLocalStoragePath() {
        return dataFolder.getAbsolutePath() + File.separator;
    }

    @Override
    @BridgeOverride
    public boolean isLocalStorageAvailable() {
        return usable(dataFolder);
    }

    /** Whether a folder can be written to, creating it when the ART side has not done so yet. */
    private static boolean usable(File folder) {
        return (folder.isDirectory() || folder.mkdirs()) && folder.canWrite();
    }
}
