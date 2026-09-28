package copper.bridge.art;

import copper.bridge.*;

import copper.bridge.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Stages the caller's native libraries under the names arc's loader asks for. arc looks its libraries up by
 * name, not by path, so each file has to be in a folder on the JVM's library search path under the name arc
 * computes for it. A file is staged under its own name, because this class never touches arc and does not
 * reproduce arc's naming rule.
 */
public final class ArcNatives {
    private ArcNatives() {
    }

    /** Cache subfolder the caller's libraries are staged in. */
    private static final String STAGING_FOLDER = "native/arc";

    /** The file name arc's own library has to arrive under: the one {@code ArcNativesLoader} asks for. */
    private static final String ARC = "libarc.so";

    /**
     * Stages the caller's native libraries under the names they arrived with, and stores the folder in
     * {@link BridgeOptions#arcNativeFolder} for the JVM side. Two layouts are accepted: the libraries
     * directly in the folder, or one subfolder per Android ABI. A subfolder for the ABI this process runs is
     * searched first. Out of a folder only the {@code .so} files are taken, and none of them is renamed: the
     * bridge does not own the list of libraries arc may need, so it has no reason to rename one.
     *
     * <p>The caller has to hand the files over already spelled the way arc asks, including the ABI infix:
     * arc asks for {@code libarc-filedialogsarm64.so}, not for {@code libarc-filedialogs.so}. A wrong name is
     * not corrected here, and arc reports nothing when it does not find a library.</p>
     *
     * <p>Whether arc's own library was among them is recorded in {@link BridgeOptions#foundArcNative}. When
     * the caller gave no library at all, nothing is staged and {@link BridgeOptions#arcNativeFolder} stays
     * {@code null}.</p>
     */
    public static void stage() {
        File folder = new File(Bridge.options.cacheFolder, STAGING_FOLDER);

        List<File> libraries = Libraries.list(Bridge.options.arcLibPath, Bridge.options.abi);
        if (libraries.isEmpty()) {
            Log.error("no native libraries under " + Bridge.options.arcLibPath
                    + "; arc's natives cannot be loaded");
            return;
        }

        // Keyed by the file's own name, because that is the name arc will ask for later: the caller hands
        // the libraries over already spelled the way arc computes, and nothing here renames them.
        Map<String, File> wanted = new LinkedHashMap<>();
        boolean foundArc = false;
        for (File library : libraries) {
            Libraries.ensureLoadable(library);
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
            Log.verbose("staged " + entry.getKey());
        }

        if (!foundArc)
            Log.error("no " + ARC + " under " + Bridge.options.arcLibPath
                    + "; arc's own native cannot be loaded");

        Bridge.options.foundArcNative = foundArc;
        Bridge.options.arcNativeFolder = folder;
    }

    /**
     * Puts one library under the staging folder: a symbolic link first, since copying costs the whole
     * size again, and a copy where the file system has no links - Android's
     * {@code getExternalFilesDir} is the usual example.
     *
     * <p>A copy needs the mode set again: a link hands out the source's mode, while a copy is created with
     * the default one, and Android will not map a library that is not executable. The copy is left read-only
     * for the same reason the source is: a library anything may write can be mapped half written.</p>
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
            Libraries.ensureLoadable(to);
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
