package copper.bridge.art;

import copper.bridge.*;

import copper.bridge.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Stages the caller's native libraries under the names arc's loader asks for. arc looks its libraries up by
 * name, not by path, so each file has to exist under that name in a folder on the JVM's library search
 * path; {@link Bootstrap} puts the staging folder there. The mapped name is computed from the injected
 * system properties, because this class never touches arc. That is what lets every branch share this file.
 */
public final class ArcNatives {
    private ArcNatives() {
    }

    /** Cache subfolder the caller's libraries are staged in. */
    private static final String STAGING_FOLDER = "native/arc";

    /** The logical name of arc's own library: the one {@code ArcNativesLoader} asks for. */
    private static final String ARC = "libarc.so";

    /**
     * Stages the caller's native libraries under the names their loaders ask for, and stores the folder in
     * {@link BridgeOptions#arcNativeFolder} for the JVM side. Two layouts are accepted: the libraries
     * directly in the folder, or one subfolder per Android ABI. A subfolder for the ABI this process runs is
     * searched first. Only the {@code lib<name>.so} shape is looked for, because the bridge does not know
     * which libraries arc may need.
     *
     * <p>When the caller gave no library at all, no folder is stored, so callers have to check for
     * {@code null}.</p>
     */
    public static void stage() {
        File folder = new File(Bridge.options.cacheFolder, STAGING_FOLDER);

        List<File> libraries = libraries();
        if (libraries.isEmpty()) {
            Log.error("no native libraries under " + Bridge.options.arcLibPath
                    + "; arc's natives cannot be loaded");
            return;
        }

        // Staged under the mapped spelling, not under the name it arrived with: renaming only
        // libarc.so left libarc-filedialogs.so unfindable, because arc asks for
        // libarc-filedialogsarm64.so. See PREFIX.
        Map<String, File> wanted = new LinkedHashMap<>();
        String arcName = null;
        boolean foundArc = false;
        for (File library : libraries) {
            boolean success = true;
            success &= library.setExecutable(true, true);
            success &= library.setReadOnly();
            if (!success)
                Log.warn("failed to mark arc library executable and readonly: " + library.getAbsolutePath());

            String name = library.getName();
            wanted.put(name, library);
            if (ARC.equals(name))
                foundArc = true;
        }

        // Reconciled, not rebuilt: an entry already staged under the right name from the same file is
        // left alone, so a launch that changes nothing does not copy a hundred-megabyte library.
        folder.mkdirs();
        removeUnwanted(folder, wanted);
        for (Map.Entry<String, File> entry : wanted.entrySet()) {
            File staged = new File(folder, entry.getKey());
            if (isCurrent(staged, entry.getValue()))
                continue;
            if (!stage(entry.getValue(), staged))
                continue;
            Log.verbose("staged " + entry.getValue().getName() + " as " + entry.getKey());
        }

        if (!foundArc)
            Log.error("no lib" + ARC + ".so under " + Bridge.options.arcLibPath
                    + "; arc's own native cannot be loaded");

        Bridge.options.foundArcNative = foundArc;
        Bridge.options.arcNativeFolder = folder;
    }

    /**
     * Every library the caller handed over, this device's architecture first: inside a folder the ABI
     * subfolder wins over the files directly in it, because it is the one certainly for this process.
     */
    private static List<File> libraries() {
        File path = Bridge.options.arcLibPath;
        List<File> found = new ArrayList<>();
        if (path == null)
            return found;
        if (path.isFile()) {
            found.add(path);
            return found;
        }

        File byAbi = new File(path, Bridge.options.abi);
        if (byAbi.isDirectory())
            addFiles(byAbi, found);
        if (found.isEmpty())
            addFiles(path, found);
        return found;
    }

    private static void addFiles(File directory, List<File> found) {
        File[] children = directory.listFiles();
        if (children == null)
            return;
        // a stable order, so a lock line says the same thing on every launch
        Arrays.sort(children, Comparator.comparing(File::getName));
        for (File child : children) {
            if (child.isFile() && child.getName().endsWith(".so"))
                found.add(child);
        }
    }

    /**
     * Puts one library under the staging folder: a symbolic link first, since copying costs the whole
     * size again, and a copy where the file system has no links - Android's
     * {@code getExternalFilesDir} is the usual example.
     */
    private static boolean stage(File from, File to) {
        try {
            if (to.exists())
                to.delete();
            Files.createSymbolicLink(to.toPath(), Paths.get(from.getAbsolutePath()));
            return true;
        } catch (Throwable e) {
            // unsupported, not permitted, or already there: any of the three means copying
        }

        try {
            try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
                Streams.pipeStream(in, out, true);
            }
            boolean success = true;
            success &= to.setExecutable(true, true);
            success &= to.setReadOnly();
            if (!success)
                Log.warn("failed to mark arc native executable and readonly: " + to.getAbsolutePath());

            return true;
        } catch (IOException e) {
            Log.error("failed to stage " + from + " as " + to);
            Log.error(e);
            return false;
        }
    }

    /** Drops whatever the folder holds that is not wanted any more, and says whether it dropped one. */
    private static boolean removeUnwanted(File folder, Map<String, File> wanted) {
        File[] children = folder.listFiles();
        if (children == null)
            return false;
        boolean removed = false;
        for (File child : children) {
            if (wanted.containsKey(child.getName()))
                continue;
            delete(child);
            removed = true;
        }
        return removed;
    }

    /**
     * Whether a staged entry already is the file it should be. A copy has nothing to compare but its size
     * and its time, and its time is when it was copied, so a source replaced afterwards is newer than its
     * copy and gets copied again.
     */
    private static boolean isCurrent(File staged, File source) {
        if (!staged.exists())
            return false;
        try {
            if (Files.isSymbolicLink(staged.toPath()))
                return Files.readSymbolicLink(staged.toPath()).equals(Paths.get(source.getAbsolutePath()));
        } catch (IOException e) {
            return false;
        }
        return staged.length() == source.length() && staged.lastModified() >= source.lastModified();
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null)
            for (File child : children)
                delete(child);
        file.delete();
    }
}
